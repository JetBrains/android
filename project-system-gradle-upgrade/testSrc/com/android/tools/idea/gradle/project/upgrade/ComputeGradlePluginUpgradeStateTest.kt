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
package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.gradle.project.upgrade.ComputeGradlePluginUpgradeStateTest.Companion.Flag.FutureCompatible.FUTURE_COMPATIBLE
import com.android.tools.idea.gradle.project.upgrade.ComputeGradlePluginUpgradeStateTest.Companion.Flag.FutureCompatible.FUTURE_INCOMPATIBLE
import com.android.ide.common.repository.AgpVersion.Companion.parse as agpVersion
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import com.android.tools.idea.gradle.project.upgrade.GradlePluginUpgradeState.Importance.*
import com.android.tools.idea.gradle.project.upgrade.GradlePluginUpgradeState.Importance.FORCE
import com.android.tools.idea.gradle.project.upgrade.GradlePluginUpgradeState.Importance.NO_UPGRADE
import org.junit.Assert.assertEquals

@RunWith(Parameterized::class)
class ComputeGradlePluginUpgradeStateTest(val case: Case, val flags: Flags) {

  @Test
  fun testComputeGradlePluginUpgradeState() {
    val futureCompatible = when (flags.futureCompatible) { FUTURE_INCOMPATIBLE -> false; FUTURE_COMPATIBLE -> true }
    val state = computeGradlePluginUpgradeState(case.current, case.latestKnown, case.published, futureCompatible)
    assertEquals(case.description, case.results.results[flags], state)
  }

  companion object {

    sealed interface Flag {
      enum class FutureCompatible {
        FUTURE_INCOMPATIBLE, FUTURE_COMPATIBLE
      }
    }

    data class Flags(val futureCompatible: Flag.FutureCompatible)

    val allFlagCombinations: Set<Flags> = Flag.FutureCompatible.entries.map { Flags(it) }.toSet()

    @Parameterized.Parameters(name = "{0} & {1}")
    @JvmStatic
    fun data(): Array<Array<*>> = Case.entries.flatMap { case-> allFlagCombinations.map { flag -> arrayOf(case, flag)}}.toTypedArray()

    data class Results(val results: Map<Flags, GradlePluginUpgradeState>) {
      constructor(result: GradlePluginUpgradeState): this(allFlagCombinations.associateWith { result })
      constructor(vararg results: Pair<Flags, GradlePluginUpgradeState>): this (mapOf(*results))
      init {
        check(results.keys == allFlagCombinations) {
          "All flag combinations should be covered, expected $allFlagCombinations, but got ${results.keys}"
        }
      }
    }

    fun GradlePluginUpgradeState.Importance.upgradeTo(agpVersion: String): GradlePluginUpgradeState =
      GradlePluginUpgradeState(this, agpVersion(agpVersion))

    enum class Case(
      val current: AgpVersion, val latestKnown: AgpVersion, val published: Set<AgpVersion>, val results: Results) {

      //  // Stable or RC to later stable should recommend an upgrade to the stable version.
      STABLE_TO_STABLE_MINOR("7.0.0", "7.1.0", agpVersions(), RECOMMEND.upgradeTo("7.1.0")),
      RC_TO_STABLE_ACROSS_SERIES("7.0.0-rc01", "7.1.0", agpVersions(), RECOMMEND.upgradeTo("7.1.0")),
      RC_TO_STABLE_WITHIN_SERIES("7.1.0-rc01", "7.1.0", agpVersions(), RECOMMEND.upgradeTo("7.1.0")),
      STABLE_TO_STABLE_SKIP_SERIES("7.0.0", "7.2.0", agpVersions(), RECOMMEND.upgradeTo("7.2.0")),
      RC_TO_STABLE_SKIP_SERIES("7.0.0-rc01", "7.2.0", agpVersions(), RECOMMEND.upgradeTo("7.2.0")),
      // Stable or RC to later RC should recommend an upgrade to the RC.
      STABLE_TO_RC_ACROSS_SERIES("7.0.0", "7.1.0-rc01", agpVersions(), RECOMMEND.upgradeTo("7.1.0-rc01")),
      RC_TO_RC_WITHIN_SERIES("7.0.0-rc01", "7.1.0-rc01", agpVersions(), RECOMMEND.upgradeTo("7.1.0-rc01")),
      RC_TO_RC_ACROSS_SERIES("7.1.0-rc01", "7.1.0-rc02", agpVersions(), RECOMMEND.upgradeTo("7.1.0-rc02")),
      STABLE_TO_RC_SKIP_SERIES("7.0.0", "7.2.0-rc01", agpVersions(), RECOMMEND.upgradeTo("7.2.0-rc01")),
      RC_TO_RC_SKIP_SERIES("7.0.0-rc01", "7.2.0-rc01", agpVersions(), RECOMMEND.upgradeTo("7.2.0-rc01")),
      // Stable or RC to Alpha or Beta, with no later stable known, should not recommend any upgrade
      NO_RECOMMENDATION_STABLE_TO_NEXT_ALPHA("7.0.0", "7.1.0-alpha01", agpVersions(), NO_UPGRADE.upgradeTo("7.0.0")),
      NO_RECOMMENDATION_RC_TO_NEXT_ALPHA("7.0.0-rc01", "7.1.0-alpha01", agpVersions(), NO_UPGRADE.upgradeTo("7.0.0-rc01")),
      NO_RECOMMENDATION_STABLE_TO_NEXT_BETA("7.0.0", "7.1.0-beta01", agpVersions(), NO_UPGRADE.upgradeTo("7.0.0")),
      NO_RECOMMENDATION_RC_TO_NEXT_BETA("7.0.0-rc01", "7.1.0-beta01", agpVersions(), NO_UPGRADE.upgradeTo("7.0.0-rc01")),
      NO_RECOMMENDATION_STABLE_TO_NEXT_ALPHA_SKIP_SERIES("7.0.0", "7.2.0-alpha01", agpVersions(), NO_UPGRADE.upgradeTo("7.0.0")),
      // Stable or RC to Alpha or Beta where a later stable version is known should recommend upgrade to the later stable
      WITH_NEWER_ALPHA_STABLE_TO_STABLE_PATCH("7.0.0", "7.1.0-alpha01", agpVersions("7.0.1"), RECOMMEND.upgradeTo("7.0.1")),
      WITH_NEWER_ALPHA_RC_TO_STABLE("7.0.0-rc01", "7.1.0-alpha01", agpVersions("7.0.0"), RECOMMEND.upgradeTo("7.0.0")),
      WITH_NEWER_BETA_STABLE_TO_STABLE_LATEST_PATCH("7.0.0", "7.1.0-beta01", agpVersions("7.0.1", "7.0.2"), RECOMMEND.upgradeTo("7.0.2")),
      WITH_NEWER_BETA_RC_TO_STABLE_LATEST_PATCH("7.0.0-rc01", "7.1.0-beta01", agpVersions("7.0.0", "7.0.1", "7.0.2"), RECOMMEND.upgradeTo("7.0.2")),
      WITH_NEWER_ALPHA_STABLE_TO_NEXT_STABLE_LATEST_PATCH("7.0.0", "7.2.0-alpha01", agpVersions("7.1.0", "7.1.1", "7.0.0", "7.0.1", "7.0.2"), RECOMMEND.upgradeTo("7.1.1")),
      // If there are published versions to upgrade to, don't suggest the latest version unless it is published
      STABLE_TO_STABLE_CURRENT_NOT_PUBLISHED_WITH_ALTERNATIVE("7.3.1", "7.4.1", agpVersions("7.3.0", "7.3.1", "7.4.0"), RECOMMEND.upgradeTo("7.4.0")),
      STABLE_TO_STABLE_CURRENT_PUBLISHED("7.3.1", "7.4.1", agpVersions("7.3.0", "7.3.1", "7.4.0", "7.4.1"), RECOMMEND.upgradeTo("7.4.1")),
      // If there are no published versions to upgrade to, suggest the latest version
      STABLE_TO_STABLE_CURRENT_NOT_PUBLISHED_NO_ALTERNATIVE("7.4.0", "7.4.1", agpVersions("7.3.0", "7.3.1", "7.4.0"), RECOMMEND.upgradeTo("7.4.1")),
      // Alpha or Beta to any later version should force an upgrade
      FORCE_ALPHA_TO_ALPHA("7.0.0-alpha01", "7.0.0-alpha02", agpVersions(), FORCE.upgradeTo("7.0.0-alpha02")),
      FORCE_ALPHA_TO_BETA("7.0.0-alpha02", "7.0.0-beta01", agpVersions(), FORCE.upgradeTo("7.0.0-beta01")),
      FORCE_ALPHA_TO_RC("7.0.0-alpha02", "7.0.0-rc01", agpVersions(), FORCE.upgradeTo("7.0.0-rc01")),
      FORCE_ALPHA_TO_STABLE("7.0.0-alpha02", "7.0.0", agpVersions(), FORCE.upgradeTo("7.0.0")),
      FORCE_ALPHA_TO_STABLE_WITH_PATCH("7.0.0-alpha02", "7.0.1", agpVersions(), FORCE.upgradeTo("7.0.1")),
      FORCE_ALPHA_TO_NEXT_SERIES_ALPHA("7.0.0-alpha02", "7.1.0-alpha01", agpVersions(), FORCE.upgradeTo("7.1.0-alpha01")),
      FORCE_ALPHA_TO_NEXT_SERIES_STABLE("7.0.0-alpha02", "7.1.0", agpVersions(), FORCE.upgradeTo("7.1.0")),
      FORCE_BETA_TO_BETA("7.0.0-beta01", "7.0.0-beta02", agpVersions(), FORCE.upgradeTo("7.0.0-beta02")),
      FORCE_BETA_TO_RC("7.0.0-beta02", "7.0.0-rc01", agpVersions(), FORCE.upgradeTo("7.0.0-rc01")),
      FORCE_BETA_TO_STABLE("7.0.0-beta02", "7.0.0", agpVersions(), FORCE.upgradeTo("7.0.0")),
      FORCE_BETA_TO_STABLE_WITH_PATCH("7.0.0-beta02", "7.0.1", agpVersions(), FORCE.upgradeTo("7.0.1")),
      FORCE_BETA_TO_NEXT_SERIES_ALPHA("7.0.0-beta02", "7.1.0-alpha01", agpVersions(), FORCE.upgradeTo("7.1.0-alpha01")),
      FORCE_BETA_TO_NEXT_SERIES_STABLE("7.0.0-beta02", "7.1.0", agpVersions(), FORCE.upgradeTo("7.1.0")),
      // The forced upgrade should be to a stable version in the same series, if a compatible one is available
      FORCE_ALPHA_TO_ALPHA_WHEN_PUBLISHED_VERSIONS_CONTAINS_INTERMEDIATE_ALPHA("7.0.0-alpha01", "7.0.0-alpha03", agpVersions("7.0.0-alpha02"), FORCE.upgradeTo("7.0.0-alpha03")),
      FORCE_ALPHA_TO_ALPHA_WHEN_PUBLISHED_VERSIONS_CONTAINS_FUTURE_STABLE("7.0.0-alpha01", "7.0.0-alpha03", agpVersions("7.0.0"), FORCE.upgradeTo("7.0.0-alpha03")),
      FORCE_ALPHA_TO_BETA_WHEN_PUBLISHED_VERSIONS_CONTAINS_PREVIOUS_ALPHA("7.0.0-alpha02", "7.0.0-beta01", agpVersions("7.0.0-alpha03"), FORCE.upgradeTo("7.0.0-beta01")),
      FORCE_ALPHA_TO_BETA_WHEN_PUBLISHED_VERSIONS_CONTAINS_FUTURE_STABLE("7.0.0-alpha02", "7.0.0-beta01", agpVersions("7.0.0"), FORCE.upgradeTo("7.0.0-beta01")),
      FORCE_ALPHA_TO_RC_WHEN_PUBLISHED_VERSIONS_CONTAINS_PREVIOUS_ALPHA("7.0.0-alpha02", "7.0.0-rc01", agpVersions("7.0.0-alpha03"), FORCE.upgradeTo("7.0.0-rc01")),
      FORCE_ALPHA_TO_RC_WHEN_PUBLISHED_VERSIONS_CONTAINS_FUTURE_STABLE("7.0.0-alpha02", "7.0.0-rc01", agpVersions("7.0.0"), FORCE.upgradeTo("7.0.0-rc01")),
      FORCE_ALPHA_TO_STABLE_CAPPED_BY_LATEST_KNOWN("7.0.0-alpha02", "7.0.0", agpVersions("7.0.0", "7.0.1"), FORCE.upgradeTo("7.0.0")),
      FORCE_ALPHA_TO_STABLE_PATCH("7.0.0-alpha02", "7.0.1", agpVersions("7.0.0", "7.0.1"), FORCE.upgradeTo("7.0.1")),
      FORCE_ALPHA_TO_STABLE_WHEN_STABLE_PUBLISHED("7.0.0-alpha02", "7.1.0-alpha01", agpVersions("7.0.0"), FORCE.upgradeTo("7.0.0")),
      FORCE_ALPHA_TO_STABLE_LATEST_PATCH("7.0.0-alpha02", "7.1.0-alpha01", agpVersions("7.0.0", "7.0.1"), FORCE.upgradeTo("7.0.1")), // AVAILABLE
      FORCE_ALPHA_TO_STABLE_WHEN_NEXT_STABLE("7.0.0-alpha02", "7.1.0", agpVersions("7.0.0", "7.0.1", "7.1.0", "7.1.1"), FORCE.upgradeTo("7.0.1")),
      FORCE_BETA_TO_BETA_WHEN_PUBLISHED_VERSIONS_ONLY_CONTAINS_INTERMEDIATE_BETA("7.0.0-beta01", "7.0.0-beta03", agpVersions("7.0.0-beta02"), FORCE.upgradeTo("7.0.0-beta03")),
      FORCE_BETA_TO_BETA_EVEN_WHEN_STABLE_IS_AVAILABLE("7.0.0-beta01", "7.0.0-beta03", agpVersions("7.0.0"), FORCE.upgradeTo("7.0.0-beta03")),
      FORCE_BETA_TO_RC_WHEN_PUBLISHED_VERSIONS_CONTAINS_INTERMEDIATE_BETA("7.0.0-beta02", "7.0.0-rc01", agpVersions("7.0.0-beta03"), FORCE.upgradeTo("7.0.0-rc01")),
      FORCE_BETA_TO_RC_WHEN_STABLE_IS_PUBLISHED("7.0.0-beta02", "7.0.0-rc01", agpVersions("7.0.0"), FORCE.upgradeTo("7.0.0-rc01")),
      FORCE_BETA_TO_STABLE_CAPPED_BY_LATEST_KNOWN("7.0.0-beta02", "7.0.0", agpVersions("7.0.0", "7.0.1"), FORCE.upgradeTo("7.0.0")),
      FORCE_BETA_TO_STABLE_PATCH("7.0.0-beta02", "7.0.1", agpVersions("7.0.0", "7.0.1"), FORCE.upgradeTo("7.0.1")),
      FORCE_BETA_TO_STABLE_STABLE_WHEN_STABLE_PUBLISHED("7.0.0-beta02", "7.1.0-alpha01", agpVersions("7.0.0"), FORCE.upgradeTo("7.0.0")),
      FORCE_BETA_TO_STABLE_PATCH_EVEN_WHEN_NEXT_ALPHA("7.0.0-beta02", "7.1.0-alpha01", agpVersions("7.0.0", "7.0.1"), FORCE.upgradeTo("7.0.1")),
      FORCE_BETA_TO_STABLE_PRESERVE_SERIES("7.0.0-beta02", "7.1.0", agpVersions("7.0.0", "7.0.1"), FORCE.upgradeTo("7.0.1")),
      // If the latest known version is equal to the current version, there should be no upgrade.
      NO_UPGRADE_ALPHA("7.0.0-alpha01", "7.0.0-alpha01", agpVersions(), NO_UPGRADE.upgradeTo("7.0.0-alpha01")),
      NO_UPGRADE_BETA("7.0.0-beta01", "7.0.0-beta01", agpVersions(), NO_UPGRADE.upgradeTo("7.0.0-beta01")),
      NO_UPGRADE_RC("7.0.0-rc01", "7.0.0-rc01", agpVersions(), NO_UPGRADE.upgradeTo("7.0.0-rc01")),
      NO_UPGRADE_STABLE("7.0.0", "7.0.0", agpVersions(), NO_UPGRADE.upgradeTo("7.0.0")),
      // Even if the set of published versions contains later versions.
      NO_UPGRADE_FUTURE_STABLE("7.0.0", "7.0.0", agpVersions("7.0.1"), NO_UPGRADE.upgradeTo("7.0.0")),
      // If the latest known version is earlier than the current version, but they are in the same rc/stable series, there should be no
      // upgrade.
      NO_DOWNGRADE_PATCH("7.0.1", "7.0.0", agpVersions(), NO_UPGRADE.upgradeTo("7.0.1")),
      NO_DOWNGRADE_STABLE_TO_RC("7.0.0", "7.0.0-rc01", agpVersions(), NO_UPGRADE.upgradeTo("7.0.0")),
      NO_DOWNGRADE_RC_TO_RC("7.0.0-rc02", "7.0.0-rc01", agpVersions(), NO_UPGRADE.upgradeTo("7.0.0-rc02")),
      // If the latest known version is earlier than the current version, but they are not in the same rc/stable series
      // and the flag to support newer AGP versions is not enabled, there should be a downgrade.
      FORCED_DOWNGRADE_NEXT_ALPHA("7.0.0-alpha02", "7.0.0-alpha01", agpVersions(), Results(
        Flags(FUTURE_INCOMPATIBLE) to FORCE.upgradeTo("7.0.0-alpha01"),
        Flags(FUTURE_COMPATIBLE) to NO_UPGRADE.upgradeTo("7.0.0-alpha02")
      )),
      FORCED_DOWNGRADE_NEXT_BETA("7.0.0-beta02", "7.0.0-beta01", agpVersions(), Results(
        Flags(FUTURE_INCOMPATIBLE) to FORCE.upgradeTo("7.0.0-beta01"),
        Flags(FUTURE_COMPATIBLE) to NO_UPGRADE.upgradeTo("7.0.0-beta02")
      )),
      FORCED_DOWNGRADE_BETA_TO_ALPHA("7.0.0-beta01", "7.0.0-alpha02", agpVersions(), Results(
        Flags(FUTURE_INCOMPATIBLE) to FORCE.upgradeTo("7.0.0-alpha02"),
        Flags(FUTURE_COMPATIBLE) to NO_UPGRADE.upgradeTo("7.0.0-beta01")
      )),
      FORCED_DOWNGRADE_RC_TO_BETA("7.0.0-rc01", "7.0.0-beta02", agpVersions(), Results(
        Flags(FUTURE_INCOMPATIBLE) to FORCE.upgradeTo("7.0.0-beta02"),
        Flags(FUTURE_COMPATIBLE) to NO_UPGRADE.upgradeTo("7.0.0-rc01")
      )),
      FORCED_DOWNGRADE_RC_TO_ALPHA("7.0.0-rc01", "7.0.0-alpha02", agpVersions(), Results(
        Flags(FUTURE_INCOMPATIBLE) to FORCE.upgradeTo("7.0.0-alpha02"),
        Flags(FUTURE_COMPATIBLE) to NO_UPGRADE.upgradeTo("7.0.0-rc01")
      )),
      FORCED_DOWNGRADE_STABLE_TO_BETA("7.0.0", "7.0.0-beta01", agpVersions(), Results(
        Flags(FUTURE_INCOMPATIBLE) to FORCE.upgradeTo("7.0.0-beta01"),
        Flags(FUTURE_COMPATIBLE) to NO_UPGRADE.upgradeTo("7.0.0")
      )),
      FORCED_DOWNGRADE_STABLE_TO_ALPHA("7.0.0", "7.0.0-alpha01", agpVersions(), Results(
        Flags(FUTURE_INCOMPATIBLE) to FORCE.upgradeTo("7.0.0-alpha01"),
        Flags(FUTURE_COMPATIBLE) to NO_UPGRADE.upgradeTo("7.0.0")
      )),
      FORCED_DOWNGRADE_ALPHA_TO_PREVIOUS_STABLE("7.1.0-alpha01", "7.0.4", agpVersions(), Results(
        Flags(FUTURE_INCOMPATIBLE) to FORCE.upgradeTo("7.0.4"),
        Flags(FUTURE_COMPATIBLE) to NO_UPGRADE.upgradeTo("7.1.0-alpha01")
      )),
      FORCED_DOWNGRADE_BETA_TO_PREVIOUS_STABLE("7.1.0-beta02", "7.0.4", agpVersions(), Results(
        Flags(FUTURE_INCOMPATIBLE) to FORCE.upgradeTo("7.0.4"),
        Flags(FUTURE_COMPATIBLE) to NO_UPGRADE.upgradeTo("7.1.0-beta02")
      )),
      FORCED_DOWNGRADE_RC_TO_PREVIOUS_STABLE("7.1.0-rc03", "7.0.4", agpVersions(), Results(
        Flags(FUTURE_INCOMPATIBLE) to FORCE.upgradeTo("7.0.4"),
        Flags(FUTURE_COMPATIBLE) to NO_UPGRADE.upgradeTo("7.1.0-rc03")
      )),
      FORCED_DOWNGRADE_STABLE_TO_PREVIOUS_STABLE("7.1.0", "7.0.4", agpVersions(), Results(
        Flags(FUTURE_INCOMPATIBLE) to FORCE.upgradeTo("7.0.4"),
        Flags(FUTURE_COMPATIBLE) to NO_UPGRADE.upgradeTo("7.1.0")
      )),
      // Versions earlier than our minimum supported version should force an upgrade.
      VERSION_BELOW_MIN("3.1.0", "7.0.0", agpVersions(), FORCE.upgradeTo("7.0.0")),
      // If we know of published versions earlier than our latestKnownVersion, prefer to upgrade to those.
      UPGRADE_BELOW_MIN_UPGRADES_TO_MIN("3.1.0", "3.2.0", agpVersions("3.2.0", "3.3.0", "3.4.0", "3.5.0", "3.6.0", "7.0.0"), FORCE.upgradeTo("3.2.0")),
      // If we do not know of any published versions earlier than our latestKnown, upgrade to latestKnown
      UPGRADE_FALLS_BACK_TO_LATEST("3.1.0", "3.2.0", agpVersions("3.3.0", "3.4.0"), FORCE.upgradeTo("3.2.0")),
      // If we know of multiple published versions in the stable series, upgrade to the latest if it is compatible.
      FORCED_UPGRADE_PREFERS_LATEST_WITHIN_SERIES("3.1.0", "7.0.0", agpVersions("3.2.0-alpha01", "3.2.0-beta02", "3.2.0", "3.2.1", "3.2.2", "3.3.0", "3.3.1"), FORCE.upgradeTo("3.2.2")),
            FORCED_UPGRADE_PREFERS_LATEST_WITHIN_SERIES_CAPPED("3.1.0", "3.2.1", agpVersions("3.2.0-alpha01", "3.2.0-beta02", "3.2.0", "3.2.1", "3.2.2", "3.3.0", "3.3.1"), FORCE.upgradeTo("3.2.1")),
      // If we have no available published stable, we will always recommend the latest known version, strongly if the current version
      // is deprecated and the latest known is not.
      UNKNOWN_PUBLISHED_STATE_RECOMMENDS_LATEST_KNOWN_BOTH_DEPRECATED("3.5.0", "3.6.1", agpVersions(), RECOMMEND.upgradeTo("3.6.1")),
      UNKNOWN_PUBLISHED_STATE_RECOMMENDS_LATEST_KNOWN_FROM_DEPRECATED_1("3.5.0", "4.0.0", agpVersions(), STRONGLY_RECOMMEND.upgradeTo("4.0.0")),
      UNKNOWN_PUBLISHED_STATE_RECOMMENDS_LATEST_KNOWN_FROM_DEPRECATED_2("3.5.0", "4.1.1", agpVersions(), STRONGLY_RECOMMEND.upgradeTo("4.1.1")),
      UNKNOWN_PUBLISHED_STATE_RECOMMENDS_LATEST_KNOWN_FROM_DEPRECATED_3("3.5.0", "4.2.2", agpVersions(), STRONGLY_RECOMMEND.upgradeTo("4.2.2")),
      UNKNOWN_PUBLISHED_STATE_RECOMMENDS_LATEST_KNOWN_FROM_DEPRECATED_4("3.5.0", "7.0.3", agpVersions(), STRONGLY_RECOMMEND.upgradeTo("7.0.3")),
      UNKNOWN_PUBLISHED_STATE_RECOMMENDS_LATEST_KNOWN_FROM_DEPRECATED_5("3.5.0", "7.1.2", agpVersions(), STRONGLY_RECOMMEND.upgradeTo("7.1.2")),
      UNKNOWN_PUBLISHED_STATE_RECOMMENDS_LATEST_KNOWN_FROM_DEPRECATED_6("3.5.0", "7.2.1", agpVersions(), STRONGLY_RECOMMEND.upgradeTo("7.2.1")),
      UNKNOWN_PUBLISHED_STATE_RECOMMENDS_LATEST_KNOWN_FROM_DEPRECATED_7("3.5.0", "7.3.0", agpVersions(), STRONGLY_RECOMMEND.upgradeTo("7.3.0")),
      UNKNOWN_PUBLISHED_STATE_RECOMMENDS_LATEST_KNOWN_FROM_DEPRECATED_8("3.5.0", "8.0.0", agpVersions(), STRONGLY_RECOMMEND.upgradeTo("8.0.0")),
      UNKNOWN_PUBLISHED_STATE_RECOMMENDS_LATEST_KNOWN_NEITHER_DEPRECATED_1("4.0.0", "4.1.1", agpVersions(), RECOMMEND.upgradeTo("4.1.1")),
      UNKNOWN_PUBLISHED_STATE_RECOMMENDS_LATEST_KNOWN_NEITHER_DEPRECATED_2("4.0.0", "7.0.3", agpVersions(), RECOMMEND.upgradeTo("7.0.3")),
      UNKNOWN_PUBLISHED_STATE_RECOMMENDS_LATEST_KNOWN_NEITHER_DEPRECATED_3("4.0.0", "8.0.0", agpVersions(), RECOMMEND.upgradeTo("8.0.0")),
      // If we have published stable versions between the current and the latest known, recommend going over at most one major version
      // boundary (and that only if we are at the last known major.minor series before the boundary).  If we start at a deprecated
      // version, strongly recommend rather than recommend, but otherwise follow the same version suggestion (even if that version is
      // also a deprecated one.)
      UPGRADE_INCREMENTALLY_DEPRECATED_WITHIN_SERIES("3.5.0", "4.0.0", publishedVersions, STRONGLY_RECOMMEND.upgradeTo("3.6.1")),
      UPGRADE_INCREMENTALLY_DEPRECATED_LAST_MAJOR_MINOR_TO_NEXT_SERIES("3.6.0", "4.0.0", publishedVersions, STRONGLY_RECOMMEND.upgradeTo("4.0.0")),
      UPGRADE_INCREMENTALLY_DEPRECATED_LAST_MAJOR_MINOR_PATCH_TO_NEXT_SERIES("3.6.1", "4.0.0", publishedVersions, STRONGLY_RECOMMEND.upgradeTo("4.0.0")),
      UPGRADE_INCREMENTALLY_DEPRECATED_WITHIN_DEPRECATED_SERIES_EVEN_IN_NEWER_VERSION("3.5.0", "4.1.0", publishedVersions, STRONGLY_RECOMMEND.upgradeTo("3.6.1")),
      UPGRADE_INCREMENTALLY_DEPRECATED_LAST_MAJOR_MINOR_TO_LATEST_IN_NEXT_SERIES("3.6.0", "4.1.0", publishedVersions, STRONGLY_RECOMMEND.upgradeTo("4.1.0")),
      UPGRADE_INCREMENTALLY_DEPRECATED_LAST_MAJOR_MINOR_PATCH_TO_LATEST_IN_NEXT_SERIES_SKIP_1("3.6.1", "4.1.0", publishedVersions, STRONGLY_RECOMMEND.upgradeTo("4.1.0")),
      UPGRADE_INCREMENTALLY_DEPRECATED_WITHIN_SERIES_EVEN_IN_NEWER_VERSION_2("3.5.0", "4.2.0", publishedVersions, STRONGLY_RECOMMEND.upgradeTo("3.6.1")),
      UPGRADE_INCREMENTALLY_DEPRECATED_LAST_MAJOR_MINOR_TO_LATEST_IN_NEXT_SERIES_SKIP_2("3.6.0", "4.2.0", publishedVersions, STRONGLY_RECOMMEND.upgradeTo("4.2.0")),
      UPGRADE_INCREMENTALLY_DEPRECATED_LAST_MAJOR_MINOR_PATCH_TO_LATEST_IN_NEXT_SERIES_SKIP_2("3.6.1", "4.2.0", publishedVersions, STRONGLY_RECOMMEND.upgradeTo("4.2.0")),
      UPGRADE_INCREMENTALLY_DEPRECATED_WITHIN_SERIES_EVEN_IN_NEWER_VERSION_3("3.5.0", "7.0.0", publishedVersions, STRONGLY_RECOMMEND.upgradeTo("3.6.1")),
      UPGRADE_INCREMENTALLY_DEPRECATED_LAST_MAJOR_MINOR_TO_LATEST_IN_NEXT_SERIES_ONE_MAJOR("3.6.0", "7.0.0", publishedVersions, STRONGLY_RECOMMEND.upgradeTo("4.2.2")),
      UPGRADE_INCREMENTALLY_DEPRECATED_LAST_MAJOR_MINOR_PATCH_TO_LATEST_IN_NEXT_SERIES_SKIP_ALL("3.6.1", "7.0.0", publishedVersions, STRONGLY_RECOMMEND.upgradeTo("4.2.2")),
      UPGRADE_INCREMENTALLY_DEPRECATED_WITHIN_SERIES_EVEN_IN_NEWER_VERSION_4("3.5.0", "7.3.0", publishedVersions, STRONGLY_RECOMMEND.upgradeTo("3.6.1")),
      UPGRADE_INCREMENTALLY_DEPRECATED_LAST_MAJOR_MINOR_TO_LATEST_IN_NEXT_SERIES_ONE_MAJOR_2("3.6.1", "7.3.0", publishedVersions, STRONGLY_RECOMMEND.upgradeTo("4.2.2")),
      UPGRADE_INCREMENTALLY_DEPRECATED_WITHIN_SERIES_EVEN_IN_NEWER_VERSION_5("3.5.0", "8.0.0", publishedVersions, STRONGLY_RECOMMEND.upgradeTo("3.6.1")),
      UPGRADE_INCREMENTALLY_DEPRECATED_LAST_MAJOR_MINOR_TO_LATEST_IN_NEXT_SERIES_ONE_MAJOR_3("3.6.0", "8.0.1", publishedVersions, STRONGLY_RECOMMEND.upgradeTo("4.2.2")),
      UPDATE_INCREMENTALLY_WITHIN_SERIES("4.0.0", "7.0.0", publishedVersions, RECOMMEND.upgradeTo("4.2.2")),
      UPGRADE_INCREMENTALLY_LAST_MAJOR_MINOR_TO_NEXT_SERIES("4.2.0", "7.0.0", publishedVersions, RECOMMEND.upgradeTo("7.0.0")),
      UPGRADE_INCREMENTALLY_LAST_MAJOR_MINOR_INTERMEDIATE_PATCH_TO_NEXT_SERIES("4.2.1", "7.0.0", publishedVersions, RECOMMEND.upgradeTo("7.0.0")),
      UPGRADE_INCREMENTALLY_LAST_MAJOR_MINOR_LAST_PATCH_TO_NEXT_SERIES("4.2.2", "7.0.0", publishedVersions, RECOMMEND.upgradeTo("7.0.0")),
      UPGRADE_INCREMENTALLY_LAST_MAJOR_MINOR_TO_NEXT_SERIES_PATCH("4.2.0", "7.0.2", publishedVersions, RECOMMEND.upgradeTo("7.0.2")),
      UPGRADE_INCREMENTALLY_LAST_MAJOR_MINOR_INTERMEDIATE_PATCH_TO_NEXT_SERIES_PATCH("4.2.1", "7.0.2", publishedVersions, RECOMMEND.upgradeTo("7.0.2")),
      UPGRADE_INCREMENTALLY_LAST_MAJOR_MINOR_PATCH_TO_NEXT_SERIES_PATCH("4.2.2", "7.0.2", publishedVersions, RECOMMEND.upgradeTo("7.0.2")),
      UPGRADE_INCREMENTALLY_LAST_MAJOR_MINOR_TO_LATEST_NEXT_SERIES("4.2.0", "7.3.0", publishedVersions, RECOMMEND.upgradeTo("7.3.0")),
      UPGRADE_INCREMENTALLY_LAST_MAJOR_MINOR_TO_LATEST_NEXT_SERIES_EVEN_IN_NEWER_VERSION("4.2.0", "8.0.0", publishedVersions, RECOMMEND.upgradeTo("7.3.0")),
      UPGRADE_INCREMENTALLY_FIRST_WITHIN_SERIES_TO_LATEST("7.0.0", "8.0.0", publishedVersions, RECOMMEND.upgradeTo("7.3.0")),
      UPGRADE_INCREMENTALLY_SOMEWHERE_WITHIN_SERIES_TO_LATEST("7.1.2", "8.0.0", publishedVersions, RECOMMEND.upgradeTo("7.3.0")),
      UPGRADE_INCREMENTALLY_SOMEWHERE_ELSE_WITHIN_SERIES_TO_LATEST("7.2.1", "8.0.0", publishedVersions, RECOMMEND.upgradeTo("7.3.0")),
      UPGRADE_INCREMENTALLY_LAST_MAJOR_MINOR_TO_NEXT_SERIES_2("7.3.0", "8.0.0", publishedVersions, RECOMMEND.upgradeTo("8.0.0")),
      UPGRADE_INCREMENTALLY_LAST_MAJOR_MINOR_TO_NEXT_SERIES_PATCH_2("7.3.0", "8.0.1", publishedVersions, RECOMMEND.upgradeTo("8.0.1")),
      ;
      constructor(current:String, latestKnown: String, published: Set<AgpVersion>, results: Results) : this(agpVersion(current), agpVersion(latestKnown), published, results)
      constructor(current:String, latestKnown: String, published: Set<AgpVersion>, result: GradlePluginUpgradeState) : this(current, latestKnown, published, Results(result))

      override fun toString(): String {
        return "$name: $description"
      }
      val description: String get() = "upgrade state from $current to $latestKnown with published versions ${if (published==publishedVersions) "${'$'}publishedVersions" else published.toString()}"
    }



    private fun agpVersions(vararg versions: String): Set<AgpVersion> {
      return versions.mapTo(mutableSetOf()) { agpVersion(it) }
    }

    private val publishedVersions: Set<AgpVersion> = agpVersions(
      "3.5.0", "3.6.0-alpha01",
      "3.6.0", "3.6.1", "4.0.0-alpha02",
      "4.0.0", "4.1.0-alpha03",
      "4.1.0", "4.1.1", "4.2.0-alpha04",
      "4.2.0", "4.2.1", "4.2.2", "5.0.0-alpha05", "7.0.0-alpha06",
      "7.0.0", "7.0.1", "7.0.2", "7.0.3", "7.1.0-beta01",
      "7.1.0", "7.1.1", "7.1.2", "7.2.0-beta02",
      "7.2.0", "7.2.1", "7.3.0-beta03",
      "7.3.0", "8.0.0-rc01",
      "8.0.0", "8.0.1", "8.1.0-rc02"
    )
  }
}