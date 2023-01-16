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
package com.android.tools.idea.res

import com.android.tools.idea.projectsystem.ScopeType
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.res.ModuleRClass.SourceSet.MAIN
import com.android.tools.idea.res.ModuleRClass.SourceSet.TEST
import com.android.tools.idea.res.ResourceRepositoryRClass.Transitivity.NON_TRANSITIVE
import com.android.tools.idea.res.ResourceRepositoryRClass.Transitivity.TRANSITIVE
import com.intellij.openapi.module.ModulePointerManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPointerManager
import org.jetbrains.android.AndroidResolveScopeEnlarger.Companion.BACKING_CLASS
import org.jetbrains.android.AndroidResolveScopeEnlarger.Companion.FILE_SOURCE_SET_KEY
import org.jetbrains.android.AndroidResolveScopeEnlarger.Companion.LIGHT_CLASS_KEY
import org.jetbrains.android.AndroidResolveScopeEnlarger.Companion.MODULE_POINTER_KEY
import org.jetbrains.android.AndroidResolveScopeEnlarger.Companion.TRANSITIVITY_KEY
import org.jetbrains.android.augment.AndroidLightField
import org.jetbrains.android.facet.AndroidFacet

class ModuleRClass(
  val facet: AndroidFacet,
  psiManager: PsiManager,
  private val sourceSet: SourceSet,
  transitivity: Transitivity,
  fieldModifier: AndroidLightField.FieldModifier
) : ResourceRepositoryRClass(
  psiManager,
  ModuleResourcesSource(facet, sourceSet, transitivity, fieldModifier)
) {

  enum class SourceSet { MAIN, TEST }

  /**
   * Finds the [PsiClass] for the compiled R class that corresponds to this R class.
   */
  private fun findPhysicalRClass(): PsiClass? {
    val rVFile = facet.getModuleSystem().moduleClassFileFinder.findClassFile("${packageName}.R") ?: return null
    return (PsiManager.getInstance(facet.module.project).findFile(rVFile) as? PsiClassOwner)
      ?.classes
      ?.singleOrNull()
  }

  init {
    setModuleInfo(
      facet.module,
      when (sourceSet) {
        MAIN -> false
        TEST -> true
      }
    )
    val lightVirtualFile = myFile.viewProvider.virtualFile

    if (fieldModifier == AndroidLightField.FieldModifier.FINAL) {
      // If the R fields are final, we try to find the actual physical R class to use real values. This ensures that
      // if the values used by the Light R class are inlined by the Live Edit compiler, they remain valid and map to
      // the values from the last compilation.
      findPhysicalRClass()?.let { lightVirtualFile.putUserData(BACKING_CLASS, SmartPointerManager.createPointer(it)) }
    }
    lightVirtualFile.putUserData(MODULE_POINTER_KEY, ModulePointerManager.getInstance(project).create(facet.module))
    lightVirtualFile.putUserData(LIGHT_CLASS_KEY, ModuleRClass::class.java)
    lightVirtualFile.putUserData(TRANSITIVITY_KEY, transitivity)
    lightVirtualFile.putUserData(FILE_SOURCE_SET_KEY, sourceSet)
  }

  override fun getScopeType() = when (sourceSet) {
    MAIN -> ScopeType.MAIN
    TEST -> ScopeType.ANDROID_TEST
  }

  private class ModuleResourcesSource(
    val facet: AndroidFacet,
    val sourceSet: SourceSet,
    private val transitivity: Transitivity,
    val _fieldModifier: AndroidLightField.FieldModifier
  ) : ResourcesSource {
    override fun getResourceNamespace() = ResourceRepositoryManager.getInstance(facet).namespace

    override fun getFieldModifier() = _fieldModifier

    override fun getPackageName() = when (sourceSet) {
      MAIN -> facet.getModuleSystem().getPackageName()
      TEST -> facet.getModuleSystem().getTestPackageName()
    }

    override fun getResourceRepositoryManager(): ResourceRepositoryManager {
      return ResourceRepositoryManager.getInstance(facet)
    }

    override fun getResourceRepository(): LocalResourceRepository {
      val repoManager = ResourceRepositoryManager.getInstance(facet)
      return when (sourceSet) {
        MAIN -> when (transitivity) {
          TRANSITIVE -> repoManager.appResources
          NON_TRANSITIVE -> repoManager.moduleResources
        }
        TEST -> when (transitivity) {
          TRANSITIVE -> repoManager.testAppResources
          NON_TRANSITIVE -> repoManager.testModuleResources
        }
      }
    }

    override fun getTransitivity(): Transitivity {
      return transitivity
    }
  }
}

