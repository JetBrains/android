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
package com.android.tools.idea.profilers;

import com.android.tools.profilers.stacktrace.TabsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

public class IntellijTabsPanel implements TabsPanel {
  @NotNull private final JBTabsImpl myTabsImpl;

  IntellijTabsPanel(@NotNull Project project) {
    myTabsImpl = new JBTabsImpl(project);
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myTabsImpl;
  }

  @Override
  public void addTab(@NotNull String label, @NotNull JComponent content) {
    TabInfo info = new TabInfo(content);
    info.setText(label);
    myTabsImpl.addTab(info);
  }

  @Override
  public void removeTab(@NotNull JComponent tab) {
    myTabsImpl.removeTab(myTabsImpl.findInfo(tab));
  }

  @Override
  public void removeAll() {
    myTabsImpl.removeAllTabs();
  }

  @Nullable
  @Override
  public JComponent getSelectedTabComponent() {
    TabInfo info = myTabsImpl.getSelectedInfo();
    if (info == null) {
      return null;
    }
    return info.getComponent();
  }

  @Override
  public void selectTab(@NotNull String label) {
    for(TabInfo tab : myTabsImpl.getTabs()) {
      if (tab.getText().equals(label)) {
        myTabsImpl.select(tab, true);
      }
    }
  }

  @Override
  public List<Component> getTabsComponents() {
    return myTabsImpl.getTabs().stream().map(TabInfo::getComponent).collect(Collectors.toList());
  }

  @Override
  public void setOnSelectionChange(@Nullable Runnable callback) {
    if (callback == null) {
      myTabsImpl.setSelectionChangeHandler(null);
      return;
    }
    myTabsImpl.setSelectionChangeHandler((info, requestFocus, activeRunnable) -> {
      ActionCallback actionCallback = activeRunnable.run();
      callback.run();
      return actionCallback;
    });
  }

  @Override
  public String getSelectedTab() {
    TabInfo info = myTabsImpl.getSelectedInfo();
    if (info == null) {
      return null;
    }
    return info.getText();
  }
}
