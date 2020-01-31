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
package org.jetbrains.android

import com.android.tools.idea.projectsystem.TestArtifactSearchScopes
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.res.AndroidManifestClassPsiElementFinder
import com.android.tools.idea.util.androidFacet
import com.intellij.facet.ProjectFacetManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.TestSourcesFilter
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.ResolveScopeEnlarger
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.android.facet.AndroidFacet

/**
 * A [ResolveScopeEnlarger] that adds the right `R` and `Manifest` light classes to the resolution scope of files within Android modules.
 *
 * This way references can be correctly resolved and offer variants for code completion. Note that for every module and AAR we create two
 * `R` classes: namespaced and non-namespaced. It's through this [ResolveScopeEnlarger] that the right classes are added to the resolution
 * process.
 *
 * For Kotlin code, this is called with the moduleFile (the `*.iml` file) instead of the source file, because Kotlin scoping works
 * per-module in the IDE plugin. Unless `module.moduleFile` is null (when the iml doesn't exist, e.g. when tests don't create one) in which
 * case the enlargers cannot be called.
 */
class AndroidResolveScopeEnlarger : ResolveScopeEnlarger() {

  companion object {
    private val LOG = Logger.getInstance(AndroidResolveScopeEnlarger::class.java)

    // Keys for caching resolve scopes.
    private val resolveScopeWithTestsKey =
      Key.create<CachedValue<SearchScope?>>("${AndroidResolveScopeEnlarger::class.java.name}.resolveScopeWithTests")
    private val resolveScopeSansTestsKey =
      Key.create<CachedValue<SearchScope?>>("${AndroidResolveScopeEnlarger::class.java.name}.resolveScopeSansTests")

    private fun computeAdditionalClassesForModule(module: Module, includeTestClasses: Boolean): Collection<PsiClass>? {
      val project = module.project
      if (!ProjectFacetManager.getInstance(project).hasFacets(AndroidFacet.ID)) return emptyList()
      if (module.androidFacet == null) return emptyList()
      val result = mutableListOf<PsiClass>()

      project.getProjectSystem()
        .getLightResourceClassService()
        .getLightRClassesAccessibleFromModule(module, includeTestClasses)
        .let(result::addAll)

      result.addAll(AndroidManifestClassPsiElementFinder.getInstance(project).getManifestClassesAccessibleFromModule(module))

      return result
    }

    private fun computeAdditionalResolveScopeForModule(module: Module, includeTests: Boolean): SearchScope? {
      val lightClasses = computeAdditionalClassesForModule(module, includeTests) ?: return null
      LOG.debug { "Enlarging scope for $module with ${lightClasses.size} light Android classes." }
      if (lightClasses.isEmpty()) return null

      // Unfortunately LocalScope looks at containingFile.virtualFile, which is null for non-physical PSI.
      val virtualFiles = lightClasses.map { it.containingFile.viewProvider.virtualFile }
      return GlobalSearchScope.filesWithoutLibrariesScope(module.project, virtualFiles)
    }

    /** Returns the (possibly cached) additional resolve scope for the given module. */
    fun getAdditionalResolveScopeForModule(module: Module, includeTests: Boolean): SearchScope? {
      // Cache is invalidated on any PSI change.
      val cacheKey = if (includeTests) resolveScopeWithTestsKey else resolveScopeSansTestsKey
      return CachedValuesManager.getManager(module.project).getCachedValue(module, cacheKey, {
        CachedValueProvider.Result(
          computeAdditionalResolveScopeForModule(module, includeTests),
          PsiModificationTracker.MODIFICATION_COUNT
        )
      }, false)
    }
  }

  override fun getAdditionalResolveScope(file: VirtualFile, project: Project): SearchScope? {
    val module = ModuleUtil.findModuleForFile(file, project) ?: return null

    return getAdditionalResolveScopeForModule(
      module,
      includeTests = when {
        !TestSourcesFilter.isTestSources(file, project) -> false
        TestArtifactSearchScopes.getInstance(module)?.isAndroidTestSource(file) == false -> false
        else -> true
      }
    )
  }
}
