#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
批量清理Java文件中的日志代码
"""
import os
import re

def clean_logs_in_file(file_path):
    """清理单个文件中的日志代码"""
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        original_content = content
        
        # 删除 Log.d, Log.e, Log.i, Log.w, Log.v 调用
        content = re.sub(r'\s*Log\.(d|e|i|w|v)\([^;]+\);', '', content)
        
        # 删除 System.out.println 调用
        content = re.sub(r'\s*System\.out\.println\([^;]+\);', '', content)
        
        # 删除 printStackTrace() 调用
        content = re.sub(r'\s*e\.printStackTrace\(\);', '', content)
        
        # 如果内容有变化，写回文件
        if content != original_content:
            with open(file_path, 'w', encoding='utf-8') as f:
                f.write(content)
            return True
        return False
    except Exception as e:
        print(f"Error processing {file_path}: {e}")
        return False

def main():
    # 定义要扫描的目录
    directories = [
        r'D:\Android\RockiotTag\app\src\main\java\com\RockiotTag\tag',
        r'D:\Android\RockiotTag\RockiotTagBackend\src\main\java\com\rockiot\tag'
    ]
    
    cleaned_count = 0
    
    for directory in directories:
        if not os.path.exists(directory):
            continue
            
        for root, dirs, files in os.walk(directory):
            for file in files:
                if file.endswith('.java'):
                    file_path = os.path.join(root, file)
                    if clean_logs_in_file(file_path):
                        print(f"Cleaned: {file_path}")
                        cleaned_count += 1
    
    print(f"\nTotal files cleaned: {cleaned_count}")

if __name__ == '__main__':
    main()
