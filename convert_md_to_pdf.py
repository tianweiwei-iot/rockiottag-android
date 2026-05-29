"""
Markdown 转 PDF 文档转换脚本
将 API_Documentation_DR.md 转换为 API_Documentation_DR.pdf
"""

import markdown
from weasyprint import HTML, CSS
import os

def convert_md_to_pdf(md_file, pdf_file):
    """将 Markdown 文件转换为 PDF 文档"""
    
    print("📖 正在读取 Markdown 文件...")
    # 读取 Markdown 文件
    with open(md_file, 'r', encoding='utf-8') as f:
        md_content = f.read()
    
    print("🔄 正在转换为 HTML...")
    # 将 Markdown 转换为 HTML
    html_content = markdown.markdown(
        md_content,
        extensions=['tables', 'fenced_code', 'codehilite']
    )
    
    # 添加完整的 HTML 结构和样式
    full_html = f"""
    <!DOCTYPE html>
    <html lang="zh-CN">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>DR_API 设备查询接口文档</title>
        <style>
            @page {{
                size: A4;
                margin: 2cm;
            }}
            
            body {{
                font-family: "Microsoft YaHei", "SimSun", sans-serif;
                font-size: 11pt;
                line-height: 1.6;
                color: #333;
                max-width: 210mm;
                margin: 0 auto;
                padding: 20px;
            }}
            
            h1 {{
                color: #003366;
                font-size: 24pt;
                border-bottom: 3px solid #003366;
                padding-bottom: 10px;
                margin-top: 30px;
                margin-bottom: 20px;
            }}
            
            h2 {{
                color: #0066CC;
                font-size: 18pt;
                border-bottom: 2px solid #0066CC;
                padding-bottom: 8px;
                margin-top: 25px;
                margin-bottom: 15px;
            }}
            
            h3 {{
                color: #333333;
                font-size: 14pt;
                margin-top: 20px;
                margin-bottom: 10px;
            }}
            
            p {{
                margin: 10px 0;
                text-align: justify;
            }}
            
            code {{
                background-color: #f4f4f4;
                padding: 2px 6px;
                border-radius: 3px;
                font-family: "Consolas", "Courier New", monospace;
                font-size: 9pt;
                color: #c7254e;
            }}
            
            pre {{
                background-color: #f8f8f8;
                border: 1px solid #ddd;
                border-radius: 5px;
                padding: 15px;
                overflow-x: auto;
                margin: 15px 0;
            }}
            
            pre code {{
                background-color: transparent;
                padding: 0;
                color: #000080;
                font-size: 9pt;
            }}
            
            table {{
                border-collapse: collapse;
                width: 100%;
                margin: 15px 0;
            }}
            
            th, td {{
                border: 1px solid #ddd;
                padding: 8px 12px;
                text-align: left;
            }}
            
            th {{
                background-color: #0066CC;
                color: white;
                font-weight: bold;
            }}
            
            tr:nth-child(even) {{
                background-color: #f9f9f9;
            }}
            
            ul, ol {{
                margin: 10px 0;
                padding-left: 30px;
            }}
            
            li {{
                margin: 5px 0;
            }}
            
            strong {{
                color: #003366;
                font-weight: bold;
            }}
            
            a {{
                color: #0066CC;
                text-decoration: none;
            }}
            
            hr {{
                border: none;
                border-top: 2px solid #eee;
                margin: 20px 0;
            }}
            
            .header-info {{
                background-color: #f0f8ff;
                padding: 15px;
                border-left: 4px solid #0066CC;
                margin: 15px 0;
            }}
            
            .warning {{
                background-color: #fff3cd;
                padding: 10px 15px;
                border-left: 4px solid #ffc107;
                margin: 15px 0;
            }}
            
            .api-key {{
                background-color: #e7f3ff;
                padding: 10px;
                border: 1px dashed #0066CC;
                font-family: "Consolas", monospace;
                font-size: 10pt;
                margin: 10px 0;
            }}
        </style>
    </head>
    <body>
        {html_content}
    </body>
    </html>
    """
    
    print("📄 正在生成 PDF...")
    # 生成 PDF
    try:
        HTML(string=full_html).write_pdf(
            pdf_file,
            stylesheets=[CSS(string='''
                @page {
                    size: A4;
                    margin: 2cm;
                }
            ''')]
        )
        print(f"✅ 转换完成！")
        print(f"📄 输出文件：{pdf_file}")
        return True
    except Exception as e:
        print(f"❌ 转换失败：{str(e)}")
        import traceback
        traceback.print_exc()
        return False

if __name__ == '__main__':
    # 文件路径
    md_file = r'C:\Users\Administrator\Desktop\API_Documentation_DR.md'
    pdf_file = r'C:\Users\Administrator\Desktop\API_Documentation_DR.pdf'
    
    print("=" * 60)
    print("  Markdown 转 PDF 转换工具")
    print("=" * 60)
    print(f"📝 输入文件：{md_file}")
    print(f"📄 输出文件：{pdf_file}")
    print("-" * 60)
    
    # 检查输入文件是否存在
    if not os.path.exists(md_file):
        print(f"❌ 错误：找不到文件 {md_file}")
        exit(1)
    
    # 执行转换
    success = convert_md_to_pdf(md_file, pdf_file)
    
    print("-" * 60)
    if success:
        print("✨ 转换成功！您可以打开 PDF 文档查看结果。")
        print("\n💡 提示：在 PowerShell 中运行以下命令打开 PDF：")
        print(f"   start {pdf_file}")
    else:
        print("⚠️  转换失败，请检查错误信息。")
        print("\n💡 可能需要安装 WeasyPrint 依赖：")
        print("   pip install weasyprint")
