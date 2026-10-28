# ShiftNote: Original Design Spec

**Date:** 2026-09-09
**Status:** SUPERSEDED by `2026-09-17-inference-dual-path-design.md`. Gemini Nano never became available on the test phone, so the app moved to Gemma 3 1B via MediaPipe, plus an opt-in cloud mode. Kept to show the starting point.

## Overview

ShiftNote is a single-purpose Android app that restructures community support worker case notes from casual shorthand into professional prose, entirely on-device. No note content ever leaves the phone.

Built for one user (the developer, on a Pixel 8a) to validate the idea before considering a broader product.

## Decisions

- **Language/framework**: Native Kotlin with Jetpack Compose. Chosen over Flutter/RN because the on-device AI SDKs (ML Kit GenAI, MediaPipe) are Kotlin/Java-first. Bridging from a cross-platform framework would add complexity with no benefit for a single-platform MVP. Also fills a gap in the developer's skill set (existing experience is Flutter and React Native).
- **Inference engine**: ML Kit GenAI (Gemini Nano via AICore), with MediaPipe LLM Inference + Gemma 2B as fallback. Spike required to confirm Nano availability on the target device.
- **Note storage**: None. The app is a transform tool, not a record system. Notes are pasted in, restructured, copied out. No history is kept.

## Target device

Google Pixel 8a, Android 14+. 8GB RAM. AICore "on device genAI features" enabled in settings.

## Screens

### 1. Main screen

The primary workflow screen. Top to bottom:

- **Client dropdown**: lists saved client profiles, plus a permanent "Other / Cover" option. Selecting "Other / Cover" reveals a temporary free-text field for one-off context (not persisted).
- **Note input**: multi-line text field. User pastes or types their raw note.
- **"Restructure" button**: triggers on-device inference. Disabled while processing.
- **Loading indicator**: shown during inference (may take a few seconds).
- **Result display**: shows the restructured text. Read-only.
- **"Copy" button**: copies result to clipboard for pasting into the work app.

### 2. Settings screen

Accessed via a gear icon on the main screen.

- **Role**: text field (e.g. "Community support worker", "Aged care worker"). Persisted.
- **General context**: free text for standing context that applies across all clients. Persisted.
- **Raw system prompt**: advanced editor showing the full prompt template. Editable. Includes placeholders like `{role}`, `{client_goals}`, `{client_context}`, `{general_context}` that are filled at inference time. Defaults to a sensible starting prompt. Persisted.
- **"My Clients" link**: navigates to the client editor.

### 3. Client editor

Accessible from settings. List of saved client profiles with add/edit/delete.

Each client profile has:
- **Name**: the client's name or an alias. Never leaves the device.
- **Active goals**: free text describing current goals.
- **Extra context**: free text for anything else relevant (mobility, communication, plan references).

## Prompt assembly

At inference time, the app assembles the full prompt by combining:

1. The raw system prompt template (from settings)
2. The user's role (from settings)
3. General context (from settings)
4. Selected client's goals and context (from client profile, or the one-off text for "Other / Cover")
5. The note text (from the input field)

Default system prompt template:

```
You are helping a {role} write professional case notes.
{client_goals_section}
{client_context_section}
{general_context_section}

Rewrite the following notes as clear, professional prose. Keep all facts exactly as stated. Do not add information that is not present in the original notes. Do not remove any details. Output only the rewritten text.

{note}
```

Sections with empty values are omitted entirely (no blank lines or empty labels).

## Inference engine

### Primary: ML Kit GenAI (Gemini Nano)

- Uses the `GenerativeModel` API from `com.google.android.gms:play-services-mlkit-genai`
- Runs via AICore system service under Private Compute Core isolation
- No internet access: AICore is architecturally network-isolated
- Model managed by Google (updates arrive via Play Services)

### Fallback: MediaPipe LLM Inference + Gemma 2B

Only if the spike proves Gemini Nano is unavailable on the target device.

- Uses MediaPipe's `LlmInference` API
- Requires bundling or downloading a ~1.5GB model file
- Runs directly in-process, no dependency on AICore
- Same privacy guarantee: fully on-device

The inference layer will be behind a simple interface (`NoteRestructurer`) so swapping between engines requires changing one implementation, not rewiring the app.

## Data & storage

### Room database (local SQLite)

Client profiles table:
- `id`: auto-generated primary key
- `name`: String
- `activeGoals`: String
- `extraContext`: String

### SharedPreferences

- `role`: String
- `generalContext`: String
- `systemPrompt`: String (the raw template)
- `selectedClientId`: Long (last-used client, for convenience)

### No note storage

Notes are held in-memory only during the active session. Closing the app or starting a new note discards the previous one. The work app is the system of record.

## Privacy & security

1. **No internet permission**: the `INTERNET` permission is not declared in AndroidManifest.xml. The OS blocks all network calls at the system level. This is an enforced guarantee, not a policy.
2. **App sandbox**: Room DB and SharedPreferences are in the app's private internal storage. Other apps cannot access them.
3. **Device encryption**: Pixel 8a encrypts storage at rest by default. Client data benefits from this automatically.
4. **No auto-backup**: `android:allowBackup="false"` and `android:dataExtractionRules` configured to prevent client data from syncing to Google Drive.
5. **Private Compute Core**: AICore (Gemini Nano) operates under PCC isolation, which is architecturally separated from network access. To be verified during the spike.

## Testing & validation

### Developer testing

- **Unit tests**: ViewModel logic: prompt assembly with various client/context combinations, state transitions (input → loading → result → error).
- **Integration test**: End-to-end inference call with a known input, asserting a non-empty structured response.
- **Manual testing**: On the Pixel 8a. No automated UI tests for a 2-screen app.

### Golden set validation

Before trusting the app on real client notes:

1. Collect 10-15 example notes (anonymised or synthetic)
2. Hand-write the ideal restructured output for each
3. Run all through the on-device model
4. Score each output:
   - Does it follow the prompt style?
   - Does it preserve all factual content?
   - Does it invent anything not present in the original?
   - Is the language professional and appropriate?
5. The developer reviews and approves the results

This is the quality gate. The app is not considered validated until the golden set results are good enough.

## Implementation order

1. **Spike** (~30 min): Throwaway app that calls ML Kit GenAI on the Pixel 8a. Confirms Nano availability. Pass/fail decision point.
2. **Project scaffold**: Kotlin + Compose project, build config, dependencies.
3. **Inference layer**: `NoteRestructurer` interface + ML Kit implementation (or MediaPipe if spike fails).
4. **Main screen**: Input, restructure, result, copy flow.
5. **Client profiles**: Room DB, client editor screen, dropdown on main screen.
6. **Settings screen**: Role, context, prompt editor.
7. **Privacy hardening**: No internet permission, no backup, verify PCC isolation.
8. **Golden set validation**: Build test set, run through model, evaluate quality.

## Out of scope

- No redaction pipeline (not needed: nothing leaves the device)
- No cloud API fallback
- No multi-user / multi-org / accounts
- No iOS
- No billing
- No note history/storage
- No accessibility-service auto-fill into the work app
