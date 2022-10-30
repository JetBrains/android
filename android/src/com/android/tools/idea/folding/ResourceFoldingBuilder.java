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

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.resources.ResourceRepository;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiAnnotationParameterList;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.ArrayUtil;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ResourceFoldingBuilder extends FoldingBuilderEx {
  private static final String ANDROID_RESOURCE_INT = "android.annotation.ResourceInt";
  private static final boolean ONLY_FOLD_ANNOTATED_METHODS = false;
  private static final boolean UNIT_TEST_MODE =  ApplicationManager.getApplication().isUnitTestMode();

  public ResourceFoldingBuilder() { }

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
      if (string != null) {
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
      return FoldingDescriptor.EMPTY_ARRAY;
    }
    final List<FoldingDescriptor> result = new ArrayList<>();
    if (element instanceof PsiJavaFile) {
      final PsiJavaFile file = (PsiJavaFile) element;
      file.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
          InlinedResource inlinedResource = findJavaExpressionReference(expression);
          if (inlinedResource != null) {
            result.add(inlinedResource.getDescriptor());
          }
          super.visitReferenceExpression(expression);
        }
      });
    } else {
      final XmlFile file = (XmlFile) element;
      file.accept(new XmlRecursiveElementVisitor() {
        @Override
        public void visitXmlAttributeValue(@NotNull XmlAttributeValue value) {
          InlinedResource inlinedResource = findXmlValueReference(value);
          if (inlinedResource != null) {
            FoldingDescriptor descriptor = inlinedResource.getDescriptor();
            result.add(descriptor);
          }
          super.visitXmlAttributeValue(value);
        }
      });
    }

    return result.toArray(FoldingDescriptor.EMPTY_ARRAY);
  }

  @Nullable
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
          if (string != null) {
            return string;
          }
        }
      }
    }

    return null;
  }

  @Nullable
  private static InlinedResource findXmlValueReference(XmlAttributeValue element) {
    String value = element.getValue();
    ResourceUrl resourceUrl = ResourceUrl.parse(value);
    if (resourceUrl == null) {
      return null;
    }
    if (resourceUrl.type.equals(ResourceType.STRING) ||
        resourceUrl.type.equals(ResourceType.DIMEN) ||
        resourceUrl.type.equals(ResourceType.INTEGER)) {
      ResourceReference resourceReference = IdeResourcesUtil.resolve(resourceUrl, element);
      if (resourceReference != null) {
        return createdInlinedResource(resourceReference, element);
      }
    }
    return null;
  }

  @Nullable
  private static InlinedResource findJavaExpressionReference(PsiReferenceExpression expression) {
    AndroidPsiUtils.ResourceReferenceType referenceType = AndroidPsiUtils.getResourceReferenceType(expression);
    if (referenceType != AndroidPsiUtils.ResourceReferenceType.APP) {
      return null;
    }
    ResourceType type = AndroidPsiUtils.getResourceType(expression);
    if (!(type == ResourceType.STRING || type == ResourceType.DIMEN || type == ResourceType.INTEGER ||
          type == ResourceType.PLURALS)) {
      return null;
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
          ResourceReference reference = new ResourceReference(ResourceNamespace.RES_AUTO, type, name);
          return createdInlinedResource(reference, callExpression);
        }

        if (!UNIT_TEST_MODE && ONLY_FOLD_ANNOTATED_METHODS) {
          PsiParameter[] parameters = null;
          int parameterIndex = ArrayUtil.indexOf(callExpression.getArgumentList().getExpressions(), expression);
          if (parameterIndex == -1) {
            return null;
          }
          PsiMethod method = callExpression.resolveMethod();
          if (!UNIT_TEST_MODE) { // For some reason, we can't resolve PsiMethods from the unit tests
            if (method == null) {
              return null;
            }
            parameters = method.getParameterList().getParameters();
            if (parameters.length <= parameterIndex) {
              return null;
            }
          }

          if (!allowsResourceType(ResourceType.STRING, parameters[parameterIndex])) {
            return null;
          }
        }
      }
    }

    ResourceReference reference = new ResourceReference(ResourceNamespace.RES_AUTO, type, name);
    return createdInlinedResource(reference, expression);
  }

  @Nullable
  private static InlinedResource createdInlinedResource(@NotNull ResourceReference resourceReference, @NotNull PsiElement foldElement) {
    Module module = ModuleUtilCore.findModuleForPsiElement(foldElement);
    if (module == null) {
      return null;
    }
    ASTNode node = foldElement.getNode();
    if (node != null) {
      TextRange textRange = foldElement.getTextRange();
      FoldingDescriptor descriptor = new FoldingDescriptor(node, textRange, null);
      ResourceRepositoryManager repositoryManager = ResourceRepositoryManager.getInstance(module);
      if (repositoryManager == null) {
        return null;
      }
      ResourceRepository resourceRepository = repositoryManager.getResourcesForNamespace(resourceReference.getNamespace());
      if (resourceRepository == null) {
        return null;
      }
      if (resourceRepository.hasResources(resourceReference.getNamespace(), resourceReference.getResourceType(),
                                          resourceReference.getName())) {
        return new InlinedResource(resourceReference, resourceRepository, descriptor, foldElement);
      }
    }

    return null;
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
    return allowed != null && allowed;
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
            return allowsResourceType(type, (PsiReferenceExpression)v);
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
