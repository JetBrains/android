/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.util;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.testing.AndroidGradleProjectRule;
import com.intellij.openapi.project.Project;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class GradleProjectSettingsFinderTest {
  private GradleProjectSettingsFinder mySettingsFinder;

  @Rule
  public AndroidGradleProjectRule projectRule = new AndroidGradleProjectRule();

  @Before
  public void setUp() throws Exception {
    mySettingsFinder = new GradleProjectSettingsFinder();
  }

  @Test
  public void testWithNonGradleProject() {
    Project project = projectRule.getProject();
    GradleProjectSettings settings = mySettingsFinder.findGradleProjectSettings(project);
    assertThat(settings).isNull();
  }
}