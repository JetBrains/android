package org.jetbrains.android.augment

import com.android.annotations.concurrency.Slow
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.StyleableResourceValue
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.ResourceRepository
import com.android.resources.ResourceType
import com.android.resources.ResourceVisibility
import com.android.tools.idea.res.ResourceUpdateTracer
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiField
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.android.AndroidResolveScopeEnlarger
import org.jetbrains.android.augment.AndroidLightField.FieldModifier.FINAL
import org.jetbrains.android.augment.InnerRClassBase.Companion.buildResourceFields

/** Provides a [ResourceRepository] for the given [ResourceNamespace], if possible. */
private typealias RepositoryProvider = (ResourceNamespace) -> ResourceRepository?

private typealias ResourceFilter = (ResourceItem) -> Boolean

/**
 * Base class for light implementations of inner classes of the R class, e.g. `R.string`.
 *
 * Implementations need to implement [doGetFields], most likely by calling one of the
 * [buildResourceFields] methods.
 */
abstract class InnerRClassBase(context: PsiClass, val resourceType: ResourceType) :
  AndroidLightInnerClassBase(context, resourceType.getName()) {
  private var fieldsCache: CachedValue<Array<PsiField>>? = null
  private val logger = thisLogger()

  override fun getFields(): Array<PsiField> {
    if (fieldsCache == null) {
      fieldsCache =
        CachedValuesManager.getManager(project).createCachedValue {
          val fields = doGetFields()
          logger.info("Recomputed ${fields.size} fields for $this")

          if (fields.isEmpty()) {
            ResourceUpdateTracer.dumpTrace("No fields found for ${this.qualifiedName}")
          }

          // When ResourceRepositoryManager's caches are dropped, new instances of repositories are
          // created and the old ones stop incrementing their modification count. We need to make
          // sure the CachedValue doesn't hold on to any particular repository instance and instead
          // reads the modification count of the "current" instance.
          val modificationTracker = fieldsDependencies
          require(modificationTracker !is ResourceRepository) {
            "Resource repository leaked in a CachedValue."
          }

          CachedValueProvider.Result.create(fields, modificationTracker)
        }
    }
    return fieldsCache!!.value
  }

  @Slow protected abstract fun doGetFields(): Array<PsiField>

  protected abstract val fieldsDependencies: ModificationTracker

  companion object {

    @JvmStatic
    protected fun buildResourceFields(
      repository: ResourceRepository,
      namespace: ResourceNamespace,
      fieldModifier: AndroidLightField.FieldModifier,
      resourceType: ResourceType,
      context: PsiClass,
      studioResourceRepositoryManager: StudioResourceRepositoryManager? = null,
      resourceFilter: ResourceFilter = { true },
    ): Array<PsiField> {
      val otherFields = mutableMapOf<String, ResourceVisibility>()
      val styleableFields = mutableMapOf<String, ResourceVisibility>()
      val styleableAttrFields = mutableListOf<StyleableAttrFieldUrl>()
      repository.getResources(namespace, resourceType).values().forEach {
        val visibility =
          if (resourceFilter(it)) ResourceVisibility.PUBLIC else ResourceVisibility.PRIVATE
        if (it.type == ResourceType.STYLEABLE) {
          styleableFields.merge(it.name, visibility, ResourceVisibility::max)
          styleableAttrFields.addAll(
            findStyleableAttrFields(
              it,
              createRepositoryProvider(studioResourceRepositoryManager, repository),
              resourceFilter,
            )
          )
        } else otherFields.merge(it.name, visibility, ResourceVisibility::max)
      }
      return buildResourceFields(
        otherFields,
        styleableFields,
        styleableAttrFields,
        resourceType,
        context,
        fieldModifier,
      )
    }

    @JvmStatic
    protected fun buildResourceFields(
      otherFields: Map<String, ResourceVisibility>,
      styleableFields: Map<String, ResourceVisibility>,
      styleableAttrFields: Collection<StyleableAttrFieldUrl>,
      resourceType: ResourceType,
      context: PsiClass,
      fieldModifier: AndroidLightField.FieldModifier,
    ): Array<PsiField> {
      val factory = JavaPsiFacade.getElementFactory(context.project)
      val innerRClassFields =
        context.containingFile.viewProvider.virtualFile
          .getUserData(AndroidResolveScopeEnlarger.BACKING_CLASS)
          ?.getResources(resourceType)

      val startId = resourceType.ordinal * 100_000
      val otherLightFields =
        otherFields.toLightFields(
          innerRClassFields,
          factory,
          startId,
          PsiTypes.intType(),
          context,
          fieldModifier,
        )

      val styleableStartId = startId + otherFields.size
      val styleableLightFields =
        styleableFields.toLightFields(
          innerRClassFields,
          factory,
          styleableStartId,
          PsiTypes.intType().createArrayType(),
          context,
          fieldModifier,
        )

      val attrStartId = styleableStartId + styleableFields.size
      val styleableAttrLightFields =
        styleableAttrFields.toLightFields(
          innerRClassFields,
          factory,
          attrStartId,
          context,
          fieldModifier,
        )

      return (otherLightFields + styleableLightFields + styleableAttrLightFields).toTypedArray()
    }

    /**
     * Creates a [RepositoryProvider] that uses the [studioResourceRepositoryManager] if non-null,
     * otherwise falls back to the [fallbackRepository]
     */
    private fun createRepositoryProvider(
      studioResourceRepositoryManager: StudioResourceRepositoryManager?,
      fallbackRepository: ResourceRepository,
    ): RepositoryProvider = { resourceNamespace ->
      if (studioResourceRepositoryManager == null) fallbackRepository
      else studioResourceRepositoryManager.getResourcesForNamespace(resourceNamespace)
    }

    /**
     * Returns a [ResourceReference] for the attribute, if it can be found and passes the
     * [resourceFilter]
     */
    private fun getAttributeResourceReference(
      attrName: String,
      attrNamespace: ResourceNamespace,
      repositoryProvider: RepositoryProvider,
      resourceFilter: ResourceFilter,
    ): ResourceReference? =
      repositoryProvider(attrNamespace)
        ?.getResources(attrNamespace, ResourceType.ATTR, attrName)
        ?.firstOrNull()
        ?.takeIf(resourceFilter::invoke)
        ?.let { ResourceReference(it.namespace, it.type, it.name) }

    /** Returns all `Styleable` attribute fields of [resource] as [StyleableAttrFieldUrl]s. */
    private fun findStyleableAttrFields(
      resource: ResourceItem,
      repositoryProvider: RepositoryProvider,
      resourceFilter: ResourceFilter,
    ): List<StyleableAttrFieldUrl> {
      val attributes =
        (resource.resourceValue as? StyleableResourceValue)?.allAttributes ?: return emptyList()
      val resourceReference =
        ResourceReference(resource.namespace, ResourceType.STYLEABLE, resource.name)
      val attributeResourceReferences =
        attributes.mapNotNull {
          getAttributeResourceReference(it.name, it.namespace, repositoryProvider, resourceFilter)
        }
      // StyleableAttrFieldUrl maps from the resource itself to the attribute of that resource.
      return attributeResourceReferences.map { StyleableAttrFieldUrl(resourceReference, it) }
    }

    /** Converts the resources in [this] to [ResourceLightField]s. */
    private fun Map<String, ResourceVisibility>.toLightFields(
      innerRClassFields: Map<String, Int>?,
      factory: PsiElementFactory,
      nextId: Int,
      psiType: PsiType,
      context: PsiClass,
      fieldModifier: AndroidLightField.FieldModifier,
    ): List<ResourceLightField> =
      entries.mapIndexed { i, (fieldName, visibility) ->
        val fieldId = innerRClassFields?.get(fieldName) ?: (nextId + i)
        ResourceLightField(
            fieldName,
            context,
            psiType,
            fieldModifier,
            fieldId.takeIf { fieldModifier == FINAL },
            visibility,
          )
          .apply { initializer = factory.createExpressionFromText(fieldId.toString(), this) }
      }

    /** Converts the attributes in [this] to [StyleableAttrLightField]s. */
    private fun Collection<StyleableAttrFieldUrl>.toLightFields(
      innerRClass: Map<String, Int>?,
      factory: PsiElementFactory,
      nextId: Int,
      context: PsiClass,
      fieldModifier: AndroidLightField.FieldModifier,
    ): List<StyleableAttrLightField> = mapIndexed { i, fieldContents ->
      val fieldId = innerRClass?.get(fieldContents.toFieldName()) ?: (nextId + i)
      StyleableAttrLightField(
          fieldContents,
          context,
          fieldModifier,
          fieldId.takeIf { fieldModifier == FINAL },
        )
        .apply { initializer = factory.createExpressionFromText(fieldId.toString(), this) }
    }
  }
}
