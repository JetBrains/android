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

import com.android.tools.rendering.classloading.ClassWriterWithPseudoClassLocator
import com.android.tools.rendering.classloading.PseudoClass
import com.android.tools.rendering.classloading.PseudoClassLocator
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.commons.ClassRemapper
import org.jetbrains.org.objectweb.asm.commons.SimpleRemapper
import org.jetbrains.org.objectweb.asm.tree.ClassNode
import org.junit.Assert.assertTrue
import org.junit.Test

@Retention(AnnotationRetention.BINARY) annotation class MockSerializable

interface MockNavKey

@MockSerializable private object SerializableNavKeyObject : MockNavKey

@MockSerializable private object SerializableNotNavKeyObject

private object NonSerializableObject

open class SerializableParentNavKey : MockNavKey

@MockSerializable private class SerializableChildNavKey : SerializableParentNavKey()

@MockSerializable private open class SerializablePrivateParentNavKey : MockNavKey

private const val SERIALIZABLE_INTERNAL_NAME = "_layoutlib_/_internal_/kotlinx/serialization/Serializable"
private const val NAV_KEY_INTERNAL_NAME = "androidx/navigation3/runtime/NavKey"
private const val JAVA_OBJECT_FQN = "java.lang.Object"
private const val GRAND_PARENT_INTERNAL_NAME = "com/example/GrandParent"

private fun loadClassBytes(c: Class<*>): ByteArray {
  val className = "${Type.getInternalName(c)}.class"
  return c.classLoader.getResourceAsStream(className)!!.use { it.readBytes() }
}

class PublicSerializableTransformTest {

  @Test
  fun testSerializableNavKeyObjectTransformation() {
    val testClassBytes = loadClassBytes(SerializableNavKeyObject::class.java)
    val classReader = ClassReader(testClassBytes)
    val classWriter = ClassWriter(0)

    // Remap MockSerializable and MockNavKey to the internal names expected by the transform
    val remapper =
      SimpleRemapper(
        mapOf(
          Type.getInternalName(MockSerializable::class.java) to SERIALIZABLE_INTERNAL_NAME,
          Type.getInternalName(MockNavKey::class.java) to NAV_KEY_INTERNAL_NAME,
        )
      )

    val transform = PublicSerializableTransform(classWriter)
    val adapter = ClassRemapper(transform, remapper)

    classReader.accept(adapter, 0)

    val resultBytes = classWriter.toByteArray()
    val resultReader = ClassReader(resultBytes)
    val resultNode = ClassNode()
    resultReader.accept(resultNode, 0)

    // Check visibility: it should be public now
    assertTrue("Class should be public", (resultNode.access and Opcodes.ACC_PUBLIC) != 0)
    assertTrue("Class should NOT be private", (resultNode.access and Opcodes.ACC_PRIVATE) == 0)
    assertTrue("Class should NOT be protected", (resultNode.access and Opcodes.ACC_PROTECTED) == 0)
  }

  @Test
  fun testInheritedNavKeyTransformation() {
    val testClassBytes = loadClassBytes(SerializableChildNavKey::class.java)
    val classReader = ClassReader(testClassBytes)

    val parentInternalName = Type.getInternalName(SerializableParentNavKey::class.java)
    val childInternalName = Type.getInternalName(SerializableChildNavKey::class.java)

    val locator =
      object : PseudoClassLocator {
        override fun locatePseudoClass(classFqn: String): PseudoClass {
          return when (classFqn) {
            NAV_KEY_INTERNAL_NAME.replace('/', '.') -> PseudoClass.forTest(classFqn, JAVA_OBJECT_FQN, true, emptyList(), this)
            parentInternalName.replace('/', '.') ->
              PseudoClass.forTest(classFqn, JAVA_OBJECT_FQN, false, listOf(NAV_KEY_INTERNAL_NAME.replace('/', '.')), this)
            childInternalName.replace('/', '.') ->
              PseudoClass.forTest(classFqn, parentInternalName.replace('/', '.'), false, emptyList(), this)
            else -> PseudoClass.objectPseudoClass()
          }
        }
      }

    val classWriter = ClassWriterWithPseudoClassLocator(0, locator)

    // Remap MockSerializable and MockNavKey to the internal names expected by the transform
    val remapper =
      SimpleRemapper(
        mapOf(
          Type.getInternalName(MockSerializable::class.java) to SERIALIZABLE_INTERNAL_NAME,
          Type.getInternalName(MockNavKey::class.java) to NAV_KEY_INTERNAL_NAME,
        )
      )

    val transform = PublicSerializableTransform(classWriter)
    val adapter = ClassRemapper(transform, remapper)

    classReader.accept(adapter, 0)

    val resultBytes = classWriter.toByteArray()
    val resultReader = ClassReader(resultBytes)
    val resultNode = ClassNode()
    resultReader.accept(resultNode, 0)

    // Check visibility: it should be public now
    assertTrue("Class should be public via hierarchy", (resultNode.access and Opcodes.ACC_PUBLIC) != 0)
    assertTrue("Class should NOT be private", (resultNode.access and Opcodes.ACC_PRIVATE) == 0)
  }

  @Test
  fun testDeepInheritanceNavKeyTransformation() {
    val testClassBytes = loadClassBytes(SerializableChildNavKey::class.java)
    val classReader = ClassReader(testClassBytes)

    val parentInternalName = Type.getInternalName(SerializableParentNavKey::class.java)
    val childInternalName = Type.getInternalName(SerializableChildNavKey::class.java)

    val locator =
      object : PseudoClassLocator {
        override fun locatePseudoClass(classFqn: String): PseudoClass {
          return when (classFqn) {
            NAV_KEY_INTERNAL_NAME.replace('/', '.') -> PseudoClass.forTest(classFqn, JAVA_OBJECT_FQN, true, emptyList(), this)
            GRAND_PARENT_INTERNAL_NAME.replace('/', '.') ->
              PseudoClass.forTest(classFqn, JAVA_OBJECT_FQN, false, listOf(NAV_KEY_INTERNAL_NAME.replace('/', '.')), this)
            parentInternalName.replace('/', '.') ->
              PseudoClass.forTest(classFqn, GRAND_PARENT_INTERNAL_NAME.replace('/', '.'), false, emptyList(), this)
            childInternalName.replace('/', '.') ->
              PseudoClass.forTest(classFqn, parentInternalName.replace('/', '.'), false, emptyList(), this)
            else -> PseudoClass.objectPseudoClass()
          }
        }
      }

    val classWriter = ClassWriterWithPseudoClassLocator(0, locator)

    val remapper =
      SimpleRemapper(
        mapOf(
          Type.getInternalName(MockSerializable::class.java) to SERIALIZABLE_INTERNAL_NAME,
          Type.getInternalName(MockNavKey::class.java) to NAV_KEY_INTERNAL_NAME,
        )
      )

    val transform = PublicSerializableTransform(classWriter)
    val adapter = ClassRemapper(transform, remapper)

    classReader.accept(adapter, 0)

    val resultBytes = classWriter.toByteArray()
    val resultReader = ClassReader(resultBytes)
    val resultNode = ClassNode()
    resultReader.accept(resultNode, 0)

    // Check visibility: it should be public now via deep hierarchy
    assertTrue("Class should be public via deep hierarchy", (resultNode.access and Opcodes.ACC_PUBLIC) != 0)
  }

  @Test
  fun testPrivateParentTransformation() {
    val testClassBytes = loadClassBytes(SerializablePrivateParentNavKey::class.java)
    val classReader = ClassReader(testClassBytes)
    val classWriter = ClassWriter(0)

    val remapper =
      SimpleRemapper(
        mapOf(
          Type.getInternalName(MockSerializable::class.java) to SERIALIZABLE_INTERNAL_NAME,
          Type.getInternalName(MockNavKey::class.java) to NAV_KEY_INTERNAL_NAME,
        )
      )

    val transform = PublicSerializableTransform(classWriter)
    val adapter = ClassRemapper(transform, remapper)

    classReader.accept(adapter, 0)

    val resultBytes = classWriter.toByteArray()
    val resultReader = ClassReader(resultBytes)
    val resultNode = ClassNode()
    resultReader.accept(resultNode, 0)

    // Parent itself should be transformed because it's private, @Serializable and implements NavKey
    assertTrue("Private parent should be public", (resultNode.access and Opcodes.ACC_PUBLIC) != 0)
  }

  @Test
  fun testSerializableNotNavKeyObjectIsNotTransformed() {
    val testClassBytes = loadClassBytes(SerializableNotNavKeyObject::class.java)
    val classReader = ClassReader(testClassBytes)
    val classWriter = ClassWriter(0)

    // Remap MockSerializable to the internal name expected by the transform
    val remapper = SimpleRemapper(mapOf(Type.getInternalName(MockSerializable::class.java) to SERIALIZABLE_INTERNAL_NAME))

    val transform = PublicSerializableTransform(classWriter)
    val adapter = ClassRemapper(transform, remapper)

    classReader.accept(adapter, 0)

    val resultBytes = classWriter.toByteArray()
    val resultReader = ClassReader(resultBytes)
    val resultNode = ClassNode()
    resultReader.accept(resultNode, 0)

    // Check visibility: it should NOT be public (it was private) because it's not a NavKey
    assertTrue("Class should NOT be public because it is not a NavKey", (resultNode.access and Opcodes.ACC_PUBLIC) == 0)
  }

  @Test
  fun testNonSerializableObjectIsNotTransformed() {
    val testClassBytes = loadClassBytes(NonSerializableObject::class.java)
    val classReader = ClassReader(testClassBytes)
    val classWriter = ClassWriter(0)

    // Even if we have a remapper, it won't find MockSerializable here
    val remapper = SimpleRemapper(emptyMap())

    val transform = PublicSerializableTransform(classWriter)
    val adapter = ClassRemapper(transform, remapper)

    classReader.accept(adapter, 0)

    val resultBytes = classWriter.toByteArray()
    val resultReader = ClassReader(resultBytes)
    val resultNode = ClassNode()
    resultReader.accept(resultNode, 0)

    // Check visibility: it should NOT be public (it was private)
    assertTrue("Class should NOT be public", (resultNode.access and Opcodes.ACC_PUBLIC) == 0)
  }

  @Test
  fun testNavKeyAbsentDoesNotTransformClasses() {
    val testClassBytes = loadClassBytes(SerializableChildNavKey::class.java)
    val classReader = ClassReader(testClassBytes)

    val parentInternalName = Type.getInternalName(SerializableParentNavKey::class.java)
    val childInternalName = Type.getInternalName(SerializableChildNavKey::class.java)

    // A locator that simulates NavKey being absent (returns objectPseudoClass for NAV_KEY_INTERNAL_NAME)
    val pseudoClassLocator =
      object : PseudoClassLocator {
        override fun locatePseudoClass(classFqn: String): PseudoClass {
          return when (classFqn) {
            parentInternalName.replace('/', '.') ->
              PseudoClass.forTest(classFqn, JAVA_OBJECT_FQN, false, listOf(NAV_KEY_INTERNAL_NAME.replace('/', '.')), this)
            childInternalName.replace('/', '.') ->
              PseudoClass.forTest(classFqn, parentInternalName.replace('/', '.'), false, emptyList(), this)
            // NavKey is not found/not on classpath, so it maps to objectPseudoClass
            else -> PseudoClass.objectPseudoClass()
          }
        }
      }

    val classWriter = ClassWriterWithPseudoClassLocator(0, pseudoClassLocator)

    val remapper =
      SimpleRemapper(
        mapOf(
          Type.getInternalName(MockSerializable::class.java) to SERIALIZABLE_INTERNAL_NAME,
          Type.getInternalName(MockNavKey::class.java) to NAV_KEY_INTERNAL_NAME,
        )
      )

    val publicSerializableTransform = PublicSerializableTransform(classWriter)
    val classRemapperAdapter = ClassRemapper(publicSerializableTransform, remapper)

    classReader.accept(classRemapperAdapter, 0)

    val resultBytes = classWriter.toByteArray()
    val resultReader = ClassReader(resultBytes)
    val resultClassNode = ClassNode()
    resultReader.accept(resultClassNode, 0)

    // Since NavKey was absent/objectPseudoClass, it should NOT have been matched or transformed, so visibility remains private
    assertTrue("Class should NOT be public when NavKey is absent from classpath", (resultClassNode.access and Opcodes.ACC_PUBLIC) == 0)
  }
}
