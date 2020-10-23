package com.android.tools.idea.rendering.classloading

import com.android.tools.idea.editors.literals.LiteralUsageReference
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.name.FqName
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PrimitiveTypeRemapperTest {
  private fun addConstant(name: String, initialValue: Any, currentValue: Any) =
    ConstantRemapperManager.getConstantRemapper().addConstant(
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
    ConstantRemapperManager.getConstantRemapper().clearConstants(null)
  }

  @Test
  fun `direct remapping returns the correct value`() {
    assertEquals(111L, PrimitiveTypeRemapper.remapLong(null, "Long.kt", 10, 123L))
    assertEquals(222, PrimitiveTypeRemapper.remapInt(null, "Int.kt", 10, 123))
    assertEquals(333.toShort(), PrimitiveTypeRemapper.remapShort(null, "Short.kt", 10, 123.toShort()))
    assertEquals(444f, PrimitiveTypeRemapper.remapFloat(null, "Float.kt", 10, 123f))
    assertEquals(555.0, PrimitiveTypeRemapper.remapDouble(null, "Double.kt", 10, 123.0), 0.1)
    assertEquals(666.toByte(), PrimitiveTypeRemapper.remapByte(null, "Byte.kt", 10, 123.toByte()))
    assertEquals('B', PrimitiveTypeRemapper.remapChar(null, "Char.kt", 10, 'A'))
    assertEquals(false, PrimitiveTypeRemapper.remapBoolean(null, "Boolean.kt", 10, true))
  }

  @Test
  fun `missing mappings return the default value`() {
    assertEquals(123L, PrimitiveTypeRemapper.remapLong(null, "Long.kt", 20, 123L))
    assertEquals(123, PrimitiveTypeRemapper.remapInt(null, "Int.kt", 20, 123))
    assertEquals(123.toShort(), PrimitiveTypeRemapper.remapShort(null, "Short.kt", 20, 123.toShort()))
    assertEquals(123f, PrimitiveTypeRemapper.remapFloat(null, "Float.kt", 20, 123f))
    assertEquals(123.0, PrimitiveTypeRemapper.remapDouble(null, "Double.kt", 20, 123.0), 0.1)
    assertEquals(123.toByte(), PrimitiveTypeRemapper.remapByte(null, "Byte.kt", 20, 123.toByte()))
    assertEquals('D', PrimitiveTypeRemapper.remapChar(null, "Char.kt", 20, 'D'))
    assertEquals(true, PrimitiveTypeRemapper.remapBoolean(null, "Boolean.kt", 20, true))
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
    assertEquals(111.toShort(), PrimitiveTypeRemapper.remapShort(null, "Long.kt", 10, 123))
    assertEquals(222.toShort(), PrimitiveTypeRemapper.remapShort(null, "Int.kt", 10, 123))
    assertEquals(666.toByte().toShort(), PrimitiveTypeRemapper.remapShort(null, "Byte.kt", 10, 123))

    // Remap to int
    assertEquals(111, PrimitiveTypeRemapper.remapInt(null, "Long.kt", 10, 123))
    assertEquals(333, PrimitiveTypeRemapper.remapInt(null, "Short.kt", 10, 123))
    assertEquals(666.toByte().toInt(), PrimitiveTypeRemapper.remapInt(null, "Byte.kt", 10, 123))

    // Remap to long
    assertEquals(222, PrimitiveTypeRemapper.remapLong(null, "Int.kt", 10, 123))
    assertEquals(333, PrimitiveTypeRemapper.remapLong(null, "Short.kt", 10, 123))
    assertEquals(666.toByte().toLong(), PrimitiveTypeRemapper.remapLong(null, "Byte.kt", 10, 123))

    // Remap to byte
    assertEquals(222.toByte(), PrimitiveTypeRemapper.remapByte(null, "Int.kt", 10, 123))
    assertEquals(333.toByte(), PrimitiveTypeRemapper.remapByte(null, "Short.kt", 10, 123))
    assertEquals(111.toByte(), PrimitiveTypeRemapper.remapByte(null, "Long.kt", 10, 123))

    // Remap to float
    assertEquals(555.0.toFloat(), PrimitiveTypeRemapper.remapFloat(null, "Double.kt", 10, 123f))

    // Remap to double
    assertEquals(444f.toDouble(), PrimitiveTypeRemapper.remapDouble(null, "Float.kt", 10, 123.0), 0.1)
  }
}