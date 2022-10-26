/*
 * Copyright (C) 2022 The Android Open Source Project
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
import static com.intellij.ide.BrowserUtil.browse;
import static com.intellij.openapi.application.ModalityState.NON_MODAL;
import static javax.swing.Action.NAME;

import com.android.tools.idea.gradle.project.upgrade.Java8DefaultRefactoringProcessor.NoLanguageLevelAction;
import com.android.tools.idea.gradle.project.upgrade.R8FullModeDefaultRefactoringProcessor.NoPropertyPresentAction;
import com.intellij.openapi.application.ActionsKt;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.UIUtil;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.event.HyperlinkEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

public class AgpUpgradeRefactoringProcessorDialog extends DialogWrapper {
  private JPanel myPanel;
  private JEditorPane myEditorPane;
  private JPanel myJava8SettingsPanel;
  private JPanel myR8FullModeSettingsPanel;
  private ComboBox<NoLanguageLevelAction> myNoLanguageLevelActionComboBox;
  private ComboBox<NoPropertyPresentAction> myNoPropertyPresentActionComboBox;

  @NotNull private AgpUpgradeRefactoringProcessor myProcessor;
  @NotNull private Java8DefaultRefactoringProcessor myJava8Processor;
  @NotNull private R8FullModeDefaultRefactoringProcessor myR8FullModeProcessor;

  AgpUpgradeRefactoringProcessorDialog(@NotNull AgpUpgradeRefactoringProcessor processor, boolean hasChangedBuildFiles) {
    this(processor,
         (Java8DefaultRefactoringProcessor)processor.getComponentRefactoringProcessors().stream()
           .filter((x) -> x instanceof Java8DefaultRefactoringProcessor).findFirst().get(),
         (R8FullModeDefaultRefactoringProcessor)processor.getComponentRefactoringProcessors().stream()
           .filter((x) -> x instanceof R8FullModeDefaultRefactoringProcessor).findFirst().get(),
         hasChangedBuildFiles);
  }

  AgpUpgradeRefactoringProcessorDialog(
    @NotNull AgpUpgradeRefactoringProcessor processor,
    @NotNull Java8DefaultRefactoringProcessor java8Processor,
    @NotNull R8FullModeDefaultRefactoringProcessor r8FullModeProcessor,
    boolean hasChangedBuildFiles
  ) {
    this(processor, java8Processor, r8FullModeProcessor, hasChangedBuildFiles, false, false);
  }

  AgpUpgradeRefactoringProcessorDialog(
    @NotNull AgpUpgradeRefactoringProcessor processor,
    @NotNull Java8DefaultRefactoringProcessor java8Processor,
    @NotNull R8FullModeDefaultRefactoringProcessor r8FullModeProcessor,
    boolean hasChangedBuildFiles,
    boolean preserveComponentProcessorConfigurations
  ) {
    this(processor, java8Processor, r8FullModeProcessor, hasChangedBuildFiles, preserveComponentProcessorConfigurations, false);
  }

  @VisibleForTesting
  AgpUpgradeRefactoringProcessorDialog(
    @NotNull AgpUpgradeRefactoringProcessor processor,
    @NotNull Java8DefaultRefactoringProcessor java8Processor,
    @NotNull R8FullModeDefaultRefactoringProcessor r8FullModeProcessor,
    boolean hasChangedBuildFiles,
    boolean preserveComponentProcessorConfigurations,
    boolean processorAlreadyConfiguredForJava8Dialog
  ) {
    super(processor.getProject());
    myProcessor = processor;
    myJava8Processor = java8Processor;
    myR8FullModeProcessor = r8FullModeProcessor;

    setTitle("Android Gradle Plugin Upgrade Assistant");
    init();

    setUpAsHtmlLabel(myEditorPane);
    myEditorPane.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(@NotNull HyperlinkEvent e) {
        browse(e.getURL());
      }
    });

    NoLanguageLevelAction initialNoLanguageLevelAction;
    NoPropertyPresentAction initialNoPropertyPresentAction;
    if (preserveComponentProcessorConfigurations) {
      initialNoLanguageLevelAction = myJava8Processor.getNoLanguageLevelAction();
      initialNoPropertyPresentAction = myR8FullModeProcessor.getNoPropertyPresentAction();
    }
    else {
      initialNoLanguageLevelAction = NoLanguageLevelAction.ACCEPT_NEW_DEFAULT;
      initialNoPropertyPresentAction = NoPropertyPresentAction.ACCEPT_NEW_DEFAULT;
      for (AgpUpgradeComponentRefactoringProcessor p : myProcessor.getComponentRefactoringProcessors()) {
        AgpUpgradeComponentNecessity necessity = p.necessity();
        p.setEnabled(necessity == MANDATORY_CODEPENDENT || necessity == MANDATORY_INDEPENDENT);
      }
    }

    if (!processorAlreadyConfiguredForJava8Dialog) {
      Action backAction = new AbstractAction(UIUtil.replaceMnemonicAmpersand("&Back")) {
        @Override
        public void actionPerformed(ActionEvent e) {
          boolean hasChangesInBuildFiles = !isCleanEnoughProject(myProcessor.getProject());
          boolean runProcessor = ActionsKt.invokeAndWaitIfNeeded(
            NON_MODAL,
            () -> {
              DialogWrapper dialog = new AgpUpgradeRefactoringProcessorDialog(
                myProcessor, myJava8Processor, myR8FullModeProcessor, hasChangesInBuildFiles, true, true);
              return dialog.showAndGet();
            });
            if (runProcessor) {
              DumbService.getInstance(myProcessor.getProject()).smartInvokeLater(() -> myProcessor.run());
            }
            else {
              notifyCancelledUpgrade(myProcessor.getProject());
            }
        }
      };
      myProcessor.setBackFromPreviewAction(backAction);
    }

    StringBuilder sb = new StringBuilder();
    sb.append("<p>The following commands will be executed to upgrade your project");
    if (myProcessor.getAgpVersionRefactoringProcessor().isEnabled()) {
      sb.append(" from Android Gradle Plugin version ").append(myProcessor.getCurrent())
        .append(" to version ").append(myProcessor.getNew());
    }
    sb.append(":</p>");
    sb.append("<br/><ul>");
    for (AgpUpgradeComponentRefactoringProcessor p : myProcessor.getComponentRefactoringProcessorsWithAgpVersionProcessorLast()) {
      if (p.isEnabled() && !p.isAlwaysNoOpForProject()) {
        sb.append("<li>").append(p.getCommandName()).append(".");
        String url = p.getReadMoreUrl();
        if (url != null) {
          sb.append(" <a href='").append(url).append("'>Read more</a>.");
        }
        sb.append("</li>");
      }
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
      myNoLanguageLevelActionComboBox = new ComboBox<>(new NoLanguageLevelAction[] {
        NoLanguageLevelAction.ACCEPT_NEW_DEFAULT,
        NoLanguageLevelAction.INSERT_OLD_DEFAULT
      });
      myNoLanguageLevelActionComboBox.setSelectedItem(initialNoLanguageLevelAction);
      myJava8SettingsPanel.add(myNoLanguageLevelActionComboBox);
      myJava8SettingsPanel.setVisible(true);
    }
    else {
      myJava8SettingsPanel.setVisible(false);
    }

    if (myR8FullModeProcessor.isEnabled() && !myR8FullModeProcessor.isAlwaysNoOpForProject()) {
      JBLabel label = new JBLabel("Action on no android.enableR8.fullMode property: ");
      myR8FullModeSettingsPanel.add(label);
      myNoPropertyPresentActionComboBox = new ComboBox<>(new NoPropertyPresentAction[] {
        NoPropertyPresentAction.ACCEPT_NEW_DEFAULT,
        NoPropertyPresentAction.INSERT_OLD_DEFAULT
      });
      myNoPropertyPresentActionComboBox.setSelectedItem(initialNoPropertyPresentAction);
      myR8FullModeSettingsPanel.add(myNoPropertyPresentActionComboBox);
      myR8FullModeSettingsPanel.setVisible(true);
    }
    else {
      myR8FullModeSettingsPanel.setVisible(false);
    }

    myPanel.setPreferredSize(new JBDimension(500, -1));
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  protected Action @NotNull [] createActions() {
    Action previewAction = new AgpUpgradeRefactoringProcessorDialog.PreviewRefactoringAction();
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
    if (myNoPropertyPresentActionComboBox != null) {
      NoPropertyPresentAction action = (NoPropertyPresentAction)myNoPropertyPresentActionComboBox.getSelectedItem();
      if (action != null) {
        myR8FullModeProcessor.setNoPropertyPresentAction(action);
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
      super("Show Usages");
    }

    @Override
    protected void doAction(ActionEvent e) {
      myProcessor.setPreviewUsages(true);
      doOKActionWithPreviewState();
    }
  }
}
