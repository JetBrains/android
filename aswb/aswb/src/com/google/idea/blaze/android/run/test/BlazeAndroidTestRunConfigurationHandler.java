/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.run.test;

import com.android.tools.idea.execution.common.DeployableToDevice;
import com.android.tools.idea.run.ConsoleProvider;
import com.android.tools.idea.run.ValidationError;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.android.run.ApkBuildStepProvider;
import com.google.idea.blaze.android.run.BazelApplicationProjectContext;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationCommonState;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationHandler;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationValidationUtil;
import com.google.idea.blaze.android.run.LaunchMetrics;
import com.google.idea.blaze.android.run.deployinfo.BlazeApkProvider;
import com.google.idea.blaze.android.run.runner.ApkBuildStep;
import com.google.idea.blaze.android.run.runner.BlazeAndroidDeployAndLaunchStrategy;
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunConfigurationRunner;
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunContext;
import com.google.idea.blaze.android.run.test.BlazeAndroidTestLaunchMethodsProvider.AndroidTestLaunchMethod;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeConfigurationNameBuilder;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationRunner;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResultFetcher;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import java.util.List;
import javax.annotation.Nullable;

/**
 * {@link com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandler} for
 * android_test targets.
 */
public class BlazeAndroidTestRunConfigurationHandler
    implements BlazeAndroidRunConfigurationHandler {
  private final Project project;
  private final BlazeAndroidTestRunConfigurationState configState;

  BlazeAndroidTestRunConfigurationHandler(BlazeCommandRunConfiguration configuration) {
    this.project = configuration.getProject();
    this.configState =
        new BlazeAndroidTestRunConfigurationState(
            Blaze.buildSystemName(configuration.getProject()));
    configuration.putUserData(DeployableToDevice.getKEY(), true);
  }

  @Override
  public BlazeAndroidTestRunConfigurationState getState() {
    return configState;
  }

  @Override
  public BlazeAndroidRunConfigurationCommonState getCommonState() {
    return configState.getCommonState();
  }

  @Override
  public BlazeCommandRunConfigurationRunner createRunner(
      Executor executor, ExecutionEnvironment env) throws ExecutionException {
    Project project = env.getProject();
    BlazeCommandRunConfiguration configuration =
        BlazeAndroidRunConfigurationHandler.getCommandConfig(env);

    BlazeAndroidRunConfigurationValidationUtil.validate(project);
    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();

    ImmutableList<String> blazeFlags =
        configState
            .getCommonState()
            .getExpandedBuildFlags(
                project,
                projectViewSet,
                BlazeCommandName.TEST,
                BlazeInvocationContext.runConfigContext(
                    ExecutorType.fromExecutor(env.getExecutor()), configuration.getType(), false));
    ImmutableList<String> exeFlags =
        ImmutableList.copyOf(
            configState.getCommonState().getExeFlagsState().getFlagsForExternalProcesses());

    // We collect metrics from a few different locations. In order to tie them all
    // together, we create a unique launch id.
    String launchId = LaunchMetrics.newLaunchId();
    String labelString = configuration.getSingleTargetPattern();
    if (labelString == null) {
      throw new ExecutionException("No target pattern specified for configuration.");
    }
    Label label = Label.create(labelString);

    ApkBuildStep buildStep =
        getTestBuildStep(
            project, configState, configuration, blazeFlags, exeFlags, launchId, label);

    var applicationIdProvider = new BlazeAndroidTestApplicationIdProvider(buildStep);
    var apkProvider = BlazeApkProvider.getApkProvider(project, buildStep);
    var applicationProjectContext =
        new BazelApplicationProjectContext(project, applicationIdProvider);

    BlazeTestResultFetcher testResultsHolder = new BlazeTestResultFetcher();

    ConsoleProvider consoleProvider =
        switch (configState.getLaunchMethod()) {
          case MOBILE_INSTALL, NON_BLAZE -> new AitIdeTestConsoleProvider(configuration, configState);
          case BLAZE_TEST -> new AitBlazeTestConsoleProvider(
              project, configuration, testResultsHolder);
          default -> throw new IllegalStateException(
              "Unsupported launch method " + configState.getLaunchMethod());
        };

    BlazeAndroidRunContext runContext = new BlazeAndroidRunContext(
        consoleProvider,
        buildStep,
        applicationIdProvider,
        apkProvider,
        applicationProjectContext,
        env.getExecutor(),
        null
    );

    BlazeAndroidDeployAndLaunchStrategy launchStrategy = new AndroidTestDeployAndLaunchStrategy(
        project,
        runContext,
        configState,
        label,
        blazeFlags,
        testResultsHolder
    );

    LaunchMetrics.logTestLaunch(
        launchId, configState.getLaunchMethod().name(), env.getExecutor().getId());

    return new BlazeAndroidRunConfigurationRunner(runContext, launchStrategy, configuration);
  }

  private static ApkBuildStep getTestBuildStep(
      Project project,
      BlazeAndroidTestRunConfigurationState configState,
      BlazeCommandRunConfiguration configuration,
      ImmutableList<String> blazeFlags,
      ImmutableList<String> exeFlags,
      String launchId,
      Label label)
    throws ExecutionException {
    boolean useMobileInstall =
      AndroidTestLaunchMethod.MOBILE_INSTALL.equals(configState.getLaunchMethod());
    return ApkBuildStepProvider.getInstance(Blaze.getBuildSystemName(project))
      .getAitBuildStep(
        project,
        useMobileInstall,
        /* nativeDebuggingEnabled= */ true,
        label,
        blazeFlags,
        exeFlags,
        launchId);
  }

  @Override
  public final void checkConfiguration() throws RuntimeConfigurationException {
    BlazeAndroidRunConfigurationValidationUtil.throwTopConfigurationError(validate());
  }

  /**
   * We collect errors rather than throwing to avoid missing fatal errors by exiting early for a
   * warning. We use a separate method for the collection so the compiler prevents us from
   * accidentally throwing.
   */
  private List<ValidationError> validate() {
    List<ValidationError> errors = Lists.newArrayList();
    errors.addAll(BlazeAndroidRunConfigurationValidationUtil.validateWorkspaceModule(project));
    errors.addAll(configState.validate(project));
    return errors;
  }

  @Override
  @Nullable
  public String suggestedName(BlazeCommandRunConfiguration configuration) {
    String target = configuration.getSingleTargetPattern();
    if (target == null) {
      return null;
    }
    BlazeConfigurationNameBuilder nameBuilder = new BlazeConfigurationNameBuilder(configuration);

    boolean isClassTest =
        configState.getTestingType() == BlazeAndroidTestRunConfigurationState.TEST_CLASS;
    boolean isMethodTest =
        configState.getTestingType() == BlazeAndroidTestRunConfigurationState.TEST_METHOD;
    if ((isClassTest || isMethodTest) && configState.getClassName() != null) {
      // Get the class name without the package.
      String className = JavaExecutionUtil.getPresentableClassName(configState.getClassName());
      if (className != null) {
        String targetString = className;
        if (isMethodTest) {
          targetString += "#" + configState.getMethodName();
        }

        if (getState().getLaunchMethod().equals(AndroidTestLaunchMethod.NON_BLAZE)) {
          return targetString;
        } else {
          return nameBuilder.setTargetString(targetString).build();
        }
      }
    }
    return nameBuilder.build();
  }

  @Override
  @Nullable
  public BlazeCommandName getCommandName() {
    if (getState().getLaunchMethod().equals(AndroidTestLaunchMethod.BLAZE_TEST)) {
      return BlazeCommandName.TEST;
    } else if (getState().getLaunchMethod().equals(AndroidTestLaunchMethod.MOBILE_INSTALL)) {
      return BlazeCommandName.MOBILE_INSTALL;
    }
    return null;
  }

  @Override
  public String getHandlerName() {
    return "Android Test Handler";
  }
}
