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
import com.android.SdkConstants.ATTR_PACKAGE
import com.android.SdkConstants.ATTR_VERSION_NAME
import com.android.SdkConstants.TAG_MANIFEST
import com.android.SdkConstants.TAG_PERMISSION
import com.android.SdkConstants.TAG_PERMISSION_GROUP
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.model.AndroidManifestIndex
import com.android.tools.idea.model.logManifestIndexQueryError
import com.android.tools.idea.model.queryCustomPermissionGroupsFromManifestIndex
import com.android.tools.idea.model.queryCustomPermissionsFromManifestIndex
import com.android.tools.idea.projectsystem.SourceProviderManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.XmlRecursiveElementVisitor
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.XmlName
import org.jetbrains.android.facet.AndroidFacet

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

private val CUSTOM_PERMISSIONS = Key.create<CachedValue<Collection<String>?>>("merged.manifest.custom.permissions")

/**
 * Returns the names of the custom permissions queried from [AndroidManifestIndex].
 * However, if index is not ready, it falls back to the custom permissions listed in the primary manifest of the module
 * corresponding to the given [facet], or null if the primary manifest couldn't be found.
 */
fun getCustomPermissions(facet: AndroidFacet): Collection<String>? {
  try {
    return DumbService.getInstance(facet.module.project)
      .runReadActionInSmartMode(Computable { facet.queryCustomPermissionsFromManifestIndex() })
  }
  catch (e: IndexNotReadyException) {
    // TODO(147116755): runReadActionInSmartMode doesn't work if we already have read access.
    //  We need to refactor the callers of this to require a *smart*
    //  read action, at which point we can remove this try-catch.
    //  It falls back to the original method when index isn't ready.
    logManifestIndexQueryError(e)
  }

  val cachedValue = facet.cachedValueFromPrimaryManifest { customPermissions }
  return facet.putUserDataIfAbsent(CUSTOM_PERMISSIONS, cachedValue).value
}

private val CUSTOM_PERMISSION_GROUPS = Key.create<CachedValue<Collection<String>?>>("merged.manifest.custom.permission.groups")

/**
 * Returns the names of the custom permission groups queried from [AndroidManifestIndex].
 * However, if index is not ready, it falls back to the custom permission groups listed in the primary manifest of the
 * module corresponding to the given [facet], or null if the primary manifest couldn't be found.
 */
fun getCustomPermissionGroups(facet: AndroidFacet): Collection<String>? {
  try {
    return DumbService.getInstance(facet.module.project)
      .runReadActionInSmartMode(Computable { facet.queryCustomPermissionGroupsFromManifestIndex() })
  }
  catch (e: IndexNotReadyException) {
    // TODO(147116755): runReadActionInSmartMode doesn't work if we already have read access.
    //  We need to refactor the callers of this to require a *smart*
    //  read action, at which point we can remove this try-catch.
    //  It falls back to the original method when index isn't ready.
    logManifestIndexQueryError(e)
  }

  val cachedValue = facet.cachedValueFromPrimaryManifest { customPermissionGroups }
  return facet.putUserDataIfAbsent(CUSTOM_PERMISSION_GROUPS, cachedValue).value
}

/**
 * Creates a [CachedValue] that runs the given [valueSelector] on the facet's primary manifest.  If the manifest is missing,
 * the returned [CachedValue] returns null and will check for the manifest again next time it's evaluated.
 *
 * Note that the primary manifest is a subset of the effective merged manifest and relying on is most likely incorrect. It's
 * up to the [AndroidModuleSystem] to determine which values can be safely read from just the primary manifest.
 *
 * @see com.android.tools.idea.model.MergedManifestManager
 * @see com.android.tools.idea.projectsystem.AndroidModuleSystem
 */
fun <T> AndroidFacet.cachedValueFromPrimaryManifest(valueSelector: AndroidManifestXmlFile.() -> T): CachedValue<T?> {
  return CachedValuesManager.getManager(module.project).createCachedValue<T?> {
    val primaryManifest = runReadAction { getPrimaryManifestXml() }
    if (primaryManifest == null) {
      CachedValueProvider.Result.create(null, ModificationTracker.EVER_CHANGED)
    }
    else {
      val result = runReadAction { primaryManifest.valueSelector() }
      CachedValueProvider.Result.create(result, primaryManifest)
    }
  }
}

/**
 * Returns the PSI representation of the facet's primary manifest, if available.
 */
fun AndroidFacet.getPrimaryManifestXml(): AndroidManifestXmlFile? {
  if (isDisposed) return null
  val psiFile = SourceProviderManager.getInstance(this).mainManifestFile?.let { AndroidPsiUtils.getPsiFileSafely(module.project, it) }
  return (psiFile as? XmlFile)?.let {
    if (it.rootTag?.name == TAG_MANIFEST) {
      AndroidManifestXmlFile(it)
    }
    else {
      null
    }
  }
}

/**
 * The PSI representation of an Android manifest file.
 */
class AndroidManifestXmlFile(delegate: XmlFile) : XmlFile by delegate {
  init {
    require(delegate.rootTag?.name == TAG_MANIFEST)
  }

  val packageName get() = rootTag?.getAttributeValue(ATTR_PACKAGE, null)

  val customPermissions get() = findAndroidNamesForTags(TAG_PERMISSION)

  val customPermissionGroups get() = findAndroidNamesForTags(TAG_PERMISSION_GROUP)

  val versionName get() = rootTag?.getAttributeValue(ATTR_VERSION_NAME, ANDROID_URI)

  /**
   * Returns the android:name attribute of each [XmlTag] of the given type in the [XmlFile].
   */
  private fun findAndroidNamesForTags(tagName: String): Collection<String> {
    val androidNames = mutableListOf<String>()
    accept(object : XmlRecursiveElementVisitor() {
      override fun visitXmlTag(tag: XmlTag?) {
        super.visitXmlTag(tag)
        if (tagName != tag?.name) return
        tag.getAttributeValue(ATTR_NAME, ANDROID_URI)?.let(androidNames::add)
      }
    })
    return androidNames
  }
}