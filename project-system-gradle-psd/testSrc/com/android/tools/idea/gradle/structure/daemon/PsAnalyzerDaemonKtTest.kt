package com.android.tools.idea.gradle.structure.daemon

import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec
import com.android.tools.idea.gradle.structure.model.TestPath
import com.android.tools.lint.checks.AlternativeLibrary
import com.android.tools.lint.checks.GooglePlaySdkIndex
import com.android.tools.lint.checks.Index
import com.android.tools.lint.checks.Library
import com.android.tools.lint.checks.LibraryDeprecation
import com.android.tools.lint.checks.LibraryIdentifier
import com.android.tools.lint.checks.LibraryVersion
import com.android.tools.lint.checks.LibraryVersionLabels
import com.android.tools.lint.checks.LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy
import com.android.tools.lint.checks.MavenIdentifier
import com.android.tools.lint.checks.Sdk
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
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
                               private val vulnerability: Boolean,
                               private val deprecated: Boolean,
                               private val violations: List<SdkPolicy>,
                               private val expectedMessages: List<String>) {

    @Test
    fun `Expected issue`() {
      val sdkIndex = TestGooglePlaySdkIndex(blocking, nonCompliant, outdated, critical, showNotes, vulnerability, deprecated, violations)
      sdkIndex.prepareForTest()
      val issues = getSdkIndexIssueFor(PsArtifactDependencySpec.Companion.create(LIBRARY_GROUP, LIBRARY_ARTIFACT, LIBRARY_VERSION),
                                      TestPath("testPath"), parentModuleRootDir = null, sdkIndex = sdkIndex)
      assertThat(issues).hasSize(expectedMessages.size)
      issues.forEachIndexed {index, issue ->
        assertThat(issue.text).isEqualTo(expectedMessages[index])
      }
    }

    companion object {
      const val LIBRARY_GROUP = "test-group"
      const val LIBRARY_ARTIFACT = "test-artifact"
      const val LIBRARY_VERSION = "test-version"
      private const val MESSAGE_POLICY = "test-group:test-artifact version test-version has<br/>\n" +
                                         "policy issues that will block publishing of your app to<br/>\n" +
                                         "Play Console in the future"
      private const val MESSAGE_POLICY_BLOCKING = "<b>[Prevents app release in Google Play Console]</b><br/>\n" +
                                                  "test-group:test-artifact version test-version has<br/>\n" +
                                                  "policy issues that will block publishing of your app to<br/>\n" +
                                                  "Play Console"
      private const val MESSAGE_OUTDATED = "test-group:test-artifact version test-version has been<br/>\n" +
                                           "reported as outdated by its author"
      private const val MESSAGE_OUTDATED_BLOCKING = "<b>[Prevents app release in Google Play Console]</b><br/>\n" +
                                                    "test-group:test-artifact version test-version has been<br/>\n" +
                                                    "reported as outdated by its author and will block<br/>\n" +
                                                    "publishing of your app to Play Console"
      private const val MESSAGE_CRITICAL = "test-group:test-artifact version test-version has an<br/>\n" +
                                           "associated message from its author"
      private const val MESSAGE_CRITICAL_WITH_NOTE = "$MESSAGE_CRITICAL.<br/>\n" +
                                                     "<br/>\n" +
                                                     "<b>Note:</b> More information at <a href=\"http://www.google.com\">http://www.google.com</a>"
      private const val MESSAGE_CRITICAL_BLOCKING = "<b>[Prevents app release in Google Play Console]</b><br/>\n" +
                                                    "test-group:test-artifact version test-version has been<br/>\n" +
                                                    "reported as problematic by its author and will block<br/>\n" +
                                                    "publishing of your app to Play Console"
      private const val MESSAGE_CRITICAL_BLOCKING_WITH_NOTE = "$MESSAGE_CRITICAL_BLOCKING.<br/>\n" +
                                                              "<br/>\n" +
                                                              "<b>Note:</b> More information at <a href=\"http://www.google.com\">http://www.google.com</a>"
      private const val MESSAGE_POLICY_USER = "test-group:test-artifact version test-version has User<br/>\n" +
                                              "Data policy issues that will block publishing of your<br/>\n" +
                                              "app to Play Console in the future"
      private const val MESSAGE_POLICY_USER_BLOCKING = "<b>[Prevents app release in Google Play Console]</b><br/>\n" +
                                                       "test-group:test-artifact version test-version has User<br/>\n" +
                                                       "Data policy issues that will block publishing of your<br/>\n" +
                                                       "app to Play Console"
      private const val MESSAGE_POLICY_PERMISSIONS = "test-group:test-artifact version test-version has<br/>\n" +
                                                     "Permissions policy issues that will block publishing of<br/>\n" +
                                                     "your app to Play Console in the future"
      private const val MESSAGE_POLICY_PERMISSIONS_BLOCKING = "<b>[Prevents app release in Google Play Console]</b><br/>\n" +
                                                              "test-group:test-artifact version test-version has<br/>\n" +
                                                              "Permissions policy issues that will block publishing of<br/>\n" +
                                                              "your app to Play Console"
      private const val MESSAGE_VULNERABILITY = "test-group:test-artifact version test-version has<br/>\n" +
                                                "unspecified vulnerability issues."
      private const val MESSAGE_VULNERABILITY_BLOCKING = "test-group:test-artifact version test-version has<br/>\n" +
                                                         "unspecified vulnerability issues."
      private const val MESSAGE_DEPRECATED = "SDK Name (test-group:test-artifact) has been deprecated<br/>\n" +
                                             "by its developer. Consider updating to an alternative<br/>\n" +
                                             "SDK before publishing a new release.<br/>\n" +
                                             "<br/>\n" +
                                             "The developer has recommended these alternatives:<br/>\n" +
                                             "\n" +
                                             "<pre>\n" +
                                             "\n" +
                                             " - Alternative 1 (first:alternative)\n" +
                                             "\n" +
                                             " - second:alternative\n" +
                                             "\n" +
                                             "</pre>\n"


      @JvmStatic
      @Parameterized.Parameters(name = "{index}: blocking={0}, nonComplaint={1}, outdated={2}, critical={3}, notes={4}, vulnerability={5}, deprecated={6}")
      fun data() = listOf(
        // No issues
        arrayOf(false, false, false, false, true, false, false, emptyList<SdkPolicy>(), emptyList<String>()),
        // Policy
        arrayOf(false, true, false, false, true, false, false, emptyList<SdkPolicy>(), listOf(MESSAGE_POLICY)),
        // Outdated
        arrayOf(false, false, true, false, true, false, false, emptyList<SdkPolicy>(), listOf(MESSAGE_OUTDATED)),
        // Critical (with notes)
        arrayOf(false, false, false, true, true, false, false, emptyList<SdkPolicy>(), listOf(MESSAGE_CRITICAL_WITH_NOTE)),
        // Critical (without notes)
        arrayOf(false, false, false, true, false, false, false, emptyList<SdkPolicy>(), listOf(MESSAGE_CRITICAL)),
        // Vulnerability
        arrayOf(false, false, false, false, true, true, false, emptyList<SdkPolicy>(), listOf(MESSAGE_VULNERABILITY)),
        // Deprecated
        arrayOf(false, false, false, false, true, false, true, emptyList<SdkPolicy>(), listOf(MESSAGE_DEPRECATED)),
        // Two types
        arrayOf(false, true, true, false, true, false, false, emptyList<SdkPolicy>(), listOf(MESSAGE_POLICY, MESSAGE_OUTDATED)),
        // Three types (with notes)
        arrayOf(false, true, true, true, true, false, false, emptyList<SdkPolicy>(), listOf(MESSAGE_POLICY, MESSAGE_OUTDATED, MESSAGE_CRITICAL_WITH_NOTE)),
        // Three types (without notes)
        arrayOf(false, true, true, true, false, false, false, emptyList<SdkPolicy>(), listOf(MESSAGE_POLICY, MESSAGE_OUTDATED, MESSAGE_CRITICAL)),
        // Four types (with notes)
        arrayOf(false, true, true, true, true, true, false, emptyList<SdkPolicy>(), listOf(MESSAGE_POLICY, MESSAGE_VULNERABILITY, MESSAGE_OUTDATED, MESSAGE_CRITICAL_WITH_NOTE)),
        // Four types (without notes)
        arrayOf(false, true, true, true, false, true, false, emptyList<SdkPolicy>(), listOf(MESSAGE_POLICY, MESSAGE_VULNERABILITY, MESSAGE_OUTDATED, MESSAGE_CRITICAL)),
        // Two policies
        arrayOf(false, true, false, false, true, false, false, listOf(SdkPolicy.SDK_POLICY_USER_DATA, SdkPolicy.SDK_POLICY_PERMISSIONS), listOf(MESSAGE_POLICY_USER, MESSAGE_POLICY_PERMISSIONS)),
        // All types (including deprecated)
        arrayOf(false, true, true, true, true, true, true, emptyList<SdkPolicy>(), listOf(MESSAGE_DEPRECATED, MESSAGE_POLICY, MESSAGE_VULNERABILITY, MESSAGE_OUTDATED, MESSAGE_CRITICAL_WITH_NOTE)),
        // Policy BLOCKING
        arrayOf(true, true, false, false, true, false, false, emptyList<SdkPolicy>(), listOf(MESSAGE_POLICY_BLOCKING)),
        // Outdated BLOCKING
        arrayOf(true, false, true, false, true, false, false, emptyList<SdkPolicy>(), listOf(MESSAGE_OUTDATED_BLOCKING)),
        // Critical BLOCKING (with notes)
        arrayOf(true, false, false, true, true, false, false, emptyList<SdkPolicy>(), listOf(MESSAGE_CRITICAL_BLOCKING_WITH_NOTE)),
        // Critical BLOCKING (without notes)
        arrayOf(true, false, false, true, false, false, false, emptyList<SdkPolicy>(), listOf(MESSAGE_CRITICAL_BLOCKING)),
        // Vulnerability BLOCKING
        arrayOf(true, false, false, false, true, true, false, emptyList<SdkPolicy>(), listOf(MESSAGE_VULNERABILITY_BLOCKING)),
        // Deprecated BLOCKING
        arrayOf(true, false, false, false, true, false, true, emptyList<SdkPolicy>(), listOf(MESSAGE_DEPRECATED)),
        // Two types BLOCKING
        arrayOf(true, true, true, false, true, false, false, emptyList<SdkPolicy>(), listOf(MESSAGE_POLICY_BLOCKING, MESSAGE_OUTDATED_BLOCKING)),
        // Three types BLOCKING (with notes)
        arrayOf(true, true, true, true, true, false, false, emptyList<SdkPolicy>(), listOf(MESSAGE_POLICY_BLOCKING, MESSAGE_CRITICAL_BLOCKING_WITH_NOTE, MESSAGE_OUTDATED_BLOCKING)),
        // Three types BLOCKING (without notes)
        arrayOf(true, true, true, true, false, false, false, emptyList<SdkPolicy>(), listOf(MESSAGE_POLICY_BLOCKING, MESSAGE_CRITICAL_BLOCKING, MESSAGE_OUTDATED_BLOCKING)),
        // Four types BLOCKING (with notes)
        arrayOf(true, true, true, true, true, true, false, emptyList<SdkPolicy>(), listOf(MESSAGE_POLICY_BLOCKING, MESSAGE_CRITICAL_BLOCKING_WITH_NOTE, MESSAGE_VULNERABILITY_BLOCKING, MESSAGE_OUTDATED_BLOCKING)),
        // Four types BLOCKING (without notes)
        arrayOf(true, true, true, true, false, true, false, emptyList<SdkPolicy>(), listOf(MESSAGE_POLICY_BLOCKING, MESSAGE_CRITICAL_BLOCKING, MESSAGE_VULNERABILITY_BLOCKING, MESSAGE_OUTDATED_BLOCKING)),
        // Two policies BLOCKING
        arrayOf(true, true, false, false, true, false, false, listOf(SdkPolicy.SDK_POLICY_USER_DATA, SdkPolicy.SDK_POLICY_PERMISSIONS), listOf(MESSAGE_POLICY_USER_BLOCKING, MESSAGE_POLICY_PERMISSIONS_BLOCKING)),
        // All types (including deprecated) BLOCKING
        arrayOf(true, true, true, true, true, true, true, emptyList<SdkPolicy>(), listOf(MESSAGE_DEPRECATED, MESSAGE_POLICY_BLOCKING, MESSAGE_CRITICAL_BLOCKING_WITH_NOTE, MESSAGE_VULNERABILITY_BLOCKING, MESSAGE_OUTDATED_BLOCKING)),
      )
    }

    class TestGooglePlaySdkIndex(
      private val blocking: Boolean,
      private val nonCompliant: Boolean,
      private val outdated: Boolean,
      private val critical: Boolean,
      private val showNotes: Boolean,
      private val vulnerability: Boolean,
      private val deprecated: Boolean,
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
        if (vulnerability) {
          labels.setSecurityVulnerabilitiesInfo(LibraryVersionLabels.SecurityVulnerabilitiesInfo.newBuilder())
        }
        val libraryDeprecation = LibraryDeprecation.newBuilder()
        if (deprecated) {
          libraryDeprecation
            .setDeprecationTimestampSeconds(Instant.parse("2024-11-20T10:00:00Z").epochSeconds)
            .addAlternativeLibraries(
              AlternativeLibrary.newBuilder()
                .setSdkName("Alternative 1")
                .setMavenSdkId(
                  MavenIdentifier.newBuilder()
                    .setGroupId("first")
                    .setArtifactId("alternative")
                )
            )
            .addAlternativeLibraries(
              AlternativeLibrary.newBuilder()
                .setMavenSdkId(
                  MavenIdentifier.newBuilder()
                    .setGroupId("second")
                    .setArtifactId("alternative")
                )
            )
        }
        val proto = Index.newBuilder()
          .addSdks(
            Sdk.newBuilder()
              .setIndexUrl("http://index.example.url/")
              .setSdkName("SDK Name")
              .addLibraries(
                Library.newBuilder()
                  .setLibraryId(
                    LibraryIdentifier.newBuilder()
                      .setMavenId(
                        MavenIdentifier.newBuilder()
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
                  .setLibraryDeprecation(libraryDeprecation)
              )
              .build()
          )
          .build()
        this.showDeprecationIssues = true
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

    @Test
    fun `New lines are doubled`() {
      val message = "Line was\nbroken"
      val wrappedMessage = formatToPSD(message, 10)
      val expectedMessage = "Line was<br/>\n" +
                            "<br/>\n" +
                            "broken"
      assertThat(wrappedMessage).isEqualTo(expectedMessage)
    }
  }
}