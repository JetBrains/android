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

import com.android.tools.idea.actions.MakeIdeaModuleAction;
import com.android.tools.idea.monitor.tool.AndroidMonitorToolWindowFactory;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.androidstudio.actions.CreateClassAction;
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.SystemProperties;
import icons.AndroidIcons;
import org.intellij.plugins.intelliLang.inject.groovy.GrConcatenationInjector;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

import static com.android.SdkConstants.EXT_JAR;
import static com.android.tools.idea.gradle.util.GradleUtil.cleanUpPreferences;
import static com.android.tools.idea.startup.Actions.hideAction;
import static com.android.tools.idea.startup.Actions.replaceAction;
import static com.intellij.openapi.actionSystem.IdeActions.*;
import static com.intellij.openapi.options.Configurable.APPLICATION_CONFIGURABLE;
import static com.intellij.openapi.util.io.FileUtil.*;
import static com.intellij.openapi.util.io.FileUtilRt.getExtension;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.util.PlatformUtils.getPlatformPrefix;

/**
 * Performs Android Studio specific initialization tasks that are build-system-independent.
 * <p>
 * <strong>Note:</strong> Do not add any additional tasks unless it is proven that the tasks are common to all IDEs. Use
 * {@link GradleSpecificInitializer} instead.
 * </p>
 */
public class AndroidStudioInitializer implements Runnable {

  @NonNls public static final String ENABLE_EXPERIMENTAL_PROFILING = "enable.experimental.profiling";

  private static final Logger LOG = Logger.getInstance(AndroidStudioInitializer.class);

  private static final List<String> IDE_SETTINGS_TO_REMOVE = Lists.newArrayList("org.jetbrains.plugins.javaFX.JavaFxSettingsConfigurable",
                                                                                "org.intellij.plugins.xpathView.XPathConfigurable",
                                                                                "org.intellij.lang.xpath.xslt.impl.XsltConfigImpl$UIImpl");

  public static boolean isAndroidStudio() {
    return "AndroidStudio".equals(getPlatformPrefix());
  }

  public static boolean isAndroidSdkManagerEnabled() {
    boolean sdkManagerDisabled = SystemProperties.getBooleanProperty("android.studio.sdk.manager.disabled", false);
    return !sdkManagerDisabled;
  }

  @Override
  public void run() {
    checkInstallation();
    removeIdeSettings();
    setUpNewFilePopupActions();
    setUpMakeActions();
    disableGroovyLanguageInjection();
    setUpNewProjectActions();
    setUpExperimentalFeatures();

    // Modify built-in "Default" color scheme to remove background from XML tags.
    // "Darcula" and user schemes will not be touched.
    EditorColorsScheme colorsScheme = EditorColorsManager.getInstance().getScheme(EditorColorsScheme.DEFAULT_SCHEME_NAME);
    TextAttributes textAttributes = colorsScheme.getAttributes(HighlighterColors.TEXT);
    TextAttributes xmlTagAttributes = colorsScheme.getAttributes(XmlHighlighterColors.XML_TAG);
    xmlTagAttributes.setBackgroundColor(textAttributes.getBackgroundColor());

    /* Causes IDE startup failure (from the command line)
          Caused by: java.lang.IllegalAccessError: tried to access class com.intellij.ui.tabs.FileColorConfiguration from class com.intellij.ui.tabs.FileColorConfigurationUtil
          at com.intellij.ui.tabs.FileColorConfigurationUtil.createFileColorConfigurationIfNotExist(FileColorConfigurationUtil.java:37)
          at com.intellij.ui.tabs.FileColorConfigurationUtil.createAndroidTestFileColorConfigurationIfNotExist(FileColorConfigurationUtil.java:28)
          at com.android.tools.idea.startup.AndroidStudioInitializer.run(AndroidStudioInitializer.java:90)
    FileColorConfigurationUtil.createAndroidTestFileColorConfigurationIfNotExist(ProjectManager.getInstance().getDefaultProject());
     */
  }

  private void setUpExperimentalFeatures() {
    if (System.getProperty(ENABLE_EXPERIMENTAL_PROFILING) != null) {
      ProjectManager.getInstance().addProjectManagerListener(new ProjectManagerAdapter() {
        @Override
        public void projectOpened(final Project project) {
          StartupManager.getInstance(project).runWhenProjectIsInitialized(
            new Runnable() {
              @Override
              public void run() {
                ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
                ToolWindow toolWindow = toolWindowManager.registerToolWindow(AndroidMonitorToolWindowFactory.ID, false, ToolWindowAnchor.BOTTOM);
                toolWindow.setIcon(AndroidIcons.AndroidToolWindow);
                new AndroidMonitorToolWindowFactory().createToolWindowContent(project, toolWindow);
                toolWindow.show(null);
              }
            }
          );
        }
      });
    }
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
    }
    else if (new File(studioHomePath, join("plugins", "android-designer")).exists()) {
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

  // Fix https://code.google.com/p/android/issues/detail?id=201624
  private static void disableGroovyLanguageInjection() {
    ProjectManager.getInstance().addProjectManagerListener(new ProjectManagerAdapter() {
      @Override
      public void projectOpened(@NotNull Project project) {
        ExtensionPoint<MultiHostInjector> extensionPoint =
          Extensions.getArea(project).getExtensionPoint(MultiHostInjector.MULTIHOST_INJECTOR_EP_NAME);

        for (MultiHostInjector injector : extensionPoint.getExtensions()) {
          if (injector instanceof GrConcatenationInjector) {
            extensionPoint.unregisterExtension(injector);
            return;
          }
        }

        LOG.info("Failed to disable 'org.intellij.plugins.intelliLang.inject.groovy.GrConcatenationInjector'");
      }

    });
  }

  private static void setUpNewProjectActions() {
    replaceAction("NewClass", new CreateClassAction());
  }
}
