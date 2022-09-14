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
import com.android.ide.common.resources.LocaleManager
import com.android.ide.common.resources.configuration.LocaleQualifier
import com.android.tools.idea.editors.strings.StringResourceData
import com.android.tools.idea.editors.strings.model.StringResourceKey
import com.android.tools.idea.rendering.FlagManager
import com.android.tools.idea.res.StringResourceWriter
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.SimpleListCellRenderer
import icons.StudioIcons
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.ResourceFolderManager
import org.jetbrains.annotations.TestOnly

class AddLocaleAction
@TestOnly
internal constructor(private val stringResourceWriter: StringResourceWriter) :
    PanelAction(
        text = "Add Locale",
        description = null,
        icon = StudioIcons.LayoutEditor.Toolbar.ADD_LOCALE) {
  constructor() : this(StringResourceWriter.INSTANCE)
  override fun doUpdate(event: AnActionEvent): Boolean =
      event.panel.table.model.keys.mapNotNull { it.directory }.isNotEmpty()

  override fun actionPerformed(event: AnActionEvent) {
    val data: StringResourceData = checkNotNull(event.panel.table.data)

    JBPopupFactory.getInstance()
        .createPopupChooserBuilder(getUnusedLocales(data.localeSet))
        .setItemChosenCallback { locale ->
          val key = findResourceKey(data, event.panel.facet)
          if (stringResourceWriter.addTranslation(
              event.requiredProject, key, data.getStringResource(key).defaultValueAsString, locale)) {
            event.panel.reloadData()
          }
        }
        .setRenderer(
            SimpleListCellRenderer.create { label, value, _ ->
              label.icon = FlagManager.getFlagImage(value)
              label.text = Locale.getLocaleLabel(value, /* brief= */ false)
            })
        .createPopup()
        .showUnderneathOf(event.inputEvent.component)
  }

  companion object {
    /**
     * Returns the list of [Locale]s that are not already present in the editor, as we don't want to
     * offer the user the option of adding those.
     */
    private fun getUnusedLocales(usedLocales: Set<Locale>): List<Locale> {
      return LocaleManager.getLanguageCodes(/* include3= */ true)
          .flatMap(this::languageToLocales)
          .minus(usedLocales)
          .sortedWith(Locale.LANGUAGE_NAME_COMPARATOR)
    }

    /** Returns the list of [Locale]s for the given language. */
    private fun languageToLocales(language: String): List<Locale> {
      val full = if (language.length == 2) language else LocaleQualifier.BCP_47_PREFIX + language
      val regionlessLocale =
          Locale.create(LocaleQualifier(full, language, /* region= */ null, /* script= */ null))
      return listOf(regionlessLocale) +
          LocaleManager.getRelevantRegions(language).map { region ->
            Locale.create(LocaleQualifier(/* full= */ null, language, region, /* script= */ null))
          }
    }

    /**
     * Returns a [StringResourceKey] for the resource named "app_name" or the first resource found
     * if that does not exist.
     */
    private fun findResourceKey(data: StringResourceData, facet: AndroidFacet): StringResourceKey {
      val directories = ResourceFolderManager.getInstance(facet).folders
      if (directories.isNotEmpty()) {
        val key = StringResourceKey(name = "app_name", directory = directories.first())
        if (data.containsKey(key)) return key
      }

      return data.keys.asSequence().filter { k -> k.directory != null }.first()
    }
  }
}
