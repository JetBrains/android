/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.inspections;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class is a copy of {@link com.intellij.codeInspection.deprecation.DeprecationInspection}
 * but with one patch applied: https://android-review.googlesource.com/#/c/149415/
 */
public class AndroidDeprecationInspection extends AbstractBaseJavaLocalInspectionTool {

  @NonNls public static final String SHORT_NAME = DeprecationUtil.DEPRECATION_SHORT_NAME;
  @NonNls public static final String ID = DeprecationUtil.DEPRECATION_ID;
  public static final String DISPLAY_NAME = DeprecationUtil.getDeprecationDisplayName();
  public static final String IGNORE_METHODS_OF_DEPRECATED_NAME = "IGNORE_METHODS_OF_DEPRECATED";

  public boolean IGNORE_INSIDE_DEPRECATED;
  public boolean IGNORE_ABSTRACT_DEPRECATED_OVERRIDES = true;
  public boolean IGNORE_IMPORT_STATEMENTS = true;
  public boolean IGNORE_METHODS_OF_DEPRECATED = true;

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new DeprecationElementVisitor(holder, IGNORE_INSIDE_DEPRECATED, IGNORE_ABSTRACT_DEPRECATED_OVERRIDES,
                                                               IGNORE_IMPORT_STATEMENTS, IGNORE_METHODS_OF_DEPRECATED);
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return "";
  }

  @Override
  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }

  @Override
  @NotNull
  @NonNls
  public String getID() {
    return ID;
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox("Ignore inside deprecated members", "IGNORE_INSIDE_DEPRECATED");
    panel.addCheckbox("Ignore inside non-static imports", "IGNORE_IMPORT_STATEMENTS");
    panel.addCheckbox("<html>Ignore overrides of deprecated abstract methods from non-deprecated supers</html>", "IGNORE_ABSTRACT_DEPRECATED_OVERRIDES");
    panel.addCheckbox("Ignore members of deprecated classes", IGNORE_METHODS_OF_DEPRECATED_NAME);
    return panel;

  }

  private static class DeprecationElementVisitor extends JavaElementVisitor {
    private final ProblemsHolder myHolder;
    private final boolean myIgnoreInsideDeprecated;
    private final boolean myIgnoreAbstractDeprecatedOverrides;
    private final boolean myIgnoreImportStatements;
    private final boolean myIgnoreMethodsOfDeprecated;

    public DeprecationElementVisitor(final ProblemsHolder holder,
                                     boolean ignoreInsideDeprecated,
                                     boolean ignoreAbstractDeprecatedOverrides,
                                     boolean ignoreImportStatements,
                                     boolean ignoreMethodsOfDeprecated) {
      myHolder = holder;
      myIgnoreInsideDeprecated = ignoreInsideDeprecated;
      myIgnoreAbstractDeprecatedOverrides = ignoreAbstractDeprecatedOverrides;
      myIgnoreImportStatements = ignoreImportStatements;
      myIgnoreMethodsOfDeprecated = ignoreMethodsOfDeprecated;
    }

    @Override
    public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
      JavaResolveResult result = reference.advancedResolve(true);
      PsiElement resolved = result.getElement();
      checkDeprecated(resolved, reference.getReferenceNameElement(), null, myIgnoreInsideDeprecated, myIgnoreImportStatements, myIgnoreMethodsOfDeprecated, myHolder);
    }

    @Override
    public void visitImportStaticStatement(@NotNull PsiImportStaticStatement statement) {
      final PsiJavaCodeReferenceElement importReference = statement.getImportReference();
      if (importReference != null) {
        checkDeprecated(importReference.resolve(), importReference.getReferenceNameElement(), null, myIgnoreInsideDeprecated, false, true, myHolder);
      }
    }

    @Override public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      visitReferenceElement(expression);
    }

    @Override public void visitNewExpression(@NotNull PsiNewExpression expression) {
      PsiType type = expression.getType();
      PsiExpressionList list = expression.getArgumentList();
      if (!(type instanceof PsiClassType)) return;
      PsiClassType.ClassResolveResult typeResult = ((PsiClassType)type).resolveGenerics();
      PsiClass aClass = typeResult.getElement();
      if (aClass == null) return;
      if (aClass instanceof PsiAnonymousClass) {
        type = ((PsiAnonymousClass)aClass).getBaseClassType();
        typeResult = ((PsiClassType)type).resolveGenerics();
        aClass = typeResult.getElement();
        if (aClass == null) return;
      }
      final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(expression.getProject()).getResolveHelper();
      final PsiMethod[] constructors = aClass.getConstructors();
      if (constructors.length > 0 && list != null) {
        JavaResolveResult[] results = resolveHelper.multiResolveConstructor((PsiClassType)type, list, list);
        MethodCandidateInfo result = null;
        if (results.length == 1) result = (MethodCandidateInfo)results[0];

        PsiMethod constructor = result == null ? null : result.getElement();
        if (constructor != null && expression.getClassOrAnonymousClassReference() != null) {
          if (expression.getClassReference() == null && constructor.getParameterList().getParametersCount() == 0) return;
          checkDeprecated(constructor, expression.getClassOrAnonymousClassReference(), null, myIgnoreInsideDeprecated, myIgnoreImportStatements, true, myHolder);
        }
      }
    }

    @Override public void visitMethod(@NotNull PsiMethod method){
      MethodSignatureBackedByPsiMethod methodSignature = MethodSignatureBackedByPsiMethod.create(method, PsiSubstitutor.EMPTY);
      if (!method.isConstructor()) {
        List<MethodSignatureBackedByPsiMethod> superMethodSignatures = method.findSuperMethodSignaturesIncludingStatic(true);
        checkMethodOverridesDeprecated(methodSignature, superMethodSignatures, myIgnoreAbstractDeprecatedOverrides, myHolder);
      } else {
        checkImplicitCallToSuper(method);
      }
    }

    private void checkImplicitCallToSuper(PsiMethod method) {
      final PsiClass containingClass = method.getContainingClass();
      assert containingClass != null;
      final PsiClass superClass = containingClass.getSuperClass();
      if (hasDefaultDeprecatedConstructor(superClass)) {
        if (superClass instanceof PsiAnonymousClass) {
          final PsiExpressionList argumentList = ((PsiAnonymousClass)superClass).getArgumentList();
          if (argumentList != null && argumentList.getExpressions().length > 0) return;
        }
        final PsiCodeBlock body = method.getBody();
        if (body != null) {
          final PsiStatement[] statements = body.getStatements();
          if (statements.length == 0 || !JavaHighlightUtil.isSuperOrThisCall(statements[0], true, true)) {
            registerDefaultConstructorProblem(superClass, method.getNameIdentifier(), false);
          }
        }
      }
    }

    private void registerDefaultConstructorProblem(PsiClass superClass, PsiElement nameIdentifier, boolean asDeprecated) {
      myHolder.registerProblem(nameIdentifier, "Default constructor in " + superClass.getQualifiedName() + " is deprecated",
                               asDeprecated ? ProblemHighlightType.LIKE_DEPRECATED : ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
    }

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      if (aClass instanceof PsiTypeParameter) return;
      final PsiMethod[] currentConstructors = aClass.getConstructors();
      if (currentConstructors.length == 0) {
        final PsiClass superClass = aClass.getSuperClass();
        if (hasDefaultDeprecatedConstructor(superClass)) {
          final boolean isAnonymous = aClass instanceof PsiAnonymousClass;
          if (isAnonymous) {
            final PsiExpressionList argumentList = ((PsiAnonymousClass)aClass).getArgumentList();
            if (argumentList != null && argumentList.getExpressions().length > 0) return;
          }
          registerDefaultConstructorProblem(superClass, isAnonymous ? ((PsiAnonymousClass)aClass).getBaseClassReference() : aClass.getNameIdentifier(), isAnonymous);
        }
      }
    }
  }

  private static boolean hasDefaultDeprecatedConstructor(PsiClass superClass) {
    if (superClass != null) {
      final PsiMethod[] constructors = superClass.getConstructors();
      for (PsiMethod constructor : constructors) {
        if (constructor.getParameterList().getParametersCount() == 0 && constructor.isDeprecated()) {
          return true;
        }
      }
    }
    return false;
  }

  //@top
  static void checkMethodOverridesDeprecated(MethodSignatureBackedByPsiMethod methodSignature,
                                             List<MethodSignatureBackedByPsiMethod> superMethodSignatures,
                                             boolean ignoreAbstractDeprecatedOverrides, ProblemsHolder holder) {
    PsiMethod method = methodSignature.getMethod();
    PsiElement methodName = method.getNameIdentifier();
    if (methodName == null) {
      return;
    }
    for (MethodSignatureBackedByPsiMethod superMethodSignature : superMethodSignatures) {
      PsiMethod superMethod = superMethodSignature.getMethod();
      PsiClass aClass = superMethod.getContainingClass();
      if (aClass == null) continue;
      // do not show deprecated warning for class implementing deprecated methods
      if (ignoreAbstractDeprecatedOverrides && !aClass.isDeprecated() && superMethod.hasModifierProperty(PsiModifier.ABSTRACT)) continue;
      if (superMethod.isDeprecated()) {
        String description = JavaErrorBundle.message(
          "overrides.deprecated.method",
          HighlightMessageUtil.getSymbolName(aClass, PsiSubstitutor.EMPTY));

        List<LocalQuickFix> fixes = new ArrayList<LocalQuickFix>(4);
        String symbolName = HighlightMessageUtil.getSymbolName(methodName, PsiSubstitutor.EMPTY);
        for (DeprecationFilter filter : getFilters()) {
          if (filter.isExcluded(superMethod, methodName, symbolName)) {
            return;
          }
          description = filter.getDeprecationMessage(superMethod, methodName, symbolName, description);
          LocalQuickFix[] additionalFixes = filter.getQuickFixes(superMethod, methodName, symbolName);
          Collections.addAll(fixes, additionalFixes);
        }

        holder.registerProblem(methodName, description, ProblemHighlightType.LIKE_DEPRECATED, null,
                               fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
      }
    }
  }

  public static void checkDeprecated(PsiElement refElement,
                                     PsiElement elementToHighlight,
                                     @Nullable TextRange rangeInElement,
                                     boolean ignoreInsideDeprecated,
                                     boolean ignoreImportStatements,
                                     boolean ignoreMethodsOfDeprecated,
                                     ProblemsHolder holder) {
    if (!(refElement instanceof PsiDocCommentOwner)) return;
    if (!((PsiDocCommentOwner)refElement).isDeprecated()) {
      if (!ignoreMethodsOfDeprecated) {
        checkDeprecated(((PsiDocCommentOwner)refElement).getContainingClass(), elementToHighlight, rangeInElement,
                        ignoreInsideDeprecated, ignoreImportStatements, false, holder);
      }
      return;
    }

    if (ignoreInsideDeprecated) {
      PsiElement parent = elementToHighlight;
      while ((parent = PsiTreeUtil.getParentOfType(parent, PsiDocCommentOwner.class, true)) != null) {
        if (((PsiDocCommentOwner)parent).isDeprecated()) return;
      }
    }

    if (ignoreImportStatements && PsiTreeUtil.getParentOfType(elementToHighlight, PsiImportStatementBase.class) != null) {
      return;
    }

    String symbolName = HighlightMessageUtil.getSymbolName(refElement, PsiSubstitutor.EMPTY);
    String description = JavaErrorBundle.message("deprecated.symbol", symbolName);

    List<LocalQuickFix> fixes = new ArrayList<>(4);
    for (DeprecationFilter filter : getFilters()) {
      if (filter.isExcluded(refElement, elementToHighlight, symbolName)) {
        return;
      }
      description = filter.getDeprecationMessage(refElement, elementToHighlight, symbolName, description);
      LocalQuickFix[] additionalFixes = filter.getQuickFixes(refElement, elementToHighlight, symbolName);
      Collections.addAll(fixes, additionalFixes);
    }

    holder.registerProblem(elementToHighlight, description, ProblemHighlightType.LIKE_DEPRECATED, rangeInElement,
                           fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
  }

  @NotNull
  public static DeprecationFilter[] getFilters() {
    return new DeprecationFilter[]{new AndroidDeprecationFilter()};
  }

  /**
   * Filter which allows plugins to customize the inspection results for deprecated elements
   */
  public static abstract class DeprecationFilter {

    /**
     * For a deprecated element, returns true to remove the deprecation warnings for this element,
     * or false to not exclude the element (e.g. show it as deprecated).
     * <p/>
     * This is for example used in Android when an API is marked as deprecated in a particular version
     * of Android, but the current project declares that it supports an older version of Android where
     * the API is not yet deprecated.
     *
     * @param deprecatedElement the deprecated element (e.g. the deprecated class, method or field)
     * @param referenceElement  the reference to that deprecated element
     * @param symbolName        the user visible symbol name
     * @return true if we should hide this deprecation
     */
    public boolean isExcluded(@NotNull PsiElement deprecatedElement, @NotNull PsiElement referenceElement, @Nullable String symbolName) {
      return false;
    }

    /**
     * Optionally changes the deprecation message shown for a given element.
     * For example, a plugin can add knowledge it has about the deprecation, such as
     * a suggested replacement.
     *
     * @param deprecatedElement the deprecated element (e.g. the deprecated class, method or field)
     * @param referenceElement  the reference to that deprecated element
     * @param symbolName        the user visible symbol name
     * @param defaultMessage    the default message to be shown for this deprecation
     * @return a suggested replacement message (which is often the default message with
     * some additional details concatenated), or just the passed in original message to leave it alone
     */
    @NotNull
    public String getDeprecationMessage(@NotNull PsiElement deprecatedElement,
                                        @NotNull PsiElement referenceElement,
                                        @Nullable String symbolName,
                                        @NotNull String defaultMessage) {
      return defaultMessage;
    }

    /**
     * Returns optional quick fixes, if any, to add to this deprecation element
     *
     * @param deprecatedElement the deprecated element (e.g. the deprecated class, method or field)
     * @param referenceElement  the reference to that deprecated element
     * @param symbolName        the user visible symbol name
     * @return a (possibly empty) array of quick fixes to register with the deprecation
     */
    @NotNull
    public LocalQuickFix[] getQuickFixes(@NotNull PsiElement deprecatedElement,
                                         @NotNull PsiElement referenceElement,
                                         @Nullable String symbolName) {
      return LocalQuickFix.EMPTY_ARRAY;
    }
  }
 }
