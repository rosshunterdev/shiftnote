# Dual-Path Inference: On-Device Primary + Pseudonymised Cloud Fallback

**Date:** 2026-09-17
**Status:** BUILT. Gemma and cloud paths are in the app and the Gemini Nano path is removed. One change from this design: the Redactor replaces the selected client's name, phone numbers, street addresses and NHI numbers, but does not detect other people's names (see Privacy in the README).

## Problem

Gemini Nano via AICore is unavailable on Pixel 8a (AICore Developer Preview beta has not delivered the model after a week). The app builds and runs but cannot perform its core function: restructuring notes.

## Solution

Replace the single Gemini Nano inference path with two backends behind the existing `NoteRestructurer` interface:

1. **On-device (default):** MediaPipe LLM Inference + Gemma 3 1B. No network, no API key, model loaded from device storage.
2. **Cloud fallback (opt-in):** Gemini Flash API with on-device PII redaction. User provides their own free API key. Only pseudonymised text leaves the device.

## Architecture

```
                         ┌─ On-device (default) ──→ GemmaRestructurer
User → MainViewModel ──→│                           (MediaPipe + Gemma 3 1B)
                         └─ Cloud (opt-in) ────→ Redactor ──→ GeminiCloudRestructurer
                                                  ↕               (Gemini Flash API)
                                             Restore names
```

The `AppContainer` reads the user's inference mode preference and injects the appropriate `NoteRestructurer` implementation. The `NoteRestructurer` interface is unchanged.

## On-Device Path: MediaPipe + Gemma

**Model:** Gemma 3 1B (~1.3GB), free and open-source under Google's Gemma license.

**Model delivery:** Manual download from Kaggle/HuggingFace, pushed to device via `adb push`. The app loads the `.task` file from app-specific internal storage. No in-app download, no internet required.

**New class:** `GemmaRestructurer` implements `NoteRestructurer`
- Uses MediaPipe `LlmInference` API
- `checkModelStatus()` checks if model file exists on device
- `restructure(prompt)` runs local inference
- `downloadModel()` is a no-op (model is manually loaded)

**Dependency:** `com.google.mediapipe:tasks-genai`

## Cloud Path: Gemini Flash + Pseudonymisation

### Redactor

The `Redactor` class strips PII from user-provided text before it leaves the device, and restores real names into the response.

**What gets redacted:**
- Client name from the selected `ClientEntity` (exact + partial match: "John Smith", "John", "Smith") → `[CLIENT]`
- Other capitalised names not in a known-places list → `[PERSON_1]`, `[PERSON_2]`, etc.
- NZ phone numbers (02x, 04x, +64 patterns) → `[PHONE_1]`
- Street addresses (number + street name pattern) → `[ADDRESS_1]`
- NHI numbers (3 letters + 4 digits) → `[NHI_1]`

**What is NOT redacted:**
- Role and template text (system instructions written by the developer, not user-entered PII)
- Place names like suburbs/cities (needed for contextual accuracy)

**Redaction scope:** Applied to the four user-provided strings (note text, clientGoals, clientContext, generalContext) before they enter `PromptAssembler`. The assembled prompt template and role are not redacted.

**Review screen:** Before the API call, the user sees the redacted prompt in a `RedactedPreviewDialog` and can confirm or cancel. Safety net for anything the Redactor missed.

### GeminiCloudRestructurer

**New class:** `GeminiCloudRestructurer` implements `NoteRestructurer`
- Uses Google `generativeai` Android SDK
- Takes API key from `UserPreferences`
- `checkModelStatus()` returns AVAILABLE if API key is set, UNAVAILABLE otherwise
- `restructure(prompt)` calls Gemini Flash, returns result
- `downloadModel()` is a no-op

**Dependency:** `com.google.ai.client.generativeai`

### Cloud flow

```
User taps Restructure
    ↓
Redactor strips PII from note/goals/context
    ↓
PromptAssembler builds prompt from redacted text
    ↓
RedactedPreviewDialog shows redacted prompt for confirmation
    ↓
GeminiCloudRestructurer calls Gemini Flash API
    ↓
Redactor restores real names in the response
    ↓
Final result displayed
```

### API Key Management

- User obtains a free key from Google AI Studio (aistudio.google.com)
- Key stored on-device using Android `EncryptedSharedPreferences`
- API key field only visible in Settings when cloud mode is selected
- Free tier: 15 req/min, 1,500 req/day (well beyond personal use)

### Cost

Gemini Flash at personal usage (5-10 notes/day): **free** within the free tier. Paid rate if exceeded: ~$0.0001 per note.

## Settings Screen Changes

New section in Settings:

1. **Inference mode** - toggle between "On-device" (default) and "Cloud (pseudonymised)"
2. **API key** - text field, only shown when cloud mode is selected
3. **Help text** - "Cloud mode strips all names before sending. Get a free API key from Google AI Studio."

## Manifest Change

Replace:
```xml
<uses-permission android:name="android.permission.INTERNET" tools:node="remove" />
```
With:
```xml
<uses-permission android:name="android.permission.INTERNET" />
```

**Privacy story update:** From "OS blocks all network access" to "network is only used when you opt into cloud mode, and only pseudonymised text is sent." This is a weaker guarantee (behavioural vs architectural).

## GeminiNanoRestructurer

Removed. Dead code with a dependency on an unavailable AICore feature.

## Files

**New:**
| File | Purpose |
|------|---------|
| `inference/GemmaRestructurer.kt` | On-device MediaPipe + Gemma |
| `inference/GeminiCloudRestructurer.kt` | Cloud Gemini Flash API |
| `inference/Redactor.kt` | PII stripping + restoration |
| `ui/main/RedactedPreviewDialog.kt` | Confirmation before cloud send |

**Modified:**
| File | Change |
|------|--------|
| `ShiftNoteApp.kt` | AppContainer picks restructurer based on setting |
| `MainViewModel.kt` | Cloud path adds redaction/restore around inference |
| `SettingsScreen.kt` | Inference mode toggle + API key field |
| `SettingsViewModel.kt` | New preferences for mode + key |
| `UserPreferences.kt` | Store inference mode + encrypted API key |
| `AndroidManifest.xml` | INTERNET permission |
| `build.gradle.kts` | MediaPipe + Generative AI SDK dependencies |

## Testing

- **Unit tests:** `Redactor` - known name replacement, regex patterns (phone, address, NHI), restore roundtrip, edge cases (partial names, no PII present)
- **Existing tests:** `PromptAssemblerTest` must still pass (interface unchanged)
- **Manual on-device:** On-device mode with Gemma model loaded, cloud mode with API key entered
