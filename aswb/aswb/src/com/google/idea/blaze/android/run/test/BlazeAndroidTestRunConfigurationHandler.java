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
import com.android.tools.idea.run.ValidationError;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.android.run.ApkBuildStepProvider;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationCommonState;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationHandler;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationValidationUtil;
import com.google.idea.blaze.android.run.LaunchMetrics;
import com.google.idea.blaze.android.run.runner.ApkBuildStep;
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunConfigurationRunner;
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunContext;
import com.google.idea.blaze.android.run.runner.FullApkBuildStep;
import com.google.idea.blaze.android.run.test.BlazeAndroidTestLaunchMethodsProvider.AndroidTestLaunchMethod;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeConfigurationNameBuilder;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationRunner;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.projectstructure.ModuleFinder;
import com.google.idea.blaze.java.AndroidBlazeRules;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.util.List;
import javax.annotation.Nullable;
import org.jetbrains.android.facet.AndroidFacet;

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
    Module module =
        ModuleFinder.getInstance(env.getProject())
            .findModuleByName(BlazeDataStorage.WORKSPACE_MODULE_NAME);
    AndroidFacet facet = module != null ? AndroidFacet.getInstance(module) : null;
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
    Label label = Label.create(configuration.getSingleTarget().toString());

    ApkBuildStep buildStep =
        getTestBuildStep(
            project, configState, configuration, blazeFlags, exeFlags, launchId, label);

    BlazeAndroidRunContext runContext =
        new BlazeAndroidTestRunContext(
            project, facet, configuration, env, configState, label, blazeFlags, buildStep);

    LaunchMetrics.logTestLaunch(
        launchId, configState.getLaunchMethod().name(), env.getExecutor().getId());

    return new BlazeAndroidRunConfigurationRunner(module, runContext, configuration);
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
    if (configuration.getTargetKind()
        == AndroidBlazeRules.RuleTypes.ANDROID_INSTRUMENTATION_TEST.getKind()) {
      boolean useMobileInstall =
          AndroidTestLaunchMethod.MOBILE_INSTALL.equals(configState.getLaunchMethod());
      return ApkBuildStepProvider.getInstance(Blaze.getBuildSystemName(project))
          .getAitBuildStep(
              project,
              useMobileInstall,
              /* nativeDebuggingEnabled= */ false,
              label,
              blazeFlags,
              exeFlags,
              launchId);
    } else {
      // TODO(b/248317444): This path is only invoked for the deprecated {@code android_test}
      // targets, and should eventually be removed.
      return new FullApkBuildStep(project, label, blazeFlags, /* nativeDebuggingEnabled= */ false);
    }
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
    TargetExpression target = configuration.getSingleTarget();
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
