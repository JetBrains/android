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

import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.npw.platform.Language
import com.android.tools.idea.npw.template.TemplateResolver
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.IdeComponents
import com.android.tools.idea.wizard.template.StringParameter
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
    templateStateCustomizer: TemplateStateCustomizer = mapOf()
  ) {
    if (DISABLED) {
      return
    }
    ensureSdkManagerAvailable()
    val template = TemplateResolver.getTemplateByName(name)!!

    templateStateCustomizer.forEach { (parameterName: String, overrideValue: String) ->
      val p = template.parameters.find { it.name == parameterName }!! as StringParameter
      p.value = overrideValue
    }

    if (isBroken(name)) {
      return
    }

    val msToCheck = measureTimeMillis {
      val projectName = "${template.name}_default"
      val projectChecker = ProjectChecker(CHECK_LINT, template, usageTracker)

      // TODO: We need to check more combinations of different moduleData/template params here.
      // Running once to make it as easy as possible.
      projectChecker.checkProject(projectName, *customizers)
    }
    println("Checked $name successfully in ${msToCheck}ms")
  }

  @MustBeDocumented
  @Retention(AnnotationRetention.RUNTIME)
  @Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
  annotation class TemplateCheck

  private val withKotlin: ProjectStateCustomizer = { moduleData: ModuleTemplateDataBuilder, projectData: ProjectTemplateDataBuilder ->
    projectData.language = Language.KOTLIN
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
    checkCreateTemplate("Basic Activity", withKotlin)
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
    checkCreateTemplate("Tabbed Activity", withKotlin)
  }

  @TemplateCheck
  fun testNewNavigationDrawerActivity() {
    checkCreateTemplate("Navigation Drawer Activity")
  }

  @TemplateCheck
  fun testNewNavigationDrawerActivityWithKotlin() {
    checkCreateTemplate("Navigation Drawer Activity", withKotlin)
  }

  @TemplateCheck
  fun testNewMasterDetailFlow() {
    checkCreateTemplate("Master/Detail Flow")
  }

  @TemplateCheck
  fun testNewMasterDetailFlowWithKotlin() {
    checkCreateTemplate("Master/Detail Flow", withKotlin)
  }

  @TemplateCheck
  fun testNewFullscreenActivity() {
    checkCreateTemplate("Fullscreen Activity")
  }

  @TemplateCheck
  fun testNewFullscreenActivityWithKotlin() {
    checkCreateTemplate("Fullscreen Activity", withKotlin)
  }

  @TemplateCheck
  fun testNewLoginActivity() {
    checkCreateTemplate("Login Activity")
  }

  @TemplateCheck
  fun testNewLoginActivityWithKotlin() {
    checkCreateTemplate("Login Activity", withKotlin)
  }

  @TemplateCheck
  fun testNewScrollingActivity() {
    checkCreateTemplate("Scrolling Activity")
  }

  @TemplateCheck
  fun testNewScrollingActivityWithKotlin() {
    checkCreateTemplate("Scrolling Activity", withKotlin)
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
    checkCreateTemplate("Bottom Navigation Activity", withKotlin)
  }

  @TemplateCheck
  fun testGoogleAdMobAdsActivity() {
    checkCreateTemplate("Google AdMob Ads Activity")
  }

  @TemplateCheck
  fun testGoogleAdMobAdsActivityWithKotlin() {
    checkCreateTemplate("Google AdMob Ads Activity", withKotlin)
  }

  @TemplateCheck
  fun testGoogleMapsActivity() {
    checkCreateTemplate("Google Maps Activity")
  }

  @TemplateCheck
  fun testGoogleMapsActivityWithKotlin() {
    checkCreateTemplate("Google Maps Activity", withKotlin)
  }

  @TemplateCheck
  fun testComposeActivity() {
    checkCreateTemplate("Empty Compose Activity", withKotlin) // Compose is always Kotlin
  }


  @TemplateCheck
  fun testNewBlankWearActivity() {
    checkCreateTemplate("Blank Activity")
  }

  @TemplateCheck
  fun testNewBlankWearActivityWithKotlin() {
    checkCreateTemplate("Blank Activity", withKotlin)
  }

  @TemplateCheck
  fun testGoogleMapsWearActivity() {
    checkCreateTemplate("Google Maps Wear Activity")
  }

  @TemplateCheck
  fun testGoogleMapsWearActivityWithKotlin() {
    checkCreateTemplate("Google Maps Wear Activity", withKotlin)
  }


  @TemplateCheck
  fun testNewTvActivity() {
    checkCreateTemplate("Android TV Activity")
  }

  @TemplateCheck
  fun testNewTvActivityWithKotlin() {
    checkCreateTemplate("Android TV Activity", withKotlin)
  }


  @TemplateCheck
  fun testNewThingsActivity() {
    checkCreateTemplate("Android Things Empty Activity")
  }

  @TemplateCheck
  fun testNewThingsActivityWithKotlin() {
    checkCreateTemplate("Android Things Empty Activity", withKotlin)
  }

  //--- Fragment templates ---
  @TemplateCheck
  fun testNewListFragment() {
    checkCreateTemplate("Fragment (List)")
  }

  @TemplateCheck
  fun testNewListFragmentWithKotlin() {
    checkCreateTemplate("Fragment (List)", withKotlin)
  }

  @TemplateCheck
  fun testNewModalBottomSheet() {
    checkCreateTemplate("Modal Bottom Sheet")
  }

  @TemplateCheck
  fun testNewModalBottomSheetWithKotlin() {
    checkCreateTemplate("Modal Bottom Sheet", withKotlin)
  }

  @TemplateCheck
  fun testNewBlankFragment() {
    checkCreateTemplate("Fragment (Blank)")
  }

  @TemplateCheck
  fun testNewBlankFragmentWithKotlin() {
    checkCreateTemplate("Fragment (Blank)", withKotlin)
  }

  // TODO(b/149007070): uncomment when the bug is fixed
  /*
  @TemplateCheck
  fun testNewSettingsFragment() {
    checkCreateTemplate("Settings Fragment")
  }

  @TemplateCheck
  fun testNewSettingsFragmentWithKotlin() {
    checkCreateTemplate("Settings Fragment", withKotlin)
  }
  */

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
    checkCreateTemplate("Fullscreen Fragment", withKotlin)
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
    checkCreateTemplate("Google AdMob Ads Fragment", withKotlin)
  }

  @TemplateCheck
  fun testLoginFragment() {
    checkCreateTemplate("Login Fragment")
  }

  @TemplateCheck
  fun testLoginFragmentWithKotlin() {
    checkCreateTemplate("Login Fragment", withKotlin)
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
    checkCreateTemplate("Font Folder", templateStateCustomizer = withNewLocation( "src/main/res/font"))
    checkCreateTemplate("Java Folder", templateStateCustomizer = withNewLocation("src/main/java"))
    checkCreateTemplate("JNI Folder", templateStateCustomizer = withNewLocation( "src/main/jni"))
    checkCreateTemplate("Raw Resources Folder", templateStateCustomizer = withNewLocation( "src/main/res/raw"))
    checkCreateTemplate("Java Resources Folder", templateStateCustomizer = withNewLocation( "src/main/resources"))
    checkCreateTemplate("RenderScript Folder", templateStateCustomizer = withNewLocation( "src/main/rs"))
    checkCreateTemplate("XML Resources Folder", templateStateCustomizer = withNewLocation( "src/main/res/xml"))
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
    checkCreateTemplate("Messaging service")
  }

  @TemplateCheck
  fun testAutomotiveMessagingServiceWithKotlin() {
    checkCreateTemplate("Messaging service", withKotlin)
  }

  // TODO(qumeric): uncomment when the template will be ready
  /*
  @TemplateCheck
  fun testAutomotiveMediaService() {
    checkCreateTemplate("Automotive Media Service")
  }

  @TemplateCheck
  fun testAutomotiveMediaServiceWithKotlin() {
    checkCreateTemplate("Automotive Media Service", withKotlin)
  }
  */
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
