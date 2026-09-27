---
name: changelog-wording-review
description: Review the wording of the most recent release block in changelog.txt (or given release notes) for proper, user-friendly English aimed at non-technical users. Use when asked to check, proofread, review, or polish the changelog / release notes wording. Suggestions only - never edits the file.
---

# Changelog Wording Review

Proofread the changelog entry wording for tinyMediaManager's **end users** (Kodi/Plex/Jellyfin
fans, not developers). Report problems and suggest better wording. **Never modify
`changelog.txt`** - the user decides what to apply.

**Light-touch editing only.** Fix grammar, tense, and phrasing so the entry reads as nice,
plain English. Never delete or water down factual/technical details (parameter names,
tokens, file names, settings paths) - users searching the changelog for exactly those
terms must still find them. At most add a short plain-English explanation *around* a
technical term. Never rewrite an entry wholesale when a few words would do.

## Workflow

1. Read `changelog.txt` in the repository root.
2. Extract the **newest version block**: from the first `Version x.y.z` line until the line
   before the next `Version x.y.z` header. If the user names a specific version, use that
   block instead.
3. Review every entry line against the style rules below.
4. Output the review (format below). Entries that are fine are not listed.

## Format rules (never "fix" these)

The block structure is machine-convention, keep it as-is and do not comment on it:

- `+` = feature/enhancement, `x` = fix, `---` = separator between the two groups
- `[Category]` tags like `[UI]`, `[TV shows]`, `[Core]`, `[Kodi]`
- trailing `#1234` issue references - always preserve them in suggestions
- the `Version x.y.z` + `=======` headers

## Style rules for non-technical readers

1. **Consistent tense/voice**: past tense, lowercase start after the tag -
   `added …`, `fixed …`, `improved …`, `removed …`. Flag `enhanced …` when vague (say *what* got better) and mixed forms
   like `fix`/`added`/`display` (see 5.3.1 block
   for real offenders).
2. **Grammatically complete, plain English**: no "enabled to …" (wrong verb pattern),
   no missing apostrophes (`systems temp folder` -> `system's temp folder`),
   hyphenate compound adjectives (`read-only`, `multi-episode`).
3. **Explain jargon, don't remove it**. Known/OK: NFO, Kodi, Plex, Jellyfin, Emby, data
   source, post-processing, renamer, TV show, artwork, trailer, Trakt.tv.
   For less familiar terms (JVM, checksum, NFC/NFD, "tokens", mediainfo, setting names
   like `tmm.mvstore.buffersize`): **keep the exact term** and, if helpful, add a brief
   plain-English hint in parentheses or reflow the sentence around it - e.g.
   "fixed reading of the JVM param `tmm.mvstore.buffersize`" ->
   "fixed the JVM parameter `tmm.mvstore.buffersize` not being applied".
   Only suggest a synonym for purely internal wording (e.g. "mediainfo" as prose ->
   "media information") when no user would search for the original form.
4. **Lead with the user-visible effect** where the sentence allows it, *without* dropping
   the technical detail. "added a guard to prevent adding/processing nested data sources"
   -> "adding or processing nested data sources is now blocked". Passive/gerund-heavy
   phrasing ("fixed storing of …", "fixed displaying of …") -> prefer "fixed an issue
   where … was not saved/shown" or "… now works correctly".
5. **One idea per entry, readable length**: flag lines over ~160 characters or that
   list 4+ unrelated things; suggest splitting or trimming (issue refs don't count).
6. **US spelling** throughout (modernized, not modernised); sentence case - do not
   capitalize random words mid-sentence (`Display` after the tag is wrong).
7. **No double negatives** ("do not list … as missing when … is disabled" -> say when
   the positive behavior applies, if a rewrite keeps it truthful).
8. **Do not change factual meaning.** If a truthful plain-English rewrite is impossible
   without developer terms (e.g. a token name users must type into the renamer), keep
   the term but add brief user-facing context. Mark such lines "OK - keep term" instead
   of suggesting a lossy rewrite.

## Worked examples (from real entries)

| Original                                                                              | Suggested                                                                                                            |
|---------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| `+ [UI] enabled to open the editor for locked entries in read only mode`              | `+ [UI] the editor can now be opened for locked entries in read-only mode`                                           |
| `x [Core] fixed reading of the JVM param tmm.mvstore.buffersize`                      | `x [Core] fixed the JVM parameter tmm.mvstore.buffersize not being read` (keeps the exact name, only tidies grammar) |
| `x [UI] wrap ratings and mediainfo logos on demand in the information panel #3358`    | `x [UI] ratings and media information logos now wrap correctly in the information panel #3358`                       |
| `x [Core] fall back to the content folder if the systems temp folder is not writable` | `x [Core] the app now falls back to the content folder if the system's temp folder is not writable`                  |

## Output

Start with a one-line header naming the reviewed version, then a markdown table:

```
## Changelog review - Version 5.3.3

| Line | Original | Suggested | Why |
|---|---|---|---|
| 65 | x [Core] fall back to the systems temp folder … | x [Core] the app now falls back to the … | rule 2 - grammar |
```

- `Line` = line number in `changelog.txt` so the user can jump to it.
- `Why` = short rule reference (e.g. "rule 2 - grammar", "rule 4 - developer-speak").
- If a whole block reads well, say so explicitly instead of inventing nitpicks.
- End with at most 2 general observations (e.g. "most `[API]` entries are too
  technical for this audience") - no editing.
