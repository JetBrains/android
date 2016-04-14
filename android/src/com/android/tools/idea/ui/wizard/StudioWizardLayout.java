/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.ui.wizard;

import com.android.tools.idea.ui.ImageComponent;
import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.core.ObservableString;
import com.android.tools.idea.ui.properties.swing.TextProperty;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * The general look and feel for all Studio-specific wizards.
 */
public final class StudioWizardLayout implements ModelWizardDialog.CustomLayout {

  private static final JBColor STUDIO_LAYOUT_HEADER_COLOR = new JBColor(0x616161, 0x4B4B4B);

  private final BindingsManager myBindings = new BindingsManager();

  private JPanel myRootPanel;
  private JPanel myHeaderPanel;
  private JBLabel myTitleLabel;
  private JBLabel myProductLabel;
  private ImageComponent myIcon;
  private JPanel myCenterPanel;
  private JPanel myTitlePanel;

  public StudioWizardLayout() {
    Icon icon = AndroidIcons.Wizards.StudioProductIcon;
    myIcon.setPreferredSize(new Dimension(icon.getIconWidth(), icon.getIconHeight()));
    myIcon.setIcon(icon);

    myHeaderPanel.setBackground(STUDIO_LAYOUT_HEADER_COLOR);
  }

  @NotNull
  @Override
  public JPanel decorate(@NotNull ObservableString title, @NotNull JPanel innerPanel) {
    myBindings.bind(new TextProperty(myTitleLabel), title);
    myCenterPanel.add(innerPanel);
    return myRootPanel;
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
  }
}
