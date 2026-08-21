/*
 * CatalogIdDeduplicationTest.java 21 Aug 2026
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import junit.framework.TestCase;

import com.eteks.sweethome3d.io.DefaultFurnitureCatalog;
import com.eteks.sweethome3d.io.DefaultTexturesCatalog;
import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.CatalogTexture;
import com.eteks.sweethome3d.model.FurnitureCategory;
import com.eteks.sweethome3d.model.TexturesCategory;

/**
 * Tests that duplicate IDs read from furniture and textures catalogs keep
 * choosing the first catalog entry, and that final catalog content is unchanged.
 * The catalogs under test are built from synthetic plugin zip fixtures so no
 * production hook is required.
 * @author DobryDom3D contributors
 */
public class CatalogIdDeduplicationTest extends TestCase {
  private static final Charset PROPERTIES_ENCODING = Charset.forName("ISO-8859-1");

  private Locale defaultLocale;
  private final List<File> temporaryFiles = new ArrayList<File>();

  @Override
  protected void setUp() {
    // Save default locale to restore it in tearDown
    this.defaultLocale = Locale.getDefault();
    // Ensure alphabetical ordering checks are stable
    Locale.setDefault(Locale.US);
  }

  @Override
  protected void tearDown() {
    Locale.setDefault(this.defaultLocale);
    // Delete fixture zips explicitly instead of relying only on deleteOnExit
    for (File temporaryFile : this.temporaryFiles) {
      temporaryFile.delete();
    }
    this.temporaryFiles.clear();
  }

  /**
   * Checks that when two furniture plugin catalogs contain pieces sharing an ID,
   * the piece of the first read catalog wins and no duplicate stays in the catalog.
   */
  public void testFurnitureDuplicateIdKeepsFirstCatalogEntry() throws IOException {
    Map<String, String> firstCatalogProperties = new LinkedHashMap<String, String>();
    putFurniture(firstCatalogProperties, 1, "shared-id", "First Chair", "Chairs");
    putFurniture(firstCatalogProperties, 2, "only-in-first-id", "First Table", "Tables");
    File firstCatalogFile = createPluginFurnitureCatalog("first", firstCatalogProperties);

    Map<String, String> secondCatalogProperties = new LinkedHashMap<String, String>();
    putFurniture(secondCatalogProperties, 1, "shared-id", "Second Chair", "Chairs");
    File secondCatalogFile = createPluginFurnitureCatalog("second", secondCatalogProperties);

    DefaultFurnitureCatalog catalog = new DefaultFurnitureCatalog(new URL [] {
        firstCatalogFile.toURI().toURL(), secondCatalogFile.toURI().toURL()});

    List<CatalogPieceOfFurniture> sharedIdPieces = getFurnitureWithId(catalog, "shared-id");
    assertEquals("Wrong number of pieces with shared ID", 1, sharedIdPieces.size());
    assertEquals("First catalog entry should win", "First Chair", sharedIdPieces.get(0).getName());
    assertEquals("Missing unique piece of first catalog", "First Table",
        getFurnitureWithId(catalog, "only-in-first-id").get(0).getName());
    assertEquals("Catalog should contain exactly two pieces", 2, countPieces(catalog));
  }

  /**
   * Checks that when two texture plugin catalogs contain textures sharing an ID,
   * the texture of the first read catalog wins and no duplicate stays in the catalog.
   */
  public void testTexturesDuplicateIdKeepsFirstCatalogEntry() throws IOException {
    Map<String, String> firstCatalogProperties = new LinkedHashMap<String, String>();
    putTexture(firstCatalogProperties, 1, "shared-texture-id", "First Wood", "Wood");
    File firstCatalogFile = createPluginTexturesCatalog("first-textures", firstCatalogProperties);

    Map<String, String> secondCatalogProperties = new LinkedHashMap<String, String>();
    putTexture(secondCatalogProperties, 1, "shared-texture-id", "Second Wood", "Wood");
    File secondCatalogFile = createPluginTexturesCatalog("second-textures", secondCatalogProperties);

    DefaultTexturesCatalog catalog = new DefaultTexturesCatalog(new URL [] {
        firstCatalogFile.toURI().toURL(), secondCatalogFile.toURI().toURL()});

    List<CatalogTexture> sharedIdTextures = getTexturesWithId(catalog, "shared-texture-id");
    assertEquals("Wrong number of textures with shared ID", 1, sharedIdTextures.size());
    assertEquals("First catalog entry should win", "First Wood", sharedIdTextures.get(0).getName());
    assertEquals("Catalog should contain exactly one texture", 1, countTextures(catalog));
  }

  /**
   * Checks that catalog content and its public ordering are unchanged by ID tracking:
   * categories stay sorted by name, pieces stay sorted by name inside their category,
   * and pieces without ID are all kept.
   */
  public void testFurnitureCatalogOrderingAndContentUnchanged() throws IOException {
    Map<String, String> properties = new LinkedHashMap<String, String>();
    putFurniture(properties, 1, "id-b", "Banana Lamp", "Lighting");
    putFurniture(properties, 2, null, "Apple Chair", "Chairs");
    putFurniture(properties, 3, "id-c", "Cherry Table", "Tables");
    putFurniture(properties, 4, "id-a", "Apricot Stool", "Chairs");
    File catalogFile = createPluginFurnitureCatalog("ordering", properties);

    DefaultFurnitureCatalog catalog = new DefaultFurnitureCatalog(
        new URL [] {catalogFile.toURI().toURL()});

    List<String> expectedContent = new ArrayList<String>();
    expectedContent.add("Chairs / Apple Chair / null");
    expectedContent.add("Chairs / Apricot Stool / id-a");
    expectedContent.add("Lighting / Banana Lamp / id-b");
    expectedContent.add("Tables / Cherry Table / id-c");
    assertEquals(expectedContent, describeFurnitureCatalog(catalog));
  }

  /**
   * Checks the default classpath furniture catalog still parses without duplicated IDs
   * and with deterministic content between two reads.
   */
  public void testDefaultFurnitureCatalogRegression() {
    DefaultFurnitureCatalog catalog = new DefaultFurnitureCatalog();
    assertTrue("Default furniture catalog should not be empty", countPieces(catalog) > 0);
    Set<String> ids = new HashSet<String>();
    int idCount = 0;
    for (CatalogPieceOfFurniture piece : getAllPieces(catalog)) {
      if (piece.getId() != null) {
        ids.add(piece.getId());
        idCount++;
      }
    }
    assertEquals("Duplicate IDs found in default furniture catalog", idCount, ids.size());
    // Content must be deterministic between two constructions
    DefaultFurnitureCatalog secondRead = new DefaultFurnitureCatalog();
    assertEquals(describeFurnitureCatalog(catalog), describeFurnitureCatalog(secondRead));
  }

  /**
   * Checks the default classpath textures catalog still parses without duplicated IDs.
   */
  public void testDefaultTexturesCatalogRegression() {
    DefaultTexturesCatalog catalog = new DefaultTexturesCatalog();
    assertTrue("Default textures catalog should not be empty", countTextures(catalog) > 0);
    Set<String> ids = new HashSet<String>();
    int idCount = 0;
    for (List<CatalogTexture> categoryTextures : getTexturesByCategory(catalog)) {
      for (CatalogTexture texture : categoryTextures) {
        if (texture.getId() != null) {
          ids.add(texture.getId());
          idCount++;
        }
      }
    }
    assertEquals("Duplicate IDs found in default textures catalog", idCount, ids.size());
  }

  /**
   * Returns the pieces of <code>catalog</code> matching the given ID.
   */
  private List<CatalogPieceOfFurniture> getFurnitureWithId(DefaultFurnitureCatalog catalog,
                                                           String id) {
    List<CatalogPieceOfFurniture> matchingPieces = new ArrayList<CatalogPieceOfFurniture>();
    for (CatalogPieceOfFurniture piece : getAllPieces(catalog)) {
      if (id.equals(piece.getId())) {
        matchingPieces.add(piece);
      }
    }
    return matchingPieces;
  }

  /**
   * Returns the textures of <code>catalog</code> matching the given ID.
   */
  private List<CatalogTexture> getTexturesWithId(DefaultTexturesCatalog catalog,
                                                 String id) {
    List<CatalogTexture> matchingTextures = new ArrayList<CatalogTexture>();
    for (List<CatalogTexture> categoryTextures : getTexturesByCategory(catalog)) {
      for (CatalogTexture texture : categoryTextures) {
        if (id.equals(texture.getId())) {
          matchingTextures.add(texture);
        }
      }
    }
    return matchingTextures;
  }

  private List<CatalogPieceOfFurniture> getAllPieces(DefaultFurnitureCatalog catalog) {
    List<CatalogPieceOfFurniture> pieces = new ArrayList<CatalogPieceOfFurniture>();
    for (FurnitureCategory category : catalog.getCategories()) {
      pieces.addAll(category.getFurniture());
    }
    return pieces;
  }

  private List<List<CatalogTexture>> getTexturesByCategory(DefaultTexturesCatalog catalog) {
    List<List<CatalogTexture>> textures = new ArrayList<List<CatalogTexture>>();
    for (TexturesCategory category : catalog.getCategories()) {
      textures.add(category.getTextures());
    }
    return textures;
  }

  private int countPieces(DefaultFurnitureCatalog catalog) {
    return getAllPieces(catalog).size();
  }

  private int countTextures(DefaultTexturesCatalog catalog) {
    int count = 0;
    for (List<CatalogTexture> categoryTextures : getTexturesByCategory(catalog)) {
      count += categoryTextures.size();
    }
    return count;
  }

  /**
   * Returns one "category / name / id" description line per piece, in public order.
   */
  private List<String> describeFurnitureCatalog(DefaultFurnitureCatalog catalog) {
    List<String> descriptions = new ArrayList<String>();
    for (FurnitureCategory category : catalog.getCategories()) {
      for (CatalogPieceOfFurniture piece : category.getFurniture()) {
        descriptions.add(category.getName() + " / " + piece.getName() + " / " + piece.getId());
      }
    }
    return descriptions;
  }

  private void putFurniture(Map<String, String> properties, int index,
                            String id, String name, String category) {
    if (id != null) {
      properties.put("id#" + index, id);
    }
    properties.put("name#" + index, name);
    properties.put("category#" + index, category);
    properties.put("icon#" + index, "/icon" + index + ".png");
    properties.put("model#" + index, "/model" + index + ".zip");
    // Avoids on-demand model size computation by ContentDigestManager
    properties.put("modelSize#" + index, "100");
    properties.put("width#" + index, "50");
    properties.put("depth#" + index, "50");
    properties.put("height#" + index, "100");
    properties.put("movable#" + index, "true");
    properties.put("doorOrWindow#" + index, "false");
  }

  private void putTexture(Map<String, String> properties, int index,
                          String id, String name, String category) {
    if (id != null) {
      properties.put("id#" + index, id);
    }
    properties.put("name#" + index, name);
    properties.put("category#" + index, category);
    properties.put("image#" + index, "/image" + index + ".png");
    properties.put("width#" + index, "10");
    properties.put("height#" + index, "10");
  }

  /**
   * Creates a temporary zip file readable as a plugin furniture catalog.
   */
  private File createPluginFurnitureCatalog(String name, Map<String, String> properties)
      throws IOException {
    return createTempZip(name, "PluginFurnitureCatalog.properties", properties);
  }

  /**
   * Creates a temporary zip file readable as a plugin textures catalog.
   */
  private File createPluginTexturesCatalog(String name, Map<String, String> properties)
      throws IOException {
    return createTempZip(name, "PluginTexturesCatalog.properties", properties);
  }

  private File createTempZip(String name, String propertiesEntryName,
                             Map<String, String> properties) throws IOException {
    File zipFile = File.createTempFile(name + "-catalog-", ".zip");
    // Kept as a safety net if the test crashes before tearDown
    zipFile.deleteOnExit();
    this.temporaryFiles.add(zipFile);
    ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFile));
    try {
      out.putNextEntry(new ZipEntry(propertiesEntryName));
      StringBuilder propertiesContent = new StringBuilder();
      for (Map.Entry<String, String> entry : properties.entrySet()) {
        propertiesContent.append(entry.getKey() + "=" + entry.getValue() + "\n");
      }
      out.write(propertiesContent.toString().getBytes(PROPERTIES_ENCODING));
      out.closeEntry();
    } finally {
      out.close();
    }
    return zipFile;
  }
}
