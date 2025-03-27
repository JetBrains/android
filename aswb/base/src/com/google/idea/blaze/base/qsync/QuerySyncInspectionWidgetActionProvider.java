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

import com.google.idea.blaze.base.qsync.QuerySyncManager.OperationType;
import com.google.idea.blaze.base.qsync.action.BuildDependenciesHelper;
import com.google.idea.blaze.base.qsync.action.BuildDependenciesHelper.DepsBuildType;
import com.google.idea.blaze.base.qsync.action.PopupPositioner;
import com.google.idea.blaze.base.qsync.settings.QuerySyncConfigurableProvider;
import com.google.idea.blaze.base.qsync.settings.QuerySyncSettings;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.qsync.project.TargetsToBuild;
import com.intellij.icons.AllIcons.Actions;
import com.intellij.ide.HelpTooltip;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.editor.markup.InspectionWidgetActionProvider;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.GotItTooltip;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import java.awt.Point;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.HierarchyBoundsListener;
import java.awt.event.HierarchyEvent;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.plaf.FontUIResource;
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

    public BuildDependencies(@NotNull Editor editor) {
      super("");
      this.editor = editor;
      buildDepsHelper = new BuildDependenciesHelper(editor.getProject(), DepsBuildType.SELF);
    }

    @Override
    public ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      buildDepsHelper.enableAnalysis(
          getClass(), e, PopupPositioner.showUnderneathClickedComponentOrCentered(e));
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
      TargetsToBuild toBuild = buildDepsHelper.getTargetsToEnableAnalysisFor(vf);

      if (toBuild.isEmpty()) {
        presentation.setEnabled(false);
        return;
      }

      presentation.setEnabled(true);
      if (toBuild.type() == TargetsToBuild.Type.SOURCE_FILE
          && QuerySyncSettings.getInstance().showDetailedInformationInEditor()) {

        int missing = buildDepsHelper.getSourceFileMissingDepsCount(toBuild);
        if (missing > 0) {
          String dependency = StringUtil.pluralize("dependency", missing);
          presentation.setText(
              String.format("Analysis disabled - missing %d %s ", missing, dependency));
        }
      }
    }

    @Override
    @NotNull
    public JComponent createCustomComponent(
        @NotNull Presentation presentation, @NotNull String place) {
      presentation.setIcon(Actions.Compile);
      presentation.setText("");
      ActionButtonWithText button =
          new ActionButtonWithText(this, presentation, place, JBUI.size(16)) {

            @Override
            protected void updateToolTipText() {
              Project project = editor.getProject();
              if (project == null) {
                return;
              }
              HelpTooltip.dispose(this);
              createPrimaryTooltip(project).installOn(this);
            }
          };

      button.setHorizontalTextPosition(SwingConstants.LEFT);
      button.setFont(
          new FontUIResource(
              button
                  .getFont()
                  .deriveFont(
                      button.getFont().getStyle(),
                      button.getFont().getSize() - JBUIScale.scale(2))));

      createGotItTooltip(button);

      return button;
    }

    private HelpTooltip createPrimaryTooltip(Project project) {
      if (fileInEditorHasNoTargetsToBuild(project)) {
        return new HelpTooltip()
            .setTitle(QuerySync.BUILD_DEPENDENCIES_ACTION_NAME)
            .setDescription(
                "This file is not owned by a project target with external dependencies.");
      } else {
        return new HelpTooltip()
            .setTitle(QuerySync.BUILD_DEPENDENCIES_ACTION_NAME)
            .setShortcut(ActionManager.getInstance().getKeyboardShortcut("Blaze.BuildDependencies"))
            .setDescription(
                "Builds the external dependencies needed for this file and enables analysis")
            .setLink(
                "Settings...",
                () ->
                    ShowSettingsUtil.getInstance()
                        .showSettingsDialog(
                            project, QuerySyncConfigurableProvider.getConfigurableClass()));
      }
    }

    private boolean fileInEditorHasNoTargetsToBuild(Project project) {
      PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      VirtualFile vf = psiFile != null ? psiFile.getVirtualFile() : null;
      QuerySyncManager querySyncManager = QuerySyncManager.getInstance(project);
      if (vf != null
          && querySyncManager.isProjectLoaded()
          && !querySyncManager.operationInProgress()) {
        TargetsToBuild toBuild = buildDepsHelper.getTargetsToEnableAnalysisFor(vf);
        return toBuild.isEmpty();
      }
      return false;
    }

    private void createGotItTooltip(ActionButtonWithText button) {
      Project project = editor.getProject();
      if (project != null) {
        QuerySyncManager querySyncManager = QuerySyncManager.getInstance(project);
        String learnMore =
            querySyncManager.getQuerySyncUrl()
            .map(l -> " To learn more about these changes: " + l)
            .orElse("");
        GotItTooltip gotIt =
            new GotItTooltip(
                    "query.sync.got.it",
                    "ASwB no longer builds your entire project during sync. Now you decide whether"
                        + " to build file dependencies for advanced code editing features or not."
                        + " Without building dependencies, you can still navigate your codebase and"
                        + " make light code edits. For analysis and deeper code editing, click the"
                        + " hammer icon to build this file's dependencies."
                        + learnMore,
                    project)
                .withHeader("Welcome to query sync");
        // Ideally we would attach the balloon to the button, however the balloon disappears when
        // the component it's attached to is hidden. Unfortunatelly the visibility of traffic
        // light components is flaky and they pop in and out of existence causing the pop up to
        // go away almost immediately. To solve this the reader mode one, attempts to wait until
        // the traffic light settles (see:
        // https://github.com/JetBrains/intellij-community/blob/155fabab65515352ea782dca0bc22ac1e67bde43/platform/lang-impl/src/com/intellij/codeInsight/actions/ReaderModeActionProvider.kt#L115
        //
        // Here we use a different approach, instead of attaching to the button we attach to the
        // editor and provide a position provider to track the button instead. This results
        // in a more stable behaviour without attaching to the global message bus.
        gotIt
            .setOnBalloonCreated(
                balloon -> {
                  // Add the listeners to track the button's position.
                  button.addHierarchyBoundsListener(new BalloonHierarchyBoundsListener(balloon));
                  button.addComponentListener(new BalloonComponentListener(balloon));
                  return null;
                })
            .show(
                editor.getContentComponent(),
                (component, balloon) -> {
                  Point p = SwingUtilities.convertPoint(button, 0, 0, component);
                  Point pos2 = new Point(p.x + button.getWidth() / 2, p.y + button.getHeight());

                  pos2.x = Math.min(Math.max(pos2.x, 0), component.getWidth());
                  pos2.y = Math.min(Math.max(pos2.y, 0), component.getHeight());
                  return pos2;
                });
      }
    }

    private static class BalloonComponentListener implements ComponentListener {

      private final Balloon balloon;

      public BalloonComponentListener(Balloon balloon) {
        this.balloon = balloon;
      }

      @Override
      public void componentResized(ComponentEvent componentEvent) {
        balloon.revalidate();
      }

      @Override
      public void componentMoved(ComponentEvent componentEvent) {
        balloon.revalidate();
      }

      @Override
      public void componentShown(ComponentEvent componentEvent) {
        balloon.revalidate();
      }

      @Override
      public void componentHidden(ComponentEvent componentEvent) {}
    }

    private static class BalloonHierarchyBoundsListener implements HierarchyBoundsListener {

      private final Balloon balloon;

      public BalloonHierarchyBoundsListener(Balloon balloon) {
        this.balloon = balloon;
      }

      @Override
      public void ancestorMoved(HierarchyEvent hierarchyEvent) {
        balloon.revalidate();
      }

      @Override
      public void ancestorResized(HierarchyEvent hierarchyEvent) {
        balloon.revalidate();
      }
    }
  }
}
