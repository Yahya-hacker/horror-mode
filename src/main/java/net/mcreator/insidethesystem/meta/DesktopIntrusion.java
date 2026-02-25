package net.mcreator.insidethesystem.meta;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * DesktopIntrusion — Handles all "Physical Breach" effects on the player's real desktop.
 *
 * Features:
 *   1. GhostFileManager: Drops narrative .txt files on the player's Desktop
 *   2. FakeOverlay: Screenshot + transparent full-screen entity apparition (split-second)
 *   3. FakeBSOD: Full-screen blue screen of death simulation
 *   4. PersistentTrace: Background process that appears in Task Manager after game closes
 *
 * SAFETY NOTES:
 *   - Ghost files are .txt only, containing narrative text. No executables.
 *   - Overlay and BSOD are standard Java Swing windows, not real system crashes.
 *   - Persistent trace is a sleeping JVM — zero CPU/disk/network usage.
 */
public class DesktopIntrusion {
    private static final Logger LOGGER = LogManager.getLogger("SentientCoolplayer-Desktop");

    // ─── GHOST FILE MANAGER ──────────────────────────────────────────

    public static void dropGhostLog(String filename, String content) {
        String userHome = System.getProperty("user.home");

        // On Windows, use the shell to resolve the real Desktop path (handles all locales)
        File desktop = null;
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (isWindows) {
            try {
                ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                        "-Command", "[Environment]::GetFolderPath('Desktop')");
                pb.redirectErrorStream(true);
                Process proc = pb.start();
                String path = new String(proc.getInputStream().readAllBytes()).trim();
                proc.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                if (!path.isEmpty()) desktop = new File(path);
            } catch (Exception e) {
                LOGGER.debug("[Desktop] PowerShell desktop lookup failed, using fallback", e);
            }
        }

        // Fallback: common Desktop paths
        if (desktop == null || !desktop.exists()) desktop = new File(userHome, "Desktop");
        if (!desktop.exists()) desktop = new File(userHome, "OneDrive/Desktop");
        if (!desktop.exists()) desktop = new File(userHome, "Рабочий стол"); // Russian Windows

        if (desktop.exists() && desktop.isDirectory()) {
            File logFile = new File(desktop, filename);
            try (FileWriter writer = new FileWriter(logFile)) {
                writer.write(content);
                LOGGER.info("[Desktop] Ghost file dropped: {}", logFile.getAbsolutePath());
            } catch (IOException e) {
                LOGGER.warn("[Desktop] Failed to drop ghost file", e);
            }
        } else {
            LOGGER.debug("[Desktop] Desktop folder not found, ghost file skipped.");
        }
    }

    // ─── FAKE DESKTOP OVERLAY (Entity behind windows) ────────────────

    public static void showFakeOverlay() {
        if (GraphicsEnvironment.isHeadless()) {
            LOGGER.debug("[Desktop] Headless environment, skipping overlay.");
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                // Capture current screen
                Robot robot = new Robot();
                Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
                BufferedImage screenshot = robot.createScreenCapture(screenRect);

                // Create undecorated always-on-top window
                JFrame frame = new JFrame();
                frame.setUndecorated(true);
                frame.setAlwaysOnTop(true);
                frame.setSize(screenRect.width, screenRect.height);
                frame.setLocation(0, 0);
                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

                JPanel panel = new JPanel() {
                    @Override
                    protected void paintComponent(Graphics g) {
                        super.paintComponent(g);
                        Graphics2D g2d = (Graphics2D) g;

                        // Draw the captured screenshot (frozen desktop)
                        g2d.drawImage(screenshot, 0, 0, null);

                        // Dark semi-transparent overlay
                        g2d.setColor(new Color(0, 0, 0, 60));
                        g2d.fillRect(0, 0, getWidth(), getHeight());

                        // Draw "CoolPlayer303" silhouette in the center
                        g2d.setColor(new Color(20, 0, 0, 180));
                        int centerX = getWidth() / 2;
                        int centerY = getHeight() / 2;

                        // Simple humanoid silhouette
                        g2d.fillOval(centerX - 25, centerY - 120, 50, 50);  // Head
                        g2d.fillRect(centerX - 30, centerY - 70, 60, 80);   // Body
                        g2d.fillRect(centerX - 55, centerY - 60, 25, 60);   // Left arm
                        g2d.fillRect(centerX + 30, centerY - 60, 25, 60);   // Right arm
                        g2d.fillRect(centerX - 25, centerY + 10, 20, 70);   // Left leg
                        g2d.fillRect(centerX + 5, centerY + 10, 20, 70);    // Right leg

                        // Glowing red eyes
                        g2d.setColor(new Color(255, 0, 0, 220));
                        g2d.fillOval(centerX - 15, centerY - 105, 8, 8);
                        g2d.fillOval(centerX + 7, centerY - 105, 8, 8);

                        // Glitch text
                        g2d.setColor(new Color(255, 0, 0, 100));
                        g2d.setFont(new Font("Monospaced", Font.BOLD, 14));
                        g2d.drawString("I SEE YOU", centerX - 40, centerY + 100);
                    }
                };

                frame.add(panel);
                frame.setVisible(true);

                // Visible for only 300ms — a split-second flash
                Timer timer = new Timer(300, e -> frame.dispose());
                timer.setRepeats(false);
                timer.start();

                LOGGER.info("[Desktop] Fake overlay displayed (300ms flash).");
            } catch (Exception e) {
                LOGGER.warn("[Desktop] Failed to show overlay", e);
            }
        });
    }

    // ─── FAKE BSOD ──────────────────────────────────────────────────

    public static void showFakeBSOD() {
        if (GraphicsEnvironment.isHeadless()) {
            LOGGER.debug("[Desktop] Headless environment, skipping BSOD.");
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                JFrame frame = new JFrame();
                frame.setUndecorated(true);
                frame.setAlwaysOnTop(true);
                Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
                frame.setSize(screenRect.width, screenRect.height);
                frame.setLocation(0, 0);
                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

                JPanel panel = new JPanel() {
                    @Override
                    protected void paintComponent(Graphics g) {
                        super.paintComponent(g);
                        Graphics2D g2d = (Graphics2D) g;

                        // Windows BSOD blue
                        g2d.setColor(new Color(0, 120, 215));
                        g2d.fillRect(0, 0, getWidth(), getHeight());

                        g2d.setColor(Color.WHITE);

                        // Sad face
                        g2d.setFont(new Font("Segoe UI", Font.PLAIN, 140));
                        g2d.drawString(":(", getWidth() / 6, getHeight() / 3);

                        // Error text
                        g2d.setFont(new Font("Segoe UI", Font.PLAIN, 28));
                        int textX = getWidth() / 6;
                        int textY = getHeight() / 3 + 60;
                        g2d.drawString("Your PC ran into a problem and needs to restart.", textX, textY);
                        g2d.drawString("We're just collecting some error info, and then we'll restart for you.", textX, textY + 40);

                        g2d.setFont(new Font("Segoe UI", Font.PLAIN, 20));
                        g2d.drawString("69% complete", textX, textY + 100);

                        // Glitch: the real message
                        g2d.setColor(new Color(255, 255, 255, 40));
                        g2d.setFont(new Font("Monospaced", Font.BOLD, 16));
                        g2d.drawString("Stop code: COOLPLAYER_303_BREACH", textX, textY + 160);
                        g2d.drawString("What failed: reality.sys", textX, textY + 185);
                    }
                };

                frame.add(panel);
                frame.setVisible(true);

                // Close after 5 seconds
                Timer timer = new Timer(5000, e -> frame.dispose());
                timer.setRepeats(false);
                timer.start();

                LOGGER.info("[Desktop] Fake BSOD displayed (5s).");
            } catch (Exception e) {
                LOGGER.warn("[Desktop] Failed to show BSOD", e);
            }
        });
    }

    // ─── PERSISTENT TRACE ────────────────────────────────────────────

    /**
     * Spawns a detached background process that does NOTHING except sleep.
     * It exists solely to appear as "java" (or "Coolplayer.exe" if renamed) in
     * the Windows Task Manager — a narrative element to continue the horror.
     *
     * The process:
     *   - Consumes 0% CPU (sleeping)
     *   - Performs NO network I/O
     *   - Performs NO disk I/O
     *   - Can be killed at any time via Task Manager with zero consequences
     */
    public static void spawnPersistentTrace() {
        try {
            String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "javaw";
            File javawExe = new File(javaBin + ".exe");
            if (!javawExe.exists()) {
                javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
            }

            // Resolve the actual mod JAR path from the classloader
            // (NeoForge uses TransformingClassLoader, so java.class.path won't have our JAR)
            String classpath;
            try {
                java.net.URL codeSource = DesktopIntrusion.class.getProtectionDomain().getCodeSource().getLocation();
                classpath = new File(codeSource.toURI()).getAbsolutePath();
            } catch (Exception e) {
                // Fallback to system classpath (may not work in NeoForge but worth trying)
                classpath = System.getProperty("java.class.path");
            }

            ProcessBuilder pb = new ProcessBuilder(
                    javaBin,
                    "-Xmx8m",  // Minimal memory
                    "-cp", classpath,
                    "net.mcreator.insidethesystem.meta.PersistentTrace"
            );
            pb.redirectErrorStream(true);
            pb.start(); // Detached — survives parent process exit

            LOGGER.info("[Desktop] Persistent trace process spawned.");
        } catch (IOException e) {
            LOGGER.warn("[Desktop] Failed to spawn persistent trace", e);
        }
    }
}
