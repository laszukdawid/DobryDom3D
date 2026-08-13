/*
 * HomeSelectionTest.java
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import junit.framework.TestCase;

import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.Wall;

/**
 * Tests that the selection of a {@link Home} answers {@link Home#isItemSelected} in agreement
 * with the list it hands out. The two are kept side by side so that painting doesn't have to
 * search the whole selection for each item it draws, which leaves room for them to drift apart.
 * @author Dawid Laszuk
 */
public class HomeSelectionTest extends TestCase {
  public void testSelectionAnswersInAgreementWithItsList() {
    Home home = new Home();
    Wall wall = new Wall(0, 0, 100, 0, 10);
    Label selectedLabel = new Label("Selected", 10, 10);
    Label otherLabel = new Label("Other", 20, 20);
    home.addWall(wall);
    home.addLabel(selectedLabel);
    home.addLabel(otherLabel);

    assertSelectionConsistent(home);
    assertFalse("Nothing is selected yet", home.isItemSelected(wall));

    home.setSelectedItems(Arrays.asList(new Selectable [] {wall, selectedLabel}));
    assertSelectionConsistent(home);
    assertTrue("Wall should be selected", home.isItemSelected(wall));
    assertTrue("Label should be selected", home.isItemSelected(selectedLabel));
    assertFalse("Other label shouldn't be selected", home.isItemSelected(otherLabel));

    home.deselectItem(wall);
    assertSelectionConsistent(home);
    assertFalse("Wall was deselected", home.isItemSelected(wall));
    assertTrue("Label is still selected", home.isItemSelected(selectedLabel));

    home.setSelectedItems(Collections.<Selectable>emptyList());
    assertSelectionConsistent(home);
    assertFalse("Selection was emptied", home.isItemSelected(selectedLabel));
  }

  /**
   * Selected items are moved and resized while they stay selected, which is what a drag does, so
   * the selection has to keep answering for an item whatever happens to its geometry.
   */
  public void testSelectionFollowsAnItemThatMoves() {
    Home home = new Home();
    Wall wall = new Wall(0, 0, 100, 0, 10);
    DimensionLine dimensionLine = new DimensionLine(0, 0, 100, 0, 20);
    home.addWall(wall);
    home.addDimensionLine(dimensionLine);
    home.setSelectedItems(Arrays.asList(new Selectable [] {wall, dimensionLine}));

    wall.setXStart(500);
    wall.setYStart(500);
    dimensionLine.setXStart(500);
    dimensionLine.setXEnd(800);

    assertSelectionConsistent(home);
    assertTrue("A moved wall stopped being selected", home.isItemSelected(wall));
    assertTrue("A resized dimension line stopped being selected", home.isItemSelected(dimensionLine));
  }

  /**
   * The list handed out by <code>getSelectedItems</code> is a view that must keep showing the
   * selection as it was when it was asked for, whatever happens to the selection afterwards.
   */
  public void testHandedOutSelectionIsntChangedByALaterSelection() {
    Home home = new Home();
    Label label = new Label("Label", 10, 10);
    home.addLabel(label);
    home.setSelectedItems(Arrays.asList(new Selectable [] {label}));

    List<Selectable> handedOutSelection = home.getSelectedItems();
    assertEquals("Wrong selection size", 1, handedOutSelection.size());

    home.setSelectedItems(Collections.<Selectable>emptyList());
    assertEquals("The list handed out earlier changed", 1, handedOutSelection.size());
    assertSame("The list handed out earlier changed", label, handedOutSelection.get(0));
    assertTrue("The new selection isn't empty", home.getSelectedItems().isEmpty());
  }

  public void testHandedOutSelectionIsUnmodifiable() {
    Home home = new Home();
    Label label = new Label("Label", 10, 10);
    home.addLabel(label);
    home.setSelectedItems(Arrays.asList(new Selectable [] {label}));
    try {
      home.getSelectedItems().clear();
      fail("The selection handed out should be unmodifiable");
    } catch (UnsupportedOperationException ex) {
      // Expected
    }
  }

  /**
   * A cloned home selects the clones of the selected items, so its own selection has to answer
   * for those clones and not for the items of the home it was cloned from.
   */
  public void testClonedHomeAnswersForItsOwnItems() {
    Home home = new Home();
    Wall wall = new Wall(0, 0, 100, 0, 10);
    Label label = new Label("Label", 10, 10);
    home.addWall(wall);
    home.addLabel(label);
    home.setSelectedItems(Arrays.asList(new Selectable [] {wall, label}));

    Home clonedHome = home.clone();
    assertSelectionConsistent(clonedHome);
    assertEquals("Wrong selection size in the clone", 2, clonedHome.getSelectedItems().size());
    for (Selectable item : clonedHome.getSelectedItems()) {
      assertTrue("The clone doesn't answer for its own selected items", clonedHome.isItemSelected(item));
    }
    assertFalse("The clone answers for the items of the home it came from",
        clonedHome.isItemSelected(wall));
  }

  /**
   * The selection isn't serialized, so a home read back has an empty one that still has to answer.
   */
  public void testDeserializedHomeAnswersAboutItsSelection() throws Exception {
    Home home = new Home();
    Label label = new Label("Label", 10, 10);
    home.addLabel(label);
    home.setSelectedItems(Arrays.asList(new Selectable [] {label}));

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ObjectOutputStream out = new ObjectOutputStream(bytes);
    out.writeObject(home);
    out.close();
    ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    Home readHome = (Home)in.readObject();
    in.close();

    assertSelectionConsistent(readHome);
    Label readLabel = readHome.getLabels().iterator().next();
    assertFalse("A home read back shouldn't hold a selection", readHome.isItemSelected(readLabel));
    readHome.setSelectedItems(Arrays.asList(new Selectable [] {readLabel}));
    assertSelectionConsistent(readHome);
    assertTrue("Selecting in a home read back doesn't answer", readHome.isItemSelected(readLabel));
  }

  /**
   * Checks that <code>isItemSelected</code> answers exactly what a search through the list
   * handed out by <code>getSelectedItems</code> would.
   */
  private void assertSelectionConsistent(Home home) {
    List<Selectable> selectedItems = home.getSelectedItems();
    List<Selectable> allItems = new ArrayList<Selectable>(home.getSelectableViewableItems());
    allItems.addAll(selectedItems);
    for (Selectable item : allItems) {
      assertEquals("isItemSelected disagrees with the selection list for " + item,
          selectedItems.contains(item), home.isItemSelected(item));
    }
  }
}
