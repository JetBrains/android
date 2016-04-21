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

import com.android.tools.idea.gradle.structure.configurables.AbstractCounterDisplayConfigurable;
import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.configurables.issues.IssuesByTypeComparator;
import com.android.tools.idea.gradle.structure.configurables.issues.IssuesViewer;
import com.android.tools.idea.gradle.structure.model.PsIssue;
import com.android.tools.idea.gradle.structure.navigation.PsModulePath;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

import static com.intellij.ui.ScrollPaneFactory.createScrollPane;
import static com.intellij.util.ui.UIUtil.invokeLaterIfNeeded;
import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

public class MessagesConfigurable extends AbstractCounterDisplayConfigurable {
  @NotNull private final IssuesViewer myIssuesViewer;
  private int myMessageCount;

  public MessagesConfigurable(@NotNull PsContext context) {
    super(context);

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
    scrollPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    add(scrollPane, BorderLayout.CENTER);

    renderIssues();
    getContext().getAnalyzerDaemon().add(model -> {
      fireCountChangeListener();
      invokeLaterIfNeeded(this::renderIssues);
    }, this);
  }

  private void renderIssues() {
    List<PsIssue> issues = getContext().getAnalyzerDaemon().getIssues().getValues(PsModulePath.class);
    if (issues.size() > 1) {
      Collections.sort(issues, IssuesByTypeComparator.INSTANCE);
    }
    myMessageCount = issues.size();
    myIssuesViewer.display(issues);
  }

  @Override
  public int getCount() {
    return myMessageCount;
  }

  @Override
  @Nls
  public String getDisplayName() {
    return "Messages";
  }
}
