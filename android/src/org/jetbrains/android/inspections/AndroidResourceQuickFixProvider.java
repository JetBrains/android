package org.jetbrains.android.inspections;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReferenceExpression;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidResourceQuickFixProvider extends UnresolvedReferenceQuickFixProvider<PsiReferenceExpression> {
  @Override
  public void registerFixes(@NotNull PsiReferenceExpression exp, @NotNull QuickFixActionRegistrar registrar) {
    Module contextModule = ModuleUtilCore.findModuleForPsiElement(exp);
    if (contextModule == null) {
      return;
    }

    AndroidFacet facet = AndroidFacet.getInstance(contextModule);
    if (facet == null) {
      return;
    }

    final PsiFile contextFile = exp.getContainingFile();
    if (contextFile == null) {
      return;
    }

    AndroidResourceUtil.MyReferredResourceFieldInfo info = AndroidResourceUtil.getReferredResourceOrManifestField(facet, exp, true);
    if (info == null) {
      final PsiElement parent = exp.getParent();
      if (parent instanceof PsiReferenceExpression) {
        info = AndroidResourceUtil.getReferredResourceOrManifestField(facet, (PsiReferenceExpression)parent, true);
      }
    }
    if (info == null || info.isFromManifest()) {
      return;
    }
    final String resClassName = info.getClassName();
    final String resFieldName = info.getFieldName();
    Module resolvedModule = info.getResolvedModule();
    if (resolvedModule != null && resolvedModule != contextModule) {
      AndroidFacet resolvedFacet = AndroidFacet.getInstance(resolvedModule);
      if (resolvedFacet != null) {
        facet = resolvedFacet;
      }
    }

    ResourceType resourceType = ResourceType.getEnum(resClassName);

    if (AndroidResourceUtil.ALL_VALUE_RESOURCE_TYPES.contains(resourceType)) {
      registrar
        .register(new CreateValueResourceQuickFix(facet, resourceType, resFieldName, contextFile, true));
    }
    ResourceFolderType folderType = AndroidResourceUtil.XML_FILE_RESOURCE_TYPES.get(resourceType);
    if (folderType != null) {
      registrar.register(new CreateFileResourceQuickFix(facet, folderType, resFieldName, contextFile, true));
    }
  }

  @NotNull
  @Override
  public Class<PsiReferenceExpression> getReferenceClass() {
    return PsiReferenceExpression.class;
  }
}
