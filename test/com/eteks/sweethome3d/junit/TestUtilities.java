/*
 * TestUtilities.java 16 mai 07
 *
 * Copyright (c) 2024 Space Mushrooms <info@sweethome3d.com>
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

import java.awt.Component;
import java.awt.Container;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;

import com.eteks.sweethome3d.tools.OperatingSystem;

import abbot.finder.BasicFinder;
import abbot.finder.ComponentSearchException;
import abbot.finder.Matcher;
import abbot.tester.JComponentTester;

/**
 * Gathers tools used by tests.
 * @author Emmanuel Puybaret
 */
public final class TestUtilities {
  private TestUtilities() {
    // This class isn't instantiable and contains only static methods
  }

  /**
   * Returns a reference to <code>fieldName</code>
   * in a given <code>instance</code> by reflection.
   */
  public static Object getField(Object instance, String fieldName)
      throws NoSuchFieldException, IllegalAccessException {
    Field field = instance.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    return field.get(instance);
  }

  /**
   * Sets the value of the <code>fieldName</code>
   * in a given <code>instance</code> by reflection.
   */
  public static void setField(Object instance, String fieldName, Object value)
      throws NoSuchFieldException, IllegalAccessException {
    Field field = instance.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(instance, value);
  }

  /**
   * Returns the component of a given class in <code>container</code> hierarchy.
   */
  public static Component findComponent(Container container,
                                        final Class componentClass)
      throws ComponentSearchException {
    return new BasicFinder().find(container, new Matcher () {
        public boolean matches(Component component) {
          return componentClass.isInstance(component);
        }
      });
  }

  /**
   * Presses the platform shortcut that temporarily toggles magnetism.
   */
  public static void pressMagnetismToggleKey(JComponentTester tester) {
    if (OperatingSystem.isWindows()) {
      tester.actionKeyPress(KeyEvent.VK_ALT);
    } else if (OperatingSystem.isMacOSX()) {
      tester.actionKeyPress(KeyEvent.VK_META);
    } else {
      // Pressing Shift first would activate alignment before the full chord is held.
      tester.actionKeyPress(KeyEvent.VK_ALT);
      tester.actionKeyPress(KeyEvent.VK_SHIFT);
    }
  }

  /**
   * Releases the platform shortcut that temporarily toggles magnetism.
   */
  public static void releaseMagnetismToggleKey(JComponentTester tester) {
    if (OperatingSystem.isWindows()) {
      tester.actionKeyRelease(KeyEvent.VK_ALT);
    } else if (OperatingSystem.isMacOSX()) {
      tester.actionKeyRelease(KeyEvent.VK_META);
    } else {
      tester.actionKeyRelease(KeyEvent.VK_SHIFT);
      tester.actionKeyRelease(KeyEvent.VK_ALT);
    }
  }
}
