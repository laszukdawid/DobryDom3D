/*
 * HomeXMLRoundTripTest.java 21 aout 2026
 *
 * This file is part of DobryDom3D, a fork of Sweet Home 3D 7.5, modified by DobryDom3D contributors since August 2026.
 *
 * Copyright (c) 2024 Space Mushrooms <info@sweethome3d.com>
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.eteks.sweethome3d.io.DefaultFurnitureCatalog;
import com.eteks.sweethome3d.io.HomeXMLExporter;
import com.eteks.sweethome3d.io.HomeXMLHandler;
import com.eteks.sweethome3d.io.XMLWriter;
import com.eteks.sweethome3d.model.Baseboard;
import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.Compass;
import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.FurnitureCatalog;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeEnvironment;
import com.eteks.sweethome3d.model.HomeEnvironment.DrawingMode;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.ObserverCamera;
import com.eteks.sweethome3d.model.Polyline;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.TextStyle;
import com.eteks.sweethome3d.model.Wall;

import junit.framework.TestCase;

/**
 * Tests that homes survive an {@link HomeXMLExporter} -> {@link HomeXMLHandler}
 * round trip without structural loss.
 * @author DobryDom3D contributors
 */
public class HomeXMLRoundTripTest extends TestCase {
  public void testHomeRoundTripKeepsStructure() throws IOException, SAXException, ParserConfigurationException {
    Home home = createRichHome();
    Home readHome = exportAndReadHome(home);

    // Home attributes
    assertEquals("Different wall height", home.getWallHeight(), readHome.getWallHeight());
    assertEquals("Different home version", home.getVersion(), readHome.getVersion());
    assertEquals("Different home properties", home.getPropertyNames(), readHome.getPropertyNames());
    for (String property : home.getPropertyNames()) {
      assertEquals("Different property " + property,
          home.getProperty(property), readHome.getProperty(property));
    }
    assertEquals("Different furniture visible properties",
        home.getFurnitureVisiblePropertyNames(), readHome.getFurnitureVisiblePropertyNames());
    assertEquals("Selected camera changed",
        home.getCamera() == home.getObserverCamera(),
        readHome.getCamera() == readHome.getObserverCamera());

    // Levels keep their ids, data and selection
    assertEquals("Different levels count", home.getLevels().size(), readHome.getLevels().size());
    for (int i = 0; i < home.getLevels().size(); i++) {
      Level level = home.getLevels().get(i);
      Level readLevel = readHome.getLevels().get(i);
      assertEquals("Different level id", level.getId(), readLevel.getId());
      assertEquals("Different level name", level.getName(), readLevel.getName());
      assertEquals("Different level elevation", level.getElevation(), readLevel.getElevation());
      assertEquals("Different level floor thickness", level.getFloorThickness(), readLevel.getFloorThickness());
      assertEquals("Different level height", level.getHeight(), readLevel.getHeight());
      assertEquals("Different level viewable state", level.isViewable(), readLevel.isViewable());
    }
    assertNotNull("No selected level", readHome.getSelectedLevel());
    assertEquals("Different selected level id",
        home.getSelectedLevel().getId(), readHome.getSelectedLevel().getId());

    // Walls keep their ids, geometry and joins (resolved to the read instances)
    List<Wall> walls = new ArrayList<Wall>(home.getWalls());
    List<Wall> readWalls = new ArrayList<Wall>(readHome.getWalls());
    assertEquals("Different walls count", walls.size(), readWalls.size());
    Wall readWall1 = readWalls.get(0);
    Wall readWall2 = readWalls.get(1);
    Wall readWall3 = readWalls.get(2);
    checkWallEquals(walls.get(0), readWall1);
    checkWallEquals(walls.get(1), readWall2);
    checkWallEquals(walls.get(2), readWall3);
    assertSame("Joined walls not rebound", readWall2.getWallAtStart(), readWall1);
    assertSame("Joined walls not rebound", readWall1.getWallAtEnd(), readWall2);

    // Rooms
    assertEquals("Different rooms count", home.getRooms().size(), readHome.getRooms().size());
    Room room = home.getRooms().get(0);
    Room readRoom = readHome.getRooms().get(0);
    assertTrue("Different room points",
        Arrays.deepEquals(room.getPoints(), readRoom.getPoints()));
    assertEquals("Different room name", room.getName(), readRoom.getName());
    assertEquals("Different room floor color", room.getFloorColor(), readRoom.getFloorColor());
    assertEquals("Different room name style", room.getNameStyle(), readRoom.getNameStyle());

    // Furniture group with nested pieces
    assertEquals("Different furniture count", home.getFurniture().size(), readHome.getFurniture().size());
    HomePieceOfFurniture readGroup = readHome.getFurniture().get(0);
    assertTrue("First piece isn't a group", readGroup instanceof HomeFurnitureGroup);
    HomeFurnitureGroup group = (HomeFurnitureGroup)home.getFurniture().get(0);
    assertEquals("Different group name", group.getName(), readGroup.getName());
    assertEquals("Different group furniture count",
        group.getFurniture().size(), ((HomeFurnitureGroup)readGroup).getFurniture().size());
    for (int i = 0; i < group.getFurniture().size(); i++) {
      HomePieceOfFurniture piece = group.getFurniture().get(i);
      HomePieceOfFurniture readPiece = ((HomeFurnitureGroup)readGroup).getFurniture().get(i);
      assertNotSame("Piece not loaded", piece, readPiece);
      assertEquals("Different piece name", piece.getName(), readPiece.getName());
      assertEquals("Different piece X", piece.getX(), readPiece.getX());
      assertEquals("Different piece Y", piece.getY(), readPiece.getY());
      assertEquals("Different piece angle", piece.getAngle(), readPiece.getAngle());
      assertEquals("Different piece color", piece.getColor(), readPiece.getColor());
      assertEquals("Different piece visibility", piece.isVisible(), readPiece.isVisible());
      assertEquals("Different piece name style", piece.getNameStyle(), readPiece.getNameStyle());
      assertEquals("Different piece properties", piece.getPropertyNames(), readPiece.getPropertyNames());
      for (String property : piece.getPropertyNames()) {
        assertEquals("Different piece property " + property,
            piece.getProperty(property), readPiece.getProperty(property));
      }
    }

    // Dimension lines, labels and polylines
    assertEquals("Different dimension lines count",
        home.getDimensionLines().size(), readHome.getDimensionLines().size());
    DimensionLine dimensionLine = new ArrayList<DimensionLine>(home.getDimensionLines()).get(0);
    DimensionLine readDimensionLine = new ArrayList<DimensionLine>(readHome.getDimensionLines()).get(0);
    assertEquals("Different dimension line offset",
        dimensionLine.getOffset(), readDimensionLine.getOffset());
    assertEquals("Different dimension line start x",
        dimensionLine.getXStart(), readDimensionLine.getXStart());
    Label label = new ArrayList<Label>(home.getLabels()).get(0);
    Label readLabel = new ArrayList<Label>(readHome.getLabels()).get(0);
    assertEquals("Different label text", label.getText(), readLabel.getText());
    assertEquals("Different label style", label.getStyle(), readLabel.getStyle());
    Polyline polyline = home.getPolylines().get(0);
    Polyline readPolyline = readHome.getPolylines().get(0);
    assertTrue("Different polyline points",
        Arrays.deepEquals(polyline.getPoints(), readPolyline.getPoints()));
    assertEquals("Different polyline thickness", polyline.getThickness(), readPolyline.getThickness());
    assertEquals("Different polyline closed path", polyline.isClosedPath(), readPolyline.isClosedPath());

    // Compass
    Compass compass = home.getCompass();
    Compass readCompass = readHome.getCompass();
    assertEquals("Different compass X", compass.getX(), readCompass.getX());
    assertEquals("Different compass Y", compass.getY(), readCompass.getY());
    assertEquals("Different compass diameter", compass.getDiameter(), readCompass.getDiameter());
    assertEquals("Different compass north direction", compass.getNorthDirection(), readCompass.getNorthDirection());
    assertEquals("Different compass visibility", compass.isVisible(), readCompass.isVisible());

    // Environment
    HomeEnvironment environment = home.getEnvironment();
    HomeEnvironment readEnvironment = readHome.getEnvironment();
    assertEquals("Different ground color", environment.getGroundColor(), readEnvironment.getGroundColor());
    assertEquals("Different sky color", environment.getSkyColor(), readEnvironment.getSkyColor());
    assertEquals("Different light color", environment.getLightColor(), readEnvironment.getLightColor());
    assertEquals("Different ceiling light color",
        environment.getCeillingLightColor(), readEnvironment.getCeillingLightColor());
    assertEquals("Different walls alpha", environment.getWallsAlpha(), readEnvironment.getWallsAlpha());
    assertEquals("Different subpart size under light",
        environment.getSubpartSizeUnderLight(), readEnvironment.getSubpartSizeUnderLight());
    assertEquals("Different all levels visible", environment.isAllLevelsVisible(), readEnvironment.isAllLevelsVisible());
    assertEquals("Different drawing mode", environment.getDrawingMode(), readEnvironment.getDrawingMode());

    // Cameras
    ObserverCamera observerCamera = home.getObserverCamera();
    ObserverCamera readObserverCamera = readHome.getObserverCamera();
    assertEquals("Different observer camera X", observerCamera.getX(), readObserverCamera.getX());
    assertEquals("Different observer camera Y", observerCamera.getY(), readObserverCamera.getY());
    assertEquals("Different observer camera Z", observerCamera.getZ(), readObserverCamera.getZ());
    assertEquals("Different observer camera yaw", observerCamera.getYaw(), readObserverCamera.getYaw());
    assertEquals("Different observer camera pitch", observerCamera.getPitch(), readObserverCamera.getPitch());
    assertEquals("Different observer camera field of view",
        observerCamera.getFieldOfView(), readObserverCamera.getFieldOfView());
    assertEquals("Different stored cameras count",
        home.getStoredCameras().size(), readHome.getStoredCameras().size());
    Camera storedCamera = home.getStoredCameras().get(0);
    Camera readStoredCamera = readHome.getStoredCameras().get(0);
    assertEquals("Different stored camera position",
        storedCamera.getX() + "@" + storedCamera.getZ(),
        readStoredCamera.getX() + "@" + readStoredCamera.getZ());
  }

  /**
   * Round trips twice: exporting the parsed home must give the same XML again,
   * which catches nondeterministic export ordering.
   */
  public void testExportIsStableAcrossRoundTrips() throws IOException, SAXException, ParserConfigurationException {
    String firstXml = exportToXml(createRichHome());
    Home parsedHome = parseHome(firstXml);
    String secondXml = exportToXml(parsedHome);
    assertEquals("XML changed after a round trip", firstXml, secondXml);
  }

  /**
   * Tests malformed XML is rejected instead of silently producing a partial home.
   */
  public void testMalformedXmlIsRejected() throws IOException, ParserConfigurationException {
    try {
      parseHome("<?xml version='1.0'?><home version='650'><wall>");
      fail("Malformed XML should be rejected");
    } catch (SAXException ex) {
      // Expected failure
    }
  }

  /**
   * Tests missing required attributes are rejected.
   */
  public void testMissingRequiredAttributesAreRejected() throws IOException, SAXException, ParserConfigurationException {
    // A wall requires at least its thickness, x/y start and end coordinates
    try {
      parseHome("<?xml version='1.0'?><home version='650'><wall id='wall1'/></home>");
      fail("Wall without required attributes should be rejected");
    } catch (SAXException ex) {
      // Expected failure
    }
    // Invalid attribute values are rejected too
    try {
      parseHome("<?xml version='1.0'?><home wallHeight='not-a-float'/>");
      fail("Invalid float attribute should be rejected");
    } catch (SAXException ex) {
      // Expected failure
    }
  }

  /**
   * Returns a home covering the model features targeted by the XML format:
   * levels, joined walls, room, furniture group, dimension line, label,
   * polyline, compass, environment and cameras.
   */
  private Home createRichHome() {
    Home home = new Home(280f);
    home.setProperty("id", "home1");
    home.setProperty("comment", "unicode \u00e9\u00e8 & <special> \"quote\"");
    home.setFurnitureVisiblePropertyNames(Arrays.asList("name", "width"));

    Level level0 = new Level("Ground", 0f, 2f, 250f);
    Level level1 = new Level("Upper", 252f, 10f, 250f);
    level1.setViewable(false);
    home.addLevel(level0);
    home.addLevel(level1);
    home.setSelectedLevel(level0);

    Wall wall1 = new Wall(-100, -100, 100, -100, 12, 280f);
    wall1.setHeightAtEnd(240f);
    wall1.setLeftSideColor(0xFF0000);
    wall1.setRightSideColor(0x00FF00);
    wall1.setLeftSideBaseboard(new Baseboard(1.5f, 7f, 0xCCCCCC, null));
    wall1.setLevel(level0);
    Wall wall2 = new Wall(100, -100, 100, 100, 8, Float.NaN);
    wall2.setArcExtent((float)Math.PI / 4);
    wall2.setTopColor(0x123456);
    wall2.setLevel(level0);
    wall2.setWallAtStart(wall1);
    wall1.setWallAtEnd(wall2);
    Wall wall3 = new Wall(-100, -100, -100, 100, 10, 260f);
    wall3.setLevel(level1);
    home.addWall(wall1);
    home.addWall(wall2);
    home.addWall(wall3);

    Room room = new Room(new float [][] {{-100, -100}, {100, -100}, {100, 100}, {-100, 100}});
    room.setName("Living room");
    room.setFloorColor(0xEAEAEA);
    room.setNameStyle(new TextStyle(14, true, false));
    room.setLevel(level0);
    home.addRoom(room);

    FurnitureCatalog catalog = new DefaultFurnitureCatalog();
    HomePieceOfFurniture piece1 = new HomePieceOfFurniture(
        catalog.getCategories().get(0).getFurniture().get(0));
    piece1.setX(10);
    piece1.setY(20);
    piece1.setAngle((float)Math.PI / 6);
    piece1.setColor(0x0000FF);
    piece1.setVisible(false);
    piece1.setNameVisible(true);
    piece1.setNameStyle(new TextStyle(11, true, true));
    piece1.setProperty("id", "piece1");
    piece1.setProperty("note", "caf\u00e9 <&> \"quoted\"");
    piece1.setLevel(level0);
    HomePieceOfFurniture piece2 = new HomePieceOfFurniture(
        catalog.getCategories().get(1).getFurniture().get(0));
    piece2.setX(30);
    piece2.setY(40);
    piece2.setProperty("id", "piece2");
    piece2.setLevel(level1);
    HomeFurnitureGroup group = new HomeFurnitureGroup(Arrays.asList(piece1, piece2), "My group");
    home.addPieceOfFurniture(group);

    DimensionLine dimensionLine = new DimensionLine(-100, -120, 100, -120, 15);
    dimensionLine.setLengthStyle(new TextStyle(9, false, true));
    dimensionLine.setLevel(level0);
    home.addDimensionLine(dimensionLine);

    Label label = new Label("<Special> & text", 5, 5);
    label.setStyle(new TextStyle(18, false, false));
    label.setLevel(level0);
    home.addLabel(label);

    Polyline polyline = new Polyline(new float [][] {{0, 0}, {30, 40}, {70, 80}});
    polyline.setThickness(3);
    polyline.setColor(0x0000FF);
    polyline.setClosedPath(true);
    polyline.setLevel(level1);
    home.addPolyline(polyline);

    Compass compass = home.getCompass();
    compass.setX(-150);
    compass.setY(150);
    compass.setDiameter(80);
    compass.setNorthDirection((float)Math.PI / 8);
    compass.setVisible(true);

    HomeEnvironment environment = home.getEnvironment();
    environment.setGroundColor(0xDDDDDD);
    environment.setSkyColor(0xCCCCFF);
    environment.setLightColor(0xFFFFFF);
    environment.setCeillingLightColor(0xEEEEEE);
    environment.setWallsAlpha(0.25f);
    environment.setSubpartSizeUnderLight(8);
    environment.setAllLevelsVisible(false);
    environment.setDrawingMode(DrawingMode.FILL);

    ObserverCamera observerCamera = home.getObserverCamera();
    observerCamera.setX(10);
    observerCamera.setY(-90);
    observerCamera.setZ(170);
    observerCamera.setYaw((float)Math.PI / 2);
    observerCamera.setPitch((float)-Math.PI / 12);
    observerCamera.setFieldOfView((float)Math.PI / 3);

    Camera storedCamera = new Camera(-5, -5, 200, 0, 0, (float)Math.PI / 4);
    home.setStoredCameras(Arrays.asList(storedCamera));
    return home;
  }

  /**
   * Exports the given <code>home</code> in XML and reads it back with {@link HomeXMLHandler}.
   */
  private Home exportAndReadHome(Home home) throws IOException, SAXException, ParserConfigurationException {
    return parseHome(exportToXml(home));
  }

  private String exportToXml(Home home) throws IOException {
    ByteArrayOutputStream xmlOut = new ByteArrayOutputStream();
    XMLWriter xmlWriter = new XMLWriter(xmlOut);
    new HomeXMLExporter().writeElement(xmlWriter, home);
    xmlWriter.flush();
    xmlWriter.close();
    return xmlOut.toString("UTF-8");
  }

  private Home parseHome(String xml) throws IOException, SAXException, ParserConfigurationException {
    HomeXMLHandler handler = new HomeXMLHandler();
    SAXParserFactory.newInstance().newSAXParser().parse(
        new InputSource(new ByteArrayInputStream(xml.getBytes("UTF-8"))), handler);
    return handler.getHome();
  }

  /**
   * Asserts <code>wall1</code> and <code>wall2</code> contain the same data.
   */
  private void checkWallEquals(Wall wall1, Wall wall2) {
    assertNotSame("Wall not loaded", wall1, wall2);
    assertEquals("Different wall id", wall1.getId(), wall2.getId());
    assertEquals("Different X start", wall1.getXStart(), wall2.getXStart());
    assertEquals("Different Y start", wall1.getYStart(), wall2.getYStart());
    assertEquals("Different X end", wall1.getXEnd(), wall2.getXEnd());
    assertEquals("Different Y end", wall1.getYEnd(), wall2.getYEnd());
    assertEquals("Different thickness", wall1.getThickness(), wall2.getThickness());
    assertEquals("Different height", wall1.getHeight(), wall2.getHeight());
    assertEquals("Different height at end", wall1.getHeightAtEnd(), wall2.getHeightAtEnd());
    assertEquals("Different arc extent", wall1.getArcExtent(), wall2.getArcExtent());
    assertEquals("Different left side color", wall1.getLeftSideColor(), wall2.getLeftSideColor());
    assertEquals("Different right side color", wall1.getRightSideColor(), wall2.getRightSideColor());
    assertEquals("Different top color", wall1.getTopColor(), wall2.getTopColor());
    if (wall1.getLevel() == null) {
      assertNull("Unexpected level", wall2.getLevel());
    } else {
      assertEquals("Different wall level id", wall1.getLevel().getId(), wall2.getLevel().getId());
    }
    Baseboard baseboard1 = wall1.getLeftSideBaseboard();
    Baseboard baseboard2 = wall2.getLeftSideBaseboard();
    if (baseboard1 == null) {
      assertNull("Unexpected baseboard", baseboard2);
    } else {
      assertNotNull("Missing baseboard", baseboard2);
      assertEquals("Different baseboard thickness", baseboard1.getThickness(), baseboard2.getThickness());
      assertEquals("Different baseboard height", baseboard1.getHeight(), baseboard2.getHeight());
      assertEquals("Different baseboard color", baseboard1.getColor(), baseboard2.getColor());
    }
  }
}
