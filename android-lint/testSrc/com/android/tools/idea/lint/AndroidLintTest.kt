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
package com.android.tools.idea.lint

import com.android.AndroidProjectTypes
import com.android.SdkConstants
import com.android.ide.common.repository.GradleVersion
import com.android.ide.common.repository.StubGoogleMavenRepository
import com.android.sdklib.AndroidVersion
import com.android.testutils.TestUtils
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.AnalyticsSettings
import com.android.tools.analytics.AnalyticsSettings.setInstanceForTest
import com.android.tools.analytics.AnalyticsSettingsData
import com.android.tools.analytics.LoggedUsage
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker.cleanAfterTesting
import com.android.tools.analytics.UsageTracker.setWriterForTest
import com.android.tools.idea.gradle.repositories.RepositoryUrlManager
import com.android.tools.idea.lint.common.AndroidLintGradleDynamicVersionInspection
import com.android.tools.idea.lint.common.AndroidLintInspectionBase
import com.android.tools.idea.lint.common.LintEditorResult
import com.android.tools.idea.lint.common.LintExternalAnnotator.MyFixingIntention
import com.android.tools.idea.lint.common.LintIdeQuickFix
import com.android.tools.idea.lint.common.LintIdeSupport
import com.android.tools.idea.lint.common.LintIgnoredResult
import com.android.tools.idea.lint.common.LintProblemData
import com.android.tools.idea.lint.common.ReplaceStringQuickFix
import com.android.tools.idea.lint.common.SuppressLintIntentionAction
import com.android.tools.idea.lint.inspections.AndroidLintAdapterViewChildrenInspection
import com.android.tools.idea.lint.inspections.AndroidLintAlwaysShowActionInspection
import com.android.tools.idea.lint.inspections.AndroidLintAndroidGradlePluginVersionInspection
import com.android.tools.idea.lint.inspections.AndroidLintAnimatorKeepInspection
import com.android.tools.idea.lint.inspections.AndroidLintAnnotateVersionCheckInspection
import com.android.tools.idea.lint.inspections.AndroidLintAppCompatCustomViewInspection
import com.android.tools.idea.lint.inspections.AndroidLintAppCompatMethodInspection
import com.android.tools.idea.lint.inspections.AndroidLintApplySharedPrefInspection
import com.android.tools.idea.lint.inspections.AndroidLintAuthLeakInspection
import com.android.tools.idea.lint.inspections.AndroidLintByteOrderMarkInspection
import com.android.tools.idea.lint.inspections.AndroidLintClickableViewAccessibilityInspection
import com.android.tools.idea.lint.inspections.AndroidLintContentDescriptionInspection
import com.android.tools.idea.lint.inspections.AndroidLintDeprecatedInspection
import com.android.tools.idea.lint.inspections.AndroidLintDisableBaselineAlignmentInspection
import com.android.tools.idea.lint.inspections.AndroidLintDuplicateIdsInspection
import com.android.tools.idea.lint.inspections.AndroidLintEnforceUTF8Inspection
import com.android.tools.idea.lint.inspections.AndroidLintExifInterfaceInspection
import com.android.tools.idea.lint.inspections.AndroidLintExportedContentProviderInspection
import com.android.tools.idea.lint.inspections.AndroidLintExportedReceiverInspection
import com.android.tools.idea.lint.inspections.AndroidLintExportedServiceInspection
import com.android.tools.idea.lint.inspections.AndroidLintExtraTextInspection
import com.android.tools.idea.lint.inspections.AndroidLintGradleDeprecatedInspection
import com.android.tools.idea.lint.inspections.AndroidLintGridLayoutInspection
import com.android.tools.idea.lint.inspections.AndroidLintHardcodedTextInspection
import com.android.tools.idea.lint.inspections.AndroidLintIconDuplicatesInspection
import com.android.tools.idea.lint.inspections.AndroidLintImpliedTouchscreenHardwareInspection
import com.android.tools.idea.lint.inspections.AndroidLintIncludeLayoutParamInspection
import com.android.tools.idea.lint.inspections.AndroidLintInefficientWeightInspection
import com.android.tools.idea.lint.inspections.AndroidLintInlinedApiInspection
import com.android.tools.idea.lint.inspections.AndroidLintInnerclassSeparatorInspection
import com.android.tools.idea.lint.inspections.AndroidLintInvalidImeActionIdInspection
import com.android.tools.idea.lint.inspections.AndroidLintInvalidPermissionInspection
import com.android.tools.idea.lint.inspections.AndroidLintInvalidUsesTagAttributeInspection
import com.android.tools.idea.lint.inspections.AndroidLintInvalidVectorPathInspection
import com.android.tools.idea.lint.inspections.AndroidLintInvalidWearFeatureAttributeInspection
import com.android.tools.idea.lint.inspections.AndroidLintLockedOrientationActivityInspection
import com.android.tools.idea.lint.inspections.AndroidLintManifestOrderInspection
import com.android.tools.idea.lint.inspections.AndroidLintMenuTitleInspection
import com.android.tools.idea.lint.inspections.AndroidLintMissingApplicationIconInspection
import com.android.tools.idea.lint.inspections.AndroidLintMissingIdInspection
import com.android.tools.idea.lint.inspections.AndroidLintMissingLeanbackSupportInspection
import com.android.tools.idea.lint.inspections.AndroidLintMissingPermissionInspection
import com.android.tools.idea.lint.inspections.AndroidLintMissingPrefixInspection
import com.android.tools.idea.lint.inspections.AndroidLintMissingTvBannerInspection
import com.android.tools.idea.lint.inspections.AndroidLintMotionLayoutInvalidSceneFileReferenceInspection
import com.android.tools.idea.lint.inspections.AndroidLintMotionSceneFileValidationErrorInspection
import com.android.tools.idea.lint.inspections.AndroidLintNetworkSecurityConfigInspection
import com.android.tools.idea.lint.inspections.AndroidLintNewApiInspection
import com.android.tools.idea.lint.inspections.AndroidLintNonResizeableActivityInspection
import com.android.tools.idea.lint.inspections.AndroidLintNotificationPermissionInspection
import com.android.tools.idea.lint.inspections.AndroidLintObsoleteLayoutParamInspection
import com.android.tools.idea.lint.inspections.AndroidLintObsoleteSdkIntInspection
import com.android.tools.idea.lint.inspections.AndroidLintOldTargetApiInspection
import com.android.tools.idea.lint.inspections.AndroidLintOverrideInspection
import com.android.tools.idea.lint.inspections.AndroidLintParcelClassLoaderInspection
import com.android.tools.idea.lint.inspections.AndroidLintParcelCreatorInspection
import com.android.tools.idea.lint.inspections.AndroidLintPermissionImpliesUnsupportedChromeOsHardwareInspection
import com.android.tools.idea.lint.inspections.AndroidLintPermissionImpliesUnsupportedHardwareInspection
import com.android.tools.idea.lint.inspections.AndroidLintProguardInspection
import com.android.tools.idea.lint.inspections.AndroidLintPxUsageInspection
import com.android.tools.idea.lint.inspections.AndroidLintReferenceTypeInspection
import com.android.tools.idea.lint.inspections.AndroidLintRegisteredInspection
import com.android.tools.idea.lint.inspections.AndroidLintResourceAsColorInspection
import com.android.tools.idea.lint.inspections.AndroidLintResourceTypeInspection
import com.android.tools.idea.lint.inspections.AndroidLintRtlCompatInspection
import com.android.tools.idea.lint.inspections.AndroidLintScrollViewCountInspection
import com.android.tools.idea.lint.inspections.AndroidLintScrollViewSizeInspection
import com.android.tools.idea.lint.inspections.AndroidLintSdCardPathInspection
import com.android.tools.idea.lint.inspections.AndroidLintSelectableTextInspection
import com.android.tools.idea.lint.inspections.AndroidLintSetAndClearCommunicationDeviceInspection
import com.android.tools.idea.lint.inspections.AndroidLintSignatureOrSystemPermissionsInspection
import com.android.tools.idea.lint.inspections.AndroidLintSourceLockedOrientationActivityInspection
import com.android.tools.idea.lint.inspections.AndroidLintSpUsageInspection
import com.android.tools.idea.lint.inspections.AndroidLintStringEscapingInspection
import com.android.tools.idea.lint.inspections.AndroidLintStringShouldBeIntInspection
import com.android.tools.idea.lint.inspections.AndroidLintSuspiciousImportInspection
import com.android.tools.idea.lint.inspections.AndroidLintSwitchIntDefInspection
import com.android.tools.idea.lint.inspections.AndroidLintTextFieldsInspection
import com.android.tools.idea.lint.inspections.AndroidLintTypographyDashesInspection
import com.android.tools.idea.lint.inspections.AndroidLintTypographyQuotesInspection
import com.android.tools.idea.lint.inspections.AndroidLintTyposInspection
import com.android.tools.idea.lint.inspections.AndroidLintUnprotectedSMSBroadcastReceiverInspection
import com.android.tools.idea.lint.inspections.AndroidLintUnsupportedChromeOsCameraSystemFeatureInspection
import com.android.tools.idea.lint.inspections.AndroidLintUnsupportedChromeOsHardwareInspection
import com.android.tools.idea.lint.inspections.AndroidLintUnusedAttributeInspection
import com.android.tools.idea.lint.inspections.AndroidLintUnusedResourcesInspection
import com.android.tools.idea.lint.inspections.AndroidLintUseCheckPermissionInspection
import com.android.tools.idea.lint.inspections.AndroidLintUselessLeafInspection
import com.android.tools.idea.lint.inspections.AndroidLintUselessParentInspection
import com.android.tools.idea.lint.inspections.AndroidLintValidActionsXmlInspection
import com.android.tools.idea.lint.inspections.AndroidLintWakelockTimeoutInspection
import com.android.tools.idea.lint.inspections.AndroidLintWearStandaloneAppFlagInspection
import com.android.tools.idea.lint.inspections.AndroidLintWifiManagerLeakInspection
import com.android.tools.idea.lint.inspections.AndroidLintWrongCallInspection
import com.android.tools.idea.lint.inspections.AndroidLintWrongCaseInspection
import com.android.tools.idea.lint.inspections.AndroidLintWrongViewCastInspection
import com.android.tools.idea.lint.intentions.AndroidAddStringResourceQuickFix
import com.android.tools.idea.lint.quickFixes.AddTargetVersionCheckQuickFix
import com.android.tools.idea.lint.quickFixes.ConvertToDpQuickFix
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.model.TestAndroidModel
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId
import com.android.tools.idea.projectsystem.TestProjectSystem
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.IdeComponents
import com.android.tools.idea.testing.getIntentionAction
import com.android.tools.lint.checks.HardcodedValuesDetector
import com.android.tools.lint.checks.IconDetector
import com.android.tools.lint.checks.TextViewDetector
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.client.api.LintDriver
import com.android.tools.lint.client.api.LintRequest
import com.android.tools.lint.detector.api.ApiConstraint
import com.android.tools.lint.detector.api.Desugaring
import com.android.tools.lint.detector.api.ExtensionSdk
import com.android.tools.lint.detector.api.Severity
import com.android.utils.CharSequences
import com.google.common.collect.ImmutableMap
import com.google.common.collect.Lists
import com.google.common.collect.Sets
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.LintIssueId.LintSeverity
import com.google.wireless.android.sdk.stats.LintSession.AnalysisType
import com.intellij.analysis.AnalysisScope
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass.IntentionsInfo
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.CommonProblemDescriptor
import com.intellij.codeInspection.GlobalInspectionTool
import com.intellij.codeInspection.QuickFix
import com.intellij.codeInspection.reference.RefEntity
import com.intellij.codeInspection.ui.util.SynchronizedBidiMultiMap
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.impl.ImaginaryEditor
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidRootUtil
import org.jetbrains.android.intentions.AndroidExtractColorAction
import org.jetbrains.android.intentions.AndroidExtractDimensionAction
import org.jetbrains.android.sdk.AndroidPlatform
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.annotations.NonNls
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.stream.Collectors

class AndroidLintTest : AndroidTestCase() {
  init {
    LintClient.clientName = LintClient.CLIENT_UNIT_TESTS
  }

  public override fun setUp() {
    super.setUp()
    val analyticsSettings = AnalyticsSettingsData()
    analyticsSettings.optedIn = false
    setInstanceForTest(analyticsSettings)
    AndroidLintInspectionBase.setRegisterDynamicToolsFromTests(false)
    myFixture.allowTreeAccessForAllFiles()
    myFixture.testDataPath = TestDataPaths.TEST_DATA_ROOT
  }

  override fun configureAdditionalModules(
    projectBuilder: TestFixtureBuilder<IdeaProjectTestFixture>,
    modules: List<MyAdditionalModuleData>
  ) {
    if ("testImlFileOutsideContentRoot" == name) {
      addModuleWithAndroidFacet(projectBuilder, modules, "module1", AndroidProjectTypes.PROJECT_TYPE_LIBRARY)
      addModuleWithAndroidFacet(projectBuilder, modules, "module2", AndroidProjectTypes.PROJECT_TYPE_LIBRARY)
    }
    else if ("testAppCompatMethod" == name || "testExtendAppCompatWidgets" == name) {
      addModuleWithAndroidFacet(projectBuilder, modules, "appcompat", AndroidProjectTypes.PROJECT_TYPE_APP)
    }
    else if ("testAddSdkIntJava" == name || "testAddSdkIntKotlin" == name || name.startsWith("testPartialResultsGlobalAnalysis")) {
      addModuleWithAndroidFacet(projectBuilder, modules, "module1", AndroidProjectTypes.PROJECT_TYPE_LIBRARY)
    }
  }

  fun testHardcodedQuickfix() {
    doTestHardcodedQuickfix()
  }

  fun testHardcodedQuickfix1() {
    doTestHardcodedQuickfix()
  }

  private fun getAvailableFixes(): List<IntentionAction> {
    val intentions = IntentionsInfo()
    ShowIntentionsPass.getActionsToShow(myFixture.editor, myFixture.file, intentions, -1)
    val actions: MutableList<IntentionAction> = Lists.newArrayList()
    for (descriptor in intentions.inspectionFixesToShow) {
      actions.add(descriptor.action)
    }
    return actions
  }

  private fun listAvailableFixes(): String {
    val intentions = IntentionsInfo()
    ShowIntentionsPass.getActionsToShow(myFixture.editor, myFixture.file, intentions, -1)
    val sb = StringBuilder()
    for (action in getAvailableFixes()) {
      sb.append(action.text).append("\n")
    }
    return sb.toString()
  }

  fun testExtraText() {
    deleteManifest()
    doTestHighlighting(AndroidLintExtraTextInspection(), "AndroidManifest.xml", "xml")
  }

  fun testHardcodedString() {
    doTestHighlighting(AndroidLintHardcodedTextInspection(), "/res/layout/layout.xml", "xml")
    // Make sure we only have the extract quickfix and the suppress quickfix: not the disable inspection fix, and
    // the edit inspection settings quickfix (they are suppressed in AndroidLintExternalAnnotator)
    assertEquals("" +
                 "Extract string resource\n" +
                 "Suppress: Add tools:ignore=\"HardcodedText\" attribute\n",
                 listAvailableFixes())
  }

  private fun doTestHardcodedQuickfix() {
    val copyTo = if (false) "AndroidManifest.xml" else "/res/layout/layout.xml"
    doTestHighlighting(AndroidLintHardcodedTextInspection(), copyTo, "xml")
    val action = myFixture.getIntentionAction(
      AndroidAddStringResourceQuickFix::class.java, AndroidBundle.message("add.string.resource.intention.text"))
    assertNotNull(action)
    assertTrue(action!!.isAvailable(myFixture.project, myFixture.editor, myFixture.file))
    WriteCommandAction.runWriteCommandAction(myFixture.project
    ) {
      action.invokeIntention(myFixture.project, myFixture.editor,
                             myFixture.file, "hello")
    }
    myFixture.checkResultByFile(BASE_PATH + getTestName(true) + "_after.xml")
  }

  fun testContentDescription() { // Also tests analytics for single file (batch) analysis
    val scheduler = VirtualTimeScheduler()
    val usageTracker = TestUsageTracker(scheduler)
    setWriterForTest(usageTracker)
    try {
      // Carefully chosen seed such that logSession()'s first *two* random results
      // will be in the expected range to pick it for submission (nextDouble() < 0.01)
      // since we want the data to be logged with the usage tracker such that we can
      // inspect the various fields of the usage data
      val lint = LintIdeSupport.Companion.get() as AndroidLintIdeSupport
      lint.random.setSeed(5356)

      doTestWithFix(AndroidLintContentDescriptionInspection(),
                    "Set contentDescription",
                    "/res/layout/layout.xml", "xml")
      val loggedLintSessions = usageTracker.usages.stream()
        .filter { usage: LoggedUsage -> usage.studioEvent.kind == AndroidStudioEvent.EventKind.LINT_SESSION }
        .collect(Collectors.toList())
      if (!AnalyticsSettings.optedIn) {
        assertThat(loggedLintSessions).isEmpty()
        return
      }
      assertThat(loggedLintSessions).hasSize(2)
      val session = loggedLintSessions[0]!!.studioEvent.lintSession
      assertThat(session.analysisType).isEqualTo(AnalysisType.IDE_FILE)
      val list = session.issueIdsList
      assertThat(list).hasSize(1)
      val issue1 = list[0]
      assertThat(issue1!!.issueId).isEqualTo("ContentDescription")
      assertThat(issue1.count).isEqualTo(1)
      assertThat(issue1.severity).isEqualTo(LintSeverity.DEFAULT_SEVERITY)
      val performance = session.lintPerformance
      assertThat(performance.fileCount).isEqualTo(1)
    } finally {
      usageTracker.close()
      cleanAfterTesting()
    }
  }

  fun testContentDescription2() {
    val scheduler = VirtualTimeScheduler()
    val usageTracker = TestUsageTracker(scheduler)
    setWriterForTest(usageTracker)
    try {
      val source =
        """
        package test.pkg;
        public class MyActivity {
           public static class Inner extends android.app.Activity {
           };
        }
        """.trimIndent()
      val mainFile = myFixture.addFileToProject("/src/test/pkg/MyActivity.java", source)

      val lint = LintIdeSupport.Companion.get() as AndroidLintIdeSupport
      lint.random.setSeed(0)
      val result = LintEditorResult(myModule, mainFile.virtualFile, source, setOf(HardcodedValuesDetector.ISSUE))
      val data = LintProblemData(HardcodedValuesDetector.ISSUE, "Sample issue", TextRange.EMPTY_RANGE, Severity.WARNING, null)
      (result.problems as MutableList).add(data)
      val client = lint.createEditorClient(result)
      val driver = LintDriver(lint.getIssueRegistry(), client, LintRequest(client, emptyList()))
      val rolls = 100000
      for (i in 0..rolls) {
        lint.logSession(driver, result)
      }
      val loggedLintSessions = usageTracker.usages.stream()
        .filter { usage: LoggedUsage -> usage.studioEvent.kind == AndroidStudioEvent.EventKind.LINT_SESSION }
        .collect(Collectors.toList())

      // Make sure we're submitting around 1% of reports.
      // This test is not flaky because we're using a fixed seed to the random generator!
      val percentage = loggedLintSessions.size * 100.0 / rolls
      val expectedPercentage = if (AnalyticsSettings.optedIn) 1.047 else 0.0
      assertEquals("Unexpected percentage of reports submitted", expectedPercentage, percentage, 0.001)
    } finally {
      usageTracker.close()
      cleanAfterTesting()
    }
  }

  fun testContentDescription1() {
    doTestNoFix(AndroidLintContentDescriptionInspection(),
                "/res/layout/layout.xml", "xml")
    // Also test the other lint infrastructure, where we produce an error report instead of overlaying
    // XML-like tags for error ranges into an existing XML doc (which makes it syntactically invalid)
    myFixture.checkLint(myFixture.file, AndroidLintContentDescriptionInspection(), "TODO|", """
      Warning: Empty `contentDescription` attribute on image
          android:contentDescription="TODO"/>
          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          Fix: Suppress: Add tools:ignore="ContentDescription" attribute
    """.trimIndent())
  }

  fun PsiFile.findCaretOffset(caret: String): Int {
    val delta = caret.indexOf("|")
    if (delta == -1) AndroidGradleTestCase.fail("${name} does not contain caret marker, |")
    val context = caret.substring(0, delta) + caret.substring(delta + 1)
    val index = text.indexOf(context)
    if (index == -1) AndroidGradleTestCase.fail("${name} does not contain $context")
    return index + delta
  }

  fun JavaCodeInsightTestFixture.checkLint(psiFile: PsiFile, inspection: AndroidLintInspectionBase, caret: String, expected: String) {
    AndroidLintInspectionBase.setRegisterDynamicToolsFromTests(false)
    enableInspections(inspection)
    val fileText = psiFile.text
    val sb = StringBuilder()
    val target = psiFile.findCaretOffset(caret)
    val highlights = doHighlighting(HighlightSeverity.WARNING).asSequence().sortedBy { it.startOffset }
    for (highlight in highlights) {
      val startIndex = highlight.startOffset
      val endOffset = highlight.endOffset
      if (target < startIndex || target > endOffset) {
        continue
      }
      val description = highlight.description
      val severity = highlight.severity
      sb.append(severity.name.lowercase(Locale.ROOT).capitalize()).append(": ")
      sb.append(description).append("\n")

      val lineStart = fileText.lastIndexOf("\n", startIndex).let { if (it == -1) 0 else it + 1 }
      val lineEnd = fileText.indexOf("\n", startIndex).let { if (it == -1) fileText.length else it }
      sb.append(fileText.substring(lineStart, lineEnd)).append("\n")
      val rangeEnd = if (lineEnd < endOffset) lineEnd else endOffset
      for (i in lineStart until startIndex) sb.append(" ")
      for (i in startIndex until rangeEnd) sb.append("~")
      sb.append("\n")

      for (pair in highlight.quickFixActionRanges) {
        val action = pair.first.action
        sb.append("    ")
        if (action.isAvailable(project, editor, psiFile)) {
          sb.append("Fix: ")
          sb.append(action.text)
        }
        else {
          sb.append("Disabled Fix: ")
          sb.append(action.text)
        }
        sb.append("\n")
      }
    }

    if (sb.isEmpty()) {
      sb.append("No warnings.")
    }

    AndroidGradleTestCase.assertEquals(expected.trimIndent().trim(), sb.toString().trimIndent().trim())
  }

  fun testAdapterViewChildren() {
    doTestNoFix(AndroidLintAdapterViewChildrenInspection(),
                "/res/layout/layout.xml", "xml")
  }

  fun testScrollViewChildren() {
    doTestNoFix(
      AndroidLintScrollViewCountInspection(),
      "/res/layout/layout.xml", "xml")
  }

  fun testMissingPrefix() {
    doTestWithFix(
      AndroidLintMissingPrefixInspection(),
      AndroidLintBundle.message("android.lint.fix.add.android.prefix"),
      "/res/layout/layout.xml", "xml")
  }

  fun testMissingPrefix1() {
    doTestWithFix(
      AndroidLintMissingPrefixInspection(),
      AndroidLintBundle.message("android.lint.fix.add.android.prefix"),
      "/res/layout/layout.xml", "xml")
  }

  fun testMissingPrefix2() { // lint.xml which disables the missing prefix
    myFixture.copyFileToProject("$globalTestDir/lint.xml", "lint.xml")
    doTestHighlighting(AndroidLintMissingPrefixInspection(), "/res/layout/layout.xml", "xml")
  }

  fun testMissingPrefix3() { // lint.xml which changes the severity to warning (is normally error)
    myFixture.copyFileToProject("$globalTestDir/lint.xml", "lint.xml")
    doTestHighlighting(AndroidLintMissingPrefixInspection(), "/res/layout/layout.xml", "xml")
  }

  fun testMissingPrefix4() { // lint.xml which suppresses this specific error path
    myFixture.copyFileToProject("$globalTestDir/lint.xml", "lint.xml")
    doTestHighlighting(AndroidLintMissingPrefixInspection(), "/res/layout/layout.xml", "xml")
  }

  fun testActions() { // Regression test for issue 79120601
    doTestNoFix(AndroidLintValidActionsXmlInspection(),
                "/res/xml/actions.xml", "xml")
  }

  fun testDuplicatedIds() {
    doTestNoFix(AndroidLintDuplicateIdsInspection(),
                "/res/layout/layout.xml", "xml")
  }

  fun testParcelCreator() {
    doTestNoFix(
      AndroidLintParcelCreatorInspection(),
      "/src/test/pkg/ParcelableDemo.java", "java")
  }

  fun testAuthString() {
    doTestNoFix(AndroidLintAuthLeakInspection(),
                "/src/test/pkg/AuthDemo.java", "java")
  }

  fun testColors() {
    addColorRes()
    addColorInt()
    doTestNoFix(
      AndroidLintResourceAsColorInspection(),
      "/src/test/pkg/Colors.kt", "kt")
  }

  fun testInefficientWeight() {
    doTestWithFix(
      AndroidLintInefficientWeightInspection(),
      AndroidLintBundle.message("android.lint.fix.replace.with.zero.dp"),
      "/res/layout/layout.xml", "xml")
  }

  fun testBaselineWeights() {
    doTestWithFix(AndroidLintDisableBaselineAlignmentInspection(),
                  "Set baselineAligned=\"false\"",
                  "/res/layout/layout.xml", "xml")
  }

  fun testObsoleteLayoutParams() {
    doTestWithFix(
      AndroidLintObsoleteLayoutParamInspection(),
      AndroidLintBundle.message("android.lint.fix.remove.attribute"),
      "/res/layout/layout.xml", "xml")
  }

  fun testConvertToDp() {
    doTestWithFix(
      AndroidLintPxUsageInspection(),
      AndroidLintBundle.message("android.lint.fix.convert.to.dp"),
      "/res/layout/layout.xml", "xml")
  }

  fun testConvertToDp1() {
    doTestWithFix(
      AndroidLintPxUsageInspection(),
      AndroidLintBundle.message("android.lint.fix.convert.to.dp"),
      "/res/values/convertToDp.xml", "xml")
  }

  fun testScrollViewSize() {
    doTestWithFix(
      AndroidLintScrollViewSizeInspection(),
      AndroidLintBundle.message("android.lint.fix.set.to.wrap.content"),
      "/res/layout/layout.xml", "xml")
  }

  fun testUnusedAttribute() {
    doTestWithFix(AndroidLintUnusedAttributeInspection(),
                  "Suppress with tools:targetApi attribute",
                  "/res/layout/layout.xml", "xml")
  }

  fun testSuppressInitJava() {
    // Regression test for https://issuetracker.google.com/151164628
    doTestWithFix(AndroidLintSdCardPathInspection(),
                  "Suppress SdCardPath with an annotation",
                  "/src/p1/p2/Foo.java", "java")
  }

  fun testSuppressInit() {
    // Regression test for https://issuetracker.google.com/151164628 (Kotlin)
    doTestWithFix(AndroidLintClickableViewAccessibilityInspection(),
                  "Suppress ClickableViewAccessibility with an annotation",
                  "/src/p1/p2/suppressInit.kt", "kt")
  }

  fun testSuppressImportJava() {
    // Regression test for https://issuetracker.google.com/216663026 (Java)
    doTestWithFix(AndroidLintSuspiciousImportInspection(),
                  "Suppress SuspiciousImport with a comment",
                  "/src/p1/p2/SuppressImportJava.java", "java")
  }

  fun testSuppressImportJavaCombine() {
    // Like testSuppressImportJava, but here there is already an existing //noinspection
    // comment on the line; verifies that we simply add the id to the list, not doubling
    // up comments etc.
    doTestWithFix(AndroidLintSuspiciousImportInspection(),
                  "Suppress SuspiciousImport with a comment",
                  "/src/p1/p2/SuppressImportJava.java", "java")
  }

  fun testSuppressImportKotlin() {
    // Regression test for https://issuetracker.google.com/216663026 (Kotlin)
    doTestWithFix(AndroidLintSuspiciousImportInspection(),
                  "Suppress SuspiciousImport with a comment",
                  "/src/p1/p2/SuppressImportKotlin.kt", "kt")
  }

  fun testExportedService() {
    deleteManifest()
    doTestWithFix(AndroidLintExportedServiceInspection(),
                  "Set permission",
                  "AndroidManifest.xml", "xml")
  }

  fun testExportedContentProvider() {
    deleteManifest()
    doTestWithFix(AndroidLintExportedContentProviderInspection(),
                  "Set exported=\"false\"", "AndroidManifest.xml", "xml")
  }

  fun testExportedReceiver() {
    deleteManifest()
    doTestWithFix(AndroidLintExportedReceiverInspection(),
                  "Set permission", "AndroidManifest.xml", "xml")
  }

  fun testEditText() {
    doTestWithFix(
      AndroidLintTextFieldsInspection(),
      "Set inputType",
      "/res/layout/layout.xml", "xml")
  }

  fun testInvalidPermission() {
    deleteManifest()
    doTestWithFix(
      AndroidLintInvalidPermissionInspection(),
      AndroidLintBundle.message("android.lint.fix.remove.attribute"),
      "AndroidManifest.xml", "xml")
  }

  fun testMissingPermissionJava() {
    myFixture.copyFileToProject("$globalTestDir/AndroidManifest.xml", "AndroidManifest.xml")
    doTestWithFix(
      AndroidLintMissingPermissionInspection(),
      "Add permission check",
      "/src/p1/p2/LocationTestJava.java", "java")
  }

  fun testMissingPermissionKotlin() {
    myFixture.copyFileToProject("$globalTestDir/AndroidManifest.xml", "AndroidManifest.xml")
    doTestWithFix(
      AndroidLintMissingPermissionInspection(),
      "Add permission check",
      "/src/p1/p2/LocationTest.kt", "kt")
  }

  fun testNotificationPermission() {
    val manifest = myFixture.addFileToProject("AndroidManifest.xml", """
      <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="p1.p2">
          <uses-sdk android:minSdkVersion="14" android:targetSdkVersion="33" />
          <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
      </manifest>
      """.trimIndent())
    doTestWithFix(
      AndroidLintNotificationPermissionInspection(),
      "Add Permission POST_NOTIFICATIONS",
      "/src/test/pkg/notificationPermission.kt", "kt")
    val updatedManifest = manifest.text
    assertEquals("""
      <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="p1.p2">
          <uses-sdk android:minSdkVersion="14" android:targetSdkVersion="33" />
          <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
          <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
      </manifest>
    """.trimIndent(), updatedManifest)
  }

  fun testUselessLeaf() {
    doTestWithFix(AndroidLintUselessLeafInspection(),
                  AndroidLintBundle.message("android.lint.fix.remove.unnecessary.view"),
                  "/res/layout/layout.xml", "xml")
  }

  fun testUselessParent() {
    doTestNoFix(AndroidLintUselessParentInspection(),
                "/res/layout/layout.xml", "xml")
  }

  fun testTypographyDashes() {
    doTestWithFix(
      AndroidLintTypographyDashesInspection(),
      "Replace with –",
      "/res/values/typography.xml", "xml")
  }

  fun testTypographyQuotes() { // Re-enable typography quotes, normally off
    myFixture.copyFileToProject("$globalTestDir/lint.xml", "lint.xml")
    doTestWithFix(AndroidLintTypographyQuotesInspection(),
                  "Replace with ‘aba’",
                  "/res/values/typography.xml", "xml")
  }

  fun testGridLayoutAttribute() {
    doTestWithFix(AndroidLintGridLayoutInspection(),
                  "Update to myns:layout_column",
                  "/res/layout/grid_layout.xml", "xml")
  }

  fun testGridLayoutAttributeMissing() {
    doTestWithFix(AndroidLintGridLayoutInspection(),
                  "Update to app:layout_column",
                  "/res/layout/grid_layout.xml", "xml")
  }

  fun testAlwaysShowAction() {
    doTestWithFix(AndroidLintAlwaysShowActionInspection(),
                  "Replace with ifRoom", "/res/menu/menu.xml", "xml")
  }

  fun testPaddingStartQuickFix() {
    deleteManifest()
    myFixture.copyFileToProject("$globalTestDir/AndroidManifest.xml", "AndroidManifest.xml")
    doTestWithFix(
      AndroidLintRtlCompatInspection(),
      "Set paddingLeft=\"12sp\"", "/res/layout/layout.xml", "xml")
  }

  fun testAppCompatMethod() {
    val modules = ModuleManager.getInstance(project).modules
    for (module in modules) {
      if (module !== myModule && AndroidFacet.getInstance(module) != null) {
        deleteManifest(module)
      }
    }
    val testProjectSystem = TestProjectSystem(project)
    testProjectSystem.useInTests()
    testProjectSystem.addDependency(GoogleMavenArtifactId.APP_COMPAT_V7, myFixture.module,
                                    GradleVersion.parse("+"))
    myFixture.copyFileToProject("$globalTestDir/AppCompatActivity.java.txt", "src/android/support/v7/app/AppCompatActivity.java")
    myFixture.copyFileToProject("$globalTestDir/ActionMode.java.txt", "src/android/support/v7/view/ActionMode.java")
    doTestWithFix(AndroidLintAppCompatMethodInspection(),
                  "Replace with getSupportActionBar()", "/src/test/pkg/AppCompatTest.java", "java")
  }

  fun testEditEncoding() {
    doTestWithFix(AndroidLintEnforceUTF8Inspection(),
                  "Replace with utf-8", "/res/layout/layout.xml", "xml")
  }

  /* Inspection disabled; these tests make network connection to MavenCentral and can change every time there
     is a new version available (which makes for unstable tests)

  public void testNewerAvailable() throws Exception {
    GradleDetector.REMOTE_VERSION.setEnabledByDefault(true);
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintNewerVersionAvailableInspection(),
                  "Update to 17.0.0", "build.gradle", "gradle");
  }
  */
  fun testGradlePlus() {
    val cache: Map<String, String> = ImmutableMap.of(
      "master-index.xml",
      "<metadata>\n" +
      "  <com.android.support/>\n" +
      "</metadata>",
      "com/android/support/group-index.xml",
      "<com.android.support>\n" +
      "  <support-v4 versions=\"26.0.2,26.0.2\"/>\n" +
      "  <appcompat-v7 versions=\"18.0.0,19.0.0,19.0.1,19.1.0,20.0.0,21.0.0,21.0.2,22.0.0-alpha1\"/>\n" +
      "</com.android.support>\n")
    val repository = StubGoogleMavenRepository(cache)
    val disposable = Disposer.newDisposable()
    IdeComponents(null, disposable).replaceApplicationService(
      RepositoryUrlManager::class.java,
      RepositoryUrlManager(repository, repository, true, false)
    )
    doTestWithFix(AndroidLintGradleDynamicVersionInspection(),
                  "Replace with specific version", "build.gradle", "gradle")
    Disposer.dispose(disposable)
  }

  fun testGradleDeprecation() {
    doTestWithFix(AndroidLintGradleDeprecatedInspection(),
                  "Replace with com.android.library", "build.gradle", "gradle")
  }

  fun testMissingAppIcon() {
    deleteManifest()
    doTestWithFix(
      AndroidLintMissingApplicationIconInspection(),
      "Set icon", "AndroidManifest.xml", "xml")
  }

  fun testMissingLeanbackSupport() {
    deleteManifest()
    doTestWithFix(
      AndroidLintMissingLeanbackSupportInspection(),
      "Add uses-feature tag", "AndroidManifest.xml", "xml")
  }

  fun testPermissionImpliesHardware() {
    deleteManifest()
    doTestWithFix(
      AndroidLintPermissionImpliesUnsupportedHardwareInspection(),
      "Add uses-feature tag", "AndroidManifest.xml", "xml")
  }

  fun testMissingTvBanner() {
    deleteManifest()
    doTestWithFix(
      AndroidLintMissingTvBannerInspection(),
      "Set banner", "AndroidManifest.xml", "xml")
  }

  fun testInvalidUsesTagAttribute() {
    doTestWithFix(
      AndroidLintInvalidUsesTagAttributeInspection(),
      "Replace with \"media\"",
      "res/xml/automotive_app_desc.xml", "xml")
  }

  fun testVectorScientificNotation() {
    doTestWithFix(
      AndroidLintInvalidVectorPathInspection(),
      "Replace with 67", "res/drawable/vector.xml", "xml")
  }

  fun testUnsupportedChromeOsHardware() {
    deleteManifest()
    doTestWithFix(AndroidLintUnsupportedChromeOsHardwareInspection(),
                  "Set required=\"false\"", "AndroidManifest.xml", "xml")
  }

  fun testPermissionImpliesChromeOsHardware() {
    deleteManifest()
    doTestWithFix(
      AndroidLintPermissionImpliesUnsupportedChromeOsHardwareInspection(),
      "Add uses-feature tag", "AndroidManifest.xml", "xml")
  }

  fun testInvalidOrientationSetOnActivity() {
    deleteManifest()
    doTestWithFix(
      AndroidLintLockedOrientationActivityInspection(),
      "Set screenOrientation=\"fullSensor\"", "AndroidManifest.xml", "xml")
  }

  fun testNonResizeableActivity() {
    deleteManifest()
    doTestWithFix(
      AndroidLintNonResizeableActivityInspection(),
      "Set resizeableActivity=\"true\"", "AndroidManifest.xml", "xml")
  }

  fun testActivityLockedOrientationSource() {
    doTestWithFix(
      AndroidLintSourceLockedOrientationActivityInspection(),
      "Set the orientation to SCREEN_ORIENTATION_UNSPECIFIED", "/src/test/pkg/TestActivity.java", "java")
  }

  fun testUnsupportedChromeOsCameraSystemFeature() {
    doTestWithFix(AndroidLintUnsupportedChromeOsCameraSystemFeatureInspection(),
                  "Switch to look for FEATURE_CAMERA_ANY", "/src/test/pkg/TestActivity.java", "java")
  }

  /* Disabled: The mipmap check now only warns about mipmap usage in Gradle projects that use
   * density filtering. Re-enable this if we broaden the mipmap check, or if we update the AndroidLintTest
   * to also check Gradle projects.
   * File was converted to Kotlin while tests were commented out, as a result, these tests are still in Java.
  public void testMipmap() throws Exception {
    deleteManifest();
    myFixture.copyFileToProject(getGlobalTestDir() + "/R.java", "/src/p1/p2/R.java");
    myFixture.copyFileToProject(getGlobalTestDir() + "/MyCode.java", "/src/p1/p2/MyCode.java");
    myFixture.copyFileToProject(getGlobalTestDir() + "/icon.png", "/res/drawable-mdpi/icon.png");
    myFixture.copyFileToProject(getGlobalTestDir() + "/icon.png", "/res/drawable-hdpi/icon.png");
    myFixture.copyFileToProject(getGlobalTestDir() + "/icon.png", "/res/drawable-xhdpi/icon.png");

    // Apply quickfix and check that the manifest file is updated
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintMipmapIconsInspection(), "Convert @drawable/icon to @mipmap/icon",
                  "AndroidManifest.xml", "xml");

    // Make sure files were moved
    assertNotNull(myFixture.findFileInTempDir("res/mipmap-mdpi/icon.png"));
    assertNotNull(myFixture.findFileInTempDir("res/mipmap-hdpi/icon.png"));
    assertNotNull(myFixture.findFileInTempDir("res/mipmap-xhdpi/icon.png"));

    // Make sure code references (in addition to Manifest XML file reference checked above) have been updated
    myFixture.checkResultByFile("src/p1/p2/MyCode.java", getGlobalTestDir() + "/MyCode_after.java", true);

    // The R.java file should not have been edited:
    myFixture.checkResultByFile("src/p1/p2/R.java", getGlobalTestDir() + "/R.java", true);
  }
  */

  fun testRemoveByteOrderMarks() {
    doTestWithFix(AndroidLintByteOrderMarkInspection(),
                  "Remove byte order marks", "/res/layout/layout.xml", "xml")
  }

  fun testBomManifest() {
    doTestHighlighting(AndroidLintByteOrderMarkInspection(), "AndroidManifest.xml", "xml")
  }

  fun testBomStrings() {
    doTestHighlighting(AndroidLintByteOrderMarkInspection(), "/res/values/strings.xml", "xml")
  }

  fun testBomClass() {
    doTestHighlighting(AndroidLintByteOrderMarkInspection(), "/src/test/pkg/MyTest.java", "java")
  }

  fun testCommitToApply() {
    deleteManifest()
    // Need to use targetSdkVersion 9
    myFixture.copyFileToProject("$globalTestDir/AndroidManifest.xml", "AndroidManifest.xml")
    doTestWithFix(AndroidLintApplySharedPrefInspection(),
                  "Replace commit() with apply()", "/src/test/pkg/CommitToApply.java", "java")
  }

  fun testMissingIntDefSwitch() {
    addIntDef()
    doTestWithFix(
      AndroidLintSwitchIntDefInspection(),
      "Add Missing @IntDef Constants", "/src/p1/p2/MissingIntDefSwitch.java", "java")
  }

  fun testMissingIntDefSwitchKotlin() {
    addIntDef()
    doTestWithFix(
      AndroidLintSwitchIntDefInspection(),
      "Add Missing @IntDef Constants", "/src/p1/p2/MissingIntDefSwitch.kt", "kt")
  }

  fun testAddKeepJava() {
    addKeep()
    doTestWithFix(AndroidLintAnimatorKeepInspection(),
                  "Annotate with @Keep", "/src/p1/p2/AnimatorTest.java", "java")
  }

  fun testAddKeepKotlin() {
    addKeep()
    doTestWithFix(AndroidLintAnimatorKeepInspection(),
                  "Annotate with @Keep", "/src/p1/p2/AnimatorTest.kt", "kt")
  }

  fun testJavaCheckResultTest2() {
    addCheckResult()
    doTestWithFix(AndroidLintUseCheckPermissionInspection(),
                  "Call enforceFooPermission instead", "/src/p1/p2/JavaCheckResultTest2.java", "java")
  }

  fun testKotlinCheckResultTest2() {
    addCheckResult()
    doTestWithFix(AndroidLintUseCheckPermissionInspection(),
                  "Call enforceFooPermission instead", "/src/p1/p2/KotlinCheckResultTest2.kt", "kt")
  }

  fun testJavaRemoveObsoleteSdkCheck() {
    deleteManifest()
    addMinSdkManifest(19)
    addRequiresApi()
    doTestWithFix(
      AndroidLintObsoleteSdkIntInspection(),
      "Unwrap 'if' statement", "/src/p1/p2/JavaRemoveObsoleteSdkCheckTest.java", "java")
  }

  fun testKotlinRemoveObsoleteSdkCheck() {
    deleteManifest()
    addMinSdkManifest(19)
    addRequiresApi()
    doTestWithFix(
      AndroidLintObsoleteSdkIntInspection(),
      "Remove obsolete SDK version check", "/src/p1/p2/KotlinRemoveObsoleteSdkCheckTest.kt", "kt")
  }

  fun testKotlinRemoveObsoleteSdkCheck2() {
    // Unlike previous test, checks that the quickfix keeps the else clause instead if the check is always false instead
    // of always true
    deleteManifest()
    addMinSdkManifest(19)
    addRequiresApi()
    doTestWithFix(
      AndroidLintObsoleteSdkIntInspection(),
      "Remove obsolete SDK version check", "/src/p1/p2/KotlinRemoveObsoleteSdkCheckTest2.kt", "kt")
  }

  fun testKotlinRemoveObsoleteSdkCheck3() {
    // Regression test for b/191289731
    deleteManifest()
    addMinSdkManifest(21)
    addRequiresApi()
    doTestWithFix(
      AndroidLintObsoleteSdkIntInspection(),
      "Remove obsolete SDK version check", "/src/p1/p2/kotlinRemoveObsoleteSdkCheck3.kt", "kt")
  }

  fun testIncludeParams() {
    doTestWithFix(
      AndroidLintIncludeLayoutParamInspection(),
      "Set layout_height", "/res/layout/layout.xml", "xml")
  }

  fun testInnerclassSeparator() {
    deleteManifest()
    myFixture.addFileToProject("/src/test/pkg/MyActivity.java",
                               """
                               package test.pkg;
                               public class MyActivity {
                                   public static class Inner extends android.app.Activity {
                                   };
                               }
                               """.trimIndent())
    doTestWithFix(
      AndroidLintInnerclassSeparatorInspection(),
      "Replace with .MyActivity\$Inner", "AndroidManifest.xml", "xml")
  }

  fun testMenuTitle() {
    deleteManifest()
    // Need to use targetSdkVersion 11
    myFixture.copyFileToProject("$globalTestDir/AndroidManifest.xml", "AndroidManifest.xml")
    doTestWithFix(
      AndroidLintMenuTitleInspection(),
      "Set title", "/res/menu/menu.xml", "xml")
  }

  fun testFragmentIds() {
    doTestWithFix(
      AndroidLintMissingIdInspection(),
      "Set id", "/res/layout/layout.xml", "xml")
  }

  fun testOldTargetApi() {
    deleteManifest()
    val expectedTarget = Integer.toString(
      AndroidLintIdeClient(project, LintIgnoredResult()).highestKnownApiLevel)
    doTestWithFix(
      AndroidLintOldTargetApiInspection(),
      "Update targetSdkVersion to $expectedTarget", "AndroidManifest.xml", "xml")
  }

  /*
  public void testOldTargetApiGradle() throws Exception {
    // Doesn't work in incremental mode because this issue is also used for manifest files;
    // we don't well support implementations pointing to different detectors for each file type
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintOldTargetApiInspection(),
                  "Change to 17.0.0", "build.gradle", "gradle");
  }
  */
  fun testReferenceTypes() {
    doTestWithFix(
      AndroidLintReferenceTypeInspection(),
      "Replace with @string/", "/res/values/strings.xml", "xml")
  }

  fun testSelectableText() {
    TextViewDetector.SELECTABLE.setEnabledByDefault(true)
    deleteManifest()
    // Need to use targetSdkVersion 11
    myFixture.copyFileToProject("$globalTestDir/AndroidManifest.xml", "AndroidManifest.xml")
    doTestWithFix(
      AndroidLintSelectableTextInspection(), "Set textIsSelectable=\"true\"",
      "/res/layout/layout.xml", "xml")
  }

  fun testSignatureOrSystem() {
    deleteManifest()
    doTestWithFix(
      AndroidLintSignatureOrSystemPermissionsInspection(),
      "Replace with signature", "AndroidManifest.xml", "xml")
  }

  fun testSp() {
    doTestWithFix(
      AndroidLintSpUsageInspection(),
      "Replace with sp", "/res/values/styles.xml", "xml")
  }

  fun testStringToInt() {
    doTestWithFix(
      AndroidLintStringShouldBeIntInspection(),
      "Replace with integer", "build.gradle", "gradle")
  }

  fun testStringTypos() {
    doTestWithFix(AndroidLintTyposInspection(),
                  "Replace with \"Android\"", "/res/values-nb/strings.xml", "xml")
  }

  // Regression test for http://b.android.com/186465
  fun testStringTyposCDATA() {
    doTestWithFix(AndroidLintTyposInspection(),
                  "Replace with \"Android\"", "/res/values-nb/strings.xml", "xml")
  }

  fun testWrongViewCall() {
    doTestWithFix(AndroidLintWrongCallInspection(),
                  "Replace call with draw()", "/src/test/pkg/WrongViewCall.java", "java")
  }

  fun testWrongCase() {
    doTestWithFix(AndroidLintWrongCaseInspection(),
                  "Replace with merge", "/res/layout/layout.xml", "xml")
  }

  fun testProguard() {
    createManifest()
    val proguardCfgPath = myFixture.copyFileToProject("$globalTestDir/proguard.cfg", "proguard.cfg")
    myFacet.properties.RUN_PROGUARD = true
    myFacet.properties.myProGuardCfgFiles = listOf(proguardCfgPath.url)
    doGlobalInspectionTest(AndroidLintProguardInspection())
  }

  fun testManifestOrder() {
    deleteManifest()
    myFixture.copyFileToProject("$globalTestDir/AndroidManifest.xml", "AndroidManifest.xml")
    doGlobalInspectionTest(AndroidLintManifestOrderInspection())
  }

  fun testViewType() {
    myFixture.copyFileToProject("$globalTestDir/MyActivity.java", "src/p1/p2/MyActivity.java")
    myFixture.copyFileToProject("$globalTestDir/layout.xml", "res/layout/layout.xml")
    doGlobalInspectionTest(AndroidLintWrongViewCastInspection())
  }

  fun testViewTypeStub() { // Regression test for 183136: don't take id references to imply a
    // view type of the referencing type
    myFixture.copyFileToProject("$globalTestDir/stub_inflated_layout.xml", "res/layout/stub_inflated_layout.xml")
    myFixture.copyFileToProject("$globalTestDir/main.xml", "res/layout/main.xml")
    myFixture.copyFileToProject("$globalTestDir/WrongCastActivity.java", "src/p1/p2/WrongCastActivity.java")
    doGlobalInspectionTest(AndroidLintWrongViewCastInspection())
  }

  fun testDuplicateIcons() {
    val file = myFixture.copyFileToProject("$globalTestDir/dup1.png", "res/drawable/dup1.png")
    myFixture.copyFileToProject("$globalTestDir/dup2.png", "res/drawable/dup2.png")
    myFixture.copyFileToProject("$globalTestDir/other.png", "res/drawable/other.png")
    doGlobalInspectionTest(AndroidLintIconDuplicatesInspection())

    // Take on a suppress test: attempt to suppress a binary file and verify that that works:
    // Regression test for https://code.google.com/p/android/issues/detail?id=225703
    val moduleDir = AndroidRootUtil.getMainContentRoot(myFacet)
    assertThat(moduleDir).isNotNull()

    val iconFile = PsiManager.getInstance(project).findFile(file)
    assertThat(iconFile).isNotNull()

    var lintXml = moduleDir!!.findChild("lint.xml")
    assertThat(lintXml).isNull()

    val action = SuppressLintIntentionAction(IconDetector.DUPLICATES_NAMES, iconFile!!)
    action.invoke(project, null, iconFile)
    moduleDir.refresh(false, true)

    lintXml = moduleDir.findChild("lint.xml")
    assertThat(lintXml).isNotNull()
    assertThat(String(lintXml!!.contentsToByteArray(), StandardCharsets.UTF_8)).isEqualTo(
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
      "<lint>\n" +
      "    <issue id=\"IconDuplicates\">\n" +
      "        <ignore path=\"res/drawable/dup1.png\" />\n" +
      "    </issue>\n" +
      "</lint>")
  }

  fun testSuppressingInXml1() {
    doTestNoFix(AndroidLintHardcodedTextInspection(),
                "/res/layout/layout.xml", "xml")
  }

  fun testSuppressingInXml2() {
    doTestNoFix(AndroidLintHardcodedTextInspection(),
                "/res/layout/layout.xml", "xml")
  }

  fun testSuppressingInXml3() {
    createManifest()
    myFixture.copyFileToProject("$globalTestDir/layout.xml", "res/layout/layout.xml")
    doGlobalInspectionTest(AndroidLintHardcodedTextInspection())
  }

  fun testApiCheck1() {
    createManifest()
    myFixture.copyFileToProject("$globalTestDir/MyActivity.java", "src/p1/p2/MyActivity.java")
    doGlobalInspectionTest(AndroidLintNewApiInspection())
  }

  fun testApiCheck1b() { // Check adding a @TargetApi annotation in a Java file to suppress
    createManifest()
    doTestWithFix(
      AndroidLintNewApiInspection(),
      "Add @TargetApi(HONEYCOMB) Annotation",
      "/src/p1/p2/MyActivity.java", "java")
  }

  fun testApiCheck1c() { // Check adding a @SuppressLint annotation in a Java file to suppress
    createManifest()
    doTestWithFix(
      AndroidLintNewApiInspection(),
      "Suppress NewApi with an annotation",
      "/src/p1/p2/MyActivity.java", "java")
  }

  fun testApiCheck1d() { // Check adding a tools:targetApi attribute in an XML file to suppress
    createManifest()
    doTestWithFix(
      AndroidLintNewApiInspection(),
      "Suppress with tools:targetApi attribute",
      "/res/layout/layout.xml", "xml")
  }

  fun testApiCheck1e() { // Check adding a tools:suppress attribute in an XML file to suppress
    createManifest()
    doTestWithFix(
      AndroidLintNewApiInspection(),
      "Suppress: Add tools:ignore=\"NewApi\" attribute",
      "/res/layout/layout.xml", "xml")
  }

  fun testExtensionSuppress() {
    createManifest()
    doTestWithFix(
      AndroidLintNewApiInspection(),
      "Surround with if (SdkExtensions.getExtensionVersion(R)) >= 4) { ... }",
      "/src/androidx/annotation/RequiresExtension.java", "java")
  }

  fun testExtensionSuppressKotlin() {
    createManifest()
    doTestWithFix(
      AndroidLintNewApiInspection(),
      "Surround with if (SdkExtensions.getExtensionVersion(R)) >= 1) { ... }",
      "/src/androidx/annotation/RequiresExtension.kt", "kt")
  }

  fun testExtensionSuppressKotlinOnR() {
    createManifest()
    doTestWithFix(
      AndroidLintNewApiInspection(),
      "Surround with if (SdkExtensions.getExtensionVersion(R)) >= 4) { ... }",
      "/src/androidx/annotation/RequiresExtension.kt", "kt")
  }

  fun testRequiresExtensionKotlin() {
    createManifest()
    doTestWithFix(
      AndroidLintNewApiInspection(),
      "Add @RequiresExtension(extension=R, version=4) Annotation",
      "/src/androidx/annotation/RequiresExtension.kt", "kt")
  }

  fun testRequiresExtensionKotlinSingle() {
    createManifest()
    doTestWithFix(
      AndroidLintNewApiInspection(),
      "Add @RequiresExtension(extension=R, version=4) Annotation",
      "/src/androidx/annotation/RequiresExtension.kt", "kt")
  }

  fun testRequiresExtensionJava() {
    createManifest()
    doTestWithFix(
      AndroidLintNewApiInspection(),
      "Add @RequiresExtension(extension=R, version=4) Annotation",
      "/src/androidx/annotation/RequiresExtension.java", "java")
  }

  fun testRequiresExtensionJavaSingle() {
    createManifest()
    doTestWithFix(
      AndroidLintNewApiInspection(),
      "Add @RequiresExtension(extension=R, version=4) Annotation",
      "/src/androidx/annotation/RequiresExtension.java", "java")
  }

  fun testMissingExtension() {
    createManifest()
    doTestWithFix(
      AndroidLintNewApiInspection(),
      "Add @RequiresExtension(extension=1000000, version=4) Annotation",
      "/src/androidx/annotation/RequiresExtension.kt", "kt")
  }

  fun testApiCheck1f() { // Check adding a version-check conditional in a Java file
    createManifest()
    doTestWithFix(
      AndroidLintNewApiInspection(),
      "Surround with if (VERSION.SDK_INT >= VERSION_CODES.HONEYCOMB) { ... }",
      "/src/p1/p2/MyActivity.java", "java")
  }

  fun testApiCheck1Kotlin() { // Check adding a version-check conditional in a Kotlin file
    createManifest()
    doTestWithFix(
      AndroidLintNewApiInspection(),
      "Surround with if (VERSION.SDK_INT >= VERSION_CODES.HONEYCOMB) { ... }",
      "/src/p1/p2/MyActivity.kt", "kt")
  }

  fun testAddSdkIntJava() {
    // Check adding a version-checking annotation in a Java File
    // This lint check only triggers in a library, so place it there instead of normal src/ location:
    val srcRoot = "/additionalModules/module1/src"
    addChecksSdkIntAtLeast(srcRoot)
    doTestWithFix(AndroidLintAnnotateVersionCheckInspection(),
                  "Annotate with @ChecksSdkIntAtLeast",
                  "$srcRoot/p1/p2/JavaSdkIntTest.java", "java")
  }

  fun testAddSdkIntKotlin() {
    // Like testAddSdkIntJava but for Kotlin
    val srcRoot = "/additionalModules/module1/src"
    addChecksSdkIntAtLeast(srcRoot)
    doTestWithFix(AndroidLintAnnotateVersionCheckInspection(),
                  "Annotate with @ChecksSdkIntAtLeast",
                  "$srcRoot/p1/p2/SdkIntTest.kt", "kt")
  }

  fun testJava8FeaturesWithoutDesugaring() {
    val minSdk = 16
    deleteManifest()
    addMinSdkManifest(minSdk)
    // Set desugaring level to DEFAULT which does not include java 8 desugaring.
    AndroidModel.set(myFacet, TestAndroidModel(minSdkVersion = AndroidVersion(minSdk), desugaringLevel = Desugaring.DEFAULT))

    val highlights = doTestHighlighting(AndroidLintNewApiInspection(), "src/com/example/test/TestActivity.java", "java", true)
    // All Java8 features should be flagged as errors
    val errors = highlights.filter { it.severity == HighlightSeverity.ERROR || it.severity == HighlightSeverity.WARNING }.toList()
    assertThat(errors).hasSize(6)

    val errorDescriptions = errors.map { it.description }
    assertThat(
      errorDescriptions).containsExactly("Call requires API level 24 (current min is 16): `java.util.stream.IntStream#range`",
                                         "Call requires API level 24 (current min is 16): `java.util.stream.IntStream#filter`",
                                         "Method reference requires API level 24 (current min is 16): `isEven::test`",
                                         "Call requires API level 24 (current min is 16): `java.util.stream.IntStream#boxed`",
                                         "Call requires API level 24 (current min is 16): `java.util.stream.Stream#collect`",
                                         "Call requires API level 24 (current min is 16): `java.util.stream.Collectors#toList`")
  }

  fun testJava8FeaturesWithDesugaring() {
    val minSdk = 16
    deleteManifest()
    addMinSdkManifest(minSdk)
    // Explicitly enable full desugaring
    AndroidModel.set(myFacet, TestAndroidModel(minSdkVersion = AndroidVersion(minSdk), desugaringLevel = Desugaring.FULL))

    val highlights = doTestHighlighting(AndroidLintNewApiInspection(), "src/com/example/test/TestActivity.java", "java", true)
    // Java8 features should not be flagged as issues
    val errors = highlights.filter { it.severity == HighlightSeverity.ERROR || it.severity == HighlightSeverity.WARNING }.toList()
    assertThat(errors).hasSize(0)
  }

  fun testImlFileOutsideContentRoot() {
    myFixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML, "additionalModules/module1/" + SdkConstants.FN_ANDROID_MANIFEST_XML)
    myFixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML, "additionalModules/module2/" + SdkConstants.FN_ANDROID_MANIFEST_XML)
    val testDir = BASE_PATH_GLOBAL + "apiCheck1"
    myFixture.copyFileToProject("$testDir/MyActivity.java", "additionalModules/module1/src/p1/p2/MyActivity.java")
    doGlobalInspectionTest(AndroidLintNewApiInspection(), testDir, AnalysisScope(project))
  }

  fun testUnusedResource() {
    // This test checks 3 things.
    // First, it runs the unused resources global inspection and checks that it gets it right (the results are checked
    // against unusedResources/expected.xml).
    //
    // Then, it checks that *all* the quickfixes associated with this use a unique family name. This checks that
    // we don't get into the scenario described in issue 235641, where a *single* action is created to run all 3
    // quickfixes (both adding tools/keep, which is taken as the display name, as well as the removal refactoring).
    //
    // Finally, it actually performs the unused refactoring fix. This verifies that this works without the crash
    // also reported in issue 235641 (where the unused refactoring is invoked under a write lock, which quickfixes
    // normally are, but which is forbidden by the refactoring framework.)
    val file = myFixture.copyFileToProject("$globalTestDir/strings.xml", "res/values/strings.xml")
    myFixture.configureFromExistingVirtualFile(file)
    val map = doGlobalInspectionTest(AndroidLintUnusedResourcesInspection())
    var targetDescriptor: CommonProblemDescriptor? = null
    var targetFix: QuickFix<CommonProblemDescriptor?>? = null

    // Ensure family names are unique; if not quickfixes get collapsed. Set.add only returns true if it wasn't already in the set.
    for (refEntity in map.keys()) {
      for (descriptor in map[refEntity]) {
        val familyNames: MutableSet<String> = Sets.newHashSet()
        val fixes = descriptor.fixes
        if (fixes != null) {
          for (fix in fixes) {
            val name = fix.name
            assertTrue(familyNames.add(fix.familyName))
            if ("Remove Declarations for R.string.unused" == name) {
              targetDescriptor = descriptor
              targetFix = fix
            }
          }
        }
      }
    }
    assertNotNull(targetDescriptor)
    assertNotNull(targetFix)
    // TODO: Consider using CodeInsightTestFixtureImpl#invokeIntention
    targetFix!!.applyFix(project, targetDescriptor!!)
    myFixture.checkResultByFile("$globalTestDir/strings_after.xml")
  }

  fun testPartialResultsGlobalAnalysis1() {
    // Regression test for b/249387643: Partial result maps were not working in
    // IDE batch/global analysis. Detectors can use partial result maps, even in
    // a global analysis. A good example of this is
    // AndroidLintSetAndClearCommunicationDeviceInspection
    // (CommunicationDeviceDetector), which the test runs. The test creates two
    // modules, where both modules contain a problem (they both call
    // setCommunicationDevice, and no module contains a call to
    // clearCommunicationDevice). The test ensures the analysis runs
    // successfully, and both problems are reported.
    // testPartialResultsGlobalAnalysis2 tests a similar scenario where no
    // problems are reported.
    myFixture.addFileToProject(
      "src/p1/p2/MainActivity.java",
      """
      package p1.p2;

      import android.app.Activity;
      import android.os.Bundle;

      import android.media.AudioDeviceInfo;
      import android.media.AudioManager;

      public class MainActivity extends Activity {

        public AudioManager manager;
        public AudioDeviceInfo info;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
          super.onCreate(savedInstanceState);

          manager.setCommunicationDevice(info);
        }
      }""".trimIndent())

    myFixture.addFileToProject("AndroidManifest.xml", manifestContents(32, 32))

    myFixture.addFileToProject(
      "additionalModules/module1/src/p1/p2/LibraryActivity.java",
      """
      package p1.p2;

      import android.app.Activity;
      import android.os.Bundle;

      import android.media.AudioDeviceInfo;
      import android.media.AudioManager;

      public class LibraryActivity extends Activity {

        public AudioManager manager;
        public AudioDeviceInfo info;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
          super.onCreate(savedInstanceState);

          manager.setCommunicationDevice(info);
        }
      }""".trimIndent())

    myFixture.addFileToProject("additionalModules/module1/AndroidManifest.xml", manifestContents(32, 32))

    val inspection = AndroidLintSetAndClearCommunicationDeviceInspection()
    myFixture.enableInspections(inspection)
    doGlobalInspectionTest(inspection, globalTestDir, AnalysisScope(project))
  }

  fun testPartialResultsGlobalAnalysis2() {
    // Same as testPartialResultsGlobalAnalysis1, except we create two modules
    // where one calls setCommunicationDevice and the other calls
    // clearCommunicationDevice. The test checks that no problems are reported,
    // which ensures the partial results from each module are correctly
    // combined.
    myFixture.addFileToProject(
      "src/p1/p2/MainActivity.java",
      """
      package p1.p2;

      import android.app.Activity;
      import android.os.Bundle;

      import android.media.AudioDeviceInfo;
      import android.media.AudioManager;

      public class MainActivity extends Activity {

        public AudioManager manager;
        public AudioDeviceInfo info;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
          super.onCreate(savedInstanceState);

          manager.setCommunicationDevice(info);
        }
      }""".trimIndent())

    myFixture.addFileToProject("AndroidManifest.xml", manifestContents(32, 32))

    myFixture.addFileToProject(
      "additionalModules/module1/src/p1/p2/LibraryActivity.java",
      """
      package p1.p2;

      import android.app.Activity;
      import android.os.Bundle;

      import android.media.AudioDeviceInfo;
      import android.media.AudioManager;

      public class LibraryActivity extends Activity {

        public AudioManager manager;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
          super.onCreate(savedInstanceState);

          manager.clearCommunicationDevice();
        }
      }""".trimIndent())

    myFixture.addFileToProject("additionalModules/module1/AndroidManifest.xml", manifestContents(32, 32))

    val inspection = AndroidLintSetAndClearCommunicationDeviceInspection()
    myFixture.enableInspections(inspection)
    doGlobalInspectionTest(inspection, globalTestDir, AnalysisScope(project))
  }

  fun testMergeObsoleteFolders() { // Force minSdkVersion to v14:
    deleteManifest()
    addMinSdkManifest(14)

    val mainFile = myFixture.copyFileToProject("$globalTestDir/values-strings.xml", "res/values/strings.xml")
    val v8strings = myFixture.copyFileToProject("$globalTestDir/values-v8-strings.xml", "res/values-v8/strings.xml")
    val v10strings = myFixture.copyFileToProject("$globalTestDir/values-v10-strings.xml", "res/values-v10/strings.xml")

    myFixture.copyFileToProject("$globalTestDir/layout-v11-activity_main.xml", "res/layout-v11/activity_main.xml")
    myFixture.copyFileToProject("$globalTestDir/layout-activity_main.xml", "res/layout/activity_main.xml")

    myFixture.configureFromExistingVirtualFile(mainFile)

    val inspection = AndroidLintObsoleteSdkIntInspection()
    val actionLabel = "Merge resources from -v8 and -v10 into values"
    doGlobalInspectionWithFix(inspection, actionLabel)
    myFixture.checkResultByFile("$globalTestDir/values-strings_after.xml")
    // check that the other folders don't exist
    assertFalse(v8strings.isValid)
    assertFalse(v10strings.isValid)
  }

  fun testImpliedTouchscreenHardware() {
    doTestWithFix(
      AndroidLintImpliedTouchscreenHardwareInspection(),
      "Add uses-feature tag",
      "AndroidManifest.xml", "xml")
  }

  fun testApiInlined() {
    createManifest()
    myFixture.copyFileToProject("$globalTestDir/MyActivity.java", "src/p1/p2/MyActivity.java")
    doGlobalInspectionTest(AndroidLintInlinedApiInspection())
  }

  fun testApiOverride() {
    createManifest()
    createProjectProperties()
    // We need a build target >= 1 but also *smaller* than 17. Ensure this is the case
    val platform = AndroidPlatform.getInstance(myFacet.module)
    if (platform != null && platform.apiLevel < 17) {
      myFixture.copyFileToProject("$globalTestDir/MyActivity.java", "src/p1/p2/MyActivity.java")
      doGlobalInspectionTest(AndroidLintOverrideInspection())
    } else { // TODO: else try to find and set a target on the project such that the above returns true
    }
  }

  fun testParcelLoader() {
    doTestWithFix(
      AndroidLintParcelClassLoaderInspection(),
      "Use getClass().getClassLoader()",
      "/src/test/pkg/ParcelClassLoaderTest.java", "java")
  }

  fun testParcelLoader2() {
    doTestWithFix(
      AndroidLintParcelClassLoaderInspection(),
      "Use getClass().getClassLoader()",
      "/src/test/pkg/ParcelClassLoaderTest.java", "java")
  }

  fun testDeprecation() { // Need to use minSdkVersion >= 3 to get all the deprecation warnings to kick in
    deleteManifest()
    addMinSdkManifest(3)
    doTestNoFix(AndroidLintDeprecatedInspection(),
                "/res/layout/deprecation.xml", "xml")
  }

  fun testUnprotectedSmsBroadcastReceiver() {
    deleteManifest()
    doTestWithFix(AndroidLintUnprotectedSMSBroadcastReceiverInspection(),
                  "Set permission=\"android.permission.BROADCAST_SMS\"", "AndroidManifest.xml", "xml")
  }

  fun testActivityRegistered() {
    createManifest()
    myFixture.copyFileToProject("$globalTestDir/MyActivity.java", "src/p1/p2/MyActivity.java")
    myFixture.copyFileToProject("$globalTestDir/MyDerived.java", "src/p1/p2/MyDerived.java")
    doGlobalInspectionTest(AndroidLintRegisteredInspection())
  }

  /** Quick fix for typos in network-security-config file. (especially elements) */
  fun testNetworkSecurityConfigTypos1() {
    createManifest()
    doTestWithFix(
      AndroidLintNetworkSecurityConfigInspection(),
      "Use domain-config", "res/xml/network-config.xml", "xml")
  }

  /** Check typos in network-security-config attribute. */
  fun testNetworkSecurityConfigTypos2() {
    createManifest()
    doTestWithFix(
      AndroidLintNetworkSecurityConfigInspection(),
      "Use includeSubdomains", "res/xml/network-config.xml", "xml")
  }

  fun testDeleteRepeatedWords() {
    doTestWithFix(AndroidLintTyposInspection(),
                  "Delete repeated word", "res/values/strings.xml", "xml")
  }

  fun testInvalidPinDigestAlg() {
    createManifest()
    doTestWithFix(
      AndroidLintNetworkSecurityConfigInspection(),
      "Set digest to \"SHA-256\"",
      "res/xml/network-config.xml", "xml")
  }

  fun testResourceTypes() {
    createManifest()
    addDrawableRes()
    doTestNoFix(
      AndroidLintResourceTypeInspection(),
      "/src/p1/p2/ResourceTypes.java", "java")
  }

  fun testStringEscapes() { // Regression test for https://code.google.com/p/android/issues/detail?id=224150
    doTestWithFix(
      AndroidLintStringEscapingInspection(),
      "Escape Apostrophe", "/res/values/strings.xml", "xml")
  }

  fun testRegistration() {
    doTestHighlighting(AndroidLintRegisteredInspection(), "/src/p1/p2/RegistrationTest.java", "java")
  }

  fun testExtendAppCompatWidgets() { // Configure appcompat dependency
    val modules = ModuleManager.getInstance(project).modules
    for (module in modules) {
      if (module !== myModule && AndroidFacet.getInstance(module) != null) {
        deleteManifest(module)
      }
    }
    val testProjectSystem = TestProjectSystem(project)
    testProjectSystem.useInTests()
    testProjectSystem.addDependency(GoogleMavenArtifactId.ANDROIDX_APP_COMPAT_V7, myFixture.module,
                                    GradleVersion.parse("+"))
    doTestWithFix(AndroidLintAppCompatCustomViewInspection(),
                  "Extend AppCompat widget instead", "/src/p1/p2/MyButton.java", "java")
  }

  fun testExif() {
    doTestWithFix(AndroidLintExifInterfaceInspection(),
                  "Update all references in this file",
                  "/src/test/pkg/ExifUsage.java", "java")
  }

  fun testMissingWearStandaloneAppFlag() {
    deleteManifest()
    doTestWithFix(AndroidLintWearStandaloneAppFlagInspection(),
                  "Add meta-data element for 'com.google.android.wearable.standalone'",
                  "AndroidManifest.xml", "xml")
  }

  fun testInvalidWearStandaloneAppAttrValue() {
    deleteManifest()
    doTestWithFix(AndroidLintWearStandaloneAppFlagInspection(),
                  "Replace with true",
                  "AndroidManifest.xml", "xml")
  }

  fun testMissingWearStandaloneAppFlagValueAttr() {
    deleteManifest()
    doTestWithFix(AndroidLintWearStandaloneAppFlagInspection(),
                  "Set value=\"true\"",
                  "AndroidManifest.xml", "xml")
  }

  fun testInvalidWearFeatureAttr() {
    deleteManifest()
    doTestWithFix(
      AndroidLintInvalidWearFeatureAttributeInspection(),
      "Remove attribute",
      "AndroidManifest.xml", "xml")
  }

  fun testWakelockTimeout() {
    deleteManifest()
    doTestWithFix(AndroidLintWakelockTimeoutInspection(),
                  "Set timeout to 10 minutes",
                  "/src/test/pkg/WakelockTest.java", "java")
  }

  fun testWifiManagerLeak() {
    deleteManifest()
    // Set minSdkVersion to pre-N:
    deleteManifest()
    addMinSdkManifest(14)
    doTestWithFix(AndroidLintWifiManagerLeakInspection(),
                  "Add getApplicationContext()",
                  "/src/test/pkg/WifiManagerLeak.java", "java")
  }

  fun testInvalidImeActionId() {
    doTestNoFix(
      AndroidLintInvalidImeActionIdInspection(),
      "/res/layout/layout.xml", "xml")
  }

  fun testLintNonAndroid() { // See LintIdeTest; this is the opposite check
    // Make sure that we don't include the non-Android lint checks here.
    val support = AndroidLintIdeSupport()
    assertNull(support.getIssueRegistry().getIssue("LintImplDollarEscapes"))
  }

  fun testOldBetaPlugin() {
    // note: the test file needs updating when major/minor versions of AGP are removed from the offline
    // Google Maven cache, and in particular there may be no way to get this test to pass (i.e. to show a
    // warning) if the only stable AGP version in the offline Google Maven cache is a .0 patchlevel version.
    // Check changes in tools/base/sdk-common/src/main/resources/versions-offline/com/android/tools/build/group-index.xml
    // and update adt/idea/android-lint/testData/lint/oldBetaPlugin.gradle
    doTestHighlighting(AndroidLintAndroidGradlePluginVersionInspection(), "build.gradle", "gradle")
  }

  fun testOldBetaPluginNoGMaven() {
    doTestHighlighting(AndroidLintAndroidGradlePluginVersionInspection(), "build.gradle", "gradle")
  }

  fun testCustomTagWithoutName() {
    doTestWithFix(
      AndroidLintMotionSceneFileValidationErrorInspection(),
      "Set attributeName",
      "/res/xml/customTagWithoutName.xml", "xml"
    )
  }

  fun testCustomTagWithDuplicateName() {
    doTestWithFix(
      AndroidLintMotionSceneFileValidationErrorInspection(),
      "Delete this custom attribute",
      "/res/xml/customTagWithDuplicateName.xml", "xml"
    )
  }

  fun testMotionLayoutWithoutLayoutDescription() {
    doTestWithFix(
      AndroidLintMotionLayoutInvalidSceneFileReferenceInspection(),
      "Generate MotionScene file",
      "/res/layout/motionLayoutWithoutLayoutDescription.xml", "xml"
    )

    val sceneFile = "${getTestName(true)}_scene.xml"
    myFixture.checkResultByFile("res/xml/$sceneFile", "$BASE_PATH/$sceneFile", false)
  }

  fun testIntentionPreviewAddTargetVersion() {
    // Test that the intention preview for AddTargetVersionCheckQuickFix works as expected; in particular,
    // it makes edits to the preview non-physical file, and does not modify the physical file.
    val file = myFixture.configureByText("X.java", /*language=JAVA */ """
      package com.example;
      import java.io.FileReader;
      import java.io.IOException;
      import java.util.Properties;
      public class X {
        public static void foo() throws IOException {
          FileReader reader = new FileReader("../local.properties");
          Properties props = new Properties();
          props.load(reader);
          reader.close();
        }
      }
      """.trimIndent()
    )

    checkPreviewFix(
      file,
      "^props.load", {
        val fix = AddTargetVersionCheckQuickFix(9, ExtensionSdk.ANDROID_SDK_ID, ApiConstraint.ALL)
        assertThat(fix.name).isEqualTo("Surround with if (VERSION.SDK_INT >= VERSION_CODES.GINGERBREAD) { ... }")
        fix
      },
      """
      @@ -9 +9
      -     props.load(reader);
      +     if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.GINGERBREAD) {
      + props.load(reader);
      +     }
      """
    )
  }

  fun testIntentionPreviewExtractString() {
    // Test that the intention preview for AndroidAddStringResourceQuickFix works as expected
    val file = myFixture.configureByText("layout.xml", /*language=XML */ """
      <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android">
          <Button android:text="Hello World"/>
      </LinearLayout>
      """.trimIndent()
    )
    checkPreviewAction(file, "Hello ^World", { AndroidAddStringResourceQuickFix(it) }, """
      @@ -2 +2
      -     <Button android:text="Hello World"/>
      +     <Button android:text="@string/hello_world"/>
      """
    )
  }

  fun testIntentionPreviewExtractDimension() {
    // Test that the intention preview for AndroidExtractDimensionAction works as expected
    val file = myFixture.configureByText("layout.xml", /*language=XML */ """
      <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android">
          <Button android:textSize="50px"  />
      </LinearLayout>
      """.trimIndent()
    )
    checkPreviewAction(file, "50^px", { AndroidExtractDimensionAction() }, """
      @@ -2 +2
      -     <Button android:textSize="50px"  />
      +     <Button android:textSize="@dimen/dimen_name"  />
      """
    )
  }

  fun testIntentionPreviewConvertToDp() {
    // Test that the intention preview for ConvertToDpQuickFix works as expected
    val file = myFixture.configureByText("layout.xml", /*language=XML */ """
      <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android">
          <Button android:textSize="50px"  />
      </LinearLayout>
      """.trimIndent()
    )
    checkPreviewFix(file, "50^px", { ConvertToDpQuickFix() }, """
      @@ -2 +2
      -     <Button android:textSize="50px"  />
      +     <Button android:textSize="50dp"  />
      """
    )
  }

  fun testIntentionPreviewExtractColor() {
    // Test that the intention preview for AndroidExtractColorAction works as expected
    val file = myFixture.configureByText("states.xml", /*language=XML */ """
                <selector xmlns:android="http://schemas.android.com/apk/res/android">
                    <item android:state_pressed="true"
                          android:color="#ffff0000"/> <!-- pressed -->
                    <item android:color="#ff000000"/>
                </selector>
      """.trimIndent()
    )
    checkPreviewAction(file, "#ff^ff", { AndroidExtractColorAction() }, """
      @@ -3 +3
      -           android:color="#ffff0000"/> <!-- pressed -->
      +           android:color="@color/color_name"/> <!-- pressed -->
      """
    )
  }

  fun testReplaceStringPreview() {
    // Test that the intention preview for ReplaceStringQuickfix works as expected
    val file = myFixture.configureByText("states.xml", /*language=KT */ """
      fun test() {
        val foo = "bar"
      }
      """.trimIndent()
    )
    checkPreviewFix(file, "val f^oo =", {
      ReplaceStringQuickFix(null, null, "(foo)", "foo: Foo")
    }, """
      @@ -2 +2
      -   val foo = "bar"
      +   val foo: Foo = "bar"
      """
    )
  }

  private fun findElement(file: PsiFile, caret: String): PsiElement {
    val offset = getCaretOffset(file.text, caret)
    myFixture.editor.caretModel.moveToOffset(offset)
    return file.findElementAt(offset)
           ?: error("No element at offset $offset")
  }

  private fun getCaretOffset(fileContent: String, caretLocation: String): Int {
    assertTrue(caretLocation, caretLocation.contains("^"))
    val caretDelta = caretLocation.indexOf('^')
    assertTrue(caretLocation, caretDelta != -1)

    // String around caret/range without the range and caret marker characters
    val caretContext: String = caretLocation.substring(0, caretDelta) + caretLocation.substring(caretDelta + 1)
    val caretContextIndex = fileContent.indexOf(caretContext)
    assertTrue("Caret content $caretContext not found in file", caretContextIndex != -1)
    return caretContextIndex + caretDelta
  }

  private fun checkPreviewFix(file: PsiFile, caret: String, createFix: (element: PsiElement)->LintIdeQuickFix, expected: String) {
    val element = findElement(file, caret)
    val fix = createFix(element)
    val action = MyFixingIntention(fix, project, file, element.textRange)
    checkPreview(expected, action, file)
  }

  private fun checkPreviewAction(file: PsiFile, caret: String, createAction: (element: PsiElement)->IntentionAction, expected: String) {
    val element = findElement(file, caret)
    val action = createAction(element)
    checkPreview(expected, action, file)
  }

  private fun checkPreview(expected: String, intentionAction: IntentionAction, file: PsiFile) {
    // Test preview
    val psiFileCopy = file.copy() as PsiFile
    val originalEditor: Editor = myFixture.editor
    // Inspired by (internal) com.intellij.codeInsight.intention.impl.preview.IntentionPreviewEditor
    val editorCopy: ImaginaryEditor = object : ImaginaryEditor(project, psiFileCopy.viewProvider.document) {
      override fun getSettings(): EditorSettings {
        return originalEditor.settings
      }
    }
    editorCopy.caretModel.moveToOffset(originalEditor.caretModel.offset)
    assertFalse(psiFileCopy.isPhysical)
    val preview = intentionAction.generatePreview(project, editorCopy, psiFileCopy)
    val documentManager = PsiDocumentManager.getInstance(project)
    documentManager.commitDocument(editorCopy.document)
    assertEquals(IntentionPreviewInfo.DIFF, preview)
    assertEquals(expected.trimIndent().trim(),
                 TestUtils.getDiff(file.text, psiFileCopy.text).trim())
  }

  private fun doGlobalInspectionTest(inspection: GlobalInspectionTool): SynchronizedBidiMultiMap<RefEntity, CommonProblemDescriptor> {
    myFixture.enableInspections(inspection)
    return doGlobalInspectionTest(inspection, globalTestDir, AnalysisScope(myModule))
  }

  private fun doGlobalInspectionWithFix(inspection: GlobalInspectionTool, actionLabel: String) {
    val map = doGlobalInspectionTest(inspection)
    // Ensure family names are unique; if not quickfixes get collapsed. Set.add only returns true if it wasn't already in the set.
    for (refEntity in map.keys()) {
      for (descriptor in map[refEntity]) {
        val fixes = descriptor.fixes
        if (fixes != null) {
          for (fix in fixes) {
            val name = fix.name
            if (actionLabel == name) {
              if (fix.startInWriteAction()) {
                WriteCommandAction.runWriteCommandAction(project) { fix.applyFix(project, descriptor) }
              } else {
                fix.applyFix(project, descriptor)
              }
              break
            }
          }
        }
      }
    }
  }

  private val globalTestDir: String
    get() = BASE_PATH_GLOBAL + getTestName(true)

  private fun doTestNoFix(inspection: AndroidLintInspectionBase, copyTo: String, extension: String) {
    doTestHighlighting(inspection, copyTo, extension)
    var action: IntentionAction? = null
    for (a in myFixture.availableIntentions) {
      if (a is MyFixingIntention) {
        action = a
      }
    }
    assertNull(action)
  }

  private fun doTestWithFix(
    inspection: AndroidLintInspectionBase,
    message: String,
    copyTo: String,
    extension: String
  ) {
    val action = doTestHighlightingAndGetQuickfix(inspection, message, copyTo, extension)
    doTestWithAction(extension, action!!)
  }

  private fun doTestWithoutHighlightingWithFix(
    inspection: AndroidLintInspectionBase,
    message: String,
    copyTo: String,
    extension: String
  ) {
    val action = getQuickfixWithoutHighlightingCheck(inspection, message, copyTo, extension)
    assertNotNull(action)
    doTestWithAction(extension, action!!)
  }

  private fun doTestWithAction(extension: String, action: IntentionAction) {
    assertTrue(action.isAvailable(myFixture.project, myFixture.editor, myFixture.file))
    WriteCommandAction.runWriteCommandAction(myFixture.project) {
      action.invoke(myFixture.project, myFixture.editor, myFixture.file)
    }

    myFixture.checkResultByFile(BASE_PATH + getTestName(true) + "_after." + extension)
  }

  private fun doTestHighlightingAndGetQuickfix(
    inspection: AndroidLintInspectionBase,
    message: String,
    copyTo: String,
    extension: String
  ): IntentionAction? {
    doTestHighlighting(inspection, copyTo, extension, false)
    return myFixture.getIntentionAction(message)
           ?: error("Couldn't find intention action \"$message\"; options were:\n${myFixture.availableIntentions.joinToString("\n") { it.text }}")
  }

  private fun getQuickfixWithoutHighlightingCheck(
    inspection: AndroidLintInspectionBase,
    message: String,
    copyTo: String,
    extension: String
  ): IntentionAction? {
    doTestHighlighting(inspection, copyTo, extension, true)
    return myFixture.getIntentionAction(message)
  }

  private fun doTestHighlighting(
    inspection: AndroidLintInspectionBase,
    copyTo: String,
    extension: String,
    skipCheck: Boolean = false
  ): List<HighlightInfo> {
    myFixture.enableInspections(inspection)
    val file = myFixture.copyFileToProject(BASE_PATH + getTestName(true) + "." + extension, copyTo)
    myFixture.configureFromExistingVirtualFile(file)
    // Strip out <error> and <warning> markers. It's not clear why the test framework
    // doesn't do this (it *does* strip out the <caret> markers). Without this,
    // lint is passed markup files that contain the error markers, which makes
    // for example quick fixes not work.
    val prev = stripMarkers(file)
    val highlightInfo = myFixture.doHighlighting()
    // Restore markers before diffing.
    restoreMarkers(file, prev)
    if (!skipCheck) {
      myFixture.checkHighlighting(true, false, false)
    }

    return highlightInfo
  }

  /** Removes any error and warning markers from a file, and returns the original text. */
  private fun stripMarkers(file: VirtualFile): String? {
    val project = project
    val psiFile = PsiManager.getInstance(project).findFile(file) ?: return null
    val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return null
    val prev = document.text
    WriteCommandAction.runWriteCommandAction(project, Runnable {
      while (true) {
        if (!(removeTag(document, "<error", ">") ||
              removeTag(document, "</error", ">") ||
              removeTag(document, "<warning", ">") ||
              removeTag(document, "</warning",
                        ">"))) {
          break
        }
      }
    })
    return prev
  }

  /** Sets the contents of the given file to the given string. */
  private fun restoreMarkers(file: VirtualFile, contents: String?) {
    val project = project
    val psiFile = PsiManager.getInstance(project).findFile(file) ?: return
    val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return
    WriteCommandAction.runWriteCommandAction(project) { document.setText(contents!!) }
  }

  private fun addMinSdkManifest(minSdk: Int) {
    myFixture.addFileToProject("AndroidManifest.xml", manifestContents(minSdk, 25))
  }

  private fun addRequiresApi() {
    myFixture.addFileToProject("/src/android/support/annotation/RequiresApi.java",
                               """
                                 package android.support.annotation;\n" +
                                 import static java.lang.annotation.ElementType.CONSTRUCTOR;
                                 import static java.lang.annotation.ElementType.FIELD;
                                 import static java.lang.annotation.ElementType.METHOD;
                                 import static java.lang.annotation.ElementType.PACKAGE;
                                 import static java.lang.annotation.ElementType.TYPE;
                                 import static java.lang.annotation.RetentionPolicy.CLASS;
                                 import java.lang.annotation.Retention;
                                 import java.lang.annotation.Target;
                                 @Retention(CLASS)
                                 @Target({TYPE, METHOD, CONSTRUCTOR, FIELD, PACKAGE})
                                 public @interface RequiresApi {
                                     int value() default 1;
                                     int api() default 1;
                                 }
                               """.trimIndent())
  }

  private fun addIntDef() {
    myFixture.addFileToProject("/src/android/support/annotation/IntDef.java",
                               """
                                 package android.support.annotation;
                                 import java.lang.annotation.Retention;
                                 import java.lang.annotation.RetentionPolicy;
                                 import java.lang.annotation.Target;
                                 import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
                                 import static java.lang.annotation.ElementType.FIELD;
                                 import static java.lang.annotation.ElementType.METHOD;
                                 import static java.lang.annotation.ElementType.PARAMETER;
                                 import static java.lang.annotation.RetentionPolicy.CLASS;
                                 import static java.lang.annotation.RetentionPolicy.SOURCE;

                                 @Retention(CLASS)
                                 @Target({ANNOTATION_TYPE})
                                 public @interface IntDef {
                                     long[] value() default {};
                                     boolean flag() default false;
                                 }
                               """.trimIndent())
  }

  private fun addKeep() {
    myFixture.addFileToProject("/src/androidx/annotation/Keep.java",
                               """
                                 package androidx.annotation;
                                 import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
                                 import static java.lang.annotation.ElementType.CONSTRUCTOR;
                                 import static java.lang.annotation.ElementType.FIELD;
                                 import static java.lang.annotation.ElementType.METHOD;
                                 import static java.lang.annotation.ElementType.PACKAGE;
                                 import static java.lang.annotation.ElementType.TYPE;
                                 import static java.lang.annotation.RetentionPolicy.CLASS;
                                 import java.lang.annotation.Retention;
                                 import java.lang.annotation.Target;
                                 @Retention(CLASS)
                                 @Target({PACKAGE,TYPE,ANNOTATION_TYPE,CONSTRUCTOR,METHOD,FIELD})
                                 public @interface Keep {
                                 }
                               """.trimIndent())
  }

  private fun addCheckResult() {
    myFixture.addFileToProject("/src/android/support/annotation/Keep.java",
                               """
                                 package android.support.annotation;
                                 import static java.lang.annotation.ElementType.METHOD;
                                 import static java.lang.annotation.RetentionPolicy.CLASS;
                                 import java.lang.annotation.Documented;
                                 import java.lang.annotation.Retention;
                                 import java.lang.annotation.Target;
                                 @Documented
                                 @Retention(CLASS)
                                 @Target({METHOD})
                                 public @interface CheckResult {
                                     String suggest() default "";
                                 }
                               """.trimIndent())
  }

  private fun addDrawableRes() {
    myFixture.addFileToProject("/src/android/support/annotation/DrawableRes.java",
                               """
                                 package android.support.annotation;
                                 import static java.lang.annotation.ElementType.FIELD;
                                 import static java.lang.annotation.ElementType.LOCAL_VARIABLE;
                                 import static java.lang.annotation.ElementType.METHOD;
                                 import static java.lang.annotation.ElementType.PARAMETER;
                                 import static java.lang.annotation.RetentionPolicy.CLASS;

                                 import java.lang.annotation.Documented;
                                 import java.lang.annotation.Retention;
                                 import java.lang.annotation.Target;
                                 @Documented
                                 @Retention(CLASS)
                                 @Target({METHOD, PARAMETER, FIELD, LOCAL_VARIABLE})
                                 public @interface DrawableRes {
                                 }
                               """.trimIndent())
  }

  // --- AndroidX ---
  private fun addColorInt() {
    myFixture.addFileToProject("/src/androidx/annotation/ColorInt.java",
                               """
                                 package androidx.annotation;

                                 import static java.lang.annotation.ElementType.FIELD;
                                 import static java.lang.annotation.ElementType.LOCAL_VARIABLE;
                                 import static java.lang.annotation.ElementType.METHOD;
                                 import static java.lang.annotation.ElementType.PARAMETER;
                                 import static java.lang.annotation.RetentionPolicy.CLASS;

                                 import java.lang.annotation.Retention;
                                 import java.lang.annotation.Target;
                                 @Retention(CLASS)
                                 @Target({PARAMETER,METHOD,LOCAL_VARIABLE,FIELD})
                                 public @interface ColorInt {
                                 }
                               """.trimIndent())
  }

  private fun addColorRes() {
    myFixture.addFileToProject("/src/androidx/annotation/ColorRes.java",
                               """
                                 package androidx.annotation;
                                 import static java.lang.annotation.ElementType.FIELD;
                                 import static java.lang.annotation.ElementType.LOCAL_VARIABLE;
                                 import static java.lang.annotation.ElementType.METHOD;
                                 import static java.lang.annotation.ElementType.PARAMETER;
                                 import static java.lang.annotation.RetentionPolicy.CLASS;

                                 import java.lang.annotation.Documented;
                                 import java.lang.annotation.Retention;
                                 import java.lang.annotation.Target;
                                 @Documented
                                 @Retention(CLASS)
                                 @Target({METHOD, PARAMETER, FIELD, LOCAL_VARIABLE})
                                 public @interface ColorRes {
                                 }
                               """.trimIndent())
  }

  private fun addChecksSdkIntAtLeast(targetDir: String = "/src/") {
    myFixture.addFileToProject("$targetDir/androidx/annotation/ChecksSdkIntAtLeast.java",
             """
            package androidx.annotation;
            import static java.lang.annotation.ElementType.FIELD;
            import static java.lang.annotation.ElementType.METHOD;
            import static java.lang.annotation.RetentionPolicy.CLASS;
            import java.lang.annotation.Documented;
            import java.lang.annotation.Retention;
            import java.lang.annotation.Target;
            @Documented
            @Retention(CLASS)
            @Target({METHOD, FIELD})
            public @interface ChecksSdkIntAtLeast {
                int api() default -1;
                String codename() default "";
                int parameter() default -1;
                int lambda() default -1;
            }
            """.trimIndent()
    )
  }

  companion object {
    @NonNls
    private const val BASE_PATH = "/lint/"
    @NonNls
    private const val BASE_PATH_GLOBAL = BASE_PATH + "global/"

    /** Searches the given document for a prefix and suffix and deletes it if found. Caller must hold write lock. */
    private fun removeTag(document: Document, prefix: String, suffix: String): Boolean {
      val sequence = document.charsSequence
      val start = CharSequences.indexOf(sequence, prefix)
      if (start != -1) {
        var end = CharSequences.indexOf(sequence, suffix, start + prefix.length)
        if (end != -1) {
          end += suffix.length
          document.deleteString(start, end)
          return true
        }
      }
      return false
    }

    private fun manifestContents(minSdk: Int, targetSdk: Int): String {
      return """
        <?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
            package="test.pkg" >
            <uses-sdk android:minSdkVersion="$minSdk" android:targetSdkVersion="$targetSdk" />
        </manifest>"
       """.trimIndent()
    }
  }
}
