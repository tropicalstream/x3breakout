# Changelog

## 2026-07-04

- Replaced the life-lost line with "Reenergizing for the people." and regenerated its Fish voice clip.
- Replaced the miss-ball sound with a rising energy-generation cue.
- Fixed MCP story voice playback so forced campaign beats always speak, even if optional commentary was previously toggled off in saved settings.
- Restored music ducking during voice lines so generated MCP dialogue cuts through the title and level tracks.
- Verified `commentary.json` has 68 unique dialogue IDs and all 68 generated MP3 clips are packaged in `app/src/main/assets/voice/`.
- Prevented caption text from drawing underneath the settings menu and made the settings panel more opaque for readability.
- Added explicit campaign music ordering: `mcp.mp3` for the title screen, IO Tower for level 1, New Song Arpeggio for level 3, Close Encounters for level 4, and Disk Check for level 5.
- Preserved the RayNeo X3 audio-stall notes in code and documentation: persistent audio can cause render hitches, so any audio change should be checked with `X3Render` `FRAME HITCH` logs.
- Uploaded a debug APK built without Fish API keys or private `tools/fish.config`.
