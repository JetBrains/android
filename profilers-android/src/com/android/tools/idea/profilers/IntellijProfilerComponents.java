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
package com.android.tools.idea.profilers;

import com.android.tools.idea.actions.EditMultipleSourcesAction;
import com.android.tools.idea.actions.PsiClassNavigation;
import com.android.tools.profilers.IdeProfilerComponents;
import com.android.tools.profilers.common.CodeLocation;
import com.android.tools.profilers.common.LoadingPanel;
import com.android.tools.profilers.common.TabsPanel;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.function.Supplier;

public class IntellijProfilerComponents implements IdeProfilerComponents {
  @Nullable
  private Project myProject;

  public IntellijProfilerComponents(@Nullable Project project) {
    myProject = project;
  }

  @Nullable
  @Override
  public LoadingPanel createLoadingPanel() {
    if (myProject == null) {
      return null;
    }

    return new LoadingPanel() {
      private JBLoadingPanel myLoadingPanel = new JBLoadingPanel(new BorderLayout(), myProject);

      @NotNull
      @Override
      public JComponent getComponent() {
        return myLoadingPanel;
      }

      @Override
      public void setLoadingText(@NotNull String loadingText) {
        myLoadingPanel.setLoadingText(loadingText);
      }

      @Override
      public void startLoading() {
        myLoadingPanel.startLoading();
      }

      @Override
      public void stopLoading() {
        myLoadingPanel.stopLoading();
      }
    };
  }

  @Nullable
  @Override
  public TabsPanel createTabsPanel() {
    if (myProject == null) {
      return null;
    }

    return new TabsPanel() {
      @NotNull private final JBTabsImpl myTabsImpl = new JBTabsImpl(myProject);
      @NotNull private final HashMap<JComponent, TabInfo> myTabsCache = new HashMap<>();

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
    };
  }

  @Override
  public void installNavigationContextMenu(@NotNull JComponent component,
                                           @NotNull Supplier<CodeLocation> codeLocationSupplier,
                                           @Nullable Runnable preNavigate) {
    component.putClientProperty(DataManager.CLIENT_PROPERTY_DATA_PROVIDER, (DataProvider)dataId -> {
      if (CommonDataKeys.NAVIGATABLE_ARRAY.is(dataId)) {
        CodeLocation frame = codeLocationSupplier.get();
        if (frame == null || myProject == null) {
          return null;
        }

        if (frame.getLine() > 0) {
          return PsiClassNavigation.getNavigationForClass(myProject, preNavigate, frame.getClassName(), frame.getLine());
        }
        else {
          return PsiClassNavigation.getNavigationForClass(myProject, preNavigate, frame.getClassName());
        }
      }
      else if (CommonDataKeys.PROJECT.is(dataId)) {
        return myProject;
      }
      return null;
    });

    DefaultActionGroup popupGroup = new DefaultActionGroup(new EditMultipleSourcesAction());
    component.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UNKNOWN, popupGroup).getComponent().show(comp, x, y);
      }
    });
  }
}
