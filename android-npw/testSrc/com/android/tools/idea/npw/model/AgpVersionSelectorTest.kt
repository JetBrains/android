/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.npw.model

import com.android.ide.common.repository.AgpVersion
import com.android.ide.common.repository.AgpVersion.Companion.parse as agpVersion
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AgpVersionSelectorTest {

  val gmaven8dot5series: Set<AgpVersion> =
    setOf(
      agpVersion("8.5.0-alpha01"),
      agpVersion("8.5.0-alpha02"),
      agpVersion("8.5.0-alpha03"),
      agpVersion("8.5.0-alpha04"),
      agpVersion("8.5.0-alpha05"),
      agpVersion("8.5.0-alpha06"),
      agpVersion("8.5.0-alpha07"),
      agpVersion("8.5.0-alpha08"),
      agpVersion("8.5.0-beta01"),
      agpVersion("8.5.0-beta02"),
      agpVersion("8.5.0-rc01"),
      agpVersion("8.5.0-rc02"),
      agpVersion("8.5.0"),
      agpVersion("8.5.1"),
      agpVersion("8.5.2"),
    )

  val gmaven8dot6series: Set<AgpVersion> =
    setOf(
      agpVersion("8.6.0-alpha01"),
      agpVersion("8.6.0-alpha02"),
      agpVersion("8.6.0-alpha03"),
      agpVersion("8.6.0-alpha04"),
      agpVersion("8.6.0-alpha05"),
      agpVersion("8.6.0-alpha06"),
      agpVersion("8.6.0-alpha07"),
      agpVersion("8.6.0-alpha08"),
      agpVersion("8.6.0-beta01"),
      agpVersion("8.6.0-beta02"),
      agpVersion("8.6.0-rc01"),
      agpVersion("8.6.0"),
      agpVersion("8.6.1"),
    )

  private fun AgpVersionSelector.resolveVersion(agpVersions: Set<AgpVersion>) = resolveVersion {
    agpVersions
  }

  @Test
  fun resolveVersionOffline() {
    val selector = AgpVersionSelector.MaximumPatchVersion(agpVersion("8.5.1"))
    val gmavenNotAvailable: () -> Set<AgpVersion> = { emptySet<AgpVersion>() }
    assertThat(selector.resolveVersion(gmavenNotAvailable)).isEqualTo(agpVersion("8.5.1"))
  }

  @Test
  fun resolveVersionSeriesNotAvailable() {
    val selector = AgpVersionSelector.MaximumPatchVersion(agpVersion("8.5.1"))
    assertThat(selector.resolveVersion(setOf(agpVersion("8.4.0")))).isEqualTo(agpVersion("8.5.1"))
  }

  @Test
  fun resolveVersionNotYetPublished() {
    val selector = AgpVersionSelector.MaximumPatchVersion(agpVersion("8.5.1"))
    assertThat(
        selector.resolveVersion(
          gmaven8dot5series + gmaven8dot6series - agpVersion("8.5.1") - agpVersion("8.5.2")
        )
      )
      .isEqualTo(agpVersion("8.5.1"))
  }

  @Test
  fun resolveVersionNoFuturePatchAvailable() {
    val selector = AgpVersionSelector.MaximumPatchVersion(agpVersion("8.5.1"))
    assertThat(selector.resolveVersion(gmaven8dot5series + gmaven8dot6series - agpVersion("8.5.2")))
      .isEqualTo(agpVersion("8.5.1"))
  }

  @Test
  fun resolveVersionPatchUpdate() {
    val selector = AgpVersionSelector.MaximumPatchVersion(agpVersion("8.5.1"))
    assertThat(selector.resolveVersion(gmaven8dot5series + gmaven8dot6series))
      .isEqualTo(agpVersion("8.5.2"))
  }

  @Test
  fun resolveVersionPreview() {
    val selector = AgpVersionSelector.MaximumPatchVersion(agpVersion("8.5.0-alpha04"))
    assertThat(selector.resolveVersion(gmaven8dot5series + gmaven8dot6series))
      .isEqualTo(agpVersion("8.5.0-alpha04"))
  }

  @Test
  fun willSelectAtLeast() {
    val selector = AgpVersionSelector.MaximumPatchVersion(agpVersion("8.5.1"))
    assertThat(selector.willSelectAtLeast(agpVersion("8.5.0-rc01"))).isTrue()
    assertThat(selector.willSelectAtLeast(agpVersion("8.5.0"))).isTrue()
    assertThat(selector.willSelectAtLeast(agpVersion("8.5.1"))).isTrue()
    assertThat(selector.willSelectAtLeast(agpVersion("8.5.2"))).isFalse()
    assertThat(selector.willSelectAtLeast(agpVersion("8.6.0-alpha01"))).isFalse()
  }

  @Test
  fun fixedVersionWillSelectAtLeast() {
    val selector = AgpVersionSelector.FixedVersion(agpVersion("8.5.1"))
    assertThat(selector.willSelectAtLeast(agpVersion("8.5.0-rc01"))).isTrue()
    assertThat(selector.willSelectAtLeast(agpVersion("8.5.0"))).isTrue()
    assertThat(selector.willSelectAtLeast(agpVersion("8.5.1"))).isTrue()
    assertThat(selector.willSelectAtLeast(agpVersion("8.5.2"))).isFalse()
    assertThat(selector.willSelectAtLeast(agpVersion("8.6.0-alpha01"))).isFalse()
  }

  @Test
  fun fixedVersionDoesNotResolveAgpVersions() {
    val selector = AgpVersionSelector.FixedVersion(agpVersion("8.5.1"))
    assertThat(selector.resolveVersion { error("Should not be called") })
      .isEqualTo(agpVersion("8.5.1"))
  }
}
