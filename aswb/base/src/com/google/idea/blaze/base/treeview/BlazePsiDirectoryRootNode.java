/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.treeview;

import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.ui.SimpleTextAttributes;

/**
 * A PsiDirectoryNode that represents a directory root, rendering the whole directory name from the
 * workspace root.
 */
public class BlazePsiDirectoryRootNode extends BlazePsiDirectoryNode {
  public BlazePsiDirectoryRootNode(Project project, PsiDirectory directory, ViewSettings settings) {
    super(project, directory, settings);
  }

  @Override
  protected void updateImpl(PresentationData data) {
    super.updateImpl(data);
    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(getProject());
    PsiDirectory psiDirectory = getValue();
    assert psiDirectory != null;
    WorkspacePath workspacePath = workspaceRoot.workspacePathFor(psiDirectory.getVirtualFile());
    String text = workspacePath.relativePath();

    for (BlazePsiDirectoryRootNodeNameModifier modifier :
        BlazePsiDirectoryRootNodeNameModifier.EP_NAME.getExtensions()) {
      text = modifier.modifyRootNodeName(text);
    }

    data.setPresentableText(text);
    data.clearText();
    data.addText(text, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    data.setLocationString("");
  }
}
