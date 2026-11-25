> 🇬🇧 [English version](README.md)

# Ironblood: Captive's Raid - Overlord — SDK CodinGame (Optimisation Solo)

Puzzle solo d'optimisation RPG tactique développé avec le **CodinGame Game Engine SDK (`v4.7.7`, Java 17 & PixiJS v6)**. Commandez l'**Overlord** et gérez votre **Rivière Runique** (`6–8` tuiles, `0–2` activations/tour, *dredging* à `2` gemmes et Renforts `EVENT`) pour repousser l'escouade de Héros IA durant `8–9` rounds.

> Seuls les 4 cas de test publics de l'IDE (`config/test1.json`..`config/test4.json`) et un bot de démarrage sans spoiler (`StarterBot.java`) sont publiés ici ; les solutions de référence et les validateurs cachés restent privés.

---

## Commandes Rapides

* **Viewer Web Local (`http://localhost:8888/test.html`)** :
  ```bash
  mvn test-compile exec:java -Dexec.mainClass="Main" -Dexec.classpathScope="test"
  ```
* **Simulation Batch (Tests Publics `test1`..`test4`)** :
  ```bash
  mvn test-compile exec:java -Dexec.mainClass="Main" -Dexec.classpathScope="test" -Dexec.args="--simulate"
  ```
* **Lancer Votre Bot Python** :
  ```bash
  mvn test-compile exec:java -Dexec.mainClass="Main" -Dexec.classpathScope="test" -Dexec.args="--simulate --python /chemin/vers/bot.py"
  ```

---

## Crédits & Liens

* **Inspiration des mécaniques** : Zones planaires, attrition par blessures, Gêne et Rivière Runique inspirées du jeu de plateau *Conan* (**Monolith Board Games**, conçu par **Frédéric Henry**).
* **Assets graphiques** : 100 % CC0 1.0 Universal par **Kenney.nl** ([`KENNEY_CC0_LICENSE.txt`](KENNEY_CC0_LICENSE.txt)).
* **Contribution CodinGame (Brouillon)** : https://www.codingame.com/contribute/view/158845b8c4e7c6304d50f4f79b36eafa544c73
* **Jouer dans l'IDE CodinGame** : https://www.codingame.com/ide/demo/1606585da9258ae0b4f82aa577b5ad11e6756a6
* **Puzzle Miroir (Côté Héros)** : https://github.com/CGphilRG/codingame-ironblood-raid-heroes
* **Hub Vitrine GitHub Pages** : https://cgphilrg.github.io/codingame-sdk-puzzles/
