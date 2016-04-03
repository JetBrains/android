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

import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.SideBorder;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

import static com.intellij.ui.SideBorder.BOTTOM;
import static javax.swing.BorderFactory.createCompoundBorder;
import static javax.swing.BorderFactory.createEmptyBorder;

class AddLibraryDependencyPanel extends JPanel implements Disposable {
  @NotNull private final PsModule myModule;
  @NotNull private final LibraryDependencyForm myLibraryDependencyForm;
  @NotNull private final ScopesForm myScopesForm;

  AddLibraryDependencyPanel(@NotNull PsModule module) {
    super(new BorderLayout());
    myModule = module;

    JBSplitter splitter = new JBSplitter(true, "psd.add.library.dependency.main.horizontal.splitter.proportion", .55f);
    splitter.setBorder(createEmptyBorder());

    myLibraryDependencyForm = new LibraryDependencyForm(myModule);
    JPanel libraryDependencyPanel = myLibraryDependencyForm.getPanel();
    libraryDependencyPanel.setBorder(createMainPanelBorder());

    splitter.setFirstComponent(libraryDependencyPanel);

    if (myModule instanceof PsAndroidModule) {
      myScopesForm = new AndroidScopesForm((PsAndroidModule)myModule);
      JPanel scopesPanel = myScopesForm.getPanel();
      scopesPanel.setBorder(createMainPanelBorder());
      splitter.setSecondComponent(scopesPanel);
    }
    else {
      // TODO Implement "scopes" for Java modules
      myScopesForm = null;
    }

    add(splitter, BorderLayout.CENTER);
  }

  @NotNull
  private static Border createMainPanelBorder() {
    return createCompoundBorder(new SideBorder(OnePixelDivider.BACKGROUND, BOTTOM), createEmptyBorder(5, 5, 5, 5));
  }

  @NotNull
  JComponent getPreferredFocusedComponent() {
    return myLibraryDependencyForm.getPreferredFocusedComponent();
  }

  @Override
  public void dispose() {
    Disposer.dispose(myLibraryDependencyForm);
  }
}
