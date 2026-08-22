/*
 * IconManagerTest.java 4 mai 2006
 *
 * Copyright (c) 2024 Space Mushrooms <info@sweethome3d.com>
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

import java.awt.Component;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import junit.framework.TestCase;

import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.swing.IconManager;
import com.eteks.sweethome3d.tools.URLContent;

/**
 * Tests IconManager class.
 * @author Emmanuel Puybaret
 */
public class IconManagerTest extends TestCase {
  private final int HEIGHT = 32;
  // Tolerance on each ARGB component between an icon scaled with bilinear steps and the
  // same icon scaled with area averaging, which differ only by rounding (measured at 1)
  private final int MAX_COMPONENT_DIFFERENCE = 4;

  public void testIconManager()
      throws NoSuchFieldException, IllegalAccessException, InterruptedException, BrokenBarrierException, ClassNotFoundException {
    // Stop iconsLoader of iconManager
    IconManager iconManager = IconManager.getInstance();
    iconManager.clear();
    // Replace icon manager by an executor that controls the start of a task with a barrier
    final CyclicBarrier iconLoadingStartBarrier = new CyclicBarrier(2);
    final ThreadPoolExecutor replacingIconsLoader =
      new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>()) {
        @Override
        protected void beforeExecute(Thread t, Runnable r) {
          super.beforeExecute(t, r);
          awaitBarrier(iconLoadingStartBarrier);
        }
    };
    // Redirect rejected tasks on iconsLoader to the replacing executor
    TestUtilities.setField(iconManager, "iconsLoader", replacingIconsLoader);

    // Test icon loading on a good image
    testIconLoading(getClass().getResource("resources/test.png"), true, iconLoadingStartBarrier);
    // Test icon loading on a content that doesn't an image
    testIconLoading(getClass().getResource("IconManagerTest.class"), false, iconLoadingStartBarrier);

    Class iconProxyClass = Class.forName(iconManager.getClass().getName() + "$IconProxy");
    URLContent waitIconContent =
        (URLContent)TestUtilities.getField(iconManager, "waitIconContent");
    URLContent errorIconContent =
        (URLContent)TestUtilities.getField(iconManager, "errorIconContent");

    // Check waitIcon is loaded directly without proxy
    Icon waitIcon = iconManager.getIcon(waitIconContent, HEIGHT, null);
    assertNotSame("Wait icon loaded with IconProxy", waitIcon.getClass(), iconProxyClass);

    // Check errorIcon is loaded directly without proxy
    Icon errorIcon = iconManager.getIcon(errorIconContent, HEIGHT, null);
    assertNotSame("Error icon loaded with IconProxy", errorIcon.getClass(), iconProxyClass);

    // For other tests, replace again iconLoader by an executor that let icon loading complete normally
    iconManager.clear();
  }

  /**
   * Test how an icon is loaded by IconManager.
   */
  private void testIconLoading(URL iconURL, boolean goodIcon, CyclicBarrier iconLoadingStartBarrier)
      throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException, InterruptedException, BrokenBarrierException {
    IconManager iconManager = IconManager.getInstance();
    Class iconProxyClass = Class.forName(iconManager.getClass().getName() + "$IconProxy");

    URLContent waitIconContent =
        (URLContent)TestUtilities.getField(iconManager, "waitIconContent");
    URLContent errorIconContent =
        (URLContent)TestUtilities.getField(iconManager, "errorIconContent");

    final CyclicBarrier waitingComponentBarrier = new CyclicBarrier(2);
    // A dummy waiting component that waits on a barrier in its repaint method
    Component waitingComponent = new Component() {
      public void repaint() {
        awaitBarrier(waitingComponentBarrier);
      }
    };

    Content iconContent = new URLContent(iconURL);
    Icon icon = iconManager.getIcon(iconContent, HEIGHT, waitingComponent);
    assertEquals("Icon not equal to wait icon while loading", waitIconContent.getURL(), icon);

    // Let iconManager load the iconContent
    iconLoadingStartBarrier.await();
    // Wait iconContent loading completion
    waitingComponentBarrier.await();
    if (goodIcon) {
      assertEquals("Icon not equal to icon read from resource", iconURL, icon);
    } else {
      assertEquals("Wrong icon not equal to errorIcon", errorIconContent.getURL(), icon);
    }

    // Check icon is loaded with proxy
    assertSame("Icon not loaded with IconProxy", icon.getClass(), iconProxyClass);

    // Check that icon is stored in cache
    Icon iconFromCache = iconManager.getIcon(iconContent, HEIGHT, waitingComponent);
    assertSame("Test icon reloaded", icon, iconFromCache);
  }

  private void awaitBarrier(CyclicBarrier barrier) {
    try {
      barrier.await();
    } catch (Exception ex) {
      fail();
    }
  }

  /**
   * Returns a bounded wait on <code>latch</code> failing the test if it isn't
   * counted down in time.
   */
  private void awaitLatch(CountDownLatch latch) throws InterruptedException {
    assertTrue("Timed out waiting for an expected event",
        latch.await(30, TimeUnit.SECONDS));
  }

  /**
   * Returns a dummy component that counts down <code>repainted</code>
   * when IconManager asks it to repaint.
   */
  private Component waitingComponent(final CountDownLatch repainted) {
    return new Component() {
      public void repaint() {
        repainted.countDown();
      }
    };
  }

  /**
   * Content counting how many times it was opened.
   */
  private static class CountingContent implements Content {
    private final URLContent wrappedContent;
    final AtomicInteger openCount = new AtomicInteger();

    CountingContent(URL url) {
      this.wrappedContent = new URLContent(url);
    }

    public InputStream openStream() throws IOException {
      this.openCount.incrementAndGet();
      return this.wrappedContent.openStream();
    }
  }

  /**
   * Content blocking in <code>openStream</code> until its gate is opened,
   * to simulate a load that never completes on its own.
   */
  private static class GatedContent implements Content {
    private final CountDownLatch opened = new CountDownLatch(1);
    private final CountDownLatch gate = new CountDownLatch(1);

    public InputStream openStream() throws IOException {
      this.opened.countDown();
      try {
        // Wait at most 30 seconds to keep a stuck test from hanging forever
        if (!this.gate.await(30, TimeUnit.SECONDS)) {
          throw new IOException("Gate content abandoned");
        }
      } catch (InterruptedException ex) {
        throw new IOException("Gate content interrupted", ex);
      }
      throw new IOException("Gate content never provides data");
    }

    boolean awaitOpened() throws InterruptedException {
      return this.opened.await(30, TimeUnit.SECONDS);
    }

    void openGate() {
      this.gate.countDown();
    }
  }

  /**
   * Content whose stream always fails once read and remembers
   * whether that stream was closed.
   */
  private static class FailingContent implements Content {
    boolean streamClosed;

    public InputStream openStream() {
      return new InputStream() {
        @Override
        public int read() throws IOException {
          throw new IOException("Stream failure");
        }

        @Override
        public void close() {
          FailingContent.this.streamClosed = true;
        }
      };
    }
  }

  /**
   * Tests that concurrent identical requests share one cache entry and one load.
   */
  public void testConcurrentIdenticalRequestsShareOneLoad()
      throws InterruptedException, BrokenBarrierException, IOException {
    IconManager iconManager = IconManager.getInstance();
    iconManager.clear();

    URL iconURL = getClass().getResource("resources/test.png");
    final CountingContent content = new CountingContent(iconURL);
    final CountDownLatch repainted = new CountDownLatch(1);
    final Component waitingComponent = waitingComponent(repainted);

    final int requestingThreadCount = 8;
    CyclicBarrier startBarrier = new CyclicBarrier(requestingThreadCount);
    final Icon [] results = new Icon [requestingThreadCount];
    Thread [] requestingThreads = new Thread [requestingThreadCount];
    for (int i = 0; i < requestingThreadCount; i++) {
      final int index = i;
      requestingThreads [index] = new Thread(new Runnable() {
          public void run() {
            awaitBarrier(startBarrier);
            results [index] = IconManager.getInstance().getIcon(content, HEIGHT, waitingComponent);
          }
        });
      requestingThreads [index].start();
    }
    for (Thread requester : requestingThreads) {
      requester.join(TimeUnit.SECONDS.toMillis(30));
      assertFalse("Requesting thread didn't complete", requester.isAlive());
    }

    for (int i = 1; i < requestingThreadCount; i++) {
      assertSame("Concurrent requests returned different icons", results [0], results [i]);
    }
    // Wait until the single background load completed
    awaitLatch(repainted);
    assertEquals("Content opened " + content.openCount.get() + " times instead of once",
        1, content.openCount.get());
    assertFalse("Loaded icon not visible from another thread", iconManager.isWaitIcon(results [0]));

    iconManager.clear();
  }

  /**
   * Tests that a content blocked during its load doesn't prevent other contents
   * from loading concurrently.
   */
  public void testDifferentContentsLoadIndependently() throws InterruptedException, IOException {
    IconManager iconManager = IconManager.getInstance();
    iconManager.clear();

    final GatedContent gatedContent = new GatedContent();
    Thread gatedLoader = new Thread(new Runnable() {
        public void run() {
          iconManager.getIcon(gatedContent, HEIGHT, null);
        }
      });
    gatedLoader.start();
    assertTrue("Gated content was never opened", gatedContent.awaitOpened());
    // The gated load is now blocked mid-read; another content must still load fully
    Icon otherIcon = iconManager.getIcon(new URLContent(getClass().getResource("resources/test.png")),
        HEIGHT, null);
    assertFalse("Other content couldn't load while first content was blocked",
        iconManager.isErrorIcon(otherIcon));

    // Let the gated load finish (with its error fallback) and check its thread ends
    gatedContent.openGate();
    gatedLoader.join(TimeUnit.SECONDS.toMillis(30));
    assertFalse("Gated loader thread didn't complete", gatedLoader.isAlive());

    iconManager.clear();
  }

  /**
   * Tests that the manager still works after clear() and reloads cleared content.
   */
  public void testClearThenReuseReloadsContent() throws InterruptedException, IOException {
    IconManager iconManager = IconManager.getInstance();
    iconManager.clear();
    URL iconURL = getClass().getResource("resources/test.png");

    CountDownLatch firstRepaint = new CountDownLatch(1);
    Icon firstIcon = iconManager.getIcon(new CountingContent(iconURL), HEIGHT,
        waitingComponent(firstRepaint));
    awaitLatch(firstRepaint);
    assertFalse("First icon not loaded", iconManager.isWaitIcon(firstIcon));

    iconManager.clear();

    CountingContent reloadedContent = new CountingContent(iconURL);
    CountDownLatch secondRepaint = new CountDownLatch(1);
    Icon secondIcon = iconManager.getIcon(reloadedContent, HEIGHT,
        waitingComponent(secondRepaint));
    awaitLatch(secondRepaint);
    assertNotSame("Cleared content wasn't reloaded", firstIcon, secondIcon);
    assertFalse("Reloaded icon not loaded", iconManager.isWaitIcon(secondIcon));
    assertEquals("Content opened " + reloadedContent.openCount.get() + " times instead of once after clear",
        1, reloadedContent.openCount.get());

    iconManager.clear();
  }

  /**
   * Tests that getInstance() publishes one shared instance to all threads.
   */
  public void testGetInstanceReturnsOneSharedInstance()
      throws InterruptedException, BrokenBarrierException {
    final int threadCount = 8;
    CyclicBarrier startBarrier = new CyclicBarrier(threadCount);
    final IconManager [] instances = new IconManager [threadCount];
    Thread [] threads = new Thread [threadCount];
    for (int i = 0; i < threadCount; i++) {
      final int index = i;
      threads [index] = new Thread(new Runnable() {
          public void run() {
            awaitBarrier(startBarrier);
            instances [index] = IconManager.getInstance();
          }
        });
      threads [index].start();
    }
    for (Thread thread : threads) {
      thread.join(TimeUnit.SECONDS.toMillis(30));
      assertFalse("getInstance() thread didn't complete", thread.isAlive());
    }
    for (int i = 0; i < threadCount; i++) {
      assertNotNull(instances [i]);
      assertSame("Threads got different singleton instances", instances [0], instances [i]);
    }
  }

  /**
   * Tests that a stream whose read fails is closed anyway.
   */
  public void testThrowingStreamIsClosedOnFailure() {
    IconManager iconManager = IconManager.getInstance();
    FailingContent content = new FailingContent();
    Icon icon = iconManager.getIcon(content, HEIGHT, null);
    assertSame("Failing content didn't fall back to error icon",
        iconManager.getErrorIcon(HEIGHT), icon);
    assertTrue("Stream not closed after ImageIO.read threw", content.streamClosed);
  }

  /**
   * Asserts icons in parameter at same size contains the same image data.
   * IconManager scales icons with bilinear steps whereas the expected image below is
   * scaled with area averaging, so image data is compared with a small tolerance.
   */
  private void assertEquals(String message, URL expectedIconURL, Icon actualIcon) {
    ImageIcon expectedIcon = new ImageIcon(expectedIconURL);
    Image scaledExpectedImage = expectedIcon.getImage()
        .getScaledInstance(actualIcon.getIconWidth(),
            actualIcon.getIconHeight(), Image.SCALE_SMOOTH);
    int maxDifference = getMaxComponentDifference(getIconData(new ImageIcon(scaledExpectedImage)),
        getIconData(actualIcon));
    assertTrue(message + " (max ARGB component difference " + maxDifference + ")",
        maxDifference <= MAX_COMPONENT_DIFFERENCE);
  }

  /**
   * Returns the largest difference between the components of the ARGB pixels in parameter.
   */
  private int getMaxComponentDifference(int [] expectedImageData, int [] imageData) {
    assertEquals("Different image data size", expectedImageData.length, imageData.length);
    int maxDifference = 0;
    for (int i = 0; i < imageData.length; i++) {
      for (int shift = 0; shift < 32; shift += 8) {
        maxDifference = Math.max(maxDifference,
            Math.abs(((expectedImageData [i] >>> shift) & 0xFF) - ((imageData [i] >>> shift) & 0xFF)));
      }
    }
    return maxDifference;
  }

  private int [] getIconData(Icon icon) {
    BufferedImage image = new BufferedImage(icon.getIconWidth(),
        icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
    icon.paintIcon(null, image.getGraphics(), 0, 0);
    int [] imageData = new int [icon.getIconWidth() * icon.getIconHeight()];
    return image.getRGB(0, 0, icon.getIconWidth(), icon.getIconHeight(),
        imageData, 0, icon.getIconWidth());
  }
}
