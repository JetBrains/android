/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.actions;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.intellij.application.options.ModulesComboBox;
import com.intellij.ide.projectView.actions.MarkLibraryRootAction;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.roots.libraries.ui.impl.RootDetectionUtil;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.DefaultLibraryRootsComponentDescriptor;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryNameAndLevelPanel;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.FormBuilder;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Replaces the {@linkplain MarkLibraryRootAction} for Android-Gradle projects. This action, given an input of a number of
 * {@linkplain VirtualFile} instances, examines them to see if they are jarfiles and could be added to a library as a dependency.
 * If so, the action displays a dialog allowing the user to choose a module to add the jars to, and updates the module's build.gradle.
 * <p>
 * For non-Android-Gradle projects, this action delegates to MarkLibraryRootAction.
 */
public class CreateLibraryFromFilesAction extends AnAction {
  private static final Logger LOG = Logger.getInstance(CreateLibraryFromFilesAction.class);
  AnAction myDelegate = new MarkLibraryRootAction();

  public CreateLibraryFromFilesAction() {
    super("Add As Library...");
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = getEventProject(e);
    if (project == null) return;

    if (!ProjectSystemUtil.requiresAndroidModel(project)) {
      myDelegate.actionPerformed(e);
      return;
    }

    final List<VirtualFile> jars = getRoots(e);
    if (jars.isEmpty()) {
      return;
    }

    final List<OrderRoot> roots = RootDetectionUtil.detectRoots(jars, null, project, new DefaultLibraryRootsComponentDescriptor());
    new CreateGradleLibraryFromFilesDialog(project, roots).show();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final Project project = getEventProject(e);
    boolean visible = false;
    if (project != null && ModuleManager.getInstance(project).getModules().length > 0) {
      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
      for (VirtualFile root : getRoots(e)) {
        if (!root.isInLocalFileSystem() && FileUtilRt.extensionEquals(root.getName(), "jar") && !fileIndex.isInLibraryClasses(root)) {
          visible = true;
          break;
        }
      }
    }

    Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(visible);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @NotNull
  private static List<VirtualFile> getRoots(@NotNull AnActionEvent e) {
    final Project project = getEventProject(e);
    final VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (project == null || files == null || files.length == 0) {
      return Collections.emptyList();
    }

    List<VirtualFile> roots = new ArrayList<>();
    for (VirtualFile file : files) {
      VirtualFile root = JarFileSystem.getInstance().getJarRootForLocalFile(file);
      if (root != null) {
        roots.add(root);
      }
    }
    return roots;
  }

  private static class CreateGradleLibraryFromFilesDialog extends DialogWrapper {
    public static final String COMMAND_TITLE = "Create Library";
    private final ModulesComboBox myModulesComboBox;
    private final Project myProject;
    private final JPanel myPanel;
    private final List<OrderRoot> myRoots;

    private CreateGradleLibraryFromFilesDialog(@NotNull Project project, @NotNull List<OrderRoot> roots) {
      super(project, true);
      setTitle(COMMAND_TITLE);
      myProject = project;
      myRoots = roots;

      final FormBuilder builder = LibraryNameAndLevelPanel.createFormBuilder();

      myModulesComboBox = new ModulesComboBox();
      List<Module> androidModules = getAndroidModules(project);

      Module initialSelection = findModule(roots);
      myModulesComboBox.setModules(androidModules);
      if (!androidModules.contains(initialSelection) && !androidModules.isEmpty()) {
        initialSelection = androidModules.get(0);
      }
      myModulesComboBox.setSelectedModule(initialSelection);

      builder.addLabeledComponent("&Add to module:", myModulesComboBox);
      myPanel = builder.getPanel();
      init();
    }

    @Nullable
    private Module findModule(List<OrderRoot> roots) {
      for (OrderRoot root : roots) {
        Module module = null;
        final VirtualFile local = JarFileSystem.getInstance().getVirtualFileForJar(root.getFile());
        if (local != null) {
          module = ModuleUtilCore.findModuleForFile(local, myProject);
        }
        if (module == null) {
          module = ModuleUtilCore.findModuleForFile(root.getFile(), myProject);
        }
        if (module != null) {
          return module;
        }
      }
      return null;
    }

    @Override
    protected void doOKAction() {
      Module module = myModulesComboBox.getSelectedModule();
      if (module == null) {
        showErrorDialog("Could not find module, please add the dependency manually");
        return;
      }

      ProgressWindow progress = new ProgressWindow(false, myProject);
      Runnable process = () -> {
        ProjectBuildModel projectBuildModel = ProjectBuildModel.get(myProject);
        GradleBuildModel gradleBuildModel = projectBuildModel.getModuleBuildModel(module);

        if (gradleBuildModel == null) {
          showErrorDialog("Could not understand build file for module '" + module.getName() + "' please add the dependency manually.");
          return;
        }

        Set<String> addedRoots = new HashSet<>();
        String scope = GradleProjectSystemUtil.useCompatibilityConfigurationNames(myProject) ? "compile" : "implementation";
        for (OrderRoot root : myRoots) {
          VirtualFile local = JarFileSystem.getInstance().getVirtualFileForJar(root.getFile());
          if (local == null) {
            LOG.warn("Couldn't find JAR for " + root.getFile().getPath());
            continue;
          }

          // We can get multiple OrderRoots referring to the same JAR for different OrderRootTypes, we need to make sure
          // that we don't add these to the users build files multiple times.
          if (addedRoots.contains(local.getPath())) {
            continue;
          }

          VirtualFile parent = gradleBuildModel.getVirtualFile().getParent();
          String path = VfsUtilCore.findRelativePath(parent, local, File.separatorChar);
          if (path == null) {
            path = local.getPath();
          }

          addedRoots.add(local.getPath());
          gradleBuildModel.dependencies().addFile(scope, path);
        }

        if (!gradleBuildModel.isModified()) {
          showErrorDialog("Failed to find any files to add, please add the dependency manually");
          return;
        }

        ApplicationManager.getApplication().invokeAndWait(() -> WriteCommandAction
          .runWriteCommandAction(myProject, "Add Library from Files", null, () -> projectBuildModel.applyChanges()));

        // Request a sync
        ApplicationManager.getApplication().invokeLater(() -> ProjectSystemUtil.getProjectSystem(myProject)
          .getSyncManager().syncProject(ProjectSystemSyncManager.SyncReason.PROJECT_MODIFIED));
      };

      progress.setTitle("Adding Dependencies to Gradle build file");
      ApplicationManager.getApplication().executeOnPooledThread(() -> ProgressManager.getInstance().runProcess(process, progress));

      super.doOKAction();
    }

    private void showErrorDialog(@NotNull String error) {
      ApplicationManager.getApplication().invokeLater(() -> Messages.showErrorDialog(myProject, error, "Create Library Action"));
    }

    @Override
    protected JComponent createCenterPanel() {
      return myPanel;
    }
  }


  @NotNull
  private static List<Module> getAndroidModules(@NotNull Project project) {
    return Arrays.stream(ModuleManager.getInstance(project).getModules()).filter((module) -> {
      GradleFacet gradleFacet = GradleFacet.getInstance(module);
      if (gradleFacet != null) {
        GradleModuleModel gradleModuleModel = gradleFacet.getGradleModuleModel();
        return gradleModuleModel != null && gradleModuleModel.getBuildFile() != null;
      }
      return false;
    }).collect(Collectors.toList());
  }
}