# Änderungen

Versionsschema `1.4.13+utopia.X.Y.Z` — vorne der LevelZ-Stand (muss ≥ 1.4.13 bleiben,
sonst startet `jobsaddon` nicht), dahinter die Utopia-Version. **Die hintere Nummer
steigt bei jedem Bau**, damit im Mods-Ordner nie zwei gleich benannte Jars liegen.

Beim Einspielen die alte Datei löschen — zwei Utopia-Core-Jars nebeneinander lässt
Fabric nicht starten.

## 0.10.2

- **Geschenkte Knoten hängen jetzt an der Klasse.** Der Startknoten des
  Ingenieurs blieb beim Klassenwechsel freigeschaltet — `unlocks` wurde nur
  ergänzt, nie bereinigt. Jetzt merkt sich der Charakter getrennt, was geschenkt
  und was gekauft ist: Geschenke verschwinden mit der Klasse, **gekaufte Knoten
  bleiben**.

## 0.10.1

- **Die Sperre greift jetzt wirklich.** Mein Einhängepunkt saß in LevelZ'
  Prüfmethode — die wird aber nur aufgerufen, *wenn der Gegenstand in einer
  LevelZ-Liste steht* (`if (customList.contains(id)) ... pruefen`). Ein
  Create-Zahnrad steht in keiner, also wurde nie geprüft. Jetzt eigene
  Ereignisse für Benutzen und Rechtsklick, plus Abbruch in `BlockItem.place`
  fürs Platzieren. Crafting lief schon vorher über die richtige Stelle.
- **Tooltip an gesperrten Gegenständen**: „Locked — needs: Kinetic Basics".
  Ohne den wirkt ein gesperrter Block wie ein Fehler.
- **Kreativmodus umgeht die Sperre** — wie bei LevelZ. Der Tooltip sagt das
  jetzt auch dazu. Zum Testen also Überlebensmodus.

## 0.10.0 — Oberfläche für die Bäume

- **Freischalt-Bildschirm**, Taste **U** (umbelegbar unter Steuerung → Utopia):
  links die Baum-Reiter, rechts das Knotenraster mit Verbindungslinien.
- Knoten sind farbig nach Zustand — grün gekauft, gold kaufbar, grau gesperrt —
  und zeigen ihre Kosten. Der Tooltip nennt Kosten, Levelanforderung, die ersten
  sechs enthaltenen Gegenstände und woran es gerade scheitert.
- Klick auf einen goldenen Knoten kauft ihn. Entschieden wird auf dem Server;
  der Bildschirm rechnet nur mit, was synchronisiert ist.
- Dafür wird der Charakterzustand jetzt vollständig auf den Client gespiegelt —
  Kosten und Levelanforderungen kommen so aus demselben Code wie auf dem Server,
  inklusive Klassenrabatt.

## 0.9.0 — Freischalt-Bäume, Grundgerüst

- **Der `tech`-Skill ist wieder raus.** Er war ein Missverständnis; wieder zwölf
  Fähigkeiten. Der Ingenieur bekommt stattdessen einen Vorsprung im Create-Baum.
- **Freischaltpunkte** als eigener Topf: einer pro Gesamtlevel, plus was Herkunft
  und Klasse dazugeben. Konkurriert nicht mit den LevelZ-Skillpunkten.
- **Bäume als Datapack**: `data/<ns>/utopia/trees/<name>.json` — Knoten mit
  Kosten, Mindestlevel, Vorgängern, Rasterposition und der Liste dessen, was sie
  öffnen (Ids oder `#tags`). Ein Beispielbaum für Create mit vier Knoten liegt bei.
- **Sperrwirkung** über die zwei Entscheidungsstellen, die LevelZ ohnehin hat —
  dadurch greift sie sofort überall: Crafting, Benutzen, Tooltips, Ausgrauen in
  REI und EMI. Keine zweite Sperrschicht.
- **Traits beeinflussen die Bäume**: Startknoten (`unlocks`), Punkte pro Level,
  Rabatt pro Baum, verschobene Levelanforderung (`unlock`-Block).
- Befehle zum Testen, solange die Oberfläche fehlt: `/utopia unlock list`,
  `/utopia unlock buy <knoten>`, `/utopia unlock points <spieler> <anzahl>` (OP).

Noch offen: die eigene Oberfläche und die eigentlichen Baum-Inhalte.

## 0.8.0

- **Fähigkeiten-Taste.** Standard **Y** (fast jeder andere Buchstabe ist im Pack
  belegt), umbelegbar unter Steuerung → Utopia. Schaltet die abschaltbaren
  Fähigkeiten des Charakters an und aus, mit Rückmeldung in der Aktionsleiste.
- Effekte in den Trait-Daten können jetzt `"toggle": true` tragen. Nur solche
  lassen sich abschalten — Geschlechter-Effekte oder Girl Power bleiben fest.
- Erste Anwendung: die **Nachtsicht der Neko**. Der Zustand wird pro Spieler
  gespeichert und überlebt Tod und Neustart.

## 0.7.2

- **Technik-Erfahrung fürs Bauen wirkt jetzt.** Der Einhängepunkt lag an
  `Block.onPlaced` — die Methode ist in Vanilla leer, und Create überschreibt sie
  in `KineticBlock` und `CogWheelBlock`, ohne die Oberklasse aufzurufen. Für
  genau die Blöcke, um die es geht, lief der Code also nie. Hauptweg ist jetzt
  `BlockItem.place`, das jede Platzierung durch einen Spieler durchläuft;
  `onPlaced` bleibt als zweiter Weg. Jede Blockart zählt weiterhin nur einmal.

## 0.7.1

- **Absturz beim Erzeugen eines Spielers behoben.** `Entity` ruft im Konstruktor
  `setAir(getMaxAir())` auf — zu dem Zeitpunkt gibt es den Attribut-Behälter von
  `LivingEntity` noch nicht, `getAttributes()` liefert null. Der Luftvorrat-Mixin
  griff trotzdem zu und riss den Start mit. Jetzt mit Prüfung.
- Derselbe Mixin läuft nur noch für Spieler statt für jede Kreatur. `getMaxAir`
  wird pro Entity und Tick aufgerufen — das Attribut gibt es aber nur am Spieler.

## 0.7.0

- **Absturz beim Start behoben** (steckte in 0.5.0 und 0.5.1): der Mixin für den
  Luftvorrat hing an `LivingEntity.getMaxAir` — die Methode sitzt aber an
  `Entity`. Ein Mixin auf eine Methode, die es im Ziel nicht gibt, bricht den
  Start ab. Jetzt an der richtigen Klasse, mit Prüfung auf `LivingEntity`, weil
  es Attribute erst dort gibt.
- **Neue Erfahrungsquellen**, damit die Klassenboni von Magier und Ingenieur
  nicht ins Leere laufen:
  - **Trank brauen** → Alchemie (5 XP). Der Braustand weiß nicht, wer ihn
    bestückt hat; den Faktor bekommt der nächststehende Spieler — derselbe, zu
    dem die Kugel ohnehin fliegt.
  - **Verzaubern** → Alchemie (4 XP je Reihe, teure Reihe gibt mehr).
  - **Create-Block zum ersten Mal bauen** → Technik (8 XP), **einmal pro
    Blockart und Spieler**. Belohnt wird das Kennenlernen eines Bauteils, nicht
    die Menge — eine Reihe Förderbänder ist so keine Farm. Gemerkt wird das im
    Spielerdatensatz.
  - **Schmelzen** zählt jetzt für Schmieden *und* Technik. Es gilt der höhere
    der beiden Faktoren, nicht das Produkt.

## 0.6.0

- **`xp_multiplier` wirkt jetzt tatsächlich.** Bisher stand der Wert nur in den
  Daten und im Screen, ohne dass ihn irgendetwas gelesen hat.
- LevelZ hat keine Erfahrung pro Fähigkeit, sondern einen Topf fürs Gesamtlevel.
  Der Faktor greift deshalb dort, wo Erfahrung entsteht und die Tätigkeit noch
  bekannt ist: Erz (mining), Mobs (strength, mit Bogen in der Hand archery),
  Schmelzen (smithing), Angeln (luck), Zucht (farming), Handel (trade).
- Ohne Erfahrungsquelle kein Bonus: **Alchemie und Technik** bekommen derzeit
  nichts — dafür müsste es die Quelle erst geben (z.B. XP fürs Brauen).
- `effect_multiplier` wird nicht mehr angezeigt, solange er nichts tut.

## 0.5.1

- Meeresbürger schwimmt wieder schneller, jetzt über den Vanilla-Effekt
  **Dolphin's Grace** (dauerhaft, wirkt nur im Wasser). Der Client kennt den
  Effekt und sagt die Bewegung selbst voraus — deshalb kein Ruckeln wie beim
  Attribut aus `additionalentityattributes`.

## 0.5.0

- **Schwimmtempo raus.** Meeresbürger hatte
  `additionalentityattributes:generic.water_speed +0.20`. Das Attribut steht per
  Voreinstellung auf 0.5, der Bonus war also real +40% und nicht +20% — und der
  Mod greift dafür in `travel()` ein, also in die Bewegung, die der Client
  vorhersagt und der Server nachrechnet. Genau daher das Ruckeln.
- Stattdessen: eigenes Attribut `utopiacore:breath_capacity` (Faktor auf den
  Luftvorrat, Meeresbürger taucht doppelt so lange) plus klare Sicht unter Wasser
  über `additionalentityattributes:player.water_visibility` — reine Anzeige, kein
  Bewegungseingriff. Beides kann nicht auseinanderlaufen.

## 0.4.2

- **Beim Wechsel bleibt nichts mehr hängen.** Der Mod merkt sich, welche
  Attribute und Effekte er zuletzt gesetzt hat, und nimmt sie ab, bevor die neuen
  kommen. Vorher trug man nach männlich → weiblich beide Geschlechter-Effekte und
  behielt die Extraherzen des alten Geschlechts. Gilt auch für
  `/utopia character reset`: der räumt jetzt vollständig auf.
- `/utopia character set` gibt **keine** Startausrüstung mehr. Beim Durchprobieren
  lagen sonst nach fünf Klassen fünf Ausrüstungen im Inventar. Den vollen Start
  gibt es über `reset` und die normale Auswahl.

## 0.4.1

- **Absturz beim Synchronisieren behoben.** Der Client kopierte im
  LEVEL_PACKET-Handler eine fest verdrahtete Anzahl Werte — genau die zwölf
  Ursprungs-Fähigkeiten plus drei Kopfwerte. Mit der dreizehnten fehlte ein Wert
  und `executeLevelPacket` lief in eine `IndexOutOfBoundsException`, wodurch
  Fähigkeitsstände und Freischaltlisten auf dem Client nicht ankamen.
- Startausrüstung wird über `equipStack` vergeben statt direkt in die
  Rüstungsliste geschrieben — nur so bekommen Client und andere Mods den
  Ausrüstungswechsel mit. Ein belegter Slot wandert ins Inventar statt verloren
  zu gehen.
- Eine Logzeile pro Charaktererstellung listet, was tatsächlich ausgeteilt wurde.

## 0.4.0

- `Skill` ist keine Enum mehr, sondern eine Registry mit Enum-Oberfläche. Die zwölf
  Ursprungs-Fähigkeiten unverändert (Reihenfolge, NBT-Schlüssel, Config-Werte).
- Neue Fähigkeit **Tech** (`data/utopia/skills/tech.json`), Zahnrad-Icon auf Platz 13
  des Icon-Streifens. Fähigkeiten werden aus dem Jar geladen, nicht aus Datapacks —
  sonst können Server und Client unterschiedliche Reihenfolgen haben.
- Die vier `switch (skill)`-Blöcke der Netzwerkklassen entfallen; Attribut und
  Sperrlisten hängen an der Fähigkeit.
- `PlayerStatsManager` speichert `int[]` statt `Map<Skill,Integer>`.
- Fähigkeitenbildschirm rechnet das Raster aus der Anzahl (12 → altes Layout).
- `/playerstats` baut seinen Befehlsbaum aus der Registry statt aus 64 Literalen.

## 0.3.0

- Advancement-Auslöser `utopia:character` (Bedingungen `origin`, `gender`, `class`),
  feuert bei der Auswahl und beim Betreten der Welt.
- Zehn unsichtbare Klassen-Advancements `utopia:class/<name>` als Aufhänger für
  Heracles-Quests.
- `/utopia character get` braucht keine Rechte mehr, `reset` wirkt ohne Argument auf
  einen selbst. Rechte hängen an den Unterbefehlen statt an der Wurzel — sonst ist in
  einer Welt ohne Cheats der ganze Befehl unsichtbar.

## 0.2.0

- Auswahl-Screen neu: undurchsichtiger Hintergrund, Panel mit Titelleiste und
  Item-Icon, aus den Trait-Daten erzeugte Wirkungsliste, Blättern mit Pfeilen,
  Mausrad und Tastatur.
- Screen öffnet erst, wenn die Welt geladen ist; Spieler ist bis zum Abschluss
  unverwundbar.
- Effekt-Icons für `utopiacore:feminine` und `utopiacore:masculine`.
- Zehn Klassen und fünf Origins nach Justins Balance-Vorschlag.
- Eigene Attribute `fire_resistance`, `fall_resistance`, `regeneration_speed`.
- Loadout-Items werden erst beim Austeilen aufgelöst — fehlt ein Mod, fällt nur das
  Item aus statt der ganzen Klasse.

## 0.1.0

- Erste Fassung: Hard-Fork von LevelZ 1.4.13, Mod-Id `utopiacore` mit
  `provides: ["levelz"]`.
- Trait-Schema für Origin, Gender und Klasse, Datapack-Loader, Auswahl-Flow,
  Persistenz, Netzwerk-Sync.
- Estrogen-Bridge ohne Compile-Abhängigkeit.
- `/utopia character` und `/utopia points`.
