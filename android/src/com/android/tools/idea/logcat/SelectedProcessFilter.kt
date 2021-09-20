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
package com.android.tools.idea.logcat

import com.android.ddmlib.logcat.LogCatMessage

/**
 * A [AndroidLogcatFilter] that filters on the selected app.
 *
 * @param pid the process id of the selected app
 * @param packageName the package name of the selected app as defined in the manifest. Null means we could not obtain the package name and
 * will only match on pid. Note that since [com.android.ddmlib.logcat.LogCatMessage#appName] is not nullable, there is no need for special
 * handling of the null case, a simple == will suffice.
 */
data class SelectedProcessFilter(private val pid: Int, private val packageName: String?) : AndroidLogcatFilter {
  override fun getName(): String = AndroidLogcatView.getSelectedAppFilter()

  override fun isApplicable(logCatMessage: LogCatMessage): Boolean =
    pid == logCatMessage.header.pid || packageName == logCatMessage.header.appName
}