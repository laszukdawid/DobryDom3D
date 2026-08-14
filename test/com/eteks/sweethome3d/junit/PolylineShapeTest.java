/*
 * PolylineShapeTest.java
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

import junit.framework.TestCase;

import com.eteks.sweethome3d.model.Polyline;

/**
 * Tests that the shape a polyline answers hit tests with follows the properties it is
 * built from. A polyline caches its stroked shape, and the stroke width comes from its
 * thickness while the stroked path depends on whether the path is closed.
 * @author Dawid Laszuk
 */
public class PolylineShapeTest extends TestCase {
  /**
   * The stroked shape is as wide as the thickness of the polyline, so a thickness
   * change must not be answered from the shape cached for the previous thickness.
   */
  public void testShapeFollowsThicknessChange() {
    Polyline polyline = new Polyline(new float [][] {{0, 0}, {200, 0}});
    // 8 cm away from a 1 cm thick segment, and inside once it is 20 cm thick
    float x = 100;
    float y = 8;

    assertFalse("A 1 cm thick polyline shouldn't reach y = 8",
        polyline.containsPoint(x, y, 0));
    polyline.setThickness(20);
    assertTrue("A polyline thickened to 20 should reach y = 8",
        polyline.containsPoint(x, y, 0));
    assertTrue("A rectangle over the thickened stroke should intersect it",
        polyline.intersectsRectangle(90, 5, 110, 9));

    polyline.setThickness(1);
    assertFalse("A polyline thinned back to 1 shouldn't reach y = 8",
        polyline.containsPoint(x, y, 0));
  }

  /**
   * Closing the path adds a segment between the last and the first points, so hit tests
   * must not keep answering from the path cached while it was open. Points of a polyline
   * drawn in the plan get their closing segment from <code>setClosedPath</code> once the
   * user closes it back on its first point.
   */
  public void testShapeFollowsClosedPathChange() {
    Polyline polyline = new Polyline(new float [][] {{0, 0}, {200, 0}, {200, 200}, {0, 200}});
    // The middle of the segment which only exists once the path is closed
    float x = 0;
    float y = 100;

    assertFalse("An open polyline has no segment between its last and first points",
        polyline.containsPoint(x, y, 2));
    polyline.setClosedPath(true);
    assertTrue("A closed polyline should contain the middle of its closing segment",
        polyline.containsPoint(x, y, 2));
    polyline.setClosedPath(false);
    assertFalse("A polyline opened back should lose its closing segment",
        polyline.containsPoint(x, y, 2));
  }

  /**
   * Same check for a polyline with curved joins, whose whole path is rebuilt from the
   * closed path flag.
   */
  public void testCurvedShapeFollowsClosedPathChange() {
    Polyline polyline = new Polyline(new float [][] {{0, 0}, {200, 0}, {200, 200}, {0, 200}});
    polyline.setJoinStyle(Polyline.JoinStyle.CURVED);
    // The closing curve from (0, 200) to (0, 0) bows away from the polyline: its control
    // points both lie at 200 / 3.625 to its left, putting its middle at 3/4 of that
    float x = -200 / 3.625f * 3 / 4;
    float y = 100;

    assertFalse("An open curved polyline has no curve between its last and first points",
        polyline.containsPoint(x, y, 2));
    polyline.setClosedPath(true);
    assertTrue("A closed curved polyline should contain the middle of its closing curve",
        polyline.containsPoint(x, y, 2));
  }
}
