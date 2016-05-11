/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.actions.annotations;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.templates.RepositoryUrlManager;
import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.BaseAnalysisAction;
import com.intellij.analysis.BaseAnalysisActionDialog;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.ide.scratch.ScratchRootType;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.usages.*;
import com.intellij.util.Processor;
import com.intellij.util.SequentialModalProgressTask;
import com.intellij.util.SequentialTask;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

import static com.android.tools.idea.gradle.dsl.model.dependencies.CommonConfigurationNames.COMPILE;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.openapi.util.text.StringUtil.pluralize;

/**
 * Analyze support annotations
 */
public class InferSupportAnnotationsAction extends BaseAnalysisAction {
  /** Whether this feature is enabled or not during development */
  static final boolean ENABLED = Boolean.valueOf(System.getProperty("studio.infer.annotations"));

  /** Number of times we pass through the project files */
  static final int MAX_PASSES = 3;

  @NonNls private static final String INFER_SUPPORT_ANNOTATIONS = "Infer Support Annotations";
  private static final int MAX_ANNOTATIONS_WITHOUT_PREVIEW = 5;

  public InferSupportAnnotationsAction() {
    super("Infer Support Annotations", INFER_SUPPORT_ANNOTATIONS);
    if (!ENABLED) {
      getTemplatePresentation().setVisible(false);
    }
  }

  private static final String ADD_DEPENDENCY = "Add Support Dependency";
  private static final int MIN_SDK_WITH_NULLABLE = 19;

  @Override
  public void update(AnActionEvent event) {
    if (!ENABLED) {
      return;
    }
    super.update(event);
    Project project = event.getProject();
    if (project == null || !Projects.isBuildWithGradle(project)) {
      Presentation presentation = event.getPresentation();
      presentation.setEnabled(false);
    }
  }

  @Override
  protected void analyze(@NotNull Project project, @NotNull AnalysisScope scope) {
    if (!Projects.isBuildWithGradle(project)) {
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

    if (usageInfos.length < MAX_ANNOTATIONS_WITHOUT_PREVIEW) {
      ApplicationManager.getApplication().invokeLater(applyRunnable(project, () -> usageInfos));
    }
    else {
      showUsageView(project, usageInfos, scope);
    }
  }

  private static Map<Module, PsiFile> findModulesFromUsage(UsageInfo[] infos) {
    // We need 1 file from each module that requires changes (the file may be overwritten below):
    final Map<Module, PsiFile> modules = new HashMap<>();

    for (UsageInfo info : infos) {
      final PsiElement element = info.getElement();
      assert element != null;
      Module module = ModuleUtilCore.findModuleForPsiElement(element);
      if (module == null) {
        continue;
      }
      PsiFile file = element.getContainingFile();
      modules.put(module, file);
    }
    return modules;
  }

  private static UsageInfo[] findUsages(@NotNull final Project project,
                                        @NotNull final AnalysisScope scope,
                                        final int fileCount) {
    final InferSupportAnnotations inferrer = new InferSupportAnnotations(false, project);
    final PsiManager psiManager = PsiManager.getInstance(project);
    final Runnable searchForUsages = () -> scope.accept(new PsiElementVisitor() {
      int myFileCount = 0;

      @Override
      public void visitFile(final PsiFile file) {
        myFileCount++;
        final VirtualFile virtualFile = file.getVirtualFile();
        final FileViewProvider viewProvider = psiManager.findViewProvider(virtualFile);
        final Document document = viewProvider == null ? null : viewProvider.getDocument();
        if (document == null || virtualFile.getFileType().isBinary()) return; //do not inspect binary files
        final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
        if (progressIndicator != null) {
          progressIndicator.setText2(ProjectUtil.calcRelativeToProjectPath(virtualFile, project));
          progressIndicator.setFraction(((double)myFileCount) / (MAX_PASSES * fileCount));
        }
        if (file instanceof PsiJavaFile) {
          inferrer.collect(file);
        }
      }
    });

    /*
      Collect these files and visit repeatedly. Consider this
      scenario, where I visit files A, B, C in alphabetical order.
      Let's say a method in A unconditionally calls a method in B
      calls a method in C. In file C I discover that the method
      requires permission P. At this point it's too late for me to
      therefore conclude that the method in B also requires it. If I
      make a whole separate pass again, I could now add that
      constraint. But only after that second pass can I infer that
      the method in A also requires it. In short, I need to keep
      passing through all files until I make no more progress. It
      would be much more efficient to handle this with a global call
      graph such that as soon as I make an inference I can flow it
      backwards.
     */
    Runnable multipass = () -> {
      for (int i = 0; i < MAX_PASSES; i++) {
        searchForUsages.run();
      }
    };

    if (ApplicationManager.getApplication().isDispatchThread()) {
      if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(multipass, INFER_SUPPORT_ANNOTATIONS, true, project)) {
        return null;
      }
    } else {
      multipass.run();
    }

    final List<UsageInfo> usages = new ArrayList<>();
    inferrer.collect(usages);
    return usages.toArray(new UsageInfo[usages.size()]);
  }

  // For Android we need to check SDK version and possibly update the gradle project file
  protected boolean checkModules(@NotNull final Project project,
                                 @NotNull final AnalysisScope scope,
                                 @NotNull Map<Module, PsiFile> modules) {
    final Set<Module> modulesWithoutAnnotations = new HashSet<>();
    final Set<Module> modulesWithLowVersion = new HashSet<>();
    for (Module module : modules.keySet()) {
      AndroidModuleInfo info = AndroidModuleInfo.get(module);
      if (info != null && info.getBuildSdkVersion() != null && info.getBuildSdkVersion().getFeatureLevel() <  MIN_SDK_WITH_NULLABLE) {
        modulesWithLowVersion.add(module);
      }
      GradleBuildModel buildModel = GradleBuildModel.get(module);
      if (buildModel == null) {
        Logger.getInstance(InferSupportAnnotationsAction.class).warn("Unable to find Gradle build model for module " + module.getModuleFilePath());
        continue;
      }
      boolean dependencyFound = false;
      DependenciesModel dependenciesModel = buildModel.dependencies();
      if (dependenciesModel != null) {
        for (ArtifactDependencyModel dependency : dependenciesModel.artifacts(COMPILE)) {
          String notation = dependency.compactNotation().value();
          if (notation.startsWith(SdkConstants.APPCOMPAT_LIB_ARTIFACT) ||
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
        String.format("Infer Support Annotations requires the project sdk level be set to %1$d or greater.", MIN_SDK_WITH_NULLABLE),
        "Infer Support Annotations");
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
                                   RepositoryUrlManager.SUPPORT_ANNOTATIONS,
                                   pluralize("dependency", count));
    if (Messages.showOkCancelDialog(project, message, "Infer Nullity Annotations", Messages.getErrorIcon()) == Messages.OK) {
      final LocalHistoryAction action = LocalHistory.getInstance().startAction(ADD_DEPENDENCY);
      try {
        new WriteCommandAction(project, ADD_DEPENDENCY) {
          @Override
          protected void run(@NotNull final Result result) throws Throwable {
            RepositoryUrlManager manager = RepositoryUrlManager.get();
            String annotationsLibraryCoordinate = manager.getLibraryCoordinate(RepositoryUrlManager.SUPPORT_ANNOTATIONS);
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

  private static Runnable applyRunnable(final Project project, final Computable<UsageInfo[]> computable) {
    return () -> {
      final LocalHistoryAction action = LocalHistory.getInstance().startAction(INFER_SUPPORT_ANNOTATIONS);
      try {
        new WriteCommandAction(project, INFER_SUPPORT_ANNOTATIONS) {
          @Override
          protected void run(@NotNull Result result) throws Throwable {
            final UsageInfo[] infos = computable.compute();
            if (infos.length > 0) {

              final Set<PsiElement> elements = new LinkedHashSet<>();
              for (UsageInfo info : infos) {
                final PsiElement element = info.getElement();
                if (element != null) {
                  PsiFile containingFile = element.getContainingFile();
                  // Skip results in .class files; these are typically from extracted AAR files
                  VirtualFile virtualFile = containingFile.getVirtualFile();
                  if (virtualFile.getFileType().isBinary()) {
                    continue;
                  }

                  ContainerUtil.addIfNotNull(elements, containingFile);
                }
              }
              if (!FileModificationService.getInstance().preparePsiElementsForWrite(elements)) return;

              final SequentialModalProgressTask progressTask = new SequentialModalProgressTask(project, INFER_SUPPORT_ANNOTATIONS, false);
              progressTask.setMinIterationTime(200);
              progressTask.setTask(new AnnotateTask(project, progressTask, infos));
              ProgressManager.getInstance().run(progressTask);
            } else {
              InferSupportAnnotations.nothingFoundMessage(project);
            }
          }
        }.execute();
      }
      finally {
        action.finish();
      }
    };
  }

  private void restartAnalysis(final Project project, final AnalysisScope scope) {
    ApplicationManager.getApplication().invokeLater(() -> analyze(project, scope));
  }

  private static void showUsageView(@NotNull Project project, final UsageInfo[] usageInfos, @NotNull AnalysisScope scope) {
    final UsageTarget[] targets = UsageTarget.EMPTY_ARRAY;
    final Ref<Usage[]> convertUsagesRef = new Ref<>();
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> ApplicationManager.getApplication().runReadAction(() -> {
      convertUsagesRef.set(UsageInfo2UsageAdapter.convert(usageInfos));
    }), "Preprocess Usages", true, project)) return;

    if (convertUsagesRef.isNull()) return;
    final Usage[] usages = convertUsagesRef.get();

    final UsageViewPresentation presentation = new UsageViewPresentation();
    presentation.setTabText("Infer Nullity Preview");
    presentation.setShowReadOnlyStatusAsRed(true);
    presentation.setShowCancelButton(true);
    presentation.setUsagesString(RefactoringBundle.message("usageView.usagesText"));

    final UsageView usageView = UsageViewManager.getInstance(project).showUsages(targets, usages, presentation, rerunFactory(project, scope));

    final Runnable refactoringRunnable = applyRunnable(project, () -> {
      final Set<UsageInfo> infos = UsageViewUtil.getNotExcludedUsageInfos(usageView);
      return infos.toArray(new UsageInfo[infos.size()]);
    });

    String canNotMakeString = "Cannot perform operation.\nThere were changes in code after usages have been found.\nPlease perform operation search again.";

    usageView.addPerformOperationAction(refactoringRunnable, INFER_SUPPORT_ANNOTATIONS, canNotMakeString, INFER_SUPPORT_ANNOTATIONS, false);
  }

  @NotNull
  private static Factory<UsageSearcher> rerunFactory(@NotNull final Project project, @NotNull final AnalysisScope scope) {
    return () -> new UsageInfoSearcherAdapter() {
      @Override
      protected UsageInfo[] findUsages() {
        return InferSupportAnnotationsAction.findUsages(project, scope, scope.getFileCount());
      }

      @Override
      public void generate(@NotNull Processor<Usage> processor) {
        processUsages(processor, project);
      }
    };
  }

  private static void addDependency(@NotNull final Module module, @Nullable final String libraryCoordinate) {
    if (isNotEmpty(libraryCoordinate)) {
      ModuleRootModificationUtil.updateModel(module, model -> {
        GradleBuildModel buildModel = GradleBuildModel.get(module);
        if (buildModel != null) {
          buildModel.dependencies().addArtifact(COMPILE, libraryCoordinate);
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

  private static class AnnotateTask implements SequentialTask {
    private final Project myProject;
    private UsageInfo[] myInfos;
    private final SequentialModalProgressTask myTask;
    private int myCount = 0;
    private final int myTotal;

    public AnnotateTask(Project project, SequentialModalProgressTask progressTask, UsageInfo[] infos) {
      myProject = project;
      myInfos = infos;
      myTask = progressTask;
      myTotal = infos.length;
    }

    @Override
    public void prepare() {
    }

    @Override
    public boolean isDone() {
      return myCount > myTotal - 1;
    }

    @Override
    public boolean iteration() {
      final ProgressIndicator indicator = myTask.getIndicator();
      if (indicator != null) {
        indicator.setFraction(((double)myCount) / myTotal);
      }

      InferSupportAnnotations.apply(myProject, myInfos[myCount++]);

      boolean done = isDone();

      if (isDone()) {
        try {
          showReport();
        } catch (Throwable ignore) {
        }
      }
      return done;
    }

    @Override
    public void stop() {
    }

    public void showReport() {
      if (InferSupportAnnotations.CREATE_INFERENCE_REPORT) {
        String report = InferSupportAnnotations.generateReport(myInfos);
        String fileName = "Annotation Inference Report";
        ScratchFileService.Option option = ScratchFileService.Option.create_new_always;
        VirtualFile f = ScratchRootType.getInstance().createScratchFile(myProject, fileName, StdLanguages.TEXT, report, option);
        if (f != null) {
          FileEditorManager.getInstance(myProject).openFile(f, true);
        }
      }
    }
  }
}
