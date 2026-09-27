# Pass text across JNI as UTF-8 byte arrays, not jstring

Established: 2026-09-27.

JNI's `NewStringUTF` and `GetStringUTFChars` use modified UTF-8, which
encodes characters outside the Basic Multilingual Plane (for example emoji)
differently from standard UTF-8. llama.cpp expects and produces standard
UTF-8, and a token piece can end in the middle of a multi-byte character.

So prompts go to native code as `ByteArray` (UTF-8 encoded in Kotlin), and
token pieces come back as `ByteArray`, reassembled by `Utf8PieceDecoder`.
Source: JNI specification, "Modified UTF-8 Strings".
