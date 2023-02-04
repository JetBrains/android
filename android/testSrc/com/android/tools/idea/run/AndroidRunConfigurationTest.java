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

import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.run.editor.NoApksProvider;
import com.android.tools.idea.run.tasks.ActivityLaunchTask;
import com.android.tools.idea.run.util.LaunchStatus;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.Mockito;

public class AndroidRunConfigurationTest extends AndroidTestCase {
  private AndroidRunConfiguration myRunConfiguration;
  private IDevice myDevice;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    ConfigurationFactory configurationFactory = AndroidRunConfigurationType.getInstance().getFactory();
    myRunConfiguration = new AndroidRunConfiguration(getProject(), configurationFactory);
    myDevice = Mockito.mock(IDevice.class);
  }

  /**
   * Verifies that public fields, which are save in configuration files (workspace.xml) are
   * not accidentally renamed during a code refactoring.
   */
  public void testPersistentFieldNames() {
    assertContainsElements(
      ContainerUtil.map(ReflectionUtil.collectFields(myRunConfiguration.getClass()), f -> f.getName()),
      "CLEAR_LOGCAT", "SHOW_LOGCAT_AUTOMATICALLY",
      "DEPLOY", "DEPLOY_APK_FROM_BUNDLE", "ARTIFACT_NAME", "PM_INSTALL_OPTIONS", "DYNAMIC_FEATURES_DISABLED_LIST",
      "ACTIVITY_EXTRA_FLAGS", "MODE", "CLEAR_APP_STORAGE");
  }

  public void testContributorsAmStartOptionsIsInlinedWithAmStartCommand() throws ExecutionException {
    myRunConfiguration.setLaunchActivity("MyActivity");

    LaunchStatus launchStatus = Mockito.mock(LaunchStatus.class);
    ConsolePrinter consolePrinter = Mockito.mock(ConsolePrinter.class);
    IDevice device = Mockito.mock(IDevice.class);
    when(device.getVersion()).thenReturn(new AndroidVersion(AndroidVersion.VersionCodes.S_V2));
    ActivityLaunchTask task = (ActivityLaunchTask)myRunConfiguration.getApplicationLaunchTask(new FakeApplicationIdProvider(),
                                                                                              myFacet,
                                                                                              "--start-profiling",
                                                                                              false,
                                                                                              launchStatus, new NoApksProvider(),
                                                                                              consolePrinter, device);

    assertEquals("am start -n \"com.example.mypackage/MyActivity\" " +
                 "-a android.intent.action.MAIN -c android.intent.category.LAUNCHER " +
                 "--start-profiling", task.getStartActivityCommand(myDevice, Mockito.mock(ConsolePrinter.class)));
  }

  public void testEmptyContributorsAmStartOptions() throws ExecutionException {
    myRunConfiguration.setLaunchActivity("MyActivity");

    LaunchStatus launchStatus = Mockito.mock(LaunchStatus.class);
    ConsolePrinter consolePrinter = Mockito.mock(ConsolePrinter.class);
    IDevice device = Mockito.mock(IDevice.class);
    when(device.getVersion()).thenReturn(new AndroidVersion(AndroidVersion.VersionCodes.S_V2));
    ActivityLaunchTask task = (ActivityLaunchTask)myRunConfiguration.getApplicationLaunchTask(new FakeApplicationIdProvider(),
                                                                                              myFacet,
                                                                                              "",
                                                                                              false,
                                                                                              launchStatus, new NoApksProvider(),
                                                                                              consolePrinter, device);
    assertEquals("am start -n \"com.example.mypackage/MyActivity\" " +
                 "-a android.intent.action.MAIN -c android.intent.category.LAUNCHER",
                 task.getStartActivityCommand(myDevice, Mockito.mock(ConsolePrinter.class)));

    when(device.getVersion()).thenReturn(new AndroidVersion(AndroidVersion.VersionCodes.TIRAMISU));
    task = (ActivityLaunchTask)myRunConfiguration.getApplicationLaunchTask(new FakeApplicationIdProvider(),
                                                                           myFacet,
                                                                           "",
                                                                           false,
                                                                           launchStatus, new NoApksProvider(),
                                                                           consolePrinter, device);
    assertEquals("am start -n \"com.example.mypackage/MyActivity\" " +
                 "-a android.intent.action.MAIN -c android.intent.category.LAUNCHER --splashscreen-show-icon",
                 task.getStartActivityCommand(myDevice, Mockito.mock(ConsolePrinter.class)));
  }

  private static class FakeApplicationIdProvider implements ApplicationIdProvider {
    @NotNull
    @Override
    public String getPackageName() {
      return "com.example.mypackage";
    }

    @Nullable
    @Override
    public String getTestPackageName() {
      return "com.example.test.mypackage";
    }
  }
}
