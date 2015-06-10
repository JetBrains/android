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
package org.jetbrains.android.run;

import com.android.SdkConstants;
import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.*;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.model.ManifestInfo;
import com.google.common.base.Predicates;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.junit.RefactoringListeners;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.dom.manifest.*;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.intellij.execution.process.ProcessOutputTypes.STDERR;
import static com.intellij.execution.process.ProcessOutputTypes.STDOUT;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidRunConfiguration extends AndroidRunConfigurationBase implements RefactoringListenerProvider {
  @NonNls public static final String LAUNCH_DEFAULT_ACTIVITY = "default_activity";
  @NonNls public static final String LAUNCH_SPECIFIC_ACTIVITY = "specific_activity";
  @NonNls public static final String DO_NOTHING = "do_nothing";

  public String ACTIVITY_CLASS = "";
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

  @Override
  protected void checkConfiguration(@NotNull AndroidFacet facet) throws RuntimeConfigurationException {
    if (getTargetSelectionMode() == TargetSelectionMode.CLOUD_DEVICE_LAUNCH && !IS_VALID_CLOUD_DEVICE_SELECTION) {
      throw new RuntimeConfigurationError(INVALID_CLOUD_DEVICE_SELECTION_ERROR);
    }

    final boolean packageContainMavenProperty = doesPackageContainMavenProperty(facet);
    final JavaRunConfigurationModule configurationModule = getConfigurationModule();
    Module module = facet.getModule();
    if (MODE.equals(LAUNCH_SPECIFIC_ACTIVITY)) {
      Project project = configurationModule.getProject();
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      PsiClass activityClass = facade.findClass(AndroidUtils.ACTIVITY_BASE_CLASS_NAME, ProjectScope.getAllScope(project));
      if (activityClass == null) {
        throw new RuntimeConfigurationError(AndroidBundle.message("cant.find.activity.class.error"));
      }
      if (ACTIVITY_CLASS == null || ACTIVITY_CLASS.length() == 0) {
        throw new RuntimeConfigurationError(AndroidBundle.message("activity.class.not.specified.error"));
      }
      PsiClass c = configurationModule.findClass(ACTIVITY_CLASS);

      if (c == null || !c.isInheritor(activityClass, true)) {
        final ActivityAlias activityAlias = findActivityAlias(facet, ACTIVITY_CLASS);

        if (activityAlias == null) {
          throw new RuntimeConfigurationError(AndroidBundle.message("not.activity.subclass.error", ACTIVITY_CLASS));
        }

        if (!isActivityLaunchable(activityAlias.getIntentFilters())) {
          throw new RuntimeConfigurationError(AndroidBundle.message("activity.not.launchable.error", AndroidUtils.LAUNCH_ACTION_NAME));
        }
        return;
      }
      if (!packageContainMavenProperty) {
        List<Activity> activities = ManifestInfo.get(module, true).getActivities();
        Activity activity = AndroidDomUtil.getActivityDomElementByClass(activities, c);
        Module libModule = null;
        if (activity == null) {
          for (AndroidFacet depFacet : AndroidUtils.getAllAndroidDependencies(module, true)) {
            final Module depModule = depFacet.getModule();
            activities = ManifestInfo.get(depModule, true).getActivities();
            activity = AndroidDomUtil.getActivityDomElementByClass(activities, c);


            if (activity != null) {
              libModule = depModule;
              break;
            }
          }
          if (activity == null) {
            throw new RuntimeConfigurationError(AndroidBundle.message("activity.not.declared.in.manifest", c.getName()));
          }
          else if (!facet.getProperties().ENABLE_MANIFEST_MERGING) {
            throw new RuntimeConfigurationError(AndroidBundle.message("activity.declared.but.manifest.merging.disabled", c.getName(),
                                                                      libModule.getName(), module.getName()));
          }
        }
      }
    }
    else if (MODE.equals(LAUNCH_DEFAULT_ACTIVITY)) {
      if (packageContainMavenProperty) {
        return;
      }

      List<Activity> activities = ManifestInfo.get(module, true).getActivities();
      List<ActivityAlias> activityAliases = ManifestInfo.get(module, true).getActivityAliases();
      String activity = AndroidUtils.getDefaultLauncherActivityName(activities, activityAliases);
      if (activity != null) {
        return;
      }
      throw new RuntimeConfigurationError(AndroidBundle.message("default.activity.not.found.error"));
    }
  }


  @Nullable
  private static ActivityAlias findActivityAlias(@NotNull AndroidFacet facet, @NotNull String name) {
    ActivityAlias alias = doFindActivityAlias(facet, name);

    if (alias != null) {
      return alias;
    }
    for (AndroidFacet depFacet : AndroidUtils.getAllAndroidDependencies(facet.getModule(), true)) {
      alias = doFindActivityAlias(depFacet, name);

      if (alias != null) {
        return alias;
      }
    }
    return null;
  }

  @Nullable
  private static ActivityAlias doFindActivityAlias(@NotNull AndroidFacet facet, @NotNull String name) {
    final Manifest manifest = facet.getManifest();

    if (manifest == null) {
      return null;
    }
    final Application application = manifest.getApplication();

    if (application == null) {
      return null;
    }
    final String aPackage = manifest.getPackage().getStringValue();

    for (ActivityAlias activityAlias : application.getActivityAliass()) {
      final String alias = activityAlias.getName().getStringValue();

      if (alias != null && alias.length() > 0 && name.endsWith(alias)) {
        String prefix = name.substring(0, name.length() - alias.length());

        if (prefix.endsWith(".")) {
          prefix = prefix.substring(0, prefix.length() - 1);
        }

        if (prefix.length() == 0 || prefix.equals(aPackage)) {
          return activityAlias;
        }
      }
    }
    return null;
  }

  private static boolean doesPackageContainMavenProperty(@NotNull AndroidFacet facet) {
    final Manifest manifest = facet.getManifest();

    if (manifest == null) {
      return false;
    }
    final String aPackage = manifest.getPackage().getStringValue();
    return aPackage != null && aPackage.contains("${");
  }

  @Override
  public AndroidRunningState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
    AndroidRunningState state = super.getState(executor, env);
    if (state != null) {
      state.setDeploy(DEPLOY);
      state.setOpenLogcatAutomatically(SHOW_LOGCAT_AUTOMATICALLY);
      state.setFilterLogcatAutomatically(FILTER_LOGCAT_AUTOMATICALLY);
    }
    return state;
  }

  @Override
  @NotNull
  protected ApkProvider getApkProvider() {
    Module module = getConfigurationModule().getModule();
    assert module != null;
    AndroidFacet facet = AndroidFacet.getInstance(module);
    assert facet != null;
    if (facet.getIdeaAndroidProject() != null && Projects.isBuildWithGradle(module)) {
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

  @Nullable
  @Override
  protected AndroidApplicationLauncher getApplicationLauncher(final AndroidFacet facet) {
    return new MyApplicationLauncher() {
      @Nullable
      @Override
      protected String getActivityName(@Nullable ProcessHandler processHandler) {
        return getActivityToLaunch(facet, processHandler);
      }
    };
  }

  @Nullable
  protected String getActivityToLaunch(@NotNull final AndroidFacet facet, @Nullable ProcessHandler processHandler) {
    String activityToLaunch = null;

    if (MODE.equals(LAUNCH_DEFAULT_ACTIVITY)) {
      final String defaultActivityName = computeDefaultActivity(facet, processHandler);

      if (defaultActivityName != null) {
        activityToLaunch = defaultActivityName;
      }
      else {
        if (processHandler != null) {
          processHandler.notifyTextAvailable(AndroidBundle.message("default.activity.not.found.error"), STDERR);
        }
        return null;
      }
    }
    else if (MODE.equals(LAUNCH_SPECIFIC_ACTIVITY)) {
      activityToLaunch = ACTIVITY_CLASS;
    }

    if (activityToLaunch != null) {
      final String finalActivityToLaunch = activityToLaunch;

      final String activityRuntimeQName = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        @Override
        public String compute() {
          final GlobalSearchScope scope = facet.getModule().getModuleWithDependenciesAndLibrariesScope(false);
          final PsiClass activityClass = JavaPsiFacade.getInstance(getProject()).findClass(finalActivityToLaunch, scope);

          if (activityClass != null) {
            return JavaExecutionUtil.getRuntimeQualifiedName(activityClass);
          }
          return null;
        }
      });
      if (activityRuntimeQName != null) {
        return activityRuntimeQName;
      }
    }

    return activityToLaunch;
  }

  @Nullable
  @VisibleForTesting
  static String computeDefaultActivity(@NotNull final AndroidFacet facet, @Nullable final ProcessHandler processHandler) {
    if (!facet.getProperties().USE_CUSTOM_COMPILER_MANIFEST) {
      final boolean useMergedManifest = facet.isGradleProject() || facet.getProperties().ENABLE_MANIFEST_MERGING;
      final ManifestInfo manifestInfo = ManifestInfo.get(facet.getModule(), useMergedManifest);

      return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        @Override
        public String compute() {
          return AndroidUtils.getDefaultLauncherActivityName(manifestInfo.getActivities(), manifestInfo.getActivityAliases());
        }
      });
    }

    File manifestCopy = null;
    try {
      Pair<File, String> pair;
      try {
        pair = getCopyOfCompilerManifestFile(facet);
      } catch (IOException e) {
        pair = null;
        if (processHandler != null) {
          processHandler.notifyTextAvailable("I/O error: " + e.getMessage(), STDERR);
        }
      }
      manifestCopy = pair != null ? pair.getFirst() : null;
      VirtualFile manifestVFile = manifestCopy != null ? LocalFileSystem.getInstance().findFileByIoFile(manifestCopy) : null;
      final Manifest manifest =
        manifestVFile == null ? null : AndroidUtils.loadDomElement(facet.getModule(), manifestVFile, Manifest.class);

      return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        @Nullable
        @Override
        public String compute() {
          if (manifest == null) {
            if (processHandler != null) {
              processHandler.notifyTextAvailable("Cannot find " + SdkConstants.FN_ANDROID_MANIFEST_XML + " file\n", STDERR);
            }
            return null;
          }
          return AndroidUtils.getDefaultLauncherActivityName(manifest);
        }
      });
    }
    finally {
      if (manifestCopy != null) {
        FileUtil.delete(manifestCopy.getParentFile());
      }
    }
  }

  /**
   * Returns whether the given module corresponds to a watch face app.
   * A module is considered to be a watch face app if there are no activities, and a single service with
   * a specific intent filter. This definition is likely stricter than it needs to be to but we are only
   * interested in matching the watch face template application.
   */
  public static boolean isWatchFaceApp(@NotNull AndroidFacet facet) {
    ManifestInfo info = ManifestInfo.get(facet.getModule(), true);
    if (!info.getActivities().isEmpty()) {
      return false;
    }

    final List<Service> services = info.getServices();
    if (services.size() != 1) {
      return false;
    }

    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        List<IntentFilter> filters = services.get(0).getIntentFilters();
        return filters.size() == 1 &&
               AndroidDomUtil.containsAction(filters.get(0), AndroidUtils.WALLPAPER_SERVICE_ACTION_NAME) &&
               AndroidDomUtil.containsCategory(filters.get(0), AndroidUtils.WATCHFACE_CATEGORY_NAME);
      }
    });
  }

  private static boolean isActivityLaunchable(List<IntentFilter> intentFilters) {
    for (IntentFilter filter : intentFilters) {
      if (AndroidDomUtil.containsAction(filter, AndroidUtils.LAUNCH_ACTION_NAME)) {
        return true;
      }
    }
    return false;
  }

  protected static abstract class MyApplicationLauncher extends AndroidApplicationLauncher {
    protected static final Logger LOG = Logger.getInstance("#org.jetbrains.android.run.AndroidRunConfiguration.MyApplicationLauncher");

    @Nullable
    protected abstract String getActivityName(@Nullable ProcessHandler processHandler);

    @SuppressWarnings({"EnumSwitchStatementWhichMissesCases"})
    @Override
    public boolean isReadyForDebugging(ClientData data, ProcessHandler processHandler) {
      final String activityName = getActivityName(processHandler);
      if (activityName == null) {
        ClientData.DebuggerStatus status = data.getDebuggerConnectionStatus();
        switch (status) {
          case ERROR:
            if (processHandler != null) {
              processHandler.notifyTextAvailable("Debug port is busy\n", STDOUT);
            }
            LOG.info("Debug port is busy");
            return false;
          case ATTACHED:
            if (processHandler != null) {
              processHandler.notifyTextAvailable("Debugger already attached\n", STDOUT);
            }
            LOG.info("Debugger already attached");
            return false;
          default:
            return true;
        }
      }
      return super.isReadyForDebugging(data, processHandler);
    }

    @Override
    public LaunchResult launch(@NotNull AndroidRunningState state, @NotNull IDevice device)
      throws IOException, AdbCommandRejectedException, TimeoutException {
      ProcessHandler processHandler = state.getProcessHandler();
      String activityName = getActivityName(processHandler);
      if (activityName == null) return LaunchResult.NOTHING_TO_DO;
      activityName = activityName.replace("$", "\\$");
      final String activityPath = state.getPackageName() + '/' + activityName;
      if (state.isStopped()) return LaunchResult.STOP;
      processHandler.notifyTextAvailable("Launching application: " + activityPath + ".\n", STDOUT);
      AndroidRunningState.MyReceiver receiver = state.new MyReceiver();
      while (true) {
        if (state.isStopped()) return LaunchResult.STOP;
        String command = "am start " +
                         getDebugFlags(state) +
                         " -n \"" + activityPath + "\" " +
                         "-a android.intent.action.MAIN " +
                         "-c android.intent.category.LAUNCHER";
        boolean deviceNotResponding = false;
        try {
          state.executeDeviceCommandAndWriteToConsole(device, command, receiver);
        }
        catch (ShellCommandUnresponsiveException e) {
          LOG.info(e);
          deviceNotResponding = true;
        }
        if (!deviceNotResponding && receiver.getErrorType() != 2) {
          break;
        }
        processHandler.notifyTextAvailable("Device is not ready. Waiting for " + AndroidRunningState.WAITING_TIME + " sec.\n", STDOUT);
        synchronized (state.getRunningLock()) {
          try {
            state.getRunningLock().wait(AndroidRunningState.WAITING_TIME * 1000);
          }
          catch (InterruptedException e) {
          }
        }
        receiver = state.new MyReceiver();
      }
      boolean success = receiver.getErrorType() == AndroidRunningState.NO_ERROR;
      if (success) {
        processHandler.notifyTextAvailable(receiver.getOutput().toString(), STDOUT);
      }
      else {
        processHandler.notifyTextAvailable(receiver.getOutput().toString(), STDERR);
      }
      return success ? LaunchResult.SUCCESS : LaunchResult.STOP;
    }

    /** Returns the flags used to the "am start" command for launching in debug mode. */
    @NotNull
    protected String getDebugFlags(@NotNull AndroidRunningState state) {
      return state.isDebugMode() ? "-D" : "";
    }
  }
}
