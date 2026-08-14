/*
 * ItemPointsTest.java
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

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.ObserverCamera;

/**
 * Tests that the points a piece of furniture, a dimension line and the observer camera
 * answer follow the properties they are computed from. All three cache their points, and
 * every property those points are built from has to drop the cache.
 * @author Dawid Laszuk
 */
public class ItemPointsTest extends TestCase {
  /**
   * The corners of a piece follow its location, its size and its angle, whether they were
   * already asked for or not.
   */
  public void testPiecePointsFollowItsProperties() {
    CatalogPieceOfFurniture catalogPiece = new CatalogPieceOfFurniture(
        "Piece", null, null, 60, 40, 80, true, false);
    HomePieceOfFurniture piece = new HomePieceOfFurniture(catalogPiece);
    // Ask for the points once so that a stale cache would be answered from
    piece.getPoints();

    piece.setX(100);
    piece.setY(50);
    piece.setAngle((float)Math.PI / 5);
    piece.setWidth(90);

    HomePieceOfFurniture freshPiece = new HomePieceOfFurniture(catalogPiece);
    freshPiece.setX(100);
    freshPiece.setY(50);
    freshPiece.setAngle((float)Math.PI / 5);
    freshPiece.setWidth(90);
    assertPointsEqual(freshPiece.getPoints(), piece.getPoints());
  }

  /**
   * The array handed out by <code>getPoints</code> belongs to its caller: writing to it
   * must not reach what the piece answers later.
   */
  public void testPiecePointsHandedOutDontShareTheCache() {
    CatalogPieceOfFurniture catalogPiece = new CatalogPieceOfFurniture(
        "Piece", null, null, 60, 40, 80, true, false);
    HomePieceOfFurniture piece = new HomePieceOfFurniture(catalogPiece);
    float [][] wreckedPoints = piece.getPoints();
    float [][] snapshot = piece.getPoints();
    assertCopiesIndependent(wreckedPoints, snapshot);
    wreckPoints(wreckedPoints);
    assertPointsEqual(snapshot, piece.getPoints());
  }

  /**
   * The corners of the box around a dimension line follow its ends, its offset, and the
   * pitch and elevations making it an elevation dimension line.
   */
  public void testDimensionLinePointsFollowItsProperties() {
    DimensionLine dimensionLine = new DimensionLine(0, 0, 100, 0, 20);
    dimensionLine.getPoints();

    dimensionLine.setXEnd(200);
    dimensionLine.setOffset(-40);

    assertPointsEqual(new DimensionLine(0, 0, 200, 0, -40).getPoints(),
        dimensionLine.getPoints());

    // Turn it into an elevation dimension line, whose points follow its pitch instead of
    // the direction between its ends
    dimensionLine.getPoints();
    dimensionLine.setXEnd(0);
    dimensionLine.setElevationEnd(250);
    dimensionLine.setPitch((float)Math.PI / 2);

    DimensionLine freshDimensionLine = new DimensionLine(0, 0, 0, 0, 0, 250, -40);
    freshDimensionLine.setPitch((float)Math.PI / 2);
    assertPointsEqual(freshDimensionLine.getPoints(), dimensionLine.getPoints());

    float [][] wreckedPoints = dimensionLine.getPoints();
    float [][] snapshot = dimensionLine.getPoints();
    assertCopiesIndependent(wreckedPoints, snapshot);
    wreckPoints(wreckedPoints);
    assertPointsEqual(snapshot, dimensionLine.getPoints());
  }

  /**
   * The corners of the observer camera follow its location and its yaw.
   */
  public void testObserverCameraPointsFollowItsProperties() {
    ObserverCamera camera = new ObserverCamera(50, 50, 170, 0, 0, (float)Math.PI / 4);
    camera.getPoints();

    camera.setX(300);
    camera.setYaw((float)Math.PI / 3);

    ObserverCamera freshCamera = new ObserverCamera(300, 50, 170, (float)Math.PI / 3, 0, (float)Math.PI / 4);
    assertPointsEqual(freshCamera.getPoints(), camera.getPoints());

    float [][] wreckedPoints = camera.getPoints();
    float [][] snapshot = camera.getPoints();
    assertCopiesIndependent(wreckedPoints, snapshot);
    wreckPoints(wreckedPoints);
    assertPointsEqual(snapshot, camera.getPoints());
  }

  private void assertCopiesIndependent(float [][] points, float [][] otherPoints) {
    assertNotSame("Two calls shared their outer array", points, otherPoints);
    for (int i = 0; i < points.length; i++) {
      assertNotSame("Two calls shared a corner at index " + i, points [i], otherPoints [i]);
    }
  }

  /**
   * Writes garbage in the given points, which must not reach the points their item
   * answers afterwards.
   */
  private void wreckPoints(float [][] points) {
    for (float [] point : points) {
      point [0] = 100000;
      point [1] = 100000;
    }
  }

  private void assertPointsEqual(float [][] expectedPoints, float [][] points) {
    assertEquals("Wrong number of corners", expectedPoints.length, points.length);
    for (int i = 0; i < expectedPoints.length; i++) {
      assertEquals("Wrong abscissa at corner " + i, expectedPoints [i][0], points [i][0], 1E-3f);
      assertEquals("Wrong ordinate at corner " + i, expectedPoints [i][1], points [i][1], 1E-3f);
    }
  }
}
