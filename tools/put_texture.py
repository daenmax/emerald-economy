# -*- coding: utf-8 -*-
"""把 Blockbench 返回的贴图 data URL 解码成资源目录里的 PNG。

为什么单独写个小工具：Blockbench 的 MCP 插件只能把贴图「读出来」（data URL），
没有直接往工程目录写文件的能力，所以由这个脚本负责落盘。

用法：python tools/put_texture.py <名字> <data-url 文件>
     例如：python tools/put_texture.py atm_face /tmp/atm_face.txt
名字不带 .png；落盘位置是 assets/emerald_economy/textures/block/<名字>.png
"""
import base64
import io
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEXTURE_DIR = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'emerald_economy',
                           'textures', 'block')


def decode(url):
    prefix = 'data:image/png;base64,'
    if url.startswith(prefix):
        url = url[len(prefix):]
    return base64.b64decode(url.strip())


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 1
    name, path = sys.argv[1], sys.argv[2]
    with io.open(path, encoding='utf-8') as handle:
        url = handle.read()
    data = decode(url)
    if not os.path.isdir(TEXTURE_DIR):
        os.makedirs(TEXTURE_DIR)
    out = os.path.join(TEXTURE_DIR, '%s.png' % name)
    with open(out, 'wb') as handle:
        handle.write(data)
    print('已写入 %s（%d 字节）' % (out, len(data)))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
