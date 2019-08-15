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

import com.android.sdklib.IAndroidTarget
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
import com.google.common.base.Stopwatch
import junit.framework.TestCase
import java.io.File

typealias ProjectStateCustomizer = (templateMap: MutableMap<String, Any>, projectMap: MutableMap<String, Any>) -> Unit

/**
 * Test for template instantiation.
 *
 *
 * Remaining work on templates:
 *  * Fix type conversion, to make the service and fragment templates work
 *
 * Remaining work on template test:
 *
 *  * Add mechanism to ensure that test coverage is comprehensive (made difficult by
 *  * Start using new NewProjectModel etc to initialise TemplateParameters and set parameter values
 *  * Fix clean model syncing, and hook up clean lint checks
 *  * We should test more combinations of parameters
 *  * We should test all combinations of build tools
 *  * Test creating a project **without** a template
 *
 */
open class TemplateTestBase : AndroidGradleTestCase() {
  /**
   * A UsageTracker implementation that allows introspection of logged metrics in tests.
   */
  private var usageTracker: TestUsageTracker? = null

  override fun createDefaultProject(): Boolean {
    // We'll be creating projects manually except for the following tests
    val testName: String = name
    return testName == "testTemplateFormatting" || testName == "testCreateGradleWrapper"
  }

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    usageTracker = TestUsageTracker(VirtualTimeScheduler())
    setWriterForTest(usageTracker!!)
    myApiSensitiveTemplate = true

    // Replace the default RepositoryUrlManager with one that enables repository checks in tests. (myForceRepositoryChecksInTests)
    // This is necessary to fully resolve dynamic gradle coordinates such as ...:appcompat-v7:+ => appcompat-v7:25.3.1
    // keeping it exactly the same as they are resolved within the NPW flow.
    IdeComponents(null, testRootDisposable).replaceApplicationService(
      RepositoryUrlManager::class.java,
      RepositoryUrlManager(IdeGoogleMavenRepository, OfflineIdeGoogleMavenRepository, true))
  }

  @Throws(Exception::class)
  override fun tearDown() {
    try {
      usageTracker!!.close()
      cleanAfterTesting()
    }
    finally {
      super.tearDown()
    }
  }

  /**
   * If true, check this template with all the interesting ([isInterestingApiLevel]) API versions.
   */
  protected var myApiSensitiveTemplate = false

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
   * @param createWithProject whether the template should be created as part of creating the project (which should
   * only be done for activities), or whether it should be added as as a separate template
   * into an existing project (which is created first, followed by the template)
   * @param customizer        An instance of [ProjectStateCustomizer] used for providing template and project overrides.
   * @throws Exception
   */
  @JvmOverloads
  @Throws(Exception::class)
  protected open fun checkCreateTemplate(
    category: String, name: String, createWithProject: Boolean = false, customizer: ProjectStateCustomizer? = null
  ) {
    if (DISABLED) {
      return
    }
    ensureSdkManagerAvailable()
    val templateFile = findTemplate(category, name)
    TestCase.assertNotNull(templateFile)
    if (isBroken(templateFile.name)) {
      return
    }
    val stopwatch: Stopwatch = Stopwatch.createStarted()
    if (customizer == null) {
      checkTemplate(templateFile, createWithProject, mapOf(), mapOf())
    }
    else {
      val templateOverrides = mutableMapOf<String, Any>()
      val projectOverrides = mutableMapOf<String, Any>()
      customizer(templateOverrides, projectOverrides)
      checkTemplate(templateFile, createWithProject, templateOverrides, projectOverrides)
    }
    stopwatch.stop()
    println("Checked " + templateFile.name + " successfully in " + stopwatch.toString())
  }

  @Throws(Exception::class)
  private fun checkTemplate(templateFile: File, createWithProject: Boolean,
                            overrides: Map<String, Any>, projectOverrides: Map<String, Any>) {
    if (isBroken(templateFile.name)) {
      return
    }
    val sdkData = AndroidSdks.getInstance().tryToChooseAndroidSdk()
    val projectState = createNewProjectState(createWithProject, sdkData!!,
                                             getModuleTemplateForFormFactor(
                                               templateFile))
    val projectNameBase: String = templateFile.name
    val activityState = projectState.activityTemplateState
    activityState.setTemplateLocation(templateFile)
    val moduleState = projectState.moduleTemplateState

    // Iterate over all (valid) combinations of build target, minSdk and targetSdk
    // TODO: Assert that the SDK manager has a minimum set of SDKs installed needed to be certain the test is comprehensive
    // For now make sure there's at least one
    var ranTest = false
    val lowestMinApiForProject = (moduleState.get(ATTR_MIN_API) as String).toInt().coerceAtLeast(moduleState.templateMetadata!!.minSdk)
    val targets: Array<IAndroidTarget> = sdkData.targets
    for (i in targets.indices.reversed()) {
      val target = targets[i]
      if (!target.isPlatform) {
        continue
      }
      if (!isInterestingApiLevel(target.version.apiLevel, MANUAL_BUILD_API, myApiSensitiveTemplate)) {
        continue
      }
      val activityMetadata = activityState.templateMetadata
      val moduleMetadata = moduleState.templateMetadata
      val lowestSupportedApi = Math.max(lowestMinApiForProject, activityMetadata!!.minSdk)
      for (minSdk in lowestSupportedApi..SdkVersionInfo.HIGHEST_KNOWN_API) {
        // Don't bother checking *every* single minSdk, just pick some interesting ones

        if (!isInterestingApiLevel(minSdk, MANUAL_MIN_API, myApiSensitiveTemplate)) {
          continue
        }
        for (targetSdk in minSdk..SdkVersionInfo.HIGHEST_KNOWN_API) {
          if (!isInterestingApiLevel(targetSdk, MANUAL_TARGET_API, myApiSensitiveTemplate)) {
            continue
          }
          var status = validateTemplate(moduleMetadata!!, minSdk, target.version.apiLevel)
          if (status != null) {
            continue
          }
          // Also make sure activity is enabled for these versions
          status = validateTemplate(activityMetadata, minSdk, target.version.apiLevel)
          if (status != null) {
            continue
          }

          // Iterate over all new new project templates

          // Should we try all options of theme with all platforms, or just try all platforms, with one setting for each?
          // Doesn't seem like we need to multiply, just pick the best setting that applies instead for each platform.
          val parameters: Collection<Parameter> = moduleMetadata.parameters
          // Does it have any enums?

          val hasEnums = parameters.stream().anyMatch { p: Parameter -> p.type === Type.ENUM }
          if (!hasEnums || overrides.isNotEmpty()) {
            var base = (projectNameBase
                        + "_min_" + minSdk
                          .toString() + "_target_" + targetSdk
                          .toString() + "_build_" + target.version.apiLevel)
            if (overrides.isNotEmpty()) {
              base += "_overrides"
            }
            checkApiTarget(minSdk, targetSdk, target, projectState, base, activityState, overrides, projectOverrides)
            ranTest = true
          }
          else {
            // Handle all enums here. None of the projects have this currently at this level
            // so we will bite the bullet when we first encounter it.

            TestCase.fail("Not expecting enums at the root level")
          }
          if (TEST_JUST_ONE_TARGET_SDK_VERSION) {
            break
          }
        }
        if (TEST_JUST_ONE_MIN_SDK) {
          break
        }
      }
      if (TEST_JUST_ONE_BUILD_TARGET) {
        break
      }
    }
    TestCase.assertTrue("Didn't run any tests! Make sure you have the right platforms installed.", ranTest)
  }

  /**
   * Checks creating the given project and template for the given SDK versions
   */
  @Throws(Exception::class)
  protected fun checkApiTarget(
    minSdk: Int,
    targetSdk: Int,
    target: IAndroidTarget,
    projectState: TestNewProjectWizardState,
    projectNameBase: String,
    activityState: TestTemplateWizardState?,
    overrides: Map<String, Any>,
    projectOverrides: Map<String, Any>) {
    val moduleState = projectState.moduleTemplateState
    val createActivity = moduleState.get(ATTR_CREATE_ACTIVITY) as Boolean? ?: true
    val templateState = (if (createActivity) projectState.activityTemplateState else activityState)!!
    TestCase.assertNotNull(templateState)
    moduleState.put(ATTR_MIN_API, minSdk.toString())
    moduleState.put(ATTR_MIN_API_LEVEL, minSdk)
    moduleState.put(ATTR_TARGET_API, targetSdk)
    moduleState.put(ATTR_TARGET_API_STRING, targetSdk.toString())
    moduleState.put(ATTR_BUILD_API, target.version.apiLevel)
    moduleState.put(ATTR_BUILD_API_STRING, getBuildApiString(target.version))

    // Next check all other parameters, cycling through booleans and enums.
    val templateHandler = templateState.template
    assertNotNull(templateHandler)
    val template = templateHandler.metadata
    assertNotNull(template)
    var parameters = template!!.parameters
    if (!createActivity) {
      templateState.setParameterDefaults()
    }
    else {
      val moduleMetadata = moduleState.template.metadata
      TestCase.assertNotNull(moduleMetadata)
      parameters = parameters + moduleMetadata!!.parameters
    }
    for ((key, value) in overrides) {
      templateState.put(key, value)
    }
    for ((key, value) in projectOverrides) {
      moduleState.put(key, value)
    }
    var projectName: String
    for (parameter in parameters) {
      if (parameter.type === Type.SEPARATOR || parameter.type === Type.STRING) {
        // TODO: Consider whether we should attempt some strings here
        continue
      }
      if (!COMPREHENSIVE && SKIPPABLE_PARAMETERS.contains(parameter.id)) {
        continue
      }
      if (overrides.isNotEmpty() && overrides.containsKey(parameter.id)) {
        continue
      }

      // revert to this one after cycling,
      val initial = parameter.getDefaultValue(templateState)
      if (parameter.type === Type.ENUM) {
        val options = parameter.options
        for (element in options) {
          val (optionId, optionMinSdk, optionMinBuildApi) = getOption(element)
          val projectMinApi = moduleState.getInt(ATTR_MIN_API_LEVEL)
          val projectBuildApi = moduleState.getInt(ATTR_BUILD_API)
          if (projectMinApi >= optionMinSdk && projectBuildApi >= optionMinBuildApi && optionId != initial) {
            templateState.put(parameter.id!!, optionId)
            projectName = projectNameBase + "_" + parameter.id + "_" + optionId
            checkProject(projectName, projectState, activityState)
            if (!COMPREHENSIVE) {
              break
            }
          }
        }
      }
      else {
        assert(parameter.type === Type.BOOLEAN)
        if (parameter.id == ATTR_IS_LAUNCHER && createActivity) {
          // Skipping this one: always true when launched from new project
          continue
        }
        val initialValue = initial as Boolean
        // For boolean values, only run checkProject in the non-default setting.
        // The default value is already used when running checkProject in the default state for all variables.
        val value = !initialValue
        templateState.put(parameter.id!!, value)
        projectName = projectNameBase + "_" + parameter.id + "_" + value
        checkProject(projectName, projectState, activityState)
      }
      templateState.put(parameter.id!!, initial)
    }
    projectName = projectNameBase + "_default"
    checkProject(projectName, projectState, activityState)
  }

  @Throws(Exception::class)
  private fun checkProject(projectName: String, projectState: TestNewProjectWizardState, activityState: TestTemplateWizardState?) {
    val moduleState = projectState.moduleTemplateState
    var checkLib = false
    if (activityState != null) {
      val template = activityState.template
      val templateMetadata = template.metadata!!
      checkLib = "Activity" == templateMetadata.category && "Mobile" == templateMetadata.formFactor &&
                 !moduleState.getBoolean(ATTR_CREATE_ACTIVITY)
      if (templateMetadata.androidXRequired) {
        setAndroidSupport(true, moduleState, activityState)
      }
    }

    val language = Language.fromName(moduleState[ATTR_LANGUAGE] as String?, Language.JAVA)

    val projectChecker = ProjectChecker(CHECK_LINT, projectState, activityState, usageTracker!!, language)
    if (moduleState.get(ATTR_ANDROIDX_SUPPORT) != true) {
      // Make sure we test all templates against androidx
      setAndroidSupport(true, moduleState, activityState)
      projectChecker.checkProjectNow(projectName + "_x")
      setAndroidSupport(false, moduleState, activityState)
    }
    if (checkLib) {
      moduleState.put(ATTR_IS_LIBRARY_MODULE, false)
      activityState!!.put(ATTR_IS_LIBRARY_MODULE, false)
      activityState.put(ATTR_HAS_APPLICATION_THEME, true)
    }
    projectChecker.checkProjectNow(projectName)

    // check that new Activities can be created on lib modules as well as app modules.
    if (checkLib) {
      moduleState.put(ATTR_IS_LIBRARY_MODULE, true)
      activityState!!.put(ATTR_IS_LIBRARY_MODULE, true)
      activityState.put(ATTR_HAS_APPLICATION_THEME, false)
      // For a library project a theme doesn't exist. This is derived in the IDE using FmGetApplicationThemeMethod
      moduleState.put(ATTR_THEME_EXISTS, false)
      projectChecker.checkProjectNow(projectName + "_lib")
    }
  }

  @MustBeDocumented
  @Retention(AnnotationRetention.RUNTIME)
  @Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
  annotation class TemplateCheck

  companion object {
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
    @JvmStatic
    protected val DISABLED =
      System.getProperty("DISABLE_STUDIO_TEMPLATE_TESTS").orEmpty().toBoolean() ||
      "true".equals(System.getenv("DISABLE_STUDIO_TEMPLATE_TESTS"), true)

    /**
     * Whether we should enforce that lint passes cleanly on the projects
     */
    internal const val CHECK_LINT = false // Needs work on closing projects cleanly

    /**
     * Manual sdk version selections
     */
    private val MANUAL_BUILD_API = Integer.parseInt(
      System.getProperty("com.android.tools.idea.templates.TemplateTest.MANUAL_BUILD_API", "-1"))
    private val MANUAL_MIN_API = Integer.parseInt(
      System.getProperty("com.android.tools.idea.templates.TemplateTest.MANUAL_MIN_API", "-1"))
    private val MANUAL_TARGET_API = Integer.parseInt(
      System.getProperty("com.android.tools.idea.templates.TemplateTest.MANUAL_TARGET_API", "-1"))
    /**
     * The following templates parameters are not very interesting (change only one small bit of text etc).
     * We can skip them when not running in comprehensive mode.
     * TODO(qumeric): update or remove
     */
    private val SKIPPABLE_PARAMETERS = setOf<String>()
    /**
     * Flags used to quickly check each template once (for one version), to get
     * quicker feedback on whether something is broken instead of waiting for
     * all the versions for each template first
     */
    val TEST_FEWER_API_VERSIONS = !COMPREHENSIVE
    private val TEST_JUST_ONE_MIN_SDK = !COMPREHENSIVE
    private val TEST_JUST_ONE_BUILD_TARGET = !COMPREHENSIVE
    private val TEST_JUST_ONE_TARGET_SDK_VERSION = !COMPREHENSIVE
    private var ourValidatedTemplateManager = false
    // TODO: this is used only in TemplateTest. We should pass this value without changing template values.
    internal const val ATTR_CREATE_ACTIVITY = "createActivity"
  }
}

