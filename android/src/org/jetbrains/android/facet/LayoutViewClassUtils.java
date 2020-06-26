// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.android.facet;

import static com.android.tools.lint.checks.AnnotationDetector.RESTRICT_TO_ANNOTATION;

import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.projectsystem.ScopeType;
import com.android.tools.idea.res.ResourceHelper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility methods for connecting PsiClass instances to layout view tag names
 */
public final class LayoutViewClassUtils {
  // Utility class, no constructor
  private LayoutViewClassUtils() { }

  @NotNull
  public static String[] getTagNamesByClass(@NotNull final PsiClass c, final int apiLevel) {
    return ApplicationManager.getApplication().runReadAction((Computable<String[]>)() -> {
      String name = c.getName();
      if (name == null || !isViewClassVisibleAsTag(c)) {
        return ArrayUtilRt.EMPTY_STRING_ARRAY;
      }

      String qualifiedName = c.getQualifiedName();
      if (qualifiedName == null) {
        return new String[]{name};
      }

      if (ResourceHelper.isClassPackageNeeded(qualifiedName, c, apiLevel)) {
        return new String[]{qualifiedName};
      }
      return new String[]{name, qualifiedName};
    });
  }

  @Nullable
  public static PsiClass findClassByTagName(@NotNull AndroidFacet facet, @NotNull String name, @NotNull PsiClass baseClass) {
    final Module module = facet.getModule();
    final Project project = module.getProject();

    if (!name.contains(".")) {
      final PsiClass[] classes = PsiShortNamesCache.getInstance(project).getClassesByName(name, module.getModuleWithLibrariesScope());
      final int apiLevel = AndroidModuleInfo.getInstance(facet).getModuleMinApi();

      for (PsiClass aClass : classes) {
        final String qualifiedName = aClass.getQualifiedName();

        if (qualifiedName != null && !ResourceHelper.isClassPackageNeeded(qualifiedName, baseClass, apiLevel) && aClass.isInheritor(baseClass, true)) {
          return aClass;
        }
      }
    }
    else {
      final PsiClass[] classes = JavaPsiFacade.getInstance(project).findClasses(
        name, ProjectSystemUtil.getModuleSystem(module).getResolveScope(ScopeType.MAIN));

      for (PsiClass aClass : classes) {
        if (aClass.isInheritor(baseClass, true)) {
          return aClass;
        }
      }
    }
    return null;
  }

  @Nullable
  public static PsiClass findClassByTagName(@NotNull AndroidFacet facet, @NotNull String name, @NotNull String baseClassQName) {
    final PsiClass baseClass = JavaPsiFacade.getInstance(facet.getModule().getProject()).findClass(
      baseClassQName, facet.getModule().getModuleWithLibrariesScope());
    return baseClass != null ? findClassByTagName(facet, name, baseClass) : null;
  }

  public static boolean isViewClassVisibleAsTag(@NotNull PsiClass aClass) {
    PsiModifierList modifierList = aClass.getModifierList();
    if (modifierList == null) {
      return false; // not public
    }
    boolean isPublic = modifierList.hasModifierProperty(PsiModifier.PUBLIC);
    boolean isRestricted = modifierList.findAnnotation(RESTRICT_TO_ANNOTATION.oldName()) != null ||
                           modifierList.findAnnotation(RESTRICT_TO_ANNOTATION.newName()) != null;
    return isPublic && !isRestricted;
  }
}
