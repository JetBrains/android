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
import com.android.tools.idea.npw.FormFactor
import com.android.tools.idea.npw.assetstudio.IconGenerator
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.npw.platform.Language
import com.android.tools.idea.npw.project.getPackageForApplication
import com.android.tools.idea.npw.template.TemplateHandle
import com.android.tools.idea.npw.template.TemplateValueInjector
import com.android.tools.idea.observable.core.BoolProperty
import com.android.tools.idea.observable.core.ObjectProperty
import com.android.tools.idea.observable.core.ObjectValueProperty
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.projectsystem.AndroidModulePaths
import com.android.tools.idea.projectsystem.NamedModuleTemplate
import com.android.tools.idea.templates.ModuleTemplateDataBuilder
import com.android.tools.idea.templates.Template
import com.android.tools.idea.templates.TemplateAttributes.ATTR_APPLICATION_PACKAGE
import com.android.tools.idea.templates.TemplateAttributes.ATTR_IS_LAUNCHER
import com.android.tools.idea.templates.TemplateAttributes.ATTR_SOURCE_PROVIDER_NAME
import com.android.tools.idea.templates.TemplateUtils
import com.android.tools.idea.templates.recipe.RenderingContext
import com.android.tools.idea.wizard.model.WizardModel
import com.android.tools.idea.wizard.template.WizardParameterData
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
import com.android.tools.idea.wizard.template.Template as Template2

private val log = logger<RenderTemplateModel>()

class ExistingNewModuleModelData(
  existingNewProjectModelData: ExistingNewProjectModelData, facet: AndroidFacet, template: NamedModuleTemplate
) : ModuleModelData, ProjectModelData by existingNewProjectModelData {
  override val template: ObjectProperty<NamedModuleTemplate> = ObjectValueProperty(template)
  override val moduleName: StringValueProperty = StringValueProperty(facet.module.name)
  override val moduleTemplateValues: MutableMap<String, Any> = mutableMapOf()

  override val moduleParent: String? get() = TODO("not implemented")
  override val formFactor: ObjectValueProperty<FormFactor> get() = TODO("not implemented")
  override val isLibrary: BoolProperty get() = TODO("not implemented")
  override val templateFile: OptionalValueProperty<File> get() = TODO("not implemented")
  override val androidSdkInfo: OptionalValueProperty<AndroidVersionsInfo.VersionItem> get() = TODO("not implemented")
}

/**
 * A model responsible for instantiating a FreeMarker [Template] into the current project representing an Android component.
 */
class RenderTemplateModel private constructor(
  moduleModelData: ModuleModelData,
  val androidFacet: AndroidFacet?,
  var templateHandle: TemplateHandle? = null,
  private val commandName: String,
  private val shouldOpenFiles: Boolean,
  /** Populated in [Template.render] */
  val createdFiles: MutableList<File> = arrayListOf(),
  // TODO(qumeric): this should replace templateHandle eventually
  var newTemplate: Template2 = Template2.NoActivity
) : WizardModel(), ModuleModelData by moduleModelData {
  /**
   * The target template we want to render. If null, the user is skipping steps that would instantiate a template and this model shouldn't
   * try to render anything.
   */
  val templateValues = hashMapOf<String, Any>()
  /**
   * This is used in place of [templateValues] for the new templates.
   */
  val moduleTemplateDataBuilder = ModuleTemplateDataBuilder(false) // FIXME(qumeric)
  val wizardParameterData = WizardParameterData(
    packageName.get(),
    module == null,
    template.get().name
  )
  var iconGenerator: IconGenerator? = null
  val renderLanguage = ObjectValueProperty(getInitialSourceLanguage(project.valueOrNull)).apply {
    addListener {
      PropertiesComponent.getInstance().setValue(PROPERTIES_RENDER_LANGUAGE_KEY, this.get().toString())
    }
  }

  val module: Module?
    get() = androidFacet?.module

  val hasActivity: Boolean
    get() = templateHandle != null || newTemplate != Template2.NoActivity

  val isNew: Boolean
    get() = templateHandle == null && newTemplate != Template2.NoActivity

  val isOld: Boolean
    get() = templateHandle != null && newTemplate == Template2.NoActivity

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

      if (StudioFlags.NPW_EXPERIMENTAL_ACTIVITY_GALLERY.get()) {
        moduleTemplateDataBuilder.apply {
          // sourceProviderName = template.get().name TODO there is no sourcesProvider (yet?)
          moduleTemplateDataBuilder.setModuleRoots(
            paths, projectLocation.get(), moduleName.get(), this@RenderTemplateModel.packageName.get())

          if (androidFacet == null) {
            return@apply
          }

          setFacet(androidFacet)
          projectTemplateDataBuilder.language = language.get().get()

          // Register application-wide settings
          val applicationPackage = androidFacet.getPackageForApplication()
          if (this@RenderTemplateModel.packageName.get() != applicationPackage) {
            projectTemplateDataBuilder.applicationPackage = androidFacet.getPackageForApplication()
          }
        }
      }

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
      templateInjector.setLanguage(renderLanguage.get()) // Note: For new projects/modules we have a different UI.

      // Register application-wide settings
      val applicationPackage = androidFacet.getPackageForApplication()
      if (packageName.get() != applicationPackage) {
        templateValues[ATTR_APPLICATION_PACKAGE] = androidFacet.getPackageForApplication()
      }
    }

    @WorkerThread
    override fun doDryRun(): Boolean {
      if (!project.get().isPresent || !hasActivity) {
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
                               paths: AndroidModulePaths,
                               filesToOpen: MutableList<File>?,
                               filesToReformat: MutableList<File>?): Boolean {
      if (templateHandle == null) {
        return true // TODO: implement new template rendering
      }
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
  private fun toScratchFile(project: Project?): VirtualFile? = ScratchRootType.getInstance().createScratchFile(
    project, "templateVars.txt", PlainTextLanguage.INSTANCE,
    templateValues.map { (key, value) -> "$key=$value" }.joinToString(System.lineSeparator()),
    ScratchFileService.Option.create_new_always)

  companion object {
    private const val PROPERTIES_RENDER_LANGUAGE_KEY = "SAVED_RENDER_LANGUAGE"

    @JvmStatic
    fun fromFacet(
      facet: AndroidFacet, templateHandle: TemplateHandle?, initialPackageSuggestion: String, template: NamedModuleTemplate,
      commandName: String, projectSyncInvoker: ProjectSyncInvoker, shouldOpenFiles: Boolean
    ) = RenderTemplateModel(
      moduleModelData = ExistingNewModuleModelData(
        ExistingNewProjectModelData(facet.module.project, projectSyncInvoker).apply { packageName.set(initialPackageSuggestion) },
        facet, template),
      androidFacet = facet,
      templateHandle = templateHandle,
      commandName = commandName,
      shouldOpenFiles = shouldOpenFiles)

    @JvmStatic
    fun fromModuleModel(
      moduleModel: NewModuleModel, templateHandle: TemplateHandle?, commandName: String = moduleModel.formFactor.get().id
    ) = RenderTemplateModel(
      moduleModelData = moduleModel,
      androidFacet = null,
      templateHandle = templateHandle,
      commandName = commandName,
      shouldOpenFiles = true
    ).apply { multiTemplateRenderer.incrementRenders() }

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
  }
}
