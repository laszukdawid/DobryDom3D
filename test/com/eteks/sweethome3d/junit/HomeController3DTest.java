/*
 * HomeController3DTest.java 4 Aug 2026
 *
 * Copyright (c) 2026 Space Mushrooms <info@sweethome3d.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 */
package com.eteks.sweethome3d.junit;

import junit.framework.TestCase;

import com.eteks.sweethome3d.io.DefaultUserPreferences;
import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.viewcontroller.HomeController3D;

/**
 * Tests 3D camera navigation.
 */
public class HomeController3DTest extends TestCase {
  public void testMoveTopCameraSideways() {
    Home home = new Home();
    HomeController3D controller = new HomeController3D(
        home, new DefaultUserPreferences(), null, null, null);
    Camera camera = home.getTopCamera();
    float delta = 10;
    float expectedX = camera.getX() - (float)Math.cos(camera.getYaw()) * delta;
    float expectedY = camera.getY() - (float)Math.sin(camera.getYaw()) * delta;

    controller.moveCameraSideways(delta);

    assertEquals(expectedX, camera.getX(), 0.0001f);
    assertEquals(expectedY, camera.getY(), 0.0001f);

    // Zooming must keep the panned orbit center rather than recentering the home.
    controller.moveCamera(0);
    assertEquals(expectedX, camera.getX(), 0.0001f);
    assertEquals(expectedY, camera.getY(), 0.0001f);

    float leftDelta = -2 * delta;
    expectedX = camera.getX() - (float)Math.cos(camera.getYaw()) * leftDelta;
    expectedY = camera.getY() - (float)Math.sin(camera.getYaw()) * leftDelta;
    controller.moveCameraSideways(leftDelta);
    assertEquals(expectedX, camera.getX(), 0.0001f);
    assertEquals(expectedY, camera.getY(), 0.0001f);
  }

  public void testMoveTopCameraOnGround() {
    Home home = new Home();
    HomeController3D controller = new HomeController3D(
        home, new DefaultUserPreferences(), null, null, null);
    Camera camera = home.getTopCamera();
    float delta = 10;
    float expectedX = camera.getX() - (float)Math.sin(camera.getYaw()) * delta;
    float expectedY = camera.getY() + (float)Math.cos(camera.getYaw()) * delta;
    float initialZ = camera.getZ();
    float initialYaw = camera.getYaw();
    float initialPitch = camera.getPitch();

    controller.moveCameraOnGround(delta);

    assertEquals(expectedX, camera.getX(), 0.0001f);
    assertEquals(expectedY, camera.getY(), 0.0001f);
    assertEquals(initialZ, camera.getZ(), 0.0001f);
    assertEquals(initialYaw, camera.getYaw(), 0.0001f);
    assertEquals(initialPitch, camera.getPitch(), 0.0001f);

    // Zooming must change orbit distance while preserving the translated center.
    controller.zoomCamera(-10);
    assertFalse(expectedX == camera.getX() && expectedY == camera.getY());
    assertTrue(initialZ != camera.getZ());
  }

  public void testZoomObserverCamera() {
    Home home = new Home();
    HomeController3D controller = new HomeController3D(
        home, new DefaultUserPreferences(), null, null, null);
    controller.viewFromObserver();
    Camera camera = home.getObserverCamera();
    camera.setPitch((float)Math.PI / 6);
    float delta = 10;
    float horizontalDelta = (float)Math.cos(camera.getPitch()) * delta;
    float expectedX = camera.getX() - (float)Math.sin(camera.getYaw()) * horizontalDelta;
    float expectedY = camera.getY() + (float)Math.cos(camera.getYaw()) * horizontalDelta;
    float expectedZ = camera.getZ() - (float)Math.sin(camera.getPitch()) * delta;

    controller.zoomCamera(delta);

    assertEquals(expectedX, camera.getX(), 0.0001f);
    assertEquals(expectedY, camera.getY(), 0.0001f);
    assertEquals(expectedZ, camera.getZ(), 0.0001f);
  }
}
