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
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.profilers.ContentType;
import com.android.tools.profilers.IdeProfilerComponents;
import com.android.tools.profilers.network.NetworkConnectionsModel;
import com.android.tools.profilers.network.httpdata.HttpData;
import com.android.tools.profilers.network.httpdata.Payload;
import com.android.tools.profilers.stacktrace.DataViewer;
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

import static com.android.tools.profilers.ProfilerFonts.STANDARD_FONT;

/**
 * A view model which wraps a target {@link HttpData} and can create useful, shared UI components
 * and display values from it.
 */
final class HttpDataViewModel {
  private static final String ID_PAYLOAD_VIEWER = "PAYLOAD_VIEWER";

  private final NetworkConnectionsModel myModel;
  private final HttpData myHttpData;

  public HttpDataViewModel(@NotNull NetworkConnectionsModel model, @NotNull HttpData httpData) {
    myModel = model;
    myHttpData = httpData;
  }

  /**
   * Search for the payload {@link DataViewer} inside a component returned by
   * {@link #createBodyComponent(IdeProfilerComponents, ConnectionType)}. If this returns
   * {@code null}, that means no payload viewer was created for it, e.g. the http data
   * instance didn't have a payload and a "No data found" label was returned instead.
   */
  @VisibleForTesting
  @Nullable
  static JComponent findPayloadViewer(@Nullable JComponent body) {
    if (body == null) {
      return null;
    }
    return TabUiUtils.findComponentWithUniqueName(body, ID_PAYLOAD_VIEWER);
  }

  /**
   * Creates a component which displays the current {@link HttpData}'s headers as a list of
   * key/value pairs.
   */
  @NotNull
  public JComponent createHeaderComponent(@NotNull ConnectionType type) {
    return TabUiUtils.createStyledMapComponent(type.getHeader(myHttpData).getFields());
  }

  /**
   * Returns a title which should be shown above the body component created by
   * {@link #createBodyComponent(IdeProfilerComponents, ConnectionType)}.
   */
  @NotNull
  public String getBodyTitle(@NotNull ConnectionType type) {
    HttpData.Header header = type.getHeader(myHttpData);
    HttpData.ContentType contentType = header.getContentType();
    if (contentType.isEmpty()) {
      return "Body";
    }
    return String.format("Body ( %s )", contentType.getTypeDisplayName());
  }

  /**
   * Returns a payload component which can display the underlying data of the current
   * {@link HttpData}'s {@link Payload}. If the payload is empty, this will return a label to
   * indicate that the target payload is not set. If the payload is not empty and is supported for
   * parsing, this will return a component containing both the raw data view and the parsed view.
   */
  @NotNull
  public JComponent createBodyComponent(@NotNull IdeProfilerComponents components, @NotNull ConnectionType type) {
    Payload payload = type.getPayload(myModel, myHttpData);
    if (payload.getBytes().isEmpty()) {
      return new JLabel("Not available");
    }
    JComponent rawDataComponent = createRawDataComponent(payload, components);
    JComponent parsedDataComponent = createParsedDataComponent(payload, components);

    JComponent bodyComponent = rawDataComponent;
    JComponent northEastComponent = null;
    if (parsedDataComponent != null) {
      final CardLayout cardLayout = new CardLayout();
      final JPanel payloadPanel = new JPanel(cardLayout);
      String cardViewParsed = "View Parsed";
      String cardViewSource = "View Source";
      parsedDataComponent.setName(cardViewParsed);
      rawDataComponent.setName(cardViewSource);
      payloadPanel.add(parsedDataComponent, cardViewParsed);
      payloadPanel.add(rawDataComponent, cardViewSource);
      bodyComponent = payloadPanel;

      final JLabel toggleLabel = new JLabel(cardViewSource);
      northEastComponent = toggleLabel;
      Color toggleHoverColor = AdtUiUtils.overlayColor(toggleLabel.getBackground().getRGB(), toggleLabel.getForeground().getRGB(), 0.9f);
      Color toggleDefaultColor = AdtUiUtils.overlayColor(toggleLabel.getBackground().getRGB(), toggleHoverColor.getRGB(), 0.6f);
      toggleLabel.setForeground(toggleDefaultColor);
      toggleLabel.setFont(STANDARD_FONT);
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
    bodyComponent.setName(type.getBodyComponentId());
    return TabUiUtils.createHideablePanel(getBodyTitle(type), bodyComponent, northEastComponent);
  }

  /**
   * Creates the raw data view of given {@link Payload}, assuming the payload data is not empty.
   * TODO: Configures which types of payload uses the ide component and other payloads do not use the ide component.
   */
  @NotNull
  private static JComponent createRawDataComponent(@NotNull Payload payload, @NotNull IdeProfilerComponents components) {
    return createIdeComponent(payload, components);
  }

  /**
   * Creates the parsed data view of given {@link Payload}, assuming the payload data is not empty.
   * TODO: Configures the parsed view for more types of payload.
   */
  @Nullable
  private static JComponent createParsedDataComponent(@NotNull Payload payload, @NotNull IdeProfilerComponents components) {
    if (payload.getContentType().isFormData()) {
      String contentToParse = payload.getBytes().toStringUtf8();
      final Map<String, String> parsedContent = new LinkedHashMap<>();
      Stream<String[]> parsedContentStream = Arrays.stream(contentToParse.trim().split("&")).map(s -> s.split("=", 2));
      parsedContentStream.forEach(a -> parsedContent.put(a[0], a.length > 1 ? a[1] : ""));
      return TabUiUtils.createStyledMapComponent(parsedContent);
    }
    return null;
  }

  @NotNull
  private static JComponent createIdeComponent(@NotNull Payload payload, @NotNull IdeProfilerComponents components) {
    byte[] payloadContent = payload.getBytes().toByteArray();
    String mimeType = payload.getContentType().getMimeType();
    DataViewer viewer = components.createDataViewer(payloadContent, ContentType.fromMimeType(mimeType));
    JComponent viewerComponent = viewer.getComponent();
    viewerComponent.setName(ID_PAYLOAD_VIEWER);
    // We force a minimum height to make sure that the component always looks reasonable -
    // useful, for example, when we are displaying a bunch of text.
    int minimumHeight = 300;
    int originalHeight = viewer.getDimension() != null ? (int)viewer.getDimension().getHeight() : minimumHeight;
    viewerComponent.setMinimumSize(new Dimension(1, Math.min(minimumHeight, originalHeight)));
    viewerComponent.setBorder(new JBEmptyBorder(6, 0, 0, 0));
    JComponent payloadComponent = new JPanel(new TabularLayout("*"));
    payloadComponent.add(viewerComponent, new TabularLayout.Constraint(0, 0));
    return payloadComponent;
  }

  public enum ConnectionType {
    REQUEST,
    RESPONSE;

    @NotNull
    HttpData.Header getHeader(@NotNull HttpData data) {
      return (this == REQUEST) ? data.getRequestHeader() : data.getResponseHeader();
    }

    @NotNull
    Payload getPayload(@NotNull NetworkConnectionsModel model, @NotNull HttpData data) {
      return (this == REQUEST) ? Payload.newRequestPayload(model, data) : Payload.newResponsePayload(model, data);
    }

    @NotNull
    String getBodyComponentId() {
      return (this == REQUEST) ? "REQUEST_PAYLOAD_COMPONENT" : "RESPONSE_PAYLOAD_COMPONENT";
    }
  }
}
