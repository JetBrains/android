package org.jetbrains.android.inspections.lint;

import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.client.api.LintRequest;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.Tools;
import com.intellij.codeInspection.lang.GlobalInspectionContextExtension;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.scopes.ModuleWithDependenciesScope;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
class AndroidLintGlobalInspectionContext implements GlobalInspectionContextExtension<AndroidLintGlobalInspectionContext> {
  static final Key<AndroidLintGlobalInspectionContext> ID = Key.create("AndroidLintGlobalInspectionContext");
  private Map<Issue, Map<File, List<ProblemData>>> myResults;

  @NotNull
  @Override
  public Key<AndroidLintGlobalInspectionContext> getID() {
    return ID;
  }

  @Override
  public void performPreRunActivities(@NotNull List<Tools> globalTools, @NotNull List<Tools> localTools, @NotNull final GlobalInspectionContext context) {
    final Project project = context.getProject();

    if (!ProjectFacetManager.getInstance(project).hasFacets(AndroidFacet.ID)) {
      return;
    }

    final List<Issue> issues = AndroidLintExternalAnnotator.getIssuesFromInspections(project, null);
    if (issues.size() == 0) {
      return;
    }

    final Map<Issue, Map<File, List<ProblemData>>> problemMap = new HashMap<Issue, Map<File, List<ProblemData>>>();
    final Set<VirtualFile> allContentRoots = new HashSet<VirtualFile>();

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (AndroidFacet.getInstance(module) != null) {
        final VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
        Collections.addAll(allContentRoots, contentRoots);
      }
    }

    final File[] ioContentRoots = toIoFiles(allContentRoots);
    final AnalysisScope scope = context.getRefManager().getScope();

    final LintClient client = IntellijLintClient.forBatch(project, problemMap, scope, issues);
    final LintDriver lint = new LintDriver(new IntellijLintIssueRegistry(), client);

    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      ProgressWrapper.unwrap(indicator).setText("Running Android Lint");
    }

    EnumSet<Scope> lintScope = EnumSet.copyOf(Scope.ALL);
    // Can't run class file based checks
    lintScope.remove(Scope.CLASS_FILE);
    lintScope.remove(Scope.ALL_CLASS_FILES);
    lintScope.remove(Scope.JAVA_LIBRARIES);

    List<File> files = Arrays.asList(ioContentRoots);

    int scopeType = scope.getScopeType();
    if (scopeType == AnalysisScope.MODULE) {
      SearchScope searchScope = scope.toSearchScope();
      if (searchScope instanceof ModuleWithDependenciesScope) {
        ModuleWithDependenciesScope s = (ModuleWithDependenciesScope)searchScope;
        if (!s.isSearchInLibraries()) {
          Module module = s.getModule();
          VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
          files = Arrays.asList(toIoFiles(Arrays.<VirtualFile>asList(contentRoots)));
        }
      }
    } else if (scopeType == AnalysisScope.FILE || scopeType == AnalysisScope.VIRTUAL_FILES) {
      assert scope.getFileCount() == 1;
      SearchScope searchScope = scope.toSearchScope();
      if (searchScope instanceof LocalSearchScope) {
        LocalSearchScope localSearchScope = (LocalSearchScope)searchScope;
        PsiElement[] elements = localSearchScope.getScope();
        Set<VirtualFile> virtualFiles = Sets.newHashSet();
        for (PsiElement element : elements) {
          if (element instanceof PsiFile) { // should be the case since scope type is FILE
            VirtualFile virtualFile = ((PsiFile)element).getVirtualFile();
            if (virtualFile != null) {
              virtualFiles.add(virtualFile);
            }
          }
        }
        if (!virtualFiles.isEmpty()) {
          files = Lists.newArrayList();
          lintScope = null; // Lint will compute it lazily based on actual files in the request
          for (VirtualFile virtualFile : virtualFiles) {
            files.add(VfsUtilCore.virtualToIoFile(virtualFile));
          }
        }
      }
    }

    LintRequest request = new IntellijLintRequest(client, files, project);
    request.setScope(lintScope);

    lint.analyze(request);

    myResults = problemMap;
  }

  private static File[] toIoFiles(@NotNull Collection<VirtualFile> files) {
    final File[] result = new File[files.size()];

    int i = 0;
    for (VirtualFile file : files) {
      result[i++] = new File(file.getPath());
    }
    return result;
  }

  @Nullable
  public Map<Issue, Map<File, List<ProblemData>>> getResults() {
    return myResults;
  }

  @Override
  public void performPostRunActivities(@NotNull List<InspectionToolWrapper> inspections, @NotNull final GlobalInspectionContext context) {
  }

  @Override
  public void cleanup() {
  }
}
