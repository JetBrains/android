/*
 * Copyright (C) 2016 The Android Open Source Project
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
package org.jetbrains.android.refactoring

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.projectsystem.getAllLinkedModules
import com.android.tools.idea.projectsystem.isHolderModule
import com.android.tools.idea.projectsystem.isLinkedAndroidModule
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandler
import org.jetbrains.android.facet.AndroidFacet

class UnusedResourcesHandler : RefactoringActionHandler {
  @UiThread
  override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) =
    invoke(project, arrayOf(file), dataContext)

  @UiThread
  override fun invoke(project: Project, elements: Array<PsiElement>, dataContext: DataContext) {
    val moduleSet: MutableSet<Module> =
      LangDataKeys.MODULE_CONTEXT_ARRAY.getData(dataContext)?.toMutableSet()
        ?: PlatformCoreDataKeys.MODULE.getData(dataContext)?.let { mutableSetOf(it) }
          ?: mutableSetOf()

    moduleSet.addAll(elements.mapNotNull { ModuleUtilCore.findModuleForPsiElement(it) })

    // If you've only selected the root project, which isn't an Android module,
    // analyze the whole project.
    if (moduleSet.size == 1 && AndroidFacet.getInstance(moduleSet.single()) == null) {
      moduleSet.clear()
    }

    invokeWithDialog(project, moduleSet)
  }

  companion object {
    fun invokeWithDialog(project: Project, modules: Collection<Module>) {
      val processor = UnusedResourcesProcessor(project, getFilter(modules))

      if (ApplicationManager.getApplication().isUnitTestMode) {
        processor.run()
      } else {
        UnusedResourcesDialog(project, processor).show()
      }
    }

    @JvmStatic
    fun invokeSilent(project: Project, modules: Collection<Module>?, filter: String?) {
      val processor = UnusedResourcesProcessor(project, getFilter(modules, filter))
      processor.includeIds = true

      processor.run()
    }

    private fun getFilter(
      modules: Collection<Module>?,
      filter: String? = null
    ): UnusedResourcesProcessor.Filter? {
      if (modules.isNullOrEmpty()) {
        return null
      }

      // Some of the given modules may be holder modules for the linked module group. In that
      // case, all the linked modules should be listed so that resources can be removed from all
      // of them.
      val modulesWithLinked =
        modules
          .flatMap { m ->
            if (m.isLinkedAndroidModule() && m.isHolderModule()) m.getAllLinkedModules()
            else listOf(m)
          }
          .toSet()
      return ResourcesProcessorFilter(modulesWithLinked, filter)
    }
  }

  private class ResourcesProcessorFilter(
    private val modules: Set<Module>,
    private val filter: String?
  ) : UnusedResourcesProcessor.Filter {
    override fun shouldProcessFile(psiFile: PsiFile): Boolean {
      val module = ModuleUtilCore.findModuleForFile(psiFile)
      return module == null || module in modules
    }

    override fun shouldProcessResource(resource: String?) = filter == null || filter == resource
  }
}
