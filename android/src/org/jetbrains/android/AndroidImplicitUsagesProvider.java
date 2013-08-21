package org.jetbrains.android;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.psi.*;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidImplicitUsagesProvider implements ImplicitUsageProvider {
  @Override
  public boolean isImplicitUsage(PsiElement element) {
    return false;
  }

  @Override
  public boolean isImplicitRead(PsiElement element) {
    return false;
  }

  @Override
  public boolean isImplicitWrite(PsiElement element) {
    if (!(element instanceof PsiField)) {
      return false;
    }
    final AndroidFacet facet = AndroidFacet.getInstance(element);

    if (facet == null) {
      return false;
    }
    final PsiField field = (PsiField)element;
    final PsiModifierList modifierList = field.getModifierList();

    if (modifierList == null) {
      return false;
    }
    for (PsiAnnotation annotation : modifierList.getAnnotations()) {
      for (PsiNameValuePair pair : annotation.getParameterList().getAttributes()) {
        final PsiAnnotationMemberValue value = pair.getValue();

        if (isResourceReference(value)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isResourceReference(PsiAnnotationMemberValue value) {
    if (!(value instanceof PsiReferenceExpression)) {
      return false;
    }
    PsiReferenceExpression exp = (PsiReferenceExpression)value;
    String refName = exp.getReferenceName();

    if (refName == null || refName.length() == 0) {
      return false;
    }
    PsiExpression qExp = exp.getQualifierExpression();

    if (!(qExp instanceof PsiReferenceExpression)) {
      return false;
    }
    exp = (PsiReferenceExpression)qExp;
    refName = exp.getReferenceName();

    if (refName == null || refName.length() == 0) {
      return false;
    }
    qExp = exp.getQualifierExpression();

    if (!(qExp instanceof PsiReferenceExpression)) {
      return false;
    }
    exp = (PsiReferenceExpression)qExp;
    return AndroidUtils.R_CLASS_NAME.equals(exp.getReferenceName());
  }
}
