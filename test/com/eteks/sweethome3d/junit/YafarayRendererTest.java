/*
 * YafarayRendererTest.java
 *
 * Copyright (c) 2026 Space Mushrooms <info@sweethome3d.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package com.eteks.sweethome3d.junit;

import java.awt.image.BufferedImage;
import java.util.Collections;

import junit.framework.TestCase;

import com.eteks.sweethome3d.j3d.AbstractPhotoRenderer;
import com.eteks.sweethome3d.j3d.YafarayRenderer;
import com.eteks.sweethome3d.model.Home;

/**
 * Tests YafaRay renderer resource ownership with optional native initialization.
 */
public class YafarayRendererTest extends TestCase {
  public void testCloseIsIdempotentAndPreventsReuse() throws Exception {
    Home home = new Home();
    YafarayRenderer renderer = new YafarayRenderer(
        home, null, AbstractPhotoRenderer.Quality.LOW);

    renderer.close();
    renderer.close();
    assertEquals(Long.valueOf(0), TestUtilities.getField(renderer, "environment"));
    assertEquals(Long.valueOf(0), TestUtilities.getField(renderer, "scene"));

    try {
      renderer.render(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), home.getCamera(), null);
      fail("A closed renderer should reject rendering");
    } catch (IllegalStateException ex) {
      // Expected.
    }
  }

  public void testRepeatedNativeCleanup() throws Exception {
    Home home = new Home();
    YafarayRenderer availabilityProbe = new YafarayRenderer(
        home, null, AbstractPhotoRenderer.Quality.LOW);
    boolean available = availabilityProbe.isAvailable();
    availabilityProbe.close();
    if (Boolean.getBoolean("com.eteks.sweethome3d.j3d.requireYafaray")) {
      assertTrue("YafaRay native libraries couldn't be loaded", available);
    }
    if (!available) {
      return;
    }

    for (int i = 0; i < 5; i++) {
      YafarayRenderer renderer = new YafarayRenderer(
          home, null, AbstractPhotoRenderer.Quality.LOW);
      try {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        renderer.render(image, home.getCamera(), null);
        renderer.render(image, home.getCamera(), Collections.singletonList(home.getCompass()), null);
      } finally {
        renderer.close();
      }
      assertEquals(Long.valueOf(0), TestUtilities.getField(renderer, "environment"));
      assertEquals(Long.valueOf(0), TestUtilities.getField(renderer, "scene"));
    }
  }
}
