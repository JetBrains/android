/*
 * Copyright (C) 2016 The Android Open Source Project
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
import com.android.annotations.concurrency.UiThread
import com.android.ide.common.resources.Locale
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.escape.xml.CharacterDataEscaper
import com.android.tools.idea.editors.strings.model.StringResourceKey
import com.android.tools.idea.res.DEFAULT_STRING_RESOURCE_FILE_NAME
import com.android.tools.idea.res.DynamicValueResourceItem
import com.android.tools.idea.res.PsiResourceItem
import com.android.tools.idea.res.StringResourceWriter
import com.android.tools.idea.res.getItemTag
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.xml.XmlTag
import com.intellij.util.SlowOperations
import com.intellij.util.concurrency.SameThreadExecutor

/** Represents a single entry in the translations editor. */
class StringResource(
  val key: StringResourceKey,
  val data: StringResourceData,
  private val stringResourceWriter: StringResourceWriter = StringResourceWriter.INSTANCE) {

  private val localeToTranslationMap: MutableMap<Locale, ResourceItemEntry> = mutableMapOf()

  var isTranslatable: Boolean = true

  /** Holds the String default value we're in the process of assigning, to prevent duplicates.  */
  private var tentativeDefaultValue: String? = null
  private var defaultValue: ResourceItemEntry? = null

  init {
    ApplicationManager.getApplication().assertReadAccessAllowed()

    for (item in data.repository.getItems(key)) {
      if (item !is PsiResourceItem && item !is DynamicValueResourceItem) {
        LOGGER.warn(item.toString() + " has an unexpected class " + item.javaClass.name)
      }

      val tag = getItemTag(data.project, item)
      if (tag?.getAttributeValue(SdkConstants.ATTR_TRANSLATABLE) == "false") isTranslatable = false

      val resourceItemEntry = ResourceItemEntry.create(item, getTextOfTag(tag))
      val qualifier = item.configuration.localeQualifier
      if (qualifier == null) {
        defaultValue = resourceItemEntry
      }
      else {
        val locale = Locale.create(qualifier)
        localeToTranslationMap[locale] = resourceItemEntry
      }
    }
  }

  fun getTagText(locale: Locale?): String {
    val resourceItemEntry = if (locale != null) localeToTranslationMap[locale] else defaultValue
    return resourceItemEntry?.tagText ?: ""
  }

  val defaultValueAsResourceItem: ResourceItem?
    get() = defaultValue?.resourceItem

  val defaultValueAsString: String
    get() = defaultValue?.string ?: ""

  @UiThread
  fun setDefaultValue(defaultValue: String): ListenableFuture<Boolean> {
    if (this.defaultValue == null) {
      if (defaultValue == tentativeDefaultValue) {
        return Futures.immediateFuture(false)
      }
      tentativeDefaultValue = defaultValue
      val futureItem = createDefaultValue(defaultValue)
      return Futures.transform(futureItem, { item: ResourceItem? ->
        tentativeDefaultValue = null
        if (item == null) {
          false
        }
        else {
          this.defaultValue = ResourceItemEntry.create(item, getTextOfTag(getItemTag(data.project, item)))
          true
        }
      }, SameThreadExecutor.INSTANCE)
    }

    if (this.defaultValue!!.string == defaultValue) return Futures.immediateFuture(false)

    val changed = stringResourceWriter.setItemText(data.project, this.defaultValue!!.resourceItem, defaultValue)
    if (!changed) return Futures.immediateFuture(false)

    if (defaultValue.isEmpty()) {
      this.defaultValue = null
      return Futures.immediateFuture(true)
    }

    val item = requireNotNull(data.repository.getDefaultValue(key))

    this.defaultValue = ResourceItemEntry.create(item, getTextOfTag(getItemTag(data.project, item)))
    return Futures.immediateFuture(true)
  }

  /**
   * Change the value of the translatable attribute and update the default resource item to reflect this change in the model.
   */
  fun changeTranslatable(translatable: Boolean): Boolean {
    val item = defaultValueAsResourceItem ?: return false
    isTranslatable = translatable

    if (!stringResourceWriter.setAttribute(
        project = data.project,
        attribute = SdkConstants.ATTR_TRANSLATABLE,
        value = if (translatable) null else SdkConstants.VALUE_FALSE,
        item = item)) {
      return false
    }

    this.defaultValue = ResourceItemEntry.create(item, getTextOfTag(getItemTag(data.project, item)))
    return true
  }

  private fun createDefaultValue(value: String): ListenableFuture<ResourceItem?> {
    if (value.isEmpty()) return Futures.immediateFuture(null)

    stringResourceWriter.addDefault(data.project, key, value, isTranslatable)

    val futureItem = SettableFuture.create<ResourceItem?>()
    val stringRepository = data.repository
    stringRepository.invokeAfterPendingUpdatesFinish(key) { futureItem.set(stringRepository.getDefaultValue(key)) }

    return futureItem
  }

  fun validateDefaultValue(): String? {
    val localDefaultValue = defaultValue
    return when {
      localDefaultValue == null -> "Key \"${key.name}\" is missing its default value"
      !localDefaultValue.stringValid -> "Invalid XML"
      else -> null
    }
  }

  fun getTranslationAsResourceItem(locale: Locale) = localeToTranslationMap[locale]?.resourceItem

  fun getTranslationAsString(locale: Locale) = localeToTranslationMap[locale]?.string ?: ""

  fun putTranslation(locale: Locale, translation: String): ListenableFuture<Boolean> {
    SlowOperations.knownIssue("b/401392046").use {
      if (getTranslationAsResourceItem(locale) == null) {
        return Futures.transform(createTranslationBefore(locale, translation, getAnchor(locale)), { item: ResourceItem? ->
          if (item == null) {
            false
          }
          else {
            localeToTranslationMap[locale] = ResourceItemEntry.create(item, getTextOfTag(getItemTag(data.project, item)))
            true
          }
        }, SameThreadExecutor.INSTANCE)
      }

      if (getTranslationAsString(locale) == translation) return Futures.immediateFuture(false)

      var item = checkNotNull(getTranslationAsResourceItem(locale))

      val changed = stringResourceWriter.setItemText(data.project, item, translation)
      if (!changed) return Futures.immediateFuture(false)

      if (translation.isEmpty()) {
        localeToTranslationMap.remove(locale)
        return Futures.immediateFuture(true)
      }

      item = checkNotNull(data.repository.getTranslation(key, locale))

      localeToTranslationMap[locale] = ResourceItemEntry.create(item, getTextOfTag(getItemTag(data.project, item)))
      return Futures.immediateFuture(true)
    }
  }

  private fun getAnchor(locale: Locale): StringResourceKey? {
    val item = defaultValueAsResourceItem as? PsiResourceItem ?: return null
    val tag = item.tag ?: return null
    val resources = tag.parentTag ?: return null
    val items = resources.subTags
    val index = items.indexOf(tag)
    for (nextIndex in (index + 1) until items.size) {
      val next = items[index + 1]
      val name = next.getAttributeValue(SdkConstants.ATTR_NAME) ?: continue
      val nextKey = StringResourceKey(name, key.directory)
      val nextResource = data.getStringResource(nextKey)
      // Check if this resource exists in the given Locale file.
      if (nextResource.getTranslationAsResourceItem(locale) != null) return nextKey
    }
    return null
  }

  private fun createTranslationBefore(locale: Locale, value: String, anchor: StringResourceKey?): ListenableFuture<ResourceItem?> {
    if (value.isEmpty()) return Futures.immediateFuture(null)

    if (key.directory == null) return Futures.immediateFuture(null)

    val project = data.project
    // If there is only one file that all translations of string resources are in, get that file.
    val file = data.getDefaultLocaleXml(locale)
    if (file != null) {
      stringResourceWriter.addTranslationToFile(project, file, key, value, anchor)
    }
    else {
      stringResourceWriter.addTranslation(project, key, value, locale, getDefaultValueFileName(), anchor)
    }

    val futureItem = SettableFuture.create<ResourceItem?>()
    val stringRepository = data.repository
    stringRepository.invokeAfterPendingUpdatesFinish(key) { futureItem.set(stringRepository.getTranslation(key, locale)) }
    return futureItem
  }

  fun validateTranslation(locale: Locale): String? {
    return when {
      localeToTranslationMap[locale]?.stringValid == false ->
        "Invalid XML"

      isTranslatable && isTranslationMissing(locale) ->
        "Key \"${key.name}\" is missing its ${Locale.getLocaleLabel(locale, false)} translation"

      !isTranslatable && !isTranslationMissing(locale) ->
        "Key \"${key.name}\" is untranslatable and should not be translated to ${Locale.getLocaleLabel(locale, false)}"

      else ->
        null
    }
  }

  val translatedLocales: Collection<Locale>
    get() = localeToTranslationMap.keys

  fun isTranslationMissing(locale: Locale): Boolean {
    val item = localeToTranslationMap[locale]
    return if (isTranslationMissing(item) && locale.hasRegion()) {
      // qualifiers from Locale objects have the language set.
      val language = checkNotNull(locale.qualifier.language)
      isTranslationMissing(localeToTranslationMap[Locale.create(language)])
    }
    else {
      isTranslationMissing(item)
    }
  }

  private fun hasSameDefaultValueFile(other: StringResource) = getDefaultValueFileName() == other.getDefaultValueFileName()

  private fun getDefaultValueFileName(): String =
    defaultValueAsResourceItem?.originalSource?.fileName?.let { fileName ->
      assert(fileName.isNotEmpty()) // Only empty if pathString is a file system root.
      fileName
    } ?: DEFAULT_STRING_RESOURCE_FILE_NAME

  private class ResourceItemEntry private constructor(
    val resourceItem: ResourceItem,
    val tagText: String,
    val string: String,
    val stringValid: Boolean) {

    companion object {
      fun create(resourceItem: ResourceItem, tagText: String): ResourceItemEntry {
        val value = resourceItem.resourceValue
        if (value == null) return ResourceItemEntry(resourceItem, tagText, string = "", stringValid = true)

        val rawString = checkNotNull(value.rawXmlValue)
        return try {
          val unescapedString = CharacterDataEscaper.unescape(rawString)
          ResourceItemEntry(resourceItem, tagText, string = unescapedString, stringValid = true)
        }
        catch (_: IllegalArgumentException) {
          ResourceItemEntry(resourceItem, tagText, string = rawString, stringValid = false)
        }
      }
    }
  }

  companion object {
    private val LOGGER = Logger.getInstance(StringResource::class.java)

    private fun isTranslationMissing(item: ResourceItemEntry?) = item == null || item.string.isEmpty()

    private fun getTextOfTag(tag: XmlTag?) = tag?.text ?: ""
  }
}
