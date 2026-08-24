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
import static com.android.tools.idea.run.configuration.execution.TestUtilsKt.createConnectedDevice;
import static com.android.tools.idea.util.ModuleExtensionsKt.getAndroidFacet;
import static com.intellij.testFramework.UsefulTestCase.assertEmpty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.adblib.ConnectedDevice;
import com.android.adblib.DeviceSelector;
import com.android.adblib.testing.FakeAdbSession;
import com.android.ddmlib.IDevice;
import com.android.flags.junit.FlagRule;
import com.android.tools.deployer.model.App;
import com.android.tools.idea.execution.common.stats.RunStats;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.run.ValidationError;
import com.android.tools.idea.run.activity.SpecificActivityLocator;
import com.android.tools.idea.run.editor.NoApksProvider;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.testFramework.EdtRule;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

public class SpecificActivityLaunchTest {
  @Rule
  public EdtRule edt = new EdtRule();
  @Rule
  public AndroidProjectRule myProjectRule = AndroidProjectRule.inMemory();
  @Rule
  public FlagRule<Boolean> flagRule = new FlagRule<>(StudioFlags.DEPLOYER_USE_CONNECTED_DEVICE, true);

  private final FakeAdbSession fakeSession = new FakeAdbSession();

  @After
  public void tearDown() {
    fakeSession.close();
  }

  @Test
  public void testGetActivity() {
    SpecificActivityLaunch.State state = new SpecificActivityLaunch.State();
    SpecificActivityLocator activityLocator = state.getActivityLocator(getAndroidFacet(myProjectRule.getModule()));
    assertNotNull(activityLocator);
  }

  @Test
  public void testCheckConfigurationSkipValidation() {
    SpecificActivityLaunch.State state = new SpecificActivityLaunch.State();
    state.SKIP_ACTIVITY_VALIDATION = true;
    List<ValidationError> errors = state.checkConfiguration(getAndroidFacet(myProjectRule.getModule()));
    assertEmpty(errors);
  }

  @Test
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
    List<ValidationError> errors = state.checkConfiguration(getAndroidFacet(myProjectRule.getModule()));
    assertEmpty(errors);
  }

  @Test
  public void testLaunch() throws Exception {
    String deviceSerial = "1234";
    ConnectedDevice connectedDevice = createConnectedDevice(fakeSession, deviceSerial, 31);
    DeviceSelector deviceSelector = DeviceSelector.Companion.fromSerialNumber(deviceSerial);
    String command = "am start -n com.example.app/com.example.app.MyActivity -a android.intent.action.MAIN -c android.intent.category.LAUNCHER";
    fakeSession.getDeviceServices().configureShellCommand(deviceSelector, command, "", "", 0);

    SpecificActivityLaunch.State state = new SpecificActivityLaunch.State();
    state.ACTIVITY_CLASS = "com.example.app.MyActivity";
    IDevice device = mock(IDevice.class);
    App app =
        createApp("com.example.app", Collections.emptyList(), new ArrayList<>(Collections.singleton("com.example.app.MyActivity")));

    state.launch(device, connectedDevice, app, new NoApksProvider(), false, "", new EmptyTestConsoleView(), new RunStats(myProjectRule.getProject()));
    assertEquals(List.of(command), fakeSession.getDeviceServices().getShellRequests().stream().map(r -> r.getCommand()).toList());
  }
}