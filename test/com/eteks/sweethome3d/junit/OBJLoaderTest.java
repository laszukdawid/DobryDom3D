/*
 * OBJLoaderTest.java 9 Aug 2026
 *
 * Sweet Home 3D, Copyright (c) 2024 Space Mushrooms <info@sweethome3d.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package com.eteks.sweethome3d.junit;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.TreeSet;

import javax.media.j3d.GeometryArray;
import javax.media.j3d.Group;
import javax.media.j3d.Node;
import javax.media.j3d.Shape3D;
import javax.vecmath.Point3f;
import javax.vecmath.TexCoord2f;

import junit.framework.TestCase;

import com.eteks.sweethome3d.j3d.OBJLoader;
import com.sun.j3d.loaders.Scene;

/**
 * Tests that {@link OBJLoader} builds the expected geometry from the elements of an OBJ stream.
 * Each test parses a small in memory OBJ file built on the vertices, texture coordinates and
 * normals declared below, then resolves the coordinates of the loaded geometry back to the
 * indices of the OBJ file, so that a mistake in the index bookkeeping of the parser shows up
 * as a wrong index or a wrong number of corners.
 * @author Dawid Laszuk
 */
public class OBJLoaderTest extends TestCase {
  /**
   * Six distinct vertices forming a convex planar hexagon, so that faces built on any prefix
   * of them are non degenerate and every vertex identifies its index unambiguously.
   */
  private static final float [][] VERTICES = {
      {1, 0, 0}, {0.5f, 1, 0}, {-0.5f, 1, 0}, {-1, 0, 0}, {-0.5f, -1, 0}, {0.5f, -1, 0}};
  private static final float [][] TEXTURE_COORDINATES = {
      {1, 0}, {2, 0}, {3, 0}, {4, 0}, {5, 0}, {6, 0}};
  private static final float [][] NORMALS = {
      {0, 0, 1}, {0, 0, 2}, {0, 0, 3}, {0, 0, 4}, {0, 0, 5}, {0, 0, 6}};

  private static final float EPSILON = 1E-4f;

  /**
   * Tests the four forms a face may take.
   */
  public void testFaceForms() throws IOException {
    assertTriangles("f 1 2 3\n", false, false, "[1//, 2//, 3//]");
    assertTriangles("f 1/2 2/3 3/1\n", true, false, "[1/2/, 2/3/, 3/1/]");
    assertTriangles("f 1//3 2//1 3//2\n", false, true, "[1//3, 2//1, 3//2]");
    assertTriangles("f 1/2/3 2/3/1 3/1/2\n", true, true, "[1/2/3, 2/3/1, 3/1/2]");
  }

  /**
   * Tests quads and n-gons, the second ones requiring more indices than the initial capacity
   * of the buffers used by the parser.
   */
  public void testQuadAndNGon() throws IOException {
    assertPolygons("f 1/1/1 2/2/2 3/3/3 4/4/4\n", true, true,
        6, "[1/1/1, 2/2/2, 3/3/3, 4/4/4]");
    assertPolygons("f 1/1/1 2/2/2 3/3/3 4/4/4 5/5/5 6/6/6\n", true, true,
        12, "[1/1/1, 2/2/2, 3/3/3, 4/4/4, 5/5/5, 6/6/6]");
  }

  /**
   * Tests that relative indices are resolved against the elements read so far, and that each
   * kind of index is resolved against the elements of its own kind.
   */
  public void testRelativeIndices() throws IOException {
    // Relative indices of the second face must not see the vertices of the first one
    StringBuilder objContent = new StringBuilder();
    appendElements(objContent, 0, 3);
    objContent.append("f -3/-3/-3 -2/-2/-2 -1/-1/-1\n");
    appendElements(objContent, 3, 6);
    objContent.append("f -3/-3/-3 -2/-2/-2 -1/-1/-1\n");
    List<Shape3D> shapes = loadShapes(objContent.toString());
    assertEquals("Wrong shape count", 1, shapes.size());
    assertTriangleCorners(shapes.get(0), true, true,
        "[1/1/1, 2/2/2, 3/3/3, 4/4/4, 5/5/5, 6/6/6]");

    // With 6 vertices, 4 texture coordinates and 5 normals, the same relative index resolves
    // to a different absolute index in each list
    objContent = new StringBuilder();
    appendVertices(objContent, 0, 6);
    appendTextureCoordinates(objContent, 0, 4);
    appendNormals(objContent, 0, 5);
    objContent.append("f -3/-3/-3 -2/-2/-2 -1/-1/-1\n");
    shapes = loadShapes(objContent.toString());
    assertEquals("Wrong shape count", 1, shapes.size());
    assertTriangleCorners(shapes.get(0), true, true, "[4/2/3, 5/3/4, 6/4/5]");

    assertTriangles("f 1/1/1 -5/-5/-5 3/3/3\n", true, true, "[1/1/1, 2/2/2, 3/3/3]");
  }

  /**
   * Tests that the indices of an element don't leak into the following ones. A face with fewer
   * vertices than its predecessor, or an element dropped because it has too few vertices, would
   * inherit the indices of the previous face if the parser reused its buffers without resetting
   * them.
   */
  public void testElementsDontLeakIndices() throws IOException {
    // Each face is put in its own group so that its corners are asserted on their own
    List<Shape3D> shapes = loadShapes(getElements()
        + "g wide\n"
        + "f 1/1/1 2/2/2 3/3/3 4/4/4 5/5/5 6/6/6\n"
        + "g narrow\n"
        + "f 1/1/1 2/2/2 3/3/3\n");
    assertEquals("Wrong shape count", 2, shapes.size());
    assertPolygonCorners(shapes.get(0), true, true, 12, "[1/1/1, 2/2/2, 3/3/3, 4/4/4, 5/5/5, 6/6/6]");
    assertTriangleCorners(shapes.get(1), true, true, "[1/1/1, 2/2/2, 3/3/3]");

    // The same faces merged in a single geometry keep their own number of corners
    assertPolygons("f 1/1/1 2/2/2 3/3/3 4/4/4 5/5/5 6/6/6\n"
                 + "f 1/1/1 2/2/2 3/3/3\n", true, true,
        15, "[1/1/1, 2/2/2, 3/3/3, 4/4/4, 5/5/5, 6/6/6]");

    // Faces with fewer than 3 vertices are dropped
    assertTriangles("f 1/1/1 2/2/2\n"
                  + "f 4/4/4 5/5/5 6/6/6\n", true, true, "[4/4/4, 5/5/5, 6/6/6]");
    assertTriangles("f\n"
                  + "f 4/4/4 5/5/5 6/6/6\n", true, true, "[4/4/4, 5/5/5, 6/6/6]");

    // A face declaring texture coordinates or normals for part of its vertices only ignores them
    assertTriangles("f 1/1 2 3\n", false, false, "[1//, 2//, 3//]");
    assertTriangles("f 1//1 2 3\n", false, false, "[1//, 2//, 3//]");
  }

  /**
   * Tests that the geometry of faces separated by group, object and material directives is
   * unaffected by those directives.
   */
  public void testElementsAcrossDirectives() throws IOException {
    List<Shape3D> shapes = loadShapes(getElements()
        + "g first\n"
        + "f 1/1/1 2/2/2 3/3/3\n"
        + "o second\n"
        + "f 4/4/4 5/5/5 6/6/6\n");
    assertEquals("Wrong shape count", 2, shapes.size());
    assertTriangleCorners(shapes.get(0), true, true, "[1/1/1, 2/2/2, 3/3/3]");
    assertTriangleCorners(shapes.get(1), true, true, "[4/4/4, 5/5/5, 6/6/6]");

    // A missing material library is ignored, and the faces of two different materials
    // are split in two shapes
    shapes = loadShapes(getElements()
        + "mtllib missing.mtl\n"
        + "usemtl red\n"
        + "f 1/1/1 2/2/2 3/3/3\n"
        + "usemtl blue\n"
        + "f 4/4/4 5/5/5 6/6/6\n");
    assertEquals("Wrong shape count", 2, shapes.size());
    assertTriangleCorners(shapes.get(0), true, true, "[1/1/1, 2/2/2, 3/3/3]");
    assertTriangleCorners(shapes.get(1), true, true, "[4/4/4, 5/5/5, 6/6/6]");
  }

  /**
   * Tests line elements, which the parser accumulates the same way as faces.
   */
  public void testLines() throws IOException {
    assertLines("l 1 2 3\n", "[1, 2, 3]", null);
    assertLines("l 1/2 2/3 3/1\n", "[1, 2, 3]", "[2, 3, 1]");
    assertLines("l -3 -2 -1\n", "[4, 5, 6]", null);
    // A line declaring texture coordinates for part of its vertices only ignores them all
    assertLines("l 1/1 2 3\n", "[1, 2, 3]", null);
    // Lines with fewer than 2 vertices are dropped and don't leak their indices
    assertLines("l 1\n"
              + "l 4 5 6\n", "[4, 5, 6]", null);
    assertLines("l 1 2 3 4 5 6\n"
              + "l 1 2\n", "[1, 2, 3, 4, 5, 6, 1, 2]", null);
  }

  /**
   * Asserts that the triangular faces of the given OBJ <code>elements</code>, appended to the
   * vertices, texture coordinates and normals of this test, produce a single shape whose sorted
   * corners are <code>expectedCorners</code>.
   */
  private void assertTriangles(String elements,
                               boolean textured,
                               boolean normals,
                               String expectedCorners) throws IOException {
    List<Shape3D> shapes = loadShapes(getElements() + elements);
    assertEquals("Wrong shape count for " + elements, 1, shapes.size());
    assertTriangleCorners(shapes.get(0), textured, normals, expectedCorners);
  }

  /**
   * Asserts that the faces of the given OBJ <code>elements</code> produce a single shape of
   * <code>expectedCornerCount</code> triangle corners using the distinct
   * <code>expectedCorners</code>. Faces with more than 3 vertices are triangulated by the
   * loader, so the number of times each corner is repeated isn't asserted.
   */
  private void assertPolygons(String elements,
                              boolean textured,
                              boolean normals,
                              int expectedCornerCount,
                              String expectedCorners) throws IOException {
    List<Shape3D> shapes = loadShapes(getElements() + elements);
    assertEquals("Wrong shape count for " + elements, 1, shapes.size());
    assertPolygonCorners(shapes.get(0), textured, normals, expectedCornerCount, expectedCorners);
  }

  private void assertTriangleCorners(Shape3D shape,
                                     boolean textured,
                                     boolean normals,
                                     String expectedCorners) {
    List<String> corners = getTriangleCorners(shape, textured, normals);
    Collections.sort(corners);
    assertEquals("Wrong corners", expectedCorners, corners.toString());
  }

  private void assertPolygonCorners(Shape3D shape,
                                    boolean textured,
                                    boolean normals,
                                    int expectedCornerCount,
                                    String expectedCorners) {
    List<String> corners = getTriangleCorners(shape, textured, normals);
    assertEquals("Wrong corner count", expectedCornerCount, corners.size());
    assertEquals("Wrong corners", expectedCorners, new TreeSet<String>(corners).toString());
  }

  /**
   * Asserts that the line elements of the given OBJ <code>elements</code> produce a single shape
   * referencing <code>expectedVertexIndices</code> in that order, along with
   * <code>expectedTextureCoordinateIndices</code> when the shape has texture coordinates.
   */
  private void assertLines(String elements,
                           String expectedVertexIndices,
                           String expectedTextureCoordinateIndices) throws IOException {
    List<Shape3D> shapes = loadShapes(getElements() + elements);
    assertEquals("Wrong shape count for " + elements, 1, shapes.size());
    Shape3D shape = shapes.get(0);
    List<Integer> vertexIndices = new ArrayList<Integer>();
    List<Integer> textureCoordinateIndices = new ArrayList<Integer>();
    Point3f vertex = new Point3f();
    TexCoord2f textureCoordinate = new TexCoord2f();
    for (int i = 0; i < shape.numGeometries(); i++) {
      GeometryArray geometryArray = (GeometryArray)shape.getGeometry(i);
      boolean textured = (geometryArray.getVertexFormat() & GeometryArray.TEXTURE_COORDINATE_2) != 0;
      assertEquals("Wrong texture coordinates for " + elements,
          expectedTextureCoordinateIndices != null, textured);
      for (int j = 0; j < geometryArray.getVertexCount(); j++) {
        geometryArray.getCoordinate(j, vertex);
        vertexIndices.add(indexOf(VERTICES, new float [] {vertex.x, vertex.y, vertex.z}, 0, 3));
        if (textured) {
          geometryArray.getTextureCoordinate(0, j, textureCoordinate);
          textureCoordinateIndices.add(
              indexOf(TEXTURE_COORDINATES, new float [] {textureCoordinate.x, textureCoordinate.y}, 0, 2));
        }
      }
    }
    assertEquals("Wrong vertex indices for " + elements, expectedVertexIndices, vertexIndices.toString());
    if (expectedTextureCoordinateIndices != null) {
      assertEquals("Wrong texture coordinate indices for " + elements,
          expectedTextureCoordinateIndices, textureCoordinateIndices.toString());
    }
  }

  /**
   * Returns the <code>vertex/textureCoordinate/normal</code> indices of each triangle corner of
   * the given <code>shape</code>, resolved from the coordinates of its geometries. Texture
   * coordinates and normals are read only when the parsed file declared some, since the loader
   * generates normals for the faces which don't reference any.
   */
  private List<String> getTriangleCorners(Shape3D shape, boolean textured, boolean normals) {
    List<String> corners = new ArrayList<String>();
    for (int i = 0; i < shape.numGeometries(); i++) {
      GeometryArray geometryArray = (GeometryArray)shape.getGeometry(i);
      int vertexFormat = geometryArray.getVertexFormat();
      assertTrue("Expected an interleaved geometry", (vertexFormat & GeometryArray.INTERLEAVED) != 0);
      assertEquals("Wrong texture coordinates in geometry",
          textured, (vertexFormat & GeometryArray.TEXTURE_COORDINATE_2) != 0);
      float [] vertexData = geometryArray.getInterleavedVertices();
      int vertexCount = geometryArray.getVertexCount();
      // Interleaved arrays store texture coordinates, then colors, then normals, then coordinates
      int stride = vertexData.length / vertexCount;
      int coordinateOffset = stride - 3;
      int normalOffset = coordinateOffset - ((vertexFormat & GeometryArray.NORMALS) != 0 ? 3 : 0);
      for (int j = 0; j < vertexCount; j++) {
        int vertexOffset = j * stride;
        StringBuilder corner = new StringBuilder();
        corner.append(indexOf(VERTICES, vertexData, vertexOffset + coordinateOffset, 3));
        corner.append('/');
        if (textured) {
          corner.append(indexOf(TEXTURE_COORDINATES, vertexData, vertexOffset, 2));
        }
        corner.append('/');
        if (normals) {
          corner.append(indexOf(NORMALS, vertexData, vertexOffset + normalOffset, 3));
        }
        corners.add(corner.toString());
      }
    }
    return corners;
  }

  /**
   * Returns the 1 based index of the row of <code>elements</code> matching the
   * <code>size</code> values stored at <code>offset</code> in <code>values</code>.
   */
  private int indexOf(float [][] elements, float [] values, int offset, int size) {
    for (int i = 0; i < elements.length; i++) {
      boolean matching = true;
      for (int j = 0; j < size && matching; j++) {
        matching = Math.abs(elements [i][j] - values [offset + j]) < EPSILON;
      }
      if (matching) {
        return i + 1;
      }
    }
    StringBuilder unknownValues = new StringBuilder();
    for (int j = 0; j < size; j++) {
      unknownValues.append(j == 0 ? "" : " ").append(values [offset + j]);
    }
    fail("No element matching " + unknownValues);
    return -1;
  }

  private String getElements() {
    StringBuilder objContent = new StringBuilder();
    appendElements(objContent, 0, VERTICES.length);
    return objContent.toString();
  }

  private void appendElements(StringBuilder objContent, int fromIndex, int toIndex) {
    appendVertices(objContent, fromIndex, toIndex);
    appendTextureCoordinates(objContent, fromIndex, toIndex);
    appendNormals(objContent, fromIndex, toIndex);
  }

  private void appendVertices(StringBuilder objContent, int fromIndex, int toIndex) {
    for (int i = fromIndex; i < toIndex; i++) {
      objContent.append("v " + VERTICES [i][0] + " " + VERTICES [i][1] + " " + VERTICES [i][2] + "\n");
    }
  }

  private void appendTextureCoordinates(StringBuilder objContent, int fromIndex, int toIndex) {
    for (int i = fromIndex; i < toIndex; i++) {
      objContent.append("vt " + TEXTURE_COORDINATES [i][0] + " " + TEXTURE_COORDINATES [i][1] + "\n");
    }
  }

  private void appendNormals(StringBuilder objContent, int fromIndex, int toIndex) {
    for (int i = fromIndex; i < toIndex; i++) {
      objContent.append("vn " + NORMALS [i][0] + " " + NORMALS [i][1] + " " + NORMALS [i][2] + "\n");
    }
  }

  private List<Shape3D> loadShapes(String objContent) throws IOException {
    Scene scene = new OBJLoader().load(new StringReader(objContent));
    List<Shape3D> shapes = new ArrayList<Shape3D>();
    collectShapes(scene.getSceneGroup(), shapes);
    return shapes;
  }

  private void collectShapes(Node node, List<Shape3D> shapes) {
    if (node instanceof Shape3D) {
      shapes.add((Shape3D)node);
    } else if (node instanceof Group) {
      for (Enumeration<?> children = ((Group)node).getAllChildren(); children.hasMoreElements(); ) {
        collectShapes((Node)children.nextElement(), shapes);
      }
    }
  }
}
