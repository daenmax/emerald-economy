# -*- coding: utf-8 -*-
"""生成模组内置的「内容配置」默认模板。\n\n产物（同时写两份：jar 里的默认模板 + 开发环境 config 目录）：\nsrc/main/resources/data/emerald_economy/economy/shop.json     自动售货机的货架\nsrc/main/resources/data/emerald_economy/economy/acquire.json  自动收购机的价格表\n\n这两个 JSON 是**运行时以 config/emerald_economy/ 下的同名文件为准**的：\n服务端第一次启动会把这里的模板复制过去，之后玩家直接编辑 config 里的那份，\n游戏里 `/ee reload` 立刻生效。\n\n用法：python tools/gen_economy_json.py\n"""
import io
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'emerald_economy', 'economy')

# 附魔书：分类 -> (翻译键, 显示名, 图标, [附魔...])
ENCHANT_GROUPS = [
    ('WEAPON', 'shop.category.weapon', '武器类', '🗡', [
        ('minecraft:sharpness', 896, 1280),
        ('minecraft:smite', 896, 1280),
        ('minecraft:bane_of_arthropods', 896, 1280),
        ('minecraft:sweeping_edge', 896, 1280),
        ('minecraft:knockback', 896, 1280),
        ('minecraft:fire_aspect', 896, 1280),
        ('minecraft:looting', 896, 1280),
        ('minecraft:impaling', 256, 640),
        ('minecraft:loyalty', 384, 768),
        ('minecraft:channeling', 896, 1280),
        ('minecraft:riptide', 896, 1280),
        ('minecraft:breach', 896, 1280),
        ('minecraft:density', 896, 1280),
        ('minecraft:wind_burst', 1560, 2560),
    ]),
    ('RANGED', 'shop.category.ranged', '远程类', '🏹', [
        ('minecraft:power', 896, 1280),
        ('minecraft:punch', 512, 896),
        ('minecraft:flame', 896, 1280),
        ('minecraft:infinity', 896, 1280),
        ('minecraft:piercing', 320, 704),
        ('minecraft:quick_charge', 384, 768),
        ('minecraft:multishot', 896, 1280),
    ]),
    ('TOOL', 'shop.category.tool', '工具类', '🛠', [
        ('minecraft:efficiency', 896, 1280),
        ('minecraft:silk_touch', 896, 1280),
        ('minecraft:fortune', 896, 1280),
        ('minecraft:luck_of_the_sea', 384, 768),
        ('minecraft:lure', 384, 768),
    ]),
    ('ARMOR', 'shop.category.armor', '盔甲类', '🛡', [
        ('minecraft:protection', 896, 1280),
        ('minecraft:fire_protection', 896, 1280),
        ('minecraft:blast_protection', 896, 1280),
        ('minecraft:projectile_protection', 896, 1280),
        ('minecraft:feather_falling', 896, 1280),
        ('minecraft:depth_strider', 896, 1280),
        ('minecraft:frost_walker', 1560, 2560),
        ('minecraft:soul_speed', 1560, 2560),
        ('minecraft:respiration', 896, 1280),
        ('minecraft:aqua_affinity', 896, 1280),
        ('minecraft:swift_sneak', 1560, 2560),
        ('minecraft:thorns', 896, 1280),
    ]),
    ('GENERIC', 'shop.category.generic', '通用与诅咒', '💎', [
        ('minecraft:unbreaking', 896, 1280),
        ('minecraft:mending', 1560, 2560),
        ('minecraft:binding_curse', 1560, 2560),
        ('minecraft:vanishing_curse', 1560, 2560),
    ]),
]

# 「其他」大分类：子分类 -> (翻译键, 显示名, 图标, [商品...])
# 商品有两种写法：
#   {'item': 'minecraft:wheat_seeds', 'price': 8}   买一件物品
#   {'exp': 1000, 'price': 6000}                    买一份经验点（注意是经验值，不是等级）
OTHER_GROUPS = [
    ('SEED', 'shop.sub.seed', '种子', '🌱', [
        {'item': 'minecraft:wheat_seeds', 'price': 8},
    ]),
    ('SAPLING', 'shop.sub.sapling', '树苗', '🌳', [
        {'item': 'minecraft:oak_sapling', 'price': 16},
        {'item': 'minecraft:birch_sapling', 'price': 16},
    ]),
    ('MISC', 'shop.sub.exp', '经验', '🧪', [
        {'exp': 10, 'price': 100},
        {'exp': 100, 'price': 800},
        {'exp': 1000, 'price': 6000},
    ]),
    ('GENERAL', 'shop.sub.misc', '杂物', '🧱', [
        {'item': 'minecraft:glass', 'price': 1},
    ]),
]

# 收购分类：id -> (翻译键, 显示名, [(物品, 标签, 价格, 批次, 翻译键)...])
#   物品 / 标签二选一；标签条目在界面上只占一行，卖这一类里的任意一种都行；
#   翻译键可选，写它是为了让「整类收购」的那一行有个好听的名字。
ACQUIRE_CATEGORIES = [
    ('AGRICULTURE', 'acquire.category.agriculture', '农业产物', [
        ('minecraft:wheat', None, 1, None, None),
        ('minecraft:carrot', None, 1, None, None),
        ('minecraft:potato', None, 1, None, None),
        ('minecraft:poisonous_potato', None, 10, None, None),
        ('minecraft:beetroot', None, 1, None, None),
        ('minecraft:sugar_cane', None, 2, None, None),
        ('minecraft:melon', None, 4, None, None),
        ('minecraft:pumpkin', None, 4, None, None),
        ('minecraft:apple', None, 4, None, None),
        ('minecraft:sweet_berries', None, 1, None, None),
        ('minecraft:nether_wart', None, 2, None, None),
        ('minecraft:cocoa_beans', None, 1, None, None),
        # 种子：8 个一份（和鸡蛋一样），1 份 = 1 绿宝石
        ('minecraft:wheat_seeds', None, 1, 8, None),
        ('minecraft:beetroot_seeds', None, 1, 8, None),
        ('minecraft:pumpkin_seeds', None, 1, 8, None),
        ('minecraft:melon_seeds', None, 1, 8, None),
    ]),
    ('LIVESTOCK', 'acquire.category.livestock', '畜牧产物', [
        ('minecraft:beef', None, 2, None, None),
        ('minecraft:porkchop', None, 2, None, None),
        ('minecraft:chicken', None, 2, None, None),
        ('minecraft:mutton', None, 2, None, None),
        ('minecraft:rabbit', None, 2, None, None),
        ('minecraft:leather', None, 2, None, None),
        ('minecraft:white_wool', None, 1, None, None),
        ('minecraft:egg', None, 1, 8, None),
        ('minecraft:milk_bucket', None, 3, None, None),
        ('minecraft:honey_bottle', None, 3, None, None),
        ('minecraft:feather', None, 1, 8, None),
        ('minecraft:bone', None, 1, None, None),
        # 河豚从「鱼类」标签里独立出来，单独定价
        ('minecraft:pufferfish', None, 20, None, None),
        ('minecraft:turtle_egg', None, 20, None, None),
        ('minecraft:armadillo_scute', None, 10, None, None),
        # 鱼类标签换成本模组自己的（原版 #minecraft:fishes 含河豚）
        (None, 'emerald_economy:acquire/fishes', 2, None, 'acquire.tag.fishes'),
    ]),
    ('MINING', 'acquire.category.mining', '矿物', [
        ('minecraft:coal', None, 1, None, None),
        ('minecraft:raw_iron', None, 2, None, None),
        ('minecraft:raw_copper', None, 2, None, None),
        ('minecraft:raw_gold', None, 4, None, None),
        ('minecraft:redstone', None, 1, None, None),
        ('minecraft:lapis_lazuli', None, 2, None, None),
        ('minecraft:diamond', None, 15, None, None),
        ('minecraft:quartz', None, 1, None, None),
        ('minecraft:amethyst_shard', None, 15, None, None),
        ('minecraft:flint', None, 1, None, None),
        ('minecraft:obsidian', None, 3, None, None),
        ('minecraft:crying_obsidian', None, 5, None, None),
        ('minecraft:amethyst_block', None, 2, None, None),
        ('minecraft:iron_ingot', None, 4, None, None),
        ('minecraft:copper_ingot', None, 4, None, None),
        ('minecraft:gold_ingot', None, 8, None, None),
        ('minecraft:netherite_scrap', None, 10, None, None),
    ]),
    ('FORESTRY', 'acquire.category.forestry', '林业产物', [
        ('minecraft:oak_log', None, 1, None, None),
        ('minecraft:spruce_log', None, 1, None, None),
        ('minecraft:birch_log', None, 1, None, None),
        ('minecraft:jungle_log', None, 1, None, None),
        ('minecraft:acacia_log', None, 1, None, None),
        ('minecraft:dark_oak_log', None, 1, None, None),
        ('minecraft:mangrove_log', None, 1, None, None),
        ('minecraft:cherry_log', None, 1, None, None),
        ('minecraft:bamboo_block', None, 1, None, None),
        ('minecraft:crimson_stem', None, 1, None, None),
        ('minecraft:warped_stem', None, 1, None, None),
        # 「整类」条目：界面上只占一行，背包里有这一类里的任意一种都能卖
        (None, 'minecraft:saplings', 1, None, 'acquire.tag.saplings'),
        (None, 'emerald_economy:acquire/flowers', 1, None, 'acquire.tag.flowers'),
        (None, 'emerald_economy:acquire/mushrooms', 1, None, 'acquire.tag.mushrooms'),
        ('minecraft:cactus', None, 1, None, None),
        ('minecraft:chorus_flower', None, 5, None, None),
        ('minecraft:chorus_fruit', None, 1, None, None),
    ]),
    ('MISC', 'acquire.category.misc', '杂物', [
        (None, 'emerald_economy:acquire/music_discs', 3, None, 'acquire.tag.music_discs'),
        # 「不祥旗帜」在游戏里就是「白色旗帜」这一件物品（名字与花纹写在物品数据里），
        # 本模组按物品类型收，所以这里收的是白色旗帜；不想要它就把这一行删掉。
        ('minecraft:white_banner', None, 50, None, 'acquire.item.ominous_banner'),
        # 不祥之瓶：放在不祥旗帜下面，价格与旗帜一致
        ('minecraft:ominous_bottle', None, 50, None, None),
        ('minecraft:goat_horn', None, 3, None, None),
        ('minecraft:shulker_shell', None, 20, None, None),
        ('minecraft:sculk_sensor', None, 20, None, None),
        ('minecraft:axolotl_bucket', None, 8, None, None),
        ('minecraft:turtle_helmet', None, 5, None, None),
        # 马铠按品质分开收（不再合并成「马铠（任意）」一行）：品质差别大，价格也应该分开
        ('minecraft:leather_horse_armor', None, 10, None, None),
        ('minecraft:iron_horse_armor', None, 50, None, None),
        ('minecraft:golden_horse_armor', None, 100, None, None),
        ('minecraft:diamond_horse_armor', None, 200, None, None),
        ('minecraft:ghast_tear', None, 20, None, None),
        ('minecraft:slime_ball', None, 2, None, None),
        ('minecraft:magma_cream', None, 5, None, None),
        ('minecraft:clay_ball', None, 1, None, None),
        ('minecraft:blaze_rod', None, 5, None, None),
        ('minecraft:phantom_membrane', None, 4, None, None),
        ('minecraft:rotten_flesh', None, 1, None, None),
        ('minecraft:spider_eye', None, 1, None, None),
        # 藏宝图：物品类型还是 filled_map，但只收带结构标记的；
        # 空地图（map）与普通已探索地图（没有结构标记）不算。
        ('minecraft:filled_map', None, 20, None, 'acquire.item.treasure_map', 'treasure_map'),
        ('minecraft:spectral_arrow', None, 10, None, None),
        ('minecraft:arrow', None, 1, None, None),
        ('minecraft:nautilus_shell', None, 50, None, None),
        ('minecraft:cobweb', None, 5, None, None),
    ]),
]

# 疯狂星期五的随机池。支持的写法：
#   'minecraft:spawner'                        具体物品
#   '#emerald_economy:acquire/heads'           整个物品标签（所有怪物的头颅，含龙首）
#   '*_spawn_egg'                              通配整类：任意刷怪蛋（含其他模组）都收
# 每个池子条目默认每天最多被挑中 1 件。通配条目被挑中时，界面上是一行
# 「刷怪蛋（任意）」，玩家卖背包里任意一种刷怪蛋都按这一行的价格结算。
FRIDAY_POOL = [
    'minecraft:nether_star', 'minecraft:elytra', 'minecraft:trident',
    'minecraft:heart_of_the_sea', 'minecraft:echo_shard', 'minecraft:spawner',
    'minecraft:ominous_bottle',
    '#emerald_economy:acquire/heads',
    '*_spawn_egg',
]
# 池子条目的挑选上限：**默认就是每天最多 1 件**（同一个具体物品、以及同一个「整类」条目
# 都不会在一天里出现两次，例如不会同时出两个下界之星、两种头颅）。
# 这个表只用来「把某一类调大」。
FRIDAY_POOL_MAX_PICKS = {}
FRIDAY_BASE_PRICES = {
    'minecraft:nether_star': 133,
    'minecraft:elytra': 100,
    'minecraft:trident': 75,
    'minecraft:heart_of_the_sea': 58,
    'minecraft:echo_shard': 33,
    'minecraft:spawner': 40,
    'minecraft:ominous_bottle': 42,
    # 池子条目也可以直接配基准价（头颅这一类里含龙首，六种头颅的价格一样）
    '#emerald_economy:acquire/heads': 83,
    # 通配整类：任意刷怪蛋（含其他模组）统一基准价 40
    '*_spawn_egg': 40,
}


def shop_json():
    groups = []
    # 附魔书：五个附魔子分类都挂在同一个「解锁制」大分类下面
    groups.append({
        "_comment": "unlockable=true 的大分类走「花绿宝石逐格解锁」；附魔书价格是区间，游戏里按附魔 ID 稳定随机。\nGroups with unlockable=true are unlocked slot by slot with emeralds; book prices are ranges and a stable value per enchant id is picked in game.",
        "id": "ENCHANTED_BOOK",
        "lang": "shop.group.enchanted_book",
        "name": "附魔书",
        "icon": "📖",
        "unlockable": True,
        "subcategories": [
            {
                "id": sub_id,
                "lang": lang,
                "name": name,
                "icon": icon,
                "enchants": [{"id": eid, "min": lo, "max": hi} for eid, lo, hi in enchants],
            }
            for sub_id, lang, name, icon, enchants in ENCHANT_GROUPS
        ],
    })
    groups.append({
        "_comment": "unlockable=false 的大分类随时能买；items 里写物品 ID 与价格，或者写 exp（经验点数）卖经验值。\nGroups with unlockable=false are always available; items entries take an item id plus price, or exp (XP points) plus price for XP goods.",
        "id": "OTHER",
        "lang": "shop.group.other",
        "name": "其他",
        "icon": "📦",
        "unlockable": False,
        "subcategories": [
            {
                "id": sub_id,
                "lang": lang,
                "name": name,
                "icon": icon,
                "items": items,
            }
            for sub_id, lang, name, icon, items in OTHER_GROUPS
        ],
    })
    return {
        "_comment": "自动售货机的货架。改完在游戏里执行 /ee reload 立刻生效（也可以重启服务器）。\nVending machine shelves. Run /ee reload in game after editing (a restart also works).",
        "_format": "groups[].subcategories[]：附魔书用 enchants（id/min/max），普通商品用 items（item/price），经验值商品用 items（exp/price）。\ngroups[].subcategories[]: enchanted books use enchants (id/min/max), ordinary goods use items (item/price), XP goods use items (exp/price).",
        "groups": groups,
    }


def acquire_json():
    categories = []
    for cat_id, lang, name, items in ACQUIRE_CATEGORIES:
        entries = []
        for row in items:
            item, tag, price, batch, name_key = row[:5]
            filter_value = row[5] if len(row) > 5 else None
            entry = {}
            if tag:
                entry["_comment"] = ("tag 表示收整类物品（原版标签或本模组标签都行）；"
                                     "界面上只占一行，卖这一类里的任意一种都可以。\ntag means \"buy the whole class\" (vanilla or this mod's item tags); "
                                     "it takes a single row on screen and accepts any item of that class.")
                entry["tag"] = tag
            else:
                entry["item"] = item
            entry["price"] = price
            if batch:
                entry["batch"] = batch
            if name_key:
                entry["lang"] = name_key
            if filter_value:
                entry["filter"] = filter_value
            entries.append(entry)
        categories.append({
            "id": cat_id,
            "lang": lang,
            "name": name,
            "items": entries,
        })
    return {
        "_comment": "自动收购机的价格表。改完在游戏里执行 /ee reload 立刻生效。\nAcquisition machine price table. Run /ee reload in game after editing.",
        "_format": "categories[].items[]：每条形如 {\"item\":\"minecraft:wheat\",\"price\":1}，"
                   "或 {\"tag\":\"minecraft:saplings\",\"price\":1}；batch=8 表示 8 个算一份；"
                   "lang 是这一行显示名的翻译键（标签行建议写上）。\ncategories[].items[]: entries look like {\"item\":\"minecraft:wheat\",\"price\":1} or "
                   "{\"tag\":\"minecraft:saplings\",\"price\":1}; batch=8 means eight items per unit; "
                   "lang is the translation key for the row label (recommended for tag rows).",
        "categories": categories,
        "friday": {
            "_comment": "疯狂星期五：每天从 pool 里随机挑几件按倍率收购，basePrices 是基准价。"
                        "pool 支持具体物品、#标签、*通配；每个池子条目默认每天最多被挑中 1 件，"
                        "poolMaxPicks 只用来把某一类调大。*_spawn_egg 表示任意刷怪蛋（含其他模组）"
                        "作为一个整类条目，抽中后玩家卖任意一种刷怪蛋都按该行价格结算。\n"
                        "Crazy Friday: a few items are picked from pool each day and bought with a multiplier; "
                        "basePrices are the base prices. pool accepts plain item ids, #tags and * wildcards. "
                        "Each pool entry contributes at most 1 item per day by default. *_spawn_egg is a "
                        "whole-class entry: any spawn egg (modded included) sells at that row's price.",
            "lang": "acquire.category.friday",
            "name": "疯狂星期五",
            "pool": FRIDAY_POOL,
            "poolMaxPicks": FRIDAY_POOL_MAX_PICKS,
            "basePrices": FRIDAY_BASE_PRICES,
        },
    }


def main():
    if not os.path.isdir(OUT_DIR):
        os.makedirs(OUT_DIR)
    for name, data in (('shop.json', shop_json()), ('acquire.json', acquire_json())):
        path = os.path.join(OUT_DIR, name)
        with io.open(path, 'w', encoding='utf-8', newline='\n') as handle:
            json.dump(data, handle, ensure_ascii=False, indent=2)
            handle.write('\n')
        print('已生成 %s' % os.path.relpath(path, ROOT))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
