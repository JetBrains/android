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

import static com.android.tools.lint.detector.api.TextFormat.RAW;

import com.android.annotations.NonNull;
import com.android.tools.lint.checks.ApiLookup;
import com.android.tools.lint.client.api.Configuration;
import com.android.tools.lint.client.api.ConfigurationHierarchy;
import com.android.tools.lint.client.api.GradleVisitor;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.client.api.LintRequest;
import com.android.tools.lint.client.api.UastParser;
import com.android.tools.lint.client.api.XmlParser;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Position;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.TextFormat;
import com.android.tools.lint.helpers.DefaultJavaEvaluator;
import com.android.tools.lint.helpers.DefaultUastParser;
import com.android.tools.lint.model.LintModelLintOptions;
import com.android.tools.lint.model.LintModelModule;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.util.PathUtil;
import com.intellij.util.lang.UrlClassLoader;
import com.intellij.util.net.HttpConfigurable;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implementation of the {@linkplain LintClient} API for executing lint within the IDE:
 * reading files, reporting issues, logging errors, etc.
 */
public class LintIdeClient extends LintClient implements Disposable {
  static {
    LintClient.setClientName(CLIENT_STUDIO);
  }
  /**
   * Whether we support running .class file checks. No class file checks are currently registered as inspections.
   * Since IntelliJ doesn't perform background compilation (e.g. only parsing, so there are no bytecode checks)
   * this might need some work before we enable it.
   */
  public static final boolean SUPPORT_CLASS_FILES = false;
  protected static final Logger LOG = Logger.getInstance(LintIdeClient.class);

  @NonNull protected Project myProject;
  @Nullable protected Map<com.android.tools.lint.detector.api.Project, Module> myModuleMap;

  protected final LintResult myLintResult;

  public LintIdeClient(@NonNull Project project, @NotNull LintResult lintResult) {
    super(CLIENT_STUDIO);
    myProject = project;
    myLintResult = lintResult;
  }

  public LintDriver createDriver(@NonNull LintRequest request) {
    return createDriver(request, LintIdeSupport.get().getIssueRegistry());
  }

  public LintDriver createDriver(@NonNull LintRequest request, @NonNull IssueRegistry registry) {
    LintDriver driver = new LintDriver(registry, this, request);

    Collection<com.android.tools.lint.detector.api.Project> projects = request.getProjects();
    if (projects != null && !projects.isEmpty()) {
      com.android.tools.lint.detector.api.Project main = request.getMainProject(projects.iterator().next());
      LintModelModule model = main.getBuildModule();
      if (model != null) {
        try {
          LintModelLintOptions lintOptions = model.getLintOptions();
          driver.setCheckTestSources(lintOptions.getCheckTestSources());
          driver.setCheckDependencies(lintOptions.getCheckDependencies());
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    }

    return driver;
  }

  /**
   * Returns an {@link ApiLookup} service.
   *
   * @param project the project to use for locating the Android SDK
   * @return an API lookup if one can be found
   */
  @Nullable
  public static ApiLookup getApiLookup(@NotNull Project project) {
    return ApiLookup.get(LintIdeSupport.get().createClient(project, new LintIgnoredResult()));
  }

  @Override
  public void runReadAction(@NonNull Runnable runnable) {
    // We only do this while running in the editor
    if (!(myLintResult instanceof LintEditorResult)) {
      ApplicationManager.getApplication().runReadAction(runnable);
      return;
    }

    // In order to prevent UI freezes due to long-running Lint read actions,
    // we cancel incremental Lint sessions if a write action is running, pending, or later requested.
    // See http://www.jetbrains.org/intellij/sdk/docs/basics/architectural_overview/general_threading_rules.html#preventing-ui-freezes

    Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode()) {
      // Do not yield to pending write actions during unit tests;
      // otherwise the tests will fail before Lint is rescheduled.
      application.runReadAction(runnable);
      return;
    }

    // We use a custom progress indicator to track action cancellation latency,
    // and to collect a stack dump at the time of cancellation.
    class ProgressIndicatorWithCancellationInfo extends AbstractProgressIndicatorExBase {

      final Thread readActionThread;

      // These fields are marked volatile since they will be accessed by two threads (the EDT and the read action thread).
      // Notice that they are set before the progress indicator is marked as cancelled; this establishes a happens-before
      // relationship with myCanceled (also volatile), thereby ensuring that the new values are visible
      // to threads which have detected cancellation.
      volatile StackTraceElement[] cancelStackDump;
      volatile long cancelStartTimeMs = -1;

      ProgressIndicatorWithCancellationInfo(Thread readActionThread) {
        this.readActionThread = readActionThread;
      }

      @Override
      public void cancel() {
        if (!isCanceled()) {
          cancelStartTimeMs = System.currentTimeMillis();
          cancelStackDump = readActionThread.getStackTrace();
        }
        super.cancel();
      }
    }

    ProgressIndicatorWithCancellationInfo progressIndicator = new ProgressIndicatorWithCancellationInfo(Thread.currentThread());
    long actionStartTimeMs = System.currentTimeMillis();
    boolean successful = ProgressIndicatorUtils.runInReadActionWithWriteActionPriority(runnable, progressIndicator);

    if (!successful) {
      LOG.info("Android Lint read action canceled due to pending write action");

      StackTraceElement[] stackDumpRaw = progressIndicator.cancelStackDump;
      if (stackDumpRaw != null) {

        // If the read action was canceled *after* being started, then the EDT still has to wait
        // for the read action to check for cancellation and throw a ProcessCanceledException.
        // If this takes a while, it will freeze the UI. We want to know about that.
        long currTimeMs = System.currentTimeMillis();
        long cancelTimeMs = currTimeMs - progressIndicator.cancelStartTimeMs;

        // Even if the read action was quick to cancel, we still want to report long-running
        // read actions because those could lead to frequent cancellations or Lint never finishing.
        long actionTimeMs = currTimeMs - actionStartTimeMs;

        // Report both in the same crash report so that one does not get discarded by the crash report rate limiter.
        if (cancelTimeMs > 200 || actionTimeMs > 1000) {
          notifyReadCanceled(stackDumpRaw, cancelTimeMs, actionTimeMs);
        }
      }

      throw new ProcessCanceledException();
    }
  }

  @Override
  public <T> T runReadAction(@NonNull Computable<T> computable) {
    if (myLintResult instanceof LintEditorResult) {
      // Defer to read action implementation for Runnable.
      Ref<T> res = new Ref<>();
      runReadAction(() -> res.set(computable.compute()));
      return res.get();
    }
    else {
      return ApplicationManager.getApplication().runReadAction(computable);
    }
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

  public void setModuleMap(@Nullable Map<com.android.tools.lint.detector.api.Project, Module> moduleMap) {
    myModuleMap = moduleMap;
  }

  @NonNull
  @Override
  public Configuration getConfiguration(@NonNull com.android.tools.lint.detector.api.Project project, @Nullable final LintDriver driver) {
    return getConfigurations().getConfigurationForProject(project, (file, defaultConfiguration) -> createConfiguration(project, defaultConfiguration));
  }

  private Configuration createConfiguration(
    @NonNull com.android.tools.lint.detector.api.Project project,
    @NonNull Configuration defaultConfiguration
  ) {
    LintModelModule model = project.getBuildModule();
    final ConfigurationHierarchy configurations = getConfigurations();
    if (model != null) {
      LintModelLintOptions options = model.getLintOptions();
      return configurations.createLintOptionsConfiguration(
        project, options, false, defaultConfiguration,
        () -> new LintIdeGradleConfiguration(configurations, options, getIssues())
      );
    } else {
      return new LintIdeConfiguration(configurations, project, getIssues());
    }
  }

  @Override
  public void report(@NonNull Context context,
                     @NonNull Issue issue,
                     @NonNull Severity severity,
                     @NonNull Location location,
                     @NonNull String message,
                     @NonNull TextFormat format,
                     @Nullable LintFix extraData) {
    if (myLintResult instanceof LintEditorResult) {
      report((LintEditorResult)myLintResult, context, issue, severity, location, message, format, extraData);
    }
    else if (myLintResult instanceof LintBatchResult) {
      report((LintBatchResult)myLintResult, context, issue, severity, location, message, format, extraData);
    }
    else if (myLintResult instanceof LintIgnoredResult) {
      // Ignore
    }
    else {
      assert false : message;
    }
  }

  public void report(
    @NonNull LintEditorResult lintResult,
    @NonNull Context context,
    @NonNull Issue issue,
    @NonNull Severity severity,
    @NonNull Location location,
    @NonNull String message,
    @NonNull TextFormat format,
    @Nullable LintFix quickfixData) {
    File file = location.getFile();
    VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(file);

    if (lintResult.getMainFile().equals(vFile)) {
      Position start = location.getStart();
      Position end = location.getEnd();

      TextRange textRange = start != null && end != null && start.getOffset() <= end.getOffset()
                            ? new TextRange(start.getOffset(), end.getOffset())
                            : TextRange.EMPTY_RANGE;

      Severity configuredSeverity = severity != issue.getDefaultSeverity() ? severity : null;
      message = format.convertTo(message, RAW);
      lintResult.getProblems().add(new LintProblemData(issue, message, textRange, configuredSeverity, quickfixData));
    }

    Location secondary = location.getSecondary();
    if (secondary != null && lintResult.getMainFile().equals(LocalFileSystem.getInstance().findFileByIoFile(secondary.getFile()))) {
      reportSecondary(context, issue, severity, location, message, format, quickfixData);
    }
  }

  public void report(
    @NonNull LintBatchResult state,
    @NonNull Context context,
    @NonNull Issue issue,
    @NonNull Severity severity,
    @NonNull Location location,
    @NonNull String message,
    @NonNull TextFormat format,
    @Nullable LintFix quickfixData) {

    AnalysisScope scope = state.getScope();
    Map<Issue, Map<File, List<LintProblemData>>> myProblemMap = state.getProblemMap();
    File file = location.getFile();
    VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(file);

    boolean inScope = vFile != null && scope.contains(vFile);
    // In analysis batch mode, the AnalysisScope contains a specific set of virtual
    // files, not directories, so any errors reported against a directory will not
    // be considered part of the scope and therefore won't be reported. Correct
    // for this.
    if (!inScope && vFile != null && vFile.isDirectory()) {
      if (scope.getScopeType() == AnalysisScope.PROJECT) {
        inScope = true;
      }
      else if (scope.getScopeType() == AnalysisScope.MODULE ||
               scope.getScopeType() == AnalysisScope.MODULES) {
        final Module module = findModuleForLintProject(myProject, context.getProject());
        if (module != null && scope.containsModule(module)) {
          inScope = true;
        }
      }
    }

    if (inScope) {
      file = new File(PathUtil.getCanonicalPath(file.getPath()));

      Map<File, List<LintProblemData>> file2ProblemList = myProblemMap.get(issue);
      if (file2ProblemList == null) {
        file2ProblemList = new HashMap<>();
        myProblemMap.put(issue, file2ProblemList);
      }

      List<LintProblemData> problemList = file2ProblemList.get(file);
      if (problemList == null) {
        problemList = new ArrayList<>();
        file2ProblemList.put(file, problemList);
      }

      TextRange textRange = TextRange.EMPTY_RANGE;

      Position start = location.getStart();
      Position end = location.getEnd();
      if (start != null && end != null && start.getOffset() <= end.getOffset()) {
        textRange = new TextRange(start.getOffset(), end.getOffset());
      }
      Severity configuredSeverity = severity != issue.getDefaultSeverity() ? severity : null;
      message = format.convertTo(message, RAW);
      problemList.add(new LintProblemData(issue, message, textRange, configuredSeverity, quickfixData));

      if (location.getSecondary() != null) {
        reportSecondary(context, issue, severity, location, message, format, quickfixData);
      }
    }

    // Ensure third party issue registered
    AndroidLintInspectionBase.getInspectionShortNameByIssue(myProject, issue);
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

  @NonNull
  protected Set<Issue> getIssues() {
    return myLintResult.getIssues();
  }

  @Nullable
  protected Module getModule() {
    return myLintResult.getModule();
  }

  @Nullable
  protected Module getModule(@NonNull com.android.tools.lint.detector.api.Project project) {
    Module module = findModuleForLintProject(getIdeProject(), project);
    if (module != null) {
      return module;
    }
    return getModule();
  }

  @Override
  public void log(@NonNull Severity severity, @Nullable Throwable exception, @Nullable String format, @Nullable Object... args) {
    if (severity == Severity.ERROR || severity == Severity.FATAL) {
      if (format != null) {
        LOG.error(String.format(format, args), exception);
      }
      else if (exception != null) {
        LOG.error(exception);
      }
    }
    else if (severity == Severity.WARNING) {
      if (format != null) {
        LOG.warn(String.format(format, args), exception);
      }
      else if (exception != null) {
        LOG.warn(exception);
      }
    }
    else {
      if (format != null) {
        LOG.info(String.format(format, args), exception);
      }
      else if (exception != null) {
        LOG.info(exception);
      }
    }
  }

  @NotNull
  @Override
  public XmlParser getXmlParser() {
    return new DomPsiParser(this);
  }

  @NotNull
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

  @NotNull
  @Override
  public GradleVisitor getGradleVisitor() {
    return new LintIdeGradleVisitor();
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
  public String readFile(@NonNull File file) {
    if (myLintResult instanceof LintEditorResult) {
      return readFile((LintEditorResult)myLintResult, file);
    }

    VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(file);
    if (vFile == null) {
      LOG.debug("Cannot find file " + file.getPath() + " in the VFS");
      return "";
    }

    return runReadAction(() -> {
      PsiFile psiFile = PsiManager.getInstance(myProject).findFile(vFile);
      if (psiFile == null) {
        LOG.info("Cannot find file " + file.getPath() + " in the PSI");
        return null;
      }
      else {
        return psiFile.getText();
      }
    });
  }

  @NotNull
  private String readFile(@NonNull LintEditorResult lintEditorResult, @NonNull File file) {
    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(file);

    if (vFile == null) {
      try {
        return Files.asCharSource(file, Charsets.UTF_8).read();
      }
      catch (IOException ioe) {
        LOG.debug("Cannot find file " + file.getPath() + " in the VFS");
        return "";
      }
    }
    final String content = getFileContent(lintEditorResult, vFile);

    if (content == null) {
      LOG.info("Cannot find file " + file.getPath() + " in the PSI");
      return "";
    }
    return content;
  }

  @Nullable
  private String getFileContent(@NonNull LintEditorResult lintResult, final VirtualFile vFile) {
    if (Objects.equals(lintResult.getMainFile(), vFile)) {
      return lintResult.getMainFileContent();
    }

    return runReadAction(() -> {
      final Module module = lintResult.getModule();
      final Project project = module.getProject();
      final PsiFile psiFile = PsiManager.getInstance(project).findFile(vFile);

      if (psiFile == null) {
        return null;
      }
      final Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);

      if (document != null) {
        final DocumentListener listener = new DocumentListener() {
          @Override
          public void documentChanged(@NotNull DocumentEvent event) {
            lintResult.markDirty();
          }
        };
        document.addDocumentListener(listener, this);
      }
      return psiFile.getText();
    });
  }

  @Override
  public void dispose() {
    myProject = null;
    myModuleMap = null;
  }

  @Nullable
  @Override
  public String getClientDisplayRevision() {
    return ApplicationInfoEx.getInstanceEx().getFullVersion();
  }

  @Nullable
  @Override
  public String getClientRevision() {
    return ApplicationInfoEx.getInstanceEx().getStrictVersion();
  }

  @Nullable private static volatile String ourSystemPath;

  @Nullable
  @Override
  public File getCacheDir(@Nullable String name, boolean create) {
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    final String path = ourSystemPath != null
                        ? ourSystemPath
                        : (ourSystemPath = PathUtil.getCanonicalPath(PathManager.getSystemPath()));
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

  @Nullable
  @Override
  public String getRelativePath(@Nullable File baseFile, @Nullable File file) {
    return FileUtilRt.getRelativePath(baseFile, file);
  }

  protected void notifyReadCanceled(StackTraceElement[] stackDumpRaw, long cancelTimeMs, long actionTimeMs) {
    LOG.info("Android Lint either took too long to run a read action (" + actionTimeMs + "ms),\n" +
             "or took too long to cancel and yield to a pending write action (" + cancelTimeMs + "ms)");
  }

  @NonNull
  @Override
  public List<File> getJavaSourceFolders(@NonNull com.android.tools.lint.detector.api.Project project) {
    Module module = getModule(project);
    if (module == null) {
      module = findModuleForLintProject(myProject, project);
      if (module == null) {
        return Collections.emptyList();
      }
    }
    final VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots(false);
    final List<File> result = new ArrayList<>(sourceRoots.length);

    for (VirtualFile root : sourceRoots) {
      result.add(new File(root.getPath()));
    }
    return result;
  }

  @Override
  public boolean supportsProjectResources() {
    return true;
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
  @NotNull
  public ClassLoader createUrlClassLoader(@NonNull URL[] urls, @NonNull ClassLoader parent) {
    //noinspection SSBasedInspection
    return UrlClassLoader.build()
      .parent(parent)
      .files(Arrays.stream(urls).map(it -> Paths.get(it.getPath())).collect(Collectors.toList()))
      .get();
  }
}
