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

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.configurables.ui.CollapsiblePanel;
import com.android.tools.idea.gradle.structure.model.PsIssue;
import com.android.tools.idea.structure.dialog.ProjectStructureConfigurable;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.navigation.Place;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.android.tools.idea.gradle.structure.configurables.ui.UiUtil.revalidateAndRepaint;
import static com.android.tools.idea.gradle.structure.model.PsPath.GO_TO_PATH_TYPE;
import static com.android.tools.idea.gradle.structure.model.PsPath.QUICK_FIX_PATH_TYPE;
import static com.android.tools.idea.gradle.structure.navigation.Places.deserialize;
import static com.android.tools.idea.gradle.structure.quickfix.QuickFixes.executeQuickFix;
import static com.intellij.ide.BrowserUtil.browse;
import static com.intellij.ui.SimpleTextAttributes.GRAY_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;
import static com.intellij.util.ui.UIUtil.getTreeFont;
import static org.jetbrains.android.util.AndroidUiUtil.setUpAsHtmlLabel;

public class IssuesViewer {
  @NotNull private final PsContext myContext;
  @NotNull private final IssuesRenderer myRenderer;

  private JBLabel myEmptyIssuesLabel;

  private JPanel myIssuesPanel1;
  private JPanel myIssuesPanel2;
  private JPanel myIssuesPanel3;
  private JPanel myIssuesPanel4;
  private JPanel myMainPanel;

  private JEditorPane myIssuesView1;
  private JEditorPane myIssuesView2;
  private JEditorPane myIssuesView3;
  private JEditorPane myIssuesView4;

  private boolean myShowEmptyText;

  public IssuesViewer(@NotNull PsContext context, @NotNull IssuesRenderer renderer) {
    myContext = context;
    myRenderer = renderer;
  }

  public void display(@NotNull Collection<PsIssue> issues) {
    if (issues.isEmpty()) {
      if (myShowEmptyText) {
        myEmptyIssuesLabel.setVisible(true);
      }
      myIssuesPanel1.setVisible(false);
      myIssuesPanel2.setVisible(false);
      myIssuesPanel3.setVisible(false);
      myIssuesPanel4.setVisible(false);
      revalidateAndRepaintPanels();
      return;
    }
    else {
      myEmptyIssuesLabel.setVisible(false);
      myIssuesPanel1.setVisible(true);
      myIssuesPanel2.setVisible(true);
      myIssuesPanel3.setVisible(true);
      myIssuesPanel4.setVisible(true);
    }

    Map<PsIssue.Severity, List<PsIssue>> issuesBySeverity = Maps.newHashMap();
    for (PsIssue issue : issues) {
      PsIssue.Severity severity = issue.getSeverity();
      List<PsIssue> currentIssues = issuesBySeverity.get(severity);
      if (currentIssues == null) {
        currentIssues = Lists.newArrayList();
        issuesBySeverity.put(severity, currentIssues);
      }
      currentIssues.add(issue);
    }

    List<PsIssue.Severity> severities = Lists.newArrayList(issuesBySeverity.keySet());
    Collections.sort(severities, (t1, t2) -> t1.getPriority() - t2.getPriority());

    int typeCount = severities.size();
    assert typeCount < 5; // There are only 4 types of issues

    // Start displaying from last to first
    int currentIssueIndex = typeCount - 1;
    PsIssue.Severity severity = severities.get(currentIssueIndex);
    List<PsIssue> group = issuesBySeverity.get(severity);
    updateTitle(((CollapsiblePanel)myIssuesPanel4), severity, group);
    renderIssues(group, myIssuesView4);

    currentIssueIndex--;
    if (currentIssueIndex < 0) {
      myIssuesPanel1.setVisible(false);
      myIssuesPanel2.setVisible(false);
      myIssuesPanel3.setVisible(false);
      revalidateAndRepaintPanels();
      return;
    }

    severity = severities.get(currentIssueIndex);
    group = issuesBySeverity.get(severity);
    updateTitle(((CollapsiblePanel)myIssuesPanel3), severity, group);
    renderIssues(group, myIssuesView3);

    currentIssueIndex--;
    if (currentIssueIndex < 0) {
      myIssuesPanel1.setVisible(false);
      myIssuesPanel2.setVisible(false);
      revalidateAndRepaintPanels();
      return;
    }

    severity = severities.get(currentIssueIndex);
    group = issuesBySeverity.get(severity);
    updateTitle(((CollapsiblePanel)myIssuesPanel2), severity, group);
    renderIssues(group, myIssuesView2);

    currentIssueIndex--;
    if (currentIssueIndex < 0) {
      myIssuesPanel1.setVisible(false);
      revalidateAndRepaintPanels();
      return;
    }

    severity = severities.get(currentIssueIndex);
    group = issuesBySeverity.get(severity);
    updateTitle(((CollapsiblePanel)myIssuesPanel1), severity, group);
    renderIssues(group, myIssuesView1);

    revalidateAndRepaintPanels();
  }

  private void renderIssues(@NotNull List<PsIssue> group, @NotNull JEditorPane view) {
    view.setCaretPosition(0);
    view.setText(myRenderer.render(group));
    view.setCaretPosition(0);
  }

  private void revalidateAndRepaintPanels() {
    revalidateAndRepaint(myIssuesPanel1);
    revalidateAndRepaint(myIssuesPanel2);
    revalidateAndRepaint(myIssuesPanel4);
    revalidateAndRepaint(myMainPanel);
  }

  private static void updateTitle(@NotNull CollapsiblePanel panel, @NotNull PsIssue.Severity severity, @NotNull List<PsIssue> issues) {
    SimpleColoredComponent title = panel.getTitleComponent();
    title.clear();
    title.setIcon(severity.getIcon());
    title.append(severity.getText(), REGULAR_ATTRIBUTES);
    int issueCount = issues.size();
    title.append(" (" + issueCount + (issueCount == 1 ? " item)" : " items)"), GRAY_ATTRIBUTES);
  }

  @NotNull
  public JPanel getPanel() {
    return myMainPanel;
  }

  private void createUIComponents() {
    Font font = getTreeFont();
    NavigationHyperlinkListener hyperlinkListener = new NavigationHyperlinkListener();

    myIssuesPanel1 = new CollapsiblePanel();
    myIssuesView1 = new JEditorPane();
    myIssuesView1.setFocusable(false);
    myIssuesView1.addHyperlinkListener(hyperlinkListener);
    setUpAsHtmlLabel(myIssuesView1, font);
    ((CollapsiblePanel)myIssuesPanel1).setContents(myIssuesView1);

    myIssuesPanel2 = new CollapsiblePanel();
    myIssuesView2 = new JEditorPane();
    myIssuesView2.setFocusable(false);
    myIssuesView2.addHyperlinkListener(hyperlinkListener);
    setUpAsHtmlLabel(myIssuesView2, font);
    ((CollapsiblePanel)myIssuesPanel2).setContents(myIssuesView2);

    myIssuesPanel3 = new CollapsiblePanel();
    myIssuesView3 = new JEditorPane();
    myIssuesView3.setFocusable(false);
    myIssuesView3.addHyperlinkListener(hyperlinkListener);
    setUpAsHtmlLabel(myIssuesView3, font);
    ((CollapsiblePanel)myIssuesPanel3).setContents(myIssuesView3);

    myIssuesPanel4 = new CollapsiblePanel();
    myIssuesView4 = new JEditorPane();
    myIssuesView4.setFocusable(false);
    myIssuesView4.addHyperlinkListener(hyperlinkListener);
    setUpAsHtmlLabel(myIssuesView4, font);
    ((CollapsiblePanel)myIssuesPanel4).setContents(myIssuesView4);
  }

  private class NavigationHyperlinkListener extends HyperlinkAdapter {
    @Override
    protected void hyperlinkActivated(HyperlinkEvent e) {
      String target = e.getDescription();

      if (target.startsWith(GO_TO_PATH_TYPE)) {
        String serializedPlace = target.substring(GO_TO_PATH_TYPE.length());
        Place place = deserialize(serializedPlace);
        ProjectStructureConfigurable mainConfigurable = myContext.getMainConfigurable();
        mainConfigurable.navigateTo(place, true);
        return;
      }
      if (target.startsWith(QUICK_FIX_PATH_TYPE)) {
        String quickFixPath = target.substring(QUICK_FIX_PATH_TYPE.length());
        executeQuickFix(quickFixPath, myContext);
        return;
      }
      if (target.startsWith("https://") || target.startsWith("http://")) {
        browse(target);
      }
    }
  }

  public void setShowEmptyText(boolean showEmptyText) {
    myShowEmptyText = showEmptyText;
    if (!myShowEmptyText) {
      myEmptyIssuesLabel.setVisible(false);
    }
  }
}
