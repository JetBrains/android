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
import java.io.File;


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
    JComponent mapComponent = TabUiUtils.createStyledMapComponent(type.getHeader(myHttpData).getFields());
    if (mapComponent instanceof JTextPane) {
      // If here, we have one or more rows. Add padding to have some space between the last row
      // and the horizontal scroll bar.
      JTextPane headers = (JTextPane)mapComponent;
      headers.setBorder(new JBEmptyBorder(0, 0, 4, 0));

      // Disable line wrapping, since we know we're in a scroller
      JPanel noWrapPanel = new JPanel(new BorderLayout());
      noWrapPanel.add(headers);
      return TabUiUtils.createNestedScrollPane(noWrapPanel);
    }
    else {
      return mapComponent;
    }
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
   * indicate that the target payload is not set.
   */
  @NotNull
  public JComponent createBodyComponent(@NotNull IdeProfilerComponents components, @NotNull ConnectionType type) {
    JComponent payloadComponent;
    Payload payload = type.getPayload(myModel, myHttpData);

    File payloadFile = payload.toFile();
    if (payloadFile.length() > 0) {
      DataViewer viewer = components.createFileViewer(payloadFile);
      JComponent viewerComponent = viewer.getComponent();
      viewerComponent.setName(ID_PAYLOAD_VIEWER);
      // We force a minimum height to make sure that the component always looks reasonable -
      // useful, for example, when we are displaying a bunch of text.
      int minimumHeight = 300;
      int originalHeight = viewer.getDimension() != null ? (int)viewer.getDimension().getHeight() : minimumHeight;
      viewerComponent.setMinimumSize(new Dimension(1, Math.min(minimumHeight, originalHeight)));
      viewerComponent.setBorder(new JBEmptyBorder(6, 0, 0, 0));
      payloadComponent = new JPanel(new TabularLayout("*"));
      payloadComponent.add(viewerComponent, new TabularLayout.Constraint(0, 0));
    }
    else {
      payloadComponent = new JLabel("No body available");
    }
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
  }
}
