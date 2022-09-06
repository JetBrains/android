/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.refactoring;

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.android.tools.idea.projectsystem.gradle.GradleProjectPathKt.getGradleProjectPath;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_REFACTOR_MODULE_RENAMED;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.isExternalSystemAwareModule;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;

import com.android.annotations.concurrency.UiThread;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.GradleSettingsModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ModuleDependencyModel;
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.projectsystem.gradle.GradleProjectPath;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.TitledHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleGrouper;
import com.intellij.openapi.module.ModuleGrouperKt;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleWithNameAlreadyExists;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.RenameHandler;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

/**
 * Replaces {@link com.intellij.ide.projectView.impl.RenameModuleHandler}. When renaming the module, the class will:
 * <ol>
 * <li>change the reference in the root Gradle settings file</li>
 * <li>change the references in all dependencies in Gradle build files</li>
 * <li>change the directory name of the module</li>
 * </ol>
 */
public class GradleRenameModuleHandler implements RenameHandler, TitledHandler {
  @Override
  public boolean isAvailableOnDataContext(@NotNull DataContext dataContext) {
    Module module = getGradleModule(dataContext);
    return module != null && getModuleRootDir(module) != null;
  }

  @Nullable
  private static VirtualFile getModuleRootDir(@NotNull Module module) {
    File moduleRootDir = AndroidRootUtil.findModuleRootFolderPath(module);
    if (moduleRootDir == null) return null;
    return findFileByIoFile(moduleRootDir, true);
  }

  @Override
  @UiThread
  public void invoke(@NotNull Project project, @Nullable Editor editor, @Nullable PsiFile file, @NotNull DataContext dataContext) {
  }

  @Override
  @UiThread
  public void invoke(@NotNull final Project project, @NotNull PsiElement[] elements, @NotNull DataContext dataContext) {
    Module module = getGradleModule(dataContext);
    assert module != null;
    String currentName = ModuleGrouper.instanceFor(project).getShortenedName(module);
    Messages.showInputDialog(project, IdeBundle.message("prompt.enter.new.module.name"), IdeBundle.message("title.rename.module"),
                             Messages.getQuestionIcon(), currentName, new MyInputValidator(module));
  }

  @Nullable
  private static Module getGradleModule(@NotNull DataContext dataContext) {
    Module module = LangDataKeys.MODULE_CONTEXT.getData(dataContext);
    if (isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)) {
      return module;
    }
    return null;
  }

  @Override
  @NotNull
  public String getActionTitle() {
    return RefactoringBundle.message("rename.module.title");
  }

  private static class MyInputValidator implements InputValidator {
    @NotNull private final Module myModule;

    private MyInputValidator(@NotNull Module module) {
      myModule = module;
    }

    @Override
    public boolean checkInput(@Nullable String inputString) {
      String currentName = ModuleGrouper.instanceFor(myModule.getProject()).getShortenedName(myModule);
      return inputString != null && !inputString.isEmpty() && !inputString.equals(currentName) && !inputString.contains(":")
        && !inputString.contains(".");
    }

    @Override
    public boolean canClose(@NotNull final String inputString) {
      final Project project = myModule.getProject();

      final ProjectBuildModel projectModel = ProjectBuildModel.get(project);
      final GradleSettingsModel settingsModel = projectModel.getProjectSettingsModel();
      if (settingsModel == null) {
        Messages.showErrorDialog(project, "Gradle settings file not found", IdeBundle.message("title.rename.module"));
        return true;
      }
      final VirtualFile moduleRoot = getModuleRootDir(myModule);
      assert moduleRoot != null;

      GradleProjectPath gradleProjectPath = getGradleProjectPath(myModule);
      if (gradleProjectPath != null && gradleProjectPath.getPath().equals(":")) {
        Messages.showErrorDialog(project, "Can't rename root module", IdeBundle.message("title.rename.module"));
        return true;
      }

      if (gradleProjectPath == null) {
        return true;
      }
      final String oldModuleGradlePath = gradleProjectPath.getPath();

      // Rename all references in Gradle build files
      final List<GradleBuildModel> modifiedBuildModels = Lists.newArrayList();
      for (Module module : ModuleManager.getInstance(project).getModules()) {
        GradleBuildModel buildModel = projectModel.getModuleBuildModel(module);
        if (buildModel != null) {
          DependenciesModel dependenciesModel = buildModel.dependencies();
          for (ModuleDependencyModel dependency : dependenciesModel.modules()) {
            // TODO consider the case that dependency.path() is not started with :
            ResolvedPropertyModel path = dependency.path();
            if (oldModuleGradlePath.equals(path.forceString())) {
              path.setValue(getNewPath(oldModuleGradlePath, inputString));
            }
          }
          List<GradlePropertyModel> dynamicFeatures = buildModel.android().dynamicFeatures().toList();
          if (dynamicFeatures != null) {
            for (GradlePropertyModel feature : dynamicFeatures) {
              if (oldModuleGradlePath.equals(feature.forceString())) {
                feature.setValue(getNewPath(oldModuleGradlePath, inputString));
              }
            }
          }
          if (buildModel.isModified()) {
            modifiedBuildModels.add(buildModel);
          }
        }
      }

      String currentName = ModuleGrouper.instanceFor(project).getShortenedName(myModule);
      String msg = IdeBundle.message("command.renaming.module", currentName);
      WriteCommandAction.Builder actionBuilder = WriteCommandAction.writeCommandAction(project).withName(msg).withGlobalUndo();
      ThrowableComputable<Boolean,Throwable> action = () -> {
        if (!settingsModel.modulePaths().contains(oldModuleGradlePath)) {
          String settingsFileName = settingsModel.getVirtualFile().getName();
          Messages.showErrorDialog(project, "Can't find module '" + currentName + "' in " + settingsFileName,
                                   IdeBundle.message("title.rename.module"));
          reset(modifiedBuildModels);
          return true;
        }

        // Rename module
        ModifiableModuleModel modifiableModel = ModuleManager.getInstance(project).getModifiableModel();
        String newName = inputString;
        // If qualified names are enabled then the user should only pick the last part of the group path. To change the actual
        // structure the Gradle build files should be changed
        if (ModuleGrouperKt.isQualifiedModuleNamesEnabled(project)) {
          List<String> groupPath = new ArrayList<>(ModuleGrouper.instanceFor(project).getGroupPath(myModule));
          groupPath.add(inputString);
          newName = StringUtil.join(groupPath, ".");
        }
        try {
          modifiableModel.renameModule(myModule, newName);
        }
        catch (ModuleWithNameAlreadyExists moduleWithNameAlreadyExists) {
          ApplicationManager.getApplication().invokeLater(
            () -> Messages.showErrorDialog(project, IdeBundle.message("error.module.already.exists", inputString),
                                           IdeBundle.message("title.rename.module")));
          reset(modifiedBuildModels);
          return false;
        }

        // Changing and applying the Gradle models MUST be done before attempting to change the module roots. If not
        // the view provider used to construct the psi tree will be marked as invalid and any attempted change will
        // cause a PsiInvalidAccessException.
        settingsModel.replaceModulePath(oldModuleGradlePath, getNewPath(oldModuleGradlePath, inputString));

        // Rename all references in Gradle build files
        for (GradleBuildModel buildModel : modifiedBuildModels) {
          buildModel.applyChanges();
        }
        settingsModel.applyChanges();

        // Rename the directory
        try {
          moduleRoot.rename(this, inputString);
        }
        catch (IOException e) {
          ApplicationManager.getApplication().invokeLater(
            () -> Messages.showErrorDialog(project, "Rename folder failed: " + e.getMessage(), IdeBundle.message("title.rename.module")));
          reset(modifiedBuildModels);
          return false;
        }

        modifiableModel.commit();

        UndoManager.getInstance(project).undoableActionPerformed(new BasicUndoableAction() {
          @Override
          public void undo() throws UnexpectedUndoException {
            requestSync(project);
          }

          @Override
          public void redo() throws UnexpectedUndoException {
            requestSync(project);
          }
        });
        return true;
      };

      try {
        if (actionBuilder.compute(action)) {
          requestSync(project);
          return true;
        }
        return false;
      }
      catch (Throwable c) {
        return false;
      }
    }
  }

  private static void requestSync(@NotNull Project project) {
    GradleSyncInvoker.getInstance().requestProjectSync(project, new GradleSyncInvoker.Request(TRIGGER_REFACTOR_MODULE_RENAMED), null);
  }

  private static String getNewPath(@NotNull String oldPath, @NotNull String newName) {
    String newPath;
    // Keep empty spaces, needed when putting the path back together
    List<String> segments = Splitter.on(GRADLE_PATH_SEPARATOR).splitToList(oldPath);
    List<String> modifiableSegments = Lists.newArrayList(segments);
    int segmentCount = modifiableSegments.size();
    if (segmentCount == 0) {
      newPath = GRADLE_PATH_SEPARATOR + newName.trim();
    }
    else {
      modifiableSegments.set(segmentCount - 1, newName);
      newPath = Joiner.on(GRADLE_PATH_SEPARATOR).join(modifiableSegments);
    }
    return newPath;
  }

  private static void reset(@NotNull List<GradleBuildModel> buildModels) {
    for (GradleBuildModel buildModel : buildModels) {
      buildModel.resetState();
    }
  }
}
