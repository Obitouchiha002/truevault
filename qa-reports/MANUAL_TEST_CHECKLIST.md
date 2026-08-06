# Manual test checklist

Everything an automated suite in this environment could not reach. Each item is written so a person
with one device and no special tooling can execute it and record a result.

Rules for whoever runs this:

- Use **synthetic files you created for the test**. Never a real photo, document or recording. Half
  of these steps end in a file being deleted.
- A step is passed only when the stated observation actually happened. "Looked fine" is not a result.
- If a step cannot be run, write **NOT RUN** and the reason. Do not leave it blank.

Record results in the Result column: `PASS` / `FAIL` / `NOT RUN — <reason>`.

---

## A. First run and lock creation

| # | Step | Expected | Result |
|---|---|---|---|
| A1 | Install on a clean device, open the app | Onboarding appears; no permission dialog | |
| A2 | Read the lock-creation screen before choosing anything | "There is no way to reset this" is visible **before** the lock type is chosen | |
| A3 | Choose 4-digit PIN | The screen states plainly that it is the weakest option | |
| A4 | Choose 6-digit PIN, enter it, confirm | The keypad is TrueVault's own — the system keyboard never appears | |
| A5 | Try to reach the vault without creating a lock (back button, recents, re-launch) | Impossible. Lock creation cannot be skipped. | |
| A6 | Complete creation, force-stop, reopen | Unlock screen appears; the correct PIN opens the vault | |

## B. Biometrics

| # | Step | Expected | Result |
|---|---|---|---|
| B1 | Enable biometric unlock during setup on a device with an enrolled fingerprint | The prompt appears and enrolment succeeds | |
| B2 | Lock, reopen, unlock with fingerprint | Vault opens | |
| B3 | On a device with **no** enrolment, open setup | A clear "no fingerprint is set up" message; no crash, no silent failure | |
| B4 | Enrol a **new** fingerprint after setup, then reopen | Biometric unlock is invalidated; the password still works. **The vault must not become unopenable.** | |
| B5 | Cancel the biometric prompt | Falls back to password entry, no error state | |

## C. Lockout throttle

| # | Step | Expected | Result |
|---|---|---|---|
| C1 | Enter a wrong PIN 4 times | No delay yet | |
| C2 | Enter a 5th wrong PIN | A 30-second wait appears and **counts down visibly** | |
| C3 | Wait for zero, enter the correct PIN | Unlocks | |
| C4 | Trigger a wait, then set the system clock backwards | The wait does **not** reset | |
| C5 | Trigger a wait, force-stop the app, reopen | The wait is still in effect | |

## D. Secure Copy

| # | Step | Expected | Result |
|---|---|---|---|
| D1 | Secure Copy 5 synthetic photos | All 5 appear in the vault | |
| D2 | Open each in the vault viewer | Content matches the originals exactly | |
| D3 | Check the originals in Gallery/Files | **Still there.** Status reads "Original remains". | |
| D4 | Secure Copy a zero-byte file | Succeeds, opens as empty — no crash | |
| D5 | Secure Copy a file with an emoji or Devanagari name | Name preserved | |
| D6 | Secure Copy a 1 GB video | Completes; progress is smooth; the app does not run out of memory | |

## E. Secure Move — the honesty tests

These are the most important items on this page.

| # | Step | Expected | Result |
|---|---|---|---|
| E1 | Secure Move one file, **approve** the system delete dialog | Original gone; status becomes "Secured" | |
| E2 | Secure Move one file, **decline** the dialog | Original still present; status reads "Original remains" — **never "Secured"** | |
| E3 | Secure Move from a provider that cannot delete (some cloud providers) | Item stays in the vault; status honestly reports the original was not removed | |
| E4 | Start a Secure Move and kill the app mid-encryption | Reopen: no partial item in the vault, **original untouched**, a retryable failure is recorded | |
| E5 | Secure Move with the device nearly full | Refused **before** anything is written; original untouched | |
| E6 | Secure Move, approve deletion, then check the platform trash | The app does not claim the file is unrecoverable if the trash still holds it | |

## F. Leak scanner

| # | Step | Expected | Result |
|---|---|---|---|
| F1 | Grant a folder containing a copy of a vaulted file | Reported as an exact duplicate, confidence 100 | |
| F2 | Grant a folder with a same-size but different file | **Not** reported as a duplicate | |
| F3 | Scan a folder with nothing in common | Zero findings, and it says so plainly rather than looking broken | |
| F4 | Check the wording about what the scanner cannot see | It states its limits (other apps, cloud, chats) rather than implying full coverage | |
| F5 | Resolve a finding | It clears; nothing is deleted without a confirmation dialog | |

## G. Backup and restore — do this on two devices if at all possible

| # | Step | Expected | Result |
|---|---|---|---|
| G1 | Export a backup containing at least 10 items | Completes; a file is produced | |
| G2 | Uninstall, reinstall, create a **new** vault with a **different** password | Fresh empty vault | |
| G3 | Restore the backup into that new vault | **Every item opens.** This is the re-keying fix; before it, this step produced permanently unopenable files. | |
| G4 | Restore onto a **different physical device** | Same result | |
| G5 | Restore with the wrong backup password | Refused cleanly; nothing is written | |
| G6 | Corrupt a byte in the backup file, restore | Refused; no partial import | |

## H. Recovery key

| # | Step | Expected | Result |
|---|---|---|---|
| H1 | Generate a recovery key during setup | Displayed once, with an explicit warning that it is the only route back |  |
| H2 | Forget the password, unlock with the recovery key | Vault opens | |
| H3 | Use the recovery key on a **different device** after restore | Works — the recovery key is deliberately not device-bound | |
| H4 | Enter a wrong recovery key | Rejected; the throttle applies | |

## I. Private Apps — Modern mode (API 35+ only)

| # | Step | Expected | Result |
|---|---|---|---|
| I1 | Open Private Apps on Android 15+ with no Private Space set up | Guided setup, pointing at the real system screen | |
| I2 | Set up Private Space, return | State updates without restarting the app | |
| I3 | Lock Private Space from the shade | The app reflects it within seconds; no app list is shown | |
| I4 | Open Private Apps on Android 14 or lower | Feature is absent or clearly explained — **never a broken or empty screen** | |
| I5 | On a managed/work device | Reports the policy block honestly | |

## J. Secure Launcher Mode

| # | Step | Expected | Result |
|---|---|---|---|
| J1 | Fresh install, press Home repeatedly | **No launcher chooser ever appears.** This was a shipped defect; it must stay fixed. | |
| J2 | Enable Secure Launcher Mode in Settings | Only now does TrueVault become a home-app candidate | |
| J3 | Set it as Home, press Home | TrueVault's launcher appears, showing icons only — never vault content | |
| J4 | Disable the mode | TrueVault stops being a candidate; the chooser stops appearing | |

## K. Auto-lock and screen privacy

| # | Step | Expected | Result |
|---|---|---|---|
| K1 | Open the vault, background the app, return after the timeout | Locked | |
| K2 | Open the recents/app switcher with the vault open | Content is not visible in the preview | |
| K3 | Attempt a screenshot inside the vault | Blocked or clearly stated as not blocked — whichever the app claims must be true | |
| K4 | Receive a call / switch apps mid-import | Import continues or fails cleanly; nothing is left half-committed | |

## L. Platform behaviour

| # | Step | Expected | Result |
|---|---|---|---|
| L1 | Rotate the device on every major screen | No crash, no lost state | |
| L2 | Enable system dark mode | Every screen is legible | |
| L3 | Set font size to the largest setting | No clipped or overlapping text | |
| L4 | Turn on TalkBack and walk the unlock and vault screens | Every control is reachable and announced; the PIN pad reads as one control, not twelve unlabelled buttons | |
| L5 | Switch the system language to Hindi | Layouts hold | |
| L6 | Revoke a granted folder in system settings, then scan | Handled with a clear message, not a crash | |
| L7 | Run the app on Android 8.0 (API 26) | Everything in Core mode works | |

## M. Release build

| # | Step | Expected | Result |
|---|---|---|---|
| M1 | Install the **release** APK (not debug) and repeat A, D, E | Identical behaviour — R8 has not broken reflection, serialization or Room | |
| M2 | Check the app's storage in system settings after securing 10 files | Size is consistent with encrypted containers; no plaintext copies remain | |
| M3 | Inspect `/data/data/<pkg>/cache` after viewing and closing a file | No decrypted plaintext left behind | |
