/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.avdmanager;

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * Display a summary of the AVD
 */
public class AvdSummaryAction extends AvdUiAction {
  private static final Logger LOG = Logger.getInstance(AvdSummaryAction.class);

  public AvdSummaryAction(AvdInfoProvider avdInfoProvider) {
    super(avdInfoProvider, "View Details", "View details for debugging", AllIcons.General.BalloonInformation);
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    AvdInfo info = getAvdInfo();
    if (info == null) {
      return;
    }

    StringBuilder sb = new StringBuilder();
    sb.append("<html>");
    sb.append("<br>Name: ").append(info.getName());
    sb.append("<br>CPU/ABI: ").append(AvdInfo.getPrettyAbiType(info));

    sb.append("<br>Path: ").append(info.getDataFolderPath());

    if (info.getStatus() != AvdInfo.AvdStatus.OK) {
      sb.append("<br>Error: ").append(info.getErrorMessage());
    } else {
      IAndroidTarget target = info.getTarget();
      AndroidVersion version = target.getVersion();
      sb.append("<br>Target: ").append(String.format("%s (API level %s)", target.getName(), version.getApiString()));

      // display some extra values.
      Map<String, String> properties = info.getProperties();
      String skin = properties.get(AvdManager.AVD_INI_SKIN_NAME);
      if (skin != null) {
        sb.append("<br>Skin: ").append(skin);
      }

      String sdcard = properties.get(AvdManager.AVD_INI_SDCARD_SIZE);
      if (sdcard == null) {
        sdcard = properties.get(AvdManager.AVD_INI_SDCARD_PATH);
      }
      if (sdcard != null) {
        sb.append("<br>SD Card: ").append(sdcard);
      }

      String snapshot = properties.get(AvdManager.AVD_INI_SNAPSHOT_PRESENT);
      if (snapshot != null) {
        sb.append("<br>Snapshot: ").append(snapshot);
      }

      // display other hardware
      HashMap<String, String> copy = new HashMap<String, String>(properties);
      // remove stuff we already displayed (or that we don't want to display)
      copy.remove(AvdManager.AVD_INI_ABI_TYPE);
      copy.remove(AvdManager.AVD_INI_CPU_ARCH);
      copy.remove(AvdManager.AVD_INI_SKIN_NAME);
      copy.remove(AvdManager.AVD_INI_SKIN_PATH);
      copy.remove(AvdManager.AVD_INI_SDCARD_SIZE);
      copy.remove(AvdManager.AVD_INI_SDCARD_PATH);
      copy.remove(AvdManager.AVD_INI_IMAGES_1);
      copy.remove(AvdManager.AVD_INI_IMAGES_2);

      if (copy.size() > 0) {
        for (Map.Entry<String, String> entry : copy.entrySet()) {
          sb.append("<br>").append(entry.getKey()).append(": ").append(entry.getValue());
        }
      }
    }
    sb.append("</html>");
    String[] options = {"Copy to Clipboard and Close", "Close"};
    int i = JOptionPane
      .showOptionDialog(null, sb.toString(), "Details for " + info.getName(), JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE,
                        null, options, options[1]);
    if (i == 0) {
      CopyPasteManager.getInstance().setContents(new StringSelection(StringUtil.stripHtml(sb.toString(), true)));
    }
  }

  @Override
  public boolean isEnabled() {
    return getAvdInfo() != null;
  }
}
