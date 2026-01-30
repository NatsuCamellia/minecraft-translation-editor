package io.github.natsucamellia.mctranslator

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.natsucamellia.mctranslator.services.TranslationService

class TranslationServiceTest : BasePlatformTestCase() {

    fun testTranslationExtraction() {
        myFixture.addFileToProject("src/main/resources/assets/testmod/lang/en_us.json", 
            """{ "menu.title": "Test Menu", "button.save": "Save" }""")
        myFixture.addFileToProject("src/main/resources/assets/testmod/lang/zh_cn.json", 
            """{ "menu.title": "测试菜单", "button.save": "保存" }""")
        
        val service = project.getService(TranslationService::class.java)
        val sets = service.getTranslationSets()
        
        assertEquals(1, sets.size)
        val set = sets[0]
        assertEquals("testmod", set.modId)
        
        val titleEntry = set.entries.find { it.key == "menu.title" }
        assertNotNull(titleEntry)
        assertEquals("Test Menu", titleEntry!!.translations["en_us"])
        assertEquals("测试菜单", titleEntry!!.translations["zh_cn"])
    }

    fun testTranslationUpdate() {
        val enFile = myFixture.addFileToProject("src/main/resources/assets/testmod/lang/en_us.json", 
            """{ "menu.title": "Test Menu", "button.save": "Save" }""")
        
        val service = project.getService(TranslationService::class.java)
        val sets = service.getTranslationSets()
        val set = sets[0]
        
        // Update existing key
        service.updateTranslation(set, "menu.title", "en_us", "New Menu Title")
        var updatedContent = String(enFile.virtualFile.contentsToByteArray(), enFile.virtualFile.charset)
        assertTrue(updatedContent.contains("\"menu.title\": \"New Menu Title\""))

        // Add missing key
        service.updateTranslation(set, "new.key", "en_us", "New Value")
        updatedContent = String(enFile.virtualFile.contentsToByteArray(), enFile.virtualFile.charset)
        assertTrue(updatedContent.contains("\"new.key\": \"New Value\""))
        
        // Check formatting (should have multiple lines now)
        assertTrue(updatedContent.lines().size > 1)
    }

    fun testAddRemoveLanguage() {
        myFixture.addFileToProject("src/main/resources/assets/testmod/lang/en_us.json", "{}")
        val service = project.getService(TranslationService::class.java)
        var sets = service.getTranslationSets()
        val set = sets[0]
        
        service.addLanguage(set, "fr_fr")
        val frFile = myFixture.findFileInTempDir("src/main/resources/assets/testmod/lang/fr_fr.json")
        assertNotNull(frFile)
        
        sets = service.getTranslationSets()
        assertTrue(sets[0].languages.contains("fr_fr"))
        
        service.removeLanguage(set, "fr_fr")
        val frFileDeleted = myFixture.findFileInTempDir("src/main/resources/assets/testmod/lang/fr_fr.json")
        assertNull(frFileDeleted)
        
        sets = service.getTranslationSets()
        assertFalse(sets[0].languages.contains("fr_fr"))
    }

    fun testLangFileUpdate() {
        val langFile = myFixture.addFileToProject("src/main/resources/assets/testmod/lang/en_us.lang", 
            "menu.title=Test Menu\nbutton.save=Save")
        
        val service = project.getService(TranslationService::class.java)
        val sets = service.getTranslationSets()
        val set = sets.find { it.files.containsKey("en_us") && it.files["en_us"]!!.extension == "lang" }!!
        
        service.updateTranslation(set, "menu.title", "en_us", "New Menu Title")
        var updatedContent = String(langFile.virtualFile.contentsToByteArray(), langFile.virtualFile.charset)
        assertTrue(updatedContent.contains("menu.title=New Menu Title"))

        // Add missing key
        service.updateTranslation(set, "new.key", "en_us", "New Value")
        updatedContent = String(langFile.virtualFile.contentsToByteArray(), langFile.virtualFile.charset)
        assertTrue(updatedContent.contains("new.key=New Value"))
    }
}
