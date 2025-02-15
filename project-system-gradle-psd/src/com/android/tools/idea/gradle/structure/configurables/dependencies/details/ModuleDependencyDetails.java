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
package com.android.tools.idea.gradle.structure.configurables.dependencies.details;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.model.PsBaseDependency;
import com.android.tools.idea.gradle.structure.model.PsDeclaredModuleDependency;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.PsModuleDependency;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.UIUtil;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Insets;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.event.HyperlinkEvent;
import org.jdesktop.swingx.JXLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ModuleDependencyDetails implements ConfigurationDependencyDetails {
  @NotNull private final PsContext myContext;
  private final boolean myShowScope;

  private JPanel myMainPanel;
  private JXLabel myNameLabel;
  private JXLabel myGradlePathLabel;
  private JBLabel myConfigurationLabel;
  private HyperlinkLabel myGoToLabel;
  private JPanel myConfigurationPanel;

  private PsModuleDependency myDependency;

  public ModuleDependencyDetails(@NotNull PsContext context, boolean showScope) {
    setupUI();
    myContext = context;
    myShowScope = showScope;
    myConfigurationLabel.setVisible(showScope);
    myConfigurationPanel.setVisible(showScope);

    myGoToLabel.setHyperlinkText("See Dependencies");
    myGoToLabel.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(@NotNull HyperlinkEvent e) {
        assert myDependency != null;
        myContext.getMainConfigurable().navigateTo(
          myContext
            .getProject()
            .findModuleByGradlePath(myDependency.getGradlePath())
            .getPath()
            .getDependenciesPath()
            .getPlaceDestination(myContext),
          true);
      }
    });
  }

  @Override
  @NotNull
  public JPanel getPanel() {
    return myMainPanel;
  }

  @Override
  public void display(@NotNull PsBaseDependency dependency) {
    PsModuleDependency d = (PsModuleDependency)dependency;
    myNameLabel.setText(d.getName());
    myGradlePathLabel.setText(d.getGradlePath());
    if (myShowScope) {
      displayConfiguration((PsDeclaredModuleDependency)d, PsModule.ImportantFor.MODULE);
    }
    myDependency = d;
  }

  @Override
  @NotNull
  public Class<PsModuleDependency> getSupportedModelType() {
    return PsModuleDependency.class;
  }

  @Override
  @Nullable
  public PsModuleDependency getModel() {
    return myDependency;
  }

  @Override
  public PsContext getContext() {
    return myContext;
  }

  @Override
  public JPanel getConfigurationUI() {
    return myConfigurationPanel;
  }

  private void setupUI() {
    myMainPanel = new JPanel();
    myMainPanel.setLayout(new GridLayoutManager(4, 2, new Insets(0, 0, 0, 0), -1, -1));
    final JBLabel jBLabel1 = new JBLabel();
    jBLabel1.setComponentStyle(UIUtil.ComponentStyle.SMALL);
    jBLabel1.setFontColor(UIUtil.FontColor.BRIGHTER);
    jBLabel1.setText("Name:");
    myMainPanel.add(jBLabel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                  GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                  false));
    final JBLabel jBLabel2 = new JBLabel();
    jBLabel2.setComponentStyle(UIUtil.ComponentStyle.SMALL);
    jBLabel2.setFontColor(UIUtil.FontColor.BRIGHTER);
    jBLabel2.setText("Gradle Path:");
    myMainPanel.add(jBLabel2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                  GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                  false));
    myNameLabel = new JXLabel();
    Font myNameLabelFont = UIManager.getFont("Tree.font");
    if (myNameLabelFont != null) myNameLabel.setFont(myNameLabelFont);
    myMainPanel.add(myNameLabel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                     null, null, 0, false));
    myGradlePathLabel = new JXLabel();
    Font myGradlePathLabelFont = UIManager.getFont("Tree.font");
    if (myGradlePathLabelFont != null) myGradlePathLabel.setFont(myGradlePathLabelFont);
    myMainPanel.add(myGradlePathLabel, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                           null, null, null, 0, false));
    myConfigurationLabel = new JBLabel();
    myConfigurationLabel.setComponentStyle(UIUtil.ComponentStyle.SMALL);
    myConfigurationLabel.setFontColor(UIUtil.FontColor.BRIGHTER);
    myConfigurationLabel.setText("Configuration:");
    myMainPanel.add(myConfigurationLabel, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                              GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                              null, null, 0, false));
    myGoToLabel = new HyperlinkLabel();
    Font myGoToLabelFont = UIManager.getFont("Tree.font");
    if (myGoToLabelFont != null) myGoToLabel.setFont(myGoToLabelFont);
    myMainPanel.add(myGoToLabel, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                     null, null, 0, false));
    myConfigurationPanel = new JPanel();
    myConfigurationPanel.setLayout(new BorderLayout(0, 0));
    myMainPanel.add(myConfigurationPanel, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                              null, null, null, 0, false));
  }
}
