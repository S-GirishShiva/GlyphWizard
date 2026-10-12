# GlyphWizard — App Flow (v1.1)

Updated 2026-10-12 after UI36–UI38. Solid arrows: push with parallax. Dotted arrows: sheet or overlay (blur-through).

```mermaid
flowchart LR
  BOOT(["startup: dot → ripple → logo"]) --> FIRST{"first run?"}
  FIRST -- "yes" --> WEL["welcome"] --> GCHK["glyph check<br/>checkerboard + device"] --> HOME
  FIRST -- "no" --> HOME
  HOME["home landing<br/>logo · count widget · continue widget<br/>triggers · pause all · activity"]
  HOME --> LIB["library grid<br/>scroll down on home"]
  HOME -. "search" .-> SRCH["search"]
  HOME -. "new" .-> NEW["new sheet"]
  LIB -. "long-press" .-> MENU["item menu<br/>rename · duplicate · assign · play · export · delete"]
  NEW --> ED
  NEW --> IMP["image / GIF import"] --> ED
  NEW --> TXT["scrolling text"] --> ED
  NEW -. "import file" .-> PICK["system file picker"] --> ED
  HOME -- "continue widget" --> ED
  LIB --> ED
  ED["editor · frames view<br/>layers button beside title<br/>preview · ✦ · play on glyph"]
  ED -- "layers button" --> TL["timeline · layer cards"]
  TL -- "layers button" --> ED
  TL -. "tap layer" .-> TLF["focused layer<br/>covers other layers"]
  TLF -. "⌄ minimise" .-> TL
  ED -. "tool group" .-> SUB["sub-tools replace strip"]
  ED -. "✦ (neutral: no chat)" .-> PROMPT["prompt over buttons"]
  PROMPT -. "send" .-> CARD["reply card above prompt<br/>undo · full chat"]
  CARD -. "full chat" .-> SHEETA["agent sheet<br/>manual/agent toggle visible"]
  SHEETA -. "swipe down / toggle manual" .-> EDRED["editor · ✦ red<br/>chat active, closed"]
  EDRED -. "✦" .-> SHEETA
  CARD -. "× collapse" .-> EDRED
  ED -- "play on glyph" --> RIT["3·2·1 · flip · live mirror"]
  ED -. "flash FAIL" .-> FW["flash warning"]
  HOME -- "triggers" --> TR["triggers list<br/>people · apps · phone"]
  MENU -- "assign" --> WIZ
  TR -- "new trigger" --> PERM{"notification access?"}
  PERM -- "missing" --> NA["access sheet"] --> RS["restricted-settings walkthrough"] --> WIZ
  PERM -- "granted" --> WIZ["wizard 1–4<br/>what · who · only if · play"] --> TV
  TR --> TV["trigger view<br/>sentence ⇄ flow"] -- "edit" --> TE["edit: sections"]
  TV -. "test now" .-> RIT
  HOME -- "gear" --> SET["settings"]
  SET --> SAPP["appearance and editor"]
  SET --> SAG["agent: endpoints · keys · caps"] --> ADD["add endpoint → compatibility check"]
  SET --> SGL["glyph · triggers · haptics"]
  SET --> SDATA["data and backup · privacy · about"]
```

**Changes since v1 (approved 35-flow-map):**
- Search is now an explicit path.
- The layers button toggles between the frames view and the timeline view.
- The timeline has a focused-layer state.
- The agent has explicit states: no chat (✦ neutral) → open (sheet + toggle) → active but closed (✦ red).
