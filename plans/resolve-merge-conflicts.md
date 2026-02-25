# Merge Conflict Resolution Plan

## Overview

This document outlines the plan to resolve all merge conflicts in the scoring-system project. The conflicts appear to be between an "Updated upstream" branch and "Stashed changes".

## Files with Conflicts

### 1. `src/main/resources/application.properties`

**Conflict Location:** Lines 5-8

**Upstream version:** No `server.port` specified
**Stashed version:** Has `server.port=8080`

**Resolution:** Keep `server.port=8080` from stashed changes. This is a useful configuration to have explicit.

---

### 2. `src/main/java/org/thingai/app/scoringservice/define/BroadcastMessageType.java`

**Conflict Location:** Lines 11-15

**Upstream version:** Has `SHOW_MATCH` constant
**Stashed version:** Has `PLAY_SOUND` constant

**Resolution:** Keep BOTH constants - they serve different purposes:
```java
public static final String SHOW_MATCH = "SHOW_MATCH";
public static final String PLAY_SOUND = "PLAY_SOUND";
```

---

### 3. `src/main/java/org/thingai/app/scoringservice/handler/LiveScoreHandler.java`

**Multiple conflicts throughout the file:**

#### Conflict 1 (Lines 24-27): Static matchTimerHandler field
- **Upstream:** No static field
- **Stashed:** Has `private static final MatchTimerHandler matchTimerHandler = new MatchTimerHandler();`
- **Resolution:** Remove the static field - the instance field is already properly initialized in the constructor.

#### Conflict 2 (Lines 42-46): typicalMatchDuration field
- **Upstream:** No field
- **Stashed:** Has `private int typicalMatchDuration = 180;`
- **Resolution:** Keep the field from stashed changes, but note that `MATCH_DURATION_SECONDS` already exists. Use one consistently.

#### Conflict 3 (Lines 117-150): startCurrentMatch method
- **Upstream:** Simple timer start
- **Stashed:** Has 3-second countdown with sound playback
- **Resolution:** Keep stashed changes - the countdown and sound features are valuable enhancements.

#### Conflict 4 (Lines 480-510): abortCurrentMatch method
- **Upstream:** Simple reset
- **Stashed:** Comprehensive abort with state management and broadcast
- **Resolution:** Keep stashed changes - better state management and proper cleanup.

---

### 4. `webui/src/app/features/scoring-display/scoring-display.css`

**Conflict Location:** Lines 236-397

**Upstream version:** Has "Up Next" view styles (`.upnext-*` classes)
**Stashed version:** Has logo panel styles (`.logo-panel`, `.fanroc-logo`, `.team-separator`)

**Resolution:** Keep BOTH sets of styles - they serve different UI components:
- Up Next styles for the upcoming match display
- Logo panel styles for the main match display

---

### 5. `webui/src/app/features/scoring-display/scoring-display.html`

**Conflict Location:** Lines 17-81

**Upstream version:** Has `@if (displayMode() === 'match')` conditional with match view
**Stashed version:** Has logo panel inside timer panel

**Resolution:** Merge both:
- Keep the `displayMode` conditional structure from upstream
- Add the logo panel from stashed changes inside the timer panel

---

### 6. `webui/src/app/features/scoring-display/scoring-display.ts`

**Multiple conflicts:**

#### Conflict 1 (Lines 22-26): durationSec signal
- **Upstream:** `signal(180)`
- **Stashed:** `signal(180); // 3 minutes`
- **Resolution:** Keep the comment from stashed changes for clarity.

#### Conflict 2 (Lines 481-530): Methods
- **Upstream:** Has `formatScheduledTime()` method
- **Stashed:** Has `enableSound()` method
- **Resolution:** Keep BOTH methods - they serve different purposes.

---

### 7. `webui/src/app/features/match-control/match-control.ts`

**Multiple conflicts:**

#### Conflict 1 (Lines 437-443): startMatch success callback
- **Upstream:** Just logs debug message
- **Stashed:** Updates active/loaded state and resets timer tracking
- **Resolution:** Keep stashed changes - proper state management.

#### Conflict 2 (Lines 471-507): abortMatch method
- **Upstream:** Simple abort without confirmation
- **Stashed:** Has confirmation dialog and proper state management
- **Resolution:** Keep stashed changes - better UX with confirmation and state handling.

---

### 8. `webui/dist/webui/browser/index.html` (Build Artifact)

**Conflict Location:** Lines 17-19

This is a build artifact with different chunk filenames. This file will be regenerated when the project is rebuilt, so it can be resolved by keeping either version or regenerating it.

**Resolution:** Keep upstream version, then rebuild the webui to regenerate this file.

---

## Resolution Strategy Summary

| File | Strategy |
|------|----------|
| application.properties | Keep stashed (add port config) |
| BroadcastMessageType.java | Merge both (keep both constants) |
| LiveScoreHandler.java | Keep stashed (more features) |
| scoring-display.css | Merge both (different style sections) |
| scoring-display.html | Merge both (integrate logo panel) |
| scoring-display.ts | Merge both (keep both methods) |
| match-control.ts | Keep stashed (better state management) |
| index.html (dist) | Keep upstream, rebuild later |

## Execution Order

1. Resolve Java backend files first (application.properties, BroadcastMessageType.java, LiveScoreHandler.java)
2. Resolve Angular frontend files (scoring-display.css, scoring-display.html, scoring-display.ts, match-control.ts)
3. Resolve build artifact (index.html)
4. Verify no remaining conflict markers
5. Test the application compiles/runs correctly
