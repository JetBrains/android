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
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClassType
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.IncorrectOperationException
import org.jetbrains.android.dom.AndroidDomUtil
import org.jetbrains.android.dom.motion.MotionDomFileDescription
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.findClassValidInXMLByName

object AndroidXmlResourcesUtil {
  @JvmField
  val SPECIAL_STYLEABLE_NAMES =
    mapOf(
      SdkConstants.TAG_APPWIDGET_PROVIDER to "AppWidgetProviderInfo",
      XmlResourceDomFileDescription.SEARCHABLE_TAG_NAME to "Searchable",
      "actionkey" to "SearchableActionKey",
      "intent" to "Intent",
      XmlResourceDomFileDescription.KEYBOARD_TAG_NAME to "Keyboard",
      "Row" to "Keyboard_Row",
      "Key" to "Keyboard_Key",
      XmlResourceDomFileDescription.DEVICE_ADMIN_TAG_NAME to "DeviceAdmin",
      XmlResourceDomFileDescription.ACCOUNT_AUTHENTICATOR_TAG_NAME to "AccountAuthenticator",
      "header" to "PreferenceHeader",
    )

  @JvmField
  val KNOWN_ROOT_TAGS = buildSet {
    addAll(XmlResourceDomFileDescription.Util.SUPPORTED_TAGS)
    add(AppRestrictionsDomFileDescription.ROOT_TAG_NAME)
    add(PathsDomFileDescription.TAG_NAME)
    add(MotionDomFileDescription.ROOT_TAG_NAME)
  }

  @JvmStatic
  fun getPossibleRoots(facet: AndroidFacet): List<String> {
    val module = facet.module
    val javaPsiFacade = JavaPsiFacade.getInstance(module.project)
    fun JavaPsiFacade.hasClass(className: String) =
      findClass(className, module.moduleWithLibrariesScope) != null

    val preferenceSource =
      when {
        javaPsiFacade.hasClass(PreferenceSource.ANDROIDX.qualifiedBaseClass) ->
          PreferenceSource.ANDROIDX
        javaPsiFacade.hasClass(PreferenceSource.SUPPORT.qualifiedBaseClass) ->
          PreferenceSource.SUPPORT
        else -> PreferenceSource.FRAMEWORK
      }

    val classMap =
      TagToClassMapper.getInstance(module).getClassMap(preferenceSource.qualifiedBaseClass)

    return buildList {
      addAll(AndroidDomUtil.removeUnambiguousNames(classMap))
      addAll(KNOWN_ROOT_TAGS)
    }
  }

  @JvmStatic
  fun isSupportedRootTag(facet: AndroidFacet, rootTagName: String): Boolean {
    return KNOWN_ROOT_TAGS.contains(rootTagName) ||
      findClassValidInXMLByName(facet, rootTagName, PreferenceClasses.CLASS_PREFERENCE) != null
  }

  enum class PreferenceSource(val qualifiedBaseClass: String, val qualifiedGroupClass: String) {
    ANDROIDX(
      PreferenceAndroidX.CLASS_PREFERENCE_ANDROIDX.newName(),
      PreferenceAndroidX.CLASS_PREFERENCE_GROUP_ANDROIDX.newName(),
    ),
    SUPPORT(
      PreferenceAndroidX.CLASS_PREFERENCE_ANDROIDX.oldName(),
      PreferenceAndroidX.CLASS_PREFERENCE_GROUP_ANDROIDX.oldName(),
    ),
    FRAMEWORK(PreferenceClasses.CLASS_PREFERENCE, PreferenceClasses.CLASS_PREFERENCE_GROUP);

    companion object {
      @JvmStatic
      fun getPreferencesSource(tag: XmlTag, facet: AndroidFacet): PreferenceSource {
        val rootTag = (tag.containingFile as XmlFile).rootTag ?: return FRAMEWORK

        val rootTagName = rootTag.name
        if (rootTagName.startsWith(SdkConstants.ANDROIDX_PKG_PREFIX)) return ANDROIDX

        if (
          rootTagName.startsWith("android.support.v") &&
            StringUtil.getPackageName(rootTagName).endsWith("preference")
        ) {
          return SUPPORT
        }

        if (rootTagName.startsWith(SdkConstants.ANDROID_PKG_PREFIX)) return FRAMEWORK

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
            return when {
              InheritanceUtil.isInheritor(type, androidXLibName) -> ANDROIDX
              InheritanceUtil.isInheritor(type, supportLibName) -> SUPPORT
              else -> FRAMEWORK
            }
          }
        } catch (ignored: IncorrectOperationException) {}
        // The root tag is an unqualified name (eg. PreferenceScreen) or does not specify a valid
        // type eg. <preference-headers>, if AndroidX. Preference class can be found then we assume
        // that AndroidX classes are being used. Otherwise, support libraries are being used.
        if (psiFacade.findClass(androidXLibName, rootTag.resolveScope) != null) return ANDROIDX
        return SUPPORT
      }
    }
  }
}
