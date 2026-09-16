#!/usr/bin/env python3
import sys
import re
import xml.etree.ElementTree as ET

def get_current_pom_version(pom_path='pom.xml'):
    tree = ET.parse(pom_path)
    ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
    ver = tree.findtext('.//m:version', '', namespaces=ns).strip()
    return ver

def compute_bumped_version(cur_ver, bump_type):
    m = re.match(r'^(\d+)\.(\d+)\.(\d+)(.*)$', cur_ver)
    if not m:
        return cur_ver
    maj, min_v, pat, extra = int(m.group(1)), int(m.group(2)), int(m.group(3)), m.group(4)
    if bump_type == 'major':
        return f"{maj + 1}.0.0"
    elif bump_type == 'minor':
        return f"{maj}.{min_v + 1}.0"
    elif bump_type == 'patch':
        return f"{maj}.{min_v}.{pat + 1}"
    return cur_ver

def resolve_target_version(cur_ver, raw_ver, bump_type, ref_name, event_name):
    raw_ver = (raw_ver or '').strip()
    bump_type = (bump_type or '').strip().lower()
    ref_name = (ref_name or '').strip()
    event_name = (event_name or '').strip().lower()

    if raw_ver:
        return raw_ver.lstrip('v')
    if event_name in ('push', 'release') and ref_name and ref_name not in ('main', 'master'):
        return ref_name.lstrip('v')
    if bump_type in ('patch', 'minor', 'major'):
        return compute_bumped_version(cur_ver, bump_type)
    return cur_ver

def update_pom_version(target_ver, pom_path='pom.xml'):
    with open(pom_path, 'r', encoding='utf-8') as f:
        content = f.read()
    new_content, count = re.subn(
        r'(<artifactId>speedrun-switch</artifactId>\s*<version>)[^<]+(</version>)',
        r'\g<1>' + target_ver + r'\g<2>',
        content,
        count=1
    )
    if count > 0:
        with open(pom_path, 'w', encoding='utf-8') as f:
            f.write(new_content)
    return count > 0

if __name__ == '__main__':
    args = sys.argv[1:]
    update = False
    if args and args[0] == '--update':
        update = True
        args = args[1:]

    raw_ver = args[0] if len(args) > 0 else ''
    bump_type = args[1] if len(args) > 1 else 'none'
    ref_name = args[2] if len(args) > 2 else ''
    event_name = args[3] if len(args) > 3 else ''

    current_ver = get_current_pom_version()
    target_ver = resolve_target_version(current_ver, raw_ver, bump_type, ref_name, event_name)

    if update and current_ver != target_ver:
        update_pom_version(target_ver)

    print(target_ver)
