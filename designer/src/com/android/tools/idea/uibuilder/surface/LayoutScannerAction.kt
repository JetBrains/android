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
package com.android.tools.idea.uibuilder.surface

import com.android.tools.idea.flags.StudioFlags
import java.util.concurrent.CompletableFuture

/**
 * Controller for layout scanner that checks the layout and produces lint checks.
 * It controls when to run/pause/resume Accessibility Testing Framework.
 * By default scanner is enabled/resumed.
 */
interface LayoutScannerControl {
  /** Return the scanner capable of checking the layout. */
  val scanner: NlLayoutScanner

  /** Pause the scanner until it is resumed by [resume] */
  fun pause()

  /** Resume the scanner until it is paused by [pause] */
  fun resume()
}

/** Configuration for layout scanner */
interface LayoutScannerConfiguration {

  /** Returns true if it layout scanner should be enabled. False otherwise. */
  var isLayoutScannerEnabled: Boolean

  /**
   * New experimental settings to always enable scanner instead of only when it is user triggered.
   * It takes effect only when [isLayoutScannerEnabled] is already on.
   */
  var isScannerAlwaysOn: Boolean

  companion object {

    /** Configuration for when layout scanner is not applicable. */
    @JvmStatic
    val DISABLED = object: LayoutScannerConfiguration {
      override var isLayoutScannerEnabled: Boolean
        get() = false
        set(value) { }

      override var isScannerAlwaysOn: Boolean
        get() = false
        set(value) {}
    }
  }
}

/** Configuration for when layout scanner is available. */
class LayoutScannerEnabled : LayoutScannerConfiguration {

  override var isLayoutScannerEnabled: Boolean = StudioFlags.NELE_LAYOUT_SCANNER_IN_EDITOR.get()

  override var isScannerAlwaysOn: Boolean = StudioFlags.NELE_LAYOUT_SCANNER_IN_EDITOR.get()
}