/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.internal

import com.google.common.collect.Sets
import com.jetbrains.rd.util.getOrCreate
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.safeCast

/**
 * A context of lambdas providing custom handling of specific model classes/properties. See: [SpecializedDumper] interface and factory
 * functions.
 */
interface ModelDumperContext {
  val propertyName: String
  fun prop(propertyName: String, v: Any?, defaultValue: Any? = null)
  fun head(name: String)
  fun nest(code: ModelDumperContext.() -> Unit)
}

/**
 * A marker interface to hint dumpers that the collection needs to be sorted.
 */
interface UnorderedCollection<T>: Collection<T>

/**
 * Returns an instance of [UnorderedCollection] containing the same elements.
 */
fun <T> Collection<T>.asUnordered(): UnorderedCollection<T> = object: UnorderedCollection<T>, Collection<T> by this@asUnordered {}

/**
 * A provider of custom handling of properties/values. See [SpecializedDumper] factory functions for possible ways to instantiate it.
 */
interface SpecializedDumper {
  /**
   * If [holder], [propertyName] and [value] are recognised as a target property/value of this provider, writes [value] to [projectDumper]
   * and returns `true`. [modelDumper] may be used to delegate processing of a transformed value to other providers.
   *
   * For a property of an object, this method is first invoked with a non-null [holder] giving a possibility to intercept handling of
   * properties of a specific instance/class and then is invoked (possibly multiple times) with a null [holder] to format values regardless
   * of their holder.
   */
  fun maybeDump(projectDumper: ProjectDumper, modelDumper: ModelDumper, holder: Any?, propertyName: String, value: Any?): Boolean
}

/**
 * Creates a [SpecializedDumper] for value of type [T].
 *
 * Example:
 * ```
 * SpecializedDumper<SpecialValueType> {
 *   prop(
 *     propertyName,
 *     format(it)
 *   )
 * },
 * ```
 *
 */
inline fun <reified T : Any> SpecializedDumper(noinline dumper: ModelDumperContext.(T) -> Unit): SpecializedDumper =
  SpecializedDumperImpl.create(T::class, dumper)

/**
 * Creates a [SpecializedDumper] for values of [property].
 *
 * Example:
 * ```
 * SpecializedDumper(property = MyType::myProperty) {
 *   prop(
 *     propertyName,
 *     format(it)
 *   )
 * },
 * ```
 *
 */
inline fun <reified T : Any, reified V : Any> SpecializedDumper(
  property: KProperty1<T, V>,
  noinline dumper: ModelDumperContext.(V) -> Unit
): SpecializedDumper =
  SpecializedDumperImpl.create(T::class, V::class, property, dumper)

/**
 * Creates a [SpecializedDumper] for values of [property].
 *
 * Example:
 * ```
 * SpecializedDumper(property = MyType::myProperty) {
 *   prop(
 *     propertyName,
 *     format(it)
 *   )
 * },
 * ```
 *
 */
@JvmName("SpecializedDumper_nullable")
inline fun <reified T : Any, reified V : Any> SpecializedDumper(
  property: KProperty1<T, V?>,
  noinline dumper: ModelDumperContext.(V) -> Unit
): SpecializedDumper =
  SpecializedDumperImpl.create(T::class, V::class, property, dumper)

/**
 * A reflection based model dumper customizable through plugins provided in [specializedDumpers].
 */
class ModelDumper(private val specializedDumpers: List<SpecializedDumper>) {
  private val seen = Sets.newIdentityHashSet<Any>()
  private val modelClassDumperDescriptors = mutableMapOf<KClass<*>, ModelClassDumperDescriptor>()

  private fun Any?.ignoreIfDefault() =
    this == null ||
      this == false ||
      this == "" ||
      this is Array<*> && isEmpty() ||
      this is Collection<*> && isEmpty() ||
      this is Map<*, *> && isEmpty()

  private fun Any?.replaceDefault() =
    when {
      this == null -> "<null>"
      this is Array<*> && isEmpty() || this is Collection<*> && isEmpty() || this is Map<*, *> && isEmpty() -> "<empty>"
      else -> this
    }

  private val fileSuffixes = listOf("dir", "file", "path", "dirs", "files", "paths")
  private val notFileSuffixes = listOf("projectPath")

  fun dumpModel(projectDumper: ProjectDumper, propertyName: String, v: Any?, defaultValue: Any? = null, mapEntry: Boolean = false) {
    with(projectDumper) {
      doDump(propertyName, v, defaultValue, mapEntry)
    }
  }

  private fun ProjectDumper.doDump(
    propertyName: String,
    v: Any?,
    defaultValue: Any? = null,
    mapEntry: Boolean = false
  ) {
    fun ModelClassDumperDescriptor.getDisplayNamePropertyValue(v: Any): String? {
      val displayNameProperty = displayNameProperty
      return displayNameProperty?.getter?.invoke(v)?.toString()?.replaceKnownPaths()
    }

    fun Any.getSortPropertyValue(): String {
      if (this::class.memberProperties.isNotEmpty()) {
        val classDumperDescriptor = getClassDumperDescriptorFor(this)
        return classDumperDescriptor.getDisplayNamePropertyValue(this) ?: toString()
      }
      return toString()
    }

    fun processObject(v: Any) {
      val seen = !seen.add(v)
      val classDumperDescriptor = getClassDumperDescriptorFor(v)

      val name = classDumperDescriptor.getDisplayNamePropertyValue(v)
      val displayName = name?.let { "$it (${v.printableClassName})" } ?: v.printableClassName

      head(propertyName) { displayName + (if (seen) " (*seen*)" else "") }
      if (!seen) {
        nest {
          outProperties(v, defaultValue, classDumperDescriptor)
        }
      }
    }

    fun processPathValue(v: String) {
      prop(propertyName) { v.toPrintablePath() }
    }

    fun processFile(v: File) {
      processPathValue(v.path)
    }

    fun processString(v: String) {
      prop(propertyName) { v.replaceKnownPaths() }
    }

    fun processOrderedCollection(v: Collection<*>) {
      v.forEach { doDump("- " + propertyName, it) }
    }

    fun processUnorderedCollection(v: Collection<*>) {
      doDump(propertyName, v.sortedBy { it?.getSortPropertyValue() ?: "" }.toSet())
    }

    fun processOrderedMap(v: Map<*, *>) {
      head(propertyName)
      nest {
        v.entries.forEach { (k, v) -> doDump(k.toString(), v, mapEntry = true) }
      }
    }

    fun processUnorderedMap(v: HashMap<*, *>) {
      doDump(propertyName, v.entries.sortedBy { it.key.getSortPropertyValue() }.associate { it.key to it.value })
    }

    fun processArray(v: Array<*>) {
      v.forEach { doDump("- " + propertyName, it) }
    }

    fun processMapEntryValue(v: Any?) {
      doDump(
        propertyName,
        v.replaceDefault(),
        mapEntry = mapEntry
      )
    }

    fun processClasspathValue(v: String) {
      doDump(propertyName, v.split(':'), defaultValue)
    }

    fun processSourceAndTargetCompatibility(v: String) {
      prop(propertyName) { v.replaceSourceAndTargetCompatibility() }
    }

    fun processNullValue() {
      prop(propertyName) { "<null>" }
    }

    fun tryProcessingBySpecializedDumpers(): Boolean {
      for (dumper in specializedDumpers) {
        if (dumper.maybeDump(this@doDump, this@ModelDumper, null, propertyName, v)) return true
      }
      return false
    }

    fun isIgnorableDefaultValue(any: Any?) = any.ignoreIfDefault() && (any == defaultValue || defaultValue.ignoreIfDefault())

    fun isPathLikePropertyName() =
      fileSuffixes.any { suffix -> propertyName.endsWith(suffix, ignoreCase = true) } &&
        !notFileSuffixes.any { suffix -> propertyName.endsWith(suffix, ignoreCase = true) }

    when {
      propertyName == "classpath" && v is String ->
        processClasspathValue(v)
      (propertyName == "sourceCompatibility" || propertyName == "targetCompatibility") && v is String ->
        processSourceAndTargetCompatibility(v)
      !mapEntry && isIgnorableDefaultValue(v) ->
        Unit
      mapEntry && isIgnorableDefaultValue(v) ->
        processMapEntryValue(v)
      v == null ->
        processNullValue()
      tryProcessingBySpecializedDumpers() ->
        Unit
      v is Array<*> ->
        processArray(v)
      v is HashMap<*, *> && v !is LinkedHashMap<*, *> ->
        processUnorderedMap(v)
      v is Map<*, *> ->
        processOrderedMap(v)
      v is HashSet<*> && v !is LinkedHashSet<*> ->
        processUnorderedCollection(v)
      v is UnorderedCollection<*> ->
        processUnorderedCollection(v)
      v is Collection<*> ->
        processOrderedCollection(v)
      !mapEntry && v is String && isPathLikePropertyName() ->
        processPathValue(v)
      v is String ->
        processString(v)
      v is Enum<*> ->
        processString(v.toString())
      v is File ->
        processFile(v)
      v::class.memberProperties.isNotEmpty() ->
        processObject(v)
      else ->
        processString(v.toString())
    }
  }

  private val Any.printableClassName
    get() = if (this::class.simpleName.orEmpty().startsWith("\$Proxy")) "<PROXY>" else this::class.simpleName ?: "?"

  private fun getClassDumperDescriptorFor(values: Any) = modelClassDumperDescriptors.getOrCreate(values::class) {
    @Suppress("UNCHECKED_CAST") (ModelClassDumperDescriptor(values::class as KClass<Any>)) // This is safe because we only use it to invoke methods on the same instance.)
  }

  private fun ProjectDumper.outProperties(v: Any, defaultValue: Any?, classDumperDescriptor: ModelClassDumperDescriptor) {
    if (defaultValue != null && v::class != defaultValue::class)
      error("Incompatible this and defaults classes: ${v::class} and ${defaultValue::class}")
    for (property in classDumperDescriptor.namedProperties) {
      val value = property.getter(v)
      if (!specializedDumpers.any { it.maybeDump(this, this@ModelDumper, v, property.name, value) }) {
        doDump(property.name, property.getter(v), defaultValue?.let { property.getter(defaultValue) })
      }
    }
  }
}

class SpecializedDumperImpl private constructor(
  private val maybeDump_: (projectDumper: ProjectDumper, modelDumper: ModelDumper, holder: Any?, propertyName: String, value: Any?) -> Boolean
) : SpecializedDumper {
  override fun maybeDump(projectDumper: ProjectDumper, modelDumper: ModelDumper, holder: Any?, propertyName: String, value: Any?): Boolean =
    maybeDump_(projectDumper, modelDumper, holder, propertyName, value)

  companion object {
    fun <T : Any> create(kClass: KClass<T>, dumper: ModelDumperContext.(T) -> Unit): SpecializedDumper {
      fun dumperImpl(projectDumper: ProjectDumper, modelDumper: ModelDumper, holder: Any?, propertyName: String, value: Any?): Boolean {
        val v: T = kClass.safeCast(value) ?: return false
        dumper(ContextImpl(propertyName, modelDumper, projectDumper), v)
        return true
      }
      return SpecializedDumperImpl(::dumperImpl)
    }

    fun <T : Any, V : Any> create(
      hClass: KClass<T>,
      kClass: KClass<V>,
      property: KProperty1<T, V?>,
      dumper: ModelDumperContext.(V) -> Unit
    ): SpecializedDumper {
      val expectedPropertyName = ModelClassDumperDescriptor.maybeMapJavaGetterToKotlinProperty(property) ?: property.name
      fun dumperImpl(projectDumper: ProjectDumper, modelDumper: ModelDumper, holder: Any?, propertyName: String, value: Any?): Boolean {
        val h: T = hClass.safeCast(holder) ?: return false
        val v: V = kClass.safeCast(value) ?: return false
        if (propertyName != expectedPropertyName) return false
        dumper(ContextImpl(propertyName, modelDumper, projectDumper), v)
        return true
      }
      return SpecializedDumperImpl(::dumperImpl)
    }
  }
}

private class ContextImpl(
  override val propertyName: String,
  private val modelDumper: ModelDumper,
  private val projectDumper: ProjectDumper
) : ModelDumperContext {
  override fun prop(propertyName: String, v: Any?, defaultValue: Any?) =
    modelDumper.dumpModel(projectDumper, propertyName, v, defaultValue)

  override fun head(name: String) = projectDumper.head(name)
  override fun nest(code: ModelDumperContext.() -> Unit) = projectDumper.nest { code() }
}
