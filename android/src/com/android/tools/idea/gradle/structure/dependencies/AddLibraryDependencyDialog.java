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
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.module.ResolvedDependenciesPanel;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.module.treeview.DependencySelection;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.SideBorder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.intellij.ui.SideBorder.BOTTOM;

public class AddLibraryDependencyDialog extends DialogWrapper {
  @NotNull private final PsModule myModule;
  @NotNull private final PsContext myContext;

  private JPanel myPanel;
  private AddLibraryDependencyPanel myAddLibraryDependencyPanel;
  private ResolvedDependenciesPanel myResolvedDependenciesPanel;

  public AddLibraryDependencyDialog(@NotNull PsModule module, @NotNull PsContext context) {
    super(module.getParent().getResolvedModel());
    myContext = context;
    myModule = module;
    setTitle("Add Artifact Dependency");
    init();
    getContentPanel().setBorder(BorderFactory.createEmptyBorder());
  }

  @Override
  @Nullable
  protected JComponent createCenterPanel() {
    if (myPanel == null) {
      myPanel = new JPanel(new BorderLayout());

      JBSplitter splitter = new OnePixelSplitter(false, "psd.add.library.dependency.main.vertical.splitter.proportion", .65f);
      splitter.setBorder(BorderFactory.createEmptyBorder());

      myAddLibraryDependencyPanel = new AddLibraryDependencyPanel(myModule);
      splitter.setFirstComponent(myAddLibraryDependencyPanel);

      if (myModule instanceof PsAndroidModule) {
        PsAndroidModule androidModule = (PsAndroidModule)myModule;
        myResolvedDependenciesPanel =
          new ResolvedDependenciesPanel("Current Dependencies", androidModule, myContext, new DependencySelection() {
            @Override
            @Nullable
            public PsAndroidDependency getSelection() {
              return null;
            }

            @Override
            public void setSelection(@Nullable PsAndroidDependency selection) {
            }
          });
        myResolvedDependenciesPanel.setBorder(new SideBorder(OnePixelDivider.BACKGROUND, BOTTOM));
        splitter.setSecondComponent(myResolvedDependenciesPanel);
      }

      myPanel.add(splitter, BorderLayout.CENTER);
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
    if (myResolvedDependenciesPanel != null) {
      Disposer.dispose(myResolvedDependenciesPanel);
    }
  }
}
