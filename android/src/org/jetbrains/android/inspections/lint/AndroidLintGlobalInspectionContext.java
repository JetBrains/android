package org.jetbrains.android.inspections.lint;

import com.android.annotations.NonNull;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.lint.client.api.*;
import com.android.tools.lint.detector.api.*;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.Tools;
import com.intellij.codeInspection.lang.GlobalInspectionContextExtension;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.module.impl.scopes.ModuleWithDependenciesScope;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
class AndroidLintGlobalInspectionContext implements GlobalInspectionContextExtension<AndroidLintGlobalInspectionContext> {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.inspections.lint.AndroidLintGlobalInspectionContext");

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

    final LintClient client = new MyLintClient(project, problemMap, scope, issues);
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

  private static class MyLintClient extends LintClient {
    private final Project myProject;
    private final Map<Issue, Map<File, List<ProblemData>>> myProblemMap;
    private final AnalysisScope myScope;
    private final Collection<Issue> myIssues;

    private MyLintClient(@NotNull Project project,
                         @NotNull Map<Issue, Map<File, List<ProblemData>>> problemMap,
                         @NotNull AnalysisScope scope,
                         @NotNull Collection<Issue> issues) {
      myProject = project;
      myProblemMap = problemMap;
      myScope = scope;
      myIssues = issues;
    }

    @Override
    public Configuration getConfiguration(com.android.tools.lint.detector.api.Project project) {
      return new IntellijLintConfiguration(myIssues);
    }

    @Override
    public void report(Context context, Issue issue, Severity severity, Location location, String message, Object data) {
      VirtualFile vFile = null;
      File file = null;

      if (location != null) {
        file = location.getFile();

        if (file != null) {
          vFile = LocalFileSystem.getInstance().findFileByIoFile(file);
        }
      }
      else if (context.getProject() != null) {
        final Module module = findModuleForLintProject(myProject, context.getProject());

        if (module != null) {
          final AndroidFacet facet = AndroidFacet.getInstance(module);
          vFile = facet != null ? AndroidRootUtil.getManifestFile(facet) : null;

          if (vFile != null) {
            file = new File(vFile.getPath());
          }
        }
      }

      if (vFile != null && myScope.contains(vFile)) {
        file = new File(PathUtil.getCanonicalPath(file.getPath()));

        Map<File, List<ProblemData>> file2ProblemList = myProblemMap.get(issue);
        if (file2ProblemList == null) {
          file2ProblemList = new HashMap<File, List<ProblemData>>();
          myProblemMap.put(issue, file2ProblemList);
        }

        List<ProblemData> problemList = file2ProblemList.get(file);
        if (problemList == null) {
          problemList = new ArrayList<ProblemData>();
          file2ProblemList.put(file, problemList);
        }

        TextRange textRange = TextRange.EMPTY_RANGE;

        if (location != null) {
          final Position start = location.getStart();
          final Position end = location.getEnd();

          if (start != null && end != null && start.getOffset() <= end.getOffset()) {
            textRange = new TextRange(start.getOffset(), end.getOffset());
          }
        }
        problemList.add(new ProblemData(issue, message, textRange));
      }
    }

    @Override
    public void log(Severity severity, Throwable exception, String format, Object... args) {
      if (severity == Severity.ERROR || severity == Severity.FATAL) {
        if (format != null) {
          LOG.error(String.format(format, args), exception);
        } else if (exception != null) {
          LOG.error(exception);
        }
      } else if (severity == Severity.WARNING) {
        if (format != null) {
          LOG.warn(String.format(format, args), exception);
        } else if (exception != null) {
          LOG.warn(exception);
        }
      } else {
        if (format != null) {
          LOG.info(String.format(format, args), exception);
        } else if (exception != null) {
          LOG.info(exception);
        }
      }
    }

    @Override
    public IDomParser getDomParser() {
      return new DomPsiParser(this);
    }

    @Override
    public IJavaParser getJavaParser() {
      return new LombokPsiParser(this);
    }

    @Override
    public String readFile(final File file) {
      final VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(file);

      if (vFile == null) {
        LOG.debug("Cannot find file " + file.getPath() + " in the VFS");
        return "";
      }

      return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        @Nullable
        @Override
        public String compute() {
          final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(vFile);
          if (psiFile == null) {
            LOG.info("Cannot find file " + file.getPath() + " in the PSI");
            return null;
          }
          else {
            return psiFile.getText();
          }
        }
      });
    }

    @Override
    public List<File> getJavaClassFolders(com.android.tools.lint.detector.api.Project project) {
      // todo: implement when class files checking detectors will be available
      return Collections.emptyList();
    }

    @Override
    public List<File> getJavaLibraries(com.android.tools.lint.detector.api.Project project) {
      // todo: implement
      return Collections.emptyList();
    }

    @Nullable
    private static Module findModuleForLintProject(@NotNull Project project,
                                                   @NotNull com.android.tools.lint.detector.api.Project lintProject) {
      final File dir = lintProject.getDir();
      final VirtualFile vDir = LocalFileSystem.getInstance().findFileByIoFile(dir);
      return vDir != null ? ModuleUtil.findModuleForFile(vDir, project) : null;
    }

    @Override
    public List<File> getJavaSourceFolders(com.android.tools.lint.detector.api.Project project) {
      final Module module = findModuleForLintProject(myProject, project);
      if (module == null) {
        return Collections.emptyList();
      }
      final VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots(false);
      final List<File> result = new ArrayList<File>(sourceRoots.length);

      for (VirtualFile root : sourceRoots) {
        result.add(new File(root.getPath()));
      }
      return result;
    }

    @NonNull
    @Override
    public List<File> getResourceFolders(@NonNull com.android.tools.lint.detector.api.Project project) {
      final Module module = findModuleForLintProject(myProject, project);
      if (module != null) {
        AndroidFacet facet = AndroidFacet.getInstance(module);
        if (facet != null) {
          return IntellijLintUtils.getResourceDirectories(facet);
        }
      }
      return super.getResourceFolders(project);
    }

    @Nullable
    @Override
    public File getSdkHome() {
      File sdkHome = super.getSdkHome();
      if (sdkHome != null) {
        return sdkHome;
      }

      for (Module module : ModuleManager.getInstance(myProject).getModules()) {
        Sdk moduleSdk = ModuleRootManager.getInstance(module).getSdk();
        if (moduleSdk != null) {
          String path = moduleSdk.getHomePath();
          if (path != null) {
            File home = new File(path);
            if (home.exists()) {
              return home;
            }
          }
        }
      }

      return null;
    }


    // Overridden such that lint doesn't complain about missing a bin dir property in the event
    // that no SDK is configured
    @Nullable
    @Override
    public File findResource(@NonNull String relativePath) {
      File top = getSdkHome();
      if (top != null) {
        File file = new File(top, relativePath);
        if (file.exists()) {
          return file;
        }
      }

      return null;
    }

    @Override
    public boolean isGradleProject(com.android.tools.lint.detector.api.Project project) {
      return Projects.isGradleProject(myProject);
    }
  }
}
