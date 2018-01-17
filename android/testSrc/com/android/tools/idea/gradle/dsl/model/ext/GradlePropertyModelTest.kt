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
import com.intellij.openapi.vfs.VfsUtil
import org.apache.commons.io.FileUtils

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

    val extModel = gradleBuildModel.ext()

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
      assertNull(propertyModel.getValue(STRING_TYPE))
      assertNull(propertyModel.getRawValue(STRING_TYPE))
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
      assertNull(propertyModel.getValue(STRING_TYPE))
      assertNull(propertyModel.getRawValue(STRING_TYPE))
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
      // TODO: Getting float values not cureently supported
      //assertEquals(25.3, propertyModel.getValue(java.lang.Double::class.java))
      assertEquals(UNKNOWN, propertyModel.valueType) // There is currently no type for this
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
      assertNull(propertyModel.getValue(STRING_TYPE))
      assertNull(propertyModel.getRawValue(STRING_TYPE))
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
      assertNull(propertyModel.getValue(STRING_TYPE))
      assertNull(propertyModel.getRawValue(STRING_TYPE))
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
      // TODO: Getting float values not cureently supported
      //assertEquals(25.3, propertyModel?.getValue(java.lang.Double::class.java))
      assertEquals(UNKNOWN, propertyModel.valueType) // There is currently no type for this
      assertEquals(VARIABLE, propertyModel.propertyType)
      assertEquals("prop6", propertyModel.name)
      assertEquals("ext.prop6", propertyModel.fullyQualifiedName)
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
      // Not: Since this doesn't actually make any sense, the word "in" gets removed as it is a keyword in Groovy.
      verifyPropertyModel(propertyModel, STRING_TYPE, "a voice like thunder", REFERENCE, REGULAR, 0)
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop2")
      // TODO: Fix this, it should still parse the value.
      verifyPropertyModel(propertyModel, OBJECT_TYPE, null, NONE, REGULAR, 0)
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

    println(FileUtils.readFileToString(myBuildFile))

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
      verifyListProperty(propertyModel, listOf<Any>(1, 2, 3, 4) , REGULAR, 0)
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

  fun testiStr() {
    assertEquals("\"Its rider held a bow, and he was given a crown, and he rode out as a conqueror bent on conquest.\"",
            iStr("Its rider held a bow, and he was given a crown, and he rode out as a conqueror bent on conquest."))
    assertEquals("\"When the Lamb opened the second seal, I heard the second living creature say, \\\"Come and see!\\\" \"",
            iStr("When the Lamb opened the second seal, I heard the second living creature say, \"Come and see!\" "))
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
        propertyModel.addMapValue("key")
        fail("Exception should have been thrown!")
      } catch (e : IllegalStateException) {
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
        val newValue = propertyModel.addMapValue("key3")
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
      propertyModel.addMapValue("key1").setValue(ReferenceTo("val"))

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

    println(FileUtils.readFileToString(myBuildFile))

    // Check that a reparse still has a missing model
    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      assertSize(0, propertyModel.getValue(MAP_TYPE)!!.entries)

      // Attempt to set a new value
      propertyModel.addMapValue("Conquest").setValue("Famine")
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

      mapPropertyModel.addMapValue("key").setValue("Hello")

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

  // TODO: Fix this b/72099838
  fun /*test*/SetMapInMap() {
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
      propertyModel.getValue(MAP_TYPE)!!["key1"]?.addMapValue("War")?.setValue("Death")
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