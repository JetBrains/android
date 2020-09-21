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
package com.android.tools.idea.npw.importing

import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.gradle.project.ModuleImporter
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.stats.withProjectId
import com.android.tools.idea.wizard.model.WizardModel
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplatesUsage.TemplateComponent.TemplateType.NO_ACTIVITY
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplatesUsage.TemplateComponent.WizardUiContext.NEW_MODULE
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplatesUsage.TemplateModule.ModuleType.IMPORT_GRADLE
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Model that represents the import of an existing library (Gradle project or Eclipse ADT project) into a Gradle project as a new Module.
 * Currently this Model actually delegates almost all of its work to the [WizardContext]. This is required as the steps that import an ADT
 * project are also used directly by IntelliJ in the "new project from existing source" flow, which uses a WizardContext for its state
 * (these steps are injected into the Wizard by the [SourceToGradleModuleStep]).
 */
class SourceToGradleModuleModel(
  val project: Project,
  private val projectSyncInvoker: ProjectSyncInvoker
) : WizardModel() {
  val context: WizardContext = WizardContext(project, this)
  private var modulesToImport = mapOf<String, VirtualFile>()
  @JvmField
  val sourceLocation = StringValueProperty().apply {
    addConstraint(String::trim)
  }

  override fun handleFinished() {
    runWriteAction {
      ModuleImporter.getImporter(context).importProjects(modulesToImport)
    }
    ApplicationManager.getApplication().invokeLater {
      projectSyncInvoker.syncProject(project)
    }

    val templateComponentBuilder = AndroidStudioEvent.TemplatesUsage.TemplateComponent.newBuilder().apply {
      templateType = NO_ACTIVITY
      wizardUiContext = NEW_MODULE
    }

    val templateModuleBuilder = AndroidStudioEvent.TemplatesUsage.TemplateModule.newBuilder().apply {
      moduleType = IMPORT_GRADLE
    }

    val aseBuilder = AndroidStudioEvent.newBuilder()
      .setCategory(AndroidStudioEvent.EventCategory.TEMPLATE)
      .setKind(AndroidStudioEvent.EventKind.WIZARD_TEMPLATES_USAGE)
      .setTemplateUsage(
        AndroidStudioEvent.TemplatesUsage.newBuilder()
          .setTemplateComponent(templateComponentBuilder)
          .setTemplateModule(templateModuleBuilder)
      )
    UsageTracker.log(aseBuilder.withProjectId(project))
  }

  fun setModulesToImport(value: Map<String, VirtualFile>) {
    modulesToImport = value.toMap()
  }
}