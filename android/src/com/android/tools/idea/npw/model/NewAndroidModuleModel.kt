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
import com.android.tools.idea.npw.module.ModuleModel
import com.android.tools.idea.npw.module.recipes.androidModule.generateAndroidModule
import com.android.tools.idea.npw.module.recipes.automotiveModule.generateAutomotiveModule
import com.android.tools.idea.npw.module.recipes.thingsModule.generateThingsModule
import com.android.tools.idea.npw.module.recipes.tvModule.generateTvModule
import com.android.tools.idea.npw.module.recipes.wearModule.generateWearModule
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.npw.platform.Language
import com.android.tools.idea.npw.template.TemplateValueInjector
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.ObjectProperty
import com.android.tools.idea.observable.core.ObjectValueProperty
import com.android.tools.idea.observable.core.OptionalProperty
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.projectsystem.NamedModuleTemplate
import com.android.tools.idea.templates.ModuleTemplateDataBuilder
import com.android.tools.idea.templates.ProjectTemplateDataBuilder
import com.android.tools.idea.templates.TemplateAttributes.ATTR_APP_TITLE
import com.android.tools.idea.templates.TemplateAttributes.ATTR_BUILD_API
import com.android.tools.idea.templates.TemplateAttributes.ATTR_INCLUDE_FORM_FACTOR
import com.android.tools.idea.templates.TemplateAttributes.ATTR_MODULE_NAME
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.Recipe
import com.android.tools.idea.wizard.template.TemplateData
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import org.jetbrains.android.util.AndroidBundle.message
import java.io.File
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplateRenderer as RenderLoggingEvent

class ExistingProjectModelData(
  override var project: Project,
  override val projectSyncInvoker: ProjectSyncInvoker = ProjectSyncInvoker.DefaultProjectSyncInvoker()
) : ProjectModelData {
  override val applicationName: StringValueProperty = StringValueProperty(message("android.wizard.module.config.new.application"))
  override val packageName: StringValueProperty = StringValueProperty()
  override val projectLocation: StringValueProperty = StringValueProperty(project.basePath!!)
  override val enableCppSupport: BoolValueProperty = BoolValueProperty()
  override val cppFlags: StringValueProperty = StringValueProperty("")
  override val useAppCompat = BoolValueProperty()
  override val useGradleKts = BoolValueProperty(project.hasKtsUsage())
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
  val formFactor: ObjectProperty<FormFactor>
  val isLibrary: Boolean
  val moduleTemplateValues: MutableMap<String, Any>
  val moduleName: StringValueProperty
  /**
   * A template that's associated with a user's request to create a new module. This may be null if the user skips creating a
   * module, or instead modifies an existing module (for example just adding a new Activity)
   */
  var templateFile: File?
  /**
   * Used in place of [templateFile] if [StudioFlags.NPW_NEW_MODULE_TEMPLATES] is enabled.
   */
  val androidSdkInfo: OptionalProperty<AndroidVersionsInfo.VersionItem>
  val moduleTemplateDataBuilder: ModuleTemplateDataBuilder
}

class NewAndroidModuleModel(
  projectModelData: ProjectModelData,
  template: NamedModuleTemplate,
  val moduleParent: String?,
  override val formFactor: ObjectProperty<FormFactor>,
  commandName: String = "New Module",
  override val isLibrary: Boolean = false,
  templateFile: File? = null
) : ModuleModel(
  templateFile,
  "",
  commandName,
  isLibrary,
  projectModelData,
  template
) {
  override val moduleTemplateValues = mutableMapOf<String, Any>()
  override val moduleTemplateDataBuilder = ModuleTemplateDataBuilder(projectTemplateDataBuilder)
  override val renderer = ModuleTemplateRenderer()

  init {
    val msgId: String = when {
      isLibrary -> "android.wizard.module.config.new.library"
      else -> "android.wizard.module.config.new.application"
    }
    applicationName.set(message(msgId))
  }

  // TODO(qumeric): replace constructors by factories
  constructor(
    project: Project,
    moduleParent: String?,
    projectSyncInvoker: ProjectSyncInvoker,
    template: NamedModuleTemplate,
    isLibrary: Boolean = false,
    templateFile: File? = null
  ) : this(
    projectModelData = ExistingProjectModelData(project, projectSyncInvoker),
    template = template,
    moduleParent = moduleParent,
    formFactor = ObjectValueProperty(FormFactor.MOBILE),
    isLibrary = isLibrary,
    templateFile = templateFile
  )

  constructor(
    projectModel: NewProjectModel, templateFile: File?, template: NamedModuleTemplate,
    formFactor: ObjectValueProperty<FormFactor> = ObjectValueProperty(FormFactor.MOBILE)
  ) : this(
    projectModelData = projectModel,
    template = template,
    moduleParent = null,
    formFactor = formFactor,
    templateFile = templateFile
  ) {
    multiTemplateRenderer.incrementRenders()
  }

  inner class ModuleTemplateRenderer : ModuleModel.ModuleTemplateRenderer() {
    override val recipe: Recipe get() = when(formFactor.get()) {
      FormFactor.MOBILE -> { data: TemplateData ->
        generateAndroidModule(data as ModuleTemplateData, applicationName.get(), enableCppSupport.get(), cppFlags.get())
      }
      FormFactor.WEAR -> { data: TemplateData -> generateWearModule(data as ModuleTemplateData, applicationName.get()) }
      FormFactor.AUTOMOTIVE -> { data: TemplateData -> generateAutomotiveModule(data as ModuleTemplateData, applicationName.get()) }
      FormFactor.TV -> { data: TemplateData -> generateTvModule(data as ModuleTemplateData, applicationName.get()) }
      FormFactor.THINGS -> { data: TemplateData -> generateThingsModule(data as ModuleTemplateData, applicationName.get()) }
    }

    override val loggingEvent: AndroidStudioEvent.TemplateRenderer
      get() = formFactor.get().toModuleRenderingLoggingEvent()

    @WorkerThread
    override fun init() {
      super.init()
      if (StudioFlags.NPW_NEW_MODULE_TEMPLATES.get()) {
        moduleTemplateDataBuilder.apply {
          setModuleRoots(template.get().paths, project.basePath!!, moduleName.get(), this@NewAndroidModuleModel.packageName.get())
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

      TemplateValueInjector(moduleTemplateValues).apply {
        setProjectDefaults(project, isNewProject)
        setModuleRoots(template.get().paths, project.basePath!!, moduleName.get(), packageName.get())
        setBuildVersion(androidSdkInfo.value, project, isNewProject)
      }

      if (useAppCompat.get()) {
        // The highest supported/recommended appCompact version is P(28)
        moduleTemplateValues[ATTR_BUILD_API] = androidSdkInfo.value.buildApiLevel.coerceAtMost(P)
      }

      moduleTemplateValues.putAll(projectTemplateValues)
    }
  }
}

private fun FormFactor.toModuleRenderingLoggingEvent() = when(this) {
  FormFactor.MOBILE -> RenderLoggingEvent.ANDROID_MODULE
  FormFactor.TV -> RenderLoggingEvent.ANDROID_TV_MODULE
  FormFactor.AUTOMOTIVE -> RenderLoggingEvent.AUTOMOTIVE_MODULE
  FormFactor.THINGS -> RenderLoggingEvent.THINGS_MODULE
  FormFactor.WEAR -> RenderLoggingEvent.ANDROID_WEAR_MODULE
}

private fun Project.hasKtsUsage() : Boolean {
  // TODO(parentej): Check if settings is kts or any module is kts
  return false
}
