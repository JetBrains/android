/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.actions;

import com.google.idea.blaze.base.lang.buildfile.search.BlazePackage;
import com.intellij.ide.actions.OpenFileAction;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import java.io.File;

class OpenCorrespondingBuildFile extends BlazeProjectAction {

  @Override
  protected QuerySyncStatus querySyncSupport() {
    return QuerySyncStatus.SUPPORTED;
  }

  @Override
  protected void actionPerformedInBlazeProject(Project project, AnActionEvent e) {
    VirtualFile vf = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (vf == null) {
      return;
    }
    navigateToTargetOrFile(project, vf);
  }

  /** Returns true if a target or BUILD file could be found and navigated to. */
  private static void navigateToTargetOrFile(Project project, VirtualFile vf) {
    // First, find the parent BUILD file. We don't want to navigate to labels in other packages
    BlazePackage parentPackage = BuildFileUtils.getBuildFile(project, vf);
    if (parentPackage == null) {
      return;
    }
    // first, look for a specific target which includes this source file
    PsiElement target =
        BuildFileUtils.findBuildTarget(project, parentPackage, new File(vf.getPath()));
    if (target instanceof NavigatablePsiElement) {
      ((NavigatablePsiElement) target).navigate(true);
      return;
    }
    OpenFileAction.openFile(parentPackage.buildFile.getFile().getPath(), project);
  }

  @Override
  protected void updateForBlazeProject(Project project, AnActionEvent e) {
    Presentation presentation = e.getPresentation();

    DataContext dataContext = e.getDataContext();
    VirtualFile virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    BlazePackage blazePackage = BuildFileUtils.getBuildFile(project, virtualFile);
    if (blazePackage != null && virtualFile.equals(blazePackage.buildFile.getVirtualFile())) {
      presentation.setEnabledAndVisible(false);
      return;
    }

    boolean enabled = blazePackage != null;
    presentation.setVisible(enabled || !ActionPlaces.isPopupPlace(e.getPlace()));
    presentation.setEnabled(enabled);
  }
}
