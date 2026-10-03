# Fortuna roadmap

This document splits the [user flows](user-flows.md) into two sets: what the first usable version (the MVP) contains, and what is planned for later.

The line is drawn with one rule. The MVP captures and protects everything that cannot be recreated later: values, amount added, exchange rates, and the encryption and recovery setup. Anything that can be calculated later from stored data waits.

## MVP

### Getting in

- **UF-01 First-time setup.** PIN, recovery phrase with confirmation, and base currency. Categories are a fixed starter set.
- **UF-02 Unlock the app.** PIN only, with auto-lock and growing delays after failed attempts.
- **UF-14 Recover from a forgotten PIN.** Get back in with the recovery phrase and set a new PIN.

### Recording

- **UF-03 Add a source.** Name, category, currency, opening value and date.
- **UF-05 Update a single source.** Value, amount added, date and note.
- **UF-04 Periodic update, basic.** Walk through the sources, entering a value or skipping, after confirming rates for foreign currencies.
- **UF-06 Corrections, basic.** Edit any snapshot's figures, delete a source's latest snapshot, and delete a source created by mistake.
- **UF-09 Currencies and rates, basic.** Add a currency when adding a source, then enter and correct rates by hand.

### Insights

- **UF-10 Net worth, basic.** Current net worth, total assets and liabilities, a timeline, and the source list with each value and when it was last updated.
- **UF-11 View a source, basic.** Value over time in the source's own currency, with each snapshot's change split into added and growth.

### Protecting the data

- **UF-12 Back up.** Export one encrypted file, on request.
- **UF-13 Restore from a backup.** On a fresh install, from a backup file and the recovery phrase.

### MVP simplifications

These keep the MVP small. Each is lifted by an item in the future plans.

- A new snapshot must be dated after the source's latest one, so history can only be entered in date order.
- A source cannot be closed. The workaround is to record a value of zero by hand; the source stays in the list.
- Categories cannot be added, renamed or removed.
- The PIN can only be changed through the forgotten-PIN flow.

## Future plans

### Recording

- **Backfilling (UF-06).** Insert or delete snapshots in the middle of a source's history, with the app adjusting the neighbouring amount added for the user to confirm.
- **Close a source (UF-07).** Closing snapshot, archiving and reopening.
- **Manage categories (UF-08).** Add, rename and archive categories, and move sources between them.
- **Periodic update extras (UF-04).** The "unchanged" shortcut, the typing-mistake check and the end-of-update summary.
- **Change base currency (UF-09).**
- **Import history from a spreadsheet (UF-15).** The app provides an empty, simplified template for the user to fill in and import.

### Insights

- **Change breakdown (UF-10).** Added, growth and currency effect across all sources, for a chosen period.
- **Allocation by category (UF-10).**
- **Base-currency view of a source (UF-11),** including the currency effect.
- **Out-of-date markers.** Each source gets an expected update frequency, with a default, and is marked once that period has passed.

### Protecting the data

- **Biometric unlock (UF-02).**
- **Security settings (UF-16).** Change the PIN and replace the recovery phrase.
- **Backup prompts (UF-12).** Offer a backup after each update and show whether data has changed since the last one.
- **Restore over existing data (UF-13).**

### Convenience

- **Unencrypted spreadsheet export.** A readable copy of the data for the user's own analysis. It leaves the app's protection, so the app must say so at the point of export.
- **Update reminders.** Prompt the user to do a periodic update.

### Network features

The architecture keeps the app off the network. These two features need it, so they change that constraint and should be opt-in and off by default.

- **Automatic exchange rates.** Fetch current rates instead of typing them in. The request reveals which currencies the user holds but no amounts.
- **Open banking connections.** Read balances from the user's accounts automatically. This usually goes through a third-party provider that can see the account data, which is a much larger departure from keeping data on the device. It needs its own design before it is committed to.
