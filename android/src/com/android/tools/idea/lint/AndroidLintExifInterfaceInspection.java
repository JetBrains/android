/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.lint;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.templates.RepositoryUrlManager;
import com.android.tools.lint.checks.ExifInterfaceDetector;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.android.inspections.lint.AndroidLintInspectionBase;
import org.jetbrains.android.inspections.lint.AndroidLintQuickFix;
import org.jetbrains.android.inspections.lint.AndroidQuickfixContexts;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.COMPILE;

public class AndroidLintExifInterfaceInspection extends AndroidLintInspectionBase {
  public static final String NEW_EXIT_INTERFACE = "android.support.media.ExifInterface";

  public AndroidLintExifInterfaceInspection() {
    super(AndroidBundle.message("android.lint.inspections.exif.interface"), ExifInterfaceDetector.ISSUE);
  }

  @NotNull
  @Override
  public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
    return new AndroidLintQuickFix[]{
      new ReplaceExifFix()
    };
  }

  private static class ReplaceExifFix extends DefaultLintQuickFix {
    public ReplaceExifFix() {
      super("Update all references in this file");
    }

    @Override
    public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context) {
      Module module = AndroidPsiUtils.getModuleSafely(startElement);
      if (module != null) {
        LocalHistoryAction action = LocalHistory.getInstance().startAction(getName());
        Project project = module.getProject();
        PsiClass cls = JavaPsiFacade.getInstance(project).findClass(NEW_EXIT_INTERFACE, GlobalSearchScope.allScope(project));
        if (cls != null) {
          replaceReferences(startElement);
          return;
        }

        // Add dependency first
        GradleBuildModel buildModel = GradleBuildModel.get(module);
        try {
          if (buildModel == null) {
            replaceReferences(startElement);
          }
          else {
            new WriteCommandAction(module.getProject(), getName()) {
              @Override
              protected void run(@NotNull Result result) throws Throwable {
                String libraryCoordinate = getExifLibraryCoordinate();
                if (libraryCoordinate != null) {
                  ModuleRootModificationUtil.updateModel(module, model -> {
                    GradleBuildModel buildModel = GradleBuildModel.get(module);
                    if (buildModel != null) {
                      String name = GradleUtil.mapConfigurationName(COMPILE, GradleUtil.getAndroidGradleModelVersionInUse(module), false);
                      buildModel.dependencies().addArtifact(name, libraryCoordinate);
                      buildModel.applyChanges();
                    }
                  });
                }

                syncAndReplaceReferences(project, startElement);
              }
            }.execute();
          }
        }
        finally {
          action.finish();
        }
      }
    }

    private void syncAndReplaceReferences(@NotNull Project project, @NotNull PsiElement startElement) {
      assert ApplicationManager.getApplication().isDispatchThread();

      ListenableFuture<ProjectSystemSyncManager.SyncResult> syncResult = ProjectSystemUtil.getProjectSystem(project)
        .getSyncManager().syncProject(ProjectSystemSyncManager.SyncReason.PROJECT_MODIFIED, false);

      Futures.addCallback(syncResult, new FutureCallback<ProjectSystemSyncManager.SyncResult>() {
        @Override
        public void onSuccess(@Nullable ProjectSystemSyncManager.SyncResult syncResult) {
          if (syncResult != null && syncResult.isSuccessful()) {
            DumbService.getInstance(project).runWhenSmart(() -> replaceReferences(startElement));
          }
        }

        @Override
        public void onFailure(@Nullable Throwable t) {
          throw new RuntimeException(t);
        }
      });
    }

    private static String getExifLibraryCoordinate() {
      RepositoryUrlManager manager = RepositoryUrlManager.get();
      String libraryCoordinate = manager.getArtifactStringCoordinate(GoogleMavenArtifactId.EXIF_INTERFACE, true);
      if (libraryCoordinate != null) {
        GradleCoordinate coordinate = GradleCoordinate.parseCoordinateString(libraryCoordinate);
        if (coordinate != null) {
          GradleVersion version = GradleVersion.tryParse(coordinate.getRevision());
          if (version != null && !version.isAtLeast(25, 1, 0)) {
            libraryCoordinate = coordinate.getGroupId() + ':' + coordinate.getArtifactId() + ":25.1.0";
          }
        }
      }
      return libraryCoordinate;
    }

    private void replaceReferences(@NotNull PsiElement element) {
      new WriteCommandAction(element.getProject(), getName()) {
        @Override
        protected void run(@NotNull Result result) throws Throwable {
          Project project = element.getProject();
          PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
          PsiClass cls = JavaPsiFacade.getInstance(project).findClass(NEW_EXIT_INTERFACE, GlobalSearchScope.allScope(project));
          PsiFile file = element.getContainingFile();
          file.accept(new JavaRecursiveElementVisitor() {
            @Override
            public void visitReferenceElement(PsiJavaCodeReferenceElement expression) {
              if (ExifInterfaceDetector.EXIF_INTERFACE.equals(expression.getReferenceName())) {
                if (expression.isQualified()) {
                  PsiElement context = expression.getParent();
                  if (expression instanceof PsiReferenceExpression) {
                    if (cls != null) {
                      PsiReferenceExpression replacement = factory.createReferenceExpression(cls);
                      expression.replace(replacement);
                      return;
                    }
                  } else {
                    expression.replace(factory.createReferenceFromText(NEW_EXIT_INTERFACE, context));
                    return;
                  }
                }
              }
              super.visitReferenceElement(expression);
            }
          });
        }
      }.execute();
    }
  }
}