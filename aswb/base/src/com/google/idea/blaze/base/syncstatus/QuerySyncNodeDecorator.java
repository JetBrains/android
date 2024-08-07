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
package com.google.idea.blaze.base.syncstatus;

import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.qsync.QuerySyncManager;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ProjectViewNodeDecorator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.ui.PackageDependenciesNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import java.util.Set;

/** Shows the number of external dependencies that need to be build for a source file. */
public class QuerySyncNodeDecorator implements ProjectViewNodeDecorator {

  private static final BoolExperiment ENABLED =
      new BoolExperiment("query.sync.decorate.nodes", false);

  @Override
  public void decorate(ProjectViewNode<?> node, PresentationData data) {
    if (!ENABLED.getValue()) {
      return;
    }
    Project project = node.getProject();
    if (project == null || Blaze.getProjectType(project) != ProjectType.QUERY_SYNC) {
      return;
    }

    VirtualFile vf = node.getVirtualFile();

    // Tree nodes may be KtClassOrObjectTreeNodes, for which Virtual Files can be determined via the
    // associated PsiElement
    if (vf == null && node.getValue() instanceof PsiElement) {
      vf = PsiUtilCore.getVirtualFile((PsiElement) node.getValue());
    }

    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);
    if (vf == null || !workspaceRoot.isInWorkspace(vf)) {
      return;
    }
    QuerySyncProjectSnapshot snapshot =
        QuerySyncManager.getInstance(project).getCurrentSnapshot().orElse(null);
    if (snapshot == null) {
      return;
    }
    Set<Label> targets = snapshot.getPendingTargets(workspaceRoot.relativize(vf));
    if (!targets.isEmpty()) {
      String text = data.getPresentableText();
      data.clearText();
      data.addText(text, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      data.addText(String.format(" (%s)", targets.size()), SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }

  @Override
  public void decorate(PackageDependenciesNode node, ColoredTreeCellRenderer cellRenderer) {}
}
