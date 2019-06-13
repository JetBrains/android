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
import com.android.tools.idea.npw.model.RenderTemplateModel.Companion.getInitialSourceLanguage
import com.android.tools.idea.npw.platform.Language
import com.android.tools.idea.npw.template.TemplateValueInjector
import com.android.tools.idea.observable.AbstractProperty
import com.android.tools.idea.observable.BatchInvoker.INVOKE_IMMEDIATELY_STRATEGY
import com.android.tools.idea.observable.BindingsManager
import com.android.tools.idea.observable.core.BoolProperty
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.OptionalProperty
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.android.tools.idea.observable.core.StringProperty
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.templates.Template
import com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_LIBRARY_MODULE
import com.android.tools.idea.templates.TemplateUtils
import com.android.tools.idea.templates.recipe.RenderingContext
import com.android.tools.idea.wizard.model.WizardModel
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import org.jetbrains.android.util.AndroidBundle.message
import java.io.File
import java.util.ArrayList
import java.util.HashMap

class NewModuleModel : WizardModel {
  val isLibrary: BoolProperty = BoolValueProperty()
  val renderTemplateValues: OptionalProperty<MutableMap<String, Any>> = OptionalValueProperty()
  val templateValues: MutableMap<String, Any> = hashMapOf()
  val project: OptionalProperty<Project>
  val projectSyncInvoker: ProjectSyncInvoker
  val multiTemplateRenderer: MultiTemplateRenderer

  // Note: INVOKE_IMMEDIATELY otherwise Objects may be constructed in the wrong state
  private val bindings = BindingsManager(INVOKE_IMMEDIATELY_STRATEGY)
  val moduleName = StringValueProperty()
  val splitName = StringValueProperty("feature")
  // A template that's associated with a user's request to create a new module. This may be null if the user skips creating a
  // module, or instead modifies an existing module (for example just adding a new Activity)
  val templateFile = OptionalValueProperty<File>()
  val applicationName: StringProperty
  val projectLocation: StringProperty
  val packageName = StringValueProperty()
  private val projectPackageName: StringProperty
  val enableCppSupport: BoolProperty
  val language: OptionalValueProperty<Language>
  private val createInExistingProject: Boolean

  init { // Default init constructor
    moduleName.addConstraint(AbstractProperty.Constraint(String::trim))
    splitName.addConstraint(AbstractProperty.Constraint(String::trim))
  }

  constructor(project: Project,
              projectSyncInvoker: ProjectSyncInvoker) {
    this.project = OptionalValueProperty(project)
    this.projectSyncInvoker = projectSyncInvoker
    projectPackageName = packageName
    createInExistingProject = true
    enableCppSupport = BoolValueProperty()
    language = OptionalValueProperty(getInitialSourceLanguage(project))
    applicationName = StringValueProperty(message("android.wizard.module.config.new.application"))
    applicationName.addConstraint(AbstractProperty.Constraint(String::trim))
    projectLocation = StringValueProperty(project.basePath!!)
    isLibrary.addListener { updateApplicationName() }
    multiTemplateRenderer = MultiTemplateRenderer(project, projectSyncInvoker)
  }

  constructor(projectModel: NewProjectModel, templateFile: File) {
    project = projectModel.project()
    projectPackageName = projectModel.packageName()
    projectSyncInvoker = projectModel.projectSyncInvoker
    createInExistingProject = false
    enableCppSupport = projectModel.enableCppSupport()
    applicationName = projectModel.applicationName()
    projectLocation = projectModel.projectLocation()
    this.templateFile.value = templateFile
    multiTemplateRenderer = projectModel.multiTemplateRenderer
    multiTemplateRenderer.incrementRenders()
    language = OptionalValueProperty()

    bindings.bind(packageName, projectPackageName)
  }

  override fun dispose() {
    super.dispose()
    bindings.releaseAll()
  }

  /**
   * This method should be called if there is no "Activity Render Template" step (For example when creating a Library, or the activity
   * creation is skipped by the user)
   */
  fun setDefaultRenderTemplateValues(renderModel: RenderTemplateModel, project: Project?) {
    val renderTemplateValues = mutableMapOf<String, Any>()
    TemplateValueInjector(renderTemplateValues)
      .setBuildVersion(renderModel.androidSdkInfo.value, project)
      .setModuleRoots(renderModel.template.get().paths, project!!.basePath!!, moduleName.get(), packageName.get())

    this.renderTemplateValues.value = renderTemplateValues
  }

  public override fun handleFinished() {
    multiTemplateRenderer.requestRender(ModuleTemplateRenderer())
  }

  override fun handleSkipped() {
    multiTemplateRenderer.skipRender()
  }

  private inner class ModuleTemplateRenderer : MultiTemplateRenderer.TemplateRenderer {
    internal var myTemplateValues: MutableMap<String, Any> = HashMap(this@NewModuleModel.templateValues)

    @WorkerThread
    override fun doDryRun(): Boolean {
      if (templateFile.valueOrNull == null) {
        return false // If here, the user opted to skip creating any module at all, or is just adding a new Activity
      }

      // By the time we run handleFinished(), we must have a Project
      if (!project.get().isPresent) {
        log.error("NewModuleModel did not collect expected information and will not complete. Please report this error.")
        return false
      }

      val renderTemplateValues = renderTemplateValues.valueOrNull

      myTemplateValues[ATTR_IS_LIBRARY_MODULE] = isLibrary.get()

      val project = project.value

      if (renderTemplateValues != null) {
        if (language.get().isPresent) { // For new Projects, we have a different UI, so no Language should be present
          TemplateValueInjector(renderTemplateValues).setLanguage(language.value)
        }
        myTemplateValues.putAll(renderTemplateValues)
      }

      // Returns false if there was a render conflict and the user chose to cancel creating the template
      return renderModule(true, myTemplateValues, project, moduleName.get())
    }

    @WorkerThread
    override fun render() {
      val project = project.value

      val success = WriteCommandAction.writeCommandAction(project).withName("New Module").compute<Boolean, Exception> {
        renderModule(false, myTemplateValues, project, moduleName.get())
      }

      if (!success) {
        log.warn("A problem occurred while creating a new Module. Please check the log file for possible errors.")
      }
    }

    private fun renderModule(dryRun: Boolean, templateState: Map<String, Any>, project: Project,
                             moduleName: String): Boolean {
      val projectRoot = File(project.basePath!!)
      val moduleRoot = getModuleRoot(project.basePath!!, moduleName)
      val template = Template.createFromPath(templateFile.value)
      val filesToOpen = ArrayList<File>()

      val context = RenderingContext.Builder.newContext(template, project)
        .withCommandName("New Module")
        .withDryRun(dryRun)
        .withShowErrors(true)
        .withOutputRoot(projectRoot)
        .withModuleRoot(moduleRoot)
        .intoOpenFiles(filesToOpen)
        .withParams(templateState)
        .build()

      val renderResult = template.render(context, dryRun)
      if (renderResult && !dryRun) {
        // calling smartInvokeLater will make sure that files are open only when the project is ready
        DumbService.getInstance(project).smartInvokeLater { TemplateUtils.openEditors(project, filesToOpen, false) }
      }

      return renderResult
    }
  }

  private fun updateApplicationName() {
    val msgId: String = when {
      isLibrary.get() -> "android.wizard.module.config.new.library"
      else -> "android.wizard.module.config.new.application"
    }
    applicationName.set(message(msgId))
  }

  companion object {
    // Module names may use ":" for sub folders. This mapping is only true when creating new modules, as the user can later customize
    // the Module Path (called Project Path in gradle world) in "settings.gradle"
    @JvmStatic
    fun getModuleRoot(projectLocation: String, moduleName: String): File =
      File(projectLocation, moduleName.replace(':', File.separatorChar))

    private val log: Logger
      get() = Logger.getInstance(NewModuleModel::class.java)
  }
}
