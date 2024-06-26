package com.android.tools.idea.gradle.plugin

import com.android.Version
import com.android.flags.junit.FlagRule
import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.flags.StudioFlags
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.net.URL
import kotlin.test.assertFailsWith

class AgpVersionsTest {

  @get:Rule
  val flagRule = FlagRule(StudioFlags.AGP_VERSION_TO_USE)

  @Test
  fun `check no override behaviour` () {
    StudioFlags.AGP_VERSION_TO_USE.clearOverride()
    assertThat(AgpVersions.studioFlagOverride).isNull()
    assertThat(AgpVersions.newProject).isEqualTo(AgpVersion.parse(Version.ANDROID_GRADLE_PLUGIN_VERSION))
    assertThat(AgpVersions.latestKnown).isEqualTo(AgpVersion.parse(Version.ANDROID_GRADLE_PLUGIN_VERSION))
  }

  @Test
  fun `check override behaviour` () {
    StudioFlags.AGP_VERSION_TO_USE.override("7.4.0")
    assertThat(AgpVersions.studioFlagOverride).isEqualTo(AgpVersion.parse("7.4.0"))
    assertThat(AgpVersions.newProject).isEqualTo(AgpVersion.parse("7.4.0"))
    assertThat(AgpVersions.latestKnown).isEqualTo(AgpVersion.parse(Version.ANDROID_GRADLE_PLUGIN_VERSION))
  }

  @Test
  fun `check override stable` () {
    StudioFlags.AGP_VERSION_TO_USE.override("stable")
    assertThat(AgpVersions.studioFlagOverride!!.isPreview).isFalse()
    assertThat(AgpVersions.newProject).isEqualTo(AgpVersions.studioFlagOverride)
    assertThat(AgpVersions.latestKnown).isEqualTo(AgpVersion.parse(Version.ANDROID_GRADLE_PLUGIN_VERSION))
  }

  @Test
  fun `check override stable case insensitivity` () {
    StudioFlags.AGP_VERSION_TO_USE.override("staBLE")
    assertThat(AgpVersions.studioFlagOverride!!.isPreview).isFalse()
  }

  @Test
  fun `check invalid version` () {
    StudioFlags.AGP_VERSION_TO_USE.override("7.4.0.3")
    val failure = assertFailsWith<IllegalStateException> { AgpVersions.studioFlagOverride }
    assertThat(failure).hasMessageThat().isEqualTo("Invalid value '7.4.0.3' for Studio flag gradle.ide.agp.version.to.use. Expected Android Gradle plugin version (e.g. '8.0.2') or 'stable'")
    assertFailsWith<IllegalStateException> { AgpVersions.newProject }
    assertFailsWith<IllegalStateException> { AgpVersions.latestKnown }
  }

  @Test
  fun `check invalid version string` () {
    StudioFlags.AGP_VERSION_TO_USE.override("canary")
    val failure = assertFailsWith<IllegalStateException> { AgpVersions.studioFlagOverride }
    assertThat(failure).hasMessageThat().isEqualTo("Invalid value 'canary' for Studio flag gradle.ide.agp.version.to.use. Expected Android Gradle plugin version (e.g. '8.0.2') or 'stable'")
    assertFailsWith<IllegalStateException> { AgpVersions.newProject }
    assertFailsWith<IllegalStateException> { AgpVersions.latestKnown }
  }

  @Test
  fun `check override with newer version`() {
    val current = AgpVersion.parse(Version.ANDROID_GRADLE_PLUGIN_VERSION)
    val higherThanCurrent = AgpVersion(current.major + 1, 0)
    assertThat(higherThanCurrent).isGreaterThan(current)
    StudioFlags.AGP_VERSION_TO_USE.override(higherThanCurrent.toString())
    assertThat(AgpVersions.studioFlagOverride).isEqualTo(higherThanCurrent)
    assertThat(AgpVersions.newProject).isEqualTo(higherThanCurrent)
    assertThat(AgpVersions.latestKnown).isEqualTo(higherThanCurrent)
  }

  @Test
  fun `test get new project wizard versions`() {
    val availableVersions = listOf(
      "3.1.0", // Older than latest supported should be omitted
      "3.2.0",
      // Skip a few
      "7.3.3",
      "7.4.0", "7.4.1", "7.4.2",
      "8.1.0", "8.1.1", "8.1.2",
      "8.2.0-alpha01", "8.2.0-alpha09", "8.2.0-beta01", "8.2.0-beta02",
      "8.3.0-alpha01", "8.3.0-alpha02"
    ).map { AgpVersion.parse(it) }.toSet()
    assertThat(
      AgpVersions.getNewProjectWizardVersions(
      latestKnown = AgpVersion.parse("8.3.0-dev"),
      gmavenVersions = availableVersions,
      localAndSnapshotVersions = listOf(),
    ).map { it.toString() })
      .containsExactly("8.3.0-alpha02", "8.2.0-beta02", "8.1.2", "8.1.1", "8.1.0", "7.4.2", "7.4.1", "7.4.0", "7.3.3", "3.2.0")
      .inOrder()

    assertThat(
      AgpVersions.getNewProjectWizardVersions(
      latestKnown = AgpVersion.parse("8.3.0-alpha01"),
      gmavenVersions = availableVersions,
      localAndSnapshotVersions = listOf(),
    ).map { it.toString() })
      .containsExactly("8.3.0-alpha01", "8.1.2", "8.1.1", "8.1.0", "7.4.2", "7.4.1", "7.4.0", "7.3.3", "3.2.0")
      .inOrder()
  }


  @Test
  fun `test get new project wizard versions with dev available`() {
    val availableVersions = listOf(
      "8.1.2",
      "8.2.0-beta01", "8.2.0-beta02",
      "8.3.0-alpha01", "8.3.0-alpha02",
    ).map { AgpVersion.parse(it) }.toSet()
    val localAndSnapshotVersions = listOf(
      AgpVersions.NewProjectWizardAgpVersion(
        AgpVersion.parse("8.2.0-dev"),  // Incompatible dev version is ignored
        listOf(URL("file:/home/user/studio-main/out/repo/"))
      ),
      AgpVersions.NewProjectWizardAgpVersion(
        AgpVersion.parse("8.3.0-dev"),
        listOf(URL("file:/home/user/studio-main/out/repo/"))
      ),
      AgpVersions.NewProjectWizardAgpVersion(
        AgpVersion.parse("8.3.0-dev"),
        listOf(URL("https://androidx.dev/studio/builds/12006839/artifacts/artifacts/repository"))
      ),
    )
    assertThat(
      AgpVersions.getNewProjectWizardVersions(
        latestKnown = AgpVersion.parse("8.3.0-dev"),
        gmavenVersions = availableVersions,
        localAndSnapshotVersions = localAndSnapshotVersions,
    ).map { it.toString() })
      .containsExactly(
        "8.3.0-dev (file:/home/user/studio-main/out/repo/)",
        "8.3.0-dev (https://androidx.dev/studio/builds/12006839/artifacts/artifacts/repository)",
        "8.3.0-alpha02",
        "8.2.0-beta02",
        "8.1.2")
      .inOrder()

    assertThat(
      AgpVersions.getNewProjectWizardVersions(
      latestKnown = AgpVersion.parse("8.3.0-alpha01"),
      gmavenVersions = availableVersions,
      localAndSnapshotVersions = localAndSnapshotVersions,
    ).map { it.toString() })
      .containsExactly(
        "8.3.0-dev (file:/home/user/studio-main/out/repo/)",
        "8.3.0-dev (https://androidx.dev/studio/builds/12006839/artifacts/artifacts/repository)",
        "8.3.0-alpha01",
        "8.1.2")
      .inOrder()
  }

}