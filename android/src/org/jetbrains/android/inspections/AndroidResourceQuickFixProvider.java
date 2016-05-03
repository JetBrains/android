package org.jetbrains.android.inspections;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReferenceExpression;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidResourceQuickFixProvider extends UnresolvedReferenceQuickFixProvider<PsiReferenceExpression> {
  @Override
  public void registerFixes(@NotNull PsiReferenceExpression exp, @NotNull QuickFixActionRegistrar registrar) {
    final Module contextModule = ModuleUtil.findModuleForPsiElement(exp);
    if (contextModule == null) {
      return;
    }

    final AndroidFacet facet = AndroidFacet.getInstance(contextModule);
    if (facet == null) {
      return;
    }

    final Manifest manifest = facet.getManifest();
    if (manifest == null) {
      return;
    }

    final String aPackage = manifest.getPackage().getValue();
    if (aPackage == null) {
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
