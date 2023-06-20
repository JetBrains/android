/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.logcat.hyperlinks

/**
 * Interface for detecting hyperlinks.
 */
internal interface HyperlinkDetector {
  /**
   * Detects hyperlinks in a region of text.
   *
   * @param startLine Start line of region to process (zero based)
   * @param endLine End line of region to process (zero based)
   * @param sdk The sdk level of the selected device or null if it is not known (should not happen)
   */
  fun detectHyperlinks(startLine: Int, endLine: Int, sdk: Int?)
}
