/*
 * Wall3DGeometryTest.java
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.media.j3d.Geometry;
import javax.media.j3d.GeometryArray;
import javax.media.j3d.Group;
import javax.media.j3d.Node;
import javax.media.j3d.Shape3D;
import javax.vecmath.Point3f;

import junit.framework.TestCase;

import com.eteks.sweethome3d.j3d.Wall3D;
import com.eteks.sweethome3d.model.Baseboard;
import com.eteks.sweethome3d.model.CatalogDoorOrWindow;
import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeDoorOrWindow;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Sash;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.tools.URLContent;

/**
 * Tests the geometry a wall builds for the 3D view, and in particular that the doors and windows
 * which cut through it are the same ones whichever side or baseboard is being built. The set of
 * doors and windows is gathered once per wall update and shared by every side, so this pins what
 * that set has to contain.
 * @author Dawid Laszuk
 */
public class Wall3DGeometryTest extends TestCase {
  private static final float WALL_LENGTH    = 500;
  private static final float WALL_THICKNESS = 10;
  private static final float WALL_HEIGHT    = 250;

  /**
   * A door across a wall cuts a hole in it, so the wall geometry differs from the same wall
   * with no door. This is what makes the rest of these tests mean anything: if a door stopped
   * being taken into account, the geometry would fall back to the plain wall's.
   */
  public void testADoorChangesTheWallGeometry() throws IOException {
    String plainWall = describeWallGeometry(createHome(false, false, false, true, true));
    String walledDoor = describeWallGeometry(createHome(true, false, false, true, true));
    assertFalse("A door across the wall didn't change its geometry", plainWall.equals(walledDoor));
  }

  /**
   * A door inside a furniture group cuts the wall exactly as it would outside one.
   */
  public void testADoorInsideAGroupCutsTheWall() throws IOException {
    String looseDoor = describeWallGeometry(createHome(true, false, false, true, true));
    String groupedDoor = describeWallGeometry(createHome(true, true, false, true, true));
    assertEquals("A door inside a group didn't cut the wall the way a loose one does",
        looseDoor, groupedDoor);
  }

  /**
   * An invisible door, and a door on a level that isn't viewable, cut nothing.
   */
  public void testHiddenDoorsCutNothing() throws IOException {
    String plainWall = describeWallGeometry(createHome(false, false, false, true, true));
    assertEquals("An invisible door still cut the wall",
        plainWall, describeWallGeometry(createHome(true, false, false, false, true)));
    assertEquals("An invisible door inside a group still cut the wall",
        plainWall, describeWallGeometry(createHome(true, true, false, false, true)));

    // Whether the second level is viewable changes the wall's own geometry, so this is compared
    // against a home with the same levels rather than against the one above
    String plainWallBelowHiddenLevel = describeWallGeometry(createHome(false, false, false, true, false));
    assertEquals("A door on a level that isn't viewable still cut the wall",
        plainWallBelowHiddenLevel, describeWallGeometry(createHome(true, false, false, true, false)));
    assertFalse("The wall itself was hidden rather than just the door",
        plainWallBelowHiddenLevel.startsWith("0 "));
  }

  /**
   * Baseboards are built by a second pass over the same doors and windows, so a wall with
   * baseboards and a door has to keep both the baseboard geometry and the hole.
   */
  public void testBaseboardsAreCutByTheSameDoors() throws IOException {
    String baseboardWall = describeWallGeometry(createHome(false, false, true, true, true));
    String baseboardWallWithDoor = describeWallGeometry(createHome(true, false, true, true, true));
    String plainWallWithDoor = describeWallGeometry(createHome(true, false, false, true, true));

    assertFalse("A door didn't change a wall with baseboards",
        baseboardWall.equals(baseboardWallWithDoor));
    assertFalse("Baseboards didn't add anything to a wall with a door",
        plainWallWithDoor.equals(baseboardWallWithDoor));
  }

  /**
   * Building the same home twice gives the same geometry, so the comparisons above are
   * comparing what they mean to.
   */
  public void testGeometryIsReproducible() throws IOException {
    assertEquals("Two builds of the same wall gave different geometry",
        describeWallGeometry(createHome(true, true, true, true, true)),
        describeWallGeometry(createHome(true, true, true, true, true)));
  }

  /**
   * Builds the wall's 3D geometry and describes every vertex it holds, in a form that doesn't
   * depend on the order the geometries were added in.
   */
  private String describeWallGeometry(Home home) {
    Wall wall = home.getWalls().iterator().next();
    Wall3D wall3D = new Wall3D(wall, home, true, true);
    List<String> vertices = new ArrayList<String>();
    collectVertices(wall3D, vertices);
    Collections.sort(vertices);
    StringBuilder description = new StringBuilder(vertices.size() + " vertices\n");
    for (String vertex : vertices) {
      description.append(vertex).append('\n');
    }
    return description.toString();
  }

  private void collectVertices(Node node, List<String> vertices) {
    if (node instanceof Group) {
      Group group = (Group)node;
      for (int i = 0; i < group.numChildren(); i++) {
        collectVertices(group.getChild(i), vertices);
      }
    } else if (node instanceof Shape3D) {
      Shape3D shape = (Shape3D)node;
      for (int i = 0; i < shape.numGeometries(); i++) {
        Geometry geometry = shape.getGeometry(i);
        if (geometry instanceof GeometryArray) {
          GeometryArray geometryArray = (GeometryArray)geometry;
          Point3f vertex = new Point3f();
          for (int j = 0; j < geometryArray.getVertexCount(); j++) {
            geometryArray.getCoordinate(j, vertex);
            vertices.add(String.format("%.3f %.3f %.3f", vertex.x, vertex.y, vertex.z));
          }
        }
      }
    }
  }

  /**
   * Returns a home with one wall, optionally crossed by a door which may sit inside a furniture
   * group, be invisible, or be on a level that isn't viewable, and whose wall may have baseboards.
   */
  private Home createHome(boolean withDoor, boolean doorInGroup, boolean withBaseboards,
                          boolean doorVisible, boolean levelViewable) throws IOException {
    Home home = new Home();
    // Every home gets the same two levels, whether or not it holds a door, so that comparing
    // their geometry compares the door and nothing else. The wall stays on the first, which is
    // always viewable; the door goes on the second, whose viewability is what's under test.
    Level wallLevel = new Level("Wall level", 0, 12, WALL_HEIGHT);
    home.addLevel(wallLevel);
    Level doorLevel = new Level("Door level", 0, 12, WALL_HEIGHT);
    doorLevel.setViewable(levelViewable);
    home.addLevel(doorLevel);
    home.setSelectedLevel(wallLevel);
    Level level = wallLevel;

    Wall wall = new Wall(0, 0, WALL_LENGTH, 0, WALL_THICKNESS, WALL_HEIGHT);
    if (withBaseboards) {
      wall.setLeftSideBaseboard(Baseboard.getInstance(2, 10, null, null));
      wall.setRightSideBaseboard(Baseboard.getInstance(2, 10, null, null));
    }
    wall.setLevel(level);
    home.addWall(wall);

    if (withDoor) {
      HomeDoorOrWindow door = createDoor();
      door.setVisible(doorVisible);
      if (doorInGroup) {
        HomeFurnitureGroup group = new HomeFurnitureGroup(
            Arrays.asList(new HomePieceOfFurniture [] {door}), "Group");
        group.setVisible(doorVisible);
        home.addPieceOfFurniture(group);
        // Home.addPieceOfFurniture puts the piece on the selected level, so move it afterwards
        group.setLevel(doorLevel);
      } else {
        home.addPieceOfFurniture(door);
        door.setLevel(doorLevel);
      }
    }
    return home;
  }

  /**
   * Returns the content of a box shaped Wavefront model, used as the 3D model of the test door:
   * the area its front face covers is what shapes the hole cut in the wall. It's handed over as a
   * <code>URLContent</code> over a file rather than as an arbitrary <code>Content</code>, because
   * a content that isn't a <code>URLContent</code> is first copied to a temporary file, through a
   * path that needs a platform specific application folder.
   */
  private Content createBoxModel() throws IOException {
    if (boxModel == null) {
      String model = "v -0.5 -0.5 -0.5\nv 0.5 -0.5 -0.5\nv 0.5 0.5 -0.5\nv -0.5 0.5 -0.5\n"
          + "v -0.5 -0.5 0.5\nv 0.5 -0.5 0.5\nv 0.5 0.5 0.5\nv -0.5 0.5 0.5\n"
          + "f 1 2 3 4\nf 5 8 7 6\nf 1 5 6 2\nf 2 6 7 3\nf 3 7 8 4\nf 4 8 5 1\n";
      File modelFile = File.createTempFile("wall3DGeometryTest", ".obj");
      modelFile.deleteOnExit();
      OutputStream out = new FileOutputStream(modelFile);
      try {
        out.write(model.getBytes("US-ASCII"));
      } finally {
        out.close();
      }
      boxModel = new URLContent(modelFile.toURI().toURL());
    }
    return boxModel;
  }

  private Content boxModel;

  private HomeDoorOrWindow createDoor() throws IOException {
    CatalogDoorOrWindow catalogDoor = new CatalogDoorOrWindow("door", "Door", null, null, createBoxModel(),
        80, WALL_THICKNESS, 200, 0, true,
        1, 0, new Sash [] {new Sash(0.1f, 0.5f, 0.8f, 0, (float)Math.PI / 2)},
        CatalogPieceOfFurniture.IDENTITY_ROTATION, null,
        true, null, null);
    HomeDoorOrWindow door = new HomeDoorOrWindow(catalogDoor);
    door.setX(WALL_LENGTH / 2);
    door.setY(0);
    door.setWallCutOutOnBothSides(true);
    return door;
  }
}
