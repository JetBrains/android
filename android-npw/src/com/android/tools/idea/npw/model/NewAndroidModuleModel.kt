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

import com.android.SdkConstants.DOT_KTS
import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate.createSampleTemplate
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.npw.model.RenderTemplateModel.Companion.getInitialSourceLanguage
import com.android.tools.idea.npw.module.ModuleModel
import com.android.tools.idea.npw.module.recipes.androidModule.generateAndroidModule
import com.android.tools.idea.npw.module.recipes.automotiveModule.generateAutomotiveModule
import com.android.tools.idea.npw.module.recipes.genericModule.generateGenericModule
import com.android.tools.idea.npw.module.recipes.tvModule.generateTvModule
import com.android.tools.idea.npw.module.recipes.wearModule.generateWearModule
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.npw.template.ModuleTemplateDataBuilder
import com.android.tools.idea.npw.template.ProjectTemplateDataBuilder
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.ObjectProperty
import com.android.tools.idea.observable.core.ObjectValueProperty
import com.android.tools.idea.observable.core.OptionalProperty
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.projectsystem.NamedModuleTemplate
import com.android.tools.idea.wizard.template.BytecodeLevel
import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.Recipe
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.ViewBindingSupport
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplatesUsage.TemplateComponent.WizardUiContext
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplatesUsage.TemplateComponent.WizardUiContext.NEW_MODULE
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import org.jetbrains.android.util.AndroidBundle.message
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplateRenderer as RenderLoggingEvent

class ExistingProjectModelData(
  override var project: Project,
  override val projectSyncInvoker: ProjectSyncInvoker = ProjectSyncInvoker.DefaultProjectSyncInvoker()
) : ProjectModelData {
  override val applicationName: StringValueProperty = StringValueProperty()
  override val packageName: StringValueProperty = StringValueProperty()
  override val projectLocation: StringValueProperty = StringValueProperty(project.basePath!!)
  override val useGradleKts = BoolValueProperty(project.hasKtsUsage())
  override val viewBindingSupport = OptionalValueProperty<ViewBindingSupport>(project.isViewBindingSupported())
  override val isNewProject = false
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
  val formFactor: ObjectProperty<FormFactor>
  val category: ObjectProperty<Category>
  val isLibrary: Boolean
  val moduleName: StringValueProperty

  /**
   * A template that's associated with a user's request to create a new module. This may be null if the user skips creating a
   * module, or instead modifies an existing module (for example just adding a new Activity)
   */
  val androidSdkInfo: OptionalProperty<AndroidVersionsInfo.VersionItem>
  val moduleTemplateDataBuilder: ModuleTemplateDataBuilder

  /**
   * A value which will be logged for Studio usage tracking.
   */
  val loggingEvent: RenderLoggingEvent

  val wizardContext: WizardUiContext

  /**
   * Modules with a component Render that sends metrics, should set this value to false (otherwise metrics will be duplicated).
   * Modules without any Component Render or that have a "No Activity" selected, should leave this field set to true.
   **/
  val sendModuleMetrics: BoolValueProperty
}

class NewAndroidModuleModel(
  projectModelData: ProjectModelData,
  template: NamedModuleTemplate,
  moduleParent: String,
  override val formFactor: ObjectProperty<FormFactor>,
  override val category: ObjectProperty<Category>,
  commandName: String = "New Module",
  override val isLibrary: Boolean = false,
  wizardContext: WizardUiContext
) : ModuleModel(
  "",
  commandName,
  isLibrary,
  projectModelData,
  template,
  moduleParent,
  wizardContext
) {
  override val moduleTemplateDataBuilder = ModuleTemplateDataBuilder(
    projectTemplateDataBuilder = projectTemplateDataBuilder,
    isNewModule = true,
    viewBindingSupport = viewBindingSupport.getValueOr(ViewBindingSupport.SUPPORTED_4_0_MORE))
  override val renderer = ModuleTemplateRenderer()

  val bytecodeLevel: OptionalProperty<BytecodeLevel> = OptionalValueProperty(getInitialBytecodeLevel())

  init {
    if (applicationName.isEmpty.get()) {
      val msg: String = when {
        isLibrary -> "My Library"
        else -> "My Application"
      }
      applicationName.set(msg)
    }
  }

  override val loggingEvent: AndroidStudioEvent.TemplateRenderer
    get() = formFactor.get().toModuleRenderingLoggingEvent()

  inner class ModuleTemplateRenderer : ModuleModel.ModuleTemplateRenderer() {
    override val recipe: Recipe get() = when(formFactor.get()) {
      FormFactor.Mobile -> { data: TemplateData ->
        generateAndroidModule(
          data = data as ModuleTemplateData,
          appTitle = applicationName.get(),
          useKts = useGradleKts.get(),
          bytecodeLevel = bytecodeLevel.value)
      }
      FormFactor.Wear -> { data: TemplateData ->
        generateWearModule(data as ModuleTemplateData, applicationName.get(), useGradleKts.get())
      }
      FormFactor.Automotive -> { data: TemplateData ->
        generateAutomotiveModule(data as ModuleTemplateData, applicationName.get(), useGradleKts.get())
      }
      FormFactor.Tv -> { data: TemplateData ->
        generateTvModule(data as ModuleTemplateData, applicationName.get(), useGradleKts.get())
      }
      FormFactor.Generic -> { data: TemplateData ->
        generateGenericModule(data as ModuleTemplateData)
      }
    }

    @WorkerThread
    override fun init() {
      super.init()

      moduleTemplateDataBuilder.apply {
        setModuleRoots(template.get().paths, project.basePath!!, moduleName.get(), this@NewAndroidModuleModel.packageName.get())
      }
      val tff = formFactor.get()
      projectTemplateDataBuilder.includedFormFactorNames.putIfAbsent(tff, mutableListOf(moduleName.get()))?.add(moduleName.get())
    }
  }

  override fun handleFinished() {
    super.handleFinished()
    saveWizardState()
  }

  private fun getInitialBytecodeLevel(): BytecodeLevel {
    if (isLibrary) {
      val savedValue = properties.getValue(PROPERTIES_BYTECODE_LEVEL_KEY)
      return BytecodeLevel.values().firstOrNull { it.toString() == savedValue } ?: BytecodeLevel.default
    }
    return BytecodeLevel.default
  }

  private fun saveWizardState() = with(properties) {
    if (isLibrary) {
      setValue(PROPERTIES_BYTECODE_LEVEL_KEY, bytecodeLevel.value.toString())
    }
  }

  companion object {
    fun fromExistingProject(
      project: Project,
      moduleParent: String,
      projectSyncInvoker: ProjectSyncInvoker,
      formFactor: FormFactor,
      category: Category,
      isLibrary: Boolean = false
    ) : NewAndroidModuleModel = NewAndroidModuleModel(
      projectModelData = ExistingProjectModelData(project, projectSyncInvoker),
      template = createSampleTemplate(),
      moduleParent = moduleParent,
      formFactor = ObjectValueProperty(formFactor),
      category = ObjectValueProperty(category),
      isLibrary = isLibrary,
      wizardContext = NEW_MODULE
    )
  }
}

private fun FormFactor.toModuleRenderingLoggingEvent() = when(this) {
  FormFactor.Mobile -> RenderLoggingEvent.ANDROID_MODULE
  FormFactor.Tv -> RenderLoggingEvent.ANDROID_TV_MODULE
  FormFactor.Automotive -> RenderLoggingEvent.AUTOMOTIVE_MODULE
  FormFactor.Wear -> RenderLoggingEvent.ANDROID_WEAR_MODULE
  FormFactor.Generic -> RenderLoggingEvent.ANDROID_MODULE // TODO(b/145975555)
}

internal fun Project.hasKtsUsage() : Boolean {
  return GradleUtil.projectBuildFilesTypes(this).contains(DOT_KTS)
}

internal fun Project.isViewBindingSupported(): ViewBindingSupport {
  val androidPluginInfo = AndroidPluginInfo.findFromModel(this) ?: return ViewBindingSupport.SUPPORTED_4_0_MORE
  val agpVersion = androidPluginInfo.pluginVersion ?: return ViewBindingSupport.SUPPORTED_4_0_MORE
  return when {
    agpVersion.isAtLeast(4, 0, 0) -> ViewBindingSupport.SUPPORTED_4_0_MORE
    agpVersion.isAtLeast(3, 6, 0) -> ViewBindingSupport.SUPPORTED_3_6
    else -> ViewBindingSupport.NOT_SUPPORTED
  }
}
