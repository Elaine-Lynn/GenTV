package com.gentv.mobile

import com.gentv.mobile.data.Store
import com.gentv.mobile.ui.AppCopy
import com.gentv.mobile.ui.AppLanguage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LanguageTest {
    @get:Rule val tmp = TemporaryFolder()
    @Test fun freshInstallUsesEnglishAndChoiceSurvivesRestart() {
        assertEquals("en", Store(tmp.root).language())
        Store(tmp.root).saveLanguage("zh")
        assertEquals("zh", Store(tmp.root).language())
        Store(tmp.root).saveLanguage("en")
        assertEquals("en", Store(tmp.root).language())
    }
    @Test fun invalidPreferenceFallsBackToEnglish() {
        java.io.File(tmp.root, "language.txt").writeText("invalid")
        assertEquals(AppLanguage.ENGLISH, AppLanguage.from(Store(tmp.root).language()))
    }
    @Test fun translationsKeepDynamicValuesAndTechnicalText() {
        assertEquals("Apps", AppCopy.translate("应用管理", AppLanguage.ENGLISH))
        assertEquals("设置", AppCopy.translate("Settings", AppLanguage.CHINESE))
        assertEquals("4 disabled", AppCopy.translate("已停用 4 项", AppLanguage.ENGLISH))
        assertEquals("可用 5.7 GB / 总容量 9.8 GB", AppCopy.translate("5.7 GB free / 9.8 GB total", AppLanguage.CHINESE))
        assertEquals("com.example.player/.Main", AppCopy.translate("com.example.player/.Main", AppLanguage.CHINESE))
    }
}
