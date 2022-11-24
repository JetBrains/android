/*
 * Copyright (C) 2019 The Android Open Source Project
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
package org.jetbrains.android.intentions;

import com.google.common.collect.Iterables;
import com.intellij.codeInsight.intention.AbstractIntentionAction;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.PsiEditorUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.GenericAttributeValue;
import java.util.Locale;
import org.jetbrains.android.dom.converters.OnClickConverter;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.asJava.classes.KtLightClass;
import org.jetbrains.kotlin.idea.KotlinLanguage;
import org.jetbrains.kotlin.idea.core.OldGenerateUtilKt;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.kotlin.psi.KtDeclaration;
import org.jetbrains.kotlin.psi.KtNamedFunction;
import org.jetbrains.kotlin.psi.KtPsiFactory;

public class AndroidCreateOnClickHandlerAction extends AbstractIntentionAction implements HighPriorityAction {
  @NotNull
  @Override
  public String getText() {
    return AndroidBundle.message("create.on.click.handler.intention.text");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (editor == null || !(file instanceof XmlFile)) {
      return false;
    }
    final AndroidFacet facet = AndroidFacet.getInstance(file);

    if (facet == null) {
      return false;
    }
    final XmlAttributeValue attrValue = getXmlAttributeValue(file, editor);

    if (attrValue == null) {
      return false;
    }
    final PsiElement parent = attrValue.getParent();

    if (!(parent instanceof XmlAttribute)) {
      return false;
    }
    final GenericAttributeValue domValue = DomManager.getDomManager(project).getDomElement((XmlAttribute)parent);

    if (domValue == null || !(domValue.getConverter() instanceof OnClickConverter)) {
      return false;
    }
    final String methodName = attrValue.getValue();
    return methodName != null && StringUtil.isJavaIdentifier(methodName);
  }

  @Nullable
  private static XmlAttributeValue getXmlAttributeValue(PsiFile file, Editor editor) {
    final int offset = editor.getCaretModel().getOffset();
    final PsiElement element = file.findElementAt(offset);
    return element != null ? PsiTreeUtil.getParentOfType(element, XmlAttributeValue.class) : null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final AndroidFacet facet = AndroidFacet.getInstance(file);
    assert facet != null;

    final XmlAttributeValue attrValue = getXmlAttributeValue(file, editor);
    assert attrValue != null;
    final String methodName = attrValue.getValue();
    assert methodName != null;
    final GenericAttributeValue domValue = DomManager.getDomManager(project).getDomElement((XmlAttribute)attrValue.getParent());
    assert domValue != null;
    final OnClickConverter converter = (OnClickConverter)domValue.getConverter();

    final PsiClass activityBaseClass = JavaPsiFacade.getInstance(project).findClass(
      AndroidUtils.ACTIVITY_BASE_CLASS_NAME, facet.getModule().getModuleWithDependenciesAndLibrariesScope(false));

    if (activityBaseClass == null) {
      return;
    }
    final GlobalSearchScope scope = facet.getModule().getModuleScope(false);
    final PsiClass selectedClass;

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      final Ref<PsiClass> selClassRef = Ref.create();

      ClassInheritorsSearch.search(activityBaseClass, scope, true, true, false).forEach(psiClass -> {
        if (!psiClass.isInterface() && !psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
          selClassRef.set(psiClass);
          return false;
        }
        return true;
      });
      selectedClass = selClassRef.get();
    }
    else {
      final TreeClassChooser chooser = TreeClassChooserFactory.getInstance(project).createInheritanceClassChooser(
        "Choose Activity to Create the Method", scope, activityBaseClass, null, aClass -> !converter.findHandlerMethod(aClass, methodName));
      chooser.showDialog();
      selectedClass = chooser.getSelected();
    }

    if (selectedClass != null) {
      addHandlerMethodAndNavigate(project, selectedClass, methodName, converter.getDefaultMethodParameterType(selectedClass));
    }
  }

  @NotNull
  private static String suggestVarName(@NotNull String type) {
    for (int i = type.length() - 1; i >= 0; i--) {
      final char c = type.charAt(i);

      if (Character.isUpperCase(c)) {
        return type.substring(i).toLowerCase(Locale.US);
      }
    }
    return "o";
  }

  @Nullable
  public static PsiMethod addHandlerMethod(@NotNull Project project,
                                           @NotNull PsiClass psiClass,
                                           @NotNull String methodName,
                                           @NotNull String methodParamType) {
    final PsiFile file = psiClass.getContainingFile();

    if (file == null) {
      return null;
    }
    final String varName = suggestVarName(methodParamType);
    Language language = psiClass.getLanguage();
    if (language.is(JavaLanguage.INSTANCE)) {
      final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
      PsiMethod method = (PsiMethod)psiClass.add(factory.createMethodFromText(
        "public void " + methodName + "(" + methodParamType + " " + varName + ") {}", psiClass));

      PsiMethod method1 = (PsiMethod)CodeStyleManager.getInstance(project).reformat(method);
      method1 = (PsiMethod)JavaCodeStyleManager.getInstance(project).shortenClassReferences(method1);
      return (PsiMethod)method.replace(method1);
    }
    else if (language.is(KotlinLanguage.INSTANCE)) {
      if (!(psiClass instanceof KtLightClass)) {
        return null;
      }
      KtClassOrObject origin = ((KtLightClass)psiClass).getKotlinOrigin();
      if (origin == null) {
        return null;
      }
      KtNamedFunction namedFunction = new KtPsiFactory(origin.getProject())
        .createFunction("fun " + methodName + "(" + varName + ": " + methodParamType + ") {}");
      KtDeclaration anchor = Iterables.getLast(origin.getDeclarations(), null);
      OldGenerateUtilKt.insertMembersAfterAndReformat(null, origin, namedFunction, anchor);
    }
    return null;
  }

  public static void addHandlerMethodAndNavigate(@NotNull final Project project,
                                                 @NotNull final PsiClass psiClass,
                                                 @NotNull final String methodName,
                                                 @NotNull final String methodParamType) {
    if (!AndroidUtils.isIdentifier(methodName)) {
      Messages.showErrorDialog(project, String.format("%1$s is not a valid Java identifier/method name.", methodName), "Invalid Name");
      return;
    }

    ApplicationManager.getApplication().runWriteAction(() -> {
      final PsiMethod method = addHandlerMethod(project, psiClass, methodName, methodParamType);

      if (method == null) {
        return;
      }
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        PsiNavigateUtil.navigate(method);
      }
      final PsiFile javaFile = method.getContainingFile();

      if (javaFile == null) {
        return;
      }
      final Editor javaEditor = PsiEditorUtil.findEditor(method);

      if (javaEditor == null) {
        return;
      }
      final PsiCodeBlock body = method.getBody();

      if (body != null) {
        final PsiJavaToken lBrace = body.getLBrace();

        if (lBrace != null) {
          javaEditor.getCaretModel().moveToOffset(lBrace.getTextRange().getEndOffset());
        }
      }
    });
  }
}
