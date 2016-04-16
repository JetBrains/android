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
package com.android.tools.idea.gradle.structure.dependencies.android;

import com.android.tools.idea.gradle.structure.dependencies.DependencyScopesForm;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.android.tools.idea.gradle.structure.model.android.dependency.PsNewDependencyScopes;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.HyperlinkAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.List;

import static com.intellij.ide.BrowserUtil.browse;
import static com.intellij.util.ui.UIUtil.getTreeFont;
import static javax.swing.BorderFactory.createEmptyBorder;
import static org.jetbrains.android.util.AndroidUiUtil.setUpAsHtmlLabel;

public class AndroidDependencyScopesForm extends JPanel implements DependencyScopesForm {
  @NotNull private final MainForm myMainForm;

  public AndroidDependencyScopesForm(@NotNull PsAndroidModule module) {
    super(new BorderLayout());

    JEditorPane instructionsPane = new JEditorPane();
    setUpAsHtmlLabel(instructionsPane, getTreeFont());

    instructionsPane.setText("<html><body><b>Step 2.</b><br/>" +
                             "Assign a scope to the new dependency by selecting the scopes, build types and product " +
                             "flavors where the dependency will be used. <a " +
                             "href='http://tools.android.com/tech-docs/new-build-system/user-guide#TOC-Dependencies-Android-Libraries-and-Multi-project-setup'>Open Documentation</a>." +
                             "</body></html>");
    instructionsPane.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        browse(e.getURL());
      }
    });
    instructionsPane.setBorder(createEmptyBorder(0, 5, 8, 5));
    add(instructionsPane, BorderLayout.NORTH);

    myMainForm = new MainForm(module);
    add(myMainForm.getPanel(), BorderLayout.CENTER);
  }

  @Override
  @NotNull
  public JPanel getPanel() {
    return this;
  }

  @Override
  @NotNull
  public List<String> getSelectedScopesNames() {
    return myMainForm.getSelectedScopeNames();
  }

  @Nullable
  public PsNewDependencyScopes getNewScopes() {
    return myMainForm.getNewScopes();
  }

  @Nullable
  @Override
  public ValidationInfo validateInput() {
    return myMainForm.validateInput();
  }

  @Override
  public void dispose() {
    Disposer.dispose(myMainForm);
  }
}
