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
package com.android.tools.idea.explainer

import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.project.Project
import javax.swing.Icon

/**
 * Service which lets clients access the explanation service and request an explanation.
 *
 * Use the companion object [IssueExplainer.get] method to look up an implementation of this
 * service.
 *
 * Some type of hints could be passed in such as "logcat entry", "lint issue", "syntax error",
 * "IDE error", and so on.
 */
open class IssueExplainer {
  /**
   * Is the explanation service available -- e.g. user is logged in and the setup flow is completed.
   */
  open fun isAvailable(): Boolean = false

  /** Opens (and focuses) the explanation window. */
  open fun open(project: Project) {}

  /**
   * Returns a suitable label to use for the quickfix or hyperlink labels to request an explanation
   */
  open fun getIcon(): Icon? = null

  /**
   * Returns a suitable label to use for the quickfix or hyperlink labels to request an explanation
   */
  open fun getShortLabel(): String? = null

  /**
   * Returns a suitable label to use for the quickfix or hyperlink labels to request an explanation
   */
  fun getConsoleLinkText(): String = ">> ${getShortLabel()}"

  /**
   * Returns a suitable label to use for the quickfix or hyperlink labels to request an explanation
   */
  @JvmOverloads
  open fun getFixLabel(item: String = "error"): String = "Explain this $item"

  enum class RequestKind {
    ERROR,
    INFO,
    LOGCAT,
    SYNC_ISSUE,
    BUILD_ISSUE,
  }

  /**
   * Opens the window, and sets the query string to the given [request]. Can optionally also pass in
   * some authoritative documentation, as well as one or more authoritative links.
   */
  @JvmOverloads
  open fun explain(
      project: Project,
      request: String,
      requestKind: RequestKind,
      extraDocumentation: String? = null,
      extraUrls: List<String> = emptyList()
  ) {}

  companion object {
    @JvmStatic
    fun get(): IssueExplainer =
      getApplication().getService(IssueExplainer::class.java) ?: IssueExplainer()
  }
}
