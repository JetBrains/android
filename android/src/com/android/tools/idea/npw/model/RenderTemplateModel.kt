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

import com.android.annotations.concurrency.UiThread
import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.hasAnyKotlinModules
import com.android.tools.idea.npw.assetstudio.IconGenerator
import com.android.tools.idea.npw.platform.Language
import com.android.tools.idea.npw.project.AndroidPackageUtils
import com.android.tools.idea.npw.template.TemplateHandle
import com.android.tools.idea.npw.template.TemplateValueInjector
import com.android.tools.idea.observable.core.ObjectProperty
import com.android.tools.idea.observable.core.ObjectValueProperty
import com.android.tools.idea.observable.core.OptionalProperty
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.android.tools.idea.observable.core.StringProperty
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.projectsystem.AndroidModuleTemplate
import com.android.tools.idea.projectsystem.NamedModuleTemplate
import com.android.tools.idea.templates.Template
import com.android.tools.idea.templates.TemplateMetadata.ATTR_APPLICATION_PACKAGE
import com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_LAUNCHER
import com.android.tools.idea.templates.TemplateMetadata.ATTR_SOURCE_PROVIDER_NAME
import com.android.tools.idea.templates.TemplateUtils
import com.android.tools.idea.templates.recipe.RenderingContext
import com.android.tools.idea.wizard.model.WizardModel
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet
import java.io.File

private val log = logger<RenderTemplateModel>()

/**
 * A model responsible for instantiating a FreeMarker [Template] into the current project representing an Android component.
 */
class RenderTemplateModel private constructor(
  val project: OptionalProperty<Project>,
  val androidFacet: AndroidFacet?,
  var templateHandle: TemplateHandle? = null,
  val template: ObjectProperty<NamedModuleTemplate>,
  private val projectLocation: StringProperty,
  private val moduleName: StringProperty,
  /** The package name affects which paths the template's output will be rendered into. */
  val packageName: StringProperty,
  private val commandName: String,
  private val multiTemplateRenderer: MultiTemplateRenderer,
  private val shouldOpenFiles: Boolean,
  val language: ObjectProperty<Language> = languagePropertyFromProject(project.valueOrNull),
  /** Populated in [Template.render] */
  val createdFiles: MutableList<File> = arrayListOf(),
  val moduleTemplateValues: MutableMap<String, Any> = mutableMapOf()
) : WizardModel() {
  /**
   * The target template we want to render. If null, the user is skipping steps that would instantiate a template and this model shouldn't
   * try to render anything.
   */
  val templateValues = hashMapOf<String, Any>()
  var iconGenerator: IconGenerator? = null

  val module: Module?
    get() = androidFacet?.module

  public override fun handleFinished() {
    multiTemplateRenderer.requestRender(FreeMarkerTemplateRenderer())
  }

  public override fun handleSkipped() {
    multiTemplateRenderer.skipRender()
  }

  private inner class FreeMarkerTemplateRenderer : MultiTemplateRenderer.TemplateRenderer {
    internal val filesToReformat: MutableList<File> = mutableListOf()
    private var renderSuccess: Boolean = false

    @WorkerThread
    override fun init() {
      val paths = template.get().paths
      if (paths.moduleRoot == null) {
        log.error("RenderTemplateModel can't create files because module root is not found. Please report this error.")
        return
      }

      templateValues.putAll(moduleTemplateValues)

      templateValues[ATTR_SOURCE_PROVIDER_NAME] = template.get().name
      if (module == null) { // New Module
        templateValues[ATTR_IS_LAUNCHER] = true
      }

      val templateInjector = TemplateValueInjector(templateValues)
        .setModuleRoots(paths, projectLocation.get(), moduleName.get(), packageName.get())

      if (androidFacet == null) {
        return
      }
      templateInjector.setFacet(androidFacet)
      templateInjector.setLanguage(language.get()) // Note: For new projects/modules we have a different UI.

      // Register application-wide settings
      val applicationPackage = AndroidPackageUtils.getPackageForApplication(androidFacet)
      if (packageName.get() != applicationPackage) {
        templateValues[ATTR_APPLICATION_PACKAGE] = AndroidPackageUtils.getPackageForApplication(androidFacet)
      }
    }

    @WorkerThread
    override fun doDryRun(): Boolean {
      if (!project.get().isPresent || templateHandle == null) {
        log.error("RenderTemplateModel did not collect expected information and will not complete. Please report this error.")
        return false
      }

      return renderTemplate(true, project.value, template.get().paths, null, null)
    }

    @WorkerThread
    override fun render() {
      val paths = template.get().paths

      try {
        val success = renderTemplate(false, project.value, paths, createdFiles, filesToReformat)
        if (success) {
          iconGenerator?.generateIconsToDisk(paths)
        }
        renderSuccess = success
      }
      catch (t: Throwable) {
        log.warn(t)
      }
    }

    @UiThread
    override fun finish() {
      if (renderSuccess && shouldOpenFiles) {
        DumbService.getInstance(project.value).smartInvokeLater { TemplateUtils.openEditors(project.value, createdFiles, true) }
      }
    }

    private fun renderTemplate(dryRun: Boolean,
                               project: Project,
                               paths: AndroidModuleTemplate,
                               filesToOpen: MutableList<File>?,
                               filesToReformat: MutableList<File>?): Boolean {
      paths.moduleRoot ?: return false

      val template = templateHandle!!.template

      if (!dryRun && StudioFlags.NPW_DUMP_TEMPLATE_VARS.get() && filesToOpen != null) {
        toScratchFile(project)?.run {
          filesToOpen.add(VfsUtilCore.virtualToIoFile(this))
        }
      }

      val context = RenderingContext.Builder.newContext(template, project)
        .withCommandName(commandName)
        .withDryRun(dryRun)
        .withShowErrors(true)
        .withModuleRoot(paths.moduleRoot!!)
        .withModule(module)
        .withParams(templateValues)
        .intoOpenFiles(filesToOpen)
        .intoTargetFiles(filesToReformat)
        .build()
      return template.render(context, dryRun)
    }
  }

  // For ease of debugging add a scratch file containing the template values.
  private fun toScratchFile(project: Project?): VirtualFile? {
    val templateVars = templateValues.map { (key, value) ->
      "$key=$value"
    }.joinToString(System.lineSeparator())

    return ScratchRootType.getInstance().createScratchFile(project, "templateVars.txt", PlainTextLanguage.INSTANCE,
                                                           templateVars, ScratchFileService.Option.create_new_always)
  }

  companion object {
    private const val PROPERTIES_RENDER_LANGUAGE_KEY = "SAVED_RENDER_LANGUAGE"

    @JvmStatic
    fun fromFacet(facet: AndroidFacet, templateHandle: TemplateHandle?, initialPackageSuggestion: String, template: NamedModuleTemplate,
                  commandName: String, projectSyncInvoker: ProjectSyncInvoker, shouldOpenFiles: Boolean) =
      RenderTemplateModel(OptionalValueProperty(facet.module.project),
                          facet,
                          templateHandle,
                          ObjectValueProperty(template),
                          StringValueProperty(facet.module.project.basePath!!),
                          StringValueProperty(facet.module.name),
                          StringValueProperty(initialPackageSuggestion),
                          commandName,
                          MultiTemplateRenderer(facet.module.project, projectSyncInvoker),
                          shouldOpenFiles)

    @JvmStatic
    fun fromModuleModel(moduleModel: NewModuleModel, templateHandle: TemplateHandle?,
                        commandName: String = moduleModel.formFactor.get().id) =
      RenderTemplateModel(moduleModel.project,
                          null,
                          templateHandle,
                          moduleModel.template,
                          moduleModel.projectLocation,
                          moduleModel.moduleName,
                          moduleModel.packageName,
                          commandName,
                          moduleModel.multiTemplateRenderer.apply { incrementRenders() },
                          true,
                          moduleTemplateValues = moduleModel.templateValues)

    /**
     * Design: If there are no kotlin facets in the project, the default should be Java, whether or not you previously chose Kotlin
     * (presumably in a different project which did have Kotlin).
     * If it *does* have a Kotlin facet, then remember the previous selection (if there was no previous selection yet, default to Kotlin)
     */
    @JvmStatic
    fun getInitialSourceLanguage(project: Project?): Language {
      return if (project != null && project.hasAnyKotlinModules())
        Language.fromName(PropertiesComponent.getInstance().getValue(PROPERTIES_RENDER_LANGUAGE_KEY), Language.KOTLIN)
      else
        Language.JAVA
    }

    private fun setInitialSourceLanguage(language: Language) {
      PropertiesComponent.getInstance().setValue(PROPERTIES_RENDER_LANGUAGE_KEY, language.toString())
    }

    private fun languagePropertyFromProject(project: Project?) = ObjectValueProperty(getInitialSourceLanguage(project)).apply {
      addListener { setInitialSourceLanguage(this.get()) }
    }
  }
}
