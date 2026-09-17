# Testauftrag — Utopia Core 0.10.11

Zum Weiterleiten an den Tester. Bitte der Reihe nach durchgehen und zu jedem
Punkt kurz **geht / geht nicht + was passiert ist** zurückmelden. Wenn etwas
abstürzt oder komisch aussieht: `latest.log` mitschicken, das hilft mehr als
jede Beschreibung.

**Vorbereitung:** alte `utopiacore-*.jar` aus dem Mods-Ordner löschen, nur
`utopiacore-1.4.13+utopia.0.10.11.jar` drin lassen. Zwei davon gleichzeitig
startet nicht. Am besten eine **neue Welt mit Cheats an** (sonst gehen die
Testbefehle nicht).

---

## 1. Freischalt-Bildschirm öffnen

**Taste U** drücken.

- Öffnet sich ein **zentriertes, kompaktes** Fenster „Unlocks", statt sich über
  die gesamte Ultrawide-Breite zu strecken?
- Sind Außenrahmen, Kopfzeilenrahmen, Sidebar/Canvas-Trenner, eingefasster
  Canvas, schwebende Werkzeugleiste und doppelt gerahmte Detailkarte klar
  voneinander getrennt?
- Liegen Canvas- und Werkzeugleistenrahmen links und rechts exakt bündig, mit
  einem sichtbaren schmalen Abstand dazwischen und ohne Überlappung?
- Bleiben Minus, Zoomanzeige, Plus und „Zentrieren" vollständig innerhalb der
  Werkzeugleiste? Der Minus-Knopf darf nicht über den linken Rahmen ragen.
- Haben Kopfzeile und Hauptfenster links/rechts gleiche Ränder, und sitzen
  Baumname sowie Unlock-Punkte vertikal mittig?
- Wirken Kopfzeile, Sidebar und Canvas wie feines Honig-/Mahagoni-Holz statt wie
  grob wiederholte oder auf Knopfgröße gestauchte Texturausschnitte?
- Links steht der Reiter **Create**; im Canvas beginnt ein frei verzweigter
  Baum aus runden Icon-Knoten, dauerhaft sichtbaren Namen und dünnen, dunkel
  konturierten sowie innen gestrichelten Hauptverbindungen?
- Drücke **Zentrieren**: Ist danach der gesamte sichtbare Tree mit etwas Rand
  zu sehen? Der Create-Baum sollte dabei ungefähr bei 17 Prozent landen.
- Lässt sich manuell bis 15 Prozent herauszoomen und anschließend wieder
  kontrolliert hineinzoomen?
- Sind die engsten Nodes bei 50 und 100 Prozent klar voneinander getrennt?
- Bleiben Namen ab 30 Prozent vollständig sichtbar und erscheinen unter
  30 Prozent zuverlässig bei Hover oder Auswahl?
- Bleibt die Bildrate beim Ziehen und Zoomen ungefähr auf dem Niveau außerhalb
  des Screens, statt auf einstellige FPS einzubrechen?
- Öffne eine Node-Detailkarte und ziehe den Tree dahinter langsam sowie schnell:
  Sind sämtliche fremden Node-Icons vollständig von der hellen Karte verdeckt?
- Bleiben Node-Ring, Item-Icon, Verbindung und Holzgrund beim Pannen exakt
  zusammen, ohne dass einzelne Nodes relativ zum Tree um einen Pixel springen?
- Bleiben die gestrichelten Verbindungen beim langsamen Pannen stabil, ohne
  wandernde, gestreckte oder trichterförmig neu verteilte Striche?
- Ist das Icon des **ersten** Eintrags unter „Unlocks" vollständig sichtbar,
  einschließlich seiner oberen vier Pixel? Auch nach Scrollen erneut prüfen.
- Oben rechts: „Unlock points: X" — welche Zahl steht da?

### Editor-Bedienung mit OP-Rechten

- **Editor öffnen** und im Werkzeug **Auswahl** einen Drag direkt auf einem
  Node beginnen: Bewegt sich nur die Holz-Arbeitsfläche, während Icon und
  Verbindung exakt auf ihrer Baumposition bleiben?
- Werkzeug **Bewegen** aktivieren und denselben Node ziehen: Erst jetzt darf
  sich seine gespeicherte Position ändern. Mit `Shift` ist die Bewegung frei.
- Rechtsklick auf Node und leere Fläche: Erscheinen jeweils passende Aktionen
  zum Bearbeiten/Duplizieren/Löschen beziehungsweise Anlegen/Zentrieren?
- `Strg`+Pfeiltaste verschiebt um 0,5, zusätzliches `Shift` um 0,1 Einheiten.
- Ein Wurzelknoten oder ein noch benötigter Vorgänger darf nicht gelöscht
  werden und muss eine verständliche Statusmeldung erzeugen.

## 2. Punkte bekommen

Punkte gibt es **pro Gesamtlevel**, nicht pro Fertigkeitspunkt.

- `/utopia unlock points <dein Name> 10` eingeben
- Bildschirm mit U öffnen: stehen jetzt 10 Punkte oben rechts?

Danach normal spielen, bis das Gesamtlevel steigt (Erz abbauen, Mobs töten).
Kommt pro Levelaufstieg mindestens ein Punkt dazu?

## 3. Knoten kaufen

Im Bildschirm einen runden Knoten anklicken.

- Zeigt die schwebende Detailkarte Namen, Kosten, ggf. „Requires overall level
  X", Vorgänger und eine scrollbare Liste aller enthaltenen Blöcke?
- Der erste Knoten (**Kinetic Basics**) sollte **golden** umrandet sein =
  kaufbar. Draufklicken.
- Wird es grün? Sinken die Punkte oben rechts um 1? Kommt unten eine Meldung?
- Die nachfolgenden Knoten (Power, Processing) brauchen Gesamtlevel 3 bzw. 5 —
  vorher müssten sie grau sein und der Tooltip sagen, was fehlt.

## 4. Die eigentliche Sperre — der wichtigste Punkt

**Vor** dem Kauf von *Kinetic Basics*:

- Versuche, eine **Create-Welle** (`create:shaft`) oder ein **Zahnrad**
  (`create:cogwheel`) zu craften. Geht das? Es sollte **nicht** gehen.
- Steht im Rezeptbrowser (REI) ein Hinweis dran, oder ist es ausgegraut?
- Nimm dir per Kreativ/Befehl ein Zahnrad und versuche, es **zu platzieren**.
  Geht das?

**Nach** dem Kauf:

- Lassen sich Welle, Zahnrad, großes Zahnrad, Getriebe und Andesit-Gehäuse jetzt
  craften und platzieren?
- **Mühlstein** (`create:millstone`) und **Mechanische Presse** gehören zum
  Knoten *Processing* — die müssten weiterhin gesperrt sein.

## 5. Klassenvorteil prüfen

- `/utopia character set <dein Name> utopia:human utopia:male utopia:engineer`
  (falls „human" nicht geht: irgendein Origin aus dem Auswahlbildschirm)
- U drücken: Ist **Kinetic Basics** beim Ingenieur schon grün, ohne es gekauft
  zu haben?
- Kosten der übrigen Knoten: stehen dort **kleinere Zahlen** als vorher?
  (Der Ingenieur zahlt einen Punkt weniger pro Create-Knoten.)
- Mit einer anderen Klasse (z.B. `utopia:farmer`) gegenprüfen: dort sollten die
  vollen Kosten stehen und nichts vorab freigeschaltet sein.

## 6. Bleibt es erhalten?

- Nach dem Kauf: **sterben** (`/kill`) — ist der Knoten danach noch grün?
- Welt verlassen und neu betreten — Knoten und Punkte noch da?

## 7. Nebenbei, falls Zeit ist

- **Taste Y**: schaltet die Nachtsicht der Neko an/aus, mit Meldung unten.
  Nur bei Neko-Charakteren; bei anderen kommt „no abilities to toggle".
- **Trank brauen** und **verzaubern**: gibt es dabei Erfahrungskugeln?
- **Ersten Create-Block einer Sorte platzieren**: Erfahrung? Beim zweiten Block
  derselben Sorte bewusst nicht mehr.

## 8. Handbuch über Utopia anfordern

Diese beiden Befehle brauchen weder Cheats noch OP-Rechte:

- `/utopia handbuch` gibt das deutsche Utopia-Handbuch.
- `/utopia handbook` gibt das englische Utopia-Handbuch.
- Die Antwort im Chat verweist für die jeweils andere Sprache ebenfalls auf
  den passenden `/utopia`-Befehl.
- Die alten `/trigger handbuch` und `/trigger handbook` funktionieren weiterhin
  als Fallback.

Optionaler Fehlerpfad mit OP-Rechten: das `utopia_handbook`-Datapack kurz
deaktivieren und `/reload` ausführen. Beide `/utopia`-Befehle müssen dann eine
verständliche Datapack-Fehlermeldung liefern, ohne den Server oder Utopia Core
zu beschädigen. Danach das Datapack wieder aktivieren und erneut `/reload`
ausführen.

## 9. Einmalige Wasser-Startversorgung

Mit einem neuen Spieler die Charaktererstellung normal abschließen oder zuerst
`/utopia character reset` ausführen und danach neu wählen.

- Zusätzlich zur gewählten Ausrüstung müssen genau sechs gereinigte
  Wasserflaschen im Inventar liegen. Es sind Vanilla-Trankitems mit
  `Potion:"minecraft:purified_water"`, keine normalen Wassertränke und keine
  wirkungslosen Glasflaschen.
- Eine Flasche trinken: Dehydration muss den internen Durstwert im aktuellen
  Testprofil um 2 erhöhen (ungefähr ein sichtbares Durstsymbol). Alle sechs
  Flaschen ergeben damit 12 von maximal 20 internen Punkten.
- Beim Trinken darf der negative Dehydration-Dursteffekt nicht ausgelöst werden.
- Balanceziel: Von Sonnenaufgang bis zum nächsten Sonnenaufgang einschließlich
  normaler Bewegung, Arbeit und einmal Schlafen dürfen höchstens 6 interne
  Durstpunkte fehlen. Drei gereinigte Flaschen müssen den kompletten
  Tagesverbrauch ersetzen; die sechs Starterflaschen sind damit ein einmaliger
  Vorrat für ungefähr zwei Minecraft-Tage, keine tägliche Ausgabe.
- Den Tagestest im Singleplayer und auf dem Dedicated Server mit
  `hydrating_factor=2.0` wiederholen. Startwert, Wert vor dem Schlafen und Wert
  nach dem Aufstehen notieren. Der Server darf nicht mehr den abweichenden Wert
  1.5 verwenden.
- Welt verlassen und erneut betreten, sterben/respawnen und Dimension wechseln:
  Es dürfen keine weiteren Flaschen erscheinen.
- `/utopia character set …` darf ebenfalls keine neue Startausrüstung geben.
- Vollinventar-Test: Vor dem Abschluss alle Inventarplätze belegen. Nicht
  einpassende Wasserflaschen müssen wie bestehende Loadouts sicher vor dem
  Spieler landen und dürfen nicht verschwinden oder den Abschluss abbrechen.
- Ein bewusster `/utopia character reset` mit erneutem Abschluss gibt genau ein
  neues vollständiges Starterset; innerhalb desselben Abschlusses niemals mehr.

## 10. Thigh Highs und Kältespawn

- Eine beliebige Variante aus `#thigh_highs_etc:thigh_highs` im Bein-Slot
  anziehen. Das EnvironmentZ-Thermometer muss sie als warme Kleidung
  berücksichtigen; mehrere Farben stichprobenartig prüfen. Nur im Inventar
  liegende Thigh Highs dürfen keine Wärme liefern.
- Neue Welt mit Spawn in einem Schnee-/Eisbiom erstellen und dort die
  Charaktererstellung abschließen. Genau ein vollständiges Wandererset (Helm,
  Brust, Hose, Stiefel) muss im Inventar liegen und der achtminütige
  EnvironmentZ-Effekt **Comfort** muss aktiv sein. Der Komforteffekt ist der
  eigentliche unmittelbare Schutz; EnvironmentZ klassifiziert das Wandererset
  selbst ausdrücklich als temperaturneutral, auch wenn es angezogen wird.
- Vor dem Abschluss schützt bei einem wirklich neuen Spieler zunächst nur
  EnvironmentZs eigener Start-Comfort. Nach dem Abschluss im kalten Biom muss
  Core den Schutz wieder mit 9.600 Ticks setzen.
- In einem nicht kalten Biom denselben Ablauf mit einem bereits vorhandenen
  Testspieler nach Reset wiederholen: weder Wandererset noch neuer Comfort von
  Core.
- Bei voller Ausrüstung und nahezu vollem Inventar prüfen, dass keine
  Klassenrüstung überschrieben wird und nicht einpassbare Teile wie andere
  Starteritems sicher vor dem Spieler landen.
- Tod, Rejoin und Dimensionswechsel dürfen kein weiteres Wandererset geben.
- `/utopia character set …` darf kein Set geben. Ein bewusster Character-Reset
  mit erneutem vollständigem Abschluss folgt wie die übrige Startausrüstung der
  bestehenden Reset-Semantik und darf im kalten Biom erneut genau ein Set geben.
- Optionaler Abhängigkeitstest: Ohne EnvironmentZ darf der Charakterabschluss
  nicht abstürzen; Kleidung und Comfort werden nur protokolliert und
  übersprungen.

## 11. Freie Holz- und Steinstufe

Mit einem neuen Charakter ohne investierte Skillpunkte testen:

- Holzaxt, Holzhacke, Holzschwert, Holzspitzhacke und Holzschaufel lassen sich
  craften und benutzen.
- Steinaxt, Steinhacke, Steinschwert, Steinspitzhacke und Steinschaufel lassen
  sich craften und benutzen.
- Bogen, Eimer, Leder- und Kettenrüstung bleiben auf ihren bisherigen
  Anforderungen; Eisenwerkzeuge und -waffen bleiben weiterhin Stufe 8.
- Rezeptbrowser und Tooltips dürfen an Holz/Stein keine LevelZ-Sperre mehr
  anzeigen. Die genannten Kontrollgegenstände müssen ihre Sperrhinweise
  behalten.

---

## Was mich am meisten interessiert

1. Fühlt sich das Sperren **verständlich** an, oder wirkt es wie ein Bug?
   (Nicht craftbar, aber sichtbar — merkt man, dass es Absicht ist?)
2. Ist im Bildschirm klar, **was ein Knoten bringt**, bevor man ihn kauft?
3. Sind die Kosten und Levelanforderungen zu hart, zu lasch, oder passend?
