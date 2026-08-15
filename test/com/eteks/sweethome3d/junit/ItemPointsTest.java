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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import junit.framework.TestCase;

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.ObserverCamera;

/**
 * Tests that the points a piece of furniture, a dimension line and the observer camera
 * answer follow the properties they are computed from. All three cache their points, so
 * the points are asked for before every single property change: a setter which forgets
 * to drop the cache is then caught on its own, not masked by a working neighbor.
 * @author Dawid Laszuk
 */
public class ItemPointsTest extends TestCase {
  /**
   * The corners of a piece follow each of its location, plan size and angle properties,
   * checked one at a time against a piece built in the same configuration.
   */
  public void testPiecePointsFollowEachProperty() {
    CatalogPieceOfFurniture catalogPiece = createCatalogPiece();
    HomePieceOfFurniture piece = new HomePieceOfFurniture(catalogPiece);
    HomePieceOfFurniture mirrorPiece = new HomePieceOfFurniture(catalogPiece);

    piece.getPoints();
    piece.setX(100);
    mirrorPiece.setX(100);
    assertPointsEqual("setX", mirrorPiece.getPoints(), piece.getPoints());

    piece.getPoints();
    piece.setY(50);
    mirrorPiece.setY(50);
    assertPointsEqual("setY", mirrorPiece.getPoints(), piece.getPoints());

    piece.getPoints();
    piece.setAngle((float)Math.PI / 5);
    mirrorPiece.setAngle((float)Math.PI / 5);
    assertPointsEqual("setAngle", mirrorPiece.getPoints(), piece.getPoints());

    piece.getPoints();
    piece.setWidthInPlan(90);
    mirrorPiece.setWidthInPlan(90);
    assertPointsEqual("setWidthInPlan", mirrorPiece.getPoints(), piece.getPoints());

    piece.getPoints();
    piece.setDepthInPlan(70);
    mirrorPiece.setDepthInPlan(70);
    assertPointsEqual("setDepthInPlan", mirrorPiece.getPoints(), piece.getPoints());
  }

  /**
   * A clone shares nothing live with its source: changing one must not reach the points
   * the other answers, whichever was asked for its points first. A piece read back from
   * a stream starts with no cache and has to answer and follow changes like its source.
   */
  public void testPiecePointsSurviveCloneAndSerialization() throws Exception {
    HomePieceOfFurniture piece = new HomePieceOfFurniture(createCatalogPiece());
    piece.setX(100);
    piece.getPoints();

    HomePieceOfFurniture clone = piece.clone();
    float [][] clonePoints = clone.getPoints();
    piece.setX(300);
    assertPointsEqual("clone after source change", clonePoints, clone.getPoints());
    clone.setY(200);
    HomePieceOfFurniture mirrorPiece = new HomePieceOfFurniture(createCatalogPiece());
    mirrorPiece.setX(300);
    assertPointsEqual("source after clone change", mirrorPiece.getPoints(), piece.getPoints());

    piece.getPoints();
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ObjectOutputStream out = new ObjectOutputStream(bytes);
    out.writeObject(piece);
    out.close();
    ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    HomePieceOfFurniture readPiece = (HomePieceOfFurniture)in.readObject();
    in.close();
    assertPointsEqual("piece read back", piece.getPoints(), readPiece.getPoints());
    readPiece.getPoints();
    readPiece.setX(400);
    mirrorPiece.setX(400);
    assertPointsEqual("piece read back then changed", mirrorPiece.getPoints(), readPiece.getPoints());
  }

  /**
   * The array handed out by <code>getPoints</code> belongs to its caller: writing to it
   * must not reach what the piece answers later.
   */
  public void testPiecePointsHandedOutDontShareTheCache() {
    HomePieceOfFurniture piece = new HomePieceOfFurniture(createCatalogPiece());
    float [][] wreckedPoints = piece.getPoints();
    float [][] snapshot = piece.getPoints();
    assertCopiesIndependent(wreckedPoints, snapshot);
    wreckPoints(wreckedPoints);
    assertPointsEqual("points after a handed-out write", snapshot, piece.getPoints());
  }

  /**
   * The corners of the box around a dimension line follow each of its ends, its offset,
   * and the elevations and pitch making it an elevation dimension line.
   */
  public void testDimensionLinePointsFollowEachProperty() {
    DimensionLine dimensionLine = new DimensionLine(0, 0, 100, 0, 20);

    dimensionLine.getPoints();
    dimensionLine.setXStart(10);
    assertPointsEqual("setXStart", new DimensionLine(10, 0, 100, 0, 20).getPoints(),
        dimensionLine.getPoints());

    dimensionLine.getPoints();
    dimensionLine.setYStart(5);
    assertPointsEqual("setYStart", new DimensionLine(10, 5, 100, 0, 20).getPoints(),
        dimensionLine.getPoints());

    dimensionLine.getPoints();
    dimensionLine.setXEnd(200);
    assertPointsEqual("setXEnd", new DimensionLine(10, 5, 200, 0, 20).getPoints(),
        dimensionLine.getPoints());

    dimensionLine.getPoints();
    dimensionLine.setYEnd(60);
    assertPointsEqual("setYEnd", new DimensionLine(10, 5, 200, 60, 20).getPoints(),
        dimensionLine.getPoints());

    dimensionLine.getPoints();
    dimensionLine.setOffset(-40);
    assertPointsEqual("setOffset", new DimensionLine(10, 5, 200, 60, -40).getPoints(),
        dimensionLine.getPoints());

    // Collapse the line on its start and raise its end to make an elevation dimension
    // line, whose points follow its pitch, one setter at a time
    dimensionLine.getPoints();
    dimensionLine.setXEnd(10);
    dimensionLine.getPoints();
    dimensionLine.setYEnd(5);
    dimensionLine.getPoints();
    dimensionLine.setElevationEnd(250);
    assertPointsEqual("setElevationEnd", new DimensionLine(10, 5, 0, 10, 5, 250, -40).getPoints(),
        dimensionLine.getPoints());

    dimensionLine.getPoints();
    dimensionLine.setPitch((float)Math.PI / 2);
    DimensionLine mirrorDimensionLine = new DimensionLine(10, 5, 0, 10, 5, 250, -40);
    mirrorDimensionLine.setPitch((float)Math.PI / 2);
    assertPointsEqual("setPitch", mirrorDimensionLine.getPoints(), dimensionLine.getPoints());

    dimensionLine.getPoints();
    dimensionLine.setElevationStart(50);
    DimensionLine raisedMirror = new DimensionLine(10, 5, 50, 10, 5, 250, -40);
    raisedMirror.setPitch((float)Math.PI / 2);
    assertPointsEqual("setElevationStart", raisedMirror.getPoints(), dimensionLine.getPoints());

    float [][] wreckedPoints = dimensionLine.getPoints();
    float [][] snapshot = dimensionLine.getPoints();
    assertCopiesIndependent(wreckedPoints, snapshot);
    wreckPoints(wreckedPoints);
    assertPointsEqual("points after a handed-out write", snapshot, dimensionLine.getPoints());
  }

  /**
   * A dimension line read back from a stream starts with no cache and answers the same
   * points as its source.
   */
  public void testDimensionLinePointsSurviveSerialization() throws Exception {
    DimensionLine dimensionLine = new DimensionLine(0, 0, 100, 0, 20);
    dimensionLine.getPoints();

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ObjectOutputStream out = new ObjectOutputStream(bytes);
    out.writeObject(dimensionLine);
    out.close();
    ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    DimensionLine readDimensionLine = (DimensionLine)in.readObject();
    in.close();

    assertPointsEqual("dimension line read back", dimensionLine.getPoints(),
        readDimensionLine.getPoints());
    readDimensionLine.getPoints();
    readDimensionLine.setOffset(35);
    assertPointsEqual("dimension line read back then changed",
        new DimensionLine(0, 0, 100, 0, 35).getPoints(), readDimensionLine.getPoints());
  }

  /**
   * The corners of the observer camera follow each of its location, yaw and plan scale
   * properties.
   */
  public void testObserverCameraPointsFollowEachProperty() {
    ObserverCamera camera = new ObserverCamera(50, 50, 170, 0, 0, (float)Math.PI / 4);
    ObserverCamera mirrorCamera = new ObserverCamera(50, 50, 170, 0, 0, (float)Math.PI / 4);

    camera.getPoints();
    camera.setX(300);
    mirrorCamera.setX(300);
    assertPointsEqual("setX", mirrorCamera.getPoints(), camera.getPoints());

    camera.getPoints();
    camera.setY(120);
    mirrorCamera.setY(120);
    assertPointsEqual("setY", mirrorCamera.getPoints(), camera.getPoints());

    camera.getPoints();
    camera.setYaw((float)Math.PI / 3);
    mirrorCamera.setYaw((float)Math.PI / 3);
    assertPointsEqual("setYaw", mirrorCamera.getPoints(), camera.getPoints());

    camera.getPoints();
    camera.setPlanScale(2);
    mirrorCamera.setPlanScale(2);
    assertPointsEqual("setPlanScale", mirrorCamera.getPoints(), camera.getPoints());

    float [][] wreckedPoints = camera.getPoints();
    float [][] snapshot = camera.getPoints();
    assertCopiesIndependent(wreckedPoints, snapshot);
    wreckPoints(wreckedPoints);
    assertPointsEqual("points after a handed-out write", snapshot, camera.getPoints());
  }

  private CatalogPieceOfFurniture createCatalogPiece() {
    return new CatalogPieceOfFurniture("Piece", null, null, 60, 40, 80, true, false);
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

  private void assertPointsEqual(String context, float [][] expectedPoints, float [][] points) {
    assertEquals("Wrong number of corners after " + context, expectedPoints.length, points.length);
    for (int i = 0; i < expectedPoints.length; i++) {
      assertEquals("Wrong abscissa at corner " + i + " after " + context,
          expectedPoints [i][0], points [i][0], 1E-3f);
      assertEquals("Wrong ordinate at corner " + i + " after " + context,
          expectedPoints [i][1], points [i][1], 1E-3f);
    }
  }
}
