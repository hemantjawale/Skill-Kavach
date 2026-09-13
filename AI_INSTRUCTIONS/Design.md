# SurakshaSetu Mobile App — Design System

## 1. Design Goal

SurakshaSetu is an Android-first industrial safety training and workforce management application for workers in mining, steel, mica, and similar industrial environments.

The UI must be:

- Easy to understand at first glance
- Usable by workers with different levels of digital literacy
- Comfortable to use on mid-range Android phones
- Suitable for Hindi, Santali, and English
- Clear in outdoor and industrial environments
- Offline-friendly
- Consistent across safety training, jobs, attendance, salary, leave, and certificates
- Professional enough for enterprise and government use

The UI must **not** look like a generic AI-generated dashboard.

---

## 2. Core Design Principles

### 2.1 Clarity Over Decoration

Every screen should answer these three questions immediately:

1. Where am I?
2. What information is important here?
3. What should I do next?

Avoid visual elements that do not help the user perform a task.

### 2.2 Flat UI

Use a clean, mostly flat interface.

Do not use:

- Gradients
- Glassmorphism
- Neon effects
- Glowing buttons
- Decorative blobs
- Excessive shadows
- Huge rounded cards everywhere
- Random illustrations
- Floating decorative shapes
- Unnecessary animations
- Extremely large hero headings
- Different colors for every card

### 2.3 Safety Colors Have Meaning

Safety colors should never be used randomly.

- Green = safe, valid, successful, completed
- Amber = warning, attention required
- Red = danger, failure, emergency
- Blue-purple = normal primary action / navigation
- Gray = neutral or secondary information

### 2.4 One Primary Action Per Screen

Each screen should have one obvious main action.

Examples:

- Training details → `Start Training`
- Job details → `Start Shift`
- Assessment → `Submit Answer`
- Leave form → `Apply Leave`
- Certificate → `Show QR`
- Attendance → `Check In`

Secondary actions should be visually less dominant.

### 2.5 Icons + Text

Do not rely only on icons.

Use:

`Icon + Label`

Examples:

- Training
- My Job
- Attendance
- Certificates
- Salary
- Leave

This is especially important for workers who may not immediately understand unfamiliar icons.

---

# 3. Color System

## Primary Color

**Primary**
`#869BDB`

Use for:

- App bar accents
- Selected bottom navigation item
- Main non-safety buttons
- Links
- Active tabs
- Progress indicators
- Selected controls
- Important informational icons

Do not fill the entire app with the primary color.

---

## Primary Variants

| Token | Hex | Usage |
|---|---|---|
| Primary 700 | `#586DAF` | Pressed states, strong text accents |
| Primary 600 | `#6F84C5` | Secondary primary actions |
| Primary 500 | `#869BDB` | Main brand color |
| Primary 300 | `#B7C3EA` | Borders, selected backgrounds |
| Primary 100 | `#E8ECF9` | Soft information background |
| Primary 50 | `#F4F6FC` | Very subtle tinted sections |

---

# 4. Neutral Colors

| Token | Hex | Usage |
|---|---|---|
| Background | `#F7F8FA` | Main application background |
| Surface | `#FFFFFF` | Cards, sheets, forms |
| Surface Secondary | `#F1F3F6` | Secondary containers |
| Border | `#E1E4E8` | Card and input borders |
| Divider | `#EAECF0` | Section separators |
| Text Primary | `#20242C` | Main text |
| Text Secondary | `#606873` | Supporting text |
| Text Muted | `#8A929D` | Less important metadata |
| Disabled | `#B8BEC7` | Disabled elements |

Avoid pure black for normal text.

---

# 5. Safety Semantic Colors

## Success / Safe

`#2E7D4F`

Use for:

- Training completed
- Assessment passed
- Valid certificate
- Attendance checked in
- Task completed
- Successful synchronization

Soft success background:

`#EAF5EE`

---

## Warning

`#D58B18`

Use for:

- Training due soon
- Certificate expiring
- Pending approval
- Sync waiting
- Important caution

Soft warning background:

`#FFF6E5`

---

## Danger

`#C63D3D`

Use for:

- SOS
- Failed assessment
- Invalid/expired certification
- Dangerous AR feedback
- Critical alerts
- Destructive actions

Soft danger background:

`#FCECEC`

Never use red for normal navigation or decorative UI.

---

## Information

Use primary color:

`#869BDB`

Soft information background:

`#E8ECF9`

---

# 6. Typography

Use a highly readable Android font.

Recommended:

**Inter** for English UI.

For Hindi and Santali-compatible localization, prefer system/Noto fonts where needed:

- Noto Sans
- Noto Sans Devanagari
- Appropriate supported Santali font depending on selected script

The app should prioritize language support over forcing a single decorative font.

---

## Type Scale

### Screen Title

- 24sp
- SemiBold
- Line height: 30sp

### Section Title

- 18sp
- SemiBold
- Line height: 24sp

### Card Title

- 16sp
- SemiBold

### Body

- 15–16sp
- Regular
- Line height: 22–24sp

### Supporting Text

- 14sp
- Regular

### Caption

- 12–13sp
- Medium

### Button

- 15–16sp
- SemiBold

Avoid text smaller than 12sp.

---

# 7. Spacing System

Use an 8-point spacing system.

Recommended values:

- 4dp — very small internal spacing
- 8dp — icon/text spacing
- 12dp — compact component padding
- 16dp — default spacing
- 20dp — card padding
- 24dp — section spacing
- 32dp — large section separation

Default screen horizontal padding:

`16dp`

Do not crowd screens.

---

# 8. Corner Radius

Keep corners slightly rounded, not overly pill-shaped.

| Component | Radius |
|---|---|
| Cards | 12dp |
| Buttons | 10dp |
| Inputs | 10dp |
| Chips | 8dp |
| Bottom sheets | 16dp top corners |
| Small status tags | 6dp |

Avoid 24–40dp radius cards across the whole UI.

---

# 9. Elevation and Shadows

Use borders before shadows.

Default card:

- White background
- `1dp` border using `#E1E4E8`
- No shadow

Use subtle elevation only when necessary:

- Dialog
- Bottom sheet
- Floating SOS button
- Sticky bottom action bar

Maximum normal elevation:

`4dp`

No glowing shadows.

---

# 10. Buttons

## Primary Button

Background:

`#869BDB`

Text:

`#FFFFFF`

Height:

`48–52dp`

Example:

`Start Training`

---

## Secondary Button

Background:

`#FFFFFF`

Border:

`1dp #869BDB`

Text:

`#586DAF`

Example:

`View Details`

---

## Tertiary Button

No container.

Text:

`#586DAF`

Example:

`View History`

---

## Danger Button

Background:

`#C63D3D`

Text:

White

Only use for genuine dangerous/emergency/destructive actions.

---

## Disabled Button

Background:

`#E2E5EA`

Text:

`#9299A3`

---

# 11. Status Components

Use simple badges.

### Completed

Background: `#EAF5EE`  
Text: `#2E7D4F`

`Completed`

### Pending

Background: `#FFF6E5`  
Text: `#A66A10`

`Pending`

### Failed / Expired

Background: `#FCECEC`  
Text: `#C63D3D`

`Expired`

### In Progress

Background: `#E8ECF9`  
Text: `#586DAF`

`In Progress`

Do not use highly saturated colored cards for statuses.

---

# 12. Icons

Use one icon family consistently.

Recommended:

**Material Symbols Rounded** or **Material Symbols Outlined**

Use icons around:

`22–24dp`

Important worker actions may use:

`28–32dp`

Do not mix icons from multiple styles.

Every important icon should have a visible text label.

---

# 13. Bottom Navigation

Use five main destinations:

1. Home
2. Training
3. Jobs
4. Activity
5. Profile

### Default

Icon: `#7B838D`  
Text: `#7B838D`

### Selected

Icon: `#586DAF`  
Text: `#586DAF`

Optional selected background:

`#E8ECF9`

Keep bottom navigation white with a top border.

Do not use a floating curved navigation bar.

---

# 14. Global SOS Access

SOS must remain easily reachable.

Recommended:

A red circular button positioned above the bottom navigation on relevant worker screens.

Color:

`#C63D3D`

Use:

SOS icon + `SOS`

The button should not be decorative.

Pressing it should open an emergency confirmation sheet before triggering an alert, unless the product specifically implements press-and-hold emergency activation.

---

# 15. Home Screen

## Header

Do not create a huge colored hero section.

Use a simple white/top surface.

Content:

- Greeting
- Worker name
- Employee/site information
- Notification icon
- Small profile photo

Example:

`Good morning, Ramesh`

`Bokaro Steel Plant • Shift A`

---

## Safety Status

Immediately after the header show a clear safety summary.

Example:

**Safety Status**

`3 / 4 Required Certifications Active`

If action is required:

`Confined Space training must be renewed in 3 days.`

Use warning background only for the warning row.

---

## Quick Actions

Use a 2-column grid.

Suggested actions:

- Training
- My Job
- Attendance
- Certificates

Card design:

- White
- Thin border
- 12dp radius
- Simple icon
- Title
- One small line of supporting information

Do not use different backgrounds for every card.

---

## Today's Job

Use a full-width structured card.

Show:

- Job title
- Worksite
- Shift
- Supervisor
- Required PPE
- Task count

Main action:

`View Job`

---

## Training Due

Only show if necessary.

Show:

- Module name
- Due date
- Progress
- Continue button

---

# 16. Training Screen

Top:

`Safety Training`

Secondary line:

`Complete required modules before working in high-risk zones.`

Tabs/filter:

- All
- Required
- Completed

Training card:

- Module icon/thumbnail
- Title
- Short description
- Duration
- Progress
- Status
- Action

Example:

**Fire & Explosion Response**

`12 min • Required`

`60% complete`

`Continue`

Avoid huge colorful thumbnails.

---

# 17. Training Detail Screen

Sections:

1. Module name
2. Why this training matters
3. What the worker will learn
4. Estimated duration
5. Required equipment
6. Download/offline status
7. Training progress

Sticky bottom button:

`Start AR Training`

If not downloaded:

`Download for Offline Use`

Show approximate file size.

---

# 18. AR Training UI

The AR screen should have the least amount of UI possible.

Camera occupies nearly the entire screen.

## Top Overlay

Show:

- Close/back
- Module name
- `Step 2 of 6`
- Small progress bar

Use a semi-opaque neutral surface only when required for readability.

No gradients over the camera.

---

## Instruction Panel

Use a solid or near-solid white bottom panel.

Example:

**Step 2**

`Point the camera toward the nearest emergency exit.`

Buttons:

- Hear instruction
- Hint

---

## AR Feedback

### Correct

Use a green outline/check.

`Correct`

### Incorrect

Use red only around the relevant element.

`Try again`

Then explain why.

Do not flash the whole screen red or green.

---

# 19. Assessment Screen

One question per screen.

Structure:

- `Question 4 of 10`
- Progress bar
- Question
- Optional image/diagram
- Answer choices
- Main action

Answer choices:

White background  
Thin gray border

Selected:

Primary border `#869BDB`  
Soft background `#F4F6FC`

Correct after submission:

Green

Wrong after submission:

Red

---

# 20. Assessment Result

Top:

Simple result status.

### Passed

Green check icon

`Assessment Passed`

`Score: 84%`

Show:

- Correct answers
- Weak topics
- Training completion
- Certificate status

Primary:

`View Certificate`

---

### Failed

`Assessment Not Passed`

Do not use humiliating language.

Show:

- Score
- Required score
- Topics to revise

Primary:

`Review Training`

Secondary:

`Retry Assessment`

---

# 21. Certificate Screen

Use a simple digital credential card.

Information:

- Worker name
- Certificate title
- Certificate ID
- Issue date
- Expiry date
- Status

Status should be prominent.

Primary action:

`Show QR Code`

Secondary:

`View Details`

QR screen should maximize QR size and reduce unrelated UI.

---

# 22. Jobs Screen

Tabs:

- Today
- Upcoming
- Completed

Job card:

- Job title
- Site
- Date and shift
- Supervisor
- Status
- Required certifications

If worker cannot start:

Show exactly why.

Example:

`Confined Space certificate required`

Action:

`Complete Required Training`

This connects training directly with job eligibility.

---

# 23. Job Detail Screen

Header:

Job title + status

Sections:

### Shift

- Time
- Site
- Supervisor

### Required Safety

Show checklist:

✓ PPE Safety  
✓ Fire Safety  
✕ Confined Space Training

### Today's Tasks

Simple checklist.

Example:

- Inspect ventilation unit
- Confirm gas sensor status
- Submit inspection photo

### Required PPE

Use icon + text.

Do not communicate PPE only through illustrations.

Sticky main action:

`Start Shift`

---

# 24. Attendance Screen

Top summary:

**Today's Attendance**

Status:

`Checked in at 08:04 AM`

Show:

- Shift start
- Check-in
- Check-out
- Working hours

Primary:

`Check Out`

Monthly history can use a simple calendar/list.

Use status dots carefully:

Green = present  
Amber = leave  
Red = absent  
Gray = non-working day

---

# 25. Activity Screen

This area contains worker records.

Sections:

- Attendance
- Leave
- Salary
- Tasks
- Training History

Use simple list rows rather than multiple dashboard cards.

---

# 26. Salary Screen

Salary information must be simple and private.

Top:

**August 2026**

**Net Pay**

`₹24,850`

Then structured rows:

- Base salary
- Overtime
- Incentives
- Deductions
- Net salary

Action:

`View Payslip`

Avoid finance-dashboard charts unless they provide real value.

---

# 27. Leave Screen

Top:

`Leave Balance`

Show simple values:

Casual Leave: `4`

Sick Leave: `3`

Then:

`Apply for Leave`

Leave history below using normal list rows.

Statuses:

- Pending
- Approved
- Rejected

---

# 28. Apply Leave Form

Fields:

- Leave type
- Start date
- End date
- Reason
- Optional attachment

Main button:

`Apply Leave`

Avoid multi-step forms unless truly needed.

---

# 29. Emergency UI

Emergency screen uses a clean white background.

Top:

**Emergency Assistance**

Primary large SOS action:

Red `#C63D3D`

Below show emergency types:

- Fire
- Gas Leak
- Injury
- Machinery Accident
- Other

Each should use text + recognizable icon.

Offline emergency guides should remain available.

Example:

`No internet? Emergency instructions are available offline.`

---

# 30. Offline UI

Offline functionality should feel intentional.

When offline show a slim banner:

`You are offline. Saved data will sync automatically.`

Color:

Soft warning / neutral.

Do not block the entire application.

Downloaded training:

`Available Offline`

Pending changes:

`Waiting to Sync`

Successful:

`Synced`

---

# 31. Sync States

Use clear language:

- `Saved on this device`
- `Waiting for internet`
- `Syncing`
- `Synced`
- `Sync failed — Retry`

Avoid technical messages such as:

`HTTP 503`

or:

`Network request failed`

---

# 32. Empty States

Keep empty states simple.

Example:

**No upcoming jobs**

`Your assigned jobs will appear here.`

Do not add giant decorative illustrations.

A small icon is enough.

---

# 33. Loading States

Use:

- Skeleton rows/cards
- Small circular progress indicators
- Inline loading indicators

Do not use full-screen animated loaders unless the application truly cannot continue.

---

# 34. Error States

Errors must explain what happened and what the worker can do.

Bad:

`Something went wrong.`

Better:

`We couldn't load your jobs. Your saved jobs are still available offline.`

Actions:

`Retry`

---

# 35. Forms

Every input must have a visible label.

Example:

**Employee ID**

`Enter employee ID`

Do not rely solely on placeholders.

Input height:

`52–56dp`

Provide validation directly below the field.

---

# 36. Language Selection

Language selection should be one of the first onboarding screens.

Title:

`Choose your language`

Options should display native names where appropriate.

Example:

- English
- हिन्दी
- Santali / selected script representation

The selected language should remain changeable from Profile.

---

# 37. Localization Rules

Layouts must support longer translated strings.

Never hard-code tiny button widths.

Prefer flexible containers.

Important safety terminology should be reviewed by domain/language experts rather than blindly machine translated.

Where useful, training may offer:

- Text instruction
- Audio instruction
- Visual instruction

---

# 38. Accessibility

Minimum touch target:

`48dp × 48dp`

Maintain strong text/background contrast.

Never use color as the only indicator.

Example:

Do not show only a green circle.

Show:

`✓ Valid`

Support Android font scaling where possible.

Use content descriptions for icons.

---

# 39. Motion

Motion should provide feedback, not entertainment.

Allowed:

- Small page transitions
- Progress changes
- Button press feedback
- Checklist completion
- AR instruction transitions
- Success confirmation

Duration:

Approximately `150–250ms`

Avoid:

- Bouncy cards
- Continuous floating animations
- Parallax
- Animated backgrounds
- Automatic carousels
- Large entrance animations

---

# 40. App Bar

Standard screen app bar:

- White background
- Back button when needed
- Screen title
- Optional relevant action

Bottom border:

`#EAECF0`

Avoid a different colored header on every page.

---

# 41. Cards

Standard card:

```text
Background: #FFFFFF
Border: 1dp #E1E4E8
Radius: 12dp
Padding: 16dp
Shadow: none
```

Cards should group related information, not be used for every single row.

---

# 42. Progress Bars

Track:

`#E7E9ED`

Fill:

`#869BDB`

Height:

`6–8dp`

For completed training, green may be used when appropriate.

Always show textual progress where important.

Example:

`3 of 5 modules completed`

---

# 43. Worker Profile Screen

Sections:

### Worker

- Name
- Employee ID
- Department
- Site
- Job role

### Safety

- Skill profile
- Active certificates
- Training history

### App

- Language
- Downloads
- Sync status
- Notifications

### Account

- Help
- Logout

Keep settings minimal.

---

# 44. Navigation Rules

Do not nest the app too deeply.

Ideal:

Home → Feature → Detail

Avoid:

Home → Category → Category → Menu → Detail → Sub-detail

Critical worker workflows should normally be reachable within 2–3 taps.

---

# 45. Recommended Home Information Hierarchy

From top to bottom:

1. Worker greeting / site
2. Important safety warning, only if present
3. Today's job / shift
4. Quick actions
5. Required or ongoing training
6. Attendance status
7. Recent updates

Do not make salary, leave, certificates, training, attendance, and jobs visually equal if some are much more urgent today.

---

# 46. Production Component Inventory

Create reusable components:

- `PrimaryButton`
- `SecondaryButton`
- `DangerButton`
- `AppTopBar`
- `BottomNavigation`
- `StatusBadge`
- `SectionHeader`
- `InformationCard`
- `TrainingCard`
- `JobCard`
- `CertificateCard`
- `TaskRow`
- `PPEItem`
- `ProgressBar`
- `OfflineBanner`
- `SyncIndicator`
- `AlertBanner`
- `FormField`
- `LanguageSelector`
- `EmptyState`
- `ConfirmationSheet`
- `SOSButton`

Use these consistently instead of creating a new visual style on every screen.

---

# 47. Do Not Do — Vibe-Coded UI Traps

Strictly avoid:

- Gradients
- Glass cards
- Blurred backgrounds
- Neon colors
- Purple-blue gradient buttons
- Giant dashboard numbers everywhere
- Excessive rounded rectangles
- Random colored cards
- Fake analytics charts
- Floating blobs
- Decorative mesh backgrounds
- 3D floating icons
- Text with gradient fills
- Glow effects
- Huge shadows
- Every section inside its own card
- Too many chips
- Too many badges
- Excessive animations
- Emoji as production icons
- Tiny text
- Icon-only navigation for important actions
- Huge hero sections
- Marketing-style landing page layouts inside the application

The app must feel like an operational tool used every day by industrial workers.

---

# 48. Visual Identity Summary

The application should visually communicate:

**Safe**

**Reliable**

**Simple**

**Industrial**

**Human**

**Professional**

The primary color `#869BDB` gives SurakshaSetu its identity, but safety-state colors must remain semantically stronger when required.

The final interface should feel closer to a polished enterprise Android application than a trendy SaaS landing page.

---

# 49. Final Color Tokens

```text
Primary              #869BDB
Primary Dark         #586DAF
Primary Medium       #6F84C5
Primary Light        #B7C3EA
Primary Surface      #E8ECF9
Primary Subtle       #F4F6FC

Background           #F7F8FA
Surface              #FFFFFF
Surface Secondary    #F1F3F6
Border               #E1E4E8
Divider              #EAECF0

Text Primary         #20242C
Text Secondary       #606873
Text Muted           #8A929D
Disabled             #B8BEC7

Success              #2E7D4F
Success Surface      #EAF5EE

Warning              #D58B18
Warning Surface      #FFF6E5

Danger               #C63D3D
Danger Surface       #FCECEC
```

---

# 50. Main Worker Navigation

```text
Home
Training
Jobs
Activity
Profile
```

Persistent/quick-access feature:

```text
SOS
```

Activity contains:

```text
Attendance
Leave
Salary
Tasks
Training History
```

This keeps the bottom navigation simple while still supporting the full workforce-management system.

---

# 51. Final Design Rule

Before adding any UI element, ask:

> Does this help the worker understand something, make a decision, or complete an action?

If the answer is no, remove it.
