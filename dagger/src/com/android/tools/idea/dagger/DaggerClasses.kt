/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.dagger

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/** An immutable representation of a Dagger class name. */
data class DaggerClass(
  /** The [ClassId] of the class. */
  val classId: ClassId
) {
  /**
   * The name of the class, as an [FqName].
   *
   * Prefer [classId], since it more precisely distinguishes between packages and nested classes.
   */
  val fqName: FqName by lazy(LazyThreadSafetyMode.PUBLICATION) { classId.asSingleFqName() }

  /**
   * The name of the class, as a fully-qualified dot-separated string.
   *
   * Prefer [classId], since it more precisely distinguishes between packages and nested classes.
   */
  val fqNameString: String by lazy(LazyThreadSafetyMode.PUBLICATION) { classId.asFqNameString() }

  companion object {
    fun fromString(classIdString: String): DaggerClass =
      DaggerClass(ClassId.fromString(classIdString))
  }
}

object DaggerClasses {
  private val DAGGER_PACKAGE_FQNAME = FqName("dagger")
  private val JAVAX_INJECT_PACKAGE_FQNAME = FqName("javax.inject")
  private val DAGGER_ASSISTED_PACKAGE_FQNAME = FqName("dagger.assisted")

  private fun dagger(className: String): DaggerClass =
    DaggerClass(ClassId(DAGGER_PACKAGE_FQNAME, Name.identifier(className)))

  private fun daggerAssisted(className: String): DaggerClass =
    DaggerClass(ClassId(DAGGER_ASSISTED_PACKAGE_FQNAME, Name.identifier(className)))

  private fun javaxInject(className: String): DaggerClass =
    DaggerClass(ClassId(JAVAX_INJECT_PACKAGE_FQNAME, Name.identifier(className)))

  private fun DaggerClass.nested(nestedClassName: String): DaggerClass =
    DaggerClass(classId.createNestedClassId(Name.identifier(nestedClassName)))

  val Module = dagger("Module")
  val Provides = dagger("Provides")
  val Binds = dagger("Binds")
  val Lazy = dagger("Lazy")
  val BindsInstance = dagger("BindsInstance")
  val BindsOptionalOf = dagger("BindsOptionalOf")
  val Component = dagger("Component")
  val ComponentBuilder = Component.nested("Builder")
  val ComponentFactory = Component.nested("Factory")
  val Subcomponent = dagger("Subcomponent")
  val SubcomponentFactory = Subcomponent.nested("Factory")

  val Inject = javaxInject("Inject")
  val Provider = javaxInject("Provider")
  val Qualifier = javaxInject("Qualifier")

  val Assisted = daggerAssisted("Assisted")
  val AssistedFactory = daggerAssisted("AssistedFactory")
  val AssistedInject = daggerAssisted("AssistedInject")

  val EntryPoint = DaggerClass.fromString("dagger/hilt/EntryPoint")
  val ViewModelInject = DaggerClass.fromString("androidx/hilt/lifecycle/ViewModelInject")
  val WorkerInject = DaggerClass.fromString("androidx/hilt/work/WorkerInject")
}
