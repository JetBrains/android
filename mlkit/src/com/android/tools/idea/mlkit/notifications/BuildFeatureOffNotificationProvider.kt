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
package com.android.tools.idea.mlkit.notifications

import com.android.tools.idea.mlkit.MlUtils
import com.android.tools.idea.mlkit.viewer.TfliteModelFileEditor
import com.android.tools.idea.npw.model.render
import com.android.tools.idea.npw.template.getExistingModuleTemplateDataBuilder
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.getSyncManager
import com.android.tools.idea.templates.recipe.DefaultRecipeExecutor
import com.android.tools.idea.templates.recipe.RenderingContext
import com.android.tools.idea.wizard.template.Recipe
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplateRenderer
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import java.util.function.Function
import javax.swing.JComponent

/**
 * Notifies users that build feature flag mlModelBinding is off.
 */
class BuildFeatureOffNotificationProvider : EditorNotificationProvider {

  private val addBuildFeatureRecipe: Recipe = {
    setBuildFeature("mlModelBinding", true)
  }

  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
    return Function { createNotificationPanel(file, it, project) }
  }

  private fun createNotificationPanel(file: VirtualFile, fileEditor: FileEditor, project: Project): EditorNotificationPanel? {
    if (fileEditor !is TfliteModelFileEditor || fileEditor.getUserData(HIDDEN_KEY) != null) {
      return null
    }

    val module = ModuleUtilCore.findModuleForFile(file, project)
    if (module == null || MlUtils.isMlModelBindingBuildFeatureEnabled(module) || !MlUtils.isModelFileInMlModelsFolder(module, file)) {
      return null
    }

    val panel = EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Warning)
    panel.text = BANNER_MESSAGE
    panel.createActionLabel("Enable Now") {
      if (Messages.OK == Messages.showOkCancelDialog(project, DIALOG_MESSAGE, DIALOG_TITLE,
                                                     Messages.getOkButton(), Messages.getCancelButton(), Messages.getInformationIcon())) {
        addBuildFeature(module)
        project.getSyncManager().syncProject(ProjectSystemSyncManager.SyncReason.PROJECT_MODIFIED)
      }
    }
    panel.createActionLabel("Hide notification") {
      fileEditor.putUserData(HIDDEN_KEY, "true")
      EditorNotifications.getInstance(project).updateNotifications(file)
    }
    return panel
  }

  private fun addBuildFeature(module: Module) {
    val renderingContext = RenderingContext(
      module.project,
      module,
      "Add build feature mlModelBinding",
      getExistingModuleTemplateDataBuilder(module).build(),
      showErrors = true,
      dryRun = false,
      moduleRoot = null
    )
    addBuildFeatureRecipe.render(
      renderingContext, DefaultRecipeExecutor(renderingContext), TemplateRenderer.ML_MODEL_BINDING_FEATURE_OFF_NOTIFICATION)
  }

  companion object {
    private val HIDDEN_KEY = Key.create<String>("ml.build.feature.off.notification.panel.hidden")
    private const val BANNER_MESSAGE = "ML Model Binding build feature not enabled."
    private const val DIALOG_TITLE = "Enable Build Feature"
    private const val DIALOG_MESSAGE = "This operation adds the below build feature\n\n" +
                                       "buildFeatures {\n" +
                                       "    mlModelBinding true\n" +
                                       "}\n\n" +
                                       "Would you like to add this now?"
  }
}