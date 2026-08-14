/*
 * PlanComponentTopViewIconTest.java
 *
 * Copyright (c) 2026 Space Mushrooms <info@sweethome3d.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place, Suite 330, Boston, MA 02111-1307 USA
 */
package com.eteks.sweethome3d.junit;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import javax.imageio.ImageIO;

import junit.framework.TestCase;

import com.eteks.sweethome3d.io.DefaultUserPreferences;
import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.swing.PlanComponent;

/**
 * Tests the top view icons a plan paints for its furniture. The icons are shared between the
 * pieces that would draw the same one, through two weak maps whose entries only stay alive as
 * long as some piece keeps a strong reference to the key they're stored under, so this checks
 * that sharing keeps working and that nothing is lost when pieces are collected.
 * @author Dawid Laszuk
 */
public class PlanComponentTopViewIconTest extends TestCase {
  private static final int IMAGE_WIDTH  = 500;
  private static final int IMAGE_HEIGHT = 300;

  /**
   * Two pieces of the same model paint the same icon, and a piece of another model doesn't.
   */
  public void testIdenticalPiecesPaintTheSameIcon() throws Exception {
    Home home = new Home();
    Content redIcon = createIconContent(Color.RED);
    Content blueIcon = createIconContent(Color.BLUE);
    addPiece(home, createCatalogPiece("red", redIcon), 100, 150);
    addPiece(home, createCatalogPiece("red2", redIcon), 250, 150);
    addPiece(home, createCatalogPiece("blue", blueIcon), 400, 150);

    TestPlanComponent planComponent = createPlanComponent(home, createPreferences());
    BufferedImage image = paintWith(planComponent);
    assertEquals("Two pieces of the same model painted different icons",
        sample(planComponent, image, 100, 150), sample(planComponent, image, 250, 150));
    assertFalse("Two pieces of different models painted the same icon",
        sample(planComponent, image, 100, 150) == sample(planComponent, image, 400, 150));
  }

  /**
   * Repainting after the caches are dropped -- which is what changing the furniture icon size
   * preference does -- paints exactly what it painted before.
   */
  public void testRepaintAfterCachesAreDroppedIsUnchanged() throws Exception {
    Home home = createHomeWithSharedAndDistinctPieces();
    UserPreferences preferences = createPreferences();

    BufferedImage before = paint(home, preferences);
    // Drops furnitureTopViewIconKeys and its two companion caches
    preferences.setFurnitureModelIconSize(preferences.getFurnitureModelIconSize() + 1);
    preferences.setFurnitureModelIconSize(preferences.getFurnitureModelIconSize() - 1);
    BufferedImage after = paint(home, preferences);

    assertImagesEqual("Repainting after the icon caches were dropped changed the plan",
        before, after);
  }

  /**
   * The icon shared by several identical pieces lives in a weak map under a single key, and
   * that key is only kept alive by the pieces that map to it. Whichever of them survives, the
   * survivor has to keep painting the icon -- so this deletes every piece but one, in turn.
   */
  public void testIconSurvivesTheCollectionOfThePiecesSharingIt() throws Exception {
    int pieceCount = 3;
    for (int survivor = 0; survivor < pieceCount; survivor++) {
      Home home = new Home();
      Content icon = createIconContent(Color.RED);
      List<HomePieceOfFurniture> pieces = new ArrayList<HomePieceOfFurniture>();
      for (int i = 0; i < pieceCount; i++) {
        pieces.add(addPiece(home, createCatalogPiece("shared" + i, icon), 100 + i * 150, 150));
      }

      UserPreferences preferences = createPreferences();
      TestPlanComponent planComponent = createPlanComponent(home, preferences);
      BufferedImage before = paintWith(planComponent);
      float survivorX = 100 + survivor * 150;
      int sharedIconColor = sample(planComponent, before, survivorX, 150);

      // Leave one piece and drop every reference to the others, then push the weak maps hard
      // enough that anything only they kept alive is expunged.
      for (int i = 0; i < pieceCount; i++) {
        if (i != survivor) {
          home.deletePieceOfFurniture(pieces.get(i));
        }
      }
      pieces.clear();
      forceGarbageCollection();

      BufferedImage after = paintWith(planComponent);
      assertEquals("Keeping only piece " + survivor + " of " + pieceCount
          + " lost the icon they shared",
          sharedIconColor, sample(planComponent, after, survivorX, 150));
    }
  }

  /**
   * Painting has to keep answering after a garbage collection whatever else happened, since the
   * maps it reads are weak on both the pieces and the keys.
   */
  public void testRepaintsSurviveRepeatedCollections() throws Exception {
    Home home = createHomeWithSharedAndDistinctPieces();
    UserPreferences preferences = createPreferences();
    BufferedImage before = paint(home, preferences);

    for (int i = 0; i < 3; i++) {
      forceGarbageCollection();
      BufferedImage after = paint(home, preferences);
      assertImagesEqual("A repaint after a garbage collection changed the plan", before, after);
    }
  }

  /**
   * A change to a piece that changes the icon it should paint has to be picked up, even though
   * the piece is already mapped to a key.
   */
  public void testChangingAPieceChangesItsIcon() throws Exception {
    Home home = new Home();
    Content icon = createIconContent(Color.RED);
    HomePieceOfFurniture piece = addPiece(home, createCatalogPiece("piece", icon), 100, 150);
    HomePieceOfFurniture otherPiece = addPiece(home, createCatalogPiece("other", icon), 250, 150);

    UserPreferences preferences = createPreferences();
    TestPlanComponent planComponent = createPlanComponent(home, preferences);
    BufferedImage before = paintWith(planComponent);
    assertEquals("The two pieces should start out painting the same icon",
        sample(planComponent, before, 100, 150), sample(planComponent, before, 250, 150));

    piece.setColor(0xFF00FF00);
    BufferedImage after = paintWith(planComponent);
    assertFalse("A recoloured piece kept painting its old icon",
        sample(planComponent, after, 100, 150) == sample(planComponent, before, 100, 150));
    assertEquals("Recolouring one piece changed the icon of another",
        sample(planComponent, before, 250, 150), sample(planComponent, after, 250, 150));
    assertNotNull("The other piece stopped being painted", otherPiece);
  }

  /**
   * The cache the icons live in is a weak map, so the key a piece is mapped to has to be the
   * very instance that map is keyed by -- an equal sibling would let the entry be collected
   * while the piece still needs it, and the piece would then paint nothing at all.
   * <p>Printing a piece whose icon is still being loaded replaces the icon of the entry an
   * equal piece already created. Replacing the value of a map entry doesn't replace its key,
   * so that's where the piece and the cache may end up holding two different instances.
   */
  public void testEachPieceIsMappedToTheKeyItsIconIsCachedUnder() throws Exception {
    Home home = new Home();
    // Keep the icon of the first piece loading, so that it's still a wait icon when the
    // second piece is painted and the printed paint replaces it
    CountDownLatch iconLoaded = new CountDownLatch(1);
    Content icon = createBlockingIconContent(Color.RED, iconLoaded);
    try {
      addPiece(home, createCatalogPiece("first", icon), 100, 150);
      TestPlanComponent planComponent = createPlanComponent(home, createPreferences());
      paintOnce(planComponent, true);

      // A second identical piece, painted for the first time while printing
      addPiece(home, createCatalogPiece("second", icon), 250, 150);
      paintOnce(planComponent, false);

      Map<?, ?> iconKeys = readField(planComponent, "furnitureTopViewIconKeys");
      Map<?, ?> icons = readField(planComponent, "furnitureTopViewIconsCache");
      assertEquals("Both pieces should be mapped to a top view icon key", 2, iconKeys.size());
      assertEquals("Both pieces should share one cached icon", 1, icons.size());
      Object cachedKey = icons.keySet().iterator().next();
      for (Object key : iconKeys.values()) {
        assertNotNull("A piece was mapped to a null key", key);
        assertSame("A piece is mapped to an equal sibling of the key the cache is keyed by, "
            + "so the icon may be dropped while the piece is still painted", cachedKey, key);
      }
    } finally {
      iconLoaded.countDown();
    }
  }

  /**
   * The keys of that cache are used in hash maps, so they have to honour the equals/hashCode
   * contract: equality has to be symmetric, and two equal keys have to share a hash code.
   */
  public void testIconKeysHonourTheEqualsContract() throws Exception {
    Content planIcon = createIconContent(Color.RED);
    Content model = createIconContent(Color.BLUE);

    // A piece drawn from a plan icon and a piece rendered from the same model paint different
    // things, so neither key may match the other -- in either direction
    Object drawnKey = createIconKey(new HomePieceOfFurniture(
        createCatalogPiece("drawn", planIcon, model)));
    Object renderedKey = createIconKey(new HomePieceOfFurniture(
        createCatalogPiece("rendered", null, model)));
    assertFalse("A piece drawn from its plan icon matched a piece rendered from its model",
        drawnKey.equals(renderedKey));
    assertFalse("A piece rendered from its model matched a piece drawn from its plan icon",
        renderedKey.equals(drawnKey));

    // Data which doesn't change the plan icon of a piece doesn't change its key either,
    // and so may not change its hash code
    HomePieceOfFurniture dull = new HomePieceOfFurniture(
        createCatalogPiece("dull", planIcon, model));
    HomePieceOfFurniture shiny = new HomePieceOfFurniture(
        createCatalogPiece("shiny", planIcon, model));
    shiny.setShininess(0.5f);
    assertEqualKeysShareAHashCode("shininess", createIconKey(dull), createIconKey(shiny));

    HomePieceOfFurniture unrotated = new HomePieceOfFurniture(
        createCatalogPiece("unrotated", planIcon, model));
    HomePieceOfFurniture rotated = new HomePieceOfFurniture(createCatalogPiece("rotated",
        planIcon, model, new float [][] {{0, 0, 1}, {0, 1, 0}, {-1, 0, 0}}));
    assertEqualKeysShareAHashCode("model rotation",
        createIconKey(unrotated), createIconKey(rotated));
  }

  private void assertEqualKeysShareAHashCode(String difference, Object key1, Object key2) {
    assertTrue("Two pieces differing only by their " + difference + " should share an icon",
        key1.equals(key2) && key2.equals(key1));
    assertEquals("Two equal keys differing by their " + difference + " got a different hash code",
        key1.hashCode(), key2.hashCode());
  }

  private Object createIconKey(HomePieceOfFurniture piece) throws Exception {
    Constructor<?> keyConstructor = Class.forName(
            "com.eteks.sweethome3d.swing.PlanComponent$HomePieceOfFurnitureTopViewIconKey")
        .getDeclaredConstructor(HomePieceOfFurniture.class);
    keyConstructor.setAccessible(true);
    return keyConstructor.newInstance(piece);
  }

  private Map<?, ?> readField(PlanComponent planComponent, String name) throws Exception {
    Field field = PlanComponent.class.getDeclaredField(name);
    field.setAccessible(true);
    return (Map<?, ?>)field.get(planComponent);
  }

  private Home createHomeWithSharedAndDistinctPieces() throws IOException {
    Home home = new Home();
    Content sharedIcon = createIconContent(Color.RED);
    addPiece(home, createCatalogPiece("shared", sharedIcon), 80, 100);
    addPiece(home, createCatalogPiece("shared2", sharedIcon), 200, 100);
    addPiece(home, createCatalogPiece("shared3", sharedIcon), 320, 100);
    addPiece(home, createCatalogPiece("green", createIconContent(Color.GREEN)), 80, 220);
    addPiece(home, createCatalogPiece("blue", createIconContent(Color.BLUE)), 200, 220);
    addPiece(home, createCatalogPiece("cyan", createIconContent(Color.CYAN)), 320, 220);
    return home;
  }

  /**
   * Fills memory until a weakly held object is cleared, so that the weak maps read while
   * painting really have expunged what nothing keeps alive.
   */
  private void forceGarbageCollection() {
    java.lang.ref.WeakReference<Object> canary =
        new java.lang.ref.WeakReference<Object>(new Object());
    List<byte []> ballast = new ArrayList<byte []>();
    for (int i = 0; i < 200 && canary.get() != null; i++) {
      System.gc();
      try {
        ballast.add(new byte [1024 * 1024]);
      } catch (OutOfMemoryError ex) {
        ballast.clear();
      }
    }
    ballast.clear();
    System.gc();
    assertNull("Couldn't get a weakly held object collected", canary.get());
  }

  private UserPreferences createPreferences() {
    UserPreferences preferences = new DefaultUserPreferences();
    preferences.setFurnitureViewedFromTop(true);
    preferences.setGridVisible(false);
    return preferences;
  }

  private TestPlanComponent createPlanComponent(Home home, UserPreferences preferences) {
    TestPlanComponent planComponent = new TestPlanComponent(home, preferences);
    planComponent.setSize(IMAGE_WIDTH, IMAGE_HEIGHT);
    planComponent.setBounds(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
    return planComponent;
  }

  private BufferedImage paint(Home home, UserPreferences preferences) throws Exception {
    return paintWith(createPlanComponent(home, preferences));
  }

  /**
   * Paints the plan. Icons are pulled in asynchronously in screen paint mode, so the plan is
   * painted once in printing mode first, which loads them synchronously; without that, the
   * first paint of a piece draws a wait icon and every comparison here is against a placeholder.
   */
  private BufferedImage paintWith(TestPlanComponent planComponent) throws Exception {
    paintOnce(planComponent, false);
    return paintOnce(planComponent, true);
  }

  private BufferedImage paintOnce(TestPlanComponent planComponent, boolean screenPaintMode)
      throws Exception {
    BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
    Graphics2D g2D = (Graphics2D)image.getGraphics();
    g2D.setColor(Color.WHITE);
    g2D.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
    g2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
    g2D.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
    planComponent.setScreenPaintMode(screenPaintMode);
    float scale = planComponent.getScale();
    g2D.scale(scale, scale);
    g2D.translate(-planComponent.convertXPixelToModel(0), -planComponent.convertYPixelToModel(0));
    planComponent.paintItems(g2D, scale);
    g2D.dispose();
    return image;
  }

  /**
   * A plan component that paints its items on demand, in either paint mode.
   */
  private static class TestPlanComponent extends PlanComponent {
    private final Home    home;
    private boolean       screenPaintMode = true;

    public TestPlanComponent(Home home, UserPreferences preferences) {
      super(home, preferences, null);
      this.home = home;
    }

    public void setScreenPaintMode(boolean screenPaintMode) {
      this.screenPaintMode = screenPaintMode;
    }

    public void paintItems(Graphics g, float planScale) throws Exception {
      Level selectedLevel = this.home.getSelectedLevel();
      paintHomeItems(g, selectedLevel, planScale, Color.WHITE, Color.BLACK,
          this.screenPaintMode ? PaintMode.PAINT : PaintMode.PRINT);
    }
  }

  /**
   * Returns the color painted where the given point of the plan landed in the image.
   */
  private int sample(TestPlanComponent planComponent, BufferedImage image, float x, float y) {
    // The same transform paintOnce applies before painting the items
    float scale = planComponent.getScale();
    int pixelX = Math.round(scale * (x - planComponent.convertXPixelToModel(0)));
    int pixelY = Math.round(scale * (y - planComponent.convertYPixelToModel(0)));
    assertTrue("The point (" + x + ", " + y + ") of the plan falls outside the painted image, at ("
        + pixelX + ", " + pixelY + ")",
        pixelX >= 0 && pixelX < IMAGE_WIDTH && pixelY >= 0 && pixelY < IMAGE_HEIGHT);
    return image.getRGB(pixelX, pixelY);
  }

  private HomePieceOfFurniture addPiece(Home home, CatalogPieceOfFurniture catalogPiece,
                                        float x, float y) {
    HomePieceOfFurniture piece = new HomePieceOfFurniture(catalogPiece);
    piece.setX(x);
    piece.setY(y);
    home.addPieceOfFurniture(piece);
    return piece;
  }

  /**
   * Returns a catalog piece with a plan icon, which is what makes the plan paint a top view
   * icon for it without needing Java 3D.
   */
  private CatalogPieceOfFurniture createCatalogPiece(String id, Content planIcon) {
    return new CatalogPieceOfFurniture(id, "Piece " + id, null, null, null, null, null,
        planIcon, planIcon, null,
        60, 40, 80, 0, 0, true, null,
        CatalogPieceOfFurniture.IDENTITY_ROTATION, 0, null, null,
        true, true, true, false, null, null, null, null);
  }

  /**
   * Returns a catalog piece with both a plan icon and a model, either of which may be null.
   */
  private CatalogPieceOfFurniture createCatalogPiece(String id, Content planIcon, Content model) {
    return createCatalogPiece(id, planIcon, model, CatalogPieceOfFurniture.IDENTITY_ROTATION);
  }

  private CatalogPieceOfFurniture createCatalogPiece(String id, Content planIcon, Content model,
                                                     float [][] modelRotation) {
    return new CatalogPieceOfFurniture(id, "Piece " + id, null, model, planIcon, model,
        60, 40, 80, 0, true, modelRotation, null, true, null, null);
  }

  /**
   * Returns a content whose first read blocks until <code>released</code> counts down, which
   * keeps the icon read from it a wait icon for as long as the caller needs.
   */
  private Content createBlockingIconContent(Color color, final CountDownLatch released)
      throws IOException {
    final Content content = createIconContent(color);
    return new Content() {
        public InputStream openStream() throws IOException {
          try {
            released.await();
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while waiting to read the icon");
          }
          return content.openStream();
        }
      };
  }

  private Content createIconContent(Color color) throws IOException {
    BufferedImage iconImage = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
    Graphics2D g2D = (Graphics2D)iconImage.getGraphics();
    g2D.setColor(color);
    g2D.fillRect(0, 0, 32, 32);
    g2D.dispose();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(iconImage, "PNG", out);
    final byte [] png = out.toByteArray();
    return new Content() {
        public InputStream openStream() {
          return new ByteArrayInputStream(png);
        }
      };
  }

  private void assertImagesEqual(String message, BufferedImage expectedImage, BufferedImage image) {
    for (int y = 0; y < IMAGE_HEIGHT; y++) {
      for (int x = 0; x < IMAGE_WIDTH; x++) {
        if (expectedImage.getRGB(x, y) != image.getRGB(x, y)) {
          fail(message + " at (" + x + ", " + y + "): expected "
              + Integer.toHexString(expectedImage.getRGB(x, y))
              + " but was " + Integer.toHexString(image.getRGB(x, y)));
        }
      }
    }
  }
}
