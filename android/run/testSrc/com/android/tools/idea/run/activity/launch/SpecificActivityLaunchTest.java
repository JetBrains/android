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
import static com.android.tools.idea.util.ModuleExtensionsKt.getAndroidFacet;
import static com.intellij.testFramework.UsefulTestCase.assertEmpty;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.tools.deployer.model.App;
import com.android.tools.idea.execution.common.stats.RunStats;
import com.android.tools.idea.run.ValidationError;
import com.android.tools.idea.run.activity.SpecificActivityLocator;
import com.android.tools.idea.run.editor.NoApksProvider;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.testFramework.EdtRule;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

public class SpecificActivityLaunchTest {
  @Rule
  public EdtRule edt = new EdtRule();
  @Rule
  public AndroidProjectRule myProjectRule = AndroidProjectRule.inMemory();

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
    SpecificActivityLaunch.State state = new SpecificActivityLaunch.State();
    state.ACTIVITY_CLASS = "com.example.app.MyActivity";
    IDevice device = Mockito.mock(IDevice.class);
    App app =
      createApp(device, "com.example.app", Collections.emptyList(), new ArrayList<>(Collections.singleton("com.example.app.MyActivity")));

    state.launch(device, app, new NoApksProvider(), false, "", new EmptyTestConsoleView(), new RunStats(myProjectRule.getProject()));
    Mockito.verify(device).executeShellCommand(
      eq("am start -n com.example.app/com.example.app.MyActivity -a android.intent.action.MAIN -c android.intent.category.LAUNCHER"),
      any(IShellOutputReceiver.class),
      eq(15L),
      eq(TimeUnit.SECONDS));
  }
}