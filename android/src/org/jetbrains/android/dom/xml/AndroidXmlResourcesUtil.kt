/*
 * Copyright (C) 2019 The Android Open Source Project
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
package org.jetbrains.android.dom.xml

import com.android.AndroidXConstants.PreferenceAndroidX
import com.android.SdkConstants
import com.android.SdkConstants.PreferenceClasses
import com.android.tools.idea.dom.xml.PathsDomFileDescription
import com.android.tools.idea.psi.TagToClassMapper
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClassType
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.IncorrectOperationException
import org.jetbrains.android.dom.AndroidDomUtil
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.findClassValidInXMLByName
import org.jetbrains.annotations.NonNls

object AndroidXmlResourcesUtil {
  @NonNls val SEARCHABLE_TAG_NAME = "searchable"

  @NonNls val KEYBOARD_TAG_NAME = "Keyboard"

  @NonNls val DEVICE_ADMIN_TAG_NAME = "device-admin"

  @NonNls val ACCOUNT_AUTHENTICATOR_TAG_NAME = "account-authenticator"

  @NonNls val PREFERENCE_HEADERS_TAG_NAME = "preference-headers"
  @JvmField
  val SPECIAL_STYLEABLE_NAMES =
    ImmutableMap.builder<String, String>()
      .put(SdkConstants.TAG_APPWIDGET_PROVIDER, "AppWidgetProviderInfo")
      .put(SEARCHABLE_TAG_NAME, "Searchable")
      .put("actionkey", "SearchableActionKey")
      .put("intent", "Intent")
      .put(KEYBOARD_TAG_NAME, "Keyboard")
      .put("Row", "Keyboard_Row")
      .put("Key", "Keyboard_Key")
      .put(DEVICE_ADMIN_TAG_NAME, "DeviceAdmin")
      .put(ACCOUNT_AUTHENTICATOR_TAG_NAME, "AccountAuthenticator")
      .put("header", "PreferenceHeader")
      .build()
  val PREFERENCES_ROOT_TAGS =
    ImmutableSet.of(
      SdkConstants.TAG_APPWIDGET_PROVIDER,
      SEARCHABLE_TAG_NAME,
      KEYBOARD_TAG_NAME,
      DEVICE_ADMIN_TAG_NAME,
      ACCOUNT_AUTHENTICATOR_TAG_NAME,
      PREFERENCE_HEADERS_TAG_NAME,
      PathsDomFileDescription.TAG_NAME
    )
  @JvmField
  val ROOT_TAGS =
    ImmutableSet.builder<String>()
      .addAll(PREFERENCES_ROOT_TAGS)
      .add(AppRestrictionsDomFileDescription.ROOT_TAG_NAME)
      .build()

  @JvmStatic
  fun getPossibleRoots(facet: AndroidFacet): List<String> {
    val result: MutableList<String> = ArrayList()
    val javaPsiFacade = JavaPsiFacade.getInstance(facet.module.project)
    val hasAndroidXClass =
      javaPsiFacade.findClass(
        PreferenceAndroidX.CLASS_PREFERENCE_ANDROIDX.newName(),
        facet.module.moduleWithLibrariesScope
      ) != null
    if (hasAndroidXClass) {
      result.addAll(
        AndroidDomUtil.removeUnambiguousNames(
          TagToClassMapper.getInstance(facet.module)
            .getClassMap(PreferenceAndroidX.CLASS_PREFERENCE_ANDROIDX.newName())
        )
      )
    } else if (
      javaPsiFacade.findClass(
        PreferenceAndroidX.CLASS_PREFERENCE_ANDROIDX.oldName(),
        facet.module.moduleWithLibrariesScope
      ) != null
    ) {
      result.addAll(
        AndroidDomUtil.removeUnambiguousNames(
          TagToClassMapper.getInstance(facet.module)
            .getClassMap(PreferenceAndroidX.CLASS_PREFERENCE_ANDROIDX.oldName())
        )
      )
    } else {
      result.addAll(
        AndroidDomUtil.removeUnambiguousNames(
          TagToClassMapper.getInstance(facet.module).getClassMap(PreferenceClasses.CLASS_PREFERENCE)
        )
      )
    }
    result.addAll(ROOT_TAGS)
    return result
  }

  @JvmStatic
  fun isSupportedRootTag(facet: AndroidFacet, rootTagName: String): Boolean {
    return ROOT_TAGS.contains(rootTagName) ||
      findClassValidInXMLByName(facet, rootTagName, PreferenceClasses.CLASS_PREFERENCE) != null
  }

  enum class PreferenceSource(val qualifiedBaseClass: String, val qualifiedGroupClass: String) {
    ANDROIDX(
      PreferenceAndroidX.CLASS_PREFERENCE_ANDROIDX.newName(),
      PreferenceAndroidX.CLASS_PREFERENCE_GROUP_ANDROIDX.newName()
    ),
    SUPPORT(
      PreferenceAndroidX.CLASS_PREFERENCE_ANDROIDX.oldName(),
      PreferenceAndroidX.CLASS_PREFERENCE_GROUP_ANDROIDX.oldName()
    ),
    FRAMEWORK(PreferenceClasses.CLASS_PREFERENCE, PreferenceClasses.CLASS_PREFERENCE_GROUP);

    companion object {
      @JvmStatic
      fun getPreferencesSource(tag: XmlTag, facet: AndroidFacet): PreferenceSource {
        val rootTag = (tag.containingFile as XmlFile).rootTag ?: return FRAMEWORK
        val rootTagName = rootTag.name
        if (rootTagName.startsWith(SdkConstants.ANDROIDX_PKG_PREFIX)) {
          return ANDROIDX
        } else if (
          rootTagName.startsWith("android.support.v") &&
            StringUtil.getPackageName(rootTagName).endsWith("preference")
        ) {
          return SUPPORT
        } else if (rootTagName.startsWith(SdkConstants.ANDROID_PKG_PREFIX)) {
          return FRAMEWORK
        }
        val project = facet.module.project
        val supportLibName = PreferenceAndroidX.CLASS_PREFERENCE_ANDROIDX.oldName()
        val androidXLibName = PreferenceAndroidX.CLASS_PREFERENCE_ANDROIDX.newName()
        val psiFacade = JavaPsiFacade.getInstance(project)
        if (
          psiFacade.findClass(supportLibName, rootTag.resolveScope) == null &&
            psiFacade.findClass(androidXLibName, rootTag.resolveScope) == null
        ) {
          return FRAMEWORK
        }
        val parser = psiFacade.parserFacade
        try {
          val type = parser.createTypeFromText(rootTagName, null)
          if (type is PsiClassType && type.resolve() != null) {
            if (InheritanceUtil.isInheritor(type, androidXLibName)) {
              return ANDROIDX
            } else if (InheritanceUtil.isInheritor(type, supportLibName)) {
              return SUPPORT
            }
            return FRAMEWORK
          }
        } catch (ignored: IncorrectOperationException) {}
        // The root tag is an unqualified name (eg. PreferenceScreen) or does not specify a valid
        // type eg. <preference-headers>, if AndroidX
        // Preference class can be found then we assume that AndroidX classes are being used.
        // Otherwise, support libraries are being used.
        return if (psiFacade.findClass(androidXLibName, rootTag.resolveScope) != null) {
          ANDROIDX
        } else {
          SUPPORT
        }
      }
    }
  }
}
