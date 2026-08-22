/*
 * IconManager.java 2 mai 2006
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
package com.eteks.sweethome3d.swing;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;

import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.tools.ResourceURLContent;

/**
 * Singleton managing icons cache.
 * @author Emmanuel Puybaret
 */
public class IconManager {
  // Icon used if an image content couldn't be loaded
  private final Content                          errorIconContent;
  // Icon used while an image content is loaded
  private final Content                          waitIconContent;
  // Map storing loaded icons. Guarded by its own monitor for structural changes,
  // while each inner per-content map is guarded by the monitor of that map.
  private final Map<Content, Map<Integer, Icon>> icons;
  // Lock ensuring atomic creation and shutdown of iconsLoader
  private final Object                           iconsLoaderLock = new Object();
  // Executor used by IconProxy to load images
  private ExecutorService                        iconsLoader;

  private IconManager() {
    this.errorIconContent = new ResourceURLContent(IconManager.class, "resources/error.png");
    this.waitIconContent = new ResourceURLContent(IconManager.class, "resources/wait.png");
    this.icons = new WeakHashMap<Content, Map<Integer, Icon>>();
  }

  /**
   * Holder deferring singleton construction until first use and guaranteeing
   * its safe publication to all threads.
   */
  private static class InstanceHolder {
    static final IconManager instance = new IconManager();
  }

  /**
   * Returns an instance of this singleton.
   */
  public static IconManager getInstance() {
    return InstanceHolder.instance;
  }

  /**
   * Clears the loaded resources cache and shutdowns the multithreaded service
   * that loads icons.
   */
  public void clear() {
    ExecutorService loader;
    synchronized (this.iconsLoaderLock) {
      loader = this.iconsLoader;
      this.iconsLoader = null;
    }
    if (loader != null) {
      loader.shutdownNow();
    }
    synchronized (this.icons) {
      this.icons.clear();
    }
  }

  /**
   * Returns the icon displayed for wrong content resized at a given height.
   */
  public Icon getErrorIcon(int height) {
    return getIcon(this.errorIconContent, height, null);
  }

  /**
   * Returns the icon displayed for wrong content.
   */
  public Icon getErrorIcon() {
    return getIcon(this.errorIconContent, -1, null);
  }

  /**
   * Returns <code>true</code> if the given <code>icon</code> is the error icon
   * used by this manager to indicate it couldn't load an icon.
   */
  public boolean isErrorIcon(Icon icon) {
    return isCachedIcon(this.errorIconContent, icon);
  }

  /**
   * Returns the icon displayed while a content is loaded resized at a given height.
   */
  public Icon getWaitIcon(int height) {
    return getIcon(this.waitIconContent, height, null);
  }

  /**
   * Returns the icon displayed while a content is loaded.
   */
  public Icon getWaitIcon() {
    return getIcon(this.waitIconContent, -1, null);
  }

  /**
   * Returns <code>true</code> if the given <code>icon</code> is the wait icon
   * used by this manager to indicate it's currently loading an icon.
   */
  public boolean isWaitIcon(Icon icon) {
    return isCachedIcon(this.waitIconContent, icon);
  }

  /**
   * Returns <code>true</code> if <code>icon</code>, or the icon hidden behind it
   * in case of a proxy, is stored in cache for <code>content</code>.
   */
  private boolean isCachedIcon(Content content, Icon icon) {
    Map<Integer, Icon> contentIcons;
    synchronized (this.icons) {
      contentIcons = this.icons.get(content);
    }
    if (contentIcons == null) {
      return false;
    }
    synchronized (contentIcons) {
      return contentIcons.containsValue(icon)
          || icon instanceof IconProxy
              && contentIcons.containsValue(((IconProxy)icon).getIcon());
    }
  }

  /**
   * Returns an icon read from <code>content</code>.
   * @param content an object containing an image
   * @param waitingComponent a waiting component. If <code>null</code>, the returned icon will
   *            be read immediately in the current thread.
   */
  public Icon getIcon(Content content, Component waitingComponent) {
    return getIcon(content, -1, waitingComponent);
  }

  /**
   * Returns an icon read from <code>content</code> and rescaled at a given <code>height</code>.
   * @param content an object containing an image
   * @param height  the desired height of the returned icon
   * @param waitingComponent a waiting component. If <code>null</code>, the returned icon will
   *            be read immediately in the current thread.
   */
  public Icon getIcon(Content content, final int height, Component waitingComponent) {
    // Get or create atomically the per-content icons map, so concurrent requests
    // for the same content always share the same cache entry
    Map<Integer, Icon> contentIcons;
    synchronized (this.icons) {
      contentIcons = this.icons.get(content);
      if (contentIcons == null) {
        contentIcons = new HashMap<Integer, Icon>();
        this.icons.put(content, contentIcons);
      }
    }

    // Resolve the error and wait icons before locking contentIcons to avoid
    // holding one content lock while acquiring another
    Icon errorIcon = null;
    Icon waitIcon = null;
    if (content != null && content != this.errorIconContent && content != this.waitIconContent) {
      errorIcon = getIcon(this.errorIconContent, height, null);
      if (waitingComponent != null) {
        waitIcon = getIcon(this.waitIconContent, height, null);
      }
    }

    synchronized (contentIcons) {
      Icon icon = contentIcons.get(height);
      if (icon == null) {
        if (content == null) {
          // Tolerate null content
          icon = new Icon() {
              public void paintIcon(Component c, Graphics g, int x, int y) {
              }

              public int getIconWidth() {
                return Math.max(0, height);
              }

              public int getIconHeight() {
                return Math.max(0, height);
              }
            };
        } else if (content == this.errorIconContent ||
                   content == this.waitIconContent) {
          // Load error and wait icons immediately in this thread
          icon = createIcon(content, height, null);
        } else if (waitingComponent == null) {
          // Load icon immediately in this thread
          icon = createIcon(content, height, errorIcon);
        } else {
          // For content different from error icon and wait icon,
          // load it in a different thread with a virtual proxy
          icon = new IconProxy(content, height, waitingComponent,
                   errorIcon, waitIcon);
        }
        // Store the icon in icons map
        contentIcons.put(height, icon);
      }
      return icon;
    }
  }

  /**
   * Returns an icon created and scaled from its content.
   * @param content the content from which the icon image is read
   * @param height  the desired height of the returned icon
   * @param errorIcon the returned icon in case of error
   */
  private Icon createIcon(Content content, int height, Icon errorIcon) {
    try {
      // Read the icon of the piece, closing the stream even if reading fails
      try (InputStream contentStream = content.openStream()) {
        BufferedImage image = ImageIO.read(contentStream);
        if (image != null) {
          if (height != -1 && height != image.getHeight()) {
            int width = Math.max(1, image.getWidth() * height / image.getHeight());
            // Halve the image while it's more than twice the requested size, a single
            // bilinear step sampling too few pixels to downscale that much smoothly
            BufferedImage reducedImage = image;
            int reducedWidth = image.getWidth();
            int reducedHeight = image.getHeight();
            while (reducedWidth > width * 2 || reducedHeight > height * 2) {
              reducedWidth = Math.max(width, reducedWidth / 2);
              reducedHeight = Math.max(height, reducedHeight / 2);
              reducedImage = getScaledImage(reducedImage, reducedWidth, reducedHeight);
            }
            // Create a scaled image not bound to original image to let the original image being garbage collected
            return new ImageIcon(getScaledImage(reducedImage, width, height));
          } else {
            return new ImageIcon(image);
          }
        }
      }
    } catch (IOException ex) {
      // Too bad, we'll use errorIcon
    }
    return errorIcon;
  }

  /**
   * Returns a new image of the given size in which <code>image</code> was drawn scaled.
   * @param image  the image to scale
   * @param width  the width of the returned image
   * @param height the height of the returned image
   */
  private static BufferedImage getScaledImage(BufferedImage image, int width, int height) {
    BufferedImage scaledImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2D = (Graphics2D)scaledImage.getGraphics();
    g2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g2D.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    g2D.drawImage(image, 0, 0, width, height, null);
    g2D.dispose();
    return scaledImage;
  }

  /**
   * Proxy icon that displays a temporary icon while waiting
   * image loading completion.
   */
  private class IconProxy implements Icon {
    // Volatile so the icon loaded by a background thread is safely and
    // promptly published to the threads painting or reading this proxy
    private volatile Icon icon;

    public IconProxy(final Content content, final int height,
                     final Component waitingComponent,
                     final Icon errorIcon, Icon waitIcon) {
      this.icon = waitIcon;
      // Get or create atomically the executor used to load images
      ExecutorService loader;
      synchronized (IconManager.this.iconsLoaderLock) {
        if (iconsLoader == null) {
          iconsLoader = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
        }
        loader = iconsLoader;
      }
      Runnable iconLoader = new Runnable () {
          public void run() {
            Icon loadedIcon = createIcon(content, height, errorIcon);
            IconProxy.this.icon = loadedIcon;
            waitingComponent.repaint();
          }
        };
      try {
        // Load the icon in a different thread
        loader.execute(iconLoader);
      } catch (RejectedExecutionException ex) {
        // The loader was shut down concurrently by clear(): fall back
        // to loading the icon in this thread so it's still displayed
        iconLoader.run();
      }
    }

    public int getIconWidth() {
      return this.icon.getIconWidth();
    }

    public int getIconHeight() {
      return this.icon.getIconHeight();
    }

    public void paintIcon(Component c, Graphics g, int x, int y) {
      this.icon.paintIcon(c, g, x, y);
    }

    public Icon getIcon() {
      return this.icon;
    }
  }
}
