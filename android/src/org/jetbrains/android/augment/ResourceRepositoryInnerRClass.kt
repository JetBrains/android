package org.jetbrains.android.augment;

import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.android.tools.idea.res.ResourceRepositoryRClass;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiTypes;
import org.jetbrains.android.compiler.AndroidCompileUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

/**
 * Implementation of {@link InnerRClassBase} backed by a resource repository.
 */
public class ResourceRepositoryInnerRClass extends InnerRClassBase {
  @NotNull private final ResourceRepositoryRClass.ResourcesSource mySource;

  public ResourceRepositoryInnerRClass(@NotNull ResourceType resourceType,
                                       @NotNull ResourceRepositoryRClass.ResourcesSource source,
                                       @NotNull PsiClass parentClass) {
    super(parentClass, resourceType);
    mySource = source;
  }

  @NotNull
  static PsiField[] buildLocalResourceFields(@NotNull ResourceType resourceType,
                                             @NotNull ResourceRepositoryRClass.ResourcesSource resourcesSource,
                                             @NotNull PsiClass context) {
    AndroidLightField.FieldModifier modifier = resourcesSource.getFieldModifier();

    return InnerRClassBase.buildResourceFields(resourcesSource.getResourceRepository(),
                                               resourcesSource.getResourceNamespace(),
                                               resourcesSource.getResourceRepositoryManager(),
                                               modifier,
                                               ACCESSIBLE_RESOURCE_FILTER,
                                               resourceType,
                                               context);
  }

  @NotNull
  @Override
  protected PsiField[] doGetFields() {
    return buildLocalResourceFields(myResourceType, mySource, this);
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
    if (myResourceType == ResourceType.STYLEABLE // styleables require further modification of the name to handle sub attributes
        || mySource.getFieldModifier() == AndroidLightField.FieldModifier.FINAL // app projects use final ids, which requires assigning ids to all fields
        || name.contains("_")) { // Resource fields with underscores are flattened resources, for which ResourceRepository.hasResources will not find the correct resource.
      return super.findFieldByName(name, checkBases);
    }

    if (!mySource.getResourceRepository().hasResources(mySource.getResourceNamespace(), myResourceType, name)) {
      return null;
    }

    return new ResourceLightField(name,
                                  this,
                                  PsiTypes.intType(),
                                  AndroidLightField.FieldModifier.NON_FINAL,
                                  null,
                                  ResourceVisibility.PUBLIC);
  }

  @NotNull
  @Override
  protected ModificationTracker getFieldsDependencies() {
    return () -> mySource.getResourceRepository().getModificationCount();
  }
}
