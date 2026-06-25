# -*- coding: utf-8 -*-
"""
RockiotTag 项目优化方案 PDF 生成脚本
"""

import os
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import mm, cm
from reportlab.lib.colors import HexColor, white, black
from reportlab.lib.enums import TA_LEFT, TA_CENTER, TA_JUSTIFY
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, PageBreak,
    Table, TableStyle, ListFlowable, ListItem, KeepTogether
)
from reportlab.platypus.flowables import HRFlowable
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont

# ============================================================
# 注册中文字体（Windows 系统自带）
# ============================================================
def register_chinese_fonts():
    """注册中文字体"""
    font_paths = [
        ("C:/Windows/Fonts/msyh.ttc", "MicrosoftYaHei"),
        ("C:/Windows/Fonts/msyhbd.ttc", "MicrosoftYaHeiBold"),
        ("C:/Windows/Fonts/simsun.ttc", "SimSun"),
        ("C:/Windows/Fonts/simhei.ttf", "SimHei"),
        ("C:/Windows/Fonts/consola.ttf", "Consolas"),
    ]
    
    registered = {}
    for path, name in font_paths:
        if os.path.exists(path):
            try:
                pdfmetrics.registerFont(TTFont(name, path))
                registered[name] = True
                print(f"  ✓ 注册字体: {name}")
            except Exception as e:
                print(f"  ✗ 注册字体 {name} 失败: {e}")
    
    return registered

# ============================================================
# 自定义样式
# ============================================================
def create_styles():
    """创建自定义段落样式"""
    styles = {}
    
    # 正文
    styles['body'] = ParagraphStyle(
        'Body_CN',
        fontName='MicrosoftYaHei',
        fontSize=10,
        leading=18,
        spaceAfter=6,
        alignment=TA_JUSTIFY,
        textColor=HexColor('#333333'),
    )
    
    # 封面标题
    styles['cover_title'] = ParagraphStyle(
        'CoverTitle',
        fontName='MicrosoftYaHeiBold',
        fontSize=28,
        leading=40,
        alignment=TA_CENTER,
        textColor=HexColor('#003366'),
        spaceAfter=20,
    )
    
    # 封面副标题
    styles['cover_subtitle'] = ParagraphStyle(
        'CoverSubtitle',
        fontName='MicrosoftYaHei',
        fontSize=14,
        leading=24,
        alignment=TA_CENTER,
        textColor=HexColor('#666666'),
        spaceAfter=10,
    )
    
    # 一级标题
    styles['h1'] = ParagraphStyle(
        'H1_CN',
        fontName='MicrosoftYaHeiBold',
        fontSize=20,
        leading=30,
        spaceBefore=24,
        spaceAfter=12,
        textColor=HexColor('#003366'),
        borderPadding=(0, 0, 4, 0),
    )
    
    # 二级标题
    styles['h2'] = ParagraphStyle(
        'H2_CN',
        fontName='MicrosoftYaHeiBold',
        fontSize=15,
        leading=24,
        spaceBefore=18,
        spaceAfter=8,
        textColor=HexColor('#0066CC'),
        borderPadding=(0, 0, 2, 0),
    )
    
    # 三级标题
    styles['h3'] = ParagraphStyle(
        'H3_CN',
        fontName='MicrosoftYaHeiBold',
        fontSize=12,
        leading=20,
        spaceBefore=12,
        spaceAfter=6,
        textColor=HexColor('#444444'),
    )
    
    # 代码块
    styles['code'] = ParagraphStyle(
        'Code_CN',
        fontName='Consolas',
        fontSize=8,
        leading=13,
        spaceAfter=4,
        leftIndent=12,
        textColor=HexColor('#000080'),
        backColor=HexColor('#F5F5F5'),
        borderPadding=6,
        borderWidth=0.5,
        borderColor=HexColor('#DDDDDD'),
    )
    
    # 行内代码
    styles['inline_code'] = ParagraphStyle(
        'InlineCode',
        fontName='Consolas',
        fontSize=9,
        leading=16,
        textColor=HexColor('#C7254E'),
        backColor=HexColor('#F9F2F4'),
    )
    
    # 列表项
    styles['list_item'] = ParagraphStyle(
        'ListItem_CN',
        fontName='MicrosoftYaHei',
        fontSize=10,
        leading=18,
        spaceAfter=3,
        leftIndent=20,
        bulletIndent=10,
        textColor=HexColor('#333333'),
    )
    
    # 高优先级的列表项
    styles['list_item_high'] = ParagraphStyle(
        'ListItemHigh',
        fontName='MicrosoftYaHei',
        fontSize=10,
        leading=18,
        spaceAfter=3,
        leftIndent=20,
        bulletIndent=10,
        textColor=HexColor('#C62828'),
    )
    
    # 中等优先级的列表项
    styles['list_item_med'] = ParagraphStyle(
        'ListItemMed',
        fontName='MicrosoftYaHei',
        fontSize=10,
        leading=18,
        spaceAfter=3,
        leftIndent=20,
        bulletIndent=10,
        textColor=HexColor('#E65100'),
    )
    
    # 低优先级的列表项
    styles['list_item_low'] = ParagraphStyle(
        'ListItemLow',
        fontName='MicrosoftYaHei',
        fontSize=10,
        leading=18,
        spaceAfter=3,
        leftIndent=20,
        bulletIndent=10,
        textColor=HexColor('#2E7D32'),
    )
    
    # 表格单元格
    styles['table_header'] = ParagraphStyle(
        'TableHeader',
        fontName='MicrosoftYaHeiBold',
        fontSize=9,
        leading=14,
        alignment=TA_CENTER,
        textColor=white,
    )
    
    styles['table_cell'] = ParagraphStyle(
        'TableCell',
        fontName='MicrosoftYaHei',
        fontSize=8.5,
        leading=13,
        textColor=HexColor('#333333'),
    )
    
    styles['table_cell_bold'] = ParagraphStyle(
        'TableCellBold',
        fontName='MicrosoftYaHeiBold',
        fontSize=8.5,
        leading=13,
        textColor=HexColor('#333333'),
    )
    
    # 页眉页脚
    styles['footer'] = ParagraphStyle(
        'Footer',
        fontName='MicrosoftYaHei',
        fontSize=8,
        leading=12,
        alignment=TA_CENTER,
        textColor=HexColor('#999999'),
    )
    
    # 提示框
    styles['tip_box'] = ParagraphStyle(
        'TipBox',
        fontName='MicrosoftYaHei',
        fontSize=9.5,
        leading=16,
        spaceAfter=8,
        leftIndent=8,
        rightIndent=8,
        textColor=HexColor('#555555'),
        backColor=HexColor('#F0F8FF'),
        borderPadding=10,
        borderWidth=1,
        borderColor=HexColor('#BBDEFB'),
    )
    
    return styles


# ============================================================
# 辅助函数
# ============================================================
def heading1(text, styles):
    """一级标题 + 分隔线"""
    return [
        Paragraph(text, styles['h1']),
        HRFlowable(width="100%", thickness=1, color=HexColor('#003366'), spaceAfter=10),
    ]

def heading2(text, styles):
    """二级标题"""
    return [Paragraph(text, styles['h2'])]

def heading3(text, styles):
    """三级标题"""
    return [Paragraph(text, styles['h3'])]

def body(text, styles):
    """正文段落"""
    return [Paragraph(text, styles['body'])]

def code_block(text, styles):
    """代码块"""
    return [Paragraph(text.replace('\n', '<br/>'), styles['code'])]

def bullet(text, styles, priority=None):
    """列表项，根据优先级选择颜色"""
    if priority == 'high':
        s = styles['list_item_high']
    elif priority == 'medium':
        s = styles['list_item_med']
    elif priority == 'low':
        s = styles['list_item_low']
    else:
        s = styles['list_item']
    return [Paragraph(f"• {text}", s)]

def tip_box(text, styles):
    """提示框"""
    return [Paragraph(text, styles['tip_box'])]

def spacer(h=6):
    return [Spacer(1, h)]

def make_table(headers, rows, styles, col_widths=None):
    """创建格式化表格"""
    header_cells = [Paragraph(h, styles['table_header']) for h in headers]
    data = [header_cells]
    
    for row in rows:
        data.append([Paragraph(str(c), styles['table_cell']) for c in row])
    
    # 默认列宽
    if col_widths is None:
        col_widths = [170 * mm / len(headers)] * len(headers)
    
    t = Table(data, colWidths=col_widths, repeatRows=1)
    
    style_commands = [
        ('BACKGROUND', (0, 0), (-1, 0), HexColor('#0066CC')),
        ('TEXTCOLOR', (0, 0), (-1, 0), white),
        ('ALIGN', (0, 0), (-1, 0), 'CENTER'),
        ('FONTNAME', (0, 0), (-1, 0), 'MicrosoftYaHeiBold'),
        ('FONTSIZE', (0, 0), (-1, 0), 9),
        ('BOTTOMPADDING', (0, 0), (-1, 0), 8),
        ('TOPPADDING', (0, 0), (-1, 0), 8),
        ('GRID', (0, 0), (-1, -1), 0.5, HexColor('#CCCCCC')),
        ('VALIGN', (0, 0), (-1, -1), 'TOP'),
        ('TOPPADDING', (0, 1), (-1, -1), 6),
        ('BOTTOMPADDING', (0, 1), (-1, -1), 6),
        ('LEFTPADDING', (0, 0), (-1, -1), 8),
        ('RIGHTPADDING', (0, 0), (-1, -1), 8),
    ]
    
    # 隔行变色
    for i in range(1, len(data)):
        if i % 2 == 0:
            style_commands.append(('BACKGROUND', (0, i), (-1, i), HexColor('#F5F8FC')))
    
    t.setStyle(TableStyle(style_commands))
    return [t]


# ============================================================
# 页眉页脚
# ============================================================
def on_first_page(canvas, doc):
    """封面页不需要页眉页脚"""
    pass

def on_later_pages(canvas, doc):
    """内页页眉页脚"""
    canvas.saveState()
    
    # 页眉线
    canvas.setStrokeColor(HexColor('#0066CC'))
    canvas.setLineWidth(0.5)
    canvas.line(25*mm, A4[1] - 20*mm, A4[0] - 25*mm, A4[1] - 20*mm)
    
    # 页眉文字
    canvas.setFont('MicrosoftYaHei', 8)
    canvas.setFillColor(HexColor('#999999'))
    canvas.drawString(25*mm, A4[1] - 18*mm, "RockiotTag 项目优化方案")
    
    # 页脚
    canvas.setFont('MicrosoftYaHei', 8)
    canvas.drawCentredString(A4[0]/2, 15*mm, f"- {doc.page} -")
    
    canvas.restoreState()


# ============================================================
# 构建文档内容
# ============================================================
def build_content(styles):
    story = []
    s = styles  # 简写
    
    # ==================== 封面 ====================
    story.append(Spacer(1, 60*mm))
    story.append(Paragraph("RockiotTag", s['cover_title']))
    story.append(Paragraph("项目优化方案", s['cover_title']))
    story.append(Spacer(1, 15*mm))
    story.append(HRFlowable(width="50%", thickness=2, color=HexColor('#0066CC'), spaceAfter=15))
    story.append(Paragraph("Android BLE 物联网标签追踪应用", s['cover_subtitle']))
    story.append(Paragraph("代码审查 · 架构优化 · 安全加固 · 性能提升", s['cover_subtitle']))
    story.append(Spacer(1, 25*mm))
    story.append(Paragraph("2025年6月", s['cover_subtitle']))
    story.append(Paragraph("版本 1.0", s['cover_subtitle']))
    story.append(PageBreak())
    
    # ==================== 目录页 ====================
    story.extend(heading1("目录", s))
    story.extend(spacer(10))
    
    toc_items = [
        ("一、架构层面", [
            "1.1 双数据模型并存",
            "1.2 双数据库并存",
            "1.3 双 API 服务并存",
            "1.4 未完成的 MVVM 重构",
            "1.5 重复的模型类",
        ]),
        ("二、代码质量", [
            "2.1 单例线程安全问题",
            "2.2 无限重试的上传逻辑",
            "2.3 卡尔曼滤波器实现不完整",
            "2.4 AsyncTask 使用",
            "2.5 new Thread() 裸线程",
            "2.6 裸 new Handler() 无 Looper 指定",
            "2.7 日志泛滥",
        ]),
        ("三、构建与发布", [
            "3.1 代码混淆未开启",
            "3.2 敏感信息硬编码",
            "3.3 依赖版本统一管理",
            "3.4 部分 SDK 版本偏旧",
        ]),
        ("四、性能优化", [
            "4.1 LocationOptimizationManager 过于庞大",
            "4.2 地图双引擎并存",
            "4.3 DeviceApiService 未使用 HttpHelper",
            "4.4 JSON 解析方式不统一",
        ]),
        ("五、安全与清单", [
            "5.1 权限声明问题",
            "5.2 备份允许",
            "5.3 enableJetifier 仍开启",
        ]),
        ("六、测试与可维护性", [
            "6.1 测试覆盖不足",
            "6.2 未完成的功能标记不清晰",
        ]),
        ("七、优先级汇总", []),
    ]
    
    for section, items in toc_items:
        story.extend(body(f"<b>{section}</b>", s))
        for item in items:
            story.append(Paragraph(f"    {item}", s['body']))
    
    story.append(PageBreak())
    
    # ==================== 一、架构层面 ====================
    story.extend(heading1("一、架构层面", s))
    
    # 1.1
    story.extend(heading2("1.1 双数据模型并存（高优先级）", s))
    story.extend(body(
        '项目中同时存在 <font name="Consolas" color="#C7254E">Device</font>（根包）、'
        '<font name="Consolas" color="#C7254E">TagDevice</font>（model 包）、'
        '<font name="Consolas" color="#C7254E">DeviceEntity</font>（room 包）'
        '三个功能高度重叠的数据模型，导致大量转换代码（如 <font name="Consolas" color="#C7254E">DeviceRepository.convertToTagDevice()</font>）和心智负担。', s))
    
    story.extend(heading3("建议", s))
    story.extend(bullet('以 <font name="Consolas">TagDevice</font> 作为统一的领域模型', s))
    story.extend(bullet('<font name="Consolas">DeviceEntity</font> 仅作为 Room 持久化层模型', s))
    story.extend(bullet('<font name="Consolas">Device</font> 标记为 <font name="Consolas">@Deprecated</font> 并逐步迁移移除', s))
    story.extend(bullet('消除 <font name="Consolas">DeviceRepository</font> 中的手动字段拷贝转换方法', s))
    
    # 1.2
    story.extend(heading2("1.2 双数据库并存（高优先级）", s))
    story.extend(body(
        '项目同时使用了 <font name="Consolas" color="#C7254E">DatabaseHelper</font>'
        '（SQLiteOpenHelper，<font name="Consolas" color="#C7254E">rockiottag.db</font>，version 8）'
        '和 <font name="Consolas" color="#C7254E">AppDatabase</font>'
        '（Room，<font name="Consolas" color="#C7254E">rockiottag_room.db</font>，version 2），'
        '两套数据库存储相似数据（设备、位置记录、地址缓存），造成数据不一致风险和维护成本翻倍。', s))
    story.extend(tip_box(
        '<b>注意：</b> <font name="Consolas">AppDatabase</font> 中使用了 '
        '<font name="Consolas" color="#C7254E">.allowMainThreadQueries()</font> 临时允许主线程查询，应改为异步。', s))
    
    story.extend(heading3("建议", s))
    story.extend(bullet('全面迁移到 Room，废弃 <font name="Consolas">DatabaseHelper</font>', s))
    story.extend(bullet('将 <font name="Consolas">DatabaseHelper</font> 的升级逻辑迁移为 Room Migration', s))
    story.extend(bullet('移除 <font name="Consolas">allowMainThreadQueries()</font>，改用 <font name="Consolas">LiveData</font>/<font name="Consolas">Flow</font> 异步查询', s))
    
    # 1.3
    story.extend(heading2("1.3 双 API 服务并存（高优先级）", s))
    story.extend(body(
        '<font name="Consolas" color="#C7254E">NewApiService</font>（API Key 模式）和 '
        '<font name="Consolas" color="#C7254E">DeviceApiService</font>/'
        '<font name="Consolas" color="#C7254E">UserApiService</font>（Bearer Token 模式）并存，'
        '且 <font name="Consolas" color="#C7254E">UserApiService.login()</font> '
        '返回 <font name="Consolas" color="#C7254E">NewApiService.ApiResponse</font>，耦合混乱。'
        '<font name="Consolas" color="#C7254E">NewApiService</font> 中 '
        '<font name="Consolas" color="#C7254E">login()</font> 方法直接返回假成功。', s))
    
    story.extend(heading3("建议", s))
    story.extend(bullet('统一为一套 API 服务，按认证模式分支', s))
    story.extend(bullet('移除 <font name="Consolas">NewApiService.login()</font> 假实现', s))
    story.extend(bullet('<font name="Consolas">DeviceApiService</font> 中重复的 HTTP 请求代码统一走 <font name="Consolas">HttpHelper</font>', s))
    
    # 1.4
    story.extend(heading2("1.4 未完成的 MVVM 重构（中优先级）", s))
    story.extend(body(
        '部分 Fragment 仍是空壳，包含大量 TODO 未实现：'
        '<font name="Consolas" color="#C7254E">ControlPanelFragment</font>、'
        '<font name="Consolas" color="#C7254E">MapFragment</font>、'
        '<font name="Consolas" color="#C7254E">DeviceInfoFragment</font> 等。'
        '<font name="Consolas" color="#C7254E">MainActivity</font> 仍有 3987 行、294 条 Log，直接持有大量 View 和业务逻辑引用。', s))
    
    story.extend(heading3("建议", s))
    story.extend(bullet('完成或移除未完成的 Fragment', s))
    story.extend(bullet('<font name="Consolas">MainActivity</font> 业务逻辑下沉到 ViewModel/UseCase', s))
    story.extend(bullet('Activity 只负责 View 绑定和导航', s))
    story.extend(bullet('将 <font name="Consolas">MainActivity</font> 拆分为多个 Helper（已部分完成，继续推进）', s))
    
    # 1.5
    story.extend(heading2("1.5 重复的模型类（中优先级）", s))
    story.extend(body(
        '<font name="Consolas" color="#C7254E">StayPoint</font> 存在于根包和 model 包两份；'
        '<font name="Consolas" color="#C7254E">LocationManager</font>（根包）和 '
        '<font name="Consolas" color="#C7254E">location/LocationManager</font>（子包）重名；'
        '<font name="Consolas" color="#C7254E">GoogleGeocoder</font>（根包）和 '
        '<font name="Consolas" color="#C7254E">map/google/GoogleGeocoderService</font> 功能重叠。', s))
    
    story.extend(heading3("建议", s))
    story.extend(bullet('合并重复类，删除废弃版本，统一命名规范', s))
    
    story.append(PageBreak())
    
    # ==================== 二、代码质量 ====================
    story.extend(heading1("二、代码质量", s))
    
    # 2.1
    story.extend(heading2("2.1 单例线程安全问题（高优先级）", s))
    story.extend(body(
        '多个单例的 <font name="Consolas" color="#C7254E">getInstance()</font> 不是线程安全的'
        '（缺少 <font name="Consolas" color="#C7254E">synchronized</font> 或双重检查锁）：', s))
    
    # 表格：单例状态
    singleton_headers = ['类名', '线程安全状态', '建议']
    singleton_rows = [
        ['DeviceApiService', '❌ 无同步', '添加 DCL'],
        ['UserApiService', '❌ 无同步', '添加 DCL'],
        ['NewApiService', '❌ 无同步', '添加 DCL'],
        ['IconCache', '❌ 无同步', '添加 DCL'],
        ['UnboundDeviceManager', '❌ 无同步', '添加 DCL'],
        ['BLERepository', '❌ 无同步', '添加 DCL'],
        ['LocationRepository', '❌ 无同步', '添加 DCL'],
        ['DeviceRepository', '✅ synchronized', '—'],
        ['DataRepository', '✅ volatile + DCL', '—'],
        ['AppDatabase', '✅ volatile + DCL', '—'],
        ['NetworkManager', '✅ synchronized', '—'],
    ]
    story.extend(make_table(singleton_headers, singleton_rows, s, [120, 100, 100]))
    
    story.extend(heading3("建议", s))
    story.extend(bullet('统一使用 volatile + 双重检查锁（DCL）或静态内部类持有单例', s))
    
    # 2.2
    story.extend(heading2("2.2 无限重试的上传逻辑（高优先级）", s))
    story.extend(body(
        '<font name="Consolas" color="#C7254E">LocationOptimizationManager.syncLocationToServerImmediately</font> '
        '使用 <font name="Consolas" color="#C7254E">while(!success)</font> 无限循环 + 20 秒 sleep，'
        '如果服务器持续不可用，线程将永不退出，持续消耗资源。', s))
    
    story.extend(heading3("建议", s))
    story.extend(bullet('设置最大重试次数（如 10 次）', s))
    story.extend(bullet('使用 <font name="Consolas">RetryableTask</font>（已存在！）替代手写循环', s))
    story.extend(bullet('改用 WorkManager 管理上传任务，支持持久化和退避策略', s))
    
    # 2.3
    story.extend(heading2("2.3 卡尔曼滤波器实现不完整（中优先级）", s))
    story.extend(body(
        '根包 <font name="Consolas" color="#C7254E">LocationManager.kalmanFilter()</font> '
        '只处理纬度，未处理经度，代码注释也承认了这一点。'
        '而 <font name="Consolas" color="#C7254E">util/LocationKalmanFilter</font> 已有完整双维度实现。', s))
    
    story.extend(heading3("建议", s))
    story.extend(bullet('删除 <font name="Consolas">LocationManager.kalmanFilter()</font>，统一使用 <font name="Consolas">LocationKalmanFilter</font>', s))
    
    # 2.4
    story.extend(heading2("2.4 AsyncTask 使用（中优先级）", s))
    story.extend(body(
        '<font name="Consolas" color="#C7254E">CrowdSourcingManager</font> 使用了已废弃的 '
        '<font name="Consolas" color="#C7254E">AsyncTask</font>（API 30+ 已废弃）。', s))
    
    story.extend(heading3("建议", s))
    story.extend(bullet('替换为 <font name="Consolas">ExecutorService</font> + <font name="Consolas">Handler</font> 或 RxJava/Coroutines', s))
    
    # 2.5
    story.extend(heading2("2.5 new Thread() 裸线程（中优先级）", s))
    story.extend(body(
        '项目中有 <b>21 处</b>直接 <font name="Consolas" color="#C7254E">new Thread().start()</font>，'
        '无线程池管理，无取消机制，无异常统一处理。部分线程持有 Activity Context 可能导致泄漏。', s))
    
    story.extend(heading3("建议", s))
    story.extend(bullet('统一使用 <font name="Consolas">ExecutorService</font>（<font name="Consolas">DataRepository</font> 已有 4 线程池，可复用）', s))
    story.extend(bullet('网络请求线程使用 <font name="Consolas">NetworkManager.executeWithRetry()</font>', s))
    story.extend(bullet('考虑引入 RxJava 或 Kotlin Coroutines 简化异步代码', s))
    
    # 2.6
    story.extend(heading2("2.6 裸 new Handler() 无 Looper 指定（中优先级）", s))
    story.extend(body(
        '<b>12 处</b> <font name="Consolas" color="#C7254E">new Handler()</font> 中，'
        '<font name="Consolas" color="#C7254E">BLEManager</font>、<font name="Consolas" color="#C7254E">MapManager</font> '
        '等未指定 Looper，在子线程创建时可能崩溃。', s))
    
    story.extend(heading3("建议", s))
    story.extend(bullet('统一使用 <font name="Consolas">new Handler(Looper.getMainLooper())</font> 或使用已封装的 <font name="Consolas">SafeHandler</font>', s))
    
    # 2.7
    story.extend(heading2("2.7 日志泛滥（中优先级）", s))
    story.extend(body(
        '<font name="Consolas" color="#C7254E">MainActivity</font> 294 条 Log，'
        '<font name="Consolas" color="#C7254E">TrackActivity</font> 272 条，'
        '<font name="Consolas" color="#C7254E">LocationOptimizationManager</font> 203 条，'
        '且 Release 构建未关闭日志（<font name="Consolas" color="#C7254E">minifyEnabled false</font>）。', s))
    
    story.extend(heading3("建议", s))
    story.extend(bullet('创建 <font name="Consolas">LogUtil</font> 封装，Release 构建自动关闭 <font name="Consolas">Log.d/v/i</font>', s))
    story.extend(bullet('保留 <font name="Consolas">Log.e/w</font> 用于错误监控', s))
    story.extend(bullet('开启 <font name="Consolas">minifyEnabled true</font> 并配置 ProGuard 移除日志调用', s))
    
    story.append(PageBreak())
    
    # ==================== 三、构建与发布 ====================
    story.extend(heading1("三、构建与发布", s))
    
    # 3.1
    story.extend(heading2("3.1 代码混淆未开启（高优先级）", s))
    story.extend(body(
        'Release 包 <font name="Consolas" color="#C7254E">minifyEnabled false</font>，'
        '未开启混淆和资源压缩，导致 APK 体积偏大、代码易被反编译、API Key 等硬编码暴露风险。', s))
    
    story.extend(heading3("建议", s))
    story.extend(bullet('设置 <font name="Consolas">minifyEnabled true</font> 和 <font name="Consolas">shrinkResources true</font>', s))
    story.extend(bullet('创建 <font name="Consolas">proguard-rules.pro</font> 文件（当前缺失！），保留高德地图、Google Maps、Gson、Room 等规则', s))
    story.extend(bullet('签名密码和 API Key 迁移到 <font name="Consolas">local.properties</font> 或环境变量', s))
    
    # 3.2
    story.extend(heading2("3.2 敏感信息硬编码（高优先级）", s))
    story.extend(body(
        '签名密码（<font name="Consolas" color="#C7254E">storePassword</font>、'
        '<font name="Consolas" color="#C7254E">keyPassword</font>）、'
        '多个客户 API Key、Google Maps API Key 均明文硬编码在源码中。', s))
    
    story.extend(heading3("建议", s))
    story.extend(bullet('签名密码移到 <font name="Consolas">gradle.properties</font>（不提交 Git）或环境变量', s))
    story.extend(bullet('API Key 移到 <font name="Consolas">local.properties</font> 或通过 <font name="Consolas">buildConfigField</font> 注入', s))
    story.extend(bullet('在 <font name="Consolas">.gitignore</font> 中确保敏感配置文件不被提交', s))
    
    # 3.3
    story.extend(heading2("3.3 依赖版本统一管理（低优先级）", s))
    story.extend(body(
        '版本号散落在 <font name="Consolas" color="#C7254E">build.gradle</font> 各处，'
        '如 <font name="Consolas" color="#C7254E">lifecycle_version</font>、'
        '<font name="Consolas" color="#C7254E">room_version</font>、'
        '<font name="Consolas" color="#C7254E">camerax_version</font> 使用局部变量，未统一管理。', s))
    
    story.extend(heading3("建议", s))
    story.extend(bullet('迁移到 Version Catalog（<font name="Consolas">libs.versions.toml</font>），Gradle 8 原生支持', s))
    story.extend(bullet('或在根 <font name="Consolas">build.gradle</font> 的 <font name="Consolas">ext</font> 块统一定义', s))
    
    # 3.4
    story.extend(heading2("3.4 部分 SDK 版本偏旧（低优先级）", s))
    
    sdk_headers = ['依赖', '当前版本', '最新版本']
    sdk_rows = [
        ['appcompat', '1.6.1', '1.7.x'],
        ['constraintlayout', '2.1.4', '2.2.x'],
        ['core', '1.12.0', '1.13.x'],
        ['camerax', '1.3.1', '1.4.x'],
        ['play-services-maps', '18.2.0', '19.x'],
    ]
    story.extend(make_table(sdk_headers, sdk_rows, s, [160, 80, 80]))
    story.extend(spacer(6))
    story.extend(bullet('评估升级，注意兼容性测试', s))
    
    story.append(PageBreak())
    
    # ==================== 四、性能优化 ====================
    story.extend(heading1("四、性能优化", s))
    
    # 4.1
    story.extend(heading2("4.1 LocationOptimizationManager 过于庞大（中优先级）", s))
    story.extend(body(
        '该类有 <b>800+ 行</b>，203 条 Log，承担了 BLE 扫描调度、位置融合、'
        '服务器同步、离线缓存、MAC 映射等过多职责，违反单一职责原则。', s))
    
    story.extend(heading3("建议", s))
    story.extend(bullet('拆分为 <font name="Consolas">LocationSyncManager</font>（服务器同步）', s))
    story.extend(bullet('拆分为 <font name="Consolas">DeviceMacMapper</font>（MAC 映射）', s))
    story.extend(bullet('拆分为 <font name="Consolas">OfflineCacheManager</font>（离线缓存）', s))
    story.extend(bullet('BLE 扫描调度委托给已有的 <font name="Consolas">OptimizedBLEScanner</font>', s))
    
    # 4.2
    story.extend(heading2("4.2 地图双引擎并存（中优先级）", s))
    story.extend(body(
        '高德地图和 Google 地图的标记、轨迹、播放逻辑在 <font name="Consolas" color="#C7254E">MainActivity</font>、'
        '<font name="Consolas" color="#C7254E">TrackActivity</font> 中完全重复维护'
        '（<font name="Consolas" color="#C7254E">Marker</font> vs <font name="Consolas" color="#C7254E">googleMarker</font>，'
        '<font name="Consolas" color="#C7254E">Polyline</font> vs <font name="Consolas" color="#C7254E">googlePolyline</font> 等）。', s))
    
    story.extend(heading3("建议", s))
    story.extend(bullet('定义 <font name="Consolas">IMapAdapter</font> 接口，抽象标记、折线、相机操作', s))
    story.extend(bullet('<font name="Consolas">AMapManager</font> 和 <font name="Consolas">GoogleMapManager</font> 实现该接口', s))
    story.extend(bullet('Activity/Fragment 只面向接口编程，消除双份变量', s))
    
    # 4.3
    story.extend(heading2("4.3 DeviceApiService 未使用 HttpHelper（中优先级）", s))
    story.extend(body(
        '<font name="Consolas" color="#C7254E">DeviceApiService</font> 和 '
        '<font name="Consolas" color="#C7254E">UserApiService</font> 中多处手动创建 '
        '<font name="Consolas" color="#C7254E">HttpURLConnection</font>，'
        '未复用 <font name="Consolas" color="#C7254E">HttpHelper</font>，'
        '超时配置、错误处理不一致。', s))
    
    story.extend(heading3("建议", s))
    story.extend(bullet('<font name="Consolas">HttpHelper</font> 增加 <font name="Consolas">getWithAuth(url, token)</font> 和 <font name="Consolas">postWithAuth(url, body, token)</font> 方法', s))
    story.extend(bullet('统一所有 HTTP 调用', s))
    
    # 4.4
    story.extend(heading2("4.4 JSON 解析方式不统一（低优先级）", s))
    story.extend(body(
        '部分代码用 <font name="Consolas" color="#C7254E">Gson</font> 反序列化，'
        '部分用 <font name="Consolas" color="#C7254E">JsonParser</font> 手动解析，'
        '部分两者混用。<font name="Consolas" color="#C7254E">NewApiService</font> '
        '中还使用了 Java 10+ 的 <font name="Consolas" color="#C7254E">var</font> 关键字，风格不统一。', s))
    
    story.extend(heading3("建议", s))
    story.extend(bullet('定义统一的 Response DTO 类，用 <font name="Consolas">Gson.fromJson()</font> 反序列化', s))
    story.extend(bullet('移除手动 JSON 解析，统一风格', s))
    
    story.append(PageBreak())
    
    # ==================== 五、安全与清单 ====================
    story.extend(heading1("五、安全与清单", s))
    
    # 5.1
    story.extend(heading2("5.1 权限声明问题（中优先级）", s))
    story.extend(body(
        '<font name="Consolas" color="#C7254E">BLUETOOTH_SCAN</font> 设置了 '
        '<font name="Consolas" color="#C7254E">neverForLocation</font>，'
        '但如果 BLE 扫描结果用于推导位置，应移除此标志。'
        '同时 <font name="Consolas" color="#C7254E">BLUETOOTH</font> 和 '
        '<font name="Consolas" color="#C7254E">BLUETOOTH_ADMIN</font> 权限应添加 '
        '<font name="Consolas" color="#C7254E">android:maxSdkVersion="30"</font> 限制。', s))
    
    # 5.2
    story.extend(heading2("5.2 备份允许（低优先级）", s))
    story.extend(body(
        '<font name="Consolas" color="#C7254E">android:allowBackup="true"</font> '
        '允许通过 <font name="Consolas" color="#C7254E">adb backup</font> 导出应用数据'
        '（含数据库、SharedPreferences 中的 token），有数据泄露风险。', s))
    
    story.extend(heading3("建议", s))
    story.extend(bullet('生产环境设为 <font name="Consolas">false</font>', s))
    
    # 5.3
    story.extend(heading2("5.3 enableJetifier 仍开启（低优先级）", s))
    story.extend(body(
        '<font name="Consolas" color="#C7254E">android.enableJetifier=true</font>，'
        '如果所有依赖已迁移到 AndroidX，应关闭以提升构建速度。', s))
    
    story.append(PageBreak())
    
    # ==================== 六、测试与可维护性 ====================
    story.extend(heading1("六、测试与可维护性", s))
    
    # 6.1
    story.extend(heading2("6.1 测试覆盖不足（中优先级）", s))
    story.extend(body(
        '<font name="Consolas" color="#C7254E">build.gradle</font> 引入了 JUnit、Mockito、Espresso，'
        '但项目中未见实际测试类。UseCase 层（<font name="Consolas" color="#C7254E">GetDeviceInfoUseCase</font> 等）'
        '和 Repository 层是可测试的，但缺乏测试。', s))
    
    story.extend(heading3("建议", s))
    story.extend(bullet('为 UseCase 和 Repository 编写单元测试', s))
    story.extend(bullet('为关键算法（<font name="Consolas">LocationKalmanFilter</font>、<font name="Consolas">TrackCalculator</font>、<font name="Consolas">BLETagFilter</font>）编写测试', s))
    story.extend(bullet('Mock 网络层和数据库层', s))
    
    # 6.2
    story.extend(heading2("6.2 未完成的功能标记不清晰（低优先级）", s))
    story.extend(body(
        '散落的 TODO 没有统一追踪。<font name="Consolas" color="#C7254E">fragment/</font> 包下的多个 Fragment'
        '（<font name="Consolas" color="#C7254E">ControlPanelFragment</font>、'
        '<font name="Consolas" color="#C7254E">MapFragment</font> 等）基本是空壳。', s))
    
    story.extend(heading3("建议", s))
    story.extend(bullet('评估这些 Fragment 是否需要，不需要则删除', s))
    story.extend(bullet('需要的则排期实现，创建 Issue 追踪', s))
    
    story.append(PageBreak())
    
    # ==================== 七、优先级汇总 ====================
    story.extend(heading1("七、优先级汇总", s))
    
    priority_headers = ['优先级', '优化项', '预期收益']
    priority_rows = [
        ['🔴 高', '开启代码混淆 + 创建 ProGuard 规则', 'APK 瘦身、防反编译'],
        ['🔴 高', '敏感信息移出源码', '安全合规'],
        ['🔴 高', '统一数据模型（Device/TagDevice/DeviceEntity）', '降低复杂度'],
        ['🔴 高', '统一数据库（废弃 DatabaseHelper，全面 Room）', '消除数据不一致'],
        ['🔴 高', '修复单例线程安全', '防止并发崩溃'],
        ['🔴 高', '修复无限重试上传逻辑', '防止资源耗尽'],
        ['🔴 高', '统一 API 服务层', '降低维护成本'],
        ['🟡 中', '完成 MVVM 重构，瘦身 MainActivity（3987行）', '可维护性'],
        ['🟡 中', '地图双引擎抽象适配器', '消除重复代码'],
        ['🟡 中', '统一 HTTP 调用走 HttpHelper', '一致性'],
        ['🟡 中', '替换 AsyncTask 和裸 new Thread', '稳定性'],
        ['🟡 中', '统一 Handler 指定 Looper', '防 ANR/崩溃'],
        ['🟡 中', '日志治理 + Release 关闭日志', '性能、安全'],
        ['🟡 中', '拆分 LocationOptimizationManager', '单一职责'],
        ['🟡 中', '补充单元测试', '质量保障'],
        ['🟢 低', '关闭 allowBackup 和 Jetifier', '安全、构建速度'],
        ['🟢 低', '依赖版本升级和统一管理', '技术债'],
        ['🟢 低', '清理废弃类和重复类', '整洁度'],
    ]
    story.extend(make_table(priority_headers, priority_rows, s, [60, 200, 110]))
    
    story.extend(spacer(20))
    story.extend(body('<b>共计 18 项优化建议</b>：高优先级 7 项、中优先级 8 项、低优先级 3 项。', s))
    story.extend(body(
        '建议按优先级顺序分阶段推进：<b>第一阶段</b>先完成高优先级的 7 项（安全 + 架构核心问题），'
        '<b>第二阶段</b>推进中优先级的代码质量和性能优化，<b>第三阶段</b>处理低优先级的收尾工作。', s))
    
    story.extend(spacer(30))
    story.append(HRFlowable(width="30%", thickness=1, color=HexColor('#0066CC'), spaceAfter=10))
    story.extend(body('<i>— 文档结束 —</i>', s))
    
    return story


# ============================================================
# 主函数
# ============================================================
def main():
    print("=" * 60)
    print("  RockiotTag 项目优化方案 PDF 生成工具")
    print("=" * 60)
    
    # 注册字体
    print("\n📝 注册中文字体...")
    registered = register_chinese_fonts()
    
    if 'MicrosoftYaHei' not in registered:
        print("\n⚠️  警告：未找到微软雅黑字体，尝试备用字体...")
        # 尝试其他字体
        alt_fonts = [
            ("C:/Windows/Fonts/simsun.ttc", "SimSun"),
            ("C:/Windows/Fonts/simhei.ttf", "SimHei"),
        ]
        for path, name in alt_fonts:
            if os.path.exists(path):
                pdfmetrics.registerFont(TTFont(name, path))
                # 将样式中所有 MicrosoftYaHei 替换
                print(f"  使用备用字体: {name}")
                break
    
    # 创建样式
    print("\n🎨 创建文档样式...")
    styles = create_styles()
    
    # 输出路径
    output_path = r"d:/Project/Android/RockiotTag/RockiotTag_优化方案.pdf"
    
    # 创建文档
    print(f"\n📄 正在生成 PDF: {output_path}")
    
    doc = SimpleDocTemplate(
        output_path,
        pagesize=A4,
        leftMargin=25*mm,
        rightMargin=25*mm,
        topMargin=25*mm,
        bottomMargin=25*mm,
        title='RockiotTag 项目优化方案',
        author='Code Review Team',
        subject='Android Project Optimization',
    )
    
    # 构建内容
    story = build_content(styles)
    
    # 生成 PDF
    doc.build(story, onFirstPage=on_first_page, onLaterPages=on_later_pages)
    
    print(f"\n✅ PDF 生成成功！")
    print(f"📄 输出文件: {output_path}")
    print(f"\n💡 在 PowerShell 中运行以下命令打开 PDF：")
    print(f"   start '{output_path}'")

if __name__ == '__main__':
    main()
