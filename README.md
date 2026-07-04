# X3 Breakout

Free software, licensed under the GNU GPL v3 (see LICENSE).

Arkanoid with Tempest-style neon visuals for RayNeo X3 Pro glasses.
Vector webs, right-arm temple-pad swipe paddle control, power-up
countdowns in a Tempest-style vector font, per-level music, and a
pre-generated TTS hype announcer. Built native (no Unity, no RayNeo SDK,
no camera — hand tracking was removed as too problematic).

Presentation follows NeonTetris3D: a head-locked big screen (fixed camera,
55° FOV, slow sway) that fills the view — with a WINDOW SIZE setting if
you want it smaller or even larger.

## Story

You pilot THE PADDLE — a living open-source defense program: deflector,
shield, public firewall, labor-memory archive. Every ball is an open
signal bounced back into the privatized grid. The enemy is the MONOPOLY
CONTROL PROTOCOL (MCP), a corporate extraction intelligence wearing the
webs as its body: it wants to privatize open-source AI, paywall public
knowledge, and charge humanity rent on its own collective labor.
(Original characters — homage energy, legally distinct.)


## License

X3 Breakout — a story-driven neon breakout for RayNeo X3 Pro glasses.
Copyright (C) 2026 Mars

This program is free software: you can redistribute it and/or modify it
under the terms of the GNU General Public License as published by the
Free Software Foundation, either version 3 of the License, or (at your
option) any later version. See the LICENSE file for the full text.

This program is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

Note on assets: voice clips in `app/src/main/assets/voice/` are
generated with Fish Audio TTS from the quotes in `commentary.json`;
SFX are procedurally generated WAVs. `tools/fish.config` (API keys,
private voice model ids) is gitignored and must never be committed.
