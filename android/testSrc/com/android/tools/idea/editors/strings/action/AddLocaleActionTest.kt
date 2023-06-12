/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.editors.strings.action

import com.android.ide.common.resources.Locale
import com.android.ide.common.resources.configuration.LocaleQualifier
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.swing.popup.FakeJBPopup
import com.android.tools.adtui.swing.popup.JBPopupRule
import com.android.tools.idea.editors.strings.StringResource
import com.android.tools.idea.editors.strings.StringResourceData
import com.android.tools.idea.editors.strings.StringResourceEditor
import com.android.tools.idea.editors.strings.StringResourceViewPanel
import com.android.tools.idea.editors.strings.model.StringResourceKey
import com.android.tools.idea.editors.strings.table.StringResourceTable
import com.android.tools.idea.editors.strings.table.StringResourceTableModel
import com.android.tools.idea.res.StringResourceWriter
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.ResourceFolderManager
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import java.awt.event.MouseEvent
import javax.swing.JPanel


/** Test [AddLocaleAction] methods. */
@RunWith(JUnit4::class)
@RunsInEdt
class AddLocaleActionTest {
  private val projectRule = AndroidProjectRule.inMemory()
  private val edtRule = EdtRule()
  private val popupRule = JBPopupRule()

  @get:Rule val ruleChain = RuleChain(projectRule, edtRule, popupRule)

  private val project: Project
    get() = projectRule.project
  private val stringResourceEditor: StringResourceEditor = mock()
  private val panel: StringResourceViewPanel = mock()
  private val stringResourceWriter: StringResourceWriter = mock()
  private val table: StringResourceTable = mock()
  private val data: StringResourceData = mock()
  private val model: StringResourceTableModel = mock()
  private val resourceDirectory: VirtualFile = mock()
  private val stringResource: StringResource = mock()
  private val component = JPanel()
  private val addLocaleAction = AddLocaleAction(stringResourceWriter)
  private val mapDataContext = MapDataContext()

  private lateinit var facet: AndroidFacet
  private lateinit var event: AnActionEvent

  @Before
  fun setUp() {
    facet = AndroidFacet.getInstance(projectRule.module)!!
    val mouseEvent =
        MouseEvent(
            component,
            /* id= */ 0,
            /* when= */ 0L,
            /* modifiers= */ 0,
            /* x= */ 0,
            /* y= */ 0,
            /* clickCount= */ 1,
            /* popupTrigger= */ true)

    event =
        AnActionEvent(
            mouseEvent, mapDataContext, "place", Presentation(), ActionManager.getInstance(), 0)
    mapDataContext.apply {
      put(CommonDataKeys.PROJECT, project)
      put(PlatformDataKeys.FILE_EDITOR, stringResourceEditor)
    }

    whenever(stringResourceEditor.panel).thenReturn(panel)
    whenever(panel.table).thenReturn(table)
    whenever(panel.facet).thenReturn(facet)
    whenever(table.data).thenReturn(data)
    whenever(table.model).thenReturn(model)
    whenever(model.keys).thenReturn(listOf(StringResourceKey("foo", resourceDirectory)))
  }

  @Test
  fun doUpdate_noKeys() {
    whenever(model.keys).thenReturn(listOf())

    addLocaleAction.update(event)

    assertThat(event.presentation.isEnabled).isFalse()
  }
  @Test
  fun doUpdate_nullDirectory() {
    whenever(model.keys).thenReturn(listOf(StringResourceKey("foo")))

    addLocaleAction.update(event)

    assertThat(event.presentation.isEnabled).isFalse()
  }

  @Test
  fun doUpdate() {
    addLocaleAction.update(event)

    assertThat(event.presentation.isEnabled).isTrue()
  }

  @Test
  fun actionPerformed_popupConfig() {
    whenever(data.localeSet).thenReturn(USED_LOCALES)

    addLocaleAction.actionPerformed(event)

    val popup = popupRule.fakePopupFactory.getPopup<Locale>(0)
    USED_LOCALES.forEach { assertThat(popup.items).doesNotContain(it) }
    assertThat(popup.items).containsAllIn(UNUSED_LOCALE_SAMPLE)
    assertThat(popup.items).isStrictlyOrdered(Locale.LANGUAGE_NAME_COMPARATOR)
    assertThat(popup.showStyle).isEqualTo(FakeJBPopup.ShowStyle.SHOW_UNDERNEATH_OF)
    assertThat(popup.showArgs).containsExactly(component)
  }

  @Test
  fun actionPerformed_firstAvailableKey_addFails() {
    val firstAvailableKey =
        StringResourceKey(name = "firstAvailable", directory = resourceDirectory)
    whenever(data.keys).thenReturn(listOf(StringResourceKey("bogusNoDirectory"), firstAvailableKey))
    whenever(data.localeSet).thenReturn(USED_LOCALES)
    whenever(data.getStringResource(firstAvailableKey)).thenReturn(stringResource)
    whenever(stringResource.defaultValueAsString).thenReturn(DEFAULT_VALUE_AS_STRING)

    addLocaleAction.actionPerformed(event)

    val popup = popupRule.fakePopupFactory.getPopup<Locale>(0)
    popup.selectItem(UNUSED_LOCALE_SAMPLE.first())

    verify(stringResourceWriter)
        .addTranslation(project, firstAvailableKey, DEFAULT_VALUE_AS_STRING, locale = UNUSED_LOCALE_SAMPLE.first())
    // Add did not succeed so should not reload.
    verify(panel, never()).reloadData()
  }

  @Test
  fun actionPerformed_firstAvailableKey_addSucceeds() {
    val firstAvailableKey =
        StringResourceKey(name = "firstAvailable", directory = resourceDirectory)
    whenever(data.keys).thenReturn(listOf(StringResourceKey("bogusNoDirectory"), firstAvailableKey))
    whenever(data.localeSet).thenReturn(USED_LOCALES)
    whenever(data.getStringResource(firstAvailableKey)).thenReturn(stringResource)
    whenever(stringResource.defaultValueAsString).thenReturn(DEFAULT_VALUE_AS_STRING)

    addLocaleAction.actionPerformed(event)

    whenever(
            stringResourceWriter.addTranslation(
                project, firstAvailableKey, DEFAULT_VALUE_AS_STRING, locale = UNUSED_LOCALE_SAMPLE.first()))
        .thenReturn(true)
    val popup = popupRule.fakePopupFactory.getPopup<Locale>(0)

    popup.selectItem(UNUSED_LOCALE_SAMPLE.first())

    verify(stringResourceWriter)
        .addTranslation(project, firstAvailableKey, DEFAULT_VALUE_AS_STRING, locale = UNUSED_LOCALE_SAMPLE.first())
    verify(panel).reloadData()
  }

  @Test
  fun actionPerformed_appNameKey_addFails() {
    val resourceFolderManager: ResourceFolderManager = mock()
    projectRule.module.replaceService(
        ResourceFolderManager::class.java, resourceFolderManager, project)
    whenever(resourceFolderManager.folders).thenReturn(listOf(resourceDirectory))
    val appNameKey = StringResourceKey(name = "app_name", directory = resourceDirectory)
    whenever(data.containsKey(appNameKey)).thenReturn(true)
    whenever(data.localeSet).thenReturn(USED_LOCALES)
    whenever(data.getStringResource(appNameKey)).thenReturn(stringResource)
    whenever(stringResource.defaultValueAsString).thenReturn(DEFAULT_VALUE_AS_STRING)

    addLocaleAction.actionPerformed(event)

    val popup = popupRule.fakePopupFactory.getPopup<Locale>(0)

    popup.selectItem(UNUSED_LOCALE_SAMPLE.first())

    verify(stringResourceWriter)
        .addTranslation(project, appNameKey, DEFAULT_VALUE_AS_STRING, locale = UNUSED_LOCALE_SAMPLE.first())
    // Add did not succeed so should not reload.
    verify(panel, never()).reloadData()
  }

  @Test
  fun actionPerformed_appNameKey_addSucceeds() {
    val resourceFolderManager: ResourceFolderManager = mock()
    projectRule.module.replaceService(
        ResourceFolderManager::class.java, resourceFolderManager, project)
    whenever(resourceFolderManager.folders).thenReturn(listOf(resourceDirectory))
    val appNameKey = StringResourceKey(name = "app_name", directory = resourceDirectory)
    whenever(data.containsKey(appNameKey)).thenReturn(true)
    whenever(data.localeSet).thenReturn(USED_LOCALES)
    whenever(data.getStringResource(appNameKey)).thenReturn(stringResource)
    whenever(stringResource.defaultValueAsString).thenReturn(DEFAULT_VALUE_AS_STRING)

    addLocaleAction.actionPerformed(event)

    whenever(
            stringResourceWriter.addTranslation(
                project, appNameKey, DEFAULT_VALUE_AS_STRING, locale = UNUSED_LOCALE_SAMPLE.first()))
        .thenReturn(true)
    val popup = popupRule.fakePopupFactory.getPopup<Locale>(0)

    popup.selectItem(UNUSED_LOCALE_SAMPLE.first())

    verify(stringResourceWriter)
        .addTranslation(project, appNameKey, DEFAULT_VALUE_AS_STRING, locale = UNUSED_LOCALE_SAMPLE.first())
    verify(panel).reloadData()
  }

  companion object {
    private const val DEFAULT_VALUE_AS_STRING = "I am a great default value!"
    private val USED_LOCALES = listOf("en", "fr", "de").toLocales().toSet()
    /**
     * This isn't all the Locales we should see, but a good sample. We want to avoid just
     * duplicating the code under test here in the test.
     */
    private val UNUSED_LOCALE_SAMPLE =
        listOf(
                "ab",
                "eu",
                "en-ZA",
                "it-CH",
                "ja",
                "ru",
                "ng",
                "ii",
                "es-US",
                "xh")
            .toLocales()

    private fun List<String>.toLocales(): List<Locale> = map { s -> s.toLocale() }
    private fun String.toLocale(): Locale {
      if (contains('-')) {
        val (lang, region) = split('-')
        return Locale.create(LocaleQualifier(null, lang, region, null))
      }
      return Locale.create(this)
    }
  }
}
