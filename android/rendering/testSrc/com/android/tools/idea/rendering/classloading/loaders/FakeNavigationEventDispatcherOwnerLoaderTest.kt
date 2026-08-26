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
package com.android.tools.idea.rendering.classloading.loaders

import com.android.tools.idea.rendering.classloading.FakeNavigationEventDispatcherOwnerDump
import com.android.tools.rendering.classloading.loaders.DelegatingClassLoader
import com.android.tools.rendering.classloading.loaders.StaticLoader
import java.lang.reflect.Modifier
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Assert.assertArrayEquals
import org.junit.Test

private const val CLASS_NAME_1 = "should.skip.ThisClass1"
private const val CLASS_NAME_2 = "should.skip.ThisClass2"
private const val CLASS_NAME_3 = "should.skip.ThisClass3"
private const val FAKE_NAVIGATION_EVENT_DISPATCHER_OWNER = "androidx.navigationevent.compose.FakeNavigationEventDispatcherOwner"

class FakeNavigationEventDispatcherOwnerLoaderTest {

  private val expectedClassContent = FakeNavigationEventDispatcherOwnerDump.getClassDumpByteArray

  @Test
  fun `FakeNavigationEventDispatcherOwner is loaded with the correct content`() {
    val thisClass1Bytes = ByteArray(size = 4)
    val thisClass2Bytes = ByteArray(size = 5)
    val thisClass3Bytes = ByteArray(size = 42)
    val fakeNavigationEventDispatcherOwnerBytes = ByteArray(size = 0)

    // Given a map of classes with an empty FakeNavigationEventDispatcherOwner.
    val classesToLoad =
      mapOf(
        CLASS_NAME_1 to thisClass1Bytes,
        FAKE_NAVIGATION_EVENT_DISPATCHER_OWNER to fakeNavigationEventDispatcherOwnerBytes,
        CLASS_NAME_2 to thisClass2Bytes,
        CLASS_NAME_3 to thisClass3Bytes,
      )

    // Given a delegate containing the classes.
    val loadedClasses = mutableSetOf<String>()
    val staticLoader = StaticLoader(classesToLoad)
    val classDetectorDelegate =
      object : DelegatingClassLoader.Loader {
        override fun loadClass(fqcn: String): ByteArray? {
          try {
            return staticLoader.loadClass(fqcn)
          } finally {
            loadedClasses.add(fqcn)
          }
        }
      }

    // When FakeNavigationEventDispatcherOwnerLoader loads the classes from the delegate.
    val loader = FakeNavigationEventDispatcherOwnerLoader(classDetectorDelegate)
    val loadedClassesContent = classesToLoad.keys.associateWith { name -> loader.loadClass(name) }

    // Then one of the content of the FakeNavigationEventDispatcherOwner is not empty, and contains
    // the expected content.
    assertArrayEquals(expectedClassContent, loadedClassesContent[FAKE_NAVIGATION_EVENT_DISPATCHER_OWNER])
    assertArrayNotEquals(fakeNavigationEventDispatcherOwnerBytes, loadedClassesContent[FAKE_NAVIGATION_EVENT_DISPATCHER_OWNER])

    // Verify other classes are loaded correctly
    assertArrayEquals(thisClass1Bytes, loadedClassesContent[CLASS_NAME_1])
    assertArrayEquals(thisClass2Bytes, loadedClassesContent[CLASS_NAME_2])
    assertArrayEquals(thisClass3Bytes, loadedClassesContent[CLASS_NAME_3])
  }

  @Test
  fun testFakeNavigationEventDispatcherOwnerBytecodeVerification() {
    val staticLoader = StaticLoader(emptyMap())
    val classDetectorDelegate =
      object : DelegatingClassLoader.Loader {
        override fun loadClass(fqcn: String): ByteArray? {
          return staticLoader.loadClass(fqcn)
        }
      }
    val loader = FakeNavigationEventDispatcherOwnerLoader(classDetectorDelegate)
    val classBytes = loader.loadClass(FAKE_NAVIGATION_EVENT_DISPATCHER_OWNER)!!

    val classLoader =
      object : ClassLoader(FakeNavigationEventDispatcherOwnerLoaderTest::class.java.classLoader) {
        fun defineClassByName(name: String, bytes: ByteArray): Class<*> {
          return defineClass(name, bytes, 0, bytes.size)
        }
      }

    // This will define the class and trigger JVM bytecode verification.
    // If stack map frames are invalid, this will throw a java.lang.VerifyError.
    val clazz = classLoader.defineClassByName(FAKE_NAVIGATION_EVENT_DISPATCHER_OWNER, classBytes)

    // Verify getHistory method signature
    val getHistoryMethod = clazz.declaredMethods.single { it.name == "getHistory" }
    assertEquals(List::class.java, getHistoryMethod.returnType)

    // Verify getCurrentIndex method signature
    val getCurrentIndexMethod = clazz.declaredMethods.single { it.name == "getCurrentIndex" }
    assertEquals(Int::class.javaPrimitiveType, getCurrentIndexMethod.returnType)
    assertEquals(0, getCurrentIndexMethod.parameterCount)

    // Verify backToState method signature
    val backToStateMethod = clazz.declaredMethods.single { it.name == "backToState" }
    assertEquals(Boolean::class.javaPrimitiveType, backToStateMethod.returnType)
    assertEquals(1, backToStateMethod.parameterCount)
    assertEquals(Any::class.java, backToStateMethod.parameterTypes[0])
  }

  @Test
  fun testDelegateIsNotCalledForFakeNavigationEventDispatcherOwner() {
    // Given a delegate loader that throws an error if called for FakeNavigationEventDispatcherOwner.
    val throwingDelegate =
      object : DelegatingClassLoader.Loader {
        override fun loadClass(fqcn: String): ByteArray? {
          if (fqcn == FAKE_NAVIGATION_EVENT_DISPATCHER_OWNER) {
            throw AssertionError("Delegate should not be called for $FAKE_NAVIGATION_EVENT_DISPATCHER_OWNER")
          }
          return null
        }
      }
    val loader = FakeNavigationEventDispatcherOwnerLoader(throwingDelegate)

    // Verify the delegate reference is preserved on the loader.
    assertSame(throwingDelegate, loader.delegate)

    // When loading FakeNavigationEventDispatcherOwner, verify it returns the dump byte array
    // without delegating.
    val result = loader.loadClass(FAKE_NAVIGATION_EVENT_DISPATCHER_OWNER)
    assertArrayEquals(expectedClassContent, result)
  }

  @Test
  fun testDelegateIsCalledForOtherClassesAndReturnsNullWhenNotFound() {
    // Given a spy delegate that tracks which class was requested and provides sample bytes for known classes.
    var delegateCalledWith: String? = null
    val spyDelegate =
      object : DelegatingClassLoader.Loader {
        override fun loadClass(fqcn: String): ByteArray? {
          delegateCalledWith = fqcn
          return if (fqcn == "com.example.KnownClass") ByteArray(10) else null
        }
      }
    val loader = FakeNavigationEventDispatcherOwnerLoader(spyDelegate)

    // When loading a class present in the delegate, verify delegation occurs and bytes are returned.
    val knownResult = loader.loadClass("com.example.KnownClass")
    assertEquals("com.example.KnownClass", delegateCalledWith)
    assertNotNull(knownResult)
    assertEquals(10, knownResult.size)

    // When loading an unknown class, verify delegation occurs and null is returned.
    val unknownResult = loader.loadClass("com.example.UnknownClass")
    assertEquals("com.example.UnknownClass", delegateCalledWith)
    assertNull(unknownResult)
  }

  @Test
  fun testFakeNavigationEventDispatcherOwnerClassStructureAndModifiers() {
    // Given the FakeNavigationEventDispatcherOwner bytecode loaded from the loader.
    val staticLoader = StaticLoader(emptyMap())
    val loader = FakeNavigationEventDispatcherOwnerLoader(staticLoader)
    val classBytes = loader.loadClass(FAKE_NAVIGATION_EVENT_DISPATCHER_OWNER)!!

    // Define the class using a custom ClassLoader to inspect JVM class metadata.
    val classLoader =
      object : ClassLoader(FakeNavigationEventDispatcherOwnerLoaderTest::class.java.classLoader) {
        fun defineClassByName(name: String, bytes: ByteArray): Class<*> {
          return defineClass(name, bytes, 0, bytes.size)
        }
      }

    val clazz = classLoader.defineClassByName(FAKE_NAVIGATION_EVENT_DISPATCHER_OWNER, classBytes)

    // Verify class modifiers are public and final.
    assertTrue(Modifier.isPublic(clazz.modifiers))
    assertTrue(Modifier.isFinal(clazz.modifiers))

    // Verify default constructor existence and modifiers.
    val constructors = clazz.declaredConstructors
    assertEquals(1, constructors.size)
    assertEquals(0, constructors[0].parameterCount)
    assertTrue(Modifier.isPublic(constructors[0].modifiers))

    // Verify implemented interfaces.
    val interfaceNames = clazz.interfaces.map { it.name }
    assertTrue(interfaceNames.contains("androidx.navigationevent.NavigationEventDispatcherOwner"))

    // Verify expected fields exist on the class.
    val fieldNames = clazz.declaredFields.map { it.name }.toSet()
    assertTrue(fieldNames.contains("navigationEventDispatcher"))
    assertTrue(fieldNames.contains("directNavigationEventInput" + '$' + "delegate"))
    assertTrue(fieldNames.contains('$' + "stable"))
  }

  @Test
  fun testFakeNavigationEventDispatcherOwnerNavigationMethodSignatures() {
    // Given the FakeNavigationEventDispatcherOwner bytecode defined in a custom ClassLoader.
    val staticLoader = StaticLoader(emptyMap())
    val loader = FakeNavigationEventDispatcherOwnerLoader(staticLoader)
    val classBytes = loader.loadClass(FAKE_NAVIGATION_EVENT_DISPATCHER_OWNER)!!

    val classLoader =
      object : ClassLoader(FakeNavigationEventDispatcherOwnerLoaderTest::class.java.classLoader) {
        fun defineClassByName(name: String, bytes: ByteArray): Class<*> {
          return defineClass(name, bytes, 0, bytes.size)
        }
      }

    val clazz = classLoader.defineClassByName(FAKE_NAVIGATION_EVENT_DISPATCHER_OWNER, classBytes)

    // Verify getNavigationEventDispatcher method signature.
    val getNavDispatcherMethod = clazz.declaredMethods.single { it.name == "getNavigationEventDispatcher" }
    assertEquals("androidx.navigationevent.NavigationEventDispatcher", getNavDispatcherMethod.returnType.name)
    assertEquals(0, getNavDispatcherMethod.parameterCount)

    // Verify back press handling method signatures (canBackPress, onBackPressStarted, onBackPressProgress, onBackPressCompleted,
    // onBackPressCancelled).
    val canBackPressMethod = clazz.declaredMethods.single { it.name == "canBackPress" }
    assertEquals(Boolean::class.javaPrimitiveType, canBackPressMethod.returnType)
    assertEquals(0, canBackPressMethod.parameterCount)

    val onBackPressStartedMethod = clazz.declaredMethods.single { it.name == "onBackPressStarted" }
    assertEquals(Void.TYPE, onBackPressStartedMethod.returnType)
    assertEquals(1, onBackPressStartedMethod.parameterCount)
    assertEquals(String::class.java, onBackPressStartedMethod.parameterTypes[0])

    val onBackPressProgressMethod = clazz.declaredMethods.single { it.name == "onBackPressProgress" }
    assertEquals(Void.TYPE, onBackPressProgressMethod.returnType)
    assertEquals(2, onBackPressProgressMethod.parameterCount)
    assertEquals(Float::class.javaPrimitiveType, onBackPressProgressMethod.parameterTypes[0])
    assertEquals(String::class.java, onBackPressProgressMethod.parameterTypes[1])

    val onBackPressCompletedMethod = clazz.declaredMethods.single { it.name == "onBackPressCompleted" }
    assertEquals(Void.TYPE, onBackPressCompletedMethod.returnType)
    assertEquals(0, onBackPressCompletedMethod.parameterCount)

    val onBackPressCancelledMethod = clazz.declaredMethods.single { it.name == "onBackPressCancelled" }
    assertEquals(Void.TYPE, onBackPressCancelledMethod.returnType)
    assertEquals(0, onBackPressCancelledMethod.parameterCount)

    // Verify forward press handling method signatures (canForwardPress, onForwardPressStarted, onForwardPressProgress,
    // onForwardPressCompleted, onForwardPressCancelled).
    val canForwardPressMethod = clazz.declaredMethods.single { it.name == "canForwardPress" }
    assertEquals(Boolean::class.javaPrimitiveType, canForwardPressMethod.returnType)
    assertEquals(0, canForwardPressMethod.parameterCount)

    val onForwardPressStartedMethod = clazz.declaredMethods.single { it.name == "onForwardPressStarted" }
    assertEquals(Void.TYPE, onForwardPressStartedMethod.returnType)
    assertEquals(1, onForwardPressStartedMethod.parameterCount)
    assertEquals(String::class.java, onForwardPressStartedMethod.parameterTypes[0])

    val onForwardPressProgressMethod = clazz.declaredMethods.single { it.name == "onForwardPressProgress" }
    assertEquals(Void.TYPE, onForwardPressProgressMethod.returnType)
    assertEquals(2, onForwardPressProgressMethod.parameterCount)
    assertEquals(Float::class.javaPrimitiveType, onForwardPressProgressMethod.parameterTypes[0])
    assertEquals(String::class.java, onForwardPressProgressMethod.parameterTypes[1])

    val onForwardPressCompletedMethod = clazz.declaredMethods.single { it.name == "onForwardPressCompleted" }
    assertEquals(Void.TYPE, onForwardPressCompletedMethod.returnType)
    assertEquals(0, onForwardPressCompletedMethod.parameterCount)

    val onForwardPressCancelledMethod = clazz.declaredMethods.single { it.name == "onForwardPressCancelled" }
    assertEquals(Void.TYPE, onForwardPressCancelledMethod.returnType)
    assertEquals(0, onForwardPressCancelledMethod.parameterCount)
  }

  private fun assertArrayNotEquals(expected: ByteArray?, actual: ByteArray?) {
    assertNotEquals(expected?.contentToString(), actual?.contentToString())
  }
}
