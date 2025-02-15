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
import static com.intellij.ide.BrowserUtil.browse;
import static javax.swing.Action.NAME;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.JBDimension;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.event.HyperlinkEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// TODO(xof): this is largely a copy of AgpUpgradeRefactoringProcessorDialog, with some of the (unnecessary
//  for this case) functionality removed.  It would be nice to use inheritance or something to reduce the code duplication, but the
//  way that the GUI designer injects code (in initializer blocks, after the super() constructor runs but before the rest of the
//  constructor) makes it awkward, because DialogWrapper.init() depends on the GUI designer initialization having been done.
public class AgpUpgradeRefactoringProcessorWithCompileRuntimeSpecialCaseDialog extends DialogWrapper {
  private JPanel myPanel;
  private JEditorPane myEditorPane;

  @NotNull private AgpUpgradeRefactoringProcessor myProcessor;
  @NotNull private CompileRuntimeConfigurationRefactoringProcessor myCompileRuntimeProcessor;

  AgpUpgradeRefactoringProcessorWithCompileRuntimeSpecialCaseDialog(@NotNull AgpUpgradeRefactoringProcessor processor) {
    this(processor,
         (CompileRuntimeConfigurationRefactoringProcessor)processor.getComponentRefactoringProcessors().stream()
           .filter((x) -> x instanceof CompileRuntimeConfigurationRefactoringProcessor).findFirst().get());
  }
  AgpUpgradeRefactoringProcessorWithCompileRuntimeSpecialCaseDialog(
    @NotNull AgpUpgradeRefactoringProcessor processor,
    @NotNull CompileRuntimeConfigurationRefactoringProcessor compileRuntimeProcessor
  ) {
    super(processor.getProject());
    setupUI();
    myProcessor = processor;
    myCompileRuntimeProcessor = compileRuntimeProcessor;

    setTitle("Android Gradle Plugin Upgrade Assistant");
    init();

    setUpAsHtmlLabel(myEditorPane);
    myEditorPane.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(@NotNull HyperlinkEvent e) {
        browse(e.getURL());
      }
    });

    for (AgpUpgradeComponentRefactoringProcessor p : myProcessor.getComponentRefactoringProcessors()) {
      p.setEnabled(p == myCompileRuntimeProcessor);
    }

    StringBuilder sb = new StringBuilder();
    sb.append("<p>The following command will be executed to upgrade your project with replacements for deprecated <tt>compile</tt> " +
              "and <tt>runtime</tt> configurations:</p>");
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
    myEditorPane.setText(sb.toString());
    myPanel.setMinimumSize(new JBDimension(500, 99));
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  protected Action[] createActions() {
    Action previewAction = new AgpUpgradeRefactoringProcessorWithCompileRuntimeSpecialCaseDialog.PreviewRefactoringAction();
    return ArrayUtil.mergeArrays(super.createActions(), new Action[]{previewAction});
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

  private void setupUI() {
    myPanel = new JPanel();
    myPanel.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
    final Spacer spacer1 = new Spacer();
    myPanel.add(spacer1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                             GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    myEditorPane = new JEditorPane();
    myPanel.add(myEditorPane, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                  GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_WANT_GROW, null,
                                                  new Dimension(150, 50), null, 0, false));
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
