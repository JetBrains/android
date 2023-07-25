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

import com.android.tools.idea.AndroidEnvironmentUtils;
import com.android.tools.idea.devicemanager.physicaltab.PhysicalDevicePanel;
import com.android.tools.idea.devicemanager.virtualtab.VirtualDevicePanel;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.ui.JBUI;
import java.awt.Component;
import java.util.function.BiFunction;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

public final class DeviceManagerToolWindowFactory implements ToolWindowFactory, DumbAware {
  private static final String CURRENT_TAB_PROPERTY = "com.android.tools.idea.devicemanager.tab";

  public static final String ID = "Device Manager";
  private final @NotNull BiFunction<Project, Disposable, Component> myNewVirtualDevicePanel;

  @SuppressWarnings("unused")
  private DeviceManagerToolWindowFactory() {
    this(VirtualDevicePanel::new);
  }

  @NonInjectable
  @VisibleForTesting
  DeviceManagerToolWindowFactory(@NotNull BiFunction<Project, Disposable, Component> newVirtualDevicePanel) {
    myNewVirtualDevicePanel = newVirtualDevicePanel;
  }

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow window) {
    Disposable parent = Disposer.newDisposable("Device Manager parent");
    JComponent pane = newJBTabbedPane(project, parent);

    Content content = ContentFactory.getInstance().createContent(pane, null, false);
    content.setDisposer(parent);

    window.getContentManager().addContent(content);
  }

  private @NotNull JComponent newJBTabbedPane(@NotNull Project project, @NotNull Disposable parent) {
    JBTabbedPane pane = new JBTabbedPane();
    pane.setTabComponentInsets(JBUI.emptyInsets());

    String defaultTabTitle = "Virtual";
    pane.addTab(defaultTabTitle, myNewVirtualDevicePanel.apply(project, parent));
    pane.addTab("Physical", new PhysicalDevicePanel(project, parent));
    for (DeviceManagerTab tab : DeviceManagerTab.EP_NAME.getExtensions()) {
      if (tab.isApplicable()) {
        int index = pane.getTabCount();
        pane.addTab(tab.getName(), createTabContent(project, parent, tab));
        tab.setRecreateCallback(() -> pane.setComponentAt(index, createTabContent(project, parent, tab)), parent);
      }
    }

    // Select the tab that was selected in the prior session.
    PropertiesComponent properties = PropertiesComponent.getInstance(project);
    String tabTitle = properties.getValue(CURRENT_TAB_PROPERTY, defaultTabTitle);
    int index = pane.indexOfTab(tabTitle);
    if (index >= 0) {
      pane.setSelectedIndex(index);
    }

    pane.addChangeListener(event -> {
      // Remember the currently selected tab.
      int selectedIndex = pane.getSelectedIndex();
      if (selectedIndex >= 0) {
        properties.setValue(CURRENT_TAB_PROPERTY, pane.getTitleAt(selectedIndex), defaultTabTitle);
      }
      else {
        properties.setValue(CURRENT_TAB_PROPERTY, null);
      }
    });

    return pane;
  }

  @NotNull
  private static Component createTabContent(@NotNull Project project, @NotNull Disposable parent, DeviceManagerTab tab) {
    Component content;
    try {
      content = tab.getPanel(project, parent);
    }
    catch (Throwable throwable) {
      content = tab.getErrorComponent(throwable);
    }
    return content;
  }
}
