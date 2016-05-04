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
package com.android.tools.idea.gradle.structure.configurables;

import com.android.tools.idea.gradle.structure.configurables.issues.IssuesByTypeAndTextComparator;
import com.android.tools.idea.gradle.structure.model.PsIssue;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static com.android.tools.idea.gradle.structure.model.PsIssueCollection.getTooltipText;
import static com.intellij.ui.SimpleTextAttributes.*;
import static com.intellij.util.ui.UIUtil.getTreeFont;

public class PsModuleCellRenderer extends ColoredTreeCellRenderer {
  @NotNull private final PsContext myContext;

  public PsModuleCellRenderer(@NotNull PsContext context) {
    myContext = context;
  }

  @Override
  public void customizeCellRenderer(@NotNull JTree tree,
                                    Object value,
                                    boolean selected,
                                    boolean expanded,
                                    boolean leaf,
                                    int row,
                                    boolean hasFocus) {
    if (value instanceof MasterDetailsComponent.MyNode) {
      MasterDetailsComponent.MyNode node = (MasterDetailsComponent.MyNode)value;

      NamedConfigurable namedConfigurable = node.getConfigurable();
      if (namedConfigurable == null) {
        return;
      }

      setIcon(namedConfigurable.getIcon(expanded));
      setToolTipText(null);
      setFont(getTreeFont());

      SimpleTextAttributes textAttributes = REGULAR_ATTRIBUTES;
      if (node.isDisplayInBold()) {
        textAttributes = REGULAR_BOLD_ATTRIBUTES;
      }
      else if (namedConfigurable instanceof BaseNamedConfigurable) {
        PsModule module = ((BaseNamedConfigurable)namedConfigurable).getEditableObject();
        List<PsIssue> issues = myContext.getAnalyzerDaemon().getIssues().findIssues(module, IssuesByTypeAndTextComparator.INSTANCE);
        setToolTipText(getTooltipText(issues));

        if (!issues.isEmpty()) {
          PsIssue issue = issues.get(0);
          Color waveColor = issue.getSeverity().getColor();
          textAttributes = textAttributes.derive(STYLE_WAVED, null, null, waveColor);
        }
      }

      append(node.getDisplayName(), textAttributes);
    }
  }
}
