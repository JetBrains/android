package org.jetbrains.android.augment;

import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import org.jetbrains.android.compiler.AndroidCompileUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

/**
* @author Eugene.Kudelevsky
*/
public class ResourceTypeClass extends ResourceTypeClassBase {
  protected final AndroidFacet myFacet;

  public ResourceTypeClass(@NotNull AndroidFacet facet, @NotNull String name, @NotNull PsiClass context) {
    super(context, name);
    myFacet = facet;
  }

  @NotNull
  static PsiField[] buildLocalResourceFields(@NotNull AndroidFacet facet,
                                             @NotNull String resClassName,
                                             @NotNull final PsiClass context) {
    final Module circularDepLibWithSamePackage = AndroidCompileUtil.findCircularDependencyOnLibraryWithSamePackage(facet);
    final boolean generateNonFinalFields = facet.isLibraryProject() || circularDepLibWithSamePackage != null;
    return buildResourceFields(facet.getLocalResourceManager(), generateNonFinalFields, resClassName, context);
  }

  @NotNull
  @Override
  protected PsiField[] doGetFields() {
    return buildLocalResourceFields(myFacet, myName, this);
  }
}
