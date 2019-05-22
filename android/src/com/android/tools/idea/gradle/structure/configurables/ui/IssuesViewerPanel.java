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
package com.android.tools.idea.gradle.structure.configurables.ui;

import static com.android.tools.adtui.HtmlLabel.setUpAsHtmlLabel;
import static com.intellij.ui.SimpleTextAttributes.GRAY_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;

import com.android.tools.idea.gradle.structure.configurables.issues.DependencyViewIssuesRenderer;
import com.android.tools.idea.gradle.structure.configurables.issues.NavigationHyperlinkListener;
import com.android.tools.idea.gradle.structure.model.PsIssue;
import com.android.tools.idea.gradle.structure.model.PsPath;
import com.intellij.ui.SimpleColoredComponent;
import java.awt.Font;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import javax.swing.JEditorPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IssuesViewerPanel extends CollapsiblePanel {
  private PsIssue.Severity mySeverity;
  private List<PsIssue> myIssues;
  private JEditorPane issuesView;

  private static final PropertyChangeListener propertyChangeListener = new PropertyChangeListener() {
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
      ((IssuesViewerPanel) evt.getSource()).updateTitle();
    }
  };

  public IssuesViewerPanel(@NotNull NavigationHyperlinkListener hyperlinkListener, @NotNull Font font) {
    super("");
    issuesView = new JEditorPane();
    issuesView.setFocusable(false);
    issuesView.addHyperlinkListener(hyperlinkListener);
    setUpAsHtmlLabel(issuesView, font);
    this.setContents(issuesView);
    addPropertyChangeListener("expanded", propertyChangeListener);
  }

  public void displayIssues(@Nullable PsPath path, @NotNull DependencyViewIssuesRenderer renderer,
                            @NotNull PsIssue.Severity severity, @NotNull List<PsIssue> issues) {
    mySeverity = severity;
    myIssues = issues;
    issuesView.setText(renderer.render(issues, path));
    issuesView.setCaretPosition(0);
    updateTitle();
  }

  public void updateTitle() {
    SimpleColoredComponent title = getTitleComponent();
    title.clear();
    title.setIcon(mySeverity.getIcon());
    title.append(mySeverity.getText(), REGULAR_ATTRIBUTES);
    int issueCount = myIssues.size();
    if (!isExpanded()) {
      title.append(" (" + issueCount + " item" + (issueCount == 1 ? "" : "s") + ")", GRAY_ATTRIBUTES);
    }
  }
}
