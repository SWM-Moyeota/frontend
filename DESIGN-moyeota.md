---
version: alpha
name: Moyeota-design-analysis
description: |
  A trust-forward mobility system organized around a single vivid blue primary (`#085AF5`) that anchors every match request, confirmation, and safety CTA. The system reads like a live map interface breathing at the user's request cadence — the map surface is the canvas, floating cards deliver the interaction moments, and status transitions (idle → waiting → matched → riding) are carried by a compact 5-state color language layered on top of that base. The chrome is unusually quiet for a mobility service: bright Moyeota Blue (`#085AF5`) carries every primary CTA as a fully-rounded pill, Pretendard renders headline copy at weight 600 (semibold) for reassuring clarity, and a 12px-radius card system floats over the map on every state. The system never decorates on top of the map — no glass panels, no atmospheric mesh, no drop shadows beyond a compact 8px card-lift. The map itself does the visual heavy lifting; chrome is compact information cards, marker pins, and route polylines.

colors:
  primary: "#085AF5"
  primary-pressed: "#054BC7"
  primary-active: "#033D9F"
  primary-soft: "#E6EEFE"
  on-primary: "#FFFFFF"
  link-light: "#054BC7"
  link-dark: "#5A9DFF"
  success: "#10B981"
  success-pressed: "#0F8F66"
  success-soft: "#D1FAE5"
  on-success: "#FFFFFF"
  waiting: "#F59E0B"
  waiting-pressed: "#B57407"
  waiting-soft: "#FEF3C7"
  on-waiting: "#111111"
  danger: "#E63946"
  danger-pressed: "#B32836"
  danger-soft: "#FDECEE"
  on-danger: "#FFFFFF"
  safety: "#DC2626"
  safety-pressed: "#A81D1D"
  on-safety: "#FFFFFF"
  ink: "#111111"
  ink-deep: "#0A0A0B"
  ink-elevated: "#181A1F"
  charcoal: "#2C2E33"
  body-light: "rgba(17,17,17,0.72)"
  mute-light: "#6B7280"
  ash-light: "#D1D5DB"
  body-dark: "rgba(255,255,255,0.76)"
  mute-dark: "rgba(229,229,229,0.6)"
  ash-dark: "rgba(229,229,229,0.24)"
  canvas-light: "#FFFFFF"
  surface-soft: "#F4F6FA"
  surface-card: "#FFFFFF"
  surface-map-overlay: "rgba(255,255,255,0.94)"
  canvas-dark: "#111111"
  surface-dark-elevated: "#181A1F"
  surface-dark-card: "#22252B"
  hairline-light: "#EDEFF3"
  hairline-dark: "rgba(229,229,229,0.18)"
  on-dark: "#FFFFFF"
  on-dark-mute: "#CFD4DB"
  route-shared: "#085AF5"
  route-user-a: "#3B82F6"
  route-user-b: "#8B5CF6"
  marker-origin: "#085AF5"
  marker-destination: "#EF4444"
  marker-pickup: "#10B981"
  marker-dropoff: "#F97316"

typography:
  display-xl:
    fontFamily: Pretendard
    fontSize: 32px
    fontWeight: 700
    lineHeight: 1.25
    letterSpacing: -0.4px
  display-lg:
    fontFamily: Pretendard
    fontSize: 28px
    fontWeight: 700
    lineHeight: 1.28
    letterSpacing: -0.3px
  display-md:
    fontFamily: Pretendard
    fontSize: 24px
    fontWeight: 700
    lineHeight: 1.3
    letterSpacing: -0.2px
  heading-xl:
    fontFamily: Pretendard
    fontSize: 22px
    fontWeight: 600
    lineHeight: 1.3
    letterSpacing: -0.2px
  heading-lg:
    fontFamily: Pretendard
    fontSize: 18px
    fontWeight: 600
    lineHeight: 1.35
    letterSpacing: -0.1px
  heading-md:
    fontFamily: Pretendard
    fontSize: 16px
    fontWeight: 600
    lineHeight: 1.35
    letterSpacing: 0
  body-md:
    fontFamily: Pretendard
    fontSize: 15px
    fontWeight: 400
    lineHeight: 1.5
    letterSpacing: 0
  body-strong:
    fontFamily: Pretendard
    fontSize: 15px
    fontWeight: 600
    lineHeight: 1.4
    letterSpacing: 0
  body-sm:
    fontFamily: Pretendard
    fontSize: 13px
    fontWeight: 400
    lineHeight: 1.5
    letterSpacing: 0
  caption-md:
    fontFamily: Pretendard
    fontSize: 12px
    fontWeight: 500
    lineHeight: 1.4
    letterSpacing: 0
  caption-sm:
    fontFamily: Pretendard
    fontSize: 11px
    fontWeight: 500
    lineHeight: 1.4
    letterSpacing: 0.2px
  mono-md:
    fontFamily: "JetBrains Mono"
    fontSize: 13px
    fontWeight: 500
    lineHeight: 1.4
    letterSpacing: 0
  link-md:
    fontFamily: Pretendard
    fontSize: 15px
    fontWeight: 500
    lineHeight: 1.5
    letterSpacing: 0
  button-lg:
    fontFamily: Pretendard
    fontSize: 16px
    fontWeight: 700
    lineHeight: 1.25
    letterSpacing: -0.1px
  button-md:
    fontFamily: Pretendard
    fontSize: 14px
    fontWeight: 600
    lineHeight: 1.25
    letterSpacing: 0
  button-sm:
    fontFamily: Pretendard
    fontSize: 12px
    fontWeight: 600
    lineHeight: 1.25
    letterSpacing: 0.1px

rounded:
  none: 0px
  sm: 6px
  md: 12px
  lg: 20px
  xl: 28px
  full: 9999px

spacing:
  xxs: 4px
  xs: 8px
  sm: 12px
  md: 16px
  lg: 24px
  xl: 32px
  xxl: 48px
  section: 64px

components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.button-lg}"
    rounded: "{rounded.full}"
    padding: 14px 28px
    height: 52px
  button-primary-pressed:
    backgroundColor: "{colors.primary-pressed}"
    textColor: "{colors.on-primary}"
    typography: "{typography.button-lg}"
    rounded: "{rounded.full}"
  button-primary-soft:
    backgroundColor: "{colors.primary-soft}"
    textColor: "{colors.primary}"
    typography: "{typography.button-md}"
    rounded: "{rounded.full}"
    padding: 10px 20px
    height: 40px
  button-success:
    backgroundColor: "{colors.success}"
    textColor: "{colors.on-success}"
    typography: "{typography.button-lg}"
    rounded: "{rounded.full}"
    padding: 14px 28px
    height: 52px
  button-secondary-light:
    backgroundColor: "transparent"
    textColor: "{colors.ink}"
    typography: "{typography.button-lg}"
    rounded: "{rounded.full}"
    padding: 14px 28px
    height: 52px
  button-secondary-dark:
    backgroundColor: "transparent"
    textColor: "{colors.on-dark}"
    typography: "{typography.button-lg}"
    rounded: "{rounded.full}"
    padding: 14px 28px
    height: 52px
  button-danger:
    backgroundColor: "transparent"
    textColor: "{colors.danger}"
    typography: "{typography.button-md}"
    rounded: "{rounded.full}"
    padding: 10px 20px
    height: 40px
  button-safety:
    backgroundColor: "{colors.safety}"
    textColor: "{colors.on-safety}"
    typography: "{typography.button-lg}"
    rounded: "{rounded.full}"
    padding: 14px 28px
    height: 56px
  button-disabled:
    backgroundColor: "{colors.surface-soft}"
    textColor: "{colors.ash-light}"
    rounded: "{rounded.full}"
  status-badge-idle:
    backgroundColor: "{colors.surface-soft}"
    textColor: "{colors.mute-light}"
    typography: "{typography.caption-sm}"
    rounded: "{rounded.full}"
    padding: 4px 10px
  status-badge-waiting:
    backgroundColor: "{colors.waiting-soft}"
    textColor: "{colors.waiting-pressed}"
    typography: "{typography.caption-sm}"
    rounded: "{rounded.full}"
    padding: 4px 10px
  status-badge-matched:
    backgroundColor: "{colors.success-soft}"
    textColor: "{colors.success-pressed}"
    typography: "{typography.caption-sm}"
    rounded: "{rounded.full}"
    padding: 4px 10px
  status-badge-error:
    backgroundColor: "{colors.danger-soft}"
    textColor: "{colors.danger-pressed}"
    typography: "{typography.caption-sm}"
    rounded: "{rounded.full}"
    padding: 4px 10px
  text-input:
    backgroundColor: "{colors.canvas-light}"
    textColor: "{colors.ink}"
    typography: "{typography.body-md}"
    rounded: "{rounded.md}"
    padding: 14px 16px
    height: 52px
  text-input-focused:
    backgroundColor: "{colors.canvas-light}"
    textColor: "{colors.ink}"
    rounded: "{rounded.md}"
  location-search-bar:
    backgroundColor: "{colors.surface-map-overlay}"
    textColor: "{colors.ink}"
    typography: "{typography.body-md}"
    rounded: "{rounded.md}"
    padding: 14px 20px
    height: 56px
  filter-chip:
    backgroundColor: "{colors.surface-soft}"
    textColor: "{colors.body-light}"
    typography: "{typography.button-sm}"
    rounded: "{rounded.full}"
    padding: 6px 12px
  filter-chip-active:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.button-sm}"
    rounded: "{rounded.full}"
    padding: 6px 12px
  match-request-card:
    backgroundColor: "{colors.surface-map-overlay}"
    textColor: "{colors.ink}"
    typography: "{typography.body-md}"
    rounded: "{rounded.lg}"
    padding: 20px
  match-result-card:
    backgroundColor: "{colors.canvas-light}"
    textColor: "{colors.ink}"
    typography: "{typography.body-md}"
    rounded: "{rounded.lg}"
    padding: 20px 20px 24px
  partner-info-card:
    backgroundColor: "{colors.primary-soft}"
    textColor: "{colors.ink}"
    typography: "{typography.body-md}"
    rounded: "{rounded.md}"
    padding: 12px 16px
  fare-split-panel:
    backgroundColor: "{colors.success-soft}"
    textColor: "{colors.success-pressed}"
    typography: "{typography.heading-md}"
    rounded: "{rounded.md}"
    padding: 12px 16px
  route-summary-card:
    backgroundColor: "{colors.surface-card}"
    textColor: "{colors.ink}"
    typography: "{typography.body-md}"
    rounded: "{rounded.md}"
    padding: 16px
  safety-shield-fab:
    backgroundColor: "{colors.safety}"
    textColor: "{colors.on-safety}"
    typography: "{typography.button-sm}"
    rounded: "{rounded.full}"
    size: 56px
  map-marker-origin:
    backgroundColor: "{colors.marker-origin}"
    textColor: "{colors.on-primary}"
    typography: "{typography.caption-sm}"
    rounded: "{rounded.full}"
    padding: 4px 10px
  map-marker-destination:
    backgroundColor: "{colors.marker-destination}"
    textColor: "{colors.on-primary}"
    typography: "{typography.caption-sm}"
    rounded: "{rounded.full}"
    padding: 4px 10px
  map-marker-pickup:
    backgroundColor: "{colors.marker-pickup}"
    textColor: "{colors.on-success}"
    typography: "{typography.caption-sm}"
    rounded: "{rounded.full}"
    padding: 4px 10px
  map-marker-dropoff:
    backgroundColor: "{colors.marker-dropoff}"
    textColor: "{colors.on-primary}"
    typography: "{typography.caption-sm}"
    rounded: "{rounded.full}"
    padding: 4px 10px
  route-polyline-shared:
    strokeColor: "{colors.route-shared}"
    strokeWeight: 5
    strokeOpacity: 0.85
  chat-bubble-mine:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.body-md}"
    rounded: "{rounded.lg}"
    padding: 10px 14px
  chat-bubble-partner:
    backgroundColor: "{colors.surface-soft}"
    textColor: "{colors.ink}"
    typography: "{typography.body-md}"
    rounded: "{rounded.lg}"
    padding: 10px 14px
  timeline-step:
    backgroundColor: "{colors.surface-card}"
    textColor: "{colors.ink}"
    typography: "{typography.body-sm}"
    rounded: "{rounded.md}"
    padding: 12px 16px
  bottom-nav:
    backgroundColor: "{colors.canvas-light}"
    textColor: "{colors.ink}"
    typography: "{typography.caption-md}"
    rounded: "{rounded.none}"
    height: 64px
  top-app-bar:
    backgroundColor: "{colors.canvas-light}"
    textColor: "{colors.ink}"
    typography: "{typography.heading-lg}"
    rounded: "{rounded.none}"
    height: 56px
  toast:
    backgroundColor: "{colors.ink-deep}"
    textColor: "{colors.on-dark}"
    typography: "{typography.body-strong}"
    rounded: "{rounded.md}"
    padding: 12px 16px
  bottom-sheet:
    backgroundColor: "{colors.canvas-light}"
    textColor: "{colors.ink}"
    typography: "{typography.body-md}"
    rounded: "20px 20px 0 0"
    padding: 24px 20px 32px
  link-inline:
    textColor: "{colors.link-light}"
    typography: "{typography.link-md}"
---

## Overview

Moyeota's system reads like a live-map interface where the map surface is always primary and chrome floats on top as compact information cards. Every screen anchors on the map — origin/destination selection, matching wait state, partner discovery, ride-in-progress — and floating cards deliver the interactive moments. There is no full-page chrome mode; even the settings and history screens sit as scrollable sheets that summon the map back at any moment.

The system has three distinct interaction modes tied to the matching lifecycle: **idle mode** (empty map, prompts the user to pick origin/destination), **waiting mode** (translucent card overlays the map with a `{colors.waiting}` amber pulse indicating "매칭 대기 중"), and **matched mode** (opaque card lifts up with `{colors.success}` accents, pickup/dropoff markers drop onto the map, and the `{colors.route-shared}` polyline draws the shared route). Each mode uses the same chrome vocabulary — 12px-radius `{rounded.md}` floating cards, fully-rounded `{rounded.full}` pill buttons, Pretendard at semibold — only the accent color and card content change.

Moyeota Blue (`#085AF5`) is the trust anchor. It carries every primary CTA ("매칭 요청", "결제 진행"), the origin marker on the map, the shared-route polyline, and the active state of every filter chip. The blue is bright and vivid — not corporate navy — to feel young and confident, matching the target demographic of university students, job seekers, and early-career professionals who need affordable late-night rides. Alongside blue, a compact 4-state color language carries the matching lifecycle: `{colors.waiting}` amber for pending, `{colors.success}` emerald for matched/confirmed, `{colors.danger}` coral for errors/cancellations, and `{colors.safety}` deep red reserved exclusively for the emergency shield button.

The typography is Pretendard — the de-facto modern Korean sans-serif — carrying every text role. Display sizes stay compact (32 → 28 → 24) to prioritize screen real estate on mobile; body settles at 15px with 1.5 line-height for comfortable Korean legibility. Headlines lean semibold (600) rather than bold (700) to keep the interface calm and trustworthy — a mobility app that shouts feels stressful.

**Key Characteristics:**
- Map-first architecture: every screen has the map behind it, chrome floats as translucent-white `{colors.surface-map-overlay}` cards
- Moyeota Blue (`{colors.primary}` — `#085AF5`) is the universal primary CTA + trust marker (origin pin, shared route, active filter) — fully-rounded pill at `{rounded.full}`
- Four-state color language for the matching lifecycle: waiting `{colors.waiting}` · matched `{colors.success}` · error `{colors.danger}` · safety `{colors.safety}`
- Pretendard at weight 600 (semibold) for headlines — the brand's signature "calm confidence" voice, avoiding stressful bold display
- 12px-radius (`{rounded.md}`) as the default card corner; 20px-radius (`{rounded.lg}`) for lifted result cards and bottom sheets; pills (`{rounded.full}`) for every CTA and status badge
- Compact 8px card-lift shadow — no atmospheric mesh, no glass panels, no gradient backgrounds on chrome
- Marker system uses 4 distinct semantic colors: origin `{colors.marker-origin}` blue · destination `{colors.marker-destination}` red · pickup `{colors.marker-pickup}` green · dropoff `{colors.marker-dropoff}` orange — always paired with a text label pill

## Colors

> **Source screens (planned):** onboarding · request-idle · request-waiting · request-matched · ride-in-progress · payment · chat · rating · safety-emergency · history · profile. The chrome palette is identical across all screens; the accent shifts with the matching lifecycle state.

### Brand & Accent
- **Moyeota Blue** (`{colors.primary}` — `#085AF5`): the brand's universal primary. Every primary CTA pill, the active filter chip, the origin marker fill, the shared-route polyline stroke, and inline link color on dark surfaces.
- **Moyeota Blue Pressed** (`{colors.primary-pressed}` — `#054BC7`): pressed state for the primary pill — also doubles as the inline link color on light surfaces.
- **Moyeota Blue Active** (`{colors.primary-active}` — `#033D9F`): deeply-pressed state for the primary button.
- **Moyeota Blue Soft** (`{colors.primary-soft}` — `#E6EEFE`): translucent blue tint used for the partner-info card fill and the secondary-emphasis "soft" pill variant.

### Lifecycle States
- **Success Green** (`{colors.success}` — `#10B981`): matched state — pickup marker, matched status badge, "결제 확인" success CTA, fare-split success panel.
- **Success Soft** (`{colors.success-soft}` — `#D1FAE5`): matched status badge fill, fare-split panel background — a gentle green wash.
- **Waiting Amber** (`{colors.waiting}` — `#F59E0B`): waiting/pending state — pulse ring on the waiting card, "매칭 대기 중" status badge text, timeline in-progress dot.
- **Waiting Soft** (`{colors.waiting-soft}` — `#FEF3C7`): waiting status badge fill — an unmistakable pending signal without visual noise.
- **Danger Coral** (`{colors.danger}` — `#E63946`): error/cancel state — validation errors, "요청 취소" destructive CTA text, timeout badges.
- **Danger Soft** (`{colors.danger-soft}` — `#FDECEE`): error status badge fill.

### Safety (Reserved)
- **Safety Red** (`{colors.safety}` — `#DC2626`): reserved exclusively for the emergency shield button (원터치 비상 신고 per the product spec). This shade is intentionally different from `{colors.danger}` — deeper, more urgent — and appears nowhere else in the system to preserve its urgency signal.
- **Safety Pressed** (`{colors.safety-pressed}` — `#A81D1D`): pressed state for the emergency FAB.

### Surface
- **Canvas Light** (`{colors.canvas-light}` — `#FFFFFF`): true white default surface — bottom sheets, matched-mode result cards, top app bar background, chat surface.
- **Canvas Dark** (`{colors.canvas-dark}` — `#111111`): dark surface for full-page dark mode (planned) and the toast background.
- **Soft Surface** (`{colors.surface-soft}` — `#F4F6FA`): warm-cool neutral for chip default fill, secondary card background, disabled button fill.
- **Surface Card** (`{colors.surface-card}` — `#FFFFFF`): default card background (identical to canvas — Moyeota cards distinguish themselves via shadow, not color).
- **Surface Map Overlay** (`{colors.surface-map-overlay}` — `rgba(255,255,255,0.94)`): translucent white used for cards that float over the map — allows subtle map bleed-through without impairing readability.
- **Surface Dark Elevated** (`{colors.surface-dark-elevated}` — `#181A1F`): elevated dark surface.
- **Surface Dark Card** (`{colors.surface-dark-card}` — `#22252B`): dark-mode card background.
- **Hairline Light** (`{colors.hairline-light}` — `#EDEFF3`): 1px divider rule on light surfaces.
- **Hairline Dark** (`{colors.hairline-dark}` — `rgba(229,229,229,0.18)`): translucent 1px divider on dark surfaces.

### Text
- **Ink** (`{colors.ink}` — `#111111`): primary text on `{colors.canvas-light}`. Headlines, primary body, button text.
- **Ink Deep** (`{colors.ink-deep}` — `#0A0A0B`): near-black for the toast fill.
- **Body Light** (`{colors.body-light}` — `rgba(17,17,17,0.72)`): translucent body text on light canvas — the system's default paragraph color.
- **Mute Light** (`{colors.mute-light}` — `#6B7280`): metadata text, timestamps, secondary label.
- **Ash Light** (`{colors.ash-light}` — `#D1D5DB`): disabled text and lowest-emphasis utility on light surfaces.
- **On Dark** (`{colors.on-dark}` — `#FFFFFF`): primary text on dark surfaces including toast.
- **Body Dark** (`{colors.body-dark}` — `rgba(255,255,255,0.76)`): translucent body text on dark canvas.
- **On Dark Mute** (`{colors.on-dark-mute}` — `#CFD4DB`): secondary text on dark surfaces.

### Semantic
- **Warning / Danger** (`{colors.danger}` — `#E63946`): validation errors, destructive confirmations, timeout badges.
- **Link Light** (`{colors.link-light}` — `#054BC7`): inline body-prose anchor on light canvas — same hex as `{colors.primary-pressed}`.
- **Link Dark** (`{colors.link-dark}` — `#5A9DFF`): inline anchor on dark surfaces.

### Map Semantics
- **Route Shared** (`{colors.route-shared}` — `#085AF5`): the primary blue polyline drawn on top of the map for the matched shared route — thickest stroke, highest opacity.
- **Route User A** / **Route User B** (`{colors.route-user-a}` — `#3B82F6` / `{colors.route-user-b}` — `#8B5CF6`): individual-user route highlights when the debug/admin view surfaces both riders' original paths alongside the shared segment.
- **Marker Origin** (`{colors.marker-origin}` — `#085AF5`): the user's chosen origin pin (matches brand blue).
- **Marker Destination** (`{colors.marker-destination}` — `#EF4444`): the user's chosen destination pin (semantic "arrival" red).
- **Marker Pickup** (`{colors.marker-pickup}` — `#10B981`): the computed pickup point after matching — the "meet here" success marker.
- **Marker Dropoff** (`{colors.marker-dropoff}` — `#F97316`): the computed dropoff point after matching — a warm accent to distinguish from pickup at a glance.

## Typography

### Font Family
- **Pretendard** is the modern Korean sans-serif used across every text role. It carries weights 400 (regular), 500 (medium), 600 (semibold), and 700 (bold), and falls back through `Apple SD Gothic Neo` → `system-ui` → `sans-serif`. Moyeota's distinctive choice is using **weight 600 (semibold) for display headlines** rather than 700 (bold) — the semibold weight reads as calm and reassuring, appropriate for an interface where users are entering trip information at 11pm and need to trust the system.
- **JetBrains Mono** appears in a single supporting role — the debug/developer view for displaying coordinate values and request IDs. Not part of the user-facing chrome.

### Hierarchy

| Token | Size | Weight | Line Height | Letter Spacing | Use |
|---|---|---|---|---|---|
| `{typography.display-xl}` | 32px | 700 | 1.25 | -0.4px | Onboarding welcome headline, matched-mode "매칭 성공!" celebration |
| `{typography.display-lg}` | 28px | 700 | 1.28 | -0.3px | Screen headline, "지금 어디로 가세요?" prompt |
| `{typography.display-md}` | 24px | 700 | 1.3 | -0.2px | Section headline inside a bottom sheet |
| `{typography.heading-xl}` | 22px | 600 | 1.3 | -0.2px | Result card title ("매칭이 완료되었어요") |
| `{typography.heading-lg}` | 18px | 600 | 1.35 | -0.1px | Card sub-title, partner name in the partner-info card |
| `{typography.heading-md}` | 16px | 600 | 1.35 | 0 | Fare-split value ("6,800원"), inline card label |
| `{typography.body-md}` | 15px | 400 | 1.5 | 0 | Default body copy, paragraph text |
| `{typography.body-strong}` | 15px | 600 | 1.4 | 0 | Inline emphasis, primary nav link |
| `{typography.body-sm}` | 13px | 400 | 1.5 | 0 | Card description, secondary body, timeline step body |
| `{typography.caption-md}` | 12px | 500 | 1.4 | 0 | Metadata, timestamp, bottom nav label |
| `{typography.caption-sm}` | 11px | 500 | 1.4 | 0.2px | Status badge text, smallest utility |
| `{typography.mono-md}` | 13px | 500 | 1.4 | 0 | Coordinate display in the debug view |
| `{typography.link-md}` | 15px | 500 | 1.5 | 0 | Inline body-prose anchor link |
| `{typography.button-lg}` | 16px | 700 | 1.25 | -0.1px | Primary CTA pill |
| `{typography.button-md}` | 14px | 600 | 1.25 | 0 | Compact pill, secondary CTA |
| `{typography.button-sm}` | 12px | 600 | 1.25 | 0.1px | Filter chip, tiny action |

### Principles
The hierarchy works on a 1.25 → 1.5 line-height split — chrome and buttons stay tight at 1.25 for crisp compact UI, and body opens up to 1.5 for readable Korean prose. Headline sizes drop in tight increments (32 → 28 → 24 → 22 → 18) and body settles at 15px — smaller than the PS system's 18px because Korean characters read comfortably at a slightly denser size on mobile, and screen real estate for a map interface is precious.

The weight contrast between display (700) and body (400) is deliberate but never dramatic; the 600 semibold that carries card titles bridges the two. Letter spacing is negative on display sizes (-0.4 to -0.1px) to keep dense Korean characters tight and confident.

### Note on Font Substitutes
Pretendard is open-source (SIL Open Font License) and safe to embed. The closest fallback chain if Pretendard fails to load:
- **Apple SD Gothic Neo** — pre-installed on iOS/macOS, matches Pretendard's proportions closely.
- **system-ui** — the OS default, which resolves to Roboto on Android and SF Pro on iOS.
- **Noto Sans KR** as a broader Google Fonts alternative if Pretendard cannot be self-hosted.

When substituting, preserve the -0.4px to -0.1px tracking on display tiers — the tight tracking is part of what makes Pretendard feel modern at semibold.

## Layout

### Spacing System
- **Base unit:** 4px (with 8/12/16 as the workhorse steps).
- **Tokens (front matter):** `{spacing.xxs}` (4px) · `{spacing.xs}` (8px) · `{spacing.sm}` (12px) · `{spacing.md}` (16px) · `{spacing.lg}` (24px) · `{spacing.xl}` (32px) · `{spacing.xxl}` (48px) · `{spacing.section}` (64px).
- **Card interior:** `{spacing.lg}` (24px) for the primary result card; `{spacing.md}` (16px) for compact info cards; `{spacing.sm}` (12px) for tight badges.
- **Card gap:** `{spacing.md}` (16px) between stacked floating cards.
- **Bottom sheet padding:** `{spacing.lg}` horizontal, `{spacing.lg}` top, `{spacing.xl}` bottom to clear the home indicator on iOS.

### Grid & Container
- **Max content width:** 640px for text-heavy screens (chat history, terms, profile) — on tablet/desktop the interface centers with `{colors.surface-soft}` margins.
- **Floating card width:** ~90% of viewport width on mobile, capped at 400px on larger screens.
- **Map full-bleed:** the map always extends edge-to-edge behind chrome; card overlays never occupy more than the bottom 40% of the screen at rest.
- **Bottom sheet reach:** default open state occupies bottom 60% of screen; fully-expanded state reaches under the top app bar.

### Whitespace Philosophy
Whitespace is functional and map-preserving. The 64px `{spacing.section}` between screen sections is smaller than a marketing site's 96px — Moyeota is a task interface, and every additional pixel of chrome is a pixel of map hidden. Inside cards, content is compact but never cramped: the 16-24px vertical rhythm inside a result card lets the eye scan (partner → route → fare → CTA) without hunting.

## Elevation & Depth

| Level | Treatment | Use |
|---|---|---|
| 0 — Flat | No border, no shadow | Map surface itself, top app bar |
| 1 — Hairline | 1px solid `{colors.hairline-light}` or `{colors.hairline-dark}` | Divider rules inside cards, bottom nav top border |
| 2 — Card lift | `0 4px 12px rgba(17,24,39,0.08)` + `0 2px 4px rgba(17,24,39,0.04)` | Default floating card on top of the map — compact but unambiguous "this is above the map" |
| 3 — Bottom sheet | `0 -8px 24px rgba(17,24,39,0.12)` (upward-cast) | Bottom sheets sliding up from the screen edge |
| 4 — Modal / dialog | `0 12px 40px rgba(17,24,39,0.16)` + centered scrim `rgba(17,17,17,0.4)` | Confirmation modals, safety-reporting dialog |

Cards do not shift elevation on press — the pressed state is expressed via the pill/card's fill color darkening (primary-pressed variant), not a shadow change. This keeps interactions feeling immediate rather than floaty.

### Decorative Depth
Depth comes from the map imagery and the pin/polyline system:
- **Map polyline** — the `{colors.route-shared}` blue polyline at 5px stroke traces the actual shared route along the road network. This is the visual centerpiece of a matched-mode screen.
- **Map markers** — the 4-color semantic marker pill system (origin/destination/pickup/dropoff) drops onto the map with a compact 8px shadow at the base of each pin, giving them enough weight to read against varied map tile backgrounds.
- **Card lift** — the 8px compact shadow separates chrome from map without visually dominating; the shadow is intentionally light so the eye stays on the map.

## Shapes

### Border Radius Scale

| Token | Value | Use |
|---|---|---|
| `{rounded.none}` | 0px | Top app bar, bottom nav, map — every full-bleed structural surface |
| `{rounded.sm}` | 6px | Micro-elements like inline tags, small chips |
| `{rounded.md}` | 12px | Default card corners — floating info cards, partner-info card, route-summary card, filter chip default |
| `{rounded.lg}` | 20px | Lifted result cards, bottom sheets (top corners only), match-request card |
| `{rounded.xl}` | 28px | Rare large surface treatment — celebratory success sheet |
| `{rounded.full}` | 9999px | Every CTA pill (primary / success / safety / secondary), filter chips, status badges, marker labels, avatar frames |

The radius vocabulary is a 6 / 12 / 20 rhythm for surfaces plus full-round for interactive pills. Structural surfaces stay flat at 0.

### Photography & Icon Geometry
- **Map view:** full-bleed, always underneath chrome. Naver Maps SDK on mobile, Naver Maps JS API on web/PoC — same tile source, same color palette.
- **Avatar circles:** 32px in chat header, 40px in partner-info card, 56px in profile — always `{rounded.full}`, with a 1px `{colors.hairline-light}` border on white surfaces.
- **Marker pins:** compact pill (padding `4px 10px`) at `{rounded.full}` with a 6px triangular tail pointing down at the map coordinate. Consistent proportions across all four semantic colors.
- **Icon system:** 24px stroke icons (weight 1.75px) throughout the chrome; 20px icons inside compact buttons; 16px icons inside caption-tier labels.

## Components

> **No hover states documented** — the primary target is native mobile (Android + iOS). Each spec covers Default and Active/Pressed only; the web PoC frontend inherits the same treatment with a subtle brightness lift on hover for desktop-web parity.

### Buttons

**`button-primary`** — the universal Moyeota CTA
- Background `{colors.primary}` (Moyeota Blue), text `{colors.on-primary}`, type `{typography.button-lg}`, padding `14px 28px`, height 52px, rounded `{rounded.full}`.
- Used for "매칭 요청", "결제하기", "다음", "확인" — every primary action.
- Pressed state lives in `button-primary-pressed` — background drops to `{colors.primary-pressed}` (`#054BC7`).

**`button-primary-soft`** — the low-emphasis primary variant
- Background `{colors.primary-soft}`, text `{colors.primary}`, type `{typography.button-md}`, padding `10px 20px`, height 40px, rounded `{rounded.full}`.
- Used for tertiary blue actions inside cards ("경로 자세히 보기", "파트너 정보 더보기").

**`button-success`** — confirmation CTA
- Background `{colors.success}`, text `{colors.on-success}`, type `{typography.button-lg}`, padding `14px 28px`, height 52px, rounded `{rounded.full}`.
- Used for "결제 완료 확인", "탑승 확인" — actions that seal a matched flow.

**`button-secondary-light`** — outline variant on light canvas
- Background transparent, text `{colors.ink}`, 1px solid `{colors.ash-light}` border, type `{typography.button-lg}`, padding `14px 28px`, height 52px, rounded `{rounded.full}`.
- Lower-emphasis alternate action alongside a primary CTA ("나중에", "취소").

**`button-secondary-dark`** — outline variant on dark canvas
- Background transparent, text `{colors.on-dark}`, 1px solid `{colors.hairline-dark}`, type `{typography.button-lg}`, same padding/height/radius.
- Same role as the light variant, inverted for dark-mode.

**`button-danger`** — destructive text CTA
- Transparent background, text `{colors.danger}`, type `{typography.button-md}`, padding `10px 20px`, height 40px, rounded `{rounded.full}`.
- Used for "요청 취소", "매칭 해제" — always paired with a confirmation modal.

**`button-safety`** — the emergency escalation CTA
- Background `{colors.safety}`, text `{colors.on-safety}`, type `{typography.button-lg}`, padding `14px 28px`, height 56px (slightly taller than primary to reinforce urgency), rounded `{rounded.full}`.
- Used inside the safety flow only — the FAB variant `safety-shield-fab` is the resting-state entry point.

**`button-disabled`**
- Background `{colors.surface-soft}`, text `{colors.ash-light}`, rounded `{rounded.full}` — flat soft gray.

### Status Badges

**`status-badge-idle`** / **`status-badge-waiting`** / **`status-badge-matched`** / **`status-badge-error`**
- All at `{typography.caption-sm}`, padding `4px 10px`, rounded `{rounded.full}`.
- Fill/text pairs: idle uses `{colors.surface-soft}`/`{colors.mute-light}`; waiting uses `{colors.waiting-soft}`/`{colors.waiting-pressed}`; matched uses `{colors.success-soft}`/`{colors.success-pressed}`; error uses `{colors.danger-soft}`/`{colors.danger-pressed}`.
- Sit inline in the top-right of the primary result card and inside the timeline steps.

### Inputs & Forms

**`text-input`** + **`text-input-focused`**
- Default: background `{colors.canvas-light}`, text `{colors.ink}`, 1px solid `{colors.ash-light}`, type `{typography.body-md}`, padding `14px 16px`, height 52px, rounded `{rounded.md}` (12px).
- Focused: 2px solid `{colors.primary}` border, no halo (border weight is the focus signal).

**`location-search-bar`** — signature address search field
- Background `{colors.surface-map-overlay}` (translucent white), text `{colors.ink}`, type `{typography.body-md}`, padding `14px 20px`, height 56px, rounded `{rounded.md}`.
- Sits floating above the map at the top of the request screen with a magnifier icon at the left edge and "출발지 검색" or "목적지 검색" placeholder text.

### Filter & Chip

**`filter-chip`** + **`filter-chip-active`**
- Default: background `{colors.surface-soft}`, text `{colors.body-light}`, type `{typography.button-sm}`, padding `6px 12px`, rounded `{rounded.full}`.
- Active: background flips to `{colors.primary}`, text `{colors.on-primary}` — a bold color shift so the chip stays legible against varied map backgrounds when the filter row floats above the map.
- Used in the matching preferences row ("가까운 곳", "심야", "여성만") on the request-idle screen.

### Cards & Panels

**`match-request-card`** — the floating card during idle/waiting states
- Container: background `{colors.surface-map-overlay}` (94% white — allows subtle map bleed-through), padding `{spacing.lg}` (20px), rounded `{rounded.lg}` (20px).
- Contents (waiting variant): status badge at top-right, "매칭 대기 중" headline in `{typography.heading-xl}`, elapsed-time caption in `{typography.body-sm}`, destructive `button-danger` "요청 취소" at the bottom.

**`match-result-card`** — the primary matched-state result card
- Container: background `{colors.canvas-light}` (opaque — the moment matters), padding `20px 20px 24px`, rounded `{rounded.lg}` (20px).
- Contents (top to bottom): matched status badge inline with success icon → "매칭 완료" `{typography.display-md}` headline → embedded `partner-info-card` → embedded `route-summary-card` → embedded `fare-split-panel` → primary CTA "결제하고 탑승 준비".

**`partner-info-card`** — nested partner-identity card
- Container: background `{colors.primary-soft}`, padding `12px 16px`, rounded `{rounded.md}`.
- Layout: 40px avatar circle at left, partner name + rating badge on the right, small "채팅 시작" text link on the far right.

**`fare-split-panel`** — the money moment
- Container: background `{colors.success-soft}`, text `{colors.success-pressed}`, type `{typography.heading-md}` for the value, padding `12px 16px`, rounded `{rounded.md}`.
- Layout: "1인당 요금" label at left in `{typography.body-sm}`, "6,800원" value at right in `{typography.heading-md}` — the visual reward for the entire matching interaction.

**`route-summary-card`** — the trip summary
- Container: background `{colors.surface-card}` with 1px solid `{colors.hairline-light}`, padding `16px`, rounded `{rounded.md}`.
- Contents: origin ↔ destination stacked with a vertical connecting line (2px `{colors.primary}` dashed), pickup + dropoff coordinates below in `{typography.body-sm}`, total distance + shared distance at the bottom.

**`safety-shield-fab`** — the emergency entry point
- 56px circular button, background `{colors.safety}`, shield icon in `{colors.on-safety}`, positioned floating at the bottom-right of the ride-in-progress screen with 20px inset from screen edges.
- Pressed state sinks to `{colors.safety-pressed}` and opens the safety confirmation modal.

**`map-marker-origin`** / **`map-marker-destination`** / **`map-marker-pickup`** / **`map-marker-dropoff`**
- Compact pill (padding `4px 10px`), text `{typography.caption-sm}`, rounded `{rounded.full}`.
- Fill matches the semantic marker color; label text is white except pickup which uses `{colors.on-success}` (also white) for consistency.
- Base tail: 6px downward triangle pointing at the anchor coordinate.
- Origin: "출발" / Destination: "목적" / Pickup: "픽업" / Dropoff: "드롭"

**`route-polyline-shared`** — the shared-route line
- Stroke `{colors.route-shared}` (Moyeota Blue), weight 5px, opacity 0.85. Drawn along the actual road network (LineString from the backend's `RouteSimilarityCalculator`).
- On the debug/admin view, the two individual-user routes are overlaid at 3px stroke, 0.5 opacity with `{colors.route-user-a}` and `{colors.route-user-b}` respectively.

### Chat

**`chat-bubble-mine`** — outgoing chat message
- Background `{colors.primary}`, text `{colors.on-primary}`, type `{typography.body-md}`, padding `10px 14px`, rounded `{rounded.lg}` (20px on all corners except the bottom-right which is `{rounded.sm}` 6px — a subtle tail cut).

**`chat-bubble-partner`** — incoming chat message
- Background `{colors.surface-soft}`, text `{colors.ink}`, same type/padding/radius scheme with the bottom-left corner cut instead.

### Timeline

**`timeline-step`** — matching-progress step card
- Container: background `{colors.surface-card}` with 1px solid `{colors.hairline-light}`, padding `12px 16px`, rounded `{rounded.md}`.
- Layout: 20px status dot at left (fills with `{colors.waiting}` in-progress, `{colors.success}` completed, `{colors.ash-light}` upcoming), title `{typography.body-strong}`, description `{typography.body-sm}`.
- Steps: 요청 접수 → 후보 탐색 → 경로 확인 → 매칭 성사 → 결제 → 탑승 준비.

### Navigation

**`top-app-bar`**
- Background `{colors.canvas-light}`, text `{colors.ink}`, height 56px, rounded `{rounded.none}`.
- Layout: back arrow (24px stroke) at left, screen title centered at `{typography.heading-lg}`, right-side action icon slot (menu / share) at 24px.

**`bottom-nav`** — 4-tab bottom navigation
- Background `{colors.canvas-light}` with 1px top border `{colors.hairline-light}`, height 64px, rounded `{rounded.none}`.
- Tabs: 홈 (map+request) · 이력 · 채팅 · 마이. Active tab is `{colors.primary}` icon + label; inactive is `{colors.mute-light}`. Labels at `{typography.caption-md}`.

### Overlay

**`toast`** — feedback notification
- Background `{colors.ink-deep}`, text `{colors.on-dark}` in `{typography.body-strong}`, padding `12px 16px`, rounded `{rounded.md}`.
- Appears at the bottom above the bottom nav with a 300ms slide-up + fade-out at 3 seconds by default.

**`bottom-sheet`** — modal bottom sheet
- Background `{colors.canvas-light}`, text `{colors.ink}`, type `{typography.body-md}`, padding `24px 20px 32px`, rounded `20px 20px 0 0` (top corners only).
- Includes a 32×4 drag handle in `{colors.ash-light}` at the top-center for swipe-to-dismiss affordance.
- Used for: address search suggestions, filter preferences, safety options, profile menu.

### Inline

**`link-inline`** — body-prose anchor
- `{colors.link-light}` text on light canvas / `{colors.link-dark}` on dark canvas, no underline by default. Inline body links inside terms and support articles.

## Do's and Don'ts

### Do
- Reserve `{colors.primary}` (Moyeota Blue) for primary CTAs, the origin marker, the shared-route polyline, and the active filter chip. The blue is the trust anchor; wide accidental use dilutes it.
- Reserve `{colors.safety}` (deep red) exclusively for the emergency shield entry point and its confirmation modal. Never use for validation, cancellation, or destructive UI.
- Use Pretendard at weight 600 (semibold) for card titles and screen headlines. Bold 700 is reserved for CTAs and display headlines only.
- Stack floating cards over the map with `{spacing.md}` (16px) vertical gap between cards; keep no more than 3 cards visible at rest.
- Use `{rounded.full}` (9999px) on every CTA pill and status badge; `{rounded.md}` (12px) on every floating info card; `{rounded.lg}` (20px) on the primary result card. The three-radius vocabulary is the entire shape system.
- Pair every semantic map marker with a text label pill (`{component.map-marker-origin}` etc.) — never rely on color alone for meaning.
- Use `{component.status-badge-*}` in the top-right of any card that owns a lifecycle state; the fixed position makes the state scannable in a glance.

### Don't
- Don't use `{colors.primary}` on error text or destructive UI — mixing brand blue with negative outcomes damages the trust reading.
- Don't apply `{colors.safety}` anywhere outside the emergency flow — its urgency signal only works because it's scarce.
- Don't use drop shadows deeper than `Level 2` (compact 8px card-lift) on chrome — the map is the visual protagonist, and heavy chrome shadows compete with it.
- Don't set text on top of the map without a card fill — floating text over map tiles is illegible at zoom transitions.
- Don't introduce a second sans-serif or a serif for editorial polish. Pretendard carries every text role, and mixing faces feels off-brand for a task interface.
- Don't soften pill geometry. CTAs are always `{rounded.full}` — no medium-radius buttons.
- Don't put a gradient on chrome. The system has no gradients; the map itself provides all the "atmospheric" color the interface needs.
- Don't stack more than one floating card of the same lifecycle state on the same screen — the pattern is one card per state per screen.

## Responsive Behavior

### Breakpoints

| Name | Width | Key Changes |
|---|---|---|
| desktop | 1200px+ | Map centered with `{colors.surface-soft}` outer margins to keep a phone-like column width; primary interaction happens in the centered card stack |
| desktop-small | 1024px | Same layout as desktop, narrower outer margins |
| tablet | 768px | Single-column mobile layout activates; bottom sheet becomes centered dialog |
| mobile-lg | 480px | Default mobile — floating cards occupy ~90% width, capped at 400px |
| mobile | 360px | Standard Android/iOS width — tightens card padding to `{spacing.md}` |
| mobile-narrow | 320px | Legacy device support — hero headline scales `{typography.display-lg}` → `{typography.heading-xl}` |

### Touch Targets
All interactive elements meet WCAG AAA (≥ 44×44px). `{component.button-primary}` sits at 52px height with 28px horizontal padding. `{component.button-safety}` intentionally sits at 56px — one step taller than primary — to reinforce urgency and prevent misfire. `{component.text-input}` sits at 52px. `{component.location-search-bar}` sits at 56px. `{component.filter-chip}` is ~32px height with extended tappable padding to 44px. `{component.safety-shield-fab}` is exactly 56×56 circular.

### Collapsing Strategy
- **Top app bar:** always visible; title truncates with ellipsis on narrow screens.
- **Bottom nav:** always visible; on tablet/desktop the same 4 tabs move to a left-side rail with icons + labels stacked.
- **Match result card:** at mobile the card is full-width minus 16px screen padding; at desktop it caps at 400px width and centers under the map.
- **Route summary card:** at mobile the origin/destination stack is vertical with a connecting line; at tablet+ becomes horizontal with the line rotated 90°.
- **Bottom sheet:** at mobile slides up from the bottom edge; at tablet+ becomes a centered modal dialog with a scrim.
- **Chat surface:** always full-screen (chat is a dedicated screen, not an overlay).
- **Section padding:** `{spacing.section}` (64px) desktop → `{spacing.xxl}` (48px) tablet → `{spacing.xl}` (32px) mobile.
- **Hero headline:** `{typography.display-xl}` (32px) at mobile, holds through desktop.

### Image Behavior
- Map imagery is the primary "image" and stays full-bleed at every breakpoint.
- Avatar circles preserve their fixed pixel size (32/40/56) across breakpoints — they don't scale with viewport.
- Map markers preserve their fixed pixel size and adjust position dynamically as the user pans/zooms.
- Map polylines re-render on every zoom change to maintain the 5px stroke weight in screen pixels.

## Iteration Guide

1. Focus on ONE component at a time. Pull its YAML entry and verify every property resolves.
2. Reference component names and tokens directly (`{colors.primary}`, `{component.match-result-card}`, `{rounded.md}`) — do not paraphrase.
3. Run `npx @google/design.md lint DESIGN-moyeota.md` after edits — `broken-ref`, `contrast-ratio`, and `orphaned-tokens` warnings flag issues automatically.
4. Add new variants as separate component entries (`-pressed`, `-disabled`, `-soft`) — do not bury them inside prose.
5. Default body to `{typography.body-md}` (15px / 400 / 1.5); reach for `{typography.display-xl}` strictly for the screen-top hero headline; use `{typography.heading-md}` for numeric values (fare, distance).
6. Keep `{colors.primary}` scarce per screen — one primary CTA, one origin marker, one route polyline, one active filter. If a screen needs more, revisit which one is truly primary.
7. When introducing a new component, ask whether it can be expressed with the existing floating-card + pill CTA + status badge vocabulary before adding new tokens. The system's strength is that it almost never needs new ones.
8. Preserve the map-first hierarchy. Any new chrome element must justify why it needs to obscure the map at all.

## Known Gaps

- **Dark mode not fully specified** — the `{colors.canvas-dark}` / `{colors.surface-dark-elevated}` tokens exist for the toast and future dark mode, but individual dark-mode variants of each card component are not yet documented. A follow-up pass will produce full dark-mode component specs.
- **Driver/기사 side interface** not documented — this system covers the passenger-facing app only. The 기사 (driver) app inherits the same chrome vocabulary but adds trip-management surfaces (오늘의 매칭 목록, 정산 대시보드) that need their own component set.
- **Payment flow chrome** (카카오페이·토스페이 modal, receipt sheet) is stubbed via the `button-success` + `bottom-sheet` combination but not fully specified — waiting for payment SDK integration details before locking in.
- **Onboarding & authentication** (카카오 소셜 로그인, 본인인증) chrome not in scope for this document.
- **Real-time location sharing** (실시간 위치 공유 per the product spec) — the map treatment for showing the partner's live location is stubbed but not fully specified.
- **Rating & trust badge system** (평점 및 신뢰 인증 배지) is referenced in the `partner-info-card` component but the badge iconography and tier progression is not yet locked.
- **Empty & error states** (매칭 실패, 결제 실패, 서비스 지역 밖) not fully mapped — the general chrome supports them via `status-badge-error` + `button-secondary-light`, but full copy and iconography per state needs writing.
