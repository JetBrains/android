/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.errors

import com.android.repository.Revision
import com.android.repository.api.LocalPackage
import com.android.repository.api.RemotePackage
import com.android.repository.api.RepoManager
import com.android.repository.impl.meta.RepositoryPackages
import com.android.repository.testframework.FakePackage.FakeLocalPackage
import com.android.repository.testframework.FakePackage.FakeRemotePackage
import com.android.repository.testframework.FakeRepoManager
import com.android.tools.idea.gradle.project.build.output.TestMessageEventConsumer
import com.android.tools.idea.gradle.project.sync.quickFixes.InstallCmakeQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.SetCmakeDirQuickFix
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.truth.Truth.assertThat
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import java.io.File
import java.io.IOException
import java.util.stream.Collectors

enum class CMakeDirGetterResponse {
  FILE_PRESENT, FILE_ABSENT, THROW_IO_EXCEPTION
}

/**
 * @param cmakeVersion The CMake version to use for the created local package.
 * @return A fake local cmake package with the given version.
 */
fun createLocalPackage(cmakeVersion: String): LocalPackage {
  val revision = Revision.parseRevision(cmakeVersion)
  val pkg = FakeLocalPackage("cmake;$cmakeVersion")
  pkg.setRevision(revision)
  return pkg
}

/**
 * @param cmakeVersion The CMake version to use for the created remote package.
 * @return A fake remote cmake package with the given version.
 */
fun createRemotePackage(cmakeVersion: String): RemotePackage {
  val revision = Revision.parseRevision(cmakeVersion)
  val pkg = FakeRemotePackage("cmake;$cmakeVersion")
  pkg.setRevision(revision)
  return pkg
}

class MissingCMakeIssueCheckerTest : AndroidGradleTestCase() {

  private fun doTestAlreadyInstalledRemote(error: String) {
    val issueData = GradleIssueData(projectFolderPath.path, Throwable(error), null, null)

    val issueChecker = MissingCmakeIssueCheckerFake(listOf("3.10.2"), listOf("3.10.2"), CMakeDirGetterResponse.FILE_ABSENT)

    val buildIssue = issueChecker.check(issueData)

    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.description).contains(error)
    assertThat(buildIssue.description).contains("Set cmake.dir in local.properties")
    assertThat(buildIssue.quickFixes).hasSize(1)
    assertThat(buildIssue.quickFixes[0]).isInstanceOf(SetCmakeDirQuickFix::class.java)
  }

  fun testAlreadyInstalledRemote() {
    doTestAlreadyInstalledRemote("CMake '3.10.2' was not found in PATH or by cmake.dir property.")
  }

  fun testAlreadyInstalledRemoteVersionWithin() {
    doTestAlreadyInstalledRemote("Unable to find CMake with version: 3.10.2 within")
  }

  fun testAlreadyInstalledRemoteMalformed() {
    val error = "Unable to find CMake with version: 3.10.2 "
    val issueData = GradleIssueData(projectFolderPath.path, Throwable(error), null, null)
    val issueChecker = MissingCmakeIssueCheckerFake(listOf("3.10.2"), listOf("3.10.2"), CMakeDirGetterResponse.THROW_IO_EXCEPTION)

    val buildIssue = issueChecker.check(issueData)

    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.description).contains(error)
    assertThat(buildIssue.description).contains("Install CMake")
    assertThat(buildIssue.quickFixes).hasSize(1)
    assertThat(buildIssue.quickFixes[0]).isInstanceOf(InstallCmakeQuickFix::class.java)
  }

  fun testAlreadyInstalledRemoteReplaceInCMakeDir() {
    val error = "CMake '3.10.2' was not found in PATH or by cmake.dir property."
    val issueData = GradleIssueData(projectFolderPath.path, Throwable(error), null, null)
    val issueChecker = MissingCmakeIssueCheckerFake(listOf("3.10.2"), listOf("3.10.2"), CMakeDirGetterResponse.FILE_PRESENT)

    val buildIssue = issueChecker.check(issueData)

    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.description).contains(error)
    assertThat(buildIssue.description).contains("Replace cmake.dir in local.properties")
    assertThat(buildIssue.quickFixes).hasSize(1)
    assertThat(buildIssue.quickFixes[0]).isInstanceOf(SetCmakeDirQuickFix::class.java)
  }

  fun testAlreadyInstalledRemoteCantAccessCMakeDir() {
    val error = "CMake '3.10.2' was not found in PATH or by cmake.dir property."
    val issueData = GradleIssueData(projectFolderPath.path, Throwable(error), null, null)
    val issueChecker = MissingCmakeIssueCheckerFake(listOf("3.10.2"), listOf("3.10.2"), CMakeDirGetterResponse.THROW_IO_EXCEPTION)

    val buildIssue = issueChecker.check(issueData)
    // Check that we don't offer a quickFix in this case.
    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.description).contains(error)
    assertThat(buildIssue.quickFixes).hasSize(0)
  }

  fun testInstallFromRemote() {
    val error = "CMake '3.10.2' was not found in PATH or by cmake.dir property."
    val issueData = GradleIssueData(projectFolderPath.path, Throwable(error), null, null)
    val issueChecker = MissingCmakeIssueCheckerFake(listOf("3.8.2"), listOf("3.10.2"), CMakeDirGetterResponse.THROW_IO_EXCEPTION)

    val buildIssue = issueChecker.check(issueData)

    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.description).contains(error)
    assertThat(buildIssue.quickFixes).hasSize(1)
    assertThat(buildIssue.quickFixes[0]).isInstanceOf(InstallCmakeQuickFix::class.java)
    assertThat((buildIssue.quickFixes[0] as InstallCmakeQuickFix).myCmakeVersion.toString()).isEqualTo("3.10.2")
  }

  // Test that we correctly get the best matches.
  fun testFindBestMatchRejectsPrefixMatch() {
    assertNull(
      findBestMatch(listOf(createRemotePackage("3.8.2")), createRevision("3", false)))

    assertNull(
      findBestMatch(listOf(createRemotePackage("3.8.2")), createRevision("3.8", false)))
  }

  fun testFindBestMatchWithPlusExactMatch() {
    assertEquals(Revision.parseRevision("3.8.2"),
                 findBestMatch(listOf(createRemotePackage("3.8.2")), createRevision("3.8.2", true)))
  }

  fun testFindBestMatchWithPlusMatchesHigherVersion() {
    assertEquals(Revision.parseRevision("3.8.2"),
                 findBestMatch(listOf(createRemotePackage("3.8.2")), createRevision("3.6.2", true)))
  }

  fun testFindBestMatchWithPlusSelectsFirstMatch() {
    // Plus matches both available versions (preview version is ignored). The first match is selected.
    assertEquals(Revision.parseRevision("3.8.2-rc1"),
                 findBestMatch(
                   listOf(createRemotePackage("3.8.2-rc1"), createRemotePackage("3.8.2-rc2")),createRevision("3.8.2", true)))

    assertEquals(Revision.parseRevision("3.8.2-rc1"),
      findBestMatch(
        listOf(createRemotePackage("3.8.2-rc1"), createRemotePackage("3.8.2-rc2")), createRevision("3.8.2-rc3", true)))
  }

  fun testFindBestMatchRejectForkVersionInput() {
    // We don't want the user to put "3.6.4111459" as input.
    assertNull(
      findBestMatch(listOf(createRemotePackage("3.6.0")), createRevision("3.6.4111459", false)))
  }

  fun testFindBestMatchTranslateForkVersionFromSdk() {
    // If the SDK contains "3.6.4111459", then we translate it before matching.
    assertEquals(Revision.parseRevision("3.6.0"),
      findBestMatch(listOf(createRemotePackage("3.6.4111459")), createRevision("3.6.0", false)))
  }

  fun testVersionSatisfiesExactMatch() {
    assertTrue(versionSatisfies(
      Revision.parseRevision("3.8.0"), createRevision("3.8.0", false)))
  }

  fun testVersionSatisfiesIgnoresPreview() {
    assertTrue(versionSatisfies(
      Revision.parseRevision("3.8.0-rc1"), createRevision("3.8.0", false)))

    assertTrue(versionSatisfies(
      Revision.parseRevision("3.8.0"), createRevision("3.8.0-rc2", false)))

    assertTrue(versionSatisfies(
      Revision.parseRevision("3.8.0-rc1"), createRevision("3.8.0-rc2", false)))
  }

  fun testVersionSatisfiesMismatch() {
    assertFalse(versionSatisfies(
      Revision.parseRevision("3.8.0"), createRevision("3.10.0", false)))
  }

  fun testVersionSatisfiesWithPlusExactMatch() {
    assertTrue(versionSatisfies(
      Revision.parseRevision("3.8.0"), createRevision("3.8.0", true)))
  }

  fun testVersionSatisfiesWithPlusMatchesHigherVersion() {
    assertTrue(versionSatisfies(
      Revision.parseRevision("3.10.0"), createRevision("3.8.0", true)))
  }

  fun testVersionSatisfiesWithPlusIgnoresPreview() {
    assertTrue(versionSatisfies(
      Revision.parseRevision("3.8.0-rc1"), createRevision("3.8.0", true)))
    assertTrue(versionSatisfies(
      Revision.parseRevision("3.8.0"), createRevision("3.8.0-rc2", true)))
    assertTrue(versionSatisfies(
      Revision.parseRevision("3.8.0-rc1"), createRevision("3.8.0-rc2", true)))
  }

  fun testVersionSatisfiesWithPlusMismatch() {
    assertFalse(versionSatisfies(
      Revision.parseRevision("3.8.0"), createRevision("3.10.0", true)))
  }

  fun testCheckIssueHandled() {
    val missingCMakeIssueChecker = MissingCMakeIssueChecker()
    assertThat(
      missingCMakeIssueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception was not found in PATH or by cmake.dir property",
        "CMake options was not found in PATH or by cmake.dir property",
        null,
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(true)

    assertThat(
      missingCMakeIssueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "Failed to install the following Android SDK packages as some licences have not been accepted. \n" +
        "Please check CMake options",
        null,
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(true)

    assertThat(
      missingCMakeIssueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "Failed to install the following SDK components: cmake",
        null,
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(true)

    assertThat(
      missingCMakeIssueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "Unable to find CMake with version: ABC",
        null,
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(true)

    assertThat(
      missingCMakeIssueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "Failed to find CMake. \n Please fix.",
        null,
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(true)

    assertThat(
      missingCMakeIssueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "Unable to get the CMake version ABC for the project",
        null,
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(true)
  }

  private fun createRevision(revision: String?, orHigher: Boolean): RevisionOrHigher {
    return RevisionOrHigher(Revision.parseRevision(revision!!), orHigher)
  }

  class MissingCmakeIssueCheckerFake(private val localPackages: List<String>,
                                     private val remotePackages: List<String>,
                                     private val cmakeDirGetterResponse: CMakeDirGetterResponse) : MissingCMakeIssueChecker() {
    override fun getLocalProperties(projectPath: String): File? {
      if (cmakeDirGetterResponse == CMakeDirGetterResponse.THROW_IO_EXCEPTION)
        throw IOException()
      else if (cmakeDirGetterResponse == CMakeDirGetterResponse.FILE_PRESENT)
        return File("path/to/cmake")
      return null
    }

    override fun getSdkManager(): RepoManager {
      return FakeRepoManager(
        null,
        RepositoryPackages(
          localPackages.stream().map{e -> createLocalPackage(e)}.collect(Collectors.toList()),
          remotePackages.stream().map{e -> createRemotePackage(e)}.collect(Collectors.toList()))
      )
    }
  }
}