/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.editors.strings

import com.android.SdkConstants
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.Locale
import com.android.projectmodel.DynamicResourceValue
import com.android.resources.ResourceType
import com.android.testutils.waitForCondition
import com.android.tools.idea.editors.strings.StringResourceData.Companion.create
import com.android.tools.idea.editors.strings.StringResourceData.Companion.summarizeLocales
import com.android.tools.idea.editors.strings.model.StringResourceKey
import com.android.tools.idea.res.DynamicValueResourceRepository
import com.android.tools.idea.res.createTestModuleRepository
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import org.jetbrains.android.AndroidTestCase
import java.util.Collections
import java.util.concurrent.TimeUnit

class StringResourceDataTest : AndroidTestCase() {

  private lateinit var resourceDirectory: VirtualFile
  private lateinit var data: StringResourceData

  override fun setUp() {
    super.setUp()
    myFacet.properties.ALLOW_USER_CONFIGURATION = false
    resourceDirectory = myFixture.copyDirectoryToProject("stringsEditor/base/res", "res")

    val field = DynamicResourceValue(ResourceType.STRING, "L'Étranger")

    val dynamicRepository =
      DynamicValueResourceRepository.createForTest(myFacet, ResourceNamespace.RES_AUTO, Collections.singletonMap("dynamic_key1", field))

    val moduleRepository = createTestModuleRepository(myFacet, listOf(resourceDirectory), ResourceNamespace.RES_AUTO, dynamicRepository)

    data = create(myModule.project, Utils.createStringRepository(moduleRepository))
  }

  fun testSummarizeLocales() {
    fun localeListOf(vararg locales: String) = locales.map(Locale::create)

    assertEquals("", summarizeLocales(emptySet()))

    assertEquals("English (en) and French (fr)", summarizeLocales(localeListOf("fr", "en")))

    assertEquals("English (en), French (fr) and Hindi (hi)", summarizeLocales(localeListOf("en", "fr", "hi")))

    assertEquals("English (en), French (fr), Hindi (hi) and 1 more", summarizeLocales(localeListOf("en", "fr", "hi", "no")))

    assertEquals("English (en), French (fr), Hindi (hi) and 4 more",
                 summarizeLocales(localeListOf("en", "fr", "hi", "no", "ta", "es", "ro")))
  }

  fun testParser() {
    val actual: Any = data.localeSet.map(Locale::toLocaleId).toSet()

    assertEquals(setOf("en", "en-GB", "en-IN", "fr", "hi"), actual)

    assertNotNull(data.getStringResource(newStringResourceKey("key1")).defaultValueAsResourceItem)

    assertFalse(data.getStringResource(newStringResourceKey("key5")).isTranslatable)

    assertNull(data.getStringResource(newStringResourceKey("key1")).getTranslationAsResourceItem(Locale.create("hi")))
    assertEquals("Key 2 hi", data.getStringResource(newStringResourceKey("key2")).getTranslationAsString(Locale.create("hi")))
  }

  fun testResourceToStringPsi() {
    val locale = Locale.create("fr")

    assertEquals("L'Étranger", data.getStringResource(newStringResourceKey("key8")).getTranslationAsString(locale))
    assertEquals("<![CDATA[L'Étranger]]>", data.getStringResource(newStringResourceKey("key9")).getTranslationAsString(locale))
    assertEquals("<xliff:g>L'Étranger</xliff:g>", data.getStringResource(newStringResourceKey("key10")).getTranslationAsString(locale))
  }

  fun testResourceToStringDynamic() {
    assertEquals("L'Étranger", data.getStringResource(StringResourceKey("dynamic_key1")).defaultValueAsString)
  }

  fun testValidation() {
    assertEquals(
      "Key 'key1' has translations missing for locales French (fr) and Hindi (hi)",
      data.validateKey(newStringResourceKey("key1"))
    )

    assertNull(data.validateKey(newStringResourceKey("key2")))
    assertNull(data.validateKey(newStringResourceKey("key3")))
    assertEquals("Key 'key4' missing default value", data.validateKey(newStringResourceKey("key4")))
    assertNull(data.validateKey(newStringResourceKey("key5")))

    assertEquals(
      "Key 'key6' is marked as non translatable, but is translated in locale French (fr)",
      data.validateKey(newStringResourceKey("key6"))
    )

    assertEquals(
      "Key \"key1\" is missing its Hindi (hi) translation",
      data.getStringResource(newStringResourceKey("key1")).validateTranslation(Locale.create("hi"))
    )

    assertNull(data.getStringResource(newStringResourceKey("key2")).validateTranslation(Locale.create("hi")))

    assertEquals(
      "Key \"key6\" is untranslatable and should not be translated to French (fr)",
      data.getStringResource(newStringResourceKey("key6")).validateTranslation(Locale.create("fr"))
    )

    assertNull(data.getStringResource(newStringResourceKey("key1")).validateDefaultValue())
    assertEquals("Key \"key4\" is missing its default value", data.getStringResource(newStringResourceKey("key4")).validateDefaultValue())
  }

  fun testGetMissingTranslations() {
     val expected = setOf(
        Locale.create("en"), 
        Locale.create("en-rGB"), 
        Locale.create("en-rIN"), 
        Locale.create("fr"), 
        Locale.create("hi"))
    assertEquals(expected, data.getMissingTranslations(newStringResourceKey("key7")))
  }

  fun testIsTranslationMissing() {
    assertTrue(data.getStringResource(newStringResourceKey("key7")).isTranslationMissing(Locale.create("fr")))
  }

  fun testRegionQualifier() {
    val en_rGB = Locale.create("en-rGB")
    assertTrue(data.getStringResource(newStringResourceKey("key4")).isTranslationMissing(en_rGB))
    assertFalse(data.getStringResource(newStringResourceKey("key3")).isTranslationMissing(en_rGB))
    assertFalse(data.getStringResource(newStringResourceKey("key8")).isTranslationMissing(en_rGB))
  }

  fun testEditingDoNotTranslate() {
    val stringsFile = requireNotNull(resourceDirectory.findFileByRelativePath("values/strings.xml"))

    assertTrue(data.getStringResource(newStringResourceKey("key1")).isTranslatable)
    var tag = getNthXmlTag(stringsFile, 0)
    assertEquals("key1", tag.getAttributeValue(SdkConstants.ATTR_NAME))
    assertNull(tag.getAttributeValue(SdkConstants.ATTR_TRANSLATABLE))

    data.setTranslatable(newStringResourceKey("key1"), false)

    assertFalse(data.getStringResource(newStringResourceKey("key1")).isTranslatable)
    tag = getNthXmlTag(stringsFile, 0)
    assertEquals(SdkConstants.VALUE_FALSE, tag.getAttributeValue(SdkConstants.ATTR_TRANSLATABLE))

    assertFalse(data.getStringResource(newStringResourceKey("key5")).isTranslatable)
    tag = getNthXmlTag(stringsFile, 3)
    assertEquals("key5", tag.getAttributeValue(SdkConstants.ATTR_NAME))
    assertEquals(SdkConstants.VALUE_FALSE, tag.getAttributeValue(SdkConstants.ATTR_TRANSLATABLE))

    data.setTranslatable(newStringResourceKey("key5"), true)

    assertTrue(data.getStringResource(newStringResourceKey("key5")).isTranslatable)
    tag = getNthXmlTag(stringsFile, 3)
    assertNull(tag.getAttributeValue(SdkConstants.ATTR_TRANSLATABLE))
  }

  fun testEditingCdata() {
    var expected = """<![CDATA[
        <b>Google I/O 2014</b><br>
        Version %s<br><br>
        <a href="http://www.google.com/policies/privacy/">Privacy Policy</a>
  ]]>"""

    val resource = data.getStringResource(newStringResourceKey("key1"))
    val locale = Locale.create("en-rIN")

    assertEquals(expected, resource.getTranslationAsString(locale))

    expected = """<![CDATA[
        <b>Google I/O 2014</b><br>
        Version %1${"$"}s<br><br>
        <a href="http://www.google.com/policies/privacy/">Privacy Policy</a>
  ]]>"""

    assertTrue(putTranslation(resource, locale, expected))
    assertEquals(expected, resource.getTranslationAsString(locale))

    val file = requireNotNull(resourceDirectory.findFileByRelativePath("values-en-rIN/strings.xml"))

    val tag = getNthXmlTag(file, 0)

    assertEquals("key1", tag.getAttributeValue(SdkConstants.ATTR_NAME))
    assertEquals(expected, tag.value.text)
  }

  fun testEditingXliff() {
    val resource = data.getStringResource(newStringResourceKey("key3"))
    val locale = Locale.create("en-rIN")

    assertEquals("start <xliff:g>middle1</xliff:g>%s<xliff:g>middle3</xliff:g> end", resource.getTranslationAsString(locale))

    val expected = "start <xliff:g>middle1</xliff:g>%1\$s<xliff:g>middle3</xliff:g> end"

    assertTrue(putTranslation(resource, locale, expected))
    assertEquals(expected, resource.getTranslationAsString(locale))

    val file = requireNotNull(resourceDirectory.findFileByRelativePath("values-en-rIN/strings.xml"))

    val tag = getNthXmlTag(file, 2)

    assertEquals("key3", tag.getAttributeValue(SdkConstants.ATTR_NAME))
    assertEquals(expected, tag.value.text)
  }

  fun testAddingNewTranslation() {
    val resource = data.getStringResource(newStringResourceKey("key4"))
    val locale = Locale.create("en")

    assertNull(resource.getTranslationAsResourceItem(locale))
    assertTrue(putTranslation(resource, locale, "Hello"))
    assertEquals("Hello", resource.getTranslationAsString(locale))

    val file = requireNotNull(resourceDirectory.findFileByRelativePath("values-en/strings.xml"))

    // There was no key4 in the default locale en, a new key would be appended to the end of file.
    val tag = getNthXmlTag(file, 4)

    assertEquals("key4", tag.getAttributeValue(SdkConstants.ATTR_NAME))
    assertEquals("Hello", tag.value.text)
  }

  fun testInsertingTranslation() {
    // Adding key 2 first then adding key 1.
    // To follow the order of default locale file, the tag of key 1 should be before key 2, even key 2 is added first.
    val locale = Locale.create("zh")

    val resource2 = data.getStringResource(newStringResourceKey("key2"))
    assertNull(resource2.getTranslationAsResourceItem(locale))
    assertTrue(putTranslation(resource2, locale, "二"))
    assertEquals("二", resource2.getTranslationAsString(locale))

    val resource4 = data.getStringResource(newStringResourceKey("key1"))
    assertNull(resource4.getTranslationAsResourceItem(locale))
    assertTrue(putTranslation(resource4, locale, "一"))
    assertEquals("一", resource4.getTranslationAsString(locale))

    val file = requireNotNull(resourceDirectory.findFileByRelativePath("values-zh/strings.xml"))

    val tag1 = getNthXmlTag(file, 0)
    assertEquals("key1", tag1.getAttributeValue(SdkConstants.ATTR_NAME))
    assertEquals("一", tag1.value.text)

    val tag2 = getNthXmlTag(file, 1)
    assertEquals("key2", tag2.getAttributeValue(SdkConstants.ATTR_NAME))
    assertEquals("二", tag2.value.text)
  }

  private fun putTranslation(resource: StringResource, locale: Locale, value: String): Boolean {
    val futureResult = resource.putTranslation(locale, value)
    waitForCondition(2, TimeUnit.SECONDS) { futureResult.isDone }
    return futureResult.get()
  }

  private fun newStringResourceKey(name: String): StringResourceKey {
    return StringResourceKey(name, resourceDirectory)
  }

  private fun getNthXmlTag(file: VirtualFile, index: Int): XmlTag {
    val psiFile = requireNotNull(PsiManager.getInstance(myFacet.module.project).findFile(file) as XmlFile)
    val rootTag = requireNotNull(psiFile.rootTag)
    return rootTag.findSubTags("string")[index]
  }
}
