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
   * checked one at a time. Every expected object is built cold, from scratch, so that a
   * setter which forgot to drop the cache can't leave the same stale points on both
   * sides of the comparison.
   */
  public void testPiecePointsFollowEachProperty() {
    HomePieceOfFurniture piece = new HomePieceOfFurniture(createCatalogPiece());

    piece.getPoints();
    piece.setX(100);
    // A piece built from the catalog starts at (width / 2, depth / 2) = (30, 20)
    assertPointsEqual("setX", coldPiece(100, 20, 0, 60, 40).getPoints(), piece.getPoints());

    piece.getPoints();
    piece.setY(50);
    assertPointsEqual("setY", coldPiece(100, 50, 0, 60, 40).getPoints(), piece.getPoints());

    piece.getPoints();
    piece.setAngle((float)Math.PI / 5);
    assertPointsEqual("setAngle",
        coldPiece(100, 50, (float)Math.PI / 5, 60, 40).getPoints(), piece.getPoints());

    piece.getPoints();
    piece.setWidthInPlan(90);
    assertPointsEqual("setWidthInPlan",
        coldPiece(100, 50, (float)Math.PI / 5, 90, 40).getPoints(), piece.getPoints());

    piece.getPoints();
    piece.setDepthInPlan(70);
    assertPointsEqual("setDepthInPlan",
        coldPiece(100, 50, (float)Math.PI / 5, 90, 70).getPoints(), piece.getPoints());
  }

  /**
   * Returns a piece in the given configuration whose points were never asked for.
   */
  private HomePieceOfFurniture coldPiece(float x, float y, float angle,
                                         float widthInPlan, float depthInPlan) {
    HomePieceOfFurniture piece = new HomePieceOfFurniture(createCatalogPiece());
    piece.setX(x);
    piece.setY(y);
    piece.setAngle(angle);
    piece.setWidthInPlan(widthInPlan);
    piece.setDepthInPlan(depthInPlan);
    return piece;
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
    assertPointsEqual("source after clone change",
        coldPiece(300, 20, 0, 60, 40).getPoints(), piece.getPoints());

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
    assertPointsEqual("piece read back then changed",
        coldPiece(400, 20, 0, 60, 40).getPoints(), readPiece.getPoints());
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

    // Collapse the line on its start and give it a pitch while it is still planar, so
    // that raising its end elevation flips it to an elevation dimension line whose
    // points turn by that pitch: a setElevationEnd which kept stale points would then
    // answer the planar geometry
    dimensionLine.getPoints();
    dimensionLine.setXEnd(10);
    dimensionLine.getPoints();
    dimensionLine.setYEnd(5);
    dimensionLine.setPitch((float)Math.PI / 2);
    dimensionLine.getPoints();
    dimensionLine.setElevationEnd(250);
    assertPointsEqual("setElevationEnd",
        coldDimensionLine(10, 5, 0, 10, 5, 250, -40, (float)Math.PI / 2).getPoints(),
        dimensionLine.getPoints());

    // The pitch now drives the points, so changing it must move them
    dimensionLine.getPoints();
    dimensionLine.setPitch((float)Math.PI / 4);
    assertPointsEqual("setPitch",
        coldDimensionLine(10, 5, 0, 10, 5, 250, -40, (float)Math.PI / 4).getPoints(),
        dimensionLine.getPoints());

    // Raising the start elevation to the end one flips the line back to its planar
    // geometry, which a stale cache would miss
    dimensionLine.getPoints();
    dimensionLine.setElevationStart(250);
    assertPointsEqual("setElevationStart",
        coldDimensionLine(10, 5, 250, 10, 5, 250, -40, (float)Math.PI / 4).getPoints(),
        dimensionLine.getPoints());

    float [][] wreckedPoints = dimensionLine.getPoints();
    float [][] snapshot = dimensionLine.getPoints();
    assertCopiesIndependent(wreckedPoints, snapshot);
    wreckPoints(wreckedPoints);
    assertPointsEqual("points after a handed-out write", snapshot, dimensionLine.getPoints());
  }

  /**
   * Returns a dimension line in the given configuration whose points were never asked for.
   */
  private DimensionLine coldDimensionLine(float xStart, float yStart, float elevationStart,
                                          float xEnd, float yEnd, float elevationEnd,
                                          float offset, float pitch) {
    DimensionLine dimensionLine = new DimensionLine(xStart, yStart, elevationStart,
        xEnd, yEnd, elevationEnd, offset);
    dimensionLine.setPitch(pitch);
    return dimensionLine;
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

    camera.getPoints();
    camera.setX(300);
    assertPointsEqual("setX",
        coldCamera(300, 50, 170, 0, 1, false).getPoints(), camera.getPoints());

    camera.getPoints();
    camera.setY(120);
    assertPointsEqual("setY",
        coldCamera(300, 120, 170, 0, 1, false).getPoints(), camera.getPoints());

    camera.getPoints();
    camera.setYaw((float)Math.PI / 3);
    assertPointsEqual("setYaw",
        coldCamera(300, 120, 170, (float)Math.PI / 3, 1, false).getPoints(), camera.getPoints());

    camera.getPoints();
    camera.setPlanScale(2);
    assertPointsEqual("setPlanScale",
        coldCamera(300, 120, 170, (float)Math.PI / 3, 2, false).getPoints(), camera.getPoints());

    // The camera is drawn scaled after the height of its eyes, so its z drives its
    // corners too, until a fixed size is requested
    camera.getPoints();
    camera.setZ(250);
    assertPointsEqual("setZ",
        coldCamera(300, 120, 250, (float)Math.PI / 3, 2, false).getPoints(), camera.getPoints());

    camera.getPoints();
    camera.setFixedSize(true);
    assertPointsEqual("setFixedSize",
        coldCamera(300, 120, 250, (float)Math.PI / 3, 2, true).getPoints(), camera.getPoints());

    float [][] wreckedPoints = camera.getPoints();
    float [][] snapshot = camera.getPoints();
    assertCopiesIndependent(wreckedPoints, snapshot);
    wreckPoints(wreckedPoints);
    assertPointsEqual("points after a handed-out write", snapshot, camera.getPoints());
  }

  /**
   * Returns a camera in the given configuration whose points were never asked for.
   */
  private ObserverCamera coldCamera(float x, float y, float z, float yaw,
                                    float planScale, boolean fixedSize) {
    ObserverCamera camera = new ObserverCamera(x, y, z, yaw, 0, (float)Math.PI / 4);
    camera.setPlanScale(planScale);
    camera.setFixedSize(fixedSize);
    return camera;
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
