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
import com.android.test.testutils.TestUtils
import com.android.testutils.waitForCondition
import com.android.tools.idea.editors.strings.StringResourceData.Companion.create
import com.android.tools.idea.editors.strings.StringResourceData.Companion.summarizeLocales
import com.android.tools.idea.editors.strings.model.StringResourceKey
import com.android.tools.idea.res.DynamicValueResourceRepository
import com.android.tools.idea.res.ModuleResourceRepository
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.Collections
import java.util.concurrent.TimeUnit

@RunWith(JUnit4::class)
@RunsInEdt
class StringResourceDataTest {

  @get:Rule
  val androidProjectRule = AndroidProjectRule.onDisk().onEdt()

  private val fixture by lazy {
    androidProjectRule.fixture.apply {
      testDataPath = TestUtils.resolveWorkspacePath("tools/adt/idea/android/testData").toString()
    }
  }
  private val module by lazy { fixture.module }
  private val facet by lazy { module.androidFacet!! }

  private lateinit var resourceDirectory: VirtualFile
  private lateinit var data: StringResourceData

  @Before
  fun setUp() {
    facet.properties.ALLOW_USER_CONFIGURATION = false
    resourceDirectory = fixture.copyDirectoryToProject("stringsEditor/base/res", "res")

    val field = DynamicResourceValue(ResourceType.STRING, "L'Étranger")

    val dynamicRepository =
      DynamicValueResourceRepository.createForTest(facet, ResourceNamespace.RES_AUTO, Collections.singletonMap("dynamic_key1", field))

    val moduleRepository = ModuleResourceRepository.createForTest(facet, listOf(resourceDirectory), ResourceNamespace.RES_AUTO,
                                                                  dynamicRepository)

    data = create(module.project, Utils.createStringRepository(moduleRepository))
  }

  @Test
  fun summarizeLocales() {
    fun localeListOf(vararg locales: String) = locales.map(Locale::create)

    assertThat(summarizeLocales(emptySet())).isEqualTo("")

    assertThat(summarizeLocales(localeListOf("fr", "en"))).isEqualTo("English (en) and French (fr)")

    assertThat(summarizeLocales(localeListOf("en", "fr", "hi"))).isEqualTo("English (en), French (fr) and Hindi (hi)")

    assertThat(summarizeLocales(localeListOf("en", "fr", "hi", "no"))).isEqualTo("English (en), French (fr), Hindi (hi) and 1 more")

    assertThat(summarizeLocales(localeListOf("en", "fr", "hi", "no", "ta", "es", "ro"))).isEqualTo(
      "English (en), French (fr), Hindi (hi) and 4 more")
  }

  @Test
  fun parser() {
    assertThat(data.localeSet.map(Locale::toLocaleId)).containsExactly("en", "en-IN", "en-GB", "fr", "hi")
    assertThat(data.localeList.map(Locale::toLocaleId)).containsExactly("en", "en-IN", "en-GB", "fr", "hi").inOrder()

    assertThat(data.getStringResource(newStringResourceKey("key1")).defaultValueAsResourceItem).isNotNull()

    assertThat(data.getStringResource(newStringResourceKey("key5")).isTranslatable).isFalse()

    assertThat(data.getStringResource(newStringResourceKey("key1")).getTranslationAsResourceItem(Locale.create("hi"))).isNull()
    assertThat(data.getStringResource(newStringResourceKey("key2")).getTranslationAsString(Locale.create("hi"))).isEqualTo("Key 2 hi")
  }

  @Test
  fun resourceToStringPsi() {
    val locale = Locale.create("fr")

    assertThat(data.getStringResource(newStringResourceKey("key8")).getTranslationAsString(locale)).isEqualTo("L'Étranger")
    assertThat(data.getStringResource(newStringResourceKey("key9")).getTranslationAsString(locale)).isEqualTo("<![CDATA[L'Étranger]]>")
    assertThat(data.getStringResource(newStringResourceKey("key10")).getTranslationAsString(locale)).isEqualTo(
      "<xliff:g>L'Étranger</xliff:g>")
  }

  @Test
  fun resourceToStringDynamic() {
    assertThat(data.getStringResource(StringResourceKey("dynamic_key1")).defaultValueAsString).isEqualTo("L'Étranger")
  }

  @Test
  fun validation() {
    assertThat(data.validateKey(newStringResourceKey("key1"))
    ).isEqualTo("Key 'key1' has translations missing for locales French (fr) and Hindi (hi)")

    assertThat(data.validateKey(newStringResourceKey("key2"))).isNull()
    assertThat(data.validateKey(newStringResourceKey("key3"))).isNull()
    assertThat(data.validateKey(newStringResourceKey("key4"))).isEqualTo("Key 'key4' missing default value")
    assertThat(data.validateKey(newStringResourceKey("key5"))).isNull()

    assertThat(data.validateKey(newStringResourceKey("key6"))
    ).isEqualTo("Key 'key6' is marked as non translatable, but is translated in locale French (fr)")

    assertThat(data.getStringResource(newStringResourceKey("key1")).validateTranslation(Locale.create("hi"))
    ).isEqualTo("Key \"key1\" is missing its Hindi (hi) translation")

    assertThat(data.getStringResource(newStringResourceKey("key2")).validateTranslation(Locale.create("hi"))).isNull()

    assertThat(data.getStringResource(newStringResourceKey("key6")).validateTranslation(Locale.create("fr"))
    ).isEqualTo("Key \"key6\" is untranslatable and should not be translated to French (fr)")

    assertThat(data.getStringResource(newStringResourceKey("key1")).validateDefaultValue()).isNull()
    assertThat(data.getStringResource(newStringResourceKey("key4")).validateDefaultValue()).isEqualTo(
      "Key \"key4\" is missing its default value")
  }

  @Test
  fun getMissingTranslations() {
    assertThat(data.getMissingTranslations(newStringResourceKey("key7")))
      .containsExactly(
        Locale.create("en"),
        Locale.create("en-rGB"),
        Locale.create("en-rIN"),
        Locale.create("fr"),
        Locale.create("hi"))
  }

  @Test
  fun isTranslationMissing() {
    assertThat(data.getStringResource(newStringResourceKey("key7")).isTranslationMissing(Locale.create("fr"))).isTrue()
  }

  @Test
  fun regionQualifier() {
    val en_rGB = Locale.create("en-rGB")
    assertThat(data.getStringResource(newStringResourceKey("key4")).isTranslationMissing(en_rGB)).isTrue()
    assertThat(data.getStringResource(newStringResourceKey("key3")).isTranslationMissing(en_rGB)).isFalse()
    assertThat(data.getStringResource(newStringResourceKey("key8")).isTranslationMissing(en_rGB)).isFalse()
  }

  @Test
  fun editingDoNotTranslate() {
    val stringsFile = requireNotNull(resourceDirectory.findFileByRelativePath("values/strings.xml"))

    assertThat(data.getStringResource(newStringResourceKey("key1")).isTranslatable).isTrue()
    var tag = getNthXmlTag(stringsFile, 0)
    assertThat(tag.getAttributeValue(SdkConstants.ATTR_NAME)).isEqualTo("key1")
    assertThat(tag.getAttributeValue(SdkConstants.ATTR_TRANSLATABLE)).isNull()

    data.setTranslatable(newStringResourceKey("key1"), false)

    assertThat(data.getStringResource(newStringResourceKey("key1")).isTranslatable).isFalse()
    tag = getNthXmlTag(stringsFile, 0)
    assertThat(tag.getAttributeValue(SdkConstants.ATTR_TRANSLATABLE)).isEqualTo(SdkConstants.VALUE_FALSE)

    assertThat(data.getStringResource(newStringResourceKey("key5")).isTranslatable).isFalse()
    tag = getNthXmlTag(stringsFile, 3)
    assertThat(tag.getAttributeValue(SdkConstants.ATTR_NAME)).isEqualTo("key5")
    assertThat(tag.getAttributeValue(SdkConstants.ATTR_TRANSLATABLE)).isEqualTo(SdkConstants.VALUE_FALSE)

    data.setTranslatable(newStringResourceKey("key5"), true)

    assertThat(data.getStringResource(newStringResourceKey("key5")).isTranslatable).isTrue()
    tag = getNthXmlTag(stringsFile, 3)
    assertThat(tag.getAttributeValue(SdkConstants.ATTR_TRANSLATABLE)).isNull()
  }

  @Test
  fun editingCdata() {
    var expected = """<![CDATA[
        <b>Google I/O 2014</b><br>
        Version %s<br><br>
        <a href="http://www.google.com/policies/privacy/">Privacy Policy</a>
  ]]>"""

    val resource = data.getStringResource(newStringResourceKey("key1"))
    val locale = Locale.create("en-rIN")

    assertThat(resource.getTranslationAsString(locale)).isEqualTo(expected)

    expected = """<![CDATA[
        <b>Google I/O 2014</b><br>
        Version %1${"$"}s<br><br>
        <a href="http://www.google.com/policies/privacy/">Privacy Policy</a>
  ]]>"""

    assertThat(putTranslation(resource, locale, expected)).isTrue()
    assertThat(resource.getTranslationAsString(locale)).isEqualTo(expected)

    val file = requireNotNull(resourceDirectory.findFileByRelativePath("values-en-rIN/strings.xml"))

    val tag = getNthXmlTag(file, 0)

    assertThat(tag.getAttributeValue(SdkConstants.ATTR_NAME)).isEqualTo("key1")
    assertThat(tag.value.text).isEqualTo(expected)
  }

  @Test
  fun editingXliff() {
    val resource = data.getStringResource(newStringResourceKey("key3"))
    val locale = Locale.create("en-rIN")

    assertThat(resource.getTranslationAsString(locale)).isEqualTo("start <xliff:g>middle1</xliff:g>%s<xliff:g>middle3</xliff:g> end")

    val expected = "start <xliff:g>middle1</xliff:g>%1\$s<xliff:g>middle3</xliff:g> end"

    assertThat(putTranslation(resource, locale, expected)).isTrue()
    assertThat(resource.getTranslationAsString(locale)).isEqualTo(expected)

    val file = requireNotNull(resourceDirectory.findFileByRelativePath("values-en-rIN/strings.xml"))

    val tag = getNthXmlTag(file, 2)

    assertThat(tag.getAttributeValue(SdkConstants.ATTR_NAME)).isEqualTo("key3")
    assertThat(tag.value.text).isEqualTo(expected)
  }

  @Test
  fun addingNewTranslation() {
    val resource = data.getStringResource(newStringResourceKey("key4"))
    val locale = Locale.create("en")

    assertThat(resource.getTranslationAsResourceItem(locale)).isNull()
    assertThat(putTranslation(resource, locale, "Hello")).isTrue()
    assertThat(resource.getTranslationAsString(locale)).isEqualTo("Hello")

    val file = requireNotNull(resourceDirectory.findFileByRelativePath("values-en/strings.xml"))

    // There was no key4 in the default locale en, a new key would be appended to the end of file.
    val tag = getNthXmlTag(file, 4)

    assertThat(tag.getAttributeValue(SdkConstants.ATTR_NAME)).isEqualTo("key4")
    assertThat(tag.value.text).isEqualTo("Hello")
  }

  @Test
  fun insertingTranslation() {
    // Adding key 2 first then adding key 1.
    // To follow the order of default locale file, the tag of key 1 should be before key 2, even key 2 is added first.
    val locale = Locale.create("zh")

    val resource2 = data.getStringResource(newStringResourceKey("key2"))
    assertThat(resource2.getTranslationAsResourceItem(locale)).isNull()
    assertThat(putTranslation(resource2, locale, "二")).isTrue()
    assertThat(resource2.getTranslationAsString(locale)).isEqualTo("二")

    val resource4 = data.getStringResource(newStringResourceKey("key1"))
    assertThat(resource4.getTranslationAsResourceItem(locale)).isNull()
    assertThat(putTranslation(resource4, locale, "一")).isTrue()
    assertThat(resource4.getTranslationAsString(locale)).isEqualTo("一")

    val file = requireNotNull(resourceDirectory.findFileByRelativePath("values-zh/strings.xml"))

    val tag1 = getNthXmlTag(file, 0)
    assertThat(tag1.getAttributeValue(SdkConstants.ATTR_NAME)).isEqualTo("key1")
    assertThat(tag1.value.text).isEqualTo("一")

    val tag2 = getNthXmlTag(file, 1)
    assertThat(tag2.getAttributeValue(SdkConstants.ATTR_NAME)).isEqualTo("key2")
    assertThat(tag2.value.text).isEqualTo("二")
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
    val psiFile = requireNotNull(PsiManager.getInstance(facet.module.project).findFile(file) as XmlFile)
    val rootTag = requireNotNull(psiFile.rootTag)
    return rootTag.findSubTags("string")[index]
  }
}
