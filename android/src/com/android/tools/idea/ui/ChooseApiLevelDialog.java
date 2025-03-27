/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.ui;

import com.android.tools.idea.stats.Distribution;
import com.android.tools.idea.stats.DistributionService;
import com.android.tools.idea.ui.DistributionChartComponent;
import com.google.common.collect.ImmutableMap;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ui.JBUI;
import java.util.Locale;
import java.util.stream.Collectors;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;
import org.jetbrains.annotations.NotNull;
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
public class ChooseApiLevelDialog extends DialogWrapper implements DistributionChartComponent.SelectionChangedListener {
  private static final String LAST_UPDATED_DATE_PREFIX = "Last updated:";

  private JPanel myPanel;
  private DistributionChartComponent myDistributionChart;
  private JPanel myChartPanel; // Same as myDistributionChart. The form complains if the binding is not a JPanel (can't be a subclass)
  private JBLabel myDescriptionLeft;
  private JBScrollPane myScrollPane;
  private JBLabel myDescriptionRight;
  private JBLabel myIntroducedLabel;
  private JBLabel myLearnMoreLinkLabel;
  private JBLabel myLastUpdatedLabel;
  private int mySelectedApiLevel = -1;

  public ChooseApiLevelDialog(@Nullable Project project, int selectedApiLevel) {
    super(project);
    mySelectedApiLevel = selectedApiLevel;

        try {
      setupUI();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    Window window = getWindow();
    // Allow creation in headless mode for tests
    if (window != null) {
      window.setMinimumSize(JBUI.size(400, 680));
      window.setPreferredSize(JBUI.size(1100, 750));
      window.setMaximumSize(JBUI.size(1100, 800));
    }
    else {
      assert ApplicationManager.getApplication().isUnitTestMode();
    }
    setTitle("Android Platform/API Version Distribution");

    String lastUpdated = getLastUpdatedDate();
    if (lastUpdated != null) {
      myLastUpdatedLabel.setText(getLastUpdatedDate());
    }

    init();
  }

  private void setupUI() {
    createUIComponents();
    myPanel = new JPanel();
    myPanel.setLayout(new GridLayoutManager(3, 4, new Insets(0, 0, 0, 0), -1, -1));
    myPanel.add(myChartPanel, new GridConstraints(0, 0, 2, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_BOTH,
                                                  GridConstraints.SIZEPOLICY_WANT_GROW,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                  null, 0, false));
    myScrollPane = new JBScrollPane();
    myScrollPane.setBackground(new Color(-1118482));
    myScrollPane.setHorizontalScrollBarPolicy(31);
    myScrollPane.setOpaque(false);
    myPanel.add(myScrollPane, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_VERTICAL, 1,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                  new Dimension(500, -1), new Dimension(500, 504), new Dimension(500, -1), 0, false));
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myScrollPane.setViewportView(panel1);
    myDescriptionLeft = new JBLabel();
    myDescriptionLeft.setForeground(new Color(-12566464));
    myDescriptionLeft.setText(
      "<html>The minimum SDK version determines the lowest level of Android that your app will run on. <br><br> You typically want to target as many users as possible, so you would ideally want to support everyone -- with a minimum SDK version of 1. However, that has some disadvantages, such as lack of features, and very few people use devices that old anymore. <br><br> Your choice of minimum SDK level should be a tradeoff between the distribution of users you wish to target and the features that your application will need. <br><br> <b>Click each Android Version/API level for more information.</b> </html>");
    myDescriptionLeft.setVerticalAlignment(1);
    myDescriptionLeft.setVerticalTextPosition(1);
    panel1.add(myDescriptionLeft, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                      null, null, 0, false));
    myDescriptionRight = new JBLabel();
    myDescriptionRight.setForeground(new Color(-12566464));
    myDescriptionRight.setHorizontalAlignment(10);
    myDescriptionRight.setText("");
    myDescriptionRight.setVerticalAlignment(1);
    myDescriptionRight.setVerticalTextPosition(1);
    panel1.add(myDescriptionRight, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                       null, null, 0, false));
    final Spacer spacer1 = new Spacer();
    myPanel.add(spacer1, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
                                             GridConstraints.SIZEPOLICY_CAN_GROW, 1, new Dimension(50, -1), new Dimension(50, -1),
                                             new Dimension(50, -1), 0, false));
    myIntroducedLabel = new JBLabel();
    Font myIntroducedLabelFont = getFont(null, -1, 20, myIntroducedLabel.getFont());
    if (myIntroducedLabelFont != null) myIntroducedLabel.setFont(myIntroducedLabelFont);
    myIntroducedLabel.setHorizontalAlignment(2);
    myIntroducedLabel.setText("");
    myPanel.add(myIntroducedLabel,
                new GridConstraints(0, 2, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(500, -1), new Dimension(500, -1), 0, false));
    myLearnMoreLinkLabel = new JBLabel();
    myLearnMoreLinkLabel.setHorizontalTextPosition(0);
    myPanel.add(myLearnMoreLinkLabel,
                new GridConstraints(2, 2, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(308, 0), null, 0, false));
    myLastUpdatedLabel = new JBLabel();
    myLastUpdatedLabel.setHorizontalTextPosition(0);
    myPanel.add(myLastUpdatedLabel,
                new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(308, 0), null, 0, false));
  }

  private Font getFont(String fontName, int style, int size, Font currentFont) {
    if (currentFont == null) return null;
    String resultName;
    if (fontName == null) {
      resultName = currentFont.getName();
    }
    else {
      Font testFont = new Font(fontName, Font.PLAIN, 10);
      if (testFont.canDisplay('a') && testFont.canDisplay('1')) {
        resultName = fontName;
      }
      else {
        resultName = currentFont.getName();
      }
    }
    Font font = new Font(resultName, style >= 0 ? style : currentFont.getStyle(), size >= 0 ? size : currentFont.getSize());
    boolean isMac = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH).startsWith("mac");
    Font fontWithFallback = isMac
                            ? new Font(font.getFamily(), font.getStyle(), font.getSize())
                            : new StyleContext().getFont(font.getFamily(), font.getStyle(), font.getSize());
    return fontWithFallback instanceof FontUIResource ? fontWithFallback : new FontUIResource(fontWithFallback);
  }

  public JComponent getRootComponent() { return myPanel; }

  @Nullable
  private static String getLastUpdatedDate() {
    List<Distribution> distributions = DistributionService.getInstance().getDistributions();

    if (distributions == null) {
      return null;
    }

    return distributions.stream().flatMap(distribution -> distribution.getDescriptionBlocks().stream())
      .map(block -> block.body)
      .filter(ChooseApiLevelDialog::isLastUpdatedBlock)
      .findFirst().orElse(null);
  }

  private static Boolean isLastUpdatedBlock(String body) {
    return body != null && body.startsWith(LAST_UPDATED_DATE_PREFIX);
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    myDistributionChart.registerDistributionSelectionChangedListener(this);
    myDistributionChart.init();
    myScrollPane.getViewport().setOpaque(false);
    myScrollPane.setOpaque(false);
    myScrollPane.setBorder(null);
    myDescriptionLeft.setForeground(JBColor.foreground());
    myDescriptionLeft.setBackground(JBColor.background());
    myDescriptionRight.setForeground(JBColor.foreground());
    myDescriptionRight.setBackground(JBColor.background());
    myLastUpdatedLabel.setForeground(JBColor.foreground());
    myLastUpdatedLabel.setBackground(JBColor.background());
    myLearnMoreLinkLabel.setForeground(JBColor.blue);
    myLearnMoreLinkLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    Map<TextAttribute, ?> attributes = ImmutableMap.of(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
    myLearnMoreLinkLabel.setFont(myLearnMoreLinkLabel.getFont().deriveFont(attributes));
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
    if (mySelectedApiLevel >= 0) {
      myDistributionChart.selectDistributionApiLevel(mySelectedApiLevel);
    }
    return myPanel;
  }

  @Override
  public void onDistributionSelected(@NotNull Distribution d) {
    // Hide the block containing the last updated date since we display it elsewhere
    List<Distribution.TextBlock> blocks = d.getDescriptionBlocks();
    blocks.removeIf(block -> isLastUpdatedBlock(block.body));
    int halfwayIndex = blocks.size() / 2;
    myDescriptionLeft.setText(getHtmlFromBlocks(blocks.subList(0, halfwayIndex + 1)));
    myDescriptionRight.setText(getHtmlFromBlocks(blocks.subList(halfwayIndex + 1, blocks.size())));
    mySelectedApiLevel = d.getApiLevel();
    myIntroducedLabel.setText(d.getName());
    myLearnMoreLinkLabel.setText(d.getUrl());
  }

  private static String getHtmlFromBlocks(List<Distribution.TextBlock> blocks) {
    StringBuilder sb = new StringBuilder();
    sb.append("<html>");
    for (Distribution.TextBlock block : blocks) {
      sb.append("<h3>").append(block.title).append("</h3>");
      sb.append(block.body).append("<br>");
    }
    sb.append("</html>");
    return sb.toString();
  }

  /**
   * Get the user's choice of API level
   *
   * @return -1 if no selection was made.
   */
  public int getSelectedApiLevel() {
    return mySelectedApiLevel;
  }

  private void createUIComponents() {
    myChartPanel = myDistributionChart = new DistributionChartComponent();
  }
}
