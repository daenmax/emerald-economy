# -*- coding: utf-8 -*-
"""一键准备开发用的服务端运行目录（run/）。

为什么需要它：`run/` 是 gitignore 的临时目录，删掉之后服务端会自己生成一份
**原版默认** 的 server.properties —— 普通地形 + 生存模式。
而界面实证测试里有一步是「用真实的挖掘动作挖掉 3 块铁矿石」，
生存模式下单击一次只会开始挖掘、并不会把方块打掉，那几条断言就会静默失败。
所以每次重建 run/ 之后，先用这个脚本把环境摆成测试期望的样子。

用法：python tools/setup-dev-server.py
"""
import hashlib
import io
import json
import os
import uuid

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RUN = os.path.join(ROOT, 'run')
RUN_ALEX = os.path.join(ROOT, 'run-alex')

# 超平坦：基岩 1 层 + 泥土 2 层 + 草方块 1 层，地面在 y=-61
FLAT_SETTINGS = ('{"layers":[{"block":"minecraft:bedrock","height":1},'
                 '{"block":"minecraft:dirt","height":2},'
                 '{"block":"minecraft:grass_block","height":1}],'
                 '"biome":"minecraft:plains"}')

SERVER_PROPERTIES = """level-type=minecraft\\:flat
generator-settings={flat}
online-mode=false
enforce-secure-profile=false
spawn-protection=0
max-players=4
view-distance=6
simulation-distance=6
sync-chunk-writes=false
allow-cheats=true
difficulty=normal
spawn-monsters=false
gamemode=creative
""".format(flat=FLAT_SETTINGS)

CLIENT_OPTIONS = """guiScale:2
fov:0.0
renderDistance:6
pauseOnLostFocus:false
narrator:0
lang:zh_cn
onboardAccessibility:false
skipMultiplayerWarning:true
tutorialStep:none
"""


def offline_uuid(name):
    """离线模式的玩家 UUID（和原版 OfflinePlayer 的算法一致）。"""
    digest = hashlib.md5(('OfflinePlayer:' + name).encode('utf-8')).digest()
    raw = bytearray(digest)
    raw[6] = (raw[6] & 0x0F) | 0x30
    raw[8] = (raw[8] & 0x3F) | 0x80
    return str(uuid.UUID(bytes=bytes(raw)))


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with io.open(path, 'w', encoding='utf-8', newline='\n') as handle:
        handle.write(text)
    print('写入 ' + os.path.relpath(path, ROOT))


def main():
    os.makedirs(RUN, exist_ok=True)
    os.makedirs(RUN_ALEX, exist_ok=True)

    write(os.path.join(RUN, 'server.properties'), SERVER_PROPERTIES)
    write(os.path.join(RUN, 'eula.txt'), 'eula=true\n')
    write(os.path.join(RUN, 'options.txt'), CLIENT_OPTIONS)
    # 第二个客户端用独立目录，免得两个客户端抢同一份日志与配置
    write(os.path.join(RUN_ALEX, 'options.txt'), CLIENT_OPTIONS)

    ops = [
        {"uuid": offline_uuid('Dev'), "name": "Dev", "level": 4, "bypassesPlayerLimit": True},
        {"uuid": offline_uuid('Alex'), "name": "Alex", "level": 4, "bypassesPlayerLimit": True},
    ]
    write(os.path.join(RUN, 'ops.json'), json.dumps(ops, ensure_ascii=False))

    print('')
    print('开发用运行目录已就绪。提醒：')
    print('  - 换了地形/模式之后要删掉 run/world，否则旧存档会沿用旧设置')
    print('  - 界面实证测试默认跑主用例：gradlew runClientUiTest')


if __name__ == '__main__':
    main()
