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
package com.android.tools.idea.insights.inspection

import com.android.flags.junit.FlagRule
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.testutils.TestUtils
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.insights.AppInsight
import com.android.tools.idea.insights.AppInsightsIssue
import com.android.tools.idea.insights.Frame
import com.android.tools.idea.insights.ISSUE1
import com.android.tools.idea.insights.ui.AppInsightsGutterRenderer
import com.android.tools.idea.insights.ui.AppInsightsTabProvider
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.daemon.GutterIconDescriptor
import com.intellij.codeInsight.daemon.LineMarkerSettings
import com.intellij.lang.annotation.AnnotationBuilder
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import com.jetbrains.rd.util.first
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.util.getLineCount
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

@RunsInEdt
class AppInsightsExternalAnnotatorTest {
  @get:Rule val projectRule = AndroidProjectRule.onDisk()
  @get:Rule val edtRule = EdtRule()

  @get:Rule val disposableRule = DisposableRule()

  @get:Rule val appInsightsFlagRule = FlagRule(StudioFlags.APP_INSIGHTS_ENABLED, true)
  @get:Rule val gutterSupportFlagRule = FlagRule(StudioFlags.APP_INSIGHTS_GUTTER_SUPPORT, true)

  private lateinit var mainActivityFile: PsiFile

  @Before
  fun setup() {
    projectRule.fixture.testDataPath =
      TestUtils.resolveWorkspacePath("tools/adt/idea/app-quality-insights/ide/testData").toString()
    mainActivityFile =
      projectRule.fixture
        .copyFileToProject("src/com/google/firebase/assistant/test/MainActivity.kt")
        .toPsiFile(projectRule.project)!!
  }

  private val gutterRendererCaptor = ArgumentCaptor.forClass(AppInsightsGutterRenderer::class.java)

  @Test
  fun testAnnotationsDisabled() {
    val expected: List<AppInsight> = listOf(mock())
    withFakedInsights(expected)

    var settingEnabled = true
    val fakeLineMarkerSettings =
      object : LineMarkerSettings() {
        override fun isEnabled(descriptor: GutterIconDescriptor) = settingEnabled

        override fun setEnabled(descriptor: GutterIconDescriptor, selected: Boolean) {}
      }

    ApplicationManager.getApplication()
      .replaceService(
        LineMarkerSettings::class.java,
        fakeLineMarkerSettings,
        disposableRule.disposable
      )

    val file = runReadAction {
      PsiFileFactory.getInstance(projectRule.project)
        .createFileFromText(KotlinLanguage.INSTANCE, "foo")
    }

    val annotator = AppInsightsExternalAnnotator()
    assertThat(annotator.collectInformation(file)?.insights)
      .isEqualTo(AppInsightsExternalAnnotator.InitialInfo(mapOf("Test 1" to expected), 10).insights)
    settingEnabled = false
    assertThat(annotator.collectInformation(file)).isNull()
  }

  @Test
  fun `lines are correctly annotated`() {
    val expected: List<AppInsight> =
      listOf(
        buildCrashlyticsInsight(Frame(line = 10), mock()),
        buildCrashlyticsInsight(Frame(line = 12), mock()),
        buildCrashlyticsInsight(Frame(line = 30), mock())
      )
    withFakedInsights(expected)

    val annotator = AppInsightsExternalAnnotator()

    val collectedInformationWhenErrors =
      annotator.collectInformation(mainActivityFile, mock(), true)
    val collectedInformation = annotator.collectInformation(mainActivityFile)
    assertThat(collectedInformation).isEqualTo(collectedInformationWhenErrors)

    assertThat(collectedInformation).isNotNull()
    assertThat(collectedInformation!!.insights).hasSize(1)

    val annotationResult = annotator.doAnnotate(collectedInformation)
    assertThat(annotationResult.result)
      .isEqualTo(
        mapOf(
          9 to mapOf(testTabProvider1Name to listOf(expected[0])),
          11 to mapOf(testTabProvider1Name to listOf(expected[1])),
          29 to mapOf(testTabProvider1Name to listOf(expected[2]))
        )
      )
  }

  @Test
  fun `apply will create annotations for success scenario`() {
    val expected: List<AppInsight> =
      listOf(
        buildCrashlyticsInsight(Frame(line = 10), mock()),
        buildCrashlyticsInsight(Frame(line = 12), mock()),
        buildCrashlyticsInsight(Frame(line = 30), mock())
      )
    withFakedInsights(expected)

    val annotator = AppInsightsExternalAnnotator()
    val collectedInformation = annotator.collectInformation(mainActivityFile)
    val annotationResult = annotator.doAnnotate(collectedInformation)

    val (mockAnnotationHolder, mockAnnotationBuilder) = createMockAnnotationHolderAndBuilder()
    annotator.apply(mainActivityFile, annotationResult, mockAnnotationHolder)

    verify(mockAnnotationBuilder, times(3)).gutterIconRenderer(gutterRendererCaptor.capture())
    val capturedValues = gutterRendererCaptor.allValues
    assertThat(capturedValues.flatMap { it.insights.values.flatten() }).containsAllIn(expected)
  }

  @Test
  fun `file with no annotations is supported`() {
    withFakedInsights(emptyList())

    val annotator = AppInsightsExternalAnnotator()
    val collectedInformation = annotator.collectInformation(mainActivityFile)
    assertThat(collectedInformation).isNull()
    assertThat(annotator.doAnnotate(collectedInformation).result).isEmpty()
  }

  @Test
  fun `file with all issues out of scope is supported`() {
    val expected: List<AppInsight> =
      listOf(
        buildCrashlyticsInsight(Frame(line = 300), mock()),
        buildCrashlyticsInsight(Frame(line = 320), mock()),
        buildCrashlyticsInsight(Frame(line = 400), mock())
      )
    withFakedInsights(expected)

    val annotator = AppInsightsExternalAnnotator()
    val collectedInformation = annotator.collectInformation(mainActivityFile)
    assertThat(collectedInformation).isNotNull()
    assertThat(collectedInformation!!.insights).hasSize(1)
    assertThat(collectedInformation.insights.first().value).hasSize(3)

    val annotationResult = annotator.doAnnotate(collectedInformation)
    assertThat(annotationResult.result).isEmpty()
  }

  @Test
  fun `duplicate entries are removed from line`() {
    val issue = ISSUE1
    val expected: List<AppInsight> =
      listOf(
        buildCrashlyticsInsight(Frame(line = 10), issue),
        buildCrashlyticsInsight(Frame(line = 10), issue),
        buildCrashlyticsInsight(Frame(line = 30), mock())
      )
    withFakedInsights(expected)

    val annotator = AppInsightsExternalAnnotator()
    val collectedInformation = annotator.collectInformation(mainActivityFile)
    assertThat(collectedInformation).isNotNull()
    // Deduping happens after this point
    assertThat(collectedInformation!!.insights).hasSize(1)
    assertThat(collectedInformation.insights.first().value).hasSize(3)

    val annotationResult = annotator.doAnnotate(collectedInformation)
    assertThat(annotationResult.result).isNotEmpty()
    // Offset should be 1 less than the expected value
    assertThat(annotationResult.result.keys).containsAllIn(listOf(9, 29))
  }

  @Test
  fun `apply works for files with valid annotations`() {
    val expected: List<AppInsight> =
      listOf(
        buildCrashlyticsInsight(Frame(line = 10), mock()),
        buildCrashlyticsInsight(Frame(line = 12), mock()),
        buildCrashlyticsInsight(Frame(line = 30), mock())
      )
    withFakedInsights(expected)

    val annotator = AppInsightsExternalAnnotator()
    val collectedInformation = annotator.collectInformation(mainActivityFile)
    assertThat(collectedInformation).isNotNull()
    assertThat(collectedInformation!!.insights).hasSize(1)
    assertThat(collectedInformation.insights.first().value).hasSize(3)

    val annotationResult = annotator.doAnnotate(collectedInformation)
    assertThat(annotationResult.result).isNotEmpty()
    // Offset should be 1 less than the expected value
    assertThat(annotationResult.result.keys).containsAllIn(listOf(9, 11, 29))
  }

  @Test
  fun `annotations from two insights sources`() {
    val expected1 =
      listOf(
        buildCrashlyticsInsight(Frame(line = 10), mock()),
        buildCrashlyticsInsight(Frame(line = 12), mock()),
        buildCrashlyticsInsight(Frame(line = 30), mock())
      )
    val expected2 =
      listOf(
        buildCrashlyticsInsight(Frame(line = 12), mock()),
        buildCrashlyticsInsight(Frame(line = 27), mock()),
        buildCrashlyticsInsight(Frame(line = 78), mock()),
      )

    withFakedInsights(expected1, expected2)
    val annotator = AppInsightsExternalAnnotator()
    val collectedInformation = annotator.collectInformation(mainActivityFile)
    assertThat(collectedInformation).isNotNull()
    assertThat(collectedInformation!!.insights).hasSize(2)
    assertThat(collectedInformation.insights.flatMap { it.value }).hasSize(6)

    val annotationResult = annotator.doAnnotate(collectedInformation)
    assertThat(annotationResult.result)
      .isEqualTo(
        mapOf(
          9 to mapOf(testTabProvider1Name to listOf(expected1[0])),
          11 to
            mapOf(
              testTabProvider1Name to listOf(expected1[1]),
              testTabProvider2Name to listOf(expected2[0])
            ),
          29 to mapOf(testTabProvider1Name to listOf(expected1[2])),
          26 to mapOf(testTabProvider2Name to listOf(expected2[1]))
        )
      )

    val (mockAnnotationHolder, mockAnnotationBuilder) = createMockAnnotationHolderAndBuilder()
    annotator.apply(mainActivityFile, annotationResult, mockAnnotationHolder)

    verify(mockAnnotationBuilder, times(4)).gutterIconRenderer(gutterRendererCaptor.capture())
    assertThat(gutterRendererCaptor.allValues.flatMap { it.insights.values.flatten() })
      .containsAllIn((expected1 + expected2).filter { it.line < mainActivityFile.getLineCount() })
  }

  private fun buildCrashlyticsInsight(frame: Frame, issue: AppInsightsIssue): AppInsight {
    val insightMock: AppInsight = mock()
    whenever(insightMock.stackFrame).thenReturn(frame)
    whenever(insightMock.issue).thenReturn(issue)
    whenever(insightMock.line).thenReturn(frame.line.toInt() - 1)
    return insightMock
  }

  private fun withFakedInsights(
    expectedInsightsFromTabProvider1: List<AppInsight>,
    expectedInsightsFromTabProvider2: List<AppInsight> = emptyList()
  ) {
    AppInsightsTabProvider.EP_NAME.extensionList
      .filterIsInstance<TestTabProvider>()
      .forEachIndexed { index, tabProvider ->
        if (index == 0) {
          tabProvider.returnInsights(expectedInsightsFromTabProvider1)
        } else {
          tabProvider.returnInsights(expectedInsightsFromTabProvider2)
        }
      }
  }

  private fun createMockAnnotationHolderAndBuilder(): Pair<AnnotationHolder, AnnotationBuilder> {
    val mockAnnotationHolder: AnnotationHolder = mock()
    val mockAnnotationBuilder: AnnotationBuilder = mock()
    whenever(mockAnnotationHolder.newSilentAnnotation(any())).thenReturn(mockAnnotationBuilder)
    whenever(mockAnnotationBuilder.range(any<TextRange>())).thenReturn(mockAnnotationBuilder)
    whenever(mockAnnotationBuilder.gutterIconRenderer(any())).thenReturn(mockAnnotationBuilder)
    return mockAnnotationHolder to mockAnnotationBuilder
  }

  private val testTabProvider1: AppInsightsTabProvider
    get() = AppInsightsTabProvider.EP_NAME.extensionList.first()
  private val testTabProvider2: AppInsightsTabProvider
    get() = AppInsightsTabProvider.EP_NAME.extensionList[1]
  private val testTabProvider1Name: String
    get() = testTabProvider1.tabDisplayName
  private val testTabProvider2Name: String
    get() = testTabProvider2.tabDisplayName
}
