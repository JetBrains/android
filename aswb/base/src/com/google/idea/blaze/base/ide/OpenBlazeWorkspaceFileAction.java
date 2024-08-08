/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.ide;

import com.google.common.base.Preconditions;
import com.google.idea.blaze.base.actions.BlazeProjectAction;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.ui.UiUtil;
import com.google.idea.blaze.base.ui.WorkspaceFileTextField;
import com.intellij.ide.actions.OpenFileAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLabel;
import java.awt.GridBagLayout;
import javax.annotation.Nullable;
import javax.swing.JComponent;
import javax.swing.JPanel;

final class OpenBlazeWorkspaceFileAction extends BlazeProjectAction {

  @Override
  protected QuerySyncStatus querySyncSupport() {
    return QuerySyncStatus.SUPPORTED;
  }

  @Override
  protected void actionPerformedInBlazeProject(Project project, AnActionEvent e) {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return;
    }
    new OpenBlazeWorkspaceFileActionDialog(project, blazeProjectData.getWorkspacePathResolver())
        .show();
  }

  private static class OpenBlazeWorkspaceFileActionDialog extends DialogWrapper {

    static final int PATH_FIELD_WIDTH = 40;
    final Project project;

    final JPanel component;
    final WorkspaceFileTextField fileTextField;

    OpenBlazeWorkspaceFileActionDialog(
        Project project, WorkspacePathResolver workspacePathResolver) {
      super(project, /* canBeParent= */ false, IdeModalityType.PROJECT);
      this.project = project;

      component = new JPanel(new GridBagLayout());
      FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor();
      fileTextField =
          WorkspaceFileTextField.create(
              workspacePathResolver, descriptor, PATH_FIELD_WIDTH, myDisposable);

      component.add(new JBLabel("Path:"));
      component.add(fileTextField.getField(), UiUtil.getFillLineConstraints(0));

      UiUtil.fillBottom(component);
      init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      return component;
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
      return fileTextField.getField();
    }

    @Nullable
    @Override
    protected ValidationInfo doValidate() {
      VirtualFile selectedFile = fileTextField.getVirtualFile();
      if (selectedFile == null || !selectedFile.exists()) {
        return new ValidationInfo("File does not exist", fileTextField.getField());
      } else if (selectedFile.isDirectory()) {
        return new ValidationInfo("Directories can not be opened", fileTextField.getField());
      } else {
        return null;
      }
    }

    @Override
    protected void doOKAction() {
      VirtualFile selectedFile = fileTextField.getVirtualFile();
      // It cannot be null because it is validated in `doValidate()`
      Preconditions.checkArgument(selectedFile != null, "Virtual File cannot be null");
      OpenFileAction.openFile(selectedFile, project);
      super.doOKAction();
    }
  }
}
