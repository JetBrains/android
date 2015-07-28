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
package com.android.tools.idea.quickfix;

import com.android.tools.idea.gradle.parser.Dependency;
import com.google.common.base.Function;
import com.google.common.collect.Sets;
import com.intellij.application.options.ModuleListCellRenderer;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.android.tools.idea.gradle.util.GradleUtil.getGradlePath;
import static com.intellij.compiler.ModuleCompilerUtil.addingDependencyFormsCircularity;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;

/**
 * Quickfix to add dependency to another module in gradle.build file and sync the project.
 * Duplicated from {@link com.intellij.codeInsight.daemon.impl.quickfix.AddModuleDependencyFix} except the
 * {@link AddGradleProjectDependencyFix#addDependencyOnModule} method
 */
public class AddGradleProjectDependencyFix extends GradleDependencyFix {
  private final Set<Module> myModules = Sets.newHashSet();
  private final Module myCurrentModule;
  private final VirtualFile myClassVFile;
  private final PsiClass[] myClasses;
  private final PsiReference myReference;

  public AddGradleProjectDependencyFix(@NotNull Module currentModule,
                                       @NotNull VirtualFile classVFile,
                                       @NotNull PsiClass[] classes,
                                       @NotNull PsiReference reference) {
    PsiElement psiElement = reference.getElement();
    Project project = psiElement.getProject();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();

    for (PsiClass aClass : classes) {
      if (!facade.getResolveHelper().isAccessible(aClass, psiElement, aClass)) {
        continue;
      }
      PsiFile psiFile = aClass.getContainingFile();
      if (psiFile == null) {
        continue;
      }
      VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile == null) {
        continue;
      }
      final Module classModule = fileIndex.getModuleForFile(virtualFile);
      if (classModule != null && classModule != currentModule && !ModuleRootManager.getInstance(currentModule).isDependsOn(classModule)) {
        myModules.add(classModule);
      }
    }
    myCurrentModule = currentModule;
    myClassVFile = classVFile;
    myClasses = classes;
    myReference = reference;
  }

  @NotNull
  @Override
  public String getText() {
    if (myModules.size() == 1) {
      Module module = getFirstItem(myModules);
      assert module != null;
      return QuickFixBundle.message("orderEntry.fix.add.dependency.on.module", module.getName());
    }
    else {
      return "Add dependency on module...";
    }
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("orderEntry.fix.family.add.module.dependency");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, @Nullable Editor editor, @Nullable PsiFile file) {
    for (Module module : myModules) {
      if (module.isDisposed()) return false;
    }
    return !project.isDisposed() && !myModules.isEmpty() && !myCurrentModule.isDisposed();
  }


  @Override
  public void invoke(@NotNull final Project project, @Nullable final Editor editor, @Nullable PsiFile file) {
    if (editor != null) {
      if (!CodeInsightUtilBase.prepareEditorForWrite(editor)) return;
    }

    if (myModules.size() == 1) {
      Module module = getFirstItem(myModules);
      assert module != null;
      addDependencyOnModule(project, editor, module);
    }
    else {
      final JBList list = new JBList(myModules);
      list.setCellRenderer(new ModuleListCellRenderer());
      JBPopup popup = JBPopupFactory.getInstance().createListPopupBuilder(list)
        .setTitle("Choose Module to Add Dependency on")
        .setMovable(false)
        .setResizable(false)
        .setRequestFocus(true)
        .setItemChoosenCallback(new Runnable() {
          @Override
          public void run() {
            final Object value = list.getSelectedValue();
            if (value instanceof Module) {
              addDependencyOnModule(project, editor, (Module)value);
            }
          }
        }).createPopup();
      if (editor != null) {
        popup.showInBestPositionFor(editor);
      } else {
        popup.showCenteredInCurrentWindow(project);
      }
    }
  }

  private void addDependencyOnModule(@NotNull final Project project, @Nullable final Editor editor, @NotNull final Module module) {
    Runnable doit = new Runnable() {
      @Override
      public void run() {
        final boolean test = ModuleRootManager.getInstance(myCurrentModule).getFileIndex().isInTestSourceContent(myClassVFile);

        final Application application = ApplicationManager.getApplication();
        application.invokeAndWait(new Runnable() {
          @Override
          public void run() {
            application.runWriteAction(new Runnable() {
              @Override
              public void run() {
                addDependency(myCurrentModule, module, test);
                gradleSyncAndImportClass(module, editor, myReference, new Function<Void, List<PsiClass>>() {
                  @Override
                  public List<PsiClass> apply(@Nullable Void input) {
                    final List<PsiClass> targetClasses = new ArrayList<PsiClass>();
                    for (PsiClass psiClass : myClasses) {
                      if (ModuleUtilCore.findModuleForPsiElement(psiClass) == module) {
                        targetClasses.add(psiClass);
                      }
                    }
                    return targetClasses;
                  }
                });
              }
            });
          }
        }, application.getDefaultModalityState());
      }
    };

    Pair<Module, Module> circularModules = addingDependencyFormsCircularity(myCurrentModule, module);
    if (circularModules == null) {
      doit.run();
    }
    else {
      showCircularWarningAndContinue(project, circularModules, module, doit);
    }
  }

  private static void showCircularWarningAndContinue(@NotNull final Project project, @NotNull Pair<Module, Module> circularModules,
                                                     @NotNull Module classModule, @NotNull final Runnable doit) {
    final String message = QuickFixBundle.message("orderEntry.fix.circular.dependency.warning", classModule.getName(),
                                                  circularModules.getFirst().getName(), circularModules.getSecond().getName());
    if (ApplicationManager.getApplication().isUnitTestMode()) throw new RuntimeException(message);
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (!project.isOpen()) return;
        int ret = Messages.showOkCancelDialog(project, message, QuickFixBundle.message("orderEntry.fix.title.circular.dependency.warning"),
                                              Messages.getWarningIcon());
        if (ret == Messages.OK) {
          ApplicationManager.getApplication().runWriteAction(doit);
        }
      }
    });
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  // TODO use new gradle build file API to add dependencies.
  private static void addDependency(@NotNull Module from, @NotNull Module to, boolean test) {
    String gradlePath = getGradlePath(to);
    if (gradlePath != null) {
      Dependency dependency = new Dependency(getDependencyScope(from, test), Dependency.Type.MODULE, gradlePath);
      addDependency(from, dependency);
    }
  }
}
