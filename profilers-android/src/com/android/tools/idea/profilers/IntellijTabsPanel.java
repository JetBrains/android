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

import com.android.tools.profilers.common.TabsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.TabsListener;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

public class IntellijTabsPanel implements TabsPanel {
  @NotNull private final JBTabsImpl myTabsImpl;
  @NotNull private final Map<JComponent, TabInfo> myTabsCache;
  @Nullable private final Runnable myOnSelectionChanges;

  IntellijTabsPanel(@NotNull Project project, @Nullable Runnable onSelectionChanges) {
    myOnSelectionChanges = onSelectionChanges;
    myTabsImpl = new JBTabsImpl(project);
    myTabsCache = new HashMap<>();

    myTabsImpl.addListener(new TabsListener.Adapter() {
      @Override
      public void selectionChanged(TabInfo oldSelection, TabInfo newSelection) {
        if (myOnSelectionChanges != null) {
          myOnSelectionChanges.run();
        }
      }
    });
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
    myTabsCache.put(content, info);
    myTabsImpl.addTab(info);
  }

  @Override
  public void removeTab(@NotNull JComponent tab) {
    assert myTabsCache.containsKey(tab);
    myTabsImpl.removeTab(myTabsCache.get(tab));
    myTabsCache.remove(tab);
  }

  @Override
  public void removeAll() {
    myTabsImpl.removeAllTabs();
    myTabsCache.clear();
  }

  @Nullable
  @Override
  public JComponent getSelectedComponent() {
    TabInfo info = myTabsImpl.getSelectedInfo();
    if (info == null) {
      return null;
    }
    return info.getComponent();
  }
}
