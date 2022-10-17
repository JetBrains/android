/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.upgrade;

import com.android.ide.common.repository.AgpVersion;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.*;

/**
 * Tests for {@link GradlePluginUpgrade#shouldRecommendPluginUpgrade(Project)}.
 */
@RunWith(Parameterized.class)
public class RecommendedPluginVersionUpgradeTest {
  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
      // Test for old AGP versions, which should all force rather than recommend.
      {"2.0.0-alpha9", "2.0.0-alpha9", false},
      {"1.1.0", "2.0.0", false},
      {"2.0.0-alpha9", "2.0.0-beta1", false},
      {"2.0.0-alpha9", "2.0.0", false},
      {"1.5.0-beta1", "2.0.0-alpha10", false},
      {"2.3.0-alpha1", "2.3.0-dev", false},
      {"1.5.0-beta1", "3.4.0", false},
      {"2.3.0-alpha1", "3.4.0", false},
      // Test for deprecated AGP versions, which should force (if a prerelease) or recommend (if not).  (Move these to the set
      // above and change the expectations to false when the minimum version changes, and possibly implement a new set of
      // deprecated expectations.)
      {"3.6.0-alpha01", "4.0.0", false},
      {"3.6.0-beta01", "4.0.0", false},
      {"3.6.0-rc01", "4.0.0", true},
      {"3.6.0", "4.0.0", true},
      // We never suggest to upgrade from alpha/beta version to another alpha/beta version.
      // It is handled by force upgrade.
      {"4.3.0-alpha02", "4.3.0-alpha01", false},
      {"4.3.0-alpha01", "4.3.0-alpha02", false},
      {"4.3.0-beta01", "4.3.0-alpha01", false},
      {"4.3.0-alpha01", "4.3.0-beta01", false},
      {"4.3.0-beta01", "4.3.0-beta02", false},
      {"4.4.0-alpha01", "4.3.0-alpha01", false},
      {"4.4.0-alpha01", "4.3.0-beta01", false},
      {"4.4.0-beta01", "4.3.0-alpha01", false},
      {"4.4.0-beta01", "4.3.0-beta01", false},
      // Don't recommend to upgrade from stable version to newer alpha/beta version.
      {"4.3.0", "4.3.0-alpha01", false},
      {"4.3.0", "4.3.0-beta01", false},
      {"4.3.0", "4.4.0-alpha01", false},
      {"4.3.0", "4.4.0-beta01", false},
      // Never ask for upgrading from dev version to alpha/beta version. Dev version is for internal development only.
      {"4.3.0-dev", "4.3.0-alpha01", false},
      {"4.3.0-dev", "4.3.0-beta01", false},
      {"4.3.0-dev", "4.4.0-alpha01", false},
      {"4.3.0-dev", "4.4.0-beta01", false},
      // Upgrade to dev version. We only ask for upgrading to dev version when major versions are different.
      // (Note: Force upgrade doesn't upgrade to dev version either.)
      {"4.3.0", "4.3.0-dev", false},
      {"4.3.0-alpha01", "4.3.0-dev", false},
      {"4.3.0-beta01", "4.3.0-dev", false},
      {"4.3.0", "4.4.0-dev", false},
      {"4.3.0-alpha01", "4.4.0-dev", true},
      {"4.3.0-beta01", "4.4.0-dev", true},
      // upgrade to stable version.  Upgrades from alpha/beta are forced; upgrades from rc are recommended
      {"4.4.0-alpha01", "4.3.0", false},
      {"4.4.0-beta01", "4.3.0", false},
      {"4.4.0-rc01", "4.3.0", false},
      {"4.4.0-dev", "4.3.0", false},
      {"4.4.0", "4.3.0", false},
      {"4.4.0-alpha01", "4.4.0", false},
      {"4.4.0-beta01", "4.4.0", false},
      {"4.4.0-rc01", "4.4.0", true},
      {"4.4.0-dev", "4.4.0", false},
      {"4.3.0-rc01", "4.4.0", true},
      {"4.3.0", "4.4.0", true},
      {"4.3.0-rc01", "4.3.1", true},
      {"4.3.0", "4.3.1", true},
      {"4.3.0", "4.4.0-rc01", true},
      {"4.3.1", "4.3.0-rc01", false},
      {"4.3.1", "4.3.0", false},
      // Upgrades from unsupported stable version to new stable versions are forced.
      {"1.5.0", "4.4.0", false},
      {"2.2.0", "4.4.0", false},
      // Upgrades from long-ago stable version to new stable versions are recommended.
      {"4.3.0", "8.0.0", true},
    });
  }

  @NotNull private final AgpVersion myCurrent;
  @NotNull private final AgpVersion myRecommended;

  private final boolean myRecommendUpgrade;

  public RecommendedPluginVersionUpgradeTest(@NotNull String current, @NotNull String recommended, boolean recommendUpgrade) {
    myCurrent = AgpVersion.parse(current);
    myRecommended = AgpVersion.parse(recommended);
    myRecommendUpgrade = recommendUpgrade;
  }

  @Test
  public void shouldRecommendUpgrade() {
    boolean recommended = GradlePluginUpgrade.shouldRecommendUpgrade(myCurrent, myRecommended).getUpgrade();
    assertEquals("should recommend upgrade from " + myCurrent + " to " + myRecommended + "?", myRecommendUpgrade, recommended);
  }
}