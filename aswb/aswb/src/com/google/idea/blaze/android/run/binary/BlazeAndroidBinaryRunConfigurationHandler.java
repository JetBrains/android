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
package com.google.idea.blaze.android.run.binary;

import static com.google.idea.blaze.android.run.LaunchMetrics.logBinaryLaunch;

import com.android.tools.idea.run.ValidationError;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.android.run.ApkBuildStepProvider;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationCommonState;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationHandler;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationValidationUtil;
import com.google.idea.blaze.android.run.LaunchMetrics;
import com.google.idea.blaze.android.run.binary.AndroidBinaryLaunchMethodsUtils.AndroidBinaryLaunchMethod;
import com.google.idea.blaze.android.run.binary.mobileinstall.BlazeAndroidBinaryMobileInstallRunContextCompat;
import com.google.idea.blaze.android.run.runner.ApkBuildStep;
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunConfigurationRunner;
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunContext;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.logging.EventLoggingService;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.run.BlazeConfigurationNameBuilder;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationRunner;
import com.google.idea.blaze.base.run.state.RunConfigurationState;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.projectstructure.ModuleFinder;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import java.util.concurrent.TimeUnit;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Nullable;

/**
 * {@link com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandler} for
 * android_binary targets.
 */
public class BlazeAndroidBinaryRunConfigurationHandler
    extends BlazeAndroidBinaryRunConfigurationHandlerCompat
    implements BlazeAndroidRunConfigurationHandler {

  @VisibleForTesting
  protected BlazeAndroidBinaryRunConfigurationHandler(BlazeCommandRunConfiguration configuration) {
    super(configuration);
  }

  private static final Logger LOG =
      Logger.getInstance(BlazeAndroidBinaryRunConfigurationHandler.class);

  // Keys to store state for the MI migration prompt
  private static final String MI_LAST_PROMPT = "MI_MIGRATE_LAST_PROMPT";
  static final String MI_NEVER_ASK_AGAIN = "MI_MIGRATE_NEVER_AGAIN";
  private static final Long MI_TIMEOUT_MS = TimeUnit.HOURS.toMillis(20); // 20 hours

  @Override
  public BlazeAndroidBinaryRunConfigurationState getState() {
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

    // Only suggest building with mobile-install if native debugging isn't enabled.
    if (configState.getLaunchMethod() == AndroidBinaryLaunchMethod.NON_BLAZE
        && !configState.getCommonState().isNativeDebuggingEnabled()) {
      maybeShowMobileInstallOptIn(project, configuration);
    }

    // We collect metrics from a few different locations. In order to tie them all
    // together, we create a unique launch id.
    String launchId = LaunchMetrics.newLaunchId();

    // Create build step for matching launch method.
    ImmutableList<String> blazeFlags =
        configState
            .getCommonState()
            .getExpandedBuildFlags(
                project,
                projectViewSet,
                BlazeCommandName.RUN,
                BlazeInvocationContext.runConfigContext(
                    ExecutorType.fromExecutor(env.getExecutor()), configuration.getType(), false));
    ImmutableList<String> exeFlags =
        ImmutableList.copyOf(
            configState.getCommonState().getExeFlagsState().getFlagsForExternalProcesses());
    ApkBuildStep buildStep =
        ApkBuildStepProvider.getInstance(Blaze.getBuildSystemName(project))
            .getBinaryBuildStep(
                project,
                AndroidBinaryLaunchMethodsUtils.useMobileInstall(configState.getLaunchMethod()),
                configState.getCommonState().isNativeDebuggingEnabled(),
                Label.create(configuration.getSingleTarget().toString()),
                blazeFlags,
                exeFlags,
                launchId);

    // Create run context for matching launch method.
    BlazeAndroidRunContext runContext = null;
    switch (configState.getLaunchMethod()) {
      case NON_BLAZE:
        runContext =
            new BlazeAndroidBinaryNormalBuildRunContextCompat(
                project, facet, configuration, env, configState, buildStep, launchId);
        break;
      case MOBILE_INSTALL_V2:
        // Standardize on a single mobile-install launch method
        configState.setLaunchMethod(AndroidBinaryLaunchMethod.MOBILE_INSTALL);
        // fall through
      case MOBILE_INSTALL:
        runContext =
            new BlazeAndroidBinaryMobileInstallRunContextCompat(
                project, facet, configuration, env, configState, buildStep, launchId);
        break;
      default:
        throw new ExecutionException("No compatible launch methods.");
    }

    logBinaryLaunch(
        launchId,
        configState.getLaunchMethod().name(),
        env.getExecutor().getId(),
        configuration.getSingleTarget().toString(),
        configState.getCommonState().isNativeDebuggingEnabled());
    return new BlazeAndroidRunConfigurationRunner(module, runContext, configuration);
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
  private ImmutableList<ValidationError> validate() {
    ImmutableList.Builder<ValidationError> errors = ImmutableList.builder();
    errors.addAll(BlazeAndroidRunConfigurationValidationUtil.validateWorkspaceModule(project));
    errors.addAll(getCommonState().validate(project));
    errors.addAll(configState.validate(project));
    return errors.build();
  }

  @Override
  @Nullable
  public String suggestedName(BlazeCommandRunConfiguration configuration) {
    TargetExpression target = configuration.getSingleTarget();
    if (target == null) {
      return null;
    }
    // buildSystemName and commandName are intentionally omitted.
    return new BlazeConfigurationNameBuilder().setTargetString(target.toString()).build();
  }

  @Override
  @Nullable
  public BlazeCommandName getCommandName() {
    return BlazeCommandName.RUN;
  }

  @Override
  public String getHandlerName() {
    return "Android Binary Handler";
  }

  /**
   * Maybe shows the mobile-install optin dialog, and migrates project as appropriate.
   *
   * <p>Will only be shown once per project in a 20 hour window, with the ability to permanently
   * dismiss for this project.
   *
   * <p>If the user selects "Yes", all BlazeAndroidBinaryRunConfigurations in this project will be
   * migrated to use mobile-install.
   *
   * @return true if dialog was shown and user migrated, otherwise false
   */
  @CanIgnoreReturnValue
  private boolean maybeShowMobileInstallOptIn(
      Project project, BlazeCommandRunConfiguration configuration) {
    long lastPrompt = PropertiesComponent.getInstance(project).getOrInitLong(MI_LAST_PROMPT, 0L);
    boolean neverAsk =
        PropertiesComponent.getInstance(project).getBoolean(MI_NEVER_ASK_AGAIN, false);
    if (neverAsk || (System.currentTimeMillis() - lastPrompt) < MI_TIMEOUT_MS) {
      return false;
    }
    // Add more logging on why the MI opt-in dialog is shown.  There exists a bug there a user
    // is shown the mobile-install opt-in dialog every time they switch clients. The only way for
    // this to happen is if a new target is created or if the timeouts are not behaving as expected.
    // TODO Remove once b/130327673 is resolved.
    LOG.info(
        "Showing mobile install opt-in dialog.\n"
            + "Run target: "
            + configuration.getSingleTarget()
            + "\n"
            + "Time since last prompt: "
            + (System.currentTimeMillis() - lastPrompt));
    PropertiesComponent.getInstance(project)
        .setValue(MI_LAST_PROMPT, String.valueOf(System.currentTimeMillis()));
    int choice =
        Messages.showYesNoCancelDialog(
            project,
            "Blaze mobile-install (go/blaze-mi) introduces fast, incremental builds and deploys "
                + "for Android development.\nBlaze mobile-install is the default for new Android "
                + "Studio projects, but you're still using Blaze build.\n\nSwitch all run "
                + "configurations in this project to use Blaze mobile-install?",
            "Switch to Blaze mobile-install?",
            "Yes",
            "Not now",
            "Never ask again for this project",
            Messages.getQuestionIcon());
    if (choice == Messages.YES) {
      Messages.showInfoMessage(
          String.format(
              "Successfully migrated %d run configuration(s) to mobile-install",
              doMigrate(project)),
          "Success!");
    } else if (choice == Messages.NO) {
      // Do nothing, dialog will not be shown until the wait period has elapsed
    } else if (choice == Messages.CANCEL) {
      PropertiesComponent.getInstance(project).setValue(MI_NEVER_ASK_AGAIN, true);
    }
    EventLoggingService.getInstance()
        .logEvent(
            getClass(), "mi_migrate_prompt", ImmutableMap.of("choice", choiceToString(choice)));
    return choice == Messages.YES;
  }

  private int doMigrate(Project project) {
    int count = 0;
    for (RunConfiguration runConfig :
        RunManager.getInstance(project)
            .getConfigurationsList(BlazeCommandRunConfigurationType.getInstance())) {
      if (runConfig instanceof BlazeCommandRunConfiguration) {
        RunConfigurationState state =
            ((BlazeCommandRunConfiguration) runConfig).getHandler().getState();
        if (state instanceof BlazeAndroidBinaryRunConfigurationState) {
          ((BlazeAndroidBinaryRunConfigurationState) state)
              .setLaunchMethod(AndroidBinaryLaunchMethod.MOBILE_INSTALL);
          count++;
        }
      }
    }
    return count;
  }

  private String choiceToString(int choice) {
    if (choice == Messages.YES) {
      return "yes";
    } else if (choice == Messages.NO) {
      return "not_now";
    } else if (choice == Messages.CANCEL) {
      return "never_for_project";
    } else {
      return "unknown";
    }
  }
}
