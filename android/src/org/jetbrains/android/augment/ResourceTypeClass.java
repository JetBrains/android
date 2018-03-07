package org.jetbrains.android.augment;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import org.jetbrains.android.compiler.AndroidCompileUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.LocalResourceManager;
import org.jetbrains.android.resourceManagers.ModuleResourceManagers;
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
    final boolean generateNonFinalFields = facet.getConfiguration().isLibraryProject() || circularDepLibWithSamePackage != null;
    LocalResourceManager resourceManager = ModuleResourceManagers.getInstance(facet).getLocalResourceManager();
    ResourceRepositoryManager repositoryManager = ResourceRepositoryManager.getOrCreateInstance(facet);
    return buildResourceFields(resourceManager, repositoryManager.getAppResources(true), ResourceNamespace.TODO, generateNonFinalFields,
                               resClassName, context);
  }

  @NotNull
  @Override
  protected PsiField[] doGetFields() {
    return buildLocalResourceFields(myFacet, myName, this);
  }
}
