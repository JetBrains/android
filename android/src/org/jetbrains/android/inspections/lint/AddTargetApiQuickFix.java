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
package org.jetbrains.android.inspections.lint;

import com.android.sdklib.SdkVersionInfo;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

import static com.android.SdkConstants.*;
import static com.android.tools.lint.checks.ApiDetector.REQUIRES_API_ANNOTATION;

/** Fix which adds a {@code @TargetApi} annotation at the nearest surrounding method or class */
public class AddTargetApiQuickFix implements AndroidLintQuickFix {
  private final boolean myRequiresApi;
  private int myApi;
  private PsiElement myElement;

  public AddTargetApiQuickFix(int api, boolean requiresApi, PsiElement element) {
    myApi = api;
    myRequiresApi = requiresApi;
    myElement = element;
  }

  private String getAnnotationValue(boolean fullyQualified) {
    return AddTargetVersionCheckQuickFix.getVersionField(myApi, fullyQualified);
  }

  @NotNull
  @Override
  public String getName() {
    String key = getAnnotationValue(false);

    final PsiFile file = PsiTreeUtil.getParentOfType(myElement, PsiFile.class);
    if (file instanceof XmlFile) {
      // The quickfixes are sorted alphabetically, but for resources we really don't want
      // this quickfix (Add Target API) to appear before Override Resource, which is
      // usually the better solution. So instead of "Add tools:targetApi" we use a label
      // which sorts later alphabetically.
      return "Suppress With tools:targetApi Attribute";
    }

    if (myRequiresApi) {
      return AndroidBundle.message("android.lint.fix.add.requires.api", key);
    } else {
      return AndroidBundle.message("android.lint.fix.add.target.api", key);
    }
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement startElement,
                              @NotNull PsiElement endElement,
                              @NotNull AndroidQuickfixContexts.ContextType contextType) {
    return PsiTreeUtil.getParentOfType(startElement, PsiModifierListOwner.class, false) != null
           || PsiTreeUtil.getParentOfType(startElement, XmlTag.class, false) != null;
  }

  @SuppressWarnings("unchecked")
  @Override
  public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context) {
    // Find nearest method or class; can't add @TargetApi on modifier list owners like variable declarations
    @SuppressWarnings("unchecked")
    PsiModifierListOwner container = PsiTreeUtil.getParentOfType(startElement, PsiMethod.class, PsiClass.class);

    if (container == null) {
      // XML file? Set attribute
      XmlTag element = PsiTreeUtil.getParentOfType(startElement, XmlTag.class, false);
      if (element != null) {
        XmlFile file = PsiTreeUtil.getParentOfType(element, XmlFile.class, false);
        if (file != null) {
          AndroidResourceUtil.ensureNamespaceImported(file, TOOLS_URI, null);
          String codeName = SdkVersionInfo.getBuildCode(myApi);
          if (codeName == null) {
            codeName = Integer.toString(myApi);
          } else {
            codeName = codeName.toLowerCase(Locale.US);
          }
          element.setAttribute(ATTR_TARGET_API, TOOLS_URI, codeName);
        }
      }
      return;
    }

    while (container != null && container instanceof PsiAnonymousClass) {
      container = PsiTreeUtil.getParentOfType(container, PsiMethod.class, true, PsiClass.class);
    }
    if (container == null) {
      return;
    }

    if (!FileModificationService.getInstance().preparePsiElementForWrite(container)) {
      return;
    }
    final PsiModifierList modifierList = container.getModifierList();
    if (modifierList != null) {
      Project project = startElement.getProject();
      PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
      String fqcn = myRequiresApi ? REQUIRES_API_ANNOTATION : FQCN_TARGET_API;
      String annotationText;
      if (myRequiresApi) {
        annotationText = "@" + fqcn + "(api=" + getAnnotationValue(true) + ")";
      } else {
        annotationText = "@" + fqcn + "(" + getAnnotationValue(true) + ")";
      }
      PsiAnnotation newAnnotation = elementFactory.createAnnotationFromText(annotationText, container);
      PsiAnnotation annotation = AnnotationUtil.findAnnotation(container, FQCN_TARGET_API);
      if (annotation != null && annotation.isPhysical()) {
        annotation.replace(newAnnotation);
      } else {
        PsiNameValuePair[] attributes = newAnnotation.getParameterList().getAttributes();
        AddAnnotationFix fix = new AddAnnotationFix(fqcn, container, attributes);
        fix.invoke(project, null /*editor*/, container.getContainingFile());
      }
    }
  }
}
