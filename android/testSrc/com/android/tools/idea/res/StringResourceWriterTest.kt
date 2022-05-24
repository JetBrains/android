/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.res

import com.android.SdkConstants
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.Locale
import com.android.ide.common.resources.ResourceItem
import com.android.resources.ResourceType
import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule

/**
 * Tests the [StringResourceWriter].
 *
 * Test data for this test is located in `tools/adt/idea/android/testData/stringsEditor/base/res/`
 */
@RunWith(JUnit4::class)
@RunsInEdt
class StringResourceWriterTest {
  @get:Rule val mockitoRule: MockitoRule = MockitoJUnit.rule()
  @get:Rule val projectRule: AndroidProjectRule = AndroidProjectRule.withAndroidModel()
  @get:Rule val edtRule: EdtRule = EdtRule()

  private lateinit var resourceDirectory: VirtualFile
  private lateinit var localResourceRepository: LocalResourceRepository
  private lateinit var facet: AndroidFacet

  private val stringResourceWriter = StringResourceWriter.INSTANCE
  @Before
  fun setUp() {
    projectRule.fixture.testDataPath =
        resolveWorkspacePath("tools/adt/idea/android/testData").toString()

    facet = AndroidFacet.getInstance(projectRule.module)!!

    resourceDirectory = projectRule.fixture.copyDirectoryToProject("stringsEditor/base/res", "res")
    localResourceRepository = createTestModuleRepository(facet, listOf(resourceDirectory))
  }

  @Test
  fun getStringResourceFile_default() {
    val defaultLocaleFile = "values/strings.xml"
    assertThat(resourceDirectory.findFileByRelativePath(defaultLocaleFile)).isNotNull()

    val xmlFile = stringResourceWriter.getStringResourceFile(projectRule.project, resourceDirectory)

    assertThat(xmlFile).isNotNull()
    assertThat(xmlFile?.virtualFile)
        .isEqualTo(resourceDirectory.findFileByRelativePath(defaultLocaleFile))
  }

  @Test
  fun getStringResourceFile_withLocale() {
    assertThat(resourceDirectory.findFileByRelativePath(FRENCH_STRINGS_FILE)).isNotNull()

    val xmlFile =
        stringResourceWriter.getStringResourceFile(
            projectRule.project, resourceDirectory, FRENCH_LOCALE)

    assertThat(xmlFile).isNotNull()
    assertThat(xmlFile?.virtualFile)
        .isEqualTo(resourceDirectory.findFileByRelativePath(FRENCH_STRINGS_FILE))
  }

  @Test
  fun getStringResourceFile_creation() {
    val krLocaleFile = "values-kr/strings.xml"
    assertThat(resourceDirectory.findFileByRelativePath(krLocaleFile)).isNull()

    val xmlFile =
        stringResourceWriter.getStringResourceFile(
            projectRule.project, resourceDirectory, Locale.create("kr"))

    assertThat(xmlFile).isNotNull()
    assertThat(xmlFile?.virtualFile)
        .isEqualTo(resourceDirectory.findFileByRelativePath(krLocaleFile))
  }

  @Test
  fun removeLocale() {
    assertThat(resourceDirectory.findFileByRelativePath(FRENCH_STRINGS_FILE)).isNotNull()

    stringResourceWriter.removeLocale(FRENCH_LOCALE, facet, this)

    assertThat(resourceDirectory.findFileByRelativePath(FRENCH_STRINGS_FILE)).isNull()
  }

  @Test
  fun setAttribute() {
    val attributeName = "my-great-attribute"
    assertThat(getAttribute(FRENCH_STRINGS_FILE, KEY2, attributeName)).isNull()
    // Start by creating the attribute from nothing
    val resourceItem = getResourceItem(KEY2, FRENCH_LOCALE)
    val attributeValue = "such a great attribute, dude!"
    assertThat(
            stringResourceWriter.setAttribute(
                projectRule.project, attributeName, attributeValue, resourceItem))
        .isTrue()

    assertThat(getAttribute(FRENCH_STRINGS_FILE, KEY2, attributeName)).isEqualTo(attributeValue)
    // Change it to something new.
    val nextAttributeValue = "This attribute is even better."
    assertThat(
            stringResourceWriter.setAttribute(
                projectRule.project, attributeName, nextAttributeValue, resourceItem))
        .isTrue()

    assertThat(getAttribute(FRENCH_STRINGS_FILE, KEY2, attributeName)).isEqualTo(nextAttributeValue)

    // Now show we can set to the empty string.
    assertThat(
            stringResourceWriter.setAttribute(
                projectRule.project, attributeName, value = "", resourceItem))
        .isTrue()

    assertThat(getAttribute(FRENCH_STRINGS_FILE, KEY2, attributeName)).isEmpty()

    // Now remove by setting to null.
    assertThat(
            stringResourceWriter.setAttribute(
                projectRule.project, attributeName, value = null, resourceItem))
        .isTrue()

    assertThat(getAttribute(FRENCH_STRINGS_FILE, KEY2, attributeName)).isNull()
  }

  @Test
  fun setAttribute_collection() {
    val attributeName = "my-great-attribute"
    assertThat(getAttribute(FRENCH_STRINGS_FILE, KEY2, attributeName)).isNull()
    assertThat(getAttribute(ENGLISH_STRINGS_FILE, KEY2, attributeName)).isNull()

    val frenchResourceItem = getResourceItem(KEY2, FRENCH_LOCALE)
    val englishResourceItem = getResourceItem(KEY2, ENGLISH_LOCALE)

    // Start by creating the attribute from nothing
    val attributeValue = "such a great attribute, dude!"
    assertThat(
            stringResourceWriter.setAttribute(
                projectRule.project,
                attributeName,
                attributeValue,
                listOf(frenchResourceItem, englishResourceItem)))
        .isTrue()

    assertThat(getAttribute(FRENCH_STRINGS_FILE, KEY2, attributeName)).isEqualTo(attributeValue)
    assertThat(getAttribute(ENGLISH_STRINGS_FILE, KEY2, attributeName)).isEqualTo(attributeValue)

    // Change it to something new.
    val nextAttributeValue = "This attribute is even better."
    assertThat(
            stringResourceWriter.setAttribute(
                projectRule.project,
                attributeName,
                nextAttributeValue,
                listOf(frenchResourceItem, englishResourceItem)))
        .isTrue()

    assertThat(getAttribute(FRENCH_STRINGS_FILE, KEY2, attributeName)).isEqualTo(nextAttributeValue)
    assertThat(getAttribute(ENGLISH_STRINGS_FILE, KEY2, attributeName))
        .isEqualTo(nextAttributeValue)

    // Now show we can set to the empty string.
    assertThat(
            stringResourceWriter.setAttribute(
                projectRule.project,
                attributeName,
                value = "",
                listOf(frenchResourceItem, englishResourceItem)))
        .isTrue()

    assertThat(getAttribute(FRENCH_STRINGS_FILE, KEY2, attributeName)).isEmpty()
    assertThat(getAttribute(ENGLISH_STRINGS_FILE, KEY2, attributeName)).isEmpty()

    // Now remove by setting to null.
    assertThat(
            stringResourceWriter.setAttribute(
                projectRule.project,
                attributeName,
                value = null,
                listOf(frenchResourceItem, englishResourceItem)))
        .isTrue()

    assertThat(getAttribute(FRENCH_STRINGS_FILE, KEY2, attributeName)).isNull()
    assertThat(getAttribute(ENGLISH_STRINGS_FILE, KEY2, attributeName)).isNull()
  }

  @Test
  fun delete() {
    assertThat(textExists(FRENCH_STRINGS_FILE, KEY2)).isTrue()

    assertThat(
            stringResourceWriter.delete(projectRule.project, getResourceItem(KEY2, FRENCH_LOCALE)))
        .isTrue()

    assertThat(textExists(FRENCH_STRINGS_FILE, KEY2)).isFalse()
  }

  @Test
  fun delete_emptyCollection() {
    assertThat(stringResourceWriter.delete(projectRule.project, listOf())).isFalse()
  }

  @Test
  fun delete_collection() {
    assertThat(textExists(FRENCH_STRINGS_FILE, KEY2)).isTrue()
    assertThat(textExists(ENGLISH_STRINGS_FILE, KEY2)).isTrue()

    assertThat(
            stringResourceWriter.delete(
                projectRule.project,
                listOf(
                    getResourceItem(KEY2, FRENCH_LOCALE), getResourceItem(KEY2, ENGLISH_LOCALE))))
        .isTrue()

    assertThat(textExists(FRENCH_STRINGS_FILE, KEY2)).isFalse()
    assertThat(textExists(ENGLISH_STRINGS_FILE, KEY2)).isFalse()
  }

  @Test
  fun setItemText() {
    assertThat(getText(FRENCH_STRINGS_FILE, KEY2)).isEqualTo(KEY2_INITIAL_VALUE_FRENCH)

    assertThat(
            stringResourceWriter.setItemText(
                projectRule.project, getResourceItem(KEY2, FRENCH_LOCALE), "L'Étranger"))
        .isTrue()

    assertThat(getText(FRENCH_STRINGS_FILE, KEY2)).isEqualTo("L\\'Étranger")
  }

  @Test
  fun setItemText_cdata() {
    assertThat(getText(FRENCH_STRINGS_FILE, KEY2)).isEqualTo(KEY2_INITIAL_VALUE_FRENCH)

    assertThat(
            stringResourceWriter.setItemText(
                projectRule.project,
                getResourceItem(KEY2, FRENCH_LOCALE),
                "<![CDATA[L'Étranger]]>"))
        .isTrue()

    assertThat(getText(FRENCH_STRINGS_FILE, KEY2)).isEqualTo("<![CDATA[L'Étranger]]>")
  }

  @Test
  fun setItemText_xliff() {
    assertThat(getText(FRENCH_STRINGS_FILE, KEY2)).isEqualTo(KEY2_INITIAL_VALUE_FRENCH)

    assertThat(
            stringResourceWriter.setItemText(
                projectRule.project,
                getResourceItem(KEY2, FRENCH_LOCALE),
                "<xliff:g>L'Étranger</xliff:g>"))
        .isTrue()

    assertThat(getText(FRENCH_STRINGS_FILE, KEY2)).isEqualTo("<xliff:g>L\\'Étranger</xliff:g>")
  }

  @Test
  fun setItemText_deletesIfValueEmpty() {
    assertThat(textExists(FRENCH_STRINGS_FILE, KEY2)).isTrue()

    assertThat(
            stringResourceWriter.setItemText(
                projectRule.project, getResourceItem(KEY2, FRENCH_LOCALE), ""))
        .isTrue()

    assertThat(textExists(FRENCH_STRINGS_FILE, KEY2)).isFalse()
  }

  private fun getResourceItem(name: String, locale: Locale): ResourceItem =
      localResourceRepository
          .getResources(ResourceNamespace.RES_AUTO, ResourceType.STRING, name)
          .find { locale.qualifier == it.configuration.localeQualifier }
          ?: throw AssertionError()

  private fun getText(path: String, name: String): String {
    val virtualFile = resourceDirectory.findFileByRelativePath(path)!!
    val psiFile = PsiManager.getInstance(projectRule.project).findFile(virtualFile)!!
    val xmlTag = (psiFile as XmlFile).rootTag!!
    return xmlTag
        .findSubTags("string")
        .find { name == it.getAttributeValue(SdkConstants.ATTR_NAME) }
        ?.value
        ?.text
        ?: throw AssertionError()
  }
  private fun getAttribute(path: String, name: String, attribute: String): String? {
    val virtualFile = resourceDirectory.findFileByRelativePath(path)!!
    val psiFile = PsiManager.getInstance(projectRule.project).findFile(virtualFile)!!
    val xmlTag = (psiFile as XmlFile).rootTag!!
    return xmlTag
        .findSubTags("string")
        .find { name == it.getAttributeValue(SdkConstants.ATTR_NAME) }
        ?.getAttributeValue(attribute)
  }

  private fun textExists(path: String, name: String): Boolean {
    val virtualFile = resourceDirectory.findFileByRelativePath(path)!!
    val psiFile = PsiManager.getInstance(projectRule.project).findFile(virtualFile)!!
    val xmlTag = (psiFile as XmlFile).rootTag!!
    return xmlTag.findSubTags("string").any { name == it.getAttributeValue(SdkConstants.ATTR_NAME) }
  }

  companion object {
    private const val KEY2 = "key2"
    private const val KEY2_INITIAL_VALUE_FRENCH = "Key 2 fr"
    private val FRENCH_LOCALE = Locale.create("fr")
    private val ENGLISH_LOCALE = Locale.create("en")
    private const val FRENCH_STRINGS_FILE = "values-fr/strings.xml"
    private const val ENGLISH_STRINGS_FILE = "values-en/strings.xml"
  }
}
