package org.jetbrains.android.augment;

import com.android.ide.common.resources.ResourceItem;
import com.android.resources.ResourceType;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.res.ResourceRepositoryRClass;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiType;
import java.util.List;
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

  /**
   * {@inheritDoc}
   *
   * This implementation adds a fast path for non final resources and delegates to the super implementation in
   * {@link AndroidLightClassBase#findFieldByName(String, boolean)} for everything else. The super implementation
   * relies on first creating the PsiFields for *all* resources and then searching for the requested field.
   * In addition, the logic for determining whether a field should be final or not relies on a very expensive
   * Manifest class creation {@link AndroidCompileUtil#findCircularDependencyOnLibraryWithSamePackage(AndroidFacet)}.
   * Both of those operations are avoided for the common case in this implementation.
   */
  @Override
  public PsiField findFieldByName(String name, boolean checkBases) {
    // bail if this is a scenario we don't fully support
    if (!StudioFlags.IN_MEMORY_R_CLASSES.get()  // only works with full in memory R classes, not for example with "augmented" classes
        || myResourceType == ResourceType.STYLEABLE // styleables require further modification of the name to handle sub attributes
        || !myFacet.getConfiguration().isLibraryProject()) { // app projects use final ids, which requires assigning ids to all fields
      return super.findFieldByName(name, checkBases);
    }

    if (!mySource.getResourceRepository().hasResources(mySource.getResourceNamespace(), myResourceType, name)) {
      return null;
    }

    return new AndroidLightField(name,
                                 this,
                                 PsiType.INT,
                                 AndroidLightField.FieldModifier.NON_FINAL,
                                 null);
  }
}
