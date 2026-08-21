/*
 * ConcurrentContentDigestTest.java 21 Aug 2026
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
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;

import com.eteks.sweethome3d.io.ContentDigestManager;
import com.eteks.sweethome3d.model.Content;

/**
 * Tests the concurrency guarantees of
 * {@link ContentDigestManager#getContentDigest(Content)}: digests of different
 * contents are computed concurrently, repeated lookups are served from the
 * cache, unreadable contents keep their invalid behavior, threads racing on
 * the same content share one computation, and explicit digests still override
 * computed ones.
 * @author DobryDom3D contributors
 */
public class ConcurrentContentDigestTest extends TestCase {
  private static final int BARRIER_TIMEOUT_SECONDS = 30;
  private static final int FUTURE_TIMEOUT_SECONDS = 60;

  /**
   * Proves without wall-clock thresholds that two cache misses for different
   * contents enter their digest computation at the same time: both streams can
   * only pass the shared barrier if both computations are in flight together.
   */
  public void testConcurrentCacheMissesComputeInParallel() throws Exception {
    CyclicBarrier bothComputationsInFlight = new CyclicBarrier(2);
    Content content1 = new BarrierContent("first content data", bothComputationsInFlight);
    Content content2 = new BarrierContent("second content data", bothComputationsInFlight);
    ContentDigestManager manager = ContentDigestManager.getInstance();
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<byte []> future1 = executor.submit(digestCall(manager, content1));
      Future<byte []> future2 = executor.submit(digestCall(manager, content2));
      byte [] digest1 = future1.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      byte [] digest2 = future2.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      // A serialized implementation would let only one stream reach the
      // barrier, making the other computation fail with a timeout and an
      // invalid digest instead of the expected values below
      assertEquals("Wrong digest for first content",
          sha1("first content data"), Arrays.toString(digest1));
      assertEquals("Wrong digest for second content",
          sha1("second content data"), Arrays.toString(digest2));
    } finally {
      executor.shutdownNow();
    }
  }

  /**
   * Checks that a digest looked up again after being computed comes from the
   * cache instead of opening the content stream once more.
   */
  public void testRepeatedLookupIsCached() throws Exception {
    CountedContent content = new CountedContent("cached content data");
    ContentDigestManager manager = ContentDigestManager.getInstance();
    byte [] digest = manager.getContentDigest(content);
    assertEquals("Wrong digest", sha1("cached content data"), Arrays.toString(digest));
    assertEquals("Content should be read once", 1, content.getOpenCount());
    byte [] secondDigest = manager.getContentDigest(content);
    assertTrue("Second lookup should be cached", Arrays.equals(digest, secondDigest));
    assertEquals("Second lookup shouldn't reopen the content", 1, content.getOpenCount());
  }

  /**
   * Checks that unreadable contents keep returning the shared invalid digest,
   * which compares unequal to any other content or digest.
   */
  public void testUnreadableContentKeepsInvalidDigestBehavior() {
    UnreadableContent unreadableContent = new UnreadableContent();
    CountedContent readableContent = new CountedContent("readable content data");
    ContentDigestManager manager = ContentDigestManager.getInstance();
    byte [] digest = manager.getContentDigest(unreadableContent);
    assertEquals("Invalid digest should be empty", 0, digest.length);
    assertSame("Both invalid lookups must return the shared invalid digest instance",
        digest, manager.getContentDigest(unreadableContent));
    assertFalse("Invalid content should equal no content",
        manager.equals(unreadableContent, readableContent));
    assertFalse("Invalid content digest should match no digest",
        manager.isContentDigestEqual(unreadableContent, sha1Bytes("readable content data")));
    assertFalse(manager.equals(unreadableContent, new UnreadableContent()));
  }

  /**
   * Checks that threads racing on the same uncached content share one
   * computation and all receive the same consistent digest.
   */
  public void testSameContentRaceSharesOneComputation() throws Exception {
    final int racingThreads = 4;
    final CountedContent content = new CountedContent("raced content data");
    final ContentDigestManager manager = ContentDigestManager.getInstance();
    final CountDownLatch startGate = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(racingThreads);
    try {
      Future<byte []> [] futures = new Future [racingThreads];
      for (int i = 0; i < racingThreads; i++) {
        futures [i] = executor.submit(new Callable<byte []>() {
            public byte [] call() throws Exception {
              if (!startGate.await(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Start gate not reached");
              }
              return manager.getContentDigest(content);
            }
          });
      }
      startGate.countDown();
      byte [] referenceDigest = futures [0].get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      assertEquals("Wrong digest", sha1("raced content data"),
          Arrays.toString(referenceDigest));
      for (int i = 1; i < racingThreads; i++) {
        assertSame("All racing threads must share the published digest",
            referenceDigest, futures [i].get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
      }
      assertEquals("Racing threads should trigger a single computation",
          1, content.getOpenCount());
    } finally {
      executor.shutdownNow();
    }
  }

  /**
   * Checks that a digest set explicitly is returned without computing anything.
   */
  public void testSetContentDigestOverridesComputation() {
    CountedContent content = new CountedContent("overridden content data");
    byte [] explicitDigest = {1, 2, 3, 4};
    ContentDigestManager manager = ContentDigestManager.getInstance();
    manager.setContentDigest(content, explicitDigest);
    assertSame(manager.getContentDigest(content), explicitDigest);
    assertEquals("Explicitly set digest shouldn't open the content",
        0, content.getOpenCount());
  }

  private static Callable<byte []> digestCall(final ContentDigestManager manager,
                                              final Content content) {
    return new Callable<byte []>() {
      public byte [] call() {
        return manager.getContentDigest(content);
      }
    };
  }

  private static String sha1(String data) throws NoSuchAlgorithmException {
    return Arrays.toString(sha1Bytes(data));
  }

  private static byte [] sha1Bytes(String data) {
    try {
      MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
      return messageDigest.digest(data.getBytes());
    } catch (NoSuchAlgorithmException ex) {
      throw new InternalError(ex);
    }
  }

  /**
   * A content whose stream waits at a barrier before delivering its data.
   */
  private static class BarrierContent implements Content {
    private final byte []        data;
    private final CyclicBarrier  barrier;

    BarrierContent(String data, CyclicBarrier barrier) {
      this.data = data.getBytes();
      this.barrier = barrier;
    }

    public InputStream openStream() throws IOException {
      try {
        // Reached only if another digest computation runs at the same time
        this.barrier.await(BARRIER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      } catch (Exception ex) {
        IOException ex2 = new IOException("Digest computations didn't run concurrently");
        ex2.initCause(ex);
        throw ex2;
      }
      return new ByteArrayInputStream(this.data);
    }
  }

  /**
   * A content counting how many times its stream was opened.
   */
  private static class CountedContent implements Content {
    private final byte [] data;
    private int           openCount;

    CountedContent(String data) {
      this.data = data.getBytes();
    }

    public InputStream openStream() {
      this.openCount++;
      return new ByteArrayInputStream(this.data);
    }

    int getOpenCount() {
      return this.openCount;
    }
  }

  /**
   * A content that can't be opened.
   */
  private static class UnreadableContent implements Content {
    public InputStream openStream() throws IOException {
      throw new IOException("Unreadable content");
    }
  }
}
