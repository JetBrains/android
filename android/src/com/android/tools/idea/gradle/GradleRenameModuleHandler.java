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
package com.android.tools.idea.gradle;

import com.android.tools.idea.gradle.dsl.parser.DependenciesElement;
import com.android.tools.idea.gradle.dsl.parser.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.parser.ProjectDependencyElement;
import com.android.tools.idea.gradle.parser.GradleSettingsFile;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.RenameHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import java.io.File;
import java.io.IOException;

import static com.android.tools.idea.gradle.parser.GradleSettingsFile.getModuleGradlePath;

/**
 * Replaces {@link com.intellij.ide.projectView.impl.RenameModuleHandler}. When renaming the module, the class will:
 * <ol>
 *  <li>change the reference in the root settings.gradle file</li>
 *  <li>change the references in all build.gradle's dependencies</li>
 *  <li>change the directory name of the module</li>
 * </ol>
 */
public class GradleRenameModuleHandler implements RenameHandler, TitledHandler {
  @Override
  public boolean isAvailableOnDataContext(@NotNull DataContext dataContext) {
    Module module = LangDataKeys.MODULE_CONTEXT.getData(dataContext);
    return module != null &&
           LocalFileSystem.getInstance().findFileByIoFile((new File(module.getModuleFilePath())).getParentFile()) !=
           null;
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
    Module module = LangDataKeys.MODULE_CONTEXT.getData(dataContext);
    assert module != null;
    Messages.showInputDialog(project, IdeBundle.message("prompt.enter.new.module.name"), IdeBundle.message("title.rename.module"),
                             Messages.getQuestionIcon(), module.getName(), new MyInputValidator(project, module));
  }

  @Override
  @NotNull
  public String getActionTitle() {
    return RefactoringBundle.message("rename.module.title");
  }

  private static class MyInputValidator implements InputValidator {
    private final Project myProject;
    private final Module myModule;

    public MyInputValidator(@NotNull Project project, @NotNull Module module) {
      myProject = project;
      myModule = module;
    }

    @Override
    public boolean checkInput(@Nullable String inputString) {
      return inputString != null && inputString.length() > 0 && !inputString.equals(myModule.getName()) && !inputString.contains(":");
    }

    @Override
    public boolean canClose(@NotNull final String inputString) {
      final GradleSettingsFile settingsFile = GradleSettingsFile.get(myProject);
      if (settingsFile == null) {
        Messages.showErrorDialog(myProject, "settings.gradle file not found", IdeBundle.message("title.rename.module"));
        return true;
      }
      final VirtualFile moduleRoot =
        LocalFileSystem.getInstance().findFileByIoFile((new File(myModule.getModuleFilePath())).getParentFile());
      assert moduleRoot != null;

      if (myModule.getProject().getBaseDir().equals(moduleRoot)) {
        Messages.showErrorDialog(myProject, "Can't rename root module", IdeBundle.message("title.rename.module"));
        return true;
      }

      WriteCommandAction<Boolean> action =
        new WriteCommandAction<Boolean>(myProject, IdeBundle.message("command.renaming.module", myModule.getName()),
                                        settingsFile.getPsiFile()) {
          @Override
          protected void run(@NotNull Result<Boolean> result) throws Throwable {
            result.setResult(true);

            String oldModuleGradlePath = getModuleGradlePath(myModule);
            if (oldModuleGradlePath == null) {
              return;
            }

            GrLiteral moduleReference = settingsFile.findModuleReference(myModule);
            if (moduleReference == null) {
              Messages.showErrorDialog(myProject, "Can't find module '" + myModule.getName() + "' in settings.gradle",
                                       IdeBundle.message("title.rename.module"));
              return;
            }

            // Rename the directory
            try {
              moduleRoot.rename(this, inputString);
            }
            catch (IOException e) {
              Messages.showErrorDialog(myProject, "Rename folder failed: " + e.getMessage(), IdeBundle.message("title.rename.module"));
              result.setResult(false);
              return;
            }

            // Rename the reference in settings.gradle
            moduleReference.updateText(moduleReference.getText().replace(myModule.getName(), inputString));

            // Rename all references in build.gradle
            for (Module module : ModuleManager.getInstance(myProject).getModules()) {
              GradleBuildModel buildModel = GradleBuildModel.get(module);
              if (buildModel != null) {
                for (DependenciesElement elements : buildModel.getDependenciesBlocks()) {
                  for (ProjectDependencyElement element : elements.getProjectDependencies()) {
                    if (oldModuleGradlePath.equals(element.getPath())) {
                      element.setName(inputString);
                    }
                  }
                }
              }
            }

            UndoManager.getInstance(myProject).undoableActionPerformed(new BasicUndoableAction() {
              @Override
              public void undo() throws UnexpectedUndoException {
                GradleProjectImporter.getInstance().requestProjectSync(myProject, null);
              }

              @Override
              public void redo() throws UnexpectedUndoException {
                GradleProjectImporter.getInstance().requestProjectSync(myProject, null);
              }
            });
            result.setResult(true);
          }
        };

      if (action.execute().getResultObject()) {
        GradleProjectImporter.getInstance().requestProjectSync(myProject, null);
        return true;
      }
      return false;
    }
  }

}
