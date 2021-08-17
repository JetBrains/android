package com.android.tools.idea.rendering.classloading

import com.android.tools.idea.editors.literals.LiteralUsageReference
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.android.uipreview.ModuleProvider
import org.jetbrains.kotlin.name.FqName
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PrimitiveTypeRemapperTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()
  val project: Project
    get() = projectRule.project

  private val moduleProvider = object : ModuleProvider {
    override val module: Module
      get() = projectRule.module
  }

  private fun addConstant(name: String, initialValue: Any, currentValue: Any) =
    ProjectConstantRemapper.getInstance(project).addConstant(
      null, LiteralUsageReference(FqName("com.example.Test.${name}Test"), "$name.kt", TextRange(10, 20), 1)
      , initialValue, currentValue)

  @Before
  fun setup() {
    addConstant("Long", 123L, 111L)
    addConstant("Int", 123, 222)
    addConstant("Short", 123.toShort(), 333.toShort())
    addConstant("Float", 123f, 444f)
    addConstant("Double", 123.0, 555.0)
    addConstant("Byte", 123.toByte(), 666.toByte())
    addConstant("Char", 'A', 'B')
    addConstant("Boolean", initialValue = true, currentValue = false)
  }

  @After
  fun tearDown() {
    ProjectConstantRemapper.getInstance(project).clearConstants(null)
  }

  @Test
  fun `direct remapping returns the correct value`() {
    assertEquals(111L, PrimitiveTypeRemapper.remapLong(moduleProvider, "Long.kt", 10, 123L))
    assertEquals(222, PrimitiveTypeRemapper.remapInt(moduleProvider, "Int.kt", 10, 123))
    assertEquals(333.toShort(), PrimitiveTypeRemapper.remapShort(moduleProvider, "Short.kt", 10, 123.toShort()))
    assertEquals(444f, PrimitiveTypeRemapper.remapFloat(moduleProvider, "Float.kt", 10, 123f))
    assertEquals(555.0, PrimitiveTypeRemapper.remapDouble(moduleProvider, "Double.kt", 10, 123.0), 0.1)
    assertEquals(666.toByte(), PrimitiveTypeRemapper.remapByte(moduleProvider, "Byte.kt", 10, 123.toByte()))
    assertEquals('B', PrimitiveTypeRemapper.remapChar(moduleProvider, "Char.kt", 10, 'A'))
    assertEquals(false, PrimitiveTypeRemapper.remapBoolean(moduleProvider, "Boolean.kt", 10, true))
  }

  @Test
  fun `missing mappings return the default value`() {
    assertEquals(123L, PrimitiveTypeRemapper.remapLong(moduleProvider, "Long.kt", 20, 123L))
    assertEquals(123, PrimitiveTypeRemapper.remapInt(moduleProvider, "Int.kt", 20, 123))
    assertEquals(123.toShort(), PrimitiveTypeRemapper.remapShort(moduleProvider, "Short.kt", 20, 123.toShort()))
    assertEquals(123f, PrimitiveTypeRemapper.remapFloat(moduleProvider, "Float.kt", 20, 123f))
    assertEquals(123.0, PrimitiveTypeRemapper.remapDouble(moduleProvider, "Double.kt", 20, 123.0), 0.1)
    assertEquals(123.toByte(), PrimitiveTypeRemapper.remapByte(moduleProvider, "Byte.kt", 20, 123.toByte()))
    assertEquals('D', PrimitiveTypeRemapper.remapChar(moduleProvider, "Char.kt", 20, 'D'))
    assertEquals(true, PrimitiveTypeRemapper.remapBoolean(moduleProvider, "Boolean.kt", 20, true))
  }

  /**
   * Checks that remapping across types. For example, long to int. This could happen if a constant suddenly is edited to be a different
   * type than it was.
   *
   * For example, a constant was registered as short, but while editing, it becomes a long. We store the value as long but when the
   * byte code asks for the value back we need to cast it as short with the loss of data.
   */
  @Test
  fun `cross type remappings return valid values`() {
    // Remap to short
    assertEquals(111.toShort(), PrimitiveTypeRemapper.remapShort(moduleProvider, "Long.kt", 10, 123))
    assertEquals(222.toShort(), PrimitiveTypeRemapper.remapShort(moduleProvider, "Int.kt", 10, 123))
    assertEquals(666.toByte().toShort(), PrimitiveTypeRemapper.remapShort(moduleProvider, "Byte.kt", 10, 123))

    // Remap to int
    assertEquals(111, PrimitiveTypeRemapper.remapInt(moduleProvider, "Long.kt", 10, 123))
    assertEquals(333, PrimitiveTypeRemapper.remapInt(moduleProvider, "Short.kt", 10, 123))
    assertEquals(666.toByte().toInt(), PrimitiveTypeRemapper.remapInt(moduleProvider, "Byte.kt", 10, 123))

    // Remap to long
    assertEquals(222, PrimitiveTypeRemapper.remapLong(moduleProvider, "Int.kt", 10, 123))
    assertEquals(333, PrimitiveTypeRemapper.remapLong(moduleProvider, "Short.kt", 10, 123))
    assertEquals(666.toByte().toLong(), PrimitiveTypeRemapper.remapLong(moduleProvider, "Byte.kt", 10, 123))

    // Remap to byte
    assertEquals(222.toByte(), PrimitiveTypeRemapper.remapByte(moduleProvider, "Int.kt", 10, 123))
    assertEquals(333.toByte(), PrimitiveTypeRemapper.remapByte(moduleProvider, "Short.kt", 10, 123))
    assertEquals(111.toByte(), PrimitiveTypeRemapper.remapByte(moduleProvider, "Long.kt", 10, 123))

    // Remap to float
    assertEquals(555.0.toFloat(), PrimitiveTypeRemapper.remapFloat(moduleProvider, "Double.kt", 10, 123f))

    // Remap to double
    assertEquals(444f.toDouble(), PrimitiveTypeRemapper.remapDouble(moduleProvider, "Float.kt", 10, 123.0), 0.1)
  }
}