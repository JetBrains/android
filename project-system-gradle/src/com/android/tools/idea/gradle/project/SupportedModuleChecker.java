/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import static com.android.tools.idea.gradle.util.GradleProjectSystemUtil.GRADLE_SYSTEM_ID;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_QF_REMOVE_UNSUPPORTED_MODULES;
import static com.intellij.notification.NotificationType.ERROR;
import static com.intellij.openapi.util.text.StringUtil.join;

import com.android.tools.idea.project.AndroidNotification;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.projectsystem.ProjectSystemService;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModulePointer;
import com.intellij.openapi.module.ModulePointerManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.platform.workspace.jps.entities.ModuleEntity;
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import javax.swing.event.HyperlinkEvent;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.kotlin.idea.core.script.KotlinScriptEntitySource;

public class SupportedModuleChecker {

  @NotNull
  public static SupportedModuleChecker getInstance() {
    return ApplicationManager.getApplication().getService(SupportedModuleChecker.class);
  }

  /**
   * Verifies that the project, if it is an Android Gradle project, does not have any modules that are not known by Gradle. For example,
   * when adding a plain IDEA Java module.
   * Do not call this method from {@link ModuleListener#moduleAdded(Project, Module)} because the settings that this method look for are
   * not present when importing a valid Gradle-aware module, resulting in false positives.
   */
  public void checkForSupportedModules(@NotNull Project project) {
    Module[] modules = ModuleManager.getInstance(project).getModules();
    if (modules.length == 0 || !(ProjectSystemUtil.getProjectSystem(project) instanceof GradleProjectSystem)) {
      return;
    }
    List<Module> unsupportedModules = new ArrayList<>();
    boolean androidGradleSeen = false;
    for (Module module : modules) {
      if (isKotlinScriptModule(module)) {
        continue;
      }
      ModuleType moduleType = ModuleType.get(module);
      if (moduleType instanceof JavaModuleType) {
        String externalSystemId = ExternalSystemModulePropertyManager.getInstance(module).getExternalSystemId();
        if (!GRADLE_SYSTEM_ID.getId().equals(externalSystemId)) {
          unsupportedModules.add(module);
        }
        else
          androidGradleSeen = true;
      }
    }

    if (!androidGradleSeen || unsupportedModules.isEmpty()) {
      return;
    }
    displayUnsupportedModulesNotification(project, unsupportedModules);
  }

  private boolean isKotlinScriptModule(@NotNull Module module) {
    ModuleBridge moduleBridge = (ModuleBridge)module;
    ModuleEntity resolved = moduleBridge.getEntityStorage().getCurrent().resolve(moduleBridge.getModuleEntityId());
    if (resolved == null) {
      return false;
    }

    return resolved.getEntitySource() instanceof KotlinScriptEntitySource;
  }

  private void displayUnsupportedModulesNotification(Project project, List<Module> unsupportedModules) {
    String moduleNames = join(unsupportedModules, Module::getName, ", ");
    String notificationTitle = AndroidBundle.message("project.sync.unsupported.modules.detected.title");
    String notificationMessage = AndroidBundle.message("project.sync.unsupported.modules.detected.message", moduleNames);
    String notificationButton = AndroidBundle.message("project.sync.unsupported.modules.detected.button");
    ModulePointerManager modulePointerManager = ModulePointerManager.getInstance(project);
    List<ModulePointer> unsupportedModulePointers = unsupportedModules.stream().map(modulePointerManager::create).toList();
    UnsupportedModulesQuickFix unsupportedModulesQuickFix = new UnsupportedModulesQuickFix(notificationButton, unsupportedModulePointers);
    AndroidNotification.getInstance(project).showBalloon(notificationTitle, notificationMessage, ERROR, unsupportedModulesQuickFix);
  }

  @VisibleForTesting
  public static class UnsupportedModulesQuickFix extends NotificationHyperlink {

    private final List<ModulePointer> unsupportedModules;

    @VisibleForTesting
    public Stream<Module> getUnsupportedModules() {
      return unsupportedModules.stream().map(ModulePointer::getModule).filter(Objects::nonNull);
    }

    protected UnsupportedModulesQuickFix(String text, List<ModulePointer> unsupportedModules) {
      super("unsupported.modules.quick.fix", text);
      this.unsupportedModules = unsupportedModules;
    }

    @Override
    public boolean executeIfClicked(@NotNull Project project, @NotNull HyperlinkEvent event) {
      execute(project);
      return false;
    }

    @Override
    protected void execute(@NotNull Project project) {
      ApplicationManager.getApplication().runWriteAction(() -> {
        ModuleManager moduleManager = ModuleManager.getInstance(project);
        ModifiableModuleModel modifiableModule = moduleManager.getModifiableModel();
        getUnsupportedModules().forEach(modifiableModule::disposeModule);
        modifiableModule.commit();
      });
      ProjectSystemService.getInstance(project).getProjectSystem().getSyncManager()
        .requestSyncProject(new ProjectSystemSyncManager.SyncReason(TRIGGER_QF_REMOVE_UNSUPPORTED_MODULES));
    }
  }
}
