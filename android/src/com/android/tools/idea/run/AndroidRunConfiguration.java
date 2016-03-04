/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.run;

import com.android.tools.idea.fd.InstantRunManager;
import com.android.tools.idea.fd.InstantRunSettings;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.run.editor.*;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.android.tools.idea.run.util.LaunchStatus;
import com.google.common.base.Predicates;
import com.google.common.collect.Maps;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.JavaRunConfigurationModule;
import com.intellij.execution.configurations.RefactoringListenerProvider;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.junit.RefactoringListeners;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import org.jdom.Element;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AndroidRunConfiguration extends AndroidRunConfigurationBase implements RefactoringListenerProvider {
  @NonNls public static final String LAUNCH_DEFAULT_ACTIVITY = "default_activity";
  @NonNls public static final String LAUNCH_SPECIFIC_ACTIVITY = "specific_activity";
  @NonNls public static final String DO_NOTHING = "do_nothing";
  @NonNls public static final String LAUNCH_DEEP_LINK = "launch_deep_link";

  public static final List<? extends LaunchOption> LAUNCH_OPTIONS =
    Arrays.asList(NoLaunch.INSTANCE, DefaultActivityLaunch.INSTANCE, SpecificActivityLaunch.INSTANCE, DeepLinkLaunch.INSTANCE);

  // Deploy options
  public boolean DEPLOY = true;
  public String ARTIFACT_NAME = "";
  public String PM_INSTALL_OPTIONS = "";

  // Launch options
  public String ACTIVITY_EXTRA_FLAGS = "";
  public String MODE = LAUNCH_DEFAULT_ACTIVITY;

  private final Map<String, LaunchOptionState> myLaunchOptionStates = Maps.newHashMap();

  public AndroidRunConfiguration(Project project, ConfigurationFactory factory) {
    super(project, factory, false);

    for (LaunchOption option : LAUNCH_OPTIONS) {
      myLaunchOptionStates.put(option.getId(), option.createState());
    }
  }

  @Override
  protected Pair<Boolean, String> supportsRunningLibraryProjects(@NotNull AndroidFacet facet) {
    return Pair.create(Boolean.FALSE, AndroidBundle.message("android.cannot.run.library.project.error"));
  }

  @NotNull
  @Override
  protected List<ValidationError> checkConfiguration(@NotNull AndroidFacet facet) {
    LaunchOptionState launchOptionState = getLaunchOptionState(MODE);
    if (launchOptionState == null) {
      return Collections.emptyList();
    }

    return launchOptionState.checkConfiguration(facet);
  }

  @NotNull
  @Override
  protected LaunchOptions.Builder getLaunchOptions() {
    return super.getLaunchOptions()
      .setDeploy(DEPLOY)
      .setPmInstallOptions(PM_INSTALL_OPTIONS)
      .setOpenLogcatAutomatically(SHOW_LOGCAT_AUTOMATICALLY);
  }

  @Override
  @NotNull
  protected ApkProvider getApkProvider(@NotNull AndroidFacet facet) {
    // TODO: Resolve direct AndroidGradleModel dep (b/22596984)
    if (facet.getAndroidModel() != null && facet.getAndroidModel() instanceof AndroidGradleModel) {
      return new GradleApkProvider(facet, false);
    }
    return new NonGradleApkProvider(facet, ARTIFACT_NAME);
  }

  @NotNull
  @Override
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    Project project = getProject();
    AndroidRunConfigurationEditor<AndroidRunConfiguration> editor =
      new AndroidRunConfigurationEditor<AndroidRunConfiguration>(project, Predicates.<AndroidFacet>alwaysFalse(), this);
    editor.setConfigurationSpecificEditor(new ApplicationRunParameters(project, editor.getModuleSelector()));
    return editor;
  }

  @Override
  @Nullable
  public RefactoringElementListener getRefactoringElementListener(PsiElement element) {
    // TODO: This is a bit of a hack: Currently, refactoring only affects the specific activity launch, so we directly peek into it and
    // change its state. The correct way of implementing this would be to delegate to all of the LaunchOptions and put the results into
    // a RefactoringElementListenerComposite
    final SpecificActivityLaunch.State state = (SpecificActivityLaunch.State)getLaunchOptionState(LAUNCH_SPECIFIC_ACTIVITY);
    assert state != null;
    return RefactoringListeners.getClassOrPackageListener(element, new RefactoringListeners.Accessor<PsiClass>() {
      @Override
      public void setName(String qualifiedName) {
        state.ACTIVITY_CLASS = qualifiedName;
      }

      @Nullable
      @Override
      public PsiClass getPsiElement() {
        return getConfigurationModule().findClass(state.ACTIVITY_CLASS);
      }

      @Override
      public void setPsiElement(PsiClass psiClass) {
        state.ACTIVITY_CLASS = JavaExecutionUtil.getRuntimeQualifiedName(psiClass);
      }
    });
  }

  @NotNull
  @Override
  protected ConsoleProvider getConsoleProvider() {
    return new ConsoleProvider() {
      @NotNull
      @Override
      public ConsoleView createAndAttach(@NotNull Disposable parent,
                                         @NotNull ProcessHandler handler,
                                         @NotNull Executor executor) throws ExecutionException {
        Project project = getConfigurationModule().getProject();
        final TextConsoleBuilder builder = TextConsoleBuilderFactory.getInstance().createBuilder(project);
        ConsoleView console = builder.getConsole();
        console.attachToProcess(handler);
        return console;
      }
    };
  }

  @Override
  public boolean supportsInstantRun() {
    return true;
  }

  @Override
  protected boolean supportMultipleDevices() {
    return true;
  }

  @Nullable
  @Override
  protected LaunchTask getApplicationLaunchTask(@NotNull ApkProvider apkProvider,
                                                @NotNull AndroidFacet facet,
                                                boolean waitForDebugger,
                                                @NotNull LaunchStatus launchStatus) {
    LaunchOptionState state = getLaunchOptionState(MODE);
    assert state != null;

    try {
      return state.getLaunchTask(apkProvider.getPackageName(),
                                 facet,
                                 waitForDebugger,
                                 getAndroidDebugger(),
                                 ACTIVITY_EXTRA_FLAGS,
                                 getProfilerState());
    }
    catch (ApkProvisionException e) {
      Logger.getInstance(AndroidRunConfiguration.class).error(e);
      launchStatus.terminateLaunch("Unable to identify application id");
      return null;
    }
  }

  public void setLaunchActivity(@NotNull String activityName) {
    MODE = LAUNCH_SPECIFIC_ACTIVITY;

    // TODO: we probably need a better way to do this rather than peeking into the option state
    // Possibly something like setLaunch(LAUNCH_SPECIFIC_ACTIVITY, SpecificLaunchActivity.state(className))
    LaunchOptionState state = getLaunchOptionState(LAUNCH_SPECIFIC_ACTIVITY);
    assert state instanceof SpecificActivityLaunch.State;
    ((SpecificActivityLaunch.State)state).ACTIVITY_CLASS = activityName;
  }

  public boolean isLaunchingActivity(@Nullable String activityName) {
    if (!StringUtil.equals(MODE, LAUNCH_SPECIFIC_ACTIVITY)) {
      return false;
    }

    // TODO: we probably need a better way to do this rather than peeking into the option state, possibly just delegate equals to the option
    LaunchOptionState state = getLaunchOptionState(LAUNCH_SPECIFIC_ACTIVITY);
    assert state instanceof SpecificActivityLaunch.State;
    return StringUtil.equals(((SpecificActivityLaunch.State)state).ACTIVITY_CLASS, activityName);
  }

  @Nullable
  public LaunchOptionState getLaunchOptionState(@NotNull String launchOptionId) {
    return myLaunchOptionStates.get(launchOptionId);
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);

    for (LaunchOptionState state : myLaunchOptionStates.values()) {
      DefaultJDOMExternalizer.readExternal(state, element);
    }
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);

    for (LaunchOptionState state : myLaunchOptionStates.values()) {
      DefaultJDOMExternalizer.writeExternal(state, element);
    }
  }
}
