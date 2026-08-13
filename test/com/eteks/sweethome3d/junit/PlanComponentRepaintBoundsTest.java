/*
 * PlanComponentRepaintBoundsTest.java
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
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.undo.UndoableEditSupport;

import junit.framework.TestCase;

import com.eteks.sweethome3d.io.DefaultUserPreferences;
import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Polyline;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.swing.PlanComponent;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.viewcontroller.PlanController;
import com.eteks.sweethome3d.viewcontroller.PlanView;
import com.eteks.sweethome3d.viewcontroller.ViewFactory;

/**
 * Tests that the region a plan asks to repaint after a change covers everything the change
 * alters. Repainting less than the whole component is only safe if what is left untouched on
 * screen still matches what a full repaint would draw.
 * @author Dawid Laszuk
 */
public class PlanComponentRepaintBoundsTest extends TestCase {
  private static final int IMAGE_WIDTH  = 600;
  private static final int IMAGE_HEIGHT = 400;

  /**
   * Selecting a piece of furniture draws an outline and indicators around it. Checks that the
   * region the plan asks to repaint covers them.
   */
  public void testSelectionRepaintsWhatItOutlines() throws Exception {
    UserPreferences preferences = new DefaultUserPreferences();
    preferences.setFurnitureViewedFromTop(false);
    Home home = new Home();
    HomePieceOfFurniture piece = createPiece(200, 200);
    home.addPieceOfFurniture(piece);
    home.addPieceOfFurniture(createPiece(400, 260));
    TestPlanComponent planComponent = new TestPlanComponent(home, preferences);

    assertRepaintCoversChange(planComponent, home, new Runnable() {
        public void run() {
          home.setSelectedItems(Arrays.asList(new Selectable [] {piece}));
        }
      });
  }

  /**
   * Deselecting has to erase the outline the selection drew.
   */
  public void testDeselectionRepaintsWhatItErases() throws Exception {
    UserPreferences preferences = new DefaultUserPreferences();
    preferences.setFurnitureViewedFromTop(false);
    Home home = new Home();
    HomePieceOfFurniture piece = createPiece(200, 200);
    home.addPieceOfFurniture(piece);
    home.setSelectedItems(Arrays.asList(new Selectable [] {piece}));
    TestPlanComponent planComponent = new TestPlanComponent(home, preferences);

    assertRepaintCoversChange(planComponent, home, new Runnable() {
        public void run() {
          home.setSelectedItems(Arrays.<Selectable>asList());
        }
      });
  }

  /**
   * Moving the selection from one item to another far away has to erase the first outline
   * and draw the second one, in one repaint.
   */
  public void testSelectionMovedFarAwayRepaintsBothPlaces() throws Exception {
    UserPreferences preferences = new DefaultUserPreferences();
    preferences.setFurnitureViewedFromTop(false);
    Home home = new Home();
    HomePieceOfFurniture nearPiece = createPiece(80, 80);
    HomePieceOfFurniture farPiece = createPiece(520, 320);
    home.addPieceOfFurniture(nearPiece);
    home.addPieceOfFurniture(farPiece);
    home.setSelectedItems(Arrays.asList(new Selectable [] {nearPiece}));
    TestPlanComponent planComponent = new TestPlanComponent(home, preferences);

    assertRepaintCoversChange(planComponent, home, new Runnable() {
        public void run() {
          home.setSelectedItems(Arrays.asList(new Selectable [] {farPiece}));
        }
      });
  }

  /**
   * Selecting a room, whose name and area are painted well away from its own points.
   */
  public void testRoomSelectionRepaintsNameAndArea() throws Exception {
    UserPreferences preferences = new DefaultUserPreferences();
    preferences.setFurnitureViewedFromTop(false);
    Home home = new Home();
    home.addWall(new Wall(40, 40, 560, 40, 20, 250));
    Room room = new Room(new float [][] {{100, 100}, {400, 100}, {400, 300}, {100, 300}});
    room.setName("A room with a name");
    room.setAreaVisible(true);
    home.addRoom(room);
    TestPlanComponent planComponent = new TestPlanComponent(home, preferences);

    assertRepaintCoversChange(planComponent, home, new Runnable() {
        public void run() {
          home.setSelectedItems(Arrays.asList(new Selectable [] {room}));
        }
      });
  }

  /**
   * Dragging a piece of furniture has to erase it where it was and draw it where it landed.
   * This drives a real controller, so it goes through the path a drag actually takes.
   */
  public void testDraggingFurnitureRepaintsBothPlaces() throws Exception {
    UserPreferences preferences = new DefaultUserPreferences();
    preferences.setFurnitureViewedFromTop(false);
    final Home home = new Home();
    HomePieceOfFurniture piece = createPiece(200, 200);
    home.addPieceOfFurniture(piece);
    home.setSelectedItems(Arrays.asList(new Selectable [] {piece}));

    final TestPlanComponent [] planComponent = new TestPlanComponent [1];
    ViewFactory viewFactory = new SwingViewFactory() {
        @Override
        public PlanView createPlanView(Home home, UserPreferences preferences,
                                       PlanController controller) {
          planComponent [0] = new TestPlanComponent(home, preferences, controller);
          return planComponent [0];
        }
      };
    final PlanController controller = new PlanController(home, preferences, viewFactory,
        null, new UndoableEditSupport());
    controller.getView();
    assertNotNull("Plan view wasn't created", planComponent [0]);

    // Press inside the piece but away from its center, which is where the indicator to move
    // its name sits, then drag it straight to the right in two steps. Moving along one axis
    // only means a single coordinate changes, so erasing the piece where it stood relies on
    // remembering the region it covered at the previous step.
    final float xPress = 175;
    final float yPress = 215;
    controller.pressMouse(xPress, yPress, 1, false, false);
    controller.moveMouse(xPress + 40, yPress);

    Rectangle repaintedRegion = assertRepaintCoversChange(planComponent [0], home, new Runnable() {
        public void run() {
          controller.moveMouse(xPress + 320, yPress);
        }
      });
    // The point of bounding the repaint: a drag may not ask for the whole plan back
    assertFalse("Dragging a piece of furniture repainted the whole plan",
        repaintedRegion.equals(new Rectangle(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT)));
    controller.releaseMouse(xPress + 320, yPress);
  }

  /**
   * A polyline is stroked with a thickness given in centimeters, unlike outlines and indicators
   * which keep the same size on screen, so what it paints around its path is not covered by a
   * margin counted in pixels. Checks the repaint region follows the thickness.
   */
  public void testThickPolylineSelectionRepaintsItsWholeStripe() throws Exception {
    UserPreferences preferences = new DefaultUserPreferences();
    preferences.setFurnitureViewedFromTop(false);
    final Home home = new Home();
    // A 200 cm thick polyline is stroked over a stripe 100 pixels wide at the default zoom,
    // and the selection tints that whole stripe
    final Polyline polyline = new Polyline(new float [][] {{150, 200}, {350, 200}});
    polyline.setThickness(200);
    home.addPolyline(polyline);
    TestPlanComponent planComponent = new TestPlanComponent(home, preferences);

    assertRepaintCoversChange(planComponent, home, new Runnable() {
        public void run() {
          home.setSelectedItems(Arrays.asList(new Selectable [] {polyline}));
        }
      });
  }

  /**
   * A piece selected inside a group is painted with a halo spread over the whole group, so the
   * repaint region has to cover the group, not just the piece.
   */
  public void testSubselectedGroupChildRepaintsGroupHalo() throws Exception {
    UserPreferences preferences = new DefaultUserPreferences();
    preferences.setFurnitureViewedFromTop(false);
    final Home home = new Home();
    HomePieceOfFurniture nearPiece = createPiece(120, 200);
    HomePieceOfFurniture farPiece = createPiece(480, 200);
    List<HomePieceOfFurniture> groupedFurniture = new ArrayList<HomePieceOfFurniture>();
    groupedFurniture.add(nearPiece);
    groupedFurniture.add(farPiece);
    HomeFurnitureGroup group = new HomeFurnitureGroup(groupedFurniture, "Group");
    home.addPieceOfFurniture(group);
    TestPlanComponent planComponent = new TestPlanComponent(home, preferences);

    // Subselecting one piece of the group draws the halo over the whole group,
    // as far as the piece at the other end
    assertRepaintCoversChange(planComponent, home, new Runnable() {
        public void run() {
          home.setSelectedItems(Arrays.asList(new Selectable [] {nearPiece}));
        }
      });
  }

  /**
   * Paints the plan, applies <code>change</code>, and checks that repainting only the region
   * the plan asked for turns the first image into the one a full repaint gives. A region that
   * is too small shows up either as something missing or as a leftover of the old state.
   */
  private Rectangle assertRepaintCoversChange(TestPlanComponent planComponent, Home home,
                                              Runnable change) throws Exception {
    // Load the furniture icons synchronously first: on screen they are fetched in the
    // background, and the placeholder drawn while they travel would change the plan
    // under the comparison
    planComponent.setScreenPaintMode(false);
    paintInto(createImage(), planComponent, null);
    planComponent.setScreenPaintMode(true);
    paintInto(createImage(), planComponent, null);

    BufferedImage beforeImage = createImage();
    paintInto(beforeImage, planComponent, null);

    planComponent.clearRepaintRequests();
    change.run();
    Rectangle repaintedRegion = planComponent.getRequestedRepaintRegion();
    assertNotNull("The change asked for no repaint at all", repaintedRegion);

    // What the user ends up looking at: the previous image, with the sole region the plan
    // asked to repaint painted over
    BufferedImage partiallyRepaintedImage = copyOf(beforeImage);
    clearRegion(partiallyRepaintedImage, repaintedRegion);
    paintInto(partiallyRepaintedImage, planComponent, repaintedRegion);

    // What a full repaint would have shown
    BufferedImage fullyRepaintedImage = createImage();
    paintInto(fullyRepaintedImage, planComponent, null);

    assertSameDrawing(fullyRepaintedImage, partiallyRepaintedImage);
    return repaintedRegion;
  }

  private HomePieceOfFurniture createPiece(float x, float y) throws IOException {
    CatalogPieceOfFurniture catalogPiece = new CatalogPieceOfFurniture(
        "Piece", createIconContent(), null, 60, 40, 80, true, false);
    HomePieceOfFurniture piece = new HomePieceOfFurniture(catalogPiece);
    piece.setX(x);
    piece.setY(y);
    piece.setAngle((float)Math.PI / 7);
    piece.setNameVisible(true);
    return piece;
  }

  /**
   * Returns the content of a plain white PNG image used as the icon of the test furniture.
   * Furniture icons are drawn through a scaled <code>drawImage</code> call, whose result isn't
   * strictly clip invariant, so painting them in the background color keeps the pixels at their
   * border out of the comparison.
   */
  private Content createIconContent() throws IOException {
    BufferedImage iconImage = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
    Graphics2D g2D = (Graphics2D)iconImage.getGraphics();
    g2D.setColor(Color.WHITE);
    g2D.fillRect(0, 0, 64, 64);
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

  private BufferedImage createImage() {
    BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
    Graphics2D g2D = (Graphics2D)image.getGraphics();
    g2D.setColor(Color.WHITE);
    g2D.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
    g2D.dispose();
    return image;
  }

  private BufferedImage copyOf(BufferedImage image) {
    BufferedImage copy = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
    Graphics2D g2D = (Graphics2D)copy.getGraphics();
    g2D.drawImage(image, 0, 0, null);
    g2D.dispose();
    return copy;
  }

  /**
   * Fills the given region with the background, the way Swing hands a cleared region
   * to an opaque component before it repaints it.
   */
  private void clearRegion(BufferedImage image, Rectangle region) {
    Graphics2D g2D = (Graphics2D)image.getGraphics();
    g2D.setColor(Color.WHITE);
    g2D.fillRect(region.x, region.y, region.width, region.height);
    g2D.dispose();
  }

  private void paintInto(BufferedImage image, TestPlanComponent planComponent, Rectangle clip)
      throws Exception {
    Graphics2D g2D = (Graphics2D)image.getGraphics();
    // The clip is set before the transform, so it stays in the coordinates the component
    // asks its repaints in
    g2D.setClip(clip);
    // Rasterizing a slanted edge isn't clip invariant, so painting is compared with a pixel of
    // slack rather than exactly, and antialiasing is kept off to keep that slack down
    g2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
    g2D.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
    g2D.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
    float scale = planComponent.getScale();
    g2D.scale(scale, scale);
    g2D.translate(-planComponent.convertXPixelToModel(0), -planComponent.convertYPixelToModel(0));
    planComponent.paintItems(g2D, scale);
    g2D.dispose();
  }

  /**
   * Checks that both images paint the same thing, give or take a pixel. Rasterizing a slanted
   * edge isn't clip invariant, so a pixel is accepted when the color expected of it turns up
   * anywhere among its neighbors. Colors are compared rather than merely drawn or not, because
   * selecting an item often just tints what is already painted there.
   */
  private void assertSameDrawing(BufferedImage expectedImage, BufferedImage image) {
    for (int y = 0; y < IMAGE_HEIGHT; y++) {
      for (int x = 0; x < IMAGE_WIDTH; x++) {
        if (!isColorAround(image, x, y, expectedImage.getRGB(x, y))) {
          fail("Repainting only the region the plan asked for lost what is painted at ("
              + x + ", " + y + "): expected " + Integer.toHexString(expectedImage.getRGB(x, y))
              + " but was " + Integer.toHexString(image.getRGB(x, y)));
        }
        if (!isColorAround(expectedImage, x, y, image.getRGB(x, y))) {
          fail("Repainting only the region the plan asked for left a leftover at ("
              + x + ", " + y + "): expected " + Integer.toHexString(expectedImage.getRGB(x, y))
              + " but was " + Integer.toHexString(image.getRGB(x, y)));
        }
      }
    }
  }

  /**
   * Returns <code>true</code> if <code>rgb</code> is the color of (<code>x</code>, <code>y</code>)
   * or of one of its neighbors.
   */
  private boolean isColorAround(BufferedImage image, int x, int y, int rgb) {
    for (int dy = -1; dy <= 1; dy++) {
      for (int dx = -1; dx <= 1; dx++) {
        int neighborX = x + dx;
        int neighborY = y + dy;
        if (neighborX >= 0 && neighborX < IMAGE_WIDTH
            && neighborY >= 0 && neighborY < IMAGE_HEIGHT
            && image.getRGB(neighborX, neighborY) == rgb) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * A plan component recording the regions it is asked to repaint.
   */
  private static class TestPlanComponent extends PlanComponent {
    private final Home home;
    private Rectangle  requestedRepaintRegion;
    private boolean    wholeComponentRequested;

    public TestPlanComponent(Home home, UserPreferences preferences) {
      this(home, preferences, null);
    }

    public TestPlanComponent(Home home, UserPreferences preferences, PlanController controller) {
      super(home, preferences, controller);
      this.home = home;
      setSize(IMAGE_WIDTH, IMAGE_HEIGHT);
    }

    @Override
    public void repaint() {
      this.wholeComponentRequested = true;
    }

    @Override
    public void repaint(long tm, int x, int y, int width, int height) {
      Rectangle region = new Rectangle(x, y, width, height);
      this.requestedRepaintRegion = this.requestedRepaintRegion == null
          ? region
          : this.requestedRepaintRegion.union(region);
    }

    @Override
    public void repaint(int x, int y, int width, int height) {
      repaint(0, x, y, width, height);
    }

    public void clearRepaintRequests() {
      this.requestedRepaintRegion = null;
      this.wholeComponentRequested = false;
    }

    /**
     * Returns the region asked to be repainted since the last {@link #clearRepaintRequests()},
     * the whole painted area if a full repaint was asked for, or <code>null</code> if nothing
     * was, in the coordinates of the component.
     */
    public Rectangle getRequestedRepaintRegion() {
      Rectangle wholePaintedArea = new Rectangle(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
      if (this.wholeComponentRequested) {
        return wholePaintedArea;
      } else if (this.requestedRepaintRegion == null) {
        return null;
      } else {
        return this.requestedRepaintRegion.intersection(wholePaintedArea);
      }
    }

    /**
     * Set to paint the way the plan is painted on screen, with its outlines and its indicators.
     * Printing mode is used first to pull the furniture icons in synchronously.
     */
    private boolean screenPaintMode = true;

    public void setScreenPaintMode(boolean screenPaintMode) {
      this.screenPaintMode = screenPaintMode;
    }

    /**
     * Paints home items at the given scale, in the coordinates system of <code>g</code>.
     */
    public void paintItems(Graphics g, float planScale) throws Exception {
      paintHomeItems(g, this.home.getSelectedLevel(), planScale, Color.WHITE, Color.BLACK,
          this.screenPaintMode ? PaintMode.PAINT : PaintMode.PRINT);
    }
  }
}
