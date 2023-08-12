/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.rendering.classloading

import com.google.common.base.MoreObjects
import org.jetbrains.android.uipreview.PseudoClassLocatorForLoader
import org.jetbrains.annotations.TestOnly
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.Opcodes

private const val JAVA_OBJECT_FQN = "java.lang.Object"

/**
 * Interface to implement by classes able to locate [PseudoClass] from a class FQN.
 * An implementation of this class should read all the [PseudoClass] information without loading the class
 * in memory via the class loader.
 */
interface PseudoClassLocator {
  fun locatePseudoClass(classFqn: String): PseudoClass
}

/**
 * [PseudoClassLocator] without any resolution. It returns [PseudoClass.objectPseudoClass] for every request.
 * Mainly used for testing or for when there is no [PseudoClass]es available.
 */
object NopClassLocator : PseudoClassLocator {
  override fun locatePseudoClass(classFqn: String): PseudoClass = PseudoClass.objectPseudoClass()
}

/**
 * An object that represents a class file without using the class loader. This contains the minimum information needed to
 * be able to parse class files hierarchies without loading the class in memory.
 * @param name the class name
 * @param superName the super class name
 * @param isInterface true if this represents an interface
 * @param interfaces list of interface names implemented by this class or empty if none
 * @param classLocator the [PseudoClassLocator] to resolve additional [PseudoClass]es if needed
 */
class PseudoClass private constructor(val name: String,
                                      val superName: String,
                                      val isInterface: Boolean,
                                      val interfaces: List<String>,
                                      private val classLocator: PseudoClassLocator) {
  private fun locateClass(fqn: String): PseudoClass =
    if (fqn == JAVA_OBJECT_FQN)
      objectPseudoClass()
    else
      classLocator.locatePseudoClass(fqn)

  private fun superClass(): PseudoClass = locateClass(superName)
  private fun allSuperClasses(): Sequence<PseudoClass> {
    if (superClass() == this) return sequenceOf()

    return sequenceOf(superClass()) + superClass().allSuperClasses()
  }

  /**
   * Returns all the interfaces implemented by this type.
   */
  fun interfaces(): Sequence<PseudoClass> =
    (sequenceOf(this) + allSuperClasses())
      .flatMap { it.interfaces.asSequence() }
      .map { locateClass(it) }
      .flatMap { sequenceOf(it) + it.interfaces() }
      .distinct()

  /**
   * Returns whether this type is a subclass of [pseudoClass].
   */
  private fun isSubclassOf(pseudoClass: PseudoClass): Boolean {
    if (this == pseudoClass) return true
    // Object is not a subclass of anything
    if (this == objectPseudoClass) return false
    // All objects are subclasses of Object
    if (pseudoClass == objectPseudoClass) return true

    val superClass = superClass()
    return superClass == pseudoClass || superClass.isSubclassOf(pseudoClass)
  }

  /**
   * Returns whether this type implements the [pseudoInterface].
   */
  fun implementsInterface(pseudoInterface: PseudoClass): Boolean {
    if (!pseudoInterface.isInterface) return false

    return interfaces().any { it == pseudoInterface }
  }

  /**
   * Returns whether this type is assignable from [pseudoClass].
   */
  fun isAssignableFrom(pseudoClass: PseudoClass): Boolean = when {
    this == pseudoClass -> true
    pseudoClass.isSubclassOf(this) -> true
    pseudoClass.implementsInterface(this) -> true
    name == JAVA_OBJECT_FQN -> true
    else -> false
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is PseudoClass) return false

    if (name != other.name) return false

    return true
  }

  override fun hashCode(): Int {
    return name.hashCode()
  }

  /**
   * Returns a new [PseudoClass] with the same contents but the new given name. This allows renaming [PseudoClass]es.
   */
  fun withNewName(newName: String) =
    if (newName != name)
      PseudoClass(newName, superName, isInterface, interfaces, classLocator)
    else
      this

  override fun toString(): String = MoreObjects.toStringHelper(PseudoClass::class.java)
    .add("name", name)
    .add("superName", superName)
    .add("isInterface", isInterface)
    .add("interfaces", interfaces)
    .toString()

  companion object {
    @TestOnly
    fun forTest(name: String, superName: String, isInterface: Boolean, interfaces: List<String>, locator: PseudoClassLocator) =
      PseudoClass(name, superName, isInterface, interfaces, locator)

    /**
     * Returns a [PseudoClass] from the given class file [ByteArray] using the [classLocator] to resolve any
     * additional classes.
     */
    fun fromByteArray(classBytes: ByteArray?, classLocator: PseudoClassLocator): PseudoClass {
      if (classBytes == null) return objectPseudoClass

      val reader = ClassReader(classBytes)
      return PseudoClass(reader.className.fromBinaryNameToPackageName(),
                         reader.superName?.fromBinaryNameToPackageName() ?: JAVA_OBJECT_FQN,
                         (reader.access and Opcodes.ACC_INTERFACE) > 0,
                         reader.interfaces.map { it.fromBinaryNameToPackageName() }.toList(), classLocator)
    }

    fun fromClass(loadClass: Class<*>, classLocator: PseudoClassLocatorForLoader) =
      PseudoClass(loadClass.canonicalName,
                  loadClass.superclass?.canonicalName ?: objectPseudoClass.name,
                  loadClass.isInterface,
                  loadClass.interfaces.map { it.canonicalName }.toList(), classLocator)


    /**
     * Returns the [PseudoClass] for the [Object] class.
     */
    fun objectPseudoClass(): PseudoClass = objectPseudoClass

    /**
     * Returns the closes common super class for the given [PseudoClass]es.
     */
    fun getCommonSuperClass(class1: PseudoClass, class2: PseudoClass): PseudoClass {
      if (class1.isAssignableFrom(class2)) {
        return class1
      }
      else if (class2.isAssignableFrom(class1)) {
        return class2
      }
      else if (!class1.isInterface && !class2.isInterface) {
        var superClass = class1.superClass()
        while (!superClass.isAssignableFrom(class2)) {
          superClass = superClass.superClass()
        }

        return superClass
      }

      return objectPseudoClass
    }

    private val objectPseudoClass = PseudoClass(JAVA_OBJECT_FQN, JAVA_OBJECT_FQN, false, listOf(), NopClassLocator)
  }
}

class ClassWriterWithPseudoClassLocator(flags: Int,
                                        private val classLocator: PseudoClassLocator) : ClassWriter(flags) {
  override fun getCommonSuperClass(type1: String, type2: String): String {
    // Avoid class loading in cases where it's not necessary
    if (OBJECT_TYPE == type1 || OBJECT_TYPE == type2) {
      return OBJECT_TYPE
    }

    return PseudoClass.getCommonSuperClass(
      classLocator.locatePseudoClass(type1.fromBinaryNameToPackageName()),
      classLocator.locatePseudoClass(type2.fromBinaryNameToPackageName())
    ).name.replace(".", "/")
  }

  companion object {
    private const val OBJECT_TYPE = "java/lang/Object"
  }
}