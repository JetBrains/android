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
package com.android.tools.idea.lint;

import com.android.annotations.NonNull;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.LintOptions;
import com.android.ide.common.repository.GradleVersion;
import com.android.ide.common.repository.ResourceVisibilityLookup;
import com.android.ide.common.res2.AbstractResourceRepository;
import com.android.ide.common.res2.ResourceFile;
import com.android.ide.common.res2.ResourceItem;
import com.android.manifmerger.Actions;
import com.android.repository.Revision;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.editors.manifest.ManifestUtils;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.model.MergedManifest;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.android.tools.idea.res.AppResourceRepository;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ModuleResourceRepository;
import com.android.tools.idea.res.ProjectResourceRepository;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.templates.GoogleMavenVersionLookup;
import com.android.tools.lint.checks.ApiLookup;
import com.android.tools.lint.client.api.*;
import com.android.tools.lint.detector.api.*;
import com.android.tools.lint.helpers.DefaultJavaEvaluator;
import com.android.tools.lint.helpers.DefaultUastParser;
import com.android.utils.Pair;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.lang.UrlClassLoader;
import com.intellij.util.net.HttpConfigurable;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.inspections.lint.AndroidLintInspectionBase;
import org.jetbrains.android.inspections.lint.ProblemData;
import org.jetbrains.android.inspections.lint.State;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;

import static com.android.ide.common.repository.GoogleMavenRepository.MAVEN_GOOGLE_CACHE_DIR_KEY;
import static com.android.tools.lint.detector.api.TextFormat.RAW;

/**
 * Implementation of the {@linkplain LintClient} API for executing lint within the IDE:
 * reading files, reporting issues, logging errors, etc.
 */
public class LintIdeClient extends LintClient implements Disposable {
  protected static final Logger LOG = Logger.getInstance("#org.jetbrains.android.inspections.LintIdeClient");

  @NonNull protected Project myProject;
  @Nullable protected Map<com.android.tools.lint.detector.api.Project, Module> myModuleMap;

  public LintIdeClient(@NonNull Project project) {
    super(CLIENT_STUDIO);
    myProject = project;
  }

  /** Creates a lint client for batch inspections */
  public static LintIdeClient forBatch(@NotNull Project project,
                                       @NotNull Map<Issue, Map<File, List<ProblemData>>> problemMap,
                                       @NotNull AnalysisScope scope,
                                       @NotNull Set<Issue> issues) {
    return new BatchLintClient(project, problemMap, scope, issues);
  }

  /**
   * Returns an {@link ApiLookup} service.
   *
   * @param project the project to use for locating the Android SDK
   * @return an API lookup if one can be found
   */
  @Nullable
  public static ApiLookup getApiLookup(@NotNull Project project) {
    return ApiLookup.get(new LintIdeClient(project));
  }

  @Override
  public void runReadAction(@NonNull Runnable runnable) {
    ApplicationManager.getApplication().runReadAction(runnable);
  }

  /**
   * Creates a lint client used for in-editor single file lint analysis (e.g. background checking while user is editing.)
   */
  public static LintIdeClient forEditor(@NotNull State state) {
    return new EditorLintClient(state);
  }

  @NonNull
  public Project getIdeProject() {
    return myProject;
  }

  @Nullable
  protected Module findModuleForLintProject(@NotNull Project project,
                                            @NotNull com.android.tools.lint.detector.api.Project lintProject) {
    if (myModuleMap != null) {
      Module module = myModuleMap.get(lintProject);
      if (module != null) {
        return module;
      }
    }
    final File dir = lintProject.getDir();
    final VirtualFile vDir = LocalFileSystem.getInstance().findFileByIoFile(dir);
    return vDir != null ? ModuleUtilCore.findModuleForFile(vDir, project) : null;
  }

  void setModuleMap(@Nullable Map<com.android.tools.lint.detector.api.Project, Module> moduleMap) {
    myModuleMap = moduleMap;
  }

  @NonNull
  @Override
  public Configuration getConfiguration(@NonNull com.android.tools.lint.detector.api.Project project, @Nullable final LintDriver driver) {
    if (project.isGradleProject() && project.isAndroidProject() && !project.isLibrary()) {
      AndroidProject model = project.getGradleProjectModel();
      if (model != null) {
        try {
          LintOptions lintOptions = model.getLintOptions();
          final Map<String, Integer> overrides = lintOptions.getSeverityOverrides();
          if (overrides != null && !overrides.isEmpty()) {
            return new DefaultConfiguration(this, project, null) {
              @NonNull
              @Override
              public Severity getSeverity(@NonNull Issue issue) {
                Integer severity = overrides.get(issue.getId());
                if (severity != null) {
                  switch (severity.intValue()) {
                    case LintOptions.SEVERITY_FATAL:
                      return Severity.FATAL;
                    case LintOptions.SEVERITY_ERROR:
                      return Severity.ERROR;
                    case LintOptions.SEVERITY_WARNING:
                      return Severity.WARNING;
                    case LintOptions.SEVERITY_INFORMATIONAL:
                      return Severity.INFORMATIONAL;
                    case LintOptions.SEVERITY_IGNORE:
                    default:
                      return Severity.IGNORE;
                  }
                }

                Set<Issue> issues = getIssues();
                boolean known = issues.contains(issue);
                if (!known) {
                  if (issue == IssueRegistry.BASELINE || issue == IssueRegistry.CANCELLED) {
                    return Severity.IGNORE;
                  }

                  // Allow third-party checks
                  LintIdeIssueRegistry builtin = new LintIdeIssueRegistry();
                  if (builtin.isIssueId(issue.getId())) {
                    return Severity.IGNORE;
                  }
                }

                return super.getSeverity(issue);
              }
            };
          }
        } catch (Exception e) {
          LOG.error(e);
        }
      }
    }
    return new DefaultConfiguration(this, project, null) {
      @Override
      public boolean isEnabled(@NonNull Issue issue) {
        Set<Issue> issues = getIssues();
        boolean known = issues.contains(issue);
        if (!known) {
          if (issue == IssueRegistry.BASELINE || issue == IssueRegistry.CANCELLED) {
            return true;
          }

          // Allow third-party checks
          LintIdeIssueRegistry builtin = new LintIdeIssueRegistry();
          return !builtin.isIssueId(issue.getId());
        }

        return super.isEnabled(issue);
      }
    };
  }

  @Override
  public void report(@NonNull Context context,
                     @NonNull Issue issue,
                     @NonNull Severity severity,
                     @NonNull Location location,
                     @NonNull String message,
                     @NonNull TextFormat format,
                     @Nullable LintFix extraData) {
    assert false : message;
  }

  @NonNull protected Set<Issue> getIssues() {
    return Collections.emptySet();
  }

  @Nullable
  protected Module getModule() {
    return null;
  }

  /**
   * Recursively calls {@link #report} on the secondary location of this error, if any, which in turn may call it on a third
   * linked location, and so on.This is necessary since IntelliJ problems don't have secondary locations; instead, we create one
   * problem for each location associated with the lint error.
   */
  protected void reportSecondary(@NonNull Context context, @NonNull Issue issue, @NonNull Severity severity, @NonNull Location location,
                                 @NonNull String message, @NonNull TextFormat format, @Nullable LintFix extraData) {
    Location secondary = location.getSecondary();
    if (secondary != null && secondary.getVisible()) {
      String secondaryMessage = secondary.getMessage();
      if (secondaryMessage != null) {
        if (secondary.isSelfExplanatory()) {
          message = secondaryMessage;
        }
        else {
          message = message + " (" + secondaryMessage + ")";
        }
      }
      report(context, issue, severity, secondary, message, format, extraData);
    }
  }

  @Override
  public void log(@NonNull Severity severity, @Nullable Throwable exception, @Nullable String format, @Nullable Object... args) {
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
  public XmlParser getXmlParser() {
    return new DomPsiParser(this);
  }

  @Nullable
  @Override
  public JavaParser getJavaParser(@Nullable com.android.tools.lint.detector.api.Project project) {
    return new LintIdeJavaParser(this, myProject, project);
  }

  @Nullable
  @Override
  public UastParser getUastParser(@Nullable com.android.tools.lint.detector.api.Project project) {
    return new DefaultUastParser(project, myProject) {
      @NonNull
      @Override
      protected DefaultJavaEvaluator createEvaluator(@Nullable com.android.tools.lint.detector.api.Project project,
                                                     @NonNull Project p) {
        return new DefaultJavaEvaluator(p, project) {
          // Use JavaDirectoryService. From the CLI we avoid it.
          @Nullable
          @Override
          public PsiPackage getPackage(@NonNull PsiElement node) {
            PsiFile containingFile = node.getContainingFile();
            if (containingFile != null) {
              PsiDirectory dir = containingFile.getParent();
              if (dir != null) {
                return JavaDirectoryService.getInstance().getPackage(dir);
              }
            }
            return null;
          }
        };
      }
    };
  }

  @NonNull
  @Override
  public List<File> getJavaClassFolders(@NonNull com.android.tools.lint.detector.api.Project project) {
    // todo: implement when class files checking detectors will be available
    return Collections.emptyList();
  }

  @NonNull
  @Override
  public List<File> getJavaLibraries(@NonNull com.android.tools.lint.detector.api.Project project, boolean includeProvided) {
    // todo: implement
    return Collections.emptyList();
  }

  @NonNull
  @Override
  public List<File> getTestLibraries(@NonNull com.android.tools.lint.detector.api.Project project) {
    return Collections.emptyList();
  }

  @Override
  @NonNull
  public String readFile(@NonNull final File file) {
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
  public void dispose() {
    myProject = null;
    myModuleMap = null;
  }

  @Nullable
  @Override
  public File getSdkHome() {
    Module module = getModule();
    if (module != null && !module.isDisposed()) {
      Sdk moduleSdk = ModuleRootManager.getInstance(module).getSdk();
      if (moduleSdk != null && moduleSdk.getSdkType() instanceof AndroidSdkType) {
        String path = moduleSdk.getHomePath();
        if (path != null) {
          File home = new File(path);
          if (home.exists()) {
            return home;
          }
        }
      }
    }

    File sdkHome = super.getSdkHome();
    if (sdkHome != null) {
      return sdkHome;
    }

    if (!myProject.isDisposed()) {
      for (Module m : ModuleManager.getInstance(myProject).getModules()) {
        Sdk moduleSdk = ModuleRootManager.getInstance(m).getSdk();
        if (moduleSdk != null) {
          if (moduleSdk.getSdkType() instanceof AndroidSdkType) {
            String path = moduleSdk.getHomePath();
            if (path != null) {
              File home = new File(path);
              if (home.exists()) {
                return home;
              }
            }
          }
        }
      }
    }

    return IdeSdks.getInstance().getAndroidSdkPath();
  }

  private AndroidSdkHandler sdk = null;

  @Nullable
  @Override
  public AndroidSdkHandler getSdk() {
    if (sdk == null) {
      Module module = getModule();
      AndroidSdkHandler localSdk = getLocalSdk(module);
      if (localSdk != null) {
        sdk = localSdk;
      } else if (!myProject.isDisposed()) {
        for (Module m : ModuleManager.getInstance(myProject).getModules()) {
          localSdk = getLocalSdk(m);
          if (localSdk != null) {
            sdk = localSdk;
            break;
          }
        }

        if (localSdk == null) {
          sdk = super.getSdk();
        }
      }
    }

    return sdk;
  }

  @Nullable
  private static AndroidSdkHandler getLocalSdk(@Nullable Module module) {
    if (module != null && !module.isDisposed()) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        AndroidSdkData sdkData = AndroidSdkData.getSdkData(facet);
        if (sdkData != null) {
          return sdkData.getSdkHandler();
        }
      }
    }

    return null;
  }

  @Override
  @Nullable
  public BuildToolInfo getBuildTools(@NonNull com.android.tools.lint.detector.api.Project project) {
    if (project.isGradleProject()) {
      Module module = getModule();
      if (module != null && !module.isDisposed()) {
        AndroidModuleModel model = AndroidModuleModel.get(module);
        if (model != null) {
          GradleVersion version = model.getModelVersion();
          if (version != null && version.isAtLeast(2, 1, 0)) {
            String buildToolsVersion = model.getAndroidProject().getBuildToolsVersion();
            AndroidSdkHandler sdk = getSdk();
            if (sdk != null) {
              try {
                Revision revision = Revision.parseRevision(buildToolsVersion);
                BuildToolInfo buildToolInfo = sdk.getBuildToolInfo(revision, getRepositoryLogger());
                if (buildToolInfo != null) {
                  return buildToolInfo;
                }
              } catch (NumberFormatException ignore) {
                // Fall through and use the latest
              }
            }
          }
        }
      }
    }

    return super.getBuildTools(project);
  }

  @Nullable
  @Override
  public String getClientRevision() {
    return ApplicationInfoEx.getInstanceEx().getFullVersion();
  }

  @Override
  public boolean isGradleProject(com.android.tools.lint.detector.api.Project project) {
    Module module = getModule();
    if (module != null && !module.isDisposed()) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      return facet != null && facet.requiresAndroidModel();
    }
    return AndroidProjectInfo.getInstance(myProject).requiresAndroidModel();
  }

  @Nullable private static volatile String ourSystemPath;

  @Nullable
  @Override
  public File getCacheDir(@Nullable String name, boolean create) {
    if (MAVEN_GOOGLE_CACHE_DIR_KEY.equals(name)) {
      // Share network cache with existing implementation
      return GoogleMavenVersionLookup.INSTANCE.getCacheDir();
    }

    final String path = ourSystemPath != null ? ourSystemPath : (ourSystemPath = PathUtil.getCanonicalPath(PathManager.getSystemPath()));
    String relative = "lint";
    if (name != null) {
      relative += File.separator + name;
    }
    File lint = new File(path, relative);
    if (create && !lint.exists()) {
      //noinspection ResultOfMethodCallIgnored
      lint.mkdirs();
    }
    return lint;
  }

  @Override
  public boolean isProjectDirectory(@NonNull File dir) {
    return new File(dir, Project.DIRECTORY_STORE_FOLDER).exists();
  }

  private static final String MERGED_MANIFEST_INFO = "lint-merged-manifest-info";

  @Nullable
  @Override
  public org.w3c.dom.Document getMergedManifest(@NonNull com.android.tools.lint.detector.api.Project project) {
    final Module module = findModuleForLintProject(myProject, project);
    if (module != null) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        MergedManifest mergedManifest = MergedManifest.get(facet);
        org.w3c.dom.Document document = mergedManifest.getDocument();
        if (document != null) {
          Element root = document.getDocumentElement();
          if (root != null && !isMergeManifestNode(root)) {
            resolveMergeManifestSources(document, project.getDir());
            document.setUserData(MERGED_MANIFEST_INFO, mergedManifest, null);
          }
          return document;
        }
      }
    }

    return null;
  }

  @Override
  @Nullable
  public Pair<File, Node> findManifestSourceNode(@NonNull Node mergedNode) {
    Map<Node, Pair<File, Node>> sourceNodeCache = getSourceNodeCache();
    Pair<File,Node> source = sourceNodeCache.get(mergedNode);
    if (source != null) {
      if (source == NOT_FOUND) {
        return null;
      }
      return source;
    }

    org.w3c.dom.Document doc = mergedNode.getOwnerDocument();
    if (doc == null) {
      return null;
    }
    MergedManifest mergedManifest = (MergedManifest) doc.getUserData(MERGED_MANIFEST_INFO);
    if (mergedManifest == null) {
      return null;
    }

    source = NOT_FOUND;
    List<? extends Actions.Record> records = ManifestUtils.getRecords(mergedManifest, mergedNode);
    for (Actions.Record record : records) {
      if (record.getActionType() == Actions.ActionType.ADDED ||
          record.getActionType() == Actions.ActionType.MERGED) {
        Node sourceNode = ManifestUtils.getSourceNode(mergedManifest.getModule(), record);
        if (sourceNode != null) {
          // Cache for next time
          File file = record.getActionLocation().getFile().getSourceFile();
          source = Pair.of(file, sourceNode);
          break;
        }
      }
    }

    sourceNodeCache.put(mergedNode, source);

    return source != NOT_FOUND ? source : null;
  }

  /**
   * A lint client used for in-editor single file lint analysis (e.g. background checking while user is editing.)
   * <p>
   * Since this applies only to a given file and module, it can take some shortcuts over what the general
   * {@link BatchLintClient} has to do.
   * */
  private static class EditorLintClient extends LintIdeClient {
    private final State myState;

    public EditorLintClient(@NotNull State state) {
      super(state.getModule().getProject());
      myState = state;
    }

    @Nullable
    @Override
    protected Module getModule() {
      return myState.getModule();
    }

    @NonNull
    @Override
    protected Set<Issue> getIssues() {
      return myState.getIssues();
    }

    @Override
    public void report(@NonNull Context context,
                       @NonNull Issue issue,
                       @NonNull Severity severity,
                       @NonNull Location location,
                       @NonNull String message,
                       @NonNull TextFormat format,
                       @Nullable LintFix quickfixData) {
      if (location != null) {
        final File file = location.getFile();
        final VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(file);

        if (myState.getMainFile().equals(vFile)) {
          final Position start = location.getStart();
          final Position end = location.getEnd();

          final TextRange textRange = start != null && end != null && start.getOffset() <= end.getOffset()
                                      ? new TextRange(start.getOffset(), end.getOffset())
                                      : TextRange.EMPTY_RANGE;

          Severity configuredSeverity = severity != issue.getDefaultSeverity() ? severity : null;
          message = format.convertTo(message, RAW);
          myState.getProblems().add(new ProblemData(issue, message, textRange, configuredSeverity, quickfixData));
        }

        Location secondary = location.getSecondary();
        if (secondary != null && myState.getMainFile().equals(LocalFileSystem.getInstance().findFileByIoFile(secondary.getFile()))) {
          reportSecondary(context, issue, severity, location, message, format, quickfixData);
        }
      }
    }

    @Override
    @NotNull
    public String readFile(@NonNull File file) {
      final VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(file);

      if (vFile == null) {
        try {
          return Files.toString(file, Charsets.UTF_8);
        } catch (IOException ioe) {
          LOG.debug("Cannot find file " + file.getPath() + " in the VFS");
          return "";
        }
      }
      final String content = getFileContent(vFile);

      if (content == null) {
        LOG.info("Cannot find file " + file.getPath() + " in the PSI");
        return "";
      }
      return content;
    }

    @Nullable
    private String getFileContent(final VirtualFile vFile) {
      if (Comparing.equal(myState.getMainFile(), vFile)) {
        return myState.getMainFileContent();
      }

      return ApplicationManager.getApplication().runReadAction((Computable<String>)() -> {
        final Module module = myState.getModule();
        if (module.isDisposed()) {
          return null;
        }
        final Project project = module.getProject();
        if (project.isDisposed()) {
          return null;
        }

        final PsiFile psiFile = PsiManager.getInstance(project).findFile(vFile);

        if (psiFile == null) {
          return null;
        }
        final Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);

        if (document != null) {
          final DocumentListener listener = new DocumentListener() {
            @Override
            public void beforeDocumentChange(DocumentEvent event) {
            }

            @Override
            public void documentChanged(DocumentEvent event) {
              myState.markDirty();
            }
          };
          document.addDocumentListener(listener, this);
        }
        return psiFile.getText();
      });
    }

    @NonNull
    @Override
    public List<File> getJavaSourceFolders(@NonNull com.android.tools.lint.detector.api.Project project) {
      Module module = myState.getModule();
      if (module.isDisposed()) {
        return super.getJavaSourceFolders(project);
      }
      final VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots(false);
      final List<File> result = new ArrayList<>(sourceRoots.length);

      for (VirtualFile root : sourceRoots) {
        result.add(new File(root.getPath()));
      }
      return result;
    }

    @NonNull
    @Override
    public List<File> getResourceFolders(@NonNull com.android.tools.lint.detector.api.Project project) {
      Module module = myState.getModule();
      if (!module.isDisposed()) {
        AndroidFacet facet = AndroidFacet.getInstance(module);
        if (facet != null) {
          return LintIdeUtils.getResourceDirectories(facet);
        }
      }
      return super.getResourceFolders(project);
    }
  }

  /** Lint client used for batch operations */
  private static class BatchLintClient extends LintIdeClient {
    private final Map<Issue, Map<File, List<ProblemData>>> myProblemMap;
    private final AnalysisScope myScope;
    private final Set<Issue> myIssues;

    public BatchLintClient(@NotNull Project project,
                           @NotNull Map<Issue, Map<File, List<ProblemData>>> problemMap,
                           @NotNull AnalysisScope scope,
                           @NotNull Set<Issue> issues) {
      super(project);
      myProblemMap = problemMap;
      myScope = scope;
      myIssues = issues;
    }

    @Nullable
    @Override
    protected Module getModule() {
      // No default module
      return null;
    }

    @NonNull
    @Override
    protected Set<Issue> getIssues() {
      return myIssues;
    }

    @Override
    public void report(@NonNull Context context,
                       @NonNull Issue issue,
                       @NonNull Severity severity,
                       @NonNull Location location,
                       @NonNull String message,
                       @NonNull TextFormat format,
                       @Nullable LintFix quickfixData) {
      VirtualFile vFile = null;
      File file = null;

      if (location != null) {
        file = location.getFile();
        vFile = LocalFileSystem.getInstance().findFileByIoFile(file);
      }
      else if (context.getProject() != null) {
        final Module module = findModuleForLintProject(myProject, context.getProject());

        if (module != null) {
          final AndroidFacet facet = AndroidFacet.getInstance(module);
          vFile = facet != null ? AndroidRootUtil.getPrimaryManifestFile(facet) : null;

          if (vFile != null) {
            file = new File(vFile.getPath());
          }
        }
      }

      boolean inScope = vFile != null && myScope.contains(vFile);
      // In analysis batch mode, the AnalysisScope contains a specific set of virtual
      // files, not directories, so any errors reported against a directory will not
      // be considered part of the scope and therefore won't be reported. Correct
      // for this.
      if (!inScope && vFile != null && vFile.isDirectory()) {
        if (myScope.getScopeType() == AnalysisScope.PROJECT) {
          inScope = true;
        } else if (myScope.getScopeType() == AnalysisScope.MODULE ||
          myScope.getScopeType() == AnalysisScope.MODULES) {
          final Module module = findModuleForLintProject(myProject, context.getProject());
          if (module != null && myScope.containsModule(module)) {
            inScope = true;
          }
        }
      }

      if (inScope) {
        file = new File(PathUtil.getCanonicalPath(file.getPath()));

        Map<File, List<ProblemData>> file2ProblemList = myProblemMap.get(issue);
        if (file2ProblemList == null) {
          file2ProblemList = new HashMap<>();
          myProblemMap.put(issue, file2ProblemList);
        }

        List<ProblemData> problemList = file2ProblemList.get(file);
        if (problemList == null) {
          problemList = new ArrayList<>();
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
        Severity configuredSeverity = severity != issue.getDefaultSeverity() ? severity : null;
        message = format.convertTo(message, RAW);
        problemList.add(new ProblemData(issue, message, textRange, configuredSeverity, quickfixData));

        if (location != null && location.getSecondary() != null) {
          reportSecondary(context, issue, severity, location, message, format, quickfixData);
        }
      }

      // Ensure third party issue registered
      AndroidLintInspectionBase.getInspectionShortNameByIssue(myProject, issue);
    }

    @NonNull
    @Override
    public List<File> getJavaSourceFolders(@NonNull com.android.tools.lint.detector.api.Project project) {
      final Module module = findModuleForLintProject(myProject, project);
      if (module == null) {
        return Collections.emptyList();
      }
      final VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots(false);
      final List<File> result = new ArrayList<>(sourceRoots.length);

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
          return LintIdeUtils.getResourceDirectories(facet);
        }
      }
      return super.getResourceFolders(project);
    }
  }

  @Override
  public boolean checkForSuppressComments() {
    return false;
  }

  @Override
  public boolean supportsProjectResources() {
    return true;
  }

  @Nullable
  @Override
  public AbstractResourceRepository getResourceRepository(com.android.tools.lint.detector.api.Project project,
                                                          boolean includeModuleDependencies,
                                                          boolean includeLibraries) {
    final Module module = findModuleForLintProject(myProject, project);
    if (module != null) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        if (includeLibraries) {
          return AppResourceRepository.getOrCreateInstance(facet);
        } else if (includeModuleDependencies) {
          return ProjectResourceRepository.getOrCreateInstance(facet);
        } else {
          return ModuleResourceRepository.getOrCreateInstance(facet);
        }
      }
    }

    return null;
  }

  @Nullable
  @Override
  public URLConnection openConnection(@NonNull URL url) throws IOException {
    return HttpConfigurable.getInstance().openConnection(url.toExternalForm());
  }

  @Override
  @Nullable
  public URLConnection openConnection(@NonNull URL url, int timeout) throws IOException {
    URLConnection connection = HttpConfigurable.getInstance().openConnection(url.toExternalForm());
    if (timeout > 0) {
      connection.setConnectTimeout(timeout);
      connection.setReadTimeout(timeout);
    }
    return connection;
  }

  @Override
  public ClassLoader createUrlClassLoader(@NonNull URL[] urls, @NonNull ClassLoader parent) {
    return UrlClassLoader.build().parent(parent).urls(urls).get();
  }

  @NonNull
  @Override
  public Location.Handle createResourceItemHandle(@NonNull ResourceItem item) {
    XmlTag tag = LocalResourceRepository.getItemTag(myProject, item);
    if (tag != null) {
      ResourceFile source = item.getSource();
      assert source != null : item;
      return new LocationHandle(source.getFile(), tag);
    }
    return super.createResourceItemHandle(item);
  }

  @NonNull
  @Override
  public ResourceVisibilityLookup.Provider getResourceVisibilityProvider() {
    Module module = getModule();
    if (module != null && !module.isDisposed()) {
      AppResourceRepository appResources = AppResourceRepository.getOrCreateInstance(module);
      if (appResources != null) {
        ResourceVisibilityLookup.Provider provider = appResources.getResourceVisibilityProvider();
        if (provider != null) {
          return provider;
        }
      }
    }
    return super.getResourceVisibilityProvider();
  }

  private static class LocationHandle implements Location.Handle, Computable<Location> {
    private final File myFile;
    private final XmlElement myNode;
    private Object myClientData;

    public LocationHandle(File file, XmlElement node) {
      myFile = file;
      myNode = node;
    }

    @NonNull
    @Override
    public Location resolve() {
      if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
        return ApplicationManager.getApplication().runReadAction(this);
      }
      TextRange textRange = myNode.getTextRange();

      // For elements, don't highlight the entire element range; instead, just
      // highlight the element name
      if (myNode instanceof XmlTag) {
        String tag = ((XmlTag)myNode).getName();
        int index = myNode.getText().indexOf(tag);
        if (index != -1) {
          int start = textRange.getStartOffset() + index;
          textRange = new TextRange(start, start + tag.length());
        }
      }

      Position start = new DefaultPosition(-1, -1, textRange.getStartOffset());
      Position end = new DefaultPosition(-1, -1, textRange.getEndOffset());
      return Location.create(myFile, start, end);
    }

    @Override
    public Location compute() {
      return resolve();
    }

    @Override
    public void setClientData(@Nullable Object clientData) {
      myClientData = clientData;
    }

    @Override
    @Nullable
    public Object getClientData() {
      return myClientData;
    }
  }
}
