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

import com.android.tools.rendering.classloading.PseudoClass
import com.android.tools.rendering.classloading.PseudoClassLocator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Class test definition used to instantiate test [PseudoClass] instances. This definition contains a way to group
 * class information needed by [PseudoClass] without needing an available [PseudoClassLocator].
 */
private data class ClassDef(val superName: String, val isInterface: Boolean = false, val interfaces: List<String> = listOf())

/**
 * A test [PseudoClassLocator] that provides static [PseudoClass] instances from the given class definitions.
 */
private class MapClassLocator(private val map: Map<String, ClassDef>) : PseudoClassLocator {
  override fun locatePseudoClass(classFqn: String): PseudoClass = map[classFqn]?.let {
    PseudoClass.forTest(classFqn, it.superName, it.isInterface, it.interfaces, this)
  } ?: PseudoClass.objectPseudoClass()
}

class ClassWriterWithPseudoClassLocatorTest {
  private val classLocator = MapClassLocator(
    mapOf(
      "java.lang.Object" to ClassDef("java.lang.Object"),
      // Interfaces
      "p.I1" to ClassDef("java.lang.Object", true),
      "p.I2" to ClassDef("java.lang.Object", true),
      "p.SI1" to ClassDef("java.lang.Object", true, listOf("p.I1")),
      "p.SI2" to ClassDef("java.lang.Object", true, listOf("p.I2")),
      // Classes
      "p.C1" to ClassDef("java.lang.Object", false, listOf("p.SI1")),
      "p.C2" to ClassDef("java.lang.Object", false, listOf("p.SI2")),
      "p.SC1" to ClassDef("p.C1"),
      "p.SSC1" to ClassDef("p.SC1")
    )
  )

  @Test
  fun `can locate PseudoClass instances correctly`() {
    assertEquals(PseudoClass.objectPseudoClass(), classLocator.locatePseudoClass("p.NE1"))
    assertEquals(classLocator.locatePseudoClass("p.C1"), classLocator.locatePseudoClass("p.C1"))
  }

  @Test
  fun `check isAssignableFrom allows children to be assigned to parent types`() {
    val objectClass = classLocator.locatePseudoClass("java.lang.Object")
    val c1 = classLocator.locatePseudoClass("p.C1")
    val sc1 = classLocator.locatePseudoClass("p.SC1")
    val ssc1 = classLocator.locatePseudoClass("p.SSC1")

    // Check that they can be assigned to themselves
    assertTrue(c1.isAssignableFrom(c1))
    assertTrue(sc1.isAssignableFrom(sc1))

    // Check children can be assigned to parent
    assertTrue(c1.isAssignableFrom(sc1))
    assertTrue(c1.isAssignableFrom(ssc1))
    assertTrue(sc1.isAssignableFrom(ssc1))
    assertFalse(sc1.isAssignableFrom(c1))
    assertFalse(ssc1.isAssignableFrom(c1))
    assertFalse(ssc1.isAssignableFrom(sc1))

    // And that everything can be assigned to object
    assertTrue(objectClass.isAssignableFrom(c1))
    assertTrue(objectClass.isAssignableFrom(sc1))
    assertTrue(objectClass.isAssignableFrom(ssc1))
  }

  @Test
  fun `check interface chains are correctly resolved`() {
    val sc1 = classLocator.locatePseudoClass("p.SC1")

    assertEquals("""
      p.I1
      p.SI1
    """.trimIndent(),
                 sc1.interfaces().map { it.name }.sorted().joinToString("\n"))
  }

  @Test
  fun `check implementsInterface`() {
    val i1 = classLocator.locatePseudoClass("p.I1")
    val i2 = classLocator.locatePseudoClass("p.I2")
    val c1 = classLocator.locatePseudoClass("p.C1")
    val c2 = classLocator.locatePseudoClass("p.C2")
    val sc1 = classLocator.locatePseudoClass("p.SC1")
    val ssc1 = classLocator.locatePseudoClass("p.SSC1")

    assertTrue(c1.implementsInterface(i1))
    assertTrue(c2.implementsInterface(i2))
    assertTrue(sc1.implementsInterface(i1))
    assertTrue(ssc1.implementsInterface(i1))
    assertFalse(c1.implementsInterface(i2))
    assertFalse(c2.implementsInterface(i1))
  }

  @Test
  fun `check common super is returned correctly`() {
    val c1 = classLocator.locatePseudoClass("p.C1")
    val c2 = classLocator.locatePseudoClass("p.C2")
    val sc1 = classLocator.locatePseudoClass("p.SC1")
    val ssc1 = classLocator.locatePseudoClass("p.SSC1")

    assertEquals(PseudoClass.objectPseudoClass(), PseudoClass.getCommonSuperClass(c1, c2))
    assertEquals(PseudoClass.objectPseudoClass(), PseudoClass.getCommonSuperClass(sc1, c2))
    assertEquals("p.C1", PseudoClass.getCommonSuperClass(c1, sc1).name)
    assertEquals("p.C1", PseudoClass.getCommonSuperClass(c1, ssc1).name)
    assertEquals("p.SC1", PseudoClass.getCommonSuperClass(sc1, ssc1).name)
  }

  @Test
  fun `class rename`() {
    val sc1 = classLocator.locatePseudoClass("p.SC1")
    val renamedSc1 = sc1.withNewName("p.RSC1")
    assertEquals("p.C1", renamedSc1.superName)
  }
}