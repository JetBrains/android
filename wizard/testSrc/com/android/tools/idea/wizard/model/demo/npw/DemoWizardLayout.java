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
package com.android.tools.idea.wizard.model.demo.npw;

import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public final class DemoWizardLayout implements ModelWizardDialog.CustomLayout {
  private BindingsManager myBindings = new BindingsManager();
  private JPanel myRootPanel;
  private JLabel myTitle;
  private JPanel myInnerContainer;

  @NotNull
  @Override
  public JPanel decorate(@NotNull ModelWizard.TitleHeader titleHeader, @NotNull JPanel innerPanel) {
    myBindings.bind(new TextProperty(myTitle), titleHeader.title());
    myInnerContainer.add(innerPanel);
    return myRootPanel;
  }

  @Override
  public Dimension getDefaultPreferredSize() {
    return JBUI.size(600, 600);
  }

  @Override
  public Dimension getDefaultMinSize() {
    return JBUI.size(400, 400);
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
  }
}
