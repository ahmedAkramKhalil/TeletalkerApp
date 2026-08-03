# Robust Echo Prevention + Interruption Support Plan

## Root Cause Analysis

The echo happens because **both AudioTrack (playback) and AudioRecord (capture) are on the same voice communication channel**:

- `AIResponseBuffer.java` line 70: AudioTrack uses `USAGE_VOICE_COMMUNICATION`
- `OptimizedAudioStreamer.java` line 222: AudioRecord uses `VOICE_COMMUNICATION` source

AudioRecord captures what AudioTrack plays back → ElevenLabs hears its own voice → echo loop.

Switching AudioRecord to `MIC` doesn't fully fix it either, because on this rooted device with audio injection, the voice communication audio path bleeds into the mic path.

## Solution: 3-Layer Approach

### Layer 1: Shared Audio Session + AcousticEchoCanceler (Primary)

Android's `AcousticEchoCanceler` is designed exactly for this: it subtracts the AudioTrack playback from the AudioRecord capture when they share the same audio session.

**Changes:**

1. **AICallRecorderRefactored.java** — Create a shared audio session ID and pass it to both components:
   ```java
   int sharedAudioSessionId = AudioManager.generateAudioSessionId();
   responseBuffer.initialize(sharedAudioSessionId);
   audioStreamer.start(webSocket, sharedAudioSessionId);
   ```

2. **AIResponseBuffer.java** — Accept session ID in `initialize()`:
   ```java
   public boolean initialize(int audioSessionId) {
       audioTrack = new AudioTrack(audioAttributes, audioFormat, bufferSize,
           AudioTrack.MODE_STREAM, audioSessionId);  // was AUDIO_SESSION_ID_GENERATE
   }
   ```

3. **OptimizedAudioStreamer.java** — Accept session ID, attach AcousticEchoCanceler + NoiseSuppressor:
   ```java
   audioRecord = new AudioRecord(source, SAMPLE_RATE, CHANNEL_IN_MONO, PCM_16BIT, bufferSize);

   // Attach echo canceler
   if (AcousticEchoCanceler.isAvailable()) {
       AcousticEchoCanceler aec = AcousticEchoCanceler.create(audioRecord.getAudioSessionId());
       aec.setEnabled(true);
   }

   // Attach noise suppressor
   if (NoiseSuppressor.isAvailable()) {
       NoiseSuppressor ns = NoiseSuppressor.create(audioRecord.getAudioSessionId());
       ns.setEnabled(true);
   }
   ```

### Layer 2: Audio Channel Separation (Secondary)

If AEC is not available on the device, separate the playback and capture channels:

**Change in AIResponseBuffer.java** — Use `USAGE_MEDIA` instead of `USAGE_VOICE_COMMUNICATION`:
```java
AudioAttributes audioAttributes = new AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA)  // was USAGE_VOICE_COMMUNICATION
    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
    .setFlags(AudioAttributes.FLAG_LOW_LATENCY)
    .build();
```

This routes AI playback through the **media channel** instead of voice communication. AudioRecord (VOICE_COMMUNICATION/MIC) won't capture media channel audio.

**Note:** The AI audio also reaches the phone call party through the separate injection mechanism (`inject_stream.sh` / `AudioTrackInjector`), so changing the local playback channel doesn't affect that.

### Layer 3: Smart Echo Gate (Fallback)

As a final fallback for devices where neither AEC nor channel separation works, replace the binary silence approach with a smart echo gate:

**Change in OptimizedAudioStreamer.java** — Instead of `isSendingSilence` (all or nothing), use amplitude-based gating:

- When AI starts playing: enter "echo gate" mode, measure echo baseline amplitude (first 200ms)
- For each captured chunk: if amplitude < (echo_baseline × 3) → send silence (it's just echo)
- If amplitude > threshold → send real audio (user is speaking/interrupting)
- When AI stops: exit echo gate mode, resume normal audio

This allows interruption because the user's voice (directly into mic) will be significantly louder than the echo.

### Layer 4: Remove silence callbacks entirely

The `onResponseStarted` / `onResponseStopped` callbacks in AICallRecorderRefactored will NO LONGER call `startSendingSilence()` / `stopSendingSilence()`. The echo prevention is now handled by Layers 1-3.

## Files to Modify

1. **`OptimizedAudioStreamer.java`**
   - Add `AcousticEchoCanceler` and `NoiseSuppressor` initialization
   - Add smart echo gate logic (replace binary `isSendingSilence`)
   - Accept shared audio session ID
   - Add echo baseline measurement during AI response

2. **`AIResponseBuffer.java`**
   - Accept audio session ID parameter in `initialize()`
   - Conditionally use `USAGE_MEDIA` when AEC is not available (Layer 2)

3. **`AICallRecorderRefactored.java`**
   - Generate shared audio session ID
   - Pass to both components
   - Response callbacks: enter/exit echo gate mode (not silence mode)

## Expected Behavior After Fix

- **No echo**: AEC removes playback from capture (Layer 1), OR channels are separated (Layer 2), OR echo is gated by amplitude (Layer 3)
- **Interruption works**: Real audio always flows when user speaks loudly enough
- **No new issues**: Each layer has a fallback, so at least one will work
- **Diagnostic logging**: Each layer logs whether it's active/effective, so we can debug via logcat
