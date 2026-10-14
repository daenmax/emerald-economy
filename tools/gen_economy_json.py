# -*- coding: utf-8 -*-
"""生成模组内置的「内容配置」默认模板。

产物（同时写两份：jar 里的默认模板 + 开发环境 config 目录）：
  src/main/resources/data/emerald_economy/economy/shop.json     自动售货机的货架
  src/main/resources/data/emerald_economy/economy/acquire.json  自动收购机的价格表

这两个 JSON 是**运行时以 config/emerald_economy/ 下的同名文件为准**的：
服务端第一次启动会把这里的模板复制过去，之后玩家直接编辑 config 里的那份，
游戏里 `/ee reload` 立刻生效。

用法：python tools/gen_economy_json.py
"""
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

# 「其他」大分类：子分类 -> (翻译键, 显示名, 图标, [(物品, 价格)...])
OTHER_GROUPS = [
    ('SEED', 'shop.sub.seed', '种子', '🌱', [
        ('minecraft:wheat_seeds', 8),
    ]),
    ('SAPLING', 'shop.sub.sapling', '树苗', '🌳', [
        ('minecraft:oak_sapling', 16),
        ('minecraft:birch_sapling', 16),
    ]),
]

# 收购分类：id -> (翻译键, 显示名, [(物品, 标签, 价格, 批次)...])
ACQUIRE_CATEGORIES = [
    ('AGRICULTURE', 'acquire.category.agriculture', '农业产物', [
        ('minecraft:wheat', None, 1, None),
        ('minecraft:carrot', None, 1, None),
        ('minecraft:potato', None, 1, None),
        ('minecraft:beetroot', None, 1, None),
        ('minecraft:sugar_cane', None, 2, None),
        ('minecraft:melon_slice', None, 1, None),
        ('minecraft:pumpkin', None, 2, None),
        ('minecraft:apple', None, 2, None),
        ('minecraft:sweet_berries', None, 1, None),
        ('minecraft:nether_wart', None, 2, None),
        ('minecraft:bamboo', None, 1, 4),
        ('minecraft:cocoa_beans', None, 1, None),
        (None, 'minecraft:saplings', 1, None),
    ]),
    ('LIVESTOCK', 'acquire.category.livestock', '畜牧产物', [
        ('minecraft:beef', None, 2, None),
        ('minecraft:porkchop', None, 2, None),
        ('minecraft:chicken', None, 2, None),
        ('minecraft:mutton', None, 2, None),
        ('minecraft:rabbit', None, 2, None),
        ('minecraft:leather', None, 2, None),
        ('minecraft:white_wool', None, 1, None),
        ('minecraft:egg', None, 1, 8),
        ('minecraft:milk_bucket', None, 3, None),
        ('minecraft:honey_bottle', None, 3, None),
        ('minecraft:feather', None, 1, 8),
        ('minecraft:bone', None, 1, None),
        (None, 'minecraft:fishes', 2, None),
    ]),
    ('MINING', 'acquire.category.mining', '矿物', [
        ('minecraft:coal', None, 1, None),
        ('minecraft:raw_iron', None, 2, None),
        ('minecraft:raw_copper', None, 2, None),
        ('minecraft:raw_gold', None, 4, None),
        ('minecraft:redstone', None, 1, None),
        ('minecraft:lapis_lazuli', None, 2, None),
        ('minecraft:diamond', None, 15, None),
        ('minecraft:quartz', None, 1, None),
        ('minecraft:amethyst_shard', None, 2, None),
        ('minecraft:flint', None, 1, None),
        ('minecraft:obsidian', None, 3, None),
        ('minecraft:netherite_scrap', None, 10, None),
    ]),
    ('FORESTRY', 'acquire.category.forestry', '林业产物', [
        ('minecraft:oak_log', None, 1, None),
        ('minecraft:spruce_log', None, 1, None),
        ('minecraft:birch_log', None, 1, None),
        ('minecraft:jungle_log', None, 1, None),
        ('minecraft:acacia_log', None, 1, None),
        ('minecraft:dark_oak_log', None, 1, None),
        ('minecraft:mangrove_log', None, 1, None),
        ('minecraft:cherry_log', None, 1, None),
        ('minecraft:bamboo_block', None, 1, None),
        ('minecraft:crimson_stem', None, 1, None),
        ('minecraft:warped_stem', None, 1, None),
    ]),
    ('MISC', 'acquire.category.misc', '杂物', [
        (None, 'emerald_economy:acquire/music_discs', 3, None),
        ('minecraft:goat_horn', None, 3, None),
        ('minecraft:shulker_shell', None, 20, None),
        ('minecraft:axolotl_bucket', None, 8, None),
        ('minecraft:turtle_helmet', None, 5, None),
        (None, 'emerald_economy:acquire/horse_armors', 4, None),
        ('minecraft:slime_ball', None, 1, None),
        ('minecraft:clay_ball', None, 1, None),
        ('minecraft:blaze_rod', None, 1, None),
        ('minecraft:phantom_membrane', None, 4, None),
    ]),
]

FRIDAY_POOL = [
    'minecraft:dragon_head', 'minecraft:totem_of_undying', 'minecraft:nether_star',
    'minecraft:elytra', 'minecraft:trident', 'minecraft:heart_of_the_sea',
    'minecraft:echo_shard', 'minecraft:spawner', 'minecraft:ominous_bottle',
]
FRIDAY_BASE_PRICES = {
    'minecraft:dragon_head': 83,
    'minecraft:totem_of_undying': 67,
    'minecraft:nether_star': 133,
    'minecraft:elytra': 100,
    'minecraft:trident': 75,
    'minecraft:heart_of_the_sea': 58,
    'minecraft:echo_shard': 33,
    'minecraft:spawner': 50,
    'minecraft:ominous_bottle': 42,
}


def shop_json():
    groups = []
    # 附魔书：五个附魔子分类都挂在同一个「解锁制」大分类下面
    groups.append({
        "_comment": "unlockable=true 的大分类走「花绿宝石逐格解锁」；附魔书价格是区间，游戏里按附魔 ID 稳定随机",
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
        "_comment": "unlockable=false 的大分类随时能买；items 里写物品 ID 与价格即可",
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
                "items": [{"item": item, "price": price} for item, price in items],
            }
            for sub_id, lang, name, icon, items in OTHER_GROUPS
        ],
    })
    return {
        "_comment": "自动售货机的货架。改完在游戏里执行 /ee reload 立刻生效（也可以重启服务器）。",
        "_format": "groups[].subcategories[]，附魔书用 enchants（id/min/max），普通商品用 items（item/price）",
        "groups": groups,
    }


def acquire_json():
    categories = []
    for cat_id, lang, name, items in ACQUIRE_CATEGORIES:
        entries = []
        for item, tag, price, batch in items:
            entry = {}
            if tag:
                entry["_comment"] = "tag 表示收整类物品（原版标签或本模组标签都行）"
                entry["tag"] = tag
            else:
                entry["item"] = item
            entry["price"] = price
            if batch:
                entry["batch"] = batch
            entries.append(entry)
        categories.append({
            "id": cat_id,
            "lang": lang,
            "name": name,
            "items": entries,
        })
    return {
        "_comment": "自动收购机的价格表。改完在游戏里执行 /ee reload 立刻生效。",
        "_format": "categories[].items[]：每条形如 {\"item\":\"minecraft:wheat\",\"price\":1}，"
                   "或 {\"tag\":\"minecraft:saplings\",\"price\":1}；batch=8 表示 8 个算一份",
        "categories": categories,
        "friday": {
            "_comment": "疯狂星期五：每天从 pool 里随机挑几件按倍率收购，basePrices 是基准价",
            "lang": "acquire.category.friday",
            "name": "疯狂星期五",
            "pool": FRIDAY_POOL,
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
