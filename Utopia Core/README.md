# Utopia Core

RPG-Kern für das Utopia-Modpack (Fabric 1.20.1): Origin → Gender → Klasse bei Weltstart,
Skills, Attribute, Loadouts, Unlocks.

Hard-Fork von [LevelZ](https://github.com/Globox1997/LevelZ) (Globox_Z), **GPL-3.0**.
Ersetzt `levelz.jar` im Pack und meldet sich per `provides: ["levelz"]` als LevelZ,
damit Mods mit Soft-Dependency weiterlaufen.

Architektur, Datenschema und Roadmap: **[ARCHITEKTUR.md](ARCHITEKTUR.md)**

## Versionsschema

`1.4.13+utopia.0.1.0` — vorne der LevelZ-Stand, hinter dem `+` die Utopia-Version.

Das ist kein Kosmetikding: Fabric gibt einem per `provides` bereitgestellten Mod die
Version des Anbieters. Stünde hier `0.1.0`, würde `jobsaddon` (fordert `levelz >=1.3.7`)
den Start abbrechen. Alles hinter `+` ist Build-Metadata und wird beim Vergleich
ignoriert, für Fabric ist der Mod also LevelZ 1.4.13.

**Der LevelZ-Teil vorne muss beim Hochzählen bleiben oder steigen.**

Die hintere Nummer steigt bei **jedem** Bau, sonst liegen im Mods-Ordner mehrere
Jars mit demselben Namen und niemand weiß, welcher Stand läuft. Beim Einspielen die
alte Datei löschen — zwei Utopia-Core-Jars nebeneinander lässt Fabric nicht starten.
Was in welcher Version steckt: [CHANGELOG.md](CHANGELOG.md).

## Bauen

Braucht **JDK 17** (nicht neuer, nicht älter).

```bash
./gradlew build          # Ergebnis: build/libs/utopiacore-<version>.jar
./gradlew runClient      # Testclient
```

## Stand

| | |
|---|---|
| Kompiliert | ja (`build/libs/utopiacore-0.1.0.jar`) |
| Im Spiel getestet | Auswahl-Flow und Levelsystem laufen (Shiro, 13.08.) |
| Fertig | Trait-Schema, Datapack-Loader, Auswahl-Flow, Persistenz, Sync, Estrogen-Bridge, Commands, Effekt-Icons |
| Offen | dynamische Skill-Registry (Tech-Skill), Tech-Baum, `libz` inlinen |

### Auswahl-Screen

Aufbau an Origins angelehnt: undurchsichtiger Hintergrund (Welt ist nicht sichtbar),
zentrales Panel mit Titelleiste, Item-Icon und Seitenzähler, darunter Beschreibung und
eine aus den Trait-Daten generierte Wirkungsliste (Attribute, Startlevel, Multiplikatoren,
Effekte, Ausrüstung). Geblättert wird mit `<` / `>`, Mausrad oder Pfeiltasten, bestätigt
mit Enter.

Der Screen öffnet erst, wenn die Welt geladen ist (vorher verdrängt ihn der
Ladebildschirm), lässt sich nicht mit ESC schließen, und der Spieler ist bis zum
Abschluss unverwundbar.

## Inhalte

10 Klassen (Warrior, Hunter, Tank, Miner, Smith, Wizard, Farmer, Trader, Engineer,
Survivor) und 5 Origins (Neko, Melon Citizen, Puppy, Sea Citizen, Villager) nach
Justins Balance-Vorschlag.

Eigene Attribute für Wirkungen ohne Vanilla-Gegenstück:

| Attribut | Wirkung | angewendet in |
|---|---|---|
| `utopiacore:fire_resistance` | Anteil weniger Feuerschaden | `LivingDamageMixin` |
| `utopiacore:fall_resistance` | Anteil weniger Fallschaden | `LivingDamageMixin` |
| `utopiacore:regeneration_speed` | Faktor auf natürliche Regeneration | `HungerManagerMixin` |
| `utopiacore:breath_capacity` | Faktor auf den Luftvorrat | `LivingBreathMixin` |

**Finger weg von Bewegungsattributen fremder Mods.** `water_speed` aus
`additionalentityattributes` greift in `travel()` ein — der Client sagt Bewegung
voraus, der Server rechnet nach, und beides driftet auseinander. Boni, die nur
Zahlen oder Anzeige betreffen (Luft, Sicht, Schaden), sind unkritisch.

## Inhalte hinzufügen

Kein Java nötig — eine JSON-Datei plus `/reload`:

```
data/<namespace>/utopia/origins/<name>.json
data/<namespace>/utopia/genders/<name>.json
data/<namespace>/utopia/classes/<name>.json
```

Schema und alle Felder: ARCHITEKTUR.md, Abschnitt 4.2.
Beispiele liegen unter `src/main/resources/data/utopia/utopia/`.

## XP-Boni der Klassen

LevelZ kennt **keine Erfahrung pro Fähigkeit** — es gibt einen Topf fürs
Gesamtlevel, aus dem freie Punkte kommen. `xp_multiplier` heißt deshalb:
*Erfahrung aus dieser Art Arbeit zählt für dich mehr.*

| Tätigkeit | zählt als |
|---|---|
| Erz abbauen | `mining` |
| Mob töten | `strength`, mit Fernkampfwaffe in der Hand `archery` |
| Schmelzen | `smithing` |
| Angeln | `luck` |
| Tiere züchten | `farming` |
| Handeln | `trade` |
| Trank brauen, verzaubern | `alchemy` |
| Create-Block zum ersten Mal bauen | `tech` |
| Schmelzen | zusätzlich `tech` (höherer der beiden Faktoren) |

Die letzten drei Zeilen sind Quellen, die LevelZ nicht hatte — ohne sie hätten
Magier und Ingenieur keinen Bonus, weil es für ihre Fächer keine Erfahrung gab.
Create-Blöcke zählen **einmal pro Blockart und Spieler**, damit eine Reihe
Förderbänder keine Farm ist.

`effect_multiplier` ist noch nicht umgesetzt und wird deshalb auch nicht
angezeigt.

## Abschaltbare Fähigkeiten

Ein Effekt in den Trait-Daten mit `"toggle": true` lässt sich im Spiel per Taste
abschalten — Standard **Y**, umbelegbar unter Steuerung → Utopia:

```json
{ "effect": "minecraft:night_vision", "show_icon": true, "toggle": true }
```

Die Taste schaltet **alle** abschaltbaren Fähigkeiten des Charakters gemeinsam.
Solange kein Charakter mehr als eine hat, ist das die einfachste Lösung; kommt
eine zweite dazu, gehört an diese Stelle eine kleine Auswahl.
Effekte ohne `toggle` sind fest — Geschlechter-Effekte oder Girl Power soll
niemand wegdrücken können.

## Anbindung für Quests und Datapacks

Auslöser `utopia:character` — feuert bei der Charakterwahl und beim Betreten der
Welt, nicht pro Tick:

```json
{ "trigger": "utopia:character", "conditions": { "class": "utopia:tank" } }
```

Bedingungen `origin`, `gender`, `class` sind alle optional. Der Mod liefert die
zehn Klassen-Advancements bereits mit (`utopia:class/<name>`, unsichtbar, ohne
Toast) — damit lassen sich Heracles-Questgruppen über einen
`heracles:advancement`-Task auf eine Klasse begrenzen. Advancements für Origin
oder Gender gibt es bewusst noch nicht; sie sind eine JSON-Datei entfernt, sobald
eine Quest sie braucht.

## Handbuch

`Guidebook 3.0/chapters/15_character.py` erzeugt die Einträge *Herkunft*,
*Geschlecht* und *Klassen* im Kapitel „Fähigkeiten". Die Zahlen liest der
Generator direkt aus den Trait-JSONs — Balance nur im Mod ändern und
`python3 generate_guide.py` laufen lassen, dann stimmt das Buch wieder.

## Commands

```
/utopia character get|set|reset <spieler> [...]
/utopia points add <spieler> <anzahl>
```
