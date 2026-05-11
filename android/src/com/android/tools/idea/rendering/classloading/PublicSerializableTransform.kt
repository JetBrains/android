/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.android.tools.rendering.classloading.ClassVisitorUniqueIdProvider
import com.android.tools.rendering.classloading.PseudoClassLocator
import java.lang.reflect.Field
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Opcodes.ACC_PRIVATE
import org.jetbrains.org.objectweb.asm.Opcodes.ACC_PROTECTED
import org.jetbrains.org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.jetbrains.org.objectweb.asm.tree.AnnotationNode
import org.jetbrains.org.objectweb.asm.tree.ClassNode

/**
 * A [ClassVisitor] that transforms classes annotated with `@Serializable` that also implement `NavKey` and are not public to make them and
 * their inner classes public.
 *
 * This transformation is necessary when the original class has private or protected visibility (e.g., a private Kotlin `object` or an
 * `internal` class). When Layoutlib repackages `kotlinx.serialization`, the repackaged library uses reflection to access these classes.
 * Because they are in different packages (or because visibility is restricted), it would normally result in an [IllegalAccessException].
 *
 * This transform uses [ClassNode] only when a potential match is found (non-public class implementing `NavKey`), allowing it to inspect
 * annotations before deciding whether to modify the visibility.
 */
private const val SERIALIZABLE_ANNOTATION_NAME = "Lkotlinx/serialization/Serializable;"
private const val REPACKAGED_SERIALIZABLE_ANNOTATION_NAME = "L_layoutlib_/_internal_/kotlinx/serialization/Serializable;"
private const val NAV_KEY_INTERFACE_NAME = "androidx/navigation3/runtime/NavKey"

private const val JAVA_LANG_OBJECT = "java/lang/Object"

class PublicSerializableTransform(private val delegate: ClassVisitor) : ClassVisitor(Opcodes.ASM9), ClassVisitorUniqueIdProvider {

  override val uniqueId: String = PublicSerializableTransform::class.qualifiedName!!

  private var classNode: ClassNode? = null

  /**
   * Tries to find the [PseudoClassLocator] used by the [ClassWriter] at the end of the [ClassVisitor] chain. This is used to perform
   * hierarchy checks without loading classes.
   */
  private val pseudoClassLocator: PseudoClassLocator? by lazy { findPseudoClassLocatorInChain(delegate) }

  override fun visit(version: Int, accessFlags: Int, name: String, signature: String?, superName: String?, interfaces: Array<out String>?) {
    // We only want to change the visibility if the class is not public already and it implements NavKey (directly or via hierarchy).
    val isPrivate = (accessFlags and ACC_PUBLIC) == 0
    // When isPrivate is false, we can avoid the expensive checkImplementsNavKey call, so it fails fast.
    val implementsNavKey = isPrivate && checkImplementsNavKey(superName, interfaces, pseudoClassLocator)

    if (implementsNavKey) {
      // Potential match, buffer into ClassNode to check for @Serializable
      classNode = ClassNode()
      cv = classNode
    } else {
      // Not a NavKey or already public, no need to transform, we point to the next class visitor and skip this one.
      cv = delegate
    }
    super.visit(version, accessFlags, name, signature, superName, interfaces)
  }

  override fun visitEnd() {
    // ClassVisitor checks first the class and the annotation after. After the visit if our candidate node matches
    // our criteria (NavKey and @Serializable) we perform the transformation
    classNode?.let { node ->
      val annotations =
        (node.visibleAnnotations ?: emptyList<AnnotationNode>()) + (node.invisibleAnnotations ?: emptyList<AnnotationNode>())
      val isSerializable = annotations.any { it.desc == SERIALIZABLE_ANNOTATION_NAME || it.desc == REPACKAGED_SERIALIZABLE_ANNOTATION_NAME }

      if (isSerializable) {
        // Transform: make the class and its inner classes public to avoid IllegalAccessException
        node.access = makePublic(node.access)
        node.innerClasses?.forEach { innerClass -> innerClass.access = makePublic(innerClass.access) }
      }
      node.accept(delegate)
    } ?: super.visitEnd()
  }
}

/** Tries to find the [PseudoClassLocator] in the [ClassVisitor] chain by traversing it using reflection. */
private fun findPseudoClassLocatorInChain(startVisitor: ClassVisitor?): PseudoClassLocator? =
  generateSequence(startVisitor) { visitor -> getNextVisitorInChain(visitor) }
    .firstNotNullOfOrNull { visitor -> extractLocatorFromVisitor(visitor) }

/** Attempts to extract a 'classLocator' field from the given [visitor] using reflection. */
private fun extractLocatorFromVisitor(visitor: ClassVisitor): PseudoClassLocator? {
  val field = findField(visitor.javaClass, "classLocator") ?: return null
  if (!PseudoClassLocator::class.java.isAssignableFrom(field.type)) return null
  return runCatching {
      field.isAccessible = true
      field.get(visitor) as? PseudoClassLocator
    }
    .getOrNull()
}

/** Returns the next [ClassVisitor] in the chain by accessing the protected 'cv' field. */
private fun getNextVisitorInChain(visitor: ClassVisitor): ClassVisitor? {
  val cvField = ClassVisitor::class.java.declaredFields.firstOrNull { it.name == "cv" } ?: return null
  return runCatching {
      cvField.isAccessible = true
      cvField.get(visitor) as? ClassVisitor
    }
    .getOrNull()
}

/** Finds a field with the given [fieldName] in the [startClass] or any of its superclasses. */
private fun findField(startClass: Class<*>, fieldName: String): Field? =
  generateSequence(startClass) { currentClass -> currentClass.superclass }
    .firstNotNullOfOrNull { currentClass -> currentClass.declaredFields.firstOrNull { field -> field.name == fieldName } }

/** Checks if the class represented by [superName] and [interfaces] implements the `NavKey` interface using the provided [locator]. */
private fun checkImplementsNavKey(superName: String?, interfaces: Array<out String>?, locator: PseudoClassLocator?): Boolean {
  if (interfaces?.contains(NAV_KEY_INTERFACE_NAME) == true) return true
  val pseudoLocator = locator ?: return false

  val navKeyPseudoClass =
    runCatching { pseudoLocator.locatePseudoClass(NAV_KEY_INTERFACE_NAME.replace('/', '.')) }.getOrNull() ?: return false

  // Check super class hierarchy
  if (superName != null && superName != JAVA_LANG_OBJECT) {
    val isSuperNavKey =
      runCatching {
          val superPseudoClass = pseudoLocator.locatePseudoClass(superName.replace('/', '.'))
          navKeyPseudoClass.isAssignableFrom(superPseudoClass)
        }
        .getOrDefault(false)
    if (isSuperNavKey) return true
  }

  // Check interfaces hierarchy
  return interfaces?.any { interfaceName ->
    runCatching {
        val interfacePseudoClass = pseudoLocator.locatePseudoClass(interfaceName.replace('/', '.'))
        navKeyPseudoClass.isAssignableFrom(interfacePseudoClass)
      }
      .getOrDefault(false)
  } ?: false
}

/** Modifies the given [accessFlags] to ensure the public flag is set and private/protected flags are cleared. */
private fun makePublic(accessFlags: Int): Int {
  // Clear private and protected flags and set public flag.
  return (accessFlags and (ACC_PRIVATE or ACC_PROTECTED).inv()) or ACC_PUBLIC
}
