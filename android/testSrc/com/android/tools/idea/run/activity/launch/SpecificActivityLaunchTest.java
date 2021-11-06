/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.run.activity.launch;

import static com.android.tools.idea.run.configuration.execution.TestUtilsKt.createApp;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.tools.deployer.model.App;
import com.android.tools.idea.run.AndroidRunConfiguration;
import com.android.tools.idea.run.AndroidRunConfigurationType;
import com.android.tools.idea.run.ApkProvider;
import com.android.tools.idea.run.ValidationError;
import com.android.tools.idea.run.activity.SpecificActivityLocator;
import com.android.tools.idea.run.activity.StartActivityFlagsProvider;
import com.android.tools.idea.run.editor.NoApksProvider;
import com.android.tools.idea.run.editor.ProfilerState;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.openapi.util.Disposer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;
import org.mockito.Mockito;

public class SpecificActivityLaunchTest extends AndroidGradleTestCase {

  @Mock StartActivityFlagsProvider startActivityFlagsProvider;
  @Mock ProfilerState profilerState;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    loadSimpleApplication();
    initMocks(this);
  }

  public void testGetLaunchTask() {
    SpecificActivityLaunch.State state = new SpecificActivityLaunch.State();
    ApkProvider apkProvider = new NoApksProvider();
    LaunchTask launchTask = state.getLaunchTask("applicationId", myAndroidFacet, startActivityFlagsProvider, profilerState, apkProvider);
    assertNotNull(launchTask);
  }

  public void testGetActivity() {

    SpecificActivityLaunch.State state = new SpecificActivityLaunch.State();
    SpecificActivityLocator activityLocator = state.getActivityLocator(myAndroidFacet);
    assertNotNull(activityLocator);
  }

  public void testCheckConfigurationSkipValidation() {
    SpecificActivityLaunch.State state = new SpecificActivityLaunch.State();
    state.SKIP_ACTIVITY_VALIDATION = true;
    List<ValidationError> errors = state.checkConfiguration(myAndroidFacet);
    assertEmpty(errors);
  }

  public void testCheckConfiguration() {
    SpecificActivityLocator specificActivityLocator = mock(SpecificActivityLocator.class);
    initMocks(this);

    SpecificActivityLaunch.State state = new SpecificActivityLaunch.State() {
      @NotNull
      @Override
      protected SpecificActivityLocator getActivityLocator(@NotNull AndroidFacet facet) {
        return specificActivityLocator;
      }
    };
    List<ValidationError> errors = state.checkConfiguration(myAndroidFacet);
    assertEmpty(errors);
  }

  public void testLaunch() throws ShellCommandUnresponsiveException, AdbCommandRejectedException, IOException, TimeoutException {
    SpecificActivityLaunch.State state = new SpecificActivityLaunch.State();
    state.ACTIVITY_CLASS = "com.example.app.MyActivity";
    IDevice device = Mockito.mock(IDevice.class);
    AndroidRunConfiguration config =
      (AndroidRunConfiguration)AndroidRunConfigurationType.getInstance().getFactory().createTemplateConfiguration(getProject());
    App app =
      createApp(device, "com.example.app", Collections.emptyList(), new ArrayList<>(Collections.singleton("com.example.app.MyActivity")));
    ConsoleViewImpl console = new ConsoleViewImpl(getProject(), false);
    Disposer.register(getTestRootDisposable(), console);
    state.launch(device, app, config, false, "", console);
    Mockito.verify(device).executeShellCommand(
      eq("am start -n com.example.app/com.example.app.MyActivity -a android.intent.action.MAIN -c android.intent.category.LAUNCHER"),
      any(IShellOutputReceiver.class),
      eq(15L),
      eq(TimeUnit.SECONDS));
  }
}