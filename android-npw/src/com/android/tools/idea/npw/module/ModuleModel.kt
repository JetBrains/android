/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.npw.module

import com.android.SdkConstants
import com.android.annotations.concurrency.UiThread
import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate.createDefaultModuleTemplate
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate.createSampleTemplate
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate.getModuleRootForNewModule
import com.android.tools.idea.npw.model.ModuleModelData
import com.android.tools.idea.npw.model.MultiTemplateRenderer
import com.android.tools.idea.npw.model.NewAndroidModuleModel
import com.android.tools.idea.npw.model.ProjectModelData
import com.android.tools.idea.npw.model.TemplateMetrics
import com.android.tools.idea.npw.model.moduleTemplateRendererToModuleType
import com.android.tools.idea.npw.model.render
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.npw.template.ModuleTemplateDataBuilder
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.ObjectProperty
import com.android.tools.idea.observable.core.ObjectValueProperty
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.projectsystem.NamedModuleTemplate
import com.android.tools.idea.templates.TemplateUtils
import com.android.tools.idea.templates.recipe.DefaultRecipeExecutor
import com.android.tools.idea.templates.recipe.FindReferencesRecipeExecutor
import com.android.tools.idea.templates.recipe.RenderingContext
import com.android.tools.idea.wizard.model.WizardModel
import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.Recipe
import com.android.tools.idea.wizard.template.ViewBindingSupport
import com.android.utils.FileUtils
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplatesUsage.TemplateComponent.TemplateType.NO_ACTIVITY
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplatesUsage.TemplateComponent.WizardUiContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbService
import java.io.File
import java.io.IOException

private val log: Logger get() = logger<ModuleModel>()

abstract class ModuleModel(
  name: String,
  private val commandName: String = "New Module",
  override val isLibrary: Boolean,
  projectModelData: ProjectModelData,
  _template: NamedModuleTemplate = with(projectModelData) {
    if (isNewProject) createSampleTemplate() else createDefaultModuleTemplate(project, name)
  },
  val moduleParent: String,
  override val wizardContext: WizardUiContext
) : WizardModel(), ProjectModelData by projectModelData, ModuleModelData {
  final override val template: ObjectProperty<NamedModuleTemplate> = ObjectValueProperty(_template)
  override val formFactor: ObjectProperty<FormFactor> = ObjectValueProperty(FormFactor.Mobile)
  override val category: ObjectProperty<Category> = ObjectValueProperty(Category.Activity)
  final override val moduleName = StringValueProperty(name).apply { addConstraint(String::trim) }
  override val androidSdkInfo = OptionalValueProperty<AndroidVersionsInfo.VersionItem>()
  override val moduleTemplateDataBuilder = ModuleTemplateDataBuilder(
    projectTemplateDataBuilder = projectTemplateDataBuilder,
    isNewModule = true,
    viewBindingSupport = projectModelData.viewBindingSupport.getValueOr(ViewBindingSupport.SUPPORTED_4_0_MORE)
  )
  abstract val renderer: MultiTemplateRenderer.TemplateRenderer
  override val viewBindingSupport = projectModelData.viewBindingSupport
  override val sendModuleMetrics: BoolValueProperty = BoolValueProperty(true)

  public override fun handleFinished() {
    multiTemplateRenderer.requestRender(renderer)
  }

  override fun handleSkipped() {
    multiTemplateRenderer.skipRender()
  }

  abstract inner class ModuleTemplateRenderer : MultiTemplateRenderer.TemplateRenderer {
    /**
     * A [Recipe] which should be run from [render].
     */
    protected abstract val recipe: Recipe

    private var success = false
    private val createdFiles: MutableList<File> = arrayListOf()

    @WorkerThread
    override fun init() {
      moduleTemplateDataBuilder.apply {
        projectTemplateDataBuilder.apply {
          setProjectDefaults(project)
          language = this@ModuleModel.language.value
        }
        formFactor = this@ModuleModel.formFactor.get()
        category = this@ModuleModel.category.get()
        setBuildVersion(androidSdkInfo.value, project)
        setModuleRoots(template.get().paths, project.basePath!!, moduleName.get(), this@ModuleModel.packageName.get())
        isLibrary = this@ModuleModel.isLibrary
      }
    }

    @WorkerThread
    override fun doDryRun(): Boolean {
      // Returns false if there was a render conflict and the user chose to cancel creating the template
      return renderTemplate(true)
    }

    @WorkerThread
    override fun render() {
      success = WriteCommandAction.writeCommandAction(project).withName(commandName).compute<Boolean, Exception> {
        renderTemplate(false)
      }

      if (!success) {
        log.warn("A problem occurred while creating a new Module. Please check the log file for possible errors.")
      }
    }

    @UiThread
    override fun finish() {
      if (success) {
        DumbService.getInstance(project).smartInvokeLater { TemplateUtils.openEditors(project, createdFiles, true) }
      }
    }

    protected open fun renderTemplate(dryRun: Boolean): Boolean {
      val moduleRoot = getModuleRootForNewModule(project.basePath!!, moduleName.get())
      val context = RenderingContext(
        project = project,
        module = null,
        commandName = commandName,
        templateData = moduleTemplateDataBuilder.build(),
        moduleRoot = moduleRoot,
        dryRun = dryRun,
        showErrors = true
      )

      // TODO(qumeric) We should really only have one root - Update RenderingContext2 to get it from templateData?
      // assert(moduleRoot == (context.templateData as ModuleTemplateData).rootDir)

      val metrics = if (!dryRun && sendModuleMetrics.get()) {
        TemplateMetrics(
          templateType = NO_ACTIVITY,
          wizardContext = wizardContext,
          moduleType = moduleTemplateRendererToModuleType(loggingEvent),
          minSdk = androidSdkInfo.valueOrNull?.minApiLevel ?: 0,
          bytecodeLevel = (this@ModuleModel as? NewAndroidModuleModel)?.bytecodeLevel?.valueOrNull,
          useGradleKts = useGradleKts.get(),
          useAppCompat = false
        )
      } else null

      val executor = if (dryRun) FindReferencesRecipeExecutor(context) else DefaultRecipeExecutor(context)

      if (StudioFlags.NPW_ENABLE_GRADLE_VERSION_CATALOG.get() && isNewProject) {
        // Create a conventional default toml file for the new project because GradleVersionCatalogModel expects
        // the toml file already exists. This needs to be before start rendering the template.
        WriteCommandAction.writeCommandAction(project).run<IOException> {
          executor.copy(
            File(FileUtils.join("fileTemplates", "internal", "Version Catalog File.versions.toml.ft")),
            File(project.basePath, FileUtils.join("gradle", SdkConstants.FN_VERSION_CATALOG)))
          if (executor is DefaultRecipeExecutor) {
            executor.applyChanges()
          }
        }
      }
      return recipe.render(context, executor, loggingEvent, metrics).also {
        if (!dryRun) {
          createdFiles.addAll(context.filesToOpen)
        }
      }
    }
  }
}
