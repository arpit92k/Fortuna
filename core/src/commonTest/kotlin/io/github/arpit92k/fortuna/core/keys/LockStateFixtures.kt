package io.github.arpit92k.fortuna.core.keys

/**
 * Saved lock states in each released format version.
 *
 * Never edit one: every later version of the app must still read it. A new
 * format version adds a new fixture here. The bytes are counting runs, so a
 * wrong decoding shows at a glance:
 *
 * - the PIN's copy: 310,000 rounds, salt 0x00 to 0x0f, nonce 0x10 to 0x1b, wrapped key 0x40 to 0x7b
 * - the phrase's copy: PBKDF2-HMAC-SHA256 at 100,000 rounds, salt 0x80 to 0x8f, nonce 0x90 to 0x9b, sealed key 0xa0 to 0xcf
 * - two wrong PINs, the latest at 123,456 ms after start-up number 7
 */
object LockStateFixtures {
    const val LOCK_STATE_V1: String = """
{
  "formatVersion": 1,
  "pin": { "iterations": 310000, "salt": "AAECAwQFBgcICQoLDA0ODw==", "nonce": "EBESExQVFhcYGRob", "wrapped": "QEFCQ0RFRkdISUpLTE1OT1BRUlNUVVZXWFlaW1xdXl9gYWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXp7" },
  "phrase": { "algorithm": "pbkdf2-hmac-sha256", "iterations": 100000, "salt": "gIGCg4SFhoeIiYqLjI2Ojw==", "nonce": "kJGSk5SVlpeYmZqb", "sealedKey": "oKGio6SlpqeoqaqrrK2ur7CxsrO0tba3uLm6u7y9vr/AwcLDxMXGx8jJysvMzc7P" },
  "failedAttempts": 2,
  "lastFailure": { "startUp": 7, "sinceStartMillis": 123456 }
}
"""
}
