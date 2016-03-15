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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.editor;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.android.PsLibraryDependency;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.ui.HintHint;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import static com.intellij.codeInsight.hint.HintUtil.INFORMATION_COLOR;
import static com.intellij.ui.IdeBorderFactory.createEmptyBorder;
import static com.intellij.util.ui.UIUtil.getLabelBackground;
import static org.jetbrains.android.util.AndroidUiUtil.setUpAsHtmlLabel;

public class LibraryDependencyEditor implements DependencyEditor<PsLibraryDependency> {
  @NotNull private final PsContext myContext;

  private JPanel myPanel;
  private JTextField myGroupIdTextField;
  private JTextField myArtifactNameTextField;
  private JBLabel myResolvedVersionLabel;
  private JBLabel mySourceInfoLabel;
  private JTextField myDeclaredVersionTextField;
  private JBLabel myScopeLabel;
  private TextFieldWithBrowseButton myScopeField;
  private JButton myCheckForUpdatesButton;

  private LightweightHint mySourceInfoHint;
  private PsLibraryDependency myModel;

  public LibraryDependencyEditor(@NotNull PsContext context) {
    myContext = context;
    myScopeLabel.setLabelFor(myScopeField.getTextField());
    showAsLabel(myGroupIdTextField, myArtifactNameTextField);

    Dimension preferredSize = myGroupIdTextField.getPreferredSize();
    myResolvedVersionLabel.setPreferredSize(preferredSize);

    mySourceInfoLabel.setPreferredSize(preferredSize);
    mySourceInfoLabel.setIcon(AllIcons.General.Information);

    mySourceInfoLabel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        if (mySourceInfoLabel.isVisible() && myModel != null && (mySourceInfoHint == null || !mySourceInfoHint.isVisible())) {
          List<String> modules = myModel.findRequestingModuleDependencies();

          int moduleCount = modules.size();
          if (moduleCount > 0) {

            StringBuilder buffer = new StringBuilder();
            buffer.append("<html>Requested by:<br/>");
            for (int i = 0; i < moduleCount; i++) {
              String moduleName = modules.get(i);
              buffer.append("<a href='").append(moduleName).append("'>").append(moduleName).append("</a>");
              if (i < moduleCount - 1) {
                buffer.append("<br/>");
              }
            }

            JEditorPane hintContents = new JEditorPane();
            setUpAsHtmlLabel(hintContents);
            hintContents.setText(buffer.toString());
            hintContents.addHyperlinkListener(new HyperlinkAdapter() {
              @Override
              protected void hyperlinkActivated(HyperlinkEvent e) {
                String moduleName = e.getDescription();
                myContext.setSelectedModule(moduleName, LibraryDependencyEditor.this);

                if (mySourceInfoHint != null) {
                  mySourceInfoHint.hide();
                  mySourceInfoHint = null;
                }
              }
            });

            mySourceInfoHint = new LightweightHint(hintContents);
          }

          HintHint hintInfo = new HintHint(e).setPreferredPosition(Balloon.Position.above)
                                             .setAwtTooltip(true)
                                             .setTextBg(INFORMATION_COLOR)
                                             .setShowImmediately(true);

          Point p = e.getPoint();
          mySourceInfoHint.show(myPanel, p.x, p.y, mySourceInfoLabel, hintInfo);
        }
      }
    });
  }

  private static void showAsLabel(@NotNull JTextField... textFields) {
    Color background = getLabelBackground();
    for (JTextField textField : textFields) {
      textField.setBackground(background);
      textField.setBorder(createEmptyBorder());
    }
  }

  @Override
  @NotNull
  public JPanel getPanel() {
    return myPanel;
  }

  @Override
  public void display(@NotNull PsLibraryDependency model) {
    myModel = model;

    PsArtifactDependencySpec declaredSpec = myModel.getDeclaredSpec();
    assert declaredSpec != null;
    myGroupIdTextField.setText(declaredSpec.group);
    myArtifactNameTextField.setText(declaredSpec.name);
    myResolvedVersionLabel.setText(myModel.getResolvedSpec().version);

    mySourceInfoLabel.setVisible(myModel.hasPromotedVersion());
    mySourceInfoHint = null;

    myDeclaredVersionTextField.setText(declaredSpec.version);
    myScopeField.setText(myModel.getConfigurationName());
  }

  @Override
  @NotNull
  public Class<PsLibraryDependency> getSupportedModelType() {
    return PsLibraryDependency.class;
  }
}
