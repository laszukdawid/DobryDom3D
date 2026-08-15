/*
 * PlanComponentCullingTest.java
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

import javax.imageio.ImageIO;

import junit.framework.TestCase;

import com.eteks.sweethome3d.io.DefaultUserPreferences;
import com.eteks.sweethome3d.model.CatalogDoorOrWindow;
import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeDoorOrWindow;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Polyline;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Sash;
import com.eteks.sweethome3d.model.TextStyle;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.swing.PlanComponent;

/**
 * Tests that the clip based culling done while painting a plan doesn't drop any item
 * that should be painted.
 * @author Dawid Laszuk
 */
public class PlanComponentCullingTest extends TestCase {
  private static final int IMAGE_WIDTH  = 600;
  private static final int IMAGE_HEIGHT = 400;
  private static final int TILE_SIZE    = 64;

  /**
   * Paints the same home once with no clip, which switches culling off, then once tile by
   * tile, and checks that nothing painted in the first image is missing from the second one.
   * An item wrongly skipped by culling would be missing from the tiles its own points don't
   * reach.
   */
  public void testTiledPaintingMatchesFullPainting() throws Exception {
    UserPreferences preferences = new DefaultUserPreferences();
    // Keep painting independent from the availability of Java 3D
    preferences.setFurnitureViewedFromTop(false);
    Home home = createTestHome(preferences);
    TestPlanComponent planComponent = new TestPlanComponent(home, preferences);

    // Warm up caches (sorted items, furniture icons) so that both images are painted
    // from the same state
    paintUnclipped(planComponent);

    BufferedImage unclippedImage = paintUnclipped(planComponent);
    BufferedImage tiledImage = paintTiles(planComponent);

    assertNoDrawnPixelLost(unclippedImage, tiledImage);
  }

  /**
   * Same check with a door whose sash is far wider than the door itself. The sash values of a
   * door or a window are meant to be ratios of its size, but {@link Sash} doesn't enforce it and
   * neither does the reader of saved homes, so culling may not take it for granted.
   */
  public void testTiledPaintingKeepsOversizedSashes() throws Exception {
    UserPreferences preferences = new DefaultUserPreferences();
    preferences.setFurnitureViewedFromTop(false);
    Home home = new Home();
    Content icon = createIconContent();
    // A sash 4 times wider than the door sweeps a full circle of a 160 cm radius around a
    // door only 40 cm wide, way outside the points of the door
    CatalogDoorOrWindow catalogDoor = new CatalogDoorOrWindow(
        "wide-sash-door", "Door", null, icon, null,
        40, 20, 200, 0, true,
        1f, 0f,
        new Sash [] {new Sash(0.5f, 0.5f, 4f, 0f, (float)(2 * Math.PI))},
        CatalogPieceOfFurniture.IDENTITY_ROTATION, null,
        true, null, null);
    HomeDoorOrWindow door = new HomeDoorOrWindow(catalogDoor);
    door.setX(300);
    door.setY(200);
    home.addPieceOfFurniture(door);

    TestPlanComponent planComponent = new TestPlanComponent(home, preferences);
    paintUnclipped(planComponent);

    BufferedImage unclippedImage = paintUnclipped(planComponent);
    BufferedImage tiledImage = paintTiles(planComponent);

    assertNoDrawnPixelLost(unclippedImage, tiledImage);
  }

  /**
   * Same check with a door holding a NaN wall width. Nothing rejects a non finite float in the
   * model or in the reader of saved homes, and comparing anything to a NaN is false, so culling
   * has to keep painting an item whose bounds it can't compare rather than read that as
   * "outside the clip".
   */
  public void testTiledPaintingKeepsPieceWithNotComparableBounds() throws Exception {
    UserPreferences preferences = new DefaultUserPreferences();
    preferences.setFurnitureViewedFromTop(false);
    Home home = new Home();
    Content icon = createIconContent();
    CatalogDoorOrWindow catalogDoor = new CatalogDoorOrWindow(
        "nan-door", "Door", null, icon, null,
        40, 20, 200, 0, true,
        1f, 0f,
        new Sash [] {new Sash(0.5f, 0.5f, 4f, 0f, (float)(2 * Math.PI))},
        CatalogPieceOfFurniture.IDENTITY_ROTATION, null,
        true, null, null);
    HomeDoorOrWindow door = new HomeDoorOrWindow(catalogDoor);
    door.setX(300);
    door.setY(200);
    // The sash stays perfectly paintable, only the wall part of the door becomes uncomparable
    door.setWallWidth(Float.NaN);
    home.addPieceOfFurniture(door);

    TestPlanComponent planComponent = new TestPlanComponent(home, preferences);
    paintUnclipped(planComponent);

    BufferedImage unclippedImage = paintUnclipped(planComponent);
    BufferedImage tiledImage = paintTiles(planComponent);

    assertNoDrawnPixelLost(unclippedImage, tiledImage);
  }

  /**
   * Same check with a room ending in a sharp spike. Rooms are drawn with a miter joined stroke,
   * which sticks well past an acute vertex, and nothing keeps the points of a room from forming
   * one: {@link Room} only requires two of them and checks no angle.
   */
  public void testTiledPaintingKeepsMiteredRoomCorners() throws Exception {
    UserPreferences preferences = new DefaultUserPreferences();
    preferences.setFurnitureViewedFromTop(false);
    Home home = new Home();
    // The spike points at x = 318, just short of the tile starting at x = 320, and the miter
    // of the stroke drawn around it reaches roughly 7 cm further
    home.addRoom(new Room(new float [][] {{218, 189}, {318, 200}, {218, 211}}));

    TestPlanComponent planComponent = new TestPlanComponent(home, preferences);
    // The miter of the thinner printing stroke is too fine to cover a whole pixel
    planComponent.setScreenPaintMode(true);
    paintUnclipped(planComponent);

    BufferedImage unclippedImage = paintUnclipped(planComponent);
    BufferedImage tiledImage = paintTiles(planComponent);

    assertNoDrawnPixelLost(unclippedImage, tiledImage);
  }

  /**
   * A thick polyline ending in a sharp spike paints its miter join well past the box
   * around its points. This clip only covers pixels which lie beyond that box grown by
   * the plain thickness and arrow margin, so it passes only when culling grants miter
   * joined polylines their miter outset.
   */
  public void testClippedPaintingKeepsMiterSpikeOfThickPolyline() throws Exception {
    UserPreferences preferences = new DefaultUserPreferences();
    preferences.setFurnitureViewedFromTop(false);
    Home home = new Home();
    home.getCompass().setVisible(false);
    // The spike at (300, 100) has a half angle of atan(40 / 200) = 11.3 degrees, whose
    // miter factor 1 / sin = 5.1 stays under the limit of 10, so the join is drawn and
    // its tip reaches 6 * 5.1 = 30.6 units past the vertex, at about x = 330
    Polyline polyline = new Polyline(new float [][] {{100, 60}, {300, 100}, {100, 140}});
    polyline.setThickness(12);
    home.addPolyline(polyline);

    TestPlanComponent planComponent = new TestPlanComponent(home, preferences);
    paintUnclipped(planComponent);
    BufferedImage unclippedImage = paintUnclipped(planComponent);
    // The clip starts at x = 314, past the points box (x <= 300) grown by the thickness
    // and arrow margin (12), so only the miter outset can keep the polyline painted
    Rectangle spikeClip = new Rectangle(314, 80, 30, 40);
    BufferedImage clippedImage = createImage();
    paintInto(clippedImage, planComponent, spikeClip);

    assertClipKeepsDrawnPixels(unclippedImage, clippedImage, spikeClip);
  }

  /**
   * A curved closed polyline bows outside of the box around its points. This clip only
   * covers pixels of that bow, past the box grown by the plain thickness margin, so it
   * passes only when culling grants curved polylines the reach of their control points.
   */
  public void testClippedPaintingKeepsBowOfCurvedPolyline() throws Exception {
    UserPreferences preferences = new DefaultUserPreferences();
    preferences.setFurnitureViewedFromTop(false);
    Home home = new Home();
    home.getCompass().setVisible(false);
    // The closing left edge curve from (420, 130) to (420, 60) takes its control points
    // 100 / 3.625 = 27.6 units to the left, bowing to about x = 399 at its middle
    Polyline polyline = new Polyline(new float [][] {{420, 60}, {520, 60}, {520, 130}, {420, 130}});
    polyline.setJoinStyle(Polyline.JoinStyle.CURVED);
    polyline.setClosedPath(true);
    polyline.setThickness(3);
    home.addPolyline(polyline);

    TestPlanComponent planComponent = new TestPlanComponent(home, preferences);
    paintUnclipped(planComponent);
    BufferedImage unclippedImage = paintUnclipped(planComponent);
    // The clip ends at x = 414, short of the points box (x >= 420) grown by the
    // thickness margin (3), so only the bow allowance can keep the polyline painted
    Rectangle bowClip = new Rectangle(394, 85, 20, 20);
    BufferedImage clippedImage = createImage();
    paintInto(clippedImage, planComponent, bowClip);

    assertClipKeepsDrawnPixels(unclippedImage, clippedImage, bowClip);
  }

  /**
   * Checks that every pixel drawn strictly inside <code>clip</code> in the reference
   * image is drawn in the clipped image too, and that the region isn't trivially empty.
   */
  private void assertClipKeepsDrawnPixels(BufferedImage expectedImage, BufferedImage image,
                                          Rectangle clip) {
    int drawnPixelCount = 0;
    for (int y = clip.y + 1; y < clip.y + clip.height - 1; y++) {
      for (int x = clip.x + 1; x < clip.x + clip.width - 1; x++) {
        if (isDrawn(expectedImage, x, y)) {
          drawnPixelCount++;
          if (!isDrawnAround(image, x, y)) {
            fail("Painting under the clip lost what is painted at (" + x + ", " + y + ")");
          }
        }
      }
    }
    assertTrue("Nothing is painted under the clip, the fixture doesn't reach it",
        drawnPixelCount > 10);
  }

  /**
   * Returns a home containing items spread over the painted area, including a door
   * whose sashes and wall cut out are painted outside of its own points.
   */
  private Home createTestHome(UserPreferences preferences) throws IOException {
    Home home = new Home();
    // Walls all around and across the painted area
    Wall wallTop = new Wall(20, 20, 560, 20, 20, 250);
    Wall wallRight = new Wall(560, 20, 560, 360, 20, 250);
    Wall wallBottom = new Wall(560, 360, 20, 360, 20, 250);
    Wall wallLeft = new Wall(20, 360, 20, 20, 20, 250);
    home.addWall(wallTop);
    home.addWall(wallRight);
    home.addWall(wallBottom);
    home.addWall(wallLeft);
    // An inner wall holding the door below
    home.addWall(new Wall(20, 200, 560, 200, 20, 250));

    home.addRoom(new Room(new float [][] {{30, 30}, {300, 30}, {300, 350}, {30, 350}}));
    Room secondRoom = new Room(new float [][] {{310, 30}, {550, 30}, {550, 350}, {310, 350}});
    secondRoom.setName("Room name");
    secondRoom.setNameAngle((float)Math.PI / 6);
    secondRoom.setAreaVisible(true);
    home.addRoom(secondRoom);

    home.addPolyline(new Polyline(new float [][] {{60, 200}, {200, 260}, {280, 120}}));
    // A thick polyline ending in a sharp spike, whose miter join reaches far past the
    // spike, with arrows sized after its thickness
    Polyline miterPolyline = new Polyline(new float [][] {{350, 250}, {520, 245}, {350, 240}});
    miterPolyline.setThickness(6);
    miterPolyline.setStartArrowStyle(Polyline.ArrowStyle.DELTA);
    miterPolyline.setEndArrowStyle(Polyline.ArrowStyle.OPEN);
    home.addPolyline(miterPolyline);
    // A curved closed polyline, whose curves bow outside of the box around its points
    Polyline curvedPolyline = new Polyline(new float [][] {{420, 60}, {520, 60}, {520, 130}, {420, 130}});
    curvedPolyline.setJoinStyle(Polyline.JoinStyle.CURVED);
    curvedPolyline.setClosedPath(true);
    home.addPolyline(curvedPolyline);
    home.addLabel(new Label("A label", 420, 300));
    // Dimension lines: one with an offset, and a short one whose length text is wider
    // than the line itself and overflows its ends
    home.addDimensionLine(new DimensionLine(40, 40, 40, 340, 25));
    home.addDimensionLine(new DimensionLine(90, 330, 100, 330, -15));
    // A rotated multiline label aligned on its right edge, whose painted block reaches
    // well away from its anchor point, into tiles the anchor itself doesn't touch
    Label rotatedLabel = new Label("A much longer rotated label\non two lines", 470, 90);
    rotatedLabel.setAngle((float)Math.PI / 3);
    rotatedLabel.setStyle(new TextStyle(null, 18, false, false, TextStyle.Alignment.RIGHT));
    home.addLabel(rotatedLabel);

    // The default catalog is empty when the furniture library isn't deployed, so build
    // the catalog pieces this test needs from an image generated on the fly
    Content icon = createIconContent();

    // A door in the inner wall. Its sash sweeps a quarter of a circle of a 144 cm radius
    // reaching y = 56, far above the points of the door itself which span y = 190 to 210,
    // so the door has to be painted for tiles that its own points don't touch
    CatalogDoorOrWindow catalogDoor = new CatalogDoorOrWindow(
        "test-door", "Door", null, icon, null,
        160, 20, 200, 0, true,
        1f, 0f,
        new Sash [] {new Sash(0.05f, 0.5f, 0.9f, 0f, (float)Math.PI / 2)},
        CatalogPieceOfFurniture.IDENTITY_ROTATION, null,
        true, null, null);
    HomeDoorOrWindow door = new HomeDoorOrWindow(catalogDoor);
    door.setX(300);
    door.setY(200);
    door.setWallCutOutOnBothSides(true);
    home.addPieceOfFurniture(door);

    // Spread furniture over the whole area so that every tile holds some
    CatalogPieceOfFurniture catalogPiece = new CatalogPieceOfFurniture(
        "Piece", icon, null, 60, 40, 80, true, false);
    for (int x = 80; x < IMAGE_WIDTH - 60; x += 110) {
      for (int y = 80; y < IMAGE_HEIGHT - 60; y += 90) {
        HomePieceOfFurniture piece = new HomePieceOfFurniture(catalogPiece);
        piece.setX(x);
        piece.setY(y);
        piece.setAngle((float)Math.PI / 7);
        piece.setNameVisible(true);
        home.addPieceOfFurniture(piece);
      }
    }
    return home;
  }

  /**
   * Returns the content of a plain white PNG image used as the icon of the test furniture.
   * Furniture icons are drawn through a scaled <code>drawImage</code> call, whose result
   * isn't strictly clip invariant: a pixel at the very border of an icon may be resampled
   * differently when the plan is painted tile by tile. Painting the icons in the background
   * color keeps those pixels out of the comparison, while everything proving that a piece was
   * painted at all -- its border, its name, and the sashes and wall cut out of a door -- is
   * still drawn in the foreground color and compared exactly.
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

  /**
   * Returns an image of the whole painted area, painted tile by tile.
   */
  private BufferedImage paintTiles(TestPlanComponent planComponent) throws Exception {
    BufferedImage image = createImage();
    for (int y = 0; y < IMAGE_HEIGHT; y += TILE_SIZE) {
      for (int x = 0; x < IMAGE_WIDTH; x += TILE_SIZE) {
        Rectangle tile = new Rectangle(x, y,
            Math.min(TILE_SIZE, IMAGE_WIDTH - x), Math.min(TILE_SIZE, IMAGE_HEIGHT - y));
        paintInto(image, planComponent, tile);
      }
    }
    return image;
  }

  /**
   * Returns an image of the whole painted area, painted in one go with no clip at all.
   * A graphics with no clip switches culling off, so this is the reference of everything
   * the plan paints.
   */
  private BufferedImage paintUnclipped(TestPlanComponent planComponent) throws Exception {
    BufferedImage image = createImage();
    paintInto(image, planComponent, null);
    return image;
  }

  private BufferedImage createImage() {
    BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
    Graphics2D g2D = (Graphics2D)image.getGraphics();
    g2D.setColor(Color.WHITE);
    g2D.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
    g2D.dispose();
    return image;
  }

  private void paintInto(BufferedImage image, TestPlanComponent planComponent, Rectangle clip) throws Exception {
    Graphics2D g2D = (Graphics2D)image.getGraphics();
    // Painting is compared pixel per pixel, so antialiasing is turned off: the coverage an
    // antialiased edge gets is rounded from the tiles the rasterizer walks, which the clip
    // moves around, and two paintings of the very same shape under two different clips may
    // differ by a step or two along its border. Culling doesn't depend on those hints.
    g2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
    g2D.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
    g2D.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
    g2D.setClip(clip);
    planComponent.paintItems(g2D);
    g2D.dispose();
  }

  /**
   * Checks that every pixel drawn by <code>expectedImage</code> is still drawn in
   * <code>image</code>, give or take a pixel.
   * <p>Comparing both images pixel per pixel would be too strict: rasterizing a slanted
   * edge isn't clip invariant, whether antialiasing is on or off, so painting a plan tile
   * by tile moves some of the pixels of a slanted border by one. Culling can only ever
   * make an item disappear though, and an item that disappears leaves a whole run of
   * pixels behind, well beyond that one pixel of slack.
   */
  private void assertNoDrawnPixelLost(BufferedImage expectedImage, BufferedImage image) {
    for (int y = 0; y < IMAGE_HEIGHT; y++) {
      for (int x = 0; x < IMAGE_WIDTH; x++) {
        if (isDrawn(expectedImage, x, y)
            && !isDrawnAround(image, x, y)) {
          fail("Painting the plan tile by tile lost what is painted at (" + x + ", " + y + ")");
        }
      }
    }
  }

  /**
   * Returns <code>true</code> if the pixel at (<code>x</code>, <code>y</code>) isn't the
   * background of the painted area.
   */
  private boolean isDrawn(BufferedImage image, int x, int y) {
    return (image.getRGB(x, y) & 0xFFFFFF) != 0xFFFFFF;
  }

  /**
   * Returns <code>true</code> if a pixel is drawn at (<code>x</code>, <code>y</code>) or
   * at one of its neighbors.
   */
  private boolean isDrawnAround(BufferedImage image, int x, int y) {
    for (int dy = -1; dy <= 1; dy++) {
      for (int dx = -1; dx <= 1; dx++) {
        int neighborX = x + dx;
        int neighborY = y + dy;
        if (neighborX >= 0 && neighborX < IMAGE_WIDTH
            && neighborY >= 0 && neighborY < IMAGE_HEIGHT
            && isDrawn(image, neighborX, neighborY)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * A plan component giving access to the painting of home items.
   */
  private static class TestPlanComponent extends PlanComponent {
    private final Home home;

    public TestPlanComponent(Home home, UserPreferences preferences) {
      super(home, preferences, null);
      this.home = home;
      setSize(IMAGE_WIDTH, IMAGE_HEIGHT);
    }

    /**
     * Set to paint with the strokes of the screen rather than the thinner ones of a printer.
     * Only homes with no furniture may use it, because on screen furniture icons are loaded
     * in the background, which would make painting depend on when they arrive.
     */
    private boolean screenPaintMode;

    public void setScreenPaintMode(boolean screenPaintMode) {
      this.screenPaintMode = screenPaintMode;
    }

    /**
     * Paints home items at scale 1, in the coordinates system of <code>g</code>.
     * Printing mode is used by default to load furniture icons synchronously, so that painting
     * doesn't depend on icons loaded in the background.
     */
    public void paintItems(Graphics g) throws Exception {
      paintHomeItems(g, this.home.getSelectedLevel(), 1f, Color.WHITE, Color.BLACK,
          this.screenPaintMode ? PaintMode.PAINT : PaintMode.PRINT);
    }
  }
}
