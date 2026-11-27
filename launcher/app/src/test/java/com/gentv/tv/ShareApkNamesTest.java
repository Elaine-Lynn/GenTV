package com.gentv.tv;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ShareApkNamesTest {
    private static void assertName(String file, String title, String version) {
        ShareApkNames name = ShareApkNames.parse(file);
        assertEquals(title, name.title);
        assertEquals(version, name.version);
    }

    @Test public void splitsVersionFromName() {
        assertName("七猫免费小说v7.14.apk", "七猫免费小说", "v7.14");
        assertName("小猪电玩2.0.7.apk", "小猪电玩", "v2.0.7");
        assertName("小嘿-1.0.apk", "小嘿", "v1.0");
        assertName("云漫.v2.5.4.apk", "云漫", "v2.5.4");
        assertName("得间小说_4.8.6.1_VIP版.apk", "得间小说", "v4.8.6.1 VIP版");
    }

    @Test public void keepsNamesWithoutVersions() {
        assertName("clash.apk", "clash", "");
        assertName("51吃瓜.apk", "51吃瓜", "");
        assertName("全球应用.APK", "全球应用", "");
        assertName("1.2.apk", "1.2", "");
    }
}
