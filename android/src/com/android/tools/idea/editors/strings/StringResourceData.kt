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
import com.android.ide.common.resources.Locale
import com.android.tools.idea.editors.strings.model.StringResourceKey
import com.android.tools.idea.editors.strings.model.StringResourceRepository
import com.android.tools.idea.res.StringResourceWriter
import com.android.tools.idea.res.getItemTag
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.xml.XmlFile
import com.intellij.refactoring.rename.RenameProcessor

class StringResourceData private constructor(
  val project: Project,
  val repository: StringResourceRepository,
  private val stringResourceWriter: StringResourceWriter = StringResourceWriter.INSTANCE) {

  private val keyToResourceMap: MutableMap<StringResourceKey, StringResource> =
    repository.getKeys().associateWith {
      runReadAction { StringResource(it, this) }
    }.toMutableMap()

  fun setKeyName(key: StringResourceKey, name: String) {
    if (key.name == name || keyToResourceMap.keys.any { it.name == name }) return

    val value = getStringResource(key).defaultValueAsResourceItem ?: return
    val stringElement = checkNotNull(getItemTag(project, value))
    val nameAttribute = checkNotNull(stringElement.getAttribute(SdkConstants.ATTR_NAME))
    val nameAttributeValue = checkNotNull(nameAttribute.valueElement)

    RenameProcessor(project, nameAttributeValue, name, /* isSearchInComments = */ false, /* isSearchTextOccurrences = */ false).run()

    keyToResourceMap.remove(key)
    val newKey = StringResourceKey(name, key.directory)
    keyToResourceMap[newKey] = StringResource(newKey, this)
  }

  fun validateKey(key: StringResourceKey): String? {
    require(keyToResourceMap.containsKey(key)) { "Key $key does not exist." }

    val stringResource = getStringResource(key)
    if (!stringResource.isTranslatable) {
      val localesWithTranslation = stringResource.translatedLocales
      if (!localesWithTranslation.isEmpty()) {
        return "Key '${key.name}' is marked as non translatable, but is translated in " +
               "${StringUtil.pluralize("locale", localesWithTranslation.size)} ${summarizeLocales(localesWithTranslation)}"
      }
    }
    else { // translatable key
      if (stringResource.defaultValueAsResourceItem == null) {
        return "Key '${key.name}' missing default value"
      }
      val missingTranslations = getMissingTranslations(key)
      if (!missingTranslations.isEmpty()) {
        return "Key '${key.name}' has translations missing for " +
               "${StringUtil.pluralize("locale", missingTranslations.size)} ${summarizeLocales(missingTranslations)}"
      }
    }

    return null
  }

  @VisibleForTesting
  fun getMissingTranslations(key: StringResourceKey): Set<Locale> {
    val stringResource = getStringResource(key)
    return localeSet.filter { stringResource.isTranslationMissing(it) }.toSet()
  }

  fun containsKey(key: StringResourceKey) = keyToResourceMap.containsKey(key)

  fun getStringResource(key: StringResourceKey) = requireNotNull(keyToResourceMap[key]) { key.toString() }

  val resources: Collection<StringResource>
    get() = keyToResourceMap.values

  val keys: List<StringResourceKey>
    get() = keyToResourceMap.keys.toList()

  val localeList: List<Locale>
    get() = localeSet
      .sortedWith(Locale.LANGUAGE_NAME_COMPARATOR)
      .toList()

  val localeSet: Set<Locale>
    get() = repository.getTranslatedLocales()

  /**
   * Finds the single XML file responsible for all the translations.
   *
   * @param locale The target language of the translation update.
   * @return the [XmlFile] to which subsequent write operations should target, or null if there are either no files or multiple files
   */
  fun getDefaultLocaleXml(locale: Locale): XmlFile? {
    return keyToResourceMap.values.asSequence()
      .mapNotNull { it.getTranslationAsResourceItem(locale) }
      .mapNotNull { getItemTag(project, it)?.containingFile as? XmlFile }
      .distinct()
      .singleOrNull()
  }

  companion object {
    private const val MAX_LOCALE_LABEL_COUNT = 3

    @JvmStatic
    fun create(project: Project, repository: StringResourceRepository) = StringResourceData(project, repository)

    @VisibleForTesting
    @JvmStatic
    fun summarizeLocales(locales: Collection<Locale>): String {
      if (locales.isEmpty()) return ""

      val size = locales.size
      if (size == 1) return locales.first().getLabel()

      val sorted = locales.getLowest()
      return if (size <= MAX_LOCALE_LABEL_COUNT) {
        "${sorted.subList(0, size - 1).getLabels()} and ${sorted[size - 1].getLabel()}"
      }
      else {
        "${sorted.getLabels()} and ${size - MAX_LOCALE_LABEL_COUNT} more"
      }
    }

    private fun Collection<Locale>.getLowest(): List<Locale> = take(MAX_LOCALE_LABEL_COUNT).sortedBy { it.getLabel() }

    private fun Collection<Locale>.getLabels(): String = joinToString(separator = ", ") { it.getLabel() }

    private fun Locale.getLabel(): String = Locale.getLocaleLabel(this, false)
  }
}
