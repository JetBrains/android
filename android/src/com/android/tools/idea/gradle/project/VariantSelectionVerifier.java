/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.Variant;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.service.notification.CustomNotificationListener;
import com.android.tools.idea.gradle.service.notification.NotificationHyperlink;
import com.android.tools.idea.gradle.variant.view.BuildVariantView;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VariantSelectionVerifier {
  private static final Key<Conflict> VARIANT_SELECTION_CONFLICT_KEY = Key.create("android.variant.selection.conflict");

  @NotNull private final Project myProject;

  @NotNull
  public static VariantSelectionVerifier getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, VariantSelectionVerifier.class);
  }

  public VariantSelectionVerifier(@NotNull Project project) {
    myProject = project;
  }

  public void verifySelectedVariants() {
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    for (Module module : moduleManager.getModules()) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet == null || !facet.isGradleProject()) {
        continue;
      }

      IdeaAndroidProject androidProject = facet.getIdeaAndroidProject();
      assert androidProject != null;
      if (androidProject.getDelegate().isLibrary()) {
        Conflict conflict = findFirstConflict(module);
        if (conflict != null) {
          reportConflict(conflict);
          return;
        }
      }
    }
    storeConflict(null);
  }

  public void verifySelectedVariant(@NotNull Module updatedModule) {
    Conflict conflict = findFirstConflict(updatedModule);
    storeConflict(conflict);
    if (conflict != null) {
      reportConflict(conflict);
    }
  }

  @VisibleForTesting
  @Nullable
  static Conflict findFirstConflict(@NotNull Module module) {
    String gradlePath = getGradlePath(module);
    if (StringUtil.isEmpty(gradlePath)) {
      return null;
    }

    Variant variant = getSelectedVariant(module);
    if (variant == null) {
      return null;
    }

    String variantName = variant.getName();

    for (Module dependent : ModuleUtilCore.getAllDependentModules(module)) {
      AndroidFacet androidFacet = AndroidFacet.getInstance(dependent);
      if (androidFacet == null) {
        continue;
      }
      IdeaAndroidProject androidProject = androidFacet.getIdeaAndroidProject();
      if (androidProject == null) {
        continue;
      }
      for (AndroidLibrary library : androidProject.getSelectedVariant().getMainArtifact().getDependencies().getLibraries()) {
        if (!gradlePath.equals(library.getProject())) {
          continue;
        }
        String expected = library.getProjectVariant();
        if (StringUtil.isNotEmpty(expected) && !variantName.equals(expected)) {
          return new Conflict(module, dependent, expected, variantName);
        }
      }
    }
    return null;
  }

  @Nullable
  private static String getGradlePath(@NotNull Module module) {
    AndroidGradleFacet facet = AndroidGradleFacet.getInstance(module);
    return facet != null ? facet.getConfiguration().GRADLE_PROJECT_PATH : null;
  }

  @Nullable
  private static Variant getSelectedVariant(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet != null) {
      IdeaAndroidProject androidProject = facet.getIdeaAndroidProject();
      if (androidProject != null) {
        return androidProject.getSelectedVariant();
      }
    }
    return null;
  }

  private void reportConflict(@NotNull final Conflict conflict) {
    NotificationHyperlink quickFix = new NotificationHyperlink("fix.variant.selection", "Fix Now") {
      @Override
      protected void execute(@NotNull Project project) {
        conflict.myQuickFix.run();
      }
    };
    NotificationListener notificationListener = new CustomNotificationListener(myProject, quickFix);
    AndroidGradleNotification notification = AndroidGradleNotification.getInstance(myProject);
    String msg = conflict.myMessage + ". " + quickFix.toString();
    notification.showBalloon("Variant Selection Conflict", msg, NotificationType.ERROR, notificationListener);

    storeConflict(conflict);
    EditorNotifications.getInstance(myProject).updateAllNotifications();
    BuildVariantView.getInstance(myProject).updateNotification();
  }

  private void storeConflict(@Nullable Conflict conflict) {
    myProject.putUserData(VARIANT_SELECTION_CONFLICT_KEY, conflict);
  }

  @Nullable
  public Conflict getConflict() {
    return myProject.getUserData(VARIANT_SELECTION_CONFLICT_KEY);
  }

  public static class Conflict {
    @NotNull final Module mySource;
    @NotNull final Module myAffected;
    @NotNull final String myExpectedVariant;
    @NotNull final String mySelectedVariant;

    @NotNull final String myMessage;
    @NotNull final Runnable myQuickFix;

    Conflict(@NotNull Module source,
             @NotNull Module affected,
             @NotNull String expectedVariant,
             @NotNull String selectedVariant) {
      mySource = source;
      myAffected = affected;
      myExpectedVariant = expectedVariant;
      mySelectedVariant = selectedVariant;

      myMessage = String.format("Module '%1$s' expects module '%2$s' to have variant '%3$s' selected, but was '%4$s'", affected.getName(),
                                source.getName(), expectedVariant, selectedVariant);
      myQuickFix = new Runnable() {
        @Override
        public void run() {
          Project project = mySource.getProject();

          BuildVariantView buildVariantView = BuildVariantView.getInstance(project);
          buildVariantView.selectVariant(mySource, myExpectedVariant);
          buildVariantView.updateNotification();

          EditorNotifications.getInstance(project).updateAllNotifications();
          /* VariantSelectionVerifier */ getInstance(project).verifySelectedVariants();
        }
      };
    }

    @Nullable
    public EditorNotificationPanel createNotificationPanel() {
      return new NotificationPanel(this);
    }
  }

  private static class NotificationPanel extends EditorNotificationPanel {
    public NotificationPanel(@NotNull VariantSelectionVerifier.Conflict conflict) {
      setText(conflict.myMessage);
      createActionLabel("Fix Now", conflict.myQuickFix);
    }
  }
}
