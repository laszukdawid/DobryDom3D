/*
 * PlanComponentGridPaintingTest.java
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
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import javax.swing.JViewport;

import junit.framework.TestCase;

import com.eteks.sweethome3d.io.DefaultUserPreferences;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.swing.PlanComponent;

/**
 * Tests that the background grid painted only under the clip of the repainted region
 * matches the grid painted with the whole component as the clip. The lines of the grid
 * sit at fixed multiples of the grid size, so a grid painted tile by tile has to line up
 * exactly with a grid painted in one go.
 * @author Dawid Laszuk
 */
public class PlanComponentGridPaintingTest extends TestCase {
  private static final int IMAGE_WIDTH  = 600;
  private static final int IMAGE_HEIGHT = 400;
  private static final int TILE_SIZE    = 64;

  public void testTiledGridMatchesFullGrid() {
    checkTiledGridMatchesFullGrid();
  }

  /**
   * Same check with the grid drawn as lines rather than as an image texture, which is the
   * path taken on every system but Mac OS X, where it is only taken when the Quartz
   * rendering engine is on.
   */
  public void testTiledGridDrawnAsLinesMatchesFullGrid() {
    String previousUseQuartz = System.getProperty("apple.awt.graphics.UseQuartz");
    System.setProperty("apple.awt.graphics.UseQuartz", "true");
    try {
      checkTiledGridMatchesFullGrid();
    } finally {
      if (previousUseQuartz != null) {
        System.setProperty("apple.awt.graphics.UseQuartz", previousUseQuartz);
      } else {
        System.clearProperty("apple.awt.graphics.UseQuartz");
      }
    }
  }

  /**
   * Past 2^24 = 16777216 cm from the origin, consecutive floats are 2 cm apart, so a
   * grid path built on floats would collapse every other line of a 1 cm grid onto its
   * neighbor. The grid has to keep its lines distinct wherever the plan lies.
   */
  public void testGridLinesStayDistinctFarFromOrigin() {
    // Force the grid to be drawn as lines, as on every system but Mac OS X, and pin the
    // resolution scale the expected line spacing is computed from
    String previousUseQuartz = System.getProperty("apple.awt.graphics.UseQuartz");
    String previousResolutionScale = System.getProperty("com.eteks.sweethome3d.resolutionScale");
    System.setProperty("apple.awt.graphics.UseQuartz", "true");
    System.setProperty("com.eteks.sweethome3d.resolutionScale", "1");
    try {
      checkGridLinesStayDistinctFarFromOrigin();
    } finally {
      if (previousUseQuartz != null) {
        System.setProperty("apple.awt.graphics.UseQuartz", previousUseQuartz);
      } else {
        System.clearProperty("apple.awt.graphics.UseQuartz");
      }
      if (previousResolutionScale != null) {
        System.setProperty("com.eteks.sweethome3d.resolutionScale", previousResolutionScale);
      } else {
        System.clearProperty("com.eteks.sweethome3d.resolutionScale");
      }
    }
  }

  private void checkGridLinesStayDistinctFarFromOrigin() {
    UserPreferences preferences = new DefaultUserPreferences();
    preferences.setFurnitureViewedFromTop(false);
    assertTrue("The grid should be visible for this test to check anything",
        preferences.isGridVisible());
    Home home = new Home();
    home.getCompass().setVisible(false);
    // A wall stretching the plan past 2^24; the plan itself stays anchored at the origin
    home.addWall(new Wall(16777300, 100, 16777400, 100, 20, 250));

    TestPlanComponent planComponent = new TestPlanComponent(home, preferences);
    // At a scale of 10 the grid steps by 1 cm, drawing a line every 10 pixels
    planComponent.setScale(10);
    // Scroll a viewport to the region past 2^24, the only way to look at it since the
    // plan bounds always start back at the origin
    JViewport viewport = new JViewport();
    viewport.setSize(IMAGE_WIDTH, IMAGE_HEIGHT);
    viewport.setView(planComponent);
    planComponent.setSize(planComponent.getPreferredSize());
    int viewX = Math.round((16777250 - planComponent.convertXPixelToModel(0))
        * planComponent.getScale());
    viewport.setViewPosition(new Point(viewX, 0));

    BufferedImage image = createImage();
    Graphics2D g2D = (Graphics2D)image.getGraphics();
    g2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
    g2D.translate(-viewX, 0);
    g2D.setClip(viewX, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
    planComponent.paintFully(g2D);
    g2D.dispose();

    // Count the runs of drawn pixels along a scanline between two horizontal grid
    // lines: the view is 60 cm wide, so about 60 vertical lines have to show up, while
    // lines collapsing on shared floats would halve that count
    int lineCount = countDrawnRuns(image, 355);
    assertTrue("Only " + lineCount + " grid lines are left far from the origin",
        lineCount >= 50 && lineCount <= 70);
  }

  /**
   * Returns the number of runs of consecutive drawn pixels along the row at <code>y</code>.
   */
  private int countDrawnRuns(BufferedImage image, int y) {
    int runCount = 0;
    boolean inRun = false;
    for (int x = 0; x < IMAGE_WIDTH; x++) {
      if (isDrawn(image, x, y)) {
        if (!inRun) {
          runCount++;
          inRun = true;
        }
      } else {
        inRun = false;
      }
    }
    return runCount;
  }

  private void checkTiledGridMatchesFullGrid() {
    UserPreferences preferences = new DefaultUserPreferences();
    preferences.setFurnitureViewedFromTop(false);
    assertTrue("The grid should be visible for this test to check anything",
        preferences.isGridVisible());
    Home home = new Home();
    // A couple of axis aligned walls, whose painting is clip invariant pixel per pixel,
    // to check the grid keeps being painted around and under items. The compass is drawn
    // with antialiased curves, which aren't clip invariant, so it is hidden.
    home.getCompass().setVisible(false);
    home.addWall(new Wall(100, 100, 500, 100, 20, 250));
    home.addWall(new Wall(500, 100, 500, 300, 20, 250));

    TestPlanComponent planComponent = new TestPlanComponent(home, preferences);
    // Warm up caches so that both images are painted from the same state
    paintUnclipped(planComponent);

    BufferedImage unclippedImage = paintUnclipped(planComponent);
    BufferedImage tiledImage = paintTiles(planComponent);

    assertGridPainted(unclippedImage);
    assertNoDrawnPixelLost(unclippedImage, tiledImage);
  }

  /**
   * Checks that the image holds well more drawn pixels than the two walls alone can
   * account for, which is only true if the grid was painted.
   */
  private void assertGridPainted(BufferedImage image) {
    int drawnPixelCount = 0;
    for (int y = 0; y < IMAGE_HEIGHT; y++) {
      for (int x = 0; x < IMAGE_WIDTH; x++) {
        if (isDrawn(image, x, y)) {
          drawnPixelCount++;
        }
      }
    }
    assertTrue("Only " + drawnPixelCount + " pixels were drawn, the grid can't have been painted",
        drawnPixelCount > 20000);
  }

  private BufferedImage paintTiles(TestPlanComponent planComponent) {
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

  private BufferedImage paintUnclipped(TestPlanComponent planComponent) {
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

  private void paintInto(BufferedImage image, TestPlanComponent planComponent, Rectangle clip) {
    Graphics2D g2D = (Graphics2D)image.getGraphics();
    // Antialiasing is turned back on by the component itself, but painting only axis
    // aligned lines keeps rasterizing clip invariant, up to the one pixel of slack the
    // comparison allows
    g2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
    g2D.setClip(clip);
    planComponent.paintFully(g2D);
    g2D.dispose();
  }

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
   * white background of the painted area.
   */
  private boolean isDrawn(BufferedImage image, int x, int y) {
    return (image.getRGB(x, y) & 0xFFFFFF) != 0xFFFFFF;
  }

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
   * A plan component giving access to its whole painting, background and grid included.
   */
  private static class TestPlanComponent extends PlanComponent {
    public TestPlanComponent(Home home, UserPreferences preferences) {
      super(home, preferences, null);
      setSize(IMAGE_WIDTH, IMAGE_HEIGHT);
      setBackground(Color.WHITE);
    }

    public void paintFully(Graphics g) {
      paintComponent(g);
    }
  }
}
