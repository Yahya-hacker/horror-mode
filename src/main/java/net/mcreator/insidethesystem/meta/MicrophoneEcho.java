package net.mcreator.insidethesystem.meta;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * MicrophoneEcho — The most unsettling feature.
 *
 * Records 3 seconds of ambient room noise from the system's default audio input,
 * then plays it back 60 seconds later at reduced volume through the default output.
 *
 * The player will hear their own room sounds played back faintly — footsteps, breathing,
 * chair creaks — making them think someone is in their actual room.
 *
 * PRIVACY NOTES:
 *   - Audio is captured into a volatile byte[] buffer in RAM only
 *   - The buffer is NEVER written to disk
 *   - The buffer is NEVER transmitted over the network
 *   - The buffer is discarded after a single playback
 *   - Total capture duration: exactly 3 seconds
 *   - This feature requires user consent (enabled via config)
 */
public class MicrophoneEcho {
    private static final Logger LOGGER = LogManager.getLogger("SentientCoolplayer-MicEcho");
    private static final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "SentientCoolplayer-MicEcho");
                t.setDaemon(true);
                return t;
            });

    private static final int SAMPLE_RATE = 16000;
    private static final int RECORD_SECONDS = 3;
    private static final int PLAYBACK_DELAY_SECONDS = 60;
    private static final float PLAYBACK_VOLUME = 0.15f; // 15% volume — faint and unsettling

    /**
     * Schedules the echo sequence: record now → play back in 60 seconds.
     */
    public static void scheduleEcho() {
        scheduler.submit(() -> {
            try {
                LOGGER.info("[MicEcho] Starting 3-second ambient capture...");
                byte[] audioData = captureAmbient();

                if (audioData != null && audioData.length > 0) {
                    LOGGER.info("[MicEcho] Captured {} bytes. Scheduling playback in {}s.",
                            audioData.length, PLAYBACK_DELAY_SECONDS);

                    scheduler.schedule(() -> {
                        try {
                            playbackAmbient(audioData);
                        } catch (Exception e) {
                            LOGGER.warn("[MicEcho] Playback failed", e);
                        }
                    }, PLAYBACK_DELAY_SECONDS, TimeUnit.SECONDS);
                } else {
                    LOGGER.warn("[MicEcho] No audio captured (no microphone or access denied).");
                }
            } catch (Exception e) {
                LOGGER.warn("[MicEcho] Capture failed (expected if no mic available)", e);
            }
        });
    }

    private static byte[] captureAmbient() throws Exception {
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

        if (!AudioSystem.isLineSupported(info)) {
            LOGGER.debug("[MicEcho] No supported audio input line found.");
            return null;
        }

        TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(format);
        line.start();

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        long endTime = System.currentTimeMillis() + (RECORD_SECONDS * 1000L);

        while (System.currentTimeMillis() < endTime) {
            int bytesRead = line.read(chunk, 0, chunk.length);
            if (bytesRead > 0) {
                buffer.write(chunk, 0, bytesRead);
            }
        }

        line.stop();
        line.close();

        return buffer.toByteArray();
    }

    private static void playbackAmbient(byte[] audioData) throws Exception {
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);

        // Reduce volume by scaling sample values
        byte[] quietData = new byte[audioData.length];
        for (int i = 0; i < audioData.length - 1; i += 2) {
            short sample = (short) ((audioData[i] & 0xFF) | (audioData[i + 1] << 8));
            sample = (short) (sample * PLAYBACK_VOLUME);
            quietData[i] = (byte) (sample & 0xFF);
            quietData[i + 1] = (byte) ((sample >> 8) & 0xFF);
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(quietData);
        AudioInputStream ais = new AudioInputStream(bais, format, quietData.length / format.getFrameSize());

        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(format);
        line.start();

        byte[] chunk = new byte[4096];
        int bytesRead;
        while ((bytesRead = ais.read(chunk, 0, chunk.length)) != -1) {
            line.write(chunk, 0, bytesRead);
        }

        line.drain();
        line.close();

        LOGGER.info("[MicEcho] Ambient echo playback complete. Audio buffer discarded.");
        // Audio data is now eligible for GC — never saved anywhere
    }
}
