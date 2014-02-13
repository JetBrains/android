package org.jetbrains.android;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.android.dom.converters.OnClickConverter;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidImplicitUsagesProvider implements ImplicitUsageProvider {
  @Override
  public boolean isImplicitUsage(PsiElement element) {
    if (element instanceof PsiField) {
      return isImplicitFieldUsage((PsiField)element);
    }
    else if (element instanceof PsiParameter) {
      return isImplicitParameterUsage((PsiParameter)element);
    }
    return false;
  }

  private static boolean isImplicitParameterUsage(@NotNull PsiParameter parameter) {
    if (AndroidFacet.getInstance(parameter) == null) {
      return false;
    }
    final PsiMethod method = PsiTreeUtil.getParentOfType(parameter, PsiMethod.class);

    if (method == null ||
        !OnClickConverter.CONVERTER_FOR_LAYOUT.checkSignature(method) &&
        !OnClickConverter.CONVERTER_FOR_MENU.checkSignature(method)) {
      return false;
    }
    final PsiClass aClass = PsiTreeUtil.getParentOfType(method, PsiClass.class);

    if (aClass == null) {
      return false;
    }
    final PsiClass activityBaseClass = JavaPsiFacade.getInstance(aClass.getProject()).
      findClass(AndroidUtils.ACTIVITY_BASE_CLASS_NAME, parameter.getResolveScope());

    if (activityBaseClass == null) {
      return false;
    }
    return aClass.isInheritor(activityBaseClass, true);
  }

  private static boolean isImplicitFieldUsage(@NotNull PsiField field) {
    if (!"CREATOR".equals(field.getName())) {
      return false;
    }
    final PsiModifierList modifierList = field.getModifierList();

    if (modifierList == null || !modifierList.hasModifierProperty(PsiModifier.STATIC)) {
      return false;
    }
    final PsiClass aClass = field.getContainingClass();
    return aClass != null && InheritanceUtil.isInheritor(aClass, "android.os.Parcelable");
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
