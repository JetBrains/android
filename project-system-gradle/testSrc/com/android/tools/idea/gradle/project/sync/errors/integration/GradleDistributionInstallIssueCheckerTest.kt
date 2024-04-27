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
package com.android.tools.idea.gradle.project.sync.errors.integration

import com.android.tools.idea.gradle.project.sync.errors.UnsupportedGradleVersionIssueChecker
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenStudioProxySettingsQuickFix
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.openapi.util.io.NioFiles
import org.jetbrains.plugins.gradle.issue.quickfix.GradleWrapperSettingsOpenQuickFix
import org.junit.Assert
import org.junit.Test
import java.net.InetAddress
import java.net.UnknownHostException

class GradleDistributionInstallIssueCheckerTest : AbstractIssueCheckerIntegrationTest() {

  @Test
  fun testNoWriteAccess() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    val wrapperFile = preparedProject.root.resolve("gradle/wrapper/gradle-wrapper.properties")
    wrapperFile.writeText("""
      distributionBase=PROJECT
      distributionPath=wrapper/dists
      distributionUrl=https\://services.gradle.org/distributions/gradle-8.3-rc-2-bin.zip
      zipStoreBase=PROJECT
      zipStorePath=wrapper/dists
    """.trimIndent())

    val distsDir = preparedProject.root.resolve("wrapper/dists")
    FileUtils.mkdirs(distsDir)
    NioFiles.setReadOnly(distsDir.toPath(), true)

    runSyncAndCheckFailure(
      preparedProject,
      { buildIssue ->
        Truth.assertThat(buildIssue).isNotNull()
        Truth.assertThat(buildIssue.description).contains("Could not install Gradle distribution from 'https://services.gradle.org/distributions/gradle-8.3-rc-2-bin.zip'.")
        Truth.assertThat(buildIssue.description).contains("Reason: java.lang.RuntimeException: Could not create parent directory for lock file ")
        Truth.assertThat(buildIssue.description).contains("""
          Please ensure Android Studio can write to the specified Gradle wrapper distribution directory.
          You can also change Gradle home directory in Gradle Settings.
        """.trimIndent())
        Truth.assertThat(buildIssue.quickFixes.map { it::class.java }).isEqualTo(listOf(
          UnsupportedGradleVersionIssueChecker.OpenGradleSettingsQuickFix::class.java,
          GradleWrapperSettingsOpenQuickFix::class.java
        ))
      },
      AndroidStudioEvent.GradleSyncFailure.GRADLE_DISTRIBUTION_INSTALL_ERROR
    )
  }

  @Test
  fun testConnectionRefusedHost() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    val wrapperFile = preparedProject.root.resolve("gradle/wrapper/gradle-wrapper.properties")
    wrapperFile.writeText("""
      distributionBase=PROJECT
      distributionPath=wrapper/dists
      distributionUrl=https\://127.0.0.1:1234/distributions/gradle-8.3-rc-2-bin.zip
      zipStoreBase=PROJECT
      zipStorePath=wrapper/dists
    """.trimIndent())

    runSyncAndCheckFailure(
      preparedProject,
      { buildIssue ->
        Truth.assertThat(buildIssue).isNotNull()
        Truth.assertThat(buildIssue.description).isEqualTo("""
          Could not install Gradle distribution from 'https://127.0.0.1:1234/distributions/gradle-8.3-rc-2-bin.zip'.
          Reason: java.net.ConnectException: Connection refused
          
          Please ensure <a href="open_gradle_wrapper_settings">gradle distribution url</a> is correct.
          If you are behind an HTTP proxy, please <a href="open.proxy.settings">configure the proxy settings</a>.
        """.trimIndent())
        Truth.assertThat(buildIssue.quickFixes.map { it::class.java }).isEqualTo(listOf(
          GradleWrapperSettingsOpenQuickFix::class.java,
          OpenStudioProxySettingsQuickFix::class.java
        ))
      },
      AndroidStudioEvent.GradleSyncFailure.GRADLE_DISTRIBUTION_INSTALL_ERROR
    )
  }

  @Test
  fun testUnknownHost() {
    val unknownHost = "services.gradle.org.invalid"
    Assert.assertThrows(UnknownHostException::class.java) { InetAddress.getByName(unknownHost) }
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    val wrapperFile = preparedProject.root.resolve("gradle/wrapper/gradle-wrapper.properties")
    wrapperFile.writeText("""
      distributionBase=PROJECT
      distributionPath=wrapper/dists
      distributionUrl=https\://$unknownHost/distributions/gradle-8.3-rc-2-bin.zip
      zipStoreBase=PROJECT
      zipStorePath=wrapper/dists
    """.trimIndent())
    runSyncAndCheckFailure(
      preparedProject,
      { buildIssue ->
        Truth.assertThat(buildIssue).isNotNull()
        Truth.assertThat(buildIssue.description).isEqualTo("""
          Could not install Gradle distribution from 'https://$unknownHost/distributions/gradle-8.3-rc-2-bin.zip'.
          Reason: java.net.UnknownHostException: $unknownHost
          
          Please ensure <a href="open_gradle_wrapper_settings">gradle distribution url</a> is correct.
          If you are behind an HTTP proxy, please <a href="open.proxy.settings">configure the proxy settings</a>.
        """.trimIndent())
        Truth.assertThat(buildIssue.quickFixes.map { it::class.java }).isEqualTo(listOf(
          GradleWrapperSettingsOpenQuickFix::class.java,
          OpenStudioProxySettingsQuickFix::class.java
        ))
      },
      AndroidStudioEvent.GradleSyncFailure.GRADLE_DISTRIBUTION_INSTALL_ERROR
    )
  }
}