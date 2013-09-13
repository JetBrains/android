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
import com.android.tools.idea.rendering.ProjectResources;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static com.android.SdkConstants.R_CLASS;
import static com.android.SdkConstants.STRING_PREFIX;
import static com.android.tools.idea.folding.ResourceString.NONE;

public class ResourceFoldingBuilder extends FoldingBuilderEx {
  private static final String ANDROID_RESOURCE_INT = "android.annotation.ResourceInt";
  private static final boolean FORCE_PROJECT_RESOURCE_LOADING = true;
  private static final Key<ResourceString> CACHE = Key.create("android.resourceString.cache");
  private static final boolean UNIT_TEST_MODE =  ApplicationManager.getApplication().isUnitTestMode();

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
      ResourceString string = getResolvedString(element);
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
        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
          ResourceString resourceString = getResolvedString(expression);
          if (resourceString != NONE) {
            result.add(resourceString.getDescriptor());
          }
          super.visitMethodCallExpression(expression);
        }
      });
    } else {
      final XmlFile file = (XmlFile) element;
      file.accept(new XmlRecursiveElementVisitor() {
        @Override
        public void visitXmlAttributeValue(XmlAttributeValue value) {
          ResourceString resourceString = getResolvedString(value);
          if (resourceString != NONE) {
            FoldingDescriptor descriptor = resourceString.getDescriptor();
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
  private static ResourceString getResolvedString(PsiElement element) {
    ResourceString item = element.getUserData(CACHE);
    if (item != null) {
      // We need to refresh the folding descriptors since their text positions
      // need to be recomputed from the AST node ranges after edits earlier in
      // the document
      item.refreshTextPosition();
      return item;
    }
    if (element instanceof PsiMethodCallExpression) {
      item = findJavaExpressionReference((PsiMethodCallExpression)element);
    } else if (element instanceof XmlAttributeValue) {
      item = findXmlValueReference((XmlAttributeValue)element);
    } else {
      item = NONE;
    }
    element.putUserData(CACHE, item);
    if (item != NONE) {
      PsiElement itemElement = item.getElement();
      if (itemElement != element && itemElement != null) {
        // Store cache item on the sub-node of the expression as well,
        // such that string lookup can find it from there
        itemElement.putUserData(CACHE, item);
      }
    }

    return item;
  }

  @NotNull
  private static ResourceString findXmlValueReference(XmlAttributeValue element) {
    String value = element.getValue();
    if (!(value.startsWith(STRING_PREFIX))) {
      return NONE;
    }
    Module module = ModuleUtilCore.findModuleForPsiElement(element);
    if (module == null) {
      return NONE;
    }
    ProjectResources projectResources = ProjectResources.get(module, true, FORCE_PROJECT_RESOURCE_LOADING);
    if (projectResources == null) {
      return NONE;
    }
    ASTNode node = element.getNode();
    if (node == null) {
      return NONE;
    }
    String name = value.substring(STRING_PREFIX.length());
    TextRange textRange = element.getTextRange();
    HashSet<Object> dependencies = new HashSet<Object>();
    dependencies.add(element);
    FoldingDescriptor descriptor = new FoldingDescriptor(node, textRange, null, dependencies);
    ResourceString resourceString = new ResourceString(name, projectResources, descriptor, element);
    dependencies.add(resourceString);
    return resourceString;
  }

  @NotNull
  private static ResourceString findJavaExpressionReference(PsiMethodCallExpression expression) {
    PsiReferenceExpression methodExpression = expression.getMethodExpression();
    PsiExpression[] expressions = expression.getArgumentList().getExpressions();

    // Only check the first couple of parameters since those are the only occurrences
    // in the SDK
    int parameterCount = Math.min(2, expressions.length);
    PsiParameter[] parameters = null;
    for (int i = 0; i < parameterCount; i++) {
      String name = getStringResourceName(expressions[i]);
      if (name == null) {
        continue;
      }

      if (parameters == null) {
        PsiMethod method = expression.resolveMethod();
        if (!UNIT_TEST_MODE) { // For some reason, we can't resolve PsiMethods from the unit tests
          if (method == null) {
            return NONE;
          }
          PsiParameterList parameterList = method.getParameterList();
          parameters = parameterList.getParameters();
          if (parameters.length == 0) {
            return NONE;
          }
          parameterCount = Math.min(parameters.length, parameterCount);
        }
      }

      if (UNIT_TEST_MODE || allowsResourceType(ResourceType.STRING, parameters[i])
          || i == 0 && "getString".equals(methodExpression.getReferenceName())) {
        Module module = ModuleUtilCore.findModuleForPsiElement(expression);
        if (module == null) {
          return NONE;
        }

        ProjectResources projectResources = ProjectResources.get(module, true, FORCE_PROJECT_RESOURCE_LOADING);
        if (projectResources == null) {
          return NONE;
        }

        // Determine whether we should just replace the parameter expression, or the
        // whole method call. For now, we just replace calls into Resources or calls called getText/getString
        PsiElement foldElement = expressions[0]; // same arg
        String referenceName = expression.getMethodExpression().getReferenceName();
        if ("getString".equals(referenceName) || "getText".equals(referenceName)) {
          foldElement = expression;
        } else {
          PsiElement resolve = methodExpression.resolve();
          if (resolve instanceof PsiMethod) {
            PsiClass containingClass = ((PsiMethod)resolve).getContainingClass();
            if (containingClass != null && "Resources".equals(containingClass.getName())) {
              // Fold the entire element, e.g.
              //    getResources().getString(R.string, ...) => "...."
              // instead of just the parameter containing the string reference:
              //    getResources().getString(R.string, ...) => getResources().getString("...")
              // In other cases we don't want to do this; for example for
              //    button.setText(R.string...) we just want button.setText("...")
              foldElement = expression;
            }
          }
        }

        ASTNode node = foldElement.getNode();
        if (node != null) {
          TextRange textRange = foldElement.getTextRange();
          HashSet<Object> dependencies = new HashSet<Object>();
          dependencies.add(foldElement);
          FoldingDescriptor descriptor = new FoldingDescriptor(node, textRange, null, dependencies);
          ResourceString resourceString = new ResourceString(name, projectResources, descriptor, foldElement);
          dependencies.add(resourceString);
          return resourceString;
        }
      }
    }

    return NONE;
  }

  @Nullable
  private static String getStringResourceName(@NotNull PsiExpression expression) {
    // Check whether the expression corresponds to R.string.<name>
    if (expression instanceof PsiReferenceExpression) {
      PsiReferenceExpression refExp = (PsiReferenceExpression)expression;
      PsiExpression qualifier = refExp.getQualifierExpression();
      if (qualifier instanceof PsiReferenceExpression) {
        PsiReferenceExpression refExp2 = (PsiReferenceExpression)qualifier;
        if (ResourceType.STRING.getName().equals(refExp2.getReferenceName())) {
          PsiExpression qualifier2 = refExp2.getQualifierExpression();
          if (qualifier2 instanceof PsiReferenceExpression) {
            PsiReferenceExpression refExp3 = (PsiReferenceExpression)qualifier2;
            if (R_CLASS.equals(refExp3.getReferenceName()) && refExp3.getQualifierExpression() == null) {
              return refExp.getReferenceName();
            }
          }
        }
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
      String name = pair.getName();
      PsiAnnotationMemberValue value = pair.getValue();
      assert name == null || name.equals("value") : name;
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