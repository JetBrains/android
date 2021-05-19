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

import com.android.tools.idea.mlkit.MlUtils
import com.android.tools.idea.mlkit.logEvent
import com.android.tools.idea.npw.model.render
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.StringProperty
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.getSyncManager
import com.android.tools.idea.templates.getExistingModuleTemplateDataBuilder
import com.android.tools.idea.templates.recipe.DefaultRecipeExecutor
import com.android.tools.idea.templates.recipe.RenderingContext
import com.android.tools.idea.wizard.model.WizardModel
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.Recipe
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplateRenderer
import com.google.wireless.android.sdk.stats.MlModelBindingEvent.EventType
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.vfs.LargeFileWriteRequestor
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.util.AndroidBundle.message
import java.io.File
import java.io.IOException

/**
 * [WizardModel] that contains model location to import.
 */
class MlWizardModel(val module: Module) : WizardModel(), LargeFileWriteRequestor {

  private val basicRecipe: Recipe = {
    for (dependency in MlUtils.getMissingRequiredDependencies(module)) {
      addDependency(dependency.toString())
    }
    setBuildFeature("mlModelBinding", true)
  }

  private val gpuRecipe: Recipe = {
    for (dependency in MlUtils.getMissingTfliteGpuDependencies(module)) {
      addDependency(dependency.toString())
    }
  }

  @JvmField
  val sourceLocation: StringProperty = StringValueProperty()

  @JvmField
  val mlDirectory: StringProperty = StringValueProperty()

  @JvmField
  val autoAddBasicSetup: BoolValueProperty = BoolValueProperty(true)

  @JvmField
  val autoAddGpuSetup: BoolValueProperty = BoolValueProperty(false)

  override fun handleFinished() {
    object : Task.Modal(module.project, message("android.compile.messages.generating.r.java.content.name"), false) {
      lateinit var moduleTemplateData: ModuleTemplateData
      override fun run(indicator: ProgressIndicator) {
        // Slow operation, do in background.
        moduleTemplateData = getExistingModuleTemplateDataBuilder(module).build()
      }

      override fun onFinished() {
        render(moduleTemplateData)
      }
    }.queue()
  }

  private fun render(moduleTemplateData: ModuleTemplateData) {
    val fromFile: VirtualFile? = VfsUtil.findFileByIoFile(File(sourceLocation.get()), false)
    val directoryPath: String = mlDirectory.get()
    runWriteAction {
      try {
        val toDir: VirtualFile? = VfsUtil.createDirectoryIfMissing(directoryPath)
        if (fromFile != null && toDir != null) {
          // Delete existing file if it exists.
          val existingFile = toDir.findChild(fromFile.name)
          if (existingFile != null && existingFile.exists()) {
            existingFile.delete(this)
          }

          val virtualFile = fromFile.copy(this, toDir, fromFile.name)
          val fileEditorManager: FileEditorManager = FileEditorManager.getInstance(module.project)
          fileEditorManager.openFile(virtualFile, true)
          if (autoAddBasicSetup.get() || autoAddGpuSetup.get()) {
            val context = RenderingContext(
              module.project,
              module,
              "Import TensorFlow Lite Model",
              moduleTemplateData,
              showErrors = true,
              dryRun = false,
              moduleRoot = null
            )
            if (autoAddBasicSetup.get()) {
              basicRecipe.render(context, DefaultRecipeExecutor(context), TemplateRenderer.ML_MODEL_BINDING_IMPORT_WIZARD)
            }
            if (autoAddGpuSetup.get()) {
              gpuRecipe.render(context, DefaultRecipeExecutor(context), TemplateRenderer.ML_MODEL_BINDING_IMPORT_WIZARD)
            }
            module.project.getSyncManager().syncProject(ProjectSystemSyncManager.SyncReason.PROJECT_MODIFIED)
          }

          logEvent(EventType.MODEL_IMPORT_FROM_WIZARD, fromFile)
        }
      }
      catch (e: IOException) {
        logger<MlWizardModel>().error("Error copying %s to %s".format(fromFile, directoryPath), e)
      }
    }
  }
}