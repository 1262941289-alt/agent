package com.example.agent.browser;

/**
 * U9 登录页状态机：区分「有无图形验证码」以及是否已登录。
 * <p>U9 登录页默认只有账号/密码/组织/日期，图形验证码是条件性出现的
 * （如连续登录失败、异常 IP、会话异常时服务端才下发）。状态机据此分流：
 * 无验证码直接自动登录，有验证码转人工，已登录则跳过。
 */
public enum U9LoginState {

    /** 当前不在登录页（已登录态或业务页），无需登录。 */
    NOT_LOGIN("当前不在登录页（已登录或业务页），无需登录"),

    /** 标准登录页：仅账号+密码等普通字段，无图形验证码，可自动登录。 */
    PLAIN("标准登录页，无图形验证码，可直接自动登录"),

    /** 登录页含图形验证码（存在验证码图片或验证码输入框），需人工识别。 */
    CAPTCHA("登录页含图形验证码，需人工识别验证码"),

    /** 登录提交后仍被要求输入验证码（条件性验证码场景），需人工介入。 */
    CAPTCHA_AFTER_SUBMIT("提交账号密码后系统要求图形验证码，需人工介入");

    private final String desc;

    U9LoginState(String desc) {
        this.desc = desc;
    }

    public String desc() {
        return desc;
    }
}