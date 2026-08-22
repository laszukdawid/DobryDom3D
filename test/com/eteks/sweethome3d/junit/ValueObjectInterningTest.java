/*
 * ValueObjectInterningTest.java 21 Aug 2026
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

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;

import com.eteks.sweethome3d.model.Baseboard;
import com.eteks.sweethome3d.model.TextStyle;

/**
 * Tests the interning behavior of {@link TextStyle} derivations and
 * {@link Baseboard#getInstance}: an equal live cached instance is reused by
 * identity, unequal values never alias, concurrent lookups converge on one
 * canonical instance instead of throwing or diverging, and cleared weak
 * references are dropped from the caches.
 * @author DobryDom3D contributors
 */
public class ValueObjectInterningTest extends TestCase {
  private static final int FUTURE_TIMEOUT_SECONDS = 60;
  private static final int CONCURRENT_THREADS = 8;
  private static final int GC_CONFIRMATION_ROUNDS = 200;

  @Override
  protected void setUp() {
    // Isolate tests from any locale dependent behavior, none expected today
    Locale.setDefault(Locale.US);
  }

  /**
   * Checks that a derivation chain ending on parameters equal to a live cached
   * style returns that exact cached instance, exercising the newest-match rule.
   */
  public void testTextStyleDerivationReusesLiveCachedInstance() {
    TextStyle cached = new TextStyle("ValueInterningSerif", 12f, true, true);
    TextStyle source = new TextStyle("ValueInterningMono", 9f, false, false);
    TextStyle derived = source.deriveStyle("ValueInterningSerif")
        .deriveStyle(12f)
        .deriveBoldStyle(true)
        .deriveItalicStyle(true);
    assertEquals(cached, derived);
    assertSame("Equal derivation must reuse the cached style",
        cached, derived);
    // The cached instance must stay strongly reachable up to here
    assertEquals(12f, cached.getFontSize());
  }

  /**
   * Checks that repeated <code>Baseboard.getInstance</code> calls with equal
   * parameters return the same interned instance.
   */
  public void testBaseboardGetInstanceReusesLiveCachedInstance() {
    Baseboard cached = Baseboard.getInstance(0.7f, 2.5f, Integer.valueOf(0x33ccff), null);
    Baseboard equivalent = Baseboard.getInstance(0.7f, 2.5f, Integer.valueOf(0x33ccff), null);
    assertEquals(cached, equivalent);
    assertSame("Equal getInstance calls must reuse the cached baseboard",
        cached, equivalent);
    assertEquals(2.5f, cached.getHeight());
  }

  /**
   * Checks that unequal values produce distinct, mutually unequal instances
   * instead of aliasing the cache.
   */
  public void testUnequalValuesDoNotAlias() {
    TextStyle smallStyle = new TextStyle("ValueInterningSerif", 12f, true, true);
    TextStyle largeStyle = smallStyle.deriveStyle(14f);
    assertNotSame(smallStyle, largeStyle);
    assertFalse(smallStyle.equals(largeStyle));
    assertFalse(largeStyle.equals(smallStyle));

    Baseboard first = Baseboard.getInstance(0.7f, 2.5f, Integer.valueOf(0x33ccff), null);
    Baseboard second = Baseboard.getInstance(0.8f, 2.5f, Integer.valueOf(0x33ccff), null);
    assertNotSame(first, second);
    assertFalse(first.equals(second));

    Baseboard third = Baseboard.getInstance(0.7f, 2.5f, Integer.valueOf(0xff8800), null);
    assertNotSame(first, third);
    assertFalse(first.equals(third));
  }

  /**
   * Checks that threads concurrently deriving an equal style all receive the
   * one canonical cached instance without throwing.
   */
  public void testConcurrentDerivationsConvergeOnSeededNewestEntry() throws Exception {
    final TextStyle cached = new TextStyle("ValueInterningConcurrent", 11f, false, true);
    final TextStyle source = new TextStyle("ValueInterningOther", 8f, false, false);
    List<TextStyle> results = runConcurrently(new Callable<TextStyle>() {
        public TextStyle call() {
          return source.deriveStyle("ValueInterningConcurrent")
              .deriveStyle(11f)
              .deriveItalicStyle(true);
        }
      });
    for (TextStyle result : results) {
      assertSame("All concurrent derivations must return the seeded style",
          cached, result);
    }
  }

  /**
   * Checks that threads racing on the same yet uncached baseboard parameters
   * neither throw (the compound cache maintenance is atomic) nor diverge:
   * they all observe one canonical instance.
   */
  public void testConcurrentBaseboardRacingMissesConverge() throws Exception {
    List<Baseboard> results = runConcurrently(new Callable<Baseboard>() {
        public Baseboard call() {
          return Baseboard.getInstance(1.35f, 3.15f, Integer.valueOf(0x123456), null);
        }
      });
    Baseboard canonical = results.get(0);
    for (Baseboard result : results) {
      assertSame("Racing misses must converge on one canonical baseboard",
          canonical, result);
    }
  }

  /**
   * Checks that racing text style derivations of the same uncached parameters
   * also converge on one canonical instance.
   */
  public void testConcurrentTextStyleRacingMissesConverge() throws Exception {
    final TextStyle source = new TextStyle("ValueInterningRaceSource", 7f, false, false);
    List<TextStyle> results = runConcurrently(new Callable<TextStyle>() {
        public TextStyle call() {
          return source.deriveStyle("ValueInterningRaceTarget").deriveStyle(13.5f);
        }
      });
    TextStyle canonical = results.get(0);
    for (TextStyle result : results) {
      assertSame("Racing derivations must converge on one canonical style",
          canonical, result);
    }
  }

  /**
   * Checks that cleared weak references are dropped from the style cache when
   * a lookup sweeps it, verified reflectively without production test hooks.
   * Skipped silently if the VM doesn't confirm collection within the budget,
   * so the test never depends on timing.
   */
  public void testClearedStyleReferencesAreSweptFromCache() throws Exception {
    List<WeakReference<TextStyle>> cache = getTextStylesCache();
    TextStyle doomed = new TextStyle("ValueInterningDoomed", 21.5f, true, false);
    WeakReference<TextStyle> tracked = findReferenceTo(cache, doomed);
    assertNotNull("Newly created style should be cached", tracked);
    // Keep the instance strongly reachable until it is dropped below
    assertEquals(21.5f, doomed.getFontSize());

    doomed = null;
    if (!waitForReferenceClearing(tracked)) {
      return;
    }
    // A lookup missing everywhere traverses the whole cache and sweeps it
    TextStyle sweepResult = new TextStyle("ValueInterningSweepSource", 5f, false, false)
        .deriveStyle("ValueInterningNeverCachedSweep");
    assertNoClearedReferences(cache);
    // Keep the sweeping derivation strongly reachable until after the check
    assertEquals("ValueInterningNeverCachedSweep", sweepResult.getFontName());
  }

  /**
   * Checks that cleared weak references are dropped from the baseboard cache
   * the same way, skipped silently if collection isn't confirmed in time.
   */
  public void testClearedBaseboardReferencesAreSweptFromCache() throws Exception {
    List<WeakReference<Baseboard>> cache = getBaseboardsCache();
    Baseboard doomed = Baseboard.getInstance(2.25f, 4.75f, Integer.valueOf(0x0a0b0c), null);
    WeakReference<Baseboard> tracked = findReferenceTo(cache, doomed);
    assertNotNull("Interned baseboard should be cached", tracked);

    doomed = null;
    if (!waitForReferenceClearing(tracked)) {
      return;
    }
    Baseboard sweepResult = Baseboard.getInstance(9.5f, 19.5f, Integer.valueOf(0x0d0e0f), null);
    assertNoClearedReferences(cache);
    // Keep the sweeping lookup strongly reachable until after the check
    assertEquals(19.5f, sweepResult.getHeight());
  }

  /**
   * Runs the given lookup on separate threads and returns all results,
   * failing if any lookup throws.
   */
  @SuppressWarnings("unchecked")
  private <T> List<T> runConcurrently(final Callable<T> lookup) throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
    try {
      Future<T> [] futures = new Future [CONCURRENT_THREADS];
      for (int i = 0; i < CONCURRENT_THREADS; i++) {
        futures [i] = executor.submit(lookup);
      }
      T [] results = (T [])new Object [CONCURRENT_THREADS];
      for (int i = 0; i < CONCURRENT_THREADS; i++) {
        results [i] = futures [i].get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(results [i]);
      }
      return Arrays.asList(results);
    } finally {
      executor.shutdownNow();
    }
  }

  /**
   * Repeatedly forces collection until the given reference is confirmed
   * cleared, or gives up without failing to stay independent of VM timing.
   */
  private boolean waitForReferenceClearing(WeakReference<?> tracked)
      throws InterruptedException {
    for (int i = 0; i < GC_CONFIRMATION_ROUNDS; i++) {
      byte [] pressure = new byte [256 * 1024];
      pressure [0] = (byte)i;
      System.gc();
      Thread.sleep(10);
      if (tracked.get() == null) {
        return true;
      }
    }
    return false;
  }

  @SuppressWarnings("unchecked")
  private static List<WeakReference<TextStyle>> getTextStylesCache()
      throws Exception {
    return (List<WeakReference<TextStyle>>)getCachedFieldValue(TextStyle.class, "textStylesCache");
  }

  @SuppressWarnings("unchecked")
  private static List<WeakReference<Baseboard>> getBaseboardsCache()
      throws Exception {
    return (List<WeakReference<Baseboard>>)getCachedFieldValue(Baseboard.class, "baseboardsCache");
  }

  private static Object getCachedFieldValue(Class<?> clazz, String fieldName)
      throws Exception {
    java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
    field.setAccessible(true);
    return field.get(null);
  }

  private static <T> WeakReference<T> findReferenceTo(List<WeakReference<T>> cache,
                                                      T instance) {
    for (WeakReference<T> reference : cache) {
      if (reference.get() == instance) {
        return reference;
      }
    }
    return null;
  }

  private static <T> void assertNoClearedReferences(List<WeakReference<T>> cache) {
    for (int i = 0; i < cache.size(); i++) {
      assertTrue("Cache entry " + i + " out of " + cache.size()
              + " references a collected object",
          cache.get(i).get() != null);
    }
  }
}
