# Design system: Punguța cu 2 bani

Native Android (Jetpack Compose, Material 3 theming). This is a working app ("Operate" mode):
clarity and familiar controls come first, and the look comes from type, palette, shape and one
signature motion.

## World

- **Ground and cards:** a soft light-grey ground with white cards rounded at 24 dp, no borders,
  no shadows except on the floating nav bar.
- **Ink for action and selection:** the screen's main action is a solid near-black pill, and the
  selected segment is a solid near-black pill too.
- **One coral accent** marks what matters right now: spending, progress tracks, receipts, the
  lock fingerprint.
- **Dark theme is designed, not inverted.** Near-black ground with charcoal cards. The roles
  swap, so the brightest element (off-white) takes ink's place as the primary action.

## Tokens

Source of truth: `app/src/main/java/ro/punguta/app/ui/theme/Theme.kt`.

| Role | Light | Dark |
|---|---|---|
| background (ground) | `#EEEEEC` | `#0E0E0D` |
| surface (cards) | `#FFFFFF` | `#1B1B1A` |
| primary (ink pill, selection) | `#141414` | `#F4F4F2` |
| onSurfaceVariant (secondary text) | `#66665F` | `#A9A9A2` |
| accent (coral) | `#EF5B36` | `#FF6E4A` |
| income (green) | `#1E8E5A` | `#5BD39A` |
| warning (budget ≥ 80%) | `#C98A00` | `#F2B53A` |
| error (over budget, negative) | `#B3261E` | `#FF5470` |
| nav selected pill | `#EAEAE7` | `#2E2E2C` |

Coral is never used for small text on white: its contrast is about 3.2:1, which is enough for
icons and bars but not for body text. Secondary text is ≥ 5:1 on both cards and ground.

**Type:** Geist, a variable font bundled in `res/font/geist.ttf` under the OFL licence (see
`assets/licenses/`).

| Role | Size | Weight | Tracking | Used for |
|---|---|---|---|---|
| displaySmall | 40 sp | 800 | −1.2 sp | tab titles |
| headlineMedium | 30 sp | 700 | | the balance |
| titleLarge | 20 sp | 700 | | card titles |
| titleMedium | 16 sp | 600 | | rows, buttons |
| bodyLarge / bodyMedium | 16 / 14 sp | 400 | | body text |
| labelMedium | 12 sp | 500 | | nav and segment labels |

All money is set in tabular figures (`TextStyle.tabular`).

**Shape:** cards 24 dp (`shapes.large`); pills and round buttons are fully rounded; fields 18 dp;
segment pill 18 dp; nav pill 26 dp inside a 32 dp bar.

**Spacing:** 16 dp screen gutter, 12 dp between cards, 16–20 dp card padding. Touch targets are
48 dp or larger (round buttons 48, pills 60, nav items 56).

## Components

All in `ui/components/Design.kt`.

- **ScreenHeader:** a 40 sp title, an optional subtitle line with small actions, and round
  actions on the right. The black filled round "+" is the tab's add action.
- **DetailTopBar:** a round back button, a bold title, and actions, for pushed screens.
- **AppCard:** the white 24 dp card. It is tappable when given `onClick`.
- **CardRow:** a badge, title and subtitle, and trailing content. Lists are rows grouped inside
  one card, never one card per row.
- **PillButton:** PRIMARY (ink) is the one main action per screen ("Scanează bon",
  "Salvează"); SECONDARY is a white pill; DANGER uses red text on white.
- **RoundIconButton**, **IconBadge**: badge tones are ACCENT, NEUTRAL, INK and INCOME.
- **SegmentedTabs:** a white card with an ink pill behind the selected option. These are real
  filters, never action buttons.
- **FloatingNavBar:** a white pill bar floating above the gesture area, with a soft grey pill
  behind the current tab.
- **AccentProgress:** an 8 dp rounded track used for spent share, savings rate and budgets.
  In-budget bars are ink, at 80% they turn amber, and over the limit they turn red.

## Motion

- **Press feedback:** every tappable element scales to 0.97 over 160 ms (`pressScale`).
- **Signature move:** the nav pill and the segment pill slide to the new selection with a spring
  (damping 0.85, medium-low stiffness), so the nav bar and the filters share the same grammar.
- **Progress fills** animate over 400 ms.
- **No entrance animations.** The app loads straight into the task.
- Compose follows the system "Remove animations" setting.

## Rules

- No eyebrow labels above headings, no gradient text, no glass or blur, no coloured side borders.
- State is never carried by color alone: over-budget shows an icon and text as well as red.
- The UI is Romanian only. `MainActivity` forces the `ro-RO` locale, so plurals and dates are
  correct on any phone.
