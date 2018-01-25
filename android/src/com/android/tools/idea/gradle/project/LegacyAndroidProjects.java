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

import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.gradle.project.importing.OpenMigrationToGradleUrlHyperlink;
import com.android.tools.idea.project.AndroidNotification;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.stats.AnonymizerUtil;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventCategory.GRADLE;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.LEGACY_IDEA_ANDROID_PROJECT;
import static com.intellij.notification.NotificationType.WARNING;

class LegacyAndroidProjects {
  @NonNls private static final String SHOW_MIGRATE_TO_GRADLE_POPUP = "show.migrate.to.gradle.popup";

  @NotNull private final Project myProject;

  LegacyAndroidProjects(@NotNull Project project) {
    myProject = project;
  }

  @Nullable
  private static String getPackageNameInLegacyIdeaAndroidModule(@NotNull AndroidFacet facet) {
    // This invocation must happen after the project has been initialized.
    Manifest manifest = facet.getManifest();
    return manifest != null ? manifest.getPackage().getValue() : null;
  }

  void showMigrateToGradleWarning() {
    trackProject();
    if (!shouldShowMigrateToGradleNotification()) {
      return;
    }
    String errMsg = "This project does not use the Gradle build system. We recommend that you migrate to using the Gradle build system.";
    NotificationHyperlink doNotShowAgainHyperlink = new NotificationHyperlink("do.not.show", "Don't show this message again.") {
      @Override
      protected void execute(@NotNull Project project) {
        PropertiesComponent.getInstance(myProject).setValue(SHOW_MIGRATE_TO_GRADLE_POPUP, "false");
      }
    };

    AndroidNotification notification = AndroidNotification.getInstance(myProject);
    notification.showBalloon("Migrate Project to Gradle?", errMsg, WARNING, new OpenMigrationToGradleUrlHyperlink().setCloseOnClick(true),
                             doNotShowAgainHyperlink);
  }

  void trackProject() {
    UsageTracker usageTracker = UsageTracker.getInstance();
    if (!usageTracker.getAnalyticsSettings().hasOptedIn()) {
      return;
    }

    StartupManager.getInstance(myProject).runWhenProjectIsInitialized(() -> {
      String packageName = null;

      for (Module module : ModuleManager.getInstance(myProject).getModules()) {
        AndroidFacet facet = AndroidFacet.getInstance(module);
        if (facet != null && !facet.requiresAndroidModel()) {
          if (facet.getConfiguration().isAppProject()) {
            // Prefer the package name from an app module.
            packageName = getPackageNameInLegacyIdeaAndroidModule(facet);
            if (packageName != null) {
              break;
            }
          }
          else if (packageName == null) {
            String modulePackageName = getPackageNameInLegacyIdeaAndroidModule(facet);
            if (modulePackageName != null) {
              packageName = modulePackageName;
            }
          }
        }
        if (packageName != null) {
          AndroidStudioEvent.Builder event = AndroidStudioEvent.newBuilder();
          event.setCategory(GRADLE).setKind(LEGACY_IDEA_ANDROID_PROJECT).setProjectId(AnonymizerUtil.anonymizeUtf8(packageName));
          usageTracker.log(event);
        }
      }
    });
  }

  private boolean shouldShowMigrateToGradleNotification() {
    return PropertiesComponent.getInstance(myProject).getBoolean(SHOW_MIGRATE_TO_GRADLE_POPUP, true);
  }
}
