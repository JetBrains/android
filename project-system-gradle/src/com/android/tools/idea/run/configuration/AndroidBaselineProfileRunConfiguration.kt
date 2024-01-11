package com.android.tools.idea.run.configuration

import com.android.tools.idea.execution.common.AndroidConfigurationExecutor
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.util.BaselineProfileUtil.BP_PLUGIN_FILTERING_SUPPORTED
import com.android.tools.idea.help.AndroidWebHelpProvider
import com.android.tools.idea.projectsystem.gradle.getGradlePluginVersion
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.idea.run.AndroidRunConfigurationFactoryBase
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.run.ValidationError
import com.android.tools.idea.run.configuration.BaselineProfileRunLineMarkerContributor.Companion.anyTopLevelJavaRule
import com.android.tools.idea.run.configuration.BaselineProfileRunLineMarkerContributor.Companion.anyTopLevelKtRule
import com.android.tools.idea.run.configuration.BaselineProfileRunLineMarkerContributor.Companion.isJavaTestClassIdentifier
import com.android.tools.idea.run.configuration.BaselineProfileRunLineMarkerContributor.Companion.isJavaTestMethodIdentifier
import com.android.tools.idea.run.configuration.BaselineProfileRunLineMarkerContributor.Companion.isKtTestClassIdentifier
import com.android.tools.idea.run.configuration.BaselineProfileRunLineMarkerContributor.Companion.isKtTestMethodIdentifier
import com.android.tools.idea.run.editor.DeployTargetProvider
import com.android.tools.idea.testartifacts.instrumented.TestRunConfigurationOptions
import com.intellij.execution.BeforeRunTask
import com.intellij.execution.Executor
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.WithoutOwnBeforeRunSteps
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.psi.PsiElement
import icons.StudioIcons
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.kotlin.idea.KotlinLanguage
import javax.swing.Icon

class AndroidBaselineProfileRunConfigurationType : ConfigurationType {
  companion object {
    const val ID = "AndroidBaselineProfileRunConfigurationType"
    const val NAME = "Generate Baseline Profile"

    fun getInstance(): AndroidBaselineProfileRunConfigurationType {
      return ConfigurationTypeUtil.findConfigurationType(AndroidBaselineProfileRunConfigurationType::class.java)
    }
  }

  val factory: ConfigurationFactory
    get() = object : AndroidRunConfigurationFactoryBase(this) {
      override fun getId(): String {
        return "Android Baseline Profile Configuration Factory"
      }

      override fun createTemplateConfiguration(project: Project): RunConfiguration {
        return AndroidBaselineProfileRunConfiguration(project, this, NAME)
      }
    }

  override fun getDisplayName(): String {
    return AndroidBundle.message("android.baseline.profile.run.configuration.type.name")
  }

  override fun getConfigurationTypeDescription(): String? {
    return AndroidBundle.message("android.baseline.profile.run.configuration.type.description")
  }

  override fun getIcon(): Icon {
    return StudioIcons.Wizards.Modules.BASELINE_PROFILE_GENERATE
  }

  override fun getId(): String = ID

  override fun getConfigurationFactories(): Array<ConfigurationFactory> {
    return arrayOf(factory)
  }

  override fun getHelpTopic(): String {
    return AndroidWebHelpProvider.HELP_PREFIX + "r/studio-ui/rundebugconfig.html"
  }
}

class AndroidBaselineProfileTestOptions : TestRunConfigurationOptions() {
  val param = "-e androidx.benchmark.enabledRules baselineprofile,macrobenchmark,microbenchmark"

  override fun getExtraOptions(context: ConfigurationContext): List<String> {
    return if (checkContext(context)) listOf(param) else emptyList()
  }

  private fun checkContext(context: ConfigurationContext): Boolean {
    val element = context.location?.psiElement ?: return false
    return isKotlinTest(element) || isJavaTest(element)
  }

  private fun isKotlinTest(e: PsiElement): Boolean {
    return e.language == KotlinLanguage.INSTANCE &&
           (isKtTestClassIdentifier(e) || isKtTestMethodIdentifier(e)) &&
           anyTopLevelKtRule(e) != null

  }

  private fun isJavaTest(e: PsiElement): Boolean {
    return e.language == JavaLanguage.INSTANCE &&
           (isJavaTestClassIdentifier(e) || isJavaTestMethodIdentifier(e)) &&
           anyTopLevelJavaRule(e) != null
  }
}

class AndroidBaselineProfileRunConfiguration(project: Project, factory: ConfigurationFactory, name: String? = factory.name) :
  AndroidRunConfigurationBase(project, factory, name, true), WithoutOwnBeforeRunSteps {

  @JvmField
  var generateAllVariants: Boolean = false

  override fun getBeforeRunTasks(): MutableList<BeforeRunTask<*>> {
    // Do not allow build, as the gradle task will do it for us.
    return mutableListOf()
  }

  override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> {
    return AndroidBaselineProfileRunConfigurationEditor(project, this)
  }

  override fun supportsRunningLibraryProjects(facet: AndroidFacet): Pair<Boolean, String> = Pair(false, AndroidBundle.message("android.cannot.run.library.project.error"))

  override fun checkConfiguration(facet: AndroidFacet): MutableList<ValidationError> {
    return mutableListOf()
  }

  override fun getApplicableDeployTargetProviders(): MutableList<DeployTargetProvider> {
    return deployTargetContext.getApplicableDeployTargetProviders(true);
  }

  override fun getExecutor(env: ExecutionEnvironment, facet: AndroidFacet?, deployFutures: DeviceFutures): AndroidConfigurationExecutor {
    return AndroidBaselineProfileConfigurationExecutor(env, deployFutures)
  }

  fun getFilterArgument(): String? {
    val agpVersion = configurationModule.module?.getGradlePluginVersion() ?: return null
    return if (agpVersion >= BP_PLUGIN_FILTERING_SUPPORTED)
      null
    else
      "-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=baselineprofile"
  }

  override fun validate(executor: Executor?): MutableList<ValidationError> {
    val module = configurationModule.module ?: return mutableListOf(ValidationError.fatal("No module specified in configuration"))
    applicationIdProvider ?: return mutableListOf(ValidationError.fatal("Application ID cannot be found"))
    module.getGradlePluginVersion() ?: return mutableListOf(ValidationError.fatal("Could not determine AGP version"))
    val model = GradleAndroidModel.get(module) ?: return mutableListOf(ValidationError.fatal("Target module ${module.name} is not a Gradle Android module."))
    if (!generateAllVariants && model.isDebuggable) {
      return mutableListOf(ValidationError.fatal(
        "Target module's selected variant is debuggable. Please use Build Variants tools window to change the variant of ${module.name}."))
    }
    return mutableListOf()
  }
}
