# Title
Menu popups displaced to screen's left-inset edge when menu lies within a reported (but not actually reserved) left inset — regression from JDK 22

# Component / Subcomponent
client-libs / javax.swing

# Affects Version/s
22, 23, 24, 25, 26 (not 21)

# Environment
Linux / X11 (reproduced on XWayland, GNOME Mutter, two monitors with one docked to the left of the other). Not OS-specific in principle; only needs a GraphicsConfiguration whose Toolkit.getScreenInsets(gc).left is larger than the menu's x within that screen.

# Description
Since JDK 22, JMenu.getPopupMenuOrigin() (javax/swing/JMenu.java, lines ~392–498) added a final "keep the popup on the correct screen" clamp (comment cites JDK-6415065). The clamp subtracts the screen's left inset from the menu's on-screen x, then snaps the popup origin so the popup lands at the *usable area's left edge* whenever `position.x + x < screenBounds.x`:

    position.x -= Math.abs(screenInsets.left);          // JMenu.java:401
    ...
    if (position.x + x < screenBounds.x) {              // JMenu.java:492
        x = screenBounds.x - position.x;                // JMenu.java:493
    }

On multi-monitor X11 desktops the EWMH `_NET_WORKAREA` is a single global desktop rectangle, so Toolkit.getScreenInsets(gc) for a monitor that has no real strut on it can still report a large `left` inset (the global work area's left edge projected onto that monitor). In that case a top-level menu whose screen x is smaller than the reported `left` inset gets its popup snapped to `x = screenInsets.left`, far to the right of the menu — and *all* top-level menus in the bar land at the same x.

This is a behavioral regression: JDK 21 and earlier do not clamp here and position the popup directly under the menu.

# Reproduction
Run the attached `JMenuPopupInsetBug.java`:

    javac JMenuPopupInsetBug.java
    java JMenuPopupInsetBug

The reproducer uses only public APIs and requires no module-opening options. It iterates screen devices and, for any device whose `getScreenInsets().left > 0`, opens a window at that screen's left edge, displays the first menu, and compares the actual menu and popup screen locations.

To create the trigger on a two-monitor XWayland desktop, keep a left-side panel/dock visible on the OTHER monitor so the monitor under test reports a nonzero left inset.

# Expected vs actual (observed, JDK 25 / Temurin 25.0.4, two-monitor XWayland, ultra-wide at x=0 reporting insets.left=899)
JDK 21: popup @ (1, 59), shift (0, 21)  -> correct (under the File menu)
JDK 25: popup @ (899, 59), shift (898, 21) for File, Edit and Plan alike -> all menus land at the same x = screenInsets.left

getPopupMenuOrigin() returns (898, 21) under JDK 25 (vs the menu-aligned origin in JDK 21); desiredLocation therefore becomes menu.x + origin.x = 1 + 898 = 899 = screenInsets.left, independent of the menu clicked.

# Regression range
- 17, 18, 20, 21: correct
- 22, 23, 24, 25 (Temurin 25.0.4), 26: wrong

The clamp block is absent in JDK 21's JMenu.getPopupMenuOrigin() (it just `return new Point(x,y);`) and appears in JDK 22 with the comment citing JDK-6415065.

# Suggested fix
The clamp compares a position expressed in *inset-adjusted* coordinates (`position.x -= insets.left`) against `screenBounds.x`, which is the *raw* GC x (only screenBounds.width/height are reduced by insets, not screenBounds.x/y). That coordinate-space mismatch is what makes a nonzero left inset push every left-edge menu to the inset edge.

Either:
- clamp against the raw screen edge (don't subtract insets from `position` before this guard), so the popup stays within gc.getBounds() but isn't pushed by phantom insets; or
- consistently shift screenBounds.x/y by the insets too and compare in the same space.

The current behaviour trusts per-monitor insets derived from the single global `_NET_WORKAREA`; making the guard inset-insensitive would fix the multi-monitor projection case while still keeping popups on the correct screen.