/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.common.error

import com.android.tools.idea.common.model.NlComponent
import com.intellij.lang.annotation.HighlightSeverity
import java.util.stream.Stream
import javax.swing.event.HyperlinkListener

/**
 * Represent an Error that can be displayed in the [IssuePanel].
 */
abstract class Issue {
  /** A short summary of the error description */
  abstract val summary: String

  /** The description of the error. It can contains some HTML tag */
  abstract val description: String

  abstract val severity: HighlightSeverity

  /** An indication of the origin of the error like the Component causing the error. */
  abstract val source: NlComponent?

  /** The priority between 1 and 10. */
  abstract val category: String

  /** Allows the [Issue] to return an HyperlinkListener to handle embedded links */
  open val hyperlinkListener: HyperlinkListener? = null

  /**
   * Returns a Steam of pair containing the description of the fix as the first element
   * and a [Runnable] to execute the fix
   */
  open val fixes: Stream<Fix>
    get() = Stream.empty()

  override fun equals(o: Any?): Boolean {
    if (o === this) return true
    if (o !is Issue) return false
    return o.severity == severity && o.summary == summary && o.description == description && o.category == category && o.source === source
  }

  override fun hashCode(): Int {
    var result = 13
    result += 17 * severity.hashCode()
    result += 19 * summary.hashCode()
    result += 23 * description.hashCode()
    result += 29 * category.hashCode()
    val source = source
    if (source != null) {
      result += 31 * source.hashCode()
    }
    return result
  }

  /**
   * Representation of a quick fix for the issue.
   * @param description Description of the fix
   * @param runnable    Action to execute the fix
   */
  data class Fix(val description: String, val runnable: Runnable)

  companion object {
    const val EXECUTE_FIX = "Execute Fix: "
  }
}