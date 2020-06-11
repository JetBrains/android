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
package com.android.tools.idea.gradle.project.sync.setup.post.upgrade;

import static com.android.tools.adtui.HtmlLabel.setUpAsHtmlLabel;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBList;
import java.util.ArrayList;
import java.util.stream.IntStream;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AgpUpgradeRefactoringProcessorDialog extends DialogWrapper {
  private JPanel myPanel;
  private JEditorPane myEditorPane;
  private JPanel myListPanel;
  private JBList<AgpUpgradeComponentRefactoringProcessor> myList;

  private AgpUpgradeRefactoringProcessor myProcessor;

  AgpUpgradeRefactoringProcessorDialog(@NotNull AgpUpgradeRefactoringProcessor processor) {
    super(processor.getProject());
    myProcessor = processor;

    setTitle("Android Gradle Plugin Upgrade Assistant");
    init();

    setUpAsHtmlLabel(myEditorPane);
    myEditorPane.setText("The following commands will be executed to upgrade your project from Android Gradle Plugin version "
                         + myProcessor.getCurrent() + " to version " + myProcessor.getNew() + ":");

    ArrayList<AgpUpgradeComponentRefactoringProcessor> components = new ArrayList<>();
    components.addAll(processor.getComponentRefactoringProcessors());
    components.add(processor.getClasspathRefactoringProcessor());
    myList = new JBList<>(components);
    int[] indices = IntStream.range(0, components.size())
      .filter(i -> components.get(i).isApplicable())
      .toArray();
    myList.setSelectedIndices(indices);
    myList.setCellRenderer(SimpleListCellRenderer.create((label, value, index) -> label.setText(value.getCommandName())));
    myListPanel.add(myList);
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    return myPanel;
  }
}
