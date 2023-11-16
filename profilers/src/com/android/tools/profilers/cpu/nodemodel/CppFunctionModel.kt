/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.cpu.nodemodel

/**
 * Represents characteristics of C/C++ functions.
 */
class CppFunctionModel private constructor(builder: Builder) : NativeNodeModel() {
  /**
   * Function's full class name (e.g. art::interpreter::SomeClass). For functions that don't belong to a particular class (e.g.
   * art::bla::Method), this field stores the full namespace (e.g. art::bla).
   */
  val classOrNamespace = builder.classOrNamespace

  /**
   * List of the method's parameters (e.g. ["int", "float"]).
   */
  val parameters = builder.parameters.split(", ").filter { it.isNotEmpty() }

  /**
   * Whether the function is part of user-written code.
   */
  val isUserCode = builder.isUserCode

  /**
   * Name of the ELF file containing the instruction corresponding to the function.
   */
  val fileName = builder.fileName

  /**
   * Virtual address of the instruction in [.myFileName].
   */
  val vAddress = builder.vAddress

  // The separator is only needed when we have a class name or namespace, otherwise we're going to end up with a leading separator. We
  // don't have a class name or a namespace, for instance, for global functions.
  private val fullName = listOf(builder.classOrNamespace, builder.name).filter { it.isNotEmpty() }.joinToString("::")

  // Use lazy so we don't need to worry about the initialize state of fullName and parameters, and we don't create the string every time
  // getId() is called. Use LazyThreadSafetyMode.NONE because we don't expect any threading issues.
  private val id = lazy(LazyThreadSafetyMode.NONE) { "${fullName}${parameters}" }

  private val tag = builder.tag

  init {
    myName = builder.name
  }

  override fun getTag(): String? { return tag }

  override fun getFullName(): String { return fullName }

  override fun getId(): String { return id.value }

  // TODO: Remove the set*() methods once all uses have been converted to Kotlin.
  class Builder(val name: String) {
    // All fields in the build need to have @JvmField on them to stop Kotlin from creating get/set methods for them. If it was to create
    // set methods, they would conflict with our chaining set methods.

    @JvmField
    var classOrNamespace = ""

    @JvmField
    var isUserCode = false

    /**
     * A comma separated lust if method parameters (e.g. "int, float").
     */
    @JvmField
    var parameters = ""

    @JvmField
    var fileName: String? = null

    @JvmField
    var vAddress: Long = 0

    @JvmField
    var tag: String? = null

    fun setClassOrNamespace(value: String): Builder {
      classOrNamespace = value
      return this
    }

    fun setIsUserCode(value: Boolean): Builder {
      isUserCode = value
      return this
    }

    fun setParameters(value: String): Builder {
      parameters = value
      return this
    }

    fun setFileName(value: String?): Builder {
      fileName = value
      return this
    }

    fun setVAddress(value: Long): Builder {
      vAddress = value
      return this
    }

    fun setTag(value: String?): Builder {
      tag = value
      return this
    }

    fun build(): CppFunctionModel {
      return CppFunctionModel(this)
    }
  }
}
