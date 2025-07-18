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

import static com.android.SdkConstants.GRADLE_PLUGIN_MINIMUM_VERSION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.ide.common.repository.AgpVersion;
import java.util.Arrays;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Tests for {@link GradlePluginUpgrade#versionsAreIncompatible(AgpVersion, AgpVersion)}.
 */
@RunWith(Parameterized.class)
public class VersionsAreIncompatibleTest {
  @Parameterized.Parameters(name="{0},{1}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
      {"4.1.0-alpha09", "4.1.0-alpha09", false},
      {"4.1.0-alpha09", "4.1.0-alpha91", true},
      {"4.1.0-alpha09", "4.1.0-alpha10", true},
      {"4.1.0-alpha09", "4.1.0-beta01", true},
      {"4.1.0-alpha09", "4.1.0", true},
      {"4.1.0", "4.1.1", false},
      {"4.1.0", "7.0.0", false},
      {"4.1.0-beta01", "4.2.0-alpha10", true},
      {"4.1.0", "4.2.0-alpha10", false},
      {"4.1.0-alpha01", "4.1.0-dev", false},
      {"4.1.0-alpha08", "4.1.0-alpha08", false},
      {"4.1.0-alpha09", "4.1.0-alpha08", true},
      {"4.2.0", "4.1.0-alpha08", true},
      {"4.2.0-alpha01", "4.1.0-alpha08", true},
      {"4.1.0-alpha01", "4.2.0-alpha08", true},

      // Treat -rc as effectively stable.  (Upgrades will be recommended, but not forced)
      {"4.1.1-rc01", "4.3.0-dev", false},
      {"4.1.1-rc01", "4.3.0-alpha01", false},
      {"4.2.0-rc02", "4.2.0-rc03", false},
      {"4.2.0-rc02", "4.2.0", false},
      {"4.2.0-rc02", "4.3.0", false},

      {"4.2.0-alpha03", "4.2.0-rc01", true},
      {"4.2.0-beta03", "4.2.0-rc02", true},
      {"4.2.0-alpha03", "4.3.0", true},
      {"4.2.0-alpha05", "4.2.0", true},

      {"4.2.0-rc01", "4.3.0-alpha01", false},
      {"4.2.0-rc02", "4.3.0-alpha01", false},
      {"4.1.1", "4.3.0-alpha01", false},

      // Force upgrades from -dev to any later stable version.
      {"4.2.0-dev", "4.2.0", true},
      // Declare AGP -dev incompatible with earlier stable versions.
      {"4.2.0-dev", "4.1.0", true},

      // Do not force upgrades to -dev of prereleases.
      {"4.2.0-alpha01", "4.2.0-dev", false},
      {"4.2.0-beta02", "4.2.0-dev", false},
      {"4.2.0-rc03", "4.2.0-dev", false},

      // Do not force upgrades to -dev of previous-cycle previews.
      {"4.2.0-alpha01", "4.3.0-dev", false},
      {"4.2.0-beta02", "4.3.0-dev", false},
      {"4.2.0-rc03", "4.3.0-dev", false},

      // Force upgrades from -dev of previous-cycle to previews of current cycle.
      {"4.2.0-dev", "4.3.0-dev", true},
      {"4.2.0-dev", "4.3.0-alpha01", true},
      {"4.2.0-dev", "4.3.0-beta02", true},
      {"4.2.0-dev", "4.3.0-rc03", true},

      // Do not force upgrades from -dev to previews of the same cycle.
      {"4.2.0-dev", "4.2.0-alpha01", false},
      {"4.2.0-dev", "4.2.0-beta02", false},
      // But RCs are treated like releases.
      {"4.2.0-dev", "4.2.0-rc03", true},
    });
  }

  @NotNull private final AgpVersion myCurrent;
  @NotNull private final AgpVersion myRecommended;

  private final boolean myForceUpgrade;

  private static final AgpVersion unsupportedVersion = AgpVersion.parse("3.6.0");

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
