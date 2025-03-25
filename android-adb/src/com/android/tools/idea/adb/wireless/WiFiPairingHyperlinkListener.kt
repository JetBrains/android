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
package com.android.tools.idea.adb.wireless

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent.createEvent
import com.intellij.ui.HyperlinkAdapter
import javax.swing.event.HyperlinkEvent

/** Normal (non-testing) handler for hyperlinks in WiFi pairing UI. */
object WiFiPairingHyperlinkListener : HyperlinkAdapter() {
  override fun hyperlinkActivated(e: HyperlinkEvent) {
    if (e.description == Urls.openSdkManager) {
      ActionManager.getInstance()
        .getAction("Android.RunAndroidSdkManager")
        .actionPerformed(
          createEvent(
            { dataId: String -> null },
            null,
            ActionPlaces.UNKNOWN,
            ActionUiKind.NONE,
            null,
          )
        )
    } else if (e.description == Urls.openAdbSettings) {
      ActionManager.getInstance()
        .getAction("Android.AdbSettings")
        .actionPerformed(
          createEvent(
            { dataId: String -> null },
            null,
            ActionPlaces.UNKNOWN,
            ActionUiKind.NONE,
            null,
          )
        )
    } else if (e.url != null) {
      BrowserUtil.browse(e.url)
    }
  }
}
