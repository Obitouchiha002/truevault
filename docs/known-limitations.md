# Known limitations

Written plainly, because a privacy app that overstates what it does is worse than one that does less.

## Things Android does not allow, and TrueVault does not fake

| Limitation | What TrueVault does instead |
|------------|-----------------------------|
| An app cannot delete a picked file on its own | Uses the platform's delete request and records what actually happened, including "you cancelled" |
| Media originals cannot be deleted at all on API 26–28 without all-files access | Reports `PROVIDER_NOT_SUPPORTED`, rather than requesting a permission it should not have |
| Read-only and cloud document providers may refuse deletion | Reports it, and leaves the item as `ORIGINAL_REMAINS` |
| The system trash cannot be inspected | Reported as `TRASH_STATUS_UNKNOWN`, never guessed |
| Other apps' storage and encrypted chats cannot be scanned | The scan screen says so permanently, not as a dismissible tip |
| Apps cannot be cloned, hidden or virtualised without root | Private Apps detects Android's own Private Space and guides the user there, or says the device does not support it |
| Nothing can be un-shared once another app has it | Stated before the share sheet opens |
| A rooted OS defeats any userspace protection | Stated in the threat model; not claimed against |

## Things that are simply not built yet

| Not built | Notes |
|-----------|-------|
| Cloud backup | Deliberately out of scope for the first release. `BackupRepository` consumes a stream, so a remote destination can be added without touching the vault engine |
| Perceptual hashing for edited-image duplicates | Exact SHA-256 matching only, as specified. A perceptual pass is a later phase |
| AI-based similarity detection | Explicitly excluded from the first release |
| Expiring or one-time share links | Would need a backend, and faking them locally would be a lie |
| Background imports | Impossible by design: the master key exists only in memory while unlocked, so an import cannot continue after the vault locks. Imports run in the foreground and fail safely if the vault locks mid-way |
| WorkManager | Nothing in TrueVault is both long-running and able to run without the vault key, so there is no deferrable work to schedule. Declared here so its absence is a decision rather than an oversight |
| Folder collections inside the vault | The data model supports categories; user-defined collections are not built |
| Multi-window and desktop-class layouts | Adaptive layout tokens exist; large-screen refinement is not finished |
| Localisation | English only. All user-facing text is in string resources with plurals, so translation is a content task, not a code change |

## Performance boundaries

- Imports are serial by design. Two concurrent large encryptions would double peak memory and
  compete for the same storage bandwidth.
- A scan visits at most 50,000 documents and 12 directory levels; beyond that it stops and says so
  rather than running indefinitely.
- Search decrypts an in-memory index of file names once per unlocked session. At 10,000 items this
  is tens of milliseconds; it is the cost of not storing names in plaintext.
- Name-ordered sorting uses that same in-memory index, so it is not available before the index is
  built.

## Interface gaps

- The viewer renders images, text, PDFs and video. Other formats are stored and encrypted correctly
  and say they cannot be previewed.
- Vault categories can be filtered, but the dashboard's category cards currently open the full vault
  rather than a pre-filtered view.
