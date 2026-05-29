package com.RockiotTag.tag.model;

/**
 * 设备标签枚举 - 统一管理标签图标映射
 * 
 * 优势：
 * 1. 消除 MainActivity 和 Adapter 中的重复代码
 * 2. 类型安全，避免拼写错误
 * 3. 易于扩展新标签
 * 4. 支持多语言（可扩展）
 */
public enum DeviceTag {
    // 地点类
    HOME("home", "🏠"),
    OFFICE("office", "🏢"),
    SCHOOL("school", "🏫"),
    HOSPITAL("hospital", "🏥"),
    MALL("mall", "🏬"),
    PARK("park", "🌳"),
    GYM("gym", "🏋️"),
    RESTAURANT("restaurant", "🍽️"),
    
    // 宠物类
    PET("pet", "🐾"),
    DOG("dog", "🐕"),
    CAT("cat", "🐱"),
    BIRD("bird", "🐦"),
    PIG("pig", "🐷"),
    
    // 交通工具类
    CAR("car", "🚗"),
    BIKE("bike", "🚴"),
    MOTORCYCLE("moto", "🏍️"),
    
    // 物品类
    LUGGAGE("luggage", "🧳"),
    KEY("key", "🔑"),
    WALLET("wallet", "👛"),
    PHONE("phone", "📱"),
    COMPUTER("computer", "💻"),
    BAG("bag", "👜"),
    BANK_CARD("bank_card", "💳"),
    
    // 人物类
    BOY("boy", "👦"),
    GIRL("girl", "👧");
    
    private final String code;
    private final String emoji;
    
    DeviceTag(String code, String emoji) {
        this.code = code;
        this.emoji = emoji;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getEmoji() {
        return emoji;
    }
    
    /**
     * 根据标签代码获取对应的 Emoji 图标
     * 
     * @param code 标签代码
     * @return Emoji 图标，如果未找到则返回默认图标
     */
    public static String getEmoji(String code) {
        if (code == null || code.isEmpty()) {
            return "";
        }
        
        // 处理中文标签（向后兼容）
        if ("家".equals(code)) return HOME.emoji;
        if ("公司".equals(code)) return OFFICE.emoji;
        if ("学校".equals(code)) return SCHOOL.emoji;
        if ("医院".equals(code)) return HOSPITAL.emoji;
        if ("商场".equals(code)) return MALL.emoji;
        if ("公园".equals(code)) return PARK.emoji;
        if ("健身房".equals(code)) return GYM.emoji;
        if ("餐厅".equals(code)) return RESTAURANT.emoji;
        if ("宠物".equals(code)) return PET.emoji;
        if ("车".equals(code)) return CAR.emoji;
        if ("自行车".equals(code)) return BIKE.emoji;
        if ("摩托车".equals(code)) return MOTORCYCLE.emoji;
        if ("行李".equals(code)) return LUGGAGE.emoji;
        if ("钥匙".equals(code)) return KEY.emoji;
        if ("钱包".equals(code)) return WALLET.emoji;
        if ("手机".equals(code)) return PHONE.emoji;
        if ("电脑".equals(code)) return COMPUTER.emoji;
        if ("包".equals(code)) return BAG.emoji;
        
        // 查找英文标签
        for (DeviceTag tag : values()) {
            if (tag.code.equalsIgnoreCase(code)) {
                return tag.emoji;
            }
        }
        
        // 默认图标
        return "🏷️";
    }
    
    /**
     * 根据标签代码获取 DeviceTag 枚举
     * 
     * @param code 标签代码
     * @return DeviceTag 枚举，如果未找到则返回 null
     */
    public static DeviceTag fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        
        for (DeviceTag tag : values()) {
            if (tag.code.equalsIgnoreCase(code)) {
                return tag;
            }
        }
        
        return null;
    }
    
    /**
     * 判断是否是有效的标签代码
     * 
     * @param code 标签代码
     * @return 是否有效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }
}
