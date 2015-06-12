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
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.welcome.config.FirstRunWizardMode;
import com.android.tools.idea.welcome.wizard.AndroidStudioWelcomeScreenProvider;
import com.android.utils.Pair;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.actions.TemplateProjectSettingsGroup;
import com.intellij.ide.projectView.actions.MarkRootGroup;
import com.intellij.ide.projectView.impl.MoveModuleToGroupTopLevel;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.XmlHighlighterColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableEP;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.util.PlatformUtils;
import com.intellij.util.SystemProperties;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.android.AndroidPlugin;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Properties;

import static com.android.SdkConstants.EXT_JAR;
import static com.android.tools.idea.gradle.util.GradleUtil.cleanUpPreferences;
import static com.android.tools.idea.gradle.util.GradleUtil.stopAllGradleDaemons;
import static com.android.tools.idea.gradle.util.PropertiesUtil.getProperties;
import static com.android.tools.idea.sdk.VersionCheck.isCompatibleVersion;
import static com.intellij.openapi.options.Configurable.APPLICATION_CONFIGURABLE;
import static com.intellij.openapi.util.io.FileUtil.*;
import static com.intellij.openapi.util.io.FileUtilRt.getExtension;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static org.jetbrains.android.sdk.AndroidSdkUtils.*;

/** Initialization performed only in the context of the Android IDE. */
public class AndroidStudioSpecificInitializer implements Runnable {
  /**
   * We set the timeout for Gradle daemons to -1, this way IDEA will not set it to 1 minute and it will use the default instead (3 hours.)
   * We need to keep Gradle daemons around as much as possible because creating new daemons is resource-consuming and slows down the IDE.
   */
  public static final int GRADLE_DAEMON_TIMEOUT_MS = -1;
  static {
    System.setProperty("external.system.remote.process.idle.ttl.ms", String.valueOf(GRADLE_DAEMON_TIMEOUT_MS));
  }
  private static final Logger LOG = Logger.getInstance("#com.android.tools.idea.startup.AndroidStudioSpecificInitializer");
  private static final List<String> IDE_SETTINGS_TO_REMOVE = Lists.newArrayList(
    "org.jetbrains.plugins.javaFX.JavaFxSettingsConfigurable", "org.intellij.plugins.xpathView.XPathConfigurable",
    "org.intellij.lang.xpath.xslt.impl.XsltConfigImpl$UIImpl"
  );
  @NonNls private static final String USE_IDEA_NEW_PROJECT_WIZARDS = "use.idea.newProjectWizard";
  @NonNls private static final String USE_JPS_MAKE_ACTIONS = "use.idea.jpsMakeActions";
  @NonNls private static final String USE_IDEA_NEW_FILE_POPUPS = "use.idea.newFilePopupActions";
  @NonNls private static final String USE_IDEA_PROJECT_STRUCTURE = "use.idea.projectStructure";
  @NonNls public static final String ENABLE_EXPERIMENTAL_ACTIONS = "enable.experimental.actions";

  @NonNls private static final String ANDROID_SDK_FOLDER_NAME = "sdk";

  /** Paths relative to the IDE installation folder where the Android SDK maybe present. */
  private static final String[] ANDROID_SDK_RELATIVE_PATHS =
    { ANDROID_SDK_FOLDER_NAME, File.separator + ".." + File.separator + ANDROID_SDK_FOLDER_NAME,};

  public static boolean isAndroidStudio() {
    return "AndroidStudio".equals(PlatformUtils.getPlatformPrefix());
  }

  private static void checkInstallation() {
    String studioHome = PathManager.getHomePath();
    if (isEmpty(studioHome)) {
      LOG.info("Unable to find Studio home directory");
      return;
    }
    File studioHomePath = new File(toSystemDependentName(studioHome));
    if (!studioHomePath.isDirectory()) {
      LOG.info(String.format("The path '%1$s' does not belong to an existing directory", studioHomePath.getPath()));
      return;
    }
    File androidPluginLibFolderPath = new File(studioHomePath, join("plugins", "android", "lib"));
    if (!androidPluginLibFolderPath.isDirectory()) {
      LOG.info(String.format("The path '%1$s' does not belong to an existing directory", androidPluginLibFolderPath.getPath()));
      return;
    }

    // Look for signs that the installation is corrupt due to improper updates (typically unzipping on top of previous install)
    // which doesn't delete files that have been removed or renamed
    String cause = null;
    File[] children = notNullize(androidPluginLibFolderPath.listFiles());
    if (hasMoreThanOneBuilderModelFile(children)) {
      cause = "(Found multiple versions of builder-model-*.jar in plugins/android/lib.)";
    } else if (new File(studioHomePath, join("plugins", "android-designer")).exists()) {
      cause = "(Found plugins/android-designer which should not be present.)";
    }
    if (cause != null) {
      String msg = "Your Android Studio installation is corrupt and will not work properly.\n" +
                   cause + "\n" +
                   "This usually happens if Android Studio is extracted into an existing older version.\n\n" +
                   "Please reinstall (and make sure the new installation directory is empty first.)";
      String title = "Corrupt Installation";
      int option = Messages.showDialog(msg, title, new String[]{"Quit", "Proceed Anyway"}, 0, Messages.getErrorIcon());
      if (option == 0) {
        ApplicationManagerEx.getApplicationEx().exit();
      }
    }
  }

  @VisibleForTesting
  static boolean hasMoreThanOneBuilderModelFile(@NotNull File[] libraryFiles) {
    int builderModelFileCount = 0;

    for (File file : libraryFiles) {
      String fileName = file.getName();
      if (fileName.startsWith("builder-model-") && EXT_JAR.equals(getExtension(fileName))) {
        if (++builderModelFileCount > 1) {
          return true;
        }
      }
    }

    return false;
  }

  private static void cleanUpIdePreferences() {
    try {
      ExtensionPoint<ConfigurableEP<Configurable>> ideConfigurable = Extensions.getRootArea().getExtensionPoint(APPLICATION_CONFIGURABLE);
      cleanUpPreferences(ideConfigurable, IDE_SETTINGS_TO_REMOVE);
    }
    catch (Throwable e) {
      LOG.info("Failed to clean up IDE preferences", e);
    }
  }

  private static void replaceIdeaNewProjectActions() {
    // Unregister IntelliJ's version of the project actions and manually register our own.
    replaceAction("OpenFile", new AndroidOpenFileAction());
    replaceAction("NewProject", new AndroidNewProjectAction());
    replaceAction("NewModule", new AndroidNewModuleAction());
    replaceAction("NewModuleInGroup", new AndroidNewModuleInGroupAction());
    replaceAction("ImportProject", new AndroidImportProjectAction());
    replaceAction("CreateLibraryFromFile", new CreateLibraryFromFilesAction());
    replaceAction("ImportModule", new AndroidImportModuleAction());

    hideAction(IdeActions.ACTION_GENERATE_ANT_BUILD, "Generate Ant Build...");
    hideAction("AddFrameworkSupport", "Add Framework Support...");
    hideAction("BuildArtifact", "Build Artifacts...");
    hideAction("RunTargetAction", "Run Ant Target");

    replaceProjectPopupActions();
    replaceIdeaWelcomeScreenActions();
  }

  private static void replaceIdeaWelcomeScreenActions() {
    // Update the Welcome Screen actions
    ActionManager am = ActionManager.getInstance();

    AndroidOpenFileAction openFileAction = new AndroidOpenFileAction();
    openFileAction.getTemplatePresentation().setText("Open an existing Android Studio project");
    replaceAction("WelcomeScreen.OpenProject", openFileAction);

    AndroidNewProjectAction newProjectAction = new AndroidNewProjectAction();
    newProjectAction.getTemplatePresentation().setText("Start a new Android Studio project");
    replaceAction("WelcomeScreen.CreateNewProject", newProjectAction);

    AndroidImportProjectAction importProjectAction = new AndroidImportProjectAction();
    importProjectAction.getTemplatePresentation().setText("Import project (Eclipse ADT, Gradle, etc.)");
    replaceAction("WelcomeScreen.ImportProject", importProjectAction);
    moveAction("WelcomeScreen.ImportProject", "WelcomeScreen.QuickStart.IDEA", "WelcomeScreen.QuickStart",
               new Constraints(Anchor.AFTER, "WelcomeScreen.GetFromVcs"));

    am.getAction("WelcomeScreen.GetFromVcs").getTemplatePresentation().setText("Check out project from Version Control");
  }

  private static void replaceProjectStructureActions() {
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

  private static void moveAction(@NotNull String actionId, @NotNull String oldGroupId, @NotNull String groupId, @NotNull Constraints constraints) {
    ActionManager am = ActionManager.getInstance();
    AnAction action = am.getAction(actionId);
    AnAction group = am.getAction(groupId);
    AnAction oldGroup = am.getAction(oldGroupId);
    if (action != null && oldGroup != null && group != null && oldGroup instanceof DefaultActionGroup && group instanceof DefaultActionGroup) {
      ((DefaultActionGroup)oldGroup).getChildren(null); //call get children to resolve stubs
      ((DefaultActionGroup)oldGroup).remove(action);
      ((DefaultActionGroup)group).add(action, constraints);
    }
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
    File androidHome = IdeSdks.getAndroidSdkPath();
    if (androidHome != null) {
      // Do not prompt user to select SDK path (we have one already.) Instead, check SDK compatibility when a project is opened.
      return;
    }

    // If running in a GUI test we don't want the "Select SDK" dialog to show up when running GUI tests.
    if (AndroidPlugin.isGuiTestingMode()) {
      // This is good enough. Later on in the GUI test we'll validate the given SDK path.
      return;
    }

    final Sdk sdk = findFirstCompatibleAndroidSdk();
    if (sdk != null) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          String androidHome = sdk.getHomePath();
          assert androidHome != null;
          IdeSdks.createAndroidSdkPerAndroidTarget(new File(toSystemDependentName(androidHome)));
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

        FirstRunWizardMode wizardMode = AndroidStudioWelcomeScreenProvider.getWizardMode();
        // Only show "Select SDK" dialog if the "First Run" wizard is not displayed.
        boolean promptSdkSelection = wizardMode == null;

        Sdk sdk = createNewAndroidPlatform(androidSdkPath.getPath(), promptSdkSelection);
        if (sdk != null) {
          // Rename the SDK to fit our default naming convention.
          if (sdk.getName().startsWith(SDK_NAME_PREFIX)) {
            SdkModificator sdkModificator = sdk.getSdkModificator();
            sdkModificator.setName(SDK_NAME_PREFIX + sdk.getName().substring(SDK_NAME_PREFIX.length()));
            sdkModificator.commitChanges();

            // Rename the JDK that goes along with this SDK.
            AndroidSdkAdditionalData additionalData = getAndroidSdkAdditionalData(sdk);
            if (additionalData != null) {
              Sdk jdk = additionalData.getJavaSdk();
              if (jdk != null) {
                sdkModificator = jdk.getSdkModificator();
                sdkModificator.setName(DEFAULT_JDK_NAME);
                sdkModificator.commitChanges();
              }
            }

            // Fill out any missing build APIs for this new SDK.
            IdeSdks.createAndroidSdkPerAndroidTarget(androidSdkPath);
          }
        }
      }
    });
  }

  @Nullable
  private static Sdk findFirstCompatibleAndroidSdk() {
    List<Sdk> sdks = getAllAndroidSdks();
    for (Sdk sdk : sdks) {
      String sdkPath = sdk.getHomePath();
      if (isCompatibleVersion(sdkPath)) {
        return sdk;
      }
    }
    if (!sdks.isEmpty()) {
      return sdks.get(0);
    }
    return null;
  }

  @Nullable
  private static File getAndroidSdkPath() {
    String studioHome = PathManager.getHomePath();
    if (isEmpty(studioHome)) {
      LOG.info("Unable to find Studio home directory");
    }
    else {
      LOG.info(String.format("Found Studio home directory at: '%1$s'", studioHome));
      for (String path : ANDROID_SDK_RELATIVE_PATHS) {
        File dir = new File(studioHome, path);
        String absolutePath = toCanonicalPath(dir.getAbsolutePath());
        LOG.info(String.format("Looking for Android SDK at '%1$s'", absolutePath));
        if (AndroidSdkType.getInstance().isValidSdkHome(absolutePath)) {
          LOG.info(String.format("Found Android SDK at '%1$s'", absolutePath));
          return new File(absolutePath);
        }
      }
    }
    LOG.info("Unable to locate SDK within the Android studio installation.");

    String androidHomeValue = System.getenv(SdkConstants.ANDROID_HOME_ENV);
    String msg = String.format("Checking if ANDROID_HOME is set: '%1$s' is '%2$s'", SdkConstants.ANDROID_HOME_ENV, androidHomeValue);
    LOG.info(msg);

    if (!isEmpty(androidHomeValue) && AndroidSdkType.getInstance().isValidSdkHome(androidHomeValue)) {
      LOG.info("Using Android SDK specified by the environment variable.");
      return new File(toSystemDependentName(androidHomeValue));
    }

    String sdkPath = getLastSdkPathUsedByAndroidTools();
    if (!isEmpty(sdkPath) && AndroidSdkType.getInstance().isValidSdkHome(androidHomeValue)) {
      msg = String.format("Last SDK used by Android tools: '%1$s'", sdkPath);
    } else {
      msg = "Unable to locate last SDK used by Android tools";
    }
    LOG.info(msg);
    return sdkPath == null ? null : new File(toSystemDependentName(sdkPath));
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
    File file = new File(new File(userHome, ".android"), "ddms.cfg");
    if (!file.exists()) {
      return null;
    }
    try {
      Properties properties = getProperties(file);
      return properties.getProperty("lastSdkPath");
    } catch (IOException e) {
      return null;
    }
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
   * Registers an callback that gets notified when the IDE is closing.
   */
  private static void registerAppClosing() {
    Application app = ApplicationManager.getApplication();
    MessageBusConnection connection = app.getMessageBus().connect(app);
    connection.subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener.Adapter() {
      @Override
      public void appClosing() {
        try {
          stopAllGradleDaemons(false);
        }
        catch (IOException e) {
          LOG.info("Failed to stop Gradle daemons", e);
        }
      }
    });
  }

  private static void checkAndSetAndroidSdkSources() {
    for (Sdk sdk : getAllAndroidSdks()) {
      checkAndSetSources(sdk);
    }
  }

  private static void checkAndSetSources(@NotNull Sdk sdk) {
    VirtualFile[] storedSources = sdk.getRootProvider().getFiles(OrderRootType.SOURCES);
    if (storedSources.length > 0) {
      return;
    }

    AndroidPlatform platform = AndroidPlatform.getInstance(sdk);
    if (platform != null) {
      SdkModificator sdkModificator = sdk.getSdkModificator();
      IAndroidTarget target = platform.getTarget();
      findAndSetPlatformSources(target, sdkModificator);
      sdkModificator.commitChanges();
    }
  }

  @Override
  public void run() {
    checkInstallation();
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

    // Modify built-in "Default" color scheme to remove background from XML tags.
    // "Darcula" and user schemes will not be touched.
    EditorColorsScheme colorsScheme = EditorColorsManager.getInstance().getScheme(EditorColorsScheme.DEFAULT_SCHEME_NAME);
    TextAttributes textAttributes = colorsScheme.getAttributes(HighlighterColors.TEXT);
    TextAttributes xmlTagAttributes   = colorsScheme.getAttributes(XmlHighlighterColors.XML_TAG);
    xmlTagAttributes.setBackgroundColor(textAttributes.getBackgroundColor());

    checkAndSetAndroidSdkSources();
  }
}
