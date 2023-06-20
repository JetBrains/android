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
package com.android.tools.idea.insights.analysis

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.insights.Frame
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

@RunWith(JUnit4::class)
class DelegatingConfidenceMatcherTest {

  private val mockFile: PsiFile = mock()
  private val mockElement: PsiElement = mock()
  private val testCrash = CrashFrame(Frame(), Cause.Throwable("java.lang.NullPointerException"))

  @Test
  fun `match with a HIGH confidence match should stop once it's found`() {
    val lowMatch = makeMatchMock(mockElement, Confidence.LOW)
    val highMatch = makeMatchMock(mockElement, Confidence.HIGH)
    val mockMatcher: CrashMatcher = mock()

    val matcher =
      DelegatingConfidenceMatcher(
        listOf(CrashMatcher { _, _ -> lowMatch }, CrashMatcher { _, _ -> highMatch }, mockMatcher),
        Confidence.MEDIUM
      )

    val result = matcher.match(mockFile, testCrash)
    assertThat(result).isSameAs(highMatch)
    verify(mockMatcher, never()).match(any(), any())
  }

  @Test
  fun `match with a MEDIUM confidence match should stop once it's found`() {
    val lowMatch = makeMatchMock(mockElement, Confidence.LOW)
    val mediumMatch = makeMatchMock(mockElement, Confidence.MEDIUM)
    val mockMatcher: CrashMatcher = mock()

    val matcher =
      DelegatingConfidenceMatcher(
        listOf(
          CrashMatcher { _, _ -> lowMatch },
          CrashMatcher { _, _ -> mediumMatch },
          mockMatcher
        ),
        Confidence.MEDIUM
      )

    val result = matcher.match(mockFile, testCrash)
    assertThat(result).isSameAs(mediumMatch)
    verify(mockMatcher, never()).match(any(), any())
  }

  @Test
  fun `match with only low confidence matches should call them all and return the first one`() {
    val lowMatch = makeMatchMock(mockElement, Confidence.LOW)
    val anotherLowMatch = makeMatchMock(mockElement, Confidence.LOW)
    val mockMatcher: CrashMatcher = mock()

    whenever(mockMatcher.match(any(), any())).thenReturn(anotherLowMatch)
    val matcher =
      DelegatingConfidenceMatcher(
        listOf(CrashMatcher { _, _ -> lowMatch }, mockMatcher),
        Confidence.MEDIUM
      )

    val result = matcher.match(mockFile, testCrash)
    // TODO: reenable when we support lower-confidence matches again
    // assertThat(result).isSameAs(lowMatch)
    verify(mockMatcher, times(1)).match(any(), any())
  }

  @Test
  fun `match with no matches that satisfy minConfidence should return the first highest confidence match`() {
    val lowMatch = makeMatchMock(mockElement, Confidence.LOW)
    val anotherLowMatch = makeMatchMock(mockElement, Confidence.LOW)
    val mediumMatch = makeMatchMock(mockElement, Confidence.MEDIUM)
    val anotherMediumMatch = makeMatchMock(mockElement, Confidence.MEDIUM)
    val lowMockMatcher: CrashMatcher = mock()
    val mediumMockMatcher: CrashMatcher = mock()

    whenever(lowMockMatcher.match(any(), any())).thenReturn(anotherLowMatch)
    whenever(mediumMockMatcher.match(any(), any())).thenReturn(anotherMediumMatch)
    val matcher =
      DelegatingConfidenceMatcher(
        listOf(
          CrashMatcher { _, _ -> lowMatch },
          CrashMatcher { _, _ -> mediumMatch },
          lowMockMatcher,
          mediumMockMatcher
        ),
        minConfidence = Confidence.HIGH
      )

    val result = matcher.match(mockFile, testCrash)
    // TODO: reenable when we support lower-confidence matches again
    // assertThat(result).isSameAs(mediumMatch)
    verify(lowMockMatcher, times(1)).match(any(), any())
    verify(mediumMockMatcher, times(1)).match(any(), any())
  }

  private fun makeMatchMock(element: PsiElement, confidence: Confidence): Match {
    val match: Match = mock()
    whenever(match.element).thenReturn(element)
    whenever(match.confidence).thenReturn(confidence)
    return match
  }
}
