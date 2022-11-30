/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.lint.common;

import static com.android.tools.idea.lint.common.AndroidLintInspectionBase.LINT_INSPECTION_PREFIX;

import com.android.tools.lint.client.api.LintBaseline;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.client.api.LintRequest;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Lint;
import com.android.tools.lint.detector.api.Scope;
import com.google.common.collect.Sets;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.Tools;
import com.intellij.codeInspection.lang.GlobalInspectionContextExtension;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.module.impl.scopes.ModuleWithDependenciesScope;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.testFramework.LightVirtualFile;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LintGlobalInspectionContext implements GlobalInspectionContextExtension<LintGlobalInspectionContext> {
  static final Key<LintGlobalInspectionContext> ID = Key.create("LintGlobalInspectionContext");
  private Map<Issue, Map<File, List<LintProblemData>>> myResults;
  private LintBaseline myBaseline;
  private Issue myEnabledIssue;

  @NotNull
  @Override
  public Key<LintGlobalInspectionContext> getID() {
    return ID;
  }

  @Override
  public void performPreRunActivities(@NotNull List<Tools> globalTools,
                                      @NotNull List<Tools> localTools,
                                      @NotNull final GlobalInspectionContext context) {
    try {
      doAnalyze(globalTools, localTools, context);
    }
    catch (ProcessCanceledException | IndexNotReadyException e) {
      throw e;
    }
    catch (Throwable e) {
      Logger.getInstance(LintGlobalInspectionContext.class).error(e);
    }
  }

  private void doAnalyze(@NotNull List<Tools> globalTools,
                         @NotNull List<Tools> localTools,
                         @NotNull final GlobalInspectionContext context) {
    final Project project = context.getProject();
    LintIdeSupport ideSupport = LintIdeSupport.get();
    if (!ideSupport.canAnalyze(project)) {
      return;
    }

    // If none of the active inspections are Lint checks, then do not run Lint.
    if (globalTools.stream().noneMatch(it -> it.getShortName().startsWith(LINT_INSPECTION_PREFIX))) {
      return;
    }

    Set<Issue> issues = LintExternalAnnotator.Companion.getIssuesFromInspections(project, null);
    if (issues.isEmpty()) {
      return;
    }

    long startTime = System.currentTimeMillis();

    // If running a single check by name, turn it on if it's off by default.
    boolean runningSingleInspection = localTools.isEmpty() && globalTools.size() == 1;
    if (runningSingleInspection) {
      Tools tool = globalTools.get(0);
      String id = tool.getShortName().substring(LINT_INSPECTION_PREFIX.length());
      Issue issue = LintIdeIssueRegistry.get().getIssue(id);
      if (issue != null && !issue.isEnabledByDefault()) {
        issues = Collections.singleton(issue);
        issue.setEnabledByDefault(true);
        // And turn it back off again in cleanup
        myEnabledIssue = issue;
      }
    }

    final Map<Issue, Map<File, List<LintProblemData>>> problemMap = new HashMap<>();
    AnalysisScope scope = context.getRefManager().getScope();
    if (scope == null) {
      scope = AndroidLintLintBaselineInspection.ourRerunScope;
      if (scope == null) {
        return;
      }
    }

    LintBatchResult lintResult = new LintBatchResult(project, problemMap, scope, issues);
    final LintIdeClient client = ideSupport.createBatchClient(lintResult);

    EnumSet<Scope> lintScope;
    if (!LintIdeClient.SUPPORT_CLASS_FILES) {
      lintScope = EnumSet.copyOf(Scope.ALL);
      // Can't run class file based checks
      lintScope.remove(Scope.CLASS_FILE);
      lintScope.remove(Scope.ALL_CLASS_FILES);
      lintScope.remove(Scope.JAVA_LIBRARIES);
    }
    else {
      lintScope = Scope.ALL;
    }

    List<VirtualFile> files = null;
    final List<Module> modules = new ArrayList<>();

    int scopeType = scope.getScopeType();
    switch (scopeType) {
      case AnalysisScope.MODULE: {
        SearchScope searchScope = ReadAction.compute(scope::toSearchScope);
        if (searchScope instanceof ModuleWithDependenciesScope) {
          ModuleWithDependenciesScope s = (ModuleWithDependenciesScope)searchScope;
          if (!s.isSearchInLibraries()) {
            modules.add(s.getModule());
          }
        }
        break;
      }
      case AnalysisScope.FILE:
      case AnalysisScope.VIRTUAL_FILES:
      case AnalysisScope.UNCOMMITTED_FILES: {
        files = new ArrayList<>();
        SearchScope searchScope = ReadAction.compute(scope::toSearchScope);
        if (searchScope instanceof LocalSearchScope) {
          final LocalSearchScope localSearchScope = (LocalSearchScope)searchScope;
          final PsiElement[] elements = localSearchScope.getScope();
          final List<VirtualFile> finalFiles = files;

          ApplicationManager.getApplication().runReadAction(() -> {
            for (PsiElement element : elements) {
              if (element instanceof PsiFile) { // should be the case since scope type is FILE
                Module module = ModuleUtilCore.findModuleForPsiElement(element);
                if (module != null && !modules.contains(module)) {
                  modules.add(module);
                }
                VirtualFile virtualFile = ((PsiFile)element).getVirtualFile();
                if (virtualFile != null) {
                  if (!(virtualFile instanceof LightVirtualFile)) { // such as translations editor
                    finalFiles.add(virtualFile);
                  }
                }
              }
            }
          });
        }
        else {
          final List<VirtualFile> finalList = files;
          scope.accept(new PsiElementVisitor() {
            @Override
            public void visitFile(PsiFile file) {
              VirtualFile virtualFile = file.getVirtualFile();
              if (virtualFile != null) {
                finalList.add(virtualFile);
              }
            }
          });
        }
        if (files.isEmpty()) {
          files = null;
        }
        else {
          // Lint will compute it lazily based on actual files in the request
          lintScope = null;
        }
        break;
      }
      case AnalysisScope.PROJECT: {
        modules.addAll(Arrays.asList(ModuleManager.getInstance(project).getModules()));
        break;
      }
      case AnalysisScope.CUSTOM:
      case AnalysisScope.MODULES:
      case AnalysisScope.DIRECTORY: {
        // Handled by the getNarrowedComplementaryScope case below
        break;
      }

      case AnalysisScope.INVALID:
        break;
      default:
        Logger.getInstance(this.getClass()).warn("Unexpected inspection scope " + scope + ", " + scopeType);
    }

    if (modules.isEmpty()) {
      for (Module module : ModuleManager.getInstance(project).getModules()) {
        if (scope.containsModule(module)) {
          modules.add(module);
        }
      }

      if (modules.isEmpty() && files != null) {
        for (VirtualFile file : files) {
          Module module = ModuleUtilCore.findModuleForFile(file, project);
          if (module != null && !modules.contains(module)) {
            modules.add(module);
          }
        }
      }

      if (modules.isEmpty()) {
        AnalysisScope scopeRef = scope; // Need effectively final reference to permit capture by lambda.
        AnalysisScope narrowed = ReadAction.compute(() -> scopeRef.getNarrowedComplementaryScope(project));
        for (Module module : ModuleManager.getInstance(project).getModules()) {
          if (narrowed.containsModule(module)) {
            modules.add(module);
          }
        }
      }
    }

    LintRequest request = new LintIdeRequest(client, project, files, modules, false);
    request.setScope(lintScope);
    final LintDriver lint = client.createDriver(request);

    // Baseline analysis?
    myBaseline = null;
    Module severityModule = null;
    for (Module module : modules) {
      if (severityModule == null) {
        if (ideSupport.getSeverityOverrides(module) != null) {
          severityModule = module;
        }
      }
      File baselineFile = ideSupport.getBaselineFile(client, module);
      if (baselineFile != null && !AndroidLintLintBaselineInspection.ourSkipBaselineNextRun) {
        if (!baselineFile.isAbsolute()) {
          String path = module.getProject().getBasePath();
          if (path != null) {
            baselineFile = new File(FileUtil.toSystemDependentName(path), baselineFile.getPath());
          }
        }
        myBaseline = new LintBaseline(client, baselineFile);
        lint.setBaseline(myBaseline);
        if (!baselineFile.isFile()) {
          myBaseline.setWriteOnClose(true);
        }
        else if (AndroidLintLintBaselineInspection.ourUpdateBaselineNextRun) {
          myBaseline.setRemoveFixed(true);
          myBaseline.setWriteOnClose(true);
        }
        break;
      }
    }

    lint.analyze();

    // Running all detectors? Then add dynamically registered detectors too.
    if (!runningSingleInspection) {
      List<Tools> dynamicTools = AndroidLintInspectionBase.getDynamicTools(project);
      if (dynamicTools != null) {
        if (dynamicTools.size() == 1) {
          for (Tools tool : dynamicTools) {
            // can't just call globalTools.contains(tool): ToolsImpl.equals does *not* check
            // tool identity, it just checks settings identity.
            String name = tool.getShortName();
            boolean alreadyRegistered = false;
            for (Tools registered : globalTools) {
              if (registered.getShortName().equals(name)) {
                alreadyRegistered = true;
                break;
              }
            }
            if (!alreadyRegistered) {
              globalTools.add(tool);
            }
          }
        }
        else {
          Set<String> registeredNames = Sets.newHashSetWithExpectedSize(dynamicTools.size());
          for (Tools registered : globalTools) {
            registeredNames.add(registered.getShortName());
          }
          for (Tools tool : dynamicTools) {
            if (!registeredNames.contains(tool.getShortName())) {
              globalTools.add(tool);
            }
          }
        }
      }
    }

    AndroidLintLintBaselineInspection.clearNextRunState();
    lint.setAnalysisStartTime(startTime);
    ideSupport.logSession(lint, severityModule, lintResult);
    myResults = problemMap;
  }

  @Nullable
  public Map<Issue, Map<File, List<LintProblemData>>> getResults() {
    return myResults;
  }

  @Override
  public void performPostRunActivities(@NotNull List<InspectionToolWrapper<?, ?>> inspections, @NotNull final GlobalInspectionContext context) {
    if (myBaseline != null) {
      // Close the baseline; we need to hold a read lock such that line numbers can be computed from PSI file contents
      if (myBaseline.getWriteOnClose()) {
        ApplicationManager.getApplication().runReadAction(() -> myBaseline.close());
      }

      // If we wrote a baseline file, post a notification
      if (myBaseline.getWriteOnClose()) {
        String message;
        if (myBaseline.getRemoveFixed()) {
          message = String
            .format(Locale.US, "Updated baseline file %1$s<br>Removed %2$d issues<br>%3$s remaining", myBaseline.getFile().getName(),
                    myBaseline.getFixedCount(),
                    Lint.describeCounts(myBaseline.getFoundErrorCount(), myBaseline.getFoundWarningCount(), false,
                                        true));
        }
        else {
          message = String
            .format(Locale.US, "Created baseline file %1$s<br>%2$d issues will be filtered out", myBaseline.getFile().getName(),
                    myBaseline.getTotalCount());
        }
        new NotificationGroup(
          "Wrote Baseline", NotificationDisplayType.BALLOON, true, null, null, null, PluginId.getId("org.jetbrains.android"))
          .createNotification(message, NotificationType.INFORMATION)
          .notify(context.getProject());
      }
    }
  }

  @Override
  public void cleanup() {
    if (myEnabledIssue != null) {
      myEnabledIssue.setEnabledByDefault(false);
      myEnabledIssue = null;
    }
  }
}
