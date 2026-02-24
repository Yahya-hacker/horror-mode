package net.mcreator.insidethesystem.meta;

/**
 * PersistentTrace — The "ghost in the machine" background process.
 *
 * This class is the entry point for a detached JVM process that:
 *   1. Does NOTHING except sleep forever
 *   2. Appears in Windows Task Manager as a Java process
 *   3. Consumes negligible resources (~8MB heap, 0% CPU)
 *   4. Performs NO network, disk, or system calls
 *   5. Can be safely killed via Task Manager at any time
 *
 * Its sole purpose is a narrative element — the "legend" continues after the game closes.
 */
public class PersistentTrace {
    public static void main(String[] args) {
        // Set the thread name so it's identifiable in profilers
        Thread.currentThread().setName("CoolPlayer303-Watching");

        try {
            // Sleep forever — doing absolutely nothing
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            // If interrupted (e.g., killed from Task Manager), exit silently
        }
    }
}
