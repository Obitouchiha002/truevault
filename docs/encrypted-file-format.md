# TrueVault encrypted file format

**Current version: 1.** Magic bytes `TVLT`.

## Layout

```
┌─ header (unencrypted, but authenticated by every chunk) ────────────────────────┐
│ offset  size  field                                                              │
│      0     4  magic              "TVLT"                                          │
│      4     2  formatVersion      u16, big-endian                                 │
│      6     1  algorithm          1 = AES-256-GCM, chunked                        │
│      7     1  flags              reserved, must be 0                             │
│      8     4  chunkSize          u32, plaintext bytes per chunk                  │
│     12     8  plaintextSize      u64, exact original length                      │
│     20     2  wrappedKeyLength   u16                                             │
│     22     n  wrappedFileKey     nonce ‖ ciphertext ‖ tag                        │
│   22+n     4  metadataLength     u32                                             │
│   26+n     m  sealedMetadata     nonce ‖ ciphertext ‖ tag                        │
└──────────────────────────────────────────────────────────────────────────────────┘
then, repeated ⌈plaintextSize / chunkSize⌉ times (at least once, even for an empty file):
    nonce (12 bytes) ‖ ciphertext ‖ tag (16 bytes)
```

Defaults: `chunkSize` = 1 MiB, bounded to 16 KiB … 8 MiB. `wrappedKeyLength` ≤ 1024,
`metadataLength` ≤ 64 KiB.

## Chunk associated data

Every chunk is encrypted with:

```
AAD = serialisedHeader ‖ chunkIndex (8 bytes, big-endian) ‖ isLastChunk (1 byte)
```

This single decision is what the format's integrity rests on:

| Attack | Why it fails |
|--------|--------------|
| Edit the header (version, chunk size, declared length) | The header is the AAD; every chunk's tag fails |
| Reorder two chunks | The chunk index is in the AAD |
| Duplicate a chunk | Same |
| Drop trailing chunks | The end-of-file marker and the authenticated `plaintextSize` both fail |
| Splice in a chunk from another file | The other file's header differs (its wrapped key has its own random nonce), so its AAD differs |
| Flip any ciphertext bit | GCM tag fails |

## Keys

```
password ──Argon2id(salt, versioned params)──▶ password key
                                                    │ seals
                              vault master key ◀─────┘
                                     │
   Android Keystore device key ──────┘ seals again → what is stored on disk
   (non-exportable, AES-256-GCM)

   vault master key ──wraps──▶ per-file random key ──encrypts──▶ one container
```

- Every vault item has its own random 256-bit file key. Only the wrapped form is stored, inside the
  container header.
- Changing the vault password re-seals one small blob. No file is re-encrypted.
- Nonces are always 12 bytes from `SecureRandom`, fresh for every operation. No API in this codebase
  accepts a caller-supplied nonce for encryption.

## Parser rules

The parser refuses, and never repairs or falls back:

| Condition | Result |
|-----------|--------|
| Magic ≠ `TVLT` | `BadMagic` |
| `formatVersion` > current or < 1 | `UnsupportedVersion(found, maxSupported)` |
| `algorithm` unknown | `UnsupportedAlgorithm` |
| `flags` ≠ 0 | `InvalidField("flags")` |
| `chunkSize` outside bounds | `InvalidField("chunkSize")` — checked **before** allocating |
| `plaintextSize` < 0 | `InvalidField("plaintextSize")` |
| Lengths outside bounds | `InvalidField(...)` — checked before allocating |
| Stream ends early | `Truncated` |
| Any tag fails | `GeneralSecurityException`, no plaintext returned |
| Bytes produced ≠ `plaintextSize` | `Truncated` |

## Changing the format

1. Increment `VaultContainer.CURRENT_FORMAT_VERSION`.
2. Keep the old reader path; old containers must keep opening.
3. Add compatibility tests that read a container written by the previous version.
4. Update this document.

A newer container on an older build produces `UnsupportedVersion` with both numbers, so the user is
told to update rather than shown a corruption error.

## Backup archive format

**Current version: 1.** A ZIP whose first entry is `manifest.json` in plaintext.

```
manifest.json            plaintext: magic, version, counts, KDF salt+params, check value, entry hashes
metadata.bin             sealed: the vault index for every item in the archive
items/<uuid>             sealed: one container
thumbnails/<uuid>        sealed: one thumbnail
```

The manifest is readable without a key on purpose: the app must be able to say "this is not a
TrueVault backup" or "this was made by a newer version" *before* asking for a passphrase, instead of
after a failed decryption that looks identical to a wrong passphrase. It contains no file names.

Every entry carries a SHA-256 of its **sealed** bytes, verified during unpacking, so a truncated or
edited archive is rejected before any of it reaches the vault directory. Entry names are matched
against the manifest and refused if they contain `..` or start with a separator.

The archive key comes from a backup passphrase, **not** from the Keystore — a backup sealed by a
device-bound key could never be restored on a replacement phone, which is exactly when a backup
matters.
