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

import static org.jetbrains.android.sdk.AndroidSdkUtils.createNewAndroidPlatform;

import com.android.sdklib.IAndroidTarget;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.io.FilePaths;
import com.android.tools.idea.projectsystem.gradle.IdeGooglePlaySdkIndex;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.android.tools.idea.ui.validation.validators.PathValidator;
import com.android.tools.idea.welcome.config.FirstRunWizardMode;
import com.android.tools.idea.welcome.wizard.AndroidStudioWelcomeScreenProvider;
import com.android.tools.lint.checks.GradleDetector;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ConfigImportHelper;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.List;
import javax.swing.event.HyperlinkEvent;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidPlatforms;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.project.CommonGradleProjectResolverExtension;

/**
 * Performs Gradle-specific IDE initialization
 */
public class GradleSpecificInitializer implements ActionConfigurationCustomizer {

  private static final Logger LOG = Logger.getInstance(GradleSpecificInitializer.class);

  @Override
  public void customize(@NotNull ActionManager actionManager) {
    checkInstallPath();

    if (AndroidSdkUtils.isAndroidSdkManagerEnabled()) {
      try {
        // Setup JDK and Android SDK if necessary
        setupSdks();
      }
      catch (Exception e) {
        LOG.error("Unexpected error while setting up SDKs: ", e);
      }
      checkAndSetAndroidSdkSources();
    }

    // Recreate JDKs since they can be invalid when changing Java versions (b/185562147)
    IdeInfo ideInfo = IdeInfo.getInstance();
    if (ConfigImportHelper.isConfigImported() && (ideInfo.isAndroidStudio() || ideInfo.isGameTools())) {
      ApplicationManager.getApplication().invokeLaterOnWriteThread(IdeSdks.getInstance()::recreateProjectJdkTable);
    }

    useIdeGooglePlaySdkIndexInGradleDetector();

    //Switch on Idea native navigation/suggestion for version catalog/gradle
    Registry.get(CommonGradleProjectResolverExtension.GRADLE_VERSION_CATALOGS_DYNAMIC_SUPPORT)
      .setValue(StudioFlags.GRADLE_VERSION_CATALOG_EXTENDED_SUPPORT.get());
  }

  /**
   * Gradle has an issue when the studio path contains ! (http://b.android.com/184588)
   */
  private static void checkInstallPath() {
    if (PathManager.getHomePath().contains("!")) {
      String message = String.format(
        "%1$s must not be installed in a path containing '!' or Gradle sync will fail!",
        ApplicationNamesInfo.getInstance().getProductName());
      Notification notification = getNotificationGroup().createNotification(message, NotificationType.ERROR);
      notification.setImportant(true);
      Notifications.Bus.notify(notification);
    }
  }

  private static void notifyInvalidSdk() {
    String key = "android.invalid.sdk.message";
    String message = AndroidBundle.message(key);

    NotificationListener listener = new NotificationListener.Adapter() {
      @Override
      protected void hyperlinkActivated(@NotNull Notification notification,
                                        @NotNull HyperlinkEvent e) {
        SdkQuickfixUtils.showAndroidSdkManager();
        notification.expire();
      }
    };
    addStartupWarning(message, listener);
  }

  private static void addStartupWarning(@NotNull final String message, @Nullable final NotificationListener listener) {
    Notification notification = getNotificationGroup().createNotification("SDK Validation", message, NotificationType.WARNING, listener);
    notification.setImportant(true);
    Notifications.Bus.notify(notification);
  }

  private static NotificationGroup getNotificationGroup() {
    // Use the system health settings by default
    NotificationGroup group = NotificationGroup.findRegisteredGroup("System Health");
    if (group == null) {
      // This shouldn't happen
      group = new NotificationGroup(
        "Gradle Initializer", NotificationDisplayType.STICKY_BALLOON, true, null, null, null, PluginId.getId("org.jetbrains.android"));
    }
    return group;
  }

  private static void setupSdks() {
    IdeSdks ideSdks = IdeSdks.getInstance();
    File androidHome = ideSdks.getAndroidSdkPath();

    if (androidHome != null) {
      Validator.Result result = PathValidator.forAndroidSdkLocation().validate(androidHome.toPath());
      Validator.Severity severity = result.getSeverity();

      if (severity == Validator.Severity.ERROR) {
        notifyInvalidSdk();
      }

      // Do not prompt user to select SDK path (we have one already.) Instead, check SDK compatibility when a project is opened.
      return;
    }

    Sdk sdk = findFirstAndroidSdk();
    if (sdk != null) {
      String sdkHomePath = sdk.getHomePath();
      assert sdkHomePath != null;
      ApplicationManager.getApplication().invokeLaterOnWriteThread(() -> {
        ideSdks.createAndroidSdkPerAndroidTarget(FilePaths.stringToFile(sdkHomePath));
      });
      return;
    }

    // Called in a 'invokeLater' block, otherwise file chooser will hang forever.
    ApplicationManager.getApplication().invokeLater(() -> {
      File androidSdkPath = getAndroidSdkPath();
      if (androidSdkPath == null) {
        return;
      }

      FirstRunWizardMode wizardMode = AndroidStudioWelcomeScreenProvider.getWizardMode();
      // Only show "Select SDK" dialog if the "First Run" wizard is not displayed.
      boolean promptSdkSelection = wizardMode == null;

      Sdk sdk1 = createNewAndroidPlatform(androidSdkPath.getPath(), promptSdkSelection);
      if (sdk1 != null) {
        // Rename the SDK to fit our default naming convention.
        String sdkNamePrefix = AndroidSdks.SDK_NAME_PREFIX;
        if (sdk1.getName().startsWith(sdkNamePrefix)) {
          SdkModificator sdkModificator = sdk1.getSdkModificator();
          sdkModificator.setName(sdkNamePrefix + sdk1.getName().substring(sdkNamePrefix.length()));
          sdkModificator.commitChanges();

          // Fill out any missing build APIs for this new SDK.
          ideSdks.createAndroidSdkPerAndroidTarget(androidSdkPath);
        }
      }
    });
  }

  @Nullable
  private static Sdk findFirstAndroidSdk() {
    List<Sdk> sdks = AndroidSdks.getInstance().getAllAndroidSdks();
    if (!sdks.isEmpty()) {
      return sdks.get(0);
    }
    return null;
  }

  @Nullable
  private static File getAndroidSdkPath() {
    return AndroidSdkInitializer.findValidAndroidSdkPath();
  }

  /**
   * Checks each of the sdk sources, and commits changes asynchronously if the calling thread is not the write thread.
   */
  private static void checkAndSetAndroidSdkSources() {
    for (Sdk sdk : AndroidSdks.getInstance().getAllAndroidSdks()) {
      checkAndSetSources(sdk);
    }
  }

  /**
   * Checks platform sources, and commits changes asynchronously if the calling thread is not the write thread.
   */
  private static void checkAndSetSources(@NotNull Sdk sdk) {
    VirtualFile[] storedSources = sdk.getRootProvider().getFiles(OrderRootType.SOURCES);
    if (storedSources.length > 0) {
      return;
    }

    AndroidPlatform platform = AndroidPlatforms.getInstance(sdk);
    if (platform != null) {
      if (ApplicationManager.getApplication().isWriteThread()) {
        setSources(sdk, platform);
      }
      else {
        // We don't wait for EDT as there would be a deadlock at startup.
        ApplicationManager.getApplication().invokeLaterOnWriteThread(
          () -> {
            setSources(sdk, platform);
          }
        );
      }
    }
  }

  private static void setSources(@NotNull Sdk sdk, @NotNull AndroidPlatform platform) {
    SdkModificator sdkModificator = sdk.getSdkModificator();
    IAndroidTarget target = platform.getTarget();
    AndroidSdks.getInstance().findAndSetPlatformSources(target, sdkModificator);
    sdkModificator.commitChanges();
  }

  private void useIdeGooglePlaySdkIndexInGradleDetector() {
    GradleDetector.setPlaySdkIndexFactory((path, client) -> {
      IdeGooglePlaySdkIndex playIndex = IdeGooglePlaySdkIndex.INSTANCE;
      playIndex.initializeAndSetFlags();
      return playIndex;
    });
  }
}
