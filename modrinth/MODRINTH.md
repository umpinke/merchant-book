# Upload-Daten für Modrinth

Beim Anlegen des Projekts (modrinth.com → „Create a project“):

| Feld | Wert |
|---|---|
| Name | Merchant Book |
| URL / Slug | merchant-book (falls vergeben: z. B. merchant-book-trades) |
| Zusammenfassung | A craftable book that shows every villager trade, level by level. |
| Projekttyp | Mod |
| Kategorien | Utility, Game Mechanics |
| Client | Required |
| Server | Required |
| Lizenz | MIT License |
| Icon | `icon.png` |
| Galerie | `gallery-1` bis `gallery-5` |

Beim Hochladen der Version:

| Feld | Wert |
|---|---|
| Datei | `merchant-book-1.0.0.jar` |
| Versionsnummer | 1.0.0 |
| Versionsname | Merchant Book 1.0.0 |
| Release-Kanal | Release |
| Loader | Fabric |
| Minecraft-Versionen | 26.2 |
| Abhängigkeit | Fabric API → Required |
| Changelog | First release. |

Der folgende Text kommt in das Feld „Description“ (Markdown).

---

# Merchant Book

Ever wondered which trades a librarian can actually roll, or what a master mason sells? The **Merchant Book** answers that without walking around your village.

## How to use

1. Craft it: **Book + Emerald** (shapeless).
2. Right-click with the book in your hand.
3. Pick a profession on the left page – all 13 professions plus the **Wandering Trader**.
4. The right page lists every trade the profession can get, sorted by level: **Novice → Apprentice → Journeyman → Expert → Master**.

## What you see

- **Price → item** for every trade, including price ranges (e.g. `5–64` emeralds).
- **How many trades are picked per level**, e.g. “2 of 3”.
- **Biome-specific trades** are marked, e.g. “Only: Taiga, Swamp”.
- **Hover a trade** for details: uses before restocking, experience for the villager.
- **Random enchanted books**: hover to see every possible enchantment with its emerald price range.
- Hover an item for its normal tooltip.

The trades are read straight from the game's data, so datapacks that change villager trades (like the experimental *Villager Trade Rebalance*) are shown correctly.

## Requirements

- Fabric Loader + **Fabric API**
- Minecraft 26.2
- Install on **client and server** (in singleplayer, just put it in your mods folder).

---

# Merchant Book (Deutsch)

Welche Trades kann ein Bibliothekar eigentlich bekommen, und was verkauft ein Steinmetz-Meister? Das **Händlerbuch** zeigt es dir, ohne dass du durchs Dorf laufen musst.

## Benutzung

1. Craften: **Buch + Smaragd** (formlos).
2. Mit dem Buch in der Hand rechtsklicken.
3. Links einen Beruf auswählen: alle 13 Berufe plus der **fahrende Händler**.
4. Rechts stehen alle möglichen Trades, nach Stufe sortiert: **Neuling → Lehrling → Geselle → Experte → Meister**.

## Was angezeigt wird

- **Preis → Ware** für jeden Trade, inklusive Preisspannen (z. B. `5–64` Smaragde).
- **Wie viele Trades pro Stufe ausgewählt werden**, z. B. „2 von 3“.
- **Biom-abhängige Trades** sind markiert, z. B. „Nur: Taiga, Sumpf“.
- **Maus über einen Trade:** Details wie Nutzungen bis zum Nachfüllen und Erfahrung für den Dorfbewohner.
- **Zufällig verzauberte Bücher:** Tooltip mit allen möglichen Verzauberungen und ihrer Preisspanne.

Die Trades kommen direkt aus den Spieldaten. Datenpakete, die Villager-Trades ändern, werden deshalb automatisch richtig angezeigt.

## Voraussetzungen

- Fabric Loader + **Fabric API**
- Minecraft 26.2
- Auf **Client und Server** installieren (im Einzelspieler reicht der mods-Ordner).
