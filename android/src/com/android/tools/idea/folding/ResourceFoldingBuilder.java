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

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.LanguageQualifier;
import com.android.resources.ResourceType;
import com.android.tools.idea.rendering.ProjectResources;
import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.SdkConstants.R_CLASS;
import static com.android.SdkConstants.STRING_PREFIX;

public class ResourceFoldingBuilder extends FoldingBuilderEx {
  private static final boolean FORCE_PROJECT_RESOURCE_LOADING = true;
  private static final int FOLD_MAX_LENGTH = 60;

  public ResourceFoldingBuilder() {
  }

  private static boolean isFoldingEnabled() {
    return AndroidFoldingSettings.getInstance().isCollapseAndroidStrings();
  }

  @Override
  public boolean isCollapsedByDefault(@NotNull ASTNode node) {
    return isFoldingEnabled();
  }

  private static final boolean SKIP_QUICK =  !ApplicationManager.getApplication().isUnitTestMode();

  @Override
  @NotNull
  public FoldingDescriptor[] buildFoldRegions(@NotNull PsiElement element, @NotNull Document document, boolean quick) {
    if (!(element instanceof PsiJavaFile || element instanceof XmlFile) || quick && SKIP_QUICK || !isFoldingEnabled()) {
      return FoldingDescriptor.EMPTY;
    }
    final List<FoldingDescriptor> result = new ArrayList<FoldingDescriptor>();
    if (element instanceof PsiJavaFile) {
      final PsiJavaFile file = (PsiJavaFile) element;
      file.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
          checkMethodCall(expression, result);
          super.visitMethodCallExpression(expression);
        }
      });
    } else {
      final XmlFile file = (XmlFile) element;
      file.accept(new XmlRecursiveElementVisitor() {
        @Override
        public void visitXmlAttributeValue(XmlAttributeValue value) {
          checkAttributeValue(value, result);
        }
      });
    }

    return result.toArray(new FoldingDescriptor[result.size()]);
  }

  private static void checkAttributeValue(XmlAttributeValue value, List<FoldingDescriptor> result) {
    if (!(value.getValue().startsWith(STRING_PREFIX))) {
      return;
    }

    Module module = ModuleUtilCore.findModuleForPsiElement(value);

    //noinspection ConstantConditions
    if (module == null || !FORCE_PROJECT_RESOURCE_LOADING && ProjectResources.get(module, false) == null) {
      return;
    }

    final HashSet<Object> set = new HashSet<Object>();
    set.add(value);
    result.add(new FoldingDescriptor(ObjectUtils.assertNotNull(value.getNode()), value.getTextRange(), null, set));
  }

  private static void checkMethodCall(PsiMethodCallExpression expression, List<FoldingDescriptor> result) {
    PsiReferenceExpression methodExpression = expression.getMethodExpression();
    PsiElement element = methodExpression.getReferenceNameElement();
    if (!(element instanceof PsiIdentifier)) {
      return;
    }
    PsiIdentifier identifier = (PsiIdentifier)element;
    if (!"getString".equals(identifier.getText())) {
      return;
    }
    PsiExpression[] expressions = expression.getArgumentList().getExpressions();
    if (expressions.length == 0 || !isStringResourceRef(expressions[0])) {
      return;
    }
    Module module = ModuleUtilCore.findModuleForPsiElement(expression);

    //noinspection ConstantConditions
    if (module == null || !FORCE_PROJECT_RESOURCE_LOADING && ProjectResources.get(module, false) == null) {
      return;
    }

    final HashSet<Object> set = new HashSet<Object>();
    set.add(expression);
    result.add(new FoldingDescriptor(ObjectUtils.assertNotNull(expression.getNode()), expression.getTextRange(), null, set));
  }

  private static boolean isStringResourceRef(PsiExpression expression) {
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
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  @Override
  public String getPlaceholderText(@NotNull ASTNode node) {
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(node);
    if (element instanceof PsiMethodCallExpression && element.isValid()) {
      PsiMethodCallExpression expression = (PsiMethodCallExpression)element;
      PsiExpressionList argumentList = expression.getArgumentList();
      PsiExpression[] expressions = argumentList.getExpressions();
      if (expressions.length > 0) {
        PsiExpression first = expressions[0];
        if (first.isValid() && isStringResourceRef(first)) {
          String name = ((PsiReferenceExpression) first).getReferenceName();
          if (name != null) {
            String resolvedString = getResolvedString(element, name);
            if (resolvedString != null) {
              return resolvedString;
            }
          }
        }
      }
    } else if (element instanceof XmlAttributeValue) {
      String value = ((XmlAttributeValue)element).getValue();
      if (value.startsWith(STRING_PREFIX)) {
        String name = value.substring(STRING_PREFIX.length());
        String resolvedString = getResolvedString(element, name);
        if (resolvedString != null) {
          return resolvedString;
        }
      }
    }

    return element != null ? element.getText() : node.getText();
  }

  @Nullable
  private static String getResolvedString(@NotNull PsiElement element, @NotNull String name) {
    Module module = ModuleUtilCore.findModuleForPsiElement(element);
    if (module != null) {
      ProjectResources resources = ProjectResources.get(module);
      if (resources != null) {
        if (resources.hasResourceItem(ResourceType.STRING, name)) {
          ResourceItem item = resources.getResourceItem(ResourceType.STRING, name);
          FolderConfiguration referenceConfig = new FolderConfiguration();
          // Nonexistent language qualifier: trick it to fall back to the default locale
          referenceConfig.setLanguageQualifier(new LanguageQualifier("xx"));
          ResourceValue value = item.getResourceValue(ResourceType.STRING, referenceConfig, false);
          if (value != null) {
            String text = value.getValue();
            if (text == null) {
              return null;
            }

            if (element instanceof PsiMethodCallExpression) {
              text = insertArguments((PsiMethodCallExpression) element, text);
            }

            return '"' + StringUtil.shortenTextWithEllipsis(text, FOLD_MAX_LENGTH - 2, 0) + '"';
          }
        }
      }
    }
    return null;
  }

  // See lint's StringFormatDetector
  private static final Pattern FORMAT = Pattern.compile("%(\\d+\\$)?([-+#, 0(<]*)?(\\d+)?(\\.\\d+)?([tT])?([a-zA-Z%])");

  @NotNull
  private static String insertArguments(@NotNull PsiMethodCallExpression methodCallExpression, @NotNull String s) {
    if (s.indexOf('%') == -1) {
      return s;
    }
    final PsiExpression[] args = methodCallExpression.getArgumentList().getExpressions();
    if (args.length == 0 || !args[0].isValid()) {
      return s;
    }

    Matcher matcher = FORMAT.matcher(s);
    int index = 0;
    int prevIndex = 0;
    int nextNumber = 1;
    int start = 0;
    StringBuilder sb = new StringBuilder(2 * s.length());
    while (true) {
      if (matcher.find(index)) {
        if ("%".equals(matcher.group(6))) {
          index = matcher.end();
          continue;
        }
        int matchStart = matcher.start();
        // Make sure this is not an escaped '%'
        for (; prevIndex < matchStart; prevIndex++) {
          char c = s.charAt(prevIndex);
          if (c == '\\') {
            prevIndex++;
          }
        }
        if (prevIndex > matchStart) {
          // We're in an escape, ignore this result
          index = prevIndex;
          continue;
        }

        index = matcher.end();

        // Shouldn't throw a number format exception since we've already
        // matched the pattern in the regexp
        int number;
        String numberString = matcher.group(1);
        if (numberString != null) {
          // Strip off trailing $
          numberString = numberString.substring(0, numberString.length() - 1);
          number = Integer.parseInt(numberString);
          nextNumber = number + 1;
        } else {
          number = nextNumber++;
        }

        if (number > 0 && number < args.length) {
          PsiExpression argExpression = args[number];
          Object value = JavaConstantExpressionEvaluator.computeConstantExpression(argExpression, false);
          if (value == null) {
            value = args[number].getText();
          }
          for (int i = start; i < matchStart; i++) {
            sb.append(s.charAt(i));
          }
          sb.append('{');
          sb.append(value);
          sb.append('}');
          start = index;
        }
      } else {
        for (int i = start, n = s.length(); i < n; i++) {
          sb.append(s.charAt(i));
        }
        break;
      }
    }

    return sb.toString();
  }
}