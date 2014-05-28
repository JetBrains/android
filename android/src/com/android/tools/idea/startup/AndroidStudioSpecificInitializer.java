/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.startup;

import com.android.SdkConstants;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.actions.*;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.run.ArrayMapRenderer;
import com.android.tools.idea.sdk.DefaultSdks;
import com.android.tools.idea.sdk.VersionCheck;
import com.android.tools.idea.wizard.AddActivityAction;
import com.android.tools.idea.wizard.ChooseApiLevelDialog;
import com.android.tools.idea.wizard.DummyWizardForTesting;
import com.android.utils.Pair;
import com.google.common.collect.Lists;
import com.google.common.io.Closeables;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.actions.TemplateProjectSettingsGroup;
import com.intellij.ide.projectView.actions.MarkRootGroup;
import com.intellij.ide.projectView.impl.MoveModuleToGroupTopLevel;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableEP;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.util.PlatformUtils;
import com.intellij.util.SystemProperties;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Properties;

/** Initialization performed only in the context of the Android IDE. */
public class AndroidStudioSpecificInitializer implements Runnable {
  private static final Logger LOG = Logger.getInstance("#com.android.tools.idea.startup.AndroidStudioSpecificInitializer");

  private static final List<String> IDE_SETTINGS_TO_REMOVE = Lists.newArrayList(
    "org.jetbrains.plugins.javaFX.JavaFxSettingsConfigurable", "org.intellij.plugins.xpathView.XPathConfigurable",
    "org.intellij.lang.xpath.xslt.impl.XsltConfigImpl$UIImpl"
  );

  @NonNls private static final String USE_IDEA_NEW_PROJECT_WIZARDS = "use.idea.newProjectWizard";
  @NonNls private static final String USE_JPS_MAKE_ACTIONS = "use.idea.jpsMakeActions";
  @NonNls private static final String USE_IDEA_NEW_FILE_POPUPS = "use.idea.newFilePopupActions";
  @NonNls private static final String USE_IDEA_PROJECT_STRUCTURE = "use.idea.projectStructure";
  @NonNls private static final String ENABLE_EXPERIMENTAL_ACTIONS = "enable.experimental.actions";

  @NonNls private static final String ANDROID_SDK_FOLDER_NAME = "sdk";

  /** Paths relative to the IDE installation folder where the Android SDK maybe present. */
  private static final String[] ANDROID_SDK_RELATIVE_PATHS =
    { ANDROID_SDK_FOLDER_NAME, File.separator + ".." + File.separator + ANDROID_SDK_FOLDER_NAME,};

  public static boolean isAndroidStudio() {
    return "AndroidStudio".equals(PlatformUtils.getPlatformPrefix());
  }

  @Override
  public void run() {
    cleanUpIdePreferences();

    if (!Boolean.getBoolean(USE_IDEA_NEW_PROJECT_WIZARDS)) {
      replaceIdeaNewProjectActions();
    }

    if (!Boolean.getBoolean(USE_IDEA_PROJECT_STRUCTURE)) {
      replaceProjectStructureActions();
    }

    if (!Boolean.getBoolean(USE_JPS_MAKE_ACTIONS)) {
      replaceIdeaMakeActions();
    }

    if (!Boolean.getBoolean(USE_IDEA_NEW_FILE_POPUPS)) {
      hideIdeaNewFilePopupActions();
    }

    if (Boolean.getBoolean(ENABLE_EXPERIMENTAL_ACTIONS)) {
      registerExperimentalActions();
    }

    replaceAction("ShowProjectStructureSettings", new AndroidShowStructureSettingsAction());

    try {
      // Setup JDK and Android SDK if necessary
      setupSdks();
    } catch (Exception e) {
      LOG.error("Unexpected error while setting up SDKs: ", e);
    }

    registerAppClosing();

    // Always reset the Default scheme to match Android standards
    // User modifications won't be lost since they are made in a separate scheme (copied off of this default scheme)
    CodeStyleScheme scheme = CodeStyleSchemes.getInstance().getDefaultScheme();
    if (scheme != null) {
      CodeStyleSettings settings = scheme.getCodeStyleSettings();
      if (settings != null) {
        AndroidCodeStyleSettingsModifier.modify(settings);
      }
    }

    NodeRendererSettings.getInstance().addPluginRenderer(new ArrayMapRenderer("android.util.ArrayMap"));
    NodeRendererSettings.getInstance().addPluginRenderer(new ArrayMapRenderer("android.support.v4.util.ArrayMap"));

    checkAndSetAndroidSdkSources();
  }


  private static void cleanUpIdePreferences() {
    try {
      ExtensionPoint<ConfigurableEP<Configurable>> ideConfigurable =
        Extensions.getRootArea().getExtensionPoint(Configurable.APPLICATION_CONFIGURABLE);

      GradleUtil.cleanUpPreferences(ideConfigurable, IDE_SETTINGS_TO_REMOVE);
    }
    catch (Throwable e) {
      LOG.info("Failed to clean up IDE preferences", e);
    }
  }

  private static void registerExperimentalActions() {
    ActionManager am = ActionManager.getInstance();
    AnAction action = new NewFromGithubAction();
    am.registerAction("NewFromGithubAction", action);
    ((DefaultActionGroup)am.getAction("NewGroup")).add(action);
    DefaultActionGroup androidToolsGroup = (DefaultActionGroup)am.getAction("ToolsMenu");
    action = new DummyWizardForTesting();
    am.registerAction("TestDummyWizard", action);
    androidToolsGroup.add(action);
    action = new ChooseApiLevelDialog.LaunchMe();
    am.registerAction("ChooseApiLevel", action);
    androidToolsGroup.add(action);
    action = new DummyWizardForTesting.ClearPrefsAction();
    am.registerAction("ClearPrefs", action);
    androidToolsGroup.add(action);
    action = new AddActivityAction();
    am.registerAction("AddActivity", action);
    androidToolsGroup.add(action);
  }

  private static void replaceIdeaNewProjectActions() {
    // Unregister IntelliJ's version of the project actions and manually register our own.
    replaceAction("NewProject", new AndroidNewProjectAction());
    replaceAction("WelcomeScreen.CreateNewProject", new AndroidNewProjectAction());
    replaceAction("NewModule", new AndroidNewModuleAction());
    replaceAction("NewModuleInGroup", new AndroidNewModuleInGroupAction());
    replaceAction("ImportProject", new AndroidImportProjectAction());
    replaceAction("WelcomeScreen.ImportProject", new AndroidImportProjectAction());
    replaceAction("CreateLibraryFromFile", new CreateLibraryFromFilesAction());
    replaceAction("ImportModule", new AndroidImportModuleAction());

    hideAction(IdeActions.ACTION_GENERATE_ANT_BUILD, "Generate Ant Build...");
    hideAction("AddFrameworkSupport", "Add Framework Support...");
    hideAction("BuildArtifact", "Build Artifacts...");
    hideAction("RunTargetAction", "Run Ant Target");

    replaceProjectPopupActions();
  }

  private static void replaceProjectStructureActions() {
    replaceAction("ShowProjectStructureSettings", new AndroidShowStructureSettingsAction());

    AndroidTemplateProjectStructureAction showDefaultProjectStructureAction = new AndroidTemplateProjectStructureAction();
    showDefaultProjectStructureAction.getTemplatePresentation().setText("Default Project Structure...");
    replaceAction("TemplateProjectStructure", showDefaultProjectStructureAction);

    ActionManager am = ActionManager.getInstance();
    AnAction action = am.getAction("WelcomeScreen.Configure.IDEA");
    if (action instanceof DefaultActionGroup) {
      DefaultActionGroup projectSettingsGroup = (DefaultActionGroup)action;
      AnAction[] children = projectSettingsGroup.getChildren(null);
      if (children.length == 1 && children[0] instanceof TemplateProjectSettingsGroup) {
        projectSettingsGroup.replaceAction(children[0], new AndroidTemplateProjectSettingsGroup());
      }
    }
  }

  private static void replaceIdeaMakeActions() {
    // 'Build' > 'Make Project' action
    replaceAction("CompileDirty", new AndroidMakeProjectAction());

    // 'Build' > 'Make Modules' action
    replaceAction(IdeActions.ACTION_MAKE_MODULE, new AndroidMakeModuleAction());

    // 'Build' > 'Rebuild' action
    replaceAction(IdeActions.ACTION_COMPILE_PROJECT, new AndroidRebuildProjectAction());

    // 'Build' > 'Compile Modules' action
    hideAction(IdeActions.ACTION_COMPILE, "Compile Module(s)");
  }

  private static void replaceAction(String actionId, AnAction newAction) {
    ActionManager am = ActionManager.getInstance();
    AnAction oldAction = am.getAction(actionId);
    if (oldAction != null) {
      newAction.getTemplatePresentation().setIcon(oldAction.getTemplatePresentation().getIcon());
      am.unregisterAction(actionId);
    }
    am.registerAction(actionId, newAction);
  }

  private static void hideAction(@NotNull String actionId, @NotNull String backupText) {
    AnAction oldAction = ActionManager.getInstance().getAction(actionId);
    if (oldAction != null) {
      AnAction newAction = new AndroidActionRemover(oldAction, backupText);
      replaceAction(actionId, newAction);
    }
  }

  private static void replaceProjectPopupActions() {
    Deque<Pair<DefaultActionGroup, AnAction>> stack = new ArrayDeque<Pair<DefaultActionGroup, AnAction>>();
    stack.add(Pair.of((DefaultActionGroup)null, ActionManager.getInstance().getAction("ProjectViewPopupMenu")));
    while (!stack.isEmpty()) {
      Pair<DefaultActionGroup, AnAction> entry = stack.pop();
      DefaultActionGroup parent = entry.getFirst();
      AnAction action = entry.getSecond();
      if (action instanceof DefaultActionGroup) {
        DefaultActionGroup actionGroup = (DefaultActionGroup)action;
        for (AnAction child : actionGroup.getChildActionsOrStubs()) {
          stack.push(Pair.of(actionGroup, child));
        }
      }

      if (action instanceof MoveModuleToGroupTopLevel) {
        parent.remove(action);
        parent.add(new AndroidActionGroupRemover((ActionGroup)action, "Move Module to Group"),
                   new Constraints(Anchor.AFTER, "OpenModuleSettings"));
      } else if (action instanceof MarkRootGroup) {
        parent.remove(action);
        parent.add(new AndroidActionGroupRemover((ActionGroup)action, "Mark Directory As"),
                   new Constraints(Anchor.AFTER, "OpenModuleSettings"));
      }
    }
  }

  private static void setupSdks() {
    final Sdk sdk = findFirstCompatibleAndroidSdk();
    if (sdk != null) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          String androidHome = sdk.getHomePath();
          assert androidHome != null;
          DefaultSdks.createAndroidSdksForAllTargets(new File(FileUtil.toSystemDependentName(androidHome)));
        }
      });
      return;
    }
    // Called in a 'invokeLater' block, otherwise file chooser will hang forever.
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        File androidSdkPath = getAndroidSdkPath();
        if (androidSdkPath == null) {
          return;
        }
        Sdk sdk = AndroidSdkUtils.createNewAndroidPlatform(androidSdkPath.getPath(), true);
        if (sdk != null) {
          // Rename the SDK to fit our default naming convention.
          if (sdk.getName().startsWith(AndroidSdkUtils.SDK_NAME_PREFIX)) {
            SdkModificator sdkModificator = sdk.getSdkModificator();
            sdkModificator.setName(AndroidSdkUtils.SDK_NAME_PREFIX +
                                   sdk.getName().substring(AndroidSdkUtils.SDK_NAME_PREFIX.length()));
            sdkModificator.commitChanges();

            // Rename the JDK that goes along with this SDK.
            AndroidSdkAdditionalData additionalData = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();
            if (additionalData != null) {
              Sdk jdk = additionalData.getJavaSdk();
              if (jdk != null) {
                sdkModificator = jdk.getSdkModificator();
                sdkModificator.setName(AndroidSdkUtils.DEFAULT_JDK_NAME);
                sdkModificator.commitChanges();
              }
            }

            // Fill out any missing build APIs for this new SDK.
            DefaultSdks.createAndroidSdksForAllTargets(androidSdkPath);
          }
        }
      }
    });
  }

  @Nullable
  private static Sdk findFirstCompatibleAndroidSdk() {
    for (Sdk sdk : AndroidSdkUtils.getAllAndroidSdks()) {
      String sdkPath = sdk.getHomePath();
      if (VersionCheck.isCompatibleVersion(sdkPath)) {
        return sdk;
      }
    }
    return null;
  }

  @Nullable
  private static File getAndroidSdkPath() {
    String studioHome = PathManager.getHomePath();
    if (StringUtil.isEmpty(studioHome)) {
      LOG.info("Unable to find Studio home directory");
    }
    else {
      LOG.info(String.format("Found Studio home directory at: '%1$s'", studioHome));
      for (String path : ANDROID_SDK_RELATIVE_PATHS) {
        File dir = new File(studioHome, path);
        String absolutePath = dir.getAbsolutePath();
        LOG.info(String.format("Looking for Android SDK at '%1$s'", absolutePath));
        if (AndroidSdkType.getInstance().isValidSdkHome(absolutePath) && VersionCheck.isCompatibleVersion(dir)) {
          LOG.info(String.format("Found Android SDK at '%1$s'", absolutePath));
          return new File(absolutePath);
        }
      }
    }
    LOG.info("Unable to locate SDK within the Android studio installation.");

    String androidHomeValue = System.getenv(SdkConstants.ANDROID_HOME_ENV);
    String msg = String.format("Checking if ANDROID_HOME is set: '%1$s' is '%2$s'", SdkConstants.ANDROID_HOME_ENV, androidHomeValue);
    LOG.info(msg);

    if (!StringUtil.isEmpty(androidHomeValue) &&
        AndroidSdkType.getInstance().isValidSdkHome(androidHomeValue) &&
        VersionCheck.isCompatibleVersion(androidHomeValue)) {
      LOG.info("Using Android SDK specified by the environment variable.");
      return new File(FileUtil.toSystemDependentName(androidHomeValue));
    }

    String sdkPath = getLastSdkPathUsedByAndroidTools();
    if (!StringUtil.isEmpty(sdkPath) &&
        AndroidSdkType.getInstance().isValidSdkHome(androidHomeValue) &&
        VersionCheck.isCompatibleVersion(sdkPath)) {
      msg = String.format("Last SDK used by Android tools: '%1$s'", sdkPath);
    } else {
      msg = "Unable to locate last SDK used by Android tools";
    }
    LOG.info(msg);
    return sdkPath == null ? null : new File(FileUtil.toSystemDependentName(sdkPath));
  }

  /**
   * Returns the value for property 'lastSdkPath' as stored in the properties file at $HOME/.android/ddms.cfg, or {@code null} if the file
   * or property doesn't exist.
   *
   * This is only useful in a scenario where existing users of ADT/Eclipse get Studio, but without the bundle. This method duplicates some
   * functionality of {@link com.android.prefs.AndroidLocation} since we don't want any file system writes to happen during this process.
   */
  @Nullable
  private static String getLastSdkPathUsedByAndroidTools() {
    String userHome = SystemProperties.getUserHome();
    if (userHome == null) {
      return null;
    }
    File f = new File(new File(userHome, ".android"), "ddms.cfg");
    if (!f.exists()) {
      return null;
    }
    Properties properties = new Properties();
    FileInputStream fis = null;
    try {
      //noinspection IOResourceOpenedButNotSafelyClosed
      fis = new FileInputStream(f);
      properties.load(fis);
    } catch (IOException e) {
      return null;
    } finally {
      Closeables.closeQuietly(fis);
    }
    return properties.getProperty("lastSdkPath");
  }

  /**
   * Remove popup actions that we don't use
   */
  private static void hideIdeaNewFilePopupActions() {
    hideAction("NewHtmlFile", "HTML File");

    hideAction("NewPackageInfo", "package-info.java");

    // Hide designer actions
    hideAction("NewForm", "GUI Form");
    hideAction("NewDialog", "Dialog");
    hideAction("NewFormSnapshot", "Form Snapshot");

    // Hide individual actions that aren't part of a group
    replaceAction("Groovy.NewClass", new EmptyAction());
    replaceAction("Groovy.NewScript", new EmptyAction());
  }

  /**
   * Registers an appClosing callback on the app lifecycle.
   * Uses it to stop gradle daemons of currently opened projects.
   */
  private static void registerAppClosing() {
    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
    connection.subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener.Adapter() {
      @Override
      public void appClosing() {
        try {
          for (Project p : ProjectManager.getInstance().getOpenProjects()) {
            if (Projects.isBuildWithGradle(p)) {
              GradleUtil.stopAllGradleDaemons(false);
              return;
            }
          }
        }
        catch (IOException e) {
          LOG.error("Failed to stop Gradle daemons", e);
        }
      }
    });
  }


  private static void checkAndSetAndroidSdkSources() {
    for (Sdk sdk : AndroidSdkUtils.getAllAndroidSdks()) {
      checkAndSetSources(sdk);
    }
  }

  private static void checkAndSetSources(@NotNull Sdk sdk) {
    VirtualFile[] storedSources = sdk.getRootProvider().getFiles(OrderRootType.SOURCES);
    if (storedSources.length > 0) {
      return;
    }

    SdkAdditionalData sdkData = sdk.getSdkAdditionalData();
    if (sdkData instanceof AndroidSdkAdditionalData) {
      AndroidSdkAdditionalData androidSdkData = (AndroidSdkAdditionalData)sdkData;
      AndroidPlatform platform = androidSdkData.getAndroidPlatform();
      if (platform != null) {
        SdkModificator sdkModificator = sdk.getSdkModificator();
        IAndroidTarget target = platform.getTarget();
        AndroidSdkUtils.findAndSetPlatformSources(target, sdkModificator);
        sdkModificator.commitChanges();
      }
    }
  }
}
