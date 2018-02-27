// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.gradle.dsl.model.ext

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.*
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.*
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType.*
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import com.google.common.collect.ImmutableMap
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VfsUtil
import java.io.File
import java.math.BigDecimal

class GradlePropertyModelTest : GradleFileModelTestCase() {
  fun testProperties() {
    val text = """
               ext {
                 prop1 = 'value'
                 prop2 = 25
                 prop3 = true
                 prop4 = [ "key": 'val']
                 prop5 = [ 'val1', 'val2', "val3"]
                 prop6 = 25.3
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val extModel = buildModel.ext()

    run {
      val propertyModel = extModel.findProperty("prop1")
      assertEquals(STRING, propertyModel.valueType)
      assertEquals(REGULAR, propertyModel.propertyType)
      assertEquals("value", propertyModel.getValue(STRING_TYPE))
      assertEquals("value", propertyModel.getRawValue(STRING_TYPE))
      assertEquals("prop1", propertyModel.name)
      assertEquals("ext.prop1", propertyModel.fullyQualifiedName)
    }

    run {
      val propertyModel = extModel.findProperty("prop2")
      assertEquals(INTEGER, propertyModel.valueType)
      assertEquals(REGULAR, propertyModel.propertyType)
      assertEquals(25, propertyModel.getValue(INTEGER_TYPE))
      assertEquals(25, propertyModel.getRawValue(INTEGER_TYPE))
      assertEquals("prop2", propertyModel.name)
      assertEquals("ext.prop2", propertyModel.fullyQualifiedName)
    }

    run {
      val propertyModel = extModel.findProperty("prop3")
      assertEquals(BOOLEAN, propertyModel.valueType)
      assertEquals(REGULAR, propertyModel.propertyType)
      assertEquals(true, propertyModel.getValue(BOOLEAN_TYPE))
      assertEquals(true, propertyModel.getRawValue(BOOLEAN_TYPE))
      assertEquals("prop3", propertyModel.name)
      assertEquals("ext.prop3", propertyModel.fullyQualifiedName)
    }

    run {
      val propertyModel = extModel.findProperty("prop4")
      assertEquals(MAP, propertyModel.valueType)
      assertEquals(REGULAR, propertyModel.propertyType)
      assertEquals("prop4", propertyModel.name)
      assertEquals("ext.prop4", propertyModel.fullyQualifiedName)

      val value = propertyModel.getValue(MAP_TYPE)!!["key"]!!
      assertEquals("val", value.getValue(STRING_TYPE))
      assertEquals(DERIVED, value.propertyType)
      assertEquals(STRING, value.valueType)
      assertEquals("key", value.name)
      assertEquals("ext.prop4.key", value.fullyQualifiedName)
    }

    run {
      val propertyModel = extModel.findProperty("prop5")
      assertEquals(LIST, propertyModel.valueType)
      assertEquals(REGULAR, propertyModel.propertyType)
      assertEquals("prop5", propertyModel.name)
      assertEquals("ext.prop5", propertyModel.fullyQualifiedName)
      val list = propertyModel.getValue(LIST_TYPE)!!
      assertSize(3, list)

      run {
        val value = list[0]
        assertEquals("val1", value.getValue(STRING_TYPE))
        assertEquals(DERIVED, value.propertyType)
        assertEquals(STRING, value.valueType)
      }

      run {
        val value = list[1]
        assertEquals("val2", value.getValue(STRING_TYPE))
        assertEquals(DERIVED, value.propertyType)
        assertEquals(STRING, value.valueType)
      }

      run {
        val value = list[2]
        assertEquals("val3", value.getValue(STRING_TYPE))
        assertEquals(DERIVED, value.propertyType)
        assertEquals(STRING, value.valueType)
      }
    }

    run {
      val propertyModel = extModel.findProperty("prop6")
      assertEquals(BigDecimal("25.3"), propertyModel.toBigDecimal())
      assertEquals(BIG_DECIMAL, propertyModel.valueType)
      assertEquals(REGULAR, propertyModel.propertyType)
      assertEquals("prop6", propertyModel.name)
      assertEquals("ext.prop6", propertyModel.fullyQualifiedName)
    }
  }

  fun testVariables() {
    val text = """
               ext {
                 def prop1 = 'value'
                 def prop2 = 25
                 def prop3 = true
                 def prop4 = [ "key": 'val']
                 def prop5 = [ 'val1', 'val2', "val3"]
                 def prop6 = 25.3
               }""".trimIndent()
    writeToBuildFile(text)

    val extModel = gradleBuildModel.ext()

    run {
      val propertyModel = extModel.findProperty("prop1")
      assertEquals(STRING, propertyModel.valueType)
      assertEquals(VARIABLE, propertyModel.propertyType)
      assertEquals("value", propertyModel.getValue(STRING_TYPE))
      assertEquals("value", propertyModel.getRawValue(STRING_TYPE))
      assertEquals("prop1", propertyModel.name)
      assertEquals("ext.prop1", propertyModel.fullyQualifiedName)
    }

    run {
      val propertyModel = extModel.findProperty("prop2")
      assertEquals(INTEGER, propertyModel.valueType)
      assertEquals(VARIABLE, propertyModel.propertyType)
      assertEquals(25, propertyModel.getValue(INTEGER_TYPE))
      assertEquals(25, propertyModel.getRawValue(INTEGER_TYPE))
      assertEquals("prop2", propertyModel.name)
      assertEquals("ext.prop2", propertyModel.fullyQualifiedName)
    }

    run {
      val propertyModel = extModel.findProperty("prop3")
      assertEquals(BOOLEAN, propertyModel.valueType)
      assertEquals(VARIABLE, propertyModel.propertyType)
      assertEquals(true, propertyModel.getValue(BOOLEAN_TYPE))
      assertEquals(true, propertyModel.getRawValue(BOOLEAN_TYPE))
      assertEquals("prop3", propertyModel.name)
      assertEquals("ext.prop3", propertyModel.fullyQualifiedName)
    }

    run {
      val propertyModel = extModel.findProperty("prop4")
      assertEquals(MAP, propertyModel.valueType)
      assertEquals(VARIABLE, propertyModel.propertyType)
      assertEquals("prop4", propertyModel.name)
      assertEquals("ext.prop4", propertyModel.fullyQualifiedName)
      val value = propertyModel.getValue(MAP_TYPE)!!["key"]!!
      assertEquals("val", value.getValue(STRING_TYPE))
      assertEquals(DERIVED, value.propertyType)
      assertEquals(STRING, value.valueType)
    }

    run {
      val propertyModel = extModel.findProperty("prop5")
      assertEquals(LIST, propertyModel.valueType)
      assertEquals(VARIABLE, propertyModel.propertyType)
      assertEquals("prop5", propertyModel.name)
      assertEquals("ext.prop5", propertyModel.fullyQualifiedName)
      val list = propertyModel.getValue(LIST_TYPE)!!
      assertSize(3, list)

      run {
        val value = list[0]
        assertEquals("val1", value.getValue(STRING_TYPE))
        assertEquals(DERIVED, value.propertyType)
        assertEquals(STRING, value.valueType)
      }

      run {
        val value = list[1]
        assertEquals("val2", value.getValue(STRING_TYPE))
        assertEquals(DERIVED, value.propertyType)
        assertEquals(STRING, value.valueType)
      }

      run {
        val value = list[2]
        assertEquals("val3", value.getValue(STRING_TYPE))
        assertEquals(DERIVED, value.propertyType)
        assertEquals(STRING, value.valueType)
      }
    }

    run {
      val propertyModel = extModel.findProperty("prop6")
      assertEquals(BigDecimal("25.3"), propertyModel.toBigDecimal())
      assertEquals(BIG_DECIMAL, propertyModel.valueType)
      assertEquals(VARIABLE, propertyModel.propertyType)
      assertEquals("prop6", propertyModel.name)
      assertEquals("ext.prop6", propertyModel.fullyQualifiedName)
    }
  }

  fun testUnknownValues() {
    val text = """
               ext {
                 prop1 = z(1)
                 prop2 = 1 + 2
                 prop3 = obj.getName()
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val propertyOne = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(propertyOne, STRING_TYPE, "z(1)", UNKNOWN, REGULAR, 0)
      val propertyTwo = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyTwo, STRING_TYPE, "1 + 2", UNKNOWN, REGULAR, 0)
      val propertyThree = buildModel.ext().findProperty("prop3")
      verifyPropertyModel(propertyThree, STRING_TYPE, "obj.getName()", UNKNOWN, REGULAR, 0)
    }
  }

  fun testUnknownValuesInMap() {
    val text = """
               ext {
                 prop1 = [key: getValue(), key2: 2 + 3]
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      val map = propertyModel.getValue(MAP_TYPE)!!
      assertSize(2, map.entries)
      verifyPropertyModel(map["key"], STRING_TYPE, "getValue()", UNKNOWN, DERIVED, 0)
      verifyPropertyModel(map["key2"], STRING_TYPE, "2 + 3", UNKNOWN, DERIVED, 0)
    }
  }

  fun testUnknownValuesInList() {
    val text = """
               ext {
                 prop1 = [getValue(), 2 + 3, z(1)]
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(LIST, propertyModel.valueType)
      val list = propertyModel.getValue(LIST_TYPE)!!
      assertSize(3, list)
      verifyPropertyModel(list[0], STRING_TYPE, "getValue()", UNKNOWN, DERIVED, 0)
      verifyPropertyModel(list[1], STRING_TYPE, "2 + 3", UNKNOWN, DERIVED, 0)
      verifyPropertyModel(list[2], STRING_TYPE, "z(1)", UNKNOWN, DERIVED, 0)
    }
  }

  fun testGetProperties() {
    val text = """
               ext {
                 def var1 = "Value1"
                 prop1 = var1
                 def var2 = "Value2"
                 prop2 = "Cool ${'$'}{var2}"
                 def var3 = "Value3"
                 prop3 = "Nice ${'$'}var3"
               }""".trimIndent()
    writeToBuildFile(text)

    val extModel = gradleBuildModel.ext()
    val properties = extModel.properties
    // Note: this shouldn't include variables.
    assertSize(3, properties)

    verifyPropertyModel(properties[0], STRING_TYPE, "var1", REFERENCE, REGULAR, 1, "prop1", "ext.prop1")
    verifyPropertyModel(properties[0].dependencies[0], STRING_TYPE, "Value1", STRING, VARIABLE, 0, "var1", "ext.var1")
    verifyPropertyModel(properties[1], STRING_TYPE, "Cool Value2", STRING, REGULAR, 1, "prop2", "ext.prop2")
    verifyPropertyModel(properties[1].dependencies[0], STRING_TYPE, "Value2", STRING, VARIABLE, 0, "var2", "ext.var2")
    verifyPropertyModel(properties[2], STRING_TYPE, "Nice Value3", STRING, REGULAR, 1, "prop3", "ext.prop3")
    verifyPropertyModel(properties[2].dependencies[0], STRING_TYPE, "Value3", STRING, VARIABLE, 0, "var3", "ext.var3")
  }

  fun testGetVariables() {
    val text = """
               ext {
                 def var1 = "gecko"
                 def var2 = "barbet"
                 def var3 = "crane"
                 prop1 = "sidewinder"
                 prop2 = "jackel"
                 prop3 = "tiger"
               }""".trimIndent()
    writeToBuildFile(text)

    val extModel = gradleBuildModel.ext()
    val variables = extModel.variables
    // Note: this shouldn't include properties.
    assertSize(3, variables)

    verifyPropertyModel(variables[0], STRING_TYPE, "gecko", STRING, VARIABLE, 0, "var1", "ext.var1")
    verifyPropertyModel(variables[1], STRING_TYPE, "barbet", STRING, VARIABLE, 0, "var2", "ext.var2")
    verifyPropertyModel(variables[2], STRING_TYPE, "crane", STRING, VARIABLE, 0, "var3", "ext.var3")
  }

  fun testAsType() {
    val text = """
               ext {
                 def prop1 = 'value'
                 def prop2 = 25
                 def prop3 = true
                 def prop4 = [ "key": 'val']
                 def prop5 = [ 'val1', 'val2', "val3"]
               }""".trimIndent()
    writeToBuildFile(text)

    val extModel = gradleBuildModel.ext()

    run {
      val stringModel = extModel.findProperty("prop1")
      assertEquals("value", stringModel.toString())
      assertNull(stringModel.toInt())
      assertNull(stringModel.toBoolean())
      assertNull(stringModel.toList())
      assertNull(stringModel.toMap())
      val intModel = extModel.findProperty("prop2")
      assertEquals(25, intModel.toInt())
      assertEquals("25", intModel.toString())
      assertNull(intModel.toBoolean())
      assertNull(intModel.toMap())
      assertNull(intModel.toList())
      val boolModel = extModel.findProperty("prop3")
      assertEquals(true, boolModel.toBoolean())
      assertEquals("true", boolModel.toString())
      assertNull(boolModel.toInt())
      val mapModel = extModel.findProperty("prop4")
      assertNotNull(mapModel.toMap())
      assertNull(mapModel.toInt())
      assertNull(mapModel.toList())
      val listModel = extModel.findProperty("prop5")
      assertNotNull(listModel.toList())
      assertNull(listModel.toBoolean())
      assertNull(listModel.toMap())
    }
  }

  fun testGetNonQuotedListIndex() {
    val text = """
               ext {
                 prop1 = [1, "two", "3", 4]
                 prop2 = prop1[0]
                 prop3 = prop1[1]
               }""".trimIndent()
    writeToBuildFile(text)

    val extModel = gradleBuildModel.ext()

    val firstModel = extModel.findProperty("prop2")
    verifyPropertyModel(firstModel.resolve(), INTEGER_TYPE, 1, INTEGER, REGULAR, 1)
    verifyPropertyModel(firstModel, STRING_TYPE, "prop1[0]", REFERENCE, REGULAR, 1)
    val secondModel = extModel.findProperty("prop3")
    verifyPropertyModel(secondModel.resolve(), STRING_TYPE, "two", STRING, REGULAR, 1)
    verifyPropertyModel(secondModel, STRING_TYPE, "prop1[1]", REFERENCE, REGULAR, 1)
  }

  fun testReferencePropertyDependency() {
    val text = """
               ext {
                 prop1 = 'value'
                 prop2 = prop1
               }""".trimIndent()
    writeToBuildFile(text)

    val extModel = gradleBuildModel.ext()
    val propertyModel = extModel.findProperty("prop2")
    assertEquals("prop1", propertyModel.getValue(STRING_TYPE))
    assertEquals("prop1", propertyModel.getRawValue(STRING_TYPE))
    assertEquals(REFERENCE, propertyModel.valueType)
    assertEquals(REGULAR, propertyModel.propertyType)

    val deps = propertyModel.dependencies
    assertSize(1, deps)
    val value = deps[0]
    assertEquals("value", value.getValue(STRING_TYPE))
    assertEquals("value", value.getRawValue(STRING_TYPE))
    assertEquals(STRING, value.valueType)
    assertEquals(REGULAR, value.propertyType)
  }

  fun testIntegerReferencePropertyDependency() {
    val text = """
               ext {
                 prop1 = 25
                 prop2 = prop1
               }""".trimIndent()
    writeToBuildFile(text)

    val extModel = gradleBuildModel.ext()
    val propertyModel = extModel.findProperty("prop2")
    assertEquals("prop1", propertyModel.getValue(STRING_TYPE))
    assertEquals("prop1", propertyModel.getRawValue(STRING_TYPE))
    assertEquals(REFERENCE, propertyModel.valueType)
    assertEquals(REGULAR, propertyModel.propertyType)

    val deps = propertyModel.dependencies
    assertSize(1, deps)
    val value = deps[0]
    assertEquals(25, value.getValue(INTEGER_TYPE))
    assertEquals(25, value.getRawValue(INTEGER_TYPE))
    assertEquals(INTEGER, value.valueType)
    assertEquals(REGULAR, value.propertyType)
  }

  fun testReferenceVariableDependency() {
    val text = """
               ext {
                 def prop1 = 'value'
                 prop2 = prop1
               }""".trimIndent()
    writeToBuildFile(text)

    val extModel = gradleBuildModel.ext()
    val propertyModel = extModel.findProperty("prop2")
    assertEquals("prop1", propertyModel.getValue(STRING_TYPE))
    assertEquals("prop1", propertyModel.getRawValue(STRING_TYPE))
    assertEquals(REFERENCE, propertyModel.valueType)
    assertEquals(REGULAR, propertyModel.propertyType)

    val deps = propertyModel.dependencies
    assertSize(1, deps)
    val value = deps[0]
    assertEquals("value", value.getValue(STRING_TYPE))
    assertEquals("value", value.getRawValue(STRING_TYPE))
    assertEquals(STRING, value.valueType)
    assertEquals(VARIABLE, value.propertyType)
  }

  fun testCreateAndDeleteListToEmpty() {
    val text = """
               ext {
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val extModel = buildModel.ext()

    run {
      val propertyModel = extModel.findProperty("prop2")
      assertMissingProperty(propertyModel)
      propertyModel.addListValue().setValue("true")
      verifyListProperty(propertyModel, listOf("true"), true)
      val valueModel = propertyModel.getListValue("true")!!
      verifyPropertyModel(valueModel, STRING_TYPE, "true", STRING, DERIVED, 0, "0")
      valueModel.delete()
      assertMissingProperty(valueModel)
      verifyListProperty(propertyModel, listOf(), true)
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = extModel.findProperty("prop2")
      verifyListProperty(propertyModel, listOf(), true)
    }
  }

  fun testCreateAndDeletePlaceHoldersToEmpty() {
    val text = """
               android {
                 defaultConfig {
                 }
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.android()?.defaultConfig()?.manifestPlaceholders()!!
      assertMissingProperty(propertyModel)
      propertyModel.getMapValue("key").setValue("true")
      verifyMapProperty(propertyModel, mapOf("key" to "true"))
      val valueModel = propertyModel.getMapValue("key")
      verifyPropertyModel(valueModel, STRING_TYPE, "true", STRING, DERIVED, 0, "key")
      valueModel.delete()
      assertMissingProperty(valueModel)
      verifyMapProperty(propertyModel, mapOf())
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.android()?.defaultConfig()?.manifestPlaceholders()!!
      verifyMapProperty(propertyModel, mapOf())
    }
  }

  fun testCreateAndDeleteMapToEmpty() {
    val text = """
               ext {
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val extModel = buildModel.ext()

    run {
      val propertyModel = extModel.findProperty("prop2")
      assertMissingProperty(propertyModel)
      propertyModel.getMapValue("key").setValue("true")
      verifyMapProperty(propertyModel, mapOf("key" to "true"))
      val valueModel = propertyModel.getMapValue("key")
      verifyPropertyModel(valueModel, STRING_TYPE, "true", STRING, DERIVED, 0, "key")
      valueModel.delete()
      assertMissingProperty(valueModel)
      verifyMapProperty(propertyModel, mapOf())
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = extModel.findProperty("prop2")
      verifyMapProperty(propertyModel, mapOf())
    }
  }

  fun testReferenceMapDependency() {
    val text = """
               ext {
                 prop1 = ["key" : 'value']
                 prop2 = prop1
               }""".trimIndent()
    writeToBuildFile(text)

    val extModel = gradleBuildModel.ext()
    val propertyModel = extModel.findProperty("prop2")

    assertEquals(REFERENCE, propertyModel.valueType)
    assertEquals(REGULAR, propertyModel.propertyType)
    assertEquals("prop1", propertyModel.getRawValue(STRING_TYPE))
    assertEquals("prop1", propertyModel.getValue(STRING_TYPE))

    assertSize(1, propertyModel.dependencies)
    val dep = propertyModel.dependencies[0]
    assertEquals(MAP, dep.valueType)
    assertEquals(REGULAR, dep.propertyType)

    val map = dep.getValue(MAP_TYPE)!!
    assertSize(1, map.entries)
    val mapValue = map["key"]!!
    assertEquals(STRING, mapValue.valueType)
    assertEquals(DERIVED, mapValue.propertyType)
    assertEquals("value", mapValue.getValue(STRING_TYPE))
  }

  fun testReferenceListDependency() {
    val text = """
               ext {
                 prop1 = [1, true]
                 prop2 = prop1
               }""".trimIndent()
    writeToBuildFile(text)

    val extModel = gradleBuildModel.ext()
    val propertyModel = extModel.findProperty("prop2")
    assertEquals(REFERENCE, propertyModel.valueType)
    assertEquals(REGULAR, propertyModel.propertyType)
    assertEquals("prop1", propertyModel.getRawValue(STRING_TYPE))
    assertEquals("prop1", propertyModel.getValue(STRING_TYPE))

    assertSize(1, propertyModel.dependencies)
    val dep = propertyModel.dependencies[0]
    assertEquals(LIST, dep.valueType)
    assertEquals(REGULAR, dep.propertyType)

    val list = dep.getValue(LIST_TYPE)!!
    assertSize(2, list)

    // Check the first list value
    val firstItem = list[0]
    assertEquals(INTEGER, firstItem.valueType)
    assertEquals(DERIVED, firstItem.propertyType)
    assertEquals(1, firstItem.getValue(INTEGER_TYPE))

    val secondItem = list[1]
    assertEquals(BOOLEAN, secondItem.valueType)
    assertEquals(DERIVED, secondItem.propertyType)
    assertEquals(true, secondItem.getValue(BOOLEAN_TYPE))
  }

  fun testPropertyDependency() {
    val text = """"
               ext {
                 prop1 = 'hello'
                 prop2 = "${'$'}{prop1} world!"
               }""".trimIndent()
    writeToBuildFile(text)

    val extModel = gradleBuildModel.ext()
    val propertyModel = extModel.findProperty("prop2")
    assertEquals("\${prop1} world!", propertyModel.getRawValue(STRING_TYPE))
    assertEquals("hello world!", propertyModel.getValue(STRING_TYPE))
    assertEquals(STRING, propertyModel.valueType)
    assertEquals(REGULAR, propertyModel.propertyType)

    val dependencies = propertyModel.dependencies

    assertSize(1, dependencies)
    val dep = dependencies[0]
    assertEquals(STRING, dep.valueType)
    assertEquals(REGULAR, dep.propertyType)
    assertEquals("hello", dep.getValue(STRING_TYPE))
  }

  fun testVariableDependency() {
    val text = """
               ext {\n" +
                 def prop1 = 'hello'
                 def prop2 = "${'$'}{prop1} world!"
               }""".trimIndent()
    writeToBuildFile(text)

    val extModel = gradleBuildModel.ext()
    val propertyModel = extModel.findProperty("prop2")
    assertEquals("\${prop1} world!", propertyModel.getRawValue(STRING_TYPE))
    assertEquals("hello world!", propertyModel.getValue(STRING_TYPE))
    assertEquals(STRING, propertyModel.valueType)
    assertEquals(VARIABLE, propertyModel.propertyType)

    val dependencies = propertyModel.dependencies

    assertSize(1, dependencies)
    val dep = dependencies[0]
    assertEquals(STRING, dep.valueType)
    assertEquals(VARIABLE, dep.propertyType)
    assertEquals("hello", dep.getValue(STRING_TYPE))
  }

  fun testPropertyVariableDependency() {
    val text = """
               ext {
                 def prop1 = 'hello'
                 prop2 = "${'$'}{prop1} world!"
               }""".trimIndent()
    writeToBuildFile(text)

    val extModel = gradleBuildModel.ext()
    val propertyModel = extModel.findProperty("prop2")
    assertEquals("\${prop1} world!", propertyModel.getRawValue(STRING_TYPE))
    assertEquals("hello world!", propertyModel.getValue(STRING_TYPE))
    assertEquals(STRING, propertyModel.valueType)
    assertEquals(REGULAR, propertyModel.propertyType)

    val dependencies = propertyModel.dependencies

    assertSize(1, dependencies)
    val dep = dependencies[0]
    assertEquals(STRING, dep.valueType)
    assertEquals(VARIABLE, dep.propertyType)
    assertEquals("hello", dep.getValue(STRING_TYPE))
  }

  fun testVariablePropertyDependency() {
    val text = """
               ext {
                 prop1 = 'hello'
                 def prop2 = "${'$'}{prop1} world!"
               }""".trimIndent()
    writeToBuildFile(text)

    val extModel = gradleBuildModel.ext()
    val propertyModel = extModel.findProperty("prop2")
    assertEquals("\${prop1} world!", propertyModel.getRawValue(STRING_TYPE))
    assertEquals("hello world!", propertyModel.getValue(STRING_TYPE))
    assertEquals(STRING, propertyModel.valueType)
    assertEquals(VARIABLE, propertyModel.propertyType)

    val dependencies = propertyModel.dependencies

    assertSize(1, dependencies)
    val dep = dependencies[0]
    assertEquals(STRING, dep.valueType)
    assertEquals(REGULAR, dep.propertyType)
    assertEquals("hello", dep.getValue(STRING_TYPE))
  }

  fun testMultipleDependenciesWithFullyQualifiedName() {
    val text = """
               ext {
                 prop1 = 'value1'
                 def prop1 = 'value2'
                 prop2 = "${'$'}{prop1} and ${'$'}{project.ext.prop1}"
               }""".trimIndent()
    writeToBuildFile(text)

    val extModel = gradleBuildModel.ext()
    val propertyModel = extModel.findProperty("prop2")
    assertEquals("value2 and value1", propertyModel.getValue(STRING_TYPE))
    assertEquals("\${prop1} and \${project.ext.prop1}", propertyModel.getRawValue(STRING_TYPE))

    // Check the dependencies are correct
    val deps = propertyModel.dependencies
    assertSize(2, deps)

    run {
      val value = deps[0]
      assertEquals("prop1", value.name)
      assertEquals("value2", value.getValue(STRING_TYPE))
      assertEquals("ext.prop1", value.fullyQualifiedName)
      assertEquals(STRING, value.valueType)
      assertEquals(VARIABLE, value.propertyType)
    }

    run {
      val value = deps[1]
      assertEquals("prop1", value.name)
      assertEquals("value1", value.getValue(STRING_TYPE))
      assertEquals("ext.prop1", value.fullyQualifiedName)
      assertEquals(STRING, value.valueType)
      assertEquals(REGULAR, value.propertyType)
    }
  }

  fun testMultipleTypeDependenciesWithFullyQualifiedName() {
    val text = """
               ext {
                 prop1 = 'value1'
                 def prop1 = true
                 prop2 = "${'$'}{prop1} and ${'$'}{project.ext.prop1}"
               }""".trimIndent()
    writeToBuildFile(text)

    val extModel = gradleBuildModel.ext()
    val propertyModel = extModel.findProperty("prop2")
    assertEquals("true and value1", propertyModel.getValue(STRING_TYPE))
    assertEquals("\${prop1} and \${project.ext.prop1}", propertyModel.getRawValue(STRING_TYPE))

    // Check the dependencies are correct
    val deps = propertyModel.dependencies
    assertSize(2, deps)

    run {
      val value = deps[0]
      assertEquals("prop1", value.name)
      assertEquals(true, value.getValue(BOOLEAN_TYPE))
      assertEquals("ext.prop1", value.fullyQualifiedName)
      assertEquals(BOOLEAN, value.valueType)
      assertEquals(VARIABLE, value.propertyType)
    }

    run {
      val value = deps[1]
      assertEquals("prop1", value.name)
      assertEquals("value1", value.getValue(STRING_TYPE))
      assertEquals("ext.prop1", value.fullyQualifiedName)
      assertEquals(STRING, value.valueType)
      assertEquals(REGULAR, value.propertyType)
    }
  }

  fun testNestedListPropertyInjection() {
    val text = """
           ext {
             prop1 = [1, 2, 3]
             prop2 = [prop1, prop1, prop1]
             prop3 = ['key' : prop2]
             prop4 = "${'$'}{prop3.key[0][2]}"
           }""".trimIndent()
    writeToBuildFile(text)

    val propertyModel = gradleBuildModel.ext().findProperty("prop4")
    assertEquals("3", propertyModel.getValue(STRING_TYPE))
    assertEquals("${'$'}{prop3.key[0][2]}", propertyModel.getRawValue(STRING_TYPE))
    assertEquals(REGULAR, propertyModel.propertyType)
    assertEquals(STRING, propertyModel.valueType)

    val dependencies = propertyModel.dependencies
    assertSize(1, dependencies)
    val depModel = dependencies[0]
    assertEquals(INTEGER, depModel.valueType)
    assertEquals(DERIVED, depModel.propertyType)
    assertEquals(3, depModel.getValue(INTEGER_TYPE))
    assertSize(0, depModel.dependencies)
  }

  fun testNestedMapVariableInjection() {
    val text = """
               ext {
                 prop = true
                 def prop1 = ['key1': "value${'$'}{prop}"]
                 def prop2 = ["key2": prop1]
                 def prop3 = "${'$'}{prop2["key2"]["key1"]}"
               }""".trimIndent()
    writeToBuildFile(text)

    val propertyModel = gradleBuildModel.ext().findProperty("prop3")
    assertEquals(STRING, propertyModel.valueType)
    assertEquals(VARIABLE, propertyModel.propertyType)
    assertEquals("valuetrue", propertyModel.getValue(STRING_TYPE))
    assertEquals("${'$'}{prop2[\"key2\"][\"key1\"]}", propertyModel.getRawValue(STRING_TYPE))

    val dependencies = propertyModel.dependencies
    assertSize(1, dependencies)
    val depModel = dependencies[0]
    assertEquals(STRING, depModel.valueType)
    assertEquals(DERIVED, depModel.propertyType)
    assertEquals("valuetrue", depModel.getValue(STRING_TYPE))
    assertEquals("value${'$'}{prop}", depModel.getRawValue(STRING_TYPE))

    val dependencies2 = depModel.dependencies
    assertSize(1, dependencies2)
    val depModel2 = dependencies2[0]
    assertEquals(BOOLEAN, depModel2.valueType)
    assertEquals(REGULAR, depModel2.propertyType)
    assertEquals(true, depModel2.getValue(BOOLEAN_TYPE))
    assertEquals(true, depModel2.getRawValue(BOOLEAN_TYPE))
    assertSize(0, depModel2.dependencies)
  }

  fun testListDependency() {
    val text = """
               ext {
                 prop1 = ['value']
                 prop2 = "${'$'}{prop1[0]}"
               }""".trimIndent()
    writeToBuildFile(text)

    val extModel = gradleBuildModel.ext()
    val propertyModel = extModel.findProperty("prop2")
    assertEquals("value", propertyModel.getValue(STRING_TYPE))
    assertEquals("\${prop1[0]}", propertyModel.getRawValue(STRING_TYPE))

    val deps = propertyModel.dependencies
    assertSize(1, deps)
    val value = deps[0]
    assertEquals("value", value.getValue(STRING_TYPE))
    assertEquals(DERIVED, value.propertyType)
    assertEquals(STRING, value.valueType)
  }

  fun testMapDependency() {
    val text = """
               ext {
                 prop1 = ["key": 'value']
                 prop2 = "${'$'}{prop1.key}"
               }""".trimIndent()
    writeToBuildFile(text)

    val extModel = gradleBuildModel.ext()
    val propertyModel = extModel.findProperty("prop2")
    assertEquals("value", propertyModel.getValue(STRING_TYPE))
    assertEquals("\${prop1.key}", propertyModel.getRawValue(STRING_TYPE))

    val deps = propertyModel.dependencies
    val value = deps[0]
    assertEquals("value", value.getValue(STRING_TYPE))
    assertEquals(DERIVED, value.propertyType)
    assertEquals(STRING, value.valueType)
  }

  fun testOutOfScopeMapAndListDependencies() {
    val text = """
               def prop1 = 'value1'
               def prop2 = ["key" : 'value2']
               def prop3 = ['value3']
               ext {
                 prop4 = "${'$'}{prop1} and ${'$'}{prop2.key} and ${'$'}{prop3[0]}"
               }""".trimIndent()
    writeToBuildFile(text)

    val extModel = gradleBuildModel.ext()
    val propertyModel = extModel.findProperty("prop4")
    assertEquals("value1 and value2 and value3", propertyModel.getValue(STRING_TYPE))

    val deps = propertyModel.dependencies
    assertSize(3, deps)

    run {
      val value = deps[0]
      assertEquals("value1", value.getValue(STRING_TYPE))
      assertEquals(STRING, value.valueType)
      assertEquals(VARIABLE, value.propertyType)
    }

    run {
      val value = deps[1]
      assertEquals("value2", value.getValue(STRING_TYPE))
      assertEquals(STRING, value.valueType)
      assertEquals(DERIVED, value.propertyType)
    }

    run {
      val value = deps[2]
      assertEquals("value3", value.getValue(STRING_TYPE))
      assertEquals(STRING, value.valueType)
      assertEquals(DERIVED, value.propertyType)
    }
  }

  fun testDeepDependencies() {
    val text = """
               ext {
                 prop1 = '1'
                 prop2 = "2${'$'}{prop1}"
                 prop3 = "3${'$'}{prop2}"
                 prop4 = "4${'$'}{prop3}"
                 prop5 = "5${'$'}{prop4}"
                 prop6 = "6${'$'}{prop5}"
                 prop7 = "7${'$'}{prop6}"
                 prop8 = "8${'$'}{prop7}"
                 prop9 = "9${'$'}{prop8}"
               }""".trimIndent()
    writeToBuildFile(text)

    val extModel = gradleBuildModel.ext()
    var expected = "987654321"
    val propertyModel = extModel.findProperty("prop9")
    assertEquals(expected, propertyModel.getValue(STRING_TYPE))
    assertEquals("9\${prop8}", propertyModel.getRawValue(STRING_TYPE))
    assertEquals(STRING, propertyModel.valueType)
    assertEquals(REGULAR, propertyModel.propertyType)

    var deps = propertyModel.dependencies
    for (i in 1..7) {
      assertSize(1, deps)
      val value = deps[0]
      expected = expected.drop(1)
      assertEquals(expected, value.getValue(STRING_TYPE))
      assertEquals("${9 - i}\${prop${8 - i}}", value.getRawValue(STRING_TYPE))
      assertEquals(STRING, propertyModel.valueType)
      assertEquals(REGULAR, propertyModel.propertyType)
      deps = deps[0].dependencies
    }

    assertSize(1, deps)
    val value = deps[0]
    assertEquals("1", value.getValue(STRING_TYPE))
    assertEquals("1", value.getRawValue(STRING_TYPE))
    assertEquals(STRING, propertyModel.valueType)
    assertEquals(REGULAR, propertyModel.propertyType)
  }

  fun testDependenciesInMap() {
    val text = """
               ext {
                 prop1 = 25
                 prop2 = false
                 prop4 = ["key1" : prop1, "key2" : "${'$'}{prop2}"]
               }""".trimIndent()
    writeToBuildFile(text)

    val extModel = gradleBuildModel.ext()
    val propertyModel = extModel.findProperty("prop4")
    assertEquals(MAP, propertyModel.valueType)
    assertEquals(REGULAR, propertyModel.propertyType)
    assertEquals("prop4", propertyModel.name)
    assertEquals("ext.prop4", propertyModel.fullyQualifiedName)
    val deps = propertyModel.dependencies
    assertSize(2, deps)

    val map = propertyModel.getValue(MAP_TYPE)!!
    assertSize(2, map.entries)

    run {
      val value = map["key1"]!!
      assertEquals(REFERENCE, value.valueType)
      assertEquals(DERIVED, value.propertyType)
      assertEquals("prop1", value.getValue(STRING_TYPE))
      assertEquals("key1", value.name)
      assertEquals("ext.prop4.key1", value.fullyQualifiedName)

      val valueDeps = value.dependencies
      assertSize(1, valueDeps)
      val depValue = valueDeps[0]
      checkContainsValue(deps as Collection<GradlePropertyModel>, depValue)
      assertEquals(INTEGER, depValue.valueType)
      assertEquals(REGULAR, depValue.propertyType)
      assertEquals(25, depValue.getValue(INTEGER_TYPE))
      assertEquals("prop1", depValue.name)
      assertEquals("ext.prop1", depValue.fullyQualifiedName)
    }

    run {
      val value = map["key2"]!!
      assertEquals(STRING, value.valueType)
      assertEquals(DERIVED, value.propertyType)
      assertEquals("false", value.getValue(STRING_TYPE))
      assertEquals("key2", value.name)
      assertEquals("ext.prop4.key2", value.fullyQualifiedName)

      val valueDeps = value.dependencies
      assertSize(1, valueDeps)
      val depValue = valueDeps[0]
      checkContainsValue(deps as Collection<GradlePropertyModel>, depValue)
      assertEquals(BOOLEAN, depValue.valueType)
      assertEquals(REGULAR, depValue.propertyType)
      assertEquals(false, depValue.getValue(BOOLEAN_TYPE))
      assertEquals("prop2", depValue.name)
      assertEquals("ext.prop2", depValue.fullyQualifiedName)
    }
  }

  fun testGetFile() {
    val text = """
               ext {
                 prop1 = 'value'
               }""".trimIndent()
    writeToBuildFile(text)

    val extModel = gradleBuildModel.ext()
    val propertyModel = extModel.findProperty("prop1")
    assertEquals(propertyModel.gradleFile, VfsUtil.findFileByIoFile(myBuildFile, true))
  }

  fun testPropertySetValue() {
    val text = """
               ext {
                 prop2 = 'ref'
                 prop1 = 'value'
               }""".trimIndent()
    runSetPropertyTest(text, REGULAR)
  }

  fun testVariableSetValue() {
    val text = """
               ext {
                 def prop2 = 'ref'
                 def prop1 = 'value'
               }""".trimIndent()
    runSetPropertyTest(text, VARIABLE)
  }

  fun testSetUnknownValueType() {
    val text = """
               ext {
                 prop1 = "hello"
               }""".trimIndent()
    writeToBuildFile(text)
    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(propertyModel, STRING_TYPE, "hello", STRING, REGULAR, 0, "prop1", "ext.prop1")
      propertyModel.setValue(25)
      verifyPropertyModel(propertyModel, INTEGER_TYPE, 25, INTEGER, REGULAR, 0, "prop1", "ext.prop1")
      propertyModel.setValue(true)
      verifyPropertyModel(propertyModel, BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 0, "prop1", "ext.prop1")
      propertyModel.setValue("goodbye")
      verifyPropertyModel(propertyModel, STRING_TYPE, "goodbye", STRING, REGULAR, 0, "prop1", "ext.prop1")

      try {
        propertyModel.setValue(File("Hello"))
        fail()
      }
      catch (e: IllegalArgumentException) {
        // Expected
      }
      try {
        propertyModel.setValue(IllegalStateException("Boo"))
        fail()
      }
      catch (e: IllegalArgumentException) {
        // Expected
      }

      verifyPropertyModel(propertyModel, STRING_TYPE, "goodbye", STRING, REGULAR, 0, "prop1", "ext.prop1")
    }
  }

  fun testEscapeSetStrings() {
    val text = """
               ext {
               }""".trimIndent()
    writeToBuildFile(text)
    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertMissingProperty(propertyModel)
      propertyModel.setValue(iStr("\nNewLines\n\tWith\n\tSome\n\tTabs\n"))
      verifyPropertyModel(propertyModel, STRING_TYPE, "\nNewLines\n\tWith\n\tSome\n\tTabs\n", STRING, REGULAR, 0)
      assertEquals("\nNewLines\n\tWith\n\tSome\n\tTabs\n", propertyModel.getRawValue(STRING_TYPE))
      val literalModel = buildModel.ext().findProperty("prop2")
      assertMissingProperty(literalModel)
      literalModel.setValue("\nNewLines\n\tWith\n\tSome\n\tTabs\n")
      verifyPropertyModel(literalModel, STRING_TYPE, "\nNewLines\n\tWith\n\tSome\n\tTabs\n", STRING, REGULAR, 0)
      assertEquals("\nNewLines\n\tWith\n\tSome\n\tTabs\n", literalModel.getRawValue(STRING_TYPE))
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(propertyModel, STRING_TYPE, "\nNewLines\n\tWith\n\tSome\n\tTabs\n", STRING, REGULAR, 0)
      assertEquals("\nNewLines\n\tWith\n\tSome\n\tTabs\n", propertyModel.getRawValue(STRING_TYPE))
      val literalModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(literalModel, STRING_TYPE, "\nNewLines\n\tWith\n\tSome\n\tTabs\n", STRING, REGULAR, 0)
      assertEquals("\nNewLines\n\tWith\n\tSome\n\tTabs\n", literalModel.getRawValue(STRING_TYPE))
    }
  }

  fun testQuotesInString() {
    val text = """
               ext {
               }""".trimIndent()
    writeToBuildFile(text)
    val buildModel = gradleBuildModel

    run {
      val literalModel = buildModel.ext().findProperty("prop1")
      assertMissingProperty(literalModel)
      literalModel.setValue("'these should be escaped' \"But these shouldn't\"")
      verifyPropertyModel(literalModel, STRING_TYPE, "'these should be escaped' \"But these shouldn't\"", STRING, REGULAR, 0)
      assertEquals("'these should be escaped' \"But these shouldn't\"", literalModel.getRawValue(STRING_TYPE))

      val gStringModel = buildModel.ext().findProperty("prop2")
      assertMissingProperty(gStringModel)
      gStringModel.setValue(iStr("'these should not be escaped' \"But these should be\""))
      verifyPropertyModel(gStringModel, STRING_TYPE, "'these should not be escaped' \"But these should be\"", STRING, REGULAR, 0)
      assertEquals("'these should not be escaped' \"But these should be\"", gStringModel.getRawValue(STRING_TYPE))
    }

    applyChangesAndReparse(buildModel)

    run {
      val literalModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(literalModel, STRING_TYPE, "'these should be escaped' \"But these shouldn't\"", STRING, REGULAR, 0)
      assertEquals("'these should be escaped' \"But these shouldn't\"", literalModel.getRawValue(STRING_TYPE))
      val gStringModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(gStringModel, STRING_TYPE, "'these should not be escaped' \"But these should be\"", STRING, REGULAR, 0)
      assertEquals("'these should not be escaped' \"But these should be\"", gStringModel.getRawValue(STRING_TYPE))
    }
  }

  fun testSetBothStringTypes() {
    val text = """
               ext {
                 lamb = 'Lamb'
                 seven = 'sêvĕn'
                 def prop1 = 'Value1'
                 prop2 = 'Value2'
               }""".trimIndent()
    writeToBuildFile(text)
    val buildModel = gradleBuildModel

    run {
      // Set the literal string
      val literalProperty = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(literalProperty, STRING_TYPE, "Value1", STRING, VARIABLE, 0)
      literalProperty.setValue("I watched as the ${'$'}{lamb}")
      verifyPropertyModel(literalProperty, STRING_TYPE, "I watched as the ${'$'}{lamb}", STRING, VARIABLE, 0)

      // Set the interpolated string
      val interpolatedProperty = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(interpolatedProperty, STRING_TYPE, "Value2", STRING, REGULAR, 0)
      interpolatedProperty.setValue(iStr("opened the first of the ${'$'}{seven} seals"))
      verifyPropertyModel(interpolatedProperty, STRING_TYPE, "opened the first of the sêvĕn seals", STRING, REGULAR, 1)
    }

    applyChangesAndReparse(buildModel)

    // Check the properties after a reparse.
    run {
      val literalProperty = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(literalProperty, STRING_TYPE, "I watched as the ${'$'}{lamb}", STRING, VARIABLE, 0)

      val interpolatedProperty = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(interpolatedProperty, STRING_TYPE, "opened the first of the sêvĕn seals", STRING, REGULAR, 1)

      // Check the dependency is correct.
      val dependencyModel = interpolatedProperty.dependencies[0]
      verifyPropertyModel(dependencyModel, STRING_TYPE, "sêvĕn", STRING, REGULAR, 0)
    }
  }

  fun testSetGarbageReference() {
    val text = """
               ext {
                 prop1 = 'Then I heard one of the four living creatures say'
                 prop2 = prop1
               }""".trimIndent()
    writeToBuildFile(text)
    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel, STRING_TYPE, "prop1", REFERENCE, REGULAR, 1)

      propertyModel.setValue(ReferenceTo("in a voice like thunder"))
      // Note: Since this doesn't actually make any sense, the word "in" gets removed as it is a keyword in Groovy.
      verifyPropertyModel(propertyModel, STRING_TYPE, "a voice like thunder", REFERENCE, REGULAR, 0)
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel, STRING_TYPE, "a voice like thunder", UNKNOWN, REGULAR, 0)
    }
  }

  fun testSetReferenceWithModel() {
    val text = """
               ext {
                 prop1 = '“Come and see!” I looked'
                 prop2 = 'and there before me was a white horse!'
               }""".trimIndent()
    writeToBuildFile(text)
    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel, STRING_TYPE, "and there before me was a white horse!", STRING, REGULAR, 0)

      val otherModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(otherModel, STRING_TYPE, "“Come and see!” I looked", STRING, REGULAR, 0)

      // Set prop2 to refer to prop1
      propertyModel.setValue(ReferenceTo(otherModel))
      verifyPropertyModel(propertyModel, STRING_TYPE, "ext.prop1", REFERENCE, REGULAR, 1)
    }

    applyChangesAndReparse(buildModel)

    // Check the value
    run {
      val propertyModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel, STRING_TYPE, "ext.prop1", REFERENCE, REGULAR, 1)
    }
  }

  fun testQuotesWithinQuotes() {
    val text = """
               ext {
                 prop2 = "\"\"\"Hello \"\"\""
               }""".trimIndent()
    writeToBuildFile(text)
    val buildModel = gradleBuildModel

    // Check we read the string correctly
    run {
      val propertyModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel, STRING_TYPE, "\"\"\"Hello \"\"\"", STRING, REGULAR, 0)
    }

    // Check we can set strings with quotes
    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      propertyModel.setValue(iStr("\"Come and see!\" I looked"))
      verifyPropertyModel(propertyModel, STRING_TYPE, "\"Come and see!\" I looked", STRING, REGULAR, 0)
    }

    applyChangesAndReparse(buildModel)

    // Check its correct after a reparse.
    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(propertyModel, STRING_TYPE, "\"Come and see!\" I looked", STRING, REGULAR, 0)
    }
  }

  fun testSetReferenceValue() {
    val text = """
               ext {
                 prop1 = "Good"
                 prop2 = "Evil"
                 prop3 = prop2
               }""".trimIndent()
    writeToBuildFile(text)
    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop3")
      verifyPropertyModel(propertyModel, STRING_TYPE, "prop2", REFERENCE, REGULAR, 1)

      propertyModel.setValue(ReferenceTo("prop1"))
    }

    applyChangesAndReparse(buildModel)

    // Check the the reference has changed.
    run {
      val propertyModel = buildModel.ext().findProperty("prop3")
      verifyPropertyModel(propertyModel, STRING_TYPE, "prop1", REFERENCE, REGULAR, 1)
    }
  }

  fun testChangePropertyTypeToReference() {
    val text = """
               ext {
                 prop1 = "25"
                 prop2 = true
               }""".trimIndent()
    writeToBuildFile(text)
    val buildModel = gradleBuildModel

    run {
      // Check the unused property as well
      val unusedModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(unusedModel, STRING_TYPE, "25", STRING, REGULAR, 0)

      val propertyModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel, BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 0)

      // Set to a reference.

      propertyModel.setValue(ReferenceTo("prop1"))
      verifyPropertyModel(propertyModel, STRING_TYPE, "prop1", REFERENCE, REGULAR, 1)
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel, STRING_TYPE, "prop1", REFERENCE, REGULAR, 1)

      val unusedModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(unusedModel, STRING_TYPE, "25", STRING, REGULAR, 0)
    }
  }

  fun testChangePropertyTypeToLiteral() {
    val text = """
               ext {
                 prop1 = 25
                 prop2 = prop1
               }""".trimIndent()
    writeToBuildFile(text)
    val buildModel = gradleBuildModel

    run {
      // Check referred to value.
      val intModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(intModel, INTEGER_TYPE, 25, INTEGER, REGULAR, 0)

      // Check the reference
      val propertyModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel, STRING_TYPE, "prop1", REFERENCE, REGULAR, 1)

      // Set the value, and check again
      propertyModel.setValue(iStr("${'$'}{prop1}"))
      verifyPropertyModel(propertyModel, STRING_TYPE, "25", STRING, REGULAR, 1)
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel, STRING_TYPE, "25", STRING, REGULAR, 1)

      // Ensure the referred value is still correct.
      val intModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(intModel, INTEGER_TYPE, 25, INTEGER, REGULAR, 0)
    }
  }

  fun testDependencyChangedUpdatesValue() {
    val text = """
               ext {
                 prop1 = 'hello'
                 prop2 = "${'$'}{prop1} world!"
               }""".trimIndent()
    writeToBuildFile(text)
    val buildModel = gradleBuildModel

    run {
      // Check the properties are correct.
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(propertyModel, STRING_TYPE, "hello", STRING, REGULAR, 0)
      val propertyModel2 = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel2, STRING_TYPE, "hello world!", STRING, REGULAR, 1)
      assertEquals("${'$'}{prop1} world!", propertyModel2.getRawValue(STRING_TYPE))
    }

    run {
      // Ensure changing prop1 changes the value of prop2.
      val propertyModel = buildModel.ext().findProperty("prop1")
      val newValue = "goodbye"
      propertyModel.setValue(newValue)
      verifyPropertyModel(propertyModel, STRING_TYPE, newValue, STRING, REGULAR, 0)
      val propertyModel2 = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel2, STRING_TYPE, "goodbye world!", STRING, REGULAR, 1)
      // Check dependency is correct.
      verifyPropertyModel(propertyModel2.dependencies[0], STRING_TYPE, newValue, STRING, REGULAR, 0)

      // Apply, reparse and check again.
      applyChangesAndReparse(buildModel)

      val propertyModel3 = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(propertyModel3, STRING_TYPE, newValue, STRING, REGULAR, 0)
      val propertyModel4 = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel4, STRING_TYPE, "goodbye world!", STRING, REGULAR, 1)
      assertEquals("${'$'}{prop1} world!", propertyModel2.getRawValue(STRING_TYPE))
      // Check dependency is correct.
      verifyPropertyModel(propertyModel4.dependencies[0], STRING_TYPE, newValue, STRING, REGULAR, 0)
    }
  }

  fun testDependencyBasicCycle() {
    val text = """
               ext {
                 prop1 = "${'$'}{prop1}"
               }""".trimIndent()
    writeToBuildFile(text)
    val buildModel = gradleBuildModel
    val propertyModel = buildModel.ext().findProperty("prop1")
    verifyPropertyModel(propertyModel, STRING_TYPE, "${'$'}{prop1}", STRING, REGULAR, 1)
  }

  fun testDependencyBasicCycleReference() {
    val text = """
               ext {
                 prop1 = prop1
               }""".trimIndent()
    writeToBuildFile(text)
    val buildModel = gradleBuildModel
    val propertyModel = buildModel.ext().findProperty("prop1")
    verifyPropertyModel(propertyModel, STRING_TYPE, "prop1", REFERENCE, REGULAR, 1)
  }

  fun testDependencyNoCycle4Depth() {
    val text = """
               ext {
                 prop1 = "Value"
                 prop2 = "${'$'}{prop1}"
                 prop3 = "${'$'}{prop2}"
                 prop4 = "${'$'}{prop3}"
               }""".trimIndent()
    writeToBuildFile(text)
    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop4")
      verifyPropertyModel(propertyModel, STRING_TYPE, "Value", STRING, REGULAR, 1)
    }
  }

  fun testDependencyTwice() {
    val text = """
               ext {
                 prop1 = "Value"
                 prop2 = "${'$'}{prop1} + ${'$'}{prop1}"
               }""".trimIndent()
    writeToBuildFile(text)
    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel, STRING_TYPE, "Value + Value", STRING, REGULAR, 2)
    }
  }

  // This test currently fails due to bug: 71579307
  fun /*test*/DependencyNoCycle() {
    val text = """
               ext {
                 prop1 = "Value"
                 prop2 = "${'$'}{prop1}"
                 prop1 = "${'$'}{prop2}"
                 prop2 = "${'$'}{prop1}"
               }""".trimIndent()
    writeToBuildFile(text)
    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel, STRING_TYPE, "Value", STRING, REGULAR, 1)
    }

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(propertyModel, STRING_TYPE, "Value", STRING, REGULAR, 1)
    }
  }

  fun testDeleteProperty() {
    val text = """
               ext {
                 prop1 = "Value"
               }""".trimIndent()
    writeToBuildFile(text)
    val buildModel = gradleBuildModel

    // Delete the property
    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      propertyModel.delete()
    }

    applyChangesAndReparse(buildModel)

    // Check everything has been deleted
    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(NONE, propertyModel.valueType)
    }
  }

  fun testDeleteVariable() {
    val text = """
               ext {
                 def prop1 = "Value"
               }""".trimIndent()
    writeToBuildFile(text)
    val buildModel = gradleBuildModel

    // Delete the property
    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      propertyModel.delete()
    }

    applyChangesAndReparse(buildModel)

    // Check everything has been deleted
    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(NONE, propertyModel.valueType)
    }
  }

  fun testDeleteAndResetProperty() {
    val text = """
               ext {
                 prop1 = "Value"
                 prop2 = "Other Value"
               }""".trimIndent()
    writeToBuildFile(text)
    val buildModel = gradleBuildModel


    // Delete and reset the property
    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      propertyModel.delete()
      propertyModel.setValue("New Value")
    }

    // Check prop2 hasn't been affected
    run {
      val propertyModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel, STRING_TYPE, "Other Value", STRING, REGULAR, 0)
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(propertyModel, STRING_TYPE, "New Value", STRING, REGULAR, 0)
    }

    // Check prop2 is still correct
    run {
      val propertyModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel, STRING_TYPE, "Other Value", STRING, REGULAR, 0)
    }
  }

  fun testDeleteEmptyProperty() {
    val text = ""
    writeToBuildFile(text)
    val buildModel = gradleBuildModel

    // Delete a nonexistent property
    run {
      val propertyModel = buildModel.ext().findProperty("coolpropertyname")
      propertyModel.delete()
      verifyPropertyModel(propertyModel, OBJECT_TYPE, null, NONE, REGULAR, 0)
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("coolpropertyname")
      verifyPropertyModel(propertyModel, OBJECT_TYPE, null, NONE, REGULAR, 0)
    }
  }

  fun testDeleteVariableDependency() {
    val text = """
               ext {
                 def prop1 = "value"
                 prop2 = prop1
               }""".trimIndent()
    writeToBuildFile(text)
    val buildModel = gradleBuildModel

    // Get the model to delete
    run {
      val firstPropertyModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(firstPropertyModel, STRING_TYPE, "prop1", REFERENCE, REGULAR, 1)

      val secondPropertyModel = firstPropertyModel.dependencies[0]
      verifyPropertyModel(secondPropertyModel, STRING_TYPE, "value", STRING, VARIABLE, 0)
      // Delete the model
      secondPropertyModel.delete()

      // After deleting this property, the value of the first property shouldn't change since it is just a reference to nothing.
      // However it will no longer have a dependency.
      verifyPropertyModel(firstPropertyModel, STRING_TYPE, "prop1", REFERENCE, REGULAR, 0)
    }

    applyChangesAndReparse(buildModel)

    run {
      val firstPropertyModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(firstPropertyModel, STRING_TYPE, "prop1", REFERENCE, REGULAR, 0)
    }
  }

  fun testCheckSettingDeletedModel() {
    val text = """
               ext {
                 prop1 = "Value"
                 prop2 = "Other Value"
               }""".trimIndent()
    writeToBuildFile(text)
    val buildModel = gradleBuildModel

    // Delete the property and attempt to set it again.
    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      propertyModel.delete()
      propertyModel.setValue("New Value")

      verifyPropertyModel(propertyModel, STRING_TYPE, "New Value", STRING, REGULAR, 0)
    }

    applyChangesAndReparse(buildModel)

    // Check this is still the case after a reparse.
    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(propertyModel, STRING_TYPE, "New Value", STRING, REGULAR, 0)
    }

    // Check prop2
    run {
      val propertyModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel, STRING_TYPE, "Other Value", STRING, REGULAR, 0)
    }
  }

  fun testEmptyProperty() {
    val text = ""
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val extModel = buildModel.ext()

    val model = extModel.findProperty("prop")
    assertEquals(NONE, model.valueType)
    assertEquals(REGULAR, model.propertyType)
    assertEquals(null, model.getValue(STRING_TYPE))
    assertEquals(null, model.getValue(BOOLEAN_TYPE))
    assertEquals(null, model.getValue(INTEGER_TYPE))
    assertEquals(null, model.getValue(MAP_TYPE))
    assertEquals(null, model.getValue(LIST_TYPE))
    assertEquals("prop", model.name)
    assertEquals("ext.prop", model.fullyQualifiedName)
    assertEquals(buildModel.virtualFile, model.gradleFile)

    assertEquals(null, model.getRawValue(STRING_TYPE))
    assertSize(0, model.dependencies)
  }

  fun testDeletePropertyInList() {
    val text = """
               ext {
                 prop1 = [1, 2, 3, 4]
                 prop2 = "${'$'}{prop1[0]}"
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val extModel = buildModel.ext()

    // Values that should be obtained from the build file.
    val one = 1
    val two = 2

    run {
      val propertyModel = extModel.findProperty("prop1")
      verifyListProperty(propertyModel, listOf<Any>(1, 2, 3, 4), REGULAR, 0)
    }

    run {
      val propertyModel = extModel.findProperty("prop2")
      verifyPropertyModel(propertyModel, STRING_TYPE, one.toString(), STRING, REGULAR, 1, "prop2", "ext.prop2")
      // Check the dependency
      val dependencyModel = propertyModel.dependencies[0]
      verifyPropertyModel(dependencyModel, INTEGER_TYPE, one, INTEGER, DERIVED, 0 /*, "0", "ext.prop1.0" TODO: FIX THIS */)
      // Delete this property.
      dependencyModel.delete()
    }

    applyChangesAndReparse(buildModel)

    // Check that the value of prop2 has changed
    run {
      val propertyModel = gradleBuildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel, STRING_TYPE, two.toString(), STRING, REGULAR, 1, "prop2", "ext.prop2")
      val dependencyModel = propertyModel.dependencies[0]
      verifyPropertyModel(dependencyModel, INTEGER_TYPE, two, INTEGER, DERIVED, 0 /*, "0", "ext.prop1.0" TODO: FIX THIS */)
    }
  }

  fun testCreateNewEmptyMapValue() {
    val text = ""
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(propertyModel, OBJECT_TYPE, null, NONE, REGULAR, 0)

      // Make it an empty map.
      propertyModel.convertToEmptyMap()
      assertEquals(MAP, propertyModel.valueType)
      assertSize(0, propertyModel.getValue(MAP_TYPE)!!.entries)
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      assertSize(0, propertyModel.getValue(MAP_TYPE)!!.entries)
    }
  }

  fun testAddMapValueToString() {
    val text = """
               ext {
                 prop1 = "hello"
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      try {
        propertyModel.getMapValue("key")
        fail("Exception should have been thrown!")
      }
      catch (e: IllegalStateException) {
        // Expected.
      }
    }
  }

  fun testSetNewValueInMap() {
    val text = """
               ext {
                 prop1 = ['key1' : 'value1', 'key2' : 'value2']
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)

      run {
        val map = propertyModel.getValue(MAP_TYPE)!!
        assertSize(2, map.entries)
        verifyPropertyModel(map["key1"], STRING_TYPE, "value1", STRING, DERIVED, 0)
        verifyPropertyModel(map["key2"], STRING_TYPE, "value2", STRING, DERIVED, 0)

        // Attempt to set a new value.
        val newValue = propertyModel.getMapValue("key3")
        verifyPropertyModel(newValue, OBJECT_TYPE, null, NONE, DERIVED, 0)
        newValue.setValue(true)
        verifyPropertyModel(newValue, BOOLEAN_TYPE, true, BOOLEAN, DERIVED, 0)
      }

      run {
        // Check map now has three values.
        val map = propertyModel.getValue(MAP_TYPE)!!
        assertSize(3, map.entries)
        verifyPropertyModel(map["key3"], BOOLEAN_TYPE, true, BOOLEAN, DERIVED, 0)
        verifyPropertyModel(map["key1"], STRING_TYPE, "value1", STRING, DERIVED, 0)
        verifyPropertyModel(map["key2"], STRING_TYPE, "value2", STRING, DERIVED, 0)
      }
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      val map = propertyModel.getValue(MAP_TYPE)!!
      assertSize(3, map.entries)
      verifyPropertyModel(map["key3"], BOOLEAN_TYPE, true, BOOLEAN, DERIVED, 0)
      verifyPropertyModel(map["key1"], STRING_TYPE, "value1", STRING, DERIVED, 0)
      verifyPropertyModel(map["key2"], STRING_TYPE, "value2", STRING, DERIVED, 0)
    }
  }

  fun testSetNewValueInEmptyMap() {
    val text = """
               ext {
                 def val = "value"
                 prop1 = [:]
               }""".trimIndent()
    writeToBuildFile(text)
    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)

      // Check every thing is in order
      run {
        val map = propertyModel.getValue(MAP_TYPE)!!
        assertSize(0, map.entries)
      }

      // Set the new value.
      propertyModel.getMapValue("key1").setValue(ReferenceTo("val"))

      // Check the correct values are shown in the property.
      run {
        val map = propertyModel.getValue(MAP_TYPE)!!
        assertSize(1, map.entries)
        verifyPropertyModel(map["key1"], STRING_TYPE, "val", REFERENCE, DERIVED, 1)
        verifyPropertyModel(map["key1"]!!.dependencies[0], STRING_TYPE, "value", STRING, VARIABLE, 0)
      }
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      val map = propertyModel.getValue(MAP_TYPE)!!
      assertSize(1, map.entries)
      verifyPropertyModel(map["key1"], STRING_TYPE, "val", REFERENCE, DERIVED, 1)
      verifyPropertyModel(map["key1"]!!.dependencies[0], STRING_TYPE, "value", STRING, VARIABLE, 0)
    }
  }

  fun testDeletePropertyInMap() {
    val text = """
               ext {
                 prop1 = ['key1' : 'value1', 'key2' : 'value2']
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      run {
        val map = propertyModel.getValue(MAP_TYPE)!!
        assertSize(2, map.entries)
        verifyPropertyModel(map["key1"], STRING_TYPE, "value1", STRING, DERIVED, 0)
        verifyPropertyModel(map["key2"], STRING_TYPE, "value2", STRING, DERIVED, 0)
        map["key1"]?.delete()
      }

      run {
        val map = propertyModel.getValue(MAP_TYPE)!!
        assertSize(1, map.entries)
        verifyPropertyModel(map["key2"], STRING_TYPE, "value2", STRING, DERIVED, 0)
      }
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      val map = propertyModel.getValue(MAP_TYPE)!!
      assertSize(1, map.entries)
      verifyPropertyModel(map["key2"], STRING_TYPE, "value2", STRING, DERIVED, 0)
    }
  }

  fun testDeleteMapItemToAndSetFromEmpty() {
    val text = """
               ext {
                 prop1 = ['key1' : 25]
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    // Delete the item in the map.
    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      val map = propertyModel.getValue(MAP_TYPE)!!
      assertSize(1, map.entries)
      verifyPropertyModel(map["key1"], INTEGER_TYPE, 25, INTEGER, DERIVED, 0)

      map["key1"]?.delete()

      val newMap = propertyModel.getValue(MAP_TYPE)!!
      assertSize(0, newMap.entries)
    }

    applyChangesAndReparse(buildModel)

    // Check that a reparse still has a missing model
    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      assertSize(0, propertyModel.getValue(MAP_TYPE)!!.entries)

      // Attempt to set a new value
      propertyModel.getMapValue("Conquest").setValue("Famine")
      // Check the model again
      assertEquals(MAP, propertyModel.valueType)
      val map = propertyModel.getValue(MAP_TYPE)!!
      verifyPropertyModel(map["Conquest"], STRING_TYPE, "Famine", STRING, DERIVED, 0)
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      val map = propertyModel.getValue(MAP_TYPE)!!
      verifyPropertyModel(map["Conquest"], STRING_TYPE, "Famine", STRING, DERIVED, 0)
    }
  }

  fun testSetMapValueToLiteral() {
    val text = """
               ext {
                 prop1 = ['key1' : 25]
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      val map = propertyModel.getValue(MAP_TYPE)!!
      assertSize(1, map.entries)

      // Set the property to a new value.
      propertyModel.setValue(77)
      verifyPropertyModel(propertyModel, INTEGER_TYPE, 77, INTEGER, REGULAR, 0)
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(propertyModel, INTEGER_TYPE, 77, INTEGER, REGULAR, 0)
    }
  }

  fun testDeleteToEmptyMap() {
    val text = """
               ext {
                 prop1 = [key : "value", key1 :32, key2: true]
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      assertSize(3, propertyModel.getValue(MAP_TYPE)!!.entries)

      val map = propertyModel.getValue(MAP_TYPE)!!
      map["key"]!!.delete()
      map["key1"]!!.delete()
      map["key2"]!!.delete()

      assertEquals(MAP, propertyModel.valueType)
      assertSize(0, propertyModel.getValue(MAP_TYPE)!!.entries)
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      assertSize(0, propertyModel.getValue(MAP_TYPE)!!.entries)
    }
  }

  fun testAddExistingMapProperty() {
    val text = """
               ext {
                 prop = [key: 'val']
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop")
      verifyMapProperty(propertyModel, ImmutableMap.of("key", "val") as Map<String, Any>)

      propertyModel.getMapValue("key").setValue("newVal")
      verifyMapProperty(propertyModel, ImmutableMap.of("key", "newVal") as Map<String, Any>)
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop")
      verifyMapProperty(propertyModel, ImmutableMap.of("key", "newVal") as Map<String, Any>)
    }
  }

  fun testDeleteMapProperty() {
    val text = """
               ext {
                 prop1 = ['key':"value"]
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      assertSize(1, propertyModel.getValue(MAP_TYPE)!!.entries)

      // Delete the map
      propertyModel.delete()
      verifyPropertyModel(propertyModel, OBJECT_TYPE, null, NONE, REGULAR, 0)
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(propertyModel, OBJECT_TYPE, null, NONE, REGULAR, 0)
    }
  }

  fun testDeleteMapVariable() {
    val text = """
               ext {
                 def map = [key : "value"]
                 prop = map
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop")
      verifyPropertyModel(propertyModel, STRING_TYPE, "map", REFERENCE, REGULAR, 1)

      val mapModel = propertyModel.dependencies[0]!!
      assertEquals(MAP, mapModel.valueType)
      assertEquals(VARIABLE, mapModel.propertyType)
      assertSize(1, mapModel.getValue(MAP_TYPE)!!.entries)

      // Delete the map model.
      mapModel.delete()
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop")
      verifyPropertyModel(propertyModel, STRING_TYPE, "map", REFERENCE, REGULAR, 0)
    }
  }

  fun testDeleteEmptyMap() {
    val text = """
               ext {
                 prop = [:]
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop")
      assertEquals(MAP, propertyModel.valueType)
      assertSize(0, propertyModel.getValue(MAP_TYPE)!!.entries)

      propertyModel.delete()
      verifyPropertyModel(propertyModel, OBJECT_TYPE, null, NONE, REGULAR, 0)
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop")
      verifyPropertyModel(propertyModel, OBJECT_TYPE, null, NONE, REGULAR, 0)
    }
  }

  // Test currently fails due to b/72144740
  fun /*test*/SetLiteralToMapValue() {
    val text = """
               ext {
                 def val = 'value'
                 prop1 = val
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(propertyModel, STRING_TYPE, "val", REFERENCE, REGULAR, 1)
      val deps = propertyModel.dependencies
      assertSize(1, deps)
      val mapPropertyModel = deps[0]
      // Check it is not a map yet
      verifyPropertyModel(mapPropertyModel, STRING_TYPE, "value", STRING, VARIABLE, 0)

      mapPropertyModel.getMapValue("key").setValue("Hello")

      assertEquals(MAP, mapPropertyModel.valueType)
      val map = mapPropertyModel.getValue(MAP_TYPE)!!
      assertSize(1, map.entries)
      verifyPropertyModel(map["key"], STRING_TYPE, "Hello", STRING, DERIVED, 0)
    }

    applyChangesAndReparse(buildModel)

    /* This is what is written to file, it is very wrong, the property are the wrong way round.
       This would not work in Gradle.
            ext {
                prop1 = val
                def val = [key: 'Hello']
            }
    */
    fail()

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(propertyModel, STRING_TYPE, "val", REFERENCE, REGULAR, 1)
      val deps = propertyModel.dependencies
      assertSize(1, deps)
      val mapPropertyModel = deps[0]
      assertEquals(MAP, mapPropertyModel.valueType)
      val map = mapPropertyModel.getValue(MAP_TYPE)!!
      assertSize(1, map.entries)
      verifyPropertyModel(map["key"], STRING_TYPE, "Hello", STRING, DERIVED, 0)
    }
  }

  fun testParseMapInMap() {
    val text = """
               ext {
                 prop1 = [key1 : 25, key2 : [key: "value"]]
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      val map = propertyModel.getValue(MAP_TYPE)!!
      assertSize(2, map.entries)
      verifyPropertyModel(map["key1"], INTEGER_TYPE, 25, INTEGER, DERIVED, 0)

      val mapPropertyModel = map["key2"]!!
      assertEquals(MAP, mapPropertyModel.valueType)
      val innerMap = mapPropertyModel.getValue(MAP_TYPE)!!
      assertSize(1, innerMap.entries)
      verifyPropertyModel(innerMap["key"], STRING_TYPE, "value", STRING, DERIVED, 0)
    }
  }

  fun testMapsInMap() {
    val text = """
               ext {
                 def var = "hellO"
                 prop1 = [key1 : [key2 : 'value'], key3 : [key4: 'value2', key5: 43], key6 : [key7: 'value3']]
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      val map = propertyModel.getValue(MAP_TYPE)!!
      assertSize(3, map.entries)

      val firstInnerMapModel = map["key1"]!!
      assertEquals(MAP, firstInnerMapModel.valueType)
      val firstInnerMap = firstInnerMapModel.getValue(MAP_TYPE)!!
      assertSize(1, firstInnerMap.entries)
      // Delete the first inner map
      firstInnerMapModel.delete()

      // Check is has been deleted.
      verifyPropertyModel(firstInnerMapModel, OBJECT_TYPE, null, NONE, DERIVED, 0)

      val secondInnerMapModel = map["key3"]!!
      assertEquals(MAP, secondInnerMapModel.valueType)
      val secondInnerMap = secondInnerMapModel.getValue(MAP_TYPE)!!
      assertSize(2, secondInnerMap.entries)
      verifyPropertyModel(secondInnerMap["key4"], STRING_TYPE, "value2", STRING, DERIVED, 0)
      verifyPropertyModel(secondInnerMap["key5"], INTEGER_TYPE, 43, INTEGER, DERIVED, 0)
      // Delete one of these values, and change the other.
      secondInnerMap["key4"]!!.setValue(ReferenceTo("var"))
      secondInnerMap["key5"]!!.delete()

      // Check the values are correct.
      verifyPropertyModel(secondInnerMap["key4"], STRING_TYPE, "var", REFERENCE, DERIVED, 1)
      verifyPropertyModel(secondInnerMap["key5"], OBJECT_TYPE, null, NONE, DERIVED, 0)

      val thirdInnerMapModel = map["key6"]!!
      assertEquals(MAP, thirdInnerMapModel.valueType)
      val thirdInnerMap = thirdInnerMapModel.getValue(MAP_TYPE)!!
      assertSize(1, thirdInnerMap.entries)
      verifyPropertyModel(thirdInnerMap["key7"], STRING_TYPE, "value3", STRING, DERIVED, 0)

      // Set this third map model to be another basic value.
      thirdInnerMapModel.setValue(77)

      // Check it has been deleted.
      verifyPropertyModel(thirdInnerMapModel, INTEGER_TYPE, 77, INTEGER, DERIVED, 0)
    }

    applyChangesAndReparse(buildModel)

    // Check everything is in order after a reparse.
    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      val map = propertyModel.getValue(MAP_TYPE)!!
      assertSize(2, map.entries)

      val firstInnerMapModel = map["key1"]
      assertNull(firstInnerMapModel)

      val secondInnerMapModel = map["key3"]!!
      assertEquals(MAP, secondInnerMapModel.valueType)
      val secondInnerMap = secondInnerMapModel.getValue(MAP_TYPE)!!
      assertSize(1, secondInnerMap.entries)
      verifyPropertyModel(secondInnerMap["key4"], STRING_TYPE, "var", REFERENCE, DERIVED, 1)
      assertNull(secondInnerMap["key5"])

      val thirdInnerMapModel = map["key6"]!!
      verifyPropertyModel(thirdInnerMapModel, INTEGER_TYPE, 77, INTEGER, DERIVED, 0)
    }
  }

  fun testSetMapInMap() {
    val text = """
               ext {
                 prop1 = ['key1' : 25]
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      val map = propertyModel.getValue(MAP_TYPE)!!
      assertSize(1, map.entries)
      // Try to set a new map value.
      propertyModel.getValue(MAP_TYPE)!!["key1"]!!.convertToEmptyMap().getMapValue("War")?.setValue("Death")
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      val map = propertyModel.getValue(MAP_TYPE)!!
      assertSize(1, map.entries)
      val innerProperty = map["key1"]!!
      assertEquals(MAP, innerProperty.valueType)
      val innerMap = innerProperty.getValue(MAP_TYPE)!!
      assertSize(1, innerMap.entries)
      verifyPropertyModel(innerMap["War"], STRING_TYPE, "Death", STRING, DERIVED, 0)
    }
  }

  fun testCreateNewEmptyList() {
    val text = ""
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(propertyModel, OBJECT_TYPE, null, NONE, REGULAR, 0)

      propertyModel.convertToEmptyList()
      assertEquals(LIST, propertyModel.valueType)
      val list = propertyModel.getValue(LIST_TYPE)
      assertSize(0, list)
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(LIST, propertyModel.valueType)
      val list = propertyModel.getValue(LIST_TYPE)
      assertSize(0, list)
    }
  }

  fun testConvertToEmptyList() {
    val text = """
               ext {
                 prop1 = 25
                 prop2 = prop1
                 prop3 = [key:'value', key1:'value']
               }
               """.trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val firstModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(firstModel, INTEGER_TYPE, 25, INTEGER, REGULAR, 0)
      firstModel.convertToEmptyList()
      assertEquals(LIST, firstModel.valueType)
      val firstList = firstModel.getValue(LIST_TYPE)
      assertSize(0, firstList)

      val secondModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(secondModel, STRING_TYPE, "prop1", REFERENCE, REGULAR, 1)
      secondModel.convertToEmptyList()
      assertEquals(LIST, secondModel.valueType)
      val secondList = secondModel.getValue(LIST_TYPE)
      assertSize(0, secondList)

      val thirdModel = buildModel.ext().findProperty("prop3")
      thirdModel.convertToEmptyList()
      assertEquals(LIST, thirdModel.valueType)
      val thirdList = thirdModel.getValue(LIST_TYPE)
      assertSize(0, thirdList)
    }

    applyChangesAndReparse(buildModel)

    run {
      val firstModel = buildModel.ext().findProperty("prop1")
      assertEquals(LIST, firstModel.valueType)
      val firstList = firstModel.getValue(LIST_TYPE)
      assertSize(0, firstList)

      val secondModel = buildModel.ext().findProperty("prop2")
      assertEquals(LIST, secondModel.valueType)
      val secondList = secondModel.getValue(LIST_TYPE)
      assertSize(0, secondList)

      val thirdModel = buildModel.ext().findProperty("prop3")
      assertEquals(LIST, thirdModel.valueType)
      val thirdList = thirdModel.getValue(LIST_TYPE)
      assertSize(0, thirdList)
    }
  }

  fun testAddToNoneList() {
    val text = """
               ext {
                 prop1 = true
               }
               """.trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(propertyModel, BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 0)

      try {
        propertyModel.addListValue().setValue("True")
        fail()
      }
      catch (e: IllegalStateException) {
        // Expected
      }

      try {
        propertyModel.addListValueAt(23).setValue(72)
        fail()
      }
      catch (e: IllegalStateException) {
        // Expected
      }
    }
  }

  fun testAddOutOfBounds() {
    val text = """
               ext {
                 prop1 = [1, 2, 3, 4, 5, 6, "hello"]
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyListProperty(propertyModel, listOf(1, 2, 3, 4, 5, 6, "hello"), REGULAR, 0)

      try {
        propertyModel.addListValueAt(82).setValue(true)
        fail()
      }
      catch (e: IndexOutOfBoundsException) {
        // Expected
      }
    }
  }

  fun testSetListInMap() {
    val text = """
               ext {
                 prop1 = [key: 'val', key1: 'val', key2: 'val']
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      val map = propertyModel.getValue(MAP_TYPE)!!
      assertSize(3, map.entries)
      verifyPropertyModel(map["key"], STRING_TYPE, "val", STRING, DERIVED, 0)
      verifyPropertyModel(map["key1"], STRING_TYPE, "val", STRING, DERIVED, 0)
      verifyPropertyModel(map["key2"], STRING_TYPE, "val", STRING, DERIVED, 0)
      map["key1"]!!.convertToEmptyList().addListValue().setValue(true)
      verifyListProperty(map["key1"], listOf(true), DERIVED, 0)
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      val map = propertyModel.getValue(MAP_TYPE)!!
      assertSize(3, map.entries)
      verifyPropertyModel(map["key"], STRING_TYPE, "val", STRING, DERIVED, 0)
      verifyListProperty(map["key1"], listOf(true), DERIVED, 0)
      verifyPropertyModel(map["key2"], STRING_TYPE, "val", STRING, DERIVED, 0)
    }
  }

  fun testSetToListValues() {
    val text = """
               ext {
                 def var = "hello"
                 prop1 = 5
                 prop2 = var
                 prop3 = "${'$'}{prop2}"
                 prop4 = [key: 'val', key1: true]
                 prop5 = ['val']
                 prop6 = [key: 'val']
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val firstModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(firstModel, INTEGER_TYPE, 5, INTEGER, REGULAR, 0)
      firstModel.convertToEmptyList().addListValue().setValue("5")
      verifyListProperty(firstModel, listOf("5"), REGULAR, 0)

      val secondModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(secondModel, STRING_TYPE, "var", REFERENCE, REGULAR, 1)
      val varModel = secondModel.dependencies[0]!!
      verifyPropertyModel(varModel, STRING_TYPE, "hello", STRING, VARIABLE, 0)
      varModel.convertToEmptyList().addListValue().setValue("goodbye")
      secondModel.setValue(ReferenceTo("var[0]"))
      verifyPropertyModel(secondModel, STRING_TYPE, "var[0]", REFERENCE, REGULAR, 1)
      val depModel = secondModel.dependencies[0]!!
      verifyPropertyModel(depModel, STRING_TYPE, "goodbye", STRING, DERIVED, 0)

      val thirdModel = buildModel.ext().findProperty("prop3")
      verifyPropertyModel(thirdModel, STRING_TYPE, "goodbye", STRING, REGULAR, 1)
      thirdModel.convertToEmptyList().addListValue().setValue(ReferenceTo("prop2"))
      assertEquals(LIST, thirdModel.valueType)
      val thirdList = thirdModel.getValue(LIST_TYPE)!!
      assertSize(1, thirdList)
      verifyPropertyModel(thirdList[0], STRING_TYPE, "prop2", REFERENCE, DERIVED, 1)

      val fourthModel = buildModel.ext().findProperty("prop4")
      assertEquals(MAP, fourthModel.valueType)
      val map = fourthModel.getValue(MAP_TYPE)!!
      assertSize(2, map.entries)
      verifyPropertyModel(map["key"], STRING_TYPE, "val", STRING, DERIVED, 0)
      verifyPropertyModel(map["key1"], BOOLEAN_TYPE, true, BOOLEAN, DERIVED, 0)
      map["key"]!!.convertToEmptyList().addListValue().setValue("we are in")
      verifyListProperty(map["key"], listOf("we are in"), DERIVED, 0)

      val fifthModel = buildModel.ext().findProperty("prop5")
      verifyListProperty(fifthModel, listOf("val"), REGULAR, 0)
      fifthModel.convertToEmptyList().addListValue().setValue("good")
      verifyListProperty(fifthModel, listOf("good"), REGULAR, 0)

      val sixthModel = buildModel.ext().findProperty("prop6")
      assertEquals(MAP, sixthModel.valueType)
      sixthModel.convertToEmptyList().addListValue().setValue(true)
      verifyListProperty(sixthModel, listOf(true), REGULAR, 0)
    }

    applyChangesAndReparse(buildModel)

    run {
      val firstModel = buildModel.ext().findProperty("prop1")
      verifyListProperty(firstModel, listOf("5"), REGULAR, 0)

      // TODO: Order of statements is wrong so this model does not get correctly parsed.
      /*val secondModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(secondModel, STRING_TYPE, "var[0]", REFERENCE, REGULAR, 1)
      val depModel = secondModel.dependencies[0]!!
      verifyPropertyModel(depModel, STRING_TYPE, "goodbye", STRING, DERIVED, 0)*/

      val thirdModel = buildModel.ext().findProperty("prop3")
      assertEquals(LIST, thirdModel.valueType)
      val thirdList = thirdModel.getValue(LIST_TYPE)!!
      assertSize(1, thirdList)
      verifyPropertyModel(thirdList[0], STRING_TYPE, "prop2", REFERENCE, DERIVED, 1)

      val fourthModel = buildModel.ext().findProperty("prop4")
      assertEquals(MAP, fourthModel.valueType)
      val map = fourthModel.getValue(MAP_TYPE)!!
      verifyListProperty(map["key"], listOf("we are in"), DERIVED, 0)
      verifyPropertyModel(map["key1"], BOOLEAN_TYPE, true, BOOLEAN, DERIVED, 0)

      val fifthModel = buildModel.ext().findProperty("prop5")
      verifyListProperty(fifthModel, listOf("good"), REGULAR, 0)

      val sixthModel = buildModel.ext().findProperty("prop6")
      verifyListProperty(sixthModel, listOf(true), REGULAR, 0)
    }
  }

  fun testAddSingleElementToEmpty() {
    val text = """
               ext {
                 prop1 = true
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(propertyModel, BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 0)
      propertyModel.convertToEmptyList().addListValue().setValue("Good")

      verifyListProperty(propertyModel, listOf("Good"), REGULAR, 0)
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyListProperty(propertyModel, listOf("Good"), REGULAR, 0)
    }
  }

  fun testAddToAndDeleteListFromEmpty() {
    val text = """
               ext {
                 def six = 6
                 prop1 = []
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(LIST, propertyModel.valueType)
      verifyListProperty(propertyModel, listOf(), REGULAR, 0)

      propertyModel.addListValue().setValue("3")
      propertyModel.addListValue().setValue("4")
      propertyModel.addListValueAt(0).setValue("1")
      propertyModel.addListValueAt(1).setValue("2")
      propertyModel.addListValueAt(4).setValue(5)
      propertyModel.addListValueAt(5).setValue(ReferenceTo("six"))

      val list = propertyModel.getValue(LIST_TYPE)!!
      assertSize(6, list)
      verifyPropertyModel(list[0], STRING_TYPE, "1", STRING, DERIVED, 0, "0", "ext.prop1[0]")
      verifyPropertyModel(list[1], STRING_TYPE, "2", STRING, DERIVED, 0, "1", "ext.prop1[1]")
      verifyPropertyModel(list[2], STRING_TYPE, "3", STRING, DERIVED, 0, "2", "ext.prop1[2]")
      verifyPropertyModel(list[3], STRING_TYPE, "4", STRING, DERIVED, 0, "3", "ext.prop1[3]")
      verifyPropertyModel(list[4], INTEGER_TYPE, 5, INTEGER, DERIVED, 0, "4", "ext.prop1[4]")
      verifyPropertyModel(list[5], STRING_TYPE, "six", REFERENCE, DERIVED, 1, "5", "ext.prop1[5]")
      verifyPropertyModel(list[5].dependencies[0], INTEGER_TYPE, 6, INTEGER, VARIABLE, 0, "six", "ext.six")

      // Delete some elements
      list[1].delete()
      list[3].delete()
      list[5].delete()

      val newList = propertyModel.getValue(LIST_TYPE)!!
      assertSize(3, newList)
      verifyPropertyModel(newList[0], STRING_TYPE, "1", STRING, DERIVED, 0, "0", "ext.prop1[0]")
      verifyPropertyModel(newList[1], STRING_TYPE, "3", STRING, DERIVED, 0, "1", "ext.prop1[1]")
      verifyPropertyModel(newList[2], INTEGER_TYPE, 5, INTEGER, DERIVED, 0, "2", "ext.prop1[2]")
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(LIST, propertyModel.valueType)
      val newList = propertyModel.getValue(LIST_TYPE)!!
      assertSize(3, newList)
      verifyPropertyModel(newList[0], STRING_TYPE, "1", STRING, DERIVED, 0, "0", "ext.prop1[0]")
      verifyPropertyModel(newList[1], STRING_TYPE, "3", STRING, DERIVED, 0, "1", "ext.prop1[1]")
      verifyPropertyModel(newList[2], INTEGER_TYPE, 5, INTEGER, DERIVED, 0, "2", "ext.prop1[2]")
    }
  }

  fun testAddAndRemoveFromNonLiteralList() {
    val text = """
               android {
                 defaultConfig {
                   proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules2.txt'
                 }
               }
               """.trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val proguardFiles = buildModel.android()?.defaultConfig()?.proguardFiles()!!
      verifyListProperty(proguardFiles, listOf("getDefaultProguardFile('proguard-android.txt')", "proguard-rules2.txt"), DERIVED, 0)
      proguardFiles.addListValueAt(0).setValue("z.txt")
      proguardFiles.addListValueAt(2).setValue("proguard-rules.txt")
      verifyListProperty(proguardFiles, listOf("z.txt", "getDefaultProguardFile('proguard-android.txt')", "proguard-rules.txt", "proguard-rules2.txt"), DERIVED, 0)
    }

    applyChangesAndReparse(buildModel)

    run {
      val proguardFiles = buildModel.android()?.defaultConfig()?.proguardFiles()!!
      verifyListProperty(proguardFiles, listOf("z.txt", "getDefaultProguardFile('proguard-android.txt')", "proguard-rules.txt", "proguard-rules2.txt"), DERIVED, 0)
    }
  }

  fun testSetList() {
    val text = """
               ext {
                 prop1 = [1, 2, 3]
                 prop2 = ["hellO"]
                 prop3 = [54]
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyListProperty(propertyModel, listOf(1, 2, 3), REGULAR, 0)
      // Set middle value
      propertyModel.getValue(LIST_TYPE)!![1].setValue(true)
      verifyListProperty(propertyModel, listOf(1, true, 3), REGULAR, 0)

      val secondModel = buildModel.ext().findProperty("prop2")
      verifyListProperty(secondModel, listOf("hellO"), REGULAR, 0)
      secondModel.setValue(77)
      verifyPropertyModel(secondModel, INTEGER_TYPE, 77, INTEGER, REGULAR, 0)

      val thirdModel = buildModel.ext().findProperty("prop3")
      verifyListProperty(thirdModel, listOf(54), REGULAR, 0)
      thirdModel.setValue(ReferenceTo("prop1[1]"))
      verifyPropertyModel(thirdModel, STRING_TYPE, "prop1[1]", REFERENCE, REGULAR, 1)
      verifyPropertyModel(thirdModel.dependencies[0], BOOLEAN_TYPE, true, BOOLEAN, DERIVED, 0)
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyListProperty(propertyModel, listOf(1, true, 3), REGULAR, 0)

      val secondModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(secondModel, INTEGER_TYPE, 77, INTEGER, REGULAR, 0)

      // TODO: This is not currently parsed.
      val thirdModel = buildModel.ext().findProperty("prop3")
      verifyPropertyModel(thirdModel, STRING_TYPE, "prop1[1]", REFERENCE, REGULAR, 1)
      verifyPropertyModel(thirdModel.dependencies[0], BOOLEAN_TYPE, true, BOOLEAN, DERIVED, 0)
    }
  }

  fun testAddMiddleOfList() {
    val text = """
               ext {
                 def var = "2"
                 prop1 = [1, 4]
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyListProperty(propertyModel, listOf(1, 4), REGULAR, 0)

      propertyModel.addListValueAt(1).setValue(ReferenceTo("var"))
      propertyModel.addListValueAt(2).setValue(3)

      verifyListProperty(propertyModel, listOf(1, "2", 3, 4), REGULAR, 1)
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyListProperty(propertyModel, listOf(1, "2", 3, 4), REGULAR, 1)
    }
  }

  fun testSetInMiddleOfList() {
    val text = """
               ext {
                 def var = "2"
                 prop1 = [1, 2, var, 4]
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyListProperty(propertyModel, listOf(1, 2, "2", 4), REGULAR, 1)

      propertyModel.getValue(LIST_TYPE)!![1].setValue(ReferenceTo("var"))
      propertyModel.getValue(LIST_TYPE)!![2].setValue(3)

      verifyListProperty(propertyModel, listOf(1, "2", 3, 4), REGULAR, 1)
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyListProperty(propertyModel, listOf(1, "2", 3, 4), REGULAR, 1)
    }
  }

  fun testResolveAndSetVariablesInParentModule() {
    val parentText = """
                     ext {
                       greeting = "hello"
                     }""".trimIndent()

    val childText = """
                    ext {
                      prop1 = greeting
                      prop2 = "${'$'}{greeting} world!"
                    }""".trimIndent()
    writeToBuildFile(parentText)
    writeToSubModuleBuildFile(childText)
    writeToSettingsFile("include ':${SUB_MODULE_NAME}'")

    val buildModel = subModuleGradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      propertyModel.getValue(STRING_TYPE)
      verifyPropertyModel(propertyModel, STRING_TYPE, "greeting", REFERENCE, REGULAR, 1)
      verifyPropertyModel(propertyModel.dependencies[0], STRING_TYPE, "hello", STRING, REGULAR, 0)
      val otherModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(otherModel, STRING_TYPE, "hello world!", STRING, REGULAR, 1)


      propertyModel.dependencies[0].setValue("howdy")

      verifyPropertyModel(propertyModel, STRING_TYPE, "greeting", REFERENCE, REGULAR, 1)
      verifyPropertyModel(propertyModel.dependencies[0], STRING_TYPE, "howdy", STRING, REGULAR, 0)
      verifyPropertyModel(otherModel, STRING_TYPE, "howdy world!", STRING, REGULAR, 1)
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      val otherModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel, STRING_TYPE, "greeting", REFERENCE, REGULAR, 1)
      verifyPropertyModel(propertyModel.dependencies[0], STRING_TYPE, "howdy", STRING, REGULAR, 0)
      verifyPropertyModel(otherModel, STRING_TYPE, "howdy world!", STRING, REGULAR, 1)
    }
  }

  fun testResolveVariablesInPropertiesFile() {
    val parentText = """
                     ext {
                       animal = "penguin"
                     }""".trimIndent()

    val childText = """
                    ext {
                      def animal = "rhino"
                      prop = "hello, ${'$'}{animal}!"
                    }""".trimIndent()
    val childProperties = "animal = lion"
    val parentProperties = "animal = meerkat"
    writeToBuildFile(parentText)
    writeToSubModuleBuildFile(childText)
    writeToSettingsFile("include ':${SUB_MODULE_NAME}'")
    writeToPropertiesFile(parentProperties)
    writeToSubModulePropertiesFile(childProperties)

    var buildModel = subModuleGradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop")
      verifyPropertyModel(propertyModel, STRING_TYPE, "hello, rhino!", STRING, REGULAR, 1)
      verifyPropertyModel(propertyModel.dependencies[0], STRING_TYPE, "rhino", STRING, VARIABLE, 0)

      // Delete the dependency and try resolution again.
      propertyModel.dependencies[0].delete()

      verifyPropertyModel(propertyModel, STRING_TYPE, "hello, lion!", STRING, REGULAR, 1)
      verifyPropertyModel(propertyModel.dependencies[0], STRING_TYPE, "lion", STRING, PROPERTIES_FILE, 0)
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop")
      verifyPropertyModel(propertyModel, STRING_TYPE, "hello, lion!", STRING, REGULAR, 1)
      verifyPropertyModel(propertyModel.dependencies[0], STRING_TYPE, "lion", STRING, PROPERTIES_FILE, 0)

      // Properties file can't be edited directed.
      writeToSubModulePropertiesFile("")
      // Applying changes and reparsing does not affect properties files, need to completely remake the build model.
      buildModel = subModuleGradleBuildModel
    }

    run {
      val propertyModel = buildModel.ext().findProperty("prop")
      verifyPropertyModel(propertyModel, STRING_TYPE, "hello, meerkat!", STRING, REGULAR, 1)
      verifyPropertyModel(propertyModel.dependencies[0], STRING_TYPE, "meerkat", STRING, PROPERTIES_FILE, 0)

      // Properties file can't be edited directed.
      writeToPropertiesFile("")
      // Applying changes and reparsing does not affect properties files, need to completely remake the build model.
      buildModel = subModuleGradleBuildModel
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop")
      verifyPropertyModel(propertyModel, STRING_TYPE, "hello, penguin!", STRING, REGULAR, 1)
      verifyPropertyModel(propertyModel.dependencies[0], STRING_TYPE, "penguin", STRING, REGULAR, 0)

      propertyModel.dependencies[0].delete()

      verifyPropertyModel(propertyModel, STRING_TYPE, "hello, ${'$'}{animal}!", STRING, REGULAR, 0)
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop")
      verifyPropertyModel(propertyModel, STRING_TYPE, "hello, ${'$'}{animal}!", STRING, REGULAR, 0)
    }
  }

  fun testSetValueInMap() {
    val text = """
               ext {
                 def val = "hello"
                 def otherVal = "goodbye"
                 prop1 = [key1: 'value', key2: val, key3: 23, key4: true]
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      val map = propertyModel.getValue(MAP_TYPE)!!
      verifyPropertyModel(map["key1"], STRING_TYPE, "value", STRING, DERIVED, 0)
      verifyPropertyModel(map["key2"], STRING_TYPE, "val", REFERENCE, DERIVED, 1)
      verifyPropertyModel(map["key3"], INTEGER_TYPE, 23, INTEGER, DERIVED, 0)
      verifyPropertyModel(map["key4"], BOOLEAN_TYPE, true, BOOLEAN, DERIVED, 0)

      propertyModel.getMapValue("key1").setValue(ReferenceTo("otherVal"))
      propertyModel.getMapValue("key2").setValue("newValue")
      propertyModel.getMapValue("key3").setValue(false)
      propertyModel.getMapValue("key4").setValue(32)
      propertyModel.getMapValue("newKey").setValue("meerkats")

      assertEquals(MAP, propertyModel.valueType)
      val newMap = propertyModel.getValue(MAP_TYPE)!!
      verifyPropertyModel(newMap["key1"], STRING_TYPE, "otherVal", REFERENCE, DERIVED, 1)
      verifyPropertyModel(newMap["key2"], STRING_TYPE, "newValue", STRING, DERIVED, 0)
      verifyPropertyModel(newMap["key3"], BOOLEAN_TYPE, false, BOOLEAN, DERIVED, 0)
      verifyPropertyModel(newMap["key4"], INTEGER_TYPE, 32, INTEGER, DERIVED, 0)
      verifyPropertyModel(newMap["newKey"], STRING_TYPE, "meerkats", STRING, DERIVED, 0)
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      val map = propertyModel.getValue(MAP_TYPE)!!
      verifyPropertyModel(map["key1"], STRING_TYPE, "otherVal", REFERENCE, DERIVED, 1)
      verifyPropertyModel(map["key2"], STRING_TYPE, "newValue", STRING, DERIVED, 0)
      verifyPropertyModel(map["key3"], BOOLEAN_TYPE, false, BOOLEAN, DERIVED, 0)
      verifyPropertyModel(map["key4"], INTEGER_TYPE, 32, INTEGER, DERIVED, 0)
      verifyPropertyModel(map["newKey"], STRING_TYPE, "meerkats", STRING, DERIVED, 0)
    }
  }

  fun testSetMapValueOnNoneMap() {
    val text = """
               ext {
                 prop1 = ['value1', false, 17]
                 prop2 = "hello"
                 prop3 = prop1 // Should only work for resolved properties.
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val firstModel = buildModel.ext().findProperty("prop1")
      try {
        firstModel.getMapValue("value1").setValue("newValue")
        fail()
      }
      catch (e: IllegalStateException) {
        // Expected
      }

      val secondModel = buildModel.ext().findProperty("prop2")
      try {
        secondModel.getMapValue("hello").setValue("goodbye")
        fail()
      }
      catch (e: IllegalStateException) {
        // Expected
      }

      val thirdModel = buildModel.ext().findProperty("prop3")
      try {
        thirdModel.getMapValue("key").setValue(0)
        fail()
      }
      catch (e: IllegalStateException) {
        // Expected
      }
    }
  }

  fun testOuterScopeVariablesResolved() {
    val text = """
               def max_version = 15
               android {
                 def min_version = 12
                 defaultConfig {
                   minSdkVersion min_version
                   targetSdkVersion max_version
                 }
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val defaultConfig = buildModel.android()!!.defaultConfig()
      verifyPropertyModel(defaultConfig.minSdkVersion(), INTEGER_TYPE, 12, INTEGER, REGULAR, 1)
      verifyPropertyModel(defaultConfig.targetSdkVersion(), INTEGER_TYPE, 15, INTEGER, REGULAR, 1)

      // Check that we can edit them.
      defaultConfig.minSdkVersion().resultModel.setValue(18)
      defaultConfig.targetSdkVersion().resultModel.setValue(21)

      verifyPropertyModel(defaultConfig.minSdkVersion(), INTEGER_TYPE, 18, INTEGER, REGULAR, 1)
      verifyPropertyModel(defaultConfig.targetSdkVersion(), INTEGER_TYPE, 21, INTEGER, REGULAR, 1)
    }

    applyChangesAndReparse(buildModel)

    run {
      val defaultConfig = buildModel.android()!!.defaultConfig()
      verifyPropertyModel(defaultConfig.minSdkVersion(), INTEGER_TYPE, 18, INTEGER, REGULAR, 1)
      verifyPropertyModel(defaultConfig.targetSdkVersion(), INTEGER_TYPE, 21, INTEGER, REGULAR, 1)
    }
  }

  fun testInScopeElement() {
    val parentText = """
                     def var1 = "aardwolf" // No
                     ext {
                       def var2 = "zorro" // No
                       prop1 = "baboon" // Yes
                     }""".trimIndent()
    val childText = """
                    ext {
                      def var6 = "swan" // No
                      prop2 = "kite" // Yes
                    }
                    def var3 = "goldeneye" // Yes
                    android {
                      def var4 = "wallaby" // Yes
                      defaultConfig {
                        def var5 = "curlew" // Yes
                        targetSdkVersion 14 // No
                        minSdkVersion 12 // No
                      }
                    }""".trimIndent()
    val childProperties = "prop3 = chickadee"
    val parentProperties = "prop4 = ferret"
    writeToBuildFile(parentText)
    writeToSubModuleBuildFile(childText)
    writeToSettingsFile("include ':${SUB_MODULE_NAME}'")
    writeToPropertiesFile(parentProperties)
    writeToSubModulePropertiesFile(childProperties)

    val buildModel = subModuleGradleBuildModel

    run {
      val defaultConfig = buildModel.android()!!.defaultConfig()
      val properties = defaultConfig.inScopeProperties
      assertEquals(7, properties.entries.size)

      // Check all the properties that we expect are present.
      verifyPropertyModel(properties["var3"], STRING_TYPE, "goldeneye", STRING, VARIABLE, 0)
      verifyPropertyModel(properties["var4"], STRING_TYPE, "wallaby", STRING, VARIABLE, 0)
      verifyPropertyModel(properties["var5"], STRING_TYPE, "curlew", STRING, VARIABLE, 0)
      verifyPropertyModel(properties["prop1"], STRING_TYPE, "baboon", STRING, REGULAR, 0)
      verifyPropertyModel(properties["prop2"], STRING_TYPE, "kite", STRING, REGULAR, 0)
      verifyPropertyModel(properties["prop3"], STRING_TYPE, "chickadee", STRING, PROPERTIES_FILE, 0)
      verifyPropertyModel(properties["prop4"], STRING_TYPE, "ferret", STRING, PROPERTIES_FILE, 0)
    }

    run {
      val properties = buildModel.ext().inScopeProperties
      assertEquals(6, properties.entries.size)
      verifyPropertyModel(properties["prop1"], STRING_TYPE, "baboon", STRING, REGULAR, 0)
      verifyPropertyModel(properties["prop2"], STRING_TYPE, "kite", STRING, REGULAR, 0)
      verifyPropertyModel(properties["prop3"], STRING_TYPE, "chickadee", STRING, PROPERTIES_FILE, 0)
      verifyPropertyModel(properties["prop4"], STRING_TYPE, "ferret", STRING, PROPERTIES_FILE, 0)
      verifyPropertyModel(properties["var6"], STRING_TYPE, "swan", STRING, VARIABLE, 0)
      // TODO: Should not be visible, this needs line number support to correctly hide itself.
      verifyPropertyModel(properties["var3"], STRING_TYPE, "goldeneye", STRING, VARIABLE, 0)

    }
  }

  fun testVariablesFromNestedApply() {
    val firstApplyFileText = """
                             def var1 = "1"
                             def var2 = true
                             def var3 = 1

                             ext {
                               prop1 = [var1, var2, var3]
                               prop2 = true
                             }""".trimIndent()
    val secondApplyFileText = """
                              ext {
                                prop2 = false
                                prop3 = "hello"
                                prop4 = "boo"
                              }

                              apply from: "b.gradle"
                              """.trimIndent()
    val text = """
               apply from: "a.gradle"

               ext {
                 prop3 = "goodbye"
                 prop4 = prop1[0]
                 prop5 = 5
               }""".trimIndent()
    writeToNewProjectFile("b.gradle", firstApplyFileText)
    writeToNewProjectFile("a.gradle", secondApplyFileText)
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val properties = buildModel.ext().inScopeProperties
      assertSize(5, properties.values)
      verifyPropertyModel(properties["prop2"], BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 0, "prop2", "ext.prop2")
      verifyFilePathsAreEqual(File(myProjectBasePath, "b.gradle"), properties["prop2"]!!.gradleFile)
      verifyListProperty(properties["prop1"], listOf("var1", "var2", "var3"), false)
      verifyListProperty(properties["prop1"], listOf("1", true, 1), REGULAR, 3)
      verifyFilePathsAreEqual(File(myProjectBasePath, "b.gradle"), properties["prop1"]!!.gradleFile)
      // TODO: This is currently picking up the wrong element. Once ordering is complete they should be correct.
      verifyPropertyModel(properties["prop4"], STRING_TYPE, "boo", STRING, REGULAR, 0, "prop4", "ext.prop4")
      verifyFilePathsAreEqual(File(myProjectBasePath, "a.gradle"), properties["prop4"]!!.gradleFile)
      // TODO: This is currently picking up the wrong element. Once ordering is complete they should be correct.
      verifyPropertyModel(properties["prop3"], STRING_TYPE, "hello", STRING, REGULAR, 0, "prop3", "ext.prop3")
      verifyFilePathsAreEqual(File(myProjectBasePath, "a.gradle"), properties["prop3"]!!.gradleFile)
      verifyPropertyModel(properties["prop5"], INTEGER_TYPE, 5, INTEGER, REGULAR, 0, "prop5", "ext.prop5")

      // Check we can actually make changes to all the files.
      properties["prop5"]!!.setValue(ReferenceTo("prop2"))
      properties["prop1"]!!.getValue(LIST_TYPE)!![1].dependencies[0].setValue(false)
      properties["prop1"]!!.getValue(LIST_TYPE)!![0].setValue(2)
      properties["prop2"]!!.setValue("true")

      verifyPropertyModel(properties["prop2"], STRING_TYPE, "true", STRING, REGULAR, 0, "prop2", "ext.prop2")
      verifyFilePathsAreEqual(File(myProjectBasePath, "b.gradle"), properties["prop2"]!!.gradleFile)
      verifyListProperty(properties["prop1"], listOf(2, "var2", "var3"), false)
      verifyListProperty(properties["prop1"], listOf(2, false, 1), REGULAR, 2)
      verifyFilePathsAreEqual(File(myProjectBasePath, "b.gradle"), properties["prop1"]!!.gradleFile)
    }

    applyChangesAndReparse(buildModel)

    run {
      val properties = buildModel.ext().inScopeProperties
      verifyPropertyModel(properties["prop2"], STRING_TYPE, "true", STRING, REGULAR, 0, "prop2", "ext.prop2")
      verifyFilePathsAreEqual(File(myProjectBasePath, "b.gradle"), properties["prop2"]!!.gradleFile)
      verifyListProperty(properties["prop1"], listOf(2, "var2", "var3"), false)
      verifyListProperty(properties["prop1"], listOf(2, false, 1), REGULAR, 2)
      verifyFilePathsAreEqual(File(myProjectBasePath, "b.gradle"), properties["prop1"]!!.gradleFile)
      verifyPropertyModel(properties["prop4"], STRING_TYPE, "boo", STRING, REGULAR, 0, "prop4", "ext.prop4")
      verifyFilePathsAreEqual(File(myProjectBasePath, "a.gradle"), properties["prop4"]!!.gradleFile)
      verifyPropertyModel(properties["prop3"], STRING_TYPE, "hello", STRING, REGULAR, 0, "prop3", "ext.prop3")
      verifyFilePathsAreEqual(File(myProjectBasePath, "a.gradle"), properties["prop3"]!!.gradleFile)
    }
  }

  fun /*test*/ApplicationCycle() {
    val applyFileText = """
                        ext {
                          prop1 = "hello"
                        }

                        apply from: "build.gradle"
                        """.trimIndent()
    val text = """
               ext {
                 prop2 = "true"
               }

               apply from: "a.gradle"
               """.trimIndent()
    writeToNewProjectFile("a.gradle", applyFileText)
    writeToBuildFile(text)

    // Make sure we don't blow up.
    val buildModel = gradleBuildModel

    run {
      val properties = buildModel.ext().inScopeProperties
      assertSize(2, properties.values)
    }
  }

  fun testVariablesFromApply() {
    val applyFileText = """
                        def var1 = "Hello"
                        def var2 = var1
                        def var3 = true

                        ext {
                          prop1 = var2
                          prop2 = "${'$'}{prop1} world!"
                          prop3 = var3
                        }
                        """.trimIndent()
    val text = """
               apply from: "vars.gradle"
               ext {
                 prop4 = "${'$'}{prop1} : ${'$'}{prop2} : ${'$'}{prop3}"
               }
               """.trimIndent()
    writeToNewProjectFile("vars.gradle", applyFileText)
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop4")
      verifyPropertyModel(propertyModel, STRING_TYPE, "Hello : Hello world! : true", STRING, REGULAR, 3)

      val deps = propertyModel.dependencies
      verifyPropertyModel(deps[0], STRING_TYPE, "var2", REFERENCE, REGULAR, 1)
      verifyPropertyModel(deps[1], STRING_TYPE, "Hello world!", STRING, REGULAR, 1)
      verifyPropertyModel(deps[2], STRING_TYPE, "var3", REFERENCE, REGULAR, 1)

      // Lets delete one of the variables
      verifyPropertyModel(deps[0].dependencies[0], STRING_TYPE, "var1", REFERENCE, VARIABLE, 1)
      deps[0].dependencies[0].delete()
      // And edit one of the properties
      deps[0].setValue(72)

      buildModel.ext().inScopeProperties
      verifyPropertyModel(propertyModel, STRING_TYPE, "72 : 72 world! : true", STRING, REGULAR, 3)
    }

    applyChangesAndReparse(buildModel)
    ApplicationManager.getApplication().runWriteAction { myProject.baseDir.fileSystem.refresh(false) }

    run {
      val propertyModel = buildModel.ext().findProperty("prop4")
      verifyPropertyModel(propertyModel, STRING_TYPE, "72 : 72 world! : true", STRING, REGULAR, 3)
    }
  }

  fun testAddRemoveReferenceValues() {
    val text = """
               ext {
                 propB = "2"
                 propC = "3"
                 propRef = propB
                 propInterpolated = "${'$'}{propB}nd"
                 propList = ["1", propB, propC, propRef, propInterpolated]
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val extModel = buildModel.ext()

    run {
      val propertyModel = extModel.findProperty("propList")
      verifyListProperty(propertyModel, listOf("1", "2", "3", "2", "2nd"), REGULAR, 4)
      propertyModel.toList()!![0].setValue(ReferenceTo("propC"))
      verifyListProperty(propertyModel, listOf("3", "2", "3", "2", "2nd"), REGULAR, 5)
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = extModel.findProperty("propList")
      verifyListProperty(propertyModel, listOf("3", "2", "3", "2", "2nd"), REGULAR, 5)
    }
  }

  fun testRename() {
    val text = """
               ext {
                 def var1 = "hello"

                 prop1 = "${'$'}{var1} ${'$'}{var2}"
               }
               """.trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(propertyModel, STRING_TYPE, "hello ${'$'}{var2}", STRING, REGULAR, 1, "prop1", "ext.prop1")
      val varModel = propertyModel.dependencies[0]
      verifyPropertyModel(varModel, STRING_TYPE, "hello", STRING, VARIABLE, 0, "var1", "ext.var1")

      // Rename the properties.
      propertyModel.rename("prop2")
      varModel.rename("var2")

      // TODO: Names aren't updated until after a reparse
      //verifyPropertyModel(propertyModel, STRING_TYPE, "${'$'}{var1} hello", STRING, REGULAR, 1, "prop2", "ext.prop2")
      //verifyPropertyModel(varModel, STRING_TYPE, "hello", STRING, VARIABLE, 0, "var2", "ext.var2")
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop2")
      val varModel = propertyModel.dependencies[0]
      verifyPropertyModel(propertyModel, STRING_TYPE, "${'$'}{var1} hello", STRING, REGULAR, 1, "prop2", "ext.prop2")
      verifyPropertyModel(varModel, STRING_TYPE, "hello", STRING, VARIABLE, 0, "var2", "ext.var2")
    }
  }

  fun testRenameMapPropertyAndKeys() {
    val text = """
               ext {
                 def map1 = [key1 : 'a', "key2" : 'b', key3 : 'c']
                 map2 = [key4 : 4]
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    run {
      val firstMapModel = buildModel.ext().findProperty("map1")
      verifyMapProperty(firstMapModel, mapOf("key1" to "a", "key2" to "b", "key3" to "c"), "map1", "ext.map1")
      val secondMapModel = buildModel.ext().findProperty("map2")
      verifyMapProperty(secondMapModel, mapOf("key4" to 4), "map2", "ext.map2")

      // Rename the keys
      val firstKeyModel = firstMapModel.getMapValue("key2")
      verifyPropertyModel(firstKeyModel, STRING_TYPE, "b", STRING, DERIVED, 0, "key2", "ext.map1.key2")
      val secondKeyModel = secondMapModel.getMapValue("key4")
      verifyPropertyModel(secondKeyModel, INTEGER_TYPE, 4, INTEGER, DERIVED, 0, "key4", "ext.map2.key4")

      firstKeyModel.rename("newKey1")
      secondKeyModel.rename("newKey2")

      // Rename the maps
      firstMapModel.rename("newMap1")
      secondMapModel.rename("newMap2")

      // TODO: Names aren't updated until after a reparse
      // verifyMapProperty(firstMapModel, mapOf("key1" to "a", "key2" to "b", "key3" to "c"), "map1", "ext.map1")
      // verifyMapProperty(secondMapModel, mapOf("key4" to 4), "map2", "ext.map2")
      // verifyPropertyModel(firstKeyModel, STRING_TYPE, "b", STRING, DERIVED, 0, "key2", "ext.map1.key2")
      // verifyPropertyModel(secondKeyModel, INTEGER_TYPE, 4, INTEGER, REGULAR, 0, "key4", "ext.map2.key4")
    }

    applyChangesAndReparse(buildModel)

    run {
      val firstMapModel = buildModel.ext().findProperty("newMap1")
      verifyMapProperty(firstMapModel, mapOf("key1" to "a", "newKey1" to "b", "key3" to "c"), "newMap1", "ext.newMap1")
      val secondMapModel = buildModel.ext().findProperty("newMap2")
      verifyMapProperty(secondMapModel, mapOf("newKey2" to 4), "newMap2", "ext.newMap2")

      // Rename the keys
      val firstKeyModel = firstMapModel.getMapValue("newKey1")
      verifyPropertyModel(firstKeyModel, STRING_TYPE, "b", STRING, DERIVED, 0, "newKey1", "ext.newMap1.newKey1")
      val secondKeyModel = secondMapModel.getMapValue("newKey2")
      verifyPropertyModel(secondKeyModel, INTEGER_TYPE, 4, INTEGER, DERIVED, 0, "newKey2", "ext.newMap2.newKey2")
    }
  }

  fun testRenameListValueThrows() {
    val text = """
               ext {
                 def list1 = [1, 2, 3, 4]
                 list2 = ['a', 'b', 'c', 'd']
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    run {
      val firstListModel = buildModel.ext().findProperty("list1")
      verifyListProperty(firstListModel, listOf(1, 2, 3, 4), VARIABLE, 0, "list1", "ext.list1")
      val secondListModel = buildModel.ext().findProperty("list2")
      verifyListProperty(secondListModel, listOf("a", "b", "c", "d"), REGULAR, 0, "list2", "ext.list2")

      val listItem = secondListModel.getListValue("b")!!
      try {
        listItem.rename("listItemName")
        fail()
      } catch (e : IllegalStateException) {
        // Expected
      }

      firstListModel.rename("varList")
      secondListModel.rename("propertyList")

      // TODO: Names aren't updated until after a reparse
      // verifyListProperty(firstListModel, listOf(1, 2, 3, 4), VARIABLE, 0, "varList", "ext.varList")
      // verifyListProperty(secondListModel, listOf("a", "b", "c", "d"), REGULAR, 0, "propertyList", "ext.propertyList")
    }

    applyChangesAndReparse(buildModel)

    run {
      val firstListModel = buildModel.ext().findProperty("varList")
      verifyListProperty(firstListModel, listOf(1, 2, 3, 4), VARIABLE, 0, "varList", "ext.varList")
      val secondListModel = buildModel.ext().findProperty("propertyList")
      verifyListProperty(secondListModel, listOf("a", "b", "c", "d"), REGULAR, 0, "propertyList", "ext.propertyList")
    }
  }

  private fun runSetPropertyTest(text: String, type: PropertyType) {
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(propertyModel, STRING_TYPE, "value", STRING, type, 0)
      val oldGradleFile = propertyModel.gradleFile

      val stringValue = "Hello world!"
      propertyModel.setValue(stringValue)
      verifyPropertyModel(propertyModel, STRING_TYPE, stringValue, STRING, type, 0)
      applyChangesAndReparse(buildModel)
      val newStringModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(newStringModel, STRING_TYPE, stringValue, STRING, type, 0)
      assertEquals(oldGradleFile, newStringModel.gradleFile)
    }

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      val intValue = 26
      propertyModel.setValue(intValue)
      verifyPropertyModel(propertyModel, INTEGER_TYPE, intValue, INTEGER, type, 0)
      applyChangesAndReparse(buildModel)
      val newIntModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(newIntModel, INTEGER_TYPE, intValue, INTEGER, type, 0)
    }

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      val boolValue = true
      propertyModel.setValue(boolValue)
      verifyPropertyModel(propertyModel, BOOLEAN_TYPE, boolValue, BOOLEAN, type, 0)
      applyChangesAndReparse(buildModel)
      val newBooleanModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(newBooleanModel, BOOLEAN_TYPE, boolValue, BOOLEAN, type, 0)
    }

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      val refValue = "\"${'$'}{prop2}\""
      propertyModel.setValue(refValue)
      // Resolved value and dependencies are only updated after the model has been applied and re-parsed.
      verifyPropertyModel(propertyModel, STRING_TYPE, "ref", STRING, type, 1)
      assertEquals("${'$'}{prop2}", propertyModel.getRawValue(STRING_TYPE))
      applyChangesAndReparse(buildModel)
      val newRefModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(newRefModel, STRING_TYPE, "ref", STRING, type, 1)
      assertEquals("${'$'}{prop2}", newRefModel.getRawValue(STRING_TYPE))
    }
  }

  private fun checkContainsValue(models: Collection<GradlePropertyModel>, model: GradlePropertyModel) {
    val result = models.any { areModelsEqual(it, model) }
    assertTrue("checkContainsValue", result)
  }
}