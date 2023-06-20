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
package com.android.tools.idea.common.surface

import com.android.tools.idea.common.error.Issue
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.rendering.RenderResult

/**
 * Controller for layout scanner that checks the layout and produces lint checks.
 * It controls when to run/pause/resume Accessibility Testing Framework.
 * By default scanner is enabled/resumed.
 */
interface LayoutScannerControl {

  /**  Returns the list of accessibility issues created by ATF. */
  val issues: Set<Issue>

  /** Pause the scanner until it is resumed by [resume] */
  fun pause()

  /** Resume the scanner until it is paused by [pause] */
  fun resume()

  /** Validate the layout and update the lint accordingly. */
  fun validateAndUpdateLint(renderResult: RenderResult, model: NlModel)
}

/** Configuration for layout scanner */
interface LayoutScannerConfiguration {

  /** Returns true if it layout scanner should be enabled. False otherwise. */
  var isLayoutScannerEnabled: Boolean

  /**
   * Determines default behaviour on whether the scanner result should be integrated with issue panel.
   * If true, it'll be integrated automatically. If false, results will not be used for anything.
   */
  var isIntegrateWithDefaultIssuePanel: Boolean

  companion object {

    /** Configuration for when layout scanner is not applicable. */
    @JvmStatic
    val DISABLED = object: LayoutScannerConfiguration {
      override var isLayoutScannerEnabled: Boolean
        get() = false
        set(value) { }

      override var isIntegrateWithDefaultIssuePanel: Boolean
        get() = false
        set(value) {}
    }
  }
}

/** Configuration for when layout scanner is available. */
class LayoutScannerEnabled : LayoutScannerConfiguration {

  override var isLayoutScannerEnabled: Boolean = true

  override var isIntegrateWithDefaultIssuePanel: Boolean = true
}