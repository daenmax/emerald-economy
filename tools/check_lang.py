# -*- coding: utf-8 -*-
"""校验：Java 代码里用到的每个翻译键，是否都在 zh_cn / en_us 里有定义。

用法：python tools/check_lang.py
"""
import io
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA_ROOT = os.path.join(ROOT, 'src', 'main', 'java')
LANG_DIR = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'emerald_economy', 'lang')
PREFIX = 'emerald_economy.'


def collect_java_keys():
    keys = set()
    literal = re.compile(r'langKey\(\s*"([^"]+)"')
    prefixed = re.compile(r'"((?:screen|atm|market|lottery|shop|acquire|news|tx|fund|weather|message|settlement)'
                          r'\.[A-Za-z0-9_.]+)"')
    for base, _dirs, files in os.walk(JAVA_ROOT):
        for name in files:
            if not name.endswith('.java'):
                continue
            text = io.open(os.path.join(base, name), encoding='utf-8').read()
            for match in literal.finditer(text):
                keys.add(PREFIX + match.group(1))
            for match in prefixed.finditer(text):
                key = match.group(1)
                # 排除文件名（例如 EconomyContent 里的 "shop.json" / "acquire.json"）
                if key.endswith('.json') or key.endswith('.toml'):
                    continue
                keys.add(PREFIX + key)
    return keys


def dynamic_keys():
    """代码里用拼接方式生成的键，这里手工列出来一起校验。"""
    keys = set()
    for suffix in ('deposit', 'withdraw', 'loan', 'repay', 'interest', 'loan_interest',
                   'fund_buy', 'fund_sell', 'lottery_bet', 'lottery_win', 'shop_buy',
                   'shop_upgrade', 'acquire_sell'):
        keys.add(PREFIX + 'tx.' + suffix)
    for fund in ('min', 'agr', 'liv', 'bld', 'hyd', 'hun', 'cmp'):
        keys.add(PREFIX + 'fund.emr_' + fund)
    for weather in ('clear', 'rain', 'thunder'):
        keys.add(PREFIX + 'weather.' + weather)
    for lottery in ('odd_even', 'big_small', 'last_digit'):
        keys.add(PREFIX + 'lottery.' + lottery)
    for option in ('0', '1'):
        keys.add(PREFIX + 'lottery.odd_even.option.' + option)
        keys.add(PREFIX + 'lottery.big_small.option.' + option)
    for digit in range(10):
        keys.add(PREFIX + 'lottery.last_digit.option.%d' % digit)
    for category in ('agriculture', 'livestock', 'mining', 'forestry', 'misc', 'friday'):
        keys.add(PREFIX + 'acquire.category.' + category)
    # 自动售货机的大分类 / 子分类（由枚举名与配置里的子分类名拼出来）
    for group in ('enchanted_book', 'other'):
        keys.add(PREFIX + 'shop.group.' + group)
    for sub in ('seed', 'sapling'):
        keys.add(PREFIX + 'shop.sub.' + sub)
    # 附魔书分类与「适用物品」文案（由 EnchantCategory / EnchantCatalog 拼接）
    for category in ('weapon', 'ranged', 'tool', 'armor', 'generic'):
        keys.add(PREFIX + 'shop.category.' + category)
    for applicable in ('sword_axe_mace', 'sword', 'trident', 'mace', 'bow', 'crossbow', 'mining',
                       'mining_loot', 'fishing', 'armor', 'foot_armor', 'head_armor', 'leg_armor',
                       'durability', 'any'):
        keys.add(PREFIX + 'shop.applicable.' + applicable)
    # 方块与物品的名字走「原版格式」的键：block.<命名空间>.<路径> / item.<命名空间>.<路径>
    for machine in ('atm', 'exchange', 'lottery', 'vending', 'acquisition'):
        keys.add('block.%s.%s' % (PREFIX[:-1], machine))
    keys.add('item.%s.emerald_icon' % PREFIX[:-1])
    return {k for k in keys if not k.endswith('.')}


def config_keys():
    """配置界面的翻译键。

    NeoForge 的配置界面找不到 `emerald_economy.configuration.<键>` 时就显示原始键名，
    所以这里直接从 EEConfig.java 里把「配置项名」抠出来核对——以后加了配置项忘了写翻译，
    这个脚本就会报出来。

    键的拼法是 ConfigurationScreen 里的 fallback：`<modid>.configuration.<配置项名>`；
    层级（push 的段名）与列表的 `.button` 也是同一套规则。
    """
    keys = {PREFIX + 'configuration.title'}
    # 两种可能的配置文件名写法（NeoForge 会按文件名生成 section 键）
    for suffix in ('server', 'server.toml'):
        keys.add(PREFIX + 'configuration.section.emerald.economy.' + suffix)
        keys.add(PREFIX + 'configuration.section.emerald.economy.' + suffix + '.title')

    path = os.path.join(JAVA_ROOT, 'cn', 'daenx', 'mc', 'emeraldeconomy', 'config', 'EEConfig.java')
    if not os.path.isfile(path):
        return keys
    text = io.open(path, encoding='utf-8').read()
    # 普通配置项：只有名字，界面直接用键名做标签
    for name in re.findall(r'define(?:InRange|Enum|\(|Range)?\(\s*"([A-Za-z0-9_]+)"', text):
        keys.add(PREFIX + 'configuration.' + name)
    # 列表型配置项：会渲染成「名字... + 按钮」，按钮文字走 <键>.button
    for name in re.findall(r'defineList\(\s*"([A-Za-z0-9_]+)"', text):
        keys.add(PREFIX + 'configuration.' + name)
        keys.add(PREFIX + 'configuration.' + name + '.button')
    # 层级（push）：同样是「名字 + 按钮」
    for name in re.findall(r'\.push\(\s*"([A-Za-z0-9_]+)"', text):
        keys.add(PREFIX + 'configuration.' + name)
        keys.add(PREFIX + 'configuration.' + name + '.button')
    return keys


def main():
    zh = json.load(io.open(os.path.join(LANG_DIR, 'zh_cn.json'), encoding='utf-8'))
    en = json.load(io.open(os.path.join(LANG_DIR, 'en_us.json'), encoding='utf-8'))
    wanted = {k for k in (collect_java_keys() | dynamic_keys() | config_keys()) if not k.endswith('.')}

    missing_zh = sorted(k for k in wanted if k not in zh)
    missing_en = sorted(k for k in wanted if k not in en)
    extra_zh = sorted(k for k in zh if k not in wanted)
    extra_en = sorted(k for k in en if k not in wanted)

    print('代码中引用的翻译键：%d' % len(wanted))
    if missing_zh:
        print('zh_cn.json 缺少：')
        for key in missing_zh:
            print('  - ' + key)
    if missing_en:
        print('en_us.json 缺少：')
        for key in missing_en:
            print('  - ' + key)
    if extra_zh:
        print('zh_cn.json 多出的键：')
        for key in extra_zh:
            print('  + ' + key)
    if extra_en:
        print('en_us.json 多出的键（可能是动态生成，可忽略）：%d' % len(extra_en))
    if missing_zh or missing_en:
        return 1
    print('两种语言的翻译键都完整。')
    return 0


if __name__ == '__main__':
    sys.exit(main())
