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

import com.android.tools.adtui.LegendComponent;
import com.android.tools.adtui.LegendConfig;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.TreeWalker;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.legend.FixedLegend;
import com.android.tools.adtui.model.legend.Legend;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.android.tools.profilers.FeatureConfig;
import com.android.tools.profilers.IdeProfilerComponents;
import com.android.tools.profilers.ProfilerMonitor;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.network.ConnectionsStateChart;
import com.android.tools.profilers.network.NetworkConnectionsModel;
import com.android.tools.profilers.network.NetworkState;
import com.android.tools.profilers.network.httpdata.HttpData;
import com.android.tools.profilers.network.httpdata.Payload;
import com.android.tools.profilers.stacktrace.DataViewer;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.labels.BoldLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.PlatformColors;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.function.LongFunction;

/**
 * Tab which shows a bunch of useful, high level information for a network request.
 *
 * This tab will be the first one shown to the user when they first select a request.
 */
final class OverviewTabContent extends TabContent {

  private static final LongFunction<String> TIME_FORMATTER =
    time -> time >= 0 ? StringUtil.formatDuration(TimeUnit.MICROSECONDS.toMillis(time)) : "*";

  private static final String ID_CONTENT_TYPE = "CONTENT_TYPE";
  private static final String ID_SIZE = "SIZE";
  private static final String ID_URL = "URL";
  private static final String ID_TIMING = "TIMING";
  private static final String ID_INITIATING_THREAD = "INITIATING_THREAD";
  private static final String ID_OTHER_THREADS = "OTHER_THREADS";
  private static final String ID_RESPONSE_PAYLOAD_VIEWER = "RESPONSE_PAYLOAD_VIEWER";

  private final FeatureConfig myFeatures;
  private final IdeProfilerComponents myComponents;
  private final NetworkConnectionsModel myModel;

  private JPanel myPanel;

  public OverviewTabContent(@NotNull FeatureConfig features,
                            @NotNull IdeProfilerComponents components,
                            @NotNull NetworkConnectionsModel model) {
    myFeatures = features;
    myComponents = components;
    myModel = model;
  }

  private static JComponent createFields(@NotNull HttpData httpData, @Nullable Dimension payloadDimension) {
    JPanel myFieldsPanel = new JPanel(new TabularLayout("Fit,20px,*").setVGap(TabUiUtils.SECTION_VGAP));

    int row = 0;
    myFieldsPanel.add(new NoWrapBoldLabel("Request"), new TabularLayout.Constraint(row, 0));
    myFieldsPanel.add(new JLabel(HttpData.getUrlName(httpData.getUrl())), new TabularLayout.Constraint(row, 2));

    row++;
    myFieldsPanel.add(new NoWrapBoldLabel("Method"), new TabularLayout.Constraint(row, 0));
    myFieldsPanel.add(new JLabel(httpData.getMethod()), new TabularLayout.Constraint(row, 2));
    HttpData.ResponseHeader responseHeader = httpData.getResponseHeader();
    if (responseHeader.getStatusCode() != HttpData.ResponseHeader.NO_STATUS_CODE) {
      row++;
      myFieldsPanel.add(new NoWrapBoldLabel("Status"), new TabularLayout.Constraint(row, 0));
      JLabel statusCode = new JLabel(String.valueOf(responseHeader.getStatusCode()));
      myFieldsPanel.add(statusCode, new TabularLayout.Constraint(row, 2));
    }

    if (payloadDimension != null) {
      row++;
      myFieldsPanel.add(new NoWrapBoldLabel("Dimension"), new TabularLayout.Constraint(row, 0));
      JLabel dimension = new JLabel(String.format("%d x %d", (int)payloadDimension.getWidth(), (int)payloadDimension.getHeight()));
      myFieldsPanel.add(dimension, new TabularLayout.Constraint(row, 2));
    }

    if (!responseHeader.getContentType().isEmpty()) {
      row++;
      myFieldsPanel.add(new NoWrapBoldLabel("Content type"), new TabularLayout.Constraint(row, 0));
      JLabel contentTypeLabel = new JLabel(responseHeader.getContentType().getMimeType());
      contentTypeLabel.setName(ID_CONTENT_TYPE);
      myFieldsPanel.add(contentTypeLabel, new TabularLayout.Constraint(row, 2));
    }

    int contentLength = responseHeader.getContentLength();
    if (contentLength != -1) {
      try {
        row++;
        myFieldsPanel.add(new NoWrapBoldLabel("Size"), new TabularLayout.Constraint(row, 0));
        JLabel contentLengthLabel = new JLabel(StringUtil.formatFileSize(contentLength));
        contentLengthLabel.setName(ID_SIZE);
        myFieldsPanel.add(contentLengthLabel, new TabularLayout.Constraint(row, 2));
      }
      catch (NumberFormatException ignored) {
      }
    }

    row++;
    myFieldsPanel.add(new NoWrapBoldLabel("Initiating thread"), new TabularLayout.Constraint(row, 0));
    JLabel initiatingThreadLabel = new JLabel(httpData.getJavaThreads().get(0).getName());
    initiatingThreadLabel.setName(ID_INITIATING_THREAD);
    myFieldsPanel.add(initiatingThreadLabel, new TabularLayout.Constraint(row, 2));

    if (httpData.getJavaThreads().size() > 1) {
      StringBuilder otherThreadsBuilder = new StringBuilder();
      for (int i = 1; i < httpData.getJavaThreads().size(); ++i) {
        if (otherThreadsBuilder.length() > 0) {
          otherThreadsBuilder.append(", ");
        }
        otherThreadsBuilder.append(httpData.getJavaThreads().get(i).getName());
      }

      row++;
      myFieldsPanel.add(new NoWrapBoldLabel("Other threads"), new TabularLayout.Constraint(row, 0));
      JLabel otherThreadsLabel = new JLabel(otherThreadsBuilder.toString());
      otherThreadsLabel.setName(ID_OTHER_THREADS);
      myFieldsPanel.add(otherThreadsLabel, new TabularLayout.Constraint(row, 2));
    }

    row++;
    NoWrapBoldLabel urlLabel = new NoWrapBoldLabel("URL");
    urlLabel.setVerticalAlignment(SwingConstants.TOP);
    myFieldsPanel.add(urlLabel, new TabularLayout.Constraint(row, 0));
    WrappedHyperlink hyperlink = new WrappedHyperlink(httpData.getUrl());
    hyperlink.setName(ID_URL);
    myFieldsPanel.add(hyperlink, new TabularLayout.Constraint(row, 2));

    row++;
    JSeparator separator = TabUiUtils.createSeparator();
    separator.setMinimumSize(separator.getPreferredSize());
    int gap = TabUiUtils.PAGE_VGAP - TabUiUtils.SECTION_VGAP - (int)separator.getPreferredSize().getHeight() / 2;
    JPanel separatorContainer = new JPanel(new VerticalFlowLayout(0, gap));
    separatorContainer.add(separator);
    myFieldsPanel.add(separatorContainer, new TabularLayout.Constraint(row, 0, 1, 3));

    row++;
    NoWrapBoldLabel timingLabel = new NoWrapBoldLabel("Timing");
    timingLabel.setVerticalAlignment(SwingConstants.TOP);
    myFieldsPanel.add(timingLabel, new TabularLayout.Constraint(row, 0));
    JComponent timingBar = createTimingBar(httpData);
    timingBar.setName(ID_TIMING);
    myFieldsPanel.add(timingBar, new TabularLayout.Constraint(row, 2));

    new TreeWalker(myFieldsPanel).descendantStream().forEach(TabUiUtils::adjustFont);

    return myFieldsPanel;
  }

  @NotNull
  private static JComponent createTimingBar(@NotNull HttpData httpData) {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    Range range = new Range(httpData.getStartTimeUs(),
                            httpData.getEndTimeUs() > 0 ? httpData.getEndTimeUs() : httpData.getStartTimeUs() + 1);
    ConnectionsStateChart connectionsChart = new ConnectionsStateChart(httpData, range);
    connectionsChart.getComponent().setMinimumSize(new Dimension(0, JBUI.scale(28)));
    connectionsChart.setHeightGap(0);
    panel.add(connectionsChart.getComponent());

    long sentTime = -1;
    long receivedTime = -1;
    if (httpData.getDownloadingTimeUs() > 0) {
      sentTime = httpData.getDownloadingTimeUs() - httpData.getStartTimeUs();
      receivedTime = httpData.getEndTimeUs() - httpData.getDownloadingTimeUs();
    }
    else if (httpData.getEndTimeUs() > 0) {
      sentTime = httpData.getEndTimeUs() - httpData.getStartTimeUs();
      receivedTime = 0;
    }

    Legend sentLegend = new FixedLegend("Sent", TIME_FORMATTER.apply(sentTime));
    Legend receivedLegend = new FixedLegend("Received", TIME_FORMATTER.apply(receivedTime));
    LegendComponentModel legendModel = new LegendComponentModel(ProfilerMonitor.LEGEND_UPDATE_FREQUENCY_MS);
    legendModel.add(sentLegend);
    legendModel.add(receivedLegend);

    // TODO: Add waiting time in (currently hidden because it's always 0)
    LegendComponent legend = new LegendComponent.Builder(legendModel).setLeftPadding(0).setVerticalPadding(JBUI.scale(8)).build();
    legend.setFont(legend.getFont().deriveFont(TabUiUtils.FIELD_FONT_SIZE));
    legend.configure(sentLegend,
                     new LegendConfig(LegendConfig.IconType.BOX, connectionsChart.getColors().getColor(NetworkState.SENDING)));
    legend.configure(receivedLegend,
                     new LegendConfig(LegendConfig.IconType.BOX, connectionsChart.getColors().getColor(NetworkState.RECEIVING)));
    legendModel.update(1);
    panel.add(legend);

    return panel;
  }

  @NotNull
  @Override
  public String getTitle() {
    return myFeatures.isNetworkRequestPayloadEnabled() ? "Overview" : "Response";
  }

  @NotNull
  @Override
  protected JComponent createComponent() {
    TabularLayout layout = new TabularLayout("*").setVGap(TabUiUtils.PAGE_VGAP);
    myPanel = new JPanel(layout);
    myPanel.setBorder(BorderFactory.createEmptyBorder(TabUiUtils.PAGE_VGAP, TabUiUtils.HGAP, 0, TabUiUtils.HGAP));
    JBScrollPane overviewScroll = TabUiUtils.createVerticalScrollPane(myPanel);
    overviewScroll.getVerticalScrollBar().setUnitIncrement(TabUiUtils.SCROLL_UNIT);
    overviewScroll.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        layout.setRowSizing(0, new TabularLayout.SizingRule(TabularLayout.SizingRule.Type.FIXED,
                                                            (int)(overviewScroll.getViewport().getHeight() * 0.4f)));
        layout.layoutContainer(myPanel);
      }
    });

    return overviewScroll;
  }

  @Override
  public void populateFor(@Nullable HttpData data) {
    myPanel.removeAll();
    if (data == null) {
      return;
    }

    File payloadFile = Payload.newResponsePayload(myModel, data).toFile();
    DataViewer fileViewer = myComponents.createFileViewer(payloadFile);
    JComponent responsePayloadComponent = fileViewer.getComponent();
    responsePayloadComponent.setName(ID_RESPONSE_PAYLOAD_VIEWER);

    myPanel.add(responsePayloadComponent, new TabularLayout.Constraint(0, 0));
    myPanel.add(createFields(data, fileViewer.getDimension()), new TabularLayout.Constraint(1, 0));
  }

  @Override
  public void trackWith(@NotNull FeatureTracker featureTracker) {
    if (myFeatures.isNetworkRequestPayloadEnabled()) {
      featureTracker.trackSelectNetworkDetailsOverview();
    }
    else {
      featureTracker.trackSelectNetworkDetailsResponse();
    }
  }

  @Nullable
  @VisibleForTesting
  JComponent findResponsePayloadViewer() {
    return TabUiUtils.findComponentWithUniqueName(myPanel, ID_RESPONSE_PAYLOAD_VIEWER);
  }

  @Nullable
  @VisibleForTesting
  JLabel findContentTypeValue() {
    return (JLabel)TabUiUtils.findComponentWithUniqueName(myPanel, ID_CONTENT_TYPE);
  }

  @Nullable
  @VisibleForTesting
  JLabel findSizeValue() {
    return (JLabel)TabUiUtils.findComponentWithUniqueName(myPanel, ID_SIZE);
  }

  @Nullable
  @VisibleForTesting
  JTextArea findUrlValue() {
    return (JTextArea)TabUiUtils.findComponentWithUniqueName(myPanel, ID_URL);
  }

  @Nullable
  @VisibleForTesting
  JComponent findTimingBar() {
    return TabUiUtils.findComponentWithUniqueName(myPanel, ID_TIMING);
  }

  @Nullable
  @VisibleForTesting
  JLabel findInitiatingThreadValue() {
    return (JLabel)TabUiUtils.findComponentWithUniqueName(myPanel, ID_INITIATING_THREAD);
  }

  @Nullable
  @VisibleForTesting
  JLabel findOtherThreadsValue() {
    return (JLabel)TabUiUtils.findComponentWithUniqueName(myPanel, ID_OTHER_THREADS);
  }

  /**
   * This is a label with bold font and does not wrap.
   */
  static final class NoWrapBoldLabel extends BoldLabel {
    public NoWrapBoldLabel(String text) {
      super("<nobr>" + text + "</nobr>");
    }
  }

  /**
   * This is a hyperlink which will break and wrap when it hits the right border of its container.
   */
  private static class WrappedHyperlink extends JTextArea {

    public WrappedHyperlink(@NotNull String url) {
      super(url);
      setLineWrap(true);
      setEditable(false);
      setBackground(UIUtil.getLabelBackground());
      setFont(UIManager.getFont("Label.font").deriveFont(TabUiUtils.FIELD_FONT_SIZE).deriveFont(ImmutableMap.of(
        TextAttribute.FOREGROUND, PlatformColors.BLUE,
        TextAttribute.BACKGROUND, UIUtil.getLabelBackground())));

      MouseAdapter mouseAdapter = getMouseAdapter(url);
      addMouseListener(mouseAdapter);
      addMouseMotionListener(mouseAdapter);
    }

    private MouseAdapter getMouseAdapter(String url) {
      return new MouseAdapter() {
        @Override
        public void mouseEntered(MouseEvent e) {
          mouseMoved(e);
        }

        @Override
        public void mouseExited(MouseEvent e) {
          setCursor(Cursor.getDefaultCursor());
        }

        @Override
        public void mouseClicked(MouseEvent e) {
          if (isMouseOverText(e)) {
            BrowserUtil.browse(url);
          }
        }

        @Override
        public void mouseMoved(MouseEvent e) {
          setCursor(isMouseOverText(e) ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
        }

        private boolean isMouseOverText(MouseEvent e) {
          return viewToModel(e.getPoint()) < getDocument().getLength();
        }
      };
    }
  }
}
