/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLDocument;
import java.awt.*;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * View shown if no processes are selected
 */
public class NullMonitorStageView extends StageView<NullMonitorStage> {

  public static final String NO_DEVICE_MESSAGE = "No device detected. Please plug in a device,<br/>or launch the emulator.";
  public static final String NO_DEBUGGABLE_PROCESS_MESSAGE = "No debuggable processes detected for<br/>the selected device.";
  private JEditorPane myDisabledMessage;

  public NullMonitorStageView(@NotNull StudioProfilersView profilersView, @NotNull NullMonitorStage stage) {
    super(profilersView, stage);

    JPanel topPanel = new JPanel();
    BoxLayout layout = new BoxLayout(topPanel, BoxLayout.Y_AXIS);
    topPanel.setLayout(layout);

    topPanel.add(Box.createVerticalGlue());
    topPanel.setBackground(ProfilerColors.MONITOR_BACKGROUND);

    JLabel picLabel = new JLabel(ProfilerIcons.ANDROID_PROFILERS);
    picLabel.setHorizontalAlignment(SwingConstants.CENTER);
    picLabel.setVerticalAlignment(SwingConstants.CENTER);
    picLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
    topPanel.add(picLabel);

    JLabel title = new JLabel("Android Profiler");
    title.setHorizontalAlignment(SwingConstants.CENTER);
    title.setVerticalAlignment(SwingConstants.TOP);
    title.setAlignmentX(Component.CENTER_ALIGNMENT);
    title.setFont(title.getFont().deriveFont(21.0f));
    title.setForeground(new JBColor(0x000000, 0xFFFFFF));
    topPanel.add(title);
    topPanel.add(Box.createRigidArea(new Dimension(1, 15)));

    myDisabledMessage = new JEditorPane() {
      @Override
      public Dimension getMaximumSize() {
        return getPreferredSize();
      }
    };
    Font font = title.getFont().deriveFont(11.0f);
    setUpAsHtmlLabel(myDisabledMessage, font);
    myDisabledMessage.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent event) {
        if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          String uri = event.getDescription();
          try {
            BrowserLauncher.getInstance().browse(new URI(uri));
          }
          catch (URISyntaxException ignored) {
          }
        }
      }
    });
    topPanel.add(myDisabledMessage);
    topPanel.add(Box.createVerticalGlue());

    getComponent().add(topPanel, BorderLayout.CENTER);
    stage.getStudioProfilers().addDependency(this).onChange(ProfilerAspect.DEVICES, this::changed);
    changed();
  }

  @VisibleForTesting
  public String getMessage() {
    if (getStage().getStudioProfilers().getDevice() == null) {
      return NO_DEVICE_MESSAGE;
    }
    return NO_DEBUGGABLE_PROCESS_MESSAGE;
  }

  private void changed() {
    setMessageText(getMessage());
  }

  private void setMessageText(String message) {
    myDisabledMessage.setText("<html><body><div style='text-align: center;'>" + message +
                              " <a href=\"https://developer.android.com/r/studio-ui/about-profilers.html\">Learn More</a></div></body></html>");
  }

  @Override
  public JComponent getToolbar() {
    return new JPanel();
  }

  @Override
  public boolean needsProcessSelection() {
    return true;
  }

  // TODO Unify with the AndroidUI one (move to adt-ui?)
  public static void setUpAsHtmlLabel(@NotNull JEditorPane editorPane, @NotNull Font font) {
    editorPane.setContentType("text/html");
    editorPane.setEditable(false);
    editorPane.setOpaque(false);
    String color = UIUtil.isUnderDarcula() ? "#C8C8C8" : "#787878";
    String bodyRule = "body { font-family: " + font.getFamily() + "; " + "font-size: " + font.getSize() + "pt; color: " + color + "; } " +
                      "ol { padding-left: 0px; margin-left: 35px; margin-top: 0px; } " +
                      "ol li { margin-left: 0px; padding-left: 0px; list-style-type: decimal; }";
    ((HTMLDocument)editorPane.getDocument()).getStyleSheet().addRule(bodyRule);
  }
}
