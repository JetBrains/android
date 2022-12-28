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

import com.android.tools.idea.insights.Frame
import com.intellij.psi.PsiElement

/** A pair of a [crash frame][Frame] and its ["cause"][Cause]. */
data class CrashFrame(val frame: Frame, val cause: Cause)

/**
 * Represents the "cause" of the stack frame.
 *
 * Any stack frame either has a [previous frame][Cause.Frame] or a [Cause.Throwable] that was thrown
 * at that frame.
 */
sealed class Cause {
  data class Frame(val frame: com.android.tools.idea.insights.Frame) : Cause()
  data class Throwable(val exceptionType: String) : Cause()
}

/** Confidence level of a static analysis match. */
enum class Confidence {
  LOW,
  MEDIUM,
  HIGH
}

/**
 * Represents Stack Frame analysis matches.
 *
 * Points to the matched [PsiElement] and carries the [Confidence] of the match.
 */
data class Match(val element: PsiElement, val confidence: Confidence, val matcherName: String)
