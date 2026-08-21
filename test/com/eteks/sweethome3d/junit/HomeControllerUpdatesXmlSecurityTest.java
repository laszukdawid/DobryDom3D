/*
 * HomeControllerUpdatesXmlSecurityTest.java 21 aout 2026
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
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.eteks.sweethome3d.io.FileUserPreferences;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Library;
import com.eteks.sweethome3d.viewcontroller.HomeController;

import junit.framework.TestCase;
import org.xml.sax.SAXException;

/**
 * Tests that {@link HomeController} refuses update feeds using DOCTYPE
 * declarations (external entities, external DTDs and entity expansion attacks)
 * while valid update feeds are still parsed, and that the feed input stream
 * is always closed.
 * @author Emmanuel Puybaret
 */
public class HomeControllerUpdatesXmlSecurityTest extends TestCase {
  private static final String SECRET = "XXE-SECRET-" + UUID.randomUUID();

  @Override
  protected void setUp() {
    // Ensure tests don't inherit an interrupted status from previous failures
    Thread.interrupted();
  }

  /**
   * Checks that a valid update feed is still parsed and that updates are
   * filtered according to the library version.
   */
  public void testValidUpdateFeedIsParsedAndFiltered() throws Exception {
    byte [] feed = ("<?xml version='1.0'?>\n"
        + "<updates>\n"
        + "  <update id='testLib' version='2.0' date='2026-01-01' url='https://example.com/2.0'/>\n"
        + "  <update id='testLib' version='0.9' date='2026-01-02' url='https://example.com/0.9'/>\n"
        + "</updates>\n").getBytes("UTF-8");
    BooleanHolder streamClosed = new BooleanHolder();
    URL url = createMemoryUrl(feed, streamClosed);
    Library library = createLibrary("testLib", "1.0");
    HomeController controller = new HomeController(new Home(), new FileUserPreferences(), null);

    Map<Library, List<?>> availableUpdates = readAvailableUpdates(controller,
        url, Collections.singletonList(library));

    assertEquals("One library should have updates available", 1, availableUpdates.size());
    List<?> libraryUpdates = availableUpdates.get(library);
    assertNotNull("Updates of tested library missing", libraryUpdates);
    assertEquals("Older update shouldn't be proposed", 1, libraryUpdates.size());
    assertEquals("Incorrect update version", "2.0", getUpdateVersion(libraryUpdates.get(0)));
    assertTrue("Feed stream not closed after successful parse", streamClosed.value);
  }

  /**
   * Checks that an update feed referencing a local secret file through an
   * external entity is rejected, and that the secret content isn't exposed.
   */
  public void testExternalEntitySecretIsRejected() throws Exception {
    File secretFile = createSecretFile();
    try {
      byte [] feed = ("<?xml version='1.0'?>\n"
          + "<!DOCTYPE updates [<!ENTITY secret SYSTEM '" + secretFile.toURI() + "'>]>\n"
          + "<updates>\n"
          + "  <update id='testLib' version='2.0'><description>&secret;</description></update>\n"
          + "</updates>\n").getBytes("UTF-8");
      BooleanHolder streamClosed = new BooleanHolder();
      URL url = createMemoryUrl(feed, streamClosed);
      HomeController controller = new HomeController(new Home(), new FileUserPreferences(), null);

      try {
        readAvailableUpdates(controller, url, Collections.<Library>emptyList());
        fail("Feed using an external entity shouldn't be parsed");
      } catch (SAXException ex) {
        assertSecretNotExposed(ex);
      }
      assertTrue("Feed stream not closed after failed parse", streamClosed.value);
    } finally {
      secretFile.delete();
    }
  }

  /**
   * Checks that an update feed containing an entity expansion attack is rejected.
   */
  public void testEntityExpansionAttackIsRejected() throws Exception {
    byte [] feed = ("<?xml version='1.0'?>\n"
        + "<!DOCTYPE updates [\n"
        + "  <!ENTITY a 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA'>\n"
        + "  <!ENTITY b '&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;'>\n"
        + "  <!ENTITY c '&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;'>\n"
        + "  <!ENTITY d '&c;&c;&c;&c;&c;&c;&c;&c;&c;&c;'>\n"
        + "  <!ENTITY e '&d;&d;&d;&d;&d;&d;&d;&d;&d;&d;'>\n"
        + "]>\n"
        + "<updates>\n"
        + "  <update id='testLib' version='2.0'><description>&e;</description></update>\n"
        + "</updates>\n").getBytes("UTF-8");
    BooleanHolder streamClosed = new BooleanHolder();
    URL url = createMemoryUrl(feed, streamClosed);
    HomeController controller = new HomeController(new Home(), new FileUserPreferences(), null);

    try {
      readAvailableUpdates(controller, url, Collections.<Library>emptyList());
      fail("Feed using entity expansion shouldn't be parsed");
    } catch (SAXException ex) {
      // Expected: DOCTYPE declarations are refused
    }
    assertTrue("Feed stream not closed after failed parse", streamClosed.value);
  }

  /**
   * Invokes the private <code>HomeController.readAvailableUpdates</code> method.
   */
  @SuppressWarnings("unchecked")
  private Map<Library, List<?>> readAvailableUpdates(HomeController controller,
                                                     URL url,
                                                     List<Library> libraries) throws Exception {
    Method readAvailableUpdates = HomeController.class.getDeclaredMethod("readAvailableUpdates",
        URL.class, List.class, Long.class, int.class);
    readAvailableUpdates.setAccessible(true);
    try {
      return (Map<Library, List<?>>)readAvailableUpdates.invoke(
          controller, url, libraries, null, -1);
    } catch (InvocationTargetException ex) {
      // Rethrow the exception thrown by readAvailableUpdates itself
      Throwable cause = ex.getCause();
      if (cause instanceof Exception) {
        throw (Exception)cause;
      } else {
        throw ex;
      }
    }
  }

  /**
   * Returns the version of an <code>HomeController.Update</code> instance.
   */
  private String getUpdateVersion(Object update) throws Exception {
    Method getVersion = update.getClass().getDeclaredMethod("getVersion");
    getVersion.setAccessible(true);
    return (String)getVersion.invoke(update);
  }

  /**
   * Returns a URL served from memory by a custom protocol handler, whose input
   * stream signals in <code>streamClosed</code> when it is closed.
   */
  private URL createMemoryUrl(final byte [] content, final BooleanHolder streamClosed) throws IOException {
    return new URL(null, "memory://updates-feed/test", new URLStreamHandler() {
        @Override
        protected URLConnection openConnection(URL url) {
          return new URLConnection(url) {
            @Override
            public void connect() {
            }

            @Override
            public InputStream getInputStream() {
              return new ByteArrayInputStream(content) {
                @Override
                public void close() throws IOException {
                  streamClosed.value = true;
                  super.close();
                }
              };
            }
          };
        }
      });
  }

  /**
   * Returns a test library with the given <code>id</code> and <code>version</code>.
   */
  private Library createLibrary(final String id, final String version) {
    return new Library() {
        public String getLocation() {
          return null;
        }

        public String getId() {
          return id;
        }

        public String getType() {
          return null;
        }

        public String getName() {
          return id;
        }

        public String getDescription() {
          return null;
        }

        public String getVersion() {
          return version;
        }

        public String getLicense() {
          return null;
        }

        public String getProvider() {
          return null;
        }
      };
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
   * Asserts none of the exceptions in the cause chain of <code>ex</code>
   * exposes the secret file content.
   */
  private void assertSecretNotExposed(Throwable ex) {
    Throwable cause = ex;
    while (cause != null) {
      if (cause.getMessage() != null && cause.getMessage().contains(SECRET)) {
        fail("Secret content exposed in exception: " + cause.getMessage());
      }
      cause = cause.getCause();
    }
  }

  private static class BooleanHolder {
    boolean value;
  }
}
