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
import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.resources.Locale
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.escape.xml.CharacterDataEscaper.unescape
import com.android.tools.idea.editors.strings.model.StringResourceKey
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
import com.intellij.util.concurrency.SameThreadExecutor

/**
 * Represents a single entry in the translations editor.
 */
class StringResource(key: StringResourceKey, data: StringResourceData) {
  private val myKey: StringResourceKey
  private val myData: StringResourceData
  var isTranslatable: Boolean

  /** Holds the String default value we're in the process of assigning, to prevent duplicates.  */
  private var myTentativeDefaultValue: String? = null
  private var myDefaultValue: ResourceItemEntry?
  private val myLocaleToTranslationMap: MutableMap<Locale, ResourceItemEntry>
  private val myStringResourceWriter = StringResourceWriter.INSTANCE

  init {
    ApplicationManager.getApplication().assertReadAccessAllowed()
    myKey = key
    myData = data
    var translatable = true
    var defaultValue: ResourceItemEntry? = null
    val localeToTranslationMap: MutableMap<Locale, ResourceItemEntry> = HashMap()
    for (item in data.repository.getItems(key)) {
      if (!(item is PsiResourceItem || item is DynamicValueResourceItem)) {
        LOGGER.warn(item.toString() + " has an unexpected class " + item.javaClass.name)
      }
      val tag = getItemTag(data.project, item)
      if (tag != null && "false" == tag.getAttributeValue(SdkConstants.ATTR_TRANSLATABLE)) {
        translatable = false
      }
      val qualifier = item.configuration.localeQualifier
      val tagText = getTextOfTag(tag)
      if (qualifier == null) {
        defaultValue = ResourceItemEntry(item, tagText)
      } else {
        localeToTranslationMap[Locale.create(qualifier)] = ResourceItemEntry(item, tagText)
      }
    }
    isTranslatable = translatable
    myDefaultValue = defaultValue
    myLocaleToTranslationMap = localeToTranslationMap
  }

  fun getTagText(locale: Locale?): String {
    var resourceItemEntry = myDefaultValue
    if (locale != null) {
      resourceItemEntry = myLocaleToTranslationMap[locale]
    }
    return resourceItemEntry?.myTagText ?: ""
  }

  val defaultValueAsResourceItem: ResourceItem?
    get() = if (myDefaultValue == null) null else myDefaultValue!!.myResourceItem
  val defaultValueAsString: String
    get() = if (myDefaultValue == null) "" else myDefaultValue!!.myString

  @UiThread
  fun setDefaultValue(defaultValue: String): ListenableFuture<Boolean> {
    if (myDefaultValue == null) {
      if (defaultValue == myTentativeDefaultValue) {
        return Futures.immediateFuture(false)
      }
      myTentativeDefaultValue = defaultValue
      val futureItem = createDefaultValue(defaultValue)
      return Futures.transform(futureItem, { item: ResourceItem? ->
        myTentativeDefaultValue = null
        if (item == null) {
          return@transform false
        }
        myDefaultValue = ResourceItemEntry(item, getTextOfTag(getItemTag(myData.project, item)))
        true
      }, SameThreadExecutor.INSTANCE)
    }
    if (myDefaultValue!!.myString == defaultValue) {
      return Futures.immediateFuture(false)
    }
    val changed = myStringResourceWriter.setItemText(myData.project, myDefaultValue!!.myResourceItem, defaultValue)
    if (!changed) {
      return Futures.immediateFuture(false)
    }
    if (defaultValue.isEmpty()) {
      myDefaultValue = null
      return Futures.immediateFuture(true)
    }
    val item = myData.repository.getDefaultValue(myKey)!!
    myDefaultValue = ResourceItemEntry(item, getTextOfTag(getItemTag(myData.project, item)))
    return Futures.immediateFuture(true)
  }

  private fun createDefaultValue(value: String): ListenableFuture<ResourceItem?> {
    if (value.isEmpty()) {
      return Futures.immediateFuture(null)
    }
    val project = myData.project
    StringResourceWriter.INSTANCE.addDefault(project, myKey, value, isTranslatable)
    val futureItem = SettableFuture.create<ResourceItem?>()
    val stringRepository = myData.repository
    stringRepository.invokeAfterPendingUpdatesFinish(myKey) { futureItem.set(stringRepository.getDefaultValue(myKey)) }
    return futureItem
  }

  fun validateDefaultValue(): String? {
    if (myDefaultValue == null) {
      return "Key \"" + myKey.name + "\" is missing its default value"
    }
    return if (!myDefaultValue!!.myStringValid) {
      "Invalid XML"
    } else null
  }

  fun getTranslationAsResourceItem(locale: Locale): ResourceItem? {
    val resourceItemEntry = myLocaleToTranslationMap[locale]
    return resourceItemEntry?.myResourceItem
  }

  fun getTranslationAsString(locale: Locale): String {
    val resourceItemEntry = myLocaleToTranslationMap[locale]
    return resourceItemEntry?.myString ?: ""
  }

  fun putTranslation(locale: Locale, translation: String): ListenableFuture<Boolean> {
    if (getTranslationAsResourceItem(locale) == null) {
      val keys = myData.keys
      var index = keys.indexOf(myKey)
      var anchor: StringResourceKey? = null
      if (index != -1) {
        // This translation exists in default translation. Find the anchor
        while (++index < keys.size) {
          val next = keys[index]
          val nextResource = myData.getStringResource(next)
          // If we're into another file already, we're not going to find the anchor here.
          if (!hasSameDefaultValueFile(nextResource)) {
            break
          }
          // Check if this resource exists in the given Locale file.
          if (nextResource.getTranslationAsResourceItem(locale) != null) {
            anchor = next
            break
          }
        }
      }
      return Futures.transform(createTranslationBefore(locale, translation, anchor), { item: ResourceItem? ->
        if (item == null) {
          return@transform false
        }
        myLocaleToTranslationMap[locale] = ResourceItemEntry(item, getTextOfTag(getItemTag(myData.project, item)))
        true
      }, SameThreadExecutor.INSTANCE)
    }
    if (getTranslationAsString(locale) == translation) {
      return Futures.immediateFuture(false)
    }
    var item = getTranslationAsResourceItem(locale)!!
    val changed = myStringResourceWriter.setItemText(myData.project, item, translation)
    if (!changed) {
      return Futures.immediateFuture(false)
    }
    if (translation.isEmpty()) {
      myLocaleToTranslationMap.remove(locale)
      return Futures.immediateFuture(true)
    }
    item = myData.repository.getTranslation(myKey, locale)
    assert(item != null)
    myLocaleToTranslationMap[locale] = ResourceItemEntry(item, getTextOfTag(getItemTag(myData.project, item)))
    return Futures.immediateFuture(true)
  }

  private fun createTranslationBefore(
    locale: Locale, value: String,
    anchor: StringResourceKey?
  ): ListenableFuture<ResourceItem?> {
    if (value.isEmpty()) {
      return Futures.immediateFuture(null)
    }
    val resourceDirectory = myKey.directory ?: return Futures.immediateFuture(null)
    val project = myData.project
    // If there is only one file that all translations of string resources are in, get that file.
    val file = myData.getDefaultLocaleXml(locale)
    if (file != null) {
      StringResourceWriter.INSTANCE.addTranslationToFile(project, file, myKey, value, anchor)
    } else {
      StringResourceWriter.INSTANCE.addTranslation(project, myKey, value, locale, defaultValueFileName, anchor)
    }
    val futureItem = SettableFuture.create<ResourceItem?>()
    val stringRepository = myData.repository
    stringRepository.invokeAfterPendingUpdatesFinish(myKey) { futureItem.set(stringRepository.getTranslation(myKey, locale)) }
    return futureItem
  }

  fun validateTranslation(locale: Locale): String? {
    val entry = myLocaleToTranslationMap[locale]
    if (entry != null && !entry.myStringValid) {
      return "Invalid XML"
    }
    return if (isTranslatable && isTranslationMissing(locale)) {
      "Key \"" + myKey.name + "\" is missing its " + Locale.getLocaleLabel(locale, false) + " translation"
    } else if (!isTranslatable && !isTranslationMissing(locale)) {
      "Key \"" + myKey.name + "\" is untranslatable and should not be translated to " +
        Locale.getLocaleLabel(locale, false)
    } else {
      null
    }
  }

  val translatedLocales: Collection<Locale>
    get() = myLocaleToTranslationMap.keys

  fun isTranslationMissing(locale: Locale): Boolean {
    var locale = locale
    var item = myLocaleToTranslationMap[locale]
    if (isTranslationMissing(item) && locale.hasRegion()) {
      // qualifiers from Locale objects have the language set.
      val language = locale.qualifier.language!!
      locale = Locale.create(language)
      item = myLocaleToTranslationMap[locale]
    }
    return isTranslationMissing(item)
  }

  private fun hasSameDefaultValueFile(other: StringResource): Boolean {
    return defaultValueFileName == other.defaultValueFileName
  }

  private val defaultValueFileName: String
    private get() {
      val resourceItem = defaultValueAsResourceItem
      if (resourceItem != null) {
        val pathString = resourceItem.originalSource
        if (pathString != null) {
          val fileName = pathString.fileName
          assert(
            !fileName.isEmpty() // Only empty if pathString is a file system root.
          )
          return fileName
        }
      }
      return DEFAULT_STRING_RESOURCE_FILE_NAME
    }

  private class ResourceItemEntry(val myResourceItem: ResourceItem, val myTagText: String) {
    var myString: String
    var myStringValid: Boolean

    init {
      val value: ResourceValue = resourceItem.getResourceValue()
      if (value == null) {
        myString = ""
        myStringValid = true
        return
      }
      var string = value.rawXmlValue!!
      var stringValid = true
      try {
        string = unescape(string)
      } catch (exception: IllegalArgumentException) {
        stringValid = false
      }
      myString = string
      myStringValid = stringValid
    }
  }

  companion object {
    private val LOGGER = Logger.getInstance(StringResource::class.java)
    private fun isTranslationMissing(item: ResourceItemEntry?): Boolean {
      return item == null || item.myString.isEmpty()
    }

    private fun getTextOfTag(tag: XmlTag?): String {
      return if (tag == null) "" else tag.text
    }
  }
}
