# Voice Input: Tap-to-Speak Dictation into the Note Box

**Date:** 2026-10-21
**Status:** BUILT and checked on a Pixel 8a. Known issue: some words are lost when the speaker talks through a short pause, because of the restart fallback (see Risk check results).

## Problem

Support workers write notes on their phones, often straight after a shift. Many are not technical and some are older. Typing a full note is slow, and the keyboard mic takes extra steps: open the keyboard, then find a small icon.

## Goal

A non-technical support worker can speak a note into ShiftNote with no instructions, and the audio stays on the phone.

Success means:

1. **Simplicity test passes:** someone non-technical, given the phone and a made-up note card, records the note when told only "record this note".
2. Spoken words land in the note box with names and shorthand mostly right (counted on golden notes 2, 6 and 10).
3. It works in airplane mode.
4. Words already heard are never lost, whatever stops the session.

## Where this fits

Voice only fills the note box. Everything after that (Improve now, Suggest later, cloud redaction and preview) works on the text exactly as it does for typed notes.

## Decisions (2026-10-21)

- **Tap on, tap off**, not hold-to-talk. Holding a button for a 1-2 minute note is tiring, a slipped finger ends the recording, people who don't know about the hold tap it and think it's broken, and TalkBack users can't easily press and hold.
- **Spoken words are added to the end** of the note box, never at the cursor or replacing the box.
- **Words appear live** in the note box while the user speaks.
- **Engine: Android's on-device recogniser** (`SpeechRecognizer.createOnDeviceSpeechRecognizer`) behind a `SpeechTranscriber` interface. Rejected: bundling a speech model such as Whisper or Vosk (+50-150 MB, slower, ~10+ hrs; revisit only if the on-device recogniser fails the risk check) and Android's standard "Speak now" popup (stops on pauses, Google's own UI, may use Google's servers).
- **Cloud future:** if ShiftNote stays a native Android app with cloud AI, this design is unchanged, because only text leaves the phone and text can be redacted while audio cannot. If ShiftNote becomes a web app, the engine is swapped behind the interface.
- **Voice logic lives in its own `VoiceInput` class**, not in `MainViewModel`, so it can be reused for other text boxes later.

## Out of scope

- Voice in other boxes (cover context, client goals, extra instructions). `VoiceInput` makes this easy later.
- Cloud or bundled speech engines, and per-organisation engine choice.
- AI working on the voice directly. The user checks the transcript first, then presses Improve.
- A language picker in Settings.
- Phones below Android 12. The button is hidden there; the keyboard mic still works.

## Platform facts

Checked on 2026-10-21 against the local SDK source (`sources/android-36.1/android/speech`) and `platforms/android-35/data/api-versions.xml`. The app's `minSdk` is 26.

| API | Level | What the docs say |
|---|---|---|
| `createOnDeviceSpeechRecognizer` | 31 | Returns "a new on-device SpeechRecognizer". Throws `UnsupportedOperationException` if `isOnDeviceRecognitionAvailable` is false, so it fails rather than falling back. The docs do not state that no audio is sent anywhere. |
| `isOnDeviceRecognitionAvailable` | 31 | False means the factory above will fail. |
| `EXTRA_PREFER_OFFLINE` | 23 | "may have no effect". Not used. |
| `EXTRA_SEGMENTED_SESSION`, `onSegmentResults`, `onEndOfSegmentedSession` | 33 | Results arrive in segments until the session ends. "may have no effect". |
| `EXTRA_ENABLE_FORMATTING` | 33 | Punctuation and capitals. Results then hold formatted text first, raw text second. "may have no effect". |
| `EXTRA_PARTIAL_RESULTS`, `onPartialResults` | 8 | Live words. "may ignore a request for partial results". |
| `checkRecognitionSupport`, `triggerModelDownload` | 33 | Checks whether a language is installed on the device; asks the system to download it (may prompt the user). |
| `ERROR_LANGUAGE_NOT_SUPPORTED`, `ERROR_LANGUAGE_UNAVAILABLE` | 31 | Language not available. |
| Threading | - | All `SpeechRecognizer` methods must be called on the main thread, and `destroy()` must be called when finished. |

The app's icon set (`material-icons-core`) has no mic or stop icon.

## Design

### Parts

**New files:**

- `speech/SpeechTranscriber.kt`: the interface plus its event types.
- `speech/AndroidSpeechTranscriber.kt`: the real engine. Wraps the on-device recogniser, follows the main-thread and `destroy()` rules, and keeps listening through pauses (segmented session, or the restart fallback if the risk check shows segmented sessions don't work).
- `speech/VoiceInput.kt`: listening state, the merge of speech into a text box, the 60 s silence stop and the user-facing messages. Takes the box's text through a getter and setter, so it works with any box.
- `res/drawable/ic_mic.xml`, `res/drawable/ic_stop.xml`: Material icon vectors.

**Changed files:**

- `AndroidManifest.xml`: adds `android.permission.RECORD_AUDIO`.
- `ShiftNoteApp.kt`: `AppContainer` gains a `speechTranscriberFactory`, following `noteRestructurerFactory`.
- `ui/main/MainViewModel.kt`: holds one `VoiceInput` wired to `noteText`, and blocks `restructure()` while listening.
- `ui/main/MainScreen.kt`: the voice button between the note box and Improve, the permission request, and the voice message line.

**Not touched:** Gemma, Gemini, `PromptAssembler`, `Redactor`, Settings.

Interface sketch (the plan sets the final signatures):

```kotlin
sealed interface SpeechEvent {
    data class Live(val text: String) : SpeechEvent       // words still changing
    data class Finished(val text: String) : SpeechEvent   // final words for one chunk
    data class Problem(val problem: SpeechProblem) : SpeechEvent
    data object Ended : SpeechEvent                       // engine has stopped
}

sealed interface SpeechProblem {
    data object NoPermission : SpeechProblem
    data class LanguageMissing(val canDownload: Boolean) : SpeechProblem
    data object Other : SpeechProblem                     // busy, audio fault, anything else
}

interface SpeechTranscriber {
    fun isAvailable(): Boolean
    fun start(onEvent: (SpeechEvent) -> Unit)
    fun stop()                    // deliver the last Finished words, then Ended
    fun requestLanguageDownload()
    fun release()
}
```

Pauses with no words (`ERROR_NO_MATCH`, `ERROR_SPEECH_TIMEOUT`) are handled inside `AndroidSpeechTranscriber` and never reported as problems. The 60 s silence stop lives in `VoiceInput`, so it can be unit tested.

### Behaviour

1. **The button shows only if voice works on the phone** (Android 12+ and `isOnDeviceRecognitionAvailable`). Otherwise it is hidden.
2. **First tap:** Android's own prompt asks to allow audio recording. If allowed, listening starts straight away with no second tap.
3. **While listening:**
   - The button reads "Listening... tap to stop" with the stop icon. It is full width, at least 48dp tall, and in a strong colour different from Improve.
   - The note box shows existing text plus live words and is read-only.
   - Improve is disabled.
   - Pauses do not end the session.
4. **Tap stop:** the last words are added, the box is editable again, and the button returns to "Tap to speak" with the mic icon.
5. **Stops by itself, keeping the words,** when the user leaves the main screen, when the app goes to the background, or after 60 s with no words heard. The 60 s clock starts at the tap and restarts on every live or finished event.
6. **Merge rule:** spoken text is added at the end with one space. No space is added when the box is empty or already ends in a space or new line. Formatted text is used when the phone provides it; otherwise words arrive plain.

### When things go wrong

Messages appear in plain English on a line under the voice button, separate from the Improve error card. They clear on the next tap.

| Situation | Message and action |
|---|---|
| Mic permission denied | "ShiftNote needs the microphone to hear you." Tapping again asks again. |
| Mic permission denied permanently (Android stops asking) | Same message, plus an "Open settings" button to ShiftNote's app settings page. |
| Language missing, Android 13+ | "Voice needs a one-time download of English speech." plus a "Download" button (`triggerModelDownload`). |
| Language missing, Android 12 | "Voice isn't set up on this phone. Use the keyboard mic instead." |
| Pause or no words | No message; keeps listening until the 60 s rule. |
| 60 s with no words | "Stopped listening. Tap to speak to carry on." |
| Anything else | "Voice stopped. Tap to speak to try again." |

- Any stop or error keeps the finished words and whatever live words were showing.
- Logs record the error code only, never the words heard.

### Privacy

- Audio goes to the phone's on-device recogniser. The docs call it on-device but do not promise that no audio is ever sent, so the airplane-mode check is evidence, not proof. Docs and user-facing text must not claim more than that.
- The transcript is treated like typed text: held in memory only (`noteText` is not saved), and redacted the same way in cloud mode.
- The mic is on only while listening, and listening stops when the screen or app is left.

## Testing

**Unit tests** (JVM, fake `SpeechTranscriber`, same pattern as `MainViewModelTest`):

- Merge rule: empty box; box ending in text; box ending in a space; box ending in a new line; several finished chunks in a row.
- `VoiceInput`:
  - start turns listening on
  - new live words replace the previous live words
  - finished words are kept
  - stop keeps the last words
  - a problem keeps the words and sets the right message
  - 60 s with no words stops listening (virtual time)
  - start while already listening is ignored
- `MainViewModel`: `restructure()` does nothing while listening.

Not unit tested (no Robolectric): `AndroidSpeechTranscriber`, the permission flow and the screen. The device checks cover them.

### Risk check (first plan task, on the Pixel 8a)

Answers the "may have no effect" points before the rest is built.

| Question | If no |
|---|---|
| 1. Is the strict on-device recogniser available? | Stop. Revisit the bundled-model approach. |
| 2. Does a segmented session listen through pauses? | Restart listening after each result until stop is tapped. Check how many words are lost at each restart. |
| 3. Do live words arrive? | The box updates on each finished chunk only. The "Listening..." button stays as the sign it's working. |
| 4. Does formatting add punctuation and capitals? | Words arrive plain. Improve tidies them. |
| 5. Is NZ English (`en-NZ`) installed or downloadable on-device? | Use whichever English is available and record which one in this spec. |
| 6. Does recognition work in airplane mode? | Treat it as possibly not on-device. Stop and revisit the design. |

### Final device check

1. Read made-up golden notes 2, 6 and 10 aloud. Count misheard names, shorthand and NZ words.
2. Speak a 2-minute note with pauses. Nothing is cut off.
3. Deny the mic once, then twice. "Open settings" opens the right page.
4. Leave the screen, then background the app, mid-note. Android's green mic dot goes off and the words stay.
5. Stay silent for 60 s. Listening stops with the message.
6. Dictate, then Improve, in Gemma mode and in cloud mode. The cloud preview shows the redacted dictated text.
7. Simplicity test (Goal, point 1).

## Estimate

About 6.5 hrs:

- risk check and engine: ~1.5 hrs
- `VoiceInput` and unit tests: ~1.5 hrs
- screen and permission: ~1.5 hrs
- error messages: ~1 hr
- final device check: ~1 hr

## Risk check results (2026-10-21, Pixel 8a, Android 16)

| Question | Answer | Action |
|---|---|---|
| 1. Strict on-device recogniser available | Yes | None |
| 2. Listens through pauses | Restart fallback. Segmented sessions have no effect: only `onResults` fires, never `onSegmentResults`, so recording stopped after the first sentence | `useSegmentedSession = false` in the `AppContainer` factory. With it off, 3 of 3 sentences arrived across 5 s and longer pauses, with no duplicates |
| 3. Live words arrive | Yes | None |
| 4. Punctuation and capitals | Yes ("nine" became "9:00", "half past one" became "1:30") | None |
| 5. Language | Phone locale `en-US-u-mu-celsius`: installed. **`en-NZ` is not supported on-device** (error 12); `en-AU`, `en-GB` and other English variants are downloadable | Phones set to NZ English use `en-AU` (closest accent). "Not supported" now shows the "isn't set up" message; only "unavailable" offers Download |
| 6. Works in airplane mode | Yes (airplane mode on, Wi-Fi off, no active network) | None |
| Extra: sound on each restart | No | None |

Also found:

- **Download path:** on an `en-AU` start before download (error 13), "Download" opens Google's own dialog (Android System Intelligence): "Download English (Australia) update. Download this update (124 MB) to use speech recognition offline." NZ users therefore face a 124 MB one-time download on first use. After it, `en-AU` transcribed correctly.
- **Stale recogniser after a download:** after the model installed, every start failed with error 11 (`ERROR_SERVER_DISCONNECTED`) until the app restarted, because the speech service restarted. The engine now discards its recogniser after any error and creates a new one on the next start. Checked by equivalence (a fresh recogniser after an app restart listened normally); the disconnect itself was not reproduced after the fix.
- **Accuracy hint:** "Sam chose two books" became "Those two books" with `en-US` and was correct with `en-AU`. One sentence only; the final device check counts properly.
