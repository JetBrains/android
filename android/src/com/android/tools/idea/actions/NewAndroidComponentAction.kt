/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.actions

import com.android.AndroidProjectTypes
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.model.AndroidModuleInfo
import com.android.tools.idea.npw.model.ProjectSyncInvoker.DefaultProjectSyncInvoker
import com.android.tools.idea.npw.model.RenderTemplateModel.Companion.fromFacet
import com.android.tools.idea.npw.project.getModuleTemplates
import com.android.tools.idea.npw.project.getPackageForApplication
import com.android.tools.idea.npw.project.getPackageForPath
import com.android.tools.idea.npw.template.ConfigureTemplateParametersStep
import com.android.tools.idea.npw.template.ConfigureTemplateParametersStep2
import com.android.tools.idea.npw.template.TemplateHandle
import com.android.tools.idea.npw.template.TemplateResolver
import com.android.tools.idea.templates.TemplateManager
import com.android.tools.idea.templates.TemplateMetadata.TemplateConstraint
import com.android.tools.idea.ui.wizard.StudioWizardDialogBuilder
import com.android.tools.idea.ui.wizard.WizardUtils
import com.android.tools.idea.ui.wizard.WizardUtils.COMPOSE_MIN_AGP_VERSION
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.template.WizardUiContext
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.module.Module
import icons.AndroidIcons
import icons.StudioIcons
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.refactoring.hasAndroidxProperty
import org.jetbrains.android.refactoring.isAndroidx
import org.jetbrains.android.util.AndroidBundle
import java.io.File
import java.util.EnumSet

// These categories will be using a new wizard
@JvmField
val NEW_WIZARD_CATEGORIES = setOf("Activity", "Google", TemplateManager.CATEGORY_AUTOMOTIVE, TemplateManager.CATEGORY_COMPOSE)
@JvmField
val FRAGMENT_CATEGORY = setOf("Fragment")
@JvmField
val CREATED_FILES = DataKey.create<MutableList<File>>("CreatedFiles")

/**
 * An action to launch a wizard to create a component from a template.
 */
data class NewAndroidComponentAction @JvmOverloads constructor(
  private val templateCategory: String,
  private val templateName: String,
  private val minSdkApi: Int,
  private val minBuildSdkApi: Int = minSdkApi,
  private val templateConstraints: EnumSet<TemplateConstraint> = EnumSet.noneOf(TemplateConstraint::class.java),
  private val templateFile: File? = TemplateManager.getInstance().getTemplateFile(templateCategory, templateName)
) : AnAction(templateName, AndroidBundle.message("android.wizard.action.new.component", templateName), null) {
  var shouldOpenFiles = true

  private val isActivityTemplate: Boolean
    get() = NEW_WIZARD_CATEGORIES.contains(templateCategory)

  init {
    templatePresentation.icon = if (isActivityTemplate) AndroidIcons.Activity else StudioIcons.Shell.Filetree.ANDROID_FILE
  }

  override fun update(e: AnActionEvent) {
    val module = LangDataKeys.MODULE.getData(e.dataContext) ?: return
    val moduleInfo = AndroidModuleInfo.getInstance(module) ?: return
    val presentation = e.presentation
    presentation.isVisible = true
    // See also com.android.tools.idea.npw.template.ChooseActivityTypeStep#validateTemplate
    val buildSdkVersion = moduleInfo.buildSdkVersion
    when {
      minSdkApi > moduleInfo.minSdkVersion.featureLevel -> {
        presentation.text = AndroidBundle.message("android.wizard.action.requires.minsdk", templateName, minSdkApi)
        presentation.isEnabled = false
      }
      buildSdkVersion != null && minBuildSdkApi > buildSdkVersion.featureLevel -> {
        presentation.text = AndroidBundle.message("android.wizard.action.requires.minbuildsdk", templateName, minBuildSdkApi)
        presentation.isEnabled = false
      }
      templateConstraints.contains(TemplateConstraint.ANDROIDX) && !useAndroidX(module) -> {
        presentation.text = AndroidBundle.message("android.wizard.action.requires.androidx", templateName)
        presentation.isEnabled = false
      }
      !WizardUtils.hasComposeMinAgpVersion(module.project, templateCategory) -> {
        presentation.text = AndroidBundle.message("android.wizard.action.requires.new.agp", templateName, COMPOSE_MIN_AGP_VERSION)
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
    val module = LangDataKeys.MODULE.getData(e.dataContext) ?: return
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
    val moduleTemplates = facet.getModuleTemplates(targetDirectory)
    assert(moduleTemplates.isNotEmpty())
    val initialPackageSuggestion =
      if (targetDirectory == null) facet.getPackageForApplication() else facet.getPackageForPath(moduleTemplates, targetDirectory)
    val templateModel = fromFacet(
      facet, TemplateHandle(templateFile!!), initialPackageSuggestion, moduleTemplates[0], "New $activityDescription",
      DefaultProjectSyncInvoker(), shouldOpenFiles
    )
    val newActivity = TemplateResolver.getAllTemplates()
      .filter { WizardUiContext.MenuEntry in it.uiContexts }
      .find { it.name == templateName }

    val useNewActivity = StudioFlags.NPW_NEW_ACTIVITY_TEMPLATES.get() && newActivity != null

    if (useNewActivity) {
      templateModel.templateHandle = null
      templateModel.newTemplate = newActivity!!
    }

    val dialogTitle = AndroidBundle.message(
      if (isActivityTemplate) "android.wizard.new.activity.title" else "android.wizard.new.component.title"
    )
    val stepTitle = AndroidBundle.message(
      if (isActivityTemplate) "android.wizard.config.activity.title" else "android.wizard.config.component.title"
    )
    val wizardBuilder = ModelWizard.Builder().apply {
      addStep(if (useNewActivity) ConfigureTemplateParametersStep2(templateModel, stepTitle, moduleTemplates)
              else ConfigureTemplateParametersStep(templateModel, stepTitle, moduleTemplates)
      )
    }
    StudioWizardDialogBuilder(wizardBuilder.build(), dialogTitle).setProject(module.project).build().show()
    e.dataContext.getData(CREATED_FILES)?.addAll(templateModel.createdFiles)
    // TODO: Implement the getCreatedElements call for the wizard
    // dialog.createdElements.forEach { view.selectElement(it) }
  }

  companion object {
    private fun useAndroidX(module: Module?) = module != null && module.project.hasAndroidxProperty() && module.project.isAndroidx()
  }
}