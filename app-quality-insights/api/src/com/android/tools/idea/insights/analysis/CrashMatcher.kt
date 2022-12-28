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

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiFile

/** SAM interface for classes that match Crash frames with the code in the editor. */
fun interface CrashMatcher {
  /** Given a [file] and a [crash][CrashFrame], returns a [Match] or null. */
  fun match(file: PsiFile, crash: CrashFrame): Match?
}

/**
 * Matcher that delegates to other [CrashMatcher]s in the order they are registered.
 *
 * The [matchers] arg should be in priority order. The [match] method will iterate through that
 * list, returning the first [Match] with confidence greater or equal than [minConfidence], null
 * otherwise.
 */
class DelegatingConfidenceMatcher(
  private val matchers: List<CrashMatcher>,
  private val minConfidence: Confidence
) : CrashMatcher {
  companion object {
    private val log: Logger
      get() = Logger.getInstance(DelegatingConfidenceMatcher::class.java)
  }
  override fun match(file: PsiFile, crash: CrashFrame): Match? =
    matchers
      .asSequence()
      .also { log.debug("Looking for crash matches for ${file.name}") }
      .mapNotNull { matcher ->
        ProgressManager.checkCanceled()
        log.debug("[${matcher::class.java.simpleName}] running...")
        matcher.match(file, crash)?.also {
          log.debug(
            "[${matcher::class.java.simpleName}] found match with confidence ${it.confidence}"
          )
        }
      }
      .firstOrNull { match -> match.confidence >= minConfidence }
}
