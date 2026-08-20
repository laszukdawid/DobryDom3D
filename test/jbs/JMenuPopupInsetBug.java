/*
 * Reproduction for: top-level JMenu popup snapped far right when the
 * monitor it lives on reports a nonzero left screen inset.
 *
 * Regression introduced in JDK 22 by the "keep popup on correct screen"
 * clamp in JMenu.getPopupMenuOrigin() (citing JDK-6415065).
 *
 * Trigger condition: a GraphicsConfiguration whose
 * Toolkit.getScreenInsets(gc).left > menuOnScreenLocalX. A dock or panel
 * reserved as a strut on a multi-monitor desktop is the common cause;
 * Mutter/XWayland can apply one monitor's reservation globally.
 *
 * Build:  javac JMenuPopupInsetBug.java
 * Run:    java JMenuPopupInsetBug
 *
 * If no screen has a nonzero left inset, the program prints "no-trigger"
 * because the environment doesn't expose the bug. To reproduce on GNOME,
 * keep a left-side dock visible on one monitor and place the test window at
 * the left edge of another monitor which inherits that global work-area
 * inset.
 */
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class JMenuPopupInsetBug {
  public static void main(String[] args) throws Exception {
    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    SwingUtilities.invokeAndWait(() -> {
      GraphicsEnvironment environment =
          GraphicsEnvironment.getLocalGraphicsEnvironment();
      Toolkit toolkit = Toolkit.getDefaultToolkit();
      boolean triggerFound = false;
      for (GraphicsDevice device : environment.getScreenDevices()) {
        GraphicsConfiguration configuration = device.getDefaultConfiguration();
        Rectangle bounds = configuration.getBounds();
        Insets insets = toolkit.getScreenInsets(configuration);
        System.out.println("screen " + device.getIDstring() + " " + bounds
            + " insets=" + insets);
        if (insets.left > 0) {
          triggerFound = true;
          reproduce(configuration, bounds, insets);
        } else {
          System.out.println("  (no left inset -> no trigger on this screen)");
        }
      }
      if (!triggerFound) {
        System.out.println("no-trigger");
      }
    });
  }

  private static void reproduce(GraphicsConfiguration configuration,
                                Rectangle screenBounds,
                                Insets screenInsets) {
    JFrame frame = new JFrame("JMenu popup inset probe", configuration);
    frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

    JMenuBar menuBar = new JMenuBar();
    JMenu fileMenu = new JMenu("File");
    fileMenu.add("New");
    fileMenu.add("Open");
    fileMenu.add("Save");
    menuBar.add(fileMenu);
    frame.setJMenuBar(menuBar);

    int width = Math.max(200, Math.min(1200, screenBounds.width - 40));
    int height = Math.max(100, Math.min(700, screenBounds.height - 40));
    frame.setBounds(screenBounds.x, screenBounds.y, width, height);
    frame.setVisible(true);

    try {
      fileMenu.setPopupMenuVisible(true);
      JPopupMenu popupMenu = fileMenu.getPopupMenu();
      Point menuLocation = fileMenu.getLocationOnScreen();
      Point popupLocation = popupMenu.getLocationOnScreen();
      long displacement = (long)popupLocation.x - menuLocation.x;

      System.out.println("  menu=" + menuLocation
          + " popup=" + popupLocation
          + " displacement=" + displacement
          + " (left inset=" + screenInsets.left + ")");
      if (Math.abs(displacement) > 5) {
        System.out.println("  >>> BUG: popup displaced by ~" + displacement
            + "px; expected near menu.x=" + menuLocation.x
            + ", got " + popupLocation.x);
      } else {
        System.out.println("  ok");
      }
      fileMenu.setPopupMenuVisible(false);
    } finally {
      frame.dispose();
    }
  }
}
