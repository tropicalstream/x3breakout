# Voice commentary clips (generated — don't hand-edit)

Generated from `assets/commentary.json` by `tools/generate_commentary.py`
using Fish Audio TTS. One `<line id>.mp3` per configured line.

```
cp tools/fish.config.example tools/fish.config
# edit tools/fish.config with FISH_API_KEY and FISH_VOICE_MODEL_ID
python3 tools/generate_commentary.py
```

The game silently skips events whose clips are missing, so it runs fine
before you've generated anything.
