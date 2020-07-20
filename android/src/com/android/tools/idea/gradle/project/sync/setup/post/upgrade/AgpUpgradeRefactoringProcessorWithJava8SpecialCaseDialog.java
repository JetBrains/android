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
import static com.android.tools.idea.gradle.project.sync.setup.post.upgrade.AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT;
import static com.android.tools.idea.gradle.project.sync.setup.post.upgrade.AgpUpgradeComponentNecessity.MANDATORY_INDEPENDENT;
import static com.android.tools.idea.gradle.project.sync.setup.post.upgrade.Java8DefaultRefactoringProcessor.NoLanguageLevelAction.ACCEPT_NEW_DEFAULT;
import static com.android.tools.idea.gradle.project.sync.setup.post.upgrade.Java8DefaultRefactoringProcessor.NoLanguageLevelAction.INSERT_OLD_DEFAULT;
import static com.intellij.ide.BrowserUtil.browse;
import static javax.swing.Action.NAME;

import com.android.tools.idea.gradle.project.sync.setup.post.upgrade.Java8DefaultRefactoringProcessor.NoLanguageLevelAction;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ArrayUtil;
import java.awt.event.ActionEvent;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.event.HyperlinkEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AgpUpgradeRefactoringProcessorWithJava8SpecialCaseDialog extends DialogWrapper {
  private JPanel myPanel;
  private JEditorPane myEditorPane;
  private JPanel myJava8SettingsPanel;
  private ComboBox<NoLanguageLevelAction> myNoLanguageLevelActionComboBox;

  private AgpUpgradeRefactoringProcessor myProcessor;
  private Java8DefaultRefactoringProcessor myJava8Processor;

  AgpUpgradeRefactoringProcessorWithJava8SpecialCaseDialog(@NotNull AgpUpgradeRefactoringProcessor processor) {
    super(processor.getProject());
    myProcessor = processor;

    setTitle("Android Gradle Plugin Upgrade Assistant");
    init();

    setUpAsHtmlLabel(myEditorPane);
    myEditorPane.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        browse(e.getURL());
      }
    });
    StringBuilder sb = new StringBuilder();
    sb.append("<p>The following commands will be executed to upgrade your project from Android Gradle Plugin version ")
      .append(myProcessor.getCurrent()).append(" to version ").append(myProcessor.getNew()).append(":</p>");
    sb.append("<ul>");
    for (AgpUpgradeComponentRefactoringProcessor p : myProcessor.getComponentRefactoringProcessors()) {
      AgpUpgradeComponentNecessity necessity = p.necessity();
      p.setEnabled(necessity == MANDATORY_CODEPENDENT || necessity == MANDATORY_INDEPENDENT);
      if (p.isEnabled()) {
        sb.append("<li>").append(p.getCommandName());
        String url = p.getReadMoreUrl();
        if (url != null) {
          sb.append(" [<a href='").append(url).append("'>read more</a>]");
        }
        sb.append("</li>");
      }
    }
    sb.append("<li>").append(myProcessor.getClasspathRefactoringProcessor().getCommandName()).append("</li>");
    sb.append("</ul>");
    myEditorPane.setText(sb.toString());

    myProcessor.getComponentRefactoringProcessors()
      .stream()
      .filter((p) -> p instanceof Java8DefaultRefactoringProcessor)
      .findFirst()
      .ifPresent((p) -> myJava8Processor = (Java8DefaultRefactoringProcessor)p);

    if (myJava8Processor != null && myJava8Processor.isEnabled() && !myJava8Processor.isAlwaysNoOpForProject()) {
      JBLabel label = new JBLabel("Action on no explicit Java language level: ");
      myJava8SettingsPanel.add(label);
      myNoLanguageLevelActionComboBox = new ComboBox<>(new NoLanguageLevelAction[] {ACCEPT_NEW_DEFAULT, INSERT_OLD_DEFAULT});
      myJava8SettingsPanel.add(myNoLanguageLevelActionComboBox);
      myJava8SettingsPanel.setVisible(true);
    }
    else {
      myJava8SettingsPanel.setVisible(false);
    }
  }
  @Override
  protected @Nullable JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  protected Action @NotNull [] createActions() {
    return ArrayUtil.mergeArrays(super.createActions(), new Action [] { new AgpUpgradeRefactoringProcessorWithJava8SpecialCaseDialog.PreviewRefactoringAction() });
  }

  @Override
  protected void doOKAction() {
    if (myNoLanguageLevelActionComboBox != null) {
      NoLanguageLevelAction action = (NoLanguageLevelAction)myNoLanguageLevelActionComboBox.getSelectedItem();
      if (action != null) {
        myJava8Processor.setNoLanguageLevelAction(action);
      }
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
