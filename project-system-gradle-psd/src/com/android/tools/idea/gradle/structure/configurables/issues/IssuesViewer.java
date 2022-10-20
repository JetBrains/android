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
package com.android.tools.idea.gradle.structure.configurables.issues;

import static com.android.tools.idea.gradle.structure.configurables.ui.UiUtil.revalidateAndRepaint;
import static com.intellij.util.ui.UIUtil.getTreeFont;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.configurables.ui.IssuesViewerPanel;
import com.android.tools.idea.gradle.structure.model.PsIssue;
import com.android.tools.idea.gradle.structure.model.PsPath;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.ui.components.JBLabel;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IssuesViewer {
  @NotNull private final PsContext myContext;
  @NotNull private final DependencyViewIssuesRenderer myRenderer;

  @NotNull private List<IssuesViewerPanel> myIssuesPanels;
  private JPanel myIssuesPanel1;
  private JPanel myIssuesPanel2;
  private JPanel myIssuesPanel3;
  private JPanel myIssuesPanel4;
  private JPanel myMainPanel;

  @Nullable private Set<PsIssue> myLastDisplayedIssues = null;

  public IssuesViewer(@NotNull PsContext context, @NotNull DependencyViewIssuesRenderer renderer) {
    myContext = context;
    myRenderer = renderer;
  }

  public void display(@NotNull Collection<PsIssue> issues, @Nullable PsPath scope) {
    Set<PsIssue> newIssues = new LinkedHashSet<>(issues);
    if (newIssues.equals(myLastDisplayedIssues)) return;
    myLastDisplayedIssues = newIssues;
    if (newIssues.isEmpty()) {
      for (IssuesViewerPanel panel : myIssuesPanels) {
        panel.setVisible(false);
      }
      revalidateAndRepaintPanels();
      return;
    }

    Map<PsIssue.Severity, List<PsIssue>> issuesBySeverity = Maps.newHashMap();
    for (PsIssue issue : newIssues) {
      PsIssue.Severity severity = issue.getSeverity();
      List<PsIssue> currentIssues = issuesBySeverity.computeIfAbsent(severity, k -> new ArrayList<>());
      currentIssues.add(issue);
    }

    List<PsIssue.Severity> severities = Lists.newArrayList(issuesBySeverity.keySet());
    severities.sort(Comparator.comparingInt(PsIssue.Severity::getPriority));

    int typeCount = severities.size();
    assert typeCount < 5; // There are only 4 types of issues

    // Start displaying from last to first, so that if any issue panels are visible the vertically-expanding
    // one at the bottom of IssuesViewer.form is (to keep the locations of the issues stable)
    for (int index = 3; index >= 0; index--) {
      if(typeCount <= 0) {
        myIssuesPanels.get(index).setVisible(false);
      } else {
        PsIssue.Severity severity = severities.get(--typeCount);
        myIssuesPanels.get(index).displayIssues(scope, myRenderer, severity, issuesBySeverity.get(severity));
        myIssuesPanels.get(index).setVisible(true);
      }
    }
    revalidateAndRepaintPanels();
  }

  private void revalidateAndRepaintPanels() {
    for (IssuesViewerPanel panel : myIssuesPanels) {
      revalidateAndRepaint(panel);
    }
    revalidateAndRepaint(myMainPanel);
  }

  @NotNull
  public JPanel getPanel() {
    return myMainPanel;
  }

  private void createUIComponents() {
    Font font = getTreeFont();
    NavigationHyperlinkListener hyperlinkListener = new NavigationHyperlinkListener(myContext);

    myIssuesPanel1 = new IssuesViewerPanel(hyperlinkListener, font);
    myIssuesPanel2 = new IssuesViewerPanel(hyperlinkListener, font);
    myIssuesPanel3 = new IssuesViewerPanel(hyperlinkListener, font);
    myIssuesPanel4 = new IssuesViewerPanel(hyperlinkListener, font);
    myIssuesPanels = Arrays.asList((IssuesViewerPanel) myIssuesPanel1,
                                   (IssuesViewerPanel) myIssuesPanel2,
                                   (IssuesViewerPanel) myIssuesPanel3,
                                   (IssuesViewerPanel) myIssuesPanel4);
    for (IssuesViewerPanel panel : myIssuesPanels) {
      panel.setVisible(false);
    }
  }
}
