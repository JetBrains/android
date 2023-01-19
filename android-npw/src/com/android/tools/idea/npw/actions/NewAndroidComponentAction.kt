/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.npw.actions

import com.android.AndroidProjectTypes
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.model.StudioAndroidModuleInfo
import com.android.tools.idea.npw.COMPOSE_MIN_AGP_VERSION
import com.android.tools.idea.npw.hasComposeMinAgpVersion
import com.android.tools.idea.npw.model.ProjectSyncInvoker.DefaultProjectSyncInvoker
import com.android.tools.idea.npw.model.RenderTemplateModel.Companion.fromFacet
import com.android.tools.idea.npw.project.getModuleTemplates
import com.android.tools.idea.npw.project.getPackageForPath
import com.android.tools.idea.npw.template.ConfigureTemplateParametersStep
import com.android.tools.idea.npw.template.TemplateResolver
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.TemplateConstraint
import com.android.tools.idea.wizard.template.WizardUiContext
import com.android.tools.idea.wizard.ui.SimpleStudioWizardLayout
import com.android.tools.idea.wizard.ui.StudioWizardDialogBuilder
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplatesUsage.TemplateComponent.WizardUiContext.MENU_GALLERY
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.module.Module
import icons.StudioIcons
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.refactoring.hasAndroidxProperty
import org.jetbrains.android.refactoring.isAndroidx
import org.jetbrains.android.util.AndroidBundle
import java.io.File

// These categories will be using a new wizard
val NEW_WIZARD_CATEGORIES = setOf(Category.Activity, Category.Google, Category.Automotive, Category.Compose)
@JvmField
val CREATED_FILES = DataKey.create<MutableList<File>>("CreatedFiles")

/**
 * An action to launch a wizard to create a component from a template.
 */
// TODO(qumeric): consider accepting [Template] instead?
data class NewAndroidComponentAction @JvmOverloads constructor(
  private val category: Category,
  private val templateName: String,
  private val minSdkApi: Int,
  private val templateConstraints: Collection<TemplateConstraint> = setOf()
) : AnAction(templateName, AndroidBundle.message("android.wizard.action.new.component", templateName), null) {

  @Deprecated("Please use the main constructor")
  constructor(
    category: String,
    templateName: String,
    minSdkApi: Int
  ): this(Category.values().find { it.name == category }!!, templateName, minSdkApi)

  var shouldOpenFiles = true

  private val isActivityTemplate: Boolean
    get() = NEW_WIZARD_CATEGORIES.contains(category)

  init {
    templatePresentation.icon = if (isActivityTemplate) StudioIcons.Shell.Filetree.ACTIVITY else StudioIcons.Shell.Filetree.ANDROID_FILE
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  @Suppress("DialogTitleCapitalization")
  override fun update(e: AnActionEvent) {
    val module = PlatformCoreDataKeys.MODULE.getData(e.dataContext) ?: return
    val moduleInfo = StudioAndroidModuleInfo.getInstance(module) ?: return
    val presentation = e.presentation
    presentation.isVisible = true
    // See also com.android.tools.idea.npw.template.ChooseActivityTypeStep#validateTemplate
    when {
      minSdkApi > moduleInfo.minSdkVersion.featureLevel -> {
        presentation.text = AndroidBundle.message("android.wizard.action.requires.minsdk", templateName, minSdkApi)
        presentation.isEnabled = false
      }
      templateConstraints.contains(TemplateConstraint.AndroidX) && !useAndroidX(module) -> {
        presentation.text = AndroidBundle.message("android.wizard.action.requires.androidx", templateName)
        presentation.isEnabled = false
      }
      !hasComposeMinAgpVersion(module.project, category) -> {
        presentation.text = AndroidBundle.message("android.wizard.action.requires.new.agp", templateName, COMPOSE_MIN_AGP_VERSION)
        presentation.isEnabled = false
      }
      templateConstraints.contains(TemplateConstraint.Aidl) &&
      ProjectBuildModel.get(module.project).getModuleBuildModel(module)?.android()?.buildFeatures()?.aidl()?.toBoolean() != true -> {
        presentation.text = AndroidBundle.message("android.wizard.action.requires.aidlEnabled", templateName)
        presentation.isEnabled = false
      }
      else -> {
        val facet = AndroidFacet.getInstance(module)
        val isProjectReady = facet != null && AndroidModel.get(facet) != null &&
                             facet.configuration.projectType != AndroidProjectTypes.PROJECT_TYPE_INSTANTAPP
        presentation.isEnabled = isProjectReady
      }
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val module = PlatformCoreDataKeys.MODULE.getData(e.dataContext) ?: return
    val facet = AndroidFacet.getInstance(module) ?: return
    if (AndroidModel.get(facet) == null) {
      return
    }
    var targetDirectory = CommonDataKeys.VIRTUAL_FILE.getData(e.dataContext)
    // If the user selected a simulated folder entry (eg "Manifests"), there will be no target directory
    if (targetDirectory != null && !targetDirectory.isDirectory) {
      targetDirectory = targetDirectory.parent
      assert(targetDirectory != null)
    }
    val activityDescription = e.presentation.text // e.g. "Empty Activity", "Tabbed Activity"
    // TODO(qumeric): always show all available templates but preselect a good default
    val moduleTemplates = facet.getModuleTemplates(targetDirectory)
    assert(moduleTemplates.isNotEmpty())
    val initialPackageSuggestion =
      if (targetDirectory == null) facet.getModuleSystem().getPackageName() else facet.getPackageForPath(moduleTemplates, targetDirectory)
    val templateModel = fromFacet(
      facet, initialPackageSuggestion, moduleTemplates[0], "New $activityDescription", DefaultProjectSyncInvoker(),
      shouldOpenFiles, MENU_GALLERY
    )
    val newActivity = TemplateResolver.getAllTemplates()
      .filter { WizardUiContext.MenuEntry in it.uiContexts }
      .find { it.name == templateName }

    templateModel.newTemplate = newActivity!!

    val dialogTitle = AndroidBundle.message(
      if (isActivityTemplate) "android.wizard.new.activity.title" else "android.wizard.new.component.title"
    )
    val stepTitle = AndroidBundle.message(
      if (isActivityTemplate) "android.wizard.config.activity.title" else "android.wizard.config.component.title"
    )
    val wizardBuilder = ModelWizard.Builder().apply {
      addStep(ConfigureTemplateParametersStep(templateModel, stepTitle, moduleTemplates))
    }
    val wizardLayout = SimpleStudioWizardLayout()
    StudioWizardDialogBuilder(wizardBuilder.build(), dialogTitle).setProject(module.project).build(wizardLayout).show()
    e.dataContext.getData(CREATED_FILES)?.addAll(templateModel.createdFiles)
  }

  companion object {
    private fun useAndroidX(module: Module?) = module != null && module.project.hasAndroidxProperty() && module.project.isAndroidx()
  }
}