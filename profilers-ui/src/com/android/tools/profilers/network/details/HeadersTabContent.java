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
package com.android.tools.profilers.network.details;

import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.TreeWalker;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.network.httpdata.HttpData;
import com.intellij.util.ui.JBEmptyBorder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;
import java.util.TreeMap;

/**
 * Tab which shows a list of request and response headers for a request.
 *
 * @deprecated This is going away and will be replaced with separate request/response tabs
 */
@Deprecated
final class HeadersTabContent extends TabContent {

  private JPanel myPanel;

  @NotNull
  private static JPanel createHeaderSection(@NotNull String title, @NotNull HttpData.Header header) {
    JPanel panel = new JPanel(new TabularLayout("*").setVGap(TabUiUtils.SECTION_VGAP));
    panel.setBorder(new JBEmptyBorder(0, TabUiUtils.HGAP, 0, 0));

    JLabel titleLabel = new OverviewTabContent.NoWrapBoldLabel(title);
    titleLabel.setFont(titleLabel.getFont().deriveFont(TabUiUtils.TITLE_FONT_SIZE));
    panel.add(titleLabel, new TabularLayout.Constraint(0, 0));
    Map<String, String> sortedMap = new TreeMap<>(header.getFields());
    panel.add(TabUiUtils.createMapComponent(sortedMap), new TabularLayout.Constraint(1, 0));
    new TreeWalker(panel).descendantStream().forEach(c -> {
      if (c != titleLabel) {
        TabUiUtils.adjustFont(c);
      }
    });

    panel.setName(TabUiUtils.toTestName(title));
    return panel;
  }

  @NotNull
  @Override
  public String getTitle() {
    return "Headers";
  }

  @NotNull
  @Override
  protected JComponent createComponent() {
    myPanel = TabUiUtils.createVerticalPanel(TabUiUtils.PAGE_VGAP);
    return TabUiUtils.createVerticalScrollPane(myPanel);
  }

  @Override
  public void populateFor(@Nullable HttpData data) {
    myPanel.removeAll();
    if (data == null) {
      return;
    }

    myPanel.add(createHeaderSection("Response Headers", data.getResponseHeader()));
    myPanel.add(TabUiUtils.createSeparator());
    myPanel.add(createHeaderSection("Request Headers", data.getRequestHeader()));
  }

  @Override
  public void trackWith(@NotNull FeatureTracker featureTracker) {
    featureTracker.trackSelectNetworkDetailsHeaders();
  }
}
