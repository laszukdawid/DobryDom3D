/*
 * Architecture.java
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
package com.eteks.sweethome3d.tools;

import java.util.Locale;

/**
 * Tools used to normalize the <code>os.arch</code> system property into an
 * architecture family and a bitness, and to map supported architectures to the
 * folder names used to store native libraries.
 * <p>All methods are dependency-free and return only simple values (strings,
 * integers and booleans), so they can be safely called from classes loaded by
 * different class loaders, like the bootstrap class and the application classes
 * loaded by the child-first {@code ExtensionsClassLoader}.
 * @author Emmanuel Puybaret
 */
public class Architecture {
  public static final String X86 = "x86";
  public static final String ARM = "arm";
  public static final String UNKNOWN = "unknown";

  // This class contains only static methods
  private Architecture() {
  }

  /**
   * Returns the normalized family of the current JVM architecture.
   */
  public static String getFamily() {
    return getFamily(System.getProperty("os.arch"));
  }

  /**
   * Returns the normalized family of the given <code>os.arch</code> value,
   * one of <code>X86</code>, <code>ARM</code> or <code>UNKNOWN</code>.
   */
  public static String getFamily(String osArch) {
    String arch = osArch != null ? osArch.trim().toLowerCase(Locale.ENGLISH) : null;
    if (arch != null
        && arch.length() > 0) {
      if (isX86(arch)) {
        return X86;
      } else if (isARM(arch)) {
        return ARM;
      }
    }
    return UNKNOWN;
  }

  /**
   * Returns the bitness of the current JVM (32 or 64), or -1 if the
   * <code>os.arch</code> value isn't a recognized architecture.
   */
  public static int getBitness() {
    return getBitness(System.getProperty("os.arch"));
  }

  /**
   * Returns the bitness (32 or 64) of the given <code>os.arch</code> value,
   * or -1 if the value isn't a recognized architecture.
   */
  public static int getBitness(String osArch) {
    String arch = osArch != null ? osArch.trim().toLowerCase(Locale.ENGLISH) : null;
    if (arch == null) {
      return -1;
    } else if (isX86(arch)) {
      return isX8664(arch) ? 64 : 32;
    } else if (isARM(arch)) {
      return isARM64(arch) ? 64 : 32;
    } else {
      return -1;
    }
  }

  /**
   * Returns <code>true</code> if the current JVM runs on a 64 bits architecture.
   */
  public static boolean is64Bit() {
    return getBitness() == 64;
  }

  /**
   * Returns the folder token used by the Java 3D 1.6 native libraries,
   * <code>amd64</code> for 64 bits architectures and <code>i586</code> for
   * 32 bits ones.
   * @throws UnsupportedOperationException if the current architecture isn't supported
   */
  public static String getJava3DFolderName() {
    return getJava3DFolderName(System.getProperty("os.arch"));
  }

  /**
   * Returns the folder token used by the Java 3D 1.6 native libraries for the
   * given <code>os.arch</code> value.
   * @throws UnsupportedOperationException if the given architecture isn't supported
   */
  public static String getJava3DFolderName(String osArch) {
    return getFolderName(osArch, "amd64", "i586");
  }

  /**
   * Returns the folder token used by the YafaRay native libraries and the legacy
   * Java 3D 1.5.2 payload, <code>x64</code> for 64 bits architectures and
   * <code>i386</code> for 32 bits ones.
   * @throws UnsupportedOperationException if the current architecture isn't supported
   */
  public static String getYafarayFolderName() {
    return getYafarayFolderName(System.getProperty("os.arch"));
  }

  /**
   * Returns the folder token used by the YafaRay native libraries for the given
   * <code>os.arch</code> value.
   * @throws UnsupportedOperationException if the given architecture isn't supported
   */
  public static String getYafarayFolderName(String osArch) {
    return getFolderName(osArch, "x64", "i386");
  }

  /**
   * Returns the folder token matching the given <code>os.arch</code> value among
   * the given folder names, or throws if the architecture isn't supported.
   */
  private static String getFolderName(String osArch, String x8664Folder, String x8632Folder) {
    String family = getFamily(osArch);
    int bitness = getBitness(osArch);
    if (X86.equals(family)) {
      return bitness == 64 ? x8664Folder : x8632Folder;
    }
    throw new UnsupportedOperationException("Unsupported architecture " + osArch
        + " (family " + family + ", " + (bitness == -1 ? "unknown bitness" : bitness + " bits") + ")");
  }

  private static boolean isX86(String arch) {
    return arch.matches("^(x86|x86_32|i[3-6]86|x86_64|amd64|x64)$");
  }

  private static boolean isX8664(String arch) {
    return arch.matches("^(x86_64|amd64|x64)$");
  }

  private static boolean isARM(String arch) {
    return arch.startsWith("aarch") || arch.startsWith("arm");
  }

  private static boolean isARM64(String arch) {
    return arch.matches("^(aarch64|arm64)$");
  }
}
