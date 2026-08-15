/*
 * PlanControllerMagnetismTest.java
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

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Properties;

import javax.swing.undo.UndoableEditSupport;

import junit.framework.TestCase;

import com.eteks.sweethome3d.io.DefaultUserPreferences;
import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.TextStyle;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.viewcontroller.PlanController;
import com.eteks.sweethome3d.viewcontroller.PlanView;
import com.eteks.sweethome3d.viewcontroller.View;
import com.eteks.sweethome3d.viewcontroller.ViewFactory;
import com.eteks.sweethome3d.viewcontroller.ViewFactoryAdapter;

/**
 * Tests how a dragged piece of furniture magnetizes to walls and to other furniture,
 * against a view stub needing no screen. These drags cross the search for nearby walls
 * and pieces, so they pin its answers whatever shortcuts the search takes.
 * @author Dawid Laszuk
 */
public class PlanControllerMagnetismTest extends TestCase {
  /**
   * A piece dropped close to a wall side must land flush against it.
   */
  public void testPieceMagnetizesToWall() {
    Home home = new Home();
    UserPreferences preferences = new DefaultUserPreferences();
    home.addWall(new Wall(0, 0, 300, 0, 10, 250));
    HomePieceOfFurniture piece = createPiece();
    piece.setX(150);
    piece.setY(100);
    home.addPieceOfFurniture(piece);
    PlanController planController = createPlanController(home, preferences);

    // Drag the piece up to 2 cm from the wall side at y = 5, within reach of magnetism
    planController.moveMouse(150, 100);
    planController.pressMouse(150, 100, 1, false, false);
    planController.moveMouse(150, 27);
    planController.releaseMouse(150, 27);

    assertEquals("Piece dropped close to a wall didn't land against its side",
        5 + piece.getDepthInPlan() / 2, piece.getY(), 0.15f);
    assertEquals("Piece magnetized to a straight wall shouldn't turn", 0f, piece.getAngle(), 1E-4f);
  }

  /**
   * Same check against the bulge of an arc wall, far away from the segment between its
   * two ends: a wall search pruned on anything less than the points of the arc would
   * miss it there.
   */
  public void testPieceMagnetizesToArcWallBulge() {
    Home home = new Home();
    UserPreferences preferences = new DefaultUserPreferences();
    Wall arcWall = new Wall(0, 0, 200, 0, 10, 250);
    // A quarter circle arc of radius 200 / (2 * sin(45)) = 141.4 bulging 41.4 cm
    // away from the segment between its ends
    arcWall.setArcExtent((float)Math.PI / 2);
    home.addWall(arcWall);
    HomePieceOfFurniture piece = createPiece();
    piece.setX(100);
    piece.setY(-150);
    home.addPieceOfFurniture(piece);
    PlanController planController = createPlanController(home, preferences);

    float bulgeY = getMinY(arcWall.getPoints());
    assertTrue("The arc should bulge far away from its chord for this test to check anything",
        bulgeY < -30);
    // Drag the piece up to 2 cm outside the apex of the bulge
    float dropY = bulgeY - 2 - piece.getDepthInPlan() / 2;
    planController.moveMouse(100, -150);
    planController.pressMouse(100, -150, 1, false, false);
    planController.moveMouse(100, dropY);
    planController.releaseMouse(100, dropY);

    assertEquals("Piece dropped close to the bulge of an arc wall didn't land against it",
        bulgeY - piece.getDepthInPlan() / 2, piece.getY(), 0.6f);
  }

  /**
   * A piece dropped close to another one must land against the side the other piece has
   * NOW: its sides are cached, and rotating it in place must not leave a drag snapping
   * along the contour it had before.
   */
  public void testPieceMagnetizesToTheRotatedSideOfAnotherPiece() {
    Home home = new Home();
    UserPreferences preferences = new DefaultUserPreferences();
    // A 60 x 40 piece whose top side lies at y = 100 - 20 = 80
    HomePieceOfFurniture fixedPiece = createPiece();
    fixedPiece.setX(100);
    fixedPiece.setY(100);
    home.addPieceOfFurniture(fixedPiece);
    HomePieceOfFurniture piece = createPiece();
    piece.setX(100);
    piece.setY(300);
    home.addPieceOfFurniture(piece);
    PlanController planController = createPlanController(home, preferences);

    // A first drag against the top side warms whatever the search keeps per piece
    planController.moveMouse(100, 300);
    planController.pressMouse(100, 300, 1, false, false);
    planController.moveMouse(100, 80 - piece.getDepthInPlan() / 2 - 3);
    planController.releaseMouse(100, 80 - piece.getDepthInPlan() / 2 - 3);
    assertEquals("Piece dropped close to another one didn't land against its side",
        80 - piece.getDepthInPlan() / 2, piece.getY(), 0.15f);

    // Once the fixed piece is rotated a quarter turn, its top side rises to
    // y = 100 - 30 = 70, and the same drag must land 10 cm higher than before
    fixedPiece.setAngle((float)Math.PI / 2);
    planController.moveMouse(100, piece.getY());
    planController.pressMouse(100, piece.getY(), 1, false, false);
    planController.moveMouse(100, 70 - piece.getDepthInPlan() / 2 - 3);
    planController.releaseMouse(100, 70 - piece.getDepthInPlan() / 2 - 3);
    assertEquals("Piece dropped close to a piece rotated after a previous drag "
            + "landed along its old side",
        70 - piece.getDepthInPlan() / 2, piece.getY(), 0.15f);
  }

  /**
   * A wall holding a NaN coordinate can't be compared to anything, so it must neither
   * break a drag nor rob a comparable wall of its magnetism.
   */
  public void testMagnetismSurvivesAWallWithNaNCoordinates() {
    Home home = new Home();
    UserPreferences preferences = new DefaultUserPreferences();
    home.addWall(new Wall(Float.NaN, Float.NaN, Float.NaN, Float.NaN, 10, 250));
    home.addWall(new Wall(0, 0, 300, 0, 10, 250));
    HomePieceOfFurniture piece = createPiece();
    piece.setX(150);
    piece.setY(100);
    home.addPieceOfFurniture(piece);
    PlanController planController = createPlanController(home, preferences);

    planController.moveMouse(150, 100);
    planController.pressMouse(150, 100, 1, false, false);
    planController.moveMouse(150, 27);
    planController.releaseMouse(150, 27);

    assertEquals("A wall with NaN coordinates robbed a comparable wall of its magnetism",
        5 + piece.getDepthInPlan() / 2, piece.getY(), 0.15f);
  }

  /**
   * The box test pruning the magnetism searches must fail open: points it can't compare,
   * like NaN coordinates read from a home file, have to stay in and reach the exact
   * geometry tests, wherever the box lies. The drag above proves a NaN wall doesn't
   * break editing, but such a wall is filtered out earlier on its NaN length, so the
   * contract of the box test itself is checked here directly.
   */
  public void testBoundsTestKeepsUncomparablePoints() throws Exception {
    Method intersectsBounds = PlanController.class.getDeclaredMethod("intersectsBounds",
        float [][].class, float.class, float.class, float.class, float.class, float.class);
    intersectsBounds.setAccessible(true);

    float [][] nanPoints = {{Float.NaN, Float.NaN}, {Float.NaN, Float.NaN}};
    assertTrue("Points with NaN coordinates were pruned",
        (Boolean)intersectsBounds.invoke(null, nanPoints, 0f, 0f, 0f, 10f, 10f));
    // A NaN abscissa can't rule anything out, so as long as the ordinates overlap the
    // box the points must stay in; ordinates provably outside may still prune them
    float [][] halfNaNPoints = {{Float.NaN, 5}, {Float.NaN, 8}};
    assertTrue("Points with a NaN abscissa and overlapping ordinates were pruned",
        (Boolean)intersectsBounds.invoke(null, halfNaNPoints, 0f, 0f, 0f, 10f, 10f));
    float [][] farPoints = {{100, 100}, {110, 110}};
    assertFalse("Points far outside the box weren't pruned",
        (Boolean)intersectsBounds.invoke(null, farPoints, 0f, 0f, 0f, 10f, 10f));
    float [][] touchingPoints = {{10, 10}, {20, 20}};
    assertTrue("Points touching the corner of the box were pruned",
        (Boolean)intersectsBounds.invoke(null, touchingPoints, 0f, 0f, 0f, 10f, 10f));
    float [][] marginPoints = {{12, 12}, {20, 20}};
    assertTrue("Points reaching the box through their margin were pruned",
        (Boolean)intersectsBounds.invoke(null, marginPoints, 2f, 0f, 0f, 10f, 10f));
  }

  private HomePieceOfFurniture createPiece() {
    return new HomePieceOfFurniture(new CatalogPieceOfFurniture(
        "Piece", null, null, 60, 40, 80, true, false));
  }

  private float getMinY(float [][] points) {
    float minY = points [0][1];
    for (float [] point : points) {
      minY = Math.min(minY, point [1]);
    }
    return minY;
  }

  private PlanController createPlanController(Home home, UserPreferences preferences) {
    ViewFactory viewFactory = new ViewFactoryAdapter() {
        @Override
        public PlanView createPlanView(Home home, UserPreferences preferences,
                                       PlanController planController) {
          return new NoOpPlanView();
        }
      };
    return new PlanController(home, preferences, viewFactory, null, new UndoableEditSupport());
  }

  /**
   * A plan view for a controller under test: everything is a no-op, coordinates map one
   * to one and the scale is 1, so controller coordinates read as plan coordinates.
   */
  private static class NoOpPlanView implements PlanView {
    public void setRectangleFeedback(float x0, float y0, float x1, float y1) {
    }

    public void makeSelectionVisible() {
    }

    public void makePointVisible(float x, float y) {
    }

    public float getScale() {
      return 1;
    }

    public void setScale(float scale) {
    }

    public float getPrintPreferredScale(float preferredWidth, float preferredHeight) {
      return 1;
    }

    public void moveView(float dx, float dy) {
    }

    public float convertXPixelToModel(int x) {
      return x;
    }

    public float convertYPixelToModel(int y) {
      return y;
    }

    public int convertXModelToScreen(float x) {
      return (int)x;
    }

    public int convertYModelToScreen(float y) {
      return (int)y;
    }

    public float getPixelLength() {
      return 1;
    }

    public float [][] getTextBounds(String text, TextStyle style, float x, float y, float angle) {
      return new float [][] {{x, y}, {x + 1, y}, {x + 1, y + 1}, {x, y + 1}};
    }

    public void setCursor(CursorType cursorType) {
    }

    public void setToolTipFeedback(String toolTipFeedback, float x, float y) {
    }

    public void setToolTipEditedProperties(PlanController.EditableProperty [] toolTipEditedProperties,
                                           Object [] toolTipPropertyValues, float x, float y) {
    }

    public void setToolTipEditedPropertyValue(PlanController.EditableProperty toolTipEditedProperty,
                                              Object toolTipPropertyValue) {
    }

    public void deleteToolTipFeedback() {
    }

    public void setResizeIndicatorVisible(boolean resizeIndicatorVisible) {
    }

    public void setAlignmentFeedback(Class<? extends Selectable> alignedObjectClass,
                                     Selectable alignedObject, float x, float y,
                                     boolean showPoint) {
    }

    public void setAngleFeedback(float xCenter, float yCenter, float x1, float y1,
                                 float x2, float y2) {
    }

    public void setDraggedItemsFeedback(List<Selectable> draggedItems) {
    }

    public void setDimensionLinesFeedback(List<DimensionLine> dimensionLines) {
    }

    public void deleteFeedback() {
    }

    public View getHorizontalRuler() {
      return null;
    }

    public View getVerticalRuler() {
      return null;
    }

    public boolean canImportDraggedItems(List<Selectable> items, int x, int y) {
      return false;
    }

    public float [] getPieceOfFurnitureSizeInPlan(HomePieceOfFurniture piece) {
      return null;
    }

    public boolean isFurnitureSizeInPlanSupported() {
      return false;
    }

    public Object createTransferData(DataType dataType) {
      return null;
    }

    public boolean isFormatTypeSupported(FormatType formatType) {
      return false;
    }

    public void exportData(OutputStream out, FormatType formatType, Properties settings)
        throws IOException {
    }
  }
}
