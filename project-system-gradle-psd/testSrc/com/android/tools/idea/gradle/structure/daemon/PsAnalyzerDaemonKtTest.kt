package com.android.tools.idea.gradle.structure.daemon

import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec
import com.android.tools.idea.gradle.structure.model.TestPath
import com.android.tools.lint.checks.GooglePlaySdkIndex
import com.android.tools.lint.checks.Index
import com.android.tools.lint.checks.Library
import com.android.tools.lint.checks.LibraryIdentifier
import com.android.tools.lint.checks.LibraryVersion
import com.android.tools.lint.checks.LibraryVersionLabels
import com.android.tools.lint.checks.LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy
import com.android.tools.lint.checks.Sdk
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.ByteArrayInputStream

@RunWith(value = Enclosed::class)
class PsAnalyzerDaemonKtTest {
  @RunWith(value = Parameterized::class)
  class PsAnalyzerMessagesTest(private val blocking: Boolean,
                               private val nonCompliant: Boolean,
                               private val outdated: Boolean,
                               private val critical: Boolean,
                               private val violations: List<SdkPolicy>,
                               private val expectedMessages: List<String>) {

    @Test
    fun `Expected issue`() {
      val sdkIndex = TestGooglePlaySdkIndex(blocking, nonCompliant, outdated, critical, violations)
      sdkIndex.prepareForTest()
      val issues = getSdkIndexIssueFor(PsArtifactDependencySpec.Companion.create(LIBRARY_GROUP, LIBRARY_ARTIFACT, LIBRARY_VERSION),
                                      TestPath("testPath"), parentModuleRootDir = null, sdkIndex = sdkIndex)
      assertThat(issues).hasSize(expectedMessages.size)
      issues.forEachIndexed {index, issue ->
        assertThat(issue.text.replace("\n<br>", " ")).endsWith(expectedMessages[index])
      }
    }

    companion object {
      const val LIBRARY_GROUP = "test-group"
      const val LIBRARY_ARTIFACT = "test-artifact"
      const val LIBRARY_VERSION = "test-version"
      private const val MESSAGE_POLICY = "has policy issues that will block publishing of your app to Play Console in the future"
      private const val MESSAGE_POLICY_BLOCKING = "has policy issues that will block publishing of your app to Play Console"
      private const val MESSAGE_OUTDATED = "has been marked as outdated by its author"
      private const val MESSAGE_OUTDATED_BLOCKING = "has been marked as outdated by its author and will block publishing of your app to Play Console"
      private const val MESSAGE_CRITICAL = "has an associated message from its author"
      private const val MESSAGE_CRITICAL_BLOCKING = "has been reported as problematic by its author and will block publishing of your app to Play Console"
      private const val MESSAGE_POLICY_USER = "has User Data policy issues that will block publishing of your app to Play Console in the future"
      private const val MESSAGE_POLICY_USER_BLOCKING = "has User Data policy issues that will block publishing of your app to Play Console"
      private const val MESSAGE_POLICY_PERMISSIONS = "has Permissions policy issues that will block publishing of your app to Play Console in the future"
      private const val MESSAGE_POLICY_PERMISSIONS_BLOCKING = "has Permissions policy issues that will block publishing of your app to Play Console"

      @JvmStatic
      @Parameterized.Parameters(name = "{index}: blocking={0}, nonComplaint={1}, outdated={2}, critical={3}, message={4}")
      fun data() = listOf(
        // No issues
        arrayOf(false, false, false, false, emptyList<SdkPolicy>(), emptyList<String>()),
        // Policy
        arrayOf(false, true, false, false, emptyList<SdkPolicy>(), listOf(MESSAGE_POLICY)),
        // Outdated
        arrayOf(false, false, true, false, emptyList<SdkPolicy>(), listOf(MESSAGE_OUTDATED)),
        // Critical
        arrayOf(false, false, false, true, emptyList<SdkPolicy>(), listOf(MESSAGE_CRITICAL)),
        // Two types
        arrayOf(false, true, true, false, emptyList<SdkPolicy>(), listOf(MESSAGE_POLICY, MESSAGE_OUTDATED)),
        // Three types
        arrayOf(false, true, true, true, emptyList<SdkPolicy>(), listOf(MESSAGE_POLICY, MESSAGE_OUTDATED, MESSAGE_CRITICAL)),
        // Two policies
        arrayOf(false, true, false, false, listOf(SdkPolicy.SDK_POLICY_USER_DATA, SdkPolicy.SDK_POLICY_PERMISSIONS), listOf(MESSAGE_POLICY_USER, MESSAGE_POLICY_PERMISSIONS)),
        // Policy BLOCKING
        arrayOf(true, true, false, false, emptyList<SdkPolicy>(), listOf(MESSAGE_POLICY_BLOCKING)),
        // Outdated BLOCKING
        arrayOf(true, false, true, false, emptyList<SdkPolicy>(), listOf(MESSAGE_OUTDATED_BLOCKING)),
        // Critical BLOCKING
        arrayOf(true, false, false, true, emptyList<SdkPolicy>(), listOf(MESSAGE_CRITICAL_BLOCKING)),
        // Two types BLOCKING
        arrayOf(true, true, true, false, emptyList<SdkPolicy>(), listOf(MESSAGE_POLICY_BLOCKING, MESSAGE_OUTDATED_BLOCKING)),
        // Three types BLOCKING
        arrayOf(true, true, true, true, emptyList<SdkPolicy>(), listOf(MESSAGE_POLICY_BLOCKING, MESSAGE_CRITICAL_BLOCKING, MESSAGE_OUTDATED_BLOCKING)),
        // Two policies BLOCKING
        arrayOf(true, true, false, false, listOf(SdkPolicy.SDK_POLICY_USER_DATA, SdkPolicy.SDK_POLICY_PERMISSIONS), listOf(MESSAGE_POLICY_USER_BLOCKING, MESSAGE_POLICY_PERMISSIONS_BLOCKING)),
      )
    }

    class TestGooglePlaySdkIndex(
      private val blocking: Boolean,
      private val nonCompliant: Boolean,
      private val outdated: Boolean,
      private val critical: Boolean,
      private val violations: List<SdkPolicy>,
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
          labels.setPolicyIssuesInfo(LibraryVersionLabels.PolicyIssuesInfo.newBuilder().addAllViolatedSdkPolicies(violations))
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

  class WrappingTests {
    @Test
    fun `Text wrapped correctly`() {
      val message = "Message should be wrapped up to 10 characters per line unless there_is_a_very_long_word in the text"
      val wrappedMessage = wrapMessage(message, 10)
      val expectedMessage = "Message\n" +
                            "<br>should be\n" +
                            "<br>wrapped up\n" +
                            "<br>to 10\n" +
                            "<br>characters\n" +
                            "<br>per line\n" +
                            "<br>unless\n" +
                            "<br>there_is_a_very_long_word\n" +
                            "<br>in the\n" +
                            "<br>text"
      assertThat(wrappedMessage).isEqualTo(expectedMessage)
    }
  }
}