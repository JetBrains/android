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
package com.android.tools.idea.deviceManager.avdmanager.actions

import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.internal.avd.AvdManager
import com.android.utils.HtmlBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import java.util.HashMap

/**
 * Display a summary of the AVD
 */
class AvdSummaryAction(avdInfoProvider: AvdInfoProvider) : AvdUiAction(
  avdInfoProvider, "View Details", "View details for debugging", AllIcons.General.BalloonInformation
) {
  override fun actionPerformed(e: ActionEvent) {
    val info = avdInfo ?: return
    val htmlBuilder = HtmlBuilder().apply {
      openHtmlBody()
      addHtml("<br>Name: ").add(info.name)
      addHtml("<br>CPU/ABI: ").add(AvdInfo.getPrettyAbiType(info))
      addHtml("<br>Path: ").add(info.dataFolderPath)
      if (info.status != AvdInfo.AvdStatus.OK) {
        addHtml("<br>Error: ").add(info.errorMessage!!)
      }
      else {
        addHtml("<br>Target: ").add(String.format("%1\$s (API level %2\$s)", info.tag, info.androidVersion.apiString))

        // display some extra values.
        val properties = info.properties
        val skin = properties[AvdManager.AVD_INI_SKIN_NAME]
        if (skin != null) {
          addHtml("<br>Skin: ").add(skin)
        }
        val sdcard = properties[AvdManager.AVD_INI_SDCARD_SIZE] ?: properties[AvdManager.AVD_INI_SDCARD_PATH]
        if (sdcard != null) {
          addHtml("<br>SD Card: ").add(sdcard)
        }
        val snapshot = properties[AvdManager.AVD_INI_SNAPSHOT_PRESENT]
        if (snapshot != null) {
          addHtml("<br>Snapshot: ").add(snapshot)
        }

        // display other hardware
        val copy = HashMap(properties).apply {
          // remove stuff we already displayed (or that we don't want to display)
          remove(AvdManager.AVD_INI_ABI_TYPE)
          remove(AvdManager.AVD_INI_CPU_ARCH)
          remove(AvdManager.AVD_INI_SKIN_NAME)
          remove(AvdManager.AVD_INI_SKIN_PATH)
          remove(AvdManager.AVD_INI_SDCARD_SIZE)
          remove(AvdManager.AVD_INI_SDCARD_PATH)
          remove(AvdManager.AVD_INI_IMAGES_2)
        }
        copy.forEach { (k, v) ->
          addHtml("<br>").add(k).add(": ").add(v)
        }
      }
      closeHtmlBody()
    }
    val options = arrayOf("Copy to Clipboard and Close", "Close")
    val i = Messages.showDialog(
      project, htmlBuilder.html, "Details for ${info.name}",
      options, 0, AllIcons.General.InformationDialog
    )
    if (i == 0) {
      CopyPasteManager.getInstance().setContents(StringSelection(StringUtil.stripHtml(htmlBuilder.html, true)))
    }
  }

  override fun isEnabled(): Boolean = avdInfo != null
}