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
import com.android.tools.idea.npw.platform.Language
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberFunctions

open class TemplateTest : TemplateTestBase() {
  private val withKotlin = { templateMap: MutableMap<String, Any>, projectMap: MutableMap<String, Any> ->
    projectMap[TemplateAttributes.ATTR_KOTLIN_VERSION] = TestUtils.getKotlinVersionForTests()
    projectMap[TemplateAttributes.ATTR_LANGUAGE] = Language.KOTLIN.toString()
    templateMap[TemplateAttributes.ATTR_LANGUAGE] = Language.KOTLIN.toString()
    templateMap[TemplateAttributes.ATTR_PACKAGE_NAME] = "test.pkg.in" // Add in a Kotlin keyword ("in") in the package name to trigger escape code too
  }

  private val withCpp = { templateMap: MutableMap<String, Any>, projectMap: MutableMap<String, Any> ->
    projectMap[TemplateAttributes.ATTR_CPP_SUPPORT] = true
    projectMap[TemplateAttributes.ATTR_CPP_FLAGS] = ""
    templateMap[TemplateAttributes.ATTR_CPP_SUPPORT] = true
    templateMap[TemplateAttributes.ATTR_CPP_FLAGS] = ""
  }

  private val withNewRenderingContext = { templateMap: MutableMap<String, Any>, _: MutableMap<String, Any> ->
    templateMap[COMPARE_NEW_RENDERING_CONTEXT] = true
  }

  private fun withNewLocation(location: String) = { templateMap: MutableMap<String, Any>, _: MutableMap<String, Any> ->
    templateMap["newLocation"] = location
  }

  //--- Activity templates ---
  @TemplateCheck
  fun testNewBasicActivity() {
    checkCreateTemplate("activities", "BasicActivity", false, true)
  }

  @TemplateCheck
  fun testCompareNewBasicActivity() {
    checkCreateTemplate("activities", "BasicActivity", false, true, withNewRenderingContext)
  }

  @TemplateCheck
  fun testNewBasicActivityWithKotlin() {
    checkCreateTemplate("activities", "BasicActivity", false, true, withKotlin)
  }

  @TemplateCheck
  fun testCompareNewBasicActivityWithKotlin() {
    checkCreateTemplate("activities", "BasicActivity", false, true, withKotlin, withNewRenderingContext)
  }

  @TemplateCheck
  fun testCompareNewEmptyActivityWithKotlin() {
    checkCreateTemplate("activities", "EmptyActivity", false, true, withKotlin, withNewRenderingContext)
  }

  @TemplateCheck
  fun testNewProjectWithBasicActivity() {
    checkCreateTemplate("activities", "BasicActivity", true, true)
  }

  @TemplateCheck
  fun testNewThingsActivity() {
    checkCreateTemplate("activities", "AndroidThingsActivity", false, true)
  }

  @TemplateCheck
  fun testNewProjectWithThingsActivity() {
    checkCreateTemplate("activities", "AndroidThingsActivity", true, true)
  }

  @TemplateCheck
  fun testNewProjectWithThingsActivityWithKotlin() {
    checkCreateTemplate("activities", "AndroidThingsActivity", true, true, withKotlin)
  }

  @TemplateCheck
  fun testNewEmptyActivity() {
    checkCreateTemplate("activities", "EmptyActivity", false, true)
  }

  @TemplateCheck
  fun testCompareNewEmptyActivity() {
    checkCreateTemplate("activities", "EmptyActivity", false, true, withNewRenderingContext)
  }

  @TemplateCheck
  fun testNewEmptyActivityWithKotlin() {
    checkCreateTemplate("activities", "EmptyActivity", false, true, withKotlin)
  }

  @TemplateCheck
  fun testNewProjectWithEmptyActivity() {
    checkCreateTemplate("activities", "EmptyActivity", true, true)
  }

  @TemplateCheck
  fun testNewProjectWithEmptyActivityWithCpp() {
    checkCreateTemplate("activities", "EmptyActivity", true, true, withCpp)
  }

  @TemplateCheck
  fun testNewViewModelActivity() {
    checkCreateTemplate("activities", "ViewModelActivity", false, true)
  }

  @TemplateCheck
  fun testNewViewModelActivityWithKotlin() {
    checkCreateTemplate("activities", "ViewModelActivity", false, true, withKotlin)
  }

  @TemplateCheck
  fun testCompareViewModelActivity() {
    checkCreateTemplate("activities", "ViewModelActivity", false, true, withNewRenderingContext)
  }

  @TemplateCheck
  fun testCompareViewModelActivityWithKotlin() {
    checkCreateTemplate("activities", "ViewModelActivity", false, true, withKotlin, withNewRenderingContext)
  }

  @TemplateCheck
  fun testNewProjectWithViewModelActivity() {
    checkCreateTemplate("activities", "ViewModelActivity", true, true)
  }

  @TemplateCheck
  fun testNewTabbedActivity() {
    checkCreateTemplate("activities", "TabbedActivity", false, true)
  }

  @TemplateCheck
  fun testNewProjectWithTabbedActivity() {
    checkCreateTemplate("activities", "TabbedActivity", true, true)
  }

  @TemplateCheck
  fun testNewProjectWithTabbedActivityWithKotlin() {
    checkCreateTemplate("activities", "TabbedActivity", true, true, withKotlin)
  }

  @TemplateCheck
  fun testCompareTabbedActivity() {
    checkCreateTemplate("activities", "TabbedActivity", false, true, withNewRenderingContext)
  }

  @TemplateCheck
  fun testCompareTabbedActivityWithKotlin() {
    checkCreateTemplate("activities", "TabbedActivity", false, true, withKotlin, withNewRenderingContext)
  }

  @TemplateCheck
  fun testNewBlankWearActivity() {
    checkCreateTemplate("activities", "BlankWearActivity", false, true)
  }

  @TemplateCheck
  fun testNewProjectWithBlankWearActivity() {
    checkCreateTemplate("activities", "BlankWearActivity", true, true)
  }

  @TemplateCheck
  fun testNewProjectWithBlankWearActivityWithKotlin() {
    checkCreateTemplate("activities", "BlankWearActivity", true, true, withKotlin)
  }

  @TemplateCheck
  fun testNewNavigationDrawerActivity() {
    checkCreateTemplate("activities", "NavigationDrawerActivity", false, true)
  }

  @TemplateCheck
  fun testNewProjectWithNavigationDrawerActivity() {
    checkCreateTemplate("activities", "NavigationDrawerActivity", true, true)
  }

  @TemplateCheck
  fun testNewNavigationDrawerActivityWithKotlin() {
    checkCreateTemplate("activities", "NavigationDrawerActivity", false, true, withKotlin)
  }

  @TemplateCheck
  fun testNewMasterDetailFlow() {
    checkCreateTemplate("activities", "MasterDetailFlow", false, true)
  }

  @TemplateCheck
  fun testNewProjectWithMasterDetailFlow() {
    checkCreateTemplate("activities", "MasterDetailFlow", true, true)
  }

  @TemplateCheck
  fun testNewProjectWithMasterDetailFlowWithKotlin() {
    checkCreateTemplate("activities", "MasterDetailFlow", true, true, withKotlin)
  }

  @TemplateCheck
  fun testNewFullscreenActivity() {
    checkCreateTemplate("activities", "FullscreenActivity", false, true)
  }

  @TemplateCheck
  fun testNewProjectWithFullscreenActivity() {
    checkCreateTemplate("activities", "FullscreenActivity", true, true)
  }

  @TemplateCheck
  fun testNewProjectWithFullscreenActivityWithKotlin() {
    checkCreateTemplate("activities", "FullscreenActivity", true, true, withKotlin)
  }

  @TemplateCheck
  fun testNewLoginActivity() {
    checkCreateTemplate("activities", "LoginActivity", false, true)
  }

  @TemplateCheck
  fun testNewProjectWithLoginActivity() {
    checkCreateTemplate("activities", "LoginActivity", true, true)
  }

  @TemplateCheck
  fun testNewProjectWithLoginActivityWithKotlin() {
    checkCreateTemplate("activities", "LoginActivity", true, true, withKotlin)
  }

  @TemplateCheck
  fun testNewScrollActivity() {
    checkCreateTemplate("activities", "ScrollActivity", false, true)
  }

  @TemplateCheck
  fun testNewProjectWithScrollActivity() {
    checkCreateTemplate("activities", "ScrollActivity", true, true)
  }

  @TemplateCheck
  fun testNewProjectWithScrollActivityWithKotlin() {
    checkCreateTemplate(
      "activities", "ScrollActivity", true, true,
      withKotlin, withNewLocation("menu_scroll_activity")
    )
  }

  @TemplateCheck
  fun testNewSettingsActivity() {
    checkCreateTemplate("activities", "SettingsActivity", false, true)
  }

  @TemplateCheck
  fun testNewProjectWithSettingsActivity() {
    checkCreateTemplate("activities", "SettingsActivity", true, true)
  }

  @TemplateCheck
  fun testNewProjectWithSettingsActivityWithKotlin() {
    checkCreateTemplate("activities", "SettingsActivity", true, true, withKotlin)
  }

  @TemplateCheck
  fun testBottomNavigationActivity() {
    checkCreateTemplate("activities", "BottomNavigationActivity", false, true)
  }

  @TemplateCheck
  fun testNewProjectWithBottomNavigationActivity() {
    checkCreateTemplate("activities", "BottomNavigationActivity", true, true)
  }

  @TemplateCheck
  fun testNewProjectWithBottomNavigationActivityWithKotlin() {
    checkCreateTemplate("activities", "BottomNavigationActivity", true, true, withKotlin)
  }

  @TemplateCheck
  fun testNewTvActivity() {
    checkCreateTemplate("activities", "AndroidTVActivity", false, true)
  }

  @TemplateCheck
  fun testNewTvActivityWithKotlin() {
    checkCreateTemplate("activities", "AndroidTVActivity", false, true, withKotlin)
  }

  @TemplateCheck
  fun testNewProjectWithTvActivity() {
    checkCreateTemplate("activities", "AndroidTVActivity", true, true)
  }

  @TemplateCheck
  fun testNewProjectWithTvActivityWithKotlin() {
    checkCreateTemplate("activities", "AndroidTVActivity", true, true, withKotlin)
  }

  @TemplateCheck
  fun testGoogleAdMobAdsActivity() {
    checkCreateTemplate("activities", "GoogleAdMobAdsActivity", false, true)
  }

  @TemplateCheck
  fun testNewProjectWithGoogleAdMobAdsActivity() {
    checkCreateTemplate("activities", "GoogleAdMobAdsActivity", true, true)
  }

  @TemplateCheck
  fun testGoogleMapsActivity() {
    checkCreateTemplate("activities", "GoogleMapsActivity", false, true)
  }

  @TemplateCheck
  fun testNewProjectWithGoogleMapsActivity() {
    checkCreateTemplate("activities", "GoogleMapsActivity", true, true)
  }

  @TemplateCheck
  fun testGoogleMapsWearActivity() {
    checkCreateTemplate("activities", "GoogleMapsWearActivity", false, true)
  }

  @TemplateCheck
  fun testNewProjectWithGoogleMapsWearActivity() {
    checkCreateTemplate("activities", "GoogleMapsWearActivity", true, true)
  }

  @TemplateCheck
  fun testNewProjectWithGoogleMapsWearActivityWithKotlin() {
    checkCreateTemplate("activities", "GoogleMapsWearActivity", true, true, withKotlin)
  }

  @TemplateCheck
  fun testNewAutomotiveProjectWithMediaService() {
    checkCreateTemplate("other", "AutomotiveMediaService", true, true)
  }

  @TemplateCheck
  fun testNewAutomotiveProjectWithMediaServiceWithKotlin() {
    checkCreateTemplate("other", "AutomotiveMediaService", true, true, withKotlin)
  }

  @TemplateCheck
  fun testNewProjectWithComposeActivity() {
    checkCreateTemplate("activities", "ComposeActivity", true, true, withKotlin) // Compose is always kotlin
  }

  @TemplateCheck
  fun testComposeActivity() {
    checkCreateTemplate("activities", "ComposeActivity", false, true, withKotlin) // Compose is always kotlin
  }

  //--- Non-activity templates ---

  @TemplateCheck
  fun testNewBroadcastReceiver() {
    // No need to try this template with multiple platforms, one is adequate
    checkCreateTemplate("other", "BroadcastReceiver", false)
  }

  @TemplateCheck
  fun testNewBroadcastReceiverWithKotlin() {
    // No need to try this template with multiple platforms, one is adequate
    checkCreateTemplate("other", "BroadcastReceiver", false, false, withKotlin)
  }

  @TemplateCheck
  fun testNewContentProvider() {
    checkCreateTemplate("other", "ContentProvider")
  }

  @TemplateCheck
  fun testNewContentProviderWithKotlin() {
    checkCreateTemplate("other", "ContentProvider", false, false, withKotlin)
  }

  @TemplateCheck
  fun testNewSliceProvider() {
    checkCreateTemplate("other", "SliceProvider", false, false)
  }

  @TemplateCheck
  fun testNewSliceProviderWithKotlin() {
    checkCreateTemplate("other", "SliceProvider", false, false, withKotlin)
  }

  @TemplateCheck
  fun testNewCustomView() {
    checkCreateTemplate("other", "CustomView")
  }

  @TemplateCheck
  fun testNewIntentService() {
    checkCreateTemplate("other", "IntentService")
  }

  @TemplateCheck
  fun testNewIntentServiceWithKotlin() {
    checkCreateTemplate("other", "IntentService", false,  false,withKotlin)
  }

  @TemplateCheck
  fun testNewListFragment() {
    checkCreateTemplate("fragments", "ListFragment")
  }

  @TemplateCheck
  fun testNewListFragmentWithKotlin() {
    checkCreateTemplate("fragments", "ListFragment", false, false, withKotlin)
  }

  @TemplateCheck
  fun testNewModalBottomSheet() {
    checkCreateTemplate("fragments", "ModalBottomSheet")
  }

  @TemplateCheck
  fun testNewAppWidget() {
    checkCreateTemplate("other", "AppWidget")
  }

  @TemplateCheck
  fun testNewBlankFragment() {
    checkCreateTemplate("fragments", "BlankFragment")
  }

  @TemplateCheck
  fun testNewBlankFragmentWithKotlin() {
    checkCreateTemplate("fragments", "BlankFragment", false, false, withKotlin)
  }

  @TemplateCheck
  fun testNewSettingsFragment() {
    checkCreateTemplate("fragments", "SettingsFragment", true, false)
  }

  @TemplateCheck
  fun testNewSettingsFragmentWithKotlin() {
    checkCreateTemplate("fragments", "SettingsFragment", false, false, withKotlin)
  }

  @TemplateCheck
  fun testNewViewModelFragment() {
    checkCreateTemplate("fragments", "ViewModelFragment")
  }

  @TemplateCheck
  fun testNewViewModelFragmentWithKotlin() {
    checkCreateTemplate("fragments", "ViewModelFragment", false, false, withKotlin)
  }

  @TemplateCheck
  fun testNewScrollFragment() {
    checkCreateTemplate("fragments", "ScrollFragment")
  }

  @TemplateCheck
  fun testNewScrollFragmentWithKotlin() {
    checkCreateTemplate("fragments", "ScrollFragment", false, false, withKotlin)
  }

  @TemplateCheck
  fun testNewFullscreenFragment() {
    checkCreateTemplate("fragments", "FullscreenFragment")
  }

  @TemplateCheck
  fun testNewFullscreenFragmentWithKotlin() {
    checkCreateTemplate("fragments", "FullscreenFragment", false, false, withKotlin)
  }

  @TemplateCheck
  fun testNewGoogleMapsFragment() {
    checkCreateTemplate("fragments", "GoogleMapsFragment")
  }

  @TemplateCheck
  fun testNewGoogleMapsFragmentWithKotlin() {
    checkCreateTemplate("fragments", "GoogleMapsFragment", false, false, withKotlin)
  }

  @TemplateCheck
  fun testNewGoogleAdMobFragment() {
    checkCreateTemplate("fragments", "GoogleAdMobAdsFragment")
  }

  @TemplateCheck
  fun testNewGoogleAdMobFragmentWithKotlin() {
    checkCreateTemplate("fragments", "GoogleAdMobAdsFragment", false, false, withKotlin)
  }

  fun testLoginFragment() {
    checkCreateTemplate("fragments", "LoginFragment")
  }

  @TemplateCheck
  fun testLoginFragmentWithKotlin() {
    checkCreateTemplate("fragments", "LoginFragment", false, false, withKotlin)
  }

  @TemplateCheck
  fun testNewService() {
    checkCreateTemplate("other", "Service")
  }

  @TemplateCheck
  fun testNewServiceWithKotlin() {
    checkCreateTemplate("other", "Service", false, false, withKotlin)
  }

  @TemplateCheck
  fun testNewAidlFile() {
    checkCreateTemplate("other", "AidlFile")
  }

  @TemplateCheck
  fun testNewAidlFolder() {
    checkCreateTemplate("other", "AidlFolder", false, false, withNewLocation("foo"))
  }

  @TemplateCheck
  fun testAndroidManifest() {
    checkCreateTemplate("other", "AndroidManifest", false, false, withNewLocation("src/foo/AndroidManifest.xml"))
  }

  @TemplateCheck
  fun testAssetsFolder() {
    checkCreateTemplate("other", "AssetsFolder", false, false, withNewLocation("src/main/assets"))
  }

  @TemplateCheck
  fun testJavaAndJniFolder() {
    checkCreateTemplate("other", "JavaFolder", false, false, withNewLocation("src/main/java"))
    checkCreateTemplate("other", "JniFolder", false, false, withNewLocation( "src/main/jni"))
  }

  @TemplateCheck
  fun testFontFolder() {
    checkCreateTemplate("other", "FontFolder", false, false, withNewLocation( "src/main/res/font"))
  }

  @TemplateCheck
  fun testRawFolder() {
    checkCreateTemplate("other", "RawFolder", false, false, withNewLocation( "src/main/res/raw"))
  }

  @TemplateCheck
  fun testXmlFolder() {
    checkCreateTemplate("other", "XmlFolder", false, false, withNewLocation( "src/main/res/xml"))
  }

  @TemplateCheck
  fun testRenderSourceFolder() {
    checkCreateTemplate("other", "RsFolder", false, false, withNewLocation( "src/main/rs"))
    checkCreateTemplate("other", "ResFolder", false, false, withNewLocation( "src/main/res"))
    checkCreateTemplate("other", "ResourcesFolder", false, false, withNewLocation( "src/main/resources"))
  }

  @TemplateCheck
  fun testNewLayoutResourceFile() {
    checkCreateTemplate("other", "LayoutResourceFile")
  }

  @TemplateCheck
  fun testNewAppActionsResourceFile() {
    checkCreateTemplate("other", "AppActionsResourceFile")
  }

  @TemplateCheck
  fun testAutomotiveMediaService() {
    checkCreateTemplate("other", "AutomotiveMediaService", false, false)
  }

  @TemplateCheck
  fun testAutomotiveMediaServiceWithKotlin() {
    checkCreateTemplate("other", "AutomotiveMediaService", false, false, withKotlin)
  }

  @TemplateCheck
  fun testAutomotiveMessagingService() {
    checkCreateTemplate("other", "AutomotiveMessagingService")
  }

  @TemplateCheck
  fun testAutomotiveMessagingServiceWithKotlin() {
    checkCreateTemplate("other", "AutomotiveMessagingService", false, false, withKotlin)
  }

  @TemplateCheck
  fun testWatchFaceService() {
    checkCreateTemplate("other", "WatchFaceService")
  }

  @TemplateCheck
  fun testWatchFaceServiceWithKotlin() {
    checkCreateTemplate("other", "WatchFaceService", true, false, withKotlin)
  }

  @TemplateCheck
  fun testNewValueResourceFile() {
    checkCreateTemplate("other", "ValueResourceFile")
  }

  open fun testAllTemplatesCovered() {
    if (DISABLED) {
      return
    }
    CoverageChecker().testAllTemplatesCovered()
  }

  // Create a dummy version of this class that just collects all the templates it will test when it is run.
  // It is important that this class is not run by JUnit!
  class CoverageChecker : TemplateTest() {
    override fun shouldRunTest(): Boolean = false

    // Set of templates tested with unit test
    private val templatesChecked = mutableSetOf<String>()

    private fun gatherMissedTests(templateFile: File, createWithProject: Boolean): String? {
      val category: String = templateFile.parentFile.name
      val name: String = templateFile.name

      return "\nCategory: \"$category\" Name: \"$name\" createWithProject: $createWithProject".takeUnless {
        isBroken(name) || getCheckKey(category, name, createWithProject) in templatesChecked
      }
    }

    override fun checkCreateTemplate(
      category: String, name: String, createWithProject: Boolean, apiSensitive: Boolean, vararg customizers: ProjectStateCustomizer
    ) {
      templatesChecked.add(getCheckKey(category, name, createWithProject))
    }

    // The actual implementation of the test
    override fun testAllTemplatesCovered() {
      this::class.memberFunctions
        .filter { it.findAnnotation<TemplateCheck>() != null && it.name.startsWith("test") }
        .forEach { it.call(this) }
      val manager = TemplateManager.getInstance()

      val failureMessages = sequence {
        for (templateFile in manager.getTemplates("other")) {
          yield(gatherMissedTests(templateFile, false))
        }

        // Also try creating templates, not as part of creating a project
        for (templateFile in manager.getTemplates("activities")) {
          yield(gatherMissedTests(templateFile, true))
          yield(gatherMissedTests(templateFile, false))
        }
      }.filterNotNull().toList()

      val failurePrefix = """
        The following templates were not covered by TemplateTest. Please ensure that tests are added to cover
        these templates and that they are annotated with @TemplateCheck.
        """.trimIndent()
      assertWithMessage(failurePrefix).that(failureMessages).isEmpty()
    }
  }
}
