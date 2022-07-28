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
import com.android.tools.idea.hasAnyKotlinModules
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.npw.template.ModuleTemplateDataBuilder
import com.android.tools.idea.npw.template.ProjectTemplateDataBuilder
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.ObjectProperty
import com.android.tools.idea.observable.core.ObjectValueProperty
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.projectsystem.AndroidModulePaths
import com.android.tools.idea.projectsystem.NamedModuleTemplate
import com.android.tools.idea.projectsystem.getMainModule
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.templates.KeystoreUtils.getSha1DebugKeystoreSilently
import com.android.tools.idea.templates.TemplateUtils
import com.android.tools.idea.templates.recipe.DefaultRecipeExecutor
import com.android.tools.idea.templates.recipe.FindReferencesRecipeExecutor
import com.android.tools.idea.templates.recipe.RenderingContext
import com.android.tools.idea.wizard.model.WizardModel
import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.Template
import com.android.tools.idea.wizard.template.TemplateConstraint
import com.android.tools.idea.wizard.template.ViewBindingSupport
import com.android.tools.idea.wizard.template.WizardParameterData
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplatesUsage.TemplateComponent.WizardUiContext
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet
import java.io.File

private val log = logger<RenderTemplateModel>()

private class ExistingNewModuleModelData(
  existingProjectModelData: ExistingProjectModelData, facet: AndroidFacet, template: NamedModuleTemplate,
  override val wizardContext: WizardUiContext
) : ModuleModelData, ProjectModelData by existingProjectModelData {
  override val template: ObjectProperty<NamedModuleTemplate> = ObjectValueProperty(template)
  override val moduleName: StringValueProperty = StringValueProperty(facet.module.name)
  override val moduleTemplateDataBuilder = ModuleTemplateDataBuilder(
    projectTemplateDataBuilder = ProjectTemplateDataBuilder(false),
    isNewModule = false,
    viewBindingSupport = existingProjectModelData.viewBindingSupport.getValueOr(ViewBindingSupport.SUPPORTED_4_0_MORE)
  )
  override val loggingEvent: AndroidStudioEvent.TemplateRenderer
    get() = AndroidStudioEvent.TemplateRenderer.UNKNOWN_TEMPLATE_RENDERER

  override val formFactor: ObjectValueProperty<FormFactor> get() =
    throw UnsupportedOperationException("We cannot reliably know formFactor of an existing module")
  override val category: ObjectValueProperty<Category> get() =
    throw UnsupportedOperationException("We cannot reliably know category of an existing module")
  override val isLibrary: Boolean = false
  override val androidSdkInfo: OptionalValueProperty<AndroidVersionsInfo.VersionItem> = OptionalValueProperty.absent()
  override val sendModuleMetrics: BoolValueProperty = BoolValueProperty(true)
}

/**
 * A model responsible for instantiating a [Template] into the current project representing an Android component.
 */
class RenderTemplateModel private constructor(
  private val moduleModelData: ModuleModelData,
  val androidFacet: AndroidFacet?,
  private val commandName: String,
  private val shouldOpenFiles: Boolean
) : WizardModel(), ModuleModelData by moduleModelData {
  /**
   * The target template we want to render. If null, the user is skipping steps that would instantiate a template and this model shouldn't
   * try to render anything.
   */
  lateinit var wizardParameterData: WizardParameterData
  var newTemplate: Template = Template.NoActivity
  set(value) {
    field = value
    wizardParameterData = WizardParameterData(
      packageName.get(),
      module == null,
      template.get().name,
      value.parameters
    )
  }
  init {
    language.addListener {
      PropertiesComponent.getInstance().setValue(PROPERTIES_RENDER_LANGUAGE_KEY, language.value.toString())
    }
  }

  val module: Module?
    get() = androidFacet?.module?.getMainModule()

  val hasActivity: Boolean get() = newTemplate != Template.NoActivity

  val createdFiles: MutableList<File> = arrayListOf()

  public override fun handleFinished() {
    multiTemplateRenderer.requestRender(TemplateRenderer())
  }

  public override fun handleSkipped() {
    multiTemplateRenderer.skipRender()
  }

  private inner class TemplateRenderer : MultiTemplateRenderer.TemplateRenderer {
    private var renderSuccess: Boolean = false

    @WorkerThread
    override fun init() {
      val paths = template.get().paths
      if (paths.moduleRoot == null) {
        log.error("RenderTemplateModel can't create files because module root is not found. Please report this error.")
        return
      }

      sendModuleMetrics.set(!hasActivity)

      moduleTemplateDataBuilder.apply {
        // sourceProviderName = template.get().name TODO(qumeric) there is no sourcesProvider (yet?)
        projectTemplateDataBuilder.setProjectDefaults(project)
        formFactor = newTemplate.formFactor
        moduleTemplateDataBuilder.setModuleRoots(
          paths, projectLocation.get(), moduleName.get(), this@RenderTemplateModel.packageName.get()
        )
        category = newTemplate.category
        isMaterial3 = newTemplate.constraints.contains(TemplateConstraint.Material3)
        useGenericInstrumentedTests = newTemplate.useGenericInstrumentedTests
        useGenericLocalTests = newTemplate.useGenericLocalTests
        projectTemplateDataBuilder.language = language.value

        projectTemplateDataBuilder.debugKeyStoreSha1 = getSha1DebugKeystoreSilently(androidFacet)

        if (androidFacet == null) {
          return@apply
        }

        setFacet(androidFacet)

        // Register application-wide settings
        val applicationPackage = androidFacet.getModuleSystem().getPackageName()
        if (this@RenderTemplateModel.packageName.get() != applicationPackage) {
          projectTemplateDataBuilder.applicationPackage = applicationPackage
        }
      }
    }

    @WorkerThread
    override fun doDryRun(): Boolean {
      if (!hasActivity) {
        return true
      }

      return renderTemplate(true, project, template.get().paths)
    }

    @WorkerThread
    override fun render() {
      val paths = template.get().paths

      try {
        renderSuccess = renderTemplate(false, project, paths)
      }
      catch (t: Throwable) {
        log.warn(t)
      }
    }

    @UiThread
    override fun finish() {
      if (renderSuccess && shouldOpenFiles) {
        DumbService.getInstance(project).smartInvokeLater { TemplateUtils.openEditors(project, createdFiles, true) }
      }
    }

    private fun renderTemplate(
      dryRun: Boolean, project: Project, paths: AndroidModulePaths
    ): Boolean {
      paths.moduleRoot ?: return false

      if (newTemplate.constraints.contains(TemplateConstraint.Compose)) {
        // Compose requires this specific Kotlin
        moduleTemplateDataBuilder.projectTemplateDataBuilder.kotlinVersion =
          getComposeKotlinVersion(isMaterial3 = newTemplate.constraints.contains(TemplateConstraint.Material3))
      }

      val context = RenderingContext(
        project = project,
        module = module,
        commandName = commandName,
        templateData = moduleTemplateDataBuilder.build(), // FIXME
        moduleRoot = paths.moduleRoot!!,
        dryRun = dryRun,
        showErrors = true
      )

      val metrics = TemplateMetrics(
        templateType = titleToTemplateType(newTemplate.name, newTemplate.formFactor),
        wizardContext = wizardContext,
        moduleType = moduleTemplateRendererToModuleType(moduleModelData.loggingEvent),
        minSdk = androidSdkInfo.valueOrNull?.minApiLevel ?: 0,
        bytecodeLevel = (moduleModelData as? NewAndroidModuleModel)?.bytecodeLevel?.valueOrNull,
        useGradleKts = useGradleKts.get(),
        useAppCompat = useAppCompat.get()
      )

      val executor = if (dryRun) FindReferencesRecipeExecutor(context) else DefaultRecipeExecutor(context)

      return newTemplate.render(context, executor, metrics).also {
        if (!dryRun) {
          createdFiles.addAll(context.filesToOpen)
        }
      }
    }
  }

  companion object {
    private const val PROPERTIES_RENDER_LANGUAGE_KEY = "SAVED_RENDER_LANGUAGE"

    @JvmStatic
    fun fromFacet(
      facet: AndroidFacet,
      initialPackageSuggestion: String?,
      template: NamedModuleTemplate,
      commandName: String,
      projectSyncInvoker: ProjectSyncInvoker,
      shouldOpenFiles: Boolean,
      wizardContext: WizardUiContext
    ) = RenderTemplateModel(
      moduleModelData = ExistingNewModuleModelData(
        ExistingProjectModelData(facet.module.project, projectSyncInvoker).apply { initialPackageSuggestion?.let { packageName.set(it) }},
        facet, template, wizardContext),
      androidFacet = facet,
      commandName = commandName,
      shouldOpenFiles = shouldOpenFiles)

    @JvmStatic
    fun fromModuleModel(
      moduleModel: NewAndroidModuleModel,
      commandName: String = "Render new ${moduleModel.formFactor.get().name} template"
    ) = RenderTemplateModel(
      moduleModelData = moduleModel,
      androidFacet = null,
      commandName = commandName,
      shouldOpenFiles = true
    ).apply { multiTemplateRenderer.incrementRenders() }

    /**
     * Design: If there are no kotlin facets in the project, the default should be Java, whether or not you previously chose Kotlin
     * (presumably in a different project which did have Kotlin).
     * If it *does* have a Kotlin facet, then remember the previous selection (if there was no previous selection yet, default to Kotlin)
     */
    fun getInitialSourceLanguage(project: Project?): Language {
      return if (project != null && project.hasAnyKotlinModules())
        Language.fromName(PropertiesComponent.getInstance().getValue(PROPERTIES_RENDER_LANGUAGE_KEY), Language.Kotlin)
      else
        Language.Java
    }

    fun getComposeKotlinVersion(isMaterial3: Boolean): String = "1.7.0"
  }
}
