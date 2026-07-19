package com.dlofpkg.massage.util;

/**
 * Firebase Authentication يعمل ببريد إلكتروني + كلمة مرور. بما أن التطبيق
 * يريد تجربة "اسم مستخدم (ID) + كلمة مرور" بدون بريد حقيقي، نبني بريداً
 * داخلياً ثابتاً من اسم المستخدم. هذا أسلوب شائع ومقبول، والمستخدم لا يرى
 * هذا البريد أبداً ولا يحتاج معرفته.
 */
public class AuthUtils {

    private static final String DOMAIN = "@mydoc-users.app";

    public static String usernameToEmail(String username) {
        return username.toLowerCase().trim() + DOMAIN;
    }
}
