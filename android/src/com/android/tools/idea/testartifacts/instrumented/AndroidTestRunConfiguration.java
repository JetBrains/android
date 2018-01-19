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

package com.android.tools.idea.testartifacts.instrumented;

import com.android.annotations.VisibleForTesting;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.TestOptions.Execution;
import com.android.builder.model.Variant;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.testrunner.OnDeviceOrchestratorRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ide.common.gradle.model.IdeAndroidArtifact;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.run.*;
import com.android.tools.idea.run.editor.AndroidRunConfigurationEditor;
import com.android.tools.idea.run.editor.TestRunParameters;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.android.tools.idea.run.util.LaunchStatus;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.configurations.*;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.refactoring.listeners.RefactoringElementAdapter;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import org.jetbrains.android.dom.manifest.Instrumentation;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.openapi.util.text.StringUtil.getPackageName;

public class AndroidTestRunConfiguration extends AndroidRunConfigurationBase implements RefactoringListenerProvider {
  private static final Logger LOG = Logger.getInstance(AndroidTestRunConfiguration.class);

  public static final int TEST_ALL_IN_MODULE = 0;
  public static final int TEST_ALL_IN_PACKAGE = 1;
  public static final int TEST_CLASS = 2;
  public static final int TEST_METHOD = 3;

  public int TESTING_TYPE = TEST_ALL_IN_MODULE;
  public String INSTRUMENTATION_RUNNER_CLASS = "";

  public String METHOD_NAME = "";
  public String CLASS_NAME = "";
  public String PACKAGE_NAME = "";
  public String EXTRA_OPTIONS = "";

  public AndroidTestRunConfiguration(final Project project, final ConfigurationFactory factory) {
    super(project, factory, true);
  }

  @Override
  protected Pair<Boolean, String> supportsRunningLibraryProjects(@NotNull AndroidFacet facet) {
    if (!facet.requiresAndroidModel()) {
      // Non Gradle projects always require an application
      return Pair.create(Boolean.FALSE, AndroidBundle.message("android.cannot.run.library.project.error"));
    }

    // TODO: Resolve direct AndroidGradleModel dep (b/22596984)
    AndroidModuleModel androidModel = AndroidModuleModel.get(facet);
    if (androidModel == null) {
      return Pair.create(Boolean.FALSE, AndroidBundle.message("android.cannot.run.library.project.error"));
    }

    // Gradle only supports testing against a single build type (which could be anything, but is "debug" build type by default)
    // Currently, the only information the model exports that we can use to detect whether the current build type
    // is testable is by looking at the test task name and checking whether it is null.
    AndroidArtifact testArtifact = androidModel.getSelectedVariant().getAndroidTestArtifact();
    String testTask = testArtifact != null ? testArtifact.getAssembleTaskName() : null;
    return new Pair<>(testTask != null, AndroidBundle.message("android.cannot.run.library.project.in.this.buildtype"));
  }

  @Override
  public boolean isGeneratedName() {
    final String name = getName();

    if ((TESTING_TYPE == TEST_CLASS || TESTING_TYPE == TEST_METHOD) &&
        (CLASS_NAME == null || CLASS_NAME.isEmpty())) {
      return JavaExecutionUtil.isNewName(name);
    }
    if (TESTING_TYPE == TEST_METHOD &&
        (METHOD_NAME == null || METHOD_NAME.isEmpty())) {
      return JavaExecutionUtil.isNewName(name);
    }
    return Comparing.equal(name, suggestedName());
  }

  @Override
  public String suggestedName() {
    if (TESTING_TYPE == TEST_ALL_IN_PACKAGE) {
      return ExecutionBundle.message("test.in.scope.presentable.text", PACKAGE_NAME);
    }
    else if (TESTING_TYPE == TEST_CLASS) {
      return ProgramRunnerUtil.shortenName(JavaExecutionUtil.getShortClassName(CLASS_NAME), 0);
    }
    else if (TESTING_TYPE == TEST_METHOD) {
      return ProgramRunnerUtil.shortenName(METHOD_NAME, 2) + "()";
    }
    return ExecutionBundle.message("all.tests.scope.presentable.text");
  }

  @NotNull
  @Override
  public List<ValidationError> checkConfiguration(@NotNull AndroidFacet facet) {
    List<ValidationError> errors = Lists.newArrayList();

    Module module = facet.getModule();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(module.getProject());
    switch (TESTING_TYPE) {
      case TEST_ALL_IN_PACKAGE:
        final PsiPackage testPackage = facade.findPackage(PACKAGE_NAME);
        if (testPackage == null) {
          errors.add(ValidationError.warning(ExecutionBundle.message("package.does.not.exist.error.message", PACKAGE_NAME)));
        }
        break;
      case TEST_CLASS:
        PsiClass testClass = null;
        try {
          testClass =
            getConfigurationModule().checkModuleAndClassName(CLASS_NAME, ExecutionBundle.message("no.test.class.specified.error.text"));
        }
        catch (RuntimeConfigurationException e) {
          errors.add(ValidationError.fromException(e));
        }
        if (testClass != null && !JUnitUtil.isTestClass(testClass)) {
          errors.add(ValidationError.warning(ExecutionBundle.message("class.isnt.test.class.error.message", CLASS_NAME)));
        }
        break;
      case TEST_METHOD:
        errors.addAll(checkTestMethod());
        break;
    }
    if (GradleProjectInfo.getInstance(getProject()).isBuildWithGradle() && !INSTRUMENTATION_RUNNER_CLASS.isEmpty()) {
      if (facade.findClass(INSTRUMENTATION_RUNNER_CLASS, module.getModuleWithDependenciesAndLibrariesScope(true)) == null) {
        errors.add(ValidationError.fatal(AndroidBundle.message("instrumentation.runner.class.not.specified.error")));
      }
    }

    final AndroidFacetConfiguration configuration = facet.getConfiguration();
    if (!facet.requiresAndroidModel() && !configuration.getState().PACK_TEST_CODE) {
      final int count = getTestSourceRootCount(module);
      if (count > 0) {
        final String shortMessage = "Test code not included into APK";
        final String fixMessage = "Code and resources under test source " + (count > 1 ? "roots" : "root") +
                                  " aren't included into debug APK.\nWould you like to include them and recompile " +
                                  module.getName() + " module?" + "\n(You may change this option in Android facet settings later)";
        Runnable quickFix = () -> {
          final int result =
            Messages.showYesNoCancelDialog(getProject(), fixMessage, shortMessage, Messages.getQuestionIcon());
          if (result == Messages.YES) {
            configuration.getState().PACK_TEST_CODE = true;
          }
        };
        errors.add(ValidationError.fatal(shortMessage, quickFix));
      }
    }

    return errors;
  }

  @Override
  @NotNull
  protected ApkProvider getApkProvider(@NotNull AndroidFacet facet, @NotNull ApplicationIdProvider applicationIdProvider) {
    if (facet.getConfiguration().getModel() != null && facet.getConfiguration().getModel() instanceof AndroidModuleModel) {
      return new GradleApkProvider(facet, applicationIdProvider, myOutputProvider, true);
    }
    return new NonGradleApkProvider(facet, applicationIdProvider, null);
  }

  private static int getTestSourceRootCount(@NotNull Module module) {
    final ModuleRootManager manager = ModuleRootManager.getInstance(module);
    return manager.getSourceRoots(true).length - manager.getSourceRoots(false).length;
  }

  private List<ValidationError> checkTestMethod() {
    JavaRunConfigurationModule configurationModule = getConfigurationModule();
    final PsiClass testClass;
    try {
      testClass = configurationModule.checkModuleAndClassName(CLASS_NAME, ExecutionBundle.message("no.test.class.specified.error.text"));
    }
    catch (RuntimeConfigurationException e) {
      // We can't proceed without a test class.
      return ImmutableList.of(ValidationError.fromException(e));
    }
    List<ValidationError> errors = Lists.newArrayList();
    if (!JUnitUtil.isTestClass(testClass)) {
      errors.add(ValidationError.warning(ExecutionBundle.message("class.isnt.test.class.error.message", CLASS_NAME)));
    }
    if (METHOD_NAME == null || METHOD_NAME.trim().isEmpty()) {
      errors.add(ValidationError.fatal(ExecutionBundle.message("method.name.not.specified.error.message")));
    }
    final JUnitUtil.TestMethodFilter filter = new JUnitUtil.TestMethodFilter(testClass);
    boolean found = false;
    boolean testAnnotated = false;
    for (final PsiMethod method : testClass.findMethodsByName(METHOD_NAME, true)) {
      if (filter.value(method)) found = true;
      if (JUnitUtil.isTestAnnotated(method)) testAnnotated = true;
    }
    if (!found) {
      errors.add(ValidationError.warning(ExecutionBundle.message("test.method.doesnt.exist.error.message", METHOD_NAME)));
    }

    if (!AnnotationUtil.isAnnotated(testClass, JUnitUtil.RUN_WITH, true) && !testAnnotated) {
      try {
        final PsiClass testCaseClass = JUnitUtil.getTestCaseClass(configurationModule.getModule());
        if (!testClass.isInheritor(testCaseClass, true)) {
          errors.add(ValidationError.fatal(ExecutionBundle.message("class.isnt.inheritor.of.testcase.error.message", CLASS_NAME)));
        }
      }
      catch (JUnitUtil.NoJUnitException e) {
        errors.add(ValidationError.warning(ExecutionBundle.message(AndroidBundle.message("cannot.find.testcase.error"))));
      }
    }
    return errors;
  }

  @NotNull
  @Override
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    Project project = getProject();
    AndroidRunConfigurationEditor<AndroidTestRunConfiguration> editor =
      new AndroidRunConfigurationEditor<>(project, facet -> facet != null && supportsRunningLibraryProjects(facet).getFirst(), this);
    editor.setConfigurationSpecificEditor(new TestRunParameters(project, editor.getModuleSelector()));
    return editor;
  }

  @NotNull
  @Override
  protected ConsoleProvider getConsoleProvider() {
    return (parent, handler, executor) -> {
      AndroidTestConsoleProperties properties = new AndroidTestConsoleProperties(this, executor);
      ConsoleView consoleView = SMTestRunnerConnectionUtil.createAndAttachConsole("Android", handler, properties);
      Disposer.register(parent, consoleView);
      return consoleView;
    };
  }

  @Override
  protected boolean supportMultipleDevices() {
    return false;
  }

  @Override
  public boolean monitorRemoteProcess() {
    // Tests are run using the "am instrument" command. The output from the shell command is processed by AndroidTestListener,
    // which sends events over to the test UI via GeneralToSMTRunnerEventsConvertor.
    // If the process handler detects that the test process has terminated before all of the output from that shell process
    // makes its way through the AndroidTestListener, the test UI marks the test run as having "Terminated" instead of terminating
    // gracefully once all the test results have been parsed.
    // As a result, we don't want the process handler monitoring the test process at all in this case..
    // See https://code.google.com/p/android/issues/detail?id=201968
    return false;
  }

  @Nullable
  @Override
  protected LaunchTask getApplicationLaunchTask(@NotNull ApplicationIdProvider applicationIdProvider,
                                                @NotNull AndroidFacet facet,
                                                boolean waitForDebugger,
                                                @NotNull LaunchStatus launchStatus) {
    String runner;
    if (StringUtil.isEmpty(INSTRUMENTATION_RUNNER_CLASS) || GradleProjectInfo.getInstance(getProject()).isBuildWithGradle()) {
      runner = findInstrumentationRunner(facet);
    }
    else {
      runner = INSTRUMENTATION_RUNNER_CLASS;
    }
    Map<String, String> runnerArguments = getRunnerArguments(facet);
    String testPackage;
    try {
      testPackage = applicationIdProvider.getTestPackageName();
      if (testPackage == null) {
        launchStatus.terminateLaunch("Unable to determine test package name");
        return null;
      }
    }
    catch (ApkProvisionException e) {
      launchStatus.terminateLaunch("Unable to determine test package name");
      return null;
    }

    AndroidModuleModel moduleModel = AndroidModuleModel.get(facet);
    IdeAndroidArtifact testArtifact = null;
    if (moduleModel != null) {
      testArtifact = moduleModel.getArtifactForAndroidTest();
    }
    return new MyApplicationLaunchTask(runner, testPackage, waitForDebugger, runnerArguments, testArtifact);
  }

  @Nullable
  public static String findInstrumentationRunner(@NotNull AndroidFacet facet) {
    String runner = getRunnerFromManifest(facet);

    // TODO: Resolve direct AndroidGradleModel dep (b/22596984)
    AndroidModuleModel androidModel = AndroidModuleModel.get(facet);
    if (runner == null && androidModel != null) {
      Variant selectedVariant = androidModel.getSelectedVariant();
      String testRunner = selectedVariant.getMergedFlavor().getTestInstrumentationRunner();
      if (testRunner != null) {
        runner = testRunner;
      }
    }

    return runner;
  }

  @NotNull
  public static Map<String, String> getRunnerArguments(@NotNull AndroidFacet facet) {
    AndroidModuleModel androidModel = AndroidModuleModel.get(facet);
    if (androidModel != null) {
      return new HashMap<>(androidModel.getSelectedVariant().getMergedFlavor().getTestInstrumentationRunnerArguments());
    }
    return Collections.emptyMap();
  }

  @Nullable
  private static String getRunnerFromManifest(@NotNull final AndroidFacet facet) {
    if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
      return ApplicationManager.getApplication().runReadAction((Computable<String>)() -> getRunnerFromManifest(facet));
    }

    Manifest manifest = facet.getManifest();
    if (manifest != null) {
      for (Instrumentation instrumentation : manifest.getInstrumentations()) {
        if (instrumentation != null) {
          PsiClass instrumentationClass = instrumentation.getInstrumentationClass().getValue();
          if (instrumentationClass != null) {
            return instrumentationClass.getQualifiedName();
          }
        }
      }
    }
    return null;
  }

  /**
   * Returns a refactoring listener that listens to changes in either the package, class or method names
   * depending on the current {@link #TESTING_TYPE}.
   */
  @Nullable
  @Override
  public RefactoringElementListener getRefactoringElementListener(PsiElement element) {
    if (element instanceof PsiPackage) {
      String pkgName = ((PsiPackage)element).getQualifiedName();
      if (TESTING_TYPE == TEST_ALL_IN_PACKAGE && !Objects.equals(pkgName, PACKAGE_NAME)) {
        // testing package, but the refactored package does not match our package
        return null;
      }
      else if ((TESTING_TYPE != TEST_ALL_IN_PACKAGE) && !Objects.equals(pkgName, getPackageName(CLASS_NAME))) {
        // testing a class or a method, but the refactored package doesn't match our containing package
        return null;
      }

      return new RefactoringElementAdapter() {
        @Override
        protected void elementRenamedOrMoved(@NotNull PsiElement newElement) {
          if (newElement instanceof PsiPackage) {
            String newPkgName = ((PsiPackage)newElement).getQualifiedName();
            if (TESTING_TYPE == TEST_ALL_IN_PACKAGE) {
              PACKAGE_NAME = newPkgName;
            }
            else {
              CLASS_NAME = CLASS_NAME.replace(getPackageName(CLASS_NAME), newPkgName);
            }
          }
        }

        @Override
        public void undoElementMovedOrRenamed(@NotNull PsiElement newElement, @NotNull String oldQualifiedName) {
          if (newElement instanceof PsiPackage) {
            if (TESTING_TYPE == TEST_ALL_IN_PACKAGE) {
              PACKAGE_NAME = oldQualifiedName;
            }
            else {
              CLASS_NAME = CLASS_NAME.replace(getPackageName(CLASS_NAME), oldQualifiedName);
            }
          }
        }
      };
    }
    else if ((TESTING_TYPE == TEST_CLASS || TESTING_TYPE == TEST_METHOD) && element instanceof PsiClass) {
      if (!StringUtil.equals(JavaExecutionUtil.getRuntimeQualifiedName((PsiClass)element), CLASS_NAME)) {
        return null;
      }

      return new RefactoringElementAdapter() {
        @Override
        protected void elementRenamedOrMoved(@NotNull PsiElement newElement) {
          if (newElement instanceof PsiClass) {
            CLASS_NAME = JavaExecutionUtil.getRuntimeQualifiedName((PsiClass)newElement);
          }
        }

        @Override
        public void undoElementMovedOrRenamed(@NotNull PsiElement newElement, @NotNull String oldQualifiedName) {
          if (newElement instanceof PsiClass) {
            CLASS_NAME = oldQualifiedName;
          }
        }
      };
    }
    else if (TESTING_TYPE == TEST_METHOD && element instanceof PsiMethod) {
      PsiMethod psiMethod = (PsiMethod)element;
      if (!StringUtil.equals(psiMethod.getName(), METHOD_NAME)) {
        return null;
      }

      PsiClass psiClass = psiMethod.getContainingClass();
      if (psiClass == null) {
        return null;
      }

      String fqName = psiClass.getQualifiedName();
      if (fqName != null && !StringUtil.equals(fqName, CLASS_NAME)) {
        return null;
      }

      return new RefactoringElementAdapter() {
        @Override
        protected void elementRenamedOrMoved(@NotNull PsiElement newElement) {
          if (newElement instanceof PsiMethod) {
            METHOD_NAME = ((PsiMethod)newElement).getName();
          }
        }

        @Override
        public void undoElementMovedOrRenamed(@NotNull PsiElement newElement, @NotNull String oldQualifiedName) {
          if (newElement instanceof PsiMethod) {
            METHOD_NAME = oldQualifiedName;
          }
        }
      };
    }
    return null;
  }

  @NotNull
  @Override
  protected LaunchOptions.Builder getLaunchOptions() {
    // `am instrument` force stops the target package anyway, so there's no need for an explicit `am force-stop` for every APK involved.
    return super.getLaunchOptions().setForceStopRunningApp(false);
  }

  @VisibleForTesting
  class MyApplicationLaunchTask implements LaunchTask {
    @Nullable private final String myInstrumentationTestRunner;
    @NotNull private final String myTestApplicationId;
    private final boolean myWaitForDebugger;
    @NotNull private final Map<String, String> myInstrumentationTestRunnerArguments;
    @Nullable private final IdeAndroidArtifact myArtifact;

    public MyApplicationLaunchTask(@Nullable String runner,
                                   @NotNull String testPackage,
                                   boolean waitForDebugger,
                                   @NotNull Map<String, String> arguments,
                                   @Nullable IdeAndroidArtifact artifact) {
      myInstrumentationTestRunner = runner;
      myWaitForDebugger = waitForDebugger;
      myTestApplicationId = testPackage;
      myInstrumentationTestRunnerArguments = arguments;
      myArtifact = artifact;
    }

    @NotNull
    @Override
    public String getDescription() {
      return "Launching instrumentation runner";
    }

    @Override
    public int getDuration() {
      return 2;
    }

    @NotNull
    public RemoteAndroidTestRunner getRemoteAndroidTestRunner(@Nullable IdeAndroidArtifact artifact, @NotNull IDevice device) {
      return artifact != null &&
             artifact.getTestOptions() != null &&
             Execution.ANDROID_TEST_ORCHESTRATOR.equals(artifact.getTestOptions().getExecution()) ?
             new OnDeviceOrchestratorRemoteAndroidTestRunner(myTestApplicationId, myInstrumentationTestRunner, device) :
             new RemoteAndroidTestRunner(myTestApplicationId, myInstrumentationTestRunner, device);
    }

    @Override
    public boolean perform(@NotNull IDevice device, @NotNull final LaunchStatus launchStatus, @NotNull final ConsolePrinter printer) {
      printer.stdout("Running tests\n");
      final RemoteAndroidTestRunner runner = getRemoteAndroidTestRunner(myArtifact, device);

      switch (TESTING_TYPE) {
        case TEST_ALL_IN_PACKAGE:
          runner.setTestPackageName(PACKAGE_NAME);
          break;
        case TEST_CLASS:
          runner.setClassName(CLASS_NAME);
          break;
        case TEST_METHOD:
          runner.setMethodName(CLASS_NAME, METHOD_NAME);
          break;
      }
      runner.setDebug(myWaitForDebugger);
      runner.setRunOptions(EXTRA_OPTIONS);

      for (Map.Entry<String, String> entry : myInstrumentationTestRunnerArguments.entrySet()) {
        runner.addInstrumentationArg(entry.getKey(), entry.getValue());
      }

      printer.stdout("$ adb shell " + runner.getAmInstrumentCommand());

      // run in a separate thread as this will block until the tests complete
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        try {
          runner.run(new AndroidTestListener(launchStatus, printer), new UsageTrackerTestRunListener(myArtifact, device));
        }
        catch (Exception e) {
          LOG.info(e);
          printer.stderr("Error: Unexpected exception while running tests: " + e);
        }
      });

      return true;
    }
  }
}
