/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.android.tools.idea.actions.MakeIdeaModuleAction;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.welcome.config.FirstRunWizardMode;
import com.android.tools.idea.welcome.wizard.AndroidStudioWelcomeScreenProvider;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
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
import com.intellij.openapi.ui.Messages;
import com.intellij.util.PlatformUtils;
import com.intellij.util.SystemProperties;
import org.jetbrains.android.AndroidPlugin;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

import static com.android.SdkConstants.EXT_JAR;
import static com.android.tools.idea.gradle.util.GradleUtil.cleanUpPreferences;
import static com.android.tools.idea.gradle.util.PropertiesUtil.getProperties;
import static com.android.tools.idea.sdk.VersionCheck.isCompatibleVersion;
import static com.android.tools.idea.startup.Actions.hideAction;
import static com.android.tools.idea.startup.Actions.replaceAction;
import static com.intellij.openapi.actionSystem.IdeActions.*;
import static com.intellij.openapi.options.Configurable.APPLICATION_CONFIGURABLE;
import static com.intellij.openapi.util.io.FileUtil.*;
import static com.intellij.openapi.util.io.FileUtilRt.getExtension;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static org.jetbrains.android.sdk.AndroidSdkUtils.*;

/**
 * Performs Android Studio specific initialization tasks that are build-system-independent.
 * <p>
 * <strong>Note:</strong> Do not add any additional tasks unless it is proven that the tasks are common to all IDEs. Use
 * {@link GradleSpecificInitializer} instead.
 * </p>
 */
public class AndroidStudioInitializer implements Runnable {
  private static final Logger LOG = Logger.getInstance(AndroidStudioInitializer.class);

  private static final List<String> IDE_SETTINGS_TO_REMOVE = Lists.newArrayList("org.jetbrains.plugins.javaFX.JavaFxSettingsConfigurable",
                                                                                "org.intellij.plugins.xpathView.XPathConfigurable",
                                                                                "org.intellij.lang.xpath.xslt.impl.XsltConfigImpl$UIImpl");
  // Paths relative to the IDE installation folder where the Android SDK may be present.
  @NonNls private static final String ANDROID_SDK_FOLDER_NAME = "sdk";
  private static final String[] ANDROID_SDK_RELATIVE_PATHS = {
    ANDROID_SDK_FOLDER_NAME,
    File.separator + ".." + File.separator + ANDROID_SDK_FOLDER_NAME
  };

  public static boolean isAndroidStudio() {
    return "AndroidStudio".equals(PlatformUtils.getPlatformPrefix());
  }

  @Override
  public void run() {
    checkInstallation();
    removeIdeSettings();
    setUpNewFilePopupActions();
    setUpMakeActions();
    setUpSdksIfApplicable();

    // Modify built-in "Default" color scheme to remove background from XML tags.
    // "Darcula" and user schemes will not be touched.
    EditorColorsScheme colorsScheme = EditorColorsManager.getInstance().getScheme(EditorColorsScheme.DEFAULT_SCHEME_NAME);
    TextAttributes textAttributes = colorsScheme.getAttributes(HighlighterColors.TEXT);
    TextAttributes xmlTagAttributes   = colorsScheme.getAttributes(XmlHighlighterColors.XML_TAG);
    xmlTagAttributes.setBackgroundColor(textAttributes.getBackgroundColor());
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

  private static void removeIdeSettings() {
    try {
      ExtensionPoint<ConfigurableEP<Configurable>> ideConfigurable = Extensions.getRootArea().getExtensionPoint(APPLICATION_CONFIGURABLE);
      cleanUpPreferences(ideConfigurable, IDE_SETTINGS_TO_REMOVE);
    }
    catch (Throwable e) {
      LOG.info("Failed to clean up IDE preferences", e);
    }
  }

  // Remove popup actions that we don't use
  private static void setUpNewFilePopupActions() {
    hideAction("NewHtmlFile");
    hideAction("NewPackageInfo");

    // Hide designer actions
    hideAction("NewForm");
    hideAction("NewDialog");
    hideAction("NewFormSnapshot");

    // Hide individual actions that aren't part of a group
    hideAction("Groovy.NewClass");
    hideAction("Groovy.NewScript");
  }

  // The original actions will be visible only on plain IDEA projects.
  private static void setUpMakeActions() {
    // 'Build' > 'Make Project' action
    hideAction("CompileDirty");

    // 'Build' > 'Make Modules' action
    // We cannot simply hide this action, because of a NPE.
    replaceAction(ACTION_MAKE_MODULE, new MakeIdeaModuleAction());

    // 'Build' > 'Rebuild' action
    hideAction(ACTION_COMPILE_PROJECT);

    // 'Build' > 'Compile Modules' action
    hideAction(ACTION_COMPILE);
  }

  private static void setUpSdksIfApplicable() {
    // If running in a GUI test we don't want the "Select SDK" dialog to show up when running GUI tests.
    if (AndroidPlugin.isGuiTestingMode()) {
      // This is good enough. Later on in the GUI test we'll validate the given SDK path.
      return;
    }

    File androidSdkPath = IdeSdks.getAndroidSdkPath();
    if (androidSdkPath != null) {
      // Do not prompt user to select SDK path (we have one already.) Instead, check SDK compatibility when a project is opened.
      return;
    }

    try {
      // Setup JDK and Android SDK if necessary
      setUpSdks();
    } catch (Exception e) {
      LOG.error("Unexpected error while setting up SDKs: ", e);
    }
  }

  private static void setUpSdks() {
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
        File androidSdkPath = findOrGetAndroidSdkPath();
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
    return !sdks.isEmpty() ? sdks.get(0) : null;
  }

  @Nullable
  private static File findOrGetAndroidSdkPath() {
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
    return sdkPath != null ? new File(toSystemDependentName(sdkPath)) : null;
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
}
