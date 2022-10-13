/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.device.explorer.files.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.progress.util.ColorProgressBar;
import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.SimpleColoredComponent;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Panel displayed at the bottom of the Device Explorer tool window
 * used to track progress (status text and progress bar) of long running
 * operations, such as file transfers.
 */
public class ProgressPanel extends JPanel {
  private static final int PROGRESS_STEPS = 1000;
  @NotNull private final JProgressBar myProgressBar;
  @NotNull private final SimpleColoredComponent myState;
  @Nullable private ActionListener myCancelActionListener;

  public ProgressPanel() {
    // Create components
    myState = new SimpleColoredComponent() {
      @NotNull
      @Override
      public Dimension getMinimumSize() {
        // Override min size to (0, 0) to ensure the label does not grow past the
        // container width, as, by default, a SimpleColoredComponent returns a
        // min size == preferred size == size to display the text, however long.
        return new Dimension(0, 0);
      }
    };

    myProgressBar = new JProgressBar();
    myProgressBar.setMaximum(PROGRESS_STEPS);

    IconButton stopIcon = new IconButton("Cancel", AllIcons.Process.Stop, AllIcons.Process.StopHovered);
    //noinspection SpellCheckingInspection
    InplaceButton cancelButton = new InplaceButton(stopIcon, e -> {
      if (myCancelActionListener != null) {
        myCancelActionListener.actionPerformed(e);
      }
    });

    setOkStatusColor();
    setVisible(false);

    // Layout components:
    // +-----------------------------------------------+
    // + <status text>                                 +
    // +-----------------------------------------------+
    // + <progress bar>                | <cancel icon> +
    // +-----------------------------------------------+
    BorderLayout layout = new BorderLayout(0, 0);
    setLayout(layout);
    add(myState, BorderLayout.NORTH);
    add(myProgressBar, BorderLayout.CENTER);
    add(cancelButton, BorderLayout.EAST);
  }

  public void start() {
    clear();
    setVisible(true);
  }

  public void stop() {
    setVisible(false);
    clear();
  }

  private void clear() {
    setProgress(0);
    setText("");
    setOkStatusColor();
  }

  public void setCancelActionListener(@Nullable ActionListener cancelActionListener) {
    myCancelActionListener = cancelActionListener;
  }

  public void setOkStatusColor() {
    myProgressBar.setForeground(ColorProgressBar.GREEN);
  }

  public void setWarningStatusColor() {
    myProgressBar.setForeground(ColorProgressBar.YELLOW);
  }

  public void setErrorStatusColor() {
    myProgressBar.setForeground(ColorProgressBar.RED);
  }

  public void setProgress(double v) {
    int fraction = (int)(v * PROGRESS_STEPS);
    myProgressBar.setValue(fraction);
  }

  public void setIndeterminate(boolean indeterminate) {
    myProgressBar.setIndeterminate(indeterminate);
  }

  public void setText(@NotNull String text) {
    myState.clear();
    myState.append(text);
  }
}
