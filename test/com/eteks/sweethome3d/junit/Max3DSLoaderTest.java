/*
 * Max3DSLoaderTest.java 5 Aug. 2026
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import junit.framework.TestCase;

import com.eteks.sweethome3d.j3d.Max3DSLoader;
import com.sun.j3d.loaders.IncorrectFormatException;
import com.sun.j3d.loaders.Scene;

/**
 * Tests how Max3DSLoader handles the chunks it doesn't parse.
 * @author Dawid Laszuk
 */
public class Max3DSLoaderTest extends TestCase {
  private static final int M3DMAGIC      = 0x4D4D;
  private static final int M3D_VERSION   = 0x0002;
  private static final int UNKNOWN_CHUNK = 0x7777;

  private static final int IGNORED_CHUNK_BODY_LENGTH = 64 * 1024;

  /**
   * Tests that a chunk declaring more bytes than the file actually contains is rejected.
   */
  public void testTruncatedIgnoredChunk() throws IOException {
    File file = File.createTempFile("truncated", ".3ds");
    try {
      OutputStream out = new FileOutputStream(file);
      try {
        writeChunkHeader(out, M3DMAGIC, 6 + 6 + IGNORED_CHUNK_BODY_LENGTH);
        writeChunkHeader(out, UNKNOWN_CHUNK, 6 + IGNORED_CHUNK_BODY_LENGTH);
        out.write(new byte [16]);
      } finally {
        out.close();
      }

      try {
        new Max3DSLoader().load(file.getAbsolutePath());
        fail("Truncated chunk should be rejected");
      } catch (IncorrectFormatException ex) {
        // Expected exception
      }
    } finally {
      file.delete();
    }
  }

  /**
   * Tests that an ignored chunk is consumed up to its exact end, checking that the
   * chunk following it is still read at the right offset.
   * Requires a display, since a successful load builds Java 3D nodes.
   */
  public void testIgnoredChunkConsumedExactly() throws IOException {
    File file = File.createTempFile("ignoredChunk", ".3ds");
    try {
      OutputStream out = new FileOutputStream(file);
      try {
        writeChunkHeader(out, M3DMAGIC, 6 + (6 + IGNORED_CHUNK_BODY_LENGTH) + 10);
        writeChunkHeader(out, UNKNOWN_CHUNK, 6 + IGNORED_CHUNK_BODY_LENGTH);
        out.write(new byte [IGNORED_CHUNK_BODY_LENGTH]);
        writeChunkHeader(out, M3D_VERSION, 10);
        out.write(new byte [] {3, 0, 0, 0});
      } finally {
        out.close();
      }

      Scene scene = new Max3DSLoader().load(file.getAbsolutePath());
      assertNotNull("Missing scene", scene);
    } finally {
      file.delete();
    }
  }

  private static void writeChunkHeader(OutputStream out, int id, long length) throws IOException {
    out.write(id & 0xFF);
    out.write((id >>> 8) & 0xFF);
    out.write((int)(length & 0xFF));
    out.write((int)((length >>> 8) & 0xFF));
    out.write((int)((length >>> 16) & 0xFF));
    out.write((int)((length >>> 24) & 0xFF));
  }
}
