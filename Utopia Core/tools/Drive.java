import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Minimaler Ersatz fuer xdotool/import auf Xvfb, nur mit dem JDK. Im Cloud-
 * Container fehlen beide Programme zeitweise, und nachinstallieren ist dort
 * nicht erlaubt; java.awt.Robot bringt das JDK selbst mit.
 * Aufruf: DISPLAY=:99 java tools/Drive.java <befehl> [argumente] ...  (mehrere Befehle hintereinander moeglich)
 *   shot <datei.png>        Bildschirmfoto
 *   click <x> <y>           Linksklick (Bildschirmpixel)
 *   move <x> <y>            Maus bewegen
 *   scroll <n>              Mausrad, n>0 = nach unten
 *   key <NAME>              Taste, z. B. ENTER, ESCAPE, RIGHT, LEFT, U, F8, TAB
 *   type <text>             Text tippen (nur a-z, 0-9, Leerzeichen)
 *   wait <ms>               warten
 */
public class Drive {
    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(40);
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "shot" -> {
                    Rectangle screen = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
                    ImageIO.write(robot.createScreenCapture(screen), "png", new File(args[++i]));
                }
                case "click" -> {
                    robot.mouseMove(Integer.parseInt(args[++i]), Integer.parseInt(args[++i]));
                    robot.delay(80);
                    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                    robot.delay(60);
                    robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                }
                case "move" -> robot.mouseMove(Integer.parseInt(args[++i]), Integer.parseInt(args[++i]));
                case "scroll" -> robot.mouseWheel(Integer.parseInt(args[++i]));
                case "key" -> {
                    int code = KeyEvent.class.getField("VK_" + args[++i]).getInt(null);
                    robot.keyPress(code);
                    robot.delay(60);
                    robot.keyRelease(code);
                }
                case "type" -> {
                    for (char c : args[++i].toUpperCase().toCharArray()) {
                        int code = c == ' ' ? KeyEvent.VK_SPACE : KeyEvent.getExtendedKeyCodeForChar(c);
                        robot.keyPress(code);
                        robot.keyRelease(code);
                    }
                }
                case "wait" -> robot.delay(Integer.parseInt(args[++i]));
                default -> throw new IllegalArgumentException("unbekannt: " + args[i]);
            }
        }
    }
}
