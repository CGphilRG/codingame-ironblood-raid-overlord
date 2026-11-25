import java.util.Scanner;

/**
 * Minimal non-spoiler starter bot reading the full Overlord input protocol
 * and emitting PASS each turn. Replace with your own strategy!
 */
public class StarterBot {
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        if (!in.hasNextInt()) return;
        in.nextInt(); // role
        in.nextInt(); // maxRounds
        in.nextInt(); // cols
        in.nextInt(); // rows
        int zoneCount = in.nextInt();
        int heroCount = in.nextInt();
        int tileCount = in.nextInt();
        in.nextInt(); // recoverRate
        in.nextInt(); // reinforcePower

        for (int i = 0; i < zoneCount * 8; i++) in.nextInt();
        for (int i = 0; i < heroCount; i++) {
            in.nextInt();
            in.next(); // archetype
            for (int j = 0; j < 7; j++) in.nextInt();
        }
        for (int i = 0; i < tileCount; i++) {
            in.nextInt();
            in.next(); // unitType
            for (int j = 0; j < 7; j++) in.nextInt();
        }

        while (in.hasNextInt()) {
            for (int i = 0; i < 5; i++) in.nextInt();
            for (int i = 0; i < heroCount * 8; i++) in.nextInt();
            for (int i = 0; i < tileCount * 5; i++) in.nextInt();
            int enemyCount = in.nextInt();
            for (int i = 0; i < enemyCount * 5; i++) in.nextInt();
            int poiCount = in.nextInt();
            for (int i = 0; i < poiCount * 3; i++) in.nextInt();
            System.out.println("PASS");
        }
    }
}
