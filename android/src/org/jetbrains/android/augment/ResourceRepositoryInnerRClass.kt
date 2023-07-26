package org.jetbrains.android.augment

import com.android.resources.ResourceType
import com.android.resources.ResourceVisibility
import com.android.tools.idea.res.ResourceRepositoryRClass.ResourcesSource
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiTypes

/**
 * Implementation of [InnerRClassBase] backed by a resource repository.
 */
class ResourceRepositoryInnerRClass(
  resourceType: ResourceType,
  private val mySource: ResourcesSource,
  parentClass: PsiClass
) : InnerRClassBase(parentClass, resourceType) {
  override fun doGetFields(): Array<PsiField> {
    return buildLocalResourceFields(myResourceType, mySource, this)
  }

  /**
   * {@inheritDoc}
   *
   * This implementation adds a fast path for non final resources and delegates to the super implementation in
   * [AndroidLightClassBase.findFieldByName] for everything else. The super implementation
   * relies on first creating the PsiFields for *all* resources and then searching for the requested field.
   * In addition, the logic for determining whether a field should be final or not relies on a very expensive
   * Manifest class creation [AndroidCompileUtil.findCircularDependencyOnLibraryWithSamePackage].
   * Both of those operations are avoided for the common case in this implementation.
   */
  override fun findFieldByName(name: String, checkBases: Boolean): PsiField? {
    // bail if this is a scenario we don't fully support
    if ( // app projects use final ids, which requires assigning ids to all fields
      myResourceType == ResourceType.STYLEABLE || mySource.fieldModifier === AndroidLightField.FieldModifier.FINAL || name.contains("_")) { // Resource fields with underscores are flattened resources, for which ResourceRepository.hasResources will not find the correct resource.
      return super.findFieldByName(name, checkBases)
    }
    return if (!mySource.resourceRepository.hasResources(mySource.resourceNamespace, myResourceType, name)) {
      null
    } else ResourceLightField(
      name,
      this,
      PsiTypes.intType(),
      AndroidLightField.FieldModifier.NON_FINAL,
      null,
      ResourceVisibility.PUBLIC
    )
  }

  override fun getFieldsDependencies(): ModificationTracker {
    return ModificationTracker { mySource.resourceRepository.modificationCount }
  }

  companion object {
    fun buildLocalResourceFields(
      resourceType: ResourceType,
      resourcesSource: ResourcesSource,
      context: PsiClass
    ): Array<PsiField> {
      val modifier = resourcesSource.fieldModifier
      return buildResourceFields(
        resourcesSource.resourceRepository,
        resourcesSource.resourceNamespace,
        resourcesSource.resourceRepositoryManager,
        modifier,
        ACCESSIBLE_RESOURCE_FILTER,
        resourceType,
        context
      )
    }
  }
}
