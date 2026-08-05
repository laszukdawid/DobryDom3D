/*
 * ArchitectureTest.java
 *
 * Sweet Home 3D, Copyright (c) 2024 Space Mushrooms <info@sweethome3d.com>
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

import com.eteks.sweethome3d.tools.Architecture;

import junit.framework.TestCase;

/**
 * Tests {@linkplain Architecture architecture} normalization and native library
 * folder mapping.
 * @author Emmanuel Puybaret
 */
public class ArchitectureTest extends TestCase {
  public void testX86Aliases() {
    assertFamily(Architecture.X86, "x86");
    assertFamily(Architecture.X86, "i386");
    assertFamily(Architecture.X86, "i486");
    assertFamily(Architecture.X86, "i586");
    assertFamily(Architecture.X86, "i686");
    assertFamily(Architecture.X86, "x86_32");
    assertFamily(Architecture.X86, "x86_64");
    assertFamily(Architecture.X86, "amd64");
    assertFamily(Architecture.X86, "x64");
    assertFamily(Architecture.X86, "X86_64");
    assertFamily(Architecture.X86, "AMD64");
  }

  public void testARMAliases() {
    assertFamily(Architecture.ARM, "aarch64");
    assertFamily(Architecture.ARM, "arm64");
    assertFamily(Architecture.ARM, "aarch32");
    assertFamily(Architecture.ARM, "arm");
    assertFamily(Architecture.ARM, "armv6l");
    assertFamily(Architecture.ARM, "armv7l");
    assertFamily(Architecture.ARM, "armv8l");
  }

  public void testUnknownArchitectures() {
    assertFamily(Architecture.UNKNOWN, "riscv64");
    assertFamily(Architecture.UNKNOWN, "ppc64");
    assertFamily(Architecture.UNKNOWN, "s390x");
    assertFamily(Architecture.UNKNOWN, "sparcv9");
    assertFamily(Architecture.UNKNOWN, null);
    assertFamily(Architecture.UNKNOWN, "");
    assertFamily(Architecture.UNKNOWN, "  ");
  }

  public void testBitness() {
    assertEquals(32, Architecture.getBitness("x86"));
    assertEquals(32, Architecture.getBitness("i686"));
    assertEquals(32, Architecture.getBitness("x86_32"));
    assertEquals(64, Architecture.getBitness("x86_64"));
    assertEquals(64, Architecture.getBitness("amd64"));
    assertEquals(64, Architecture.getBitness("aarch64"));
    assertEquals(64, Architecture.getBitness("arm64"));
    assertEquals(32, Architecture.getBitness("aarch32"));
    assertEquals(32, Architecture.getBitness("armv7l"));
    assertEquals(-1, Architecture.getBitness("riscv64"));
    assertEquals(-1, Architecture.getBitness(null));
  }

  public void testJava3DFolderName() {
    assertEquals("amd64", Architecture.getJava3DFolderName("x86_64"));
    assertEquals("amd64", Architecture.getJava3DFolderName("AMD64"));
    assertEquals("i586", Architecture.getJava3DFolderName("i586"));
    assertEquals("i586", Architecture.getJava3DFolderName("x86"));
    assertUnsupportedArchitecture("aarch64");
    assertUnsupportedArchitecture("arm64");
    assertUnsupportedArchitecture("riscv64");
  }

  public void testYafarayFolderName() {
    assertEquals("x64", Architecture.getYafarayFolderName("x86_64"));
    assertEquals("x64", Architecture.getYafarayFolderName("amd64"));
    assertEquals("i386", Architecture.getYafarayFolderName("i386"));
    assertEquals("i386", Architecture.getYafarayFolderName("i686"));
    assertUnsupportedArchitecture("aarch64");
    assertUnsupportedArchitecture("aarch32");
    assertUnsupportedArchitecture("riscv64");
  }

  private void assertFamily(String expectedFamily, String osArch) {
    assertEquals(expectedFamily + " expected for " + osArch,
        expectedFamily, Architecture.getFamily(osArch));
  }

  private void assertUnsupportedArchitecture(String osArch) {
    try {
      Architecture.getJava3DFolderName(osArch);
      fail("Expected UnsupportedOperationException for " + osArch);
    } catch (UnsupportedOperationException ex) {
      // Expected
    }
    try {
      Architecture.getYafarayFolderName(osArch);
      fail("Expected UnsupportedOperationException for " + osArch);
    } catch (UnsupportedOperationException ex) {
      // Expected
    }
  }
}
