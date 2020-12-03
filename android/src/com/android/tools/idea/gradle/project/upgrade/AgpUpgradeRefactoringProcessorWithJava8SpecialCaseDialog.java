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
import static com.android.tools.idea.gradle.project.upgrade.AgpUpgradeRefactoringProcessorKt.notifyCancelledUpgrade;
import static com.android.tools.idea.gradle.project.upgrade.GradlePluginUpgrade.isCleanEnoughProject;
import static com.android.tools.idea.gradle.project.upgrade.Java8DefaultRefactoringProcessor.NoLanguageLevelAction.ACCEPT_NEW_DEFAULT;
import static com.android.tools.idea.gradle.project.upgrade.Java8DefaultRefactoringProcessor.NoLanguageLevelAction.INSERT_OLD_DEFAULT;
import static com.intellij.ide.BrowserUtil.browse;
import static com.intellij.openapi.application.ModalityState.NON_MODAL;
import static javax.swing.Action.NAME;

import com.android.tools.idea.gradle.project.upgrade.Java8DefaultRefactoringProcessor.NoLanguageLevelAction;
import com.intellij.openapi.application.ActionsKt;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.components.JBLabel;
import com.intellij.usages.UsageView;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.UIUtil;
import java.awt.event.ActionEvent;
import java.util.Arrays;
import java.util.Collections;
import javax.swing.AbstractAction;
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

  @NotNull private AgpUpgradeRefactoringProcessor myProcessor;
  @NotNull private Java8DefaultRefactoringProcessor myJava8Processor;

  AgpUpgradeRefactoringProcessorWithJava8SpecialCaseDialog(
    @NotNull AgpUpgradeRefactoringProcessor processor,
    @NotNull Java8DefaultRefactoringProcessor java8Processor,
    boolean hasChangedBuildFiles
  ) {
    this(processor, java8Processor, hasChangedBuildFiles, false);
  }

  AgpUpgradeRefactoringProcessorWithJava8SpecialCaseDialog(
    @NotNull AgpUpgradeRefactoringProcessor processor,
    @NotNull Java8DefaultRefactoringProcessor java8Processor,
    boolean hasChangedBuildFiles,
    boolean processorAlreadyConfiguredForJava8Dialog
  ) {
    super(processor.getProject());
    myProcessor = processor;
    myJava8Processor = java8Processor;

    setTitle("Android Gradle Plugin Upgrade Assistant");
    init();

    setUpAsHtmlLabel(myEditorPane);
    myEditorPane.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        browse(e.getURL());
      }
    });

    NoLanguageLevelAction initialNoLanguageLevelAction;
    if (processorAlreadyConfiguredForJava8Dialog) {
      initialNoLanguageLevelAction = myJava8Processor.getNoLanguageLevelAction();
    }
    else {
      initialNoLanguageLevelAction = ACCEPT_NEW_DEFAULT;
      for (AgpUpgradeComponentRefactoringProcessor p : myProcessor.getComponentRefactoringProcessors()) {
        AgpUpgradeComponentNecessity necessity = p.necessity();
        p.setEnabled(necessity == MANDATORY_CODEPENDENT || necessity == MANDATORY_INDEPENDENT);
      }
      Action backAction = new AbstractAction(UIUtil.replaceMnemonicAmpersand("&Back")) {
        @Override
        public void actionPerformed(ActionEvent e) {
          boolean hasChangesInBuildFiles = !isCleanEnoughProject(myProcessor.getProject());
          boolean runProcessor = ActionsKt.invokeAndWaitIfNeeded(
            NON_MODAL,
            () -> {
              DialogWrapper dialog = new AgpUpgradeRefactoringProcessorWithJava8SpecialCaseDialog(
                myProcessor, myJava8Processor, hasChangesInBuildFiles, true);
              return dialog.showAndGet();
            });
            if (runProcessor) {
              DumbService.getInstance(myProcessor.getProject()).smartInvokeLater(() -> myProcessor.run());
            }
            else {
              notifyCancelledUpgrade(myProcessor.getProject(), myProcessor);
            }
        }
      };
      myProcessor.setBackFromPreviewAction(backAction);
    }

    StringBuilder sb = new StringBuilder();
    sb.append("<p>The following commands will be executed to upgrade your project from Android Gradle Plugin version ")
      .append(myProcessor.getCurrent()).append(" to version ").append(myProcessor.getNew()).append(":</p>");
    sb.append("<br/><ul>");
    for (AgpUpgradeComponentRefactoringProcessor p : myProcessor.getComponentRefactoringProcessors()) {
      if (p.isEnabled() && !p.isAlwaysNoOpForProject()) {
        sb.append("<li>").append(p.getCommandName()).append(".");
        String url = p.getReadMoreUrl();
        if (url != null) {
          sb.append(" <a href='").append(url).append("'>Read more</a>.");
        }
        sb.append("</li>");
      }
    }
    if (myProcessor.getClasspathRefactoringProcessor().isEnabled()) {
      sb.append("<li>").append(myProcessor.getClasspathRefactoringProcessor().getCommandName()).append(".").append("</li>");
    }
    sb.append("</ul>");

    if (hasChangedBuildFiles) {
      sb.append("<br/><p><b>Warning</b>: there are uncommitted changes in project build files.  Before upgrading, " +
                "you should commit or revert changes to the build files so that changes from the upgrade process " +
                "can be handled separately.</p>");
    }
    myEditorPane.setText(sb.toString());

    if (myJava8Processor.isEnabled() && !myJava8Processor.isAlwaysNoOpForProject()) {
      JBLabel label = new JBLabel("Action on no explicit Java language level: ");
      myJava8SettingsPanel.add(label);
      myNoLanguageLevelActionComboBox = new ComboBox<>(new NoLanguageLevelAction[] {ACCEPT_NEW_DEFAULT, INSERT_OLD_DEFAULT});
      myNoLanguageLevelActionComboBox.setSelectedItem(initialNoLanguageLevelAction);
      myJava8SettingsPanel.add(myNoLanguageLevelActionComboBox);
      myJava8SettingsPanel.setVisible(true);
    }
    else {
      myJava8SettingsPanel.setVisible(false);
    }
    myPanel.setPreferredSize(new JBDimension(500, -1));
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  protected Action @NotNull [] createActions() {
    Action previewAction = new AgpUpgradeRefactoringProcessorWithJava8SpecialCaseDialog.PreviewRefactoringAction();
    return ArrayUtil.mergeArrays(super.createActions(), new Action [] { previewAction });
  }

  @Override
  public void doCancelAction() {
    if (myProcessor.getUsageView() != null) {
      myProcessor.getUsageView().close();
    }
    super.doCancelAction();
  }

  @Override
  protected void doOKAction() {
    myProcessor.setPreviewUsages(false);
    if (myProcessor.getUsageView() != null) {
      myProcessor.getUsageView().close();
    }
    doOKActionWithPreviewState();
  }

  private void doOKActionWithPreviewState() {
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
      doOKActionWithPreviewState();
    }
  }
}
