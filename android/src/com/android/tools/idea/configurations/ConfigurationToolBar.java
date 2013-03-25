/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.configurations;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowForm;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * A widget for configuring a configuration
 */
public class ConfigurationToolBar extends JPanel {
  private final AndroidLayoutPreviewToolWindowForm myPreviewWindow;


  public ConfigurationToolBar(AndroidLayoutPreviewToolWindowForm previewWindow) {
    myPreviewWindow = previewWindow;
    DefaultActionGroup group = createActions(myPreviewWindow);
    ActionToolbar toolbar = createToolBar(group);

    setLayout(new BorderLayout());
    add(toolbar.getComponent(), BorderLayout.NORTH);
  }

  private static ActionToolbar createToolBar(ActionGroup group) {
    ActionManager actionManager = ActionManager.getInstance();
    ActionToolbar toolbar = actionManager.createActionToolbar("Configuration Toolbar", group, true);
    toolbar.setLayoutPolicy(ActionToolbar.WRAP_LAYOUT_POLICY);
    //// The default toolbar layout adds too much spacing between the buttons. Switch to mini mode,
    //// but also set a minimum size which will add *some* padding for our 16x16 icons.
    toolbar.setMiniMode(true);
    toolbar.setMinimumButtonSize(new Dimension(22, 24));
    return toolbar;
  }

  public static DefaultActionGroup createActions(RenderContext configurationHolder) {
    DefaultActionGroup group = new DefaultActionGroup();
    ConfigurationMenuAction configAction = new ConfigurationMenuAction(configurationHolder);
    group.add(configAction);
    group.addSeparator();

    DeviceMenuAction deviceAction = new DeviceMenuAction(configurationHolder);
    group.add(deviceAction);
    group.addSeparator();

    OrientationMenuAction orientationAction = new OrientationMenuAction(configurationHolder);
    group.add(orientationAction);
    group.addSeparator();

    ThemeMenuAction themeAction = new ThemeMenuAction(configurationHolder);
    group.add(themeAction);
    group.addSeparator();

    ActivityMenuAction activityAction = new ActivityMenuAction(configurationHolder);
    group.add(activityAction);
    group.addSeparator();

    LocaleMenuAction localeAction = new LocaleMenuAction(configurationHolder);
    group.add(localeAction);
    group.addSeparator();

    TargetMenuAction targetMenuAction = new TargetMenuAction(configurationHolder);
    group.add(targetMenuAction);
    return group;
  }

  public PsiFile getFile() {
    return myPreviewWindow.getFile();
  }

  @Nullable
  AndroidPlatform getPlatform() {
    PsiFile file = getFile();
    return file != null ? getPlatform(file) : null;
  }

  @Nullable
  private static AndroidPlatform getPlatform(PsiFile file) {
    if (file == null) {
      return null;
    }

    final Module module = ModuleUtilCore.findModuleForPsiElement(file);
    if (module == null) {
      return null;
    }

    final Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
    if (sdk != null && sdk.getSdkType() instanceof AndroidSdkType) {
      final AndroidSdkAdditionalData additionalData = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();
      if (additionalData != null) {
        return additionalData.getAndroidPlatform();
      }
    }
    return null;
  }

  @Nullable
  public Configuration getConfiguration() {
    return myPreviewWindow.getConfiguration();
  }
}
