# -*- coding: utf-8 -*-
"""给中英语言文件补上本轮新增的键（界面文案 + 配置界面翻译）。

用法：python tools/add_lang_round3.py
只做「按 key 覆盖/追加」，不动已有排版；重复运行是安全的。
"""
import io
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LANG_DIR = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'emerald_economy', 'lang')
P = 'emerald_economy.'

ZH = {
    P + 'atm.deposit_all_ok': '已存入背包里的全部 %s 绿宝石（到账 %s，手续费 %s）',
    P + 'atm.withdraw_all_ok': '已取出全部余额 %s 绿宝石（手续费 %s）',
    P + 'atm.fail_no_space': '背包放不下这么多绿宝石，请先清出空间',
    P + 'screen.atm.button.deposit_all': '存入全部',
    P + 'screen.atm.button.withdraw_all': '取出全部',
    P + 'screen.atm.records.tab.balance': '账户余额明细',
    P + 'screen.atm.records.tab.debt': '贷款欠款明细',
    P + 'screen.atm.records.balance_before': '变动前余额',
    P + 'screen.atm.records.balance_after': '变动后余额',
    P + 'screen.atm.records.debt_before': '变动前欠款',
    P + 'screen.atm.records.debt_after': '变动后欠款',
    P + 'screen.atm.records.no_debt': '暂无贷款欠款变动',

    P + 'shop.group.enchanted_book': '附魔书',
    P + 'shop.group.other': '其他',
    P + 'shop.sub.seed': '种子',
    P + 'shop.sub.sapling': '树苗',
    P + 'shop.buy_ok': '购买成功：%s 已放入背包',
    P + 'shop.upgrade_ok': '升级成功，解锁了 %s，当前已解锁 %s 个格子',
    P + 'shop.upgraded': '【商店升级】%s 升级了自动售货机，解锁了 %s，当前已解锁 %s 个格子！',

    P + 'acquire.category.misc': '杂物',
    P + 'acquire.fail_debt': '还有贷款没还清，不能参加疯狂星期五',
    P + 'acquire.fail_batch': '数量还没凑够一份（该物品要凑够若干个才收）',
    P + 'screen.acquire.batch_price': '每 %s 个 → %s',
    P + 'screen.acquire.friday_blocked': '【疯狂星期五】重金收购：%s（有欠款未清，暂不能参加）',

    P + 'configuration.title': '绿宝石经济 · 配置',
    P + 'configuration.section.emerald.economy.server.toml': '服务端配置',
    P + 'configuration.section.emerald.economy.server.toml.title': '绿宝石经济 · 服务端配置',
    P + 'configuration.section.emerald.economy.server': '服务端配置',
    P + 'configuration.section.emerald.economy.server.title': '绿宝石经济 · 服务端配置',

    P + 'configuration.account': '账户',
    P + 'configuration.account.button': '编辑',
    P + 'configuration.market': '基金市场',
    P + 'configuration.market.button': '编辑',
    P + 'configuration.lottery': '抽奖',
    P + 'configuration.lottery.button': '编辑',
    P + 'configuration.shop': '自动售货机',
    P + 'configuration.shop.button': '编辑',
    P + 'configuration.acquire': '自动收购机',
    P + 'configuration.acquire.button': '编辑',

    P + 'configuration.depositFeeRate': '存款手续费率',
    P + 'configuration.withdrawFeeRate': '取款手续费率',
    P + 'configuration.dailyInterestRate': '账户余额日利率',
    P + 'configuration.interestMinBalance': '计息起算余额',
    P + 'configuration.loanInterestRate': '贷款日利率',
    P + 'configuration.creditLimit': '信用额度',
    P + 'configuration.maxTransactionRecords': '保留流水条数',

    P + 'configuration.priceUpdateIntervalTicks': '价格更新间隔（刻）',
    P + 'configuration.momentumDecay': '动量衰减系数',
    P + 'configuration.meanReversion': '默认均值回归系数',
    P + 'configuration.maxMomentum': '单次最大波动幅度',
    P + 'configuration.priceMinFactor': '价格下界系数',
    P + 'configuration.priceMaxFactor': '价格上界系数',
    P + 'configuration.eventSensitivity': '世界事件灵敏度',
    P + 'configuration.weatherStrength': '天气修正强度',
    P + 'configuration.marketEventChance': '随机市场事件概率',
    P + 'configuration.tradeFeeRate': '基金交易手续费率',
    P + 'configuration.tradeCooldownMillis': '交易冷却（毫秒）',
    P + 'configuration.historySize': '走势图价格点数',
    P + 'configuration.fundBasePrices': '基金基础价格',
    P + 'configuration.fundVolatilities': '基金波动率',
    P + 'configuration.fundMeanReversions': '基金均值回归系数',
    P + 'configuration.fundDailySupplies': '基金每日预期供给量',
    P + 'configuration.weatherModifiers': '天气修正表',
    P + 'configuration.marketEvents': '随机市场事件表',

    P + 'configuration.oddsOddEven': '猜单双赔率',
    P + 'configuration.oddsBigSmall': '猜大小赔率',
    P + 'configuration.oddsLastDigit': '猜尾数赔率',
    P + 'configuration.dailyBetLimit': '每场次每日下注次数',

    P + 'configuration.initialSlots': '初始解锁格子数',
    P + 'configuration.dailyPurchaseLimit': '每格每日限购次数',
    P + 'configuration.upgradeCostMin': '升级费用下限',
    P + 'configuration.upgradeCostMax': '升级费用上限',
    P + 'configuration.priceMin': '未列表附魔最低价',
    P + 'configuration.priceMax': '未列表附魔最高价',
    P + 'configuration.treasureMultiplierMin': '宝藏附魔倍率下限',
    P + 'configuration.treasureMultiplierMax': '宝藏附魔倍率上限',
    P + 'configuration.enchantmentPrices': '附魔价格与分类表',
    P + 'configuration.otherItems': '其他商品表',
    P + 'configuration.priceOverrides': '单件附魔价格覆盖',
    P + 'configuration.excludedEnchantments': '不参与售卖的附魔',

    P + 'configuration.agriculture': '农业产物价格表',
    P + 'configuration.livestock': '畜牧产物价格表',
    P + 'configuration.mining': '矿物价格表',
    P + 'configuration.forestry': '林业产物价格表',
    P + 'configuration.misc': '杂物价格表',
    P + 'configuration.batchSizes': '批次收购表',
    P + 'configuration.fridayIntervalDays': '疯狂星期五开放间隔',
    P + 'configuration.fridayPickCount': '每日挑选物品种类数',
    P + 'configuration.fridayMultiplierMin': '疯狂星期五倍率下限',
    P + 'configuration.fridayMultiplierMax': '疯狂星期五倍率上限',
    P + 'configuration.fridayPool': '疯狂星期五物品池',
    P + 'configuration.fridayBasePrices': '疯狂星期五基准价',
}

EN = {
    P + 'atm.deposit_all_ok': 'Deposited all %s emeralds from your inventory (credited %s, fee %s)',
    P + 'atm.withdraw_all_ok': 'Withdrew the whole balance: %s emeralds (fee %s)',
    P + 'atm.fail_no_space': 'Not enough inventory space for that many emeralds',
    P + 'screen.atm.button.deposit_all': 'Deposit all',
    P + 'screen.atm.button.withdraw_all': 'Withdraw all',
    P + 'screen.atm.records.tab.balance': 'Balance details',
    P + 'screen.atm.records.tab.debt': 'Debt details',
    P + 'screen.atm.records.balance_before': 'Balance before',
    P + 'screen.atm.records.balance_after': 'Balance after',
    P + 'screen.atm.records.debt_before': 'Debt before',
    P + 'screen.atm.records.debt_after': 'Debt after',
    P + 'screen.atm.records.no_debt': 'No debt changes yet',

    P + 'shop.group.enchanted_book': 'Enchanted Books',
    P + 'shop.group.other': 'Other',
    P + 'shop.sub.seed': 'Seeds',
    P + 'shop.sub.sapling': 'Saplings',
    P + 'shop.buy_ok': 'Purchase complete: %s is in your inventory',
    P + 'shop.upgrade_ok': 'Upgrade complete, unlocked %s (%s slots so far)',
    P + 'shop.upgraded': '[Shop Upgrade] %s upgraded the vending machine, unlocking %s (%s slots so far)!',

    P + 'acquire.category.misc': 'Miscellaneous',
    P + 'acquire.fail_debt': 'You still have an unpaid loan; Crazy Friday is off-limits',
    P + 'acquire.fail_batch': 'Not enough items to make up one batch yet',
    P + 'screen.acquire.batch_price': 'per %s -> %s',
    P + 'screen.acquire.friday_blocked': '[Crazy Friday] Premium buyout: %s (blocked while you owe money)',

    P + 'configuration.title': 'Emerald Economy Configuration',
    P + 'configuration.section.emerald.economy.server.toml': 'Server settings',
    P + 'configuration.section.emerald.economy.server.toml.title': 'Emerald Economy - Server Config',
    P + 'configuration.section.emerald.economy.server': 'Server settings',
    P + 'configuration.section.emerald.economy.server.title': 'Emerald Economy - Server Config',

    P + 'configuration.account': 'Account',
    P + 'configuration.account.button': 'Edit',
    P + 'configuration.market': 'Fund market',
    P + 'configuration.market.button': 'Edit',
    P + 'configuration.lottery': 'Lottery',
    P + 'configuration.lottery.button': 'Edit',
    P + 'configuration.shop': 'Vending machine',
    P + 'configuration.shop.button': 'Edit',
    P + 'configuration.acquire': 'Acquisition machine',
    P + 'configuration.acquire.button': 'Edit',

    P + 'configuration.depositFeeRate': 'Deposit fee rate',
    P + 'configuration.withdrawFeeRate': 'Withdraw fee rate',
    P + 'configuration.dailyInterestRate': 'Daily interest rate',
    P + 'configuration.interestMinBalance': 'Interest starts at balance',
    P + 'configuration.loanInterestRate': 'Daily loan interest rate',
    P + 'configuration.creditLimit': 'Credit limit',
    P + 'configuration.maxTransactionRecords': 'Kept transaction records',

    P + 'configuration.priceUpdateIntervalTicks': 'Price update interval (ticks)',
    P + 'configuration.momentumDecay': 'Momentum decay',
    P + 'configuration.meanReversion': 'Default mean reversion',
    P + 'configuration.maxMomentum': 'Max momentum',
    P + 'configuration.priceMinFactor': 'Price lower bound factor',
    P + 'configuration.priceMaxFactor': 'Price upper bound factor',
    P + 'configuration.eventSensitivity': 'World event sensitivity',
    P + 'configuration.weatherStrength': 'Weather modifier strength',
    P + 'configuration.marketEventChance': 'Random event chance',
    P + 'configuration.tradeFeeRate': 'Trade fee rate',
    P + 'configuration.tradeCooldownMillis': 'Trade cooldown (ms)',
    P + 'configuration.historySize': 'Chart price points',
    P + 'configuration.fundBasePrices': 'Fund base prices',
    P + 'configuration.fundVolatilities': 'Fund volatilities',
    P + 'configuration.fundMeanReversions': 'Fund mean reversions',
    P + 'configuration.fundDailySupplies': 'Fund daily expected supply',
    P + 'configuration.weatherModifiers': 'Weather modifiers',
    P + 'configuration.marketEvents': 'Random market events',

    P + 'configuration.oddsOddEven': 'Odd/even odds',
    P + 'configuration.oddsBigSmall': 'Big/small odds',
    P + 'configuration.oddsLastDigit': 'Last digit odds',
    P + 'configuration.dailyBetLimit': 'Bets per game per day',

    P + 'configuration.initialSlots': 'Initial unlocked slots',
    P + 'configuration.dailyPurchaseLimit': 'Daily purchase limit',
    P + 'configuration.upgradeCostMin': 'Upgrade cost min',
    P + 'configuration.upgradeCostMax': 'Upgrade cost max',
    P + 'configuration.priceMin': 'Fallback min price',
    P + 'configuration.priceMax': 'Fallback max price',
    P + 'configuration.treasureMultiplierMin': 'Treasure multiplier min',
    P + 'configuration.treasureMultiplierMax': 'Treasure multiplier max',
    P + 'configuration.enchantmentPrices': 'Enchantment prices & categories',
    P + 'configuration.otherItems': 'Other goods',
    P + 'configuration.priceOverrides': 'Per-enchantment price overrides',
    P + 'configuration.excludedEnchantments': 'Excluded enchantments',

    P + 'configuration.agriculture': 'Farm produce prices',
    P + 'configuration.livestock': 'Livestock prices',
    P + 'configuration.mining': 'Mineral prices',
    P + 'configuration.forestry': 'Forestry prices',
    P + 'configuration.misc': 'Miscellaneous prices',
    P + 'configuration.batchSizes': 'Batch sizes',
    P + 'configuration.fridayIntervalDays': 'Crazy Friday interval (days)',
    P + 'configuration.fridayPickCount': 'Daily picked item count',
    P + 'configuration.fridayMultiplierMin': 'Crazy Friday multiplier min',
    P + 'configuration.fridayMultiplierMax': 'Crazy Friday multiplier max',
    P + 'configuration.fridayPool': 'Crazy Friday item pool',
    P + 'configuration.fridayBasePrices': 'Crazy Friday base prices',
}

LIST_KEYS = ('fundBasePrices', 'fundVolatilities', 'fundMeanReversions', 'fundDailySupplies',
             'weatherModifiers', 'marketEvents', 'enchantmentPrices', 'otherItems',
             'priceOverrides', 'excludedEnchantments', 'agriculture', 'livestock', 'mining',
             'forestry', 'misc', 'batchSizes', 'fridayPool', 'fridayBasePrices')


def main():
    for table, button in ((ZH, '编辑'), (EN, 'Edit')):
        for leaf in LIST_KEYS:
            table[P + 'configuration.' + leaf + '.button'] = button
    for name, table in (('zh_cn.json', ZH), ('en_us.json', EN)):
        path = os.path.join(LANG_DIR, name)
        with io.open(path, encoding='utf-8') as handle:
            data = json.load(handle)
        for key, value in table.items():
            data[key] = value
        with io.open(path, 'w', encoding='utf-8', newline='\n') as handle:
            json.dump(data, handle, ensure_ascii=False, indent=2)
            handle.write('\n')
        print('%s 共 %d 个键' % (name, len(data)))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
