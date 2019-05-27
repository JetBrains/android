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

import static com.android.tools.idea.io.FilePaths.toSystemDependentPath;
import static com.android.tools.idea.startup.Actions.hideAction;
import static com.android.tools.idea.startup.Actions.replaceAction;
import static com.intellij.openapi.actionSystem.IdeActions.ACTION_COMPILE;
import static com.intellij.openapi.actionSystem.IdeActions.ACTION_COMPILE_PROJECT;
import static com.intellij.openapi.actionSystem.IdeActions.ACTION_MAKE_MODULE;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;

import com.android.tools.analytics.AnalyticsSettings;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.actions.CreateClassAction;
import com.android.tools.idea.actions.MakeIdeaModuleAction;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.stats.AndroidStudioUsageTracker;
import com.android.tools.idea.stats.GcPauseWatcher;
import com.android.tools.idea.testartifacts.junit.AndroidJUnitConfigurationProducer;
import com.android.tools.idea.testartifacts.junit.AndroidJUnitConfigurationType;
import com.android.tools.idea.ui.resourcemanager.actions.ShowFileInResourceManagerAction;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.intellij.concurrency.JobScheduler;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.junit.JUnitConfigurationProducer;
import com.intellij.execution.junit.JUnitConfigurationType;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
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
import com.intellij.ui.AppUIUtil;
import java.io.File;
import java.util.Arrays;
import org.intellij.plugins.intelliLang.inject.groovy.GrConcatenationInjector;
import org.jetbrains.annotations.NotNull;

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

    if (StudioFlags.CUSTOM_JAVA_NEW_CLASS_DIALOG.get()) {
      replaceNewClassDialog();
    }

    setupAnalytics();
    disableIdeaJUnitConfigurations();
    hideRarelyUsedIntellijActions();
    renameSynchronizeAction();
    setupResourceManagerActions();

    // Modify built-in "Default" color scheme to remove background from XML tags.
    // "Darcula" and user schemes will not be touched.
    EditorColorsScheme colorsScheme = EditorColorsManager.getInstance().getScheme(EditorColorsScheme.DEFAULT_SCHEME_NAME);
    TextAttributes textAttributes = colorsScheme.getAttributes(HighlighterColors.TEXT);
    TextAttributes xmlTagAttributes = colorsScheme.getAttributes(XmlHighlighterColors.XML_TAG);
    xmlTagAttributes.setBackgroundColor(textAttributes.getBackgroundColor());
  }

  private static void setupResourceManagerActions() {
    replaceAction("Images.ShowThumbnails", new ShowFileInResourceManagerAction());
  }

  /*
   * sets up collection of Android Studio specific analytics.
   */
  private static void setupAnalytics() {
    UsageStatisticsPersistenceComponent.getInstance().initializeAndroidStudioUsageTrackerAndPublisher();

    // If the user hasn't opted in, we will ask IJ to check if the user has
    // provided a decision on the statistics consent. If the user hasn't made a
    // choice, a modal dialog will be shown asking for a decision
    // before the regular IDE ui components are shown.
    if (!AnalyticsSettings.getOptedIn()) {
      Application application = ApplicationManager.getApplication();
      // If we're running in a test or headless mode, do not show the dialog
      // as it would block the test & IDE from proceeding.
      // NOTE: in this case the metrics logic will be left in the opted-out state
      // and no metrics are ever sent.
      if (!application.isUnitTestMode() && !application.isHeadlessEnvironment() &&
        !Boolean.getBoolean("disable.android.analytics.consent.dialog.for.test")) {
        AppUIUtil.showConsentsAgreementIfNeed(getLog());
      }
    }

    ApplicationInfo application = ApplicationInfo.getInstance();
    UsageTracker.setVersion(application.getStrictVersion());
    UsageTracker.setIdeBrand(getIdeBrand());
    if (ApplicationManager.getApplication().isInternal()) {
      UsageTracker.setIdeaIsInternal(true);
    }
    AndroidStudioUsageTracker.setup(JobScheduler.getScheduler());
    new GcPauseWatcher();
  }

  private static AndroidStudioEvent.IdeBrand getIdeBrand() {
    // The ASwB plugin name depends on the bundling scheme, in development builds it is "Android Studio with Blaze", but in release
    // builds, it is just "Blaze"
    return Arrays.stream(PluginManagerCore.getPlugins()).anyMatch(plugin -> plugin.isBundled() && plugin.getName().contains("Blaze"))
      ? AndroidStudioEvent.IdeBrand.ANDROID_STUDIO_WITH_BLAZE
      : AndroidStudioEvent.IdeBrand.ANDROID_STUDIO;
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
    if (new File(studioHomePath, join("plugins", "android-designer")).exists()) {
      String msg = "Your Android Studio installation is corrupt and will not work properly.\n" +
                   "(Found plugins/android-designer which should not be present.)\n" +
                   "This usually happens if Android Studio is extracted into an existing older version.\n\n" +
                   "Please reinstall (and make sure the new installation directory is empty first.)";
      String title = "Corrupt Installation";
      int option = Messages.showDialog(msg, title, new String[]{"Quit", "Proceed Anyway"}, 0, Messages.getErrorIcon());
      if (option == 0) {
        ApplicationManager.getApplication().exit();
      }
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
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectOpened(@NotNull Project project) {
        ExtensionPoint<MultiHostInjector> extensionPoint =
          Extensions.getArea(project).getExtensionPoint(MultiHostInjector.MULTIHOST_INJECTOR_EP_NAME);

        for (MultiHostInjector injector : extensionPoint.getExtensions()) {
          if (injector instanceof GrConcatenationInjector) {
            extensionPoint.unregisterExtension(injector.getClass());
            return;
          }
        }

        getLog().info("Failed to disable 'org.intellij.plugins.intelliLang.inject.groovy.GrConcatenationInjector'");
      }
    });
  }

  private static void replaceNewClassDialog() {
    replaceAction("NewClass", new CreateClassAction());

    // Update the text for the file creation templates.
    FileTemplateManager fileTemplateManager = FileTemplateManager.getDefaultInstance();
    for (String templateName : new String[]{"Singleton", "Class", "Interface", "Enum", "AnnotationType"}) {
      FileTemplate template = fileTemplateManager.getInternalTemplate(templateName);
      template.setText(fileTemplateManager.getJ2eeTemplate(templateName).getText());
    }
  }

  // JUnit original Extension JUnitConfigurationType is disabled so it can be replaced by its child class AndroidJUnitConfigurationType
  private static void disableIdeaJUnitConfigurations() {
    // First we unregister the ConfigurationProducers, and after the ConfigurationType
    ExtensionPoint<RunConfigurationProducer> configurationProducerExtensionPoint = RunConfigurationProducer.EP_NAME.getPoint(null);
    for (RunConfigurationProducer runConfigurationProducer : configurationProducerExtensionPoint.getExtensions()) {
      if (runConfigurationProducer instanceof JUnitConfigurationProducer
          && !(runConfigurationProducer instanceof AndroidJUnitConfigurationProducer)) {
        // In AndroidStudio these ConfigurationProducer s are replaced
        configurationProducerExtensionPoint.unregisterExtension(runConfigurationProducer.getClass());
      }
    }

    ExtensionPoint<ConfigurationType> configurationTypeExtensionPoint =
      Extensions.getRootArea().getExtensionPoint(ConfigurationType.CONFIGURATION_TYPE_EP);
    for (ConfigurationType configurationType : configurationTypeExtensionPoint.getExtensions()) {
      if (configurationType instanceof JUnitConfigurationType && !(configurationType instanceof AndroidJUnitConfigurationType)) {
        // In Android Studio the user is forced to use AndroidJUnitConfigurationType instead of JUnitConfigurationType
        configurationTypeExtensionPoint.unregisterExtension(configurationType.getClass());
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
