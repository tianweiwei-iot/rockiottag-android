package com.RockiotTag.tag.util;

import android.text.InputFilter;
import android.text.Spanned;

/**
 * 设备昵称长度过滤器
 * 规则：
 * - 纯中文：最多10个汉字
 * - 纯英文/数字：最多25个字符
 * - 混合输入：中文占2.5个英文字符的权重
 *   例如：1个汉字 = 2.5个英文字符位置
 *   公式：汉字数 * 2.5 + 英文字母数 <= 25
 */
public class DeviceNicknameFilter implements InputFilter {
    
    private static final int MAX_CHINESE_CHARS = 10;
    private static final int MAX_ENGLISH_CHARS = 25;
    private static final float CHINESE_WEIGHT = 2.5f; // 1个汉字相当于2.5个英文字符
    
    @Override
    public CharSequence filter(CharSequence source, int start, int end, 
                               Spanned dest, int dstart, int dend) {
        // 计算当前已有的字符权重
        float currentWeight = calculateWeight(dest.toString());
        
        // 计算新输入字符的权重
        String newText = source.subSequence(start, end).toString();
        float newWeight = calculateWeight(newText);
        
        // 总权重
        float totalWeight = currentWeight + newWeight;
        
        // 如果超过最大权重，拒绝输入
        if (totalWeight > MAX_ENGLISH_CHARS) {
            return "";
        }
        
        // 额外检查：如果全是中文，不能超过10个
        if (isAllChinese(dest.toString() + newText)) {
            int chineseCount = countChineseChars(dest.toString() + newText);
            if (chineseCount > MAX_CHINESE_CHARS) {
                return "";
            }
        }
        
        return null; // 接受输入
    }
    
    /**
     * 计算字符串的权重
     * 中文字符权重 = 2.5
     * 英文/数字/符号权重 = 1
     */
    private float calculateWeight(String text) {
        float weight = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isChineseChar(c)) {
                weight += CHINESE_WEIGHT;
            } else {
                weight += 1;
            }
        }
        return weight;
    }
    
    /**
     * 判断是否为中文字符
     */
    private boolean isChineseChar(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
            || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
            || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
            || block == Character.UnicodeBlock.GENERAL_PUNCTUATION
            || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
            || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS;
    }
    
    /**
     * 统计中文字符数量
     */
    private int countChineseChars(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (isChineseChar(text.charAt(i))) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * 判断是否全是中文字符（忽略空格和标点）
     */
    private boolean isAllChinese(String text) {
        String trimmed = text.replaceAll("[\\s\\p{Punct}]", "");
        if (trimmed.isEmpty()) {
            return false;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            if (!isChineseChar(trimmed.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
