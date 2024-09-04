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
package com.google.idea.blaze.base.dependencies;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.logging.EventLoggingService;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewEdit;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.sections.AutomaticallyDeriveTargetsSection;
import com.google.idea.blaze.base.projectview.section.sections.DirectoryEntry;
import com.google.idea.blaze.base.projectview.section.sections.DirectorySection;
import com.google.idea.blaze.base.projectview.section.sections.TargetSection;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.settings.ui.OpenProjectViewAction;
import com.google.idea.blaze.base.sync.BlazeSyncManager;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverProvider;
import com.google.idea.blaze.base.targetmaps.SourceToTargetMap;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.swing.event.HyperlinkEvent;

class AddSourceToProjectHelper {

  static boolean autoDeriveTargets(Project project) {
    return ProjectViewManager.getInstance(project)
        .getProjectViewSet()
        .getScalarValue(AutomaticallyDeriveTargetsSection.KEY)
        .orElse(false);
  }

  /**
   * Given the workspace targets building a source file, updates the .blazeproject 'directories' and
   * 'targets' sections accordingly.
   */
  static void addSourceToProject(
      Project project,
      WorkspacePath workspacePath,
      boolean inProjectDirectories,
      Future<List<TargetInfo>> targetsFuture) {
    EventLoggingService.getInstance()
        .logEvent(AddSourceToProjectHelper.class, "AddSourceToProject");
    List<TargetInfo> targets;
    try {
      targets = targetsFuture.get();
    } catch (InterruptedException | ExecutionException e) {
      return;
    }
    boolean addDirectory = !inProjectDirectories;
    boolean addTarget = !targets.isEmpty();
    if (!addDirectory && !addTarget) {
      return;
    }
    if (targets.size() <= 1) {
      AddSourceToProjectHelper.addSourceAndTargetsToProject(
          project, workspacePath, convertTargets(targets));
      return;
    }
    AddSourceToProjectDialog dialog = new AddSourceToProjectDialog(project, targets);
    dialog
        .showAndGetOk()
        .doWhenDone(
            (Consumer<Boolean>)
                ok -> {
                  if (ok) {
                    AddSourceToProjectHelper.addSourceAndTargetsToProject(
                        project, workspacePath, convertTargets(dialog.getSelectedTargets()));
                  }
                });
  }

  private static List<Label> convertTargets(List<TargetInfo> targets) {
    return targets.stream().map(t -> t.label).collect(Collectors.toList());
  }

  /**
   * Adds the parent directory of the specified {@link WorkspacePath}, and the given targets to the
   * project view.
   */
  static void addSourceAndTargetsToProject(
      Project project, WorkspacePath workspacePath, List<? extends TargetExpression> targets) {
    ImportRoots roots = ImportRoots.forProjectSafe(project);
    if (roots == null) {
      notifyFailed(
          project, "Couldn't parse existing project view file. Please sync the project and retry.");
      return;
    }
    WorkspacePath parentPath = Preconditions.checkNotNull(workspacePath.getParent());

    boolean addDirectory = !roots.containsWorkspacePath(parentPath);
    if (targets.isEmpty() && !addDirectory) {
      return;
    }
    ProjectViewEdit edit =
        ProjectViewEdit.editLocalProjectView(
            project,
            builder -> {
              if (addDirectory) {
                addDirectory(builder, parentPath);
              }
              addTargets(builder, targets);
              return true;
            });
    if (edit == null) {
      Messages.showErrorDialog(
          "Could not modify project view. Check for errors in your project view and try again",
          "Error");
      return;
    }
    edit.apply();
    // TODO(brendandouglas): support partially syncing a directory with the same query-based
    // filtering
    List<? extends TargetExpression> targetsToSync = targets;
    if (autoDeriveTargets(project)) {
      targetsToSync = ImmutableList.of(TargetExpression.allFromPackageRecursive(parentPath));
    }
    BlazeSyncManager.getInstance(project)
        .partialSync(targetsToSync, /* reason= */ "AddSourceToProjectHelper");
    notifySuccess(project, addDirectory ? parentPath : null, targets);
  }

  private static void addDirectory(ProjectView.Builder builder, WorkspacePath dir) {
    ListSection<DirectoryEntry> section = builder.getLast(DirectorySection.KEY);
    builder.replace(
        section,
        ListSection.update(DirectorySection.KEY, section).add(DirectoryEntry.include(dir)));
  }

  private static void addTargets(
      ProjectView.Builder builder, List<? extends TargetExpression> targets) {
    if (targets.isEmpty()) {
      return;
    }
    ListSection<TargetExpression> section = builder.getLast(TargetSection.KEY);
    builder.replace(section, ListSection.update(TargetSection.KEY, section).addAll(targets));
  }

  private static void notifyFailed(Project project, String message) {
    Notifications.Bus.notify(
        new Notification(
            "AddToProject",
            "Failed to add source file to project",
            message,
            NotificationType.WARNING),
        project);
  }

  private static void notifySuccess(
      Project project,
      @Nullable WorkspacePath directory,
      List<? extends TargetExpression> targets) {
    if (directory == null && targets.isEmpty()) {
      return;
    }
    StringBuilder builder = new StringBuilder();
    if (directory != null) {
      builder.append(String.format("Added directory '%s' to project view\n", directory));
    }
    if (!targets.isEmpty()) {
      builder.append("Added targets to project view:\n");
      targets.forEach(t -> builder.append("  ").append(t).append("\n"));
    }
    builder.append("<a href='open'>Open project view file</a>");

    Notifications.Bus.notify(
        new Notification(
                "AddToProject",
                "Updated project view file",
                builder.toString(),
                NotificationType.INFORMATION)
            .setListener(
                new NotificationListener.Adapter() {
                  @Override
                  protected void hyperlinkActivated(Notification notification, HyperlinkEvent e) {
                    notification.expire();
                    OpenProjectViewAction.openLocalProjectViewFile(project);
                  }
                }),
        project);
  }

  /**
   * Returns the list of targets building the given source file, which aren't already in the
   * project. Returns null if this can't be calculated.
   */
  @Nullable
  static ListenableFuture<List<TargetInfo>> getTargetsBuildingSource(LocationContext context) {
    if (!SourceToTargetProvider.hasProvider()) {
      return null;
    }
    // early-out if source is trivially covered by project targets (e.g. because there's a wildcard
    // target pattern for the parent package)
    if (context.getImportRoots().packageInProjectTargets(context.blazePackage)) {
      return null;
    }
    // Finally, query the exact targets building this source file.
    // This is required to handle project targets which failed to build
    return Futures.transform(
        SourceToTargetHelper.findTargetsBuildingSourceFile(
            context.project, context.workspacePath.relativePath()),
        (Function<List<TargetInfo>, List<TargetInfo>>)
            (List<TargetInfo> result) ->
                result == null || sourceInProjectTargets(context, fromTargetInfo(result))
                    ? ImmutableList.of()
                    : result,
        MoreExecutors.directExecutor());
  }

  /**
   * Returns true if the project view targets (both included and excluded targets) trivially contain
   * one of the targets building the source file.
   */
  static boolean sourceCoveredByProjectViewTargets(LocationContext context) {
    Collection<TargetKey> targetsBuildingSource =
        SourceToTargetMap.getInstance(context.project)
            .getRulesForSourceFile(new File(context.file.getPath()));
    return !targetsBuildingSource.isEmpty()
        && sourceInProjectTargets(context, targetsBuildingSource);
  }

  private static Collection<TargetKey> fromTargetInfo(Collection<TargetInfo> targetInfos) {
    return targetInfos.stream()
        .map(t -> TargetKey.forPlainTarget(t.label))
        .collect(toImmutableList());
  }

  /**
   * Returns true if the project view, or targets indexed during the previous sync, contains one of
   * the targets building the source file.
   */
  private static boolean sourceInProjectTargets(
      LocationContext context, Collection<TargetKey> targetsBuildingSource) {
    if (targetsBuildingSource.stream().anyMatch(context.syncData.getTargetMap()::contains)) {
      return true;
    }
    ImportRoots roots = context.getImportRoots();
    return targetsBuildingSource.stream().anyMatch(t -> roots.targetInProject(t.getLabel()));
  }

  static boolean packageCoveredByProjectTargets(LocationContext context) {
    return context.getImportRoots().packageInProjectTargets(context.blazePackage);
  }

  /** Returns true if the source is already covered by the current .blazeproject directories. */
  static boolean sourceInProjectDirectories(LocationContext context) {
    return context.getImportRoots().containsWorkspacePath(context.workspacePath);
  }

  @Nullable
  private static WorkspacePath getWorkspacePath(Project project, File file) {
    WorkspacePathResolver pathResolver =
        WorkspacePathResolverProvider.getInstance(project).getPathResolver();
    if (pathResolver == null) {
      return null;
    }
    return pathResolver.getWorkspacePath(file);
  }

  private static boolean isBuildSystemOutputArtifact(Project project, WorkspacePath path) {
    return Blaze.getBuildSystemProvider(project)
        .buildArtifactDirectories(WorkspaceRoot.fromProject(project))
        .stream()
        .anyMatch(outDir -> FileUtil.isAncestor(outDir, path.relativePath(), false));
  }

  @Nullable
  private static WorkspacePath findBlazePackagePath(Project project, WorkspacePath source) {
    WorkspacePathResolver pathResolver =
        WorkspacePathResolverProvider.getInstance(project).getPathResolver();
    if (pathResolver == null) {
      return null;
    }
    BuildSystemProvider provider = Blaze.getBuildSystemProvider(project);
    while (source != null) {
      ProgressManager.checkCanceled();
      if (provider.findBuildFileInDirectory(pathResolver.resolveToFile(source)) != null) {
        return source;
      }
      source = source.getParent();
    }
    return null;
  }

  /** Returns the location context related to a source file to be added to the project. */
  @Nullable
  static LocationContext getContext(Project project, VirtualFile file) {
    if (Blaze.getProjectType(project).equals(ProjectType.QUERY_SYNC)) {
      throw new UnsupportedOperationException("AddSourceToProjectHelper#getContext");
    }
    BlazeProjectData syncData = BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (syncData == null) {
      return null;
    }
    WorkspacePath workspacePath = getWorkspacePath(project, new File(file.getPath()));
    if (workspacePath == null || isBuildSystemOutputArtifact(project, workspacePath)) {
      return null;
    }
    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    if (projectViewSet == null) {
      return null;
    }
    WorkspacePath parent = workspacePath.getParent();
    if (parent == null) {
      return null;
    }
    WorkspacePath blazePackage = findBlazePackagePath(project, parent);
    return blazePackage != null
        ? new LocationContext(project, syncData, projectViewSet, file, workspacePath, blazePackage)
        : null;
  }

  /** Location context related to a source file to be added to the project. */
  static class LocationContext {
    final Project project;
    final BlazeProjectData syncData;
    final ProjectViewSet projectViewSet;
    final VirtualFile file;
    final WorkspacePath workspacePath;
    final WorkspacePath blazePackage;

    private LocationContext(
        Project project,
        BlazeProjectData syncData,
        ProjectViewSet projectViewSet,
        VirtualFile file,
        WorkspacePath workspacePath,
        WorkspacePath blazePackage) {
      this.project = project;
      this.syncData = syncData;
      this.projectViewSet = projectViewSet;
      this.file = file;
      this.workspacePath = workspacePath;
      this.blazePackage = blazePackage;
    }

    ImportRoots getImportRoots() {
      return ImportRoots.builder(
              WorkspaceRoot.fromProject(project), Blaze.getBuildSystemName(project))
          .add(projectViewSet)
          .build();
    }
  }
}
