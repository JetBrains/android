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

import com.android.AndroidXConstants;
import com.android.resources.ResourceType;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.codeInsight.lookup.VariableLookupItem;
import com.intellij.codeInspection.magicConstant.MagicCompletionContributor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.TypeConversionUtil;
import java.util.ArrayList;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.kotlin.asJava.LightClassUtilsKt;
import org.jetbrains.kotlin.psi.KtClass;
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression;
import org.jetbrains.kotlin.psi.KtElement;

import static com.android.SdkConstants.*;
import static com.android.tools.lint.detector.api.ResourceEvaluator.*;
import static org.jetbrains.kotlin.asJava.LightClassUtilsKt.toLightClass;

/**
 * A custom version of the IntelliJ {@link MagicCompletionContributor}, almost identical, except
 * the changes are:
 * <li>
 *   it calls {@link ResourceTypeInspection}
 *    instead of {@link com.intellij.codeInspection.magicConstant.MagicConstantInspection}
 *    to produce the set of values it will offer (actually these have been inlined into
 *    the class)
 * </li>
 * <li>
 *   it can compute resource type suggestions ({@code R.string}, {@code R.drawable}, etc) when
 *   completing parameters or return types that have been annotated with one of the resource
 *   type annotations: {@code @android.support.annotation.StringRes},
 *   {@code @android.support.annotation.DrawableRes}, ...
 * </li>
 * <li>
 *   it can compute suggestions for Android typedefs ({@code @IntDef}, {@code @LongDef}, {@code @StringDef})
 *   based on the values of those typedefs
 * </li>
 */
public class ResourceTypeCompletionContributor extends CompletionContributor {
  private static final int PRIORITY = 100;

  @Override
  public void fillCompletionVariants(@NotNull final CompletionParameters parameters, @NotNull final CompletionResultSet result) {
    //if (parameters.getCompletionType() != CompletionType.SMART) return;
    PsiElement pos = parameters.getPosition();

    if (JavaKeywordCompletion.AFTER_DOT.accepts(pos)) {
      return;
    }

    AndroidFacet facet = AndroidFacet.getInstance(pos);
    if (facet == null) {
      return;
    }

    Constraints allowedValues = getAllowedValues(pos);
    if (allowedValues == null) return;

    final Set<PsiElement> allowed = new ObjectOpenCustomHashSet<>(new Hash.Strategy<PsiElement>() {
      @Override
      public int hashCode(PsiElement object) {
        return 0;
      }

      @Override
      public boolean equals(PsiElement o1, PsiElement o2) {
        return parameters.getOriginalFile().getManager().areElementsEquivalent(o1, o2);
      }
    });

    // Suggest resource types
    if (allowedValues instanceof ResourceTypeAllowedValues) {
      for (ResourceType resourceType : ((ResourceTypeAllowedValues)allowedValues).types) {
        // We should *not* offer completion for non-resource type resource types such as "public"; these
        // are markers for @ColorInt and @Px
        if (resourceType.isSynthetic()) {
          continue;
        }
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(pos.getProject());
        String code = "R." + resourceType.getName();
        // Look up the fully qualified name of the application package
        String fqcn = ProjectSystemUtil.getModuleSystem(facet).getPackageName();
        String qualifiedCode = fqcn + "." + code;
        Project project = facet.getModule().getProject();
        PsiClass cls = JavaPsiFacade.getInstance(project).findClass(qualifiedCode, GlobalSearchScope.allScope(project));
        if (cls != null) {
          result.addElement(new JavaPsiClassReferenceElement(cls));
        } else {
          PsiExpression type = factory.createExpressionFromText(code, pos);
          result.addElement(PrioritizedLookupElement.withPriority(LookupElementBuilder.create(type, code), PRIORITY - 1));
          allowed.add(type);
        }
      }
    } else if (allowedValues instanceof AllowedValues) {
       AllowedValues a = (AllowedValues)allowedValues;
      if (a.canBeOred) {
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(pos.getProject());
        PsiExpression zero = factory.createExpressionFromText("0", pos);
        result.addElement(PrioritizedLookupElement.withPriority(LookupElementBuilder.create(zero, "0"), PRIORITY - 1));
        PsiExpression minusOne = factory.createExpressionFromText("-1", pos);
        result.addElement(PrioritizedLookupElement.withPriority(LookupElementBuilder.create(minusOne, "-1"), PRIORITY - 1));
        allowed.add(zero);
        allowed.add(minusOne);
      }
      List<ExpectedTypeInfo> types = Arrays.asList(JavaSmartCompletionContributor.getExpectedTypes(parameters));
      for (PsiAnnotationMemberValue value : a.values) {
        @Nullable PsiNamedElement namedElement = getNamedElementFromAnnotationMember(value);
        if (namedElement != null) {
          LookupElement element = PrioritizedLookupElement.withPriority(LookupElementBuilder.create(namedElement), PRIORITY);
          result.addElement(decorate(parameters, types, element));
          allowed.add(namedElement);
        }
        else {
          LookupElement element = decorate(parameters, types, LookupElementBuilder.create(value, value.getText()));
          result.addElement(decorate(parameters, types, element));
          allowed.add(value);
        }
      }
    }

    result.runRemainingContributors(parameters, completionResult -> {
      Object object = completionResult.getLookupElement().getObject();
      if (object instanceof PsiElement && allowed.contains(object)) {
        return;
      }
      result.passResult(completionResult);
    });
  }

  @Nullable
  private static PsiNamedElement getNamedElementFromAnnotationMember(PsiAnnotationMemberValue v) {
    if (v instanceof PsiReference) {
      if (((PsiReference)v).resolve() instanceof PsiNamedElement named) return named;
    }
    else if (v instanceof LightElement) {
      PsiElement navigationElement = v.getNavigationElement();
      if (navigationElement instanceof KtDotQualifiedExpression dotQualifiedExpression) {
        navigationElement = dotQualifiedExpression.getSelectorExpression();
      }
      PsiReference psiReference = navigationElement.getReference();
      if (psiReference != null) {
        PsiElement element = psiReference.resolve();
        if (element instanceof KtElement ktElement) {
          return LightClassUtilsKt.toLightElements(ktElement).stream().findFirst().orElse(null);
        }
      }
    }
    return null;
  }

  @Nullable
  private static Constraints getAllowedValues(@NotNull PsiElement pos) {
    Constraints allowedValues = null;
    for (Pair<PsiModifierListOwner, PsiType> pair : MagicCompletionContributor.getMembersWithAllowedValues(pos)) {
      Constraints values = getAllowedValues(pair.first, pair.second, null);
      if (values == null) continue;
      if (allowedValues == null) {
        allowedValues = values;
        continue;
      }
      if (!allowedValues.equals(values)) return null;
    }
    return allowedValues;
  }


  private static LookupElement decorate(CompletionParameters parameters, List<ExpectedTypeInfo> types, LookupElement element) {
    if (!types.isEmpty() && parameters.getCompletionType() == CompletionType.SMART) {
      element = JavaSmartCompletionContributor.decorate(element, types);
    }
    return element;
  }

  @Nullable
  public static Constraints getAllowedValues(@NotNull PsiModifierListOwner element, @Nullable PsiType type, @Nullable Set<PsiClass> visited) {
    PsiAnnotation[] annotations = getAllAnnotations(element);
    PsiManager manager = element.getManager();
    List<ResourceType> resourceTypes = null;
    Constraints constraint = null;
    for (PsiAnnotation annotation : annotations) {
      String qualifiedName = annotation.getQualifiedName();
      if (qualifiedName == null) {
        continue;
      }

      if (AndroidXConstants.SUPPORT_ANNOTATIONS_PREFIX.isPrefix(qualifiedName) || qualifiedName.startsWith("test.pkg.")) {
        if (AndroidXConstants.INT_DEF_ANNOTATION.isEquals(qualifiedName)
            || AndroidXConstants.STRING_DEF_ANNOTATION.isEquals(qualifiedName)
            || AndroidXConstants.LONG_DEF_ANNOTATION.isEquals(qualifiedName)) {
          if (type != null && !(annotation instanceof PsiCompiledElement)) { // Don't fetch constants from .class files: can't hold data
            constraint = merge(getAllowedValuesFromTypedef(type, annotation, manager), constraint);
          }
        }
        else if (COLOR_INT_ANNOTATION.isEquals(qualifiedName)) {
          constraint = merge(new ResourceTypeAllowedValues(Collections.singletonList(COLOR_INT_MARKER_TYPE)), constraint);
        }
        else if (PX_ANNOTATION.isEquals(qualifiedName) || DIMENSION_ANNOTATION.isEquals(qualifiedName)) {
          constraint = merge(new ResourceTypeAllowedValues(Collections.singletonList(DIMENSION_MARKER_TYPE)), constraint);
        }
        else if (qualifiedName.endsWith(RES_SUFFIX)) {
          ResourceType resourceType = getResourceTypeFromAnnotation(qualifiedName);
          if (resourceType != null) {
            if (resourceTypes == null) {
              resourceTypes = new ArrayList<>();
            }
            resourceTypes.add(resourceType);
          }
        }
      }

      if (constraint == null) {
        PsiJavaCodeReferenceElement ref = annotation.getNameReferenceElement();
        PsiElement resolved = ref == null ? null : ref.resolve();
        if (resolved != null) resolved = resolved.getNavigationElement();
        if (resolved instanceof KtClass ktClass) resolved = toLightClass(ktClass);
        if (resolved instanceof PsiClass aClass && aClass.isAnnotationType()) {
          if (visited == null) visited = new HashSet<>();
          if (!visited.add(aClass)) continue;
          constraint = getAllowedValues(aClass, type, visited);
        }
      }
    }

    if (resourceTypes != null) {
      constraint = merge(new ResourceTypeAllowedValues(resourceTypes), constraint);
    }

    return constraint;
  }

  @Nullable
  private static Constraints merge(@Nullable Constraints head, @Nullable Constraints tail) {
    if (head != null) {
      if (tail != null) {
        head.next = tail;

        // The only valid combination of multiple constraints are @IntDef and @IntRange.
        // In this case, always arrange for the IntDef constraint to be processed first
        if (tail instanceof AllowedValues) {
          head.next = tail.next;
          tail.next = head;
          head = tail;
        }
      }
      return head;
    }
    return tail;
  }

  @NotNull
  public static PsiAnnotation[] getAllAnnotations(@NotNull final PsiModifierListOwner element) {
    return CachedValuesManager.getCachedValue(element,
                                              () -> CachedValueProvider.Result.create(AnnotationUtil.getAllAnnotations(element, true, null),
                                                                                      PsiModificationTracker.MODIFICATION_COUNT));
  }

  @Nullable
  private static Constraints getAllowedValuesFromTypedef(@NotNull PsiType type,
                                                                                @NotNull PsiAnnotation magic,
                                                                                @NotNull PsiManager manager) {
    PsiAnnotationMemberValue[] allowedValues;
    final boolean canBeOred;

    // Extract the actual type of the declaration. For examples, for int[], extract the int
    if (type instanceof PsiEllipsisType) {
      type = ((PsiEllipsisType)type).getComponentType();
    } else if (type instanceof PsiArrayType) {
      type = ((PsiArrayType)type).getComponentType();
    }
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
      return new AllowedValues(allowedValues, canBeOred);
    }

    return null;
  }

  static class Constraints {
    public boolean isSubsetOf(@NotNull Constraints other, @NotNull PsiManager manager) {
      return false;
    }

    /** Linked list next reference, when more than one applies */
    @Nullable public Constraints next;
  }

  /**
   * A typedef constraint. Then name is kept as "AllowedValues" to keep all the surrounding code
   * which references this class unchanged (since it's based on MagicConstantInspection, so we
   * can more easily diff and incorporate recent MagicConstantInspection changes.)
   */
  static class AllowedValues extends Constraints {
    final PsiAnnotationMemberValue[] values;
    final boolean canBeOred;

    private AllowedValues(@NotNull PsiAnnotationMemberValue[] values, boolean canBeOred) {
      this.values = values;
      this.canBeOred = canBeOred;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      AllowedValues a2 = (AllowedValues)o;
      if (canBeOred != a2.canBeOred) {
        return false;
      }
      Set<PsiAnnotationMemberValue> v1 = new HashSet<>(Arrays.asList(values));
      Set<PsiAnnotationMemberValue> v2 = new HashSet<>(Arrays.asList(a2.values));
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

    @Override
    public boolean isSubsetOf(@NotNull Constraints other, @NotNull PsiManager manager) {
      if (!(other instanceof AllowedValues)) {
        return false;
      }
      AllowedValues o = (AllowedValues)other;
      for (PsiAnnotationMemberValue value : values) {
        boolean found = false;
        for (PsiAnnotationMemberValue otherValue : o.values) {
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

  private static boolean same(@Nullable PsiElement e1, @Nullable PsiElement e2, @NotNull PsiManager manager) {
    if (e1 instanceof PsiLiteralExpression && e2 instanceof PsiLiteralExpression) {
      return Objects.equals(((PsiLiteralExpression)e1).getValue(), ((PsiLiteralExpression)e2).getValue());
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

  @Nullable
  public static ResourceType getResourceTypeFromAnnotation(@NotNull String qualifiedName) {
    String resourceTypeName;

    if (qualifiedName.startsWith(AndroidXConstants.SUPPORT_ANNOTATIONS_PREFIX.oldName())) {
      resourceTypeName = Character.toLowerCase(qualifiedName.charAt(AndroidXConstants.SUPPORT_ANNOTATIONS_PREFIX.oldName().length())) +
                         qualifiedName
                           .substring(AndroidXConstants.SUPPORT_ANNOTATIONS_PREFIX.oldName().length() + 1, qualifiedName.length() - RES_SUFFIX.length());
    }
    else {
      resourceTypeName = Character.toLowerCase(qualifiedName.charAt(AndroidXConstants.SUPPORT_ANNOTATIONS_PREFIX.newName().length())) +
                         qualifiedName
                           .substring(AndroidXConstants.SUPPORT_ANNOTATIONS_PREFIX.newName().length() + 1, qualifiedName.length() - RES_SUFFIX.length());
    }
    return ResourceType.fromClassName(resourceTypeName);
  }

  static class ResourceTypeAllowedValues extends Constraints {
    /**
     * Type of Android resource that we must be passing. An empty list means no
     * resource type is allowed; this is currently used for {@code @ColorInt},
     * stating that not only is it <b>not</b> supposed to be a {@code R.color.name},
     * but it should correspond to an ARGB integer.
     */
    @NotNull
    final List<ResourceType>  types;

    public ResourceTypeAllowedValues(@NotNull List<ResourceType> types) {
      this.types = types;
    }

    /** Returns true if this resource type constraint allows a type of the given name */
    public boolean isTypeAllowed(@NotNull ResourceType type) {
      return isTypeAllowed(type.getName());
    }

    public boolean isTypeAllowed(@NotNull String typeName) {
      for (ResourceType type : types) {
        if (type.getName().equals(typeName) ||
            type == ResourceType.DRAWABLE &&
            (ResourceType.COLOR.getName().equals(typeName) || ResourceType.MIPMAP.getName().equals(typeName))) {
          return true;
        }
      }
      return false;
    }

    /**
     * Returns true if the resource type constraint is compatible with the other resource type
     * constraint
     *
     * @param other the resource type constraint to compare it to
     * @return true if the two resource constraints are compatible
     */
    public boolean isCompatibleWith(@NotNull ResourceTypeAllowedValues other) {
      // Happy if *any* of the resource types on the annotation matches any of the
      // annotations allowed for this API
      for (ResourceType type : other.types) {
        if (isTypeAllowed(type)) {
          return true;
        }
      }

      return false;
    }
  }



}
