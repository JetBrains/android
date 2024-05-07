package org.jetbrains.android.augment

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.ResourceItemWithVisibility
import com.android.resources.ResourceType
import com.android.resources.ResourceVisibility
import com.android.tools.idea.res.ModuleResourceRepository
import com.android.tools.idea.res.ResourceRepositoryRClass.ResourcesSource
import com.android.tools.idea.res.ResourceUpdateTracer
import com.android.tools.res.MultiResourceRepository
import com.android.utils.TraceUtils.simpleId
import com.google.common.base.MoreObjects
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiTypes

/** Implementation of [InnerRClassBase] backed by a `ResourceRepository`. */
class ResourceRepositoryInnerRClass(
  resourceType: ResourceType,
  private val resourcesSource: ResourcesSource,
  parentClass: PsiClass
) : InnerRClassBase(parentClass, resourceType) {
  override fun doGetFields(): Array<PsiField> {
    val fields = buildLocalResourceFields(resourceType, resourcesSource, this)

    if (fields.isEmpty()) {
      // The super class will dump resource traces, but here let's log some detail about the repository.
      val repository = resourcesSource.resourceRepository
      ResourceUpdateTracer.log {
        buildString {
          append("${this@ResourceRepositoryInnerRClass.simpleId} using repository ${repository.simpleId})")
          if (repository is MultiResourceRepository<*>) {
            appendLine(" with children:")
            for (child in repository.children) {
              appendLine("\t${child.simpleId}")
            }
          }
        }
      }
    }

    return fields
  }

  /**
   * This implementation adds a fast path for non-final resources and delegates to the super implementation in
   * [AndroidLightClassBase.findFieldByName] for everything else. The `super` implementation
   * relies on first creating the [PsiField]s for *all* resources and then searching for the requested field.
   * In addition, the logic for determining whether a field should be final or not relies on a very expensive
   * Manifest class creation `AndroidCompileUtil.findCircularDependencyOnLibraryWithSamePackage`.
   * Both of those operations are avoided for the common case in this implementation.
   */
  override fun findFieldByName(name: String, checkBases: Boolean): PsiField? {
    // Bail if this is a scenario we don't fully support.
    if (scenarioUnsupported(name)) return super.findFieldByName(name, checkBases)

    if (!resourcesSource.resourceRepository.hasResources(resourcesSource.resourceNamespace, resourceType, name)) return null

    return ResourceLightField(
      name,
      myContext = this,
      PsiTypes.intType(),
      AndroidLightField.FieldModifier.NON_FINAL,
      myConstantValue = null,
      ResourceVisibility.PUBLIC
    )
  }

  override val fieldsDependencies = ModificationTracker { resourcesSource.resourceRepository.modificationCount }

  /**
   * Returns whether this scenario is unsupported. This can be due to one of three reasons:
   * * The [ResourceType] is [ResourceType.STYLEABLE] - styleables require further modification of the name to handle sub attributes.
   * * The ID is `final` - App projects use final IDs, which requires assigning IDs to all fields.
   * * The [name] contains an underscore - resource fields with underscores are flattened resources, for which
   *   `ResourceRepository.hasResources` will not find the correct resource.
   */
  private fun scenarioUnsupported(name: String) =
    resourceType == ResourceType.STYLEABLE
    || resourcesSource.fieldModifier == AndroidLightField.FieldModifier.FINAL
    || name.contains("_")

  override fun toString(): String {
    return MoreObjects.toStringHelper(this)
      .add("fqn", qualifiedName)
      .add("repository", resourcesSource.resourceRepository.simpleId)
      .toString()
  }

  companion object {
    fun buildLocalResourceFields(resourceType: ResourceType, resourcesSource: ResourcesSource, context: PsiClass): Array<PsiField> =
      with(resourcesSource) {
        buildResourceFields(
          resourceRepository,
          resourceNamespace,
          fieldModifier,
          resourceType,
          context,
          resourceRepositoryManager,
          resourceFilter = ::isResourceAccessible)
      }

    /** Returns whether the [resource] is visible (as opposed to private). */
    private fun isResourceAccessible(resource: ResourceItem) = when {
      resource.namespace != ResourceNamespace.ANDROID && resource.libraryName == null -> true
      resource is ResourceItemWithVisibility -> resource.visibility == ResourceVisibility.PUBLIC
      else -> throw AssertionError(
        "Library resource ${resource.type}/${resource.name} of type ${resource.javaClass.simpleName} doesn't implement " +
        "ResourceItemWithVisibility"
      )
    }
  }
}
