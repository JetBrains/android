/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.avdmanager;

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.wizard.WizardConstants;
import com.android.tools.idea.wizard.dynamic.WizardStepHeaderPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.FrameWrapper;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Display existing AVDs and offer actions for editing/creating.
 */
public class AvdListDialog extends FrameWrapper implements AvdUiAction.AvdInfoProvider {
  private static final String DIMENSION_KEY = "AVDManager";
  private final Project myProject;
  private AvdDisplayList myAvdDisplayList;

  public AvdListDialog(@Nullable Project project) {
    super(project, DIMENSION_KEY);
    myProject = project;
    myAvdDisplayList = new AvdDisplayList(this, project);
    myAvdDisplayList.setBorder(new EmptyBorder(UIUtil.PANEL_REGULAR_INSETS));
    closeOnEsc();
    setTitle("Android Virtual Device Manager");
    setDimensionKey(getClass().getName());
    getFrame().setSize(1000, 600);
  }

  public void init() {
    JPanel root = new JPanel(new BorderLayout());
    setComponent(root);
    JPanel northPanel = WizardStepHeaderPanel
      .create(myProject, WizardConstants.ANDROID_NPW_HEADER_COLOR, AndroidIcons.Wizards.NewProjectMascotGreen, null, "Your Virtual Devices",
              "Android Studio");
    root.add(northPanel, BorderLayout.NORTH);
    root.add(myAvdDisplayList, BorderLayout.CENTER);
    getFrame().setSize(1000, 600);
  }

  @Nullable
  @Override
  public AvdInfo getAvdInfo() {
    return null;
  }

  @Override
  public void refreshAvds() {
    myAvdDisplayList.refreshAvds();
  }

  @Nullable
  @Override
  public Project getProject() {
    return myProject;
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myAvdDisplayList;
  }

  @Nullable
  public AvdInfo getSelected() {
    return myAvdDisplayList.getAvdInfo();
  }
}
