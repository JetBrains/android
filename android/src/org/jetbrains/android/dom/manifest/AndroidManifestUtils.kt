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
@file:JvmName("AndroidManifestUtils")

package org.jetbrains.android.dom.manifest

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_PERMISSION
import com.android.SdkConstants.TAG_PERMISSION_GROUP
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.XmlRecursiveElementVisitor
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.text.nullize
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.XmlName
import org.jetbrains.android.facet.AndroidFacet

/**
 * Returns the effective package name of the module corresponding to the given [facet]
 * (i.e. the "package" attribute of the merged manifest once the module is built),
 * or null if the package name couldn't be determined.
 */
fun getPackageName(facet: AndroidFacet): String? {
  return CachedValuesManager.getManager(facet.module.project).getCachedValue(facet) {
    // TODO(b/110188226): read the merged manifest
    val manifest = facet.manifest
    if (manifest == null) {
      // TODO(b/110188226): implement a ModificationTracker for the set of existing manifest files.
      // For now we just recompute every time, which is safer than never recomputing.
      CachedValueProvider.Result.create<String>(null, ModificationTracker.EVER_CHANGED)
    }
    else {
      val packageName = manifest.`package`.value
      CachedValueProvider.Result.create(packageName.nullize(true), manifest.xmlTag!!)
    }
  }
}

/**
 * Returns the package name for resources from the module under test corresponding to the given [facet].
 * See [https://developer.android.com/studio/build/application-id#change_the_application_id_for_testing].
 * TODO: Make this build-system independent.
 */
fun getTestPackageName(facet: AndroidFacet): String? {
  val flavor = AndroidModuleModel.get(facet)?.selectedVariant?.mergedFlavor ?: return null
  return flavor.testApplicationId ?: run {
    // That's how AGP works today: in apps the applicationId from the model is used with the ".test" suffix (ignoring the manifest), in libs
    // there is no applicationId and the package name from the manifest is used with the suffix.
    val applicationId = if (facet.configuration.isLibraryProject) getPackageName(facet) else flavor.applicationId
    if (applicationId.isNullOrEmpty()) null else "$applicationId.test"
  }
}

/**
 * Returns whether the given manifest [element] requires an attribute named [attrName].
 */
fun isRequiredAttribute(attrName: XmlName, element: DomElement): Boolean {
  return if (element is CompatibleScreensScreen && attrName.namespaceKey == ANDROID_URI) {
    when (attrName.localName) {
      "screenSize", "screenDensity" -> true
      else -> false
    }
  }
  else {
    false
  }
}

/**
 * Returns the names of the custom permissions listed in the primary manifest of the module
 * corresponding to the given [facet], or null if the primary manifest couldn't be found.
 */
fun getCustomPermissions(facet: AndroidFacet) = getPrimaryManifestXml(facet)?.findAndroidNamesForTags(TAG_PERMISSION)

/**
 * Returns the names of the custom permission groups listed in the primary manifest of the module
 * corresponding to the given [facet], or null if the primary manifest couldn't be found.
 */
fun getCustomPermissionGroups(facet: AndroidFacet) = getPrimaryManifestXml(facet)?.findAndroidNamesForTags(TAG_PERMISSION_GROUP)

private fun getPrimaryManifestXml(facet: AndroidFacet): XmlFile? {
  val psiFile = facet.manifestFile?.let { AndroidPsiUtils.getPsiFileSafely(facet.module.project, it) }
  return psiFile as? XmlFile
}

/**
 * Returns the android:name attribute of each [XmlTag] of the given type in the [XmlFile].
 */
private fun XmlFile.findAndroidNamesForTags(tagName: String): Collection<String> {
  val androidNames = mutableListOf<String>()
  accept(object: XmlRecursiveElementVisitor() {
    override fun visitXmlTag(tag: XmlTag?) {
      super.visitXmlTag(tag)
      if (tagName != tag?.name) return
      tag.getAttributeValue(ATTR_NAME, ANDROID_URI)?.let(androidNames::add)
    }
  })
  return androidNames
}