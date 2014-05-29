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

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.fileTemplates.impl.UrlUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.MouseInputAdapter;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;

/**
 * An explanation dialog that helps the user select an API level.
 */
public class ChooseApiLevelDialog extends DialogWrapper implements DistributionChartComponent.DistributionSelectionChangedListener {
  private JPanel myPanel;
  private DistributionChartComponent myDistributionChart;
  private JBLabel myDescriptionLeft;
  private JBScrollPane myScrollPane;
  private JPanel myChartPanel;
  private JBLabel myDescriptionRight;
  private JBScrollPane myScrollPane2;
  private JBLabel myIntroducedLabel;
  private JBLabel myLearnMoreLinkLabel;
  private int mySelectedApiLevel = -1;

  public static class LaunchMe extends AnAction {

    public LaunchMe() {
      super("Launch Choose API Level Dialog");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final DataContext dataContext = e.getDataContext();
      Project project = CommonDataKeys.PROJECT.getData(dataContext);
      ChooseApiLevelDialog dialog = new ChooseApiLevelDialog(project, 16);
      dialog.show();
    }
  }

  protected ChooseApiLevelDialog(@Nullable Project project, int selectedApiLevel) {
    super(project);
    mySelectedApiLevel = selectedApiLevel;

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
    myDistributionChart = new DistributionChartComponent();
    myDistributionChart.registerDistributionSelectionChangedListener(this);
    myChartPanel.setLayout(new BorderLayout());
    myChartPanel.add(myDistributionChart, BorderLayout.CENTER);
    myDistributionChart.init();
    myScrollPane.getViewport().setOpaque(false);
    myScrollPane.setOpaque(false);
    myScrollPane.setBorder(null);
    myScrollPane2.getViewport().setOpaque(false);
    myScrollPane2.setOpaque(false);
    myScrollPane2.setBorder(null);
    myDescriptionLeft.setForeground(JBColor.foreground());
    myDescriptionLeft.setBackground(JBColor.background());
    myDescriptionRight.setForeground(JBColor.foreground());
    myDescriptionRight.setBackground(JBColor.background());
    myLearnMoreLinkLabel.setForeground(JBColor.blue);
    myLearnMoreLinkLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    Font font = myLearnMoreLinkLabel.getFont();
    Map attributes = font.getAttributes();
    attributes.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
    myLearnMoreLinkLabel.setFont(font.deriveFont(attributes));
    myLearnMoreLinkLabel.addMouseListener(new MouseInputAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        try {
          BrowserUtil.browse(new URL(myLearnMoreLinkLabel.getText()));
        }
        catch (MalformedURLException e1) {
          // Pass
        }
      }
    });
    return myPanel;
  }

  @Override
  public void onDistributionSelected(DistributionChartComponent.Distribution d) {
    int halfwayIndex = d.descriptionBlocks.size() / 2;
    myDescriptionLeft.setText(getHtmlFromBlocks(d.descriptionBlocks.subList(0, halfwayIndex + 1)));
    myDescriptionRight.setText(getHtmlFromBlocks(d.descriptionBlocks.subList(halfwayIndex, d.descriptionBlocks.size())));
    mySelectedApiLevel = d.apiLevel;
    myIntroducedLabel.setText(d.name);
    myLearnMoreLinkLabel.setText(d.url);
  }

  private String getHtmlFromBlocks(List<DistributionChartComponent.Distribution.TextBlock> blocks) {
    StringBuilder sb = new StringBuilder();
    sb.append("<html>");
    for (DistributionChartComponent.Distribution.TextBlock block : blocks) {
      sb.append("<h3>");
      sb.append(block.title);
      sb.append("</h3>");
      sb.append(block.body);
      sb.append("<br>");
    }
    sb.append("</html>");
    return sb.toString();
  }

  /**
   * Get the user's choice of API level
   * @return -1 if no selection was made.
   */
  public int getSelectedApiLevel() {
    return mySelectedApiLevel;
  }

  /**
   * @return the percentage of supported devices for the given api level
   */
  public double getSupportedDistributionForApiLevel(int apiLevel) {
    return myDistributionChart.getSupportedDistributionForApiLevel(apiLevel);
  }
}
