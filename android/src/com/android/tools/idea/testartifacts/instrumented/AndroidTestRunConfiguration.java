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

import static com.android.tools.idea.testartifacts.instrumented.testsuite.view.OptInBannerViewKt.createConsoleViewWithOptInBanner;
import static com.intellij.codeInsight.AnnotationUtil.CHECK_HIERARCHY;
import static com.intellij.openapi.util.text.StringUtil.getPackageName;
import static com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces;

import com.android.ddmlib.IDevice;
import com.android.ide.common.gradle.model.IdeAndroidArtifact;
import com.android.ide.common.gradle.model.IdeTestOptions;
import com.android.ide.common.gradle.model.IdeVariant;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.run.AndroidLaunchTasksProvider;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.run.ApkProvider;
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.ConsoleProvider;
import com.android.tools.idea.run.GradleAndroidLaunchTasksProvider;
import com.android.tools.idea.run.LaunchOptions;
import com.android.tools.idea.run.ValidationError;
import com.android.tools.idea.run.editor.AndroidRunConfigurationEditor;
import com.android.tools.idea.run.editor.AndroidTestExtraParam;
import com.android.tools.idea.run.editor.AndroidTestExtraParamKt;
import com.android.tools.idea.run.editor.TestRunParameters;
import com.android.tools.idea.run.tasks.AppLaunchTask;
import com.android.tools.idea.run.tasks.LaunchTasksProvider;
import com.android.tools.idea.run.ui.BaseAction;
import com.android.tools.idea.run.util.LaunchStatus;
import com.android.tools.idea.testartifacts.instrumented.configuration.AndroidTestConfiguration;
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.AndroidTestSuiteView;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.JUnitBundle;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.JavaRunConfigurationModule;
import com.intellij.execution.configurations.RefactoringListenerProvider;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.TestRunnerBundle;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackage;
import com.intellij.refactoring.listeners.RefactoringElementAdapter;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import kotlin.sequences.SequencesKt;
import org.jetbrains.android.dom.manifest.Instrumentation;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Run Configuration for "Android Instrumented Tests"
 */
public class AndroidTestRunConfiguration extends AndroidRunConfigurationBase implements RefactoringListenerProvider {
  public static final int TEST_ALL_IN_MODULE = 0;
  public static final int TEST_ALL_IN_PACKAGE = 1;
  public static final int TEST_CLASS = 2;
  public static final int TEST_METHOD = 3;

  /**
   * A default value for instrumentation runner class used in Android Gradle Plugin.
   */
  public static final String DEFAULT_ANDROID_INSTRUMENTATION_RUNNER_CLASS = "android.test.InstrumentationTestRunner";

  public int TESTING_TYPE = TEST_ALL_IN_MODULE;
  @NotNull public String METHOD_NAME = "";
  @NotNull public String CLASS_NAME = "";
  @NotNull public String PACKAGE_NAME = "";

  /**
   * A fully qualified name of an instrumentation runner class to use. If this is an empty string, the value is inferred from the project:
   * 1) If this is gradle project, values in gradle.build file will be used.
   * 2) If this is non-gradle project, the first instrumentation in AndroidManifest of the instrumentation APK (not the application APK)
   * will be used.
   */
  @NotNull public String INSTRUMENTATION_RUNNER_CLASS = "";

  /**
   * An extra instrumentation runner options. If this is an empty string, the value is inferred from the project:
   * 1) If this is gradle project, values in gradle.build file will be used.
   * 2) If this is non-gradle project, no extra options will be set.
   */
  @NotNull public String EXTRA_OPTIONS = "";

  /**
   * If this is set to true, extra options defined in gradle build file will be merged into {@link #EXTRA_OPTIONS} and passed to
   * instrumentation.
   */
  public boolean INCLUDE_GRADLE_EXTRA_OPTIONS = true;

  public AndroidTestRunConfiguration(final Project project, final ConfigurationFactory factory) {
    super(project, factory, true);

    putUserData(BaseAction.SHOW_APPLY_CHANGES_UI, true);
  }

  @Override
  protected Pair<Boolean, String> supportsRunningLibraryProjects(@NotNull AndroidFacet facet) {
    if (!AndroidModel.isRequired(facet)) {
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
    IdeAndroidArtifact testArtifact = androidModel.getSelectedVariant().getAndroidTestArtifact();
    String testTask = testArtifact != null ? testArtifact.getBuildInformation().getAssembleTaskName() : null;
    return new Pair<>(testTask != null, AndroidBundle.message("android.cannot.run.library.project.in.this.buildtype"));
  }

  @Override
  public boolean isGeneratedName() {
    return Objects.equals(getName(), suggestedName());
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
    return TestRunnerBundle.message("all.tests.scope.presentable.text");
  }

  @NotNull
  @Override
  public List<ValidationError> checkConfiguration(@NotNull AndroidFacet facet) {
    return checkConfiguration(facet, AndroidModuleModel.get(facet.getModule()));
  }

  @NotNull
  @VisibleForTesting
  List<ValidationError> checkConfiguration(@NotNull AndroidFacet facet, @Nullable AndroidModuleModel androidModel) {
    List<ValidationError> errors = Lists.newArrayList();

    Module module = facet.getModule();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(module.getProject());
    switch (TESTING_TYPE) {
      case TEST_ALL_IN_PACKAGE:
        final PsiPackage testPackage = facade.findPackage(PACKAGE_NAME);
        if (testPackage == null) {
          errors.add(ValidationError.warning(JUnitBundle.message("package.does.not.exist.error.message", PACKAGE_NAME)));
        }
        break;
      case TEST_CLASS:
        PsiClass testClass = null;
        try {
          testClass =
            getConfigurationModule().checkModuleAndClassName(CLASS_NAME, JUnitBundle.message("no.test.class.specified.error.text"));
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

    final AndroidFacetConfiguration configuration = facet.getConfiguration();
    if (!AndroidModel.isRequired(facet) && !configuration.getState().PACK_TEST_CODE) {
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

    if (androidModel != null) {
      IdeAndroidArtifact testArtifact = androidModel.getArtifactForAndroidTest();
      if (testArtifact == null) {
        IdeVariant selectedVariant = androidModel.getSelectedVariant();
        errors.add(ValidationError.warning("Active build variant \"" + selectedVariant.getName() + "\" does not have a test artifact."));
      }
    }

    return errors;
  }

  @Override
  public boolean isTestConfiguration() {
    return true;
  }

  private static int getTestSourceRootCount(@NotNull Module module) {
    final ModuleRootManager manager = ModuleRootManager.getInstance(module);
    return manager.getSourceRoots(true).length - manager.getSourceRoots(false).length;
  }

  private List<ValidationError> checkTestMethod() {
    JavaRunConfigurationModule configurationModule = getConfigurationModule();
    final PsiClass testClass;
    try {
      testClass = configurationModule.checkModuleAndClassName(CLASS_NAME, JUnitBundle.message("no.test.class.specified.error.text"));
    }
    catch (RuntimeConfigurationException e) {
      // We can't proceed without a test class.
      return ImmutableList.of(ValidationError.fromException(e));
    }
    List<ValidationError> errors = Lists.newArrayList();
    if (!JUnitUtil.isTestClass(testClass)) {
      errors.add(ValidationError.warning(ExecutionBundle.message("class.isnt.test.class.error.message", CLASS_NAME)));
    }
    if (isEmptyOrSpaces(METHOD_NAME)) {
      errors.add(ValidationError.fatal(JUnitBundle.message("method.name.not.specified.error.message")));
    }
    final JUnitUtil.TestMethodFilter filter = new JUnitUtil.TestMethodFilter(testClass);
    boolean found = false;
    boolean testAnnotated = false;
    for (final PsiMethod method : testClass.findMethodsByName(METHOD_NAME, true)) {
      if (filter.value(method)) found = true;
      if (JUnitUtil.isTestAnnotated(method)) testAnnotated = true;
    }
    if (!found) {
      errors.add(ValidationError.warning(JUnitBundle.message("test.method.doesnt.exist.error.message", METHOD_NAME)));
    }

    if (!AnnotationUtil.isAnnotated(testClass, JUnitUtil.RUN_WITH, CHECK_HIERARCHY) && !testAnnotated) {
      try {
        final PsiClass testCaseClass = JUnitUtil.getTestCaseClass(configurationModule.getModule());
        if (!testClass.isInheritor(testCaseClass, true)) {
          errors.add(ValidationError.fatal(JUnitBundle.message("class.isnt.inheritor.of.testcase.error.message", CLASS_NAME)));
        }
      }
      catch (JUnitUtil.NoJUnitException e) {
        errors.add(ValidationError.warning(ExecutionBundle.message(AndroidBundle.message("cannot.find.testcase.error"))));
      }
    }
    return errors;
  }

  @Override
  protected LaunchTasksProvider createLaunchTasksProvider(@NotNull ExecutionEnvironment env,
                                                       @NotNull AndroidFacet facet,
                                                       @NotNull ApplicationIdProvider applicationIdProvider,
                                                       @NotNull ApkProvider apkProvider,
                                                       @NotNull LaunchOptions launchOptions) {
    if (StudioFlags.UTP_INSTRUMENTATION_TESTING.get()) {
      return new GradleAndroidLaunchTasksProvider(this, env, facet, applicationIdProvider, launchOptions,
                                                  TESTING_TYPE, PACKAGE_NAME, CLASS_NAME, METHOD_NAME);
    } else {
      return new AndroidLaunchTasksProvider(this, env, facet, applicationIdProvider, apkProvider, launchOptions);
    }
  }

  @NotNull
  @Override
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new AndroidRunConfigurationEditor<>(
      getProject(),
      facet -> facet != null && supportsRunningLibraryProjects(facet).getFirst(),
      this,
      false,
      moduleSelector -> new TestRunParameters(getProject(), moduleSelector));
  }

  @NotNull
  @Override
  protected ConsoleProvider getConsoleProvider(boolean runOnMultipleDevices) {
    return (parent, handler, executor) -> {
      final ConsoleView consoleView;
      if ((runOnMultipleDevices || AndroidTestConfiguration.getInstance().getALWAYS_DISPLAY_RESULTS_IN_THE_TEST_MATRIX())
          && StudioFlags.MULTIDEVICE_INSTRUMENTATION_TESTS.get()
          && (executor.getId().equals(DefaultRunExecutor.EXECUTOR_ID)
              || executor.getId().equals(DefaultDebugExecutor.EXECUTOR_ID))) {
        consoleView = new AndroidTestSuiteView(parent, getProject(), getConfigurationModule().getModule(),
                                               executor.getToolWindowId(), this);
        consoleView.attachToProcess(handler);
      } else {
        AndroidTestConsoleProperties properties = new AndroidTestConsoleProperties(this, executor);
        consoleView = createConsoleViewWithOptInBanner(
          SMTestRunnerConnectionUtil.createAndAttachConsole("Android", handler, properties));
        Disposer.register(parent, consoleView);
      }
      return consoleView;
    };
  }

  @Override
  protected boolean supportMultipleDevices() {
    return false;
  }

  @Nullable
  @Override
  protected AppLaunchTask getApplicationLaunchTask(@NotNull ApplicationIdProvider applicationIdProvider,
                                                   @NotNull AndroidFacet facet,
                                                   @NotNull String contributorsAmStartOptions,
                                                   boolean waitForDebugger,
                                                   @NotNull LaunchStatus launchStatus,
                                                   @NotNull ApkProvider apkProvider,
                                                   @NotNull ConsolePrinter consolePrinter,
                                                   @NotNull IDevice device) {
    String runner = INSTRUMENTATION_RUNNER_CLASS;
    if (isEmptyOrSpaces(runner)) {
      runner = getDefaultInstrumentationRunner(facet);
    }
    if (isEmptyOrSpaces(runner)) {
      launchStatus.terminateLaunch("Unable to determine instrumentation runner", true);
      return null;
    }

    String instrumentationOptions = Joiner.on(" ").join(getExtraInstrumentationOptions(facet), getInstrumentationOptions(facet));

    String testAppId;
    try {
      testAppId = applicationIdProvider.getTestPackageName();
      if (testAppId == null) {
        launchStatus.terminateLaunch("Unable to determine test package name", true);
        return null;
      }
    }
    catch (ApkProvisionException e) {
      launchStatus.terminateLaunch("Unable to determine test package name", true);
      return null;
    }

    AndroidModuleModel moduleModel = AndroidModuleModel.get(facet);
    IdeAndroidArtifact testArtifact = null;
    if (moduleModel != null) {
      testArtifact = moduleModel.getArtifactForAndroidTest();
    }

    switch (TESTING_TYPE) {
      case TEST_ALL_IN_MODULE:
        return AndroidTestApplicationLaunchTask.allInModuleTest(runner,
                                                                testAppId,
                                                                waitForDebugger,
                                                                instrumentationOptions,
                                                                testArtifact,
                                                                launchStatus.getProcessHandler(),
                                                                consolePrinter,
                                                                device);

      case TEST_ALL_IN_PACKAGE:
        return AndroidTestApplicationLaunchTask.allInPackageTest(runner,
                                                                 testAppId,
                                                                 waitForDebugger,
                                                                 instrumentationOptions,
                                                                 testArtifact,
                                                                 launchStatus.getProcessHandler(),
                                                                 consolePrinter,
                                                                 device,
                                                                 PACKAGE_NAME);

      case TEST_CLASS:
        return AndroidTestApplicationLaunchTask.classTest(runner,
                                                          testAppId,
                                                          waitForDebugger,
                                                          instrumentationOptions,
                                                          testArtifact,
                                                          launchStatus.getProcessHandler(),
                                                          consolePrinter,
                                                          device,
                                                          CLASS_NAME);

      case TEST_METHOD:
        return AndroidTestApplicationLaunchTask.methodTest(runner,
                                                           testAppId,
                                                           waitForDebugger,
                                                           instrumentationOptions,
                                                           testArtifact,
                                                           launchStatus.getProcessHandler(),
                                                           consolePrinter,
                                                           device,
                                                           CLASS_NAME,
                                                           METHOD_NAME);

      default:
        launchStatus.terminateLaunch("Unknown testing type is selected", true);
        return null;
    }
  }

  /**
   * Returns the qualified class name of the default instrumentation runner class of the given facet.
   */
  @NotNull
  public static String getDefaultInstrumentationRunner(@Nullable AndroidFacet facet) {
    if (facet == null) {
      return DEFAULT_ANDROID_INSTRUMENTATION_RUNNER_CLASS;
    }
    AndroidModuleModel androidModel = AndroidModuleModel.get(facet);
    if (androidModel != null) {
      // When a project is a gradle based project, instrumentation runner is always specified
      // by AGP DSL (even if you have androidTest/AndroidManifest.xml with instrumentation tag,
      // these values are always overwritten by AGP).
      String runner = androidModel.getSelectedVariant().getTestInstrumentationRunner();
      if (isEmptyOrSpaces(runner)) {
        return DEFAULT_ANDROID_INSTRUMENTATION_RUNNER_CLASS;
      }
      else {
        return runner;
      }
    }
    else {
      // In non-Gradle project, instrumentation runner must be defined in AndroidManifest.
      return DumbService.getInstance(facet.getModule().getProject()).runReadActionInSmartMode(() -> {
        Manifest manifest = Manifest.getMainManifest(facet);
        if (manifest == null) {
          return DEFAULT_ANDROID_INSTRUMENTATION_RUNNER_CLASS;
        }
        for (Instrumentation instrumentation : manifest.getInstrumentations()) {
          if (instrumentation != null) {
            PsiClass instrumentationClass = instrumentation.getInstrumentationClass().getValue();
            if (instrumentationClass != null) {
              return instrumentationClass.getQualifiedName();
            }
          }
        }
        return DEFAULT_ANDROID_INSTRUMENTATION_RUNNER_CLASS;
      });
    }
  }

  /**
   * Returns the extra options string to be passed to the instrumentation runner.
   *
   * @see #EXTRA_OPTIONS
   */
  @NotNull
  public String getExtraInstrumentationOptions(@Nullable AndroidFacet facet) {
    Collection<AndroidTestExtraParam> extraParams;

    if (INCLUDE_GRADLE_EXTRA_OPTIONS) {
      extraParams = AndroidTestExtraParamKt.merge(AndroidTestExtraParam.parseFromString(EXTRA_OPTIONS),
                                                  AndroidTestExtraParamKt.getAndroidTestExtraParams(facet));
    }
    else {
      extraParams = SequencesKt.toList(AndroidTestExtraParam.parseFromString(EXTRA_OPTIONS));
    }

    return extraParams.stream()
      .map(param -> "-e " + param.getNAME() + " " + param.getVALUE())
      .collect(Collectors.joining(" "));
  }

  /**
   * Retrieves instrumentation options from the given facet. Extra instrumentation options are not included.
   *
   * @param facet a facet to retrieve instrumentation options
   * @return instrumentation options string. All instrumentation options specified by the facet are concatenated by a single space.
   */
  @NotNull
  public String getInstrumentationOptions(@Nullable AndroidFacet facet) {
    ImmutableList.Builder<String> builder = new ImmutableList.Builder<>();
    boolean isAnimationDisabled = Optional.ofNullable(facet)
      .map(AndroidModuleModel::get)
      .map(AndroidModuleModel::getArtifactForAndroidTest)
      .map(IdeAndroidArtifact::getTestOptions)
      .map(IdeTestOptions::getAnimationsDisabled)
      .orElse(false);
    if (isAnimationDisabled) {
      builder.add("--no-window-animation");
    }
    return Joiner.on(" ").join(builder.build());
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
    LaunchOptions.Builder builder = super.getLaunchOptions();
    // `am instrument` force stops the target package anyway, so there's no need for an explicit `am force-stop` for every APK involved.
    builder.setForceStopRunningApp(false);
    builder.setPmInstallOptions(device -> {
      // -t: Allow test APKs to be installed.
      // -g: Grant all permissions listed in the app manifest. (Introduced at Android 6.0).
      if (device.isPresent() && device.get().getVersion().getApiLevel() >= 23) {
        return "-t -g";
      } else {
        return "-t";
      }
    });
    return builder;
  }

  /**
   * Returns a test execution option specified by the facet or HOST is returned by default.
   *
   * @param facet Android facet to retrieve test execution option
   */
  public IdeTestOptions.Execution getTestExecution(@Nullable AndroidFacet facet) {
    return Optional.ofNullable(facet)
      .map(f -> AndroidModuleModel.get(f))
      .map(model -> model.getArtifactForAndroidTest())
      .map(testArtifact -> testArtifact.getTestOptions())
      .map(testOptions -> testOptions.getExecution())
      .orElse(IdeTestOptions.Execution.HOST);
  }
}
