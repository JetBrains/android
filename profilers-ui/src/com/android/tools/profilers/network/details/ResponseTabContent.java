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

import com.android.tools.adtui.ui.HideablePanel;
import com.android.tools.profilers.IdeProfilerComponents;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.network.NetworkConnectionsModel;
import com.android.tools.profilers.network.details.HttpDataViewModel.ConnectionType;
import com.android.tools.profilers.network.httpdata.HttpData;
import com.google.common.annotations.VisibleForTesting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.android.tools.profilers.network.details.TabUiUtils.SECTION_TITLE_HEADERS;

/**
 * Tab which shows a response's headers and payload.
 */
final class ResponseTabContent extends TabContent {

  private static final String ID_BODY_COMPONENT = "BODY_COMPONENT";

  private final IdeProfilerComponents myComponents;
  private final NetworkConnectionsModel myModel;
  private JPanel myPanel;

  public ResponseTabContent(@NotNull IdeProfilerComponents components,
                            @NotNull NetworkConnectionsModel model) {
    myComponents = components;
    myModel = model;
  }

  @NotNull
  @Override
  public String getTitle() {
    return "Response";
  }

  @NotNull
  @Override
  protected JComponent createComponent() {
    myPanel = TabUiUtils.createVerticalPanel(TabUiUtils.TAB_SECTION_VGAP);
    return TabUiUtils.createVerticalScrollPane(myPanel);
  }

  @Override
  public void populateFor(@Nullable HttpData data) {
    myPanel.removeAll();
    if (data == null) {
      return;
    }

    HttpDataViewModel httpDataViewModel = new HttpDataViewModel(myModel, data);
    JComponent headersComponent = httpDataViewModel.createHeaderComponent(ConnectionType.RESPONSE);
    myPanel.add(TabUiUtils.createHideablePanel(SECTION_TITLE_HEADERS, headersComponent, null));

    String bodyTitle = httpDataViewModel.getBodyTitle(ConnectionType.RESPONSE);
    JComponent bodyComponent = httpDataViewModel.createBodyComponent(myComponents, ConnectionType.RESPONSE);
    bodyComponent.setName(ID_BODY_COMPONENT);
    HideablePanel bodyPanel = TabUiUtils.createHideablePanel(bodyTitle, bodyComponent, null);
    myPanel.add(bodyPanel);
  }

  @Override
  public void trackWith(@NotNull FeatureTracker featureTracker) {
    // TODO(b/69739486): Add missing tracking for NEW "Response" tab.
  }

  @Nullable
  @VisibleForTesting
  JComponent findPayloadViewer() {
    JComponent bodyComponent = TabUiUtils.findComponentWithUniqueName(myPanel, ID_BODY_COMPONENT);
    return HttpDataViewModel.findPayloadViewer(bodyComponent);
  }
}
