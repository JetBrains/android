/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.ibm.google.onboardingandauthentication.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.window.core.layout.WindowWidthSizeClass
import com.ibm.google.onboardingandauthentication.R
import com.ibm.google.onboardingandauthentication.data.WalkthroughStepModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SELECTED_DATE_FORMAT = "MM/dd/yyyy"

object Utils {
  fun convertMillisToDate(dateFormat: String = SELECTED_DATE_FORMAT, millis: Long): String {
    val formatter = SimpleDateFormat(dateFormat, Locale.getDefault())
    return formatter.format(Date(millis))
  }

  /**
   * Returns the appropriate icon based on the text field's value and error state.
   *
   * @param value The current value of the text field.
   * @param isError `true` if the text field is in an error state; otherwise, `false`.
   * @return The [ImageVector] for the clear or error icon, or `null` if no icon should be shown.
   */
  fun getClearOrErrorIcon(value: String = "", isError: Boolean = false): ImageVector? {
    return when {
      value.trim().isNotEmpty() && !isError -> Icons.Outlined.Cancel
      isError -> Icons.Outlined.Error
      else -> null
    }
  }

  /**
   * Returns the appropriate string resource ID for the icon's content description based on the
   * error state of the text field.
   *
   * @param isError `true` if the text field is in an error state; otherwise, `false`.
   * @return The string resource ID for the error or clear icon description.
   */
  fun getClearOrErrorIconDescription(isError: Boolean = false): Int {
    return when {
      isError -> R.string.error
      else -> R.string.clear
    }
  }

  /**
   * Calculates the progress as a fraction based on the current index and the total number of
   * images.
   *
   * @param currentIndex The current index in the list of images.
   * @param images The list of images to calculate progress for.
   * @return A [Float] representing the progress, where 0f means no progress and 1f means full
   *   progress.
   */
  fun getProgress(currentIndex: Int, images: List<WalkthroughStepModel>): Float =
    if (images.isNotEmpty()) (currentIndex + 1).toFloat() / images.size else 0f

  /**
   * Determines the maximum width fraction for layout based on the current window width size class.
   *
   * This composable reads the [WindowWidthSizeClass] from the current adaptive window information
   * and returns a `Float` representing the fraction of available width that content should occupy.
   *
   * Breakdown of returned values:
   * - **Compact** (width < 600dp): returns `1f`, content takes full width.
   * - **Medium** (600dp ≤ width < 840dp): returns `0.8f`, content takes 60% of width.
   * - **Expanded** (width ≥ 840dp): returns `0.5f`, content takes 50% of width.
   *
   * @return A `Float` in the range (0f, 1f] indicating the maximum width fraction.
   */
  @Composable
  fun maxWidth(): Float {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass

    return when (windowSizeClass) {
      WindowWidthSizeClass.MEDIUM -> 0.8f
      WindowWidthSizeClass.EXPANDED -> 0.5f
      else -> 1f
    }
  }
}
