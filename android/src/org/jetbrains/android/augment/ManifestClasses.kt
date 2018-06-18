/*
 * Copyright (C) 2018 The Android Open Source Project
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
package org.jetbrains.android.augment

import com.android.SdkConstants
import com.android.tools.idea.res.AndroidClassWithOnlyInnerClassesBase
import com.intellij.openapi.util.text.StringUtil.getShortName
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.containers.isNullOrEmpty
import org.jetbrains.android.dom.manifest.AndroidManifestUtils
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidResourceUtil.getFieldNameByResourceName

/**
 * Manifest class for a given module.
 */
class ManifestClass(
  packageName: String,
  private val facet: AndroidFacet,
  psiManager: PsiManager
) : AndroidClassWithOnlyInnerClassesBase(
  SdkConstants.FN_MANIFEST_BASE,
  packageName,
  psiManager,
  listOf(PsiModifier.PUBLIC, PsiModifier.FINAL)
) {
  override fun doGetInnerClasses(): Array<PsiClass> {
    val classes = mutableListOf<PsiClass>()

    if (!AndroidManifestUtils.getCustomPermissions(facet).isNullOrEmpty()) {
      classes += PermissionClass(facet, this)
    }

    if (!AndroidManifestUtils.getCustomPermissionGroups(facet).isNullOrEmpty()) {
      classes += PermissionGroupClass(facet, this)
    }

    return classes.toTypedArray()
  }
}

/**
 * Base class for light implementations of inner classes of the Manifest class, e.g. `Manifest.permission`.
 */
sealed class ManifestInnerClass(
  private val myFacet: AndroidFacet,
  name: String,
  parentClass: PsiClass
) : AndroidLightInnerClassBase(parentClass, name) {

  protected data class FieldInfo(val fieldName: String, val fieldValue: String)

  private val javaLangString = PsiType.getJavaLangString(myManager, GlobalSearchScope.allScope(project))
  private val factory = JavaPsiFacade.getElementFactory(project)

  private val myFieldsCache: CachedValue<Array<PsiField>> = CachedValuesManager.getManager(project).createCachedValue {
    val manifest = myFacet.manifest
    if (manifest == null) {
      CachedValueProvider.Result.create(PsiField.EMPTY_ARRAY, PsiModificationTracker.MODIFICATION_COUNT)
    }
    else {
      CachedValueProvider.Result.create<Array<PsiField>>(
        doGetFields().map { (name, value) ->
          AndroidLightField(
            name,
            this,
            javaLangString,
            AndroidLightField.FieldModifier.FINAL,
            value
          ).apply {
            initializer = factory.createExpressionFromText("\"$value\"", this)
          }
        }.toTypedArray(),
        listOf(manifest.xmlElement?.containingFile ?: PsiModificationTracker.MODIFICATION_COUNT)
      )
    }
  }

  override fun getFields(): Array<PsiField> = myFieldsCache.value

  private fun doGetFields(): List<FieldInfo> {
    return getNamesFromManifest().map { FieldInfo(getFieldNameByResourceName(getShortName(it)), it) }
  }

  protected abstract fun getNamesFromManifest(): Collection<String>
}

/**
 * Light implementation of `Manifest.permission`.
 */
internal class PermissionClass(
  private val facet: AndroidFacet,
  parentClass: PsiClass
) : ManifestInnerClass(facet, "permission", parentClass) {
  override fun getNamesFromManifest(): Collection<String> = AndroidManifestUtils.getCustomPermissions(facet) ?: emptySet()
}


/**
 * Light implementation of `Manifest.permission_group`.
 */
internal class PermissionGroupClass(
  private val facet: AndroidFacet,
  parentClass: PsiClass
) : ManifestInnerClass(facet, "permission_group", parentClass) {
  override fun getNamesFromManifest(): Collection<String> = AndroidManifestUtils.getCustomPermissionGroups(facet) ?: emptySet()
}
