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

import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.core.ObservableString;
import com.android.tools.idea.ui.properties.swing.TextProperty;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class DemoWizardLayout implements ModelWizardDialog.CustomLayout {
  private BindingsManager myBindings = new BindingsManager();
  private JPanel myRootPanel;
  private JLabel myTitle;
  private JPanel myInnerContainer;

  @NotNull
  @Override
  public JPanel decorate(@NotNull ObservableString title, @NotNull JPanel innerPanel) {
    myBindings.bind(new TextProperty(myTitle), title);
    myInnerContainer.add(innerPanel);
    return myRootPanel;
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
  }
}
