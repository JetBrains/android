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

import com.android.tools.idea.gradle.structure.dependencies.android.AndroidDependencyScopesPanel;
import com.android.tools.idea.gradle.structure.dependencies.java.JavaDependencyScopesPanel;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.android.tools.idea.gradle.structure.model.java.PsJavaModule;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

import static com.intellij.ui.SideBorder.BOTTOM;
import static com.intellij.util.ui.UIUtil.getButtonFont;
import static com.intellij.util.ui.UIUtil.getTreeFont;
import static javax.swing.BorderFactory.createCompoundBorder;
import static javax.swing.BorderFactory.createEmptyBorder;
import static org.jetbrains.android.util.AndroidUiUtil.setUpAsHtmlLabel;

public abstract class AbstractAddDependenciesDialog extends DialogWrapper {
  @NotNull private final PsModule myModule;

  private JPanel myMainPanel;
  private AbstractDependencyScopesPanel myScopesPanel;

  protected AbstractAddDependenciesDialog(@NotNull PsModule module) {
    super(module.getParent().getResolvedModel());
    myModule = module;
    init();
    getContentPanel().setBorder(createEmptyBorder());
  }

  @Override
  protected boolean postponeValidation() {
    return false;
  }

  @NotNull
  private static AbstractDependencyScopesPanel createDependencyScopesPanel(@NotNull PsModule module) {
    if (module instanceof PsAndroidModule) {
      return new AndroidDependencyScopesPanel((PsAndroidModule)module);
    }
    if (module instanceof PsJavaModule) {
      return new JavaDependencyScopesPanel((PsJavaModule)module);
    }
    throw new IllegalStateException("Unsupported module type: " + module.getClass().getName());
  }

  @Override
  @Nullable
  protected JComponent createCenterPanel() {
    if (myMainPanel == null) {
      myMainPanel = new JPanel(new BorderLayout());
      myScopesPanel = createDependencyScopesPanel(myModule);

      JBSplitter splitter = new JBSplitter(true, getSplitterProportionKey(), .55f);
      splitter.setBorder(createEmptyBorder());

      JComponent view = getDependencySelectionView();
      view.setBorder(createMainPanelBorder());
      splitter.setFirstComponent(view);

      myScopesPanel.setBorder(createMainPanelBorder());
      splitter.setSecondComponent(myScopesPanel);

      myMainPanel.add(splitter, BorderLayout.CENTER);
      myMainPanel.add(new TitlePanel(myModule, getInstructions()), BorderLayout.NORTH);
    }

    return myMainPanel;
  }

  @NotNull
  private static Border createMainPanelBorder() {
    return createCompoundBorder(new SideBorder(OnePixelDivider.BACKGROUND, BOTTOM), createEmptyBorder(5, 5, 5, 5));
  }

  @NotNull
  protected abstract String getSplitterProportionKey();

  @NotNull
  protected abstract JComponent getDependencySelectionView();

  @NotNull
  protected abstract String getInstructions();

  @NotNull
  protected PsModule getModule() {
    return myModule;
  }

  protected AbstractDependencyScopesPanel getScopesPanel() {
    return myScopesPanel;
  }

  public abstract void addNewDependencies();

  @Override
  protected void dispose() {
    if (myScopesPanel != null) {
      Disposer.dispose(myScopesPanel);
    }
    super.dispose();
  }

  private static class TitlePanel extends JPanel {
    TitlePanel(@NotNull PsModule module, @NotNull String instructions) {
      super(new BorderLayout());
      JBLabel titleLabel = new JBLabel();
      titleLabel.setFont(getButtonFont().deriveFont(Font.BOLD));
      titleLabel.setIcon(module.getIcon());
      titleLabel.setText(String.format("Module '%1$s'", module.getName()));
      add(titleLabel, BorderLayout.NORTH);

      JEditorPane instructionsPane = new JEditorPane();
      setUpAsHtmlLabel(instructionsPane, getTreeFont());
      instructionsPane.setText("<html><body><b>Step 1.</b><br/>" + instructions + "</body></html>");
      instructionsPane.setBorder(createEmptyBorder(8, 5, 0, 5));
      add(instructionsPane, BorderLayout.CENTER);

      setBorder(createEmptyBorder(5, 5, 5, 5));
    }
  }
}
