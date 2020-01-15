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
package com.android.tools.idea.templates

import com.android.sdklib.SdkVersionInfo
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker.cleanAfterTesting
import com.android.tools.analytics.UsageTracker.setWriterForTest
import com.android.tools.idea.npw.platform.Language
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.templates.Parameter.Type
import com.android.tools.idea.templates.TemplateAttributes.ATTR_BUILD_API
import com.android.tools.idea.templates.TemplateAttributes.ATTR_BUILD_API_STRING
import com.android.tools.idea.templates.TemplateAttributes.ATTR_HAS_APPLICATION_THEME
import com.android.tools.idea.templates.TemplateAttributes.ATTR_IS_LAUNCHER
import com.android.tools.idea.templates.TemplateAttributes.ATTR_IS_LIBRARY_MODULE
import com.android.tools.idea.templates.TemplateAttributes.ATTR_LANGUAGE
import com.android.tools.idea.templates.TemplateAttributes.ATTR_MIN_API
import com.android.tools.idea.templates.TemplateAttributes.ATTR_MIN_API_LEVEL
import com.android.tools.idea.templates.TemplateAttributes.ATTR_THEME_EXISTS
import com.android.tools.idea.templates.TemplateMetadata.TemplateConstraint.ANDROIDX
import com.android.tools.idea.templates.recipe.RenderingContext
import com.android.tools.idea.templates.recipe.RenderingContext2
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.IdeComponents
import java.io.File
import java.lang.RuntimeException
import kotlin.system.measureTimeMillis

typealias ProjectStateCustomizer = (templateMap: MutableMap<String, Any>, projectMap: MutableMap<String, Any>) -> Unit

/**
 * Base class for test for template instantiation.
 *
 * Remaining work on template test:
 * - Start using new NewProjectModel etc to initialise TemplateParameters and set parameter values.
 * - Fix clean model syncing, and hook up clean lint checks.
 * - Test all combinations of build tools.
 * - Add metadata to template parameters (e.g. values to test) and simplify code here.
 */
abstract class TemplateTestBase : AndroidGradleTestCase() {
  /** A UsageTracker implementation that allows introspection of logged metrics in tests. */
  private val usageTracker = TestUsageTracker(VirtualTimeScheduler())

  private val templateOverrides = mutableMapOf<String, Any>()
  private val moduleOverrides = mutableMapOf<String, Any>()

  override fun createDefaultProject() = false

  override fun setUp() {
    super.setUp()
    setWriterForTest(usageTracker)

    /**
     * Replace the default RepositoryUrlManager with one that enables repository checks in tests.
     * This is necessary to fully resolve dynamic gradle coordinates (e.g. appcompat-v7:+ => appcompat-v7:25.3.1).
     * It will keep coordinates exactly the same as they are resolved within the NPW flow.
     *
     * @see RepositoryUrlManager.forceRepositoryChecksInTests
     */
    IdeComponents(null, testRootDisposable).replaceApplicationService(
      RepositoryUrlManager::class.java,
      RepositoryUrlManager(IdeGoogleMavenRepository, OfflineIdeGoogleMavenRepository, true)
    )
  }

  override fun tearDown() {
    try {
      usageTracker.close()
      cleanAfterTesting()
    }
    finally {
      super.tearDown()
    }
  }

  /**
   * A wrapper to allow Kotlin "last argument is a lambda" syntax.
   */
  protected fun checkCreateTemplate(
    category: String,
    name: String,
    activityCreationMode: ActivityCreationMode = ActivityCreationMode.WITHOUT_PROJECT,
    apiSensitive: Boolean = true,
    customizer: ProjectStateCustomizer
  ): Unit = checkCreateTemplate(category, name, activityCreationMode, customizer, { _, _ -> })

  /**
   * Checks the given template in the given category. Supports overridden template values.
   *
   * @param category          the template category
   * @param name              the template name
   * @param activityCreationMode whether the template should be created as part of creating the project (only for activities), or whether it
   * should be added as as a separate template into an existing project (which is created first, followed by the template).
   * @param apiSensitive       If true, check this template with all the interesting ([isInterestingApiLevel]) API versions.
   * @param customizers        An instance of [ProjectStateCustomizer]s used for providing template and project overrides.
   */
  protected open fun checkCreateTemplate(
    category: String,
    name: String,
    activityCreationMode: ActivityCreationMode = ActivityCreationMode.WITHOUT_PROJECT,
    vararg customizers: ProjectStateCustomizer
  ) {
    if (DISABLED) {
      return
    }
    ensureSdkManagerAvailable()
    val templateFile = findTemplate(category, name)
    if (isBroken(templateFile.name)) {
      return
    }
    customizers.forEach {
      it(templateOverrides, moduleOverrides)
    }
    val msToCheck = measureTimeMillis {
      checkTemplate(templateFile, activityCreationMode)
    }
    println("Checked ${templateFile.name} successfully in ${msToCheck}ms")
  }

  /**
   * Runs [checkApiTarget] with proper min and build apis. Currently only runs it with only one combination.
   * See b/143199720 for context.
   */
  private fun checkTemplate(
    templateFile: File,
    activityCreationMode: ActivityCreationMode
  ) {
    require(!isBroken(templateFile.name))

    val sdkData = AndroidSdks.getInstance().tryToChooseAndroidSdk()!!
    val projectState = createNewProjectState(sdkData, getModuleTemplateForFormFactor(templateFile))
    val moduleState = projectState.moduleTemplateState
    val moduleMetadata = moduleState.template.metadata!!
    val activityState = projectState.activityTemplateState.apply { setTemplateLocation(templateFile) }
    val activityMetadata = activityState.template.metadata!!
    val hasEnums = moduleMetadata.parameters.any { it.type == Type.ENUM }

    if (hasEnums && templateOverrides.isEmpty()) {
      // TODO: Handle all enums here. None of the projects have this currently at this level.
      return fail("Not expecting enums at the root level")
    }

    // TODO: Assert that the SDK manager has required SDKs
    val buildSdk = SdkVersionInfo.HIGHEST_KNOWN_STABLE_API
    val minSdk = maxOf(
      moduleState.getString(ATTR_MIN_API).toInt(),
      moduleMetadata.minSdk,
      activityMetadata.minSdk
    )

    var base = "${templateFile.name}_min_${minSdk}_build_${buildSdk}"
    if (templateOverrides.isNotEmpty()) {
      base += "_overrides"
    }

    checkApiTarget(minSdk, buildSdk, projectState, base, activityState, activityCreationMode)
  }

  /**
   * Checks creating the given project and template for the given SDK versions.
   * If [COMPREHENSIVE] run check for all possible versions of each enum and boolean parameter.
   */
  protected fun checkApiTarget(
    minSdk: Int,
    buildVersion: Int,
    projectState: TestNewProjectWizardState,
    projectNameBase: String,
    activityState: TestTemplateWizardState,
    activityCreationMode: ActivityCreationMode = ActivityCreationMode.WITH_PROJECT
  ) {
    fun Option.needsCheck(initial: Any?, moduleState: TestTemplateWizardState): Boolean {
      val projectMinApi = moduleState.getInt(ATTR_MIN_API_LEVEL)
      val projectBuildApi = moduleState.getInt(ATTR_BUILD_API)
      return projectMinApi >= minSdk && projectBuildApi >= minBuild && id != initial
    }

    val moduleState = projectState.moduleTemplateState.apply {
      put(ATTR_MIN_API, minSdk.toString())
      put(ATTR_MIN_API_LEVEL, minSdk)
      put(ATTR_BUILD_API, buildVersion)
      put(ATTR_BUILD_API_STRING, buildVersion.toString())
      putAll(moduleOverrides)
    }

    if (activityCreationMode == ActivityCreationMode.DO_NOT_CREATE) {
      val projectName = "${projectNameBase}_no_activity"
      return checkProject(projectName, projectState, activityState.apply { putAll(templateOverrides) }, activityCreationMode, false)
    }

    val templateState = when (activityCreationMode) {
      ActivityCreationMode.WITH_PROJECT -> projectState.activityTemplateState
      ActivityCreationMode.WITHOUT_PROJECT -> activityState
      else -> throw RuntimeException("We should not initialize template state if we are not going to create an activity")
    }

    templateState.apply { putAll(templateOverrides) }

    val parameters = when (activityCreationMode) {
      ActivityCreationMode.WITHOUT_PROJECT -> {
        templateState.setParameterDefaults()
        templateState.template.metadata!!.parameters
      }
      ActivityCreationMode.WITH_PROJECT ->
        moduleState.template.metadata!!.parameters + templateState.template.metadata!!.parameters
      else -> listOf()
    }

    parameters.filterNot { it.type == Type.STRING || it.type == Type.SEPARATOR || templateOverrides.containsKey(it.id) }.forEach { p ->
      val initial = p.getDefaultValue(templateState)!!

      fun checkAndRestore(parameterValue: Any) {
        templateState.put(p.id!!, parameterValue)
        val projectName = "${projectNameBase}_${p.id}_$parameterValue"
        checkProject(projectName, projectState, activityState, activityCreationMode, true)
        templateState.put(p.id!!, initial)
      }

      if (p.type === Type.ENUM) {
        p.options.takeOneIfTrueElseAll(!COMPREHENSIVE)
          .asSequence()
          .map { e -> e.toOption() }
          .filter { it.needsCheck(initial, moduleState) }
          .forEach { checkAndRestore(it.id) }
      }
      else {
        assert(p.type === Type.BOOLEAN)
        if (p.id == ATTR_IS_LAUNCHER && activityCreationMode == ActivityCreationMode.WITH_PROJECT) {
          // ATTR_IS_LAUNCHER is always true when launched from new project
          return@forEach
        }
        checkAndRestore(!(initial as Boolean))
      }
    }
    val projectName = "${projectNameBase}_default"
    checkProject(projectName, projectState, activityState, activityCreationMode, false)
  }

  /**
   * Initializes [ProjectChecker] and runs [ProjectChecker.checkProject] 1-3 times.
   * 1. Normally.
   * 2. Without androidX if it is not required.
   * 3. Library version if it is mobile activity.
   */
  private fun checkProject(
    projectName: String,
    projectState: TestNewProjectWizardState,
    activityState: TestTemplateWizardState,
    activityCreationMode: ActivityCreationMode,
    onlyAndroidX: Boolean
  ) {
    val moduleState = projectState.moduleTemplateState
    val templateMetadata = activityState.template.metadata
    val language = Language.fromName(moduleState[ATTR_LANGUAGE] as String?, Language.JAVA)
    val projectChecker = ProjectChecker(CHECK_LINT, projectState, activityState, usageTracker, language, activityCreationMode)

    val supportLibIsNotSupported = templateMetadata != null && templateMetadata.constraints.contains(ANDROIDX)

    if (!supportLibIsNotSupported && !onlyAndroidX && moduleState.getInt(ATTR_BUILD_API) < 29) {
      // Make sure we test all templates against androidx
      disableAndroidX(moduleState, activityState)
      projectChecker.checkProject(projectName + "_android_support")
    }

    enableAndroidX(moduleState, activityState)
    projectChecker.checkProject(projectName)
    // check that new Activities can be created on lib modules as well as app modules.
    // only NavigationDrawerActivity and GoogleMapsActivity are being tested because it gives 100% coverage and saves time.
    val checkLib = "Activity" == templateMetadata?.category && "Mobile" == templateMetadata.formFactor &&
                   activityCreationMode == ActivityCreationMode.WITHOUT_PROJECT && "default" in projectName &&
                   activityState[COMPARE_NEW_RENDERING_CONTEXT] != true &&
                   ("NavigationDrawerActivity" in projectName || "GoogleMapsWearActivity" in projectName)
    if (checkLib) {
      moduleState.put(ATTR_IS_LIBRARY_MODULE, true)
      activityState.put(ATTR_IS_LIBRARY_MODULE, true)
      activityState.put(ATTR_HAS_APPLICATION_THEME, false)
      // For a library project a theme doesn't exist. This is derived in the IDE using FmGetApplicationThemeMethod
      moduleState.put(ATTR_THEME_EXISTS, false)
      projectChecker.checkProject(projectName + "_lib")
    }
  }

  @MustBeDocumented
  @Retention(AnnotationRetention.RUNTIME)
  @Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
  annotation class TemplateCheck
}

private fun getBoolFromEnvironment(key: String) = System.getProperty(key).orEmpty().toBoolean() || System.getenv(key).orEmpty().toBoolean()

/**
 * Whether we should run comprehensive tests or not. This flag allows a simple run to just check a small set of
 * template combinations, and when the flag is set on the build server, a much more comprehensive battery of
 * checks to be performed.
 */
private val COMPREHENSIVE = getBoolFromEnvironment("com.android.tools.idea.templates.TemplateTest.COMPREHENSIVE")
/**
 * Whether we should run these tests or not.
 */
internal val DISABLED = getBoolFromEnvironment("DISABLE_STUDIO_TEMPLATE_TESTS")
/**
 * Whether we should enforce that lint passes cleanly on the projects
 */
internal const val CHECK_LINT = false // Needs work on closing projects cleanly
/**
 * Const for toggling the behavior of a test.
 *
 * If this value is true, the test should include comparison between the contents of the two projects generated from
 * the [RenderingContext] and the new [RenderingContext2].
 */
const val COMPARE_NEW_RENDERING_CONTEXT = "compareNewRenderingContext"
