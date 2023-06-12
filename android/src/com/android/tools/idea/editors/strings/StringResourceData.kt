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
import com.google.common.collect.Iterables
import com.google.common.collect.Sets
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlFile
import com.intellij.refactoring.rename.RenameProcessor
import java.util.function.Consumer
import java.util.stream.Collectors
import java.util.stream.Stream

class StringResourceData private constructor(val project: Project, val repository: StringResourceRepository) {
  private val myKeyToResourceMap: LinkedHashMap<StringResourceKey, StringResource>
  private val myStringResourceWriter = StringResourceWriter.INSTANCE

  init {
    myKeyToResourceMap = LinkedHashMap()
  }

  fun setKeyName(key: StringResourceKey, name: String) {
    var key = key
    if (key.name == name) {
      return
    }
    val mapContainsName = myKeyToResourceMap.keys.stream()
      .map { (name1): StringResourceKey -> name1 }
      .anyMatch { n: String -> n == name }
    if (mapContainsName) {
      return
    }
    val value = getStringResource(key).defaultValueAsResourceItem ?: return
    val stringElement = getItemTag(project, value)!!
    val nameAttribute = stringElement.getAttribute(SdkConstants.ATTR_NAME)!!
    val nameAttributeValue: PsiElement = nameAttribute.valueElement!!
    RenameProcessor(project, nameAttributeValue, name, false, false).run()
    myKeyToResourceMap.remove(key)
    key = StringResourceKey(name, key.directory)
    myKeyToResourceMap[key] = StringResource(key, this)
  }

  fun setTranslatable(key: StringResourceKey, translatable: Boolean): Boolean {
    val stringResource = getStringResource(key)
    val item = stringResource.defaultValueAsResourceItem
    if (item != null) {
      val translatableAsString: String?
      if (translatable) {
        translatableAsString = null
        stringResource.isTranslatable = true
      } else {
        translatableAsString = SdkConstants.VALUE_FALSE
        stringResource.isTranslatable = false
      }
      return myStringResourceWriter.setAttribute(project, SdkConstants.ATTR_TRANSLATABLE, translatableAsString, item)
    }
    return false
  }

  fun validateKey(key: StringResourceKey): String? {
    require(myKeyToResourceMap.containsKey(key)) { "Key $key does not exist." }
    val stringResource = getStringResource(key)
    if (!stringResource.isTranslatable) {
      val localesWithTranslation = stringResource.translatedLocales
      if (!localesWithTranslation.isEmpty()) {
        return String.format(
          "Key '%1\$s' is marked as non translatable, but is translated in %2\$s %3\$s", key.name,
          StringUtil.pluralize("locale", localesWithTranslation.size), summarizeLocales(localesWithTranslation)
        )
      }
    } else { // translatable key
      if (stringResource.defaultValueAsResourceItem == null) {
        return "Key '" + key.name + "' missing default value"
      }
      val missingTranslations = getMissingTranslations(key)
      if (!missingTranslations.isEmpty()) {
        return String.format(
          "Key '%1\$s' has translations missing for %2\$s %3\$s", key.name,
          StringUtil.pluralize("locale", missingTranslations.size), summarizeLocales(missingTranslations)
        )
      }
    }
    return null
  }

  @VisibleForTesting
  fun getMissingTranslations(key: StringResourceKey): Collection<Locale?> {
    val missingTranslations: MutableSet<Locale?> = Sets.newHashSet()
    for (locale in localeSet) {
      val stringResource = getStringResource(key)
      if (stringResource.isTranslationMissing(locale)) {
        missingTranslations.add(locale)
      }
    }
    return missingTranslations
  }

  fun containsKey(key: StringResourceKey): Boolean {
    return myKeyToResourceMap.containsKey(key)
  }

  fun getStringResource(key: StringResourceKey): StringResource {
    return myKeyToResourceMap[key] ?: throw IllegalArgumentException(key.toString())
  }

  val resources: Collection<StringResource>
    get() = myKeyToResourceMap.values
  val keys: List<StringResourceKey>
    get() = ArrayList(myKeyToResourceMap.keys)
  val localeList: List<Locale>
    get() = translatedLocaleStream
      .distinct()
      .sorted(Locale.LANGUAGE_NAME_COMPARATOR)
      .collect(Collectors.toList())
  val localeSet: Set<Locale>
    get() = translatedLocaleStream.collect(Collectors.toSet())
  private val translatedLocaleStream: Stream<Locale>
    private get() = myKeyToResourceMap.values.stream().flatMap { resource: StringResource -> resource.translatedLocales.stream() }

  /**
   * Finds the single XML file responsible for all the translations.
   *
   * @param locale The target language of the translation update.
   * @return the [XmlFile] to which subsequent write operations should target, or null if there are either no files or multiple files
   */
  fun getDefaultLocaleXml(locale: Locale): XmlFile? {
    var lastFile: XmlFile? = null
    for (stringResource in myKeyToResourceMap.values) {
      val resourceItem = stringResource.getTranslationAsResourceItem(locale) ?: continue
      val tag = getItemTag(project, resourceItem) ?: continue
      val file = tag.containingFile as? XmlFile ?: continue
      if (lastFile == null) {
        lastFile = file
      } else if (lastFile !== file) {
        return null
      }
    }
    return lastFile
  }

  companion object {
    private const val MAX_LOCALE_LABEL_COUNT = 3
    @JvmStatic
    fun create(project: Project, repository: StringResourceRepository): StringResourceData {
      val data = StringResourceData(project, repository)
      repository.getKeys().forEach(Consumer { key: StringResourceKey -> data.myKeyToResourceMap[key] = StringResource(key, data) })
      return data
    }

    @VisibleForTesting
    fun summarizeLocales(locales: Collection<Locale?>): String {
      if (locales.isEmpty()) {
        return ""
      }
      val size = locales.size
      if (size == 1) {
        return getLabel(Iterables.getFirst(locales, null))
      }
      val sorted = getLowest(locales)
      return if (size <= MAX_LOCALE_LABEL_COUNT) {
        getLabels(
          sorted.subList(
            0,
            size - 1
          )
        ) + " and " + getLabel(sorted[size - 1])
      } else {
        getLabels(sorted) + " and " + (size - MAX_LOCALE_LABEL_COUNT) + " more"
      }
    }

    private fun getLowest(locales: Collection<Locale?>): List<Locale?> {
      return locales.stream()
        .limit(MAX_LOCALE_LABEL_COUNT.toLong())
        .sorted(Comparator.comparing { locale: Locale? -> getLabel(locale) })
        .collect(Collectors.toList())
    }

    private fun getLabels(locales: Collection<Locale?>): String {
      return locales.stream()
        .map { locale: Locale? -> getLabel(locale) }
        .collect(Collectors.joining(", "))
    }

    private fun getLabel(locale: Locale?): String {
      return if (locale == null) "" else Locale.getLocaleLabel(locale, false)
    }
  }
}
