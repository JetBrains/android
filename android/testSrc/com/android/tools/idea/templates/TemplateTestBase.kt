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

import com.android.sdklib.AndroidVersion
import com.android.sdklib.SdkVersionInfo
import com.android.testutils.TestUtils.getKotlinVersionForTests
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker.cleanAfterTesting
import com.android.tools.analytics.UsageTracker.setWriterForTest
import com.android.tools.idea.npw.platform.Language
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.templates.Parameter.Type
import com.android.tools.idea.templates.TemplateMetadata.ATTR_ANDROIDX_SUPPORT
import com.android.tools.idea.templates.TemplateMetadata.ATTR_BUILD_API
import com.android.tools.idea.templates.TemplateMetadata.ATTR_BUILD_API_STRING
import com.android.tools.idea.templates.TemplateMetadata.ATTR_HAS_APPLICATION_THEME
import com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_LAUNCHER
import com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_LIBRARY_MODULE
import com.android.tools.idea.templates.TemplateMetadata.ATTR_KOTLIN_VERSION
import com.android.tools.idea.templates.TemplateMetadata.ATTR_LANGUAGE
import com.android.tools.idea.templates.TemplateMetadata.ATTR_MIN_API
import com.android.tools.idea.templates.TemplateMetadata.ATTR_MIN_API_LEVEL
import com.android.tools.idea.templates.TemplateMetadata.ATTR_PACKAGE_NAME
import com.android.tools.idea.templates.TemplateMetadata.ATTR_TARGET_API
import com.android.tools.idea.templates.TemplateMetadata.ATTR_TARGET_API_STRING
import com.android.tools.idea.templates.TemplateMetadata.ATTR_THEME_EXISTS
import com.android.tools.idea.templates.TemplateMetadata.getBuildApiString
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.IdeComponents
import com.google.common.primitives.UnsignedInts.min
import java.io.File
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
open class TemplateTestBase : AndroidGradleTestCase() {
  /**
   * A UsageTracker implementation that allows introspection of logged metrics in tests.
   */
  private lateinit var usageTracker: TestUsageTracker

  override fun createDefaultProject() = false

  override fun setUp() {
    super.setUp()
    usageTracker = TestUsageTracker(VirtualTimeScheduler())
    setWriterForTest(usageTracker)
    apiSensitiveTemplate = true

    // Replace the default RepositoryUrlManager with one that enables repository checks in tests. (myForceRepositoryChecksInTests)
    // This is necessary to fully resolve dynamic gradle coordinates such as ...:appcompat-v7:+ => appcompat-v7:25.3.1
    // keeping it exactly the same as they are resolved within the NPW flow.
    IdeComponents(null, testRootDisposable).replaceApplicationService(
      RepositoryUrlManager::class.java,
      RepositoryUrlManager(IdeGoogleMavenRepository, OfflineIdeGoogleMavenRepository, true))
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
   * If true, check this template with all the interesting ([isInterestingApiLevel]) API versions.
   */
  protected var apiSensitiveTemplate = false

  protected val withKotlin = { templateMap: MutableMap<String, Any>, projectMap: MutableMap<String, Any> ->
    projectMap[ATTR_KOTLIN_VERSION] = getKotlinVersionForTests()
    projectMap[ATTR_LANGUAGE] = Language.KOTLIN.toString()
    templateMap[ATTR_LANGUAGE] = Language.KOTLIN.toString()
    templateMap[ATTR_PACKAGE_NAME] = "test.pkg.in" // Add in a Kotlin keyword ("in") in the package name to trigger escape code too
  }

  /**
   * Checks the given template in the given category
   *
   * @param category          the template category
   * @param name              the template name
   * @param createWithProject whether the template should be created as part of creating the project (only for activities), or whether it
   * should be added as as a separate template into an existing project (which is created first, followed by the template).
   * @param customizer        An instance of [ProjectStateCustomizer] used for providing template and project overrides.
   */
  protected open fun checkCreateTemplate(
    category: String, name: String, createWithProject: Boolean = false, customizer: ProjectStateCustomizer = { _, _ -> }
  ) {
    if (DISABLED) {
      return
    }
    ensureSdkManagerAvailable()
    val templateFile = findTemplate(category, name)
    if (isBroken(templateFile.name)) {
      return
    }
    val templateOverrides = mutableMapOf<String, Any>()
    val projectOverrides = mutableMapOf<String, Any>()
    customizer(templateOverrides, projectOverrides)
    val msToCheck = measureTimeMillis {
      checkTemplate(templateFile, createWithProject, templateOverrides, projectOverrides)
    }
    println("Checked ${templateFile.name} successfully in ${msToCheck}ms")
  }

  /**
   * Generates "interesting" API level combinations (min, target, and build) and runs [checkApiTarget] for each.
   * [TEST_JUST_ONE_MIN_SDK] etc. may be useful for manual testing.
   *
   * @see isInterestingApiLevel
   */
  private fun checkTemplate(
    templateFile: File, createWithProject: Boolean, overrides: Map<String, Any>, projectOverrides: Map<String, Any>
  ) {
    require(!isBroken(templateFile.name))
    val sdkData = AndroidSdks.getInstance().tryToChooseAndroidSdk()!!

    val projectState = createNewProjectState(sdkData, getModuleTemplateForFormFactor(templateFile))
    val moduleState = projectState.moduleTemplateState
    val activityState = projectState.activityTemplateState.apply { setTemplateLocation(templateFile) }

    val moduleMetadata = moduleState.template.metadata!!
    val activityMetadata = activityState.template.metadata!!

    val lowestSupportedApi = min(
      moduleState.getString(ATTR_MIN_API).toInt(),
      moduleMetadata.minSdk,
      activityMetadata.minSdk
    )

    val buildTargets = sdkData.targets.reversed()
      .filter { it.isPlatform && isInterestingApiLevel(it.version.apiLevel, MANUAL_BUILD_API, apiSensitiveTemplate) }
      .takeOneIfTrueElseAll(TEST_JUST_ONE_BUILD_TARGET)

    // Iterate over all (valid) combinations of build target, minSdk and targetSdk
    // TODO: Assert that the SDK manager has a minimum set of SDKs installed needed to be certain the test is comprehensive
    // For now make sure there's at least one
    var ranTest = false
    for (buildTarget in buildTargets) {
      val interestingMinSdks = (lowestSupportedApi..SdkVersionInfo.HIGHEST_KNOWN_API)
        .filter { isInterestingApiLevel(it, MANUAL_MIN_API, apiSensitiveTemplate) }
        .takeOneIfTrueElseAll(TEST_JUST_ONE_MIN_SDK)

      for (minSdk in interestingMinSdks) {
        val interestingTargetSdks = (minSdk..SdkVersionInfo.HIGHEST_KNOWN_API)
          .filter { isInterestingApiLevel(it, MANUAL_TARGET_API, apiSensitiveTemplate) }
          .takeOneIfTrueElseAll(TEST_JUST_ONE_TARGET_SDK_VERSION)
          .filter {
            moduleMetadata.validateTemplate(minSdk, buildTarget.version.apiLevel) == null &&
            activityMetadata.validateTemplate(minSdk, buildTarget.version.apiLevel) == null
          }

        for (targetSdk in interestingTargetSdks) {
          // Should we try all options of theme with all platforms, or just try all platforms, with one setting for each?
          // Doesn't seem like we need to multiply, just pick the best setting that applies instead for each platform.
          val hasEnums = moduleMetadata.parameters.any { it.type == Type.ENUM }
          if (hasEnums && overrides.isEmpty()) {
            // TODO: Handle all enums here. None of the projects have this currently at this level.
            return fail("Not expecting enums at the root level")
          }
          var base = "${templateFile.name}_min_${minSdk}_target_${targetSdk}_build_${buildTarget.version.apiLevel}"
          if (overrides.isNotEmpty()) {
            base += "_overrides"
          }
          checkApiTarget(
            minSdk, targetSdk, buildTarget.version, projectState, base, activityState, overrides, projectOverrides, createWithProject
          )
          ranTest = true
        }
      }
    }
    assertTrue("Didn't run any tests! Make sure you have the right platforms installed.", ranTest)
  }

  /**
   * Checks creating the given project and template for the given SDK versions.
   *
   * If [COMPREHENSIVE] run check for all possible versions of each enum and boolean parameter.
   */
  protected fun checkApiTarget(
    minSdk: Int,
    targetSdk: Int,
    buildVersion: AndroidVersion,
    projectState: TestNewProjectWizardState,
    projectNameBase: String,
    activityState: TestTemplateWizardState,
    overrides: Map<String, Any>,
    projectOverrides: Map<String, Any>,
    createActivity: Boolean = true
  ) {
    fun Option.needsCheck(initial: Any?, moduleState: TestTemplateWizardState): Boolean {
      val projectMinApi = moduleState.getInt(ATTR_MIN_API_LEVEL)
      val projectBuildApi = moduleState.getInt(ATTR_BUILD_API)
      return projectMinApi >= minSdk && projectBuildApi >= minBuild && id != initial
    }

    val moduleState = projectState.moduleTemplateState.apply {
      put(ATTR_MIN_API, minSdk.toString())
      put(ATTR_MIN_API_LEVEL, minSdk)
      put(ATTR_TARGET_API, targetSdk)
      put(ATTR_TARGET_API_STRING, targetSdk.toString())
      put(ATTR_BUILD_API, buildVersion.apiLevel)
      put(ATTR_BUILD_API_STRING, getBuildApiString(buildVersion))
      putAll(projectOverrides)
    }
    val templateState = (if (createActivity) projectState.activityTemplateState else activityState).apply { putAll(overrides) }

    val parameters = if (!createActivity) {
      templateState.setParameterDefaults()
      templateState.template.metadata!!.parameters
    }
    else {
      moduleState.template.metadata!!.parameters + templateState.template.metadata!!.parameters
    }

    parameters.filterNot { it.type == Type.STRING || it.type == Type.SEPARATOR || overrides.containsKey(it.id) }.forEach { p ->
      val initial = p.getDefaultValue(templateState)!!

      fun checkAndRestore(parameterValue: Any) {
        templateState.put(p.id!!, parameterValue)
        val projectName = "${projectNameBase}_${p.id}_$parameterValue"
        checkProject(projectName, projectState, activityState, createActivity)
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
        if (p.id == ATTR_IS_LAUNCHER && createActivity) {
          // ATTR_IS_LAUNCHER is always true when launched from new project
          return@forEach
        }
        checkAndRestore(!(initial as Boolean))
      }
    }
    val projectName = "${projectNameBase}_default"
    checkProject(projectName, projectState, activityState, createActivity)
  }

  /**
   * Initializes [ProjectChecker] and runs [ProjectChecker.checkProject] 1-3 times.
   * 1. Normally.
   * 2. Without androidX if it is not required.
   * 3. Library version if it is mobile activity.
   */
  private fun checkProject(
    projectName: String, projectState: TestNewProjectWizardState, activityState: TestTemplateWizardState, createActivity: Boolean
  ) {
    val moduleState = projectState.moduleTemplateState
    val templateMetadata = activityState.template.metadata
    val checkLib = "Activity" == templateMetadata?.category && "Mobile" == templateMetadata.formFactor && !createActivity
    val language = Language.fromName(moduleState[ATTR_LANGUAGE] as String?, Language.JAVA)
    val projectChecker = ProjectChecker(CHECK_LINT, projectState, activityState, usageTracker, language, createActivity)

    if (templateMetadata?.androidXRequired == true) {
      enableAndroidX(moduleState, activityState)
    }
    if (moduleState[ATTR_ANDROIDX_SUPPORT] != true) {
      // Make sure we test all templates against androidx
      enableAndroidX(moduleState, activityState)
      projectChecker.checkProject(projectName + "_x")
      disableAndroidX(moduleState, activityState)
    }
    projectChecker.checkProject(projectName)
    // check that new Activities can be created on lib modules as well as app modules.
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

/**
 * Whether we should run comprehensive tests or not. This flag allows a simple run to just check a small set of
 * template combinations, and when the flag is set on the build server, a much more comprehensive battery of
 * checks to be performed.
 */
private val COMPREHENSIVE =
  System.getProperty("com.android.tools.idea.templates.TemplateTest.COMPREHENSIVE").orEmpty().toBoolean() ||
  "true".equals(System.getenv("com.android.tools.idea.templates.TemplateTest.COMPREHENSIVE"), true)
/**
 * Whether we should run these tests or not.
 */
internal val DISABLED =
  System.getProperty("DISABLE_STUDIO_TEMPLATE_TESTS").orEmpty().toBoolean() ||
  "true".equals(System.getenv("DISABLE_STUDIO_TEMPLATE_TESTS"), true)
/**
 * Whether we should enforce that lint passes cleanly on the projects
 */
internal const val CHECK_LINT = false // Needs work on closing projects cleanly
/**
 * Manual sdk version selections
 */
private val MANUAL_BUILD_API =
  System.getProperty("com.android.tools.idea.templates.TemplateTest.MANUAL_BUILD_API")?.toIntOrNull() ?: -1
private val MANUAL_MIN_API =
  System.getProperty("com.android.tools.idea.templates.TemplateTest.MANUAL_MIN_API")?.toIntOrNull() ?: -1
private val MANUAL_TARGET_API =
  System.getProperty("com.android.tools.idea.templates.TemplateTest.MANUAL_TARGET_API")?.toIntOrNull() ?: -1
/**
 * Flags used to quickly check each template once (for one version), to get
 * quicker feedback on whether something is broken instead of waiting for
 * all the versions for each template first
 */
internal val TEST_FEWER_API_VERSIONS = !COMPREHENSIVE
private val TEST_JUST_ONE_MIN_SDK = !COMPREHENSIVE
private val TEST_JUST_ONE_BUILD_TARGET = !COMPREHENSIVE
private val TEST_JUST_ONE_TARGET_SDK_VERSION = !COMPREHENSIVE
