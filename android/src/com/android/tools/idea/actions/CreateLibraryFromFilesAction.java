/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.tools.idea.gradle.parser.BuildFileKey;
import com.android.tools.idea.gradle.parser.Dependency;
import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.android.tools.idea.gradle.parser.GradleSettingsFile;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.intellij.application.options.ModulesComboBox;
import com.intellij.ide.projectView.actions.MarkLibraryRootAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.roots.libraries.ui.impl.RootDetectionUtil;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.DefaultLibraryRootsComponentDescriptor;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryNameAndLevelPanel;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SortedListModel;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_PROJECT_MODIFIED;

/**
 * Replaces the {@linkplain MarkLibraryRootAction} for Android-Gradle projects. This action, given an input of a number of
 * {@linkplain VirtualFile} instances, examines them to see if they are jarfiles and could be added to a library as a dependency.
 * If so, the action displays a dialog allowing the user to choose a module to add the jars to, and updates the module's build.gradle.
 * <p>
 * For non-Android-Gradle projects, this action delegates to MarkLibraryRootAction.
 */
public class CreateLibraryFromFilesAction extends AnAction {
  AnAction myDelegate = new MarkLibraryRootAction();

  public CreateLibraryFromFilesAction() {
    super("Add As Library...");
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = getEventProject(e);
    if (project == null) return;

    if (!AndroidProjectInfo.getInstance(project).requiresAndroidModel()) {
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
    presentation.setVisible(visible);
    presentation.setEnabled(visible);
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
    private GradleSettingsFile mySettingsFile;

    public CreateGradleLibraryFromFilesDialog(@NotNull Project project, @NotNull List<OrderRoot> roots) {
      super(project, true);
      setTitle(COMMAND_TITLE);
      myProject = project;
      myRoots = roots;
      mySettingsFile = GradleSettingsFile.get(myProject);

      final FormBuilder builder = LibraryNameAndLevelPanel.createFormBuilder();
      myModulesComboBox = new ModulesComboBox();
      myModulesComboBox.fillModules(myProject);
      myModulesComboBox.setSelectedModule(findModule(roots));
      for (Iterator iter = ((SortedListModel)myModulesComboBox.getModel()).iterator(); iter.hasNext(); ) {
        Module module = (Module)iter.next();
        String path = GradleSettingsFile.getModuleGradlePath(module);
        if (path == null || !mySettingsFile.hasBuildFile(path)) {
          iter.remove();
        }
      }
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
      WriteAction.run(() -> {
        final Module module = myModulesComboBox.getSelectedModule();
        if (module == null) { return; }
        String moduleGradlePath = GradleSettingsFile.getModuleGradlePath(module);
        if (moduleGradlePath == null) { return; }
        final GradleBuildFile buildFile = mySettingsFile.getModuleBuildFile(moduleGradlePath);
        List<Dependency> value = (List<Dependency>)buildFile.getValue(BuildFileKey.DEPENDENCIES);
        final List<Dependency> dependencies = value != null ? value : new ArrayList<Dependency>();
        boolean added = false;
        for (OrderRoot root : myRoots) {
          VirtualFile parent = buildFile.getFile().getParent();
          final VirtualFile local = JarFileSystem.getInstance().getVirtualFileForJar(root.getFile());
          if (local != null) {
            String path = VfsUtilCore.getRelativePath(local, parent, '/');
            if (path == null) {
              path = local.getPath();
            }
            Dependency.Scope scope = Dependency.Scope.getDefaultScope(myProject);
            Dependency newDependency = new Dependency(scope, Dependency.Type.FILES, path);
            if (!dependencies.contains(newDependency)) {
              dependencies.add((newDependency));
              added = true;
            }
          }
          if (added) {
            new WriteCommandAction<Void>(myProject, COMMAND_TITLE, buildFile.getPsiFile()) {
              @Override
              protected void run(@NotNull Result<Void> result) throws Throwable {
                buildFile.setValue(BuildFileKey.DEPENDENCIES, dependencies);
              }
            }.execute();
          }
        }
      });
      GradleSyncInvoker.getInstance().requestProjectSyncAndSourceGeneration(myProject, TRIGGER_PROJECT_MODIFIED, null);

      super.doOKAction();
    }

    @Override
    protected JComponent createCenterPanel() {
      return myPanel;
    }
  }
}