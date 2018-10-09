package org.jetbrains.android.augment;

import com.android.builder.model.AaptOptions;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.android.utils.Pair;
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
  @NotNull private final AndroidFacet myFacet;
  @NotNull private final AaptOptions.Namespacing myNamespacing;


  public ModuleResourceTypeClass(@NotNull AndroidFacet facet,
                                 @NotNull AaptOptions.Namespacing namespacing,
                                 @NotNull ResourceType resourceType,
                                 @NotNull PsiClass context) {
    super(context, resourceType);
    myFacet = facet;
    myNamespacing = namespacing;
  }

  @NotNull
  static PsiField[] buildLocalResourceFields(@NotNull AndroidFacet facet,
                                             @NotNull ResourceType resourceType,
                                             @NotNull AaptOptions.Namespacing namespacing,
                                             @NotNull PsiClass context) {
    Module circularDepLibWithSamePackage = AndroidCompileUtil.findCircularDependencyOnLibraryWithSamePackage(facet);
    AndroidLightField.FieldModifier modifier = !facet.getConfiguration().isLibraryProject() && circularDepLibWithSamePackage == null ?
                                               AndroidLightField.FieldModifier.FINAL :
                                               AndroidLightField.FieldModifier.NON_FINAL;

    LocalResourceManager resourceManager = ModuleResourceManagers.getInstance(facet).getLocalResourceManager();
    Pair<LocalResourceRepository, ResourceNamespace> resources = pickRepositoryAndNamespace(namespacing, facet);
    return buildResourceFields(resourceManager, resources.getFirst(), resources.getSecond(), modifier, resourceType, context);
  }

  @NotNull
  private static Pair<LocalResourceRepository, ResourceNamespace> pickRepositoryAndNamespace(@NotNull AaptOptions.Namespacing namespacing,
                                                                                             @NotNull AndroidFacet facet) {
    ResourceRepositoryManager repositoryManager = ResourceRepositoryManager.getOrCreateInstance(facet);
    ResourceNamespace namespace;
    LocalResourceRepository repository;
    if (namespacing == AaptOptions.Namespacing.DISABLED) {
      namespace = ResourceNamespace.RES_AUTO;
      repository = repositoryManager.getAppResources(true);
    } else {
      namespace = repositoryManager.getNamespace();
      repository = repositoryManager.getModuleResources(true);
    }
    return Pair.of(repository, namespace);
  }

  @NotNull
  @Override
  protected PsiField[] doGetFields() {
    return buildLocalResourceFields(myFacet, myResourceType, myNamespacing, this);
  }
}
