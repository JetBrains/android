/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.mlkit.importmodel

import com.android.tools.idea.mlkit.MlkitUtils
import com.android.tools.idea.npw.model.render
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.StringProperty
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.templates.getDummyModuleTemplateDataBuilder
import com.android.tools.idea.templates.recipe.DefaultRecipeExecutor
import com.android.tools.idea.templates.recipe.RenderingContext
import com.android.tools.idea.wizard.model.WizardModel
import com.android.tools.idea.wizard.template.Recipe
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.io.IOException

/**
 * [WizardModel] that contains model location to import.
 */
class MlWizardModel(private val module: Module) : WizardModel() {

  private val mlkitRecipe: Recipe = {
    for (dependency in MlkitUtils.getRequiredDependencies()) {
      addDependency(dependency)
      //TODO(b/152783932): add mlModelBinding build feature once it is ready.
    }
  }

  @JvmField
  val sourceLocation: StringProperty = StringValueProperty()
  @JvmField
  val mlDirectory: StringProperty = StringValueProperty()
  @JvmField
  val autoUpdateBuildFile: BoolValueProperty = BoolValueProperty(true)

  override fun handleFinished() {
    val fromFile: VirtualFile? = VfsUtil.findFileByIoFile(File(sourceLocation.get()), false)
    val directoryPath: String = mlDirectory.get()
    runWriteAction {
      try {
        val toDir: VirtualFile? = VfsUtil.createDirectoryIfMissing(directoryPath)
        if (fromFile != null && toDir != null) {
          val virtualFile: VirtualFile = VfsUtilCore.copyFile(this, fromFile, toDir)
          val fileEditorManager: FileEditorManager = FileEditorManager.getInstance(module.project)
          fileEditorManager.openFile(virtualFile, true)
          if (autoUpdateBuildFile.get()) {
            val context = RenderingContext(
              module.project,
              module,
              "Import TensorFlow Lite Model",
              getDummyModuleTemplateDataBuilder(module.project).build(),
              showErrors = true,
              dryRun = false,
              moduleRoot = null
            )
            // TODO(b/153163381): Add loggingEvent here.
            mlkitRecipe.render(context, DefaultRecipeExecutor(context), null)
          }
        }
      }
      catch (e: IOException) {
        logger<MlWizardModel>().error("Error copying %s to %s".format(fromFile, directoryPath), e)
      }
    }
  }
}