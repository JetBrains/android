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

import com.android.SdkConstants;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.util.GradleProjects;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.templates.RepositoryUrlManager;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.BaseAnalysisActionDialog;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.inferNullity.InferNullityAnnotationsAction;
import com.intellij.codeInspection.inferNullity.NullityInferrer;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.usages.*;
import com.intellij.util.Processor;
import com.intellij.util.SequentialModalProgressTask;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

import static com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.COMPILE;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
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
    if (!GradleProjects.isBuildWithGradle(project)) {
      super.analyze(project, scope);
      return;
    }
    int[] fileCount = new int[]{0};
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    UsageInfo[] usageInfos = findUsages(project, scope, fileCount[0]);
    if (usageInfos == null) return;

    Map<Module, PsiFile> modules = findModulesFromUsage(usageInfos);

    if (!checkModules(project, scope, modules)) {
      return;
    }

    if (usageInfos.length < 5) {
      ApplicationManager.getApplication().invokeLater(applyRunnable(project, () -> usageInfos));
    }
    else {
      showUsageView(project, usageInfos, scope, this);
    }
  }

  private static Map<Module, PsiFile> findModulesFromUsage(UsageInfo[] infos) {
    // We need 1 file from each module that requires changes (the file may be overwritten below):
    Map<Module, PsiFile> modules = new HashMap<>();

    for (UsageInfo info : infos) {
      PsiElement element = info.getElement();
      assert element != null;
      Module module = ModuleUtilCore.findModuleForPsiElement(element);
      PsiFile file = element.getContainingFile();
      modules.put(module, file);
    }
    return modules;
  }

  // For Android we need to check SDK version and possibly update the gradle project file
  protected boolean checkModules(@NotNull Project project,
                                 @NotNull AnalysisScope scope,
                                 @NotNull Map<Module, PsiFile> modules) {
    Set<Module> modulesWithoutAnnotations = new HashSet<>();
    Set<Module> modulesWithLowVersion = new HashSet<>();
    for (Module module : modules.keySet()) {
      AndroidModuleInfo info = AndroidModuleInfo.getInstance(module);
      if (info != null && info.getBuildSdkVersion() != null && info.getBuildSdkVersion().getFeatureLevel() < MIN_SDK_WITH_NULLABLE) {
        modulesWithLowVersion.add(module);
      }
      GradleBuildModel buildModel = GradleBuildModel.get(module);
      if (buildModel == null) {
        LOG.warn("Unable to find Gradle build model for module " + module.getModuleFilePath());
        continue;
      }
      boolean dependencyFound = false;
      DependenciesModel dependenciesModel = buildModel.dependencies();
      if (dependenciesModel != null) {
        String configurationName =
          GradleUtil.mapConfigurationName(COMPILE, GradleUtil.getAndroidGradleModelVersionInUse(module), false);
        for (ArtifactDependencyModel dependency : dependenciesModel.artifacts(configurationName)) {
          String notation = dependency.compactNotation().value();
          if (notation.startsWith(SdkConstants.APPCOMPAT_LIB_ARTIFACT) ||
              notation.startsWith(SdkConstants.SUPPORT_LIB_ARTIFACT) ||
              notation.startsWith(SdkConstants.ANNOTATIONS_LIB_ARTIFACT)) {
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
    String moduleNames = StringUtil.join(modulesWithoutAnnotations, Module::getName, ", ");
    int count = modulesWithoutAnnotations.size();
    String message = String.format("The %1$s %2$s %3$sn't refer to the existing '%4$s' library with Android nullity annotations. \n\n" +
                                   "Would you like to add the %5$s now?",
                                   pluralize("module", count),
                                   moduleNames,
                                   count > 1 ? "do" : "does",
                                   GoogleMavenArtifactId.SUPPORT_ANNOTATIONS.getMavenArtifactId(),
                                   pluralize("dependency", count));
    if (Messages.showOkCancelDialog(project, message, "Infer Nullity Annotations", Messages.getErrorIcon()) == Messages.OK) {
      LocalHistoryAction action = LocalHistory.getInstance().startAction(ADD_DEPENDENCY);
      try {
        new WriteCommandAction(project, ADD_DEPENDENCY) {
          @Override
          protected void run(@NotNull Result result) throws Throwable {
            RepositoryUrlManager manager = RepositoryUrlManager.get();
            String annotationsLibraryCoordinate = manager.getArtifactStringCoordinate(GoogleMavenArtifactId.SUPPORT_ANNOTATIONS, true);
            for (Module module : modulesWithoutAnnotations) {
              addDependency(module, annotationsLibraryCoordinate);
            }

            syncAndRestartAnalysis(project, scope);
          }
        }.execute();
      }
      finally {
        action.finish();
      }
    }
    return false;
  }

  private void syncAndRestartAnalysis(@NotNull Project project, @NotNull AnalysisScope scope) {
    assert ApplicationManager.getApplication().isDispatchThread();

    ListenableFuture<ProjectSystemSyncManager.SyncResult> syncResult = ProjectSystemUtil.getProjectSystem(project)
      .getSyncManager().syncProject(ProjectSystemSyncManager.SyncReason.PROJECT_MODIFIED, false);

    Futures.addCallback(syncResult, new FutureCallback<ProjectSystemSyncManager.SyncResult>() {
      @Override
      public void onSuccess(@Nullable ProjectSystemSyncManager.SyncResult syncResult) {
        if (syncResult != null && syncResult.isSuccessful()) {
          restartAnalysis(project, scope);
        }
      }

      @Override
      public void onFailure(@Nullable Throwable t) {
        throw new RuntimeException(t);
      }
    });
  }

  // Intellij code from InferNullityAnnotationsAction.
  private static Runnable applyRunnable(Project project, Computable<UsageInfo[]> computable) {
    return () -> {
      LocalHistoryAction action = LocalHistory.getInstance().startAction(INFER_NULLITY_ANNOTATIONS);
      try {
        new WriteCommandAction(project, INFER_NULLITY_ANNOTATIONS) {
          @Override
          protected void run(@NotNull Result result) throws Throwable {
            UsageInfo[] infos = computable.compute();
            if (infos.length > 0) {

              Set<PsiElement> elements = new LinkedHashSet<>();
              for (UsageInfo info : infos) {
                PsiElement element = info.getElement();
                if (element != null) {
                  ContainerUtil.addIfNotNull(elements, element.getContainingFile());
                }
              }
              if (!FileModificationService.getInstance().preparePsiElementsForWrite(elements)) return;

              SequentialModalProgressTask progressTask = new SequentialModalProgressTask(project, INFER_NULLITY_ANNOTATIONS, false);
              progressTask.setMinIterationTime(200);
              progressTask.setTask(new AnnotateTask(project, progressTask, infos));
              ProgressManager.getInstance().run(progressTask);
            }
            else {
              NullityInferrer.nothingFoundMessage(project);
            }
          }
        }.execute();
      }
      finally {
        action.finish();
      }
    };
  }

  // Intellij code from InferNullityAnnotationsAction.
  @Override
  protected void restartAnalysis(Project project, AnalysisScope scope) {
    ApplicationManager.getApplication().invokeLater(() -> analyze(project, scope));
  }

  // Intellij code from InferNullityAnnotationsAction.
  private static void showUsageView(@NotNull Project project,
                                    UsageInfo[] usageInfos,
                                    @NotNull AnalysisScope scope,
                                    AndroidInferNullityAnnotationAction action) {
    UsageTarget[] targets = UsageTarget.EMPTY_ARRAY;
    Ref<Usage[]> convertUsagesRef = new Ref<>();
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> ApplicationManager.getApplication().runReadAction(
      () -> convertUsagesRef.set(UsageInfo2UsageAdapter.convert(usageInfos))), "Preprocess Usages", true, project)) {
      return;
    }

    if (convertUsagesRef.isNull()) return;
    Usage[] usages = convertUsagesRef.get();

    UsageViewPresentation presentation = new UsageViewPresentation();
    presentation.setTabText("Infer Nullity Preview");
    presentation.setShowReadOnlyStatusAsRed(true);
    presentation.setShowCancelButton(true);
    presentation.setUsagesString(RefactoringBundle.message("usageView.usagesText"));

    UsageView usageView = UsageViewManager.getInstance(project).showUsages(targets, usages, presentation, rerunFactory(project, scope, action));

    Runnable refactoringRunnable = applyRunnable(project, () -> {
      Set<UsageInfo> infos = UsageViewUtil.getNotExcludedUsageInfos(usageView);
      return infos.toArray(new UsageInfo[infos.size()]);
    });

    String canNotMakeString =
      "Cannot perform operation.\nThere were changes in code after usages have been found.\nPlease perform operation search again.";

    usageView.addPerformOperationAction(refactoringRunnable, INFER_NULLITY_ANNOTATIONS, canNotMakeString, INFER_NULLITY_ANNOTATIONS, false);
  }

  // Intellij code from InferNullityAnnotationsAction.
  @NotNull
  private static Factory<UsageSearcher> rerunFactory(@NotNull Project project,
                                                     @NotNull AnalysisScope scope,
                                                     AndroidInferNullityAnnotationAction action) {
    return () -> new UsageInfoSearcherAdapter() {
      @Override
      protected UsageInfo[] findUsages() {
        return action.findUsages(project, scope, scope.getFileCount());
      }

      @Override
      public void generate(@NotNull Processor<Usage> processor) {
        processUsages(processor, project);
      }
    };
  }

  private static void addDependency(@NotNull Module module, @Nullable String libraryCoordinate) {
    if (isNotEmpty(libraryCoordinate)) {
      ModuleRootModificationUtil.updateModel(module, model -> {
        GradleBuildModel buildModel = GradleBuildModel.get(module);
        if (buildModel != null) {
          String name = GradleUtil.mapConfigurationName(COMPILE, GradleUtil.getAndroidGradleModelVersionInUse(module), false);
          buildModel.dependencies().addArtifact(name, libraryCoordinate);
          buildModel.applyChanges();
        }
      });
    }
  }

  /* Android nullable annotations do not support annotations on local variables. */
  @Override
  protected JComponent getAdditionalActionSettings(Project project, BaseAnalysisActionDialog dialog) {
    JComponent panel = super.getAdditionalActionSettings(project, dialog);
    if (panel != null && GradleProjectInfo.getInstance(project).isBuildWithGradle()) {
      panel.setVisible(false);
    }
    return panel;
  }
}
