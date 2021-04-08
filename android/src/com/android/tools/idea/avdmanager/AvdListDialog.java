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
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import icons.StudioIllustrations;
import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Display existing AVDs and offer actions for editing/creating.
 */
public final class AvdListDialog extends FrameWrapper implements AvdUiAction.AvdInfoProvider {
  private static final String DIMENSION_KEY = "AVDManager";
  private Project myProject;
  private AvdDisplayList myAvdDisplayList;

  public AvdListDialog(@Nullable Project project) {
    super(project, DIMENSION_KEY, false, "Android Virtual Device Manager");
    myProject = project;
    myAvdDisplayList = new AvdDisplayList(project);
    myAvdDisplayList.setBorder(new EmptyBorder(UIUtil.PANEL_REGULAR_INSETS));
    closeOnEsc();
  }

  @Override
  public void dispose() {
    super.dispose();
    myProject = null;
    myAvdDisplayList = null;
  }

  public void init() {
    JPanel root = new JPanel(new BorderLayout());
    setComponent(root);
    JPanel northPanel = WizardStepHeaderPanel
      .create(this, WizardConstants.ANDROID_NPW_HEADER_COLOR, StudioIllustrations.Common.PRODUCT_ICON,
              null, "Your Virtual Devices", "Android Studio");
    root.add(northPanel, BorderLayout.NORTH);
    root.add(myAvdDisplayList, BorderLayout.CENTER);
    getFrame().setSize(JBUI.size(1000, 600));
    getFrame().setMinimumSize(JBUI.size(600, 350));
  }

  @Override
  @Nullable
  public AvdInfo getAvdInfo() {
    return null;
  }

  @Override
  public void refreshAvds() {
    myAvdDisplayList.refreshAvds();
  }

  @Override
  public void refreshAvdsAndSelect(@Nullable AvdInfo avdToSelect) {
    myAvdDisplayList.refreshAvdsAndSelect(avdToSelect);
  }

  @Override
  @Nullable
  public Project getProject() {
    return myProject;
  }

  @Override
  @NotNull
  public JComponent getAvdProviderComponent() {
    return myAvdDisplayList;
  }

  @Nullable
  public AvdInfo getSelected() {
    return myAvdDisplayList.getAvdInfo();
  }
}
