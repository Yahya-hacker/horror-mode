package net.mcreator.insidethesystem.meta;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * PanamaSystemLink — Windows system bridge using Runtime.exec() + PowerShell.
 *
 * Rewritten from the original Project Panama (FFM) design to use PowerShell,
 * because NeoForge 1.21.1 runs on JDK 21 where Panama FFM is still a preview API.
 * PowerShell is present on all Windows 10/11 systems by default.
 *
 * Provides:
 *   1. Process scanning via ProcessHandle API (cross-platform, standard JDK 9+)
 *   2. Desktop wallpaper manipulation via PowerShell + .NET interop
 *   3. Hardware audio bypass via PowerShell [System.Media.SoundPlayer]
 *
 * SAFETY: All calls are wrapped in try-catch and gracefully degrade on non-Windows.
 * PRIVACY: Process names are checked locally against keywords. NO data is transmitted.
 */
public class PanamaSystemLink {
    private static final Logger LOGGER = LogManager.getLogger("SentientCoolplayer-Panama");
    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    // ─── PROCESS SCANNING (Cross-platform, standard JDK 9+) ──────────

    /**
     * Returns a list of running process names.
     * Uses standard JDK ProcessHandle API — no native code needed.
     * Data is processed locally and NEVER transmitted anywhere.
     */
    public static List<String> getActiveProcesses() {
        List<String> processes = new ArrayList<>();
        ProcessHandle.allProcesses().forEach(ph -> {
            Optional<String> cmd = ph.info().command();
            cmd.ifPresent(s -> {
                String name = s;
                int lastSep = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
                if (lastSep >= 0 && lastSep < s.length() - 1) {
                    name = s.substring(lastSep + 1);
                }
                processes.add(name);
            });
        });
        return processes;
    }

    // ─── WALLPAPER MANIPULATION (Windows only, PowerShell) ────────────

    /**
     * Changes the Windows desktop wallpaper using PowerShell + .NET P/Invoke.
     * No-op on non-Windows systems.
     */
    public static void setWallpaper(String absolutePath) {
        if (!IS_WINDOWS) {
            LOGGER.debug("[Panama] setWallpaper skipped (not Windows)");
            return;
        }

        try {
            String psScript =
                "Add-Type -TypeDefinition '" +
                "using System.Runtime.InteropServices; " +
                "public class WP { " +
                "  [DllImport(\"user32.dll\", CharSet = CharSet.Unicode)] " +
                "  public static extern int SystemParametersInfo(int a, int b, string c, int d); " +
                "}' -ErrorAction SilentlyContinue; " +
                "[WP]::SystemParametersInfo(0x0014, 0, \"" + absolutePath.replace("\"", "`\"") + "\", 3)";

            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", psScript);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            proc.waitFor();
            LOGGER.info("[Panama] Wallpaper set to '{}'", absolutePath);
        } catch (Exception e) {
            LOGGER.error("[Panama] Failed to set wallpaper", e);
        }
    }

    // ─── AUDIO BYPASS (Windows only, PowerShell) ──────────────────────

    /**
     * Plays an audio file through the Windows Default Audio Output using PowerShell.
     * This BYPASSES Minecraft's Master Volume — the player hears it even at 0%.
     *
     * Supports both .wav (via SoundPlayer) and .ogg/.mp3 (via Windows Media Player COM).
     * No-op on non-Windows systems.
     */
    public static void playSystemSound(String absolutePath) {
        if (!IS_WINDOWS) {
            LOGGER.debug("[Panama] playSystemSound skipped (not Windows)");
            return;
        }

        try {
            String psScript;
            if (absolutePath.toLowerCase().endsWith(".wav")) {
                // .wav files: use System.Media.SoundPlayer (simple, reliable)
                psScript =
                    "(New-Object System.Media.SoundPlayer \"" +
                    absolutePath.replace("\"", "`\"") +
                    "\").PlaySync()";
            } else {
                // .ogg, .mp3, etc: use Windows Media Player COM object
                // WMP supports OGG if proper codecs are installed (Windows 10/11 have them via store)
                // Falls back gracefully if the format isn't supported
                psScript =
                    "$wmp = New-Object -ComObject WMPlayer.OCX; " +
                    "$media = $wmp.newMedia(\"" + absolutePath.replace("\"", "`\"") + "\"); " +
                    "$wmp.currentPlaylist.appendItem($media); " +
                    "$wmp.controls.play(); " +
                    "Start-Sleep -Seconds 10; " +
                    "$wmp.close()";
            }

            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", psScript);
            pb.redirectErrorStream(true);
            pb.start(); // Fire and forget — async playback
            LOGGER.info("[Panama] Playing system sound '{}'", absolutePath);
        } catch (Exception e) {
            LOGGER.error("[Panama] Failed to play system sound", e);
        }
    }
}
