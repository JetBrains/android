/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.run;

import static com.android.tools.idea.run.configuration.execution.TestUtilsKt.createApp;
import static com.android.tools.idea.util.ModuleExtensionsKt.getAndroidFacet;
import static com.intellij.testFramework.UsefulTestCase.assertContainsElements;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.deployer.model.App;
import com.android.tools.idea.execution.common.stats.RunStats;
import com.android.tools.idea.run.editor.NoApksProvider;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

public class AndroidRunConfigurationTest {
  @Rule
  public AndroidProjectRule myProjectRule = AndroidProjectRule.inMemory();
  private AndroidRunConfiguration myRunConfiguration;

  @Before
  public void setUp() throws Exception {
    ConfigurationFactory configurationFactory = AndroidRunConfigurationType.getInstance().getFactory();
    myRunConfiguration = new AndroidRunConfiguration(myProjectRule.getProject(), configurationFactory);
  }

  /**
   * Verifies that public fields, which are save in configuration files (workspace.xml) are
   * not accidentally renamed during a code refactoring.
   */
  @Test
  public void testPersistentFieldNames() {
    assertContainsElements(
      ContainerUtil.map(ReflectionUtil.collectFields(myRunConfiguration.getClass()), f -> f.getName()),
      "CLEAR_LOGCAT", "SHOW_LOGCAT_AUTOMATICALLY",
      "DEPLOY", "DEPLOY_APK_FROM_BUNDLE", "ARTIFACT_NAME", "PM_INSTALL_OPTIONS", "DYNAMIC_FEATURES_DISABLED_LIST",
      "ACTIVITY_EXTRA_FLAGS", "MODE", "CLEAR_APP_STORAGE");
  }

  @Test
  public void testContributorsAmStartOptionsIsInlinedWithAmStartCommand() throws Exception {
    myRunConfiguration.setLaunchActivity("com.example.mypackage.MyActivity");

    ConsoleView consolePrinter = Mockito.mock(ConsoleView.class);
    IDevice device = Mockito.mock(IDevice.class);
    when(device.getVersion()).thenReturn(new AndroidVersion(AndroidVersion.VersionCodes.S_V2));

    final App app =
      createApp(device, "com.example.mypackage", Collections.emptyList(), Collections.singletonList("com.example.mypackage.MyActivity"));
    myRunConfiguration.launch(app,
                              device,
                              getAndroidFacet(myProjectRule.getModule()),
                              "--start-profiler file",
                              false,
                              new NoApksProvider(),
                              consolePrinter,
                              new RunStats(myProjectRule.getProject()));
    verify(device).executeShellCommand(eq("am start -n com.example.mypackage/com.example.mypackage.MyActivity " +
                                          "-a android.intent.action.MAIN -c android.intent.category.LAUNCHER --start-profiler file"),
                                       any(), anyLong(), any());
  }

  @Test
  public void testEmptyContributorsAmStartOptions() throws Exception {
    myRunConfiguration.setLaunchActivity("com.example.mypackage.MyActivity");

    ConsoleView consolePrinter = Mockito.mock(ConsoleView.class);
    IDevice device = Mockito.mock(IDevice.class);
    when(device.getVersion()).thenReturn(new AndroidVersion(AndroidVersion.VersionCodes.S_V2));
    final App app =
      createApp(device, "com.example.mypackage", Collections.emptyList(), Collections.singletonList("com.example.mypackage.MyActivity"));
    myRunConfiguration.launch(app,
                              device,
                              getAndroidFacet(myProjectRule.getModule()),
                              "",
                              false,
                              new NoApksProvider(),
                              consolePrinter,
                              new RunStats(myProjectRule.getProject()));
    verify(device).executeShellCommand(eq("am start -n com.example.mypackage/com.example.mypackage.MyActivity " +
                 "-a android.intent.action.MAIN -c android.intent.category.LAUNCHER"),
                 any(), anyLong(), any());

    when(device.getVersion()).thenReturn(new AndroidVersion(AndroidVersion.VersionCodes.TIRAMISU));
    myRunConfiguration.launch(app,
                              device,
                              getAndroidFacet(myProjectRule.getModule()),
                              "",
                              false,
                              new NoApksProvider(),
                              consolePrinter,
                              new RunStats(myProjectRule.getProject()));
    verify(device).executeShellCommand(eq("am start -n com.example.mypackage/com.example.mypackage.MyActivity " +
                                          "-a android.intent.action.MAIN -c android.intent.category.LAUNCHER --splashscreen-show-icon"),
                                       any(), anyLong(), any());
  }


  @Test
  public void testDeepLinkLaunch() throws Exception {

   testDeepLink("example://host/path", "", "am start -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d 'example://host/path'");
   testDeepLink("example://host/path", "-D", "am start -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d 'example://host/path' -D");
   testDeepLink("https://example.com/example?foo=bar&baz=duck", "", "am start -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d 'https://example.com/example?foo=bar&baz=duck'");
   testDeepLink("text'with'single'quotes", "", "am start -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d 'text'\\''with'\\''single'\\''quotes'");
   testDeepLink("example://host/path", "", "am start -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d 'example://host/path'");

  }

  private void testDeepLink(String link, String extraFlags, String expectedCommand) throws Exception {
    ConsoleView consolePrinter = Mockito.mock(ConsoleView.class);
    IDevice device = Mockito.mock(IDevice.class);
    when(device.getVersion()).thenReturn(new AndroidVersion(AndroidVersion.VersionCodes.S_V2));
    final App app =
      createApp(device, "com.example.mypackage", Collections.emptyList(), Collections.singletonList("com.example.mypackage.MyActivity"));

    myRunConfiguration.setLaunchUrl(link);

    myRunConfiguration.launch(app,
                              device,
                              getAndroidFacet(myProjectRule.getModule()),
                              extraFlags,
                              false,
                              new NoApksProvider(),
                              consolePrinter,
                              new RunStats(myProjectRule.getProject()));
    verify(device).executeShellCommand(eq(expectedCommand), any(), anyLong(), any());
  }
}
