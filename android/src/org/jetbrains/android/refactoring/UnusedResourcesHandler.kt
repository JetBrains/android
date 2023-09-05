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
import com.google.common.collect.Sets
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager.modules
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandler
import org.jetbrains.android.facet.AndroidFacet
import java.util.Collections

class UnusedResourcesHandler : RefactoringActionHandler {
  @UiThread
  override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) {
    invoke(project, arrayOf(file), dataContext)
  }

  @UiThread
  override fun invoke(project: Project, elements: Array<PsiElement>, dataContext: DataContext) {
    val moduleSet: MutableSet<Module> = Sets.newHashSet()
    val modules = LangDataKeys.MODULE_CONTEXT_ARRAY.getData(dataContext)
    if (modules != null) {
      Collections.addAll(moduleSet, *modules)
    } else {
      val module = PlatformCoreDataKeys.MODULE.getData(dataContext)
      if (module != null) {
        moduleSet.add(module)
      }
    }
    for (element in elements) {
      val module = ModuleUtilCore.findModuleForPsiElement(element)
      if (module != null) {
        moduleSet.add(module)
      }
    }

    // If you've only selected the root project, which isn't an Android module,
    // analyze the whole project.
    if (moduleSet.size == 1 &&
      AndroidFacet.getInstance(moduleSet.iterator().next()) == null
    ) {
      moduleSet.clear()
    }
    invoke(project, moduleSet.toArray(Module.EMPTY_ARRAY), null, false, false)
  }

  companion object {
    @JvmStatic
    operator fun invoke(
      project: Project?,
      modules: Array<Module?>?,
      filter: String?,
      skipDialog: Boolean,
      skipIds: Boolean
    ) {
      var modules = modules
      if (modules == null || modules.size == 0) {
        modules = getInstance.getInstance(project).modules
      }
      val processor = UnusedResourcesProcessor(project!!, modules, filter)
      if (skipIds) {
        processor.setIncludeIds(true)
      }
      if (skipDialog || ApplicationManager.getApplication().isUnitTestMode) {
        processor.run()
      } else {
        val dialog = UnusedResourcesDialog(project, processor)
        dialog.show()
      }
    }
  }
}
