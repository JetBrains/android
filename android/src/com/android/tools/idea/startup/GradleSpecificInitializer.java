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
import com.android.tools.idea.gradle.actions.AndroidTemplateProjectSettingsGroup;
import com.android.tools.idea.gradle.actions.AndroidTemplateProjectStructureAction;
import com.android.tools.idea.npw.WizardUtils;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.welcome.config.FirstRunWizardMode;
import com.android.tools.idea.welcome.wizard.AndroidStudioWelcomeScreenProvider;
import com.android.utils.Pair;
import com.google.common.collect.Sets;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInsight.intention.impl.config.IntentionManagerImpl;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.actions.TemplateProjectSettingsGroup;
import com.intellij.ide.projectView.actions.MarkRootGroup;
import com.intellij.ide.projectView.impl.MoveModuleToGroupTopLevel;
import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.util.SystemProperties;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.jetbrains.android.AndroidPlugin;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.android.tools.idea.gradle.util.PropertiesUtil.getProperties;
import static com.android.tools.idea.sdk.VersionCheck.isCompatibleVersion;
import static com.android.tools.idea.startup.Actions.*;
import static com.intellij.openapi.actionSystem.Anchor.AFTER;
import static com.intellij.openapi.util.io.FileUtil.toCanonicalPath;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static org.jetbrains.android.sdk.AndroidSdkUtils.*;

/** Performs Gradle-specific IDE initialization */
public class GradleSpecificInitializer implements Runnable {
  /**
   * We set the timeout for Gradle daemons to -1, this way IDEA will not set it to 1 minute and it will use the default instead (3 hours.)
   * We need to keep Gradle daemons around as much as possible because creating new daemons is resource-consuming and slows down the IDE.
   */
  public static final int GRADLE_DAEMON_TIMEOUT_MS = -1;
  static {
    System.setProperty("external.system.remote.process.idle.ttl.ms", String.valueOf(GRADLE_DAEMON_TIMEOUT_MS));
  }

  private static final Logger LOG = Logger.getInstance(GradleSpecificInitializer.class);

  @NonNls public static final String ENABLE_EXPERIMENTAL_ACTIONS = "enable.experimental.actions";

  // Paths relative to the IDE installation folder where the Android SDK may be present.
  @NonNls private static final String ANDROID_SDK_FOLDER_NAME = "sdk";
  private static final String[] ANDROID_SDK_RELATIVE_PATHS =
    {ANDROID_SDK_FOLDER_NAME, File.separator + ".." + File.separator + ANDROID_SDK_FOLDER_NAME};

  private static Set<String> unwantedIntentions = Sets.newHashSet("TestNGOrderEntryFix", "JCiPOrderEntryFix");


  @Override
  public void run() {
    setUpNewProjectActions();
    setUpWelcomeScreenActions();
    replaceProjectPopupActions();
    checkInstallPath();

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

    checkAndSetAndroidSdkSources();
    hideUnwantedIntentions();
  }

  /**
   * Gradle has an issue when the studio path contains ! (http://b.android.com/184588)
   */
  private static void checkInstallPath() {
    if (PathManager.getHomePath().contains("!")) {
      final Application app = ApplicationManager.getApplication();

      app.getMessageBus().connect(app).subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener.Adapter() {
        @Override
        public void appStarting(Project project) {
          app.invokeLater(new Runnable() {
            @Override
            public void run() {
              String message = String.format("%1$s must not be installed in a path containing '!' or Gradle sync will fail!",
                                             ApplicationNamesInfo.getInstance().getProductName());
              Notification notification = getNotificationGroup().createNotification(message, NotificationType.ERROR);
              notification.setImportant(true);
              Notifications.Bus.notify(notification);
            }
          });
        }
      });
    }
  }

  private static void setUpNewProjectActions() {
    // Unregister IntelliJ's version of the project actions and manually register our own.
    replaceAction("OpenFile", new AndroidOpenFileAction());
    replaceAction("NewProject", new AndroidNewProjectAction());
    replaceAction("NewModule", new AndroidNewModuleAction());
    replaceAction("NewModuleInGroup", new AndroidNewModuleInGroupAction());
    replaceAction("ImportProject", new AndroidImportProjectAction());
    replaceAction("CreateLibraryFromFile", new CreateLibraryFromFilesAction());
    replaceAction("ImportModule", new AndroidImportModuleAction());

    hideAction(IdeActions.ACTION_GENERATE_ANT_BUILD);
    hideAction("AddFrameworkSupport");
    hideAction("BuildArtifact");
    hideAction("RunTargetAction");
  }

  private static void setUpWelcomeScreenActions() {
    // Update the Welcome Screen actions
    replaceAction("WelcomeScreen.OpenProject", new AndroidOpenFileAction("Open an existing Android Studio project"));
    replaceAction("WelcomeScreen.CreateNewProject", new AndroidNewProjectAction("Start a new Android Studio project"));
    replaceAction("WelcomeScreen.ImportProject", new AndroidImportProjectAction("Import project (Eclipse ADT, Gradle, etc.)"));
    replaceAction("TemplateProjectStructure", new AndroidTemplateProjectStructureAction("Default Project Structure..."));

    moveAction("WelcomeScreen.ImportProject", "WelcomeScreen.QuickStart.IDEA",
               "WelcomeScreen.QuickStart", new Constraints(AFTER, "WelcomeScreen.GetFromVcs"));

    ActionManager actionManager = ActionManager.getInstance();
    AnAction getFromVcsAction = actionManager.getAction("WelcomeScreen.GetFromVcs");
    if (getFromVcsAction != null) {
      getFromVcsAction.getTemplatePresentation().setText("Check out project from Version Control");
    }

    AnAction configureIdeaAction = actionManager.getAction("WelcomeScreen.Configure.IDEA");
    if (configureIdeaAction instanceof DefaultActionGroup) {
      DefaultActionGroup settingsGroup = (DefaultActionGroup)configureIdeaAction;
      AnAction[] children = settingsGroup.getChildren(null);
      if (children.length == 1) {
        AnAction child = children[0];
        if (child instanceof TemplateProjectSettingsGroup) {
          settingsGroup.replaceAction(child, new AndroidTemplateProjectSettingsGroup());
        }
      }
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
                   new Constraints(AFTER, "OpenModuleSettings"));
      } else if (action instanceof MarkRootGroup) {
        parent.remove(action);
        parent.add(new AndroidActionGroupRemover((ActionGroup)action, "Mark Directory As"),
                   new Constraints(AFTER, "OpenModuleSettings"));
      }
    }
  }

  private static void notifyInvalidSdk() {
    final String key = "android.invalid.sdk.message";
    final String message = AndroidBundle.message(key);

    final NotificationListener listener = new NotificationListener.Adapter() {
      @Override
      protected void hyperlinkActivated(@NotNull Notification notification,
                                        @NotNull HyperlinkEvent e) {
        ActionManager actionManager = ActionManager.getInstance();
        AnAction sdkManagerAction = actionManager.getAction("Android.RunAndroidSdkManager");
        sdkManagerAction.actionPerformed(null);
        notification.expire();
      }
    };

    final Application app = ApplicationManager.getApplication();

    app.getMessageBus().connect(app).subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener.Adapter() {
      @Override
      public void appStarting(Project project) {
        app.invokeLater(new Runnable() {
          @Override
          public void run() {
            Notification notification = getNotificationGroup().createNotification("SDK Validation", message, NotificationType.WARNING, listener);
            notification.setImportant(true);
            Notifications.Bus.notify(notification);
          }
        });
      }
    });
  }

  private static NotificationGroup getNotificationGroup() {
    // Use the system health settings by default
    NotificationGroup group = NotificationGroup.findRegisteredGroup("System Health");
    if (group == null) {
      // This shouldn't happen
      group = new NotificationGroup("Gradle Initializer", NotificationDisplayType.STICKY_BALLOON, true);
    }
    return group;
  }

  private static void setupSdks() {
    File androidHome = IdeSdks.getAndroidSdkPath();

    if (androidHome != null) {
      WizardUtils.ValidationResult sdkValidationResult =
        WizardUtils.validateLocation(androidHome.getAbsolutePath(), "Android SDK location", false, false);
      if (sdkValidationResult.isError()) {
        notifyInvalidSdk();
      }

      // Do not prompt user to select SDK path (we have one already.) Instead, check SDK compatibility when a project is opened.
      return;
    }

    // If running in a GUI test we don't want the "Select SDK" dialog to show up when running GUI tests.
    if (AndroidPlugin.isGuiTestingMode()) {
      // This is good enough. Later on in the GUI test we'll validate the given SDK path.
      return;
    }

    Sdk sdk = findFirstCompatibleAndroidSdk();
    if (sdk != null) {
      String sdkHomePath = sdk.getHomePath();
      assert sdkHomePath != null;
      IdeSdks.createAndroidSdkPerAndroidTarget(new File(toSystemDependentName(sdkHomePath)));
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

  // Registers a callback that gets notified when the IDE is closing.
  private static void registerAppClosing() {
    Application app = ApplicationManager.getApplication();
    MessageBusConnection connection = app.getMessageBus().connect(app);
    connection.subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener.Adapter() {
      @Override
      public void appClosing() {
        try {
          DefaultGradleConnector.close();
        }
        catch (RuntimeException e) {
          LOG.info("Failed to stop Gradle daemons during IDE shutdown", e);
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

  // Disable the intentions that we don't want in android studio.
  private static void hideUnwantedIntentions() {
    IntentionManager intentionManager = IntentionManager.getInstance();
    if (!(intentionManager instanceof IntentionManagerImpl)) {
      return;
    }

    // IntentionManagerImpl.hasActiveRequests equals true when there is unprocessed register extension request, theoretically, it is
    // possible that two requests have a relative big time gap, so that hasActiveRequests could become true after first request has been
    // processed but the second one haven't been sent, thus cause us skipping the intentions we care about.
    // In reality this isn't problem so far because all the extension register requests are sent through a loop.
    // TODO Ideally, we want make IntentionManagerImpl.registerIntentionFromBean as protected method so we could override it and ignore the
    // unwanted intentions in the first place.
    while (((IntentionManagerImpl)intentionManager).hasActiveRequests()) {
      TimeoutUtil.sleep(100);
    }

    for (IntentionAction intentionAction : intentionManager.getIntentionActions()) {
      if (unwantedIntentions.contains(intentionAction.getClass().getSimpleName())) {
        intentionManager.unregisterIntention(intentionAction);
      }
    }
  }
}
