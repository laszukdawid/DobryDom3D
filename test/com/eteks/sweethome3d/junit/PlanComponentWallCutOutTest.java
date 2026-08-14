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
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeDoorOrWindow;
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
  }
}
