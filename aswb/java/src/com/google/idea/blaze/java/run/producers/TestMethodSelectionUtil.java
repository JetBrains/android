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

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;

/** Helper functions for getting selected test methods. */
public class TestMethodSelectionUtil {

  /**
   * Get all test methods directly or indirectly selected in the given context. This includes
   * methods selected in the Structure panel, as well as methods the context location is inside of.
   *
   * @param context The context to get selected test methods from.
   * @param allMustMatch If true, will return null if any selected elements are not test methods.
   * @return A list of test methods (with at least one element), or null if:
   *     <ul>
   *     <li>There are no selected test methods
   *     <li>{@code allMustMatch} is true, but elements other than test methods are selected
   *     </ul>
   *
   * @see #getDirectlySelectedMethods(ConfigurationContext, boolean)
   * @see #getIndirectlySelectedMethod(ConfigurationContext)
   */
  @Nullable
  public static List<PsiMethod> getSelectedMethods(
      @NotNull ConfigurationContext context, boolean allMustMatch) {
    List<PsiMethod> directlySelectedMethods = getDirectlySelectedMethods(context, allMustMatch);
    if (directlySelectedMethods != null && directlySelectedMethods.size() > 0) {
      return directlySelectedMethods;
    }
    if (allMustMatch && JUnitConfigurationUtil.isMultipleElementsSelected(context)) {
      return null;
    }
    PsiMethod indirectlySelectedMethod = getIndirectlySelectedMethod(context);
    if (indirectlySelectedMethod != null) {
      return Collections.singletonList(indirectlySelectedMethod);
    }
    return null;
  }

  /**
   * Get all test methods directly or indirectly selected in the given context. This includes
   * methods selected in the Structure panel, as well as methods the context location is inside of.
   *
   * @param context The context to get selected test methods from.
   * @return A list of test methods (with at least one element), or null if:
   *     <ul>
   *     <li>There are no selected test methods
   *     <li>Any elements other than test methods are selected
   *     </ul>
   *
   * @see #getDirectlySelectedMethods(ConfigurationContext, boolean)
   * @see #getIndirectlySelectedMethod(ConfigurationContext)
   */
  @Nullable
  public static List<PsiMethod> getSelectedMethods(@NotNull ConfigurationContext context) {
    return getSelectedMethods(context, true);
  }

  /**
   * Get all test methods directly selected in the given context. This includes, for example,
   * methods selected from the Structure panel. It does not include methods the context location is
   * inside of. Note that methods may belong to different classes (possible if methods are selected
   * from the Project panel with "Show Members" checked), and methods in abstract classes are not
   * returned.
   *
   * @param context The context to get selected test methods from.
   * @param allMustMatch If true, will return null if any selected elements are not test methods.
   * @return A list of test methods (possibly empty), or null if:
   *     <ul>
   *     <li>There is no selection
   *     <li>{@code allMustMatch} is true, but elements other than test methods are selected
   *     </ul>
   */
  @Nullable
  public static List<PsiMethod> getDirectlySelectedMethods(
      @NotNull ConfigurationContext context, boolean allMustMatch) {
    final DataContext dataContext = context.getDataContext();
    PsiElement[] elements = getSelectedPsiElements(dataContext);
    if (elements == null) {
      return null;
    }
    List<PsiMethod> methods = new ArrayList<>();
    for (PsiElement element : elements) {
      if (element instanceof PsiMethod) {
        PsiMethod method = (PsiMethod) element;
        if (JUnitUtil.isTestMethod(
            PsiLocation.fromPsiElement(method), /* checkAbstract= */ false)) {
          methods.add(method);
        } else if (allMustMatch) {
          return null;
        }
      } else if (allMustMatch) {
        return null;
      }
    }
    return methods;
  }

  @Nullable
  private static PsiElement[] getSelectedPsiElements(DataContext context) {
    PsiElement[] elements = LangDataKeys.PSI_ELEMENT_ARRAY.getData(context);
    if (elements != null) {
      return elements;
    }
    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(context);
    return element != null ? new PsiElement[] {element} : null;
  }

  /**
   * Get a test method which is considered selected in the given context, belonging to a
   * non-abstract class. The context location may be the method itself, or anywhere inside the
   * method.
   *
   * @param context The context to search for a test method in.
   * @return A test method, or null if none are found.
   */
  @Nullable
  public static PsiMethod getIndirectlySelectedMethod(@NotNull ConfigurationContext context) {
    final Location<?> contextLocation = context.getLocation();
    if (contextLocation == null) {
      return null;
    }
    Iterator<Location<PsiMethod>> locationIterator =
        contextLocation.getAncestors(PsiMethod.class, false);
    while (locationIterator.hasNext()) {
      Location<PsiMethod> methodLocation = locationIterator.next();
      if (JUnitUtil.isTestMethod(methodLocation)) {
        return methodLocation.getPsiElement();
      }
    }
    return null;
  }
}
