/*
 * Copyright (C) 2020 The Android Open Source Project
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

fun createIssue(severity: HighlightSeverity, source: NlComponent? = null): Issue {
  return object : Issue() {
    override fun getSummary(): String { return "" }

    override fun getDescription(): String { return "" }

    override fun getSeverity(): HighlightSeverity {
      return severity
    }

    override fun getSource(): NlComponent? {
      return source
    }

    override fun getCategory(): String {
      return ""
    }

    override fun getFixes(): Stream<Fix> {
      return ArrayList<Issue.Fix>().stream()
    }

    override fun getHyperlinkListener(): HyperlinkListener? {
      return null
    }
  }
}