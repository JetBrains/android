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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.res.AndroidManifestClassPsiElementFinder
import com.android.tools.idea.res.AndroidResourceClassPsiElementFinder
import com.android.tools.idea.util.androidFacet
import com.intellij.facet.ProjectFacetManager
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.ResolveScopeEnlarger
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import org.jetbrains.android.facet.AndroidFacet

/**
 * A [ResolveScopeEnlarger] that adds the right `R` and `Manifest` light classes to the resolution scope of files within Android modules.
 *
 * This way references can be correctly resolved and offer variants for code completion. Note that for every module and AAR we create two
 * `R` classes: namespaced and non-namespaced. It's through this [ResolveScopeEnlarger] that the right classes are added to the resolution
 * process.
 */
class AndroidResolveScopeEnlarger : ResolveScopeEnlarger() {
  override fun getAdditionalResolveScope(file: VirtualFile, project: Project): SearchScope? {
    if (!StudioFlags.IN_MEMORY_R_CLASSES.get()) return null
    if (!ProjectFacetManager.getInstance(project).hasFacets(AndroidFacet.ID)) return null
    val module = ModuleUtil.findModuleForFile(file, project) ?: return null
    if (module.androidFacet == null) return null

    val result = mutableListOf<PsiClass>()

    project.getProjectSystem()
      .getPsiElementFinders()
      .filterIsInstance(AndroidResourceClassPsiElementFinder::class.java)
      .singleOrNull()
      ?.lightResourceClassService
      ?.getLightRClassesAccessibleFromModule(module)
      ?.let(result::addAll)

    result.addAll(AndroidManifestClassPsiElementFinder.getInstance(project).getManifestClassesAccessibleFromModule(module))

    // Unfortunately LocalScope looks at containingFile.virtualFile, which is null for non-physical PSI.
    return GlobalSearchScope.filesWithoutLibrariesScope(project, result.map { it.containingFile.viewProvider.virtualFile })
  }
}
