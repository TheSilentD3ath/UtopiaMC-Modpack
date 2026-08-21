# Testauftrag — Utopia Core 0.10.0

Zum Weiterleiten an den Tester. Bitte der Reihe nach durchgehen und zu jedem
Punkt kurz **geht / geht nicht + was passiert ist** zurückmelden. Wenn etwas
abstürzt oder komisch aussieht: `latest.log` mitschicken, das hilft mehr als
jede Beschreibung.

**Vorbereitung:** alte `utopiacore-*.jar` aus dem Mods-Ordner löschen, nur
`utopiacore-1.4.13+utopia.0.10.0.jar` drin lassen. Zwei davon gleichzeitig
startet nicht. Am besten eine **neue Welt mit Cheats an** (sonst gehen die
Testbefehle nicht).

---

## 1. Freischalt-Bildschirm öffnen

**Taste U** drücken.

- Öffnet sich ein Fenster „Unlocks"?
- Links steht der Reiter **Create**, rechts vier Kästchen mit Zahnrad-Symbolen,
  durch Linien verbunden?
- Oben rechts: „Unlock points: X" — welche Zahl steht da?

## 2. Punkte bekommen

Punkte gibt es **pro Gesamtlevel**, nicht pro Fertigkeitspunkt.

- `/utopia unlock points <dein Name> 10` eingeben
- Bildschirm mit U öffnen: stehen jetzt 10 Punkte oben rechts?

Danach normal spielen, bis das Gesamtlevel steigt (Erz abbauen, Mobs töten).
Kommt pro Levelaufstieg mindestens ein Punkt dazu?

## 3. Knoten kaufen

Im Bildschirm mit der Maus über die Kästchen fahren.

- Zeigt der Tooltip Namen, Kosten, ggf. „Requires overall level X" und eine
  Liste der enthaltenen Blöcke?
- Das erste Kästchen (**Kinetic Basics**) sollte **golden** umrandet sein =
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

---

## Was mich am meisten interessiert

1. Fühlt sich das Sperren **verständlich** an, oder wirkt es wie ein Bug?
   (Nicht craftbar, aber sichtbar — merkt man, dass es Absicht ist?)
2. Ist im Bildschirm klar, **was ein Knoten bringt**, bevor man ihn kauft?
3. Sind die Kosten und Levelanforderungen zu hart, zu lasch, oder passend?
