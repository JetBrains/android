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

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberFunctions

open class TemplateTest : TemplateTestBase() {
  //--- Activity templates ---
  @TemplateCheck
  fun testNewBasicActivity() {
    checkCreateTemplate("activities", "BasicActivity", false)
  }

  @TemplateCheck
  fun testNewBasicActivityWithKotlin() {
    checkCreateTemplate("activities", "BasicActivity", false, withKotlin)
  }

  @TemplateCheck
  fun testNewProjectWithBasicActivity() {
    checkCreateTemplate("activities", "BasicActivity", true)
  }

  @TemplateCheck
  fun testNewThingsActivity() {
    checkCreateTemplate("activities", "AndroidThingsActivity", false)
  }

  @TemplateCheck
  fun testNewProjectWithThingsActivity() {
    checkCreateTemplate("activities", "AndroidThingsActivity", true)
  }

  @TemplateCheck
  fun testNewProjectWithThingsActivityWithKotlin() {
    checkCreateTemplate("activities", "AndroidThingsActivity", true, withKotlin)
  }

  @TemplateCheck
  fun testNewEmptyActivity() {
    checkCreateTemplate("activities", "EmptyActivity", false)
  }

  @TemplateCheck
  fun testNewEmptyActivityWithKotlin() {
    checkCreateTemplate("activities", "EmptyActivity", false, withKotlin)
  }

  @TemplateCheck
  fun testNewProjectWithEmptyActivity() {
    checkCreateTemplate("activities", "EmptyActivity", true)
  }

  @TemplateCheck
  fun testNewProjectWithEmptyActivityWithCpp() {
    checkCreateTemplate("activities", "EmptyActivity", true, withCpp)
  }

  @TemplateCheck
  fun testNewViewModelActivity() {
    checkCreateTemplate("activities", "ViewModelActivity", false)
  }

  @TemplateCheck
  fun testNewViewModelActivityWithKotlin() {
    checkCreateTemplate("activities", "ViewModelActivity", false, withKotlin)
  }

  @TemplateCheck
  fun testNewProjectWithViewModelActivity() {
    checkCreateTemplate("activities", "ViewModelActivity", true)
  }

  @TemplateCheck
  fun testNewTabbedActivity() {
    checkCreateTemplate("activities", "TabbedActivity", false)
  }

  @TemplateCheck
  fun testNewProjectWithTabbedActivity() {
    checkCreateTemplate("activities", "TabbedActivity", true)
  }

  @TemplateCheck
  fun testNewProjectWithTabbedActivityWithKotlin() {
    checkCreateTemplate("activities", "TabbedActivity", true, withKotlin)
  }

  @TemplateCheck
  fun testNewBlankWearActivity() {
    checkCreateTemplate("activities", "BlankWearActivity", false)
  }

  @TemplateCheck
  fun testNewProjectWithBlankWearActivity() {
    checkCreateTemplate("activities", "BlankWearActivity", true)
  }

  @TemplateCheck
  fun testNewProjectWithBlankWearActivityWithKotlin() {
    checkCreateTemplate("activities", "BlankWearActivity", true, withKotlin)
  }

  @TemplateCheck
  fun testNewNavigationDrawerActivity() {
    checkCreateTemplate("activities", "NavigationDrawerActivity", false)
  }

  @TemplateCheck
  fun testNewProjectWithNavigationDrawerActivity() {
    checkCreateTemplate("activities", "NavigationDrawerActivity", true)
  }

  @TemplateCheck
  fun testNewNavigationDrawerActivityWithKotlin() {
    checkCreateTemplate("activities", "NavigationDrawerActivity", false, withKotlin)
  }

  @TemplateCheck
  fun testNewMasterDetailFlow() {
    checkCreateTemplate("activities", "MasterDetailFlow", false)
  }

  @TemplateCheck
  fun testNewProjectWithMasterDetailFlow() {
    checkCreateTemplate("activities", "MasterDetailFlow", true)
  }

  @TemplateCheck
  fun testNewProjectWithMasterDetailFlowWithKotlin() {
    checkCreateTemplate("activities", "MasterDetailFlow", true, withKotlin)
  }

  @TemplateCheck
  fun testNewFullscreenActivity() {
    checkCreateTemplate("activities", "FullscreenActivity", false)
  }

  @TemplateCheck
  fun testNewProjectWithFullscreenActivity() {
    checkCreateTemplate("activities", "FullscreenActivity", true)
  }

  @TemplateCheck
  fun testNewProjectWithFullscreenActivityWithKotlin() {
    checkCreateTemplate("activities", "FullscreenActivity", true, withKotlin)
  }

  @TemplateCheck
  fun testNewLoginActivity() {
    checkCreateTemplate("activities", "LoginActivity", false)
  }

  @TemplateCheck
  fun testNewProjectWithLoginActivity() {
    checkCreateTemplate("activities", "LoginActivity", true)
  }

  @TemplateCheck
  fun testNewProjectWithLoginActivityWithKotlin() {
    checkCreateTemplate("activities", "LoginActivity", true, withKotlin)
  }

  @TemplateCheck
  fun testNewScrollActivity() {
    checkCreateTemplate("activities", "ScrollActivity", false)
  }

  @TemplateCheck
  fun testNewProjectWithScrollActivity() {
    checkCreateTemplate("activities", "ScrollActivity", true)
  }

  @TemplateCheck
  fun testNewProjectWithScrollActivityWithKotlin() {
    checkCreateTemplate("activities", "ScrollActivity", true
    ) { templateMap, projectMap ->
      withKotlin(templateMap, projectMap)
      templateMap["menuName"] = "menu_scroll_activity"
    }
  }

  @TemplateCheck
  fun testNewSettingsActivity() {
    checkCreateTemplate("activities", "SettingsActivity", false)
  }

  @TemplateCheck
  fun testNewProjectWithSettingsActivity() {
    checkCreateTemplate("activities", "SettingsActivity", true)
  }

  @TemplateCheck
  fun testNewProjectWithSettingsActivityWithKotlin() {
    checkCreateTemplate("activities", "SettingsActivity", true, withKotlin)
  }

  @TemplateCheck
  fun testBottomNavigationActivity() {
    checkCreateTemplate("activities", "BottomNavigationActivity", false)
  }

  @TemplateCheck
  fun testNewProjectWithBottomNavigationActivity() {
    checkCreateTemplate("activities", "BottomNavigationActivity", true)
  }

  @TemplateCheck
  fun testNewProjectWithBottomNavigationActivityWithKotlin() {
    checkCreateTemplate("activities", "BottomNavigationActivity", true, withKotlin)
  }

  @TemplateCheck
  fun testNewTvActivity() {
    checkCreateTemplate("activities", "AndroidTVActivity", false)
  }

  @TemplateCheck
  fun testNewTvActivityWithKotlin() {
    checkCreateTemplate("activities", "AndroidTVActivity", false, withKotlin)
  }

  @TemplateCheck
  fun testNewProjectWithTvActivity() {
    checkCreateTemplate("activities", "AndroidTVActivity", true)
  }

  @TemplateCheck
  fun testNewProjectWithTvActivityWithKotlin() {
    checkCreateTemplate("activities", "AndroidTVActivity", true, withKotlin)
  }

  @TemplateCheck
  fun testGoogleAdMobAdsActivity() {
    checkCreateTemplate("activities", "GoogleAdMobAdsActivity", false)
  }

  @TemplateCheck
  fun testNewProjectWithGoogleAdMobAdsActivity() {
    checkCreateTemplate("activities", "GoogleAdMobAdsActivity", true)
  }

  @TemplateCheck
  fun testGoogleMapsActivity() {
    checkCreateTemplate("activities", "GoogleMapsActivity", false)
  }

  @TemplateCheck
  fun testNewProjectWithGoogleMapsActivity() {
    checkCreateTemplate("activities", "GoogleMapsActivity", true)
  }

  @TemplateCheck
  fun testGoogleMapsWearActivity() {
    checkCreateTemplate("activities", "GoogleMapsWearActivity", false)
  }

  @TemplateCheck
  fun testNewProjectWithGoogleMapsWearActivity() {
    checkCreateTemplate("activities", "GoogleMapsWearActivity", true)
  }

  @TemplateCheck
  fun testNewProjectWithGoogleMapsWearActivityWithKotlin() {
    checkCreateTemplate("activities", "GoogleMapsWearActivity", true, withKotlin)
  }

  @TemplateCheck
  fun testNewAutomotiveProjectWithMediaService() {
    checkCreateTemplate("other", "AutomotiveMediaService", true)
  }

  @TemplateCheck
  fun testNewAutomotiveProjectWithMediaServiceWithKotlin() {
    checkCreateTemplate("other", "AutomotiveMediaService", true, withKotlin)
  }

  @TemplateCheck
  fun testNewProjectWithComposeActivity() {
    checkCreateTemplate("activities", "ComposeActivity", true, withKotlin) // Compose is always kotlin
  }

  @TemplateCheck
  fun testComposeActivity() {
    checkCreateTemplate("activities", "ComposeActivity", false, withKotlin) // Compose is always kotlin
  }

  //--- Non-activity templates ---

  @TemplateCheck
  fun testNewBroadcastReceiver() {
    // No need to try this template with multiple platforms, one is adequate
    apiSensitiveTemplate = false
    checkCreateTemplate("other", "BroadcastReceiver")
  }

  @TemplateCheck
  fun testNewBroadcastReceiverWithKotlin() {
    // No need to try this template with multiple platforms, one is adequate
    apiSensitiveTemplate = false
    checkCreateTemplate("other", "BroadcastReceiver", false, withKotlin)
  }

  @TemplateCheck
  fun testNewContentProvider() {
    apiSensitiveTemplate = false
    checkCreateTemplate("other", "ContentProvider")
  }

  @TemplateCheck
  fun testNewContentProviderWithKotlin() {
    apiSensitiveTemplate = false
    checkCreateTemplate("other", "ContentProvider", false, withKotlin)
  }

  @TemplateCheck
  fun testNewSliceProvider() {
    apiSensitiveTemplate = false
    checkCreateTemplate("other", "SliceProvider", false)
  }

  @TemplateCheck
  fun testNewSliceProviderWithKotlin() {
    apiSensitiveTemplate = false
    checkCreateTemplate("other", "SliceProvider", false, withKotlin)
  }

  @TemplateCheck
  fun testNewCustomView() {
    apiSensitiveTemplate = false
    checkCreateTemplate("other", "CustomView")
  }

  @TemplateCheck
  fun testNewIntentService() {
    apiSensitiveTemplate = false
    checkCreateTemplate("other", "IntentService")
  }

  @TemplateCheck
  fun testNewIntentServiceWithKotlin() {
    apiSensitiveTemplate = false
    checkCreateTemplate("other", "IntentService", false, withKotlin)
  }

  @TemplateCheck
  fun testNewListFragment() {
    apiSensitiveTemplate = false
    checkCreateTemplate("fragments", "ListFragment")
  }

  @TemplateCheck
  fun testNewListFragmentWithKotlin() {
    apiSensitiveTemplate = false
    checkCreateTemplate("fragments", "ListFragment", false, withKotlin)
  }

  @TemplateCheck
  fun testNewModalBottomSheet() {
    apiSensitiveTemplate = false
    checkCreateTemplate("fragments", "ModalBottomSheet")
  }

  @TemplateCheck
  fun testNewAppWidget() {
    apiSensitiveTemplate = false
    checkCreateTemplate("other", "AppWidget")
  }

  @TemplateCheck
  fun testNewBlankFragment() {
    apiSensitiveTemplate = false
    checkCreateTemplate("fragments", "BlankFragment")
  }

  @TemplateCheck
  fun testNewBlankFragmentWithKotlin() {
    apiSensitiveTemplate = false
    checkCreateTemplate("fragments", "BlankFragment", false, withKotlin)
  }

  @TemplateCheck
  fun testNewSettingsFragment() {
    apiSensitiveTemplate = false
    checkCreateTemplate("fragments", "SettingsFragment", true)
  }

  @TemplateCheck
  fun testNewSettingsFragmentWithKotlin() {
    apiSensitiveTemplate = false
    checkCreateTemplate("fragments", "SettingsFragment", false, withKotlin)
  }

  @TemplateCheck
  fun testNewViewModelFragment() {
    apiSensitiveTemplate = false
    checkCreateTemplate("fragments", "ViewModelFragment")
  }

  @TemplateCheck
  fun testNewViewModelFragmentWithKotlin() {
    apiSensitiveTemplate = false
    checkCreateTemplate("fragments", "ViewModelFragment", false, withKotlin)
  }

  @TemplateCheck
  fun testNewScrollFragment() {
    apiSensitiveTemplate = false
    checkCreateTemplate("fragments", "ScrollFragment")
  }

  @TemplateCheck
  fun testNewScrollFragmentWithKotlin() {
    apiSensitiveTemplate = false
    checkCreateTemplate("fragments", "ScrollFragment", false, withKotlin)
  }

  @TemplateCheck
  fun testNewFullscreenFragment() {
    apiSensitiveTemplate = false
    checkCreateTemplate("fragments", "FullscreenFragment")
  }

  @TemplateCheck
  fun testNewFullscreenFragmentWithKotlin() {
    apiSensitiveTemplate = false
    checkCreateTemplate("fragments", "FullscreenFragment", false, withKotlin)
  }

  @TemplateCheck
  fun testNewGoogleMapsFragment() {
    apiSensitiveTemplate = false
    checkCreateTemplate("fragments", "GoogleMapsFragment")
  }

  @TemplateCheck
  fun testNewGoogleMapsFragmentWithKotlin() {
    apiSensitiveTemplate = false
    checkCreateTemplate("fragments", "GoogleMapsFragment", false, withKotlin)
  }

  @TemplateCheck
  fun testNewGoogleAdMobFragment() {
    apiSensitiveTemplate = false
    checkCreateTemplate("fragments", "GoogleAdMobAdsFragment")
  }

  @TemplateCheck
  fun testNewGoogleAdMobFragmentWithKotlin() {
    apiSensitiveTemplate = false
    checkCreateTemplate("fragments", "GoogleAdMobAdsFragment", false, withKotlin)
  }

  fun testLoginFragment() {
    apiSensitiveTemplate = false
    checkCreateTemplate("fragments", "LoginFragment")
  }

  @TemplateCheck
  fun testLoginFragmentWithKotlin() {
    apiSensitiveTemplate = false
    checkCreateTemplate("fragments", "LoginFragment", false, withKotlin)
  }

  @TemplateCheck
  fun testNewService() {
    apiSensitiveTemplate = false
    checkCreateTemplate("other", "Service")
  }

  @TemplateCheck
  fun testNewServiceWithKotlin() {
    apiSensitiveTemplate = false
    checkCreateTemplate("other", "Service", false, withKotlin)
  }

  @TemplateCheck
  fun testNewAidlFile() {
    apiSensitiveTemplate = false
    checkCreateTemplate("other", "AidlFile")
  }

  @TemplateCheck
  fun testNewAidlFolder() {
    apiSensitiveTemplate = false
    checkCreateTemplate("other", "AidlFolder", false
    ) { templateMap, _ ->
      templateMap["newLocation"] = "foo"
    }
  }

  @TemplateCheck
  fun testAndroidManifest() {
    apiSensitiveTemplate = false
    checkCreateTemplate("other", "AndroidManifest", false
    ) { t, _ ->
      t["newLocation"] = "src/foo/AndroidManifest.xml"
    }
  }

  @TemplateCheck
  fun testAssetsFolder() {
    apiSensitiveTemplate = false
    checkCreateTemplate("other", "AssetsFolder", false
    ) { templateMap, _ ->
      templateMap["newLocation"] = "src/main/assets/"
    }
  }

  @TemplateCheck
  fun testJavaAndJniFolder() {
    apiSensitiveTemplate = false
    checkCreateTemplate("other", "JavaFolder", false
    ) { t, _ ->
      t["newLocation"] = "src/main/java"
    }
    checkCreateTemplate("other", "JniFolder", false
    ) { t, _ ->
      t["newLocation"] = "src/main/jni"
    }
  }

  @TemplateCheck
  fun testFontFolder() {
    apiSensitiveTemplate = false
    checkCreateTemplate("other", "FontFolder", false
    ) { templateMap, _ ->
      templateMap["newLocation"] = "src/main/res/font"
    }
  }

  @TemplateCheck
  fun testRawFolder() {
    apiSensitiveTemplate = false
    checkCreateTemplate("other", "RawFolder", false
    ) { templateMap, _ ->
      templateMap["newLocation"] = "src/main/res/raw"
    }
  }

  @TemplateCheck
  fun testXmlFolder() {
    apiSensitiveTemplate = false
    checkCreateTemplate("other", "XmlFolder", false
    ) { templateMap, _ ->
      templateMap["newLocation"] = "src/main/res/xml"
    }
  }

  @TemplateCheck
  fun testRenderSourceFolder() {
    apiSensitiveTemplate = false
    checkCreateTemplate("other", "RsFolder", false
    ) { t, _ ->
      t["newLocation"] = "src/main/rs"
    }
    checkCreateTemplate("other", "ResFolder", false
    ) { t, _ ->
      t["newLocation"] = "src/main/res"
    }
    checkCreateTemplate("other", "ResourcesFolder", false
    ) { t, _ ->
      t["newLocation"] = "src/main/res"
    }
  }

  @TemplateCheck
  fun testNewLayoutResourceFile() {
    apiSensitiveTemplate = false
    checkCreateTemplate("other", "LayoutResourceFile")
  }

  @TemplateCheck
  fun testNewAppActionsResourceFile() {
    apiSensitiveTemplate = false
    checkCreateTemplate("other", "AppActionsResourceFile")
  }

  @TemplateCheck
  fun testAutomotiveMediaService() {
    apiSensitiveTemplate = false
    checkCreateTemplate("other", "AutomotiveMediaService", false)
  }

  @TemplateCheck
  fun testAutomotiveMediaServiceWithKotlin() {
    apiSensitiveTemplate = false
    checkCreateTemplate("other", "AutomotiveMediaService", false, withKotlin)
  }

  @TemplateCheck
  fun testAutomotiveMessagingService() {
    apiSensitiveTemplate = false
    checkCreateTemplate("other", "AutomotiveMessagingService")
  }

  @TemplateCheck
  fun testAutomotiveMessagingServiceWithKotlin() {
    apiSensitiveTemplate = false
    checkCreateTemplate("other", "AutomotiveMessagingService", false, withKotlin)
  }

  @TemplateCheck
  fun testWatchFaceService() {
    apiSensitiveTemplate = false
    checkCreateTemplate("other", "WatchFaceService")
  }

  @TemplateCheck
  fun testWatchFaceServiceWithKotlin() {
    apiSensitiveTemplate = false
    checkCreateTemplate("other", "WatchFaceService", true, withKotlin)
  }

  @TemplateCheck
  fun testNewValueResourceFile() {
    apiSensitiveTemplate = false
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

    override fun checkCreateTemplate(category: String, name: String, createWithProject: Boolean, customizer: ProjectStateCustomizer) {
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
