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
package com.android.tools.idea.gradle.quickfix;

import com.android.builder.model.AndroidProject;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.parser.BuildFileKey;
import com.android.tools.idea.gradle.parser.BuildFileStatement;
import com.android.tools.idea.gradle.parser.Dependency;
import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.application.options.ModuleListCellRenderer;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.ui.components.JBList;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

import static com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST;
import static com.android.tools.idea.gradle.util.GradleUtil.getAndroidProject;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradlePath;
import static com.intellij.codeInsight.CodeInsightUtilBase.prepareEditorForWrite;
import static com.intellij.compiler.ModuleCompilerUtil.addingDependencyFormsCircularity;
import static com.intellij.openapi.module.ModuleUtilCore.findModuleForPsiElement;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static com.intellij.util.ui.UIUtil.invokeLaterIfNeeded;

/**
 * Quickfix to add dependency to another module in gradle.build file and sync the project.
 * Duplicated from {@link com.intellij.codeInsight.daemon.impl.quickfix.AddModuleDependencyFix} except the
 * {@link AddGradleProjectDependencyFix#addDependencyOnModule} method
 */
public class AddGradleProjectDependencyFix extends AbstractGradleDependencyFix {
  @NotNull private final Set<Module> myModules = Sets.newHashSet();
  @NotNull private final VirtualFile myClassVFile;
  @NotNull private final PsiClass[] myClasses;

  public AddGradleProjectDependencyFix(@NotNull Module module,
                                       @NotNull VirtualFile classVFile,
                                       @NotNull PsiClass[] classes,
                                       @NotNull PsiReference reference) {
    super(module, reference);

    Project project = module.getProject();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    ModuleType currentModuleType = getModuleType(module);

    PsiElement psiElement = reference.getElement();
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
      Module classModule = fileIndex.getModuleForFile(virtualFile);
      if (classModule != null && classModule != module && !ModuleRootManager.getInstance(module).isDependsOn(classModule)) {
        ModuleType classModuleType = getModuleType(classModule);
        boolean legalDependency = false;
        switch (currentModuleType) {
          case JAVA:
            legalDependency = classModuleType == ModuleType.JAVA;
            break;
          case ANDROID_LIBRARY:
            legalDependency = classModuleType == ModuleType.JAVA || classModuleType == ModuleType.ANDROID_LIBRARY;
            break;
          case ANDROID_APPLICATION:
            legalDependency = classModuleType != ModuleType.ANDROID_APPLICATION;
            break;
        }

        if (legalDependency) {
          myModules.add(classModule);
        }
      }
    }
    myClassVFile = classVFile;
    myClasses = classes;
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
      if (module.isDisposed()) {
        return false;
      }
    }
    return !project.isDisposed() && !myModules.isEmpty() && !myModule.isDisposed();
  }

  @Override
  public void invoke(@NotNull final Project project, @Nullable final Editor editor, @Nullable PsiFile file) {
    if (editor != null && !prepareEditorForWrite(editor)) {
      return;
    }

    if (myModules.size() == 1) {
      Module module = getFirstItem(myModules);
      assert module != null;
      addDependencyOnModule(project, editor, module);
    }
    else {
      final JBList list = new JBList(myModules);
      list.setCellRenderer(new ModuleListCellRenderer());
      Runnable callback = new Runnable() {
        @Override
        public void run() {
          Object value = list.getSelectedValue();
          if (value instanceof Module) {
            addDependencyOnModule(project, editor, (Module)value);
          }
        }
      };
      JBPopup popup =
        JBPopupFactory.getInstance().createListPopupBuilder(list).setTitle("Choose Module to Add Dependency on").setMovable(false)
          .setResizable(false).setRequestFocus(true).setItemChoosenCallback(callback).createPopup();
      if (editor != null) {
        popup.showInBestPositionFor(editor);
      }
      else {
        popup.showCenteredInCurrentWindow(project);
      }
    }
  }

  private void addDependencyOnModule(@NotNull final Project project, @Nullable final Editor editor, @NotNull final Module module) {
    Runnable doit = new Runnable() {
      @Override
      public void run() {
        final boolean testScope = ModuleRootManager.getInstance(myModule).getFileIndex().isInTestSourceContent(myClassVFile);

        runWriteCommandActionAndSync(project, new Runnable() {
          @Override
          public void run() {
            addDependencyUndoable(myModule, module, testScope);
          }
        }, new Computable<PsiClass[]>() {
          @Override
          public PsiClass[] compute() {
            List<PsiClass> targetClasses = Lists.newArrayList();
            for (PsiClass psiClass : myClasses) {
              if (findModuleForPsiElement(psiClass) == module) {
                targetClasses.add(psiClass);
              }
            }
            return targetClasses.toArray(new PsiClass[targetClasses.size()]);
          }
        }, editor);
      }
    };

    Pair<Module, Module> circularModules = addingDependencyFormsCircularity(myModule, module);
    if (circularModules == null) {
      doit.run();
    }
    else {
      showCircularWarningAndContinue(project, circularModules, module, doit);
    }
  }

  private static void showCircularWarningAndContinue(@NotNull final Project project,
                                                     @NotNull Pair<Module, Module> circularModules,
                                                     @NotNull Module classModule,
                                                     @NotNull final Runnable doit) {
    final String message = QuickFixBundle
      .message("orderEntry.fix.circular.dependency.warning", classModule.getName(), circularModules.getFirst().getName(),
               circularModules.getSecond().getName());
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      throw new RuntimeException(message);
    }
    invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        if (!project.isOpen()) {
          return;
        }
        String title = QuickFixBundle.message("orderEntry.fix.title.circular.dependency.warning");
        int answer = Messages.showOkCancelDialog(project, message, title, Messages.getWarningIcon());
        if (answer == Messages.OK) {
          ApplicationManager.getApplication().runWriteAction(doit);
        }
      }
    });
  }

  // TODO use new gradle build file API to add dependencies.
  private void addDependencyUndoable(@NotNull Module from, @NotNull Module to, boolean test) {
    String gradlePath = getGradlePath(to);
    if (gradlePath != null) {
      Dependency dependency = new Dependency(getDependencyScope(from, test), Dependency.Type.MODULE, gradlePath);
      addDependencyUndoable(from, dependency);
    }
  }

  private void addDependencyUndoable(@NotNull Module module, @NotNull Dependency dependency) {
    GradleBuildFile gradleBuildFile = GradleBuildFile.get(module);
    if (gradleBuildFile == null) {
      return;
    }
    List<BuildFileStatement> dependencies = Lists.newArrayList(gradleBuildFile.getDependencies());
    dependencies.add(dependency);
    gradleBuildFile.setValue(BuildFileKey.DEPENDENCIES, dependencies);
    registerUndoAction(module.getProject());
    myAddedDependency = dependency.getValueAsString();
    myAddedDependency = dependency.scope.getGroovyMethodCall();
  }

  @NotNull
  private static Dependency.Scope getDependencyScope(@NotNull Module module, boolean test) {
    Dependency.Scope testScope = Dependency.Scope.TEST_COMPILE;
    if (test) {
      AndroidFacet androidFacet = AndroidFacet.getInstance(module);
      if (androidFacet != null) {
        AndroidGradleModel androidModel = AndroidGradleModel.get(androidFacet);
        if (androidModel != null && ARTIFACT_ANDROID_TEST.equals(androidModel.getSelectedTestArtifactName())) {
          testScope = Dependency.Scope.ANDROID_TEST_COMPILE;
        }
      }
    }
    return test ? testScope : Dependency.Scope.COMPILE;
  }

  private enum ModuleType {
    JAVA,
    ANDROID_LIBRARY,
    ANDROID_APPLICATION
  }

  @NotNull
  private static ModuleType getModuleType(@NotNull Module module) {
    AndroidProject androidProject = getAndroidProject(module);
    if (androidProject == null) {
      return ModuleType.JAVA;
    }
    else {
      return androidProject.isLibrary() ? ModuleType.ANDROID_LIBRARY : ModuleType.ANDROID_APPLICATION;
    }
  }
}
