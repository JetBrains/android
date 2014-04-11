/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea;

import com.android.resources.ResourceType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.SdkConstants.ANDROID_PKG;
import static com.android.SdkConstants.R_CLASS;

public class AndroidPsiUtils {
  /**
   * Looks up the {@link PsiFile} for a given {@link VirtualFile} in a given {@link Project}, in
   * a safe way (meaning it will acquire a read lock first, and will check that the file is valid
   *
   * @param project the project
   * @param file the file
   * @return the corresponding {@link PsiFile}, or null if not found or valid
   */
  @Nullable
  public static PsiFile getPsiFileSafely(@NotNull final Project project, @NotNull final VirtualFile file) {
    return ApplicationManager.getApplication().runReadAction(new Computable<PsiFile>() {
      @Nullable
      @Override
      public PsiFile compute() {
        return file.isValid() ? PsiManager.getInstance(project).findFile(file) : null;
      }
    });
  }

  /**
   * Looks up the {@link Module} for a given {@link PsiElement}, in a safe way (meaning it will
   * acquire a read lock first.
   *
   * @param element the element
   * @return the module containing the element, or null if not found
   */
  @Nullable
  public static Module getModuleSafely(@NotNull final PsiElement element) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Module>() {
      @Nullable
      @Override
      public Module compute() {
        return ModuleUtilCore.findModuleForPsiElement(element);
      }
    });
  }

  /**
   * Looks up the {@link Module} containing a given {@link VirtualFile} in a given {@link Project}, in
   * a safe way (meaning it will acquire a read lock first
   *
   * @param project the project
   * @param file the file
   * @return the corresponding {@link Module}, or null if not found
   */
  @Nullable
  public static Module getModuleSafely(@NotNull final Project project, @NotNull final VirtualFile file) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Module>() {
      @Nullable
      @Override
      public Module compute() {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        return psiFile == null ? null : ModuleUtilCore.findModuleForPsiElement(psiFile);
      }
    });
  }

  /** Type of resource reference: R.type.name or android.R.type.name or neither */
  public enum ResourceReferenceType { NONE, APP, FRAMEWORK }

  /**
   * Returns true if the given PsiElement is a reference to an Android Resource.
   * The element can either be an identifier such as y in R.x.y, or the expression R.x.y itself.
   */
  public static boolean isResourceReference(@NotNull PsiElement element) {
    return getResourceReferenceType(element) != ResourceReferenceType.NONE;
  }

  /**
   * Returns the type of resource reference for the given PSiElement; for R fields and android.R
   * fields it will return {@link ResourceReferenceType#APP} and {@link ResourceReferenceType#FRAMEWORK}
   * respectively, and otherwise it returns {@link ResourceReferenceType#NONE}.
   * <p>
   * The element can either be an identifier such as y in R.x.y, or the expression R.x.y itself.
   */
  @NotNull
  public static ResourceReferenceType getResourceReferenceType(@NotNull PsiElement element) {
    if (element instanceof PsiReferenceExpression) {
      return getResourceReferenceType((PsiReferenceExpression)element);
    }

    if (element instanceof PsiIdentifier && element.getParent() instanceof PsiReferenceExpression) {
      return getResourceReferenceType((PsiReferenceExpression)element.getParent());
    }

    return ResourceReferenceType.NONE;
  }

  /**
   * Returns the resource name; e.g. for "R.string.foo" it returns "foo".
   * NOTE: This method should only be called for elements <b>known</b> to be
   * resource references!
   * */
  @NotNull
  public static String getResourceName(@NotNull PsiElement element) {
    assert isResourceReference(element);
    if (element instanceof PsiReferenceExpression) {
      PsiReferenceExpression refExp = (PsiReferenceExpression)element;
      String name = refExp.getReferenceName();
      if (name != null) {
        return name;
      }
    }

    return element.getText();
  }

  @NotNull
  public static ResourceReferenceType getResourceReferenceType(PsiReferenceExpression element) {
    PsiExpression exp = element.getQualifierExpression();
    if (!(exp instanceof PsiReferenceExpression)) {
      return ResourceReferenceType.NONE;
    }

    exp = ((PsiReferenceExpression)exp).getQualifierExpression();
    if (!(exp instanceof PsiReferenceExpression)) {
      return ResourceReferenceType.NONE;
    }

    PsiReferenceExpression ref = (PsiReferenceExpression)exp;
    if (R_CLASS.equals(ref.getReferenceName())) {
      PsiExpression qualifierExpression = ref.getQualifierExpression();
      if (qualifierExpression instanceof PsiReferenceExpression &&
          ANDROID_PKG.equals(((PsiReferenceExpression)qualifierExpression).getReferenceName())) {
        return ResourceReferenceType.FRAMEWORK;
      } else {
        // TODO: Check resolved type to make sure it's not a class with android.R imported?
        // Not super important since we actively discourage importing android.R directly
        // via lint checks and the AndroidImportFilter preventing it from happening automatically
        return ResourceReferenceType.APP;
      }
    }

    return ResourceReferenceType.NONE;
  }

  /** Returns the Android {@link ResourceType} given a PSI reference to an Android resource. */
  @Nullable
  public static ResourceType getResourceType(PsiElement resourceRefElement) {
    if (!isResourceReference(resourceRefElement)) {
      return null;
    }

    PsiReferenceExpression exp = resourceRefElement instanceof PsiReferenceExpression ?
                                 (PsiReferenceExpression)resourceRefElement :
                                 (PsiReferenceExpression)resourceRefElement.getParent();

    PsiExpression qualifierExpression = exp.getQualifierExpression();
    if (qualifierExpression == null) {
      return null;
    }
    return ResourceType.getEnum(qualifierExpression.getLastChild().getText());
  }
}
