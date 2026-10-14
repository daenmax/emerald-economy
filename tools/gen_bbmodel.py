# -*- coding: utf-8 -*-
"""生成 Blockbench 工程文件（.bbmodel），方便以后继续在 Blockbench 里改模型。

为什么要有这个脚本：
  - 方块贴图与模型是 2026-10 用 Blockbench 重做的，但 Blockbench 的 MCP 插件
    没有「保存工程到磁盘」的能力，输出只能通过返回值拿回来；
  - 与其把工程文件手工拼一遍，不如用脚本从「同一份几何定义 + 贴图文件」重新生成，
    这样 5 台机器的工程永远和资源文件一致，也不会漏掉某台机器。

几何定义直接复用 `tools/gen_resources.py` 里的 `machine_model()`——
也就是说**模型只有一个真源**：改了 gen_resources.py 里的几何，
工程文件与资源文件都会跟着变。

用法：python tools/gen_bbmodel.py
输出：素材/blockbench/<机器>.bbmodel
"""
import base64
import io
import json
import os
import sys
import uuid

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, 'tools'))

import gen_resources  # noqa: E402  （先加路径再导入）

OUT_DIR = os.path.join(ROOT, '素材', 'blockbench')
TEXTURE_DIR = os.path.join(ROOT, 'src', 'main', 'resources', 'assets',
                           gen_resources.MOD_ID, 'textures', 'block')


def texture_entry(name, particle):
    """把磁盘上的贴图打包成 .bbmodel 里内嵌的贴图条目。"""
    path = os.path.join(TEXTURE_DIR, '%s.png' % name)
    with open(path, 'rb') as f:
        data = base64.b64encode(f.read()).decode('ascii')
    return {
        'name': name,
        'path': '',
        'folder': 'block',
        'namespace': gen_resources.MOD_ID,
        'id': '',
        'group': '',
        'width': 16,
        'height': 16,
        'uv_width': 16,
        'uv_height': 16,
        'particle': particle,
        'use_as_default': False,
        'layers_enabled': False,
        'render_mode': 'default',
        'render_sides': 'auto',
        'pbr_channel': 'color',
        'frame_time': 1,
        'frame_order_type': 'loop',
        'visible': True,
        'internal': True,
        'saved': True,
        'uuid': str(uuid.uuid4()),
        'source': 'data:image/png;base64,' + data,
    }


def element_entry(element, texture_ids):
    """把一个元素转成 .bbmodel 的写法（faces 用贴图下标 + uv）。"""
    faces = {}
    for face, spec in element['faces'].items():
        key = spec['texture']
        index = texture_ids.index(key) if key in texture_ids else 0
        faces[face] = {'uv': list(spec['uv']), 'texture': index}
    return {
        'name': element['name'],
        'box_uv': False,
        'rescale': False,
        'locked': False,
        'light_emission': 0,
        'export': True,
        'allow_mirror_modeling': True,
        'from': list(element['from']),
        'to': list(element['to']),
        'autouv': 0,
        'color': 0,
        'origin': [0, 0, 0],
        'faces': faces,
        'type': 'cube',
        'uuid': str(uuid.uuid4()),
    }


def build(machine):
    """一台机器的 .bbmodel 内容。"""
    model = gen_resources.machine_model(machine)
    texture_ids = ['#base', '#face']
    textures = [
        texture_entry('machine_base', True),
        texture_entry('%s_face' % machine, False),
    ]
    textures[0]['id'] = '0'
    textures[1]['id'] = '1'
    elements = [element_entry(e, texture_ids) for e in model['elements']]
    return {
        'meta': {'format_version': '5.0', 'model_format': 'java_block', 'box_uv': False},
        'name': machine,
        'parent': '',
        'ambientocclusion': True,
        'front_gui_light': False,
        'visible_box': [1, 1, 0],
        'resolution': {'width': 16, 'height': 16},
        'elements': elements,
        'groups': [],
        'outliner': [e['uuid'] for e in elements],
        'textures': textures,
    }


def main():
    if not os.path.isdir(OUT_DIR):
        os.makedirs(OUT_DIR)
    for machine in gen_resources.MACHINES:
        path = os.path.join(OUT_DIR, '%s.bbmodel' % machine)
        with io.open(path, 'w', encoding='utf-8') as f:
            json.dump(build(machine), f, ensure_ascii=False, indent=1)
            f.write('\n')
        print('已生成 %s' % path)
    return 0


if __name__ == '__main__':
    sys.exit(main())
