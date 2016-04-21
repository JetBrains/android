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
package com.android.tools.idea.gradle.structure.configurables.messages;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.configurables.issues.IssuesByTypeComparator;
import com.android.tools.idea.gradle.structure.configurables.issues.IssuesViewer;
import com.android.tools.idea.gradle.structure.model.PsIssue;
import com.android.tools.idea.gradle.structure.navigation.PsModulePath;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

import static com.intellij.ui.ScrollPaneFactory.createScrollPane;
import static com.intellij.util.ui.UIUtil.invokeLaterIfNeeded;
import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

public class MessagesConfigurable extends JPanel implements Configurable, Disposable {
  @NotNull private final PsContext myContext;
  @NotNull private final IssuesViewer myIssuesViewer;

  public MessagesConfigurable(@NotNull PsContext context) {
    super(new BorderLayout());
    setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    myContext = context;

    myIssuesViewer = new IssuesViewer(context, issues -> {
      StringBuilder buffer = new StringBuilder();
      buffer.append("<html><body><ol>");

      for (PsIssue issue : issues) {
        buffer.append("<li>")
              .append(issue.getPath().toHtml()).append(": ").append(issue.getText())
              .append("</li>");
      }

      buffer.append("</ul></body></html");
      return buffer.toString();
    });

    JPanel issuesViewerPanel = myIssuesViewer.getPanel();
    JScrollPane scrollPane = createScrollPane(issuesViewerPanel, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
    add(scrollPane, BorderLayout.CENTER);

    renderIssues();
    myContext.getDaemonAnalyzer().add(model -> invokeLaterIfNeeded(this::renderIssues), this);
  }

  private void renderIssues() {
    List<PsIssue> issues = myContext.getDaemonAnalyzer().getIssues().getValues(PsModulePath.class);
    if (issues.size() > 1) {
      Collections.sort(issues, IssuesByTypeComparator.INSTANCE);
    }

    myIssuesViewer.display(issues);
  }

  @Override
  @Nls
  public String getDisplayName() {
    return "Messages";
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return null;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return this;
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
  }

  @Override
  public void reset() {
  }

  @Override
  public void disposeUIResources() {
    Disposer.dispose(this);
  }

  @Override
  public void dispose() {
  }
}
