/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.android.resources.ResourceType;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.slicer.DuplicateMap;
import com.intellij.slicer.SliceAnalysisParams;
import com.intellij.slicer.SliceRootNode;
import com.intellij.slicer.SliceUsage;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.containers.ConcurrentSoftValueHashMap;
import gnu.trove.THashSet;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.android.SdkConstants.*;

/**
 * A custom version of the IntelliJ
 * {@link com.intellij.codeInspection.magicConstant.MagicConstantInspection},
 * with two changes:
 * <ol>
 *   <li>
 *     Checks for proper resource types, e.g. ensuring that you call
 *     getResources().getDimension(id) with an ID that is a constant from
 *     R.dimen, not for example R.string.
 *   </li>
 *   <li>
 *     Checks for typedef annotations. These are precisely like the IntelliJ
 *     MagicConstant annotations, but with different annotation names and
 *     fields (for example for the integer case there is an explicit flags= boolean
 *     annotation attribute.
 *   </li>
 * </ol>
 * <p>
 * We didn't necessarily need to customize the inspection itself to handle the
 * second case; instead, we could have translated the annotations.zip file at
 * Gradle sync time to generate a derived annotations file with the annotation
 * names and parameters rewritten as IntelliJ annotations. However, doing
 * a resource type check is similar enough the the inspector needs to do nearly
 * everything else anyway (check the same kinds of calls, load the external annotations
 * for each resolved element, and so on) so with all that code in place we might
 * as well also support the android support annotations natively. This also has
 * the advantage that it will work with non-Gradle (IntelliJ and Maven) projects.
 * <p>
 * Since a lot of the code is identical to the IntelliJ magic constant inspection,
 * I have left the code identical to that inspection as much as possible, in order
 * to facilitate diffing the two classes and migrating future changes to the base
 * inspection over to this one.
 * <p>
 * To diff this class with the inspection it was based on, check out tag
 * idea/135.445 in the IntelliJ community edition git repository.
 * <p>
 * This means the code isn't as clear as possible; I just added a {@link ResourceType}
 * field to the AllowedValues inner class to pass around the required ResourceType,
 * which if non-null is the set of resource types we're enforcing rather than the
 * other values held in the object.
 * <p>
 * The main methods that were modified are:
 * <ul>
 *   <li>
 *     {@link #getAllowedValues(com.intellij.psi.PsiModifierListOwner, com.intellij.psi.PsiType, java.util.Set)}:
 *     Changed to look for IntDef/StringDef instead of MagicConstant, as well as look for the Resource type annotations
 *     ({@code }@StringRes}, {@code @IdRes}, etc) and for these we have to loop since you can specify more than one.
 *   </li>
 *   <li>
 *     {@code getAllowedValuesFromMagic()}: Changed to extract attributes from
 *     the support library's IntDef and TypeDef annotations and split into
 *     {@link #getAllowedValuesFromTypedef} and {@link #getResourceTypeFromAnnotation(String)}
 *   </li>
 *   <li>
 *     {@link AllowedValues}: Added ResourceType field which if non-null means
 *     we should look for a ResourceType instead of a set of integer/string constants
 *   </li>
 *   <li>
 *     {@link #isAllowed}: Added check for allowedValues.types and if non-null,
 *     check that if the call can be resolved to R.type.name, that the type is one
 *     of the expected types (this is done by a method similar to getGoodExpression
 *     but which analyzes resource types instead)
 *   </li>
 *   <li>
 *     Removed {@code checkAnnotationsJarAttached()}, since we will always provide SDK annotations
 *     for use by the type def collector in the SDK. (Also removed the code to call it and
 *     to clean up the associated key.)
 *   </li>
 *   <li>
 *     Sprinkled {@code @Nullable} annotations on various parameters and return values to
 *     remove warnings
 *   </li>
 *   <li>
 *     Removed {@code readFromClass} and code associated with picking all fields from
 *     a class
 *   </li>
 *   <li>
 *     Plugin registration: Increased severity to error, and changed category to Android.
 *   </li>
 *   <li>
 *     Stripped out {@code parseBeanInfo}
 *   </li>
 * </ul>
 * <p>
 */
public class ResourceTypeInspection extends BaseJavaLocalInspectionTool {

  private static final String RESOURCE_TYPE_ANNOTATIONS_SUFFIX = "Res";

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    AndroidFacet facet = AndroidFacet.getInstance(holder.getFile());
    if (facet == null) {
      // No-op outside of Android modules
      return new PsiElementVisitor() {};
    }

    return new JavaElementVisitor() {
      @Override
      public void visitCallExpression(PsiCallExpression callExpression) {
        checkCall(callExpression, holder);
      }

      @Override
      public void visitAssignmentExpression(PsiAssignmentExpression expression) {
        PsiExpression r = expression.getRExpression();
        if (r == null) return;
        PsiExpression l = expression.getLExpression();
        if (!(l instanceof PsiReferenceExpression)) return;
        PsiElement resolved = ((PsiReferenceExpression)l).resolve();
        if (!(resolved instanceof PsiModifierListOwner)) return;
        PsiModifierListOwner owner = (PsiModifierListOwner)resolved;
        PsiType type = expression.getType();
        checkExpression(r, owner, type, holder);
      }

      @Override
      public void visitReturnStatement(PsiReturnStatement statement) {
        PsiExpression value = statement.getReturnValue();
        if (value == null) return;
        PsiMethod method = PsiTreeUtil.getParentOfType(statement, PsiMethod.class);
        if (method == null) return;
        checkExpression(value, method, value.getType(), holder);
      }

      @Override
      public void visitNameValuePair(PsiNameValuePair pair) {
        PsiAnnotationMemberValue value = pair.getValue();
        if (!(value instanceof PsiExpression)) return;
        PsiReference ref = pair.getReference();
        if (ref == null) return;
        PsiMethod method = (PsiMethod)ref.resolve();
        if (method == null) return;
        checkExpression((PsiExpression)value, method, method.getReturnType(), holder);
      }

      @Override
      public void visitBinaryExpression(PsiBinaryExpression expression) {
        IElementType tokenType = expression.getOperationTokenType();
        if (tokenType != JavaTokenType.EQEQ && tokenType != JavaTokenType.NE) return;
        PsiExpression l = expression.getLOperand();
        PsiExpression r = expression.getROperand();
        if (r == null) return;
        checkBinary(l, r);
        checkBinary(r, l);
      }

      private void checkBinary(PsiExpression l, PsiExpression r) {
        if (l instanceof PsiReference) {
          PsiElement resolved = ((PsiReference)l).resolve();
          if (resolved instanceof PsiModifierListOwner) {
            checkExpression(r, (PsiModifierListOwner)resolved, getType((PsiModifierListOwner)resolved), holder);
          }
        }
        else if (l instanceof PsiMethodCallExpression) {
          PsiMethod method = ((PsiMethodCallExpression)l).resolveMethod();
          if (method != null) {
            checkExpression(r, method, method.getReturnType(), holder);
          }
        }
      }
    };
  }

  private static void checkExpression(PsiExpression expression,
                                      PsiModifierListOwner owner,
                                      @Nullable PsiType type,
                                      ProblemsHolder holder) {
    AllowedValues allowed = getAllowedValues(owner, type, null);
    if (allowed == null) return;
    //noinspection ConstantConditions
    PsiElement scope = PsiUtil.getTopLevelEnclosingCodeBlock(expression, null);
    if (scope == null) scope = expression;
    if (!isAllowed(scope, expression, allowed, expression.getManager(), null)) {
      registerProblem(expression, allowed, holder);
    }
  }

  private static void checkCall(@NotNull PsiCallExpression methodCall, @NotNull ProblemsHolder holder) {
    PsiMethod method = methodCall.resolveMethod();
    if (method == null) return;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    PsiExpressionList argumentList = methodCall.getArgumentList();
    if (argumentList == null) return;
    PsiExpression[] arguments = argumentList.getExpressions();
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      AllowedValues values = getAllowedValues(parameter, parameter.getType(), null);
      if (values == null) continue;
      if (i >= arguments.length) break;
      PsiExpression argument = arguments[i];
      argument = PsiUtil.deparenthesizeExpression(argument);
      if (argument == null) continue;

      checkMagicParameterArgument(parameter, argument, values, holder);
    }
  }

  static class AllowedValues {
    final PsiAnnotationMemberValue[] values;
    final boolean canBeOred;
    /** Type of Android resource that we must be passing. If non null, this is the only value to be
     * checked, not the member values. This is done to minimize the set of changes to this inspection
     * from the {@link com.intellij.codeInspection.magicConstant.MagicConstantInspection} it was based on. */
    final List<ResourceType> types;

    private AllowedValues(@NotNull PsiAnnotationMemberValue[] values, boolean canBeOred,
                          @Nullable List<ResourceType> types) {
      this.values = values;
      this.canBeOred = canBeOred;
      this.types = types;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      AllowedValues a2 = (AllowedValues)o;
      if (canBeOred != a2.canBeOred) {
        return false;
      }
      Set<PsiAnnotationMemberValue> v1 = new THashSet<PsiAnnotationMemberValue>(Arrays.asList(values));
      Set<PsiAnnotationMemberValue> v2 = new THashSet<PsiAnnotationMemberValue>(Arrays.asList(a2.values));
      if (v1.size() != v2.size()) {
        return false;
      }
      for (PsiAnnotationMemberValue value : v1) {
        for (PsiAnnotationMemberValue value2 : v2) {
          if (same(value, value2, value.getManager())) {
            v2.remove(value2);
            break;
          }
        }
      }
      return v2.isEmpty();
    }
    @Override
    public int hashCode() {
      int result = Arrays.hashCode(values);
      result = 31 * result + (canBeOred ? 1 : 0);
      return result;
    }

    public boolean isSubsetOf(@NotNull AllowedValues other, @NotNull PsiManager manager) {
      for (PsiAnnotationMemberValue value : values) {
        boolean found = false;
        for (PsiAnnotationMemberValue otherValue : other.values) {
          if (same(value, otherValue, manager)) {
            found = true;
            break;
          }
        }
        if (!found) return false;
      }
      return true;
    }
  }

  @Nullable
  private static AllowedValues getAllowedValuesFromTypedef(@NotNull PsiType type,
                                                           @NotNull PsiAnnotation magic,
                                                           @NotNull PsiManager manager) {
    PsiAnnotationMemberValue[] allowedValues;
    final boolean canBeOred;
    boolean isInt = TypeConversionUtil.getTypeRank(type) <= TypeConversionUtil.LONG_RANK;
    boolean isString = !isInt && type.equals(PsiType.getJavaLangString(manager, GlobalSearchScope.allScope(manager.getProject())));
    if (isInt || isString) {
      PsiAnnotationMemberValue intValues = magic.findAttributeValue(TYPE_DEF_VALUE_ATTRIBUTE);
      allowedValues = intValues instanceof PsiArrayInitializerMemberValue ? ((PsiArrayInitializerMemberValue)intValues).getInitializers() : PsiAnnotationMemberValue.EMPTY_ARRAY;

      if (isInt) {
        PsiAnnotationMemberValue orValue = magic.findAttributeValue(TYPE_DEF_FLAG_ATTRIBUTE);
        canBeOred = orValue instanceof PsiLiteral && Boolean.TRUE.equals(((PsiLiteral)orValue).getValue());
      } else {
        canBeOred = false;
      }
    } else {
      return null; //other types not supported
    }

    if (allowedValues.length != 0) {
      return new AllowedValues(allowedValues, canBeOred, null);
    }

    return null;
  }

  @Nullable
  private static ResourceType getResourceTypeFromAnnotation(@NotNull String qualifiedName) {
    String resourceTypeName =
      Character.toLowerCase(qualifiedName.charAt(SUPPORT_ANNOTATIONS_PREFIX.length())) +
      qualifiedName.substring(SUPPORT_ANNOTATIONS_PREFIX.length() + 1, qualifiedName.length() - RESOURCE_TYPE_ANNOTATIONS_SUFFIX.length());
    return ResourceType.getEnum(resourceTypeName);
  }

  @Nullable
  static AllowedValues getAllowedValues(@NotNull PsiModifierListOwner element, @Nullable PsiType type, @Nullable Set<PsiClass> visited) {
    //noinspection ConstantConditions
    PsiAnnotation[] annotations = AnnotationUtil.getAllAnnotations(element, true, null);
    PsiManager manager = element.getManager();
    List<ResourceType> resourceTypes = null;
    for (PsiAnnotation annotation : annotations) {
      AllowedValues values;
      if (type != null) {
        String qualifiedName = annotation.getQualifiedName();
        if (qualifiedName == null) {
          continue;
        }

        if (INT_DEF_ANNOTATION.equals(qualifiedName) || STRING_DEF_ANNOTATION.equals(qualifiedName)) {
          values = getAllowedValuesFromTypedef(type, annotation, manager);
          if (values != null) return values;
        } else if (qualifiedName.startsWith(SUPPORT_ANNOTATIONS_PREFIX) && qualifiedName.endsWith(RESOURCE_TYPE_ANNOTATIONS_SUFFIX)) {
          ResourceType resourceType = getResourceTypeFromAnnotation(qualifiedName);
          if (resourceType != null) {
            if (resourceTypes == null) {
              resourceTypes = Lists.newArrayList();
            }
            resourceTypes.add(resourceType);
          }
        }
      }

      PsiJavaCodeReferenceElement ref = annotation.getNameReferenceElement();
      PsiElement resolved = ref == null ? null : ref.resolve();
      if (!(resolved instanceof PsiClass) || !((PsiClass)resolved).isAnnotationType()) continue;
      PsiClass aClass = (PsiClass)resolved;
      if (visited == null) visited = new THashSet<PsiClass>();
      if (!visited.add(aClass)) continue;
      values = getAllowedValues(aClass, type, visited);
      if (values != null) return values;
    }

    if (resourceTypes != null) {
      return new AllowedValues(PsiAnnotationMemberValue.EMPTY_ARRAY, false, resourceTypes);
    }

    return null;
  }

  @Nullable
  private static PsiType getType(@NotNull PsiModifierListOwner element) {
    return element instanceof PsiVariable ? ((PsiVariable)element).getType() : element instanceof PsiMethod ? ((PsiMethod)element).getReturnType() : null;
  }

  private static void checkMagicParameterArgument(@NotNull PsiParameter parameter,
                                                  @NotNull PsiExpression argument,
                                                  @NotNull AllowedValues allowedValues,
                                                  @NotNull ProblemsHolder holder) {
    final PsiManager manager = PsiManager.getInstance(holder.getProject());

    if (!argument.getTextRange().isEmpty() && !isAllowed(parameter.getDeclarationScope(), argument, allowedValues, manager, null)) {
      registerProblem(argument, allowedValues, holder);
    }
  }

  private static void registerProblem(@NotNull PsiExpression argument, @NotNull AllowedValues allowedValues, @NotNull ProblemsHolder holder) {
    if (allowedValues.types != null) {
      if (allowedValues.types.size() == 1) {
        holder.registerProblem(argument, "Expected resource of type " + allowedValues.types.get(0));
      } else {
        holder.registerProblem(argument, "Expected resource type to be one of " + Joiner.on(", ").join(allowedValues.types));
      }
      return;
    }
    String values = StringUtil.join(allowedValues.values,
                                    new Function<PsiAnnotationMemberValue, String>() {
                                      @Override
                                      public String fun(PsiAnnotationMemberValue value) {
                                        if (value instanceof PsiReferenceExpression) {
                                          PsiElement resolved = ((PsiReferenceExpression)value).resolve();
                                          if (resolved instanceof PsiVariable) {
                                            return PsiFormatUtil.formatVariable((PsiVariable)resolved, PsiFormatUtilBase.SHOW_NAME |
                                                                                                       PsiFormatUtilBase.SHOW_CONTAINING_CLASS, PsiSubstitutor.EMPTY);
                                          }
                                        }
                                        return value.getText();
                                      }
                                    }, ", ");
    holder.registerProblem(argument, "Must be one of: "+ values);
  }

  private static boolean isAllowed(@NotNull final PsiElement scope,
                                   @NotNull final PsiExpression argument,
                                   @NotNull final AllowedValues allowedValues,
                                   @NotNull final PsiManager manager,
                                   @Nullable final Set<PsiExpression> visited) {
    // Resource type check
    if (allowedValues.types != null) {
      return isResourceTypeAllowed(scope, argument, allowedValues, manager, visited);
    }

    if (isGoodExpression(argument, allowedValues, scope, manager, visited)) return true;

    return processValuesFlownTo(argument, scope, manager, new Processor<PsiExpression>() {
      @Override
      public boolean process(PsiExpression expression) {
        return isGoodExpression(expression, allowedValues, scope, manager, visited);
      }
    });
  }

  private static boolean isGoodExpression(@NotNull PsiExpression e,
                                          @NotNull AllowedValues allowedValues,
                                          @NotNull PsiElement scope,
                                          @NotNull PsiManager manager,
                                          @Nullable Set<PsiExpression> visited) {
    PsiExpression expression = PsiUtil.deparenthesizeExpression(e);
    if (expression == null) return true;
    if (visited == null) visited = new THashSet<PsiExpression>();
    if (!visited.add(expression)) return true;
    if (expression instanceof PsiConditionalExpression) {
      PsiExpression thenExpression = ((PsiConditionalExpression)expression).getThenExpression();
      boolean thenAllowed = thenExpression == null || isAllowed(scope, thenExpression, allowedValues, manager, visited);
      if (!thenAllowed) return false;
      PsiExpression elseExpression = ((PsiConditionalExpression)expression).getElseExpression();
      return elseExpression == null || isAllowed(scope, elseExpression, allowedValues, manager, visited);
    }

    // Resource type check
    assert allowedValues.types == null; // Handled separately

    if (isOneOf(expression, allowedValues, manager)) return true;

    if (allowedValues.canBeOred) {
      PsiExpression zero = getLiteralExpression(expression, manager, "0");
      if (same(expression, zero, manager)) return true;
      PsiExpression mOne = getLiteralExpression(expression, manager, "-1");
      if (same(expression, mOne, manager)) return true;
      if (expression instanceof PsiPolyadicExpression) {
        IElementType tokenType = ((PsiPolyadicExpression)expression).getOperationTokenType();
        if (JavaTokenType.OR.equals(tokenType) || JavaTokenType.AND.equals(tokenType) || JavaTokenType.PLUS.equals(tokenType)) {
          for (PsiExpression operand : ((PsiPolyadicExpression)expression).getOperands()) {
            if (!isAllowed(scope, operand, allowedValues, manager, visited)) return false;
          }
          return true;
        }
      }
      if (expression instanceof PsiPrefixExpression &&
          JavaTokenType.TILDE.equals(((PsiPrefixExpression)expression).getOperationTokenType())) {
        PsiExpression operand = ((PsiPrefixExpression)expression).getOperand();
        return operand == null || isAllowed(scope, operand, allowedValues, manager, visited);
      }
    }

    PsiElement resolved = null;
    if (expression instanceof PsiReference) {
      resolved = ((PsiReference)expression).resolve();
    }
    else if (expression instanceof PsiCallExpression) {
      resolved = ((PsiCallExpression)expression).resolveMethod();
    }

    AllowedValues allowedForRef;
    if (resolved instanceof PsiModifierListOwner &&
        (allowedForRef = getAllowedValues((PsiModifierListOwner)resolved, getType((PsiModifierListOwner)resolved), null)) != null &&
        allowedForRef.isSubsetOf(allowedValues, manager)) return true;

    //noinspection ConstantConditions
    return PsiType.NULL.equals(expression.getType());
  }

  /** Return value from {@link #isValidResourceTypeExpression} : the expression is valid */
  private static final int VALID = 1001;
  /** Return value from {@link #isValidResourceTypeExpression} : the expression is not valid */
  private static final int INVALID = VALID + 1;
  /** Return value from {@link #isValidResourceTypeExpression} : uncertain whether the resource type is valid */
  private static final int UNCERTAIN = INVALID + 1;

  private static boolean isResourceTypeAllowed(@NotNull final PsiElement scope,
                                               @NotNull final PsiExpression argument,
                                               @NotNull final AllowedValues allowedValues,
                                               @NotNull final PsiManager manager,
                                               @Nullable final Set<PsiExpression> visited) {
    int result = isValidResourceTypeExpression(argument, allowedValues, scope, manager, visited);
    if (result == VALID) {
      return true;
    } else if (result == INVALID) {
      return false;
    }
    assert result == UNCERTAIN;

    final AtomicInteger b = new AtomicInteger();
    processValuesFlownTo(argument, scope, manager, new Processor<PsiExpression>() {
      @Override
      public boolean process(PsiExpression expression) {
        int goodExpression = isValidResourceTypeExpression(expression, allowedValues, scope, manager, visited);
        b.set(goodExpression);
        return goodExpression == UNCERTAIN;
      }
    });
    result = b.get();
    // Treat uncertain as allowed: this means that we were passed some integer whose origins
    // we don't recognize; don't flag those.
    return result != INVALID;
  }

  private static int isValidResourceTypeExpression(@NotNull PsiExpression e,
                                                   @NotNull AllowedValues allowedValues,
                                                   @NotNull PsiElement scope,
                                                   @NotNull PsiManager manager,
                                                   @Nullable Set<PsiExpression> visited) {
    PsiExpression expression = PsiUtil.deparenthesizeExpression(e);
    if (expression == null) return VALID;
    if (visited == null) visited = new THashSet<PsiExpression>();
    if (!visited.add(expression)) return VALID;
    if (expression instanceof PsiConditionalExpression) {
      PsiExpression thenExpression = ((PsiConditionalExpression)expression).getThenExpression();
      boolean thenAllowed = thenExpression == null || isAllowed(scope, thenExpression, allowedValues, manager, visited);
      if (!thenAllowed) return INVALID;
      PsiExpression elseExpression = ((PsiConditionalExpression)expression).getElseExpression();
      return elseExpression == null || isAllowed(scope, elseExpression, allowedValues, manager, visited) ? VALID : UNCERTAIN;
    }

    // Resource type check
    assert allowedValues.types != null;
    if (expression instanceof PsiReferenceExpression) {
      PsiReferenceExpression refExpression = (PsiReferenceExpression)expression;
      PsiExpression qualifierExpression = refExpression.getQualifierExpression();
      if (qualifierExpression instanceof PsiReferenceExpression) {
        PsiReferenceExpression typeDef = (PsiReferenceExpression)qualifierExpression;
        PsiExpression r = typeDef.getQualifierExpression();
        if (r instanceof PsiReferenceExpression) {
          if (R_CLASS.equals(((PsiReferenceExpression)r).getReferenceName())) {
            String typeName = typeDef.getReferenceName();
            for (ResourceType type : allowedValues.types) {
              if (type.getName().equals(typeName)) {
                return VALID;
              }
            }
            return INVALID;
          }
        }
      }
    }
    else if (expression instanceof PsiLiteral) {
      if (expression instanceof PsiLiteralExpression) {
        PsiElement parent = expression.getParent();
        if (parent instanceof PsiField) {
          parent = parent.getParent();
          if (parent instanceof PsiClass) {
            PsiElement outerMost = parent.getParent();
            if (outerMost instanceof PsiClass && R_CLASS.equals(((PsiClass)outerMost).getName())) {
              PsiClass typeClass = (PsiClass)parent;
              String typeClassName = typeClass.getName();
              for (ResourceType type : allowedValues.types) {
                if (type.getName().equals(typeClassName)) {
                  return VALID;
                }
              }
              return INVALID;
            }
          }
        }
      }

      // Allow a literal '0' or '-1' as the resource type; this is sometimes used to communicate that
      // no id was specified (the support library does this in a few places for example)
      Object value = ((PsiLiteral)expression).getValue();
      if (value instanceof Integer) {
        return ((Integer)value).intValue() == 0 ? VALID : INVALID;
      }
    } else if (expression instanceof PsiPrefixExpression) {
      // Allow a literal '-1' as the resource type; this is sometimes used to communicate that
      // no id was specified
      PsiPrefixExpression ppe = (PsiPrefixExpression)expression;
      if (ppe.getOperationTokenType() == JavaTokenType.MINUS &&
          ppe.getOperand() instanceof PsiLiteral) {
        Object value = ((PsiLiteral)ppe.getOperand()).getValue();
        if (value instanceof Integer) {
          return ((Integer)value).intValue() == 1 ? VALID : INVALID;
        }
      }
    }

    PsiElement resolved = null;
    if (expression instanceof PsiReference) {
      resolved = ((PsiReference)expression).resolve();
      if (resolved instanceof PsiField) {
        PsiField field = (PsiField)resolved;
        PsiClass containingClass = field.getContainingClass();
        if (containingClass != null) {
          PsiClass r = containingClass.getContainingClass();
          if (r != null && R_CLASS.equals(r.getName())) {
            ResourceType type = ResourceType.getEnum(containingClass.getName());
            if (type != null) {
              for (ResourceType t : allowedValues.types) {
                if (t == type) {
                  return VALID;
                }
              }
              return INVALID;
            }
          }
        }
      }
    }
    else if (expression instanceof PsiCallExpression) {
      resolved = ((PsiCallExpression)expression).resolveMethod();
    }

    AllowedValues allowedForRef;

    if (resolved instanceof PsiModifierListOwner) {
      PsiType type = getType((PsiModifierListOwner)resolved);
      allowedForRef = getAllowedValues((PsiModifierListOwner)resolved, type, null);
      if (allowedForRef != null && allowedForRef.types != null) {
        // Happy if *any* of the resource types on the annotation matches any of the
        // annotations allowed for this API
        for (ResourceType t1 : allowedForRef.types) {
          for (ResourceType t2 : allowedValues.types) {
            if (t1 == t2) {
              return VALID;
            }
          }
        }
        return INVALID;
      }
    }

    return UNCERTAIN;
  }

  // Would be nice to reuse the MagicConstantInspection's cache for this, but it's not accessible
  private static final Key<Map<String, PsiExpression>> LITERAL_EXPRESSION_CACHE = Key.create("TYPE_DEF_LITERAL_EXPRESSION");
  private static PsiExpression getLiteralExpression(@NotNull PsiExpression context, @NotNull PsiManager manager, @NotNull String text) {
    Map<String, PsiExpression> cache = LITERAL_EXPRESSION_CACHE.get(manager);
    if (cache == null) {
      cache = new ConcurrentSoftValueHashMap<String, PsiExpression>();
      cache = manager.putUserDataIfAbsent(LITERAL_EXPRESSION_CACHE, cache);
    }
    PsiExpression expression = cache.get(text);
    if (expression == null) {
      expression = JavaPsiFacade.getElementFactory(manager.getProject()).createExpressionFromText(text, context);
      cache.put(text, expression);
    }
    return expression;
  }

  private static boolean isOneOf(@NotNull PsiExpression expression, @NotNull AllowedValues allowedValues, @NotNull PsiManager manager) {
    for (PsiAnnotationMemberValue allowedValue : allowedValues.values) {
      if (same(allowedValue, expression, manager)) return true;
    }
    return false;
  }

  private static boolean same(@Nullable PsiElement e1, @Nullable PsiElement e2, @NotNull PsiManager manager) {
    if (e1 instanceof PsiLiteralExpression && e2 instanceof PsiLiteralExpression) {
      return Comparing.equal(((PsiLiteralExpression)e1).getValue(), ((PsiLiteralExpression)e2).getValue());
    }
    if (e1 instanceof PsiPrefixExpression && e2 instanceof PsiPrefixExpression && ((PsiPrefixExpression)e1).getOperationTokenType() == ((PsiPrefixExpression)e2).getOperationTokenType()) {
      return same(((PsiPrefixExpression)e1).getOperand(), ((PsiPrefixExpression)e2).getOperand(), manager);
    }
    if (e1 instanceof PsiReference && e2 instanceof PsiReference) {
      e1 = ((PsiReference)e1).resolve();
      e2 = ((PsiReference)e2).resolve();
    }
    return manager.areElementsEquivalent(e2, e1);
  }

  private static boolean processValuesFlownTo(@NotNull final PsiExpression argument,
                                              @NotNull PsiElement scope,
                                              @NotNull PsiManager manager,
                                              @NotNull final Processor<PsiExpression> processor) {
    SliceAnalysisParams params = new SliceAnalysisParams();
    params.dataFlowToThis = true;
    params.scope = new AnalysisScope(new LocalSearchScope(scope), manager.getProject());

    SliceRootNode rootNode = new SliceRootNode(manager.getProject(), new DuplicateMap(), SliceUsage.createRootUsage(argument, params));

    @SuppressWarnings("unchecked")
    Collection<? extends AbstractTreeNode> children = rootNode.getChildren().iterator().next().getChildren();
    for (AbstractTreeNode child : children) {
      SliceUsage usage = (SliceUsage)child.getValue();
      PsiElement element = usage.getElement();
      if (element instanceof PsiExpression && !processor.process((PsiExpression)element)) return false;
    }

    return !children.isEmpty();
  }
}
