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
package com.android.tools.idea.folding;

import com.android.resources.ResourceType;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.res.AppResourceRepository;
import com.android.tools.idea.res.LocalResourceRepository;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static com.android.SdkConstants.STRING_PREFIX;
import static com.android.tools.idea.folding.InlinedResource.NONE;

public class ResourceFoldingBuilder extends FoldingBuilderEx {
  private static final String ANDROID_RESOURCE_INT = "android.annotation.ResourceInt";
  private static final boolean ONLY_FOLD_ANNOTATED_METHODS = false;
  private static final boolean UNIT_TEST_MODE =  ApplicationManager.getApplication().isUnitTestMode();
  public static final String DIMEN_PREFIX = "@dimen/";
  public static final String INTEGER_PREFIX = "@integer/";

  public ResourceFoldingBuilder() {
  }

  private static boolean isFoldingEnabled() {
    return AndroidFoldingSettings.getInstance().isCollapseAndroidStrings();
  }

  @Override
  public boolean isCollapsedByDefault(@NotNull ASTNode node) {
    return isFoldingEnabled();
  }

  @Override
  public String getPlaceholderText(@NotNull ASTNode node) {
    PsiElement element = SourceTreeToPsiMap.treeElementToPsi(node);
    if (element != null) {
      InlinedResource string = getResolvedString(element);
      if (string != NONE) {
        String foldLabel = string.getResolvedString();
        if (foldLabel != null) {
          return foldLabel;
        }
      }
    }

    return element != null ? element.getText() : node.getText();
  }

  @Override
  @NotNull
  public FoldingDescriptor[] buildFoldRegions(@NotNull PsiElement element, @NotNull Document document, boolean quick) {
    if (!(element instanceof PsiJavaFile || element instanceof XmlFile) || quick && !UNIT_TEST_MODE || !isFoldingEnabled()) {
      return FoldingDescriptor.EMPTY;
    }
    final List<FoldingDescriptor> result = new ArrayList<FoldingDescriptor>();
    if (element instanceof PsiJavaFile) {
      final PsiJavaFile file = (PsiJavaFile) element;
      file.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitReferenceExpression(PsiReferenceExpression expression) {
          InlinedResource inlinedResource = findJavaExpressionReference(expression);
          if (inlinedResource != NONE) {
            result.add(inlinedResource.getDescriptor());
          }
          super.visitReferenceExpression(expression);
        }
      });
    } else {
      final XmlFile file = (XmlFile) element;
      file.accept(new XmlRecursiveElementVisitor() {
        @Override
        public void visitXmlAttributeValue(XmlAttributeValue value) {
          InlinedResource inlinedResource = findXmlValueReference(value);
          if (inlinedResource != NONE) {
            FoldingDescriptor descriptor = inlinedResource.getDescriptor();
            if (descriptor != null) {
              result.add(descriptor);
            }
          }
          super.visitXmlAttributeValue(value);
        }
      });
    }

    return result.toArray(new FoldingDescriptor[result.size()]);
  }

  @NotNull
  private static InlinedResource getResolvedString(PsiElement element) {
    if (element instanceof PsiReferenceExpression) {
      return findJavaExpressionReference((PsiReferenceExpression)element);
    } else if (element instanceof XmlAttributeValue) {
      return findXmlValueReference((XmlAttributeValue)element);
    } else if (element instanceof PsiMethodCallExpression) {
      // This can happen when a folding lookup for a parameter ends up returning the
      // surrounding method call as the folding region; in that case we have to map
      // back to the right parameter
      PsiMethodCallExpression call = (PsiMethodCallExpression)element;
      for (PsiExpression expression : call.getArgumentList().getExpressions()) {
        if (expression instanceof PsiReferenceExpression) {
          InlinedResource string = findJavaExpressionReference((PsiReferenceExpression)expression);
          if (string != NONE) {
            return string;
          }
        }
      }
    }

    return NONE;
  }

  @NotNull
  private static InlinedResource findXmlValueReference(XmlAttributeValue element) {
    String value = element.getValue();
    if (value.startsWith(STRING_PREFIX)) {
      String name = value.substring(STRING_PREFIX.length());
      return createdInlinedResource(ResourceType.STRING, name, element);
    } else if (value.startsWith(DIMEN_PREFIX)) {
      String name = value.substring(DIMEN_PREFIX.length());
      return createdInlinedResource(ResourceType.DIMEN, name, element);
    } else if (value.startsWith(INTEGER_PREFIX)) {
      String name = value.substring(INTEGER_PREFIX.length());
      return createdInlinedResource(ResourceType.INTEGER, name, element);
    } else {
      return NONE;
    }
  }

  @NotNull
  private static InlinedResource findJavaExpressionReference(PsiReferenceExpression expression) {
    AndroidPsiUtils.ResourceReferenceType referenceType = AndroidPsiUtils.getResourceReferenceType(expression);
    if (referenceType != AndroidPsiUtils.ResourceReferenceType.APP) {
      return NONE;
    }
    ResourceType type = AndroidPsiUtils.getResourceType(expression);
    if (type == null || !(type == ResourceType.STRING || type == ResourceType.DIMEN || type == ResourceType.INTEGER ||
        type == ResourceType.PLURALS)) {
      return NONE;
    }

    PsiElement parameterList = expression.getParent();
    String name = AndroidPsiUtils.getResourceName(expression);
    if (parameterList instanceof PsiExpressionList) {
      PsiElement call = parameterList.getParent();
      if (call instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression callExpression = (PsiMethodCallExpression)call;
        PsiReferenceExpression methodExpression = callExpression.getMethodExpression();
        String methodName = methodExpression.getReferenceName();
        if (methodName != null &&
            (methodName.equals("getString") ||
             methodName.equals("getText") ||
             methodName.equals("getInteger") ||
             methodName.startsWith("getDimension") ||
             methodName.startsWith("getQuantityString"))) {
          // This seems to be an IntelliJ bug; it complains that type can be null, but it clearly can not
          // (and if I insert assert type != null it correctly says that the assertion is not necessary)
          //noinspection ConstantConditions
          @NotNull ResourceType resourceType = type;
          //noinspection ConstantConditions
          return createdInlinedResource(resourceType, name, callExpression);
        }

        //noinspection ConstantConditions
        if (!UNIT_TEST_MODE && ONLY_FOLD_ANNOTATED_METHODS) {
          PsiParameter[] parameters = null;
          int parameterIndex = ArrayUtil.indexOf(callExpression.getArgumentList().getExpressions(), expression);
          if (parameterIndex == -1) {
            return NONE;
          }
          PsiMethod method = callExpression.resolveMethod();
          if (!UNIT_TEST_MODE) { // For some reason, we can't resolve PsiMethods from the unit tests
            if (method == null) {
              return NONE;
            }
            parameters = method.getParameterList().getParameters();
            if (parameters.length <= parameterIndex) {
              return NONE;
            }
          }

          if (!allowsResourceType(ResourceType.STRING, parameters[parameterIndex])) {
            return NONE;
          }
        }
      }
    }

    // Suppress null warning; see @NotNull comment further up in this method
    //noinspection ConstantConditions
    return createdInlinedResource(type, name, expression);
  }

  @Nullable
  private static LocalResourceRepository getAppResources(PsiElement element) {
    Module module = ModuleUtilCore.findModuleForPsiElement(element);
    if (module == null) {
      return null;
    }

    return AppResourceRepository.getOrCreateInstance(module);
  }

  private static InlinedResource createdInlinedResource(@NotNull ResourceType type, @NotNull String name,
                                                        @NotNull PsiElement foldElement) {
    // Not part of a call: just fold the R.string reference itself
    LocalResourceRepository appResources = getAppResources(foldElement);
    if (appResources != null && appResources.hasResourceItem(type, name)) {
      ASTNode node = foldElement.getNode();
      if (node != null) {
        TextRange textRange = foldElement.getTextRange();
        HashSet<Object> dependencies = new HashSet<Object>();
        dependencies.add(foldElement);
        FoldingDescriptor descriptor = new FoldingDescriptor(node, textRange, null, dependencies);
        InlinedResource inlinedResource = new InlinedResource(type, name, appResources, descriptor, foldElement);
        dependencies.add(inlinedResource);
        return inlinedResource;
      }
    }

    return NONE;
  }

  /**
   * Returns true if the given modifier list owner (such as a method or parameter)
   * specifies a {@code @ResourceInt} which includes the given {@code type}.
   *
   * @param type the resource type to check
   * @param owner the potentially annotated element to check
   * @return true if the resource type is allowed
   */
  public static boolean allowsResourceType(@NotNull ResourceType type, @Nullable PsiModifierListOwner owner) {
    if (owner == null) {
      return false;
    }
    PsiAnnotation annotation = AnnotationUtil.findAnnotation(owner, ANDROID_RESOURCE_INT);
    Boolean allowed = allowsResourceType(type, annotation);
    return allowed != null && allowed.booleanValue();
  }

  /**
   * Returns true if the given {@code @ResourceInt} annotation usage specifies that the given resource type
   * is allowed
   *
   * @param type the resource type to check
   * @param annotation an annotation usage on an element
   * @return true if the resource type is allowed, false if it is not, and null if no annotation
   *   was found
   */
  @Nullable
  public static Boolean allowsResourceType(@NotNull ResourceType type, @Nullable PsiAnnotation annotation) {
    if (annotation == null) {
      return null;
    }
    assert ANDROID_RESOURCE_INT.equals(annotation.getQualifiedName());
    PsiAnnotationParameterList annotationParameters = annotation.getParameterList();
    for (PsiNameValuePair pair : annotationParameters.getAttributes()) {
      PsiAnnotationMemberValue value = pair.getValue();
      if (value instanceof PsiReferenceExpression) {
        PsiReferenceExpression expression = (PsiReferenceExpression) value;
        return allowsResourceType(type, expression);
      } else if (value instanceof PsiArrayInitializerMemberValue) {
        PsiArrayInitializerMemberValue mv = (PsiArrayInitializerMemberValue) value;
        for (PsiAnnotationMemberValue v : mv.getInitializers()) {
          if (v instanceof PsiReferenceExpression) {
            Boolean b = allowsResourceType(type, (PsiReferenceExpression)v);
            if (b != null) {
              return b;
            }
          }
        }
      }
    }

    return null;
  }

  private static Boolean allowsResourceType(ResourceType type, PsiReferenceExpression v) {
    // When the @ResourceInt annotation is added to the SDK and potentially added to the
    // user's classpath, we should resolve this constant properly as follows:
    //  PsiElement resolved = v.resolve();
    //  if (resolved instanceof PsiEnumConstant) {
    //    String name = ((PsiEnumConstant) resolved).getName();
    // However, for now these are just annotations in the external annotations file,
    // so we simply use the text tokens:
    String name = v.getText();
    if (name.equals("all")) { // Corresponds to ResourceInt.Type.ALL
      return true;
    } else if (name.equals("none")) { // Corresponds to ResourceInt.Type.NONE
      return false;
    } else {
      return type.getName().equalsIgnoreCase(name);
    }
  }
}
