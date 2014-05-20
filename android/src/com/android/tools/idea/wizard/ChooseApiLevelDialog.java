/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.wizard;

import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiDirectory;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * An explanation dialog that helps the user select an API level.
 */
public class ChooseApiLevelDialog extends DialogWrapper implements DistributionChartComponent.DistributionSelectionChangedListener {
  private JPanel myPanel;
  private DistributionChartComponent myDistributionChart;
  private JBLabel myDescription;
  private JBScrollPane myScrollPane;
  private int mySelectedApiLevel = -1;

  public static class LaunchMe extends AnAction {

    public LaunchMe() {
      super("Launch Choose API Level Dialog");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final DataContext dataContext = e.getDataContext();
      Project project = CommonDataKeys.PROJECT.getData(dataContext);
      ChooseApiLevelDialog dialog = new ChooseApiLevelDialog(project);
      dialog.show();
    }
  }

  protected ChooseApiLevelDialog(@Nullable Project project) {
    super(project);

    Window window = getWindow();
    // Allow creation in headless mode for tests
    if (window != null) {
      window.setMinimumSize(new Dimension(400, 680));
      window.setPreferredSize(new Dimension(1100, 750));
      window.setMaximumSize(new Dimension(1100, 800));
    } else {
      assert ApplicationManager.getApplication().isUnitTestMode();
    }
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    myDistributionChart.registerDistributionSelectionChangedListener(this);
    myScrollPane.getViewport().setOpaque(false);
    myScrollPane.setOpaque(false);
    myScrollPane.setBorder(null);
    myDescription.setForeground(JBColor.foreground());
    myDescription.setBackground(JBColor.background());
    return myPanel;
  }

  @Override
  public void onDistributionSelected(DistributionChartComponent.Distribution d) {
    StringBuilder sb = new StringBuilder();
    sb.append("<html>");
    for (DistributionChartComponent.Distribution.TextBlock block : d.descriptionBlocks) {
      sb.append("<h3>");
      sb.append(block.title);
      sb.append("</h3>");
      sb.append(block.body);
      sb.append("<br>");
    }
    sb.append("</html>");
    myDescription.setText(sb.toString());
    mySelectedApiLevel = d.apiLevel;
  }

  /**
   * Get the user's choice of API level
   * @return -1 if no selection was made.
   */
  public int getSelectedApiLevel() {
    return mySelectedApiLevel;
  }
}
