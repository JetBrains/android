/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.templates.diff

import com.android.test.testutils.TestUtils
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate
import com.android.tools.idea.npw.model.RenderTemplateModel
import com.android.tools.idea.npw.template.ModuleTemplateDataBuilder
import com.android.tools.idea.npw.template.ProjectTemplateDataBuilder
import com.android.tools.idea.npw.template.TemplateResolver
import com.android.tools.idea.templates.diff.TemplateDiffTestUtils.getPinnedAgpVersion
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.StringParameter
import com.intellij.openapi.project.Project
import com.intellij.testFramework.DisposableRule
import org.jetbrains.android.AndroidTestBase
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import kotlin.system.measureTimeMillis

/**
 * Template test that generates the template files and diffs them against golden files located in
 * android-templates/testData/golden
 *
 * For context and instructions on running and generating golden files, see go/template-diff-tests
 */
@RunWith(Parameterized::class)
class TemplateDiffTest(private val testMode: TestMode) {
  @get:Rule
  val projectRule: TestRule =
    if (shouldUseGradle()) AndroidGradleProjectRule() else AndroidProjectRule.withAndroidModels()

  @get:Rule val disposableRule = DisposableRule()

  companion object {
    /** Keeps track of whether the previous parameterized test failed */
    private var validationFailed = false

    /**
     * Utilizes parameterized test to decide which modes to run the test in. When DIFFING the
     * template-generated files against golden files, we do not run Gradle sync, to keep the test
     * fast.
     *
     * When we need to validate and generate the golden files however, we run the first part,
     * VALIDATING, with Gradle sync, which calls into BaselineValidator that also builds and Lints.
     * Then, after the template is validated, we generate the golden files WITHOUT Gradle sync, to
     * have them be diff-able without syncing.
     */
    @JvmStatic
    @Parameters(name = "{0}")
    fun data(): List<TestMode> {
      return if (shouldGenerateGolden()) {
        listOf(TestMode.VALIDATING, TestMode.GENERATING)
      } else {
        listOf(TestMode.DIFFING)
      }
    }

    /**
     * Gets the system property for whether to generate and overwrite the golden files. This can be
     * run from Bazel with the option: --test_env=GENERATE_GOLDEN=true
     *
     * Or from IDEA by setting the environment variable: GENERATE_GOLDEN=true
     */
    private fun shouldGenerateGolden(): Boolean {
      return System.getenv("GENERATE_GOLDEN")?.equals("true") ?: false
    }
  }

  @Before
  fun setUp() {
    // This is to enforce that new or changed dependencies are added to the BUILD file
    assertNotNull(
      "TemplateDiffTest golden file generator must be run from Bazel! See go/template-diff-tests",
      System.getenv("TEST_UNDECLARED_OUTPUTS_DIR")
    )

    assertFalse("Previous validation failed", validationFailed)

    println("Current test mode: $testMode")
    if (testMode != TestMode.DIFFING) {
      validationFailed = true
    }

    getPinnedAgpVersion().agpVersion?.let { StudioFlags.AGP_VERSION_TO_USE.override(it) }
  }

  @After
  fun tearDown() {
    StudioFlags.AGP_VERSION_TO_USE.clearOverride()
  }

  enum class TestMode {
    DIFFING,
    VALIDATING,
    GENERATING
  }

  private fun shouldUseGradle(): Boolean {
    return when (testMode) {
      TestMode.DIFFING -> false
      TestMode.VALIDATING -> true
      TestMode.GENERATING -> false
    }
  }

  /**
   * Checks the given template in the given category. Supports overridden template values.
   *
   * @param name the template name
   * @param customizers An instance of [ProjectStateCustomizer]s used for providing template and
   *   project overrides.
   */
  private fun checkCreateTemplate(
    name: String,
    vararg customizers: ProjectStateCustomizer,
    templateStateCustomizer: TemplateStateCustomizer = mapOf(),
    category: Category? = null,
    formFactor: FormFactor? = null,
  ) {
    AndroidTestBase.ensureSdkManagerAvailable(disposableRule.disposable)
    val template = TemplateResolver.getTemplateByName(name, category, formFactor)!!

    val goldenDirName = findEnclosingTestMethodName()

    templateStateCustomizer.forEach { (parameterName: String, overrideValue: String) ->
      val p = template.parameters.find { it.name == parameterName }!! as StringParameter
      p.value = overrideValue
    }

    val msToCheck = measureTimeMillis {
      val project: Project = getProject()
      val projectRenderer: ProjectRenderer =
        when (testMode) {
          TestMode.DIFFING -> ProjectDiffer(template, goldenDirName)
          TestMode.VALIDATING ->
            BaselineValidator(template, goldenDirName, projectRule as AndroidGradleProjectRule)
          TestMode.GENERATING -> BaselineGenerator(template, goldenDirName)
        }

      // TODO: We need to check more combinations of different moduleData/template params here.
      // Running once to make it as easy as possible.
      projectRenderer.renderProject(project, *customizers)
    }
    println("Checked $name ($goldenDirName) successfully in ${msToCheck}ms\n")
    validationFailed = false
  }

  private fun getProject() =
    if (shouldUseGradle()) {
      (projectRule as AndroidGradleProjectRule).project
    } else {
      (projectRule as AndroidProjectRule).project
    }

  /**
   * Goes up the stack trace to find the closest @Test method that this was called from. This will
   * be used as a unique identifier for the golden directory name
   */
  private fun findEnclosingTestMethodName(): String {
    val stackTrace = Thread.currentThread().stackTrace
    for (i in 2..stackTrace.size) {
      val element = stackTrace[i]

      val methodName = element.methodName
      val clazz = Class.forName(element.className)
      try {
        val method = clazz.getDeclaredMethod(methodName)
        if (method.getAnnotation(Test::class.java) != null) {
          println("Using @Test method name: $methodName")
          return methodName
        }
      } catch (_: NoSuchMethodException) {
        // Kt methods with optional parameters don't seem to play well
      }
    }
    throw RuntimeException("Must be called from a @Test")
  }

  private fun withKotlin(
    kotlinVersion: String = TestUtils.KOTLIN_VERSION_FOR_TESTS
  ): ProjectStateCustomizer =
    { _: ModuleTemplateDataBuilder, projectData: ProjectTemplateDataBuilder ->
      projectData.language = Language.Kotlin
      // Use the Kotlin version for tests
      projectData.kotlinVersion = kotlinVersion
    }

  private val withSpecificKotlin: ProjectStateCustomizer =
    withKotlin(RenderTemplateModel.getComposeKotlinVersion())

  @Suppress("SameParameterValue")
  private fun withApplicationId(applicationId: String): ProjectStateCustomizer =
    { _: ModuleTemplateDataBuilder, projectData: ProjectTemplateDataBuilder ->
      projectData.applicationPackage = applicationId
    }

  @Suppress("SameParameterValue")
  private fun withPackage(packageName: String): ProjectStateCustomizer =
    { moduleData: ModuleTemplateDataBuilder, projectData: ProjectTemplateDataBuilder ->
      moduleData.packageName = packageName
      val paths =
        GradleAndroidModuleTemplate.createDefaultModuleTemplate(getProject(), moduleData.name!!)
          .paths
      moduleData.setModuleRoots(paths, projectData.topOut!!.path, moduleData.name!!, packageName)
    }

  /*
   * Tests for individual activity templates go below here. Each test method should only test one
   * template parameter combination, because the test method name is used as the directory name for
   * the golden files.
   */
  @Test
  fun testNewEmptyViewsActivity() {
    checkCreateTemplate("Empty Views Activity")
  }

  @Test
  fun testNewEmptyViewsActivity_notInRootPackage() {
    checkCreateTemplate(
      "Empty Views Activity",
      withApplicationId("com.mycompany.myapp"),
      withPackage("com.mycompany.myapp.subpackage")
    )
  }

  @Test
  fun testNewEmptyViewsActivityKotlin() {
    checkCreateTemplate("Empty Views Activity", withKotlin())
  }

  @Test
  fun testNewEmptyViewsActivityKotlin_notInRootPackage() {
    checkCreateTemplate(
      "Empty Views Activity",
      withKotlin(),
      withApplicationId("com.mycompany.myapp"),
      withPackage("com.mycompany.myapp.subpackage")
    )
  }

  @Test
  fun testNewBasicViewsActivity() {
    checkCreateTemplate("Basic Views Activity")
  }

  @Test
  fun testNewBasicActivityMaterial3() {
    val withMaterial3: ProjectStateCustomizer =
      { moduleData: ModuleTemplateDataBuilder, _: ProjectTemplateDataBuilder ->
        moduleData.isMaterial3 = true
      }
    checkCreateTemplate("Basic Views Activity", withKotlin(), withMaterial3)
  }

  @Test
  fun testNewViewModelActivity() {
    checkCreateTemplate("Fragment + ViewModel")
  }

  @Test
  fun testNewViewModelActivityWithKotlin() {
    checkCreateTemplate("Fragment + ViewModel", withKotlin())
  }

  @Test
  fun testNewTabbedActivity() {
    checkCreateTemplate("Tabbed Views Activity")
  }

  @Test
  fun testNewTabbedActivityWithKotlin() {
    checkCreateTemplate("Tabbed Views Activity", withKotlin())
  }

  @Test
  fun testNewNavigationDrawerActivity() {
    checkCreateTemplate("Navigation Drawer Views Activity")
  }

  @Test
  fun testNewNavigationDrawerActivityWithKotlin() {
    checkCreateTemplate("Navigation Drawer Views Activity", withKotlin())
  }

  @Test
  fun testNewPrimaryDetailFlow() {
    checkCreateTemplate("Primary/Detail Views Flow")
  }

  @Test
  fun testNewPrimaryDetailFlowWithKotlin() {
    checkCreateTemplate("Primary/Detail Views Flow", withKotlin())
  }

  @Test
  fun testNewFullscreenActivity() {
    checkCreateTemplate("Fullscreen Views Activity")
  }

  @Test
  fun testNewFullscreenActivityWithKotlin() {
    checkCreateTemplate("Fullscreen Views Activity", withKotlin())
  }

  @Test
  fun testNewFullscreenActivity_activityNotInRootPackage() {
    checkCreateTemplate(
      "Fullscreen Views Activity",
      withApplicationId("com.mycompany.myapp"),
      withPackage("com.mycompany.myapp.subpackage")
    )
  }

  @Test
  fun testNewFullscreenActivityWithKotlin_activityNotInRootPackage() {
    checkCreateTemplate(
      "Fullscreen Views Activity",
      withKotlin(),
      withApplicationId("com.mycompany.myapp"),
      withPackage("com.mycompany.myapp.subpackage")
    )
  }

  @Test
  fun testNewLoginActivity() {
    checkCreateTemplate("Login Views Activity")
  }

  @Test
  fun testNewLoginActivityWithKotlin() {
    checkCreateTemplate("Login Views Activity", withKotlin())
  }

  @Test
  fun testNewScrollingActivity() {
    checkCreateTemplate("Scrolling Views Activity")
  }

  @Test
  fun testNewScrollingActivityWithKotlin() {
    checkCreateTemplate("Scrolling Views Activity", withKotlin())
  }

  @Test
  fun testNewSettingsActivity() {
    checkCreateTemplate("Settings Views Activity")
  }

  @Test
  fun testNewSettingsActivityWithKotlin() {
    checkCreateTemplate("Settings Views Activity", withKotlin())
  }

  @Test
  fun testBottomNavigationActivity() {
    checkCreateTemplate("Bottom Navigation Views Activity")
  }

  @Test
  fun testBottomNavigationActivityWithKotlin() {
    checkCreateTemplate("Bottom Navigation Views Activity", withKotlin())
  }

  @Test
  fun testGoogleAdMobAdsActivity() {
    checkCreateTemplate("Google AdMob Ads Views Activity")
  }

  @Test
  fun testGoogleAdMobAdsActivityWithKotlin() {
    checkCreateTemplate("Google AdMob Ads Views Activity", withKotlin())
  }

  @Test
  fun testGoogleMapsActivity() {
    checkCreateTemplate("Google Maps Views Activity")
  }

  @Test
  fun testGoogleMapsActivityWithKotlin() {
    checkCreateTemplate("Google Maps Views Activity", withKotlin())
  }

  @Test
  fun testGooglePayActivity() {
    checkCreateTemplate("Google Pay Views Activity")
  }

  @Test
  fun testGooglePayActivityWithKotlin() {
    checkCreateTemplate("Google Pay Views Activity", withKotlin())
  }

  @Test
  fun testGoogleWalletActivity() {
    checkCreateTemplate("Google Wallet Activity")
  }

  @Test
  fun testGoogleWalletActivityWithKotlin() {
    checkCreateTemplate("Google Wallet Activity", withKotlin())
  }

  @Test
  fun testGameActivity() {
    checkCreateTemplate("Game Activity (C++)")
  }

  @Test
  fun testGameActivityWithKotlin() {
    checkCreateTemplate("Game Activity (C++)", withKotlin())
  }

  @Test
  fun testComposeActivityMaterial3() {
    checkCreateTemplate("Empty Activity", withSpecificKotlin) // Compose is always Kotlin
  }

  @Test
  fun testResponsiveActivity() {
    checkCreateTemplate("Responsive Views Activity")
  }

  @Test
  fun testResponsiveActivityWithKotlin() {
    checkCreateTemplate("Responsive Views Activity", withKotlin())
  }

  @Test
  fun testNewComposeWearActivity() {
    checkCreateTemplate("Empty Wear App", withSpecificKotlin)
  }

  @Test
  fun testNewComposeWearActivityWithTileAndComplication() {
    checkCreateTemplate("Empty Wear App With Tile And Complication", withSpecificKotlin)
  }

  @Test
  fun testNewTvActivity() {
    checkCreateTemplate("Android TV Blank Views Activity")
  }

  @Test
  fun testNewTvActivityWithKotlin() {
    checkCreateTemplate("Android TV Blank Views Activity", withKotlin())
  }

  @Test
  fun testNewEmptyComposeForTvActivity() {
    checkCreateTemplate("Empty Activity", withSpecificKotlin, formFactor = FormFactor.Tv)
  }

  @Test
  fun testNewNativeCppActivity() {
    checkCreateTemplate("Native C++")
  }

  @Test
  fun testNewNativeCppActivityWithKotlin() {
    checkCreateTemplate("Native C++", withKotlin())
  }

  /*
   * Tests for individual fragment templates go below here. Each test method should only test one
   * template parameter combination, because the test method name is used as the directory name for
   * the golden files.
   */
  @Test
  fun testNewListFragment() {
    checkCreateTemplate("Fragment (List)")
  }

  @Test
  fun testNewListFragmentWithKotlin() {
    checkCreateTemplate("Fragment (List)", withKotlin())
  }

  @Test
  fun testNewModalBottomSheet() {
    checkCreateTemplate("Modal Bottom Sheet")
  }

  @Test
  fun testNewModalBottomSheetWithKotlin() {
    checkCreateTemplate("Modal Bottom Sheet", withKotlin())
  }

  @Test
  fun testNewBlankFragment() {
    checkCreateTemplate("Fragment (Blank)")
  }

  @Test
  fun testNewBlankFragmentWithKotlin() {
    checkCreateTemplate("Fragment (Blank)", withKotlin())
  }

  @Test
  fun testNewSettingsFragment() {
    checkCreateTemplate("Settings Fragment")
  }

  @Test
  fun testNewSettingsFragmentWithKotlin() {
    checkCreateTemplate("Settings Fragment", withKotlin())
  }

  @Test
  fun testNewViewModelFragment() {
    checkCreateTemplate("Fragment (with ViewModel)")
  }

  @Test
  fun testNewViewModelFragmentWithKotlin() {
    checkCreateTemplate("Fragment (with ViewModel)", withKotlin())
  }

  @Test
  fun testNewScrollingFragment() {
    checkCreateTemplate("Scrolling Fragment")
  }

  @Test
  fun testNewScrollingFragmentWithKotlin() {
    checkCreateTemplate("Scrolling Fragment", withKotlin())
  }

  @Test
  fun testNewFullscreenFragment() {
    checkCreateTemplate("Fullscreen Fragment")
  }

  @Test
  fun testNewFullscreenFragmentWithKotlin() {
    checkCreateTemplate("Fullscreen Fragment", withKotlin())
  }

  @Test
  fun testNewGoogleMapsFragment() {
    checkCreateTemplate("Google Maps Fragment")
  }

  @Test
  fun testNewGoogleMapsFragmentWithKotlin() {
    checkCreateTemplate("Google Maps Fragment", withKotlin())
  }

  @Test
  fun testNewGoogleAdMobFragment() {
    checkCreateTemplate("Google AdMob Ads Fragment")
  }

  @Test
  fun testNewGoogleAdMobFragmentWithKotlin() {
    checkCreateTemplate("Google AdMob Ads Fragment", withKotlin())
  }

  @Test
  fun testLoginFragment() {
    checkCreateTemplate("Login Fragment")
  }

  @Test
  fun testLoginFragmentWithKotlin() {
    checkCreateTemplate("Login Fragment", withKotlin())
  }

  /*
   * Tests for individual miscellaneous templates go below here. Each test method should only test
   * one template parameter combination, because the test method name is used as the directory name
   * for the golden files.
   */
  @Test
  fun testNewAppWidget() {
    checkCreateTemplate("App Widget")
  }

  @Test
  fun testNewBroadcastReceiver() {
    checkCreateTemplate("Broadcast Receiver")
  }

  @Test
  fun testNewBroadcastReceiverWithKotlin() {
    checkCreateTemplate("Broadcast Receiver", withKotlin())
  }

  @Test
  fun testNewContentProvider() {
    checkCreateTemplate("Content Provider")
  }

  @Test
  fun testNewContentProviderWithKotlin() {
    checkCreateTemplate("Content Provider", withKotlin())
  }

  @Test
  fun testNewSliceProvider() {
    checkCreateTemplate("Slice Provider")
  }

  @Test
  fun testNewSliceProviderWithKotlin() {
    checkCreateTemplate("Slice Provider", withKotlin())
  }

  @Test
  fun testNewCustomView() {
    checkCreateTemplate("Custom View")
  }

  @Test
  fun testNewIntentService() {
    checkCreateTemplate("Service (IntentService)")
  }

  @Test
  fun testNewIntentServiceWithKotlin() {
    checkCreateTemplate("Service (IntentService)", withKotlin())
  }

  @Test
  fun testNewService() {
    checkCreateTemplate("Service")
  }

  @Test
  fun testNewServiceWithKotlin() {
    checkCreateTemplate("Service", withKotlin())
  }

  @Test
  fun testAndroidManifest() {
    checkCreateTemplate("Android Manifest File")
  }

  @Test
  fun testNewAidlFile() {
    checkCreateTemplate("AIDL File")
  }

  @Test
  fun testNewAppActionsXmlFile() {
    checkCreateTemplate("App Actions XML File (deprecated)")
  }

  @Test
  fun testNewLayoutXmlFile() {
    checkCreateTemplate("Layout XML File")
  }

  @Test
  fun testNewValuesXmlFile() {
    checkCreateTemplate("Values XML File")
  }

  @Test
  fun testNewShortcutsXmlFile() {
    checkCreateTemplate("Shortcuts XML File")
  }

  @Test
  fun testAutomotiveMessagingService() {
    checkCreateTemplate("Messaging Service")
  }

  @Test
  fun testAutomotiveMessagingServiceWithKotlin() {
    checkCreateTemplate("Messaging Service", withKotlin())
  }

  @Test
  fun testAutomotiveMediaService() {
    checkCreateTemplate("Media Service")
  }

  @Test
  fun testAutomotiveMediaServiceWithKotlin() {
    checkCreateTemplate("Media Service", withKotlin())
  }
}

typealias TemplateStateCustomizer = Map<String, String>

typealias ProjectStateCustomizer = (ModuleTemplateDataBuilder, ProjectTemplateDataBuilder) -> Unit
