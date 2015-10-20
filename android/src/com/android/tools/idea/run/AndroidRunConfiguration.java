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

import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.run.activity.*;
import com.android.tools.idea.run.editor.AndroidRunConfigurationEditor;
import com.android.tools.idea.run.editor.ApplicationRunParameters;
import com.google.common.base.Predicates;
import com.intellij.execution.Executor;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RefactoringListenerProvider;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.junit.RefactoringListeners;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AndroidRunConfiguration extends AndroidRunConfigurationBase implements RefactoringListenerProvider {
  @NonNls public static final String LAUNCH_DEFAULT_ACTIVITY = "default_activity";
  @NonNls public static final String LAUNCH_SPECIFIC_ACTIVITY = "specific_activity";
  @NonNls public static final String DO_NOTHING = "do_nothing";

  public String ACTIVITY_CLASS = "";
  public String ACTIVITY_EXTRA_FLAGS = "";
  public String MODE = LAUNCH_DEFAULT_ACTIVITY;
  public boolean DEPLOY = true;
  public String ARTIFACT_NAME = "";

  public AndroidRunConfiguration(Project project, ConfigurationFactory factory) {
    super(project, factory);
  }

  @Override
  protected Pair<Boolean, String> supportsRunningLibraryProjects(@NotNull AndroidFacet facet) {
    return Pair.create(Boolean.FALSE, AndroidBundle.message("android.cannot.run.library.project.error"));
  }

  @NotNull
  @Override
  protected List<ValidationError> checkConfiguration(@NotNull AndroidFacet facet) {
    return getApplicationLauncher(facet).checkConfiguration();
  }

  @NotNull
  @Override
  protected LaunchOptions.Builder getLaunchOptions() {
    return super.getLaunchOptions()
      .setDeploy(DEPLOY)
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
      new AndroidRunConfigurationEditor<AndroidRunConfiguration>(project, Predicates.<AndroidFacet>alwaysFalse());
    editor.setConfigurationSpecificEditor(new ApplicationRunParameters(project, editor.getModuleSelector()));
    return editor;
  }

  @Override
  @Nullable
  public RefactoringElementListener getRefactoringElementListener(PsiElement element) {
    return RefactoringListeners.getClassOrPackageListener(element, new RefactoringListeners.Accessor<PsiClass>() {
      @Override
      public void setName(String qualifiedName) {
        ACTIVITY_CLASS = qualifiedName;
      }

      @Nullable
      @Override
      public PsiClass getPsiElement() {
        return getConfigurationModule().findClass(ACTIVITY_CLASS);
      }

      @Override
      public void setPsiElement(PsiClass psiClass) {
        ACTIVITY_CLASS = JavaExecutionUtil.getRuntimeQualifiedName(psiClass);
      }
    });
  }

  @NotNull
  @Override
  protected ConsoleView attachConsole(AndroidRunningState state, Executor executor) {
    Project project = getConfigurationModule().getProject();
    final TextConsoleBuilder builder = TextConsoleBuilderFactory.getInstance().createBuilder(project);
    ConsoleView console = builder.getConsole();
    console.attachToProcess(state.getProcessHandler());
    return console;
  }

  @Override
  protected boolean supportMultipleDevices() {
    return true;
  }

  @NotNull
  @Override
  protected AndroidActivityLauncher getApplicationLauncher(@NotNull AndroidFacet facet) {
    return new AndroidActivityLauncher(facet, needsLaunch(), getActivityLocator(facet), ACTIVITY_EXTRA_FLAGS);
  }

  @NotNull
  protected ActivityLocator getActivityLocator(@NotNull AndroidFacet facet) {
    if (MODE.equals(LAUNCH_DEFAULT_ACTIVITY)) {
      if (facet.getProperties().USE_CUSTOM_COMPILER_MANIFEST) {
        return new MavenDefaultActivityLocator(facet);
      }
      else {
        return new DefaultActivityLocator(facet);
      }
    }
    else if (MODE.equals(LAUNCH_SPECIFIC_ACTIVITY)) {
      return new SpecificActivityLocator(facet, ACTIVITY_CLASS);
    }
    else {
      return new EmptyActivityLocator();
    }
  }

  protected boolean needsLaunch() {
    return LAUNCH_SPECIFIC_ACTIVITY.equals(MODE) || LAUNCH_DEFAULT_ACTIVITY.equals(MODE);
  }
}
