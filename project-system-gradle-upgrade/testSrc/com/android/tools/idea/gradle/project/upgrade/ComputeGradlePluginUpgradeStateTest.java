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
package com.android.tools.idea.gradle.project.upgrade;

import static com.android.tools.idea.gradle.project.upgrade.GradlePluginUpgradeState.Importance.FORCE;
import static com.android.tools.idea.gradle.project.upgrade.GradlePluginUpgradeState.Importance.NO_UPGRADE;
import static com.android.tools.idea.gradle.project.upgrade.GradlePluginUpgradeState.Importance.RECOMMEND;
import static com.android.tools.idea.gradle.project.upgrade.GradlePluginUpgrade.computeGradlePluginUpgradeState;
import static org.junit.Assert.assertEquals;

import com.android.ide.common.repository.GradleVersion;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ComputeGradlePluginUpgradeStateTest {
  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][] {
      // Stable or RC to later stable should recommend an upgrade to the stable version.
      {"7.0.0", "7.1.0", Collections.emptyList(), RECOMMEND, "7.1.0"},
      {"7.0.0-rc01", "7.1.0", Collections.emptyList(), RECOMMEND, "7.1.0"},
      {"7.1.0-rc01", "7.1.0", Collections.emptyList(), RECOMMEND, "7.1.0"},
      {"7.0.0", "7.2.0", Collections.emptyList(), RECOMMEND, "7.2.0"},
      {"7.0.0-rc01", "7.2.0", Collections.emptyList(), RECOMMEND, "7.2.0"},

      // Stable or RC to later RC should recommend an upgrade to the RC.
      {"7.0.0", "7.1.0-rc01", Collections.emptyList(), RECOMMEND, "7.1.0-rc01"},
      {"7.0.0-rc01", "7.1.0-rc01", Collections.emptyList(), RECOMMEND, "7.1.0-rc01"},
      {"7.1.0-rc01", "7.1.0-rc02", Collections.emptyList(), RECOMMEND, "7.1.0-rc02"},
      {"7.0.0", "7.2.0-rc01", Collections.emptyList(), RECOMMEND, "7.2.0-rc01"},
      {"7.0.0-rc01", "7.2.0-rc01", Collections.emptyList(), RECOMMEND, "7.2.0-rc01"},

      // Stable or RC to Alpha or Beta, with no later stable known, should not recommend any upgrade
      {"7.0.0", "7.1.0-alpha01", Collections.emptyList(), NO_UPGRADE, "7.0.0"},
      {"7.0.0-rc01", "7.1.0-alpha01", Collections.emptyList(), NO_UPGRADE, "7.0.0-rc01"},
      {"7.0.0", "7.1.0-beta01", Collections.emptyList(), NO_UPGRADE, "7.0.0"},
      {"7.0.0-rc01", "7.1.0-beta01", Collections.emptyList(), NO_UPGRADE, "7.0.0-rc01"},
      {"7.0.0", "7.2.0-alpha01", Collections.emptyList(), NO_UPGRADE, "7.0.0"},

      // Stable or RC to Alpha or Beta where a later stable version is known should recommend upgrade to the later stable
      {"7.0.0", "7.1.0-alpha01", Collections.singletonList("7.0.1"), RECOMMEND, "7.0.1"},
      {"7.0.0-rc01", "7.1.0-alpha01", Collections.singletonList("7.0.0"), RECOMMEND, "7.0.0"},
      {"7.0.0", "7.1.0-beta01", Arrays.asList("7.0.1", "7.0.2"), RECOMMEND, "7.0.2"},
      {"7.0.0-rc01", "7.1.0-beta01", Arrays.asList("7.0.0", "7.0.1", "7.0.2"), RECOMMEND, "7.0.2"},
      {"7.0.0", "7.2.0-alpha01", Arrays.asList("7.1.0", "7.1.1", "7.0.0", "7.0.1", "7.0.2"), RECOMMEND, "7.1.1"},

      // Alpha or Beta to any later version should force an upgrade
      {"7.0.0-alpha01", "7.0.0-alpha02", Collections.emptyList(), FORCE, "7.0.0-alpha02"},
      {"7.0.0-alpha02", "7.0.0-beta01", Collections.emptyList(), FORCE, "7.0.0-beta01"},
      {"7.0.0-alpha02", "7.0.0-rc01", Collections.emptyList(), FORCE, "7.0.0-rc01"},
      {"7.0.0-alpha02", "7.0.0", Collections.emptyList(), FORCE, "7.0.0"},
      {"7.0.0-alpha02", "7.0.1", Collections.emptyList(), FORCE, "7.0.1"},
      {"7.0.0-alpha02", "7.1.0-alpha01", Collections.emptyList(), FORCE, "7.1.0-alpha01"},
      {"7.0.0-alpha02", "7.1.0", Collections.emptyList(), FORCE, "7.1.0"},
      {"7.0.0-beta01", "7.0.0-beta02", Collections.emptyList(), FORCE, "7.0.0-beta02"},
      {"7.0.0-beta02", "7.0.0-rc01", Collections.emptyList(), FORCE, "7.0.0-rc01"},
      {"7.0.0-beta02", "7.0.0", Collections.emptyList(), FORCE, "7.0.0"},
      {"7.0.0-beta02", "7.0.1", Collections.emptyList(), FORCE, "7.0.1"},
      {"7.0.0-beta02", "7.1.0-alpha01", Collections.emptyList(), FORCE, "7.1.0-alpha01"},
      {"7.0.0-beta02", "7.1.0", Collections.emptyList(), FORCE, "7.1.0"},

      // The forced upgrade should be to a stable version in the same series, if a compatible one is available
      {"7.0.0-alpha01", "7.0.0-alpha03", Collections.singletonList("7.0.0-alpha02"), FORCE, "7.0.0-alpha03"},
      {"7.0.0-alpha01", "7.0.0-alpha03", Collections.singletonList("7.0.0"), FORCE, "7.0.0-alpha03"},
      {"7.0.0-alpha02", "7.0.0-beta01", Collections.singletonList("7.0.0-alpha03"), FORCE, "7.0.0-beta01"},
      {"7.0.0-alpha02", "7.0.0-rc01", Collections.singletonList("7.0.0-alpha03"), FORCE, "7.0.0-rc01"},
      {"7.0.0-alpha02", "7.0.0", Arrays.asList("7.0.0", "7.0.1"), FORCE, "7.0.0"},
      {"7.0.0-alpha02", "7.0.1", Arrays.asList("7.0.0", "7.0.1"), FORCE, "7.0.1"},
      {"7.0.0-alpha02", "7.1.0-alpha01", Collections.singletonList("7.0.0"), FORCE, "7.0.0"},
      {"7.0.0-alpha02", "7.1.0-alpha01", Arrays.asList("7.0.0", "7.0.1"), FORCE, "7.0.1"},
      {"7.0.0-alpha02", "7.1.0", Arrays.asList("7.0.0", "7.0.1", "7.1.0", "7.1.1"), FORCE, "7.0.1"},
      {"7.0.0-beta01", "7.0.0-beta03", Collections.singletonList("7.0.0-beta02"), FORCE, "7.0.0-beta03"},
      {"7.0.0-beta01", "7.0.0-beta03", Collections.singletonList("7.0.0"), FORCE, "7.0.0-beta03"},
      {"7.0.0-beta02", "7.0.0-rc01", Collections.singletonList("7.0.0-beta03"), FORCE, "7.0.0-rc01"},
      {"7.0.0-beta02", "7.0.0-rc01", Collections.singletonList("7.0.0"), FORCE, "7.0.0-rc01"},
      {"7.0.0-beta02", "7.0.0", Arrays.asList("7.0.0", "7.0.1"), FORCE, "7.0.0"},
      {"7.0.0-beta02", "7.0.1", Arrays.asList("7.0.0", "7.0.1"), FORCE, "7.0.1"},
      {"7.0.0-beta02", "7.1.0-alpha01", Collections.singletonList("7.0.0"), FORCE, "7.0.0"},
      {"7.0.0-beta02", "7.1.0-alpha01", Arrays.asList("7.0.0", "7.0.1"), FORCE, "7.0.1"},
      {"7.0.0-beta02", "7.1.0", Arrays.asList("7.0.0", "7.0.1"), FORCE, "7.0.1"},

      // If the latest known version is earlier than or equal to the current version, there should be no upgrade
      {"7.0.0-alpha01", "7.0.0-alpha01", Collections.emptyList(), NO_UPGRADE, "7.0.0-alpha01"},
      {"7.0.0-alpha02", "7.0.0-alpha01", Collections.emptyList(), NO_UPGRADE, "7.0.0-alpha02"},
      {"7.0.0-beta01", "7.0.0-beta01", Collections.emptyList(), NO_UPGRADE, "7.0.0-beta01"},
      {"7.0.0-beta02", "7.0.0-beta01", Collections.emptyList(), NO_UPGRADE, "7.0.0-beta02"},
      {"7.0.0-beta01", "7.0.0-alpha02", Collections.emptyList(), NO_UPGRADE, "7.0.0-beta01"},
      {"7.0.0-rc01", "7.0.0-rc01", Collections.emptyList(), NO_UPGRADE, "7.0.0-rc01"},
      {"7.0.0-rc02", "7.0.0-rc01", Collections.emptyList(), NO_UPGRADE, "7.0.0-rc02"},
      {"7.0.0-rc01", "7.0.0-beta02", Collections.emptyList(), NO_UPGRADE, "7.0.0-rc01"},
      {"7.0.0-rc01", "7.0.0-alpha02", Collections.emptyList(), NO_UPGRADE, "7.0.0-rc01"},
      {"7.0.0", "7.0.0", Collections.emptyList(), NO_UPGRADE, "7.0.0"},
      {"7.0.1", "7.0.0", Collections.emptyList(), NO_UPGRADE, "7.0.1"},
      {"7.0.0", "7.0.0-rc01", Collections.emptyList(), NO_UPGRADE, "7.0.0"},
      {"7.0.0", "7.0.0-beta01", Collections.emptyList(), NO_UPGRADE, "7.0.0"},
      {"7.0.0", "7.0.0-alpha01", Collections.emptyList(), NO_UPGRADE, "7.0.0"},
      // Even if the set of published versions contains later versions.
      {"7.0.0", "7.0.0", Collections.singletonList("7.0.1"), NO_UPGRADE, "7.0.0"},

      // Versions earlier than our minimum supported version should force an upgrade.
      {"3.1.0", "7.0.0", Collections.emptyList(), FORCE, "7.0.0"},
      // If we know of published versions earlier than our latestKnownVersion, prefer to upgrade to those.
      {"3.1.0", "3.2.0", Arrays.asList("3.2.0", "3.3.0", "3.4.0", "3.5.0", "3.6.0", "7.0.0"), FORCE, "3.2.0"},
      // If we do not know of any published versions earlier than our latestKnown, upgrade to latestKnown
      {"3.1.0", "3.2.0", Arrays.asList("3.3.0", "3.4.0"), FORCE, "3.2.0"},
      // If we know of multiple published versions in the stable series, upgrade to the latest if it is compatible.
      {"3.1.0", "7.0.0", Arrays.asList("3.2.0-alpha01", "3.2.0-beta02", "3.2.0", "3.2.1", "3.2.2", "3.3.0", "3.3.1"), FORCE, "3.2.2"},
      {"3.1.0", "3.2.1", Arrays.asList("3.2.0-alpha01", "3.2.0-beta02", "3.2.0", "3.2.1", "3.2.2", "3.3.0", "3.3.1"), FORCE, "3.2.1"},
    });
  }

  private final GradleVersion current;
  private final GradleVersion latestKnown;
  private final Set<GradleVersion> published;
  private final GradlePluginUpgradeState.Importance importance;
  private final GradleVersion expectedTarget;

  public ComputeGradlePluginUpgradeStateTest(@NotNull String current,
                                             @NotNull String latestKnown,
                                             @NotNull List<String> published,
                                             @NotNull GradlePluginUpgradeState.Importance importance,
                                             @NotNull String expectedTarget) {
    this.current = GradleVersion.parse(current);
    this.latestKnown = GradleVersion.parse(latestKnown);
    this.published = published.stream().map(GradleVersion::parse).collect(Collectors.toSet());
    this.importance = importance;
    this.expectedTarget = GradleVersion.parse(expectedTarget);
  }

  @Test
  public void testComputeGradlePluginUpgradeState() {
    GradlePluginUpgradeState state = computeGradlePluginUpgradeState(current, latestKnown, published);
    assertEquals("computing upgrade state from " + current + " to " + latestKnown + " with published versions " + published,
                 new GradlePluginUpgradeState(importance, expectedTarget), state);
  }
}