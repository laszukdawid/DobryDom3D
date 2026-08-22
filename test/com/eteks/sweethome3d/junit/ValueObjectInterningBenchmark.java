/*
 * ValueObjectInterningBenchmark.java 21 Aug 2026
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

import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.eteks.sweethome3d.model.TextStyle;
import com.sun.management.ThreadMXBean;

/**
 * Micro-level benchmark of the {@link TextStyle} value interning cache. It
 * measures a bounded synthetic workload of repeated equal-value lookups,
 * comparing the former implementation that returned the freshly allocated
 * candidate on every cache hit with the fixed implementation that returns the
 * live cached instance. Callers are modeled as retaining every returned style,
 * like walls and labels retain baseboards and text styles.
 *
 * <p>This is NOT a UI benchmark: no window, rendering, plan or I/O code runs
 * here, only model value allocation and weak-cache lookup. Run manually from
 * the command line; results are reported in the pull request description.</p>
 */
public class ValueObjectInterningBenchmark {
  private static final int DISTINCT_STYLE_COUNT = 64;
  private static final int LOOKUP_COUNT = 40000;
  private static final int WARMUP_ROUNDS = 2;
  private static final int MEASURED_ROUNDS = 3;

  public static void main(String [] args) {
    Locale.setDefault(Locale.US);
    List<TextStyle> seededStyles = createSeededStyles(DISTINCT_STYLE_COUNT);
    List<WeakReference<TextStyle>> cache = seedWeakCache(seededStyles);
    System.out.println("Synthetic workload: " + DISTINCT_STYLE_COUNT
        + " distinct cached styles, " + LOOKUP_COUNT + " retained-result lookups");

    System.out.println("Former implementation (returns fresh candidate on hit):");
    runBenchmark(cache, true);

    System.out.println("Fixed implementation (returns cached instance on hit):");
    runBenchmark(cache, false);
  }

  /**
   * Creates the distinct styles whose values the workload looks up.
   */
  private static List<TextStyle> createSeededStyles(int count) {
    List<TextStyle> styles = new ArrayList<TextStyle>(count);
    for (int i = 0; i < count; i++) {
      styles.add(newUncachedTextStyle("BenchmarkFont" + i, 8f + i, i % 2 == 0, i % 3 == 0));
    }
    return styles;
  }

  /**
   * Seeds a local weak cache like <code>TextStyle.textStylesCache</code>,
   * oldest entry first so the last entry is the newest match candidate.
   */
  private static List<WeakReference<TextStyle>> seedWeakCache(List<TextStyle> styles) {
    List<WeakReference<TextStyle>> cache = new ArrayList<WeakReference<TextStyle>>(styles.size());
    for (TextStyle style : styles) {
      cache.add(new WeakReference<TextStyle>(style));
    }
    return cache;
  }

  private static void runBenchmark(List<WeakReference<TextStyle>> cache,
                                   boolean formerImplementation) {
    ThreadMXBean threadMXBean = (ThreadMXBean)ManagementFactory.getThreadMXBean();
    long threadId = Thread.currentThread().getId();
    for (int round = 0; round < WARMUP_ROUNDS + MEASURED_ROUNDS; round++) {
      System.gc();
      long allocatedBefore = threadMXBean.getThreadAllocatedBytes(threadId);
      long start = System.nanoTime();
      List<TextStyle> retainedByCallers = simulateLookups(cache, formerImplementation);
      long elapsedNs = System.nanoTime() - start;
      long allocatedBytes = threadMXBean.getThreadAllocatedBytes(threadId) - allocatedBefore;
      if (round >= WARMUP_ROUNDS) {
        // Retained references are all equally reachable; what differs is how
        // many distinct live objects back them
        Set<TextStyle> distinctInstances =
            Collections.newSetFromMap(new IdentityHashMap<TextStyle, Boolean>());
        distinctInstances.addAll(retainedByCallers);
        System.out.printf(Locale.ENGLISH,
            "  round %d: %.2f us/lookup, %,d bytes allocated (%d bytes/lookup), "
                + "%,d retained references backed by %,d distinct instances%n",
            round - WARMUP_ROUNDS + 1, elapsedNs / 1000.0 / LOOKUP_COUNT,
            allocatedBytes, allocatedBytes / LOOKUP_COUNT,
            retainedByCallers.size(), distinctInstances.size());
      }
    }
  }

  /**
   * Performs lookups cycling over all distinct style values, retaining each
   * returned instance as a real caller would, using either the former logic
   * that returns its fresh candidate or the fixed logic returning the cached
   * instance found by the newest-match backward scan.
   */
  private static List<TextStyle> simulateLookups(List<WeakReference<TextStyle>> cache,
                                                 boolean formerImplementation) {
    List<TextStyle> retainedByCallers = new ArrayList<TextStyle>(LOOKUP_COUNT);
    for (int i = 0; i < LOOKUP_COUNT; i++) {
      TextStyle requested = cache.get(i % cache.size()).get();
      TextStyle lookedUp = getInstance(cache,
          requested.getFontName(), requested.getFontSize(),
          requested.isBold(), requested.isItalic(), formerImplementation);
      // A real caller keeps the returned style reachable in the model
      retainedByCallers.add(lookedUp);
    }
    return retainedByCallers;
  }

  /**
   * The interning lookup shared by both simulated implementations: allocate an
   * unregistered candidate, scan the cache from the newest entry dropping
   * cleared weak references, then either return the matching cached instance
   * (fixed behavior) or the fresh candidate (former behavior).
   */
  private static TextStyle getInstance(List<WeakReference<TextStyle>> cache,
                                       String fontName, float fontSize,
                                       boolean bold, boolean italic,
                                       boolean formerImplementation) {
    TextStyle textStyle = newUncachedTextStyle(fontName, fontSize, bold, italic);
    synchronized (cache) {
      for (int i = cache.size() - 1; i >= 0; i--) {
        TextStyle cachedTextStyle = cache.get(i).get();
        if (cachedTextStyle == null) {
          cache.remove(i);
        } else if (cachedTextStyle.equals(textStyle)) {
          return formerImplementation ? textStyle : cachedTextStyle;
        }
      }
    }
    return textStyle;
  }

  /**
   * Creates a probe candidate through the private uncached constructor so the
   * benchmark workload doesn't grow the production static cache itself.
   */
  private static TextStyle newUncachedTextStyle(String fontName, float fontSize,
                                                boolean bold, boolean italic) {
    try {
      Constructor<TextStyle> constructor = TextStyle.class.getDeclaredConstructor(
          String.class, float.class, boolean.class, boolean.class,
          TextStyle.Alignment.class, boolean.class);
      constructor.setAccessible(true);
      return constructor.newInstance(fontName, fontSize, bold, italic,
          TextStyle.Alignment.CENTER, Boolean.FALSE);
    } catch (NoSuchMethodException | InstantiationException | IllegalAccessException
        | InvocationTargetException ex) {
      throw new InternalError(ex);
    }
  }
}
