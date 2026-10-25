# Fortuna visual design

How the app looks: its colour, its icons and its welcome picture. Layout rules for different window sizes are in the code (`AdaptiveScreen`) and the plan; this page records the look.

## Colour

- **Mint** is the app's colour, taken from the accent of the launcher icon. Dark uses `#34D399`. Light uses a deeper `#047857`, because the pale mint cannot be read as lines or carry white text on an off-white page.
- **Charcoal and off-white** are the backgrounds: `#1A1D21` in dark, `#F6F8F7` in light. The same colour is the window and splash screen background, so launching shows no flash.
- **The error colour is for what is owed and for warnings.** An amount owed, the debt icon and the warning on the welcome screen use it, so they look different from what is owned.
- The scheme is `FortunaTheme`. It follows the system light and dark setting.

## Icons and the mark

- **Drawn in code** (`FortunaIcons.kt`), not loaded from a library or from image files. Each icon is a few strokes on a 24 by 24 grid, in one line colour with one accent, and takes both from the theme, so one definition serves both themes.
- **Starter categories:** cash (a note), investments (a rising line), property (a house), pension (an umbrella), debt (a bill). Debt is drawn in the error colour. A category without a picture simply shows none.
- **Screens:** lock, PIN (the keys of a pad), recovery phrase (a page of words), currency (two arrows, one each way), add account.
- **Presentation:** a small rounded tile above a screen's title; a tile beside each account on the dashboard.
- **The mark** is the launcher icon's shield and F, with the F's middle arm in the accent colour. Inside two thin rings it is the picture on the welcome screen and the empty dashboard.

## Welcome screen

- **A picture and three rows that say the same thing.** The mark sits in its rings, with three icons on the outer ring: on this phone at the top, no account at the lower right, PIN and recovery phrase at the lower left. The rows below use the same icons in the same clockwise order, so the picture reads at a glance and the rows explain it.
- **The warning** about losing both the PIN and the recovery phrase is its own line in the error colour, so it is not buried in a row.
- **Button at the bottom** on a narrow window, within reach of the thumbs. On a wide window the rows are on the left and the picture is on the right with the button directly under it, as wide as the picture.
