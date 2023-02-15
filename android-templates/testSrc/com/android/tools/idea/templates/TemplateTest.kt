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

import com.android.testutils.TestUtils
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate
import com.android.tools.idea.gradle.repositories.IdeGoogleMavenRepository
import com.android.tools.idea.gradle.repositories.OfflineIdeGoogleMavenRepository
import com.android.tools.idea.gradle.repositories.RepositoryUrlManager
import com.android.tools.idea.npw.model.RenderTemplateModel
import com.android.tools.idea.npw.template.ModuleTemplateDataBuilder
import com.android.tools.idea.npw.template.ProjectTemplateDataBuilder
import com.android.tools.idea.npw.template.TemplateResolver
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.IdeComponents
import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.StringParameter
import com.android.tools.idea.wizard.template.WizardUiContext
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.intellij.testFramework.DisposableRule
import org.jetbrains.android.AndroidTestBase
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberFunctions
import kotlin.system.measureTimeMillis

/**
 * Test for template instantiation.
 *
 * Remaining work on template test:
 * - Start using new NewProjectModel etc to initialise TemplateParameters and set parameter values.
 * - Fix clean model syncing, and hook up clean lint checks.
 *
 * WARNING: This test is designed to be run by TemplateTestSuite. Templates that use viewBinding will fail
 * when tested directly from this class; use bazel instead.
 */
class TemplateTest {
  private var runTemplateCoverageOnly: Boolean = false

  @get:Rule
  val projectRule = AndroidGradleProjectRule()

  @get:Rule
  val disposableRule = DisposableRule()

  @get:Rule
  var exceptionRule: ExpectedException = ExpectedException.none()

  /** A UsageTracker implementation that allows introspection of logged metrics in tests. */
  private val usageTracker = TestUsageTracker(VirtualTimeScheduler())

  // Set of templates tested with unit test - Used to detect templates without tests
  private val templatesChecked = mutableSetOf<String>()

  @Before
  fun setUp() {
    UsageTracker.setWriterForTest(usageTracker)

    /**
     * Replace the default RepositoryUrlManager with one that enables repository checks in tests.
     * This is necessary to fully resolve dynamic gradle coordinates (e.g. appcompat-v7:+ => appcompat-v7:25.3.1).
     * It will keep coordinates exactly the same as they are resolved within the NPW flow.
     *
     * @see RepositoryUrlManager.forceRepositoryChecksInTests
     */
    IdeComponents(null, disposableRule.disposable).replaceApplicationService(
      RepositoryUrlManager::class.java,
      RepositoryUrlManager(IdeGoogleMavenRepository, OfflineIdeGoogleMavenRepository, true)
    )
    StudioFlags.NPW_ENABLE_GRADLE_VERSION_CATALOG.override(false)
  }

  @After
  fun tearDown() {
    usageTracker.close()
    UsageTracker.cleanAfterTesting()
    StudioFlags.NPW_ENABLE_GRADLE_VERSION_CATALOG.clearOverride()
  }

  /**
   * Checks the given template in the given category. Supports overridden template values.
   *
   * @param name              the template name
   * @param customizers        An instance of [ProjectStateCustomizer]s used for providing template and project overrides.
   */
  private fun checkCreateTemplate(
    name: String,
    vararg customizers: ProjectStateCustomizer,
    templateStateCustomizer: TemplateStateCustomizer = mapOf(),
    category: Category? = null,
    formFactor: FormFactor? = null,
    avoidModifiedModuleName: Boolean = false
  ) {
    if (runTemplateCoverageOnly) {
      templatesChecked.add(name)
      return
    }
    if (DISABLED || isBroken(name)) {
      return
    }
    AndroidTestBase.ensureSdkManagerAvailable(disposableRule.disposable)
    val template = TemplateResolver.getTemplateByName(name, category, formFactor)!!

    // Name must be title-cased
    assertThat(template.name).isEqualTo(template.name.split(" ").joinToString(" ") { it.capitalize() })

    // Description and help should not end with spaces or "."
    assertThat(template.description).doesNotContainMatch("[\\. ]$")
    template.parameters
      .map {parameter -> parameter.help }
      .filter { it != null && !it.endsWith("etc.") }
      .forEach {
        assertThat(it).doesNotContainMatch("[\\. ]$")
      }

    templateStateCustomizer.forEach { (parameterName: String, overrideValue: String) ->
      val p = template.parameters.find { it.name == parameterName }!! as StringParameter
      p.value = overrideValue
    }

    val msToCheck = measureTimeMillis {
      val projectName = "${template.name}_default"
      val projectChecker = ProjectChecker(CHECK_LINT, template, usageTracker)

      // TODO: We need to check more combinations of different moduleData/template params here.
      // Running once to make it as easy as possible.
      projectChecker.checkProject(projectRule, projectName, avoidModifiedModuleName, *customizers)
    }
    println("Checked $name successfully in ${msToCheck}ms")
  }

  @MustBeDocumented
  @Retention(AnnotationRetention.RUNTIME)
  @Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
  annotation class TemplateCheck

  private val withKotlin: ProjectStateCustomizer = { _: ModuleTemplateDataBuilder, projectData: ProjectTemplateDataBuilder ->
    projectData.language = Language.Kotlin
    // Use the Kotlin version for tests
    projectData.kotlinVersion = TestUtils.KOTLIN_VERSION_FOR_TESTS
  }

  private fun withNewLocation(location: String): TemplateStateCustomizer = mapOf(
    "New Folder Location" to location
  )

  private fun withApplicationId(applicationId: String): ProjectStateCustomizer =
    { moduleData: ModuleTemplateDataBuilder, projectData: ProjectTemplateDataBuilder ->
        projectData.applicationPackage = applicationId
    }

  private fun withPackage(packageName: String): ProjectStateCustomizer =
    { moduleData: ModuleTemplateDataBuilder, projectData: ProjectTemplateDataBuilder ->
        moduleData.packageName = packageName
        val paths = GradleAndroidModuleTemplate.createDefaultModuleTemplate(projectRule.project, moduleData.name!!).paths
        moduleData.setModuleRoots(paths, projectData.topOut!!.path, moduleData.name!!, packageName)
    }


  //--- Activity templates ---
  @TemplateCheck
  @Test
  fun testNewBasicActivityMaterial3() {
    val withMaterial3: ProjectStateCustomizer = { moduleData: ModuleTemplateDataBuilder, _: ProjectTemplateDataBuilder ->
      moduleData.isMaterial3 = true
    }
    checkCreateTemplate("Basic Views Activity", withKotlin, withMaterial3)
  }

  @TemplateCheck
  @Test
  fun testNewEmptyViewActivity() {
    checkCreateTemplate("Empty Views Activity")
  }

  @TemplateCheck
  @Test
  fun testNewEmptyViewActivity_notInRootPackage() {
    checkCreateTemplate("Empty Views Activity",
                        withApplicationId("com.mycompany.myapp"),
                        withPackage("com.mycompany.myapp.subpackage"))
  }

  @TemplateCheck
  @Test
  fun testNewEmptyViewActivityWithKotlin() {
    checkCreateTemplate("Empty Views Activity", withKotlin)
  }

  @TemplateCheck
  @Test
  fun testNewEmptyViewActivityWithKotlin_notInRootPackage() {
    checkCreateTemplate("Empty Views Activity",
                        withKotlin,
                        withApplicationId("com.mycompany.myapp"),
                        withPackage("com.mycompany.myapp.subpackage"))
  }

  @TemplateCheck
  @Test
  fun testNewViewModelActivity() {
    checkCreateTemplate("Fragment + ViewModel")
  }

  @TemplateCheck
  @Test
  fun testNewViewModelActivityWithKotlin() {
    checkCreateTemplate("Fragment + ViewModel", withKotlin)
  }

  @TemplateCheck
  @Test
  fun testNewTabbedActivity() {
    checkCreateTemplate("Tabbed Views Activity")
  }

  @TemplateCheck
  @Test
  fun testNewTabbedActivityWithKotlin() {
    checkCreateTemplate("Tabbed Views Activity", withKotlin, avoidModifiedModuleName = true)
  }

  @TemplateCheck
  @Test
  fun testNewNavigationDrawerActivity() {
    checkCreateTemplate("Navigation Drawer Views Activity")
  }

  @TemplateCheck
  @Test
  fun testNewNavigationDrawerActivityWithKotlin() {
    checkCreateTemplate("Navigation Drawer Views Activity", withKotlin, avoidModifiedModuleName = true)
  }

  @TemplateCheck
  @Test
  fun testNewPrimaryDetailFlow() {
    checkCreateTemplate("Primary/Detail Views Flow")
  }

  @TemplateCheck
  @Test
  fun testNewPrimaryDetailFlowWithKotlin() {
    checkCreateTemplate("Primary/Detail Views Flow", withKotlin, avoidModifiedModuleName = true)
  }

  @TemplateCheck
  @Test
  fun testNewFullscreenActivity() {
    checkCreateTemplate("Fullscreen Views Activity")
  }

  @TemplateCheck
  @Test
  fun testNewFullscreenActivityWithKotlin() {
    checkCreateTemplate("Fullscreen Views Activity", withKotlin, avoidModifiedModuleName = true)
  }

  @TemplateCheck
  @Test
  fun testNewFullscreenActivity_activityNotInRootPackage() {
    checkCreateTemplate(
      "Fullscreen Views Activity",
      withApplicationId("com.mycompany.myapp"),
      withPackage("com.mycompany.myapp.subpackage"))
  }

  @TemplateCheck
  @Test
  fun testNewFullscreenActivityWithKotlin_activityNotInRootPackage() {
    checkCreateTemplate(
      "Fullscreen Views Activity",
      withKotlin,
      withApplicationId("com.mycompany.myapp"),
      withPackage("com.mycompany.myapp.subpackage"),
      avoidModifiedModuleName = true
    )
  }

  @TemplateCheck
  @Test
  fun testNewLoginActivity() {
    checkCreateTemplate("Login Views Activity")
  }

  @TemplateCheck
  @Test
  fun testNewLoginActivityWithKotlin() {
    checkCreateTemplate("Login Views Activity", withKotlin, avoidModifiedModuleName = true)
  }

  @TemplateCheck
  @Test
  fun testNewScrollingActivity() {
    checkCreateTemplate("Scrolling Views Activity")
  }

  @TemplateCheck
  @Test
  fun testNewScrollingActivityWithKotlin() {
    checkCreateTemplate("Scrolling Views Activity", withKotlin, avoidModifiedModuleName = true)
  }

  @TemplateCheck
  @Test
  fun testNewSettingsActivity() {
    checkCreateTemplate("Settings Views Activity")
  }

  @TemplateCheck
  @Test
  fun testNewSettingsActivityWithKotlin() {
    checkCreateTemplate("Settings Views Activity", withKotlin)
  }

  @TemplateCheck
  @Test
  fun testBottomNavigationActivity() {
    checkCreateTemplate("Bottom Navigation Views Activity")
  }

  @TemplateCheck
  @Test
  fun testBottomNavigationActivityWithKotlin() {
    checkCreateTemplate("Bottom Navigation Views Activity", withKotlin, avoidModifiedModuleName = true)
  }

  @TemplateCheck
  @Test
  fun testGoogleAdMobAdsActivity() {
    checkCreateTemplate("Google AdMob Ads Views Activity")
  }

  @TemplateCheck
  @Test
  fun testGoogleAdMobAdsActivityWithKotlin() {
    checkCreateTemplate("Google AdMob Ads Views Activity", withKotlin, avoidModifiedModuleName = true)
  }

  @TemplateCheck
  @Test
  fun testGoogleMapsActivity() {
    checkCreateTemplate("Google Maps Views Activity")
  }

  @TemplateCheck
  @Test
  fun testGoogleMapsActivityWithKotlin() {
    checkCreateTemplate("Google Maps Views Activity", withKotlin, avoidModifiedModuleName = true)
  }

  @TemplateCheck
  @Test
  fun testGooglePayActivity() {
    checkCreateTemplate("Google Pay Views Activity")
  }

  @TemplateCheck
  @Test
  fun testGooglePayActivityWithKotlin() {
    checkCreateTemplate("Google Pay Views Activity", withKotlin)
  }

  @TemplateCheck
  @Test
  fun testGoogleWalletActivity() {
    checkCreateTemplate("Google Wallet Activity")
  }

  @TemplateCheck
  @Test
  fun testGoogleWalletActivityWithKotlin() {
    checkCreateTemplate("Google Wallet Activity", withKotlin)
  }

  @TemplateCheck
  @Test
  fun testGameActivity(){
    checkCreateTemplate("Game Activity (C++)", avoidModifiedModuleName = true)
  }

  @TemplateCheck
  @Test
  fun testGameActivityWithKotlin(){
    checkCreateTemplate("Game Activity (C++)", withKotlin, avoidModifiedModuleName = true)
  }

  @TemplateCheck
  @Test
  fun testComposeActivityMaterial3() {
    val withSpecificKotlin: ProjectStateCustomizer = { moduleData: ModuleTemplateDataBuilder, projectData: ProjectTemplateDataBuilder ->
      projectData.language = Language.Kotlin
      projectData.kotlinVersion = RenderTemplateModel.getComposeKotlinVersion(isMaterial3 = true)
    }
    checkCreateTemplate("Empty Activity", withSpecificKotlin) // Compose is always Kotlin
  }

  @TemplateCheck
  @Test
  fun testResponsiveActivity() {
    checkCreateTemplate("Responsive Views Activity")
  }

  @TemplateCheck
  @Test
  fun testResponsiveActivityWithKotlin() {
    checkCreateTemplate("Responsive Views Activity", withKotlin)
  }

  @TemplateCheck
  @Test
  fun testNewComposeWearActivity() {
    val withSpecificKotlin: ProjectStateCustomizer = { moduleData: ModuleTemplateDataBuilder, projectData: ProjectTemplateDataBuilder ->
      projectData.language = Language.Kotlin
      projectData.kotlinVersion = RenderTemplateModel.getComposeKotlinVersion(isMaterial3 = false)
    }
    checkCreateTemplate("Empty Wear App", withSpecificKotlin)
  }

  @TemplateCheck
  @Test
  fun testNewComposeWearActivityWithTileAndComplication() {
    val withSpecificKotlin: ProjectStateCustomizer = { moduleData: ModuleTemplateDataBuilder, projectData: ProjectTemplateDataBuilder ->
      projectData.language = Language.Kotlin
      projectData.kotlinVersion = RenderTemplateModel.getComposeKotlinVersion(isMaterial3 = false)
    }
    checkCreateTemplate("Empty Wear App With Tile And Complication", withSpecificKotlin)
  }

  @TemplateCheck
  @Test
  fun testNewTvActivity() {
    checkCreateTemplate("Android TV Blank Views Activity")
  }

  @TemplateCheck
  @Test
  fun testNewTvActivityWithKotlin() {
    checkCreateTemplate("Android TV Blank Views Activity", withKotlin)
  }

  @TemplateCheck
  @Test
  fun testNewNativeCppActivity() {
    checkCreateTemplate("Native C++")
  }

  @TemplateCheck
  @Test
  fun testNewNativeCppActivityWithKotlin() {
    checkCreateTemplate("Native C++", withKotlin, avoidModifiedModuleName = true)
  }

  //--- Fragment templates ---
  @TemplateCheck
  @Test
  fun testNewListFragment() {
    checkCreateTemplate("Fragment (List)")
  }

  @TemplateCheck
  @Test
  fun testNewListFragmentWithKotlin() {
    checkCreateTemplate("Fragment (List)", withKotlin, avoidModifiedModuleName = true)
  }

  @TemplateCheck
  @Test
  fun testNewModalBottomSheet() {
    checkCreateTemplate("Modal Bottom Sheet")
  }

  @TemplateCheck
  @Test
  fun testNewModalBottomSheetWithKotlin() {
    checkCreateTemplate("Modal Bottom Sheet", withKotlin, avoidModifiedModuleName = true)
  }

  @TemplateCheck
  @Test
  fun testNewBlankFragment() {
    checkCreateTemplate("Fragment (Blank)")
  }

  @TemplateCheck
  @Test
  fun testNewBlankFragmentWithKotlin() {
    checkCreateTemplate("Fragment (Blank)", withKotlin)
  }

  @TemplateCheck
  @Test
  fun testNewSettingsFragment() {
    checkCreateTemplate("Settings Fragment")
  }

  @TemplateCheck
  @Test
  fun testNewSettingsFragmentWithKotlin() {
    checkCreateTemplate("Settings Fragment", withKotlin)
  }

  @TemplateCheck
  @Test
  fun testNewViewModelFragment() {
    checkCreateTemplate("Fragment (with ViewModel)")
  }

  @TemplateCheck
  @Test
  fun testNewViewModelFragmentWithKotlin() {
    checkCreateTemplate("Fragment (with ViewModel)", withKotlin)
  }

  @TemplateCheck
  @Test
  fun testNewScrollingFragment() {
    checkCreateTemplate("Scrolling Fragment")
  }

  @TemplateCheck
  @Test
  fun testNewScrollingFragmentWithKotlin() {
    checkCreateTemplate("Scrolling Fragment", withKotlin)
  }

  @TemplateCheck
  @Test
  fun testNewFullscreenFragment() {
    checkCreateTemplate("Fullscreen Fragment")
  }

  @TemplateCheck
  @Test
  fun testNewFullscreenFragmentWithKotlin() {
    checkCreateTemplate("Fullscreen Fragment", withKotlin, avoidModifiedModuleName = true)
  }

  @TemplateCheck
  @Test
  fun testNewGoogleMapsFragment() {
    checkCreateTemplate("Google Maps Fragment")
  }

  @TemplateCheck
  @Test
  fun testNewGoogleMapsFragmentWithKotlin() {
    checkCreateTemplate("Google Maps Fragment", withKotlin)
  }

  @TemplateCheck
  @Test
  fun testNewGoogleAdMobFragment() {
    checkCreateTemplate("Google AdMob Ads Fragment")
  }

  @TemplateCheck
  @Test
  fun testNewGoogleAdMobFragmentWithKotlin() {
    checkCreateTemplate("Google AdMob Ads Fragment", withKotlin, avoidModifiedModuleName = true)
  }

  @TemplateCheck
  @Test
  fun testLoginFragment() {
    checkCreateTemplate("Login Fragment")
  }

  @TemplateCheck
  @Test
  fun testLoginFragmentWithKotlin() {
    checkCreateTemplate("Login Fragment", withKotlin, avoidModifiedModuleName = true)
  }

  //--- Other templates ---
  @TemplateCheck
  @Test
  fun testNewAppWidget() {
    checkCreateTemplate("App Widget")
  }

  @TemplateCheck
  @Test
  fun testNewBroadcastReceiver() {
    checkCreateTemplate("Broadcast Receiver")
  }

  @TemplateCheck
  @Test
  fun testNewBroadcastReceiverWithKotlin() {
    checkCreateTemplate("Broadcast Receiver", withKotlin)
  }

  @TemplateCheck
  @Test
  fun testNewContentProvider() {
    checkCreateTemplate("Content Provider")
  }

  @TemplateCheck
  @Test
  fun testNewContentProviderWithKotlin() {
    checkCreateTemplate("Content Provider", withKotlin)
  }

  @TemplateCheck
  @Test
  fun testNewSliceProvider() {
    checkCreateTemplate("Slice Provider")
  }

  @TemplateCheck
  @Test
  fun testNewSliceProviderWithKotlin() {
    checkCreateTemplate("Slice Provider", withKotlin)
  }

  @TemplateCheck
  @Test
  fun testNewCustomView() {
    checkCreateTemplate("Custom View")
  }

  @TemplateCheck
  @Test
  fun testNewIntentService() {
    checkCreateTemplate("Service (IntentService)")
  }

  @TemplateCheck
  @Test
  fun testNewIntentServiceWithKotlin() {
    checkCreateTemplate("Service (IntentService)", withKotlin)
  }

  @TemplateCheck
  @Test
  fun testNewService() {
    checkCreateTemplate("Service")
  }

  @TemplateCheck
  @Test
  fun testNewServiceWithKotlin() {
    checkCreateTemplate("Service", withKotlin)
  }

  @TemplateCheck
  @Test
  fun testNewAidlFolders() {
    checkCreateTemplate("AIDL Folder", templateStateCustomizer = withNewLocation("foo"))
  }

  @TemplateCheck
  @Test
  fun testNewAssetsFolders() {
    checkCreateTemplate("Assets Folder", templateStateCustomizer = withNewLocation("src/main/assets"))
  }

  @TemplateCheck
  @Test
  fun testNewFontsFolders() {
    checkCreateTemplate("Font Folder", templateStateCustomizer = withNewLocation("src/main/res/font"))
  }

  @TemplateCheck
  @Test
  fun testNewJavaFolders() {
    checkCreateTemplate("Java Folder", templateStateCustomizer = withNewLocation("src/main/java"))
  }

  @TemplateCheck
  @Test
  fun testNewJniFolders() {
    checkCreateTemplate("JNI Folder", templateStateCustomizer = withNewLocation("src/main/jni"))
  }

  @TemplateCheck
  @Test
  fun testNewRawResourcesFolders() {
    checkCreateTemplate("Raw Resources Folder", templateStateCustomizer = withNewLocation("src/main/res/raw"))
  }

  @TemplateCheck
  @Test
  fun testNewResFolders() {
    checkCreateTemplate("Res Folder", templateStateCustomizer = withNewLocation("src/main/resources"))
  }

  @TemplateCheck
  @Test
  fun testNewJavaResFolders() {
    checkCreateTemplate("Java Resources Folder", templateStateCustomizer = withNewLocation("src/main/resources"))
  }

  @TemplateCheck
  @Test
  fun testNewRenderscriptFolders() {
    checkCreateTemplate("RenderScript Folder", templateStateCustomizer = withNewLocation("src/main/rs"))
  }

  @TemplateCheck
  @Test
  fun testNewXmlRestFolders() {
    checkCreateTemplate("XML Resources Folder", templateStateCustomizer = withNewLocation("src/main/res/xml"))
  }

  @TemplateCheck
  @Test
  fun testAndroidManifest() {
    checkCreateTemplate("Android Manifest File")
  }

  @TemplateCheck
  @Test
  fun testNewFiles() {
    checkCreateTemplate("AIDL File")
    checkCreateTemplate("App Actions XML File (deprecated)")
    checkCreateTemplate("Layout XML File")
    checkCreateTemplate("Values XML File")
    checkCreateTemplate("Shortcuts XML File")
  }

  @TemplateCheck
  @Test
  fun testAutomotiveMessagingService() {
    checkCreateTemplate("Messaging Service")
  }

  @TemplateCheck
  @Test
  fun testAutomotiveMessagingServiceWithKotlin() {
    checkCreateTemplate("Messaging Service", withKotlin)
  }

  @TemplateCheck
  @Test
  fun testAutomotiveMediaService() {
    checkCreateTemplate("Media Service")
  }

  @TemplateCheck
  @Test
  fun testAutomotiveMediaServiceWithKotlin() {
    checkCreateTemplate("Media Service", withKotlin)
  }

  @Test
  fun testWizardUiContext() {
    TemplateResolver.getAllTemplates().filter { it.uiContexts.contains(WizardUiContext.NewProjectExtraDetail) }.forEach {
      // NewProjectExtraDetail should simultaneously declare NewProject
      assertThat(it.uiContexts).contains(WizardUiContext.NewProject)
    }
  }

  @Test
  fun testAllTemplatesCovered() {
    // Create a placeholder version of this class that just collects all the templates it will test when it is run.
    val templateTest = TemplateTest().apply { runTemplateCoverageOnly = true }

    // Find all methods annotated with @TemplateCheck and run them (will just add the template name to a list)
    templateTest::class.memberFunctions
      .filter { it.findAnnotation<TemplateCheck>() != null }
      .forEach { it.call(templateTest) }

    val templatesWhichShouldBeCovered = TemplateResolver.getAllTemplates().map { it.name }.toSet()

    val notCoveredTemplates = templatesWhichShouldBeCovered.minus(templateTest.templatesChecked)

    val failurePrefix = """
        The following templates were not covered by TemplateTest. Please ensure that tests are added to cover
        these templates and that they are annotated with @TemplateCheck.
        """.trimIndent()

    assertWithMessage(failurePrefix).that(notCoveredTemplates).isEmpty()
  }
}

typealias ProjectStateCustomizer = (ModuleTemplateDataBuilder, ProjectTemplateDataBuilder) -> Unit
typealias TemplateStateCustomizer = Map<String, String>

private fun getBoolFromEnvironment(key: String) = System.getProperty(key).orEmpty().toBoolean() || System.getenv(key).orEmpty().toBoolean()

/**
 * Whether we should run these tests or not.
 */
private val DISABLED = getBoolFromEnvironment("DISABLE_STUDIO_TEMPLATE_TESTS")

/**
 * Whether we should enforce that lint passes cleanly on the projects
 */
internal const val CHECK_LINT = false // Needs work on closing projects cleanly
