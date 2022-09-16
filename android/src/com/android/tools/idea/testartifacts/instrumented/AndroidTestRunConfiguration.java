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

import static com.android.tools.idea.projectsystem.ProjectSystemUtil.getModuleSystem;
import static com.intellij.codeInsight.AnnotationUtil.CHECK_HIERARCHY;
import static com.intellij.openapi.util.text.StringUtil.getPackageName;
import static com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.model.TestExecutionOption;
import com.android.tools.idea.model.TestOptions;
import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.run.ApkProvider;
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.ConsoleProvider;
import com.android.tools.idea.run.LaunchOptions;
import com.android.tools.idea.run.ValidationError;
import com.android.tools.idea.run.editor.AndroidRunConfigurationEditor;
import com.android.tools.idea.run.editor.AndroidTestExtraParam;
import com.android.tools.idea.run.editor.DeployTargetProvider;
import com.android.tools.idea.run.editor.TestRunParameters;
import com.android.tools.idea.run.tasks.AppLaunchTask;
import com.android.tools.idea.run.util.LaunchStatus;
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.AndroidTestSuiteView;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.wireless.android.sdk.stats.TestLibraries;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.JUnitBundle;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.JavaRunConfigurationModule;
import com.intellij.execution.configurations.RefactoringListenerProvider;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.testframework.TestRunnerBundle;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationQuickFix;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackage;
import com.intellij.refactoring.listeners.RefactoringElementAdapter;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import java.util.ArrayList;
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
   * A regular expression to filter test cases to be executed.
   * This param is passed to a test runner along with an instrumentation flag "-e tests_regex".
   * This param is only used when the testing type is TEST_ALL_IN_MODULE, otherwise ignored.
   */
  @NotNull public String TEST_NAME_REGEX = "";

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

  /*
   * Configurations for Emulator Snapshot for Test Failures (a.k.a Android Test Retention, Icebox).
   *
   * Can be set to yes, no or use gradle settings.
   */
  public EnableRetention RETENTION_ENABLED = EnableRetention.NO;
  /*
   * Maximum number of snapshots for Emulator Snapshot for Test Failures.
   */
  public int RETENTION_MAX_SNAPSHOTS = 2;
  /*
   * Compress snapshots or not for Emulator Snapshot for Test Failures.
   */
  public boolean RETENTION_COMPRESS_SNAPSHOTS = false;

  public AndroidTestRunConfiguration(final Project project, final ConfigurationFactory factory) {
    super(project, factory, true);
  }

  @Override
  public @NotNull List<DeployTargetProvider> getApplicableDeployTargetProviders() {
    return getDeployTargetContext().getApplicableDeployTargetProviders(true);
  }

  @Override
  protected Pair<Boolean, String> supportsRunningLibraryProjects(@NotNull AndroidFacet facet) {
    if (!AndroidModel.isRequired(facet)) {
      // Non Gradle projects always require an application
      return Pair.create(Boolean.FALSE, AndroidBundle.message("android.cannot.run.library.project.error"));
    }

    // TODO: Resolve direct AndroidGradleModel dep (b/22596984)
    AndroidModel androidModel = AndroidModel.get(facet);
    if (androidModel == null) {
      return Pair.create(Boolean.FALSE, AndroidBundle.message("android.cannot.run.library.project.error"));
    }
    return new Pair<>(Boolean.TRUE, null);
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
      return JavaExecutionUtil.getShortClassName(CLASS_NAME);
    }
    else if (TESTING_TYPE == TEST_METHOD) {
      return METHOD_NAME + "()";
    }
    else if (TESTING_TYPE == TEST_ALL_IN_MODULE && isNotEmpty(TEST_NAME_REGEX)) {
      if (isNotEmpty(METHOD_NAME)) {
        return METHOD_NAME + "()";
      }
      return TEST_NAME_REGEX;
    }
    return TestRunnerBundle.message("all.tests.scope.presentable.text");
  }

  @NotNull
  @Override
  public List<ValidationError> checkConfiguration(@NotNull AndroidFacet facet) {
    List<ValidationError> errors = new ArrayList<>();

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
        ConfigurationQuickFix quickFix = (dataContext) -> {
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
  public boolean isTestConfiguration() {
    return true;
  }

  @NotNull
  @Override
  public TestCompileType getTestCompileMode() {
    return TestCompileType.ANDROID_TESTS;
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
    List<ValidationError> errors = new ArrayList<>();
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
        final PsiClass testCaseClass = JUnitUtil.getTestCaseClass(getConfigurationModule().getAndroidTestModule());
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

  @NotNull
  @Override
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new AndroidRunConfigurationEditor<>(
      getProject(),
      facet -> facet != null && supportsRunningLibraryProjects(facet).getFirst(),
      this,
      false,
      true,
      moduleSelector -> new TestRunParameters(getProject(), moduleSelector));
  }

  @NotNull
  @Override
  protected ConsoleProvider getConsoleProvider(boolean runOnMultipleDevices) {
    return (parent, handler, executor) -> {
      final ConsoleView consoleView = new AndroidTestSuiteView(parent, getProject(), getConfigurationModule().getAndroidTestModule(),
                                                               executor.getToolWindowId(), this);
      consoleView.attachToProcess(handler);
      return consoleView;
    };
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
    @Nullable AndroidModel androidModel = AndroidModel.get(facet);
    @Nullable TestOptions testOptions = androidModel != null ? androidModel.getTestOptions() : null;
    String instrumentationOptions = Joiner.on(" ").join(getExtraInstrumentationOptions(facet), getInstrumentationOptions(testOptions));

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

    AndroidModuleSystem moduleSystem = getModuleSystem(facet);
    TestLibraries testLibrariesInUse = moduleSystem.getTestLibrariesInUse();
    TestExecutionOption testExecutionOption = testOptions != null ? testOptions.getExecutionOption() : null;
    switch (TESTING_TYPE) {
      case TEST_ALL_IN_MODULE:
        return AndroidTestApplicationLaunchTask.allInModuleTest(runner,
                                                                testAppId,
                                                                waitForDebugger,
                                                                instrumentationOptions,
                                                                testLibrariesInUse,
                                                                testExecutionOption,
                                                                launchStatus.getProcessHandler(),
                                                                consolePrinter,
                                                                device);

      case TEST_ALL_IN_PACKAGE:
        return AndroidTestApplicationLaunchTask.allInPackageTest(runner,
                                                                 testAppId,
                                                                 waitForDebugger,
                                                                 instrumentationOptions,
                                                                 testLibrariesInUse,
                                                                 testExecutionOption,
                                                                 launchStatus.getProcessHandler(),
                                                                 consolePrinter,
                                                                 device,
                                                                 PACKAGE_NAME);

      case TEST_CLASS:
        return AndroidTestApplicationLaunchTask.classTest(runner,
                                                          testAppId,
                                                          waitForDebugger,
                                                          instrumentationOptions,
                                                          testLibrariesInUse,
                                                          testExecutionOption,
                                                          launchStatus.getProcessHandler(),
                                                          consolePrinter,
                                                          device,
                                                          CLASS_NAME);

      case TEST_METHOD:
        return AndroidTestApplicationLaunchTask.methodTest(runner,
                                                           testAppId,
                                                           waitForDebugger,
                                                           instrumentationOptions,
                                                           testLibrariesInUse,
                                                           testExecutionOption,
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
    AndroidModel androidModel = AndroidModel.get(facet);
    if (androidModel != null) {
      // When a project is a gradle based project, instrumentation runner is always specified
      // by AGP DSL (even if you have androidTest/AndroidManifest.xml with instrumentation tag,
      // these values are always overwritten by AGP).
      String runner = androidModel.getTestOptions().getInstrumentationRunner();
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

    extraParams = SequencesKt.toList(AndroidTestExtraParam.parseFromString(EXTRA_OPTIONS));

    return extraParams.stream()
      .map(param -> "-e " + param.getNAME() + " " + param.getVALUE())
      .collect(Collectors.joining(" "));
  }

  /**
   * Retrieves instrumentation options from the given facet. Extra instrumentation options are not included.
   *
   * @return instrumentation options string. All instrumentation options specified by the facet are concatenated by a single space.
   */
  @NotNull
  public String getInstrumentationOptions(@Nullable TestOptions testOptions) {
    ImmutableList.Builder<String> builder = new ImmutableList.Builder<>();
    boolean isAnimationDisabled = testOptions != null ? testOptions.getAnimationsDisabled() : false;
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
  public TestExecutionOption getTestExecutionOption(@Nullable AndroidFacet facet) {
    return Optional.ofNullable(facet)
      .map(AndroidModel::get)
      .map(AndroidModel::getTestOptions)
      .map(TestOptions::getExecutionOption)
      .orElse(TestExecutionOption.HOST);
  }
}
