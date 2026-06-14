package mage.client.shell;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/**
 * Entry point for the modern UI shell.
 * <p>
 * The shell is <b>always on</b> — there is no enable/disable flag. {@link #isEnabled()} is kept
 * (returning {@code true}) only so the existing one-line seams read clearly and need no edits.
 * <p>
 * Design notes (see {@code SHELL.md} at the repo root):
 * <ul>
 *     <li><b>Additive.</b> Practically all shell code lives in this new {@code mage.client.shell}
 *     package and in additive resources, so upstream merges of {@code master} rarely conflict.</li>
 *     <li><b>Few seams.</b> The shell is wired into the existing client at a small, documented set
 *     of one-line call sites.</li>
 * </ul>
 *
 * @author modern-shell
 */
public final class Shell {

    /** System property selecting the variant ({@code dark} or {@code light}); defaults to dark. */
    public static final String THEME_PROPERTY = "xmage.shell.theme";

    /** Environment variable selecting the variant ({@code dark} or {@code light}). */
    public static final String THEME_ENV = "XMAGE_SHELL_THEME";

    /**
     * Package that holds the shell's FlatLaf customisation files
     * ({@code FlatLaf.properties}, etc.). Registered as a custom defaults source so all theming
     * lives in additive resources rather than hard-coded Java.
     */
    private static final String DEFAULTS_PACKAGE = "mage.client.shell";

    private static boolean defaultsRegistered;

    private Shell() {
    }

    /**
     * @return always {@code true}. The shell has no off switch; kept so the seam call sites stay
     * readable and unchanged.
     */
    public static boolean isEnabled() {
        return true;
    }

    /**
     * Install the modern look-and-feel. Called from the client's LAF-install seams.
     * <p>
     * All concrete styling (accent colour, corner radii, scrollbar shape, spacing) is expressed in
     * the additive {@code FlatLaf.properties} resource in this package, so it can be tuned without
     * touching Java code.
     *
     * @throws UnsupportedLookAndFeelException if the platform rejects the look-and-feel (matches the
     *                                         checked exception already handled at the call site).
     */
    public static void installLookAndFeel() throws UnsupportedLookAndFeelException {
        // Must be registered before the LAF is created so the .properties overrides are picked up.
        // FlatLaf loads FlatLaf.properties (shared) plus FlatDarkLaf/FlatLightLaf.properties (variant).
        // Guarded so repeated calls (startup + theme refresh) don't register the source twice.
        if (!defaultsRegistered) {
            FlatLaf.registerCustomDefaultsSource(DEFAULTS_PACKAGE);
            defaultsRegistered = true;
        }
        UIManager.setLookAndFeel(isLightVariant() ? new FlatLightLaf() : new FlatDarkLaf());

        // Modernise ad-hoc /buttons/ icons across windows (deck editor, lobby, dialogs) without
        // touching their many generated call sites. Idempotent; re-sweeps on theme change.
        ShellIconSweep.install();
    }

    /**
     * @return true when the light ("Arcane Parchment") variant is requested; dark is the default.
     */
    public static boolean isLightVariant() {
        String value = System.getProperty(THEME_PROPERTY);
        if (value == null) {
            value = System.getenv(THEME_ENV);
        }
        return value != null && "light".equalsIgnoreCase(value.trim());
    }
}
