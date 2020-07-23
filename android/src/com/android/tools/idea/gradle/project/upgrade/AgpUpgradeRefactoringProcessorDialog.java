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
package com.android.tools.idea.gradle.project.upgrade;

import static com.android.tools.adtui.HtmlLabel.setUpAsHtmlLabel;
import static com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT;
import static com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.MANDATORY_INDEPENDENT;
import static com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.OPTIONAL_CODEPENDENT;
import static com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.OPTIONAL_INDEPENDENT;
import static javax.swing.Action.NAME;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.swing.Action;
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

  private static final Set<AgpUpgradeComponentNecessity> SELECTED_NECESSITIES =
    Arrays.stream(new AgpUpgradeComponentNecessity[] {
      MANDATORY_CODEPENDENT, MANDATORY_INDEPENDENT,
      OPTIONAL_CODEPENDENT, OPTIONAL_INDEPENDENT
    }).collect(Collectors.toSet());

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
      .filter(i -> SELECTED_NECESSITIES.contains(components.get(i).necessity()) )
      .toArray();
    myList.setSelectedIndices(indices);
    myList.setCellRenderer(SimpleListCellRenderer.create((label, value, index) -> label.setText(value.getCommandName())));
    myListPanel.add(myList);
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  protected Action @NotNull [] createActions() {
    return ArrayUtil.mergeArrays(super.createActions(), new Action [] { new PreviewRefactoringAction() });
  }

  @Override
  protected void doOKAction() {
    List<AgpUpgradeComponentRefactoringProcessor> selectedValues = myList.getSelectedValuesList();
    myProcessor.getClasspathRefactoringProcessor().setEnabled(selectedValues.contains(myProcessor.getClasspathRefactoringProcessor()));
    for (AgpUpgradeComponentRefactoringProcessor p : myProcessor.getComponentRefactoringProcessors()) {
      p.setEnabled(selectedValues.contains(p));
    }
    super.doOKAction();
  }

  @Override
  protected @NotNull Action getOKAction() {
    Action okAction = super.getOKAction();
    okAction.putValue(NAME, "Upgrade");
    return okAction;
  }

  private class PreviewRefactoringAction extends DialogWrapperAction {
    protected PreviewRefactoringAction() {
      super("Preview");
    }

    @Override
    protected void doAction(ActionEvent e) {
      myProcessor.setPreviewUsages(true);
      doOKAction();
    }
  }
}
