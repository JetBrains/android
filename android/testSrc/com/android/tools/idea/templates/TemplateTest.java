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
package com.android.tools.idea.templates;

import static com.google.common.truth.Truth.assertWithMessage;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

public class TemplateTest extends TemplateTestBase {
  //--- Activity templates ---

  @TemplateCheck
  public void testNewBasicActivity() throws Exception {
    checkCreateTemplate("activities", "BasicActivity", false);
  }

  @TemplateCheck
  public void testNewBasicActivityWithKotlin() throws Exception {
    checkCreateTemplate("activities", "BasicActivity", false, withKotlin);
  }

  @TemplateCheck
  public void testNewProjectWithBasicActivity() throws Exception {
    checkCreateTemplate("activities", "BasicActivity", true);
  }

  @TemplateCheck
  public void testNewThingsActivity() throws Exception {
    checkCreateTemplate("activities", "AndroidThingsActivity", false);
  }

  @TemplateCheck
  public void testNewProjectWithThingsActivity() throws Exception {
    checkCreateTemplate("activities", "AndroidThingsActivity", true);
  }

  @TemplateCheck
  public void testNewProjectWithThingsActivityWithKotlin() throws Exception {
    checkCreateTemplate("activities", "AndroidThingsActivity", true, withKotlin);
  }

  @TemplateCheck
  public void testNewEmptyActivity() throws Exception {
    checkCreateTemplate("activities", "EmptyActivity", false);
  }

  @TemplateCheck
  public void testNewEmptyActivityWithKotlin() throws Exception {
    checkCreateTemplate("activities", "EmptyActivity", false, withKotlin);
  }

  @TemplateCheck
  public void testNewProjectWithEmptyActivity() throws Exception {
    checkCreateTemplate("activities", "EmptyActivity", true);
  }

  @TemplateCheck
  public void testNewViewModelActivity() throws Exception {
    checkCreateTemplate("activities", "ViewModelActivity", false);
  }

  @TemplateCheck
  public void testNewViewModelActivityWithKotlin() throws Exception {
    checkCreateTemplate("activities", "ViewModelActivity", false, withKotlin);
  }

  @TemplateCheck
  public void testNewProjectWithViewModelActivity() throws Exception {
    checkCreateTemplate("activities", "ViewModelActivity", true);
  }

  @TemplateCheck
  public void testNewTabbedActivity() throws Exception {
    checkCreateTemplate("activities", "TabbedActivity", false);
  }

  @TemplateCheck
  public void testNewProjectWithTabbedActivity() throws Exception {
    checkCreateTemplate("activities", "TabbedActivity", true);
  }

  @TemplateCheck
  public void testNewProjectWithTabbedActivityWithKotlin() throws Exception {
    checkCreateTemplate("activities", "TabbedActivity", true, withKotlin);
  }

  @TemplateCheck
  public void testNewBlankWearActivity() throws Exception {
    checkCreateTemplate("activities", "BlankWearActivity", false);
  }

  @TemplateCheck
  public void testNewProjectWithBlankWearActivity() throws Exception {
    checkCreateTemplate("activities", "BlankWearActivity", true);
  }

  @TemplateCheck
  public void testNewProjectWithBlankWearActivityWithKotlin() throws Exception {
    checkCreateTemplate("activities", "BlankWearActivity", true, withKotlin);
  }

  @TemplateCheck
  public void testNewNavigationDrawerActivity() throws Exception {
    checkCreateTemplate("activities", "NavigationDrawerActivity", false);
  }

  @TemplateCheck
  public void testNewProjectWithNavigationDrawerActivity() throws Exception {
    checkCreateTemplate("activities", "NavigationDrawerActivity", true);
  }

  @TemplateCheck
  public void testNewNavigationDrawerActivityWithKotlin() throws Exception {
    checkCreateTemplate("activities", "NavigationDrawerActivity", false, withKotlin);
  }

  @TemplateCheck
  public void testNewMasterDetailFlow() throws Exception {
    checkCreateTemplate("activities", "MasterDetailFlow", false);
  }

  @TemplateCheck
  public void testNewProjectWithMasterDetailFlow() throws Exception {
    checkCreateTemplate("activities", "MasterDetailFlow", true);
  }

  @TemplateCheck
  public void testNewProjectWithMasterDetailFlowWithKotlin() throws Exception {
    checkCreateTemplate("activities", "MasterDetailFlow", true, withKotlin);
  }

  @TemplateCheck
  public void testNewFullscreenActivity() throws Exception {
    checkCreateTemplate("activities", "FullscreenActivity", false);
  }

  @TemplateCheck
  public void testNewProjectWithFullscreenActivity() throws Exception {
    checkCreateTemplate("activities", "FullscreenActivity", true);
  }

  @TemplateCheck
  public void testNewProjectWithFullscreenActivityWithKotlin() throws Exception {
    checkCreateTemplate("activities", "FullscreenActivity", true, withKotlin);
  }

  @TemplateCheck
  public void testNewLoginActivity() throws Exception {
    checkCreateTemplate("activities", "LoginActivity", false);
  }

  @TemplateCheck
  public void testNewProjectWithLoginActivity() throws Exception {
    checkCreateTemplate("activities", "LoginActivity", true);
  }

  @TemplateCheck
  public void testNewProjectWithLoginActivityWithKotlin() throws Exception {
    checkCreateTemplate("activities", "LoginActivity", true, withKotlin);
  }

  @TemplateCheck
  public void testNewScrollActivity() throws Exception {
    checkCreateTemplate("activities", "ScrollActivity", false);
  }

  @TemplateCheck
  public void testNewProjectWithScrollActivity() throws Exception {
    checkCreateTemplate("activities", "ScrollActivity", true);
  }

  @TemplateCheck
  public void testNewProjectWithScrollActivityWithKotlin() throws Exception {
    checkCreateTemplate("activities", "ScrollActivity", true,
                        (templateMap, projectMap) -> {
                          withKotlin.customize(templateMap, projectMap);
                          templateMap.put("menuName", "menu_scroll_activity");
                        });
  }

  @TemplateCheck
  public void testNewSettingsActivity() throws Exception {
    checkCreateTemplate("activities", "SettingsActivity", false);
  }

  @TemplateCheck
  public void testNewProjectWithSettingsActivity() throws Exception {
    checkCreateTemplate("activities", "SettingsActivity", true);
  }

  @TemplateCheck
  public void testNewProjectWithSettingsActivityWithKotlin() throws Exception {
    checkCreateTemplate("activities", "SettingsActivity", true, withKotlin);
  }

  @TemplateCheck
  public void testBottomNavigationActivity() throws Exception {
    checkCreateTemplate("activities", "BottomNavigationActivity", false);
  }

  @TemplateCheck
  public void testNewProjectWithBottomNavigationActivity() throws Exception {
    checkCreateTemplate("activities", "BottomNavigationActivity", true);
  }

  @TemplateCheck
  public void testNewProjectWithBottomNavigationActivityWithKotlin() throws Exception {
    checkCreateTemplate("activities", "BottomNavigationActivity", true, withKotlin);
  }

  @TemplateCheck
  public void testNewTvActivity() throws Exception {
    checkCreateTemplate("activities", "AndroidTVActivity", false);
  }

  @TemplateCheck
  public void testNewTvActivityWithKotlin() throws Exception {
    checkCreateTemplate("activities", "AndroidTVActivity", false, withKotlin);
  }

  @TemplateCheck
  public void testNewProjectWithTvActivity() throws Exception {
    checkCreateTemplate("activities", "AndroidTVActivity", true);
  }

  @TemplateCheck
  public void testNewProjectWithTvActivityWithKotlin() throws Exception {
    checkCreateTemplate("activities", "AndroidTVActivity", true, withKotlin);
  }

  @TemplateCheck
  public void testGoogleAdMobAdsActivity() throws Exception {
    checkCreateTemplate("activities", "GoogleAdMobAdsActivity", false);
  }

  @TemplateCheck
  public void testNewProjectWithGoogleAdMobAdsActivity() throws Exception {
    checkCreateTemplate("activities", "GoogleAdMobAdsActivity", true);
  }

  @TemplateCheck
  public void testGoogleMapsActivity() throws Exception {
    checkCreateTemplate("activities", "GoogleMapsActivity", false);
  }

  @TemplateCheck
  public void testNewProjectWithGoogleMapsActivity() throws Exception {
    checkCreateTemplate("activities", "GoogleMapsActivity", true);
  }

  @TemplateCheck
  public void testGoogleMapsWearActivity() throws Exception {
    checkCreateTemplate("activities", "GoogleMapsWearActivity", false);
  }

  @TemplateCheck
  public void testNewProjectWithGoogleMapsWearActivity() throws Exception {
    checkCreateTemplate("activities", "GoogleMapsWearActivity", true);
  }

  @TemplateCheck
  public void testNewProjectWithGoogleMapsWearActivityWithKotlin() throws Exception {
    checkCreateTemplate("activities", "GoogleMapsWearActivity", true, withKotlin);
  }

  @TemplateCheck
  public void testNewAutomotiveProjectWithMediaService() throws Exception {
    checkCreateTemplate("other", "AutomotiveMediaService", true);
  }

  @TemplateCheck
  public void testNewAutomotiveProjectWithMediaServiceWithKotlin() throws Exception {
    checkCreateTemplate("other", "AutomotiveMediaService", true, withKotlin);
  }

  //--- Non-activity templates ---

  @TemplateCheck
  public void testNewBroadcastReceiver() throws Exception {
    // No need to try this template with multiple platforms, one is adequate
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "BroadcastReceiver");
  }

  @TemplateCheck
  public void testNewBroadcastReceiverWithKotlin() throws Exception {
    // No need to try this template with multiple platforms, one is adequate
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "BroadcastReceiver", false, withKotlin);
  }

  @TemplateCheck
  public void testNewContentProvider() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "ContentProvider");
  }

  @TemplateCheck
  public void testNewContentProviderWithKotlin() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "ContentProvider", false, withKotlin);
  }

  @TemplateCheck
  public void testNewSliceProvider() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "SliceProvider", false);
  }

  @TemplateCheck
  public void testNewSliceProviderWithKotlin() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "SliceProvider", false, withKotlin);
  }

  @TemplateCheck
  public void testNewCustomView() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "CustomView");
  }

  @TemplateCheck
  public void testNewIntentService() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "IntentService");
  }

  @TemplateCheck
  public void testNewIntentServiceWithKotlin() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "IntentService", false, withKotlin);
  }

  @TemplateCheck
  public void testNewListFragment() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("fragments", "ListFragment");
  }

  @TemplateCheck
  public void testNewListFragmentWithKotlin() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("fragments", "ListFragment", false, withKotlin);
  }

  @TemplateCheck
  public void testNewModalBottomSheet() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("fragments", "ModalBottomSheet");
  }

  @TemplateCheck
  public void testNewAppWidget() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "AppWidget");
  }

  @TemplateCheck
  public void testNewBlankFragment() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("fragments", "BlankFragment");
  }

  @TemplateCheck
  public void testNewBlankFragmentWithKotlin() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("fragments", "BlankFragment", false, withKotlin);
  }

  @TemplateCheck
  public void testNewSettingsFragment() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("fragments", "SettingsFragment", true);
  }

  @TemplateCheck
  public void testNewSettingsFragmentWithKotlin() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("fragments", "SettingsFragment", false, withKotlin);
  }

  @TemplateCheck
  public void testNewViewModelFragment() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("fragments", "ViewModelFragment");
  }

  @TemplateCheck
  public void testNewViewModelFragmentWithKotlin() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("fragments", "ViewModelFragment", false, withKotlin);
  }

  @TemplateCheck
  public void testNewScrollFragment() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("fragments", "ScrollFragment");
  }

  @TemplateCheck
  public void testNewScrollFragmentWithKotlin() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("fragments", "ScrollFragment", false, withKotlin);
  }

  @TemplateCheck
  public void testNewFullscreenFragment() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("fragments", "FullscreenFragment");
  }

  @TemplateCheck
  public void testNewFullscreenFragmentWithKotlin() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("fragments", "FullscreenFragment", false, withKotlin);
  }

  @TemplateCheck
  public void testNewGoogleMapsFragment() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("fragments", "GoogleMapsFragment");
  }

  @TemplateCheck
  public void testNewGoogleMapsFragmentWithKotlin() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("fragments", "GoogleMapsFragment", false, withKotlin);
  }

  @TemplateCheck
  public void testNewGoogleAdMobFragment() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("fragments", "GoogleAdMobAdsFragment");
  }

  @TemplateCheck
  public void testNewGoogleAdMobFragmentWithKotlin() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("fragments", "GoogleAdMobAdsFragment", false, withKotlin);
  }

  public void testLoginFragment() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("fragments", "LoginFragment");
  }

  @TemplateCheck
  public void testLoginFragmentWithKotlin() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("fragments", "LoginFragment", false, withKotlin);
  }

  @TemplateCheck
  public void testNewService() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "Service");
  }

  @TemplateCheck
  public void testNewServiceWithKotlin() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "Service", false, withKotlin);
  }

  @TemplateCheck
  public void testNewAidlFile() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "AidlFile");
  }

  @TemplateCheck
  public void testNewAidlFolder() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "AidlFolder", false,
                        (templateMap, projectMap) -> templateMap.put("newLocation", "foo"));
  }

  @TemplateCheck
  public void testAndroidManifest() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "AndroidManifest", false,
                        (t, p) -> t.put("newLocation", "src/foo/AndroidManifest.xml"));
  }

  @TemplateCheck
  public void testAssetsFolder() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "AssetsFolder", false,
                        (templateMap, projectMap) -> templateMap.put("newLocation", "src/main/assets/"));
  }

  @TemplateCheck
  public void testJavaAndJniFolder() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "JavaFolder", false,
                        (t, p) -> t.put("newLocation", "src/main/java"));
    checkCreateTemplate("other", "JniFolder", false,
                        (t, p) -> t.put("newLocation", "src/main/jni"));
  }

  @TemplateCheck
  public void testFontFolder() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "FontFolder", false,
                        (templateMap, projectMap) -> templateMap.put("newLocation", "src/main/res/font"));
  }

  @TemplateCheck
  public void testRawFolder() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "RawFolder", false,
                        (templateMap, projectMap) -> templateMap.put("newLocation", "src/main/res/raw"));
  }

  @TemplateCheck
  public void testXmlFolder() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "XmlFolder", false,
                        (templateMap, projectMap) -> templateMap.put("newLocation", "src/main/res/xml"));
  }

  @TemplateCheck
  public void testRenderSourceFolder() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "RsFolder", false,
                        (t, p) -> t.put("newLocation", "src/main/rs"));
    checkCreateTemplate("other", "ResFolder", false,
                        (t, p) -> t.put("newLocation", "src/main/res"));
    checkCreateTemplate("other", "ResourcesFolder", false,
                        (t, p) -> t.put("newLocation", "src/main/res"));
  }

  @TemplateCheck
  public void testNewLayoutResourceFile() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "LayoutResourceFile");
  }

  @TemplateCheck
  public void testNewAppActionsResourceFile() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "AppActionsResourceFile");
  }

  @TemplateCheck
  public void testAutomotiveMediaService() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "AutomotiveMediaService", false);
  }

  @TemplateCheck
  public void testAutomotiveMediaServiceWithKotlin() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "AutomotiveMediaService", false, withKotlin);
  }

  @TemplateCheck
  public void testAutomotiveMessagingService() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "AutomotiveMessagingService");
  }

  @TemplateCheck
  public void testAutomotiveMessagingServiceWithKotlin() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "AutomotiveMessagingService", false, withKotlin);
  }

  @TemplateCheck
  public void testWatchFaceService() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "WatchFaceService");
  }

  @TemplateCheck
  public void testWatchFaceServiceWithKotlin() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "WatchFaceService", true, withKotlin);
  }

  @TemplateCheck
  public void testNewValueResourceFile() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "ValueResourceFile");
  }

  public void testAllTemplatesCovered() throws Exception {
    if (DISABLED) {
      return;
    }

    new CoverageChecker().testAllTemplatesCovered();
  }

  // Create a dummy version of this class that just collects all the templates it will test when it is run.
  // It is important that this class is not run by JUnit!
  @SuppressWarnings("JUnitTestClassNamingConvention")
  public static class CoverageChecker extends TemplateTest {
    @Override
    protected boolean shouldRunTest() {
      return false;
    }

    // Set of templates tested with unit test
    private final Set<String> myTemplatesChecked = new HashSet<>();

    private static String getCheckKey(String category, String name, boolean createWithProject) {
      return category + ':' + name + ':' + createWithProject;
    }

    private void gatherMissedTests(File templateFile, boolean createWithProject, ArrayList<String> failures) {
      String category = templateFile.getParentFile().getName();
      String name = templateFile.getName();
      if (!isBroken(name) && !myTemplatesChecked.contains(getCheckKey(category, name, createWithProject))) {
        failures.add("\nCategory: \"" + category + "\" Name: \"" + name + "\" createWithProject: " + createWithProject);
      }
    }

    @Override
    protected void checkCreateTemplate(String category, String name, boolean createWithProject,
                                       @Nullable ProjectStateCustomizer customizer) {
      myTemplatesChecked.add(getCheckKey(category, name, createWithProject));
    }

    // The actual implementation of the test
    @Override
    public void testAllTemplatesCovered() throws Exception {
      for (Method method : getClass().getMethods()) {
        if (method.getAnnotation(TemplateCheck.class) != null && method.getName().startsWith("test")) {
          method.invoke(this);
        }
      }

      ArrayList<String> failureMessages = new ArrayList<>();
      TemplateManager manager = TemplateManager.getInstance();
      for (File templateFile : manager.getTemplates("other")) {
        gatherMissedTests(templateFile, false, failureMessages);
      }

      // Also try creating templates, not as part of creating a project
      for (File templateFile : manager.getTemplates("activities")) {
        gatherMissedTests(templateFile, true, failureMessages);
        gatherMissedTests(templateFile, false, failureMessages);
      }

      String failurePrefix = "\nThe following templates were not covered by TemplateTest. Please ensure that tests are added to cover\n" +
                             "these templates and that they are annotated with @TemplateCheck.\n\n";
      assertWithMessage(failurePrefix).that(failureMessages).isEmpty();
    }
  }
}
