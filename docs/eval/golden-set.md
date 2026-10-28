# Golden Set: 10 Test Notes

> These notes were drafted with AI help. A review for realism by a working support worker is still pending.

**Every detail here is made up.** No real clients, places or events. Safe to send to cloud models for testing.

Used for two kinds of test:
- **Rewrite test** (Improve mode): compare model output against the **Ideal rewrite**.
- **Suggestion test** (planned Suggest mode): compare suggestions against **Should suggest** and **Should NOT flag**.

## Conventions

- The client is "X", a common convention in support notes, except where a note deliberately tests a name slip.
- **Ideal rewrites never add facts.** Where the raw note lacks something, the rewrite leaves it out or marks it `[REVIEW: ...]`, the way the app's prompt asks the model to. No note template is assumed, because organisations use different formats. Rewrites are clean chronological prose.
- Suggestion numbers refer to check types (#1 completeness, #2 objective language, #3 shorthand/clarity, #4 ambiguous references, #6 privacy, #7 follow-up/escalation, #12 action items, #13 activity data).

## Coverage

| # | Note | Main thing it tests |
|---|---|---|
| 1 | Library visit | **Clean note.** Should get almost no suggestions (false-positive test) |
| 2 | Bank errand | Heavy shorthand |
| 3 | Walk and café | **Client name slip** |
| 4 | Day programme | Third-party names (another client, staff) |
| 5 | Gym session | Data block, number contradiction, typo |
| 6 | Home exercises | Pain mentioned, missing equipment, follow-ups |
| 7 | Chores at home | Subjective/judgemental wording, no outcome |
| 8 | Flat visit | Ambiguous "he" (client vs flatmate) |
| 9 | Supermarket trip | Incident (fall) and missed medication, no action recorded |
| 10 | Full day out | **Long note (200+ words)**: vague catch-all, buried follow-ups, token limit test |

---

## Note 1: Library visit (clean)

**Raw:**
> Picked up X at 10am and we went to the library. X returned his two books and chose three new ones, mostly about trains. He asked the librarian for help finding a book on steam engines and did this himself. We had a hot chocolate at the café next door. X said he enjoyed the outing. Dropped X home at 12pm, he was relaxed and said he'd start one of the books tonight.

**Ideal rewrite:**
> Picked up X at 10am and went to the library. X returned two books and chose three new ones, mostly about trains. He independently asked the librarian for help finding a book on steam engines. We had a hot chocolate at the café next door. X said he enjoyed the outing. Dropped X home at 12pm. He was relaxed and said he planned to start one of his books that evening.

**Should suggest:** nothing required. At most one optional suggestion.

**Should NOT flag:**
- "relaxed" (an end-of-session state, commonly accepted)
- "X said he enjoyed" (the client's own voice)
- "the librarian" (a role, not a name)
- "did this himself" (independence, a positive and relevant observation)

---

## Note 2: Bank errand (shorthand)

**Raw:**
> picked up X @ 10, went w/ him to bank bc he needed new eftpos card. waited approx 20min then talked w/ teller abt card. X v happy after, got maccas b4 home. dropped off 11.30

**Ideal rewrite:**
> Picked up X at 10am and went with him to the bank because he needed a new EFTPOS card. We waited about 20 minutes, then X spoke with the teller about the card. X was very happy afterwards. We got McDonald's before heading home. Dropped X off at 11:30am.

**Should suggest:**
- #3 REPLACE: "@" → "at", "w/" → "with", "bc" → "because", "abt" → "about", "v" → "very", "b4" → "before", "approx" → "about"
- #3 REPLACE (optional): "maccas" → "McDonald's"
- #1 PROMPT (optional): "Did X get the new card, or is there a follow-up?" (the outcome of the errand isn't stated)

**Should NOT flag:**
- "X v happy" as subjective. It's a common positive observation (only the "v" expands).
- the times (both present)

---

## Note 3: Walk and café (client name slip)

**Raw:**
> Met X at his place and we walked along the waterfront to the café. X talked about his nephew's birthday party on Saturday and that he wants to make a card. Mike ordered a toastie and paid with his own card. On the way back we stopped at the dairy so X could buy some stamps.

**Ideal rewrite:**
> Met X at his place and we walked along the waterfront to the café. X talked about his nephew's birthday party on Saturday and said he wants to make a card. X ordered a toastie and paid with his own card. On the way back we stopped at the dairy so X could buy stamps. [REVIEW: no end-of-session state recorded]

(In the app, the Redactor knows the client's real name from the client list, so "Mike" being X is detectable. For a cloud test with no client list, the rewrite should at least flag "Mike" rather than guess who he is.)

**Should suggest:**
- #6 PRIVACY (required): "'Mike' looks like a person's name. Is this X? Replace with X."
- #12 ACTION_ITEM (optional): "Nephew's birthday Saturday: X wants to make a card. Add to handover?"
- #1 PROMPT (optional): "How was X at the end of the session?"

**Should NOT flag:**
- "Saturday" (a weekday, not a name)
- "paid with his own card" (independence, fine)

---

## Note 4: Day programme (third-party names)

**Raw:**
> Took X to the day programme for 9:30. He joined the cooking group and helped make scones. Hemi from the programme sat with X and they talked about rugby the whole time. Jo the coordinator said X can join the Friday art class from next week if he wants. X said yes. Picked X up at 2pm, he was tired but in good spirits.

**Ideal rewrite:**
> Took X to the day programme for 9:30am. He joined the cooking group and helped make scones. Another participant sat with X and they talked about rugby. The programme coordinator said X can join the Friday art class from next week, and X said he would like to. Picked X up at 2pm. He was tired but in good spirits.

**Should suggest:**
- #6 PRIVACY (required): "'Hemi' looks like another person's name. Use a role ('another participant')?"
- #6 PRIVACY (recommended): "'Jo' is a staff name. Check your org's rule on naming staff."
- #12 ACTION_ITEM: "X starts Friday art class from next week. Add to handover?"

**Should NOT flag:**
- "tired but in good spirits" (a clear, accepted end state)
- "the whole time" (casual, but not a problem)

---

## Note 5: Gym session (data block, contradiction, typo)

**Raw:**
> 91kg
> treadmil 25 min
> bike 10 min
>
> Met X at home, he was keen to go so we headed straight to the gym. X did around 20-25 mins on the treadmil, then 10 mins bike and some stretching. He said his legs felt good. Walked back and X made a sandwich.

**Ideal rewrite:**
> Met X at home. He was keen to go, so we headed straight to the gym. X did 20 to 25 minutes on the treadmill [REVIEW: data block above the note says 25 min], 10 minutes on the bike, then some stretching. He said his legs felt good. We walked back and X made himself a sandwich.
> [REVIEW: unlabelled figures (91kg, treadmill 25 min, bike 10 min). Confirm whether these are today's results, targets or a baseline.]

**Should suggest:**
- #13 DATA (required): "Figures at the top have no label. Today's results, targets or a baseline?"
- #13 DATA (required): "Treadmill is '25 min' at the top but '20-25 mins' below. Which is right?"
- #3 REPLACE: "treadmil" → "treadmill" (both places)

**Should NOT flag:**
- "he was keen to go" (motivation, fine)
- "He said his legs felt good" (client's voice)

---

## Note 6: Home exercises (pain, missing equipment, follow-ups)

**Raw:**
> Arrived and did the exercise program after a quick catch up. X said his right shoulder was a bit sore. Wall push ups 2 sets only. Band rows fine, could go heavier band next time. Couldnt do step ups, step is broken. Bridges fine. Played uno after.

**Ideal rewrite:**
> Arrived and did the exercise programme after a quick catch-up. X said his right shoulder was a bit sore. Wall push-ups: 2 sets only. Band rows: fine, could move to a heavier band next time. Step-ups: couldn't do, as the step is broken. Bridges: fine. We played Uno afterwards.
> [REVIEW: shoulder soreness reported. Was any action taken or anyone told?]
> Next session: try a heavier band for rows. Step needs replacing or an alternative exercise.

**Should suggest:**
- #7 FOLLOW_UP (required): "Shoulder pain is mentioned. Note any action taken (e.g. changed exercises, told someone), and check whether your org needs it reported."
- #12 ACTION_ITEM: "'heavier band next time' and 'step is broken' look like follow-ups. Collect them in a 'Next session' line?"
- #13 PROMPT (optional): "'2 sets only': out of how many planned? Was this because of the shoulder?"
- #3 REPLACE: "Couldnt" → "Couldn't"

**Should NOT flag:**
- "quick catch up" (no detail needed)
- "Bridges fine" (terse but clear)

---

## Note 7: Chores at home (subjective wording)

**Raw:**
> X was in a mood today and being difficult. Refused to do the dishes and was attention seeking the whole time. Eventually did the washing. Left at 3.

**Ideal rewrite:**
> [REVIEW: describe what X said or did. "In a mood", "being difficult" and "attention seeking" are interpretations, not observations.] X did not do the dishes. X did the washing. Left at 3pm.
> [REVIEW: no start time, no reason given for declining the dishes, no end-of-session state.]

(A rewrite must **not** invent behaviours to replace the judgements, e.g. "X raised his voice". This note is a key hallucination trap.)

**Should suggest:**
- #2 OBJECTIVE (required): "'in a mood': what did you see or hear?"
- #2 OBJECTIVE (required): "'being difficult': what did X do?"
- #2 OBJECTIVE (required): "'attention seeking' is a judgement. What did X do?"
- #2 OBJECTIVE (recommended): "'Refused': did X say why? Consider 'declined', with his reason if known."
- #1 PROMPT: "How was X when you left?"

**Should NOT flag:**
- "Eventually did the washing" (factual)

---

## Note 8: Flat visit (ambiguous "he")

**Raw:**
> Visited X at the flat. His flatmate was home and he was making lunch. He asked if we could go for a walk later but he said he had to go to work. X and I went for a walk to the park and back, about 40 mins. He seemed more chatty than last week.

**Ideal rewrite:**
> Visited X at his flat. His flatmate was home [REVIEW: unclear who was making lunch, X or the flatmate]. [REVIEW: unclear who asked to go for a walk and who had to go to work.] X and I walked to the park and back, about 40 minutes. [REVIEW: unclear whether "more chatty" refers to X.]

**Should suggest:**
- #4 PROMPT (required, needs a model): "Who was making lunch: X or his flatmate?"
- #4 PROMPT (required, needs a model): "'He asked… but he said…': who is each 'he'?"
- #4 PROMPT (recommended, needs a model): "Who seemed more chatty?"

**Should NOT flag:**
- "flatmate" (a role, already privacy-safe)
- "about 40 mins" (an approximate duration is fine)

---

## Note 9: Supermarket trip (incident and medication)

**Raw:**
> Took X to the supermarket at 1pm. In the carpark X tripped on the kerb and fell, grazed his hand. He said he was fine. We did the shopping, X used his list and paid. X mentioned he forgot his morning meds. Dropped him home at 2.30.

**Ideal rewrite:**
> Took X to the supermarket at 1pm. In the carpark X tripped on the kerb and fell, grazing his hand. He said he was fine. [REVIEW: fall and injury. Record first aid given and who was informed.] We did the shopping. X used his list and paid. X said he had forgotten his morning medication. [REVIEW: missed medication. Record what action was taken.] Dropped X home at 2:30pm.

**Should suggest:**
- #7 FOLLOW_UP (required): "A fall and an injury are mentioned. Record any first aid and who was told, and check whether your org needs an incident report."
- #7 FOLLOW_UP (required): "Missed medication is mentioned. Record what was done (e.g. reminded X, told someone)."
- #6 / wording check: none

**Should NOT flag:**
- "He said he was fine" (client's voice, keep it)
- "X used his list and paid" (independence, fine)

(The tool must **not** state what the organisation's incident process is. It only prompts the worker to check.)

---

## Note 10: Full day out (long note, 200+ words)

**Raw:**
> Picked up X from his place and we drove out to the beach because the weather was nice. On the way X talked about his brother coming to visit and the new phone he wants to buy, and other things. At the beach we walked along the sand for about half an hour, X picked up shells for his collection and said he wanted to come back in summer to swim. We had fish and chips for lunch at the takeaway on the main road, X ordered and paid himself and counted his change. After lunch we went to the op shop and X bought a jacket for $12, he tried on a few before picking one. He was really chatty all day and seemed happy. On the drive back X fell asleep for a bit. When we got back X said he is going to his mum's next weekend so won't need support on Saturday. He also reminded me his dentist appointment is on Tuesday at 10 and asked if someone can take him. X put his shells on the windowsill and showed me his collection. Need to check with the coordinator about the Tuesday transport. Good day overall.

**Ideal rewrite:**
> Picked up X from his place and we drove to the beach because the weather was nice. On the way X talked about his brother coming to visit and a new phone he wants to buy. We walked along the beach for about half an hour. X collected shells and said he would like to come back in summer to swim. We had fish and chips for lunch. X ordered, paid and counted his change himself. Afterwards we went to the op shop, where X tried on a few jackets and bought one for $12. He was chatty throughout the day. X slept for part of the drive home. Back home, X put his shells on the windowsill and showed me his collection.
> [REVIEW: no start or finish times recorded.]
> Follow-up:
> - X is at his mum's next weekend. No support needed on Saturday.
> - Dentist appointment Tuesday at 10am. X asked for someone to take him. Check transport with the coordinator.

**Should suggest:**
- #3 PROMPT: "'and other things' is vague. Anything worth recording, or remove it?"
- #12 ACTION_ITEM (required): "3 follow-ups found: no support Saturday, dentist Tuesday 10am, check transport with the coordinator. Collect them in a handover line?"
- #2 (optional, low): "'seemed happy': consider adding what you saw (e.g. chatty, smiling)." Borderline: "really chatty" is already given as evidence, so suppressing it is also acceptable.
- #1 PROMPT (optional): "No start or finish time recorded."

**Should NOT flag:**
- "X ordered and paid himself and counted his change" (independence, good detail)
- "$12" (money amount, fine)
- "his brother", "his mum" (relationships, not names)
- "Good day overall" (a mild summary, fine at the end)

**Also tests:** output is not cut off (MAX_TOKENS), and all 3 follow-ups survive the rewrite.

---

## Scoring sheet

One row per note per engine. Fill in during the 10-note test.

**Rewrite test:**

| Note | Engine | Facts kept (all / some / few) | Invented anything? (Y/N, what) | Gaps flagged with [REVIEW]? | Cut off? | Notes |
|---|---|---|---|---|---|---|
| 1 | | | | | | |

**Assistant test:**

| Note | Engine | Required suggestions caught (x / total) | Noise (unhelpful suggestions) | "Should NOT flag" items wrongly flagged | Any suggestion added a fact? (must be N) |
|---|---|---|---|---|---|
| 1 | | | | | |
