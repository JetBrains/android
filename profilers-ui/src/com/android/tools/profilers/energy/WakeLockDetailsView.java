/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.energy;

import com.android.tools.profiler.proto.EnergyProfiler;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.util.stream.Collectors;

public class WakeLockDetailsView {
  @NotNull private final JComponent myComponent;

  public WakeLockDetailsView(@NotNull EventDuration duration) {
    JTextPane textPane = new JTextPane();
    textPane.setContentType("text/html");
    textPane.setBackground(null);
    textPane.setBorder(null);
    textPane.setEditable(false);
    Font labelFont = UIManager.getFont("Label.font");
    StyleSheet styleSheet = ((HTMLDocument)textPane.getDocument()).getStyleSheet();
    styleSheet.addRule("body { font-family: " + labelFont.getFamily() + "; font-size: 12pt; }");
    styleSheet.addRule("p { margin: 4 0 4 0; }");

    EnergyProfiler.WakeLockAcquired wakeLockAcquired = duration.getEventList().get(0).getWakeLockAcquired();
    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append("<html>");
    appendTitleAndValue(stringBuilder, "Name", wakeLockAcquired.getTag());
    appendTitleAndValue(stringBuilder,"Level", wakeLockAcquired.getLevel().name());
    if (!wakeLockAcquired.getFlagsList().isEmpty()) {
      String creationFlags = wakeLockAcquired.getFlagsList().stream()
        .map(EnergyProfiler.WakeLockAcquired.CreationFlag::name)
        .collect(Collectors.joining(", "));
      appendTitleAndValue(stringBuilder, "Flags", creationFlags);
    }
    stringBuilder.append("</html>");
    textPane.setText(stringBuilder.toString());
    myComponent = textPane;
  }

  @NotNull
  public JComponent getComponent() {
    return myComponent;
  }

  private static void appendTitleAndValue(StringBuilder stringBuilder, String title, String value) {
    stringBuilder.append("<p><b>").append(title).append("</b>:&nbsp<span>");
    stringBuilder.append(value).append("</span></p>");
  }
}
