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
package com.android.tools.idea.appinspection.inspector.api

import com.android.annotations.concurrency.UiThread

/**
 * A set of utility methods used for communicating requests to the IDE.
 *
 * Note that this class, despite containing the word "IDE" in its name, does not belong in the
 * `ide` module -- rather, we expect the `ide` module to implement it. This interface is just an
 * API for making requests that will make sense if we are running in the context of an IDE, but can
 * be ignored otherwise (for example, in tests).
 */
interface AppInspectionIdeServices {
  enum class Severity {
    INFORMATION,
    ERROR,
  }

  /**
   * Shows the App Inspection tool window.
   * @param callback A callback executed right after the window shows up. The call is asynchronous since it may require animation.
   */
  @UiThread
  fun showToolWindow(@UiThread callback: () -> Unit = { })

  /**
   * Shows a notification which will be reported by the tool window with UX that is consistent across all inspectors.
   *
   * @param content Content text for this notification, which can contain html. If an `<a href=.../>` tag is present
   *   and the user clicks on it, the [hyperlinkClicked] parameter will be triggered.
   * @param title A title to show for this notification, which can be empty
   * @param hyperlinkClicked If the notification contains a hyperlink, this callback will be fired if the user clicks it.
   */
  @UiThread
  fun showNotification(content: String,
                       title: String = "",
                       severity: Severity = Severity.INFORMATION,
                       @UiThread hyperlinkClicked: () -> Unit = {})


  class CodeLocation private constructor(val fileName: String?, val fqcn: String?, val lineNumber: Int?) {
    companion object {
      fun forClass(fqcn: String) = CodeLocation(null, fqcn, null)
      fun forFile(fileName: String, lineNumber: Int? = null) = CodeLocation(fileName, null, lineNumber)
    }
  }

  /**
   * Navigate to the target code location.
   *
   * This method may do expensive work to convert the [CodeLocation] to an actual navigation point before finally doing
   * the navigation, which is why it is a suspend function and should get launched on a background thread.
   */
  suspend fun navigateTo(codeLocation: CodeLocation)

  /**
   * Returns true if the tab corresponding to [inspectorId] is currently selected in the App Inspection tool window.
   */
  fun isTabSelected(inspectorId: String): Boolean
}

open class AppInspectionIdeServicesAdapter : AppInspectionIdeServices {
  override fun showToolWindow(callback: () -> Unit) {}
  override fun showNotification(content: String, title: String, severity: AppInspectionIdeServices.Severity, hyperlinkClicked: () -> Unit) {}
  override suspend fun navigateTo(codeLocation: AppInspectionIdeServices.CodeLocation) {}
  override fun isTabSelected(inspectorId: String) = false
}
