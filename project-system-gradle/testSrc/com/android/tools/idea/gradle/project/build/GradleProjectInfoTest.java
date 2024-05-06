/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.testing.AndroidProjectRule;
import org.junit.Rule;
import org.junit.Test;

public class GradleProjectInfoTest {
  @Rule
  public AndroidProjectRule rule = AndroidProjectRule.Companion.inMemory();

  @SuppressWarnings("deprecation")
  @Test
  public void testSetSkipStartupActivity() {
    GradleProjectInfo info = GradleProjectInfo.getInstance(rule.getProject());
    assertThat(info.isSkipStartupActivity()).isFalse();
    // See b/291935296
    info.setSkipStartupActivity(true);
    assertThat(info.isSkipStartupActivity()).isTrue();
  }

  @Test
  public void testSetNewProject() {
    GradleProjectInfo info = GradleProjectInfo.getInstance(rule.getProject());
    assertThat(info.isNewProject()).isFalse();
    info.setNewProject(true);
    assertThat(info.isNewProject()).isTrue();
  }
}
