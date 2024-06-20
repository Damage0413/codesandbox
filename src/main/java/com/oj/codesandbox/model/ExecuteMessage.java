package com.oj.codesandbox.model;

import lombok.Data;

@Data
public class ExecuteMessage {
    /**
     * 执行结果代码
     */
    private Integer value;

    /**
     * 进程成功执行信息
     */
    private String message;

    /**
     * 进程失败执行信息
     */
    private String errorMessage;

    /**
     * 进程执行耗时
     */
    private Long time;

    /**
     * 进程执行使用内存
     */
    private Long memory;

}
