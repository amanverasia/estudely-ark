# Estudely Ark: ten Android design directions

Created using Claude’s frontend-design skill. These are visual concepts, not changes to the Android app. Download, reading and storage states are illustrative. No account, ads or AI features are proposed.

## Shared brief

Make the existing library readable and inviting. Put reading first, separate downloaded subjects from available subjects, make module size and download state easy to scan, and give storage and Wi-Fi controls a clear home. Keep all current functional capabilities.

The existing screen puts long descriptions, checkboxes, logs, storage, downloads, and sharing together. These concepts explore different hierarchies and compositions rather than ten recolors.

## Directions

### 01 — Atlas

A calm, beautifully organised pocket library.

Reading comes first. A memorable illustrated collection sits above ready-to-open modules, with downloads kept secondary.

Palette: #FCFDFE, #172D49, #2852CC, #EDF3FC, #B7D0F8. Typography: Avenir Next + Source Serif.

Left-aligned reader-first hierarchy. Replaced a generic dashboard card grid with one botanical book panel, two compact ready modules, and a quiet download row.

### 02 — Field Notes

A useful field companion, wherever you go.

An offline map anchors a practical field-book layout. Earthy greens and a straightforward list make the app feel resourceful and reassuring.

Palette: #F5F8F2, #254337, #315B46, #DCE7D5, #B1C49E. Typography: Source Serif + Gill Sans.

Contour-map composition, not a generic hero illustration. Quiet list rows below the map prevent every module competing as a big card.

### 03 — Reading Room

A personal bookshelf, with room to grow.

Typographic book covers make downloaded collections feel tangible. A plum-and-lavender palette puts the pleasure of reading at the centre.

Palette: #F8F5FA, #493453, #704278, #EBDFF0, #D9E5D1. Typography: Source Serif + Avenir Next.

A real shelf of subject collections carries the visual identity. Avoided progress rings and generic dashboard stats; book covers remain readable at phone size.

### 04 — Signal

Clear, confident downloads with no guesswork.

A bold transfer view makes a large download understandable. One blue progress surface contrasts with a quiet queue and a clear ready-to-read result.

Palette: #2246E8, #FFFFFF, #151C2C, #D8DFFF, #F0F6F3. Typography: Helvetica Neue.

The bold number is meaningful transfer progress, not decoration. A clear queue and readable completion state replace technical logs; no gradient or ornamental data.

### 05 — Hearth

A friendly first library

Large topic tiles make the first download feel approachable. The colour belongs to the subjects; the action stays a single, unmistakable button.

Palette: #FFF9F5, #814254, #F6E8CD, #DBE7E4, #3F222A. Typography: Avenir Next.

Plan: milk-white base, mulberry ink, apricot and sage subject tiles; Avenir Next for both title and controls. Layout [brand] / [short welcome] / [2×2 topic picker] / [selection + download] / [navigation]. Left aligned copy. Critique: rejected a generic pastel dashboard with ornamental totals; each colour now encodes a subject and every tile shows the download size.

### 06 — Monsoon

A quiet map-led dark mode

An offline map gives the app an immediate purpose. Deep blue surfaces and restrained sea-glass accents keep night reading calm, with saved books immediately below.

Palette: #101E30, #18313E, #C2DEDA, #E1EBEE, #91A8BA. Typography: Georgia + Avenir Next.

Plan: midnight base, ocean surfaces, pale sea-glass actions; Georgia only for the map headline, Avenir for tasks. Layout [brand + offline state] / [place search] / [India map] / [saved subjects] / [navigation]. Left aligned. Critique: removed glowing gradients and dashboard-style stat cards; the dark treatment is carried by a real cartographic subject and useful offline status.

### 07 — Index

Clear, compact and easy to scan

A carefully typeset catalogue keeps every subject within reach. Size, selection and the next download action are visible without turning the screen into a settings form.

Palette: #FFFFFF, #17191D, #174BC0, #E5E8ED, #707985. Typography: Helvetica Neue.

Plan: white, ink, restrained blue and neutral rules; Helvetica Neue at a clear 28/17/12.5 scale. Layout [identity] / [heading + explanation] / [all/saved tabs] / [7 subject rows] / [download] / [navigation]. Left aligned. Critique: replaced editorial eyebrows and decorative numbering with task-based tabs. Dividers separate actionable rows, not newspaper columns.

### 08 — Saffron

Bright, useful, unmistakably welcoming.

A confident yellow landscape introduces the library, while two large ready-to-read subjects put useful content one tap away. Simple paper-like illustrations make a serious collection feel approachable.

Palette: #FFFDF7, #E9B63E, #172B4E, #E8EAD9, #69715F. Typography: Avenir Next / Ark Serif.

Plan: sunlight #E9B63E, paper #FFFDF7, ink #172B4E, leaf #E8EAD9, soft ink #69715F. Avenir Next carries clear navigation; Ark Serif gives the app title a bookish identity. Layout: [Ark + settings] [illustrated introduction] [two ready subjects] [download list] [navigation], left aligned. Critique: removed decorative category pills and a large number hero; the illustration earns its space by connecting books and landscapes, and the only statistics are download sizes and readiness.

### 09 — Grove

A calm place to keep coming back to.

Deep forest framing and generous book covers make this feel like a personal reading collection. A single pale reading surface gives the ready library priority; download progress stays quiet but easy to find.

Palette: #143D33, #EFF4E9, #B9D0B2, #DBE7D7, #D5B67A. Typography: Ark Serif / Avenir Next.

Plan: forest #143D33, reading paper #EFF4E9, sage #B9D0B2, mist #DBE7D7, binding gold #D5B67A. Ark Serif is used for the title and covers; Avenir Next for actions. Layout: [wordmark] [large reading title] [featured ready library] [two book-like subjects] [download strip] [navigation], left aligned. Critique: replaced a generic green dashboard of equal cards with one pale reading surface, a strong horizontal cover, and two vertical covers. No neon accents, decorative progress scores, account avatar, or invented AI controls.

### 10 — Slate

Native Android, with the details cared for.

A practical shipping direction: clean native-style navigation, separate ready and available subjects, legible storage information, and a dedicated download surface. Familiar Android patterns make the interface easy to learn.

Palette: #F6F7FB, #FFFFFF, #283E77, #E1E6F7, #687080. Typography: Avenir Next / system-ui.

Plan: canvas #F6F7FB, surface #FFFFFF, indigo #283E77, selection #E1E6F7, secondary ink #687080. One sans family with deliberate 32/18/14/11 type hierarchy. Layout: [Library] [segmented ready/available] [storage strip] [three ready rows] [active download] [navigation], all left aligned. Critique: removed the default giant rounded hero and detached card for every number. Storage is one quiet strip; downloadable subjects are grouped in one surface, and selected states use clear contrast rather than shadow.

## Review

Open index.html for the interactive gallery. Select any of ten directions, use the previous/next controls, compare all concepts, or shortlist favorites. The gallery is self-contained and works without internet. The underlying concept JSON and this build script make each direction editable.
