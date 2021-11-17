/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBPanel;
import java.awt.BorderLayout;
import java.util.Optional;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DetailsPanelPanel2 extends JBPanel<DetailsPanelPanel2> {
  public static final boolean ENABLED = false;

  private final @NotNull JComponent myScrollPane;
  private @Nullable Splitter mySplitter;

  public DetailsPanelPanel2(@NotNull JComponent scrollPane) {
    super(new BorderLayout());

    myScrollPane = scrollPane;
    add(scrollPane);
  }

  public void addSplitter(@NotNull JComponent detailsPanel) {
    remove(myScrollPane);

    mySplitter = new JBSplitter(true);
    mySplitter.setFirstComponent(myScrollPane);
    mySplitter.setSecondComponent(detailsPanel);

    add(mySplitter);
  }

  @VisibleForTesting
  @NotNull Optional<@NotNull Splitter> getSplitter() {
    return Optional.ofNullable(mySplitter);
  }
}
