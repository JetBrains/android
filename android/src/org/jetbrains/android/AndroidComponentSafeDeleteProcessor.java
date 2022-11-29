package org.jetbrains.android;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.safeDelete.JavaSafeDeleteProcessor;
import com.intellij.refactoring.safeDelete.NonCodeUsageSearchInfo;
import com.intellij.refactoring.safeDelete.SafeDeleteProcessorDelegateBase;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.android.dom.AndroidAttributeValue;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class AndroidComponentSafeDeleteProcessor extends SafeDeleteProcessorDelegateBase {

  private final JavaSafeDeleteProcessor myBaseHandler = new JavaSafeDeleteProcessor();

  private JavaSafeDeleteProcessor getBaseHandler() {
    return myBaseHandler;
  }

  @Override
  public boolean handlesElement(PsiElement element) {
    return getBaseHandler().handlesElement(element) &&
           element instanceof PsiClass &&
           AndroidFacet.getInstance(element) != null &&
           AndroidUtils.isAndroidComponent((PsiClass)element);
  }

  @Override
  public NonCodeUsageSearchInfo findUsages(@NotNull PsiElement element, @NotNull PsiElement[] allElementsToDelete, @NotNull List<? super UsageInfo> result) {
    final ArrayList<UsageInfo> usages = new ArrayList<UsageInfo>();
    final NonCodeUsageSearchInfo info = getBaseHandler().findUsages(element, allElementsToDelete, usages);
    if (info == null) {
      return info;
    }

    assert element instanceof PsiClass;
    final PsiClass componentClass = (PsiClass)element;
    final AndroidAttributeValue<PsiClass> declaration = AndroidDomUtil.findComponentDeclarationInManifest(componentClass);
    if (declaration == null) {
      return info;
    }

    final XmlAttributeValue declarationAttributeValue = declaration.getXmlAttributeValue();

    for (UsageInfo usage : usages) {
      if (declarationAttributeValue != usage.getElement()) {
        result.add(usage);
      }
    }
    return info;
  }

  @Override
  public Collection<? extends PsiElement> getElementsToSearch(@NotNull PsiElement element,
                                                              @Nullable Module module,
                                                              @NotNull Collection<? extends PsiElement> allElementsToDelete) {
    return getBaseHandler().getElementsToSearch(element, module, allElementsToDelete);
  }

  @Override
  public Collection<PsiElement> getAdditionalElementsToDelete(@NotNull PsiElement element,
                                                              @NotNull Collection<? extends PsiElement> allElementsToDelete,
                                                              boolean askUser) {
    return getBaseHandler().getAdditionalElementsToDelete(element, allElementsToDelete, askUser);
  }

  @Override
  public Collection<String> findConflicts(@NotNull PsiElement element, @NotNull PsiElement[] allElementsToDelete) {
    return getBaseHandler().findConflicts(element, allElementsToDelete);
  }

  @Override
  public UsageInfo[] preprocessUsages(@NotNull Project project, UsageInfo @NotNull [] usages) {
    return usages;
  }

  @Override
  public void prepareForDeletion(@NotNull PsiElement element) throws IncorrectOperationException {
    assert element instanceof PsiClass;
    final AndroidAttributeValue<PsiClass> declaration = AndroidDomUtil.findComponentDeclarationInManifest((PsiClass)element);
    if (declaration != null) {
      final XmlAttribute declarationAttr = declaration.getXmlAttribute();
      if (declarationAttr != null) {
        final XmlTag declarationTag = declarationAttr.getParent();
        if (declarationTag != null) {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              declarationTag.delete();
            }
          });
        }
      }
    }
    getBaseHandler().prepareForDeletion(element);
  }

  @Override
  public boolean isToSearchInComments(PsiElement element) {
    return getBaseHandler().isToSearchInComments(element);
  }

  @Override
  public void setToSearchInComments(PsiElement element, boolean enabled) {
    getBaseHandler().setToSearchInComments(element, enabled);
  }

  @Override
  public boolean isToSearchForTextOccurrences(PsiElement element) {
    return getBaseHandler().isToSearchForTextOccurrences(element);
  }

  @Override
  public void setToSearchForTextOccurrences(PsiElement element, boolean enabled) {
    getBaseHandler().setToSearchForTextOccurrences(element, enabled);
  }
}
