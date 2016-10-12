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
import com.android.sdklib.ISystemImage;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.utils.HtmlBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;

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

    HtmlBuilder htmlBuilder = new HtmlBuilder();
    htmlBuilder.openHtmlBody();
    htmlBuilder.addHtml("<br>Name: ").add(info.getName());
    htmlBuilder.addHtml("<br>CPU/ABI: ").add(AvdInfo.getPrettyAbiType(info));

    htmlBuilder.addHtml("<br>Path: ").add(info.getDataFolderPath());

    if (info.getStatus() != AvdInfo.AvdStatus.OK) {
      htmlBuilder.addHtml("<br>Error: ").add(info.getErrorMessage());
    } else {
      AndroidVersion version = info.getAndroidVersion();
      htmlBuilder.addHtml("<br>Target: ").add(String.format("%1$s (API level %2$s)", info.getTag(), version.getApiString()));

      // display some extra values.
      Map<String, String> properties = info.getProperties();
      String skin = properties.get(AvdManager.AVD_INI_SKIN_NAME);
      if (skin != null) {
        htmlBuilder.addHtml("<br>Skin: ").add(skin);
      }

      String sdcard = properties.get(AvdManager.AVD_INI_SDCARD_SIZE);
      if (sdcard == null) {
        sdcard = properties.get(AvdManager.AVD_INI_SDCARD_PATH);
      }
      if (sdcard != null) {
        htmlBuilder.addHtml("<br>SD Card: ").add(sdcard);
      }

      String snapshot = properties.get(AvdManager.AVD_INI_SNAPSHOT_PRESENT);
      if (snapshot != null) {
        htmlBuilder.addHtml("<br>Snapshot: ").add(snapshot);
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
      copy.remove(AvdManager.AVD_INI_IMAGES_2);

      if (copy.size() > 0) {
        for (Map.Entry<String, String> entry : copy.entrySet()) {
          htmlBuilder.addHtml("<br>").add(entry.getKey()).add(": ").add(entry.getValue());
        }
      }
    }
    htmlBuilder.closeHtmlBody();
    String[] options = {"Copy to Clipboard and Close", "Close"};
    int i = Messages.showDialog(getProject(), htmlBuilder.getHtml(), "Details for " + info.getName(),
                                options, 0, AllIcons.General.InformationDialog);
    if (i == 0) {
      CopyPasteManager.getInstance().setContents(new StringSelection(StringUtil.stripHtml(htmlBuilder.getHtml(), true)));
    }
  }

  @Override
  public boolean isEnabled() {
    return getAvdInfo() != null;
  }
}
