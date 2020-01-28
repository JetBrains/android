/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.build.attribution.ui.panels;

import static com.android.build.attribution.ui.BuildAttributionUIUtilKt.durationString;
import static com.android.build.attribution.ui.BuildAttributionUIUtilKt.issueIcon;
import static com.android.build.attribution.ui.BuildAttributionUIUtilKt.percentageString;

import com.android.build.attribution.ui.DescriptionWithHelpLinkLabel;
import com.android.build.attribution.ui.analytics.BuildAttributionUiAnalytics;
import com.android.build.attribution.ui.controllers.TaskIssueReporter;
import com.android.build.attribution.ui.data.InterTaskIssueUiData;
import com.android.build.attribution.ui.data.PluginSourceType;
import com.android.build.attribution.ui.data.TaskIssueUiData;
import com.android.build.attribution.ui.data.TaskUiData;
import com.android.utils.HtmlBuilder;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

public class TaskIssueInfoPanel extends JBPanel {
  private final TaskUiData myTaskData;
  private final TaskIssueUiData myIssue;
  private final TaskIssueReporter myIssueReporter;
  private final BuildAttributionUiAnalytics myAnalytics;

  public TaskIssueInfoPanel(TaskIssueUiData issue, TaskIssueReporter reporter, BuildAttributionUiAnalytics analytics) {
    super(new GridBagLayout());
    myIssue = issue;
    myTaskData = issue.getTask();
    myIssueReporter = reporter;
    myAnalytics = analytics;

    GridBagConstraints c = new GridBagConstraints();
    c.insets = JBUI.insetsBottom(15);
    c.weightx = 1.0;
    c.weighty = 0.0;
    c.fill = GridBagConstraints.BOTH;
    c.anchor = GridBagConstraints.FIRST_LINE_START;
    add(createIssueDescription(), c);

    c.gridy = 1;
    c.fill = GridBagConstraints.HORIZONTAL;
    JComponent recommendation = createRecommendation();
    add(recommendation, c);

    c.gridy = 2;
    add(createTaskInfo(), c);

    //add bottom space filler
    c.gridy = 3;
    c.weighty = 1.0;
    c.fill = GridBagConstraints.BOTH;
    add(new JBPanel(), c);

    withPreferredWidth(recommendation.getPreferredSize().width);
  }

  protected JComponent createIssueDescription() {
    String text = new HtmlBuilder()
      .openHtmlBody()
      .addHtml(myIssue.getExplanation())
      .closeHtmlBody()
      .getHtml();

    JLabel iconLabel = new JLabel(issueIcon(myIssue.getType()));
    JComponent issueDescription = new DescriptionWithHelpLinkLabel(text, myIssue.getHelpLink(), myAnalytics);

    JBPanel<JBPanel> panel = new JBPanel<>(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.NONE;
    c.gridx = 0;
    c.gridy = 0;
    c.anchor = GridBagConstraints.FIRST_LINE_START;
    panel.add(iconLabel, c);

    c.gridx = 1;
    c.gridy = 0;
    c.insets = JBUI.insetsLeft(5);
    c.weightx = 1.0;
    c.weighty = 1.0;
    c.fill = GridBagConstraints.BOTH;
    panel.add(issueDescription, c);
    return panel;
  }

  protected JComponent createRecommendation() {
    JPanel panel = new JPanel(new VerticalLayout(5));
    panel.add(new JBLabel("Recommendation").withFont(JBFont.label().asBold()));
    if (myTaskData.getSourceType() == PluginSourceType.BUILD_SRC) {
      panel.add(new JBLabel(myIssue.getBuildSrcRecommendation()));
    }
    else {
      HyperlinkLabel recommendationLabel = new HyperlinkLabel();
      recommendationLabel.addHyperlinkListener(e -> {
        myAnalytics.bugReportLinkClicked();
        myIssueReporter.reportIssue(myIssue);
      });
      recommendationLabel.setHyperlinkText("Consider filing a bug to report this issue to the plugin developer. ", "Generate report.", "");

      panel.add(recommendationLabel);
    }
    return panel;
  }

  protected JComponent createTaskInfo() {
    JBPanel<JBPanel> panel = new JBPanel<>(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.insets = JBUI.insetsBottom(15);
    c.weightx = 0.0;
    c.weighty = 1.0;
    c.anchor = GridBagConstraints.FIRST_LINE_START;
    c.fill = GridBagConstraints.NONE;

    panel.add(createTaskInfo(myTaskData), c);
    if (myIssue instanceof InterTaskIssueUiData) {
      c.gridx = 1;
      c.insets = JBUI.insetsLeft(100);
      panel.add(createTaskInfo(((InterTaskIssueUiData)myIssue).getConnectedTask()), c);
    }

    //add space filler to the right
    c.weightx = 1.0;
    c.fill = GridBagConstraints.BOTH;
    c.gridx++;
    panel.add(new JBPanel(), c);
    return panel;
  }

  private static JComponent createTaskInfo(TaskUiData taskData) {
    String text = new HtmlBuilder()
      .openHtmlBody()
      .addBold(taskData.getTaskPath())
      .newline()
      .add("Plugin: ")
      .add(taskData.getPluginName())
      .newline()
      .add("Type: ")
      .add(taskData.getTaskType())
      .newline()
      .add("Determined this build’s duration: ")
      .add(taskData.getOnExtendedCriticalPath() ? "Yes" : "No")
      .newline()
      .add("Duration: ")
      .add(durationString(taskData.getExecutionTime()))
      .add(" / ")
      .add(percentageString(taskData.getExecutionTime()))
      .newline()
      .add("Executed incrementally: ")
      .add(taskData.getExecutedIncrementally() ? "Yes" : "No")
      .closeHtmlBody()
      .getHtml();
    JBLabel label = new JBLabel().setAllowAutoWrapping(true).setCopyable(true);
    label.setFocusable(false);
    label.setVerticalTextPosition(SwingConstants.TOP);
    label.setText(text);
    return label;
  }
}
