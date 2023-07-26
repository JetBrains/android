package org.jetbrains.android.augment

import com.android.annotations.concurrency.Slow
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.StyleableResourceValue
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.ResourceItemWithVisibility
import com.android.ide.common.resources.ResourceRepository
import com.android.resources.ResourceType
import com.android.resources.ResourceVisibility
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.google.common.base.Verify
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.android.AndroidResolveScopeEnlarger
import java.util.function.Predicate

/**
 * Base class for light implementations of inner classes of the R class, e.g. `R.string`.
 *
 *
 * Implementations need to implement [.doGetFields], most likely by calling one of the `buildResourceFields` methods.
 */
abstract class InnerRClassBase(context: PsiClass, val resourceType: ResourceType) :
  AndroidLightInnerClassBase(context, resourceType.getName()) {
  private var myFieldsCache: CachedValue<Array<PsiField>>? = null
  override fun getFields(): Array<PsiField> {
    if (myFieldsCache == null) {
      myFieldsCache = CachedValuesManager.getManager(project).createCachedValue {
        if (LOG.isDebugEnabled) {
          LOG.debug("Recomputing fields for $this")
        }
        val dependencies = fieldsDependencies

        // When ResourceRepositoryManager's caches are dropped, new instances of repositories are created and the old ones
        // stop incrementing their modification count. We need to make sure the CachedValue doesn't hold on to any particular repository
        // instance and instead reads the modification count of the "current" instance.
        Verify.verify(dependencies !is ResourceRepository, "Resource repository leaked in a CachedValue.")
        CachedValueProvider.Result.create(doGetFields(), dependencies)
      }
    }
    return myFieldsCache!!.value
  }

  @Slow
  protected abstract fun doGetFields(): Array<PsiField>
  protected abstract val fieldsDependencies: ModificationTracker

  companion object {
    private val LOG = Logger.getInstance(InnerRClassBase::class.java)
    protected val INT_ARRAY: PsiType = PsiTypes.intType().createArrayType()
    protected val ACCESSIBLE_RESOURCE_FILTER = Predicate { resource: ResourceItem ->
      if (resource.namespace != ResourceNamespace.ANDROID && resource.libraryName == null) {
        return@Predicate true // Project resource.
      }
      if (resource is ResourceItemWithVisibility) {
        return@Predicate resource.visibility == ResourceVisibility.PUBLIC
      }
      throw AssertionError(
        "Library resource " + resource.type + '/' + resource.name + " of type " +
          resource.javaClass.simpleName + " doesn't implement ResourceItemWithVisibility"
      )
    }

    @JvmStatic
    protected fun buildResourceFields(
      repository: ResourceRepository,
      namespace: ResourceNamespace,
      studioResourceRepositoryManager: StudioResourceRepositoryManager?,
      fieldModifier: AndroidLightField.FieldModifier,
      resourceFilter: Predicate<ResourceItem?>,
      resourceType: ResourceType,
      context: PsiClass
    ): Array<PsiField?> {
      val otherFields: MutableMap<String, ResourceVisibility> = LinkedHashMap()
      val styleableFields: MutableMap<String, ResourceVisibility> = LinkedHashMap()
      val styleableAttrFields: MutableCollection<StyleableAttrFieldUrl> = ArrayList()
      val map = repository.getResources(namespace, resourceType)
      for (resource in map.values()) {
        val visibility = if (resourceFilter.test(resource)) ResourceVisibility.PUBLIC else ResourceVisibility.PRIVATE
        if (resourceType == ResourceType.STYLEABLE) {
          styleableFields.merge(resource.name, visibility) { v1: ResourceVisibility?, v2: ResourceVisibility? ->
            ResourceVisibility.max(
              v1!!, v2!!
            )
          }
          val value = resource.resourceValue as StyleableResourceValue?
          if (value != null) {
            val attributes = value.allAttributes
            for (attr in attributes) {
              val attrNamespace = attr.namespace
              val attrRepository =
                if (studioResourceRepositoryManager == null) repository else studioResourceRepositoryManager.getResourcesForNamespace(
                  attrNamespace
                )
              if (attrRepository != null) {
                val attrResources = attrRepository.getResources(attrNamespace, ResourceType.ATTR, attr.name)
                if (!attrResources.isEmpty()) {
                  val attrResource = attrResources[0]
                  if (resourceFilter.test(attrResource)) {
                    styleableAttrFields.add(
                      StyleableAttrFieldUrl(
                        ResourceReference(namespace, ResourceType.STYLEABLE, resource.name),
                        ResourceReference(attrNamespace, ResourceType.ATTR, attr.name)
                      )
                    )
                  }
                }
              }
            }
          }
        } else {
          otherFields.merge(resource.name, visibility) { v1: ResourceVisibility?, v2: ResourceVisibility? ->
            ResourceVisibility.max(
              v1!!, v2!!
            )
          }
        }
      }
      return buildResourceFields(otherFields, styleableFields, styleableAttrFields, resourceType, context, fieldModifier)
    }

    /**
     * Returns the inner R class int id for the given fieldName.
     * @param innerRClass the [PsiClass] of the an inner class of the R class containing a specific resource type.
     * @param fieldName the name of the field to look up.
     * @param defaultValue a default value returned if the field can not be found.
     */
    private fun findBackingField(innerRClass: PsiClass?, fieldName: String, defaultValue: Int): Int {
      if (innerRClass == null) {
        return defaultValue
      }
      val backingField = innerRClass.findFieldByName(fieldName, false)
      val backingFieldValue = backingField?.computeConstantValue()
      return if (backingFieldValue is Int) backingFieldValue else defaultValue
    }

    protected fun buildResourceFields(
      otherFields: Map<String, ResourceVisibility>,
      styleableFields: Map<String, ResourceVisibility>,
      styleableAttrFields: Collection<StyleableAttrFieldUrl>,
      resourceType: ResourceType,
      context: PsiClass,
      fieldModifier: AndroidLightField.FieldModifier
    ): Array<PsiField?> {
      val result = arrayOfNulls<PsiField>(otherFields.size + styleableFields.size + styleableAttrFields.size)
      val factory = JavaPsiFacade.getElementFactory(context.project)
      val rClassSmartPointer = context.containingFile.viewProvider.virtualFile.getUserData(AndroidResolveScopeEnlarger.BACKING_CLASS)
      val rClass = rClassSmartPointer?.element
      val innerRClass = rClass?.findInnerClassByName(resourceType.getName(), false)
      var nextId = resourceType.ordinal * 100000
      var i = 0
      for ((key, value) in otherFields) {
        val fieldId = findBackingField(innerRClass, key, nextId++)
        val field = ResourceLightField(
          key,
          context,
          PsiTypes.intType(),
          fieldModifier,
          if (fieldModifier === AndroidLightField.FieldModifier.FINAL) fieldId else null,
          value
        )
        field.initializer = factory.createExpressionFromText(Integer.toString(fieldId), field)
        result[i++] = field
      }
      for ((key, value) in styleableFields) {
        val fieldId = findBackingField(innerRClass, key, nextId++)
        val field = ResourceLightField(
          key,
          context,
          INT_ARRAY,
          fieldModifier,
          if (fieldModifier === AndroidLightField.FieldModifier.FINAL) fieldId else null,
          value
        )
        field.initializer = factory.createExpressionFromText(Integer.toString(fieldId), field)
        result[i++] = field
      }
      for (fieldContents in styleableAttrFields) {
        val fieldId = findBackingField(innerRClass, fieldContents.toFieldName(), nextId++)
        val field: AndroidLightField = StyleableAttrLightField(
          fieldContents,
          context,
          fieldModifier,
          if (fieldModifier === AndroidLightField.FieldModifier.FINAL) fieldId else null
        )
        field.initializer = factory.createExpressionFromText(Integer.toString(fieldId), field)
        result[i++] = field
      }
      return result
    }
  }
}
