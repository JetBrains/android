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
package com.android.tools.idea.gradle.quickfix;

import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.dsl.dependencies.ExternalDependencySpec;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.android.tools.idea.gradle.project.AndroidGradleNotification;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.android.tools.idea.gradle.service.notification.hyperlink.CustomNotificationListener;
import com.android.tools.idea.gradle.service.notification.hyperlink.OpenFileHyperlink;
import com.intellij.codeInsight.daemon.impl.actions.AddImportAction;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST;
import static com.android.tools.idea.gradle.dsl.dependencies.CommonConfigurationNames.*;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.psi.util.PsiUtilCore.getVirtualFile;

abstract class AbstractGradleDependencyFix extends AbstractGradleAwareFix {
  @NotNull protected final Module myModule;
  @NotNull protected final PsiReference myReference;
  @NotNull private VirtualFile myCurrentFile;
  private static final Logger LOG = Logger.getInstance(AbstractGradleDependencyFix.class);

  // TODO replace these two fields with com.android.tools.idea.gradle.dsl.parser.DependencyElement
  @Nullable protected String myAddedDependency;
  @Nullable protected String myAddedDependencyConfiguration;

  protected AbstractGradleDependencyFix(@NotNull Module module, @NotNull PsiReference reference) {
    myModule = module;
    myReference = reference;
    myCurrentFile = myReference.getElement().getContainingFile().getVirtualFile();
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, @Nullable Editor editor, @Nullable PsiFile file) {
    return !project.isDisposed() && !myModule.isDisposed();
  }

  static boolean isTestScope(@NotNull Module module, @NotNull PsiReference reference) {
    VirtualFile location = getVirtualFile(reference.getElement());
    return isTestScope(module, location);
  }

  static boolean isTestScope(@NotNull Module module, @Nullable VirtualFile location) {
    return location != null && ModuleRootManager.getInstance(module).getFileIndex().isInTestSourceContent(location);
  }

  @NotNull
  static String getConfigurationName(@NotNull Module module, boolean testScope) {
    if (testScope) {
      AndroidFacet androidFacet = AndroidFacet.getInstance(module);
      if (androidFacet != null) {
        AndroidGradleModel androidModel = AndroidGradleModel.get(androidFacet);
        String configurationName = TEST_COMPILE;
        if (androidModel != null && ARTIFACT_ANDROID_TEST.equals(androidModel.getSelectedTestArtifactName())) {
          configurationName = ANDROID_TEST_COMPILE;
        }
        return configurationName;
      }
    }
    return COMPILE;
  }

  void addDependencyAndSync(@NotNull final String configurationName,
                            @NotNull final ExternalDependencySpec dependency,
                            @NotNull final Computable<PsiClass[]> getTargetClasses,
                            @Nullable final Editor editor) {
    final GradleBuildModel buildModel = GradleBuildModel.get(myModule);
    if (buildModel == null) {
      return;
    }
    buildModel.dependencies().add(configurationName, dependency);
    GradleSyncListener listener = new GradleSyncListener.Adapter() {
      @Override
      public void syncSucceeded(@NotNull Project project) {
        runAddImportAction(project, getTargetClasses, editor);
      }

      @Override
      public void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
        runAddImportAction(project, getTargetClasses, editor);
      }
    };

    final Project project = myModule.getProject();
    runWriteCommandActionAndSync(project, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
        registerUndoAction(project);
        myAddedDependency = dependency.compactNotation();
        myAddedDependencyConfiguration = configurationName;
      }
    }, listener);
  }

  /**
   * Run gradle sync and add import statement to the source file after modifying gradle build file and trigger gradle
   * sync.
   *
   * @param project          current project.
   * @param action           the action to be run inside write command
   * @param getTargetClasses the callback to find resolved classes for the reference after sync is done.
   * @param editor           the editor in which the quick fix is invoked.
   */
  protected void runWriteCommandActionAndSync(@NotNull final Project project,
                                              @NotNull final Runnable action,
                                              @NotNull final Computable<PsiClass[]> getTargetClasses,
                                              @Nullable final Editor editor) {
    GradleSyncListener listener = new GradleSyncListener.Adapter() {
      @Override
      public void syncSucceeded(@NotNull Project project) {
        runAddImportAction(project, getTargetClasses, editor);
      }

      @Override
      public void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
        runAddImportAction(project, getTargetClasses, editor);
      }
    };

    runWriteCommandActionAndSync(project, action, listener);
  }

  private void runAddImportAction(@NotNull final Project project,
                                  @NotNull final Computable<PsiClass[]> getTargetClasses,
                                  @Nullable final Editor editor) {
    if (editor != null) {
      DumbService.getInstance(project).withAlternativeResolveEnabled(new Runnable() {
        @Override
        public void run() {
          PsiClass[] targetClasses = getTargetClasses.compute();
          if (targetClasses != null) {
            new AddImportAction(project, myReference, editor, targetClasses).execute();
          }
          else {
            GradleBuildFile gradleBuildFile = GradleBuildFile.get(myModule);
            // The quickfix won't get created if build file doesn't exist.
            assert gradleBuildFile != null;

            LOG.assertTrue(isNotEmpty(myAddedDependency) && isNotEmpty(myAddedDependencyConfiguration),
                           "Dependency is not recorded correctly by the quickfix: " + this.getClass().getName());

            OpenFileHyperlink buildFileHyperlink = new OpenFileHyperlink(gradleBuildFile.getFile().getPath(),
                                                                         gradleBuildFile.getFile().getName(), -1, -1);
            OpenFileHyperlink javaFileHyperlink = new OpenFileHyperlink(myCurrentFile.getPath(), myCurrentFile.getName(), -1, -1);

            String referenceName = myReference.getRangeInElement().substring(myReference.getElement().getText());

            NotificationListener notificationListener = new CustomNotificationListener(project, buildFileHyperlink, javaFileHyperlink);

            AndroidGradleNotification.getInstance(project).showBalloon(
              "Quick Fix Error",
              "Failed to add dependency. To manually fix this, please do the following:\n<ul>" +
              "<li>Add dependency '" + myAddedDependency + "' for configuration '" + myAddedDependencyConfiguration + "' in '" +
              buildFileHyperlink.toHtml() + "'.</li>\n" +
              "<li>Import class '" + referenceName + "' to '" + javaFileHyperlink.toHtml() + "'. </li></ul>",
              NotificationType.ERROR, notificationListener);
          }
        }
      });
    }
  }

}
