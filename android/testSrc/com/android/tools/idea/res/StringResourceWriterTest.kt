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
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.adtui.swing.enableHeadlessDialogs
import com.android.tools.idea.editors.strings.model.StringResourceKey
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.BundleBase
import com.intellij.ide.IdeBundle
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbServiceImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.jetbrains.android.facet.AndroidFacet
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import javax.swing.JButton
import javax.swing.JCheckBox
import kotlin.coroutines.resume
import kotlin.test.assertFailsWith
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

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

  private val dialogMessages: MutableList<String> = mutableListOf()
  private val project: Project
    get() = projectRule.project

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

  @After
  fun tearDown() {
    TestDialogManager.setTestDialog(TestDialog.DEFAULT)
  }

  @Test
  fun getStringResourceFile_default() {
    val defaultLocaleFile = "values/strings.xml"
    assertThat(resourceDirectory.findFileByRelativePath(defaultLocaleFile)).isNotNull()

    val xmlFile = stringResourceWriter.getStringResourceFile(project, resourceDirectory)

    assertThat(xmlFile).isNotNull()
    assertThat(xmlFile?.virtualFile)
        .isEqualTo(resourceDirectory.findFileByRelativePath(defaultLocaleFile))
  }

  @Test
  fun getStringResourceFile_withLocale() {
    assertThat(resourceDirectory.findFileByRelativePath(FRENCH_STRINGS_FILE)).isNotNull()

    val xmlFile =
        stringResourceWriter.getStringResourceFile(project, resourceDirectory, FRENCH_LOCALE)

    assertThat(xmlFile).isNotNull()
    assertThat(xmlFile?.virtualFile)
        .isEqualTo(resourceDirectory.findFileByRelativePath(FRENCH_STRINGS_FILE))
  }

  @Test
  fun getStringResourceFile_creation() {
    assertThat(resourceDirectory.findFileByRelativePath(KOREAN_STRINGS_FILE)).isNull()

    val xmlFile =
        stringResourceWriter.getStringResourceFile(project, resourceDirectory, KOREAN_LOCALE)

    assertThat(xmlFile).isNotNull()
    assertThat(xmlFile?.virtualFile)
        .isEqualTo(resourceDirectory.findFileByRelativePath(KOREAN_STRINGS_FILE))
  }

  @Test
  fun add_defaultLocale() {
    assertThat(textExists(DEFAULT_STRINGS_FILE, NEW_KEY)).isFalse()
    val resourceKey = StringResourceKey(NEW_KEY, resourceDirectory)

    assertThat(stringResourceWriter.add(project, resourceKey, NEW_VALUE)).isTrue()

    assertThat(getText(DEFAULT_STRINGS_FILE, NEW_KEY)).isEqualTo(NEW_VALUE_ESCAPED)
    assertThat(getAttribute(DEFAULT_STRINGS_FILE, NEW_KEY, SdkConstants.ATTR_TRANSLATABLE)).isNull()
  }

  @Test
  fun add_nonexistentLocale() {
    assertThat(resourceDirectory.findFileByRelativePath(KOREAN_STRINGS_FILE)).isNull()
    val resourceKey = StringResourceKey(NEW_KEY, resourceDirectory)

    assertThat(stringResourceWriter.add(project, resourceKey, NEW_VALUE, KOREAN_LOCALE)).isTrue()

    assertThat(resourceDirectory.findFileByRelativePath(KOREAN_STRINGS_FILE)).isNotNull()
    assertThat(getText(KOREAN_STRINGS_FILE, NEW_KEY)).isEqualTo(NEW_VALUE_ESCAPED)
    assertThat(getAttribute(KOREAN_STRINGS_FILE, NEW_KEY, SdkConstants.ATTR_TRANSLATABLE)).isNull()
  }

  @Test
  fun add_existingLocale() {
    assertThat(resourceDirectory.findFileByRelativePath(FRENCH_STRINGS_FILE)).isNotNull()
    val resourceKey = StringResourceKey(NEW_KEY, resourceDirectory)

    assertThat(stringResourceWriter.add(project, resourceKey, NEW_VALUE, FRENCH_LOCALE)).isTrue()

    assertThat(getText(FRENCH_STRINGS_FILE, NEW_KEY)).isEqualTo(NEW_VALUE_ESCAPED)
    assertThat(getAttribute(FRENCH_STRINGS_FILE, NEW_KEY, SdkConstants.ATTR_TRANSLATABLE)).isNull()
  }

  @Test
  fun add_notTranslatable() {
    val resourceKey = StringResourceKey(NEW_KEY, resourceDirectory)

    assertThat(stringResourceWriter.add(project, resourceKey, NEW_VALUE, translatable = false))
        .isTrue()

    assertThat(getText(DEFAULT_STRINGS_FILE, NEW_KEY)).isEqualTo(NEW_VALUE_ESCAPED)
    assertThat(getAttribute(DEFAULT_STRINGS_FILE, NEW_KEY, SdkConstants.ATTR_TRANSLATABLE))
        .isEqualTo(false.toString())
  }

  @Test
  fun add_invalidXml() {
    val invalidXml = "<foo"
    val resourceKey = StringResourceKey(NEW_KEY, resourceDirectory)

    assertThat(stringResourceWriter.add(project, resourceKey, invalidXml)).isTrue()

    assertThat(getText(DEFAULT_STRINGS_FILE, NEW_KEY)).isEqualTo(invalidXml)
  }

  @Test
  fun add_specificFile() {
    val file = resourceDirectory.findFileByRelativePath(FRENCH_STRINGS_FILE)
    assertThat(file).isNotNull()
    val xmlFile = PsiManager.getInstance(project).findFile(file!!) as XmlFile

    val resourceKey = StringResourceKey(NEW_KEY, resourceDirectory)

    assertThat(stringResourceWriter.add(project, resourceKey, NEW_VALUE, xmlFile)).isTrue()

    assertThat(getText(FRENCH_STRINGS_FILE, NEW_KEY)).isEqualTo(NEW_VALUE_ESCAPED)
    assertThat(getAttribute(FRENCH_STRINGS_FILE, NEW_KEY, SdkConstants.ATTR_TRANSLATABLE)).isNull()
  }

  @Test
  fun add_before_withLocale() {
    assertThat(resourceDirectory.findFileByRelativePath(FRENCH_STRINGS_FILE)).isNotNull()
    assertThat(textExists(FRENCH_STRINGS_FILE, KEY2)).isTrue()
    val resourceKey = StringResourceKey(NEW_KEY, resourceDirectory)

    val insertBefore = StringResourceKey(KEY2, resourceDirectory)
    assertThat(
            stringResourceWriter.add(project, resourceKey, NEW_VALUE, FRENCH_LOCALE, insertBefore))
        .isTrue()

    assertThat(textExists(FRENCH_STRINGS_FILE, NEW_KEY)).isTrue()
    assertThat(textPosition(FRENCH_STRINGS_FILE, KEY2) - textPosition(FRENCH_STRINGS_FILE, NEW_KEY))
        .isEqualTo(1)
  }

  @Test
  fun add_before_specificFile() {
    val file = resourceDirectory.findFileByRelativePath(FRENCH_STRINGS_FILE)
    assertThat(file).isNotNull()
    val xmlFile = PsiManager.getInstance(project).findFile(file!!) as XmlFile

    assertThat(textExists(FRENCH_STRINGS_FILE, KEY2)).isTrue()
    val resourceKey = StringResourceKey(NEW_KEY, resourceDirectory)

    val insertBefore = StringResourceKey(KEY2, resourceDirectory)
    assertThat(stringResourceWriter.add(project, resourceKey, NEW_VALUE, xmlFile, insertBefore))
        .isTrue()

    assertThat(textExists(FRENCH_STRINGS_FILE, NEW_KEY)).isTrue()
    assertThat(textPosition(FRENCH_STRINGS_FILE, KEY2) - textPosition(FRENCH_STRINGS_FILE, NEW_KEY))
        .isEqualTo(1)
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
            stringResourceWriter.setAttribute(project, attributeName, attributeValue, resourceItem))
        .isTrue()

    assertThat(getAttribute(FRENCH_STRINGS_FILE, KEY2, attributeName)).isEqualTo(attributeValue)
    // Change it to something new.
    val nextAttributeValue = "This attribute is even better."
    assertThat(
            stringResourceWriter.setAttribute(
                project, attributeName, nextAttributeValue, resourceItem))
        .isTrue()

    assertThat(getAttribute(FRENCH_STRINGS_FILE, KEY2, attributeName)).isEqualTo(nextAttributeValue)

    // Now show we can set to the empty string.
    assertThat(stringResourceWriter.setAttribute(project, attributeName, value = "", resourceItem))
        .isTrue()

    assertThat(getAttribute(FRENCH_STRINGS_FILE, KEY2, attributeName)).isEmpty()

    // Now remove by setting to null.
    assertThat(
            stringResourceWriter.setAttribute(project, attributeName, value = null, resourceItem))
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
                project,
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
                project,
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
                project,
                attributeName,
                value = "",
                listOf(frenchResourceItem, englishResourceItem)))
        .isTrue()

    assertThat(getAttribute(FRENCH_STRINGS_FILE, KEY2, attributeName)).isEmpty()
    assertThat(getAttribute(ENGLISH_STRINGS_FILE, KEY2, attributeName)).isEmpty()

    // Now remove by setting to null.
    assertThat(
            stringResourceWriter.setAttribute(
                project,
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

    assertThat(stringResourceWriter.delete(project, getResourceItem(KEY2, FRENCH_LOCALE))).isTrue()

    assertThat(textExists(FRENCH_STRINGS_FILE, KEY2)).isFalse()
  }

  @Test
  fun delete_emptyCollection() {
    assertThat(stringResourceWriter.delete(project, listOf())).isFalse()
  }

  @Test
  fun delete_collection() {
    assertThat(textExists(FRENCH_STRINGS_FILE, KEY2)).isTrue()
    assertThat(textExists(ENGLISH_STRINGS_FILE, KEY2)).isTrue()

    assertThat(
            stringResourceWriter.delete(
                project,
                listOf(
                    getResourceItem(KEY2, FRENCH_LOCALE), getResourceItem(KEY2, ENGLISH_LOCALE))))
        .isTrue()

    assertThat(textExists(FRENCH_STRINGS_FILE, KEY2)).isFalse()
    assertThat(textExists(ENGLISH_STRINGS_FILE, KEY2)).isFalse()
  }

  @Test
  fun safeDelete_dumb_cancel() {
    assertThat(textExists(FRENCH_STRINGS_FILE, KEY2)).isTrue()
    respondToNextDialogWith(Messages.CANCEL)

    dumbSafeDelete(getResourceItem(KEY2, FRENCH_LOCALE))

    assertThat(dialogMessages).hasSize(1)
    assertThat(dialogMessages[0]).startsWith("Delete XML tag \"string\"?")
    // The resource should NOT be deleted because we canceled.
    assertThat(textExists(FRENCH_STRINGS_FILE, KEY2)).isTrue()
  }

  @Test
  fun safeDelete_collection_dumb_cancel() {
    assertThat(textExists(FRENCH_STRINGS_FILE, KEY2)).isTrue()
    assertThat(textExists(ENGLISH_STRINGS_FILE, KEY2)).isTrue()
    respondToNextDialogWith(Messages.CANCEL)

    dumbSafeDelete(
        listOf(getResourceItem(KEY2, FRENCH_LOCALE), getResourceItem(KEY2, ENGLISH_LOCALE)))

    assertThat(dialogMessages).hasSize(1)
    assertThat(dialogMessages[0]).startsWith("Delete 2 XML tags?")
    // The resources should NOT be deleted because we canceled.
    assertThat(textExists(FRENCH_STRINGS_FILE, KEY2)).isTrue()
    assertThat(textExists(ENGLISH_STRINGS_FILE, KEY2)).isTrue()
  }

  @Test
  fun safeDelete_dumb_ok() {
    assertThat(textExists(FRENCH_STRINGS_FILE, KEY2)).isTrue()
    respondToNextDialogWith(Messages.OK)

    dumbSafeDelete(getResourceItem(KEY2, FRENCH_LOCALE))

    assertThat(dialogMessages).hasSize(1)
    assertThat(dialogMessages[0]).startsWith("Delete XML tag \"string\"?")
    // The resource should be deleted because we clicked "OK".
    assertThat(textExists(FRENCH_STRINGS_FILE, KEY2)).isFalse()
  }

  @Test
  fun safeDelete_collection_dumb_ok() {
    assertThat(textExists(FRENCH_STRINGS_FILE, KEY2)).isTrue()
    assertThat(textExists(ENGLISH_STRINGS_FILE, KEY2)).isTrue()
    respondToNextDialogWith(Messages.OK)

    dumbSafeDelete(
        listOf(getResourceItem(KEY2, FRENCH_LOCALE), getResourceItem(KEY2, ENGLISH_LOCALE)))

    assertThat(dialogMessages).hasSize(1)
    assertThat(dialogMessages[0]).startsWith("Delete 2 XML tags?")
    // The resources should be deleted because we clicked "OK".
    assertThat(textExists(FRENCH_STRINGS_FILE, KEY2)).isFalse()
    assertThat(textExists(ENGLISH_STRINGS_FILE, KEY2)).isFalse()
  }

  @Test
  fun safeDelete_smart_cancel() {
    assertThat(textExists(FRENCH_STRINGS_FILE, KEY2)).isTrue()
    enableHeadlessDialogs(project)

    // This won't finish normally because the callback to resume the continuation is not invoked.
    assertFailsWith<TimeoutCancellationException> {
      interactWithSafeDeleteDialog(getResourceItem(KEY2, FRENCH_LOCALE)) {
        it.getSafeDeleteCheckbox().isSelected = true
        it.click("Cancel")
      }
    }

    // Resource should NOT be deleted.
    assertThat(textExists(FRENCH_STRINGS_FILE, KEY2)).isTrue()
  }

  @Test
  fun safeDelete_smart_ok() {
    assertThat(textExists(FRENCH_STRINGS_FILE, KEY2)).isTrue()
    enableHeadlessDialogs(project)

    interactWithSafeDeleteDialog(getResourceItem(KEY2, FRENCH_LOCALE)) {
      it.getSafeDeleteCheckbox().isSelected = true
      it.click("OK")
    }

    // Resource should be deleted.
    assertThat(textExists(FRENCH_STRINGS_FILE, KEY2)).isFalse()
  }

  @Test
  fun safeDelete_smart_collection_ok() {
    assertThat(textExists(FRENCH_STRINGS_FILE, KEY2)).isTrue()
    assertThat(textExists(ENGLISH_STRINGS_FILE, KEY2)).isTrue()
    enableHeadlessDialogs(project)

    interactWithSafeDeleteDialog(
        listOf(getResourceItem(KEY2, FRENCH_LOCALE), getResourceItem(KEY2, ENGLISH_LOCALE))) {
      it.getSafeDeleteCheckbox().isSelected = true
      it.click("OK")
    }

    // Resources should be deleted.
    assertThat(textExists(FRENCH_STRINGS_FILE, KEY2)).isFalse()
    assertThat(textExists(ENGLISH_STRINGS_FILE, KEY2)).isFalse()
  }

  @Test
  fun safeDelete_smart_notSafe_ok() {
    assertThat(textExists(FRENCH_STRINGS_FILE, KEY2)).isTrue()
    enableHeadlessDialogs(project)

    interactWithSafeDeleteDialog(getResourceItem(KEY2, FRENCH_LOCALE)) {
      // Deselect the checkbox so that it runs a not-safe delete.
      it.getSafeDeleteCheckbox().isSelected = false
      it.click("OK")
    }

    // Resource should be deleted, no further confirmation needed.
    assertThat(textExists(FRENCH_STRINGS_FILE, KEY2)).isFalse()
  }

  @Test
  fun safeDelete_smart_collection_notSafe_ok() {
    assertThat(textExists(FRENCH_STRINGS_FILE, KEY2)).isTrue()
    assertThat(textExists(ENGLISH_STRINGS_FILE, KEY2)).isTrue()
    enableHeadlessDialogs(project)

    interactWithSafeDeleteDialog(
        listOf(getResourceItem(KEY2, FRENCH_LOCALE), getResourceItem(KEY2, ENGLISH_LOCALE))) {
      // Deselect the checkbox so that it runs a not-safe delete.
      it.getSafeDeleteCheckbox().isSelected = false
      it.click("OK")
    }

    // Resources should be deleted, no further confirmation needed.
    assertThat(textExists(FRENCH_STRINGS_FILE, KEY2)).isFalse()
    assertThat(textExists(ENGLISH_STRINGS_FILE, KEY2)).isFalse()
  }

  @Test
  fun setItemText() {
    assertThat(getText(FRENCH_STRINGS_FILE, KEY2)).isEqualTo(KEY2_INITIAL_VALUE_FRENCH)

    assertThat(
            stringResourceWriter.setItemText(
                project, getResourceItem(KEY2, FRENCH_LOCALE), "L'Étranger"))
        .isTrue()

    assertThat(getText(FRENCH_STRINGS_FILE, KEY2)).isEqualTo("""L\'Étranger""")
  }

  @Test
  fun setItemText_cdata() {
    assertThat(getText(FRENCH_STRINGS_FILE, KEY2)).isEqualTo(KEY2_INITIAL_VALUE_FRENCH)

    assertThat(
            stringResourceWriter.setItemText(
                project, getResourceItem(KEY2, FRENCH_LOCALE), "<![CDATA[L'Étranger]]>"))
        .isTrue()

    assertThat(getText(FRENCH_STRINGS_FILE, KEY2)).isEqualTo("<![CDATA[L'Étranger]]>")
  }

  @Test
  fun setItemText_xliff() {
    assertThat(getText(FRENCH_STRINGS_FILE, KEY2)).isEqualTo(KEY2_INITIAL_VALUE_FRENCH)

    assertThat(
            stringResourceWriter.setItemText(
                project, getResourceItem(KEY2, FRENCH_LOCALE), "<xliff:g>L'Étranger</xliff:g>"))
        .isTrue()

    assertThat(getText(FRENCH_STRINGS_FILE, KEY2)).isEqualTo("""<xliff:g>L\'Étranger</xliff:g>""")
  }

  @Test
  fun setItemText_deletesIfValueEmpty() {
    assertThat(textExists(FRENCH_STRINGS_FILE, KEY2)).isTrue()

    assertThat(stringResourceWriter.setItemText(project, getResourceItem(KEY2, FRENCH_LOCALE), ""))
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
    val psiFile = PsiManager.getInstance(project).findFile(virtualFile)!!
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
    val psiFile = PsiManager.getInstance(project).findFile(virtualFile)!!
    val xmlTag = (psiFile as XmlFile).rootTag!!
    return xmlTag
        .findSubTags("string")
        .find { name == it.getAttributeValue(SdkConstants.ATTR_NAME) }
        ?.getAttributeValue(attribute)
  }

  private fun textExists(path: String, name: String): Boolean {
    val virtualFile = resourceDirectory.findFileByRelativePath(path)!!
    val psiFile = PsiManager.getInstance(project).findFile(virtualFile)!!
    val xmlTag = (psiFile as XmlFile).rootTag!!
    return xmlTag.findSubTags("string").any { name == it.getAttributeValue(SdkConstants.ATTR_NAME) }
  }

  private fun textPosition(path: String, name: String): Int {
    val virtualFile = resourceDirectory.findFileByRelativePath(path)!!
    val psiFile = PsiManager.getInstance(projectRule.project).findFile(virtualFile)!!
    val xmlTag = (psiFile as XmlFile).rootTag!!
    return xmlTag.findSubTags("string").indexOfFirst {
      name == it.getAttributeValue(SdkConstants.ATTR_NAME)
    }
  }

  private fun respondToNextDialogWith(dialogAnswer: Int) {
    val testDialog = TestDialog {
      dialogMessages.add(it)
      dialogAnswer
    }
    TestDialogManager.setTestDialog(testDialog)
  }

  private fun dumbSafeDelete(item: ResourceItem) = dumbSafeDelete(listOf(item))

  private fun dumbSafeDelete(items: List<ResourceItem>) {
    (DumbService.getInstance(project) as DumbServiceImpl).isDumb = true
    runBlocking {
      withTimeout(2.seconds) {
        suspendCancellableCoroutine<Unit> { cont ->
          stringResourceWriter.safeDelete(project, items) { cont.resume(Unit) }
        }
      }
    }
  }

  private fun interactWithSafeDeleteDialog(
      item: ResourceItem,
      dialogInteraction: (DialogWrapper) -> Unit
  ) = interactWithSafeDeleteDialog(listOf(item), dialogInteraction)

  private fun interactWithSafeDeleteDialog(
      items: List<ResourceItem>,
      dialogInteraction: (DialogWrapper) -> Unit
  ) {
    (DumbService.getInstance(project) as DumbServiceImpl).isDumb = false
    runBlocking {
      withTimeout(20.seconds) {
        suspendCancellableCoroutine<Unit> { cont ->
          createModalDialogAndInteractWithIt({
            stringResourceWriter.safeDelete(project, items) { cont.resume(Unit) }
          }) { dialogInteraction.invoke(it) }
        }
      }
    }
  }

  companion object {
    private const val KEY2 = "key2"
    private const val KEY2_INITIAL_VALUE_FRENCH = "Key 2 fr"
    private val FRENCH_LOCALE = Locale.create("fr")
    private val ENGLISH_LOCALE = Locale.create("en")
    private val KOREAN_LOCALE = Locale.create("kr")
    private const val FRENCH_STRINGS_FILE = "values-fr/strings.xml"
    private const val ENGLISH_STRINGS_FILE = "values-en/strings.xml"
    private const val KOREAN_STRINGS_FILE = "values-kr/strings.xml"
    private const val DEFAULT_STRINGS_FILE = "values/strings.xml"
    private const val NEW_KEY = "new_key"
    private const val NEW_VALUE = "Hey, I'm a new value!"
    private const val NEW_VALUE_ESCAPED = """Hey, I\'m a new value!"""
    private val SAFE_DELETE_CHECKBOX_MSG =
        IdeBundle.message("checkbox.safe.delete.with.usage.search")
            .replace("${BundleBase.MNEMONIC}", "")

    private fun DialogWrapper.click(text: String) {
      getTextComponent<JButton>(text) { it.text }.doClick()
    }

    private fun DialogWrapper.getSafeDeleteCheckbox(): JCheckBox =
      getTextComponent(SAFE_DELETE_CHECKBOX_MSG) { it.text }

    private inline fun <reified T> DialogWrapper.getTextComponent(
        text: String,
        getText: (T) -> String
    ): T {
      val components = TreeWalker(rootPane).descendants().toList()
      return TreeWalker(rootPane).descendants().filterIsInstance<T>().firstOrNull {
        getText(it) == text
      }
          ?: fail("${T::class.simpleName} '$text' not found in $components")
    }
  }
}
