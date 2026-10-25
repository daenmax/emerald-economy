# -*- coding: utf-8 -*-
"""生成 README 里「默认数值 / 价格总表」那一节的 markdown。

这一节的数字全部来自代码里的真源，不手抄：
  * 数值参数：`EEConfig.java` 里 `defineInRange("键", 默认值, ...)` 的默认值
  * 内容表：`tools/gen_economy_json.py` 里的附魔 / 商品 / 收购价 / 疯狂星期五池

物品与附魔的名字取自原版语言文件：
  * 英文名直接从 `build/moddev/artifacts/*-client-extra-aka-minecraft-resources.jar` 里读
  * 中文名需要 `assets/minecraft/lang/zh_cn.json`（原版客户端运行时才下载，仓库里没有），
    放到下面任一位置即可：
      build/vanilla_lang/zh_cn.json
      tools/vanilla_lang/zh_cn.json
      %EE_VANILLA_LANG_DIR%/zh_cn.json
    获取方式（走本机 HTTP 代理，1.21.1 资源镜像）：
      curl -x http://127.0.0.1:7890 -o build/vanilla_lang/zh_cn.json 
        https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.21.1/assets/minecraft/lang/zh_cn.json
    找不到中文文件时中文表格会退回英文名，数字部分不受影响。

用法：
  python tools/gen_readme_tables.py zh > build/readme-tables-zh.md
  python tools/gen_readme_tables.py en > build/readme-tables-en.md
然后把生成的表格复制进 README.md / README_EN.md 的「默认数值与价格总表」一节。
"""
import io
import json
import os
import re
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import gen_economy_json as content  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONFIG_JAVA = os.path.join(ROOT, 'src', 'main', 'java', 'cn', 'daenx', 'mc', 'emeraldeconomy',
                           'config', 'EEConfig.java')
CLIENT_EXTRA = os.path.join(ROOT, 'build', 'moddev', 'artifacts',
                            'neoforge-21.1.259-client-extra-aka-minecraft-resources.jar')
VANILLA_HINTS = [
    os.path.join(ROOT, 'build', 'vanilla_lang'),
    os.path.join(ROOT, 'tools', 'vanilla_lang'),
    os.environ.get('EE_VANILLA_LANG_DIR') or '',
]

LANG = {}

FUND_NAMES = {
    'EMR_MIN': ('矿业基金', 'Mining'),
    'EMR_AGR': ('农业基金', 'Farming'),
    'EMR_LIV': ('畜牧基金', 'Livestock'),
    'EMR_BLD': ('建筑基金', 'Building'),
    'EMR_HYD': ('水电基金', 'Hydro'),
    'EMR_HUN': ('狩猎基金', 'Hunting'),
    'EMR_CMP': ('综合基金', 'Composite'),
}
CATEGORY_NAMES = {
    'AGRICULTURE': ('农业产物', 'Farm produce'),
    'LIVESTOCK': ('畜牧产物', 'Livestock produce'),
    'MINING': ('矿物', 'Minerals'),
    'FORESTRY': ('林业产物', 'Forestry produce'),
    'MISC': ('杂物', 'Misc'),
}
OTHER_NAMES = {
    'SEED': ('种子', 'Seeds'),
    'SAPLING': ('树苗', 'Saplings'),
    'MISC': ('经验', 'Experience'),
    'GENERAL': ('杂物', 'Misc'),
}
TAG_NAMES = {
    'minecraft:saplings': ('树苗（任意）', 'Sapling (any)'),
    'emerald_economy:acquire/fishes': ('鱼（任意，不含河豚）', 'Fish (any, excluding pufferfish)'),
    'emerald_economy:acquire/flowers': ('花（任意）', 'Flower (any)'),
    'emerald_economy:acquire/mushrooms': ('蘑菇（任意）', 'Mushroom (any)'),
    'emerald_economy:acquire/music_discs': ('音乐唱片（任意）', 'Music disc (any)'),
    'emerald_economy:acquire/horse_armors': ('马铠（任意）', 'Horse armor (any)'),
    'emerald_economy:acquire/heads': (
        '怪物的头颅（6 种：骷髅 / 凋灵骷髅 / 僵尸 / 苦力怕 / 猪灵 / 龙首）',
        'Mob heads (6 kinds: skeleton / wither skeleton / zombie / creeper / piglin / dragon)'),
}
ENTRY_NAMES = {
    'acquire.item.ominous_banner': ('不祥旗帜（白色旗帜）', 'Ominous Banner (white banner)'),
    'acquire.item.treasure_map': ('藏宝图（带结构标记）', 'Treasure map (with a structure marker)'),
}


def load_vanilla_lang(lang):
    """读原版语言文件（找不到就返回空表，调用方退回物品 ID）。"""
    for hint in VANILLA_HINTS:
        if hint and os.path.isfile(os.path.join(hint, '%s.json' % lang)):
            with io.open(os.path.join(hint, '%s.json' % lang), encoding='utf-8') as handle:
                return json.load(handle)
    if lang == 'en_us' and os.path.isfile(CLIENT_EXTRA):
        with zipfile.ZipFile(CLIENT_EXTRA) as jar:
            with jar.open('assets/minecraft/lang/en_us.json') as handle:
                return json.loads(handle.read().decode('utf-8'))
    return {}


def enchant_name(enchant_id, lang):
    short = enchant_id.split(':')[-1]
    return LANG.get('enchantment.minecraft.%s' % short) or short


def item_name(item_id, lang):
    short = item_id.split(':')[-1]
    name = LANG.get('item.minecraft.%s' % short) or LANG.get('block.minecraft.%s' % short)
    return name or short


def enchant_max_level(enchant_id):
    """从原版数据里读附魔的等级上限（商店卖的都是满级书）。"""
    short = enchant_id.split(':')[-1]
    if not os.path.isfile(CLIENT_EXTRA):
        return 1
    with zipfile.ZipFile(CLIENT_EXTRA) as jar:
        try:
            with jar.open('data/minecraft/enchantment/%s.json' % short) as handle:
                return int(json.loads(handle.read().decode('utf-8')).get('max_level', 1))
        except KeyError:
            return 1


def config_defaults():
    """从 EEConfig.java 里抠出 `defineInRange("键", 默认值, ...)` 的默认值。"""
    source = io.open(CONFIG_JAVA, encoding='utf-8').read()
    out = {}
    for match in re.finditer(r'defineInRange\(\s*"([A-Za-z0-9_]+)"\s*,\s*([^,]+),', source):
        value = match.group(2).strip().rstrip('dDfFlL')
        if value in ('true', 'false'):
            out[match.group(1)] = value
            continue
        try:
            out[match.group(1)] = float(value) if ('.' in value) else int(value)
        except ValueError:
            out[match.group(1)] = value
    return out


def list_defaults(name):
    """读 `defineList("键", defaults("A=1", "B=2"), ...)` 的默认值。"""
    source = io.open(CONFIG_JAVA, encoding='utf-8').read()
    match = re.search(r'defineList\(\s*"%s"\s*,\s*defaults\((.*?)\)\s*,' % name, source, re.S)
    if not match:
        return []
    return re.findall(r'"([^"]+)"', match.group(1))


def fnum(value):
    """整数就写成整数，小数去掉多余的 0。"""
    if isinstance(value, float) and value == int(value):
        return str(int(value))
    return str(value)


def table(header, rows):
    lines = ['| ' + ' | '.join(header) + ' |',
             '| ' + ' | '.join(['---'] * len(header)) + ' |']
    for row in rows:
        lines.append('| ' + ' | '.join(str(cell) for cell in row) + ' |')
    return '\n'.join(lines)


def build(lang):
    global LANG
    LANG = load_vanilla_lang(lang)
    cfg = config_defaults()
    zh = lang == 'zh_cn'
    out = []

    def L(zh_text, en_text):
        """按语言挑一个：中文版里不出现英文标签，英文版里不出现中文。"""
        return zh_text if zh else en_text

    # ---------------- 账户 / ATM ----------------
    out.append(L('### ATM / 账户', '### ATM / account'))
    out.append(table([L('项目', 'Item'), L('默认值', 'Default')], [
        (L('存款手续费率', 'Deposit fee'), '%.2f%%' % (cfg['depositFeeRate'] * 100)),
        (L('取款手续费率', 'Withdraw fee'), '%.2f%%' % (cfg['withdrawFeeRate'] * 100)),
        (L('账户余额日息', 'Daily balance interest'), '%.3f%%' % (cfg['dailyInterestRate'] * 100)),
        (L('计息起算余额', 'Interest threshold'), cfg['interestMinBalance']),
        (L('贷款日息（复利）', 'Loan interest (compound)'), '%.2f%%' % (cfg['loanInterestRate'] * 100)),
        (L('信用额度', 'Credit limit'), cfg['creditLimit']),
        (L('保留流水条数', 'Records kept'), cfg['maxTransactionRecords']),
        (L('经验存取解锁费用（每人一次）', 'Experience storage unlock (once per player)'),
         cfg['levelUnlockCost']),
        (L('在线奖励经验值', 'Online reward XP'),
         L('%s + 游戏日 × %s（结算时在线才能领）'
           % (cfg['onlineRewardBaseExp'], cfg['onlineRewardPerDayExp']),
           '%s + game day x %s (online at settlement required)'
           % (cfg['onlineRewardBaseExp'], cfg['onlineRewardPerDayExp']))),
        (L('玩家转账手续费率', 'Player transfer fee'), '%.2f%%' % (cfg['transferFeeRate'] * 100)),
    ]))

    # ---------------- 基金 ----------------
    out.append('')
    out.append(L('### 基金交易所', '### Fund exchange'))
    base, vol, mean, supply = {}, {}, {}, {}
    for key, target in (('fundBasePrices', base), ('fundVolatilities', vol),
                        ('fundMeanReversions', mean), ('fundDailySupplies', supply)):
        for line in list_defaults(key):
            name, value = line.split('=')
            target[name] = float(value)
    rows = []
    for code in FUND_NAMES:
        rows.append((L(FUND_NAMES[code][0], FUND_NAMES[code][1]), fnum(base.get(code)),
                     '%.1f%%' % (vol.get(code, 0) * 100),
                     '%.1f%%' % (mean.get(code, 0) * 100), int(supply.get(code, 0))))
    out.append(table([L('基金', 'Fund'), L('基准价', 'Base price'), L('波动率', 'Volatility'),
                      L('均值回归', 'Mean reversion'), L('每日预期供给量', 'Expected daily supply')], rows))
    out.append('')
    minutes = fnum(cfg['priceUpdateIntervalTicks'] / 1200)
    out.append(table([L('项目', 'Item'), L('默认值', 'Default')], [
        (L('价格上下界', 'Price bounds'),
         L('基准价 × %.1f ~ × %.1f' % (cfg['priceMinFactor'], cfg['priceMaxFactor']),
           'base × %.1f ~ × %.1f' % (cfg['priceMinFactor'], cfg['priceMaxFactor']))),
        (L('价格更新间隔', 'Price update interval'),
         L('%s 刻（游戏内 %s 分钟）' % (cfg['priceUpdateIntervalTicks'], minutes),
           '%s ticks (%s in-game minutes)' % (cfg['priceUpdateIntervalTicks'], minutes))),
        (L('动量衰减系数', 'Momentum decay'), '%.2f' % cfg['momentumDecay']),
        (L('单次最大波动幅度', 'Max momentum'), '%.1f%%' % (cfg['maxMomentum'] * 100)),
        (L('世界事件灵敏度', 'World event sensitivity'), fnum(cfg['eventSensitivity'])),
        (L('天气修正强度', 'Weather strength'), fnum(cfg['weatherStrength'])),
        (L('每日随机情绪修正（每支基金各随机一次）', 'Daily random drift (rolled per fund)'),
         '±%.1f%%' % (cfg['dailyRandomDrift'] * 100)),
        (L('随机市场事件概率', 'Random market event chance'),
         '%.0f%%' % (cfg['marketEventChance'] * 100)),
        (L('每日涨跌幅上限（涨停 / 跌停）', 'Daily price limit (limit-up / limit-down)'),
         '±%.0f%%' % (cfg['dailyLimitRate'] * 100)),
        (L('基金交易手续费', 'Fund trade fee'),
         L('%.0f%%（最低 1）' % (cfg['tradeFeeRate'] * 100),
           '%.0f%% (minimum 1)' % (cfg['tradeFeeRate'] * 100))),
        (L('同一支基金的交易冷却', 'Trade cooldown per fund'),
         L('%s 毫秒' % cfg['tradeCooldownMillis'], '%s ms' % cfg['tradeCooldownMillis'])),
        (L('每人每日买入份额上限（所有基金合计，0 = 不限）',
           'Daily buy share limit per player (all funds, 0 = none)'),
         L('%s 份' % cfg['dailyBuyShareLimit'], '%s shares' % cfg['dailyBuyShareLimit'])),
        (L('每人每日卖出份额上限（所有基金合计，0 = 不限）',
           'Daily sell share limit per player (all funds, 0 = none)'),
         L('%s 份' % cfg['dailySellShareLimit'], '%s shares' % cfg['dailySellShareLimit'])),
    ]))

    # ---------------- 抽奖 ----------------
    out.append('')
    out.append(L('### 抽奖机', '### Lottery'))
    out.append(table([L('场次', 'Game'), L('赔率', 'Odds'), L('每日下注次数', 'Bets per day')], [
        (L('猜单双', 'Odd or even'), '%.2f' % cfg['oddsOddEven'], cfg['dailyBetLimit']),
        (L('猜大小', 'Big or small'), '%.2f' % cfg['oddsBigSmall'], cfg['dailyBetLimit']),
        (L('猜尾数', 'Last digit'), '%.2f' % cfg['oddsLastDigit'], cfg['dailyBetLimit']),
    ]))

    # ---------------- 自动售货机 ----------------
    out.append('')
    out.append(L('### 自动售货机', '### Vending machine'))
    out.append(table([L('项目', 'Item'), L('默认值', 'Default')], [
        (L('初始解锁格子数', 'Initial unlocked slots'), cfg['initialSlots']),
        (L('每个附魔书格子每日限购次数（「其他」不限量）',
           'Daily purchase limit per book slot (Other is unlimited)'), cfg['dailyPurchaseLimit']),
        (L('升级费用（同一级价格固定）', 'Upgrade cost (stable per level)'),
         '%s - %s' % (cfg['upgradeCostMin'], cfg['upgradeCostMax'])),
        (L('未列表附魔的兜底价格', 'Fallback price for unlisted enchants'),
         '%s - %s' % (cfg['priceMin'], cfg['priceMax'])),
        (L('未列表宝藏附魔的倍率', 'Fallback treasure multiplier'),
         '×%s - ×%s' % (fnum(cfg['treasureMultiplierMin']), fnum(cfg['treasureMultiplierMax']))),
        (L('附魔书分类解锁费用（每人一次）', 'Enchanted-book category unlock (once per player)'),
         cfg['enchantBookUnlockCost']),
    ]))
    out.append('')
    out.append(L('**附魔书价格区间**（每本都是满级书，价格在区间内按附魔 ID 稳定随机）',
                 '**Enchanted book prices** (max level books; the price is stable per enchant id)'))
    rows = []
    for sub_id, lang_key, name, icon, enchants in content.ENCHANT_GROUPS:
        for enchant_id, lo, hi in enchants:
            rows.append((L(name, sub_id.title()),
                         '%s %s' % (enchant_name(enchant_id, lang), enchant_max_level(enchant_id)),
                         '%s - %s' % (lo, hi)))
    out.append(table([L('分类', 'Category'), L('附魔（满级）', 'Enchant (max level)'),
                      L('价格区间', 'Price range')], rows))
    out.append('')
    out.append(L('**「其他」货架的商品**（固定价）', '**"Other" shelf goods** (fixed prices)'))
    rows = []
    for sub_id, lang_key, name, icon, items in content.OTHER_GROUPS:
        for spec in items:
            if 'exp' in spec:
                label = L('%s 点经验值' % spec['exp'], '%s XP points' % spec['exp'])
            else:
                label = item_name(spec['item'], lang)
            rows.append((L(OTHER_NAMES[sub_id][0], OTHER_NAMES[sub_id][1]), label, spec['price']))
    out.append(table([L('子分类', 'Subcategory'), L('商品', 'Good'), L('价格', 'Price')], rows))

    # ---------------- 自动收购机 ----------------
    out.append('')
    out.append(L('### 自动收购机', '### Acquisition machine'))
    out.append(table([L('项目', 'Item'), L('默认值', 'Default')], [
        (L('疯狂星期五开放间隔', 'Crazy Friday interval'),
         L('每 %s 个游戏日（逢 %s 开放）' % (cfg['fridayIntervalDays'], cfg['fridayIntervalDays']),
           'every %s game days' % cfg['fridayIntervalDays'])),
        (L('每日随机重金收购件数', 'Premium items picked per day'), cfg['fridayPickCount']),
        (L('当日收购倍率', 'Daily multiplier'),
         '×%s - ×%s' % (fnum(cfg['fridayMultiplierMin']), fnum(cfg['fridayMultiplierMax']))),
        (L('附魔回收解锁费用（每人一次）', 'Enchant recycle unlock (once per player)'),
         cfg['enchantRecycleUnlockCost']),
        (L('疯狂星期五解锁费用（每人一次）', 'Crazy Friday unlock (once per player)'),
         cfg['crazyFridayUnlockCost']),
    ]))
    out.append('')
    out.append(L('**各分类收购价**（价格是「每份」的价；批次 = 几个算一份）',
                 '**Buyout prices by category** (price per unit; batch = items per unit)'))
    rows = []
    for cat_id, lang_key, name, items in content.ACQUIRE_CATEGORIES:
        for row in items:
            item, tag, price, batch, name_key = row[:5]
            if tag:
                label = TAG_NAMES.get(tag, (tag, tag))[0 if zh else 1]
            elif name_key:
                pair = ENTRY_NAMES.get(name_key, (item, item))
                label = pair[0 if zh else 1]
            else:
                label = item_name(item, lang)
            rows.append((L(CATEGORY_NAMES[cat_id][0], CATEGORY_NAMES[cat_id][1]), label, price,
                         batch if batch else 1))
    out.append(table([L('分类', 'Category'), L('物品 / 整类', 'Item / whole tag'),
                      L('价格', 'Price'), L('批次', 'Batch')], rows))
    out.append('')
    out.append(L('**疯狂星期五随机池**（基准价 × 当日 %.0f-%.0f 倍 = 实际收购价）'
                 % (cfg['fridayMultiplierMin'], cfg['fridayMultiplierMax']),
                 '**Crazy Friday pool** (base price × %.0f-%.0f = actual buyout price)'
                 % (cfg['fridayMultiplierMin'], cfg['fridayMultiplierMax'])))
    rows = []
    for entry in content.FRIDAY_POOL:
        key = entry.lower()
        if entry.startswith('#'):
            label = TAG_NAMES.get(entry[1:], (entry, entry))[0 if zh else 1]
        elif entry == '*_spawn_egg':
            label = L('刷怪蛋（任意，含模组）', 'Spawn egg (any, incl. mods)')
        elif '*' in entry:
            label = L('通配匹配的物品', 'Wildcard-matched items')
        else:
            label = item_name(entry, lang)
        rows.append((label, content.FRIDAY_BASE_PRICES.get(key, 100)))
    out.append(table([L('池子条目', 'Pool entry'), L('基准价', 'Base price')], rows))
    return '\n'.join(out)


def main():
    lang = sys.argv[1] if len(sys.argv) > 1 else 'zh'
    if lang not in ('zh', 'en'):
        print('用法：python tools/gen_readme_tables.py [zh|en]')
        return 2
    text = build('zh_cn' if lang == 'zh' else 'en_us')
    if lang == 'zh' and not LANG:
        sys.stderr.write('警告：没找到原版 zh_cn.json，物品名会退回英文\n')
    sys.stdout.write(text + '\n')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
