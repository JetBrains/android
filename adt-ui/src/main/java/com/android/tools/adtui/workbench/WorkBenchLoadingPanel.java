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
package com.android.tools.adtui.workbench;

import com.android.annotations.Nullable;
import com.android.tools.adtui.stdui.ActionData;
import com.android.tools.adtui.stdui.Chunk;
import com.android.tools.adtui.stdui.EmptyStatePanel;
import com.android.tools.adtui.stdui.IconChunk;
import com.android.tools.adtui.stdui.LabelData;
import com.android.tools.adtui.stdui.NewLineChunk;
import com.android.tools.adtui.stdui.TextChunk;
import com.android.tools.adtui.stdui.UrlData;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBLoadingPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.LayoutManager;
import java.util.ArrayList;
import javax.swing.Icon;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

/**
 * Drop-in replacement for JBLoadingPanel with ability to display a message when loading
 * of the widget failed.
 */
public class WorkBenchLoadingPanel extends JPanel {
  private final JBLoadingPanel myLoadingPanel;
  @Nullable private EmptyStatePanel myMessagePanel = null;

  public WorkBenchLoadingPanel(@Nullable LayoutManager manager, @NotNull Disposable parent,
                        @SuppressWarnings("SameParameterValue") int startDelayMs) {
    super(new BorderLayout());
    myLoadingPanel = new JBLoadingPanel(manager, parent, startDelayMs);
    super.add(myLoadingPanel);
  }

  public void startLoading() {
    resumeLoading();
    myLoadingPanel.startLoading();
  }

  public void stopLoading() {
    resumeLoading();
    myLoadingPanel.stopLoading();
  }

  public void setLoadingText(String text) {
    myLoadingPanel.setLoadingText(text);
  }

  @VisibleForTesting
  public boolean isLoading() {
    return myLoadingPanel.isLoading();
  }

  @VisibleForTesting
  public boolean hasError() {
    return myMessagePanel != null;
  }

  boolean isLoadingOrHasError() {
    return isLoading() || hasError();
  }

  @Override
  public Component add(Component comp) {
    return myLoadingPanel.add(comp);
  }

  @Override
  public Component add(Component comp, int index) {
    return myLoadingPanel.add(comp, index);
  }

  @Override
  public void add(Component comp, Object constraints) {
    myLoadingPanel.add(comp, constraints);
  }

  @Override
  public Dimension getPreferredSize() {
    return myLoadingPanel.getPreferredSize();
  }

  /**
   * Replaces loading animation with the given message.
   */
  public void abortLoading(String message, @Nullable @SuppressWarnings("SameParameterValue") Icon icon) {
    abortLoading(message, icon, null);
  }

  /**
   * Replaces loading animation with the given message.
   */
  public void abortLoading(@NotNull String message,
                           @Nullable @SuppressWarnings("SameParameterValue") Icon icon,
                           @Nullable ActionData actionData) {
    abortLoading(message, icon, null, actionData);

  }

  /**
   * Replaces loading animation with the given message.
   */
  public void abortLoading(@NotNull String message,
                           @Nullable @SuppressWarnings("SameParameterValue") Icon icon,
                           @Nullable UrlData helpUrlData,
                           @Nullable ActionData actionData) {
    if (myMessagePanel != null) {
      super.remove(myMessagePanel);
    }

    ArrayList<Chunk> chunks = new ArrayList<>();
    if (icon != null) {
      chunks.add(new IconChunk(icon));
    }
    boolean firstLine = true;
    for (String line : StringUtil.splitByLines(message)) {
      if (!firstLine) {
        chunks.add(NewLineChunk.INSTANCE);
      }
      firstLine = false;
      chunks.add(new TextChunk(line));
    }

    myMessagePanel = new EmptyStatePanel(new LabelData(chunks.toArray(new Chunk[0])), helpUrlData, actionData);
    super.remove(myLoadingPanel);
    super.add(myMessagePanel);
  }

  private void resumeLoading() {
    if (myMessagePanel != null) {
      super.remove(myMessagePanel);
      super.add(myLoadingPanel);
      myMessagePanel = null;
    }
  }
}