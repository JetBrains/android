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

import com.android.tools.idea.gradle.structure.configurables.ui.CollapsiblePanel;
import com.android.tools.idea.gradle.structure.model.PsIssue;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static com.intellij.util.ui.UIUtil.getTreeFont;
import static org.jetbrains.android.util.AndroidUiUtil.setUpAsHtmlLabel;

public class IssuesViewer {
  @NotNull private final IssuesRenderer myRenderer;

  private JBLabel myEmptyIssuesLabel;

  private JPanel myIssuesPanel1;
  private JPanel myIssuesPanel2;
  private JPanel myIssuesPanel3;
  private JPanel myMainPanel;

  private JEditorPane myIssuesView1;
  private JEditorPane myIssuesView2;
  private JEditorPane myIssuesView3;

  public IssuesViewer(@NotNull IssuesRenderer renderer) {
    myRenderer = renderer;
  }

  public void display(@NotNull List<PsIssue> issues) {
    if (issues.isEmpty()) {
      myEmptyIssuesLabel.setVisible(true);
      myIssuesPanel1.setVisible(false);
      myIssuesPanel2.setVisible(false);
      myIssuesPanel3.setVisible(false);
      return;
    }
    else {
      myEmptyIssuesLabel.setVisible(false);
      myIssuesPanel1.setVisible(true);
      myIssuesPanel2.setVisible(true);
      myIssuesPanel3.setVisible(true);
    }

    Map<PsIssue.Type, List<PsIssue>> issuesByType = Maps.newHashMap();
    for (PsIssue issue : issues) {
      PsIssue.Type type = issue.getType();
      List<PsIssue> currentIssues = issuesByType.get(type);
      if (currentIssues == null) {
        currentIssues = Lists.newArrayList();
        issuesByType.put(type, currentIssues);
      }
      currentIssues.add(issue);
    }

    List<PsIssue.Type> types = Lists.newArrayList(issuesByType.keySet());
    Collections.sort(types, new Comparator<PsIssue.Type>() {
      @Override
      public int compare(PsIssue.Type t1, PsIssue.Type t2) {
        return t1.getPriority() - t2.getPriority();
      }
    });

    int typeCount = types.size();
    assert typeCount < 4; // There are only 3 types of issues

    // Start displaying from last to first
    int currentIssueIndex = typeCount - 1;
    PsIssue.Type type = types.get(currentIssueIndex);
    List<PsIssue> group = issuesByType.get(type);
    ((CollapsiblePanel)myIssuesPanel3).setTitle(type.getText());
    myIssuesView3.setText(myRenderer.render(group));

    currentIssueIndex--;
    if (currentIssueIndex < 0) {
      myIssuesPanel1.setVisible(false);
      myIssuesPanel2.setVisible(false);
      return;
    }

    type = types.get(currentIssueIndex);
    group = issuesByType.get(type);
    ((CollapsiblePanel)myIssuesPanel2).setTitle(type.getText());
    myIssuesView2.setText(myRenderer.render(group));

    currentIssueIndex--;
    if (currentIssueIndex < 0) {
      myIssuesPanel1.setVisible(false);
      return;
    }

    type = types.get(currentIssueIndex);
    group = issuesByType.get(type);
    ((CollapsiblePanel)myIssuesPanel1).setTitle(type.getText());
    myIssuesView1.setText(myRenderer.render(group));
  }

  @NotNull
  public JPanel getPanel() {
    return myMainPanel;
  }

  private void createUIComponents() {
    Font font = getTreeFont();

    myIssuesPanel1 = new CollapsiblePanel();
    myIssuesView1 = new JEditorPane();
    setUpAsHtmlLabel(myIssuesView1, font);
    ((CollapsiblePanel)myIssuesPanel1).setContents(myIssuesView1);

    myIssuesPanel2 = new CollapsiblePanel();
    myIssuesView2 = new JEditorPane();
    setUpAsHtmlLabel(myIssuesView2, font);
    ((CollapsiblePanel)myIssuesPanel2).setContents(myIssuesView2);

    myIssuesPanel3 = new CollapsiblePanel();
    myIssuesView3 = new JEditorPane();
    setUpAsHtmlLabel(myIssuesView3, font);
    ((CollapsiblePanel)myIssuesPanel3).setContents(myIssuesView3);
  }
}
