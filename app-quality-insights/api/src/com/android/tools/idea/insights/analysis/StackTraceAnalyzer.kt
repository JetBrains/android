/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.google.services.firebase.logs.FirebaseTracker
import com.google.services.firebase.logs.convertResolution
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.TestOnly

/** Main entry point to the analyzer infrastructure. */
@Service
class StackTraceAnalyzer
@JvmOverloads
constructor(
  private val project: Project,
  @TestOnly
  private val matcher: CrashMatcher =
    DelegatingConfidenceMatcher(
      listOf(
        FullMatcher(),
        MethodMatcher(),
      ),
      minConfidence = Confidence.HIGH
    ),
  private val tracker: FirebaseTracker = project.service()
) {

  fun match(file: PsiFile, crash: CrashFrame): Match? {
    val match = matcher.match(file, crash) ?: return null
    tracker.logMatchers(
      AppQualityInsightsUsageEvent.AppQualityInsightsMatcherDetails.newBuilder()
        .apply {
          confidence = match.confidence.toProto()
          resolution = convertResolution(match.element)
          source =
            AppQualityInsightsUsageEvent.AppQualityInsightsMatcherDetails.MatcherSource
              .UNKNOWN_SOURCE
          crashType = AppQualityInsightsUsageEvent.CrashType.FATAL
        }
        .build()
    )
    return if (match.confidence == Confidence.HIGH) match else null
  }
}
