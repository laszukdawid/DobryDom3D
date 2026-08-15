/*
 * PlanComponentWallCutOutTest.java
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
import java.awt.geom.Area;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.WeakHashMap;

import javax.imageio.ImageIO;

import junit.framework.TestCase;

import com.eteks.sweethome3d.io.DefaultUserPreferences;
import com.eteks.sweethome3d.model.CatalogDoorOrWindow;
import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeDoorOrWindow;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Sash;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.swing.PlanComponent;

/**
 * Tests that a door cutting the walls on both of its sides clears every wall lying in
 * reach of its cut out, including a wall it only brushes, whatever walls the painting
 * chooses to intersect with it.
 * @author Dawid Laszuk
 */
public class PlanComponentWallCutOutTest extends TestCase {
  private static final int IMAGE_WIDTH  = 600;
  private static final int IMAGE_HEIGHT = 400;

  public void testCutOutReachesTheWallBehindTheDoor() throws Exception {
    UserPreferences preferences = new DefaultUserPreferences();
    preferences.setFurnitureViewedFromTop(false);
    Home home = new Home();
    home.getCompass().setVisible(false);
    // The wall holding the door, spanning y in [190, 210]
    home.addWall(new Wall(100, 200, 500, 200, 20, 250));
    // A parallel wall spanning y in [205, 225], brushing the door over 5 cm only: the cut
    // out of the door has to clear it too, since it lies within twice its thickness
    home.addWall(new Wall(100, 215, 500, 215, 20, 250));

    CatalogDoorOrWindow catalogDoor = new CatalogDoorOrWindow(
        "both-sides-door", "Door", null, createIconContent(), null,
        100, 20, 200, 0, true,
        1f, 0f,
        new Sash [0],
        CatalogPieceOfFurniture.IDENTITY_ROTATION, null,
        true, null, null);
    HomeDoorOrWindow door = new HomeDoorOrWindow(catalogDoor);
    door.setX(300);
    door.setY(200);
    door.setWallCutOutOnBothSides(true);
    home.addPieceOfFurniture(door);

    TestPlanComponent planComponent = new TestPlanComponent(home, preferences);
    BufferedImage image = paint(planComponent);

    // The middle of the second wall behind the door, clear of the outline of the cut out
    // area and of the door itself, must have been wiped back to the background
    for (int y = 214; y <= 222; y++) {
      for (int x = 294; x <= 306; x++) {
        assertEquals("The cut out of the door didn't clear the wall behind it at ("
                + x + ", " + y + ")",
            0xFFFFFF, image.getRGB(x, y) & 0xFFFFFF);
      }
    }
    // A control probe far from the door, where the same wall must keep its pattern
    boolean patternDrawn = false;
    for (int y = 206; y <= 224 && !patternDrawn; y++) {
      for (int x = 120; x <= 160 && !patternDrawn; x++) {
        patternDrawn = (image.getRGB(x, y) & 0xFFFFFF) != 0xFFFFFF;
      }
    }
    assertTrue("The walls away from the door lost their painting", patternDrawn);
  }

  /**
   * The walls a door cuts depend on the level they are at, and the cut out areas are
   * cached: a wall moved to the level of the door must be cut from then on, not painted
   * whole under an area cached while it was elsewhere.
   */
  public void testCutOutFollowsAWallMovedToTheLevelOfTheDoor() throws Exception {
    UserPreferences preferences = new DefaultUserPreferences();
    preferences.setFurnitureViewedFromTop(false);
    Home home = new Home();
    home.getCompass().setVisible(false);
    Level groundLevel = new Level("ground", 0, 12, 250);
    Level upperLevel = new Level("upper", 262, 12, 250);
    home.addLevel(groundLevel);
    home.addLevel(upperLevel);
    home.setSelectedLevel(groundLevel);

    Wall doorWall = new Wall(100, 200, 500, 200, 20, 250);
    home.addWall(doorWall);
    doorWall.setLevel(groundLevel);
    // The brushing wall starts on the upper level, out of reach of the door
    Wall movedWall = new Wall(100, 215, 500, 215, 20, 250);
    home.addWall(movedWall);
    movedWall.setLevel(upperLevel);

    HomeDoorOrWindow door = createDoorCuttingBothSides();
    home.addPieceOfFurniture(door);
    door.setLevel(groundLevel);

    TestPlanComponent planComponent = new TestPlanComponent(home, preferences);
    // Warm the cut out cache while the brushing wall lives on the other level
    paintForScreen(planComponent);

    movedWall.setLevel(groundLevel);
    BufferedImage image = paintForScreen(planComponent);

    for (int y = 214; y <= 222; y++) {
      for (int x = 294; x <= 306; x++) {
        assertEquals("The cut out of the door missed the wall moved to its level at ("
                + x + ", " + y + ")",
            0xFFFFFF, image.getRGB(x, y) & 0xFFFFFF);
      }
    }
  }

  /**
   * A wall reaches the levels its height spans, so growing a wall of the level below up
   * to the level of the door must bring it into the cut out, not leave it painted whole
   * under an area cached while it was too short.
   */
  public void testCutOutFollowsAWallGrownUpToTheLevelOfTheDoor() throws Exception {
    UserPreferences preferences = new DefaultUserPreferences();
    preferences.setFurnitureViewedFromTop(false);
    Home home = new Home();
    home.getCompass().setVisible(false);
    Level lowerLevel = new Level("lower", -262, 12, 250);
    Level upperLevel = new Level("upper", 0, 12, 250);
    home.addLevel(lowerLevel);
    home.addLevel(upperLevel);
    home.setSelectedLevel(upperLevel);

    Wall doorWall = new Wall(100, 200, 500, 200, 20, 250);
    home.addWall(doorWall);
    doorWall.setLevel(upperLevel);
    // A brushing wall of the level below, 200 cm high: it stops 62 cm short of the
    // upper level, out of reach of the door
    Wall growingWall = new Wall(100, 215, 500, 215, 20, 200);
    home.addWall(growingWall);
    growingWall.setLevel(lowerLevel);

    HomeDoorOrWindow door = createDoorCuttingBothSides();
    home.addPieceOfFurniture(door);
    door.setLevel(upperLevel);

    // Warm the cut out cache while the brushing wall stays below
    TestPlanComponent planComponent = new TestPlanComponent(home, preferences);
    paintForScreen(planComponent);

    // Grown to 300 cm, the wall now spans into the upper level and shows in its plan,
    // so the cut out of the door has to wipe it there too
    growingWall.setHeight(Float.valueOf(300));
    BufferedImage image = paintForScreen(planComponent);

    for (int y = 214; y <= 222; y++) {
      for (int x = 294; x <= 306; x++) {
        assertEquals("The cut out of the door missed the wall grown up to its level at ("
                + x + ", " + y + ")",
            0xFFFFFF, image.getRGB(x, y) & 0xFFFFFF);
      }
    }
  }

  /**
   * The walls a door cuts also depend on the level of the door itself: at equal
   * elevations, a wall only reaches the levels above its own. A door moved down to the
   * level of the wall holding it must stop cutting a brushing wall of the level above,
   * whose painting a stale cached area would keep wiping.
   */
  public void testCutOutFollowsADoorMovedToAnotherLevel() throws Exception {
    UserPreferences preferences = new DefaultUserPreferences();
    preferences.setFurnitureViewedFromTop(false);
    Home home = new Home();
    home.getCompass().setVisible(false);
    // Two levels at the same elevation, drawn together in the plan and ordered by
    // their elevation index
    Level lowerLevel = new Level("lower", 0, 12, 250);
    lowerLevel.setElevationIndex(0);
    Level upperLevel = new Level("upper", 0, 12, 250);
    upperLevel.setElevationIndex(1);
    home.addLevel(lowerLevel);
    home.addLevel(upperLevel);
    home.setSelectedLevel(upperLevel);

    Wall doorWall = new Wall(100, 200, 500, 200, 20, 250);
    home.addWall(doorWall);
    doorWall.setLevel(lowerLevel);
    Wall brushingWall = new Wall(100, 215, 500, 215, 20, 250);
    home.addWall(brushingWall);
    brushingWall.setLevel(upperLevel);

    HomeDoorOrWindow door = createDoorCuttingBothSides();
    home.addPieceOfFurniture(door);
    door.setLevel(upperLevel);

    TestPlanComponent planComponent = new TestPlanComponent(home, preferences);
    // Warm the cut out cache while the door, at the upper level, cuts both walls
    BufferedImage warmImage = paintForScreen(planComponent);
    boolean brushingWallCut = true;
    for (int y = 214; y <= 222 && brushingWallCut; y++) {
      for (int x = 294; x <= 306 && brushingWallCut; x++) {
        brushingWallCut = (warmImage.getRGB(x, y) & 0xFFFFFF) == 0xFFFFFF;
      }
    }
    assertTrue("The door at the upper level should cut the brushing wall for this test "
        + "to check anything", brushingWallCut);

    // Moved down to the lower level, the door can't reach the upper brushing wall,
    // whose pattern has to show again behind it
    door.setLevel(lowerLevel);
    BufferedImage image = paintForScreen(planComponent);
    boolean patternDrawn = false;
    for (int y = 214; y <= 222 && !patternDrawn; y++) {
      for (int x = 294; x <= 306 && !patternDrawn; x++) {
        patternDrawn = (image.getRGB(x, y) & 0xFFFFFF) != 0xFFFFFF;
      }
    }
    assertTrue("The door moved to the lower level kept cutting the wall of the upper one",
        patternDrawn);
  }

  /**
   * Print and export painting may run outside the event dispatch thread, so neither may
   * read or write the cut out cache of interactive painting, which only that thread
   * owns. A cache which fails on any access proves they never touch it, and that
   * interactive painting still does.
   */
  public void testPrintAndExportPaintingLeaveTheInteractiveCacheAlone() throws Exception {
    UserPreferences preferences = new DefaultUserPreferences();
    preferences.setFurnitureViewedFromTop(false);
    Home home = new Home();
    home.getCompass().setVisible(false);
    home.addWall(new Wall(100, 200, 500, 200, 20, 250));
    HomeDoorOrWindow door = createDoorCuttingBothSides();
    home.addPieceOfFurniture(door);

    TestPlanComponent planComponent = new TestPlanComponent(home, preferences);
    Field cacheField = PlanComponent.class.getDeclaredField("doorOrWindowWallThicknessAreasCache");
    cacheField.setAccessible(true);
    cacheField.set(planComponent, new WeakHashMap<HomeDoorOrWindow, Area>() {
        @Override
        public Area get(Object key) {
          throw new IllegalStateException("Interactive cache read");
        }

        @Override
        public Area put(HomeDoorOrWindow key, Area value) {
          throw new IllegalStateException("Interactive cache written");
        }
      });

    try {
      paintForPrint(planComponent);
      paintForExport(planComponent);
    } catch (IllegalStateException ex) {
      fail("Print or export painting touched the interactive cache: " + ex.getMessage());
    }

    try {
      paintForScreen(planComponent);
      fail("Interactive painting didn't go through its cache");
    } catch (IllegalStateException ex) {
      // The trap sprang: interactive painting still uses the cache
    }
  }

  private HomeDoorOrWindow createDoorCuttingBothSides() throws IOException {
    CatalogDoorOrWindow catalogDoor = new CatalogDoorOrWindow(
        "both-sides-door", "Door", null, createIconContent(), null,
        100, 20, 200, 0, true,
        1f, 0f,
        new Sash [0],
        CatalogPieceOfFurniture.IDENTITY_ROTATION, null,
        true, null, null);
    HomeDoorOrWindow door = new HomeDoorOrWindow(catalogDoor);
    door.setX(300);
    door.setY(200);
    door.setWallCutOutOnBothSides(true);
    return door;
  }

  private BufferedImage paintForScreen(TestPlanComponent planComponent) throws Exception {
    BufferedImage image = createImage();
    Graphics2D g2D = (Graphics2D)image.getGraphics();
    g2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
    planComponent.paintItemsForScreen(g2D);
    g2D.dispose();
    return image;
  }

  private void paintForPrint(TestPlanComponent planComponent) throws Exception {
    BufferedImage image = createImage();
    Graphics2D g2D = (Graphics2D)image.getGraphics();
    planComponent.paintItems(g2D);
    g2D.dispose();
  }

  private void paintForExport(TestPlanComponent planComponent) throws Exception {
    BufferedImage image = createImage();
    Graphics2D g2D = (Graphics2D)image.getGraphics();
    planComponent.paintItemsForExport(g2D);
    g2D.dispose();
  }

  private BufferedImage createImage() {
    BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
    Graphics2D g2D = (Graphics2D)image.getGraphics();
    g2D.setColor(Color.WHITE);
    g2D.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
    g2D.dispose();
    return image;
  }

  private Content createIconContent() throws IOException {
    BufferedImage iconImage = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
    Graphics2D g2D = (Graphics2D)iconImage.getGraphics();
    g2D.setColor(Color.WHITE);
    g2D.fillRect(0, 0, 16, 16);
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

  private BufferedImage paint(TestPlanComponent planComponent) throws Exception {
    BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
    Graphics2D g2D = (Graphics2D)image.getGraphics();
    g2D.setColor(Color.WHITE);
    g2D.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
    g2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
    planComponent.paintItems(g2D);
    g2D.dispose();
    return image;
  }

  /**
   * A plan component giving access to the painting of home items, in printing mode so
   * that furniture icons are loaded synchronously.
   */
  private static class TestPlanComponent extends PlanComponent {
    private final Home home;

    public TestPlanComponent(Home home, UserPreferences preferences) {
      super(home, preferences, null);
      this.home = home;
      setSize(IMAGE_WIDTH, IMAGE_HEIGHT);
    }

    public void paintItems(Graphics g) throws Exception {
      paintHomeItems(g, this.home.getSelectedLevel(), 1f, Color.WHITE, Color.BLACK, PaintMode.PRINT);
    }

    public void paintItemsForScreen(Graphics g) throws Exception {
      paintHomeItems(g, this.home.getSelectedLevel(), 1f, Color.WHITE, Color.BLACK, PaintMode.PAINT);
    }

    public void paintItemsForExport(Graphics g) throws Exception {
      paintHomeItems(g, this.home.getSelectedLevel(), 1f, Color.WHITE, Color.BLACK, PaintMode.EXPORT);
    }
  }
}
