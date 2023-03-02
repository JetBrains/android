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

import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;

import com.android.tools.analytics.AnalyticsSettings;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.analytics.IdeBrandProviderKt;
import com.android.tools.idea.diagnostics.AndroidStudioSystemHealthMonitor;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.instrumentation.threading.ThreadingChecker;
import com.android.tools.idea.io.FilePaths;
import com.android.tools.idea.serverflags.ServerFlagDownloader;
import com.android.tools.idea.stats.AndroidStudioUsageTracker;
import com.android.tools.idea.stats.ConsentDialog;
import com.android.tools.idea.stats.GcPauseWatcher;
import com.google.common.base.Predicates;
import com.intellij.concurrency.JobScheduler;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.XmlHighlighterColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.AppUIUtil;
import java.io.File;
import java.util.concurrent.ScheduledExecutorService;
import org.intellij.plugins.intelliLang.inject.groovy.GrConcatenationInjector;
import org.jetbrains.annotations.NotNull;

/**
 * Performs Android Studio specific initialization tasks that are build-system-independent.
 * <p>
 * <strong>Note:</strong> Do not add any additional tasks unless it is proven that the tasks are common to all IDEs. Use
 * {@link GradleSpecificInitializer} instead.
 * </p>
 */
public class AndroidStudioInitializer implements ActionConfigurationCustomizer {

  @Override
  public void customize(@NotNull ActionManager actionManager) {
    checkInstallation();
    setUpNewFilePopupActions(actionManager);
    disableGroovyLanguageInjection();

    ScheduledExecutorService scheduler = JobScheduler.getScheduler();
    scheduler.execute(ServerFlagDownloader::downloadServerFlagList);

    setupAnalytics();
    setupThreadingAgentEventListener();
    hideRarelyUsedIntellijActions(actionManager);
    setupResourceManagerActions(actionManager);
    if (StudioFlags.TWEAK_COLOR_SCHEME.get()) {
      tweakDefaultColorScheme();
    }

    // Initialize System Health Monitor after Analytics and ServerFlag.
    // AndroidStudioSystemHealthMonitor requires ActionManager to be ready, but this code is a part
    // of its initialization. By pushing initialization to background thread, the thread will
    // block until ActionManager is ready and use its instance, instead of making another one.
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      if (AndroidStudioSystemHealthMonitor.getInstance() == null) {
        new AndroidStudioSystemHealthMonitor().start();
      }
    });
  }

  private static void tweakDefaultColorScheme() {
    // Modify built-in "Default" color scheme to remove background from XML tags.
    // "Darcula" and user schemes will not be touched.
    EditorColorsScheme colorsScheme = EditorColorsManager.getInstance().getScheme(EditorColorsScheme.DEFAULT_SCHEME_NAME);
    TextAttributes textAttributes = colorsScheme.getAttributes(HighlighterColors.TEXT);
    TextAttributes xmlTagAttributes = colorsScheme.getAttributes(XmlHighlighterColors.XML_TAG);
    xmlTagAttributes.setBackgroundColor(textAttributes.getBackgroundColor());
  }

  private static void setupResourceManagerActions(ActionManager actionManager) {
    Actions.hideAction(actionManager, "Images.ShowThumbnails");
    // Move the ShowServicesAction to the end of the queue by re-registering it, since it will always consume the shortcut event.
    // TODO(144579193): Remove this workaround when it's no longer necessary.
    //  Eg: When ShowServicesAction can decide whether it's enabled or not.
    AnAction servicesAction = actionManager.getAction("ServiceView.ShowServices");
    Actions.replaceAction(actionManager, "ServiceView.ShowServices", servicesAction);
  }

  /*
   * sets up collection of Android Studio specific analytics.
   */
  private static void setupAnalytics() {
    //AndroidStudioAnalytics.getInstance().initializeAndroidStudioUsageTrackerAndPublisher();

    if (StudioFlags.NEW_CONSENT_DIALOG.get()) {
      ConsentDialog.showConsentDialogIfNeeded();
    }
    else {
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
        if (!application.isUnitTestMode() && !application.isHeadlessEnvironment()) {
          ApplicationManager.getApplication().invokeLater(() -> AppUIUtil.showConsentsAgreementIfNeeded(getLog(), Predicates.alwaysTrue()));
        }
      }
    }

    ApplicationInfo application = ApplicationInfo.getInstance();
    UsageTracker.setVersion(application.getStrictVersion());
    UsageTracker.setIdeBrand(IdeBrandProviderKt.currentIdeBrand());
    if (ApplicationManager.getApplication().isInternal()) {
      UsageTracker.setIdeaIsInternal(true);
    }
    AndroidStudioUsageTracker.setup(JobScheduler.getScheduler());
    new GcPauseWatcher().setupWatcher();
  }

  private static void checkInstallation() {
    String studioHome = PathManager.getHomePath();
    if (isEmpty(studioHome)) {
      getLog().info("Unable to find Studio home directory");
      return;
    }
    File studioHomePath = FilePaths.stringToFile(studioHome);
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
      String msg = "Your " + ApplicationNamesInfo.getInstance().getFullProductName() + " installation is corrupt and will not work properly.\n" +
                   "(Found plugins/android-designer which should not be present.)\n" +
                   "This usually happens if "+ ApplicationNamesInfo.getInstance().getFullProductName() +" is extracted into an existing older version.\n\n" +
                   "Please reinstall (and make sure the new installation directory is empty first.)";
      String title = "Corrupt Installation";
      int option = Messages.showDialog(msg, title, new String[]{"Quit", "Proceed Anyway"}, 0, Messages.getErrorIcon());
      if (option == 0) {
        ApplicationManager.getApplication().exit();
      }
    }
  }

  // Remove popup actions that we don't use
  private static void setUpNewFilePopupActions(ActionManager actionManager) {
    Actions.hideAction(actionManager, "NewHtmlFile");
    Actions.hideAction(actionManager, "NewPackageInfo");

    // Hide designer actions
    Actions.hideAction(actionManager, "NewForm");
    Actions.hideAction(actionManager, "NewDialog");
    Actions.hideAction(actionManager, "NewFormSnapshot");

    // Hide individual actions that aren't part of a group
    Actions.hideAction(actionManager, "Groovy.NewClass");
    Actions.hideAction(actionManager, "Groovy.NewScript");
  }

  // Fix https://code.google.com/p/android/issues/detail?id=201624
  private static void disableGroovyLanguageInjection() {
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectOpened(@NotNull Project project) {
        ExtensionPoint<MultiHostInjector> extensionPoint = MultiHostInjector.MULTIHOST_INJECTOR_EP_NAME.getPoint(project);
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

  private static void hideRarelyUsedIntellijActions(ActionManager actionManager) {
    // Hide the Save File as Template action due to its rare use in Studio.
    Actions.hideAction(actionManager, "SaveFileAsTemplate");
  }

  private static void setupThreadingAgentEventListener() {
    ThreadingChecker.initialize();
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(AndroidStudioInitializer.class);
  }
}
