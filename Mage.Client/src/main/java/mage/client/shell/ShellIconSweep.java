package mage.client.shell;

import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ContainerEvent;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;

/**
 * Phase 2 icons: modernises the deck-editor / lobby button artwork that is loaded ad-hoc as
 * {@code new ImageIcon(getClass().getResource("/buttons/x.png"))} across many (mostly
 * NetBeans-generated) files, which have no central loading funnel.
 * <p>
 * Rather than editing dozens of generated call sites (which would break the survive-upstream goal),
 * this walks the component tree of each window as it opens and swaps any icon whose source path
 * (recorded in {@link ImageIcon#getDescription()}) matches a {@code /buttons/<name>.png} for which
 * {@link ShellIcons} has a modern glyph. Icons we don't render (mana colours, card types, …) are
 * left untouched. The replacement keeps the original description, so re-sweeping on a theme change
 * simply re-renders in the new colours.
 * <p>
 * Survivability: there is <b>no new upstream seam</b> — {@link #install()} is called from
 * {@link Shell#installLookAndFeel()} (which already runs once at startup, behind the shell flag).
 *
 * @author modern-shell
 */
public final class ShellIconSweep {

    private static final String[] MARKERS = {"/buttons/", "/menu/"};
    // A few icons are loaded as bare classpath resources (no /buttons/ prefix); match these by name.
    private static final java.util.Set<String> ALLOW_BARE = new java.util.HashSet<>(java.util.Arrays.asList(
            "editor_insert_row.png", "editor_insert_col.png"));
    // Some controls hard-code an absurd font size (e.g. the 48pt main toolbar). Clamp anything
    // larger than MAX_FONT down to TARGET_FONT so the UI reads at a modern size.
    private static final int MAX_FONT = 30;
    private static final int TARGET_FONT = 14;
    // Original /buttons/ and /menu/ PNGs are small; render replacements larger so they're legible.
    private static final float ICON_SCALE = 1.45f;
    private static final int MIN_ICON = 28;
    private static final int MAX_ICON = 64;
    private static boolean listenerInstalled = false;

    private ShellIconSweep() {
    }

    /**
     * Install the window listener (once) and sweep any already-open windows. Safe to call repeatedly
     * (e.g. on every theme refresh) — the listener is only added once and re-sweeps re-render icons.
     */
    public static synchronized void install() {
        if (!listenerInstalled) {
            Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
                // Top-level windows (dialogs, frames) fire WINDOW_OPENED.
                if (event instanceof WindowEvent && event.getID() == WindowEvent.WINDOW_OPENED) {
                    Window w = ((WindowEvent) event).getWindow();
                    SwingUtilities.invokeLater(() -> sweepRealized(w));
                }
                // XMage's panes (deck editor, collection, tables) are JLayeredPane panels added to
                // the desktop on demand - not Windows - so catch them when added to a layered pane.
                if (event instanceof ContainerEvent && event.getID() == ContainerEvent.COMPONENT_ADDED) {
                    ContainerEvent ce = (ContainerEvent) event;
                    if (ce.getContainer() instanceof JLayeredPane) {
                        Component child = ce.getChild();
                        SwingUtilities.invokeLater(() -> sweepRealized(child));
                    }
                }
            }, AWTEvent.WINDOW_EVENT_MASK | AWTEvent.CONTAINER_EVENT_MASK);
            listenerInstalled = true;
        }
        for (Window w : Window.getWindows()) {
            if (w.isShowing()) {
                sweepRealized(w);
            }
        }
    }

    /**
     * Force the current look-and-feel onto the whole subtree — the same thing a theme reselect does —
     * so the shell applies on first render even though XMage builds/shows its UI in stages. Then
     * apply our modern icons / fonts / borders (after, so they win over the LAF defaults).
     */
    private static void sweepRealized(Component c) {
        if (c == null) {
            return;
        }
        try {
            SwingUtilities.updateComponentTreeUI(c);
        } catch (RuntimeException ignore) {
            // never let a cosmetic pass break the app
        }
        sweep(c);
    }

    private static void sweep(Component c) {
        clampFont(c);
        if (c instanceof JComponent) {
            flattenBorder((JComponent) c);
        }
        if (c instanceof AbstractButton) {
            sweepButton((AbstractButton) c);
        } else if (c instanceof JLabel) {
            JLabel label = (JLabel) c;
            Icon modern = modern(label.getIcon());
            if (modern != null) {
                label.setIcon(modern);
            }
        }
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                sweep(child);
            }
        }
    }

    /** Shrink hard-coded oversized fonts (idempotent: a re-sweep sees the already-clamped size). */
    private static void clampFont(Component c) {
        Font f = c.getFont();
        if (f != null && f.getSize() > MAX_FONT) {
            c.setFont(f.deriveFont((float) TARGET_FONT));
        }
    }

    /** Replace dated etched/bevel/grey-line borders with a flat modern line. */
    private static void flattenBorder(JComponent c) {
        Border b = c.getBorder();
        Border flat = flatten(b);
        if (flat != b) {
            c.setBorder(flat);
        }
    }

    private static Border flatten(Border b) {
        if (b == null || b instanceof ShellFlatBorder) {
            return b;
        }
        if (b instanceof EtchedBorder || b instanceof BevelBorder) {
            return flatLine();
        }
        if (b instanceof LineBorder) {
            return isGrey(((LineBorder) b).getLineColor()) ? flatLine() : b;
        }
        if (b instanceof TitledBorder) {
            TitledBorder tb = (TitledBorder) b;
            Border inner = tb.getBorder();
            Border flatInner = flatten(inner);
            if (flatInner != inner) {
                tb.setBorder(flatInner);
            }
            return tb;
        }
        if (b instanceof CompoundBorder) {
            CompoundBorder cb = (CompoundBorder) b;
            Border out = flatten(cb.getOutsideBorder());
            Border in = flatten(cb.getInsideBorder());
            if (out != cb.getOutsideBorder() || in != cb.getInsideBorder()) {
                return new CompoundBorder(out, in);
            }
            return b;
        }
        return b; // leave EmptyBorder, MatteBorder, FlatLaf's own borders, etc.
    }

    private static boolean isGrey(Color c) {
        if (c == null) {
            return false;
        }
        int r = c.getRed(), g = c.getGreen(), bl = c.getBlue();
        return Math.abs(r - g) < 24 && Math.abs(g - bl) < 24 && Math.abs(r - bl) < 24;
    }

    private static Border flatLine() {
        Color col = UIManager.getColor("Component.borderColor");
        if (col == null) {
            col = new Color(0x3A3A3A);
        }
        return new ShellFlatBorder(col);
    }

    /** Marker so a later re-sweep recognises our own flat borders and leaves them alone. */
    private static final class ShellFlatBorder extends LineBorder {
        ShellFlatBorder(Color color) {
            super(color, 1);
        }
    }

    private static void sweepButton(AbstractButton b) {
        Icon i;
        if ((i = modern(b.getIcon())) != null) {
            b.setIcon(i);
        }
        if ((i = modern(b.getPressedIcon())) != null) {
            b.setPressedIcon(i);
        }
        if ((i = modern(b.getSelectedIcon())) != null) {
            b.setSelectedIcon(i);
        }
        if ((i = modern(b.getRolloverIcon())) != null) {
            b.setRolloverIcon(i);
        }
        if ((i = modern(b.getRolloverSelectedIcon())) != null) {
            b.setRolloverSelectedIcon(i);
        }
    }

    /**
     * @return a modern replacement icon for the given icon if it was loaded from a {@code /buttons/}
     * resource we have a glyph for; otherwise {@code null} (leave the original in place).
     */
    private static Icon modern(Icon icon) {
        if (!(icon instanceof ImageIcon)) {
            return null;
        }
        ImageIcon ii = (ImageIcon) icon;
        String desc = ii.getDescription();
        if (desc == null) {
            return null;
        }
        int markerEnd = -1;
        for (String marker : MARKERS) {
            int idx = desc.indexOf(marker);
            if (idx >= 0) {
                markerEnd = idx + marker.length();
                break;
            }
        }
        String name;
        if (markerEnd >= 0) {
            name = desc.substring(markerEnd);
        } else {
            int slash = Math.max(desc.lastIndexOf('/'), desc.lastIndexOf('\\'));
            String base = slash >= 0 ? desc.substring(slash + 1) : desc;
            if (!ALLOW_BARE.contains(base.toLowerCase())) {
                return null;
            }
            name = base;
        }
        int q = name.indexOf('?');
        if (q >= 0) {
            name = name.substring(0, q);
        }
        int w = ii.getIconWidth();
        int h = ii.getIconHeight();
        if (w <= 0 || h <= 0) {
            return null;
        }
        // The original PNGs are tiny (e.g. 24px), which reads as a small icon. Render a larger,
        // square icon so it's comfortably visible; buttons that size to content grow to fit.
        int target = Math.min(MAX_ICON, Math.max(MIN_ICON, Math.round(Math.max(w, h) * ICON_SCALE)));
        BufferedImage img = ShellIcons.renderButton(name, target, target);
        if (img == null) {
            return null;
        }
        ImageIcon out = new ImageIcon(img);
        out.setDescription(desc); // keep so a later re-sweep recognises and re-renders it
        return out;
    }
}
