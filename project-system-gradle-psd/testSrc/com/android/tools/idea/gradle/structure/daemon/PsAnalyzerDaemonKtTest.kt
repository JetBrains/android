package com.android.tools.idea.gradle.structure.daemon

import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec
import com.android.tools.idea.gradle.structure.model.TestPath
import com.android.tools.lint.checks.GooglePlaySdkIndex
import com.android.tools.lint.checks.Index
import com.android.tools.lint.checks.Library
import com.android.tools.lint.checks.LibraryIdentifier
import com.android.tools.lint.checks.LibraryVersion
import com.android.tools.lint.checks.LibraryVersionLabels
import com.android.tools.lint.checks.Sdk
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.ByteArrayInputStream

@RunWith(value = Parameterized::class)
class PsAnalyzerDaemonKtTest(private val blocking: Boolean,
                             private val nonCompliant: Boolean,
                             private val outdated: Boolean,
                             private val critical: Boolean,
                             private val expectedMessage: String?) {

  @Test
  fun `Expected issue`() {
    val sdkIndex = TestGooglePlaySdkIndex(blocking, nonCompliant, outdated, critical)
    sdkIndex.prepareForTest()
    val issue = getSdkIndexIssueFor(PsArtifactDependencySpec.Companion.create(LIBRARY_GROUP, LIBRARY_ARTIFACT, LIBRARY_VERSION),
                                    TestPath("testPath"), parentModuleRootDir = null, sdkIndex = sdkIndex)
    if (expectedMessage == null) {
      assertThat(issue).isNull()
    }
    else {
      assertThat(issue!!.text).endsWith(expectedMessage)
    }
  }

  companion object {
    const val LIBRARY_GROUP = "test-group"
    const val LIBRARY_ARTIFACT = "test-artifact"
    const val LIBRARY_VERSION = "test-version"
    private val MESSAGE_POLICY = null // Policy issues not shown in H
    private val MESSAGE_POLICY_BLOCKING = null // Policy issues not shown in H
    private const val MESSAGE_OUTDATED = "has been marked as outdated by its author"
    private const val MESSAGE_OUTDATED_BLOCKING = "has been marked as outdated by its author and will block publishing of your app to Play Console"
    private const val MESSAGE_CRITICAL = "has an associated message from its author"
    private const val MESSAGE_CRITICAL_BLOCKING = "has been reported as problematic by its author and will block publishing of your app to Play Console"
    private const val MESSAGE_MULTIPLE_ISSUES = "has one or more issues that could block publishing of your app to Play Console in the future"
    private const val MESSAGE_MULTIPLE_ISSUES_BLOCKING = "has one or more issues that will block publishing of your app to Play Console"

    @JvmStatic
    @Parameterized.Parameters(name = "{index}: blocking={0}, nonComplaint={1}, outdated={2}, critical={3}, message={4}")
    fun data() = listOf(
      // No issues
      arrayOf(false, false, false, false, null),
      // Policy
      arrayOf(false, true, false, false, MESSAGE_POLICY),
      // Outdated
      arrayOf(false, false, true, false, MESSAGE_OUTDATED),
      // Critical
      arrayOf(false, false, false, true, MESSAGE_CRITICAL),
      // Two types (one policy)
      arrayOf(false, true, true, false, MESSAGE_OUTDATED),
      // Two types (no policy)
      arrayOf(false, false, true, true, MESSAGE_MULTIPLE_ISSUES),
      // Three types
      arrayOf(false, true, true, true, MESSAGE_MULTIPLE_ISSUES),
      // Policy BLOCKING
      arrayOf(true, true, false, false, MESSAGE_POLICY_BLOCKING),
      // Outdated BLOCKING
      arrayOf(true, false, true, false, MESSAGE_OUTDATED_BLOCKING),
      // Critical BLOCKING
      arrayOf(true, false, false, true, MESSAGE_CRITICAL_BLOCKING),
      // Two types BLOCKING (one policy)
      arrayOf(true, true, true, false, MESSAGE_OUTDATED_BLOCKING),
      // Two types BLOCKING (no policy)
      arrayOf(true, false, true, true, MESSAGE_MULTIPLE_ISSUES_BLOCKING),
      // Three types BLOCKING
      arrayOf(true, true, true, true, MESSAGE_MULTIPLE_ISSUES_BLOCKING),
    )
  }

  class TestGooglePlaySdkIndex(
    private val blocking: Boolean,
    private val nonCompliant: Boolean,
    private val outdated: Boolean,
    private val critical: Boolean,
  ) : GooglePlaySdkIndex(null) {
    override fun readUrlData(url: String, timeout: Int): ByteArray? {
      return null
    }

    override fun error(throwable: Throwable, message: String?) {
    }

    fun prepareForTest() {
      val labels = LibraryVersionLabels.newBuilder()
      labels.severity = if (blocking) LibraryVersionLabels.Severity.BLOCKING_SEVERITY else LibraryVersionLabels.Severity.NON_BLOCKING_SEVERITY
      if (nonCompliant) {
        labels.setPolicyIssuesInfo(LibraryVersionLabels.PolicyIssuesInfo.newBuilder())
      }
      if (outdated) {
        labels.setOutdatedIssueInfo(LibraryVersionLabels.OutdatedIssueInfo.newBuilder())
      }
      if (critical) {
        labels.setCriticalIssueInfo(LibraryVersionLabels.CriticalIssueInfo.newBuilder())
      }
      val proto = Index.newBuilder()
        .addSdks(
          Sdk.newBuilder()
            .setIndexUrl("http://index.example.url/")
            .addLibraries(
              Library.newBuilder()
                .setLibraryId(
                  LibraryIdentifier.newBuilder()
                    .setMavenId(
                      LibraryIdentifier.MavenIdentifier.newBuilder()
                        .setGroupId(LIBRARY_GROUP)
                        .setArtifactId(LIBRARY_ARTIFACT)
                        .build()
                    ).build()
                )
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString(LIBRARY_VERSION)
                    .setIsLatestVersion(true)
                    .setVersionLabels(labels)
                    .build()
                )
            )
            .build()
        )
        .build()
      this.initialize(ByteArrayInputStream(proto.toByteArray()))
    }
  }
}