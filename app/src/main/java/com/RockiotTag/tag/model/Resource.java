package com.RockiotTag.tag.model;

/**
 * 统一的数据封装类，用于表示加载状态
 * 
 * 这是MVVM架构中的核心类，用于统一管理数据加载的三种状态：
 * - SUCCESS: 数据加载成功
 * - ERROR: 数据加载失败
 * - LOADING: 数据加载中
 * 
 * 使用示例：
 * <pre>
 * LiveData<Resource<DeviceInfo>> result = useCase.execute(params);
 * result.observe(this, resource -> {
 *     if (resource.isSuccess()) {
 *         // 处理成功数据
 *         updateUI(resource.data);
 *     } else if (resource.isError()) {
 *         // 处理错误
 *         showError(resource.message);
 *     } else if (resource.isLoading()) {
 *         // 显示加载状态
 *         showLoading();
 *     }
 * });
 * </pre>
 * 
 * @param <T> 数据类型
 */
public class Resource<T> {
    
    /**
     * 数据状态枚举
     */
    public enum Status {
        /** 成功 */
        SUCCESS,
        /** 失败 */
        ERROR,
        /** 加载中 */
        LOADING
    }

    /** 当前状态 */
    public final Status status;
    
    /** 数据内容（成功时有效） */
    public final T data;
    
    /** 错误消息（失败时有效） */
    public final String message;

    /**
     * 私有构造函数，通过静态工厂方法创建实例
     */
    private Resource(Status status, T data, String message) {
        this.status = status;
        this.data = data;
        this.message = message;
    }

    /**
     * 创建成功状态的Resource
     * 
     * @param data 成功返回的数据
     * @param <T> 数据类型
     * @return 成功状态的Resource
     */
    public static <T> Resource<T> success(T data) {
        return new Resource<>(Status.SUCCESS, data, null);
    }

    /**
     * 创建错误状态的Resource
     * 
     * @param message 错误消息
     * @param data 可选的数据（通常为null）
     * @param <T> 数据类型
     * @return 错误状态的Resource
     */
    public static <T> Resource<T> error(String message, T data) {
        return new Resource<>(Status.ERROR, data, message);
    }

    /**
     * 创建加载中状态的Resource
     * 
     * @param data 可选的数据（通常为null）
     * @param <T> 数据类型
     * @return 加载中状态的Resource
     */
    public static <T> Resource<T> loading(T data) {
        return new Resource<>(Status.LOADING, data, null);
    }

    /**
     * 判断是否为成功状态
     * 
     * @return true表示成功
     */
    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    /**
     * 判断是否为错误状态
     * 
     * @return true表示失败
     */
    public boolean isError() {
        return status == Status.ERROR;
    }

    /**
     * 判断是否为加载中状态
     * 
     * @return true表示加载中
     */
    public boolean isLoading() {
        return status == Status.LOADING;
    }

    @Override
    public String toString() {
        return "Resource{" +
                "status=" + status +
                ", data=" + data +
                ", message='" + message + '\'' +
                '}';
    }
}
