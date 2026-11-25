> 🇫🇷 [Version française](README_fr.md)

# Ironblood: Captive's Raid - Overlord — CodinGame Optimization SDK

A solo optimization tactical RPG puzzle built with the **CodinGame Game Engine SDK (`v4.7.7`, Java 17 & PixiJS v6)**. Command the dark **Overlord** and manage the **Runic River Conveyor** (`6–8` tiles, `0–2` activations/turn, `2`-gem *dredging*, and `EVENT` reinforcements) to stop the Hero AI raid before `8–9` rounds expire.

> Only the 4 public IDE test cases (`config/test1.json`..`config/test4.json`) and a non-spoiler `StarterBot.java` are published in this repository. Reference solutions and hidden validators (`test5`..`test14`) remain private.

---

## Quick Commands

* **Local Web Viewer (`http://localhost:8888/test.html`)**:
  ```bash
  mvn test-compile exec:java -Dexec.mainClass="Main" -Dexec.classpathScope="test"
  ```
* **Batch Simulation (Public Tests `test1`..`test4`)**:
  ```bash
  mvn test-compile exec:java -Dexec.mainClass="Main" -Dexec.classpathScope="test" -Dexec.args="--simulate"
  ```
* **Run with Your Python Bot**:
  ```bash
  mvn test-compile exec:java -Dexec.mainClass="Main" -Dexec.classpathScope="test" -Dexec.args="--simulate --python /path/to/bot.py"
  ```

---

## Credits & Links

* **Mechanics Inspiration**: Asymmetric Zones, stamina/wound attrition, Hindrance, and Runic River conveyor inspired by the board game *Conan* (**Monolith Board Games**, designed by **Frédéric Henry**).
* **Graphic Assets**: 100% CC0 1.0 Universal from **Kenney.nl** ([`KENNEY_CC0_LICENSE.txt`](KENNEY_CC0_LICENSE.txt)).
* **CodinGame Contribution (Draft)**: https://www.codingame.com/contribute/view/158845b8c4e7c6304d50f4f79b36eafa544c73
* **Play in CodinGame IDE**: https://www.codingame.com/ide/demo/1606585da9258ae0b4f82aa577b5ad11e6756a6
* **Mirror Puzzle (Heroes Side)**: https://github.com/CGphilRG/codingame-ironblood-raid-heroes
* **CodinGame SDK Puzzles Showcase Hub**: https://cgphilrg.github.io/codingame-sdk-puzzles/
