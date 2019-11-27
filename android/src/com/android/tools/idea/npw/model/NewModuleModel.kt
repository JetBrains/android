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
package com.android.tools.idea.npw.model

import com.android.annotations.concurrency.WorkerThread
import com.android.sdklib.AndroidVersion.VersionCodes.P
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.npw.FormFactor
import com.android.tools.idea.npw.model.RenderTemplateModel.Companion.getInitialSourceLanguage
import com.android.tools.idea.npw.module.NewAndroidModuleRecipe
import com.android.tools.idea.npw.module.getModuleRoot
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.npw.platform.Language
import com.android.tools.idea.npw.template.TemplateValueInjector
import com.android.tools.idea.observable.core.BoolProperty
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.ObjectProperty
import com.android.tools.idea.observable.core.ObjectValueProperty
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.projectsystem.NamedModuleTemplate
import com.android.tools.idea.templates.ModuleTemplateDataBuilder
import com.android.tools.idea.templates.ProjectTemplateDataBuilder
import com.android.tools.idea.templates.Template
import com.android.tools.idea.templates.TemplateAttributes.ATTR_APP_TITLE
import com.android.tools.idea.templates.TemplateAttributes.ATTR_BUILD_API
import com.android.tools.idea.templates.TemplateAttributes.ATTR_INCLUDE_FORM_FACTOR
import com.android.tools.idea.templates.TemplateAttributes.ATTR_IS_LIBRARY_MODULE
import com.android.tools.idea.templates.TemplateAttributes.ATTR_MODULE_NAME
import com.android.tools.idea.templates.TemplateUtils
import com.android.tools.idea.templates.recipe.DefaultRecipeExecutor2
import com.android.tools.idea.templates.recipe.FindReferencesRecipeExecutor2
import com.android.tools.idea.templates.recipe.RenderingContext
import com.android.tools.idea.templates.recipe.RenderingContext2
import com.android.tools.idea.wizard.model.WizardModel
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import org.jetbrains.android.util.AndroidBundle.message
import java.io.File
import java.util.ArrayList

private val log: Logger get() = logger<NewModuleModel>()

class ExistingProjectModelData(
  override var project: Project,
  override val projectSyncInvoker: ProjectSyncInvoker
) : ProjectModelData {
  override val applicationName: StringValueProperty = StringValueProperty(message("android.wizard.module.config.new.application"))
  override val packageName: StringValueProperty = StringValueProperty()
  override val projectLocation: StringValueProperty = StringValueProperty(project.basePath!!)
  override val enableCppSupport: BoolValueProperty = BoolValueProperty()
  override val cppFlags: StringValueProperty = StringValueProperty("")
  override val useAppCompat = BoolValueProperty()
  override val isNewProject = false
  override val projectTemplateValues: MutableMap<String, Any> = mutableMapOf()
  override val language: OptionalValueProperty<Language> = OptionalValueProperty(getInitialSourceLanguage(project))
  override val multiTemplateRenderer: MultiTemplateRenderer = MultiTemplateRenderer { renderer ->
    object : Task.Modal(project, message("android.compile.messages.generating.r.java.content.name"), false) {
      override fun run(indicator: ProgressIndicator) {
        renderer(project)
      }
    }.queue()
    projectSyncInvoker.syncProject(project)
  }
  override val projectTemplateDataBuilder = ProjectTemplateDataBuilder(false)

  init {
    applicationName.addConstraint(String::trim)
  }
}

interface ModuleModelData : ProjectModelData {
  val template: ObjectProperty<NamedModuleTemplate>
  val formFactor: ObjectValueProperty<FormFactor>
  val isLibrary: BoolProperty
  val moduleTemplateValues: MutableMap<String, Any>
  val moduleName: StringValueProperty
  /**
   * A template that's associated with a user's request to create a new module. This may be null if the user skips creating a
   * module, or instead modifies an existing module (for example just adding a new Activity)
   */
  val templateFile: OptionalValueProperty<File>
  /**
   * Used in place of [templateFile] if [StudioFlags.NPW_NEW_MODULE_TEMPLATES] is enabled.
   */
  var moduleRecipe: NewAndroidModuleRecipe?
  val androidSdkInfo: OptionalValueProperty<AndroidVersionsInfo.VersionItem>
  val moduleTemplateDataBuilder: ModuleTemplateDataBuilder
}

class NewModuleModel(
  private val projectModelData: ProjectModelData,
  override val template: ObjectProperty<NamedModuleTemplate>,
  val moduleParent: String?,
  override val formFactor: ObjectValueProperty<FormFactor>,
  private val commandName: String = "New Module"
) : WizardModel(), ProjectModelData by projectModelData, ModuleModelData {
  override val isLibrary: BoolProperty = BoolValueProperty()
  override val moduleTemplateValues = mutableMapOf<String, Any>()
  override val moduleName = StringValueProperty().apply { addConstraint(String::trim) }
  override val templateFile = OptionalValueProperty<File>()
  override var moduleRecipe: NewAndroidModuleRecipe? = null
  override val androidSdkInfo: OptionalValueProperty<AndroidVersionsInfo.VersionItem> = OptionalValueProperty()
  override val moduleTemplateDataBuilder = ModuleTemplateDataBuilder(projectTemplateDataBuilder)

  // TODO(qumeric): replace constructors by factories
  constructor(
    project: Project, moduleParent: String?, projectSyncInvoker: ProjectSyncInvoker, template: NamedModuleTemplate
  ) : this(
    projectModelData = ExistingProjectModelData(project, projectSyncInvoker),
    template = ObjectValueProperty(template),
    moduleParent = moduleParent,
    formFactor = ObjectValueProperty(FormFactor.MOBILE)
  ) {
    isLibrary.addListener { updateApplicationName() }
  }

  constructor(
    projectModel: NewProjectModel, templateFile: File?, recipe: NewAndroidModuleRecipe?, template: NamedModuleTemplate,
    formFactor: ObjectValueProperty<FormFactor> = ObjectValueProperty(FormFactor.MOBILE)
  ) : this(
    projectModelData = projectModel,
    template = ObjectValueProperty(template),
    moduleParent = null,
    formFactor = formFactor
  ) {
    if (templateFile != null) {
      this.templateFile.value = templateFile
    }
    if (recipe != null) {
      moduleRecipe = recipe
    }
    multiTemplateRenderer.incrementRenders()
  }

  public override fun handleFinished() {
    multiTemplateRenderer.requestRender(ModuleTemplateRenderer())
  }

  override fun handleSkipped() {
    multiTemplateRenderer.skipRender()
  }

  private inner class ModuleTemplateRenderer : MultiTemplateRenderer.TemplateRenderer {
    @WorkerThread
    override fun init() {
      if (StudioFlags.NPW_NEW_MODULE_TEMPLATES.get()) {
        moduleTemplateDataBuilder.apply {
          formFactor = this@NewModuleModel.formFactor.get().toTemplateFormFactor()
          isNew = true
          isLibrary = this@NewModuleModel.isLibrary.get()
          projectTemplateDataBuilder.setProjectDefaults(project)
          setModuleRoots(template.get().paths, project.basePath!!, moduleName.get(), this@NewModuleModel.packageName.get())
          if (androidSdkInfo.isPresent.get()) {
            setBuildVersion(androidSdkInfo.value, project)
          }
          if (language.get().isPresent) { // For new Projects, we have a different UI, so no Language should be present
            projectTemplateDataBuilder.language = language.value
          }
        }
        val tff = formFactor.get().toTemplateFormFactor()
        projectTemplateDataBuilder.includedFormFactorNames.putIfAbsent(tff, mutableListOf(moduleName.get()))?.add(moduleName.get())
      }

      // TODO(qumeric): let project know about formFactors (it is being rendered before NewModuleModel.init runs)
      projectTemplateValues.also {
        it[formFactor.get().id + ATTR_INCLUDE_FORM_FACTOR] = true
        it[formFactor.get().id + ATTR_MODULE_NAME] = moduleName.get()
      }

      moduleTemplateValues[ATTR_APP_TITLE] = applicationName.get()
      moduleTemplateValues[ATTR_IS_LIBRARY_MODULE] = isLibrary.get()

      TemplateValueInjector(moduleTemplateValues).apply {
        setProjectDefaults(project, isNewProject)
        setModuleRoots(template.get().paths, project.basePath!!, moduleName.get(), packageName.get())
        if (androidSdkInfo.isPresent.get()) {
          setBuildVersion(androidSdkInfo.value, project, isNewProject)
        }
        if (language.get().isPresent) { // For new Projects, we have a different UI, so no Language should be present
          setLanguage(language.value)
        }
      }

      if (useAppCompat.get()) {
        // The highest supported/recommended appCompact version is P(28)
        moduleTemplateValues[ATTR_BUILD_API] = androidSdkInfo.value.buildApiLevel.coerceAtMost(P)
      }

      moduleTemplateValues.putAll(projectTemplateValues)
    }

    @WorkerThread
    override fun doDryRun(): Boolean {
      // This is done because module needs to know about all included form factors, and currently we know about them only after init run,
      // so we need to set it again after all inits (thus in dryRun) TODO(qumeric): remove after adding formFactors to the project
      moduleTemplateValues.putAll(projectTemplateValues)
      if (templateFile.valueOrNull == null && moduleRecipe == null) {
        return false // If here, the user opted to skip creating any module at all, or is just adding a new Activity
      }

      // Returns false if there was a render conflict and the user chose to cancel creating the template
      return renderModule(true)
    }

    @WorkerThread
    override fun render() {
      val success = WriteCommandAction.writeCommandAction(project).withName(commandName).compute<Boolean, Exception> {
        renderModule(false)
      }

      if (!success) {
        log.warn("A problem occurred while creating a new Module. Please check the log file for possible errors.")
      }
    }

    private fun renderModule(dryRun: Boolean): Boolean {
      val projectRoot = File(project.basePath!!)
      val moduleRoot = getModuleRoot(project.basePath!!, moduleName.get())
      val filesToOpen = ArrayList<File>()

      if (moduleRecipe != null) {
        val context = RenderingContext2(
          project = project,
          module = null,
          commandName = commandName,
          templateData = moduleTemplateDataBuilder.build(),
          moduleRoot = moduleRoot,
          dryRun = dryRun,
          showErrors = true
        )

        val executor = if (dryRun) FindReferencesRecipeExecutor2(context) else DefaultRecipeExecutor2(context)

        return moduleRecipe!!(applicationName.get()).doRender(context, executor)
      }

      val template = Template.createFromPath(templateFile.value)
      val context = RenderingContext.Builder.newContext(template, project)
        .withCommandName(commandName)
        .withDryRun(dryRun)
        .withShowErrors(true)
        .withOutputRoot(projectRoot)
        .withModuleRoot(moduleRoot)
        .intoOpenFiles(filesToOpen)
        .withParams(moduleTemplateValues)
        .build()

      return template.render(context, dryRun).also {
        if (it && !dryRun) {
          // calling smartInvokeLater will make sure that files are open only when the project is ready
          DumbService.getInstance(project).smartInvokeLater { TemplateUtils.openEditors(project, filesToOpen, false) }
        }
      }
    }
  }

  private fun updateApplicationName() {
    val msgId: String = when {
      isLibrary.get() -> "android.wizard.module.config.new.library"
      else -> "android.wizard.module.config.new.application"
    }
    applicationName.set(message(msgId))
  }
}
