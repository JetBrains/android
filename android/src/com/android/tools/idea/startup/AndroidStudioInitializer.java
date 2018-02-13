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

import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.actions.CreateClassAction;
import com.android.tools.idea.actions.MakeIdeaModuleAction;
import com.android.tools.idea.stats.AndroidStudioUsageTracker;
import com.android.tools.idea.testartifacts.junit.AndroidJUnitConfigurationProducer;
import com.android.tools.idea.testartifacts.junit.AndroidJUnitConfigurationType;
import com.google.common.annotations.VisibleForTesting;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.intellij.concurrency.JobScheduler;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.junit.JUnitConfigurationProducer;
import com.intellij.execution.junit.JUnitConfigurationType;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationInfo;
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.PlatformUtils;
import org.intellij.plugins.intelliLang.inject.groovy.GrConcatenationInjector;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Arrays;

import static com.android.SdkConstants.EXT_JAR;
import static com.android.tools.idea.io.FilePaths.toSystemDependentPath;
import static com.android.tools.idea.startup.Actions.hideAction;
import static com.android.tools.idea.startup.Actions.replaceAction;
import static com.intellij.openapi.actionSystem.IdeActions.*;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.util.io.FileUtil.notNullize;
import static com.intellij.openapi.util.io.FileUtilRt.getExtension;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;

/**
 * Performs Android Studio specific initialization tasks that are build-system-independent.
 * <p>
 * <strong>Note:</strong> Do not add any additional tasks unless it is proven that the tasks are common to all IDEs. Use
 * {@link GradleSpecificInitializer} instead.
 * </p>
 */
public class AndroidStudioInitializer implements Runnable {
  @Override
  public void run() {
    checkInstallation();
    setUpNewFilePopupActions();
    setUpMakeActions();
    disableGroovyLanguageInjection();
    setUpNewProjectActions();
    setupAnalytics();
    disableIdeaJUnitConfigurations();
    hideRarelyUsedIntellijActions();
    renameSynchronizeAction();

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

  /*
   * sets up collection of Android Studio specific analytics.
   */
  private static void setupAnalytics() {
    AndroidStudioUsageTracker.setup(JobScheduler.getScheduler());
    ApplicationInfo application = ApplicationInfo.getInstance();
    UsageTracker.getInstance().setVersion(application.getStrictVersion());
    // Set the IDE brand when the android plugin is used in Studio.
    // See AndroidPlugin for the corresponding initialization when run in IntelliJ IDEA
    // We need to set this early enough in Studio before any events are logged.
    if (PlatformUtils.isAndroidStudio()) {
      UsageTracker.getInstance().setIdeBrand(
        Arrays.stream(PluginManagerCore.getPlugins()).anyMatch(plugin -> "Android Studio with Blaze".equals(plugin.getName()))
        ? AndroidStudioEvent.IdeBrand.ANDROID_STUDIO_WITH_BLAZE
        : AndroidStudioEvent.IdeBrand.ANDROID_STUDIO);
    }
  }

  private static void checkInstallation() {
    String studioHome = PathManager.getHomePath();
    if (isEmpty(studioHome)) {
      getLog().info("Unable to find Studio home directory");
      return;
    }
    File studioHomePath = toSystemDependentPath(studioHome);
    if (!studioHomePath.isDirectory()) {
      getLog().info(String.format("The path '%1$s' does not belong to an existing directory", studioHomePath.getPath()));
      return;
    }
    File androidPluginLibFolderPath = new File(studioHomePath, join("plugins", "android", "lib"));
    if (!androidPluginLibFolderPath.isDirectory()) {
      getLog().info(String.format("The path '%1$s' does not belong to an existing directory", androidPluginLibFolderPath.getPath()));
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
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
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

        getLog().info("Failed to disable 'org.intellij.plugins.intelliLang.inject.groovy.GrConcatenationInjector'");
      }
    });
  }

  private static void setUpNewProjectActions() {
    replaceAction("NewClass", new CreateClassAction());

    // Update the text for the file creation templates.
    FileTemplateManager fileTemplateManager = FileTemplateManager.getDefaultInstance();
    fileTemplateManager.getTemplate("Singleton").setText(fileTemplateManager.getJ2eeTemplate("Singleton").getText());
    for (String templateName : new String[]{"Class", "Interface", "Enum", "AnnotationType"}) {
      FileTemplate template = fileTemplateManager.getInternalTemplate(templateName);
      template.setText(fileTemplateManager.getJ2eeTemplate(templateName).getText());
    }
  }

  // JUnit original Extension JUnitConfigurationType is disabled so it can be replaced by its child class AndroidJUnitConfigurationType
  private static void disableIdeaJUnitConfigurations() {
    // First we unregister the ConfigurationProducers, and after the ConfigurationType
    ExtensionPoint<RunConfigurationProducer> configurationProducerExtensionPoint =
      Extensions.getRootArea().getExtensionPoint(RunConfigurationProducer.EP_NAME);
    for (RunConfigurationProducer runConfigurationProducer : configurationProducerExtensionPoint.getExtensions()) {
      if (runConfigurationProducer instanceof JUnitConfigurationProducer
          && !(runConfigurationProducer instanceof AndroidJUnitConfigurationProducer)) {
        // In AndroidStudio these ConfigurationProducer s are replaced
        configurationProducerExtensionPoint.unregisterExtension(runConfigurationProducer);
      }
    }

    ExtensionPoint<ConfigurationType> configurationTypeExtensionPoint =
      Extensions.getRootArea().getExtensionPoint(ConfigurationType.CONFIGURATION_TYPE_EP);
    for (ConfigurationType configurationType : configurationTypeExtensionPoint.getExtensions()) {
      if (configurationType instanceof JUnitConfigurationType && !(configurationType instanceof AndroidJUnitConfigurationType)) {
        // In Android Studio the user is forced to use AndroidJUnitConfigurationType instead of JUnitConfigurationType
        configurationTypeExtensionPoint.unregisterExtension(configurationType);
      }
    }

    // We hide actions registered by the JUnit plugin and instead we use those registered in android-junit.xml
    hideAction("excludeFromSuite");
    hideAction("AddToISuite");
  }

  private static void hideRarelyUsedIntellijActions() {
    // Hide the Save File as Template action due to its rare use in Studio.
    hideAction("SaveFileAsTemplate");
  }

  private static void renameSynchronizeAction() {
    // Rename the Synchronize action to Sync with File System to look better next to Sync Project with Gradle Files.
    AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_SYNCHRONIZE);
    action.getTemplatePresentation().setText("S_ync with File System", true);
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(AndroidStudioInitializer.class);
  }
}
