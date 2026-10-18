# -*- coding: utf-8 -*-
"""生成模组的方块状态、模型、配方与战利品表。

为什么用脚本生成而不是手写 26 个 JSON：
  - 五台机器的结构完全一样，手写必然各写各的、改一处漏四处；
  - 参数化之后调整外形（比如加高一层、换个贴图布局）只要改一处。

用法：python tools/gen_resources.py
"""
import io
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'emerald_economy')
DATA = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'emerald_economy')

MOD_ID = 'emerald_economy'
MACHINES = ['atm', 'exchange', 'lottery', 'vending', 'acquisition']
FACINGS = [('north', 0), ('east', 90), ('south', 180), ('west', 270)]

# 五台机器合成表正中间的物品（四周的八件固定）
CENTERS = {
    'atm': 'minecraft:emerald',
    'exchange': 'minecraft:redstone_lamp',
    'lottery': 'minecraft:clock',
    'vending': 'minecraft:item_frame',
    'acquisition': 'minecraft:cauldron',
}

# 物品在手上的显示参数（照抄原版门，因为它同样是「两个方块高」的模型）
DISPLAY = {
    'gui': {'rotation': [30, 225, 0], 'translation': [0, 2.0, 0], 'scale': [0.55, 0.55, 0.55]},
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.25, 0.25, 0.25]},
    'fixed': {'rotation': [0, 180, 0], 'translation': [0, 0, 0], 'scale': [0.5, 0.5, 0.5]},
    'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0],
                              'scale': [0.375, 0.375, 0.375]},
    'thirdperson_lefthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0],
                             'scale': [0.375, 0.375, 0.375]},
    'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 1.0, 0],
                              'scale': [0.4, 0.4, 0.4]},
    'firstperson_lefthand': {'rotation': [0, 45, 0], 'translation': [0, 1.0, 0],
                             'scale': [0.4, 0.4, 0.4]},
}


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with io.open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write('\n')


def base_faces():
    """外壳的六个面。注意 faces 里的 texture 必须是 #开头的变量引用，
    直接写完整贴图路径的话，MC 会把它当成 textures 块里的键去找，
    找不到就静默退化成 missing 贴图 —— 方块会变成品红黑格子。"""
    return {d: {'texture': '#base'} for d in ('down', 'up', 'north', 'south', 'west', 'east')}


def screen_faces():
    """正面屏幕：只有北面贴屏幕贴图，其它面还是外壳。"""
    faces = base_faces()
    faces['north'] = {'texture': '#face'}
    return faces


# ---------------------------------------------------------------------------
# 机器模型
#
# 2026-11 再次重做：五台机器要有互不相同的「一眼能认出」的外形与标志物，
# 几何写在下面这张表里（单一真源），Blockbench 工程由 tools/gen_bbmodel.py 生成：
#
#   ATM     高位屏幕 + 数字键盘 + 出钞口（立式取款机）
#   交易所  显示器立在支座上 + 键盘 + 主机箱（一台电脑）
#   抽奖机  三个滚轮窗口 + 右侧拉杆 + 顶部灯带（老虎机）
#   售货机  大面积货架展示 + 右侧操作柱 + 底部取物口
#   收购机  顶部漏斗 + 前方传送带 + 投料口
#
# 所有元素的 y 都不超过 32（整机两格高），正面朝北（north = 屏幕/标志那一面）。
# UV 窗口（贴图像素，y 从上往下）：
#   PLATE   (1,1,8,8)      14×14 的面
#   PLATE_B (1,1,8,15)     14×28 的竖面
#   TOP     (0,0,8,8)      16×16 的面
#   SIDE    (0,0,8,1)      16×薄 的面
#   DARK    (11,1,15,5)    深色钢块（细条 / 边框 / 凹槽）
#   SCREEN  (2,2,14,14)    屏幕贴图中央的 12×12 主图案
#   ACCENT  (0,14,16,16)   屏幕贴图底部的强调色条（灯带 / 名牌 / 拉杆头）
#   BAR     (2,0,14,2)     屏幕贴图顶部的 12×2 细节条（传送带 / 货架边）
# ---------------------------------------------------------------------------
UV_WINDOWS = {
    'PLATE': [1, 1, 8, 8],
    'PLATE_B': [1, 1, 8, 15],
    'TOP': [0, 0, 8, 8],
    'SIDE': [0, 0, 8, 1],
    'DARK': [11, 1, 15, 5],
    'SCREEN': [2, 2, 14, 14],
    'ACCENT': [0, 14, 16, 16],
    'BAR': [2, 0, 14, 2],
}

ALL_FACES = ('north', 'south', 'east', 'west', 'up', 'down')


def _cube(name, frm, to, window='PLATE', north=None, texture='#base', north_texture='#face'):
    """造一个元素。

    - 六个面默认都用 `window` 指定的 UV 窗口 + base 贴图；
    - 传了 `north`（窗口名）就给正面换成 `north_texture`（默认屏幕贴图）。
    """
    faces = {face: {'uv': list(UV_WINDOWS[window]), 'texture': texture} for face in ALL_FACES}
    if north is not None:
        faces['north'] = {'uv': list(UV_WINDOWS[north]), 'texture': north_texture}
    return {'name': name, 'from': list(frm), 'to': list(to), 'faces': faces}


def _plinth(down_window='TOP', side_window='SIDE'):
    """统一的底座：16×2×16，四周窄面用细条窗口。"""
    faces = {face: {'uv': list(UV_WINDOWS[side_window]), 'texture': '#base'} for face in
             ('north', 'south', 'east', 'west')}
    faces['up'] = {'uv': list(UV_WINDOWS[down_window]), 'texture': '#base'}
    faces['down'] = {'uv': list(UV_WINDOWS[down_window]), 'texture': '#base'}
    return {'name': 'plinth', 'from': [0, 0, 0], 'to': [16, 2, 16], 'faces': faces}


def _bezel(prefix, x1, y1, x2, y2, thickness=1):
    """屏幕/窗口四周的边框：上、下、左、右四条深色条。"""
    out = []
    out.append(_cube(prefix + '_top', [x1, y2, 0], [x2, y2 + thickness, 1], 'DARK'))
    out.append(_cube(prefix + '_bottom', [x1, y1 - thickness, 0], [x2, y1, 1], 'DARK'))
    out.append(_cube(prefix + '_left', [x1 - thickness, y1, 0], [x1, y2, 1], 'DARK'))
    out.append(_cube(prefix + '_right', [x2, y1, 0], [x2 + thickness, y2, 1], 'DARK'))
    return out


def machine_elements(machine):
    """一台机器的元素表（几何的唯一真源）。"""
    if machine == 'atm':
        # 立式取款机：上方大屏 + 下方键盘与出钞口
        elements = [_plinth(),
                    _cube('body', [1, 2, 1], [15, 30, 15], 'PLATE_B')]
        elements += _bezel('screen', 3, 18, 13, 29)
        elements.append(_cube('screen', [3, 18, 0.5], [13, 29, 1], 'DARK', north='SCREEN'))
        elements.append(_cube('keypad', [4, 10, 0.5], [9, 16, 1], 'DARK', north='BAR'))
        elements.append(_cube('cash_slot', [10, 10, 0.5], [14, 13, 1], 'DARK', north='BAR'))
        elements.append(_cube('card_slot', [10, 14, 0.5], [14, 15, 1], 'DARK', north='ACCENT'))
        elements.append(_cube('name_strip', [3, 3, 0.5], [13, 5, 1], 'DARK', north='ACCENT'))
        elements.append(_cube('top_cap', [1, 30, 1], [15, 32, 15], 'PLATE'))
        elements.append(_cube('vent_west', [0, 12, 4], [1, 26, 12], 'DARK'))
        elements.append(_cube('vent_east', [15, 12, 4], [16, 26, 12], 'DARK'))
        return elements

    if machine == 'exchange':
        # 交易所电脑：显示器 + 支座 + 键盘 + 主机箱
        return [_plinth(),
                _cube('desk', [0, 2, 0], [16, 4, 16], 'PLATE'),
                _cube('tower', [11, 4, 2], [15, 14, 7], 'PLATE'),
                _cube('stand', [7, 4, 3], [9, 10, 5], 'DARK'),
                _cube('monitor', [2, 10, 4], [14, 26, 6], 'PLATE'),
                _cube('screen', [3, 11, 3.5], [13, 25, 4], 'DARK', north='SCREEN'),
                _cube('chin', [3, 10, 3.5], [13, 11, 4], 'DARK', north='ACCENT'),
                _cube('keyboard', [3, 4, 9], [13, 5, 14], 'PLATE', north='BAR')]

    if machine == 'lottery':
        # 老虎机：三滚轮窗口 + 右侧拉杆 + 顶部灯带
        elements = [_plinth(),
                    _cube('body', [1, 2, 1], [15, 28, 15], 'PLATE_B')]
        elements += _bezel('window', 2, 7, 14, 19)
        elements.append(_cube('reels', [2, 7, 0.5], [14, 19, 1], 'DARK', north='SCREEN'))
        elements.append(_cube('reel_divider_a', [6, 8, 0.4], [6.5, 18, 1], 'DARK'))
        elements.append(_cube('reel_divider_b', [9.5, 8, 0.4], [10, 18, 1], 'DARK'))
        elements.append(_cube('marquee', [2, 24, 0.5], [14, 27, 1], 'DARK', north='ACCENT'))
        elements.append(_cube('coin_slot', [6, 3, 0.5], [10, 6, 1], 'DARK', north='BAR'))
        elements.append(_cube('lever_rod', [14.5, 12, 7], [16, 21, 9], 'DARK'))
        elements.append(_cube('lever_knob', [14.5, 21, 6], [16, 24, 10], 'DARK', north='ACCENT',
                              north_texture='#face'))
        elements.append(_cube('top_cap', [1, 28, 1], [15, 32, 15], 'PLATE'))
        return elements

    if machine == 'vending':
        # 售货机：大面积货架 + 右侧操作柱 + 底部取物口
        return [_plinth(),
                _cube('body', [1, 2, 1], [15, 30, 15], 'PLATE_B'),
                _cube('display', [2, 9, 0.5], [12, 28, 1], 'DARK', north='SCREEN'),
                _cube('shelf_a', [2, 18, 0.4], [12, 19, 1], 'DARK', north='BAR'),
                _cube('shelf_b', [2, 13, 0.4], [12, 14, 1], 'DARK', north='BAR'),
                _cube('column', [12, 9, 0.5], [15, 28, 1], 'DARK', north='BAR'),
                _cube('pickup', [4, 3, 0.5], [10, 7, 1], 'DARK', north='BAR'),
                _cube('name_strip', [4, 7, 0.5], [10, 9, 1], 'DARK', north='ACCENT'),
                _cube('top_cap', [1, 30, 1], [15, 32, 15], 'PLATE')]

    if machine == 'acquisition':
        # 收购机：顶部漏斗 + 前方传送带 + 投料口与信息屏
        return [_plinth(),
                _cube('body', [1, 2, 1], [15, 26, 15], 'PLATE_B'),
                _cube('hopper_lower', [2, 26, 2], [14, 29, 14], 'PLATE'),
                _cube('hopper_upper', [5, 29, 5], [11, 32, 11], 'PLATE'),
                _cube('conveyor', [1, 7, 0], [15, 10, 3], 'PLATE_B'),
                _cube('belt_stripe', [1, 9.5, 0], [15, 10, 3], 'DARK', north='BAR'),
                _cube('intake', [2, 14, 0.5], [8, 24, 1], 'DARK', north='BAR'),
                _cube('info_screen', [9, 17, 0.5], [14, 24, 1], 'DARK', north='SCREEN'),
                _cube('name_strip', [9, 14, 0.5], [14, 16, 1], 'DARK', north='ACCENT'),
                _cube('vent_west', [0, 12, 4], [1, 24, 12], 'DARK'),
                _cube('vent_east', [15, 12, 4], [16, 24, 12], 'DARK')]

    # 兜底（不该发生）：给一个空模型，至少不会崩
    return [_plinth()]


def machine_model(machine):
    """一台机器的模型 JSON。

    贴图引用必须写成 `#变量`（`#base` / `#face`）——直接写完整贴图路径的话
    MC 会把它当成 textures 块里的键去找，找不到就静默变成 missing 贴图（整机品红）。
    """
    return {
        'parent': 'block/block',
        'textures': {
            'particle': '%s:block/machine_base' % MOD_ID,
            'base': '%s:block/machine_base' % MOD_ID,
            'face': '%s:block/%s_face' % (MOD_ID, machine),
        },
        'elements': machine_elements(machine),
    }


def upper_model():
    """上半格的模型。

    真正渲染时上半格会被 RenderShape.INVISIBLE 跳过（外观全部由下半格画），
    但方块状态里仍然要指向一个合法模型，所以这里给一个最小的、贴着边角的元素。
    """
    return {
        'parent': 'block/block',
        'textures': {'particle': '%s:block/machine_base' % MOD_ID, 'base': '%s:block/machine_base' % MOD_ID},
        'elements': [
            {'from': [0.01, 0.01, 0.01], 'to': [0.02, 0.02, 0.02], 'faces': {'up': {'texture': '#base'}}},
        ],
    }


def blockstate(machine):
    variants = {}
    for half in ('lower', 'upper'):
        for facing, y in FACINGS:
            model = '%s:block/%s' % (MOD_ID, machine) if half == 'lower' else '%s:block/machine_upper' % MOD_ID
            variant = {'model': model}
            if y:
                variant['y'] = y
            variants['facing=%s,half=%s' % (facing, half)] = variant
    return {'variants': variants}


def recipe(machine):
    return {
        'type': 'minecraft:crafting_shaped',
        'category': 'redstone',
        'key': {
            'R': {'item': 'minecraft:redstone'},
            'P': {'item': 'minecraft:sticky_piston'},
            'H': {'item': 'minecraft:hopper'},
            'O': {'item': 'minecraft:observer'},
            'L': {'item': 'minecraft:lever'},
            'T': {'item': 'minecraft:repeater'},
            'C': {'item': 'minecraft:chest'},
            'X': {'item': 'minecraft:comparator'},
            'E': {'item': CENTERS[machine]},
        },
        'pattern': ['RPH', 'OEL', 'TCX'],
        'result': {'id': '%s:%s' % (MOD_ID, machine), 'count': 1},
    }


def loot_table(machine):
    """整台机器只由下半格掉落一份，避免上下两格各掉一次。"""
    return {
        'type': 'minecraft:block',
        'pools': [{
            'rolls': 1.0,
            'bonus_rolls': 0.0,
            'entries': [{
                'type': 'minecraft:item',
                'name': '%s:%s' % (MOD_ID, machine),
                'conditions': [{
                    'condition': 'minecraft:block_state_property',
                    'block': '%s:%s' % (MOD_ID, machine),
                    'properties': {'half': 'lower'},
                }],
            }],
            'conditions': [{'condition': 'minecraft:survives_explosion'}],
        }],
    }


def recipe_advancement():
    """配方解锁进度：首次拿到红石时一次性解锁五台机器的配方。

    配方书里能不能看到某个配方，取决于对应的「配方进度」是否已解锁
    （配方本身一直可用，进度只影响配方书的显示）。
    五个配方都要红石，所以用「背包里出现红石」作为唯一的解锁条件。
    """
    recipes = ['%s:%s' % (MOD_ID, machine) for machine in MACHINES]
    return {
        'parent': 'minecraft:recipes/root',
        'criteria': {
            'has_redstone': {
                'trigger': 'minecraft:inventory_changed',
                'conditions': {
                    'items': [{'items': 'minecraft:redstone'}],
                },
            },
        },
        'requirements': [['has_redstone']],
        'rewards': {'recipes': recipes},
    }


def acquire_tags():
    """收购用的物品标签。

    原版没有「音乐唱片 / 马铠 / 怪物的头颅 / 花 / 蘑菇」这类物品标签，
    但配置里想一次收掉一整类东西，所以自己建这几个标签；
    整合包可以往标签里追加自己的物品（例如自定义模组的唱片、头颅）。

    注意「花」直接引用原版的两个子标签（小花朵 + 高花），
    这样原版加了新花、整合包往原版标签里塞了花，我们这边都会自动跟着收。
    """
    discs = [
        'minecraft:music_disc_13', 'minecraft:music_disc_cat', 'minecraft:music_disc_blocks',
        'minecraft:music_disc_chirp', 'minecraft:music_disc_far', 'minecraft:music_disc_mall',
        'minecraft:music_disc_mellohi', 'minecraft:music_disc_stal', 'minecraft:music_disc_strad',
        'minecraft:music_disc_ward', 'minecraft:music_disc_11', 'minecraft:music_disc_wait',
        'minecraft:music_disc_otherside', 'minecraft:music_disc_5', 'minecraft:music_disc_pigstep',
        'minecraft:music_disc_relic', 'minecraft:music_disc_creator',
        'minecraft:music_disc_creator_music_box', 'minecraft:music_disc_precipice',
    ]
    horse_armors = [
        'minecraft:leather_horse_armor', 'minecraft:iron_horse_armor',
        'minecraft:golden_horse_armor', 'minecraft:diamond_horse_armor',
    ]
    # 怪物的头颅：不含玩家头颅（那个在生存里拿不到，也不是「怪物」掉的）
    heads = [
        'minecraft:zombie_head', 'minecraft:skeleton_skull', 'minecraft:wither_skeleton_skull',
        'minecraft:creeper_head', 'minecraft:dragon_head', 'minecraft:piglin_head',
    ]
    flowers = [
        '#minecraft:small_flowers',
        '#minecraft:tall_flowers',
    ]
    mushrooms = [
        'minecraft:brown_mushroom',
        'minecraft:red_mushroom',
    ]
    # 鱼类：原版 #minecraft:fishes 含河豚，而河豚要单独按 20/个 收，
    # 所以自己建一个不含河豚的标签（整合包可以往里补充模组鱼）。
    fishes = [
        'minecraft:cod',
        'minecraft:salmon',
        'minecraft:tropical_fish',
    ]
    return {
        'music_discs': discs,
        'horse_armors': horse_armors,
        'heads': heads,
        'flowers': flowers,
        'mushrooms': mushrooms,
        'fishes': fishes,
    }


def mineable_tags():
    """把机器方块加进原版的 {@code minecraft:mineable/pickaxe} 标签。

    这一步是「镐子挖得快 + 有掉落」的关键：原版的镐子只有在方块属于这个标签时
    才拿到挖掘速度加成，否则挖掘速度按「徒手 1.0」算、破坏进度还要除以 100 —— 
    表现出来就是「拿镐子挖又慢又什么都不掉」。
    """
    return {
        'replace': False,
        'values': ['%s:%s' % (MOD_ID, machine) for machine in MACHINES],
    }


def main():
    for machine in MACHINES:
        write(os.path.join(ASSETS, 'blockstates', '%s.json' % machine), blockstate(machine))
        write(os.path.join(ASSETS, 'models', 'block', '%s.json' % machine), machine_model(machine))
        write(os.path.join(ASSETS, 'models', 'item', '%s.json' % machine),
              {'parent': '%s:block/%s' % (MOD_ID, machine), 'display': DISPLAY})
        write(os.path.join(DATA, 'recipe', '%s.json' % machine), recipe(machine))
        write(os.path.join(DATA, 'loot_table', 'blocks', '%s.json' % machine), loot_table(machine))
    write(os.path.join(ASSETS, 'models', 'block', 'machine_upper.json'), upper_model())
    write(os.path.join(DATA, 'advancement', 'recipes', 'machines.json'), recipe_advancement())
    for name, values in acquire_tags().items():
        write(os.path.join(DATA, 'tags', 'item', 'acquire', '%s.json' % name), {'values': values})
    # 注意：写进 data/minecraft/... 是为了「追加」到原版标签里（replace=false 时是合并）
    write(os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'minecraft', 'tags', 'block',
                       'mineable', 'pickaxe.json'), mineable_tags())
    print('已生成 %d 台机器的方块状态 / 模型 / 配方 / 战利品表，以及配方解锁进度与收购标签'
          % len(MACHINES))


if __name__ == '__main__':
    main()
