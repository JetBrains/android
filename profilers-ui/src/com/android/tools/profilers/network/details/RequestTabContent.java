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

import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.ui.HideablePanel;
import com.android.tools.profilers.IdeProfilerComponents;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.network.NetworkConnectionsModel;
import com.android.tools.profilers.network.details.HttpDataViewModel.ConnectionType;
import com.android.tools.profilers.network.httpdata.HttpData;
import com.android.tools.profilers.network.httpdata.Payload;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.util.ui.JBEmptyBorder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Tab which shows a request's headers and payload.
 */
final class RequestTabContent extends TabContent {

  private static final String ID_BODY_COMPONENT = "REQUEST_PAYLOAD_COMPONENT";
  // Use Application Headers as title because the infrastructure added headers of HttpURLConnection
  // may be missed if users do not set.
  private static final String HEADERS_TITLE = "Application Headers";

  private final IdeProfilerComponents myComponents;
  private final NetworkConnectionsModel myModel;
  private JPanel myPanel;

  public RequestTabContent(@NotNull IdeProfilerComponents components,
                           @NotNull NetworkConnectionsModel model) {
    myComponents = components;
    myModel = model;
  }

  @NotNull
  @Override
  public String getTitle() {
    return "Request";
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

    JComponent headersComponent = httpDataViewModel.createHeaderComponent(ConnectionType.REQUEST);
    myPanel.add(TabUiUtils.createHideablePanel(HEADERS_TITLE, headersComponent, null));

    Payload requestPayload = Payload.newRequestPayload(myModel, data);
    JComponent bodyComponent = httpDataViewModel.createBodyComponent(myComponents, ConnectionType.REQUEST);
    bodyComponent.setName(ID_BODY_COMPONENT);
    JComponent northEastComponent = null;
    HttpData.ContentType contentType = data.getRequestHeader().getContentType();
    String contentToParse = "";
    if (contentType.isFormData()) {
      contentToParse = requestPayload.getBytes().toStringUtf8();
    }

    if (!contentToParse.isEmpty()) {
      final CardLayout cardLayout = new CardLayout();
      final JPanel payloadPanel = new JPanel(cardLayout);
      String cardViewParsed = "view parsed";
      String cardViewSource = "view source";

      final Map<String, String> parsedContent = new LinkedHashMap<>();
      Stream<String[]> parsedContentStream = Arrays.stream(contentToParse.trim().split("&")).map(s -> s.split("=", 2));
      parsedContentStream.forEach(a -> parsedContent.put(a[0], a.length > 1 ? a[1] : ""));
      payloadPanel.add(TabUiUtils.createStyledMapComponent(parsedContent), cardViewParsed);
      payloadPanel.add(bodyComponent, cardViewSource);
      bodyComponent = payloadPanel;

      final JLabel toggleLabel = new JLabel(cardViewSource);
      northEastComponent = toggleLabel;
      Color toggleHoverColor = AdtUiUtils.overlayColor(toggleLabel.getBackground().getRGB(), toggleLabel.getForeground().getRGB(), 0.9f);
      Color toggleDefaultColor = AdtUiUtils.overlayColor(toggleLabel.getBackground().getRGB(), toggleHoverColor.getRGB(), 0.6f);
      toggleLabel.setForeground(toggleDefaultColor);
      toggleLabel.setFont(UIManager.getFont("Label.font").deriveFont(TabUiUtils.FIELD_FONT_SIZE));
      toggleLabel.setBorder(new JBEmptyBorder(0, 10, 0, 5));
      toggleLabel.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          toggleLabel.setText(cardViewSource.equals(toggleLabel.getText()) ? cardViewParsed : cardViewSource);
          cardLayout.next(payloadPanel);
        }

        @Override
        public void mouseEntered(MouseEvent e) {
          toggleLabel.setForeground(toggleHoverColor);
        }

        @Override
        public void mouseExited(MouseEvent e) {
          toggleLabel.setForeground(toggleDefaultColor);
        }
      });
    }

    HideablePanel bodyPanel =
      TabUiUtils.createHideablePanel(httpDataViewModel.getBodyTitle(ConnectionType.REQUEST), bodyComponent, northEastComponent);
    myPanel.add(bodyPanel);
  }

  @Override
  public void trackWith(@NotNull FeatureTracker featureTracker) {
    featureTracker.trackSelectNetworkDetailsRequest();
  }

  @Nullable
  @VisibleForTesting
  JComponent findPayloadViewer() {
    JComponent bodyComponent = TabUiUtils.findComponentWithUniqueName(myPanel, ID_BODY_COMPONENT);
    return HttpDataViewModel.findPayloadViewer(bodyComponent);
  }
}
