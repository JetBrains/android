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

import com.android.ddmlib.ClientData
import com.android.ddmlib.logcat.LogCatMessage

/**
 * A [AndroidLogcatFilter] that filters on the selected app.
 */
data class SelectedProcessFilter(private var client: ClientData?) : AndroidLogcatFilter {
  // getPackageName name is not just a trivial getter so cache it
  private var packageName = client?.packageName

  override fun getName(): String = AndroidLogcatView.getSelectedAppFilter()

  override fun isApplicable(logCatMessage: LogCatMessage): Boolean =
    client?.let {
      it.pid == logCatMessage.header.pid || it.packageName == logCatMessage.header.appName
    } ?: true


  override fun setClient(client: ClientData?) {
    this.client = client
    packageName = client?.packageName
  }
}