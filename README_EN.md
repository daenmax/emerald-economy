[简体中文](README.md) | **English**

# Emerald Economy

[Download on Modrinth](https://modrinth.com/mod/emerald-economy-daenmax) · [Source on GitHub](https://github.com/daenmax/emerald-economy)

> An economy gameplay mod for **Minecraft Java 1.21.1** on **NeoForge**: it turns the emerald into real
> currency and builds six interconnected economic systems around one **shared player account**.
>
> Mod ID: `emerald_economy` · Package: `cn.daenx.mc.emeraldeconomy`

---

## What is this

On most servers an emerald is just "a material you can trade with". This mod makes it **the currency itself**:
every feature ultimately spends from and earns into the same account, forming a closed loop.

1. Deposit the emeralds in your inventory into your account at the **ATM** (1% fee).
2. Trade seven funds on the **Fund Exchange** to profit from price swings — prices are pushed by mining,
   logging, harvesting, mob kills, villager trades and the day's weather.
3. Bet your balance on the **Lottery Machine**; the draw happens at the next daily settlement.
4. Spend your balance at the **Vending Machine** on max-level enchanted books, seeds and saplings.
5. Sell what you mine, farm and kill to the **Acquisition Machine** — and on every 5th game day,
   **Crazy Friday** pays 5–10× for a few rare items.
6. Short on cash? Take a **loan**; interest is charged at each daily settlement.
   **Until the debt is repaid you cannot bet, and you cannot join Crazy Friday.**

Every machine must be placed **directly on top of a redstone block**, otherwise right-clicking it says
"Not connected to a redstone power source".

<table>
<tr>
<td align="center"><img src="素材/README截图/machine_atm.png" width="260"><br><b>ATM</b><br>High-mounted screen + keypad + cash slot</td>
<td align="center"><img src="素材/README截图/machine_exchange.png" width="260"><br><b>Exchange Terminal</b><br>Monitor + keyboard + tower case</td>
</tr>
<tr>
<td align="center"><img src="素材/README截图/machine_lottery.png" width="260"><br><b>Lottery Machine</b><br>Three reels + side lever + light strip on top</td>
<td align="center"><img src="素材/README截图/machine_vending.png" width="260"><br><b>Vending Machine</b><br>Shelf display + control column + pickup slot</td>
</tr>
<tr>
<td align="center"><img src="素材/README截图/machine_acquisition.png" width="260"><br><b>Acquisition Machine</b><br>Top hopper + conveyor belt + intake</td>
<td></td>
</tr>
</table>

---

## Quick start

```bash
# Build (output lands in build/libs/; build.bat also copies it into build/)
./gradlew build          # or just double-click build.bat

# Start the dev server (it runs the engine self-test and prints the result)
./gradlew runServer

# Start the client
./gradlew runClient
```

To install on a real client / server, drop `build/libs/emerald_economy-<version>.jar` into `mods/`
(requires NeoForge 21.1.x for Minecraft 1.21.1).

**Your first time in game**:

1. Crafting: all five machines are "eight redstone components around one core item" — an emerald for the ATM,
   a redstone lamp for the Exchange Terminal, a clock for the Lottery Machine, an item frame for the
   Vending Machine, a cauldron for the Acquisition Machine. Picking up redstone for the first time unlocks
   all five recipes (searchable in the recipe book).
2. Place a machine on a redstone block (**required**) and right-click it to open its screen.
3. Want to try things out fast: `/ee give @s 10000` puts money straight into your account.

---

## Screen gallery

<table>
<tr>
<td align="center"><img src="素材/README截图/01_atm_before_deposit.png" width="330"><br><b>ATM</b>: deposit / withdraw / deposit all / withdraw all / loan / repay</td>
<td align="center"><img src="素材/README截图/31_atm_transactions.png" width="330"><br><b>Transactions</b>: balance details, with before &rarr; after</td>
</tr>
<tr>
<td align="center"><img src="素材/README截图/33_atm_records_debt.png" width="330"><br><b>Transactions &middot; Debt details</b> (the two categories are kept apart)</td>
<td align="center"><img src="素材/README截图/06_exchange.png" width="330"><br><b>Fund Exchange</b>: seven funds with price, change and chart</td>
</tr>
<tr>
<td align="center"><img src="素材/README截图/09_holdings.png" width="330"><br><b>My holdings</b>: shares / average cost / market value / unrealized P&L</td>
<td align="center"><img src="素材/README截图/10_market_news.png" width="330"><br><b>Market news</b>: weather and random events</td>
</tr>
<tr>
<td align="center"><img src="素材/README截图/11_lottery.png" width="330"><br><b>Lottery Machine</b>: three games; betting is blocked while you owe money</td>
<td align="center"><img src="素材/README截图/14_vending.png" width="330"><br><b>Vending Machine</b>: enchanted books by category, with level and applicable items</td>
</tr>
<tr>
<td align="center"><img src="素材/README截图/36_vending_other.png" width="330"><br><b>Vending Machine &middot; Other</b>: seeds / saplings (no unlock progression)</td>
<td align="center"><img src="素材/README截图/17_acquisition.png" width="330"><br><b>Acquisition Machine</b>: five permanent categories + Crazy Friday</td>
</tr>
<tr>
<td align="center"><img src="素材/README截图/37_acquire_misc.png" width="330"><br><b>Miscellaneous</b>: goat horn / music disc / shulker shell / horse armor&hellip;</td>
<td align="center"><img src="素材/README截图/38_acquire_livestock.png" width="330"><br><b>Livestock</b>: fish and meat, bone, eggs in batches of 8</td>
</tr>
<tr>
<td align="center"><img src="素材/README截图/26_crazy_friday.png" width="330"><br><b>Crazy Friday</b>: every 5th game day, three random items at 5–10×</td>
<td align="center"><img src="素材/README截图/20_atm_opened_by_click.png" width="330"><br><b>Real interaction</b>: right-click a machine (needs redstone power) to open it</td>
</tr>
</table>

---

## Gameplay details

### Shared account
- One account per player: **balance / debt (principal and interest tracked separately) / credit limit /
  transaction history / fund holdings**, all persisted in the world save.
- Deposits and withdrawals each cost a 1% fee. **Daily interest is 0.500%, and only starts at a balance
  of 200** (below that, none).
- Loans charge 2% daily interest (compounded); the default credit limit is 1000.

### ATM
- **Deposit all** moves every emerald in your inventory into the account in one go; **Withdraw all** turns
  your whole balance back into emeralds and **fails as a whole if your inventory cannot hold them**
  (nothing is dropped on the ground).
- The **Transactions** button opens the history screen, split into **Balance details** and
  **Debt details**; every entry shows **before &rarr; after**.

### Fund Exchange
- Seven funds: Mining / Agriculture / Livestock / Building / Hydro Power / Hunting, plus a Composite
  (a weighted index of the first six).
- Price engine: momentum decay &rarr; deviation &rarr; mean reversion &rarr; random noise &rarr; momentum
  clamp &rarr; hard bounds; it refreshes every 5 minutes by default.
- World supply moves the market: mining, logging, harvesting ripe crops, killing passive animals,
  killing monsters, pouring water / lava, and villager trades.
- Trades cost a 2% fee (minimum 1), with a 5-second cooldown per fund.

### Lottery Machine
- Three games: Odd / Even (1.95×), Small / Big (1.95×) and Last digit 0-9 (9.5×); one bet per game per day.
- The draw happens at the daily settlement and is broadcast server-wide; the "last draw" line spells out
  **whether this round's balance grew, and by how much**.
- **You cannot bet while a loan is unpaid** (the screen still opens, so odds and results stay visible).

### Vending Machine
- **Two groups**: Enchanted Books / Other. Books are laid out in five subcategories — Weapons / Ranged /
  Tools / Armor / General &amp; Curses — and each entry shows its **level** and **applicable items**.
  Prices come from the per-category ranges in the config (for example Sharpness 896-1280,
  Wind Burst 1560-2560).
- Book slots must be **unlocked by pooling money server-wide** (512-1280 per level, fixed for a given
  level); unlocking broadcasts which enchantment was unlocked.
- **Other** currently holds seeds (wheat seeds 8 each) and saplings (oak / birch 16 each); they ignore the
  unlock progression and are always available.
- Each slot is limited to 2 purchases per player per day.

### Acquisition Machine
- Five permanent categories: **Farm Produce / Livestock Produce (fish and meat, plus bone) /
  Minerals (including netherite scrap) / Forestry Produce / Miscellaneous** (goat horn, music disc,
  shulker shell, axolotl bucket, turtle scute, horse armor, slimeball, clay ball, blaze rod,
  phantom membrane). Categories, items and prices can be added or removed freely in `acquire.json`.
- **Items you do not have are greyed out** (each row also shows "Owned N"), and quantities below one full
  batch cannot be sold either — no more clicking just to be told no.
- **Batch buyout**: eggs and feathers need **8** for one batch (8 = 1 emerald), bamboo needs **4**;
  the screen writes it as "per 8 &rarr; 1". If the amount box holds less than one batch, the screen
  **switches to selling every full batch you have** (the number in the box follows along), so "I have
  16 eggs" plus one click sells 16 eggs for 2 emeralds.
- **Crazy Friday**: on every 5th game day, three rare items are picked at random and bought at 5–10×,
  with a server-wide broadcast. **You cannot take part while a loan is unpaid** (the list still shows,
  but Sell is not clickable).
- Buyout earnings go straight into your account, with no fee.

### Machine looks and mining
All five machines are **1×1×2 two-block-tall blocks** (facing whoever placed them). Models and textures are
made in Blockbench; the project files live in `素材/blockbench/*.bbmodel` and can be opened and edited
directly.

They mine like an iron block (hardness 5.0): **any tool works, a pickaxe is fastest**, and in survival
breaking either half drops exactly one machine (never lost, never doubled).

---

## Configuration

Server config: `<world>/serverconfig/emerald_economy-server.toml`
(on a dedicated server it lives under `config/`; in the dev environment it is `run/config/`).

In game: **ESC &rarr; Mods &rarr; Emerald Economy &rarr; Config**.
That button is **editable only by the server owner / an OP (permission level &ge; 2)** — everyone else just
sees a notice.

What you can tune:

| Section | Contents |
| --- | --- |
| `account` | deposit/withdraw fee rate, daily interest and the **balance threshold that starts it**, loan interest, credit limit, transaction history size |
| `market` | price interval, momentum and reversion parameters, fees, trade cooldown, per-fund base price / volatility / supply, weather table, news event table |
| `lottery` | odds for the three games, daily bet count |
| `shop` | initial slot count, upgrade cost range, **enchantment price and category table**, other goods table, daily purchase limit, excluded enchantments |
| `acquire` | price tables for the five categories, batch sizes, Crazy Friday interval / item pool / base prices and multipliers |

### What you sell / what it buys: two editable JSON files

The Vending Machine's shelves and the Acquisition Machine's price table live in `config/emerald_economy/`
(generated on first start):

| File | What you can change |
| --- | --- |
| `shop.json` | "group &rarr; subcategory &rarr; goods". Enchanted books use `enchants` (an `id` plus a `min`/`max` price range), ordinary goods use `items` (`item` + `price`). A name can be a translation key (`lang`) or plain text (`name`) |
| `acquire.json` | "category &rarr; entries". Each entry looks like `{"item":"minecraft:wheat","price":1}`, or `{"tag":"minecraft:saplings","price":1}` to accept a whole tag (vanilla item tags work); `"batch": 8` means eight make one batch; the trailing `friday` block holds Crazy Friday's pool and base prices |

**Run `/ee reload` in game and the changes apply immediately** (no restart; a broken file keeps the previous
contents and reports the error in the log).

```jsonc
// shop.json snippet: add a group / subcategory / one good
{
  "groups": [
    { "id": "OTHER", "lang": "shop.group.other", "name": "Other", "icon": "📦", "unlockable": false,
      "subcategories": [
        { "id": "SEED", "name": "Seeds", "items": [ { "item": "minecraft:wheat_seeds", "price": 8 } ] }
      ] }
  ]
}

// acquire.json snippet: add a category that buys every sapling (1 emerald per batch)
{
  "categories": [
    { "id": "AGRICULTURE", "name": "Farm Produce",
      "items": [ { "item": "minecraft:wheat", "price": 1 },
                 { "tag": "minecraft:saplings", "price": 1 },
                 { "item": "minecraft:bamboo", "price": 1, "batch": 4 } ] }
  ]
}
```

The default templates ship inside the mod jar (`data/emerald_economy/economy/`), and the generator script is
`tools/gen_economy_json.py`.

The remaining tables — written as `key=value` string lists — still live in the TOML file (weather modifiers,
random events, fund parameters):

```toml
weatherModifiers = ["RAIN:EMR_AGR=0.08", "THUNDER:EMR_HYD=0.090"]
marketEvents = ["EMR_MIN|0.08|news.vein_found", "ALL|-0.06|news.recession"]
```

> **Remember to delete the old config after changing defaults**: NeoForge only writes a default when a key is
> *missing*, so an existing key keeps whatever the file already holds. Delete
> `emerald_economy-server.toml` (back it up first) and restart.

---

## In-game commands (OP only)

| Command | What it does |
| --- | --- |
| `/ee balance [player]` | show balance / debt / credit limit |
| `/ee give <player> <amount>` / `/ee take <player> <amount>` | add or remove balance directly |
| `/ee price` | print the seven funds' price, base price, change and momentum |
| `/ee tick` | force one fund price refresh |
| `/ee settle` | **force a daily settlement** (see the draw, interest and news right away) |
| `/ee supply` | show today's supply per sector and the current weather |
| `/ee friday` | show Crazy Friday status |
| `/ee setprice <fund code> <price>` | set a fund's price directly |
| `/ee shop reset` | reset shop purchase limits |
| `/ee reload` | **re-read `shop.json` / `acquire.json`** — no restart needed after editing shelves and prices |

---

## Development

```bash
./gradlew build                    # compile
python tools/gen_resources.py      # regenerate blockstates / models / recipes / loot tables / recipe unlocks / acquisition tags
python tools/gen_bbmodel.py        # regenerate the Blockbench projects from the same geometry (素材/blockbench/*.bbmodel)
python tools/gen_economy_json.py   # regenerate the default content-config templates (shop.json / acquire.json)
python tools/check_lang.py         # check that zh_cn / en_us translation keys are complete (config screens included)
python tools/setup-dev-server.py   # set up run/ once (superflat + creative + OP); re-run after deleting run/
java tools/TextureGenerator.java   # generate the mod icon from 素材/logo.png (cut the white background + resize to 256)
```

**The single source of truth for machine models** is `machine_elements()` in `tools/gen_resources.py`.
To edit them by hand, open `素材/blockbench/*.bbmodel` and copy the geometry back into the script afterwards.

### Automated verification

```bash
python tools/setup-dev-server.py   # once; re-run after deleting run/
rm -rf run/world                   # the main case asserts absolute balances, so start from a clean save
./gradlew runServer                # terminal 1
./gradlew runClientUiTest          # terminal 2: real server, real button clicks, screenshots at the end
```

- `SelfTest` (the server run sets `-Dee.selftest=true`) runs **95 pure-logic assertions** on startup.
- The main `ClientUiTest` case has **123 assertions / 38 screenshots**, covering: opening and clicking every
  one of the nine screens, the redstone-power gate on all five machines, the seven kinds of world supply,
  the trade cooldown, failure paths and rule-based refusals, batch buyout and batch settlement, betting
  blocked while in debt and Crazy Friday blocked while in debt, before/after balances in the transaction
  history, sell buttons greyed out by inventory, editing the JSON plus `/ee reload` hot-reload, and mining a
  machine in survival always dropping exactly one.
- Four dedicated cases: `runClientUiTestShopFull` (shop fully unlocked), `runClientUiTestMarketEvents`
  (each random market event checked individually), `runClientUiTestOwner` + `runClientUiTestPeer`
  (two players online at once), and `runClientUiTestMine` (mining and drops only — a result in under a
  minute when you are debugging).

Screenshots are archived in `素材/验证截图/` (everything) and `素材/README截图/` (the ones used here).

Deeper development notes, design trade-offs and the pitfalls we hit are in [JOB.md](JOB.md).
