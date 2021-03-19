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
import com.android.tools.idea.gradle.repositories.IdeGoogleMavenRepository
import com.android.tools.idea.gradle.repositories.OfflineIdeGoogleMavenRepository
import com.android.tools.idea.gradle.repositories.RepositoryUrlManager
import com.android.tools.idea.npw.template.TemplateResolver
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.IdeComponents
import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.StringParameter
import com.android.tools.idea.wizard.template.WizardUiContext
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberFunctions
import kotlin.system.measureTimeMillis

/**
 * Test for template instantiation.
 *
 * Remaining work on template test:
 * - Start using new NewProjectModel etc to initialise TemplateParameters and set parameter values.
 * - Fix clean model syncing, and hook up clean lint checks.
 */
open class TemplateTest : AndroidGradleTestCase() {
  /** A UsageTracker implementation that allows introspection of logged metrics in tests. */
  private val usageTracker = TestUsageTracker(VirtualTimeScheduler())

  override fun createDefaultProject() = false

  override fun setUp() {
    super.setUp()
    UsageTracker.setWriterForTest(usageTracker)

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
      UsageTracker.cleanAfterTesting()
    }
    finally {
      super.tearDown()
    }
  }

  /**
   * Checks the given template in the given category. Supports overridden template values.
   *
   * @param name              the template name
   * @param customizers        An instance of [ProjectStateCustomizer]s used for providing template and project overrides.
   */
  protected open fun checkCreateTemplate(
    name: String,
    vararg customizers: ProjectStateCustomizer,
    templateStateCustomizer: TemplateStateCustomizer = mapOf(),
    category: Category? = null,
    formFactor: FormFactor? = null,
    avoidModifiedModuleName: Boolean = false
  ) {
    if (DISABLED || isBroken(name)) {
      return
    }
    ensureSdkManagerAvailable()
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
      projectChecker.checkProject(projectName, avoidModifiedModuleName, *customizers)
    }
    println("Checked $name successfully in ${msToCheck}ms")
  }

  @MustBeDocumented
  @Retention(AnnotationRetention.RUNTIME)
  @Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
  annotation class TemplateCheck

  private val withKotlin: ProjectStateCustomizer = { moduleData: ModuleTemplateDataBuilder, projectData: ProjectTemplateDataBuilder ->
    projectData.language = Language.Kotlin
    // Use the Kotlin version for tests
    projectData.kotlinVersion = TestUtils.getKotlinVersionForTests()
    projectData.kotlinVersion = "1.4.30" // b/178380249 - Temporary until 1.4.30 is the default for all tests
  }

  private fun withNewLocation(location: String): TemplateStateCustomizer = mapOf(
    "New Folder Location" to location
  )

  //--- Activity templates ---
  @TemplateCheck
  fun testNewBasicActivity() {
    checkCreateTemplate("Basic Activity")
  }

  @TemplateCheck
  fun testNewBasicActivityWithKotlin() {
    checkCreateTemplate("Basic Activity", withKotlin, avoidModifiedModuleName = true)
  }

  @TemplateCheck
  fun testNewEmptyActivity() {
    checkCreateTemplate("Empty Activity")
  }

  @TemplateCheck
  fun testNewEmptyActivityWithKotlin() {
    checkCreateTemplate("Empty Activity", withKotlin)
  }

  @TemplateCheck
  fun testNewViewModelActivity() {
    checkCreateTemplate("Fragment + ViewModel")
  }

  @TemplateCheck
  fun testNewViewModelActivityWithKotlin() {
    checkCreateTemplate("Fragment + ViewModel", withKotlin)
  }

  @TemplateCheck
  fun testNewTabbedActivity() {
    checkCreateTemplate("Tabbed Activity")
  }

  @TemplateCheck
  fun testNewTabbedActivityWithKotlin() {
    checkCreateTemplate("Tabbed Activity", withKotlin, avoidModifiedModuleName = true)
  }

  @TemplateCheck
  fun testNewNavigationDrawerActivity() {
    checkCreateTemplate("Navigation Drawer Activity")
  }

  @TemplateCheck
  fun testNewNavigationDrawerActivityWithKotlin() {
    checkCreateTemplate("Navigation Drawer Activity", withKotlin, avoidModifiedModuleName = true)
  }

  @TemplateCheck
  fun testNewPrimaryDetailFlow() {
    checkCreateTemplate("Primary/Detail Flow")
  }

  @TemplateCheck
  fun testNewPrimaryDetailFlowWithKotlin() {
    checkCreateTemplate("Primary/Detail Flow", withKotlin, avoidModifiedModuleName = true)
  }

  @TemplateCheck
  fun testNewFullscreenActivity() {
    checkCreateTemplate("Fullscreen Activity")
  }

  @TemplateCheck
  fun testNewFullscreenActivityWithKotlin() {
    checkCreateTemplate("Fullscreen Activity", withKotlin, avoidModifiedModuleName = true)
  }

  @TemplateCheck
  fun testNewLoginActivity() {
    checkCreateTemplate("Login Activity")
  }

  @TemplateCheck
  fun testNewLoginActivityWithKotlin() {
    checkCreateTemplate("Login Activity", withKotlin, avoidModifiedModuleName = true)
  }

  @TemplateCheck
  fun testNewScrollingActivity() {
    checkCreateTemplate("Scrolling Activity")
  }

  @TemplateCheck
  fun testNewScrollingActivityWithKotlin() {
    checkCreateTemplate("Scrolling Activity", withKotlin, avoidModifiedModuleName = true)
  }

  @TemplateCheck
  fun testNewSettingsActivity() {
    checkCreateTemplate("Settings Activity")
  }

  @TemplateCheck
  fun testNewSettingsActivityWithKotlin() {
    checkCreateTemplate("Settings Activity", withKotlin)
  }

  @TemplateCheck
  fun testBottomNavigationActivity() {
    checkCreateTemplate("Bottom Navigation Activity")
  }

  @TemplateCheck
  fun testBottomNavigationActivityWithKotlin() {
    checkCreateTemplate("Bottom Navigation Activity", withKotlin, avoidModifiedModuleName = true)
  }

  @TemplateCheck
  fun testGoogleAdMobAdsActivity() {
    checkCreateTemplate("Google AdMob Ads Activity")
  }

  @TemplateCheck
  fun testGoogleAdMobAdsActivityWithKotlin() {
    checkCreateTemplate("Google AdMob Ads Activity", withKotlin, avoidModifiedModuleName = true)
  }

  @TemplateCheck
  fun testGoogleMapsActivity() {
    checkCreateTemplate("Google Maps Activity")
  }

  @TemplateCheck
  fun testGoogleMapsActivityWithKotlin() {
    checkCreateTemplate("Google Maps Activity", withKotlin, avoidModifiedModuleName = true)
  }

  @TemplateCheck
  fun testComposeActivity() {
    checkCreateTemplate("Empty Compose Activity", withKotlin) // Compose is always Kotlin
  }

  @TemplateCheck
  fun testResponsiveActivity() {
    checkCreateTemplate("Responsive Activity")
  }

  @TemplateCheck
  fun testResponsiveActivityWithKotlin() {
    checkCreateTemplate("Responsive Activity", withKotlin)
  }

  @TemplateCheck
  fun testNewBlankWearActivity() {
    checkCreateTemplate("Blank Activity")
  }

  @TemplateCheck
  fun testNewBlankWearActivityWithKotlin() {
    checkCreateTemplate("Blank Activity", withKotlin, avoidModifiedModuleName = true)
  }

  @TemplateCheck
  fun testGoogleMapsWearActivity() {
    checkCreateTemplate("Google Maps Activity", formFactor = FormFactor.Wear)
  }

  @TemplateCheck
  fun testGoogleMapsWearActivityWithKotlin() {
    checkCreateTemplate("Google Maps Activity", withKotlin, formFactor = FormFactor.Wear, avoidModifiedModuleName = true)
  }

  @TemplateCheck
  fun testNewTvActivity() {
    checkCreateTemplate("Android TV Blank Activity")
  }

  @TemplateCheck
  fun testNewTvActivityWithKotlin() {
    checkCreateTemplate("Android TV Blank Activity", withKotlin)
  }

  @TemplateCheck
  fun testNewNativeCppActivity() {
    checkCreateTemplate("Native C++")
  }

  @TemplateCheck
  fun testNewNativeCppActivityWithKotlin() {
    checkCreateTemplate("Native C++", withKotlin, avoidModifiedModuleName = true)
  }

  //--- Fragment templates ---
  @TemplateCheck
  fun testNewListFragment() {
    checkCreateTemplate("Fragment (List)")
  }

  @TemplateCheck
  fun testNewListFragmentWithKotlin() {
    checkCreateTemplate("Fragment (List)", withKotlin, avoidModifiedModuleName = true)
  }

  @TemplateCheck
  fun testNewModalBottomSheet() {
    checkCreateTemplate("Modal Bottom Sheet")
  }

  @TemplateCheck
  fun testNewModalBottomSheetWithKotlin() {
    checkCreateTemplate("Modal Bottom Sheet", withKotlin, avoidModifiedModuleName = true)
  }

  @TemplateCheck
  fun testNewBlankFragment() {
    checkCreateTemplate("Fragment (Blank)")
  }

  @TemplateCheck
  fun testNewBlankFragmentWithKotlin() {
    checkCreateTemplate("Fragment (Blank)", withKotlin)
  }

  @TemplateCheck
  fun testNewSettingsFragment() {
    checkCreateTemplate("Settings Fragment")
  }

  @TemplateCheck
  fun testNewSettingsFragmentWithKotlin() {
    checkCreateTemplate("Settings Fragment", withKotlin)
  }

  @TemplateCheck
  fun testNewViewModelFragment() {
    checkCreateTemplate("Fragment (with ViewModel)")
  }

  @TemplateCheck
  fun testNewViewModelFragmentWithKotlin() {
    checkCreateTemplate("Fragment (with ViewModel)", withKotlin)
  }

  @TemplateCheck
  fun testNewScrollingFragment() {
    checkCreateTemplate("Scrolling Fragment")
  }

  @TemplateCheck
  fun testNewScrollingFragmentWithKotlin() {
    checkCreateTemplate("Scrolling Fragment", withKotlin)
  }

  @TemplateCheck
  fun testNewFullscreenFragment() {
    checkCreateTemplate("Fullscreen Fragment")
  }

  @TemplateCheck
  fun testNewFullscreenFragmentWithKotlin() {
    checkCreateTemplate("Fullscreen Fragment", withKotlin, avoidModifiedModuleName = true)
  }

  @TemplateCheck
  fun testNewGoogleMapsFragment() {
    checkCreateTemplate("Google Maps Fragment")
  }

  @TemplateCheck
  fun testNewGoogleMapsFragmentWithKotlin() {
    checkCreateTemplate("Google Maps Fragment", withKotlin)
  }

  @TemplateCheck
  fun testNewGoogleAdMobFragment() {
    checkCreateTemplate("Google AdMob Ads Fragment")
  }

  @TemplateCheck
  fun testNewGoogleAdMobFragmentWithKotlin() {
    checkCreateTemplate("Google AdMob Ads Fragment", withKotlin, avoidModifiedModuleName = true)
  }

  @TemplateCheck
  fun testLoginFragment() {
    checkCreateTemplate("Login Fragment")
  }

  @TemplateCheck
  fun testLoginFragmentWithKotlin() {
    checkCreateTemplate("Login Fragment", withKotlin, avoidModifiedModuleName = true)
  }

  //--- Other templates ---
  @TemplateCheck
  fun testNewAppWidget() {
    checkCreateTemplate("App Widget")
  }

  @TemplateCheck
  fun testNewBroadcastReceiver() {
    checkCreateTemplate("Broadcast Receiver")
  }

  @TemplateCheck
  fun testNewBroadcastReceiverWithKotlin() {
    checkCreateTemplate("Broadcast Receiver", withKotlin)
  }

  @TemplateCheck
  fun testNewContentProvider() {
    checkCreateTemplate("Content Provider")
  }

  @TemplateCheck
  fun testNewContentProviderWithKotlin() {
    checkCreateTemplate("Content Provider", withKotlin)
  }

  @TemplateCheck
  fun testNewSliceProvider() {
    checkCreateTemplate("Slice Provider")
  }

  @TemplateCheck
  fun testNewSliceProviderWithKotlin() {
    checkCreateTemplate("Slice Provider", withKotlin)
  }

  @TemplateCheck
  fun testNewCustomView() {
    checkCreateTemplate("Custom View")
  }

  @TemplateCheck
  fun testNewIntentService() {
    checkCreateTemplate("Service (IntentService)")
  }

  @TemplateCheck
  fun testNewIntentServiceWithKotlin() {
    checkCreateTemplate("Service (IntentService)", withKotlin)
  }

  @TemplateCheck
  fun testNewService() {
    checkCreateTemplate("Service")
  }

  @TemplateCheck
  fun testNewServiceWithKotlin() {
    checkCreateTemplate("Service", withKotlin)
  }

  @TemplateCheck
  fun testNewFolders() {
    checkCreateTemplate("AIDL Folder", templateStateCustomizer = withNewLocation("foo"))
    checkCreateTemplate("Assets Folder", templateStateCustomizer = withNewLocation("src/main/assets"))
    checkCreateTemplate("Font Folder", templateStateCustomizer = withNewLocation("src/main/res/font"))
    checkCreateTemplate("Java Folder", templateStateCustomizer = withNewLocation("src/main/java"))
    checkCreateTemplate("JNI Folder", templateStateCustomizer = withNewLocation("src/main/jni"))
    checkCreateTemplate("Raw Resources Folder", templateStateCustomizer = withNewLocation("src/main/res/raw"))
    checkCreateTemplate("Res Folder", templateStateCustomizer = withNewLocation("src/main/resources"))
    checkCreateTemplate("Java Resources Folder", templateStateCustomizer = withNewLocation("src/main/resources"))
    checkCreateTemplate("RenderScript Folder", templateStateCustomizer = withNewLocation("src/main/rs"))
    checkCreateTemplate("XML Resources Folder", templateStateCustomizer = withNewLocation("src/main/res/xml"))
  }

  @TemplateCheck
  fun testAndroidManifest() {
    checkCreateTemplate("Android Manifest File")
  }

  @TemplateCheck
  fun testNewFiles() {
    checkCreateTemplate("AIDL File")
    checkCreateTemplate("App Actions XML File")
    checkCreateTemplate("Layout XML File")
    checkCreateTemplate("Values XML File")
  }

  @TemplateCheck
  fun testWatchFace() {
    checkCreateTemplate("Watch Face")
  }

  @TemplateCheck
  fun testWatchFaceWithKotlin() {
    checkCreateTemplate("Watch Face", withKotlin)
  }

  @TemplateCheck
  fun testAutomotiveMessagingService() {
    checkCreateTemplate("Messaging Service")
  }

  @TemplateCheck
  fun testAutomotiveMessagingServiceWithKotlin() {
    checkCreateTemplate("Messaging Service", withKotlin)
  }

  @TemplateCheck
  fun testAutomotiveMediaService() {
    checkCreateTemplate("Media Service")
  }

  @TemplateCheck
  fun testAutomotiveMediaServiceWithKotlin() {
    checkCreateTemplate("Media Service", withKotlin)
  }

  fun testWizardUiContext() {
    TemplateResolver.getAllTemplates().filter { it.uiContexts.contains(WizardUiContext.NewProjectExtraDetail) }.forEach {
      // NewProjectExtraDetail should simultaneously declare NewProject
      assertThat(it.uiContexts).contains(WizardUiContext.NewProject)
    }
  }

  open fun testAllTemplatesCovered() {
    CoverageChecker().testAllTemplatesCovered()
  }

  // Create a dummy version of this class that just collects all the templates it will test when it is run.
  // It is important that this class is not run by JUnit!
  class CoverageChecker : TemplateTest() {
    override fun shouldRunTest(): Boolean = false

    // Set of templates tested with unit test
    private val templatesChecked = mutableSetOf<String>()

    override fun checkCreateTemplate(
      name: String,
      vararg customizers: ProjectStateCustomizer,
      templateStateCustomizer: TemplateStateCustomizer,
      category: Category?,
      formFactor: FormFactor?,
      avoidModifiedModuleName: Boolean
    ) {
      templatesChecked.add(name)
    }

    // The actual implementation of the test
    override fun testAllTemplatesCovered() {
      this::class.memberFunctions
        .filter { it.findAnnotation<TemplateCheck>() != null }
        .forEach { it.call(this) }

      val templatesWhichShouldBeCovered = TemplateResolver.getAllTemplates().map { it.name }.toSet()

      val notCoveredTemplates = templatesWhichShouldBeCovered.minus(templatesChecked)

      val failurePrefix = """
        The following templates were not covered by TemplateTest. Please ensure that tests are added to cover
        these templates and that they are annotated with @TemplateCheck.
        """.trimIndent()

      assertWithMessage(failurePrefix).that(notCoveredTemplates).isEmpty()
    }
  }
}

typealias ProjectStateCustomizer = (ModuleTemplateDataBuilder, ProjectTemplateDataBuilder) -> Unit
typealias TemplateStateCustomizer = Map<String, String>

private fun getBoolFromEnvironment(key: String) = System.getProperty(key).orEmpty().toBoolean() || System.getenv(key).orEmpty().toBoolean()

/**
 * Whether we should run these tests or not.
 */
internal val DISABLED = getBoolFromEnvironment("DISABLE_STUDIO_TEMPLATE_TESTS")

/**
 * Whether we should enforce that lint passes cleanly on the projects
 */
internal const val CHECK_LINT = false // Needs work on closing projects cleanly
