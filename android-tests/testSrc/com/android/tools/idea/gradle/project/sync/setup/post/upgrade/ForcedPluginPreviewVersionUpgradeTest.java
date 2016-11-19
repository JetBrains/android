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
package com.android.tools.idea.gradle.project.sync.setup.post.upgrade;

import com.android.ide.common.repository.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link ForcedPluginPreviewVersionUpgrade}.
 */
@RunWith(Parameterized.class)
public class ForcedPluginPreviewVersionUpgradeTest {
  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
      {"2.0.0-alpha9", "2.0.0-alpha9", false},
      {"2.0.0-alpha9", "2.0.0-alpha10", true},
      {"2.0.0-alpha9", "2.0.0-beta1", true},
      {"2.0.0-alpha9", "2.0.0", true},
      {"2.0.0", "2.0.1", false},
      {"2.0.0", "3.0.0", false},
      {"1.5.0-beta1", "2.0.0-alpha10", true},
      {"1.5.0", "2.0.0-alpha10", false},
    });
  }

  @NotNull private final GradleVersion myCurrent;
  @NotNull private final String myRecommended;

  private final boolean myForceUpgrade;

  public ForcedPluginPreviewVersionUpgradeTest(@NotNull String current, @NotNull String recommended, boolean forceUpgrade) {
    myCurrent = GradleVersion.parse(current);
    myRecommended = recommended;
    myForceUpgrade = forceUpgrade;
  }

  @Test
  public void shouldPreviewBeForcedToUpgradePluginVersion() {
    assertEquals(myForceUpgrade, ForcedPluginPreviewVersionUpgrade.shouldPreviewBeForcedToUpgradePluginVersion(myRecommended, myCurrent));
  }
}