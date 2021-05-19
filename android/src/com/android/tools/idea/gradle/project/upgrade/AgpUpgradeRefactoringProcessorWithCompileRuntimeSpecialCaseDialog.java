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
import static com.android.tools.idea.gradle.project.upgrade.Java8DefaultRefactoringProcessor.NoLanguageLevelAction.ACCEPT_NEW_DEFAULT;
import static com.android.tools.idea.gradle.project.upgrade.Java8DefaultRefactoringProcessor.NoLanguageLevelAction.INSERT_OLD_DEFAULT;
import static com.intellij.ide.BrowserUtil.browse;
import static com.intellij.openapi.application.ModalityState.NON_MODAL;
import static javax.swing.Action.NAME;

import com.android.tools.idea.gradle.project.upgrade.Java8DefaultRefactoringProcessor.NoLanguageLevelAction;
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
import java.util.Collections;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.event.HyperlinkEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// TODO(xof): this is largely a copy of AgpUpgradeRefactoringProcessorWithJava8SpecialCaseDialog, with some of the (unnecessary
//  for this case) functionality removed.  It would be nice to use inheritance or something to reduce the code duplication, but the
//  way that the GUI designer injects code (in initializer blocks, after the super() constructor runs but before the rest of the
//  constructor) makes it awkward, because DialogWrapper.init() depends on the GUI designer initialization having been done.
public class AgpUpgradeRefactoringProcessorWithCompileRuntimeSpecialCaseDialog extends DialogWrapper {
  private JPanel myPanel;
  private JEditorPane myEditorPane;

  @NotNull private AgpUpgradeRefactoringProcessor myProcessor;
  @NotNull private CompileRuntimeConfigurationRefactoringProcessor myCompileRuntimeProcessor;

  AgpUpgradeRefactoringProcessorWithCompileRuntimeSpecialCaseDialog(
    @NotNull AgpUpgradeRefactoringProcessor processor,
    @NotNull CompileRuntimeConfigurationRefactoringProcessor compileRuntimeProcessor
  ) {
    super(processor.getProject());
    myProcessor = processor;
    myCompileRuntimeProcessor = compileRuntimeProcessor;

    setTitle("Android Gradle Plugin Upgrade Assistant");
    init();

    setUpAsHtmlLabel(myEditorPane);
    myEditorPane.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        browse(e.getURL());
      }
    });

    for (AgpUpgradeComponentRefactoringProcessor p : myProcessor.getComponentRefactoringProcessors()) {
      p.setEnabled(p == myCompileRuntimeProcessor);
    }
    myProcessor.getClasspathRefactoringProcessor().setEnabled(false);

    StringBuilder sb = new StringBuilder();
    sb.append("<p>The following command will be executed to upgrade your project with replacements for deprecated <tt>compile</tt> " +
              "and <tt>runtime</tt> configurations:</p>");
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
    myEditorPane.setText(sb.toString());
    myPanel.setMinimumSize(new JBDimension(500, 99));
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  protected Action @NotNull [] createActions() {
    Action previewAction = new AgpUpgradeRefactoringProcessorWithCompileRuntimeSpecialCaseDialog.PreviewRefactoringAction();
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
