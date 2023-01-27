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

import com.android.tools.idea.findDependenciesWithResources
import com.android.tools.idea.model.Namespacing
import com.android.tools.idea.projectsystem.ProjectSyncModificationTracker
import com.android.tools.idea.projectsystem.TestArtifactSearchScopes
import com.android.tools.idea.projectsystem.getMainModule
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.res.AndroidDependenciesCache
import com.android.tools.idea.res.ModuleRClass
import com.android.tools.idea.res.ModuleRClass.SourceSet
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.res.ResourceRepositoryRClass
import com.android.tools.idea.res.ResourceRepositoryRClass.Transitivity
import com.android.tools.idea.res.ResourceRepositoryRClass.Transitivity.NON_TRANSITIVE
import com.android.tools.idea.res.ResourceRepositoryRClass.Transitivity.TRANSITIVE
import com.android.tools.idea.res.SmallAarRClass
import com.android.tools.idea.res.TransitiveAarRClass
import com.android.tools.idea.util.androidFacet
import com.android.utils.reflection.qualifiedName
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModulePointer
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.TestSourcesFilter
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.ResolveScopeEnlarger
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.android.augment.ManifestClass

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

    /**
     * Set of keys for data that can be found in [LightVirtualFile]s and used to determine whether the files can be found in a given
     * modules' resolve scope.
     */
    @JvmField
    val LIGHT_CLASS_KEY: Key<Class<out LightElement>> = Key.create(::LIGHT_CLASS_KEY.qualifiedName)
    @JvmField
    val BACKING_CLASS: Key<SmartPsiElementPointer<PsiClass>> = Key.create(::BACKING_CLASS.qualifiedName)
    @JvmField
    val MODULE_POINTER_KEY: Key<ModulePointer> = Key.create(::MODULE_POINTER_KEY.qualifiedName)
    val AAR_ADDRESS_KEY: Key<String> = Key.create(::AAR_ADDRESS_KEY.qualifiedName)
    val FILE_SOURCE_SET_KEY: Key<SourceSet> = Key.create(::FILE_SOURCE_SET_KEY.qualifiedName)
    val TRANSITIVITY_KEY: Key<Transitivity> = Key.create(::TRANSITIVITY_KEY.qualifiedName)

    // Keys for caching resolve scopes.
    private val resolveScopeWithTestsKey =
      Key.create<CachedValue<SearchScope?>>("${AndroidResolveScopeEnlarger::class.java.name}.resolveScopeWithTests")
    private val resolveScopeSansTestsKey =
      Key.create<CachedValue<SearchScope?>>("${AndroidResolveScopeEnlarger::class.java.name}.resolveScopeSansTests")

    private fun computeAdditionalResolveScopeForModule(module: Module, includeTests: Boolean): SearchScope? {
      LOG.debug { "Enlarging scope for $module" }
      val androidFacet = module.androidFacet ?: return null
      return ManifestAndRClassScope(
        module,
        includeTests,
        if (androidFacet.module.getModuleSystem().isRClassTransitive) TRANSITIVE else NON_TRANSITIVE)
    }

    private class ManifestAndRClassScope(
      val module: Module,
      val includeTests: Boolean,
      val transitivity: Transitivity
    ): GlobalSearchScope(module.project) {

      private val dependentAarAddresses = findDependenciesWithResources(module).keys
      private val namespacing = ResourceRepositoryManager.getInstance(module)?.namespacing

      fun isLightVirtualFileFromAccessibleModule(file: LightVirtualFile): Boolean {
        val modulePointer = file.getUserData(MODULE_POINTER_KEY) ?: return false
        val resourceClassModule = modulePointer.module ?: return false
        // Resource classes should be available to all modules that stem from the same Gradle project
        if (resourceClassModule == module || resourceClassModule == module.getMainModule()) {
          return true
        }
        val resourceClassFacet = resourceClassModule.androidFacet ?: return false
        val androidDependencies = AndroidDependenciesCache.getAllAndroidDependencies(module, false)
        return androidDependencies.contains(resourceClassFacet)
      }

      private fun isLightVirtualFileFromAccessibleAar(file: LightVirtualFile): Boolean {
        val aarAddress = file.getUserData(AAR_ADDRESS_KEY) ?: return false
        return dependentAarAddresses.contains(aarAddress)
      }

      private fun isModuleRVirtualFileAccessible(file: LightVirtualFile): Boolean {
        // Check if R Class is from current or dependent module.
        if (!isLightVirtualFileFromAccessibleModule(file)) {
          return false
        }
        // Check if R Class is the required transitivity.
        val rClassTransitivity = file.getUserData(TRANSITIVITY_KEY) ?: return false
        if (transitivity != rClassTransitivity) {
          return false
        }
        // Only allow R class from relevant source sets
        val fileSourceSet = file.getUserData(FILE_SOURCE_SET_KEY) ?: return false
        return if (includeTests) true else fileSourceSet == SourceSet.MAIN
      }

      override fun contains(file: VirtualFile): Boolean {
        if (file !is LightVirtualFile) return false
        return when (file.getUserData(LIGHT_CLASS_KEY)) {
          ModuleRClass::class.java -> isModuleRVirtualFileAccessible(file)
          ManifestClass::class.java -> isLightVirtualFileFromAccessibleModule(file)
          SmallAarRClass::class.java -> isLightVirtualFileFromAccessibleAar(file) && namespacing == Namespacing.REQUIRED
          TransitiveAarRClass::class.java -> isLightVirtualFileFromAccessibleAar(file) && namespacing == Namespacing.DISABLED
          ResourceRepositoryRClass::class.java -> {
            // For BlazeRClass which does not take into account test scope or transitivity
            isLightVirtualFileFromAccessibleModule(file)
          }
          else -> false
        }
      }

      override fun isSearchInModuleContent(aModule: Module) = true
      override fun isSearchInLibraries() = false
    }

    /** Returns the (possibly cached) additional resolve scope for the given module. */
    fun getAdditionalResolveScopeForModule(module: Module, includeTests: Boolean): SearchScope? {
      // Cache is invalidated after a gradle sync.
      val cacheKey = if (includeTests) resolveScopeWithTestsKey else resolveScopeSansTestsKey
      val project = module.project
      return CachedValuesManager.getManager(project).getCachedValue(module, cacheKey, {
        CachedValueProvider.Result(
          computeAdditionalResolveScopeForModule(module, includeTests),
          ProjectSyncModificationTracker.getInstance(project)
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
