/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.lint.inspections;

import com.android.ide.common.gradle.Component;
import com.android.ide.common.gradle.Version;
import com.android.ide.common.repository.GoogleMavenArtifactId;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.support.AndroidxName;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.gradle.repositories.RepositoryUrlManager;
import com.android.tools.idea.lint.AndroidLintBundle;
import com.android.tools.idea.lint.common.AndroidLintInspectionBase;
import com.android.tools.idea.lint.common.AndroidQuickfixContexts;
import com.android.tools.idea.lint.common.DefaultLintQuickFix;
import com.android.tools.idea.lint.common.LintIdeQuickFix;
import com.android.tools.idea.projectsystem.DependencyType;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.lint.checks.ExifInterfaceDetector;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.search.GlobalSearchScope;
import java.util.concurrent.CancellationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidLintExifInterfaceInspection extends AndroidLintInspectionBase {
  private static final AndroidxName
    NEW_EXIF_INTERFACE = new AndroidxName("android.support.media.ExifInterface", "androidx.exifinterface.media.ExifInterface");

  public AndroidLintExifInterfaceInspection() {
    super(AndroidLintBundle.message("android.lint.inspections.exif.interface"), ExifInterfaceDetector.ISSUE);
  }

  @NotNull
  @Override
  public LintIdeQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
    return new LintIdeQuickFix[]{
      new ReplaceExifFix()
    };
  }

  private static class ReplaceExifFix extends DefaultLintQuickFix {
    ReplaceExifFix() {
      super("Update all references in this file");
    }

    @Override
    public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context) {
      Module module = AndroidPsiUtils.getModuleSafely(startElement);
      if (module != null) {
        LocalHistoryAction action = LocalHistory.getInstance().startAction(getName());
        Project project = module.getProject();
        PsiClass cls = JavaPsiFacade.getInstance(project).findClass(NEW_EXIF_INTERFACE.newName(), GlobalSearchScope.allScope(project));
        if (cls == null) {
          cls = JavaPsiFacade.getInstance(project).findClass(NEW_EXIF_INTERFACE.oldName(), GlobalSearchScope.allScope(project));
        }
        if (cls != null) {
          replaceReferences(getName(), startElement, cls, false);
          return;
        }

        GradleCoordinate libraryCoordinate = getExifLibraryCoordinate();
        boolean useAndroidx =
          libraryCoordinate != null && libraryCoordinate.toString().startsWith(GoogleMavenArtifactId.ANDROIDX_EXIF_INTERFACE.getMavenGroupId());
        try {
          WriteCommandAction.writeCommandAction(module.getProject()).withName(getName()).run(() -> {
            if (libraryCoordinate != null) {
              ProjectSystemUtil.getModuleSystem(module).registerDependency(libraryCoordinate, DependencyType.IMPLEMENTATION);
            }

            syncAndReplaceReferences(project, startElement, useAndroidx);
          });
        }
        finally {
          action.finish();
        }
      }
    }

    private void syncAndReplaceReferences(@NotNull Project project, @NotNull PsiElement startElement, boolean useAndroidx) {
      assert ApplicationManager.getApplication().isDispatchThread();

      ListenableFuture<ProjectSystemSyncManager.SyncResult> syncResult = ProjectSystemUtil.getProjectSystem(project)
        .getSyncManager().requestSyncProject(ProjectSystemSyncManager.SyncReason.PROJECT_MODIFIED);

      Futures.addCallback(syncResult, new FutureCallback<ProjectSystemSyncManager.SyncResult>() {
        @Override
        public void onSuccess(@Nullable ProjectSystemSyncManager.SyncResult syncResult) {
          if (syncResult != null && syncResult.isSuccessful()) {
            DumbService.getInstance(project).runWhenSmart(() -> replaceReferences(getName(), startElement, null, useAndroidx));
          }
        }

        @Override
        public void onFailure(Throwable t) {
          if (!(t instanceof CancellationException)) {
            Logger.getInstance(AndroidLintExifInterfaceInspection.class).warn(t);
          }
        }
      }, MoreExecutors.directExecutor());
    }

    private static GradleCoordinate getExifLibraryCoordinate() {
      RepositoryUrlManager manager = RepositoryUrlManager.get();
      Component component = manager.getArtifactComponent(GoogleMavenArtifactId.ANDROIDX_EXIF_INTERFACE, true);
      String libraryComponentIdentifier = null;
      if (component != null) {
        libraryComponentIdentifier = component.toIdentifier();
      }
      if (libraryComponentIdentifier != null) {
        return GradleCoordinate.parseCoordinateString(libraryComponentIdentifier);
      }

      component = manager.getArtifactComponent(GoogleMavenArtifactId.EXIF_INTERFACE, true);
      if (component != null) {
        libraryComponentIdentifier = component.toIdentifier();
      }
      if (libraryComponentIdentifier == null) {
        return null;
      }

      if (component != null) {
        if (component.getVersion().compareTo(Version.parse("25.1.0")) < 0) {
          libraryComponentIdentifier = GoogleMavenArtifactId.EXIF_INTERFACE.getComponent("25.1.0").toIdentifier();
        }
      }

      return GradleCoordinate.parseCoordinateString(libraryComponentIdentifier);
    }

    /**
     * Replaces the references to the old ExifInterface with the new class name.
     *
     * @param actionName  the name of the action to write the changes
     * @param element     the name of the element to start the search from
     * @param cls         the class to use to replace old instances of ExifInterface. If null, see {@code useAndroid}
     * @param useAndroidx when {@code cls} is null, this determines whether new references will use the androidx package name
     */
    private static void replaceReferences(@NotNull String actionName,
                                          @NotNull PsiElement element,
                                          @Nullable PsiClass cls,
                                          boolean useAndroidx) {
      WriteCommandAction.writeCommandAction(element.getProject()).withName(actionName).run(() -> {
        Project project = element.getProject();
        PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
        PsiFile file = element.getContainingFile();
        file.accept(new JavaRecursiveElementVisitor() {
          @Override
          public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement expression) {
            if (ExifInterfaceDetector.EXIF_INTERFACE.equals(expression.getReferenceName())) {
              if (expression.isQualified()) {
                PsiElement context = expression.getParent();
                if (expression instanceof PsiReferenceExpression) {
                  if (cls != null) {
                    PsiReferenceExpression replacement = factory.createReferenceExpression(cls);
                    expression.replace(replacement);
                    return;
                  }
                }
                else {
                  expression.replace(
                    factory.createReferenceFromText(useAndroidx ? NEW_EXIF_INTERFACE.newName() : NEW_EXIF_INTERFACE.oldName(), context));
                  return;
                }
              }
            }
            super.visitReferenceElement(expression);
          }
        });
      });
    }
  }
}
