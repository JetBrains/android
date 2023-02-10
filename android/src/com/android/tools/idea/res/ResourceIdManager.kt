/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.res

import com.android.annotations.concurrency.GuardedBy
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.resources.ResourceType.ANIM
import com.android.resources.ResourceType.ARRAY
import com.android.resources.ResourceType.ATTR
import com.android.resources.ResourceType.BOOL
import com.android.resources.ResourceType.COLOR
import com.android.resources.ResourceType.DIMEN
import com.android.resources.ResourceType.DRAWABLE
import com.android.resources.ResourceType.ID
import com.android.resources.ResourceType.INTEGER
import com.android.resources.ResourceType.LAYOUT
import com.android.resources.ResourceType.PLURALS
import com.android.resources.ResourceType.STRING
import com.android.resources.ResourceType.STYLE
import com.android.resources.ResourceType.STYLEABLE
import com.android.tools.idea.layoutlib.LayoutLibraryLoader
import com.android.tools.idea.model.Namespacing
import com.intellij.openapi.module.Module
import gnu.trove.TIntObjectHashMap
import gnu.trove.TObjectIntHashMap
import org.jetbrains.android.facet.AndroidFacet
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.Arrays
import java.util.EnumMap

private const val FIRST_PACKAGE_ID: Byte = 0x02

/**
 * Module service responsible for tracking the numeric resource ids we assign to resources, in an attempt to emulate aapt.
 */
interface ResourceIdManager : ResourceClassGenerator.NumericIdProvider {
  /**
   * Whether R classes with final ids are used for compiling custom views.
   */
  val finalIdsUsed: Boolean

  fun getCompiledId(resource: ResourceReference): Int?

  fun findById(id: Int): ResourceReference?

  fun loadCompiledIds(klass: Class<*>)

  /**
   * Resets the currently loaded compiled ids. Call this method before start loading new compiled ids via [loadCompiledIds].
   */
  fun resetCompiledIds()

  fun resetDynamicIds()

  companion object {
    @JvmStatic
    fun get(module: Module) = module.getService(ResourceIdManager::class.java)!!
  }
}

class TestResourceIdManager private constructor(module: Module) : ResourceIdManagerImpl(module) {
  private var _finalIdsUsed = true
  override val finalIdsUsed: Boolean
    get() = _finalIdsUsed

  fun setFinalIdsUsed(finalIdsUsed: Boolean) {
    _finalIdsUsed = finalIdsUsed
  }

  fun resetFinalIdsUsed() {
    _finalIdsUsed = true
  }

  companion object {
    fun getManager(module: Module) = module.getService(ResourceIdManager::class.java) as TestResourceIdManager
  }
}

open class ResourceIdManagerImpl protected constructor(val module: Module) : ResourceIdManager {

  private val facet = AndroidFacet.getInstance(module) ?: error("${ResourceIdManager::class.qualifiedName} used on a non-Android module.")
  private var generationCounter = 1L

  /**
   * Class for generating dynamic ids with the given byte as the "package id" part of the 32-bit resource id.
   *
   * The generated ids follow the aapt PPTTEEEE format: 1 byte for package, 1 byte for type, 2 bytes for entry id. The entry IDs are
   * assigned sequentially, starting with the highest possible value and going down. This should mean they won't conflict with
   * [compiledIds] assigned by real aapt in a normal-size project (although there is no mechanism to check that).
   */
  private class IdProvider(private val packageByte: Byte) {
    private val counters: ShortArray = ShortArray(ResourceType.values().size) { 0xffff.toShort() }

    fun getNext(type: ResourceType): Int {
      return buildResourceId(packageByte, (type.ordinal + 1).toByte(), --counters[type.ordinal])
    }

    override fun toString(): String {
      return Arrays.toString(counters)
    }
  }

  @GuardedBy("this")
  private var nextPackageId: Byte = FIRST_PACKAGE_ID

  @GuardedBy("this")
  private val perNamespaceProviders = hashMapOf<ResourceNamespace, IdProvider>()

  @Synchronized
  private fun resetProviders() {
    nextPackageId = FIRST_PACKAGE_ID
    perNamespaceProviders.clear()
    perNamespaceProviders[ResourceNamespace.RES_AUTO] = IdProvider(0x7f)
    perNamespaceProviders[ResourceNamespace.ANDROID] = IdProvider(0x01)
  }

  init {
    resetProviders()
  }

  /**
   * Ids assigned by this class, on-the-fly. May not be the same as ids chosen by aapt.
   *
   * [compiledIds] take precedence over these, if known.
   */
  @GuardedBy("this")
  private val dynamicToIdMap = TObjectIntHashMap<ResourceReference>()

  /** Inverse of [dynamicToIdMap]. */
  @GuardedBy("this")
  private val dynamicFromIdMap = TIntObjectHashMap<ResourceReference>()

  /**
   * Ids read from the real `R.class` file saved to disk by aapt. They are used instead of dynamic ids, to make sure numeric values compiled
   * into custom views bytecode are consistent with the resource-to-id mapping that this class maintains.
   *
   * These are only read when we know the custom views are compiled against an R class with fields marked as final. See [finalIdsUsed].
   */
  @GuardedBy("this")
  private var compiledIds: SingleNamespaceIdMapping? = null

  @GuardedBy("this")
  private var frameworkIds: SingleNamespaceIdMapping = loadFrameworkIds()

  override val finalIdsUsed: Boolean
    get() {
      return facet.configuration.isAppOrFeature
             && ResourceRepositoryManager.getInstance(facet).namespacing == Namespacing.DISABLED
    }

  @Synchronized
  override fun findById(id: Int): ResourceReference? = compiledIds?.findById(id) ?: dynamicFromIdMap[id]

  /**
   * Returns the compiled id of the given resource, if known.
   *
   * See [compiledIds] for an explanation of what this means for project resources. For framework resources, this will return the value
   * read from [com.android.internal.R].
   */
  @Synchronized
  override fun getCompiledId(resource: ResourceReference): Int? {
    val knownIds = when (resource.namespace) {
      ResourceNamespace.ANDROID -> frameworkIds
      ResourceNamespace.RES_AUTO -> compiledIds
      else -> null
    }

    return knownIds?.getId(resource)?.let { id -> if (id == 0) null else id }
  }

  /**
   * Returns the compiled id if known, otherwise returns the dynamic id of the resource (which may need to be generated).
   *
   * See [getCompiledId] and [dynamicToIdMap] for an explanation of what this means.
   */
  @Synchronized
  override fun getOrGenerateId(resource: ResourceReference): Int {
    val compiledId = getCompiledId(resource)
    if (compiledId != null) {
      return compiledId
    }

    val dynamicId = dynamicToIdMap[resource]
    if (dynamicId != 0) {
      return dynamicId
    }

    val provider = perNamespaceProviders.getOrPut(resource.namespace) { IdProvider(nextPackageId++) }
    val newId = provider.getNext(resource.resourceType)

    dynamicToIdMap.put(resource, newId)
    dynamicFromIdMap.put(newId, resource)

    return newId
  }

  @Synchronized
  override fun resetDynamicIds() {
    generationCounter++
    resetProviders()
    dynamicToIdMap.clear()
    dynamicFromIdMap.clear()
  }

  @Synchronized
  override fun getGeneration(): Long = generationCounter

  @Synchronized
  override fun loadCompiledIds(klass: Class<*>) {
    if (compiledIds == null) {
      compiledIds = SingleNamespaceIdMapping(ResourceNamespace.RES_AUTO)
    }
    loadIdsFromResourceClass(klass, into = compiledIds!!)
  }

  private fun loadFrameworkIds(): SingleNamespaceIdMapping {
    val frameworkIds = SingleNamespaceIdMapping(ResourceNamespace.ANDROID).apply {
      // These are the counts around the S time frame, to allocate roughly the right amount of space upfront.
      toIdMap[ANIM] = TObjectIntHashMap(75)
      toIdMap[ATTR] = TObjectIntHashMap(1752)
      toIdMap[ARRAY] = TObjectIntHashMap(181)
      toIdMap[BOOL] = TObjectIntHashMap(382)
      toIdMap[COLOR] = TObjectIntHashMap(151)
      toIdMap[DIMEN] = TObjectIntHashMap(310)
      toIdMap[DRAWABLE] = TObjectIntHashMap(519)
      toIdMap[ID] = TObjectIntHashMap(526)
      toIdMap[INTEGER] = TObjectIntHashMap(226)
      toIdMap[LAYOUT] = TObjectIntHashMap(221)
      toIdMap[PLURALS] = TObjectIntHashMap(33)
      toIdMap[STRING] = TObjectIntHashMap(1585)
      toIdMap[STYLE] = TObjectIntHashMap(794)
    }

    val rClass = LayoutLibraryLoader.LayoutLibraryProvider.EP_NAME.computeSafeIfAny { provider -> provider.frameworkRClass }
    if (rClass != null) {
      loadIdsFromResourceClass(rClass, into = frameworkIds, lookForAttrsInStyleables = true)
    }

    return frameworkIds
  }

  /**
   * Reads numeric ids from the given R class (using reflection) and stores them in the supplied [SingleNamespaceIdMapping].
   *
   * @param klass the R class to read ids from
   * @param into the result [SingleNamespaceIdMapping]
   * @param lookForAttrsInStyleables whether to get attr ids by looking at `R.styleable`. Aapt has a feature where an ignore list of all
   *                                 resources to be put in the R class can be supplied at build time (to reduce the size of the R class).
   *                                 In this case the numeric ids of attr resources can still "leak" into bytecode in the `styleable` class.
   *                                 If this argument is set to `true`, names of the attrs are inferred from corresponding fields in the
   *                                 `styleable` class and their numeric ids are saved. This is applicable mostly to the internal android
   *                                 R class.
   */
  private fun loadIdsFromResourceClass(
    klass: Class<*>,
    into: SingleNamespaceIdMapping,
    lookForAttrsInStyleables: Boolean = false) {
    assert(klass.simpleName == "R") { "Numeric ids can only be loaded from top-level R classes." }

    // Comparator for fields, which makes them appear in the same order as in the R class source code. This means that in R.styleable,
    // indices come after corresponding array and before other arrays, e.g. "ActionBar_logo" comes after "ActionBar" but before
    // "ActionBar_LayoutParams". This allows the invariant that int fields are indices into the last seen array field.
    val fieldOrdering: Comparator<Field> = Comparator { f1, f2 ->
      val name1 = f1.name
      val name2 = f2.name

      for(i in 0 until minOf(name1.length, name2.length)) {
        val c1 = name1[i]
        val c2 = name2[i]

        if (c1 != c2) {
          return@Comparator when {
            c1 == '_' -> -1
            c2 == '_' -> 1
            c1.isLowerCase() && c2.isUpperCase() -> -1
            c1.isUpperCase() && c2.isLowerCase() -> 1
            else -> c1 - c2
          }
        }
      }

      name1.length - name2.length
    }

    for (innerClass in klass.declaredClasses) {
      val type = ResourceType.fromClassName(innerClass.simpleName) ?: continue
      when {
        type != STYLEABLE -> {
          val toIdMap = into.toIdMap.getOrPut(type, ::TObjectIntHashMap)
          val fromIdMap = into.fromIdMap

          for (field in innerClass.declaredFields) {
            if (field.type != Int::class.java || !Modifier.isStatic(field.modifiers)) continue
            val id = field.getInt(null)
            val name = field.name
            toIdMap.put(name, id)
            fromIdMap.put(id, Pair(type, name))
          }
        }
        type == STYLEABLE && lookForAttrsInStyleables -> {
          val toIdMap = into.toIdMap.getOrPut(ATTR, ::TObjectIntHashMap)
          val fromIdMap = into.fromIdMap

          // We process fields by name, so that arrays come before indices into them. currentArray is initialized to a dummy value.
          var currentArray = IntArray(0)
          var currentStyleable = ""

          val sortedFields = innerClass.fields.sortedArrayWith(fieldOrdering)
          for (field in sortedFields) {
            if (field.type.isArray) {
              currentArray = field.get(null) as IntArray
              currentStyleable = field.name
            }
            else {
              val attrName: String = field.name.substring(currentStyleable.length + 1)
              val attrId = currentArray[field.getInt(null)]
              toIdMap.put(attrName, attrId)
              fromIdMap.put(attrId, Pair(ATTR, attrName))
            }
          }
        }
        else -> {
          // No interesting information in the styleable class, if we're not trying to infer attr ids from it.
        }
      }
    }
  }

  override fun resetCompiledIds() {
    compiledIds = null
  }

  /**
   * Keeps a bidirectional mapping between type+name and a numeric id, for a known namespace.
   */
  private class SingleNamespaceIdMapping(val namespace: ResourceNamespace) {
    var toIdMap = EnumMap<ResourceType, TObjectIntHashMap<String>>(ResourceType::class.java)
    var fromIdMap = TIntObjectHashMap<Pair<ResourceType, String>>()

    /**
     * Returns the id of the given resource or 0 if not known.
     */
    fun getId(resourceReference: ResourceReference): Int = toIdMap[resourceReference.resourceType]?.get(resourceReference.name) ?: 0

    /**
     * Returns the [ResourceReference] for the given id, if known.
     */
    fun findById(id: Int): ResourceReference? = fromIdMap[id]?.let { (type, name) -> ResourceReference(namespace, type, name) }
  }
}
