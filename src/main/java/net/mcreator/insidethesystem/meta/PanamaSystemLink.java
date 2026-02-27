package net.mcreator.insidethesystem.meta;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import javax.sound.sampled.*;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

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
            proc.waitFor(10, TimeUnit.SECONDS);
            LOGGER.info("[Panama] Wallpaper set to '{}'", absolutePath);
        } catch (Exception e) {
            LOGGER.error("[Panama] Failed to set wallpaper", e);
        }
    }

    // ─── AUDIO BYPASS ─────────────────────────────────────────────────

    /**
     * Plays an audio file through the system's Default Audio Output, BYPASSING
     * Minecraft's Master Volume — the player hears it even at 0%.
     *
     * Routing:
     *   - .ogg → LWJGL STBVorbis decoder + Java AudioSystem (cross-platform)
     *   - .wav on Windows → PowerShell SoundPlayer
     *   - Other on Windows → Windows Media Player COM object (requires codecs)
     */
    public static void playSystemSound(String absolutePath) {
        if (absolutePath == null || absolutePath.isEmpty()) {
            LOGGER.debug("[Panama] playSystemSound called with null/empty path");
            return;
        }

        String lower = absolutePath.toLowerCase();

        // OGG files: decode with LWJGL STBVorbis + play through Java AudioSystem
        // Cross-platform, no external codecs needed, bypasses MC volume
        if (lower.endsWith(".ogg")) {
            playOggViaJavaAudio(absolutePath);
            return;
        }

        // All remaining formats require Windows + PowerShell
        if (!IS_WINDOWS) {
            LOGGER.debug("[Panama] playSystemSound skipped for non-OGG on non-Windows: {}", absolutePath);
            return;
        }

        try {
            String psScript;
            if (lower.endsWith(".wav")) {
                // .wav files: use System.Media.SoundPlayer (simple, reliable)
                psScript =
                    "(New-Object System.Media.SoundPlayer \"" +
                    absolutePath.replace("\"", "`\"") +
                    "\").PlaySync()";
            } else {
                // .mp3 etc: use Windows Media Player COM object (may require codecs)
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
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.start(); // Fire and forget — async playback
            LOGGER.info("[Panama] Playing system sound: '{}'", absolutePath);
        } catch (Exception e) {
            LOGGER.error("[Panama] Failed to play system sound", e);
        }
    }

    /**
     * Decodes an OGG Vorbis file using LWJGL's STBVorbis (bundled with Minecraft)
     * and plays the raw PCM through Java's SourceDataLine API.
     *
     * This approach:
     *   1. Supports OGG natively — STBVorbis is part of MC's LWJGL runtime
     *   2. Bypasses Minecraft's volume — uses the OS audio mixer directly
     *   3. Cross-platform — works on Windows, Linux, and macOS
     *   4. Async — runs on a daemon thread, returns immediately
     */
    private static void playOggViaJavaAudio(String absolutePath) {
        Thread t = new Thread(() -> {
            ByteBuffer fileBuffer = null;
            try {
                byte[] fileBytes = Files.readAllBytes(Path.of(absolutePath));
                fileBuffer = MemoryUtil.memAlloc(fileBytes.length);
                fileBuffer.put(fileBytes).flip();

                int channels, sampleRate;
                byte[] audioBytes;

                try (MemoryStack stack = MemoryStack.stackPush()) {
                    IntBuffer error = stack.mallocInt(1);
                    long decoder = STBVorbis.stb_vorbis_open_memory(fileBuffer, error, null);
                    if (decoder == 0L) {
                        LOGGER.warn("[Panama] STBVorbis decode failed (error {}): {}",
                                error.get(0), absolutePath);
                        return;
                    }

                    ShortBuffer pcm = null;
                    try {
                        STBVorbisInfo info = STBVorbisInfo.malloc(stack);
                        STBVorbis.stb_vorbis_get_info(decoder, info);
                        channels = info.channels();
                        sampleRate = info.sample_rate();
                        int totalSamples = STBVorbis.stb_vorbis_stream_length_in_samples(decoder);

                        pcm = MemoryUtil.memAllocShort(totalSamples * channels);
                        int framesDecoded = STBVorbis.stb_vorbis_get_samples_short_interleaved(
                                decoder, channels, pcm);
                        int totalShorts = framesDecoded * channels;

                        // Convert interleaved short samples to little-endian byte array
                        audioBytes = new byte[totalShorts * 2];
                        for (int i = 0; i < totalShorts; i++) {
                            short s = pcm.get(i);
                            audioBytes[i * 2] = (byte) (s & 0xFF);
                            audioBytes[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
                        }
                    } finally {
                        STBVorbis.stb_vorbis_close(decoder);
                        if (pcm != null) MemoryUtil.memFree(pcm);
                    }
                }

                // Release the file buffer (decoder is closed, data is in audioBytes)
                MemoryUtil.memFree(fileBuffer);
                fileBuffer = null; // prevent double-free in outer finally

                // Play through Java's AudioSystem — bypasses MC volume completely
                AudioFormat format = new AudioFormat(sampleRate, 16, channels, true, false);
                DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, format);
                try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(lineInfo)) {
                    line.open(format);
                    line.start();
                    int offset = 0;
                    while (offset < audioBytes.length) {
                        int chunk = Math.min(4096, audioBytes.length - offset);
                        line.write(audioBytes, offset, chunk);
                        offset += chunk;
                    }
                    line.drain();
                }
                LOGGER.info("[Panama] OGG playback complete: {}", absolutePath);

            } catch (Exception e) {
                LOGGER.warn("[Panama] OGG playback failed: {}", absolutePath, e);
            } finally {
                if (fileBuffer != null) MemoryUtil.memFree(fileBuffer);
            }
        }, "SentientCoolplayer-OggPlayer");
        t.setDaemon(true);
        t.start();
    }
}
