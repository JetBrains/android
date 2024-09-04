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
                               private val showNotes: Boolean,
                               private val violations: List<SdkPolicy>,
                               private val expectedMessages: List<String>) {

    @Test
    fun `Expected issue`() {
      val sdkIndex = TestGooglePlaySdkIndex(blocking, nonCompliant, outdated, critical, showNotes, violations)
      sdkIndex.prepareForTest()
      val issues = getSdkIndexIssueFor(PsArtifactDependencySpec.Companion.create(LIBRARY_GROUP, LIBRARY_ARTIFACT, LIBRARY_VERSION),
                                      TestPath("testPath"), parentModuleRootDir = null, sdkIndex = sdkIndex)
      assertThat(issues).hasSize(expectedMessages.size)
      issues.forEachIndexed {index, issue ->
        assertThat(issue.text.replace("<br/>\n", " ")).isEqualTo(expectedMessages[index])
      }
    }

    companion object {
      const val LIBRARY_GROUP = "test-group"
      const val LIBRARY_ARTIFACT = "test-artifact"
      const val LIBRARY_VERSION = "test-version"
      private const val MESSAGE_POLICY = "test-group:test-artifact version test-version has policy issues that will block publishing of your app to Play Console in the future"
      private const val MESSAGE_POLICY_BLOCKING = "<b>[Prevents app release in Google Play Console]</b> test-group:test-artifact version test-version has policy issues that will block publishing of your app to Play Console"
      private const val MESSAGE_OUTDATED = "test-group:test-artifact version test-version has been reported as outdated by its author"
      private const val MESSAGE_OUTDATED_BLOCKING = "<b>[Prevents app release in Google Play Console]</b> test-group:test-artifact version test-version has been reported as outdated by its author and will block publishing of your app to Play Console"
      private const val MESSAGE_CRITICAL = "test-group:test-artifact version test-version has an associated message from its author"
      private const val MESSAGE_CRITICAL_WITH_NOTE = "$MESSAGE_CRITICAL. <b>Note:</b> More information at <a href=\"http://www.google.com\">http://www.google.com</a>"
      private const val MESSAGE_CRITICAL_BLOCKING = "<b>[Prevents app release in Google Play Console]</b> test-group:test-artifact version test-version has been reported as problematic by its author and will block publishing of your app to Play Console"
      private const val MESSAGE_CRITICAL_BLOCKING_WITH_NOTE = "$MESSAGE_CRITICAL_BLOCKING. <b>Note:</b> More information at <a href=\"http://www.google.com\">http://www.google.com</a>"
      private const val MESSAGE_POLICY_USER = "test-group:test-artifact version test-version has User Data policy issues that will block publishing of your app to Play Console in the future"
      private const val MESSAGE_POLICY_USER_BLOCKING = "<b>[Prevents app release in Google Play Console]</b> test-group:test-artifact version test-version has User Data policy issues that will block publishing of your app to Play Console"
      private const val MESSAGE_POLICY_PERMISSIONS = "test-group:test-artifact version test-version has Permissions policy issues that will block publishing of your app to Play Console in the future"
      private const val MESSAGE_POLICY_PERMISSIONS_BLOCKING = "<b>[Prevents app release in Google Play Console]</b> test-group:test-artifact version test-version has Permissions policy issues that will block publishing of your app to Play Console"

      @JvmStatic
      @Parameterized.Parameters(name = "{index}: blocking={0}, nonComplaint={1}, outdated={2}, critical={3}, notes={4}")
      fun data() = listOf(
        // No issues
        arrayOf(false, false, false, false, true, emptyList<SdkPolicy>(), emptyList<String>()),
        // Policy
        arrayOf(false, true, false, false, true, emptyList<SdkPolicy>(), listOf(MESSAGE_POLICY)),
        // Outdated
        arrayOf(false, false, true, false, true, emptyList<SdkPolicy>(), listOf(MESSAGE_OUTDATED)),
        // Critical (with notes)
        arrayOf(false, false, false, true, true, emptyList<SdkPolicy>(), listOf(MESSAGE_CRITICAL_WITH_NOTE)),
        // Critical (without notes)
        arrayOf(false, false, false, true, false, emptyList<SdkPolicy>(), listOf(MESSAGE_CRITICAL)),
        // Two types
        arrayOf(false, true, true, false, true, emptyList<SdkPolicy>(), listOf(MESSAGE_POLICY, MESSAGE_OUTDATED)),
        // Three types (with notes)
        arrayOf(false, true, true, true, true, emptyList<SdkPolicy>(), listOf(MESSAGE_POLICY, MESSAGE_OUTDATED, MESSAGE_CRITICAL_WITH_NOTE)),
        // Three types (without notes)
        arrayOf(false, true, true, true, false, emptyList<SdkPolicy>(), listOf(MESSAGE_POLICY, MESSAGE_OUTDATED, MESSAGE_CRITICAL)),
        // Two policies
        arrayOf(false, true, false, false, true, listOf(SdkPolicy.SDK_POLICY_USER_DATA, SdkPolicy.SDK_POLICY_PERMISSIONS), listOf(MESSAGE_POLICY_USER, MESSAGE_POLICY_PERMISSIONS)),
        // Policy BLOCKING
        arrayOf(true, true, false, false, true, emptyList<SdkPolicy>(), listOf(MESSAGE_POLICY_BLOCKING)),
        // Outdated BLOCKING
        arrayOf(true, false, true, false, true, emptyList<SdkPolicy>(), listOf(MESSAGE_OUTDATED_BLOCKING)),
        // Critical BLOCKING (with notes)
        arrayOf(true, false, false, true, true, emptyList<SdkPolicy>(), listOf(MESSAGE_CRITICAL_BLOCKING_WITH_NOTE)),
        // Critical BLOCKING (without notes)
        arrayOf(true, false, false, true, false, emptyList<SdkPolicy>(), listOf(MESSAGE_CRITICAL_BLOCKING)),
        // Two types BLOCKING
        arrayOf(true, true, true, false, true, emptyList<SdkPolicy>(), listOf(MESSAGE_POLICY_BLOCKING, MESSAGE_OUTDATED_BLOCKING)),
        // Three types BLOCKING (with notes)
        arrayOf(true, true, true, true, true, emptyList<SdkPolicy>(), listOf(MESSAGE_POLICY_BLOCKING, MESSAGE_CRITICAL_BLOCKING_WITH_NOTE, MESSAGE_OUTDATED_BLOCKING)),
        // Three types BLOCKING (without notes)
        arrayOf(true, true, true, true, false, emptyList<SdkPolicy>(), listOf(MESSAGE_POLICY_BLOCKING, MESSAGE_CRITICAL_BLOCKING, MESSAGE_OUTDATED_BLOCKING)),
        // Two policies BLOCKING
        arrayOf(true, true, false, false, true, listOf(SdkPolicy.SDK_POLICY_USER_DATA, SdkPolicy.SDK_POLICY_PERMISSIONS), listOf(MESSAGE_POLICY_USER_BLOCKING, MESSAGE_POLICY_PERMISSIONS_BLOCKING)),
      )
    }

    class TestGooglePlaySdkIndex(
      private val blocking: Boolean,
      private val nonCompliant: Boolean,
      private val outdated: Boolean,
      private val critical: Boolean,
      private val showNotes: Boolean,
      private val violations: List<SdkPolicy>,
    ) : GooglePlaySdkIndex(null) {
      override fun readUrlData(url: String, timeout: Int, lastModified: Long) = ReadUrlDataResult(null, true)

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
          labels.setCriticalIssueInfo(LibraryVersionLabels.CriticalIssueInfo.newBuilder()
                                        .setDescription("More information at http://www.google.com")
          )
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
        this.showNotesFromDeveloper = showNotes
      }
    }
  }

  class WrappingTests {
    @Test
    fun `Text wrapped correctly`() {
      val message = "   Message should be wrapped up to 10 characters per line unless there_is_a_very_long_word in the text    "
      val wrappedMessage = formatToPSD(message, 10)
      val expectedMessage = "Message<br/>\n" +
                            "should be<br/>\n" +
                            "wrapped up<br/>\n" +
                            "to 10<br/>\n" +
                            "characters<br/>\n" +
                            "per line<br/>\n" +
                            "unless<br/>\n" +
                            "there_is_a_very_long_word<br/>\n" +
                            "in the<br/>\n" +
                            "text"
      assertThat(wrappedMessage).isEqualTo(expectedMessage)
    }

    @Test
    fun `Links preserved without breaking tags`() {
      val message = "Links like http:///google.com should be tagged and not be broken"
      val wrappedMessage = formatToPSD(message, 10)
      val expectedMessage = "Links like<br/>\n" +
                            "<a href=\"http:///google.com\">http:///google.com</a><br/>\n" +
                            "should be<br/>\n" +
                            "tagged and<br/>\n" +
                            "not be<br/>\n" +
                            "broken"
      assertThat(wrappedMessage).isEqualTo(expectedMessage)
    }
  }
}