/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.java.run.producers;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.junit2.PsiMemberParameterizedLocation;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiClassUtil;
import com.intellij.psi.util.PsiModificationTracker;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Utility methods for java test run configuration producers. */
public class ProducerUtils {
  @Nullable
  public static Location<PsiMethod> getMethodLocation(Location<?> contextLocation) {
    Location<PsiMethod> methodLocation = getTestMethod(contextLocation);
    if (methodLocation == null) {
      return null;
    }

    if (contextLocation instanceof PsiMemberParameterizedLocation) {
      PsiClass containingClass =
          ((PsiMemberParameterizedLocation) contextLocation).getContainingClass();
      if (containingClass != null) {
        methodLocation =
            MethodLocation.elementInClass(methodLocation.getPsiElement(), containingClass);
      }
    }
    return methodLocation;
  }

  @Nullable
  public static Location<PsiMethod> getTestMethod(final Location<?> location) {
    for (Iterator<Location<PsiMethod>> iterator = location.getAncestors(PsiMethod.class, false);
        iterator.hasNext();
        ) {
      final Location<PsiMethod> methodLocation = iterator.next();
      if (JUnitUtil.isTestMethod(methodLocation, false)) {
        return methodLocation;
      }
    }
    return null;
  }

  /** For any test classes with nested inner test classes, also add the inner classes to the set. */
  static Set<PsiClass> includeInnerTestClasses(Set<PsiClass> testClasses) {
    Set<PsiClass> result = new HashSet<>(testClasses);
    for (PsiClass psiClass : testClasses) {
      result.addAll(getInnerTestClasses(psiClass));
    }
    return result;
  }

  static Set<PsiClass> getInnerTestClasses(PsiClass psiClass) {
    return Arrays.stream(psiClass.getInnerClasses())
        .filter(ProducerUtils::isTestClass)
        .collect(Collectors.toSet());
  }

  @Nullable
  public static PsiClass getTestClass(final PsiElement element) {
    Location<PsiElement> location = PsiLocation.fromPsiElement(element);
    return location != null ? getTestClass(location) : null;
  }

  /** Same as {@link JUnitUtil#getTestClass}, but handles classes outside the project. */
  @Nullable
  public static PsiClass getTestClass(final Location<?> location) {
    for (Iterator<Location<PsiClass>> iterator = location.getAncestors(PsiClass.class, false);
        iterator.hasNext(); ) {
      final Location<PsiClass> classLocation = iterator.next();
      if (isTestClass(classLocation.getPsiElement())) {
        return classLocation.getPsiElement();
      }
    }
    PsiElement element = location.getPsiElement();
    if (element instanceof PsiClassOwner) {
      PsiClass[] classes = ((PsiClassOwner) element).getClasses();
      if (classes.length == 1 && isTestClass(classes[0])) {
        return classes[0];
      }
    }
    return null;
  }

  /**
   * Based on {@link JUnitUtil#isTestClass}. We don't use that directly because it returns true for
   * all inner classes of a test class, regardless of whether they're also test classes.
   */
  public static boolean isTestClass(PsiClass psiClass) {
    if (psiClass.getQualifiedName() == null) {
      return false;
    }
    if (JUnitUtil.isJUnit5(psiClass) && JUnitUtil.isJUnit5TestClass(psiClass, true)) {
      return true;
    }
    if (!PsiClassUtil.isRunnableClass(psiClass, true, true)) {
      return false;
    }
    if (isJUnit4Class(psiClass)) {
      return true;
    }
    if (isTestCaseInheritor(psiClass)) {
      return true;
    }
    return CachedValuesManager.getCachedValue(
        psiClass,
        () ->
            CachedValueProvider.Result.create(
                hasTestOrSuiteMethods(psiClass),
                PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT));
  }

  private static boolean isJUnit4Class(PsiClass psiClass) {
    String qualifiedName = JUnitUtil.RUN_WITH;
    if (AnnotationUtil.isAnnotated(psiClass, qualifiedName, true)) {
      return true;
    }
    // handle the case where RunWith and/or the current class isn't indexed
    PsiModifierList modifierList = psiClass.getModifierList();
    if (modifierList == null) {
      return false;
    }
    if (modifierList.hasAnnotation(qualifiedName)) {
      return true;
    }
    String shortName = StringUtil.getShortName(qualifiedName);
    return modifierList.hasAnnotation(shortName) && hasImport(psiClass, qualifiedName);
  }

  private static boolean hasImport(PsiElement element, String qualifiedName) {
    PsiImportList imports = getImports(element);
    return imports != null && imports.findSingleClassImportStatement(qualifiedName) != null;
  }

  @Nullable
  private static PsiImportList getImports(PsiElement element) {
    PsiFile file = element.getContainingFile();
    return file instanceof PsiJavaFile ? ((PsiJavaFile) file).getImportList() : null;
  }

  private static boolean isTestCaseInheritor(PsiClass psiClass) {
    // unlike JUnitUtil#isTestCaseInheritor, works even if the class isn't in the project
    PsiClass testCaseClass =
        JavaPsiFacade.getInstance(psiClass.getProject())
            .findClass(
                JUnitUtil.TEST_CASE_CLASS, GlobalSearchScope.allScope(psiClass.getProject()));
    if (testCaseClass != null) {
      return psiClass.isInheritor(testCaseClass, true);
    }
    // TestCase isn't indexed, instead use heuristics to check
    return extendsTestCase(psiClass, new HashSet<>());
  }

  private static boolean extendsTestCase(PsiClass psiClass, Set<PsiClass> checkedClasses) {
    if (!checkedClasses.add(psiClass)) {
      return false;
    }
    PsiReferenceList extendsList = psiClass.getExtendsList();
    if (extendsList == null) {
      return false;
    }
    for (PsiJavaCodeReferenceElement ref : extendsList.getReferenceElements()) {
      if (JUnitUtil.TEST_CASE_CLASS.equals(ref.getQualifiedName())) {
        return true;
      }
      PsiElement clazz = ref.resolve();
      if (clazz instanceof PsiClass && extendsTestCase((PsiClass) clazz, checkedClasses)) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasTestOrSuiteMethods(PsiClass psiClass) {
    for (final PsiMethod method : psiClass.getAllMethods()) {
      if (JUnitUtil.isSuiteMethod(method) || JUnitUtil.isTestAnnotated(method)) {
        return true;
      }
    }
    return false;
  }
}
