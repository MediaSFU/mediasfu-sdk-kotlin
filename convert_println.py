#!/usr/bin/env python3
import os
import re

base_path = os.path.join(os.getcwd(), 'shared', 'src', 'commonMain', 'kotlin')
total_converted = 0
files_modified = 0

for root, dirs, files in os.walk(base_path):
    for filename in files:
        if not filename.endswith('.kt'):
            continue
        
        filepath = os.path.join(root, filename)
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
        
        if 'println' not in content:
            continue
        
        original = content
        tag = filename.replace('.kt', '')[:20]
        
        count_before = content.count('println(')
        
        if 'import com.mediasfu.sdk.util.Logger' not in content:
            content = re.sub(
                r'(^package\s+[\w.]+\s*\n)',
                r'\g<1>import com.mediasfu.sdk.util.Logger\n',
                content,
                count=1,
                flags=re.MULTILINE
            )
        
        content = content.replace('println(', 'Logger.d("' + tag + '", ')
        
        if content != original:
            with open(filepath, 'w', encoding='utf-8') as f:
                f.write(content)
            files_modified += 1
            total_converted += count_before
            print(f'Converted {count_before} in {filename}')

print(f'Total: {files_modified} files, {total_converted} println calls converted')
