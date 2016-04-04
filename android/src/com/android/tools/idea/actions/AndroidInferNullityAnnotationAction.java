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
package com.android.tools.idea.actions;

import com.android.tools.idea.gradle.parser.BuildFileKey;
import com.android.tools.idea.gradle.parser.BuildFileStatement;
import com.android.tools.idea.gradle.parser.Dependency;
import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.templates.RepositoryUrlManager;
import com.google.common.collect.Lists;
import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.BaseAnalysisActionDialog;
import com.intellij.codeInspection.inferNullity.InferNullityAnnotationsAction;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

import static com.intellij.openapi.util.text.StringUtil.pluralize;

/**
 * AndroidInferNullityAnnotationAction gives the user the option of adding the correct
 * component library to the gradle build file.
 * This file has excerpts of Intellij code.
 */
public class AndroidInferNullityAnnotationAction extends InferNullityAnnotationsAction {
  private static final Logger LOG = Logger.getInstance(AndroidInferNullityAnnotationAction.class);
  private static final String INFER_NULLITY_ANNOTATIONS = "Infer Nullity Annotations";
  private static final String ADD_DEPENDENCY = "Add Support Dependency";
  private static final int MIN_SDK_WITH_NULLABLE = 19;

  @Override
  protected void analyze(@NotNull Project project, @NotNull AnalysisScope scope) {
    if (!Projects.isBuildWithGradle(project)) {
      super.analyze(project, scope);
      return;
    }
    int[] fileCount = new int[] {0};
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    final UsageInfo[] usageInfos = findUsages(project, scope, fileCount[0]);
    if (usageInfos == null) return;

    Map<Module, PsiFile> modules = findModulesFromUsage(usageInfos);

    if (!checkModules(project, scope, modules)) {
      return;
    }

    processUsages(project, scope, usageInfos);
  }

  private static Map<Module, PsiFile> findModulesFromUsage(UsageInfo[] infos) {
    // We need 1 file from each module that requires changes (the file may be overwritten below):
    final Map<Module, PsiFile> modules = new HashMap<Module, PsiFile>();

    for (UsageInfo info : infos) {
      final PsiElement element = info.getElement();
      Module module = ModuleUtilCore.findModuleForPsiElement(element);
      PsiFile file = element.getContainingFile();
      modules.put(module, file);
    }
    return modules;
  }

  // For Android we need to check SDK version and possibly update the gradle project file
  protected boolean checkModules(@NotNull final Project project,
                                 @NotNull final AnalysisScope scope,
                                 @NotNull Map<Module, PsiFile> modules) {
    RepositoryUrlManager manager = RepositoryUrlManager.get();
    final String annotationsLibraryCoordinate = manager.getLibraryCoordinate(RepositoryUrlManager.SUPPORT_ANNOTATIONS);
    final String appCompatLibraryCoordinate = manager.getLibraryCoordinate(RepositoryUrlManager.APP_COMPAT_ID_V7);

    final Set<Module> modulesWithoutAnnotations = new HashSet<Module>();
    final Set<Module> modulesWithLowVersion = new HashSet<Module>();
    for (Module module : modules.keySet()) {
      AndroidModuleInfo info = AndroidModuleInfo.get(module);
      if (info != null && info.getBuildSdkVersion() != null && info.getBuildSdkVersion().getFeatureLevel() <  MIN_SDK_WITH_NULLABLE) {
        modulesWithLowVersion.add(module);
      }
      GradleBuildFile gradleBuildFile = GradleBuildFile.get(module);
      if (gradleBuildFile == null) {
        LOG.warn("Unable to find Gradle build file for module " + module.getModuleFilePath());
        continue;
      }
      boolean dependencyFound = false;
      for (BuildFileStatement entry : gradleBuildFile.getDependencies()) {
        if (entry instanceof Dependency) {
          Dependency dependency = (Dependency)entry;
          if (dependency.scope == Dependency.Scope.COMPILE &&
              dependency.type == Dependency.Type.EXTERNAL &&
              (dependency.getValueAsString().equals(annotationsLibraryCoordinate) ||
               dependency.getValueAsString().equals(appCompatLibraryCoordinate))) {
            dependencyFound = true;
            break;
          }
        }
      }
      if (!dependencyFound) {
        modulesWithoutAnnotations.add(module);
      }
    }

    if (!modulesWithLowVersion.isEmpty()) {
      Messages.showErrorDialog(
        project,
        String.format("Infer Nullity Annotations requires the project sdk level be set to %1$d or greater.", MIN_SDK_WITH_NULLABLE),
        "Infer Nullity Annotations");
      return false;
    }
    if (modulesWithoutAnnotations.isEmpty()) {
      return true;
    }
    String moduleNames = StringUtil.join(modulesWithoutAnnotations, new Function<Module, String>() {
      @Override
      public String fun(Module module) {
        return module.getName();
      }
    }, ", ");
    int count = modulesWithoutAnnotations.size();
    String message = String.format("The %1$s %2$s %3$sn't refer to the existing '%4$s' library with Android nullity annotations. \n\n" +
                                   "Would you like to add the %5$s now?",
                                   pluralize("module", count),
                                   moduleNames,
                                   count > 1 ? "do" : "does",
                                   RepositoryUrlManager.SUPPORT_ANNOTATIONS,
                                   pluralize("dependency", count));
    if (Messages.showOkCancelDialog(project, message, "Infer Nullity Annotations", Messages.getErrorIcon()) == Messages.OK) {
      final LocalHistoryAction action = LocalHistory.getInstance().startAction(ADD_DEPENDENCY);
      try {
        new WriteCommandAction(project, ADD_DEPENDENCY) {
          @Override
          protected void run(@NotNull final Result result) throws Throwable {
            for (Module module : modulesWithoutAnnotations) {
              addDependency(module, annotationsLibraryCoordinate);
            }
            GradleProjectImporter.getInstance().requestProjectSync(project, false, new GradleSyncListener.Adapter() {
              @Override
              public void syncSucceeded(@NotNull Project project) {
                restartAnalysis(project, scope);
              }
            });
          }
        }.execute();
      }
      finally {
        action.finish();
      }
    }
    return false;
  }

  private static void addDependency(final Module module, final String libraryCoordinate) {
    ModuleRootModificationUtil.updateModel(module, new Consumer<ModifiableRootModel>() {
      @Override
      public void consume(ModifiableRootModel model) {
        GradleBuildFile gradleBuildFile = GradleBuildFile.get(module);
        if (gradleBuildFile != null) {
          List<BuildFileStatement> dependencies = Lists.newArrayList(gradleBuildFile.getDependencies());
          dependencies.add(new Dependency(Dependency.Scope.COMPILE, Dependency.Type.EXTERNAL, libraryCoordinate));
          gradleBuildFile.setValue(BuildFileKey.DEPENDENCIES, dependencies);
        }
      }
    });
  }

  @Override
  protected boolean isAnnotateLocalVariables() {
    return false;
  }

  /* Android nullable annotations do not support annotations on local variables. */
  @Override
  protected JComponent getAdditionalActionSettings(Project project, BaseAnalysisActionDialog dialog) {
    if (!Projects.isBuildWithGradle(project)) {
      return super.getAdditionalActionSettings(project, dialog);
    }
    return null;
  }
}
