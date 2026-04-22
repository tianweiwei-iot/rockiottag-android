# 设备位置查询 API 文档

## 概述

本文档描述了 Tag 设备位置查询系统的 API 接口，供客户直接查询设备数据使用。

<br />

**端口说明**:

| 设备编号格式      | 端口   | 示例               |
| ----------- | ---- | ---------------- |
| 12位数字/MAC地址 | 8080 | E067F0BD854B     |
| 16位数字       | 8081 | 1756727204034763 |

***

## 1. 获取所有设备列表

**接口地址**: `GET /api/devices/all`

**请求示例**:

端口8080:

```
GET http://8.217.22.251:8080/api/devices/all
```

端口8081:

```
GET http://8.217.22.251:8081/api/devices/all
```

**响应示例**:

```json
[
    {
        "id": 1,
        "userId": 1,
        "deviceNum": "E067F0BD854B",
        "nickName": "Tag 2",
        "latitude": 22.5765269,
        "longitude": 113.9189489,
        "battery": 85,
        "timestamp": 1704067200000,
        "address": "null",
        "createdAt": "2024-01-01T00:00:00.000+00:00",
        "updatedAt": "2024-01-01T12:00:00.000+00:00"
    },
    {
        "id": 2,
        "userId": 1,
        "deviceNum": "D8A700B1628F",
        "nickName": "Tag 1",
        "latitude": 22.5765698,
        "longitude": 113.9188735,
        "battery": 100,
        "timestamp": 1704067260000,
        "address": "广东省深圳市南山区",
        "createdAt": "2024-01-01T00:00:00.000+00:00",
        "updatedAt": "2024-01-01T12:01:00.000+00:00"
    }
]
```

**字段说明**:

| 字段名       | 类型      | 说明           |
| --------- | ------- | ------------ |
| id        | int     | 设备记录ID       |
| userId    | int     | 用户ID         |
| deviceNum | String  | 设备编号         |
| nickName  | String  | 设备昵称         |
| latitude  | Double  | 纬度（WGS84坐标系） |
| longitude | Double  | 经度（WGS84坐标系） |
| battery   | Integer | 电池电量百分比      |
| timestamp | Long    | 位置上报时间戳（毫秒）  |
| address   | String  | 地址描述（暂无）     |
| createdAt | Date    | 创建时间         |
| updatedAt | Date    | 更新时间         |

***

## 2. 获取设备最新位置

**接口地址**: `GET /api/devices/{deviceNum}/latest`

**路径参数**:

| 参数名       | 类型     | 必填 | 说明   |
| --------- | ------ | -- | ---- |
| deviceNum | String | 是  | 设备编号 |

**请求示例**:

12位设备号/MAC地址（端口8080）:

```
GET http://8.217.22.251:8080/api/devices/E067F0BD854B/latest
```

16位设备号（端口8081）:

```
GET http://8.217.22.251:8081/api/devices/1756727204034763/latest
```

**响应示例**:

```json
{
    "deviceNum": "E067F0BD854B",
    "nickName": "Tag 2",
    "latitude": 22.5765269,
    "longitude": 113.9189489,
    "battery": 85,
    "timestamp": 1704067200000,
    "address": "null",
    "updatedAt": "2024-01-01T12:00:00.000+00:00"
}
```

**字段说明**:

| 字段名       | 类型      | 说明           |
| --------- | ------- | ------------ |
| deviceNum | String  | 设备编号         |
| nickName  | String  | 设备昵称         |
| latitude  | Double  | 纬度（WGS84坐标系） |
| longitude | Double  | 经度（WGS84坐标系） |
| battery   | Integer | 电池电量百分比      |
| timestamp | Long    | 位置上报时间戳（毫秒）  |
| address   | String  | 地址描述         |
| updatedAt | Date    | 更新时间         |

***

## 3. 错误码说明

| HTTP状态码 | 说明      |
| ------- | ------- |
| 200     | 请求成功    |
| 400     | 请求参数错误  |
| 404     | 资源不存在   |
| 500     | 服务器内部错误 |

***

## 4. 使用示例

### 4.1 curl 示例

```bash
# 获取所有设备列表（端口8080）
curl -X GET http://8.217.22.251:8080/api/devices/all

# 获取所有设备列表（端口8081）
curl -X GET http://8.217.22.251:8081/api/devices/all

# 获取12位设备最新位置（端口8080）
curl -X GET http://8.217.22.251:8080/api/devices/E067F0BD854B/latest

# 获取16位设备最新位置（端口8081）
curl -X GET http://8.217.22.251:8081/api/devices/1756727204034763/latest
```

### 4.2 Python 示例

```python
import requests

def get_all_devices(port=8080):
    """获取所有设备列表"""
    url = f"http://8.217.22.251:{port}/api/devices/all"
    response = requests.get(url)
    return response.json()

def get_device_latest(device_num):
    """获取设备最新位置"""
    if len(device_num) == 12 or ':' in device_num:
        port = 8080
    else:
        port = 8081
    url = f"http://8.217.22.251:{port}/api/devices/{device_num}/latest"
    response = requests.get(url)
    return response.json()

# 使用示例

# 获取所有设备
devices = get_all_devices(8080)
print(f"共有 {len(devices)} 个设备")

# 获取设备最新位置
device_num = "E067F0BD854B"
latest = get_device_latest(device_num)
print(f"设备 {device_num}:")
print(f"  纬度: {latest['latitude']}")
print(f"  经度: {latest['longitude']}")
print(f"  电量: {latest['battery']}%")
print(f"  地址: {latest['address']}")
```

### 4.3 JavaScript 示例

```javascript
// 获取所有设备列表
async function getAllDevices(port = 8080) {
    const response = await fetch(`http://8.217.22.251:${port}/api/devices/all`);
    const data = await response.json();
    console.log(`共有 ${data.length} 个设备`);
    return data;
}

// 获取设备最新位置
async function getLatestPosition(deviceNum) {
    const port = (deviceNum.length === 12 || deviceNum.includes(':')) ? 8080 : 8081;
    const response = await fetch(`http://8.217.22.251:${port}/api/devices/${deviceNum}/latest`);
    const data = await response.json();
    console.log('设备最新位置:', data);
    return data;
}

// 使用示例
getAllDevices(8080);
getLatestPosition('E067F0BD854B');
getLatestPosition('1756727204034763');
```

***

## 5. 注意事项

1. **坐标系说明**: 所有经纬度坐标均为 WGS84 坐标系，如需在国内地图（如高德地图、百度地图）上显示，需要进行坐标转换。
2. **设备编号格式**:
   - 12位数字：如 `123456789012`
   - 16位数字：如 `1756727204034763`
   - MAC地址格式（无冒号）：如 `E067F0BD854B`
3. **端口选择规则**:
   - 12位设备号或MAC地址格式 → 使用端口 **8080**
   - 16位设备号 → 使用端口 **8081**
4. **时间戳格式**: 所有时间戳均为毫秒级 Unix 时间戳。
5. **请求频率限制**: 建议请求间隔不少于1秒，避免对服务器造成压力。

***

## 6. 常见问题

**Q: 如何获取设备编号？**
A: 设备编号由系统分配，可在设备标签上查看或联系管理员获取。

**Q: 返回的坐标是什么坐标系？**
A: 返回的是 WGS84 坐标系，即 GPS 原始坐标。如需在高德地图显示，需转换为 GCJ02 坐标系。

**Q: 如何判断使用哪个端口？**
A: 12位设备号或MAC地址格式使用8080端口，16位设备号使用8081端口。

**Q: 如何获取所有设备列表？**
A: 使用 `/api/devices/all` 接口可以获取对应端口下的所有设备列表。

***

## 7. 联系方式

如有技术问题，请联系技术支持。

**文档版本**: v1.3\
**更新日期**: 2026年4月
