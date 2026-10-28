# Improve Mode: Professional Role Language + Paragraph/Sections Style

**Date:** 2026-10-21
**Status:** BUILT and checked on a Pixel 8a (2026-10-21). The app works as designed, but Gemma 3 1B's rewrite quality is not good enough yet (see Status in the README).

## Problem

On the first real phone test, the app returned the note almost unchanged with a bracketed note added at the end. The default prompt asks for "clear, professional prose" but then mostly forbids change (keep facts exactly, remove nothing, add nothing, copy brackets exactly). Its one rule that invites new text is `[REVIEW: reason]`. With Gemma 3 1B at temperature 0.2, copying the note and adding a tag is the likely result (not yet confirmed on device). The prompt never says what "better" means.

A second problem: the Settings prompt box saves on every keystroke, and a saved prompt replaces `DEFAULT_TEMPLATE` for good. Prompt fixes in code never reach a phone whose prompt has been edited.

## Where this fits

The app will have two actions on a drafted note:

1. **Improve** (this spec): rewrites the note in professional language for the user's role, in a style chosen once in Settings.
2. **Suggest** (later spec): leaves the note untouched and returns a list of fixes and questions. It reuses the same style rules.

Build order: (1) Improve mode, (2) golden-set scoring script, (3) Suggest mode, (4) onboarding. Each gets its own spec and plan.

## Solution

Code owns a fixed core prompt: role, the professional language rules, safety rules and the chosen style. The user-editable full template is removed. Settings gains an **Improve style** choice (Paragraph or Sections) and an optional **Extra instructions** box that is appended to the core prompt.

## Professional language rules

Based on general support-work practice, since no written note guidance was available.

1. Write what you saw or heard, not what you assume.
2. Keep the client's own words.
3. Use respectful, non-judgemental words. If a judgement word can't be swapped without knowing what happened, keep it and add `[REVIEW]`.
4. Show choice and support.
5. Plain English, no shorthand or slang.
6. Keep the writer's voice and the order things happened.
7. Never add, upgrade or diagnose.

## Prompt

Assembled in this order. Blank parts are left out entirely (no empty lines left behind). Wording is a starting point; spec 2's scoring script is where it gets tuned.

```
You are helping a {role} write professional support notes.
Active goals: {client_goals}                       (only if set)
Additional context: {client_context}               (only if set)
General context: {general_context}                (only if set)

Rewrite the note below in professional language for a {role}.

Rules:
- Write what was seen or heard, not what is assumed. "X was tired" becomes "X appeared tired".
- Keep the client's own words. Do not reword what X said.
- Use respectful, non-judgemental words. "refused" becomes "declined". If a judgemental word cannot be replaced without guessing, keep it and add [REVIEW: short reason].
- Show choice and support. "took X to the gym" becomes "supported X to go to the gym".
- Use plain English. Expand shorthand and slang: "w/" becomes "with", "maccas" becomes "McDonald's".
- Keep the writer's voice (I, we) and the order events happened.
- Never add, upgrade or diagnose. "happy" never becomes "elevated mood". "sore" never becomes "injured".
- If something is unclear, keep it and add [REVIEW: short reason] after it.
- Do not fill in missing details such as times, outcomes or reasons.
- Use the goals and context above as background only. Do not write about them.
- Copy any text in square brackets, such as [CLIENT] or [PHONE_1], exactly as written.   (cloud mode only)

Format:
{format block for the chosen style}

Also follow these instructions: {extra_instructions}   (only if set)

Output only the rewritten note.

NOTE TO REWRITE:
{note}
```

**Format blocks:**

- **Paragraph:** `Write plain paragraphs in the order events happened. No headings, bullet points or markdown.`
- **Sections:** `Use these plain-text headings, in this order: "Activities:", "Wellbeing:", "Follow-up:". Put each fact under one heading, on a line starting with "- ". Keep the order events happened within each heading. Leave out a heading with nothing under it. No markdown such as ** or #.`

**Role fallback:** a blank role becomes `support worker`.

**Placeholder rule in cloud mode only:** in on-device mode no redaction happens, so the `[CLIENT]` example only teaches Gemma to write `[CLIENT]` into the note (seen on golden note 10).

## Components

### PromptAssembler

- Remove `DEFAULT_TEMPLATE` and the `template` parameter.
- Add `enum class ImproveStyle { PARAGRAPH, SECTIONS }` in `PromptAssembler.kt`.
- New signature:

```kotlin
fun assemble(
    role: String,
    style: ImproveStyle,
    generalContext: String,
    clientGoals: String,
    clientContext: String,
    extraInstructions: String,
    note: String,
    keepPlaceholders: Boolean
): String
```

### UserPreferences

- Add `improveStyle: ImproveStyle` (stored as the enum name, default `PARAGRAPH`, unknown values read as `PARAGRAPH`).
- Add `extraInstructions: String` (default blank).
- Remove `systemPrompt`. On first load, delete the old `system_prompt` key so the stale prompt is gone.

### MainViewModel

- Replace `getSystemPrompt: () -> String` with `getImproveStyle: () -> ImproveStyle` and `getExtraInstructions: () -> String`.
- On-device path: `keepPlaceholders = false`.
- Cloud path: `keepPlaceholders = true`, and extra instructions go through `Redactor.redact()` like the other user-entered strings.

### SettingsViewModel

- Remove `systemPrompt`, `saveSystemPrompt`, `resetPromptToDefault`.
- Add `improveStyle` / `saveImproveStyle` and `extraInstructions` / `saveExtraInstructions`, following the existing save-on-change pattern.

## Screens

**Main screen**
- "Restructure" button becomes "Improve"; the loading text changes to match.
- Result stays plain text so it pastes cleanly into the organisation's app. Copy and New Note are unchanged.

**Settings**
- Under the existing Role field: **Improve style** radio group, Paragraph (default) or Sections.
- Advanced: the System prompt box, its placeholder help text and "Reset to default prompt" are replaced by one **Extra instructions (optional)** text field, saved on change.

## Files

| File | Change |
|---|---|
| `inference/PromptAssembler.kt` | Fixed core prompt, `ImproveStyle` enum, new `assemble` signature |
| `data/prefs/UserPreferences.kt` | Add `improveStyle`, `extraInstructions`; remove `systemPrompt` and its stored key |
| `ui/main/MainViewModel.kt` | New getters, `keepPlaceholders` per path, redact extras in cloud mode |
| `ui/main/MainScreen.kt` | Wire new getters; button and loading text |
| `ui/settings/SettingsViewModel.kt` | Style and extras state; remove prompt state |
| `ui/settings/SettingsScreen.kt` | Style radio group; Extra instructions field replaces prompt editor |
| `test/.../PromptAssemblerTest.kt` | Rewritten for the new prompt |
| `test/.../MainViewModelTest.kt` | Updated constructor and new tests |

No new files.

## Testing

**Unit tests (JVM)**

`PromptAssemblerTest`:
- All seven professional language rules are present.
- Paragraph style includes the paragraph block and none of the section headings.
- Sections style includes the three headings in order and not the paragraph block.
- The square-bracket placeholder rule appears only when `keepPlaceholders` is true.
- Extra instructions appear only when not blank.
- A blank role becomes `support worker`.
- Empty goals and context sections are left out entirely.
- The note text is the last thing in the prompt.

`MainViewModelTest`:
- On-device prompt has no placeholder rule; cloud prompt has it.
- The chosen style and extra instructions reach the prompt.
- Extra instructions are redacted in cloud mode.

Run: `./gradlew testDebugUnitTest assembleDebug`.

**On device (manual, made-up notes only)**

Run golden notes 2, 6 and 10 (`docs/eval/golden-set.md`) in both styles on Gemma: 6 runs, about 15 minutes. Check that facts are kept, nothing is invented, no `[CLIENT]` appears, and note 10 is not cut off.

**Done when:** tests and build pass, and the 6 device outputs have been read and recorded. Done means the code works as designed, not that Gemma 1B writes good notes. Model quality is measured by spec 2.

## Risks

- **Token budget:** the prompt grows by about 200 tokens. `GemmaRestructurer.MAX_TOKENS` is 1024 for input and output combined, so long notes (golden note 10) may be cut off. Raising it depends on the KV cache size the `.task` file was built with, which is unverified.
- **Sections on a 1B model:** it may invent content to fill a heading or file a fact under the wrong one. The device runs check this.
- **Rule 4** ("supported X to") changes meaning slightly. Kept on purpose; watch for it in outputs.
- **Saved prompt dropped:** any edits in the current Settings prompt box are lost once. Only the developer's test phone is affected.

## Out of scope

- Suggest mode and its list UI
- Golden-set scoring script (spec 2)
- Onboarding
- `[REVIEW]` highlighting on the result screen
- "No client" option in the client dropdown
- Changing the model, temperature or `MAX_TOKENS`
