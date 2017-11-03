/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers;

import com.android.tools.adtui.TabularLayout;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.components.labels.BoldLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * A panel with a header, a description, and an (optional) URL, useful for showing information to the user
 * with a consistent look and feel across our monitors.
 */
public class InfoMessagePanel extends JPanel {

  public static final class UrlData {
    @NotNull
    private final String myText;
    @NotNull
    private final String myUrl;

    public UrlData(@NotNull String text, @NotNull String url) {
      myText = text;
      myUrl = url;
    }
  }

  public InfoMessagePanel(@NotNull String header, @NotNull String description, @Nullable UrlData urlData) {
    super(new TabularLayout("*", "*,Fit,Fit,*"));
    JLabel headerLabel = new BoldLabel(header, SwingConstants.CENTER);
    headerLabel.setFont(headerLabel.getFont().deriveFont(14.f));
    add(headerLabel, new TabularLayout.Constraint(1, 0));
    JPanel descriptionPanel = new JPanel();
    descriptionPanel.add(new JLabel(description));
    if (urlData != null) {
      HyperlinkLabel learnMoreLink = new HyperlinkLabel(urlData.myText);
      learnMoreLink.setHyperlinkTarget(urlData.myUrl);
      descriptionPanel.add(learnMoreLink);
    }
    add(descriptionPanel, new TabularLayout.Constraint(2, 0));
  }
}
