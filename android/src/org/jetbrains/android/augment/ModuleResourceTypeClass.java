package org.jetbrains.android.augment;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.resources.ResourceType;
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
 * Implementation of {@link ResourceTypeClassBase} for a local module.
 */
public class ModuleResourceTypeClass extends ResourceTypeClassBase {
  protected final AndroidFacet myFacet;

  public ModuleResourceTypeClass(@NotNull AndroidFacet facet, @NotNull ResourceType resourceType, @NotNull PsiClass context) {
    super(context, resourceType);
    myFacet = facet;
  }

  @NotNull
  static PsiField[] buildLocalResourceFields(@NotNull AndroidFacet facet,
                                             @NotNull ResourceType resourceType,
                                             @NotNull PsiClass context) {
    Module circularDepLibWithSamePackage = AndroidCompileUtil.findCircularDependencyOnLibraryWithSamePackage(facet);
    AndroidLightField.FieldModifier modifier = !facet.getConfiguration().isLibraryProject() && circularDepLibWithSamePackage == null ?
                                               AndroidLightField.FieldModifier.FINAL :
                                               AndroidLightField.FieldModifier.NON_FINAL;

    LocalResourceManager resourceManager = ModuleResourceManagers.getInstance(facet).getLocalResourceManager();
    ResourceRepositoryManager repositoryManager = ResourceRepositoryManager.getOrCreateInstance(facet);
    ResourceNamespace namespace = repositoryManager.getNamespace();
    return buildResourceFields(resourceManager, repositoryManager.getAppResources(true), namespace, modifier,
                               resourceType, context);
  }

  @NotNull
  @Override
  protected PsiField[] doGetFields() {
    return buildLocalResourceFields(myFacet, myResourceType, this);
  }
}
