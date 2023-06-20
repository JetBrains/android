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
package com.android.tools.idea.structure.dialog;

import static com.intellij.util.ui.UIUtil.getLabelFont;

import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.ui.components.BrowserLink;
import com.intellij.util.ui.UIUtil;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JLabel;

public class VersionCatalogWarningHeader extends Header {
  public VersionCatalogWarningHeader() {
    super("This project uses Gradle Version Catalogs. There are some limitations.");
    setIcon(AllIcons.General.Warning);
    String externalUrlString = "https://developer.android.com/r/studio-ui/gradle-version-catalogs/known-issues";
    BrowserLink learnMoreLink = new BrowserLink(AllIcons.Ide.External_link_arrow, "Learn more.", null, externalUrlString);
    learnMoreLink.setCursor(new Cursor(Cursor.HAND_CURSOR));
    learnMoreLink.setFont(getLabelFont(UIUtil.FontSize.SMALL));

    addNextComponent(learnMoreLink);
  }
}
