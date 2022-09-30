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
package com.android.tools.idea.gradle.project.upgrade;

import com.android.ide.common.repository.GradleVersion.AgpVersion;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static com.android.SdkConstants.GRADLE_PLUGIN_MINIMUM_VERSION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link GradlePluginUpgrade#versionsAreIncompatible(AgpVersion, AgpVersion)}.
 */
@RunWith(Parameterized.class)
public class VersionsAreIncompatibleTest {
  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
      {"3.3.0-alpha9", "3.3.0-alpha9", false},
      {"3.3.0-alpha9", "3.3.0-alpha91", true},
      // Note: alpha9 is later in this algebra than alpha10.
      {"3.3.0-alpha9", "3.3.0-alpha10", true},
      {"3.3.9-alpha09", "3.3.0-alpha10", true},
      {"3.3.0-alpha9", "3.3.0-beta1", true},
      {"3.3.0-alpha9", "3.3.0", true},
      {"3.3.0", "3.3.1", false},
      {"3.3.0", "4.0.0", false},
      {"3.3.0-beta1", "3.4.0-alpha10", true},
      {"3.3.0", "3.4.0-alpha10", false},
      {"3.3.0-alpha1", "3.3.0-dev", false},
      {"3.3.0-alpha8", "3.3.0-alpha8", false},
      {"3.3.0-alpha9", "3.3.0-alpha8", true},
      {"3.4.0", "3.3.0-alpha8", true},
      {"3.4.0-alpha1", "3.3.0-alpha8", true},
      {"3.3.0-alpha1", "3.4.0-alpha8", true},

      // Treat -rc as effectively stable.  (Upgrades will be recommended, but not forced)
      {"3.3.1-rc01", "3.5.0-dev", false},
      {"3.3.1-rc01", "3.5.0-alpha01", false},
      {"3.4.0-rc02", "3.4.0-rc03", false},
      {"3.4.0-rc02", "3.4.0", false},
      {"3.4.0-rc02", "3.5.0", false},

      {"3.4.0-alpha03", "3.4.0-rc01", true},
      {"3.4.0-beta03", "3.4.0-rc02", true},
      {"3.4.0-alpha03", "3.5.0", true},
      {"3.4.0-alpha05", "3.4.0", true},

      {"3.4.0-rc01", "3.5.0-alpha01", false},
      {"3.4.0-rc02", "3.5.0-alpha01", false},
      {"3.3.1", "3.5.0-alpha01", false},

      // Force upgrades from -dev to any later stable version.
      {"3.4.0-dev", "3.4.0", true},
      // Declare AGP -dev incompatible with earlier stable versions.
      {"3.4.0-dev", "3.3.0", true},

      // Do not force upgrades to -dev of prereleases.
      {"3.4.0-alpha01", "3.4.0-dev", false},
      {"3.4.0-beta02", "3.4.0-dev", false},
      {"3.4.0-rc03", "3.4.0-dev", false},

      // Do not force upgrades to -dev of previous-cycle previews.
      {"3.4.0-alpha01", "3.5.0-dev", false},
      {"3.4.0-beta02", "3.5.0-dev", false},
      {"3.4.0-rc03", "3.5.0-dev", false},

      // Force upgrades from -dev of previous-cycle to previews of current cycle.
      {"3.4.0-dev", "3.5.0-dev", true},
      {"3.4.0-dev", "3.5.0-alpha01", true},
      {"3.4.0-dev", "3.5.0-beta02", true},
      {"3.4.0-dev", "3.5.0-rc03", true},

      // Do not force upgrades from -dev to previews of the same cycle.
      {"3.4.0-dev", "3.4.0-alpha01", false},
      {"3.4.0-dev", "3.4.0-beta02", false},
      // But RCs are treated like releases.
      {"3.4.0-dev", "3.4.0-rc03", true},
    });
  }

  @NotNull private final AgpVersion myCurrent;
  @NotNull private final AgpVersion myRecommended;

  private final boolean myForceUpgrade;

  private static final AgpVersion unsupportedVersion = AgpVersion.parse("3.1.0");

  public VersionsAreIncompatibleTest(@NotNull String current, @NotNull String recommended, boolean forceUpgrade) {
    myCurrent = AgpVersion.parse(current);
    myRecommended = AgpVersion.parse(recommended);
    myForceUpgrade = forceUpgrade;
  }

  @Test
  public void testVersionsAreIncompatible() {
    assertTrue("adjust test cases for new GRADLE_PLUGIN_MINIMUM_VERSION", myCurrent.compareTo(GRADLE_PLUGIN_MINIMUM_VERSION) >= 0);
    boolean forced = GradlePluginUpgrade.versionsAreIncompatible(myCurrent, myRecommended);
    assertEquals("are current " + myCurrent + " and latestKnown " + myRecommended + " compatible?", myForceUpgrade, forced);
    boolean forcedFromOldVersion = GradlePluginUpgrade.versionsAreIncompatible(unsupportedVersion, myRecommended);
    assertTrue("are unsupported " + unsupportedVersion + " and latestKnown " + myRecommended + " compatible?", forcedFromOldVersion);
  }
}
