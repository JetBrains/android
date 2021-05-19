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
import com.android.tools.idea.model.MergedManifestModificationTracker
import com.android.tools.idea.res.AndroidClassWithOnlyInnerClassesBase
import com.android.tools.idea.res.getFieldNameByResourceName
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ModulePointerManager
import com.intellij.openapi.util.text.StringUtil.getShortName
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.containers.isNullOrEmpty
import org.jetbrains.android.AndroidResolveScopeEnlarger.Companion.LIGHT_CLASS_KEY
import org.jetbrains.android.AndroidResolveScopeEnlarger.Companion.MODULE_POINTER_KEY
import org.jetbrains.android.dom.manifest.Manifest
import org.jetbrains.android.dom.manifest.getCustomPermissionGroups
import org.jetbrains.android.dom.manifest.getCustomPermissions
import org.jetbrains.android.dom.manifest.getPackageName
import org.jetbrains.android.facet.AndroidFacet


private val LOG: Logger get() = logger("#org.jetbrains.android.augment.ManifestClasses")

/**
 * Manifest class for a given module.
 */
class ManifestClass(
  val facet: AndroidFacet,
  psiManager: PsiManager
) : AndroidClassWithOnlyInnerClassesBase(
  SdkConstants.FN_MANIFEST_BASE,
  getPackageName(facet),
  psiManager,
  listOf(PsiModifier.PUBLIC, PsiModifier.FINAL)
) {

  init {
    setModuleInfo(facet.module, false)
    val lightVirtualFile = myFile.viewProvider.virtualFile
    lightVirtualFile.putUserData(MODULE_POINTER_KEY, ModulePointerManager.getInstance(project).create(facet.module))
    lightVirtualFile.putUserData(LIGHT_CLASS_KEY, ManifestClass::class.java)
  }

  override fun getQualifiedName(): String? = getPackageName(facet)?.let { it + "." + SdkConstants.FN_MANIFEST_BASE }

  override fun doGetInnerClasses(): Array<PsiClass> {
    val classes = mutableListOf<PsiClass>()

    if (!getCustomPermissions(facet).isNullOrEmpty()) {
      classes += PermissionClass(facet, this)
    }

    if (!getCustomPermissionGroups(facet).isNullOrEmpty()) {
      classes += PermissionGroupClass(facet, this)
    }

    return classes.toTypedArray()
  }

  override fun getInnerClassesDependencies() = MergedManifestModificationTracker.getInstance(facet.module)
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
    val manifest = Manifest.getMainManifest(myFacet)
    if (manifest == null) {
      CachedValueProvider.Result.create(PsiField.EMPTY_ARRAY, MergedManifestModificationTracker.getInstance(myFacet.module))
    }
    else {
      CachedValueProvider.Result.create<Array<PsiField>>(
        doGetFields().map { (name, value) ->
          ManifestLightField(
            name,
            this,
            javaLangString,
            AndroidLightField.FieldModifier.FINAL,
            value
          ).apply {
            initializer = factory.createExpressionFromText("\"$value\"", this)
          }
        }.toTypedArray(),
        listOf(MergedManifestModificationTracker.getInstance(myFacet.module))
      )
    }
  }

  override fun getFields(): Array<PsiField> = myFieldsCache.value

  private fun doGetFields(): List<FieldInfo> {
    LOG.debug { "Recomputing fields for $this" }
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
  override fun getNamesFromManifest(): Collection<String> = getCustomPermissions(facet) ?: emptySet()
}


/**
 * Light implementation of `Manifest.permission_group`.
 */
internal class PermissionGroupClass(
  private val facet: AndroidFacet,
  parentClass: PsiClass
) : ManifestInnerClass(facet, "permission_group", parentClass) {
  override fun getNamesFromManifest(): Collection<String> = getCustomPermissionGroups(facet) ?: emptySet()
}
