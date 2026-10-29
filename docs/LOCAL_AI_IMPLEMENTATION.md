# Chibi Life: on-device AI implementation plan

Current implementation: offline rule-based coach, workout library, daily check-ins, Gemini optional. A genuine embedded language model is NOT installed yet.

Verified technical sources:
- llama.cpp Android binding: https://github.com/ggml-org/llama.cpp/tree/master/examples/llama.android
- Android integration: https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md
- Qwen3 1.7B GGUF (Apache 2.0): https://huggingface.co/Qwen/Qwen3-1.7B-GGUF
- PocketLLM Android JNI server and resumable model download: https://github.com/Kimi-syl/PocketLLM
- OpenExerciseDB: https://github.com/rfivesix/OpenExerciseDB
- wger: https://github.com/wger-project/wger

Proposed release gates:
1. Stabilize existing Android Kotlin compilation and alarm reliability.
2. Implement optional model manager and download progress; require explicit download consent and free storage check.
3. Add arm64-v8a llama.cpp JNI inference with cancellable generation and 2048–4096 context. Do not claim hardware acceleration without benchmarking.
4. Try Qwen3 1.7B Q4 GGUF for Vietnamese conversation, with offline deterministic fallback. Verify license and download URL before release.
5. Retrieve curated exercise instructions and food reference data, with citations and safety limits. Never invent nutrition totals.
6. Let AI draft plans only. User reviews and confirms all schedule changes.
7. Add voice interface using installed Android Vietnamese TTS; cloud voices optional and licensed.
8. Test on real Android 16 REDMAGIC for RAM, heat, battery, background alarms, no-internet, model download interruptions and permissions.

Current status is roadmap; do not label as fully integrated AI until APK and device tests pass.
