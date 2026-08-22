/*
 * HomeFileRecorderXmlSecurityTest.java 21 aout 2026
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
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.UUID;

import com.eteks.sweethome3d.io.FileUserPreferences;
import com.eteks.sweethome3d.io.HomeFileRecorder;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.RecorderException;

import junit.framework.TestCase;

/**
 * Tests that {@link HomeFileRecorder} refuses home XML files using DOCTYPE
 * declarations (external entities, external DTDs and entity expansion attacks)
 * while valid home XML files are still read.
 * @author Emmanuel Puybaret
 */
public class HomeFileRecorderXmlSecurityTest extends TestCase {
  private static final String SECRET = "XXE-SECRET-" + UUID.randomUUID();

  /**
   * Checks a valid <code>Home.xml</code> file and a saved file containing a
   * <code>Home.xml</code> entry are still readable.
   */
  public void testValidHomeXMLStillLoads() throws URISyntaxException, RecorderException {
    HomeFileRecorder recorder = new HomeFileRecorder(0, false, new FileUserPreferences(), false, true, false);
    // Read a plain home XML file
    Home home = recorder.readHome(new File(
        HomeControllerTest.class.getResource("resources/homeTest.xml").toURI()).getAbsolutePath());
    assertEquals("Incorrect furniture count", 2, home.getFurniture().size());
    assertEquals("Incorrect walls count", 4, home.getWalls().size());
    // Read back a saved file in priority through its Home.xml entry
    Home home2 = new Home();
    String savedFile = new File("testXmlSecurity.sh3d").getAbsolutePath();
    try {
      recorder.writeHome(home2, savedFile);
      Home readHome2 = recorder.readHome(savedFile);
      assertNotNull("Home with XML entry not loaded", readHome2);
    } finally {
      new File(savedFile).delete();
    }
  }

  /**
   * Checks that an XML home referencing an external local secret file through
   * an entity is rejected, and that the secret content isn't exposed.
   */
  public void testExternalEntitySecretIsRejected() throws IOException {
    File secretFile = createSecretFile();
    try {
      File maliciousFile = File.createTempFile("xxeSecret", ".xml");
      FileWriter out = new FileWriter(maliciousFile);
      out.write("<?xml version='1.0'?>\n"
          + "<!DOCTYPE home [<!ENTITY secret SYSTEM '" + secretFile.toURI() + "'>]>\n"
          + "<home version='6000' name='Evil'>\n"
          + "  <label x='0' y='0'><text>&secret;</text></label>\n"
          + "</home>\n");
      out.close();
      checkMaliciousHomeIsRejected(maliciousFile, SECRET);
      maliciousFile.delete();
    } finally {
      secretFile.delete();
    }
  }

  /**
   * Checks that an XML home containing an entity expansion attack is rejected.
   */
  public void testEntityExpansionAttackIsRejected() throws IOException {
    File maliciousFile = File.createTempFile("xxeBomb", ".xml");
    FileWriter out = new FileWriter(maliciousFile);
    out.write("<?xml version='1.0'?>\n"
        + "<!DOCTYPE home [\n"
        + "  <!ENTITY a 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA'>\n"
        + "  <!ENTITY b '&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;'>\n"
        + "  <!ENTITY c '&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;'>\n"
        + "  <!ENTITY d '&c;&c;&c;&c;&c;&c;&c;&c;&c;&c;'>\n"
        + "  <!ENTITY e '&d;&d;&d;&d;&d;&d;&d;&d;&d;&d;'>\n"
        + "]>\n"
        + "<home version='6000' name='Evil'>\n"
        + "  <label x='0' y='0'><text>&e;</text></label>\n"
        + "</home>\n");
    out.close();
    checkMaliciousHomeIsRejected(maliciousFile, null);
    maliciousFile.delete();
  }

  /**
   * Returns a temporary file containing a unique secret string.
   */
  private File createSecretFile() throws IOException {
    File secretFile = File.createTempFile("xxeTarget", ".txt");
    FileWriter out = new FileWriter(secretFile);
    out.write(SECRET);
    out.close();
    return secretFile;
  }

  /**
   * Asserts reading <code>maliciousFile</code> fails with a
   * <code>RecorderException</code> and none of its messages contains
   * <code>forbidden</code>.
   */
  private void checkMaliciousHomeIsRejected(File maliciousFile, String forbidden) {
    HomeFileRecorder recorder = new HomeFileRecorder(0, false, new FileUserPreferences(), false, true, false);
    try {
      recorder.readHome(maliciousFile.getAbsolutePath());
      fail("Malicious home XML shouldn't be read");
    } catch (RecorderException ex) {
      Throwable cause = ex;
      while (cause != null) {
        if (forbidden != null && cause.getMessage() != null
            && cause.getMessage().contains(forbidden)) {
          fail("Secret content exposed in exception: " + cause.getMessage());
        }
        cause = cause.getCause();
      }
    }
  }
}
