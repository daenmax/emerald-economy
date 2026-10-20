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

1. Deposit the emeralds in your inventory into your account at the **ATM** (1% fee). The ATM also lets
   you **store/withdraw experience**, claim a daily **online reward**, and check the live
   **balance / experience leaderboards**, where you can **transfer money** or **gift XP** to other players.
2. Trade seven funds on the **Fund Exchange** to profit from price swings — prices are pushed by mining,
   logging, harvesting, mob kills, villager trades and the day's weather, plus a
   **random ±5% mood correction every day**, so no fund is a guaranteed riser.
3. Bet your balance on the **Lottery Machine**; the draw happens at the next daily settlement.
4. Spend your balance at the **Vending Machine** on max-level enchanted books, seeds, saplings and
   **XP points**.
5. Sell what you mine, farm and kill to the **Acquisition Machine** — and on every 5th game day,
   **Crazy Friday** pays 5–10× for a few rare items. Enchanted gear and books can also be
   **recycled for emeralds**.
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

1. Crafting: all five machines share **one recipe** and differ only in the center slot — see
   **Crafting recipes** below. Picking up redstone for the first time unlocks all five recipes
   (searchable in the recipe book).
2. Place a machine on a redstone block (**required**) and right-click it to open its screen.
3. Want to try things out fast: `/ee give @s 10000` puts money straight into your account.

---

## Crafting recipes

All five machines share **one recipe**; only the center slot changes — eight redstone components
surrounding each machine's own core item.

|  |  |  |
| :---: | :---: | :---: |
| Redstone Dust | Sticky Piston | Hopper |
| Observer | **Core item** | Lever |
| Redstone Repeater | Chest | Redstone Comparator |

The core item for each machine (the center slot):

| Machine | Core item |
| --- | --- |
| ATM | Emerald |
| Exchange Terminal | Redstone Lamp |
| Lottery Machine | Clock |
| Vending Machine | Item Frame |
| Acquisition Machine | Cauldron |

Each recipe yields one machine. Picking up redstone for the first time unlocks all five, and searching
"emerald" in the recipe book finds them too.

---

## Screen gallery

<table>
<tr>
<td align="center"><img src="素材/README截图/01_atm_overview.png" width="330"><br><b>ATM</b>: balance / credit limit / debt / available credit / stored XP + six function pages</td>
<td align="center"><img src="素材/README截图/02_transactions_balance.png" width="330"><br><b>Transactions &middot; Balance details</b>: multi-day deposits, loans and daily interest, before &rarr; after</td>
</tr>
<tr>
<td align="center"><img src="素材/README截图/03_transactions_experience.png" width="330"><br><b>Transactions &middot; XP details</b>: deposit / withdraw / online reward / gifts, with XP before &rarr; after</td>
<td align="center"><img src="素材/README截图/04_transactions_debt.png" width="330"><br><b>Transactions &middot; Debt details</b>: debt changes from loans and repayments</td>
</tr>
<tr>
<td align="center"><img src="素材/README截图/05_atm_experience_storage.png" width="330"><br><b>ATM &middot; Experience storage</b>: enter levels, see stored XP, store/withdraw everything</td>
<td align="center"><img src="素材/README截图/06_atm_online_reward.png" width="330"><br><b>ATM &middot; Online reward</b>: online at settlement, grants 50 + game day XP</td>
</tr>
<tr>
<td align="center"><img src="素材/README截图/07_ranking_balance.png" width="330"><br><b>Balance ranking</b>: refreshed when opened, showing net / balance / debt, with transfers</td>
<td align="center"><img src="素材/README截图/08_ranking_experience.png" width="330"><br><b>Experience ranking</b>: sorted by stored XP, XP column only, with gifts</td>
</tr>
<tr>
<td align="center"><img src="素材/README截图/09_exchange_multiday.png" width="330"><br><b>Fund Exchange</b>: day and total change columns plus a multi-day chart</td>
<td align="center"><img src="素材/README截图/10_exchange_holdings.png" width="330"><br><b>My holdings</b>: shares / average cost / market value / unrealized P&amp;L after several days</td>
</tr>
<tr>
<td align="center"><img src="素材/README截图/11_exchange_sell.png" width="330"><br><b>Sell dialog</b>: shows my shares and supports selling everything</td>
<td align="center"><img src="素材/README截图/12_market_news.png" width="330"><br><b>Market news</b>: weather, random events and settlement records across many days</td>
</tr>
<tr>
<td align="center"><img src="素材/README截图/13_lottery.png" width="330"><br><b>Lottery Machine</b>: three games; betting is blocked while you owe money</td>
<td align="center"><img src="素材/README截图/14_vending_enchants.png" width="330"><br><b>Vending Machine</b>: enchanted books by category, with level and applicable items</td>
</tr>
<tr>
<td align="center"><img src="素材/README截图/15_vending_other.png" width="330"><br><b>Vending &middot; Other</b>: seeds / saplings / experience / misc, all unlimited</td>
<td align="center"><img src="素材/README截图/16_vending_experience.png" width="330"><br><b>Vending &middot; Experience</b>: 10 / 100 / 1000 XP points, unlimited</td>
</tr>
<tr>
<td align="center"><img src="素材/README截图/17_acquisition.png" width="330"><br><b>Acquisition Machine</b>: farm produce with Sell / Sell all on every row</td>
<td align="center"><img src="素材/README截图/18_acquisition_misc.png" width="330"><br><b>Misc category</b>: ominous banner, ominous bottle, one music-disc row&hellip;</td>
</tr>
<tr>
<td align="center"><img src="素材/README截图/19_acquisition_livestock.png" width="330"><br><b>Livestock category</b>: fish and meat, bone, eggs in batches of 8</td>
<td align="center"><img src="素材/README截图/20_crazy_friday.png" width="330"><br><b>Crazy Friday</b>: 3 of 9 pool entries; any spawn egg is one whole-class row</td>
</tr>
<tr>
<td align="center"><img src="素材/README截图/21_enchant_recycle.png" width="330"><br><b>Enchantment recycle</b>: itemised enchant prices and whole-stack total</td>
<td align="center"><img src="素材/README截图/22_machine_opened_by_click.png" width="330"><br><b>Real interaction</b>: right-click a machine (needs redstone power) to open it</td>
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
- The main screen shows only **balance / credit limit / debt / available credit / stored XP**, followed
  by six buttons: **Deposit & withdraw, Loans & repayment, Transactions, Experience storage, Online reward,
  Leaderboard & transfer**. Fees, interest and thresholds live on the relevant sub-screen instead of the
  main page.
- **Deposit all** moves every emerald in your inventory into the account in one go; **Withdraw all** turns
  your whole balance back into emeralds and **fails as a whole if your inventory cannot hold them**
  (nothing is dropped on the ground).
- The **Transactions** button opens the history screen, split into **Balance details / Debt details /
  XP details**; every entry shows **before &rarr; after**. The XP tab uses **XP** as its amount unit.
- **Experience storage** (off by default): you enter **levels**, but the ATM stores **XP points**.
  For example, at level 27 entering 1 moves the XP from your current bar plus everything needed to reach
  level 28 into the ATM, leaving you at exactly level 26 with an empty bar. **Store all / Withdraw all**
  move everything at once. Every player must **unlock it for 1000** first, after which there are no limits:
  you cannot store more than your current level, nor withdraw more than the ATM holds.
- **Online reward**: eligibility is reset at the moment interest is settled each day, and **only players
  online at that moment** get it for that day (offline means no eligibility, and no claiming it later);
  the reward is **50 base + current game day** XP (day 38 gives 88), claimable once per day. The screen
  states whether it is claimable, already claimed, or unavailable today.
- **Leaderboard**: two tabs — **balance ranking** (sorted by balance, showing net / balance / debt) and
  **experience ranking** (sorted by stored ATM XP and showing only the XP column). The list is
  **recomputed live every time the page is opened** instead of waiting for the daily settlement; your own
  row is highlighted.
- **Player transfers**: every balance-ranking row has a **Send** button, so you can transfer any amount to
  any player. The fee is taken out of the amount: **sending 1000 with a 1% fee credits the recipient 990**.
  The fee rate is configurable.
- **XP gifts**: every experience-ranking row has a **Gift** button; it sends stored ATM XP to the recipient
  with **no fee**.
- **Transfers and gifts require the recipient to be online**: offline players have a greyed-out button and
  an "(offline)" marker, and the server validates it again.

### Fund Exchange
- Seven funds: Mining / Agriculture / Livestock / Building / Hydro Power / Hunting, plus a Composite
  (a weighted index of the first six).
- Price engine: momentum decay &rarr; deviation &rarr; mean reversion &rarr; random noise &rarr; momentum
  clamp &rarr; hard bounds; it refreshes every 5 minutes by default.
- World supply moves the market: mining, logging, harvesting ripe crops, killing passive animals,
  killing monsters, pouring water / lava, and villager trades.
- **Every settlement also rolls a ±5% random mood correction per fund** (`dailyRandomDrift`): even if
  nobody does anything, prices still move, and no fund can trend in one direction forever — so
  "buy whatever is rising and do nothing" cannot work.
- The list has **two change columns**: **Day** = current price vs the last daily close; **Total** = current
  price vs the fund's base price. Day change resets at every settlement, while Total shows the fund's
  performance since launch.
- Opening the sell screen shows **your current shares** and offers a **Sell all** button.
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
- **Other** has four subcategories: **Seeds** (wheat seeds 8 each), **Saplings** (oak / birch 16 each),
  **Experience** (sells **XP points**: 10 for 100, 100 for 800, 1000 for 6000) and
  **Misc** (currently glass at 1 balance each). They ignore the unlock progression, are always available,
  and have **no purchase limit**.
- XP goods give **experience points, not levels**: 1000 points is roughly what it takes to go from level 0
  to level 26 (for reference, level 0 to 30 needs 1395 points). The points go straight onto your XP bar.
- Only **enchanted book slots** are limited to 2 purchases per player per day; the **Other** group is unlimited.

### Acquisition Machine
- Five permanent categories: **Farm Produce / Livestock Produce (fish and meat, plus bone) /
  Minerals (including netherite scrap) / Forestry Produce (logs plus saplings, flowers, mushrooms,
  cactus) / Miscellaneous (including ominous banners and ominous bottles)**, plus a sixth special category,
  **Enchantment recycle**. Categories, items
  and prices can be added or removed freely in `acquire.json`; the full default price list is in
  **Default values and price lists** below.
- **Whole-tag rows take a single line**: music discs (any disc, 3 each), saplings, flowers, mushrooms and
  fish each occupy one row instead of listing dozens of individual items. Selling accepts any item
  from that class, and you can even mix several kinds in one sale. Horse armors are **not** merged: they
  are four separate entries by quality — leather 10, iron 50, golden 100, diamond 200.
- Each row has **Sell** and **Sell all**: the first uses the amount box, while the second asks the server
  to sell everything actually available (accurate even for treasure maps and whole-tag rows).
- **Items you do not have are greyed out** (each row also shows "Owned N"), and quantities below one full
  batch cannot be sold either — no more clicking just to be told no.
- **Batch buyout**: eggs, feathers and wheat / beetroot / pumpkin / melon seeds need **8** for one batch
  (8 = 1 emerald); the screen writes it as "per 8 &rarr; 1". If the amount box holds less than one batch,
  the screen **switches to selling every full batch you have** (the number in the box follows along),
  so "I have 16 eggs" plus one click sells 16 eggs for 2 emeralds.
- **Enchantment recycle** lists every enchanted item in your inventory (hotbar included; enchanted books
  count too). Each row shows the icon, name, stack size, the recycle price of every enchantment and the
  whole-stack total. Only enchantments matter, never the base material:
  **vending min price / 3 / max level × current level**.
  Example: Mending min price 1560, max level 1, level 1 &rarr; 520; Protection min price 896, max level 4,
  level 3 &rarr; 224; total 744. Clicking **Recycle** opens a **second confirmation**, and only then is
  the whole stack consumed and credited.
- **Treasure maps** are only accepted when they carry a structure marker (woodland mansion / ocean
  monument / buried treasure); ordinary explored maps and empty maps are rejected.
- **Crazy Friday**: on every 5th game day, three rare items are picked at random and bought at 5–10×,
  and the broadcast **includes the price of every item**. The pool has **9 entries**: nether star,
  elytra, trident, heart of the sea, echo shard, monster spawner, ominous bottle,
  **all mob heads** and **any spawn egg**. Three entries are rolled first; whole-class entries occupy
  a single row, so **a Friday contains at most one head row and one spawn-egg row**.
  - **All mob heads**: skeleton skull, wither skeleton skull, zombie head, creeper head, piglin head and
    **dragon head** — six kinds (the dragon head lives inside this entry: no separate line, no separate
    roll), base price **83**.
  - **Any spawn egg**: the `*_spawn_egg` wildcard matches vanilla and **modded** spawn eggs alike,
    base price **40**. When picked, the screen shows one row and any spawn egg in your inventory sells at
    that row's price. The monster spawner base price is now **40** as well.
  **You cannot take part while a loan is unpaid** (the list still shows, but Sell is not clickable).
- Buyout earnings go straight into your account, with no fee.

### Machine looks and mining
All five machines are **1×1×2 two-block-tall blocks** (facing whoever placed them). Models and textures are
made in Blockbench; the project files live in `素材/blockbench/*.bbmodel` and can be opened and edited
directly.

They mine like an iron block (hardness 5.0): **any tool works, a pickaxe is fastest**, and in survival
breaking either half drops exactly one machine (never lost, never doubled).

---

## Default values and price lists

Everything below is **the mod's own default configuration**, generated by
`tools/gen_readme_tables.py` straight from `EEConfig.java` and `tools/gen_economy_json.py`
(nothing is copied by hand). After changing a default, re-run the script and replace this section.
Values inside an existing save / config file are not updated automatically — see the note under
**Configuration**.

### ATM / account
| Item | Default |
| --- | --- |
| Deposit fee | 1.00% |
| Withdraw fee | 1.00% |
| Daily balance interest | 0.500% |
| Interest threshold | 200 |
| Loan interest (compound) | 2.00% |
| Credit limit | 1000 |
| Records kept | 100 |
| Experience storage unlock (once per player) | 1000 |
| Online reward XP | 50 + game day × 1 (online at settlement required) |
| Player transfer fee | 1.00% |

### Fund exchange
| Fund | Base price | Volatility | Mean reversion | Expected daily supply |
| --- | --- | --- | --- | --- |
| Mining | 100 | 6.0% | 3.0% | 800 |
| Farming | 80 | 4.5% | 2.5% | 1200 |
| Livestock | 90 | 4.0% | 2.2% | 600 |
| Building | 70 | 3.5% | 2.0% | 1500 |
| Hydro | 85 | 5.0% | 2.5% | 700 |
| Hunting | 95 | 5.5% | 2.8% | 500 |
| Composite | 100 | 2.0% | 2.0% | 200 |

| Item | Default |
| --- | --- |
| Price bounds | base × 0.3 ~ × 3.0 |
| Price update interval | 6000 ticks (5 in-game minutes) |
| Momentum decay | 0.95 |
| Max momentum | 15.0% |
| World event sensitivity | 0.05 |
| Weather strength | 1 |
| Daily random drift (rolled per fund) | ±5.0% |
| Random market event chance | 15% |
| Fund trade fee | 2% (minimum 1) |
| Trade cooldown per fund | 5000 ms |

### Lottery
| Game | Odds | Bets per day |
| --- | --- | --- |
| Odd or even | 1.95 | 1 |
| Big or small | 1.95 | 1 |
| Last digit | 9.50 | 1 |

### Vending machine
| Item | Default |
| --- | --- |
| Initial unlocked slots | 1 |
| Daily purchase limit per book slot (Other is unlimited) | 2 |
| Upgrade cost (stable per level) | 512 - 1280 |
| Fallback price for unlisted enchants | 256 - 1280 |
| Fallback treasure multiplier | ×3 - ×5 |

**Enchanted book prices** (max level books; the price is stable per enchant id)
| Category | Enchant (max level) | Price range |
| --- | --- | --- |
| Weapon | Sharpness 5 | 896 - 1280 |
| Weapon | Smite 5 | 896 - 1280 |
| Weapon | Bane of Arthropods 5 | 896 - 1280 |
| Weapon | Sweeping Edge 3 | 896 - 1280 |
| Weapon | Knockback 2 | 896 - 1280 |
| Weapon | Fire Aspect 2 | 896 - 1280 |
| Weapon | Looting 3 | 896 - 1280 |
| Weapon | Impaling 5 | 256 - 640 |
| Weapon | Loyalty 3 | 384 - 768 |
| Weapon | Channeling 1 | 896 - 1280 |
| Weapon | Riptide 3 | 896 - 1280 |
| Weapon | Breach 4 | 896 - 1280 |
| Weapon | Density 5 | 896 - 1280 |
| Weapon | Wind Burst 3 | 1560 - 2560 |
| Ranged | Power 5 | 896 - 1280 |
| Ranged | Punch 2 | 512 - 896 |
| Ranged | Flame 1 | 896 - 1280 |
| Ranged | Infinity 1 | 896 - 1280 |
| Ranged | Piercing 4 | 320 - 704 |
| Ranged | Quick Charge 3 | 384 - 768 |
| Ranged | Multishot 1 | 896 - 1280 |
| Tool | Efficiency 5 | 896 - 1280 |
| Tool | Silk Touch 1 | 896 - 1280 |
| Tool | Fortune 3 | 896 - 1280 |
| Tool | Luck of the Sea 3 | 384 - 768 |
| Tool | Lure 3 | 384 - 768 |
| Armor | Protection 4 | 896 - 1280 |
| Armor | Fire Protection 4 | 896 - 1280 |
| Armor | Blast Protection 4 | 896 - 1280 |
| Armor | Projectile Protection 4 | 896 - 1280 |
| Armor | Feather Falling 4 | 896 - 1280 |
| Armor | Depth Strider 3 | 896 - 1280 |
| Armor | Frost Walker 2 | 1560 - 2560 |
| Armor | Soul Speed 3 | 1560 - 2560 |
| Armor | Respiration 3 | 896 - 1280 |
| Armor | Aqua Affinity 1 | 896 - 1280 |
| Armor | Swift Sneak 3 | 1560 - 2560 |
| Armor | Thorns 3 | 896 - 1280 |
| Generic | Unbreaking 3 | 896 - 1280 |
| Generic | Mending 1 | 1560 - 2560 |
| Generic | Curse of Binding 1 | 1560 - 2560 |
| Generic | Curse of Vanishing 1 | 1560 - 2560 |

**"Other" shelf goods** (fixed prices)
| Subcategory | Good | Price |
| --- | --- | --- |
| Seeds | Wheat Seeds | 8 |
| Saplings | Oak Sapling | 16 |
| Saplings | Birch Sapling | 16 |
| Experience | 10 XP points | 100 |
| Experience | 100 XP points | 800 |
| Experience | 1000 XP points | 6000 |
| Misc | Glass | 1 |

### Acquisition machine
| Item | Default |
| --- | --- |
| Crazy Friday interval | every 5 game days |
| Premium items picked per day | 3 |
| Daily multiplier | ×5 - ×10 |

**Buyout prices by category** (price per unit; batch = items per unit)
| Category | Item / whole tag | Price | Batch |
| --- | --- | --- | --- |
| Farm produce | Wheat | 1 | 1 |
| Farm produce | Carrot | 1 | 1 |
| Farm produce | Potato | 1 | 1 |
| Farm produce | Poisonous Potato | 10 | 1 |
| Farm produce | Beetroot | 1 | 1 |
| Farm produce | Sugar Cane | 2 | 1 |
| Farm produce | Melon | 4 | 1 |
| Farm produce | Pumpkin | 4 | 1 |
| Farm produce | Apple | 4 | 1 |
| Farm produce | Sweet Berries | 1 | 1 |
| Farm produce | Nether Wart | 2 | 1 |
| Farm produce | Cocoa Beans | 1 | 1 |
| Farm produce | Wheat Seeds | 1 | 8 |
| Farm produce | Beetroot Seeds | 1 | 8 |
| Farm produce | Pumpkin Seeds | 1 | 8 |
| Farm produce | Melon Seeds | 1 | 8 |
| Livestock produce | Raw Beef | 2 | 1 |
| Livestock produce | Raw Porkchop | 2 | 1 |
| Livestock produce | Raw Chicken | 2 | 1 |
| Livestock produce | Raw Mutton | 2 | 1 |
| Livestock produce | Raw Rabbit | 2 | 1 |
| Livestock produce | Leather | 2 | 1 |
| Livestock produce | White Wool | 1 | 1 |
| Livestock produce | Egg | 1 | 8 |
| Livestock produce | Milk Bucket | 3 | 1 |
| Livestock produce | Honey Bottle | 3 | 1 |
| Livestock produce | Feather | 1 | 8 |
| Livestock produce | Bone | 1 | 1 |
| Livestock produce | Pufferfish | 20 | 1 |
| Livestock produce | Turtle Egg | 20 | 1 |
| Livestock produce | Armadillo Scute | 10 | 1 |
| Livestock produce | Fish (any, excluding pufferfish) | 2 | 1 |
| Minerals | Coal | 1 | 1 |
| Minerals | Raw Iron | 2 | 1 |
| Minerals | Raw Copper | 2 | 1 |
| Minerals | Raw Gold | 4 | 1 |
| Minerals | Redstone Dust | 1 | 1 |
| Minerals | Lapis Lazuli | 2 | 1 |
| Minerals | Diamond | 15 | 1 |
| Minerals | Nether Quartz | 1 | 1 |
| Minerals | Amethyst Shard | 15 | 1 |
| Minerals | Flint | 1 | 1 |
| Minerals | Obsidian | 3 | 1 |
| Minerals | Crying Obsidian | 5 | 1 |
| Minerals | Block of Amethyst | 2 | 1 |
| Minerals | Iron Ingot | 4 | 1 |
| Minerals | Copper Ingot | 4 | 1 |
| Minerals | Gold Ingot | 8 | 1 |
| Minerals | Netherite Scrap | 10 | 1 |
| Forestry produce | Oak Log | 1 | 1 |
| Forestry produce | Spruce Log | 1 | 1 |
| Forestry produce | Birch Log | 1 | 1 |
| Forestry produce | Jungle Log | 1 | 1 |
| Forestry produce | Acacia Log | 1 | 1 |
| Forestry produce | Dark Oak Log | 1 | 1 |
| Forestry produce | Mangrove Log | 1 | 1 |
| Forestry produce | Cherry Log | 1 | 1 |
| Forestry produce | Block of Bamboo | 1 | 1 |
| Forestry produce | Crimson Stem | 1 | 1 |
| Forestry produce | Warped Stem | 1 | 1 |
| Forestry produce | Sapling (any) | 1 | 1 |
| Forestry produce | Flower (any) | 1 | 1 |
| Forestry produce | Mushroom (any) | 1 | 1 |
| Forestry produce | Cactus | 1 | 1 |
| Forestry produce | Chorus Flower | 5 | 1 |
| Forestry produce | Chorus Fruit | 1 | 1 |
| Misc | Music disc (any) | 3 | 1 |
| Misc | Ominous Banner (white banner) | 50 | 1 |
| Misc | Ominous Bottle | 50 | 1 |
| Misc | Goat Horn | 3 | 1 |
| Misc | Shulker Shell | 20 | 1 |
| Misc | Sculk Sensor | 20 | 1 |
| Misc | Bucket of Axolotl | 8 | 1 |
| Misc | Turtle Shell | 5 | 1 |
| Misc | Leather Horse Armor | 10 | 1 |
| Misc | Iron Horse Armor | 50 | 1 |
| Misc | Golden Horse Armor | 100 | 1 |
| Misc | Diamond Horse Armor | 200 | 1 |
| Misc | Ghast Tear | 20 | 1 |
| Misc | Slimeball | 2 | 1 |
| Misc | Magma Cream | 5 | 1 |
| Misc | Clay Ball | 1 | 1 |
| Misc | Blaze Rod | 5 | 1 |
| Misc | Phantom Membrane | 4 | 1 |
| Misc | Rotten Flesh | 1 | 1 |
| Misc | Spider Eye | 1 | 1 |
| Misc | Treasure map (with a structure marker) | 20 | 1 |
| Misc | Spectral Arrow | 10 | 1 |
| Misc | Arrow | 1 | 1 |
| Misc | Nautilus Shell | 50 | 1 |
| Misc | Cobweb | 5 | 1 |

**Crazy Friday pool** (base price × 5-10 = actual buyout price)
| Pool entry | Base price |
| --- | --- |
| Nether Star | 133 |
| Elytra | 100 |
| Trident | 75 |
| Heart of the Sea | 58 |
| Echo Shard | 33 |
| Monster Spawner | 40 |
| Ominous Bottle | 42 |
| Mob heads (6 kinds: skeleton / wither skeleton / zombie / creeper / piglin / dragon) | 83 |
| Spawn egg (any, modded included) | 40 |


---

## Configuration

Server config: `<world>/serverconfig/emerald_economy-server.toml`
(on a dedicated server it lives under `config/`; in the dev environment it is `run/config/`).

> **Server-side only.** The TOML and `config/emerald_economy/*.json` are read by the server and synced to
> clients in snapshots. Other players **do not copy any config files**; they only need the same mod jar
> version (matching protocol version).

In game: **ESC &rarr; Mods &rarr; Emerald Economy &rarr; Config**.
That button is **editable only by the server owner / an OP (permission level &ge; 2)** — everyone else just
sees a notice.

What you can tune:

| Section | Contents |
| --- | --- |
| `account` | deposit/withdraw fee rate, daily interest and the **balance threshold that starts it**, loan interest, credit limit, transaction history size, **experience storage unlock cost**, **online reward base XP and per-day step**, **player transfer fee rate** |
| `market` | price interval, momentum and reversion parameters, **daily random drift**, fees, trade cooldown, per-fund base price / volatility / supply, weather table, news event table |
| `lottery` | odds for the three games, daily bet count |
| `shop` | initial slot count, upgrade cost range, **enchantment price and category table**, other goods table, daily purchase limit, excluded enchantments |
| `acquire` | Crazy Friday interval / item pool / base prices and multipliers; per-category price tables live in `acquire.json` |

### What you sell / what it buys: two editable JSON files

The Vending Machine's shelves and the Acquisition Machine's price table live in `config/emerald_economy/`
(generated on first start):

| File | What you can change |
| --- | --- |
| `shop.json` | "group &rarr; subcategory &rarr; goods". Enchanted books use `enchants` (an `id` plus a `min`/`max` price range); ordinary goods use `items` (`item` + `price`); **XP goods** also live in `items`, written as `exp` + `price` (buying them adds XP points directly). A name can be a translation key (`lang`) or plain text (`name`) |
| `acquire.json` | "category &rarr; entries". Each entry looks like `{"item":"minecraft:wheat","price":1}`, or `{"tag":"minecraft:saplings","price":1}` to accept a whole tag (vanilla or this mod's item tags; **a tag entry takes a single row on screen**); `"batch": 8` means eight make one batch; `"lang"` names that row; `"filter": "treasure_map"` accepts only treasure maps with a structure marker; the trailing `friday` block holds Crazy Friday's pool (plain items, `#tags` or `*` wildcards; `*_spawn_egg` means the whole "any spawn egg" class), `poolMaxPicks` (how many items a single pool entry may contribute per day) and `basePrices`. **The enchantment recycle category is generated by scanning the player's inventory and is not written in this file** |

**Run `/ee reload` in game and the changes apply immediately** (no restart; a broken file keeps the previous
contents and reports the error in the log).

> **Upgrading from an older version (level storage &rarr; experience storage)**: the server config key
> `onlineRewardLevels` is gone; use `onlineRewardBaseExp` (default 50) and `onlineRewardPerDayExp`
> (default 1). Leaving the old key in place is harmless but it does nothing. The world save's
> `storedLevels` is converted to XP on load (new key `storedXp`), so no manual save editing is needed.
> The `acquire.json` changes (melon / pumpkin / apple / ominous bottle / Friday's any-spawn-egg row and
> spawner base price) must be applied by hand or by using the new default `acquire.json`.

> If you already generated these two JSON files in an older version, **new goods / entries do not appear
> automatically** (the server only writes the default template when the file is missing). To pick up the new
> defaults, delete `config/emerald_economy/shop.json` and `acquire.json` (back them up first) and let the
> server regenerate them, or add the entries by hand using the format above.

`shop.json` snippet: add a group / subcategory / two goods (one item, one XP good).

```json
{
  "groups": [
    {
      "id": "OTHER",
      "lang": "shop.group.other",
      "name": "Other",
      "icon": "📦",
      "unlockable": false,
      "subcategories": [
        { "id": "SEED", "name": "Seeds", "items": [ { "item": "minecraft:wheat_seeds", "price": 8 } ] },
        { "id": "MISC", "name": "Misc", "items": [ { "exp": 1000, "price": 6000 } ] }
      ]
    }
  ]
}
```

`acquire.json` snippet: add a category that buys every sapling (1 emerald per batch, one row on screen).

```json
{
  "categories": [
    {
      "id": "FORESTRY",
      "name": "Forestry Produce",
      "items": [
        { "item": "minecraft:oak_log", "price": 1 },
        { "tag": "minecraft:saplings", "price": 1, "lang": "acquire.tag.saplings" },
        { "item": "minecraft:filled_map", "price": 20,
          "filter": "treasure_map", "lang": "acquire.item.treasure_map" }
      ]
    }
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

## FAQ & configuration tutorials (beginner-friendly)

This section only covers things that **cannot (or should not) be edited in the in-game mod config GUI**:
`shop.json`, `acquire.json`, item tags (datapacks) and the list-style TOML parameters.
Single numeric values such as fees, interest, odds, purchase limits and fund base prices should be changed in
**ESC &rarr; Mods &rarr; Emerald Economy &rarr; Config**.

### 0. Five rules before editing any file

1. **Where the files are**
   - Single-player: `.minecraft/config/emerald_economy/shop.json` and `acquire.json`
     (on Windows `.minecraft` is usually under `%APPDATA%\.minecraft`; modpack instances use their own folder)
   - Dedicated server: `config/emerald_economy/` in the server root
   - These two files are shared by the whole instance / server; they do not live inside a world save.
   - In multiplayer only the **server** files matter. Clients do not need to edit anything, but they do need
     the mods that provide the items, otherwise icons / names may not render.
2. **Back up first**: copy `shop.json` to `shop.json.bak` so you can restore it.
3. **It must be valid JSON**
   - Edit with VS Code / Notepad++ and save as **UTF-8 (no BOM)**;
   - Do not use `//` or `#` comments; the `"_comment"` fields in the default files are ordinary strings and
     can stay. (A `#` inside a string is a tag, e.g. `"#minecraft:saplings"`; that is not a comment.)
   - Do not leave a trailing comma after the last entry;
   - If unsure, paste the file into an online JSON validator.
4. **Run `/ee reload` after editing** (OP required). Success prints "content loaded: vending ...; acquisition ...".
   If it says a file failed to parse, the old content is kept; search `logs/latest.log` for `[内容] 解析`.
5. **How to find an item ID**
   - Type `/give @s ` in chat and press Tab; the game lists every item ID;
   - Or enable `F3 + H` advanced tooltips and hover an item;
   - The format is always `namespace:item_name`, for example `minecraft:wheat` or `farmersdelight:tomato`;
   - The part before the colon is the mod ID;
   - Only item IDs are accepted — no NBT, enchantments, custom names or counts.

> In the snippets below `...` means "keep the existing entries"; do not write `...` or comments in the real file.

### 1. Acquisition Machine: adding crops / modded items

#### 1.1 Add to an existing category (easiest)

For example, add Farmer's Delight tomatoes to `AGRICULTURE`:

```json
{
  "item": "farmersdelight:tomato",
  "price": 2
}
```

Run `/ee reload` and the machine shows "Tomato 2 each".

#### 1.2 Batch pricing

```json
{
  "item": "farmersdelight:tomato",
  "price": 2,
  "batch": 4
}
```

Four tomatoes make one batch worth 2 emeralds. Selling 10 tomatoes pays for 2 batches (8 consumed, 4 emeralds),
leaving 2 in your inventory.

#### 1.3 Create a new category

```json
{
  "id": "MOD_CROPS",
  "name": "Mod Crops",
  "items": [
    { "item": "farmersdelight:tomato", "price": 2 },
    { "item": "farmersdelight:cabbage", "price": 2 },
    { "item": "farmersdelight:onion", "price": 2 }
  ]
}
```

Put this object into the top-level `"categories"` array of `acquire.json` (mind the commas).
- The `id` can be anything except the reserved `FRIDAY` and `ENCHANT`;
- On-screen order follows file order;
- Use `name` for custom categories; an invented `lang` key will display as an untranslated key.

#### 1.4 Whole-tag buying (advanced)

```json
{
  "tag": "minecraft:saplings",
  "price": 1,
  "name": "Saplings (any)"
}
```

The tag must actually exist in game. Vanilla tags look like `minecraft:saplings`; NeoForge common tags often
use the `c:` prefix. A tag row takes one line and accepts any member, even mixed kinds.
**In a normal category the `tag` field does not take `#`** (write `minecraft:saplings`); `#` is only used in
Crazy Friday's `pool`. If you are unsure whether a tag exists, list the items individually.

#### 1.5 Remove an entry

Delete the `{ ... }` entry and run `/ee reload`. Do not use `"price": 0` to disable it — prices are clamped to
a minimum of 1, so it would still be bought.

### 2. Vending Machine: adding goods / subcategories / groups

#### 2.1 Add an ordinary good to an existing subcategory

```json
{
  "id": "GENERAL",
  "name": "Misc",
  "items": [
    { "item": "minecraft:glass", "price": 1 },
    { "item": "farmersdelight:tomato", "price": 3 }
  ]
}
```

One purchase gives **one item**; prices are account balance, not emeralds in your inventory; the per-player
daily limit is 2 per slot (change it in the TOML / config GUI).

#### 2.2 Create a subcategory

```json
{
  "id": "FOOD",
  "name": "Food",
  "icon": "🍎",
  "items": [
    { "item": "farmersdelight:tomato", "price": 3 },
    { "item": "farmersdelight:cabbage", "price": 3 }
  ]
}
```

Put it into the `subcategories` array of the `OTHER` group. IDs must be unique (existing ones are `SEED`,
`SAPLING`, `MISC`, `GENERAL`).

#### 2.3 Create a group

```json
{
  "id": "MOD_FOOD",
  "name": "Mod Food",
  "icon": "🍲",
  "unlockable": false,
  "subcategories": [
    {
      "id": "COOKED",
      "name": "Cooked",
      "items": [ { "item": "farmersdelight:hamburger", "price": 5 } ]
    }
  ]
}
```

Always use `"unlockable": false` for a normal group. `true` is only for enchanted books.

#### 2.4 XP goods

```json
{ "exp": 100, "price": 800 }
```

This gives **100 XP points** (not 100 levels) for 800 balance.

#### 2.5 Enchanted books (advanced, be careful)

```json
{ "id": "minecraft:mending", "min": 1560, "max": 2560 }
```

Adding an enchantment changes the total unlock slots (and the upgrade costs of the later slots), and its
minimum price also affects enchantment recycling (recycle price = min price / 3 / max level × current level).
Leave the enchantment table alone unless you know what you are doing.

#### 2.6 Custom names: use `name`, not an invented `lang`

`lang` is a translation key and gets the `emerald_economy.` prefix. For your own categories / rows, use
`"name": "Plain text"`. A missing `lang` key displays as `emerald_economy.xxx`.

### 3. Crazy Friday: adding single items / prices / pick limits (advanced)

`friday.pool` accepts plain item IDs, `#tags`, and `*` wildcards. The default `*_spawn_egg` wildcard
means "any spawn egg" (modded included) and appears as one whole-class row, so any spawn egg in your
inventory sells at that row's price. Add a single item:

```json
"pool": [
  "minecraft:nether_star",
  "minecraft:dragon_egg"
],
"basePrices": {
  "minecraft:nether_star": 133,
  "minecraft:dragon_egg": 200
}
```

Entries without a `basePrices` value use the fallback price 100.

Limit how many items one pool entry may contribute per day:

```json
"poolMaxPicks": {
  "#emerald_economy:acquire/heads": 1
}
```

The default is 1. Raising the head entry to 2 can produce two heads in one Friday.

Use `/ee friday` to see today's picks. For quick testing, temporarily set the Crazy Friday interval to 1 in
the config GUI, or advance the game time to a multiple of 5 days.

### 4. Advanced: create your own item tag (datapack)

1. Create a folder in the world's `datapacks/`, for example `my_tags/`;
2. Add `my_tags/pack.mcmeta`:

```json
{
  "pack": {
    "pack_format": 48,
    "description": "My custom item tags"
  }
}
```

3. Add `my_tags/data/mymod/tags/item/my_crops.json`:

```json
{
  "values": [
    "farmersdelight:tomato",
    "farmersdelight:cabbage",
    "farmersdelight:onion"
  ]
}
```

4. In `acquire.json`:

```json
{
  "tag": "mymod:my_crops",
  "price": 2,
  "name": "Mod vegetables (any)"
}
```

5. Run `/reload` (datapacks), then `/ee reload`.

The namespace (`mymod` above) can be anything, but it must use lowercase letters / digits / underscores and
match the folder path. If you do not want to make a datapack, list the items individually.

### 5. Advanced: list-style TOML parameters

These are visible in the config GUI but are long string lists, which are painful to edit there. The TOML file
is easier:

- Single-player: `saves/<world>/serverconfig/emerald_economy-server.toml`
- Dedicated server: `config/emerald_economy-server.toml`

```toml
# Per-fund base price: CODE=price
fundBasePrices = ["EMR_MIN=100", "EMR_AGR=80", "EMR_LIV=90", "EMR_BLD=70", "EMR_HYD=85", "EMR_HUN=95", "EMR_CMP=100"]

# Per-fund volatility: CODE=decimal
fundVolatilities = ["EMR_MIN=0.060", "EMR_AGR=0.045", "EMR_LIV=0.040"]

# Weather correction: WEATHER:FUND=delta
weatherModifiers = ["RAIN:EMR_AGR=0.080", "THUNDER:EMR_HYD=0.090"]

# Random market event: SCOPE|delta|lang-key
marketEvents = ["EMR_MIN|0.08|news.vein_found", "ALL|-0.06|news.recession"]
```

Fund codes: `EMR_MIN` mining, `EMR_AGR` farming, `EMR_LIV` livestock, `EMR_BLD` building, `EMR_HYD` hydro,
`EMR_HUN` hunting, `EMR_CMP` composite. Weather: `CLEAR`, `RAIN`, `THUNDER`. Event scope is `ALL` or a fund
code; use an existing `news.xxx` key (custom text needs a resource pack). Restart the server after editing
TOML; changes made in the config GUI usually apply immediately.

### 6. Configuration not working? Check in this order

1. **Are you editing the server-side file?** Client edits do nothing in multiplayer.
2. **Did you run `/ee reload`?** JSON needs it; TOML needs a restart.
3. **Is the JSON valid?** Check `logs/latest.log` for `[内容] 解析`; common mistakes are missing/extra commas,
   missing quotes, or curly Chinese quotes.
4. **Is the item ID correct?** Verify with `/give` autocomplete; IDs are lowercase and include the namespace.
5. **Is the mod installed on the server?** Items come from the server registry; if the server lacks the mod,
   the item does not exist.
6. **Is the file from an old version?** Upgrades do not merge new defaults; back up, delete and regenerate,
   or add the entries manually.
7. **Is the tag empty?** A missing tag or a tag with no members is skipped.
8. **Are category / subcategory IDs duplicated?** Keep them unique; do not use `FRIDAY` / `ENCHANT` as
   acquisition category IDs.
9. **Still seeing old content?** Close and reopen the machine screen; `/ee reload` refreshes open screens, but
   reopening is the safest check.

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
python tools/gen_readme_tables.py en   # regenerate the tables in "Default values and price lists" (zh for Chinese)
python tools/check_lang.py         # check that zh_cn / en_us translation keys are complete (config screens included)
python tools/setup-dev-server.py   # set up run/ once (superflat + creative + OP); re-run after deleting run/
java tools/TextureGenerator.java   # generate the mod icon from 素材/logo.png (cut the white background + resize to 256)
```

**The single source of truth for machine models** is `machine_elements()` in `tools/gen_resources.py`.
To edit them by hand, open `素材/blockbench/*.bbmodel` and copy the geometry back into the script afterwards.

**The single source of truth for prices and numbers** is `EEConfig.java` (values) and
`tools/gen_economy_json.py` (what is sold / bought). The **Default values and price lists** section is
generated by `gen_readme_tables.py`: after changing a default, re-run the script and replace that section
(its vanilla Chinese names need `build/vanilla_lang/zh_cn.json`; the script header has the command).

### Automated verification

```bash
python tools/setup-dev-server.py   # once; re-run after deleting run/
rm -rf run/world                   # the main case asserts absolute balances, so start from a clean save
./gradlew runServer                # terminal 1
./gradlew runClientUiTest          # terminal 2: real server, real button clicks, screenshots at the end
```

- `SelfTest` (the server run sets `-Dee.selftest=true`) runs pure-logic assertions on startup
  (1.21.1 XP math, legacy level-to-XP migration, Crazy Friday pool sampling).
- The main `ClientUiTest` case connects a real client to the server and takes screenshots, covering: opening and clicking every
  one of the nine screens, the redstone-power gate on all five machines, the seven kinds of world supply,
  the trade cooldown, failure paths and rule-based refusals, batch buyout and batch settlement,
  **whole-tag buyout (one row selling any music disc / sapling / flower / mushroom)**,
  **ATM experience storage (locked by default &rarr; paid unlock &rarr; store/withdraw XP by level) and the online
  reward (only granted to players online at the settlement)**, **buying XP goods (points +1000, balance
  -6000)**, betting blocked while in debt and Crazy Friday blocked while in debt,
  **the before/after values in all three transaction tabs (balance / debt / XP)**, **both leaderboards
  and XP gifts**, sell buttons greyed out by inventory, editing the JSON plus `/ee reload` hot-reload,
  and mining a machine in survival always dropping exactly one.
  It then fast-forwards the game to day 15 (running `/ee settle` once per day) to capture the
  **multi-day chart with mixed green/red changes, multi-day transaction history, the day-15 Crazy
  Friday, a lottery screen that accepts bets, enchantment recycle, both leaderboards and the sell dialog**.
  **v1.0.3 was re-run from a clean save: 171 assertions / 56 screenshots, 0 failures.**
- Four dedicated cases: `runClientUiTestShopFull` (shop fully unlocked), `runClientUiTestMarketEvents`
  (each random market event checked individually), `runClientUiTestOwner` + `runClientUiTestPeer`
  (two players online at once), and `runClientUiTestMine` (mining and drops only — a result in under a
  minute when you are debugging).

Screenshots are archived in `素材/验证截图/` (all 56 screenshots from the clean-save run plus the
test log) and `素材/README截图/` (the 22 selected here, numbered 01-22).

Deeper development notes, design trade-offs and the pitfalls we hit are in [JOB.md](JOB.md).
