/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync;

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.idea.blaze.base.qsync.action.ActionUtil.getVirtualFiles;
import static com.google.idea.blaze.base.qsync.action.BuildDependenciesHelperSelectTargetPopup.createDisambiguateTargetPrompt;
import static kotlinx.coroutines.guava.ListenableFutureKt.asDeferred;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStatsScope;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.qsync.QuerySyncManager.OperationType;
import com.google.idea.blaze.base.qsync.action.BuildDependenciesHelper;
import com.google.idea.blaze.base.qsync.action.PopupPositioner;
import com.google.idea.blaze.base.qsync.action.TargetDisambiguationAnchors;
import com.google.idea.blaze.base.qsync.settings.QuerySyncSettings;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.qsync.project.TargetsToBuild;
import com.intellij.icons.AllIcons.Actions;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.editor.markup.InspectionWidgetActionProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides the actions to be used with the inspection widget. The inspection widget is the
 * tri-color icon at the top-right of files showing analysis results. This class provides the action
 * that sits there and builds the file dependencies and enables analysis
 */
public class QuerySyncInspectionWidgetActionProvider implements InspectionWidgetActionProvider {

  @Nullable
  @Override
  public AnAction createAction(@NotNull Editor editor) {
    if (Blaze.getProjectType(editor.getProject()) != ProjectType.QUERY_SYNC) {
      return null;
    }
    if (!editor.getEditorKind().equals(EditorKind.MAIN_EDITOR)) {
      return null;
    }
    return new BuildDependencies(editor);
  }

  private static class BuildDependencies extends AnAction
      implements CustomComponentAction, DumbAware {

    private final Editor editor;
    private final BuildDependenciesHelper buildDepsHelper;
    public final QuerySyncManager syncManager;

    public BuildDependencies(@NotNull Editor editor) {
      super("");
      this.editor = editor;
      buildDepsHelper = new BuildDependenciesHelper(editor.getProject());
      syncManager = QuerySyncManager.getInstance(editor.getProject());
    }

    @Override
    public ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      List<VirtualFile> vfs = getVirtualFiles(e);
      QuerySyncActionStatsScope querySyncActionStats =
        QuerySyncActionStatsScope.createForFiles(getClass(), e, ImmutableList.copyOf(vfs));
      buildDepsHelper.determineTargetsAndRun(
        WorkspaceRoot.virtualFilesToWorkspaceRelativePaths(e.getProject(), vfs),
        createDisambiguateTargetPrompt(PopupPositioner.showUnderneathClickedComponentOrCentered(e)),
        new TargetDisambiguationAnchors.WorkingSet(buildDepsHelper),
        querySyncActionStats,
        labels ->
          asDeferred(
            syncManager.enableAnalysis(Sets.union(labels, buildDepsHelper.getWorkingSetTargetsIfEnabled()), querySyncActionStats,
                                       QuerySyncManager.TaskOrigin.USER_ACTION))
      );
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      Project project = e.getProject();
      presentation.setText("");
      if (project == null) {
        presentation.setEnabled(false);
        return;
      }

      PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      VirtualFile vf = psiFile != null ? psiFile.getVirtualFile() : null;
      if (vf == null) {
        presentation.setEnabled(false);
        return;
      }

      Optional<OperationType> currentOperation =
          QuerySyncManager.getInstance(project).currentOperation();
      if (currentOperation.isPresent()) {
        presentation.setEnabled(false);
        presentation.setText(
            currentOperation.get() == OperationType.SYNC
                ? "Syncing project..."
                : "Building dependencies...");
        return;
      }
      Set<TargetsToBuild> toBuild = buildDepsHelper.getTargetsToEnableAnalysisForPaths(
        WorkspaceRoot.virtualFilesToWorkspaceRelativePaths(e.getProject(), ImmutableList.of(vf)));

      if (toBuild.isEmpty()) {
        // TODO: b/411054914 - Build dependencies actions should not get disabled when not in sync/not in a project target and instead
        // they should automatically trigger sync.
        presentation.setEnabled(false);
        return;
      }

      presentation.setEnabled(true);
      if (toBuild.size() == 1 &&  getOnlyElement(toBuild) instanceof TargetsToBuild.SourceFile sourceFile
          && QuerySyncSettings.getInstance().showDetailedInformationInEditor()) {

        int missing = buildDepsHelper.getSourceFileMissingDepsCount(sourceFile);
        if (missing > 0) {
          String dependency = StringUtil.pluralize("dependency", missing);
          presentation.setText(
              String.format(Locale.ROOT, "Analysis disabled - missing %d %s ", missing, dependency));
        }
      }
    }

    @Override
    @NotNull
    public JComponent createCustomComponent(
        @NotNull Presentation presentation, @NotNull String place) {
      presentation.setIcon(Actions.Compile);
      presentation.setText("");

      return new QuerySyncWidget(this, presentation, place, editor, buildDepsHelper).component();
    }
  }
}
