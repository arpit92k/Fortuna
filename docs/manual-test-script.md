# Manual test script

The MVP release check (plan step 26): every MVP flow in the [roadmap](roadmap.md), run by hand on a real phone with a **release build**. Download the release APK from the "Release app" job of the Checks run. It is shrunk with R8 and, until the app is published, signed with the debug key.

Run the sections in order: each starts where the one before left off. Tick a line only when what it says happens exactly. Anything else is a bug: report it from the app itself (section 13), so the reporting is tested too.

**Before starting:** uninstall Fortuna, then install the release APK. Note the phone's model and Android version here: ______________________

## 1. First-time setup (UF-01)

- [ ] S-01 shows the mark in its rings, the three rows, the warning, "Get started", "Restore from a backup" and "Report a problem".
- [ ] A screenshot of S-01 is blocked: the phone says the app does not allow it, or saves a black picture.
- [ ] "Get started", then a five-digit PIN: OK is off. Six digits: OK asks for the PIN again.
- [ ] A different second PIN says it did not match and starts again. The same PIN twice moves to the phrase.
- [ ] S-03 shows twelve numbered words. **A screenshot works here**, and only here. Write the words down.
- [ ] Back from S-03 returns to the PIN, and a new start shows different words.
- [ ] S-04 asks for three words. A wrong one says so and offers the phrase again; the right ones move on.
- [ ] S-05 offers the phone's region currency first. Choose it: the empty dashboard appears.

## 2. Add a source (UF-03)

- [ ] On the empty dashboard, add a first account: a current account, Cash, in the base currency, with today's value.
- [ ] Add a second: a credit card, Debt, with a past date. Net worth falls by what is owed, and the card's row says it is owed.
- [ ] A date in the future is refused. An amount with too many decimal places is refused.
- [ ] Add a third in another currency. It asks for a rate first; after it, the row shows both currencies, and the totals count it in the base currency.

## 3. Charts (UF-10, UF-11)

- [ ] With values on one date only, the chart says it fills in as values are added.
- [ ] With an older value too, the dashboard draws net worth from the first value to today. Pressing it shows a date and an amount.
- [ ] A source page draws its own value over time, in its own currency.

## 4. Update a single source and correct it (UF-05, UF-06)

- [ ] On a source page, add a value with an amount added and a note. The history shows the change, the amount added and the growth.
- [ ] Tap an older value and correct it. Delete the latest value: it asks first, and the one before becomes the latest.
- [ ] A source with only its opening value can be deleted, after asking. One with history offers no delete.

## 5. Periodic update (UF-04)

- [ ] "Update" on the dashboard shows how many sources are due, and the rate of each foreign currency with its age.
- [ ] Enter a newer rate. Start, and walk the sources: enter one, skip one.
- [ ] Leave part-way, then start again with the same date: only the skipped and the remaining sources are offered.
- [ ] Finish: back on the dashboard, net worth reflects the new values.

## 6. Currencies and rates (UF-09)

- [ ] Settings, then Currencies and rates, lists the foreign currency, its accounts and its rates, newest first.
- [ ] Add a rate for a past date, and correct one.
- [ ] Deleting the only rate on or before an account's opening value is refused in a pop-up that says why.

## 7. Hide amounts (UF-19)

- [ ] The eye on the dashboard turns every amount into the same six dots. The chart keeps its shape, with no amounts on it.
- [ ] Source pages and the update show dots too. Amounts typed into a form stay visible.
- [ ] Close the app fully and open it again: amounts are still hidden. The eye shows them again.

## 8. Lock and unlock (UF-02)

- [ ] Leave the app for over a minute: it is locked on return. Leave it for less: it is not.
- [ ] Leave it mid-entry on a value form for under two minutes: still open, with what was typed.
- [ ] Three wrong PINs: a wait starts, counts down, and the pad is off until it ends.
- [ ] The lock icon on the dashboard locks at once. The right PIN returns to where the app was.
- [ ] The app switcher shows no figures for Fortuna.

## 8a. Biometric unlock (UF-02)

Needs a phone with a fingerprint set up. Note the order: section 1's setup is where the offer appears.

- [ ] Right after setup in section 1, the app offered fingerprint unlock once. If you chose "Not now", turn it on now in Settings: a fingerprint check confirms it.
- [ ] Lock the app and come back: the fingerprint prompt appears by itself. A recognised finger opens the app where it was.
- [ ] "Use PIN" on the prompt leaves the pad; the PIN works, and "Use fingerprint" asks again.
- [ ] Add a new fingerprint in the phone's settings, then return: the lock screen says fingerprint unlock was turned off, and the PIN works.
- [ ] Turning the switch off in Settings stops the prompt on the next lock.
- [ ] After recovering with the phrase in section 10, fingerprint unlock still works if it was on.

## 9. Back up (UF-12)

- [ ] Settings, then Backup, says no backup has been made. Save one to Downloads or to your own cloud storage.
- [ ] It now shows today's date and says the backup holds everything. Change a value: it says there are changes since.

## 10. Recover from a forgotten PIN (UF-14)

- [ ] On the lock screen, "Forgot your PIN?", then the twelve words. Typing offers suggestions above the keyboard; pasting the whole phrase into the first box fills every box.
- [ ] A misspelt word is marked. A real but wrong phrase, after the new PIN, says it is not the phrase for this data, and the old PIN still works.
- [ ] The right phrase and a new PIN open the app with everything intact. The old PIN no longer works.

## 11. Restore from a backup (UF-13)

- [ ] Uninstall and install again. "Restore from a backup", choose the file from section 9.
- [ ] A file that is not a backup, such as a photo, is refused, and the phone still has no data.
- [ ] A wrong phrase is refused, and the phone still has no data.
- [ ] The right phrase and a PIN open the app with the data as it was backed up.

## 12. Screens and sizes

- [ ] With the phone in dark mode, every screen above reads clearly.
- [ ] At the largest font size, the welcome screen, the lock screen and the dashboard can be scrolled to every button.
- [ ] Turned sideways, the PIN pad and the phrase entry are usable.
- [ ] With TalkBack on, the welcome screen reads its heading, then each row as one item; the dashboard's eye is read as "Hide amounts".

## 13. Report a bug (UF-18)

- [ ] From the lock screen, "Report a problem" opens S-18 without the PIN. The report shows the app version, the Android version and the phone, and no amounts or names.
- [ ] "Open in email app" opens the email app addressed to support.fortuna@gmail.com, with the report filled in. Nothing is sent until you send it.
- [ ] The same is offered from Settings and from the welcome screen.
- [ ] A crash in a release build cannot be forced from Settings. To check crash records, use a debug build: "Crash the app", reopen, and the app offers to report it. The record names code locations and holds no "£1,234.56".

## Result

Date: __________ Build (from Settings, Report a problem): __________

- [ ] Every line above is ticked, or has a bug report sent for it.
