/*
 * CatalogMembershipBenchmark.java 21 Aug 2026
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Micro-level benchmark of the duplicate-ID membership tracking used by
 * {@link com.eteks.sweethome3d.io.DefaultFurnitureCatalog} and
 * {@link com.eteks.sweethome3d.io.DefaultTexturesCatalog} while parsing catalogs.
 * It measures a synthetic contains/add workload over a large ID set with the
 * former ArrayList implementation and the HashSet implementation.
 *
 * <p>This is NOT a startup performance measurement: no catalog file, resource
 * bundle or I/O is involved. Run manually from the command line; results are
 * reported in the pull request description.</p>
 */
public class CatalogMembershipBenchmark {
  // Large enough to expose quadratic behavior, small enough to stay bounded.
  private static final int UNIQUE_ID_COUNT = 30000;
  private static final int WARMUP_ROUNDS = 2;
  private static final int MEASURED_ROUNDS = 3;

  public static void main(String [] args) {
    Locale.setDefault(Locale.US);
    List<String> parseOrderIds = createParseOrderIds(UNIQUE_ID_COUNT);
    System.out.println("Synthetic catalog: " + UNIQUE_ID_COUNT + " unique IDs, "
        + parseOrderIds.size() + " membership operations (duplicates included)");

    System.out.println("ArrayList (former implementation):");
    runBenchmark(parseOrderIds, true);

    System.out.println("HashSet (new implementation):");
    runBenchmark(parseOrderIds, false);
  }

  /**
   * Builds an ID stream mixing first occurrences and repeated IDs,
   * like parsing multiple overlapping catalogs would produce.
   */
  private static List<String> createParseOrderIds(int uniqueIdCount) {
    List<String> ids = new ArrayList<String>(uniqueIdCount + uniqueIdCount / 10);
    for (int i = 0; i < uniqueIdCount; i++) {
      ids.add(String.format("furniture-id-%08d", i));
      if (i % 10 == 0 && i > 0) {
        // Re-cite one earlier ID, as overlapping libraries do
        ids.add(String.format("furniture-id-%08d", i / 2));
      }
    }
    return ids;
  }

  private static void runBenchmark(List<String> parseOrderIds, boolean arrayListImplementation) {
    for (int round = 0; round < WARMUP_ROUNDS + MEASURED_ROUNDS; round++) {
      long start = System.nanoTime();
      long membershipChecks = simulateParsing(parseOrderIds, arrayListImplementation);
      long elapsedNs = System.nanoTime() - start;
      if (round >= WARMUP_ROUNDS) {
        System.out.printf(Locale.ENGLISH,
            "  round %d: %,d ns total (%.1f ms), %,d membership checks%n",
            round - WARMUP_ROUNDS + 1, elapsedNs, elapsedNs / 1e6, membershipChecks);
      }
    }
  }

  /**
   * Runs the contains/add sequence of catalog parsing against either
   * an ArrayList (former code) or a HashSet (new code).
   */
  private static long simulateParsing(List<String> parseOrderIds,
                                      boolean arrayListImplementation) {
    Set<String> identifiedIds = arrayListImplementation
        ? new ArrayListBackedSet()
        : new HashSet<String>();
    for (String id : parseOrderIds) {
      if (!identifiedIds.contains(id)) {
        identifiedIds.add(id);
      }
    }
    return identifiedIds.size();
  }

  /**
   * Mimics exactly the former production usage pattern:
   * an ArrayList used only through contains/add.
   */
  private static class ArrayListBackedSet implements Set<String> {
    private final List<String> ids = new ArrayList<String>();

    @Override
    public boolean contains(Object id) {
      return ids.contains(id);
    }

    @Override
    public boolean add(String id) {
      return ids.add(id);
    }

    @Override
    public int size() {
      return ids.size();
    }

    @Override
    public boolean isEmpty() {
      throw new UnsupportedOperationException();
    }

    @Override
    public java.util.Iterator<String> iterator() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Object [] toArray() {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T> T [] toArray(T [] a) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(java.util.Collection<?> c) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(java.util.Collection<? extends String> c) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(java.util.Collection<?> c) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(java.util.Collection<?> c) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
      throw new UnsupportedOperationException();
    }
  }
}
