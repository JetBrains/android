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

import static com.android.build.attribution.ui.BuildAttributionUIUtilKt.warningIcon;
import static com.android.build.attribution.ui.panels.BuildAttributionPanelsKt.htmlTextLabel;

import com.android.build.attribution.ui.DescriptionWithHelpLinkLabel;
import com.android.build.attribution.ui.analytics.BuildAttributionUiAnalytics;
import com.android.build.attribution.ui.data.AnnotationProcessorUiData;
import com.android.utils.HtmlBuilder;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBUI;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;

public class AnnotationProcessorIssueInfoPanel extends JBPanel {
  private static final String DESCRIPTION = "This annotation processor is non-incremental and causes the JavaCompile task " +
                                            "to always run non-incrementally. " +
                                            "Consider switching to using an incremental annotation processor.";
  private static final String HELP_LINK = "https://d.android.com/r/tools/build-attribution/non-incremental-ap";

  private final BuildAttributionUiAnalytics myAnalytics;

  public AnnotationProcessorIssueInfoPanel(
    AnnotationProcessorUiData annotationProcessorUiData,
    BuildAttributionUiAnalytics analytics
  ) {
    super(new GridBagLayout());
    myAnalytics = analytics;

    GridBagConstraints c = new GridBagConstraints();
    c.insets = JBUI.insetsBottom(15);
    c.weightx = 1.0;
    c.weighty = 0.0;
    c.fill = GridBagConstraints.BOTH;
    c.anchor = GridBagConstraints.FIRST_LINE_START;
    add(createIssueDescription(), c);

    //add bottom space filler
    c.gridy = 3;
    c.weighty = 1.0;
    c.fill = GridBagConstraints.BOTH;
    add(new JBPanel(), c);
    withPreferredWidth(500);
  }

  protected JComponent createIssueDescription() {
    String descriptionHtml = new HtmlBuilder()
      .openHtmlBody()
      .addHtml(DESCRIPTION)
      .closeHtmlBody()
      .getHtml();
    String recommendationHtml = new HtmlBuilder()
      .openHtmlBody()
      .addBold("Recommendation")
      .newline()
      .add("Ensure that you are using the most recent version of this annotation processor.")
      .closeHtmlBody()
      .getHtml();

    JLabel iconLabel = new JLabel(warningIcon());
    JComponent issueDescription = new DescriptionWithHelpLinkLabel(descriptionHtml, HELP_LINK, myAnalytics);
    JBLabel recommendation = htmlTextLabel(recommendationHtml);

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
    c.weighty = 0.0;
    c.fill = GridBagConstraints.BOTH;
    panel.add(issueDescription, c);

    c.gridx = 1;
    c.gridy = 1;
    c.weighty = 1.0;
    c.insets = JBUI.insets(5, 5, 0, 0);
    panel.add(recommendation, c);
    return panel;
  }
}
