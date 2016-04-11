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
package com.android.tools.idea.gradle.structure.dependencies;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;

public class AddLibraryDependencyDialog extends DialogWrapper {
  @NotNull private final PsModule myModule;
  @NotNull private final PsContext myContext;

  private JPanel myPanel;
  private AddLibraryDependencyPanel myAddLibraryDependencyPanel;

  public AddLibraryDependencyDialog(@NotNull PsModule module, @NotNull PsContext context) {
    super(module.getParent().getResolvedModel());
    myContext = context;
    myModule = module;
    setTitle("Add Library Dependency");
    init();
    getContentPanel().setBorder(BorderFactory.createEmptyBorder());
  }

  @Override
  @NotNull
  protected String getDimensionServiceKey() {
    return "psd.add.library.dependency.panel.dimension";
  }

  @Override
  @Nullable
  protected JComponent createCenterPanel() {
    if (myPanel == null) {
      myPanel = new JPanel(new BorderLayout());
      myAddLibraryDependencyPanel = new AddLibraryDependencyPanel(myModule);
      myPanel.add(myAddLibraryDependencyPanel, BorderLayout.CENTER);
    }
    return myPanel;
  }

  @Override
  @Nullable
  public JComponent getPreferredFocusedComponent() {
    if (myAddLibraryDependencyPanel != null) {
      return myAddLibraryDependencyPanel.getPreferredFocusedComponent();
    }
    return null;
  }

  @Override
  protected void dispose() {
    super.dispose();
    if (myAddLibraryDependencyPanel != null) {
      Disposer.dispose(myAddLibraryDependencyPanel);
    }
  }

  @Override
  @Nullable
  protected ValidationInfo doValidate() {
    return myAddLibraryDependencyPanel.validateInput();
  }
}
