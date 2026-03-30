/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.configurations;

import static com.android.tools.configurations.ConfigurationListener.CFG_ACTIVITY;
import static com.android.tools.configurations.ConfigurationListener.CFG_THEME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.configurations.ConfigurationSettings;
import com.android.tools.configurations.EnvironmentContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public class EnvironmentContextTest {

  private static class StubContext implements EnvironmentContext.Context {
    public ConfigurationSettings settings = Mockito.mock(ConfigurationSettings.class);
    public FolderConfiguration config = new FolderConfiguration();
    public String calculatedActivity = null;
    public String preferredTheme = "@style/DefaultTheme";

    @NotNull @Override public ConfigurationSettings getSettings() { return settings; }
    @NotNull @Override public FolderConfiguration getEditedConfig() { return config; }
    @Nullable @Override public String calculateActivity() { return calculatedActivity; }
    @NotNull @Override public String getPreferredTheme() { return preferredTheme; }
    @Nullable @Override public IAndroidTarget getTargetForRendering(@Nullable IAndroidTarget target) { return null; }
  }

  @Test
  public void testActivityCachingAndCalculation() {
    StubContext context = new StubContext();
    EnvironmentContext env = new EnvironmentContext(context);

    assertNull("Activity should be null initially if context calculates null", env.getActivity());

    context.calculatedActivity = "com.example.MainActivity";
    assertNull("Activity should remain cached as null (via internal NO_ACTIVITY marker)", env.getActivity());

    int flags = env.setActivity("com.example.NewActivity");
    assertEquals(CFG_ACTIVITY, flags);
    assertEquals("com.example.NewActivity", env.getActivity());
  }

  @Test
  public void testThemePrefixResolution() {
    StubContext context = new StubContext();
    EnvironmentContext env = new EnvironmentContext(context);

    assertEquals("@style/DefaultTheme", env.getTheme());

    int flags = env.setTheme("MyCustomTheme");
    assertEquals(CFG_THEME, flags);

    // Verifies ResourceUtils automatically prepends @style/
    assertEquals("@style/MyCustomTheme", env.getTheme());
  }
}
