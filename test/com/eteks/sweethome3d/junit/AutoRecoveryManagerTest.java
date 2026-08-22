/*
 * AutoRecoveryManagerTest.java 21 aout 2026
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import com.eteks.sweethome3d.io.AutoRecoveryManager;
import com.eteks.sweethome3d.io.FileUserPreferences;
import com.eteks.sweethome3d.io.HomeFileRecorder;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeApplication;
import com.eteks.sweethome3d.model.HomeRecorder;
import com.eteks.sweethome3d.model.RecorderException;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;

import junit.framework.TestCase;

/**
 * Tests {@link AutoRecoveryManager} crash recovery scenarios on an isolated
 * application folder.
 * @author DobryDom3D contributors
 */
public class AutoRecoveryManagerTest extends TestCase {
  private File tempFolder;
  private File recoveryFolder;

  @Override
  protected void setUp() throws IOException {
    this.tempFolder = createTempFolder("autorecovery");
    File applicationFolder = new File(this.tempFolder, "app");
    this.recoveryFolder = new File(applicationFolder, "recovery");
    this.recoveryFolder.mkdirs();
  }

  @Override
  protected void tearDown() {
    deleteRecursively(this.tempFolder);
  }

  /**
   * Tests homes saved in the recovery folder are reopened as recovered homes,
   * and their file is deleted once they aren't recovered anymore.
   */
  public void testRecoveredHomeIsReopenedAndFlagged() throws IOException, RecorderException {
    Home savedHome = new Home(250f);
    savedHome.addWall(new Wall(0, 0, 100, 0, 10, savedHome.getWallHeight()));
    savedHome.addWall(new Wall(100, 0, 100, 100, 10, savedHome.getWallHeight()));
    File recoveredFile = new File(this.recoveryFolder, "test.sh3d.recovered");
    new HomeFileRecorder().writeHome(savedHome, recoveredFile.getPath());

    TestHomeApplication application = new TestHomeApplication(createPreferences());
    assertEquals("No home should be open at startup", 0, application.getHomes().size());
    AutoRecoveryManager manager = new AutoRecoveryManager(application);
    manager.openRecoveredHomes();

    assertEquals("Recovered home not opened", 1, application.getHomes().size());
    Home recoveredHome = application.getHomes().get(0);
    assertTrue("Opened home isn't flagged as recovered", recoveredHome.isRecovered());
    assertEquals("Recovered home lost its walls", savedHome.getWalls().size(), recoveredHome.getWalls().size());
    assertEquals("Different wall thickness",
        savedHome.getWalls().iterator().next().getThickness(),
        recoveredHome.getWalls().iterator().next().getThickness());

    // Clearing the recovered flag deletes the recovery file
    recoveredHome.setRecovered(false);
    assertFalse("Recovery file wasn't deleted", recoveredFile.exists());

    // openRecoveredHomes can't reopen the same homes twice
    manager.openRecoveredHomes();
    assertEquals("Recovered home opened twice", 1, application.getHomes().size());
  }

  /**
   * Tests a damaged recovery file doesn't prevent startup: it's renamed
   * with the unrecoverable extension and ignored.
   */
  public void testDamagedRecoveryFileIsRenamedUnrecoverable() throws IOException, RecorderException {
    File damagedFile = new File(this.recoveryFolder, "damaged.sh3d.recovered");
    writeBytes(damagedFile, new byte [] {1, 2, 3, 4});

    TestHomeApplication application = new TestHomeApplication(createPreferences());
    new AutoRecoveryManager(application).openRecoveredHomes();

    assertFalse("Damaged file kept its recovered name", damagedFile.exists());
    File unrecoverableFile = new File(this.recoveryFolder, "damaged.sh3d.unrecoverable");
    assertTrue("Damaged file not renamed to .unrecoverable", unrecoverableFile.exists());
    assertEquals("A damaged home shouldn't be recovered", 0, application.getHomes().size());
  }

  /**
   * Tests files still locked by another process (a concurrent save in progress)
   * are skipped during recovery and left untouched.
   */
  public void testLockedRecoveryFileIsSkipped() throws IOException, RecorderException {
    Home savedHome = new Home();
    File lockedFile = new File(this.recoveryFolder, "locked.sh3d.recovered");
    new HomeFileRecorder().writeHome(savedHome, lockedFile.getPath());
    FileOutputStream lockStream = new FileOutputStream(lockedFile, true);
    try {
      assertNotNull("Couldn't lock test file", lockStream.getChannel().tryLock());

      TestHomeApplication application = new TestHomeApplication(createPreferences());
      new AutoRecoveryManager(application).openRecoveredHomes();

      assertTrue("Locked recovery file was modified", lockedFile.exists());
      assertEquals("A locked home shouldn't be recovered", 0, application.getHomes().size());
    } finally {
      lockStream.close();
    }
  }

  /**
   * Returns user preferences bound to an isolated temporary application folder.
   */
  private FileUserPreferences createPreferences() {
    return new FileUserPreferences(new File(this.tempFolder, "prefs"),
        new File [] {new File(this.tempFolder, "app")});
  }

  private static File createTempFolder(String prefix) throws IOException {
    File tempFolder = File.createTempFile(prefix, null);
    if (!tempFolder.delete()
        || !tempFolder.mkdirs()) {
      throw new IOException("Can't create folder " + tempFolder);
    }
    return tempFolder;
  }

  private static void writeBytes(File file, byte [] bytes) throws IOException {
    FileOutputStream out = new FileOutputStream(file);
    try {
      out.write(bytes);
    } finally {
      out.close();
    }
  }

  private static void deleteRecursively(File file) {
    File [] children = file.listFiles();
    if (children != null) {
      for (File child : children) {
        deleteRecursively(child);
      }
    }
    file.delete();
  }

  /**
   * A minimal home application backed by a real recorder and isolated preferences.
   */
  private static class TestHomeApplication extends HomeApplication {
    private final UserPreferences preferences;

    TestHomeApplication(UserPreferences preferences) {
      this.preferences = preferences;
    }

    @Override
    public UserPreferences getUserPreferences() {
      return this.preferences;
    }

    @Override
    public HomeRecorder getHomeRecorder() {
      return new HomeFileRecorder();
    }
  }
}
