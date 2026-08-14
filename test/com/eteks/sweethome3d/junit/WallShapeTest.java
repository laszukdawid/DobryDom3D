/*
 * WallShapeTest.java
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

import com.eteks.sweethome3d.model.Baseboard;
import com.eteks.sweethome3d.model.Wall;

/**
 * Tests that the shape a wall answers hit tests with matches the points it reports, whichever
 * variant is asked for first and whatever happens to the walls joined to it. A wall caches both,
 * and the two have to be invalidated together.
 * @author Dawid Laszuk
 */
public class WallShapeTest extends TestCase {
  private static final float BASEBOARD_THICKNESS = 20;
  private static final float WALL_THICKNESS = 10;

  /**
   * A wall keeps a shape with baseboards and a shape without, so that whichever is asked for
   * first doesn't decide the answer for the other.
   */
  public void testBaseboardShapeIsIndependentOfCallOrder() {
    // A horizontal wall covers y in [-5, 5]; its baseboards take it out to y in [-25, 25],
    // so y = 15 is outside the wall and inside the baseboard.
    float x = 100;
    float y = WALL_THICKNESS / 2 + BASEBOARD_THICKNESS / 2;

    Wall plainFirst = createWallWithBaseboards();
    assertFalse("Point beyond the wall shouldn't be contained without baseboards",
        plainFirst.containsPoint(x, y, false, 0));
    assertTrue("Point over the baseboard should be contained with baseboards",
        plainFirst.containsPoint(x, y, true, 0));

    Wall baseboardsFirst = createWallWithBaseboards();
    assertTrue("Point over the baseboard should be contained with baseboards",
        baseboardsFirst.containsPoint(x, y, true, 0));
    assertFalse("Point beyond the wall shouldn't be contained without baseboards",
        baseboardsFirst.containsPoint(x, y, false, 0));
  }

  /**
   * <code>intersectsRectangle</code> asks for the shape without baseboards, so it must not be
   * answered with a shape built for baseboards by an earlier call.
   */
  public void testRectangleIntersectionIgnoresBaseboards() {
    Wall wall = createWallWithBaseboards();
    // Populate the baseboard shape first.
    wall.containsPoint(100, WALL_THICKNESS / 2 + BASEBOARD_THICKNESS / 2, true, 0);

    // A rectangle over the baseboard only, clear of the wall itself.
    float y0 = WALL_THICKNESS / 2 + 1;
    float y1 = WALL_THICKNESS / 2 + BASEBOARD_THICKNESS;
    assertFalse("A rectangle over the baseboard alone shouldn't intersect the wall",
        wall.intersectsRectangle(90, y0, 110, y1));
  }

  /**
   * The corners of a wall are mitred against the walls joined to it, so moving one wall reshapes
   * its neighbours. Their cached shape has to go with their cached points.
   */
  public void testJoinedWallShapeFollowsAMovedNeighbour() {
    Wall movedWall = new Wall(0, 0, 200, 0, WALL_THICKNESS, 250);
    Wall joinedWall = new Wall(200, 0, 200, 200, WALL_THICKNESS, 250);
    movedWall.setWallAtEnd(joinedWall);
    joinedWall.setWallAtStart(movedWall);

    // Reads the joined wall's shape, and so caches it, before anything moves.
    float x = 203;
    float y = -3;
    joinedWall.containsPoint(x, y, 0);

    movedWall.setXStart(400);
    movedWall.setYStart(-400);

    assertContainsPointLikeAFreshWall(joinedWall, x, y);
  }

  /**
   * Same as above for the neighbour at the other end, and for a wall whose own points changed.
   */
  public void testShapeFollowsBothEndsOfAJoin() {
    Wall movedWall = new Wall(200, 0, 0, 0, WALL_THICKNESS, 250);
    Wall joinedWall = new Wall(200, 0, 200, 200, WALL_THICKNESS, 250);
    movedWall.setWallAtStart(joinedWall);
    joinedWall.setWallAtStart(movedWall);

    float x = 203;
    float y = -3;
    joinedWall.containsPoint(x, y, 0);
    movedWall.containsPoint(x, y, 0);

    movedWall.setXStart(400);
    movedWall.setYStart(-400);

    assertContainsPointLikeAFreshWall(joinedWall, x, y);
    assertContainsPointLikeAFreshWall(movedWall, x, y);
  }

  /**
   * A thickness change reshapes the joins too, and a baseboard change reshapes only the
   * baseboard variant.
   */
  public void testShapeFollowsThicknessAndBaseboardChanges() {
    Wall wall = new Wall(0, 0, 200, 0, WALL_THICKNESS, 250);
    float x = 100;
    float y = 20;

    assertFalse("A thin wall shouldn't reach y = 20", wall.containsPoint(x, y, 0));
    wall.setThickness(80);
    assertTrue("A wall thickened to 80 should reach y = 20", wall.containsPoint(x, y, 0));

    Wall baseboardWall = new Wall(0, 0, 200, 0, WALL_THICKNESS, 250);
    assertFalse("No baseboard yet", baseboardWall.containsPoint(x, y, true, 0));
    baseboardWall.setLeftSideBaseboard(Baseboard.getInstance(BASEBOARD_THICKNESS, 10, null, null));
    baseboardWall.setRightSideBaseboard(Baseboard.getInstance(BASEBOARD_THICKNESS, 10, null, null));
    assertTrue("A baseboard added afterwards should be taken into account",
        baseboardWall.containsPoint(x, y, true, 0));
    assertFalse("The wall itself didn't get any thicker",
        baseboardWall.containsPoint(x, y, false, 0));
  }

  /**
   * The array handed out by <code>getPoints</code> belongs to its caller: writing to it must not
   * reach the cache that the wall answers later calls and hit tests from.
   */
  public void testPointsHandedOutDontShareTheCache() {
    Wall wall = new Wall(0, 0, 200, 0, WALL_THICKNESS, 250);
    float [][] points = wall.getPoints();
    float [][] otherPoints = wall.getPoints();
    assertNotSame("Two calls shared their outer array", points, otherPoints);
    for (int i = 0; i < points.length; i++) {
      assertNotSame("Two calls shared a corner at index " + i, points [i], otherPoints [i]);
    }

    // Wreck the copy, then check the wall is unharmed.
    for (int i = 0; i < points.length; i++) {
      points [i][0] = 100000;
      points [i][1] = 100000;
    }
    for (float [] point : wall.getPoints()) {
      assertTrue("A write to a handed-out array reached the wall's points",
          Math.abs(point [0]) <= 200 && Math.abs(point [1]) <= 200);
    }
    assertTrue("A write to a handed-out array reached the wall's shape",
        wall.containsPoint(100, 0, 0));
  }

  /**
   * <code>Wall</code> is public and not final, and so is <code>getPoints</code>, so the methods
   * which hit test a wall have to keep going through it and keep passing on the argument they
   * were given.
   */
  public void testHitTestingGoesThroughOverriddenPoints() {
    Wall wall = new WallWithOwnPoints();
    assertTrue("An overridden shape isn't used to test whether a point is contained",
        wall.containsPoint(50, 50, 0));
    assertTrue("An overridden shape isn't used to test whether a rectangle intersects",
        wall.intersectsRectangle(40, 40, 60, 60));
    assertTrue("An overridden shape isn't used to test the wall start",
        wall.containsWallStartAt(0, 50, 1));
    assertTrue("An overridden shape isn't used to test the wall end",
        wall.containsWallEndAt(100, 50, 1));

    // A wall with no baseboard set still has to be asked for its baseboard variant, since a
    // subclass may answer something different for it.
    Wall variantWall = new WallWithOwnPoints();
    assertTrue("The baseboard variant of an overridden shape wasn't asked for",
        variantWall.containsPoint(150, 150, true, 0));
  }

  /**
   * The caches are transient, so a wall read back has none and has to build them again.
   */
  public void testWallReadBackAnswersHitTests() throws Exception {
    Wall wall = createWallWithBaseboards();
    float x = 100;
    float y = WALL_THICKNESS / 2 + BASEBOARD_THICKNESS / 2;
    // Fill every cache before writing it out.
    wall.containsPoint(x, y, false, 0);
    wall.containsPoint(x, y, true, 0);

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ObjectOutputStream out = new ObjectOutputStream(bytes);
    out.writeObject(wall);
    out.close();
    ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    Wall readWall = (Wall)in.readObject();
    in.close();

    assertFalse("A wall read back answers the baseboard shape without baseboards",
        readWall.containsPoint(x, y, false, 0));
    assertTrue("A wall read back doesn't answer with its baseboards",
        readWall.containsPoint(x, y, true, 0));
  }

  /**
   * A wall whose points don't come from its own fields, to check the hit tests still ask for them.
   */
  private static class WallWithOwnPoints extends Wall {
    public WallWithOwnPoints() {
      super(0, 0, 10, 0, 2, 250);
    }

    @Override
    public float [][] getPoints(boolean includeBaseboards) {
      return includeBaseboards
          ? new float [][] {{0, 0}, {200, 0}, {200, 200}, {0, 200}}
          : new float [][] {{0, 0}, {100, 0}, {100, 100}, {0, 100}};
    }
  }

  /**
   * Checks that a wall answers <code>containsPoint</code> the way a wall built in the same
   * configuration, with nothing cached, answers it.
   */
  private void assertContainsPointLikeAFreshWall(Wall wall, float x, float y) {
    Wall freshWall = new Wall(wall.getXStart(), wall.getYStart(), wall.getXEnd(), wall.getYEnd(),
        wall.getThickness(), 250);
    Wall wallAtStart = wall.getWallAtStart();
    if (wallAtStart != null) {
      Wall freshWallAtStart = new Wall(wallAtStart.getXStart(), wallAtStart.getYStart(),
          wallAtStart.getXEnd(), wallAtStart.getYEnd(), wallAtStart.getThickness(), 250);
      freshWall.setWallAtStart(freshWallAtStart);
      if (wallAtStart.getWallAtEnd() == wall) {
        freshWallAtStart.setWallAtEnd(freshWall);
      } else {
        freshWallAtStart.setWallAtStart(freshWall);
      }
    }
    Wall wallAtEnd = wall.getWallAtEnd();
    if (wallAtEnd != null) {
      Wall freshWallAtEnd = new Wall(wallAtEnd.getXStart(), wallAtEnd.getYStart(),
          wallAtEnd.getXEnd(), wallAtEnd.getYEnd(), wallAtEnd.getThickness(), 250);
      freshWall.setWallAtEnd(freshWallAtEnd);
      if (wallAtEnd.getWallAtStart() == wall) {
        freshWallAtEnd.setWallAtStart(freshWall);
      } else {
        freshWallAtEnd.setWallAtEnd(freshWall);
      }
    }

    // The points are already invalidated correctly today; the shape is what this checks.
    assertPointsEqual(freshWall.getPoints(), wall.getPoints());
    assertEquals("A wall reshaped by a join answers hit tests with a stale shape",
        freshWall.containsPoint(x, y, 0), wall.containsPoint(x, y, 0));
  }

  private void assertPointsEqual(float [][] expectedPoints, float [][] points) {
    assertEquals("Wrong number of corners", expectedPoints.length, points.length);
    for (int i = 0; i < expectedPoints.length; i++) {
      assertEquals("Wrong abscissa at corner " + i, expectedPoints [i][0], points [i][0], 1E-3f);
      assertEquals("Wrong ordinate at corner " + i, expectedPoints [i][1], points [i][1], 1E-3f);
    }
  }

  private Wall createWallWithBaseboards() {
    Wall wall = new Wall(0, 0, 200, 0, WALL_THICKNESS, 250);
    wall.setLeftSideBaseboard(Baseboard.getInstance(BASEBOARD_THICKNESS, 10, null, null));
    wall.setRightSideBaseboard(Baseboard.getInstance(BASEBOARD_THICKNESS, 10, null, null));
    return wall;
  }
}
