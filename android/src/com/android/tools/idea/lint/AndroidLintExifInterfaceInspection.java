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
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.templates.RepositoryUrlManager;
import com.android.tools.idea.templates.SupportLibrary;
import com.android.tools.lint.checks.ExifInterfaceDetector;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
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

import static com.android.tools.idea.gradle.dsl.model.dependencies.CommonConfigurationNames.COMPILE;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_PROJECT_MODIFIED;

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
                GradleSyncInvoker.Request request = new GradleSyncInvoker.Request().setGenerateSourcesOnSuccess(false).setTrigger(
                  TRIGGER_PROJECT_MODIFIED);
                GradleSyncInvoker.getInstance().requestProjectSync(project, request, new GradleSyncListener.Adapter() {
                  @Override
                  public void syncSucceeded(@NotNull Project project) {
                    DumbService.getInstance(project).runWhenSmart(() -> replaceReferences(startElement));
                  }
                });
              }
            }.execute();
          }
        }
        finally {
          action.finish();
        }
      }
    }

    private static String getExifLibraryCoordinate() {
      RepositoryUrlManager manager = RepositoryUrlManager.get();
      String libraryCoordinate = manager.getLibraryStringCoordinate(SupportLibrary.EXIF_INTERFACE, true);
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