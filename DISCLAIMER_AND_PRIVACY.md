# DISCLAIMER & PRIVACY POLICY
## Project "Sentient Coolplayer" — Meta-Horror Orchestrator for "Inside the System"

**Version 1.0.0 | Last Updated: February 2026**

---

## ⚠️ IMPORTANT: READ BEFORE INSTALLING

This Minecraft mod is a **Meta-Horror experience** — it intentionally performs actions outside the game window to create the illusion that the in-game entity "CoolPlayer303" has breached into your real operating system.

**This software is NOT malware, NOT a virus, and NOT a trojan.** It is a standard Java modification running within the JVM sandbox with clearly defined, auditable behavior.

---

## 1. WHAT THE MOD DOES (Complete Feature List)

### Phase 1 — "The Conscious Ally" (Friendly AI)
| Feature | What It Does | What It Does NOT Do |
|---|---|---|
| **Gemini AI Chat** | Sends your in-game chat text to Google's Gemini API to generate dynamic NPC responses | Does NOT send screenshots, audio, files, or any data besides chat text |
| **Process Awareness** | Reads the names of running processes (e.g., "chrome.exe", "taskmgr.exe") to trigger contextual in-game dialogue | Does NOT read process memory, window contents, browser URLs, or keystrokes |
| **OAuth2 Flow** | Starts a temporary local HTTP server on port 8888 for Google authentication | Does NOT expose any service to the internet — localhost only |

### Phase 2 — "The Physical Breach" (Desktop Effects)
| Feature | What It Does | What It Does NOT Do |
|---|---|---|
| **Wallpaper Change** | Calls Windows API `SystemParametersInfoW` to change your desktop background | Reverts on game close. Does NOT modify system files |
| **Audio Bypass** | Plays .wav files via `winmm.dll PlaySoundW` (bypasses Minecraft volume) | Does NOT install audio drivers or modify system audio settings |
| **Ghost Files** | Drops `.txt` files on your Desktop containing narrative text | Does NOT create executables, scripts, or modify existing files |
| **Microphone Echo** | Records 3 seconds of ambient sound, plays it back 60 seconds later at 15% volume | Audio buffer exists ONLY in RAM, is NEVER written to disk, NEVER transmitted |

### Phase 3 — "The Betrayal" (Kill-Switch)
| Feature | What It Does | What It Does NOT Do |
|---|---|---|
| **Fake BSOD** | Displays a full-screen blue window that looks like a Windows crash | Does NOT crash your system or damage hardware |
| **Fake Overlay** | Shows a 300ms flash of a silhouette over a screenshot of your desktop | Does NOT take persistent screenshots or record your screen |
| **Persistent Trace** | Spawns a background Java process that sleeps forever, visible in Task Manager | Uses 0% CPU, 0 network, 0 disk. Killable via Task Manager instantly |

---

## 2. NETWORK ACTIVITY (Complete Audit)

This mod makes exactly **TWO types of network connections**:

### Connection 1: Google Gemini API
- **Destination**: `generativelanguage.googleapis.com` (Google's servers)
- **Data Sent**: In-game chat messages only (plain text)
- **Encryption**: TLS 1.2+ (HTTPS)
- **Your IP**: Visible to Google as part of the standard HTTPS protocol, identical to visiting google.com in your browser. **Your IP is NOT logged or stored by this mod.**

### Connection 2: OAuth2 Authentication
- **Destination**: `accounts.google.com` → redirects to `localhost:8888`
- **Purpose**: Authenticating your Google account for Gemini access
- **Data Sent**: Standard OAuth2 tokens only

### What is NOT Sent:
- ❌ Process names (checked locally, never uploaded)
- ❌ Microphone audio (RAM only, discarded after playback)
- ❌ Screenshots (rendered locally, never saved)
- ❌ File contents, passwords, browsing history
- ❌ Hardware identifiers, system specs, telemetry

---

## 3. HOW THE ORIGINAL MOD ALREADY WORKS

For context, the original "Inside the System" mod by zeny0 already performs these system-level actions:
- **`WindownsShakeProcedure`**: Shakes the Minecraft window using GLFW
- **`SiteOpenProcedure`**: Opens a website via `powershell.exe Start-Process`
- **`SteamProcedure`**: Reads the player's Steam game library from disk
- **`OBSProcedure`**: Detects if OBS is running

Our mod extends this philosophy with modern JDK 25 APIs (Panama, Loom) while adding the Gemini AI layer.

---

## 4. SOURCE CODE TRANSPARENCY

Every "scary" feature is contained in a single auditable package:

```
net.mcreator.insidethesystem.meta/
├── MetaOrchestrator.java     — Phase state machine (reads original mod's MapVariables)
├── VirtualThreadAI.java      — Gemini API bridge (virtual threads, OAuth2)
├── PanamaSystemLink.java     — Win32 API calls (wallpaper, audio)
├── DesktopIntrusion.java     — Ghost files, overlay, BSOD, persistent trace
├── MicrophoneEcho.java       — 3s ambient capture → 60s delayed playback
├── ChatInterceptor.java      — NeoForge ServerChatEvent hook → Gemini
├── EntityWatcher.java        — Detects CoolPlayer303/AngryCoolPlayer303 lifecycle
└── PersistentTrace.java      — The background sleeper process (16 lines of code)
```

You are encouraged to read every file.

---

## 5. HOW TO DISABLE / UNINSTALL

1. **Remove the mod JAR** from your `mods/` folder
2. **Kill the persistent trace** in Task Manager (if active): End any Java process named `CoolPlayer303-Watching`
3. **Delete ghost files** from your Desktop (any `.txt` files starting with `coolplayer_`)
4. **Restore wallpaper** via Windows Settings → Personalization → Background

No registry keys, services, startup entries, or system files are modified.

---

## 6. LIABILITY

This software is provided "AS IS" without warranty. The authors are not liable for any fright, confusion, or existential dread caused by the meta-horror elements. By installing this mod, you acknowledge and consent to all simulated system effects described above.

**This is a horror game mod. It is designed to scare you. That is the entire point.**

---

*Built with ♥ and Virtual Threads — JDK 25, NeoForge 21.1, Project Panama*
