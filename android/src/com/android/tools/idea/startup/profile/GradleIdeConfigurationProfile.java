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
package com.android.tools.idea.startup.profile;

import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.actions.*;
import com.android.tools.idea.startup.AndroidCodeStyleSettingsModifier;
import com.android.utils.Pair;
import com.google.common.collect.Sets;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInsight.intention.impl.config.IntentionManagerImpl;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.actions.TemplateProjectSettingsGroup;
import com.intellij.ide.projectView.actions.MarkRootGroup;
import com.intellij.ide.projectView.impl.MoveModuleToGroupTopLevel;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;

import static com.android.tools.idea.gradle.util.GradleUtil.stopAllGradleDaemons;
import static com.android.tools.idea.startup.Actions.*;
import static org.jetbrains.android.sdk.AndroidSdkUtils.findAndSetPlatformSources;
import static org.jetbrains.android.sdk.AndroidSdkUtils.getAllAndroidSdks;

/**
 * This is the <em>default</em> (Gradle-centric) configuration profile for Android Studio.
 */
public class GradleIdeConfigurationProfile implements IdeConfigurationProfile {
  @NonNls public static final String ID = "gradle";

  private static final Logger LOG = Logger.getInstance(GradleIdeConfigurationProfile.class);

  private static Set<String> unwantedIntentions = Sets.newHashSet("TestNGOrderEntryFix", "JCiPOrderEntryFix");

  @Override
  @NotNull
  public String getProfileId() {
    return ID;
  }

  @Override
  public void configureIde() {
    setUpNewProjectActions();
    setUpWelcomeScreenActions();
    setUpProjectStructureActions();
    replaceProjectPopupActions();

    ActionManager actionManager = ActionManager.getInstance();
    // "Configure Plugins..." Not sure why it's called StartupWizard.
    AnAction pluginAction = actionManager.getAction("StartupWizard");
    // Never applicable in the context of android studio, so just set to invisible.
    pluginAction.getTemplatePresentation().setVisible(false);

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
    ActionManager actionManager = ActionManager.getInstance();

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

    actionManager.getAction("WelcomeScreen.GetFromVcs").getTemplatePresentation().setText("Check out project from Version Control");
  }

  private static void setUpProjectStructureActions() {
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

  // Registers a callback that gets notified when the IDE is closing.
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
