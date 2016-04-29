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

import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.templates.RepositoryUrlManager;
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
import com.intellij.openapi.roots.ModifiableRootModel;
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
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.SequentialModalProgressTask;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

import static com.android.tools.idea.gradle.dsl.model.dependencies.CommonConfigurationNames.COMPILE;
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

    if (usageInfos.length < 5) {
      SwingUtilities.invokeLater(applyRunnable(project, new Computable<UsageInfo[]>() {
        @Override
        public UsageInfo[] compute() {
          return usageInfos;
        }
      }));
    }
    else {
      showUsageView(project, usageInfos, scope, this);
    }
  }

  private static Map<Module, PsiFile> findModulesFromUsage(UsageInfo[] infos) {
    // We need 1 file from each module that requires changes (the file may be overwritten below):
    final Map<Module, PsiFile> modules = new HashMap<Module, PsiFile>();

    for (UsageInfo info : infos) {
      final PsiElement element = info.getElement();
      assert element != null;
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
      GradleBuildModel buildModel = GradleBuildModel.get(module);
      if (buildModel == null) {
        LOG.warn("Unable to find Gradle build model for module " + module.getModuleFilePath());
        continue;
      }
      boolean dependencyFound = false;
      DependenciesModel dependenciesModel = buildModel.dependencies();
      if (dependenciesModel != null) {
        for (ArtifactDependencyModel dependency : dependenciesModel.artifacts(COMPILE)) {
          String notation = dependency.getSpec().compactNotation();
          if (notation.equals(annotationsLibraryCoordinate) || notation.equals(appCompatLibraryCoordinate)) {
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

  // Intellij code from InferNullityAnnotationsAction.
  private static Runnable applyRunnable(final Project project, final Computable<UsageInfo[]> computable) {
    return new Runnable() {
      @Override
      public void run() {
        final LocalHistoryAction action = LocalHistory.getInstance().startAction(INFER_NULLITY_ANNOTATIONS);
        try {
          new WriteCommandAction(project, INFER_NULLITY_ANNOTATIONS) {
            @Override
            protected void run(@NotNull Result result) throws Throwable {
              final UsageInfo[] infos = computable.compute();
              if (infos.length > 0) {

                final Set<PsiElement> elements = new LinkedHashSet<PsiElement>();
                for (UsageInfo info : infos) {
                  final PsiElement element = info.getElement();
                  if (element != null) {
                    ContainerUtil.addIfNotNull(elements, element.getContainingFile());
                  }
                }
                if (!FileModificationService.getInstance().preparePsiElementsForWrite(elements)) return;

                final SequentialModalProgressTask progressTask = new SequentialModalProgressTask(project, INFER_NULLITY_ANNOTATIONS, false);
                progressTask.setMinIterationTime(200);
                progressTask.setTask(new AnnotateTask(project, progressTask, infos));
                ProgressManager.getInstance().run(progressTask);
              } else {
                NullityInferrer.nothingFoundMessage(project);
              }
            }
          }.execute();
        }
        finally {
          action.finish();
        }
      }
    };
  }

  // Intellij code from InferNullityAnnotationsAction.
  protected void restartAnalysis(final Project project, final AnalysisScope scope) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        analyze(project, scope);
      }
    });
  }

  // Intellij code from InferNullityAnnotationsAction.
  private static void showUsageView(@NotNull Project project,
                                    final UsageInfo[] usageInfos,
                                    @NotNull AnalysisScope scope,
                                    AndroidInferNullityAnnotationAction action) {
    final UsageTarget[] targets = UsageTarget.EMPTY_ARRAY;
    final Ref<Usage[]> convertUsagesRef = new Ref<Usage[]>();
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          @Override
          public void run() {
            convertUsagesRef.set(UsageInfo2UsageAdapter.convert(usageInfos));
          }
        });
      }
    }, "Preprocess Usages", true, project)) return;

    if (convertUsagesRef.isNull()) return;
    final Usage[] usages = convertUsagesRef.get();

    final UsageViewPresentation presentation = new UsageViewPresentation();
    presentation.setTabText("Infer Nullity Preview");
    presentation.setShowReadOnlyStatusAsRed(true);
    presentation.setShowCancelButton(true);
    presentation.setUsagesString(RefactoringBundle.message("usageView.usagesText"));

    final UsageView usageView = UsageViewManager.getInstance(project).showUsages(targets, usages, presentation, rerunFactory(project, scope, action));

    final Runnable refactoringRunnable = applyRunnable(project, new Computable<UsageInfo[]>() {
      @Override
      public UsageInfo[] compute() {
        final Set<UsageInfo> infos = UsageViewUtil.getNotExcludedUsageInfos(usageView);
        return infos.toArray(new UsageInfo[infos.size()]);
      }
    });

    String canNotMakeString = "Cannot perform operation.\nThere were changes in code after usages have been found.\nPlease perform operation search again.";

    usageView.addPerformOperationAction(refactoringRunnable, INFER_NULLITY_ANNOTATIONS, canNotMakeString, INFER_NULLITY_ANNOTATIONS, false);
  }

  // Intellij code from InferNullityAnnotationsAction.
  @NotNull
  private static Factory<UsageSearcher> rerunFactory(@NotNull final Project project,
                                                     @NotNull final AnalysisScope scope,
                                                     AndroidInferNullityAnnotationAction action) {
    return new Factory<UsageSearcher>() {
      @Override
      public UsageSearcher create() {
        return new UsageInfoSearcherAdapter() {
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
    };
  }

  private static void addDependency(@NotNull final Module module, @Nullable final String libraryCoordinate) {
    if (isNotEmpty(libraryCoordinate)) {
      ModuleRootModificationUtil.updateModel(module, new Consumer<ModifiableRootModel>() {
        @Override
        public void consume(ModifiableRootModel model) {
          GradleBuildModel buildModel = GradleBuildModel.get(module);
          if (buildModel != null) {
            buildModel.dependencies().addArtifact(COMPILE, libraryCoordinate);
          }
        }
      });
    }
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
