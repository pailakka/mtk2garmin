---
name: Kartat.Hylly.org
description: Practical field-ready landing page for free Finnish offline topographic maps and support.
colors:
  forest-50: "#f0fdf4"
  forest-100: "#dcfce7"
  forest-500: "#22c55e"
  forest-600: "#16a34a"
  forest-700: "#15803d"
  forest-800: "#166534"
  forest-900: "#14532d"
  page-bg: "#f9fafb"
  surface: "#ffffff"
  soft: "#f3f4f6"
  hairline: "#e5e7eb"
  ink: "#111827"
  text: "#1f2937"
  muted: "#4b5563"
  quiet: "#6b7280"
  amber-soft: "#fef3c7"
  amber-border: "#fcd34d"
  amber-ink: "#451a03"
typography:
  display:
    fontFamily: "ui-sans-serif, system-ui, sans-serif"
    fontSize: "clamp(1.875rem, 4vw, 3rem)"
    fontWeight: 800
    lineHeight: 1.05
    letterSpacing: "normal"
  headline:
    fontFamily: "ui-sans-serif, system-ui, sans-serif"
    fontSize: "1.875rem"
    fontWeight: 700
    lineHeight: 1.2
  title:
    fontFamily: "ui-sans-serif, system-ui, sans-serif"
    fontSize: "1.125rem"
    fontWeight: 700
    lineHeight: 1.35
  body:
    fontFamily: "ui-sans-serif, system-ui, sans-serif"
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.6
  label:
    fontFamily: "ui-sans-serif, system-ui, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 500
    lineHeight: 1.35
rounded:
  sm: "4px"
  md: "6px"
  lg: "8px"
  xl: "12px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "16px"
  lg: "24px"
  xl: "32px"
  2xl: "48px"
  3xl: "64px"
components:
  button-primary:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.forest-900}"
    typography: "{typography.label}"
    rounded: "{rounded.md}"
    padding: "12px 32px"
  button-support:
    backgroundColor: "{colors.forest-600}"
    textColor: "{colors.surface}"
    typography: "{typography.label}"
    rounded: "{rounded.md}"
    padding: "12px 24px"
  button-outline:
    backgroundColor: "transparent"
    textColor: "{colors.surface}"
    typography: "{typography.label}"
    rounded: "{rounded.md}"
    padding: "12px 32px"
  card-surface:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text}"
    rounded: "{rounded.xl}"
    padding: "24px"
  chooser-tile:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.muted}"
    rounded: "{rounded.lg}"
    padding: "16px"
---

# Design System: Kartat.Hylly.org

## 1. Overview

**Creative North Star: "The Field Map Counter"**

Kartat.Hylly.org should feel like the practical counter at an outdoor-map workshop: clear labels, known file formats, direct help, and enough visual confidence that a visitor trusts the downloads before leaving mobile coverage. The interface is a public brand surface, but the brand is utility. It earns support purchases by being honest, specific, and easy to scan.

The current system is a restrained forest-green and neutral layout with white surfaces, system sans typography, and device-specific download sections. Future work may add more character, but it must preserve the field-ready promise from PRODUCT.md: practical, trustworthy, and direct. It explicitly rejects generic SaaS gloss, hard-sell ecommerce pressure, vague adventure decoration, and AI-style card grids.

**Key Characteristics:**

- Forest green carries action, trust, and map/outdoor association.
- White and cool-gray surfaces keep dense download information readable.
- Rounded corners are modest and functional, never pillowy.
- Metadata such as dates, sizes, hashes, file names, and device names is part of the design language.
- Support CTAs can be prominent, but free downloads must remain visible and honest.

## 2. Colors

The palette is restrained: forest green for trust and action, white/gray for legibility, and amber only for build-status warnings.

### Primary

- **Field Forest**: the main action green. Use it for download buttons, support CTAs, icons, active links, and selected states.
- **Deep Topographic Green**: the hero and dark brand field. Use it for high-contrast brand moments, not for long prose.
- **Pale Map Green**: soft support backgrounds and hover fills. Use sparingly to group action areas without making the page feel decorative.

### Secondary

- **Build Amber**: warning/status tone for non-production build banners only. It must never compete with support or download CTAs.

### Neutral

- **Clean Sheet**: white surfaces for cards, tables, FAQ rows, and primary content blocks.
- **Field Fog**: page background and subtle section separation.
- **Survey Ink**: primary headings and high-importance body text.
- **Trail Gray**: secondary body copy and metadata. Do not lighten it below AA contrast.
- **Hairline Gray**: borders and dividers.

### Named Rules

**The Free Download Visibility Rule.** Green CTAs can drive support first, but a free-download path must stay visible in the same viewport or immediately adjacent flow.

**The Forest Ramp Rule.** If a component uses `forest-50`, `forest-100`, or `forest-500`, those values must be defined in the theme. Undefined Tailwind utility names are not a design system.

## 3. Typography

- **Display Font:** system sans (`ui-sans-serif, system-ui, sans-serif`)
- **Body Font:** system sans (`ui-sans-serif, system-ui, sans-serif`)
- **Label/Mono Font:** system sans for labels; browser monospace only for file names, hashes, and extensions.

**Character:** The typography is intentionally plain and utilitarian. It should read like a field manual and download index, not a fashion brand or SaaS launch page.

### Hierarchy

- **Display** (800, `clamp(1.875rem, 4vw, 3rem)`, 1.05): one H1 in the hero. Keep Finnish line breaks balanced and avoid over-tight letter spacing.
- **Headline** (700, `1.875rem`, 1.2): major sections such as support, Garmin, Android, iPhone, and FAQ.
- **Title** (700, `1.125rem`, 1.35): card titles, support tiers, chooser tiles, and FAQ summaries.
- **Body** (400, `1rem`, 1.6): explanatory text. Cap long prose at 65-75ch.
- **Label** (500, `0.875rem`, 1.35): nav links, small CTAs, metadata labels, and helper text.

### Named Rules

**The Field Manual Rule.** Use plain readable Finnish, visible file extensions, and exact device names. Never replace useful labels with clever marketing language.

**The Metadata Is Content Rule.** File sizes, dates, SHA1 hashes, and compatibility notes must remain typographically readable, not treated as decorative fine print.

## 4. Elevation

The system uses a hybrid of flat surfaces, thin borders, and small state shadows. Depth is functional: cards can lift slightly on hover to signal clickability, but large ambient shadows are forbidden because they make dense technical information look like generic marketing cards.

### Shadow Vocabulary

- **Sticky Nav** (`0 1px 2px rgba(0, 0, 0, 0.05)`): subtle separation for the fixed navigation.
- **Surface Lift** (`0 1px 2px rgba(0, 0, 0, 0.05)`): default card shadow where a border alone is not enough.
- **Interactive Lift** (`0 4px 8px rgba(0, 0, 0, 0.08)`): hover-only treatment for important clickable surfaces.

### Named Rules

**The No Ghost Cards Rule.** Do not combine a 1px border with a wide soft shadow. Choose a border or a small shadow; never both as decoration.

**The Flat Until Useful Rule.** Surfaces are flat at rest unless they need separation or click affordance.

## 5. Components

### Buttons

- **Shape:** practical rounded rectangle (`6px`).
- **Primary hero:** white background, deep green text, bold label, `12px 32px` padding.
- **Support CTA:** forest green background, white text, bold label, `12px 24px` padding.
- **Download CTA:** forest green background, white text, icon allowed, compact padding.
- **Hover / Focus:** darken green by one step; show a visible focus ring. Movement must be small and optional.
- **Secondary / Outline:** transparent or white surface with a real border. Use for free-download or install-app alternatives.

### Chips

- **Style:** no persistent chip system yet. If added, chips should be file-format or device labels with modest radius and high contrast.
- **State:** selected states use Field Forest, not gray-only styling.

### Cards / Containers

- **Corner Style:** modest (`8px` for cards, `12px` for support tiers and major grouped containers).
- **Background:** Clean Sheet for content; Pale Map Green only for emphasized map/download groups.
- **Shadow Strategy:** flat by default, small interactive lift only on hover.
- **Border:** Hairline Gray at rest; Field Forest for focused or recommended support tier.
- **Internal Padding:** `24px` for cards, `16px` for compact chooser tiles.

### Inputs / Fields

The public site currently has no form inputs. If forms are added, use white backgrounds, Hairline Gray borders, `6px` radius, Survey Ink text, visible labels, and a forest focus ring. Placeholder text must meet AA contrast.

### Navigation

Navigation is sticky, white, and simple. The brand mark uses the map icon plus `Kartat.Hylly.org`; nav links are medium-weight labels with forest hover states. Mobile navigation can remain a direct toggle menu, but focus states and touch target sizes must be explicit.

### Download Card

Download cards combine a human label, raw file name, optional description, file size/date, SHA1 hash, and a direct CTA. The structure must stay scan-friendly: file identity first, compatibility note second, hash last.

### Support Tier Card

Support cards are commercial, but must remain honest. The recommended tier may be visually stronger with a forest border and filled CTA, but all tiers should clearly say what the buyer receives. Never imply that buying is required to download maps.

### FAQ Disclosure

FAQ rows are native disclosure controls with a clear title, short answer, and visible open/closed affordance. Answers should match JSON-LD exactly when used for structured data.

## 6. Do's and Don'ts

### Do:

- **Do** keep the support CTA prominent while preserving an obvious free-download path.
- **Do** use forest green for real actions: support, download, active links, and focused states.
- **Do** keep long Finnish body copy at 65-75ch and use plain language.
- **Do** show exact file names, file extensions, sizes, dates, and SHA1 hashes where they help users trust the download.
- **Do** define the full forest ramp before using light or mid forest classes.
- **Do** maintain WCAG AA contrast for body text, metadata, buttons, warnings, and focus states.

### Don't:

- **Don't** make the site feel like a generic SaaS landing page.
- **Don't** create a hard-sell ecommerce funnel or hide free downloads behind support messaging.
- **Don't** use vague adventure imagery as a substitute for useful map/device/download information.
- **Don't** add glossy AI-style cards, over-soft shadows, repeated icon grids, fake urgency, inflated claims, or template-like section grammar.
- **Don't** use gradient text, glassmorphism, decorative grid backgrounds, large rounded card corners, or colored side-stripe borders.
- **Don't** turn metadata into unreadable gray fine print.
