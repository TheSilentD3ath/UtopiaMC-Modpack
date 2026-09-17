# Utopia Core — Architektur

Stand: 11.08.2026 · Minecraft 1.20.1 · Fabric · Java 17

RPG-Kern für Utopia: **Origin → Gender → Klasse** bei Weltstart, Skills, Attribute,
Loadouts und (später) ein Unlock-/Tech-Baum. Technisch ein **Hard-Fork von LevelZ**
(Globox_Z, GPL-3.0), kein Addon-Stapel.

---

## 1. Grundprinzipien

1. **Ein Mod, ein Datenmodell.** Origins, Gender, Klassen, Skills und Unlocks teilen
   sich Parser, Merge-Pfad, Persistenz und Sync. Keine drei fast identischen Systeme,
   die per Mixin aneinandergeklebt werden.
2. **Datengetrieben.** Inhalte sind Datapack-JSON, kein Java. Neue Origin = eine Datei
   + `/reload`. Nur Mechaniken sind Code.
3. **Ereignisgesteuert statt pro Tick.** Alles Abgeleitete (Attribute, Effekte, Boni)
   wird genau dann neu berechnet, wenn sich die Auswahl ändert. Es gibt exakt **einen**
   wiederkehrenden Hook (alle 40 Ticks, nur über die Spielerliste).
4. **Soft-Integration nach außen.** Fremde Mods werden über Vanilla-Registries
   (`Identifier` → Attribut/Effekt/Item) angesprochen, nicht über Compile-Dependencies.
   Kein Kotlin, kein cynosure, kein Zwang, dass ein Mod installiert ist.
5. **Server ist die Wahrheit.** Der Client bekommt nur eine Kopie zum Anzeigen; jede
   Auswahl wird serverseitig gegen die Requirements geprüft.

---

## 2. Warum Fork und nicht Addon

LevelZ 1.4.13 (letzter Commit 11.01.2024) ist faktisch aufgegeben, GPL-3.0, ~164
Java-Dateien, ~130 Mixins. Ein Addon müsste sich an nicht-öffentliche Interna hängen —
genau die lose Patch-Schicht, die vermieden werden soll.

**Umsetzung:**

| Punkt | Entscheidung |
|---|---|
| Mod-ID | `utopiacore` (levelz.jar fliegt aus dem Pack) |
| `provides` | `["levelz"]` — Mods mit Soft-Dependency auf LevelZ funktionieren weiter |
| Version | `1.4.13+utopia.0.1.0` — der provided Alias erbt die Version des Anbieters, deshalb muss der LevelZ-Stand vorne stehen (`jobsaddon` fordert `>=1.3.7`). Alles hinter `+` ist Build-Metadata und zählt beim Vergleich nicht |
| Geerbter Code | bleibt vorerst unter `net.levelz`, Namespace `levelz` für Datapacks/Netzwerk → **bestehende LevelZ-Datenpacks und Configs bleiben gültig** |
| Neuer Code | `dev.utopia.core.*`, Datapack-Namespace `utopia` |
| Lizenz | GPL-3.0 (Pflicht), Attribution in `LICENSE` + `fabric.mod.json` |

Migration von `net.levelz` nach `dev.utopia.core` erfolgt schrittweise pro Subsystem,
nicht als Big-Bang-Umbenennung.

---

## 3. Projektlayout

```
Utopia Core/
├─ build.gradle, gradle.properties        Loom 1.3, MC 1.20.1, Yarn, Java 17
├─ LICENSE                                GPL-3.0 (von LevelZ geerbt)
├─ vendor/                                LevelZ-README + Changelog (Attribution)
└─ src/main/
   ├─ java/net/levelz/…                   geerbter LevelZ-Code (Skills, Locks, Mixins)
   ├─ java/dev/utopia/core/
   │   ├─ UtopiaCore.java                 Entrypoint: Loader, Netzwerk, Commands, Events
   │   ├─ UtopiaEffects.java              Marker-Effekte feminine/masculine
   │   ├─ character/
   │   │   ├─ TraitType.java              ORIGIN | GENDER | CLASS
   │   │   ├─ CharacterTrait.java         JSON-Schema als Codec-Record
   │   │   ├─ CharacterTraits.java        Datapack-Loader + Registry
   │   │   ├─ CharacterData.java          Spielerzustand + NBT
   │   │   ├─ TraitBundle.java            Merge-Ergebnis (Cache)
   │   │   ├─ CharacterService.java       Anwenden, Validieren, Tick
   │   │   ├─ CharacterStateApplier.java  Attribute, Effekte, Trait-Unlocks
   │   │   ├─ CharacterStarter.java       einmalige Startwerte und Loadout
   │   │   └─ CharacterAccess.java        Mixin-Interface
   │   ├─ network/UtopiaNetworking.java
   │   ├─ integration/EstrogenBridge.java
   │   ├─ command/UtopiaCommand.java
   │   └─ client/…                        Sync-Empfänger + Auswahl-Screen
   └─ resources/data/utopia/utopia/{origins,genders,classes}/*.json
```

`CharacterService` ist die stabile Orchestrierungsfassade. Die konkreten
Seiteneffekte sind paketintern getrennt, damit Lifecycle, Auswahlregeln und
Spielerzustand nicht wieder in einer God-Class zusammenwachsen.

---

## 4. Datenmodell

### 4.1 Drei Achsen, ein Schema

**Origin** = Herkunft/Rasse (passive Traits, Attribute).
**Gender** = Körper/Identität (Estrogen-Kopplung, leichte Stat-Verschiebung).
**Klasse** = Rolle (Skills, Loadout, Unlocks).

Alle drei benutzen dasselbe JSON-Schema und werden über denselben Codec geparst.
Das ist der Kern der Optimierung: ein Parser, ein Merge, ein Sync-Format.

### 4.2 Trait-Schema

Pfad: `data/<namespace>/utopia/{origins|genders|classes}/<name>.json`
(bewusst `utopia/origins` und nicht `origins/`, um mit dem Origins-Mod nicht zu kollidieren)

```jsonc
{
  "name": "trait.utopia.origin.stoneborn",      // Übersetzungsschlüssel
  "description": "trait.utopia.origin.stoneborn.desc",
  "icon": "minecraft:deepslate",                 // Item-Id für den Screen
  "order": 2,                                    // Sortierung im Auswahl-Screen
  "selectable": true,                            // false = nur per Command/Grant

  "attributes": [                                // dauerhafte Attribut-Modifier
    { "attribute": "minecraft:generic.max_health", "operation": "addition", "value": 4.0 },
    { "attribute": "minecraft:generic.armor", "operation": "multiply_base", "value": 0.1 }
  ],

  "effects": [                                   // dauerhafte Statuseffekte
    { "effect": "utopiacore:masculine", "amplifier": 0, "show_icon": true, "show_particles": false }
  ],

  "skills": {
    "start_levels":      { "mining": 5, "defense": 2 },   // statt Level 0 starten
    "bonus_points":      3,                               // freie Punkte beim Start
    "xp_multiplier":     { "mining": 1.25 },              // schnellere XP
    "effect_multiplier": { "defense": 1.15 }              // stärkere Skill-Effekte
  },

  "loadout": {
    "clear_inventory": false,
    "items": [
      { "item": { "id": "minecraft:iron_pickaxe", "Count": 1 }, "slot": "mainhand" },
      { "item": { "id": "minecraft:bread", "Count": 8 } }   // slot default = inventory
    ]
  },

  "unlocks": ["utopia:tech/create_basics"],      // Startknoten im Tech-Baum

  "requires": {                                  // erlaubte Kombinationen, leer = alles
    "origins": [], "genders": [], "classes": [], "permission": null
  }
}
```

Slots: `mainhand`, `offhand`, `head`, `chest`, `legs`, `feet`, `inventory`.
Operationen: `addition`, `multiply_base`, `multiply_total`.

### 4.3 Merge-Regeln (`TraitBundle`)

Reihenfolge: **Origin → Gender → Klasse → gewährte Traits**.

| Feld | Zusammenführung |
|---|---|
| `attributes` | alle gesammelt; pro (Attribut, Operation) eine deterministische UUID → idempotent |
| `effects` | pro Effekt gewinnt der höchste `amplifier` |
| `start_levels` | **addiert** |
| `bonus_points` | addiert |
| `xp_multiplier`, `effect_multiplier` | **multipliziert** |
| `unlocks` | Vereinigung |
| `loadout` | konkateniert; `clear_inventory` ist ein ODER |

Nach dem Trait-Merge wird einmalig die gemeinsame Startversorgung angehängt.
Aktuell sind das sechs gereinigte Dehydration-Wassertränke
(`minecraft:potion` mit `Potion:"minecraft:purified_water"`). Dehydration 1.3.6
registriert diese Potion effektfrei; bei `potion_thirst_quench=2` deckt die
Versorgung 12 von maximal 20 internen Durstpunkten ab. Das Balanceziel sind
höchstens 6 verbrauchte Punkte beziehungsweise drei Flaschen pro Minecraft-Tag;
die Startversorgung ist daher als Vorrat für ungefähr zwei Tage gedacht. Sie
liegt in derselben maßgeblichen Loadout-Liste und verwendet denselben
Inventar-/Drop-Pfad wie Klassen- und Origin-Ausrüstung, ohne pro Auswahlachse
dupliziert zu werden.

`CharacterStarter` ergänzt anschließend genau einmal ein vollständiges
EnvironmentZ-Wandererset im Inventar, wenn das Biom am tatsächlichen
Charakterabschluss für die konkrete Blockposition als kalt gilt. Zusätzlich
wird `environmentz:comfort` für 9.600 Ticks gesetzt. Dieser Effekt ist der
unmittelbare Kälteschutz: EnvironmentZ 2.0.8 führt das Wandererset in
`non_affecting_armor`, weshalb es selbst angezogen keine Temperatur liefert.
Die bedingte Vergabe bleibt im vorhandenen Starter-Lifecycle und überschreibt
keine bereits ausgerüstete Klassenrüstung. Fehlt EnvironmentZ, werden Items und
Effekt über Registry-Lookups nur protokolliert beziehungsweise übersprungen.

Die optionale Datenintegration
`data/environmentz/tags/items/warm_armor.json` erweitert EnvironmentZs additives
Wärmerüstungs-Tag um `#thigh_highs_etc:thigh_highs`. Die Tag-Referenz ist optional;
Core erhält keine Java-Abhängigkeit auf EnvironmentZ oder Thigh Highs Etc.
EnvironmentZ wertet dabei ausschließlich tatsächlich ausgerüstete Rüstung aus;
Thigh Highs im Inventar liefern keine Wärme.

Das Bundle wird in `CharacterData` gecacht und nur bei `dirty` neu gebaut.

---

## 5. Ablauf

```
Erster Join
   └─ CharacterData leer  →  applyAll(force)  →  syncCharacter  →  openCreation
                                                                      │
Client: Screen Origin → Gender → Klasse → Bestätigen ─────────────────┘
   └─ EIN C2S-Paket (3 Identifier)
        └─ Server: isValidSelection()  ──ungültig──►  Screen erneut öffnen
                       │gültig
                       ├─ data.select(...)                 (dirty)
                       ├─ applyAll()   Attribute + Effekte + Estrogen-Bridge
                       ├─ applyStart() Start-Level, Bonuspunkte, Loadout   ← genau einmal
                       └─ syncCharacter()
```

- **Persistenz:** NBT-Compound `Utopia` am Spieler (`Origin`, `Gender`, `Class`,
  `Complete`, `Granted[]`, `Unlocks[]`). Geschrieben in denselben Injections, die
  LevelZ schon nutzt — keine zusätzliche Mixin-Injection.
- **Tod/Dimensionswechsel:** `ServerPlayerEntity.copyFrom` kopiert die Daten mit.
- **Solange unfertig:** Der 40-Tick-Hook öffnet den Screen erneut. Der Screen ist
  ESC-fest (`shouldCloseOnEsc = false`).
- **Umwahl** nur über `/utopia character set|reset` (OP), nie über das normale Paket.

---

## 6. Gender × Estrogen

### 6.1 Befund aus `estrogen-5.0.8+1.20.1-fabric.jar`

| Registry-Eintrag | Wirkung |
|---|---|
| `estrogen:estrogen` (Effekt "Girl Power") | Dash + Körper-Feature |
| `estrogen:show_boobs` (Attribut) | schaltet das Chest-Rendering |
| `estrogen:boob_initial_size` (Attribut) | Startgröße |
| `estrogen:boob_growing_start_time` (Attribut) | Wachstumsbeginn |
| `estrogen:dash_level` (Attribut) | Dash-Stufe |
| `estrogen:fall_damage_resistance` (Attribut) | Fallschaden |

**Wichtig:** Das Chest-Feature hängt am **Attribut**, nicht am Effekt (Rendering läuft
über `client.PlayerModelMixin` + eine pro Spieler synchronisierte `ChestConfig`).
Deshalb muss **nichts aus Estrogen kopiert werden** — wir setzen `estrogen:show_boobs`
direkt und können Chest und Dash unabhängig voneinander vergeben. Das spart uns eine
Kotlin-Abhängigkeit (cynosure, kittyconfig, fabric-language-kotlin) und einen kompletten
Renderer-Nachbau.

### 6.2 Rollen

| Auswahl | Start | Weg |
|---|---|---|
| **Mann** | `utopiacore:masculine` (Marker) + Attribute, kein Dash | — |
| **Cis-Frau** | `estrogen:estrogen` dauerhaft (Dash + Chest) + `utopiacore:feminine` | — |
| **Trans-Girl** | nur `utopiacore:feminine`, kleine Alchemy-Boni, `bonus_points: 2` | spielt den Estrogen-Weg (Liquid Estrogen → Pillen/Patches). Sobald der Effekt das erste Mal anliegt, wird das Trait-Bündel `utopia:female` **dauerhaft gewährt** — ab da bleibt Girl Power ohne Patches bestehen |

Der Mechanismus dahinter ist generisch: `CharacterData.grantedTraits` kann jedes
Trait-Bündel nachträglich aufnehmen. Damit lassen sich später Segen, Flüche,
Klassen-Aufstiege oder ein Transmasc-Pfad ohne neuen Code abbilden.

Ist Estrogen nicht geladen, macht die Bridge nichts — `utopiacore:feminine` bleibt als
eigener Effekt bestehen, das Pack läuft weiter.

---

## 7. Skills

### 7.1 Ist-Zustand (geerbt)

`net.levelz.stats.Skill` ist ein **Enum** mit 12 festen Skills (health, strength,
agility, defense, stamina, luck, archery, trade, smithing, mining, farming, alchemy).
Effektstärken liegen in einer Cloth-Config, Sperrlisten in Datapack-JSON
(`data/levelz/block|item|brewing|smithing/*.json`).

Die Vanilla-Grundstufe ist bewusst frei: Axt, Hacke, Schwert und die generische
Werkzeuggruppe für Holz und Stein stehen in `data/levelz/item/` auf Level 0.
Benutzung, Abbau und Angriff lesen diese gemeinsame Item-Progression. Für ihre
Rezepte existiert keine zusätzliche Datei unter `data/levelz/crafting/`, daher
sind sie ebenfalls ab Spielbeginn herstellbar. Bogen, Eimer, Leder/Kette und
höhere Materialstufen bleiben getrennte, unveränderte Anforderungen. Der
Gradle-Check `verifyStarterProgression` schützt diese Abgrenzung.

### 7.2 Umgesetzt: dynamische Skill-Registry

`Skill` ist kein Enum mehr, sondern eine Registry mit Enum-Oberfläche
(`values()`, `valueOf()`, `name()`) — dadurch laufen die ~20 geerbten Dateien und
alle Mixins unverändert weiter. Die zwölf Ursprungs-Fähigkeiten stehen mit
identischer Reihenfolge, identischen NBT-Schlüsseln und identischen Config-Werten
im Code; bestehende Spielstände merken vom Umbau nichts.

Mitgeändert:

- Die vier `switch (skill)`-Blöcke in den Netzwerkklassen (Attribut setzen,
  Sperrlisten neu rechnen) sind weg — das Verhalten hängt jetzt an der Fähigkeit.
- `PlayerStatsManager` speichert `int[]` statt `Map<Skill,Integer>`; NBT bleibt
  namensbasiert.
- Der Fähigkeitenbildschirm rechnet Spalten und Zeilenabstand aus der Anzahl.
  Bei zwölf kommt exakt das alte Raster heraus, ab dreizehn rücken die Zeilen
  zusammen, statt aus dem Panel zu laufen.
- `/playerstats` baut seinen Befehlsbaum aus der Registry statt aus 64
  handgeschriebenen Literalen.

**Woher neue Fähigkeiten kommen:** aus `data/utopia/skills/*.json` **im Jar**,
geladen beim Mod-Start — nicht über das Datapack-System. Grund: Index und
Reihenfolge müssen auf Server und Client gleich sein, sonst landen Level beim
Synchronisieren in der falschen Fähigkeit. Gleiche Jar-Datei heißt gleiche Liste,
damit braucht es kein Sync-Paket und kein Nachladen. Nach dem Laden wird die
Registry eingefroren.

```json
{ "name": "TECH", "nbt": "TechLevel", "order": 100, "icon_index": 12 }
```

`icon_index` ist die Spalte in `assets/levelz/textures/gui/icons.png` (Reihe v=16).

**Was Tech schon kann:** LevelZ' Sperrlisten referenzieren Fähigkeiten als String,
also gilt ab sofort auch `{"skill": "tech", "level": 5, "block": "create:..."}` in
einem Datapack. Das ist die Level-Schranke. Was noch fehlt, ist der *gekaufte*
Knoten aus Abschnitt 8.

### 7.3 Ursprünglicher Plan (Referenz)

Das Enum ist der eigentliche Blocker für „Tech-Skill" und klassenspezifische Skills.
Geplanter Umbau — **das ist der nächste größere Schritt, bevor Inhalte wachsen**:

- `data/<ns>/utopia/skills/<name>.json`: Anzeigename, Icon, Farbe, Max-Level,
  XP-Kurve, Attribut-Effekte pro Level, Gate-Verhalten.
- Zur Laufzeit eine Registry `Identifier → SkillDefinition` mit stabilem Int-Index
  (Reihenfolge aus sortierten Ids), damit Netzwerk und Arrays index-basiert bleiben.
- `PlayerStatsManager` speichert `int[]` statt `Map<Skill,Integer>`; NBT weiterhin
  namensbasiert (migrationsfest gegen Reihenfolgeänderungen).
- Kompatibilitäts-Shim: `Skill.MINING` etc. bleiben als Konstanten erhalten, damit die
  ~130 geerbten Mixins nicht am selben Tag umgeschrieben werden müssen.

Die JSON-Felder in Abschnitt 4.2 sind bereits darauf ausgelegt: Skills werden als
Strings referenziert (`"mining"` oder `"utopia:mining"` — beides wird normalisiert),
nicht als Enum-Konstanten. **Heute unbekannte Skill-Namen erzeugen nur eine Warnung im
Log, keinen Fehler.**

---

## 8. Freischalt-Bäume

Das zentrale Fortschrittssystem des Packs. **Nicht** eine Fähigkeit namens
Technik — das war ein Missverständnis und ist zurückgebaut.

### 8.1 Die Idee

Dein **Gesamtlevel** steigt durch Erfahrung (LevelZ, läuft bereits). Jeder
Aufstieg gibt neben dem LevelZ-Skillpunkt einen **Freischaltpunkt**. Den steckst
du in einen von mehreren **Bäumen** — Create, Winery, Alchemie, was das Pack
sonst noch hergibt. Jeder gekaufte Knoten öffnet eine Handvoll Blöcke und
Rezepte dieses Mods.

Was nicht freigeschaltet ist, lässt sich **nicht bauen und nicht benutzen**,
bleibt aber **sichtbar** — mit Hinweis, was fehlt. Spieler sollen sehen, worauf
sie hinarbeiten; ein Rezept, das scheinbar nicht existiert, erzeugt nur Ratlosigkeit.

### 8.2 Zwei getrennte Punkttöpfe

| Topf | Quelle | Ausgabe für |
|---|---|---|
| Skillpunkte | 1 pro Gesamtlevel (LevelZ) | die zwölf Fähigkeiten |
| Freischaltpunkte | konfigurierbar pro Gesamtlevel | Knoten in den Bäumen |

Bewusst getrennt: sonst konkurriert „mehr Leben" mit „Create geht weiter", und
bei einem Punkt pro Level wäre beides gleichzeitig unspielbar knapp.

### 8.3 Baum-Schema

Ein Baum ist **eine** Datei — das hält Knoten, Kosten und Positionen für die
Oberfläche beisammen:
`data/<namespace>/utopia/trees/<name>.json`

```jsonc
{
  "name": "tree.utopia.create",
  "description": "Optionaler Text oder Uebersetzungsschluessel",
  "icon": "create:cogwheel",
  "order": 0,
  "nodes": {
    "basics": {
      "description": "Optionaler Knotentext",
      "icon": "create:shaft",
      "cost": 1,              // Freischaltpunkte
      "level": 0,             // Mindest-Gesamtlevel
      "parents": [],          // erst kaufbar, wenn diese Knoten stehen
      "position": [0.0, 0.0], // freie Canvas-Koordinaten
      "unlocks": [            // Blöcke und Items, einzeln oder als Tag
        "create:shaft", "create:cogwheel", "#create:seats"
      ]
    },
    "kinetics": {
      "cost": 2, "level": 5, "parents": ["basics"], "position": [1, 0],
      "unlocks": ["create:water_wheel", "create:windmill_bearing"]
    }
  }
}
```

Optionale Addon-Zweige verwenden `"requires_mods": ["mod_id"]`. Fehlt eine
dieser Fabric-Mod-IDs, wird der Knoten vor Indexierung und Netzwerksync entfernt;
Kinder mit dadurch fehlendem Elternknoten werden ebenfalls entfernt. Neben
einzelnen IDs und Tags akzeptiert `unlocks` auch `namespace:*`, um sämtliche
registrierten Items und Blöcke eines optionalen Addons abzudecken, ohne dessen
JAR als Compile-Abhängigkeit einzubinden. Mit `"excludes": ["mod:id"]` bleiben
einzelne Creative- oder reine Deko-Inhalte eines solchen Namespace-Wildcards
bewusst frei. Eine explizite `unlocks`-Zuordnung hat weiterhin Vorrang vor
Wildcard und Ausschluss.

Mit `"legacy_owners": ["create/alter_knoten"]` kann ein neu aufgeteilter
Knoten außerdem von Freischaltungen aus älteren Baumfassungen als bereits
besessen gelten. Das verändert den Kaufpfad für neue Charaktere nicht, hält
aber bestehende Spielstände nach fachlichen Aufteilungen zugriffsberechtigt.
Die Besitzprüfung dafür liegt zentral in `UnlockService.owns(...)` und wird von
Sperren, Kaufprüfung, Elternlinien und Befehlsausgabe gemeinsam verwendet.

Der Create-Baum folgt der tatsächlichen Herstellungsprogression. Seine
Hauptachse lautet Andesit-Kinetik → mechanische Verarbeitung → Blaze-Heizung →
Messing → Deployer → Präzisionsmechanismus. Mechanisches Crafting,
Crushing-Wheels, Sturdy Sheets, Schienen, Verpackung/Stock-Netz und Elektronik
sind eigene Seitenäste mit den Maschinen als Eltern, die ihre installierten
Rezepte tatsächlich verlangen. Funktionale Farbvarianten wie Segel, Sitze,
Toolboxes, Ventilgriffe, Nixie Tubes, Postboxes und Table Cloths gehören zum
jeweiligen Technik-Knoten; sie erzeugen keine kosmetischen Einzelknoten.

Optionale technische Addons verzweigen an der fachlich passenden Stufe. Reine
Architektur-, Dekorations- und Struktur-Addons gehören nicht in den Technikbaum.
`tools/audit_create_tree_recipes.py` liest dafür die Rezepte aus den wirklich
installierten JARs, löst Item- und Maschinenabhängigkeiten auf und kann mit
`--strict` fehlende transitive Elternpfade als Fehler melden.

Die Oberfläche verwendet `position` als freie Gleitkomma-Koordinaten. Der
Spielermodus zeichnet niemals ein Raster. Im Überblick bildet ausschließlich
die erste Elternkante jedes Knotens den lesbaren Baum; weitere fachliche
Voraussetzungen bleiben vollständig erhalten und erscheinen als gestrichelte
Linien, sobald der betroffene Knoten oder sein Elternknoten fokussiert ist.
Hauptäste sind gerade und kreuzungsfrei angeordnet. Sie werden als dunkle
Kontur mit gestricheltem Messing- oder Statuskern vor den Knoten gezeichnet;
nur die Statusringe bleiben GPU-gebatcht. Dadurch liegen Kanten, Knoten und
Beschriftungen garantiert in einer stabilen GUI-Zeichenreihenfolge. Der
Holzgrund verwendet denselben Pan- und Zoom-Bezug wie der Baum, statt als
festes Bildschirmbild stehenzubleiben.

Der Screen begrenzt seine Arbeitsfläche auf 760 × 660 GUI-Pixel und zentriert
sie auf großen Auflösungen, damit die freigegebenen Mockup-Proportionen nicht
durch Ultrawide-Streckung verloren gehen. Außenrahmen, Kopfzeile, Sidebar,
Canvas, Werkzeugleiste und Detailinspektor besitzen eigenständige, sichtbar
verschachtelte Rahmen; zwischen Sidebar und Canvas liegt ein heller Holzsteg
mit dunklem Schatten. `Zentrieren` berechnet die sichtbaren Modellgrenzen und
passt den vollständigen Baum mit Rand in den Canvas ein. Manuelles Zoomen
reicht von 15 bis 175 Prozent und bleibt am Mauszeiger verankert. Der visuelle
Layoutmaßstab beträgt 64 Pixel pro Modelleinheit, damit nahe gespeicherte
Knoten auch bei mittleren Zoomstufen klar getrennt bleiben. Knotennamen sind ab
30 Prozent dauerhaft sichtbar; in der Gesamtübersicht darunter erscheinen sie
bei Hover oder Auswahl. Ein schwebender doppelt gerahmter Detailinspektor zeigt bei
Auswahl Namen, Voraussetzungen, Kosten, Level und **alle** Einträge des
Knotens; dadurch ist das repräsentative runde Icon nicht die einzige Erklärung
und verdeckt im Normalzustand keine feste rechte Bildschirmspalte. Tabs und
Werkzeugknöpfe verwenden eigene, unverzerrte Rahmenflächen und keine auf
Knopfgröße gestauchte Paneltextur.

Item-Renderer schreiben im GUI einen eigenen Tiefenwert. Deshalb wird der
Detailinspektor samt Texten, Unlock-Items und Kaufknopf auf einer expliziten
Vordergrundebene gezeichnet; Kontextmenüs liegen nochmals darüber. Der
Scroll-Scissor beginnt vier Pixel oberhalb der Textgrundlinie, weil jedes
16-Pixel-Item dort bereits beginnt. Beim Pannen wird `panX/panY` genau einmal
auf gemeinsame Renderpixel gerundet. Hintergrund, Kanten, Halos, Node-Rahmen
und Icons verwenden denselben Wert, sodass keine Komponente relativ zu den
anderen um einen Pixel driftet. Gestrichelte Kanten bestehen aus fest am
Elternknoten verankerten 8-Pixel-Strichen in einem 13-Pixel-Raster; ihre Phase
wird nicht bei jeder Längenänderung über die gesamte Kante neu verteilt.

OPs erhalten im selben Screen einen Entwurfsmodus. Nur dort ist ein dezentes
Fangraster sichtbar. Auswahl und Knotenbewegung sind getrennte Werkzeuge: Im
Auswahlmodus verschiebt ein Drag auch über einem Knoten ausschließlich die
Arbeitsfläche; nur das ausdrücklich aktivierte Bewegungswerkzeug verändert nach
einer Drag-Schwelle Knotenkoordinaten. Rechtsklick öffnet kontextabhängige
Aktionen zum Anlegen, Bearbeiten, Duplizieren und sicheren Löschen. `Strg` plus
Pfeiltaste verschiebt den gewählten Knoten um 0,5 Einheiten, zusätzliches
`Shift` um 0,1. Elternlisten stellen die Kanten her; Baum-Metadaten sind
ebenfalls editierbar. Speichern wird auf dem Server
erneut validiert (IDs, Eltern, Zyklen, Positionen und Mengenbegrenzungen),
danach atomar unter
`config/utopiacore/trees/<namespace>/<name>.json` abgelegt und an alle Spieler
synchronisiert. Datapack-Bäume bleiben der Fallback. Vorhandene Editor-Dateien
erhalten vor dem Überschreiben eine `.bak`-Sicherung. Nicht installierte
optionale Zweige und ihre ausgeblendeten Kinder werden beim Speichern erhalten,
auch wenn der bearbeitende Client sie nicht sehen konnte. Spielerfortschritt
bleibt unverändert in den Charakterdaten und wird nie in diese Konfiguration
geschrieben.

Knoten-Id ist `<baum>/<knoten>`, also `create/basics` — dieselbe Form, die schon
in `unlocks` der Traits steht.

### 8.4 Wie gesperrt wird

Der Fork erbt von LevelZ eine vollständige Sperrschicht: Crafting-Verbot,
Benutzungs-Verbot, Tooltips, Ausgrauen in REI und EMI. Die hängt an zwei
Entscheidungsstellen:

- `PlayerStatsManager.playerLevelisHighEnough(...)` — darf der Spieler das
  benutzen
- `PlayerStatsManager.listContainsItemOrBlock(..., 4)` plus die pro Spieler
  synchronisierte `lockedCraftingItemIds` — darf der Spieler das bauen

**Wir bauen keine zweite Sperrschicht daneben.** Beide Stellen fragen zusätzlich
die Baum-Registry: gehört diese Id zu einem Knoten, entscheidet der Knoten statt
eines Fähigkeitslevels. Damit wirken Freischaltungen sofort überall dort, wo
LevelZ heute schon sperrt — ohne ein Dutzend neuer Mixins.

Nachschlagen ist eine Map `Id -> Knoten` plus ein Set gekaufter Knoten am
Spieler. Beides wird bei Datapack-Reload und Kauf neu gebaut, nie pro Tick.

### 8.5 Einfluss von Origin, Klasse und Gender

Alle vier Wege sind im Trait-Schema vorgesehen:

```jsonc
"unlocks": ["create/basics"],          // Startknoten geschenkt
"unlock": {
  "points_per_level": 1,               // zusätzliche Punkte pro Aufstieg
  "discount": { "create": 1 },         // Knoten dieses Baums kosten 1 weniger
  "level_offset": { "create": -3 }     // Levelanforderung sinkt um 3
}
```

Der Ingenieur startet also mit den ersten Create-Knoten, zahlt dort weniger und
kommt früher heran — ohne dass es eine Mechanik gäbe, die nur ihm gehört.

### 8.6 Reihenfolge der Umsetzung

1. Daten, Punkte, Kauf per Befehl, Sperrwirkung — testbar ohne Oberfläche
2. Eigene Oberfläche mit Baum-Reitern und Knoten
3. Inhalte: die tatsächlichen Bäume für Create, Winery und weitere


## 9. Netzwerk & Performance

| Paket | Richtung | Wann |
|---|---|---|
| `utopiacore:sync_traits` | S2C | Join und `/reload` — alle Trait-Definitionen als Codec-Blob |
| `utopiacore:sync_character` | S2C | nur bei Änderung |
| `utopiacore:open_creation` | S2C | Join ohne Charakter, alle 40 Ticks bis erledigt |
| `utopiacore:select` | C2S | genau einmal, enthält alle drei Auswahlen |

**Harte Regeln für alles Weitere:**

1. Kein Zustand, der pro Tick neu berechnet wird. Cache + `dirty`-Flag.
2. Keine Streams, keine Boxing-Maps in Hot Paths (Kampf, Block-Break, Craft).
3. Attribut-Modifier immer mit deterministischer UUID setzen → idempotent, kein Stapeln.
4. Integrationen ausschließlich über Registry-Lookups; Mixins für fremde Mods laufen
   über den Mixin-Plugin-Filter (existiert bereits: `LevelzMixinPlugin`).
5. Effekte mit „unendlicher" Dauer (`Integer.MAX_VALUE`) einmal setzen, nicht nachtakten.

---

## 10. Commands

```
/utopia character get <spieler>
/utopia character set <spieler> <origin> <gender> <klasse>
/utopia character reset <spieler>          → öffnet die Auswahl erneut
/utopia points add <spieler> <anzahl>
/utopia handbuch                            → deutsches Utopia-Handbuch
/utopia handbook                            → englisches Utopia-Handbuch
```
`handbuch`, `handbook` und die eigene `character get`-Abfrage sind öffentlich;
verwaltende Unterbefehle bleiben auf Permission-Level 2. Die Handbuchbefehle
prüfen Lavender und das separat ausgelieferte `utopia_handbook`-Datapack zur
Laufzeit. Fehlt eines davon, bleibt Core geladen und meldet die fehlende
Voraussetzung. `/playerstats …` aus dem LevelZ-Erbe bleibt unverändert.

---

## 11. Roadmap

**Als Nächstes**

1. Dynamische Skill-Registry (Abschnitt 7.2) — Voraussetzung für den Tech-Skill.
2. Auswahl-Screen aufhübschen: Icons, Beschreibungstext, Scroll-Liste, 3D-Vorschau.
3. `libz` inlinen (6 Verwendungsstellen: Tabs, Config-Sync) → eine Abhängigkeit weniger.

**Danach**

4. Unlock-/Tech-Baum inklusive Create-Rezeptfilter (Abschnitt 8).
5. Rollen-Feintuning: Skills und Loadouts pro Klasse × Gender ausbalancieren.
6. Anbindung an Heracles (Klassen-Questlinien) und ans Handbuch (Lavender).
7. Migration `net.levelz` → `dev.utopia.core`, Subsystem für Subsystem.

**Bewusst offen**

- Transmasc-Pfad: es gibt kein Estrogen-Gegenstück. Entweder eigener Effekt + eigene
  Items oder rein statistisch — Designentscheidung, kein technisches Problem.
- Nicht-binäre Optionen: schematisch kein Aufwand (eine weitere Gender-JSON), aber
  eine Balance-Frage.

---

## 12. Lizenz

Abgeleitet von **LevelZ** © Globox_Z, **GPL-3.0**. Utopia Core steht damit ebenfalls
unter GPL-3.0; der Quellcode muss bei Weitergabe des Packs verfügbar sein.
`LICENSE` und `vendor/` enthalten die Original-Attribution.
