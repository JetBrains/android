package org.jetbrains.android.augment;

import com.android.resources.ResourceType;
import com.android.tools.idea.res.ResourceRepositoryRClass;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import org.jetbrains.android.compiler.AndroidCompileUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.LocalResourceManager;
import org.jetbrains.android.resourceManagers.ModuleResourceManagers;
import org.jetbrains.annotations.NotNull;

/**
 * Implementation of {@link InnerRClassBase} back by a resource repository.
 */
public class ResourceRepositoryInnerRClass extends InnerRClassBase {

  @NotNull private final AndroidFacet myFacet;
  @NotNull private final ResourceRepositoryRClass.ResourcesSource mySource;

  public ResourceRepositoryInnerRClass(@NotNull AndroidFacet facet,
                                       @NotNull ResourceType resourceType,
                                       @NotNull ResourceRepositoryRClass.ResourcesSource source,
                                       @NotNull PsiClass parentClass) {
    super(parentClass, resourceType);
    myFacet = facet;
    mySource = source;
  }

  @NotNull
  static PsiField[] buildLocalResourceFields(@NotNull AndroidFacet facet,
                                             @NotNull ResourceType resourceType,
                                             @NotNull ResourceRepositoryRClass.ResourcesSource resourcesSource,
                                             @NotNull PsiClass context) {
    Module circularDepLibWithSamePackage = AndroidCompileUtil.findCircularDependencyOnLibraryWithSamePackage(facet);
    AndroidLightField.FieldModifier modifier = !facet.getConfiguration().isLibraryProject() && circularDepLibWithSamePackage == null ?
                                               AndroidLightField.FieldModifier.FINAL :
                                               AndroidLightField.FieldModifier.NON_FINAL;

    LocalResourceManager resourceManager = ModuleResourceManagers.getInstance(facet).getLocalResourceManager();
    return InnerRClassBase.buildResourceFields(resourcesSource.getResourceRepository(),
                                               resourcesSource.getResourceNamespace(),
                                               modifier,
                                               (type, name) -> resourceManager.isResourcePublic(type.getName(), name),
                                               resourceType,
                                               context);
  }

  @NotNull
  @Override
  protected PsiField[] doGetFields() {
    return buildLocalResourceFields(myFacet, myResourceType, mySource, this);
  }
}
