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

import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.GradleSettingsModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ModuleDependencyModel;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.TitledHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleWithNameAlreadyExists;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.RenameHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.android.tools.idea.gradle.parser.GradleSettingsFile.getModuleGradlePath;
import static com.android.tools.idea.gradle.util.Projects.isGradleProjectModule;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;

/**
 * Replaces {@link com.intellij.ide.projectView.impl.RenameModuleHandler}. When renaming the module, the class will:
 * <ol>
 *  <li>change the reference in the root settings.gradle file</li>
 *  <li>change the references in all dependencies in build.gradle files</li>
 *  <li>change the directory name of the module</li>
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
    File moduleFilePath = new File(module.getModuleFilePath());
    return findFileByIoFile(moduleFilePath.getParentFile(), true);
  }

  @Override
  public boolean isRenaming(@NotNull DataContext dataContext) {
    return isAvailableOnDataContext(dataContext);
  }

  @Override
  public void invoke(@NotNull Project project, @Nullable Editor editor, @Nullable PsiFile file, @NotNull DataContext dataContext) {
  }

  @Override
  public void invoke(@NotNull final Project project, @NotNull PsiElement[] elements, @NotNull DataContext dataContext) {
    Module module = getGradleModule(dataContext);
    assert module != null;
    Messages.showInputDialog(project, IdeBundle.message("prompt.enter.new.module.name"), IdeBundle.message("title.rename.module"),
                             Messages.getQuestionIcon(), module.getName(), new MyInputValidator(module));
  }

  @Nullable
  private static Module getGradleModule(@NotNull DataContext dataContext) {
    Module module = LangDataKeys.MODULE_CONTEXT.getData(dataContext);
    if (module != null && (AndroidGradleFacet.getInstance(module) != null || isGradleProjectModule(module))) {
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

    public MyInputValidator(@NotNull Module module) {
      myModule = module;
    }

    @Override
    public boolean checkInput(@Nullable String inputString) {
      return inputString != null && inputString.length() > 0 && !inputString.equals(myModule.getName()) && !inputString.contains(":");
    }

    @Override
    public boolean canClose(@NotNull final String inputString) {
      final Project project = myModule.getProject();

      final GradleSettingsModel settingsModel = GradleSettingsModel.get(project);
      if (settingsModel == null) {
        Messages.showErrorDialog(project, "settings.gradle file not found", IdeBundle.message("title.rename.module"));
        return true;
      }
      final VirtualFile moduleRoot = getModuleRootDir(myModule);
      assert moduleRoot != null;

      if (isGradleProjectModule(myModule)) {
        Messages.showErrorDialog(project, "Can't rename root module", IdeBundle.message("title.rename.module"));
        return true;
      }

      final String oldModuleGradlePath = getModuleGradlePath(myModule);
      if (oldModuleGradlePath == null) {
        return true;
      }

      // Rename all references in build.gradle
      final List<GradleBuildModel> modifiedBuildModels = Lists.newArrayList();
      for (Module module : ModuleManager.getInstance(project).getModules()) {
        GradleBuildModel buildModel = GradleBuildModel.get(module);
        if (buildModel != null) {
          DependenciesModel dependenciesModel =  buildModel.dependencies();
          if (dependenciesModel != null) {
            for (ModuleDependencyModel dependency : dependenciesModel.modules()) {
              // TODO consider the case that dependency.path() is not started with :
              if (oldModuleGradlePath.equals(dependency.path())) {
                dependency.setPath(getNewPath(dependency.path(), inputString));
              }
            }
          }
          if (buildModel.isModified()) {
            modifiedBuildModels.add(buildModel);
          }
        }
      }

      String msg = IdeBundle.message("command.renaming.module", myModule.getName());
      WriteCommandAction<Boolean> action = new WriteCommandAction<Boolean>(project, msg) {
        @Override
          protected void run(@NotNull Result<Boolean> result) throws Throwable {
            result.setResult(true);

            if (!settingsModel.modulePaths().contains(oldModuleGradlePath)) {
              Messages.showErrorDialog(project, "Can't find module '" + myModule.getName() + "' in settings.gradle",
                                       IdeBundle.message("title.rename.module"));
              reset(modifiedBuildModels);
              return;
            }

            // Rename module
            ModifiableModuleModel modifiableModel = ModuleManager.getInstance(project).getModifiableModel();
            try {
              modifiableModel.renameModule(myModule, inputString);
            }
            catch (ModuleWithNameAlreadyExists moduleWithNameAlreadyExists) {
              Messages.showErrorDialog(project, IdeBundle.message("error.module.already.exists", inputString),
                                       IdeBundle.message("title.rename.module"));
              result.setResult(false);
              reset(modifiedBuildModels);
              return;
            }

            settingsModel.replaceModulePath(oldModuleGradlePath, getNewPath(oldModuleGradlePath, inputString));
            // Rename the directory
            try {
              moduleRoot.rename(this, inputString);
            }
            catch (IOException e) {
              Messages.showErrorDialog(project, "Rename folder failed: " + e.getMessage(), IdeBundle.message("title.rename.module"));
              result.setResult(false);
              reset(modifiedBuildModels);
              return;
            }

            modifiableModel.commit();

            // Rename all references in build.gradle
            for (GradleBuildModel buildModel : modifiedBuildModels) {
              buildModel.applyChanges();
            }
            settingsModel.applyChanges();

            UndoManager.getInstance(project).undoableActionPerformed(new BasicUndoableAction() {
              @Override
              public void undo() throws UnexpectedUndoException {
                GradleProjectImporter.getInstance().requestProjectSync(project, null);
              }

              @Override
              public void redo() throws UnexpectedUndoException {
                GradleProjectImporter.getInstance().requestProjectSync(project, null);
              }
            });
            result.setResult(true);
          }
        };

      if (action.execute().getResultObject()) {
        GradleProjectImporter.getInstance().requestProjectSync(project, null);
        return true;
      }
      return false;
    }
  }

  private static String getNewPath(@NotNull String oldPath, @NotNull String newName)  {
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
