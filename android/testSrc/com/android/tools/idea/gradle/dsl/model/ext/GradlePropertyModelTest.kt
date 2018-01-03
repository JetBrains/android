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
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType.*
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.*
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType
import com.android.tools.idea.gradle.dsl.api.util.TypeReference
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import com.intellij.openapi.vfs.VfsUtil

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
      assertEquals("value", propertyModel.getUnresolvedValue(STRING_TYPE))
      assertEquals("prop1", propertyModel.name)
      assertEquals("ext.prop1", propertyModel.fullyQualifiedName)
    }

    run {
      val propertyModel = extModel.findProperty("prop2")
      assertEquals(INTEGER, propertyModel.valueType)
      assertEquals(REGULAR, propertyModel.propertyType)
      assertEquals(25, propertyModel.getValue(INTEGER_TYPE))
      assertEquals(25, propertyModel.getUnresolvedValue(INTEGER_TYPE))
      assertEquals("prop2", propertyModel.name)
      assertEquals("ext.prop2", propertyModel.fullyQualifiedName)
    }

    run {
      val propertyModel = extModel.findProperty("prop3")
      assertEquals(BOOLEAN, propertyModel.valueType)
      assertEquals(REGULAR, propertyModel.propertyType)
      assertEquals(true, propertyModel.getValue(BOOLEAN_TYPE))
      assertEquals(true, propertyModel.getUnresolvedValue(BOOLEAN_TYPE))
      assertEquals("prop3", propertyModel.name)
      assertEquals("ext.prop3", propertyModel.fullyQualifiedName)
    }

    run {
      val propertyModel = extModel.findProperty("prop4")
      assertEquals(MAP, propertyModel.valueType)
      assertEquals(REGULAR, propertyModel.propertyType)
      assertNull(propertyModel.getValue(STRING_TYPE))
      assertNull(propertyModel.getUnresolvedValue(STRING_TYPE))
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
      assertNull(propertyModel.getUnresolvedValue(STRING_TYPE))
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
      assertEquals("value", propertyModel.getUnresolvedValue(STRING_TYPE))
      assertEquals("prop1", propertyModel.name)
      assertEquals("ext.prop1", propertyModel.fullyQualifiedName)
    }

    run {
      val propertyModel = extModel.findProperty("prop2")
      assertEquals(INTEGER, propertyModel.valueType)
      assertEquals(VARIABLE, propertyModel.propertyType)
      assertEquals(25, propertyModel.getValue(INTEGER_TYPE))
      assertEquals(25, propertyModel.getUnresolvedValue(INTEGER_TYPE))
      assertEquals("prop2", propertyModel.name)
      assertEquals("ext.prop2", propertyModel.fullyQualifiedName)
    }

    run {
      val propertyModel = extModel.findProperty("prop3")
      assertEquals(BOOLEAN, propertyModel.valueType)
      assertEquals(VARIABLE, propertyModel.propertyType)
      assertEquals(true, propertyModel.getValue(BOOLEAN_TYPE))
      assertEquals(true, propertyModel.getUnresolvedValue(BOOLEAN_TYPE))
      assertEquals("prop3", propertyModel.name)
      assertEquals("ext.prop3", propertyModel.fullyQualifiedName)
    }

    run {
      val propertyModel = extModel.findProperty("prop4")
      assertEquals(MAP, propertyModel.valueType)
      assertEquals(VARIABLE, propertyModel.propertyType)
      assertNull(propertyModel.getValue(STRING_TYPE))
      assertNull(propertyModel.getUnresolvedValue(STRING_TYPE))
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
      assertNull(propertyModel.getUnresolvedValue(STRING_TYPE))
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
    assertEquals("prop1", propertyModel.getUnresolvedValue(STRING_TYPE))
    assertEquals(REFERENCE, propertyModel.valueType)
    assertEquals(REGULAR, propertyModel.propertyType)

    val deps = propertyModel.dependencies
    assertSize(1, deps)
    val value = deps[0]
    assertEquals("value", value.getValue(STRING_TYPE))
    assertEquals("value", value.getUnresolvedValue(STRING_TYPE))
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
    assertEquals("prop1", propertyModel.getUnresolvedValue(STRING_TYPE))
    assertEquals(REFERENCE, propertyModel.valueType)
    assertEquals(REGULAR, propertyModel.propertyType)

    val deps = propertyModel.dependencies
    assertSize(1, deps)
    val value = deps[0]
    assertEquals(25, value.getValue(INTEGER_TYPE))
    assertEquals(25, value.getUnresolvedValue(INTEGER_TYPE))
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
    assertEquals("prop1", propertyModel.getUnresolvedValue(STRING_TYPE))
    assertEquals(REFERENCE, propertyModel.valueType)
    assertEquals(REGULAR, propertyModel.propertyType)

    val deps = propertyModel.dependencies
    assertSize(1, deps)
    val value = deps[0]
    assertEquals("value", value.getValue(STRING_TYPE))
    assertEquals("value", value.getUnresolvedValue(STRING_TYPE))
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
    assertEquals("prop1", propertyModel.getUnresolvedValue(STRING_TYPE))
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
    assertEquals("prop1", propertyModel.getUnresolvedValue(STRING_TYPE))
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
    assertEquals("\${prop1} world!", propertyModel.getUnresolvedValue(STRING_TYPE))
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
    assertEquals("\${prop1} world!", propertyModel.getUnresolvedValue(STRING_TYPE))
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
    assertEquals("\${prop1} world!", propertyModel.getUnresolvedValue(STRING_TYPE))
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
    assertEquals("\${prop1} world!", propertyModel.getUnresolvedValue(STRING_TYPE))
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
    assertEquals("\${prop1} and \${project.ext.prop1}", propertyModel.getUnresolvedValue(STRING_TYPE))

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
    assertEquals("\${prop1} and \${project.ext.prop1}", propertyModel.getUnresolvedValue(STRING_TYPE))

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
    assertEquals("${'$'}{prop3.key[0][2]}", propertyModel.getUnresolvedValue(STRING_TYPE))
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
    assertEquals("${'$'}{prop2[\"key2\"][\"key1\"]}", propertyModel.getUnresolvedValue(STRING_TYPE))

    val dependencies = propertyModel.dependencies
    assertSize(1, dependencies)
    val depModel = dependencies[0]
    assertEquals(STRING, depModel.valueType)
    assertEquals(DERIVED, depModel.propertyType)
    assertEquals("valuetrue", depModel.getValue(STRING_TYPE))
    assertEquals("value${'$'}{prop}", depModel.getUnresolvedValue(STRING_TYPE))

    val dependencies2 = depModel.dependencies
    assertSize(1, dependencies2)
    val depModel2 = dependencies2[0]
    assertEquals(BOOLEAN, depModel2.valueType)
    assertEquals(REGULAR, depModel2.propertyType)
    assertEquals(true, depModel2.getValue(BOOLEAN_TYPE))
    assertEquals(true, depModel2.getUnresolvedValue(BOOLEAN_TYPE))
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
    assertEquals("\${prop1[0]}", propertyModel.getUnresolvedValue(STRING_TYPE))

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
    assertEquals("\${prop1.key}", propertyModel.getUnresolvedValue(STRING_TYPE))

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
    assertEquals("9\${prop8}", propertyModel.getUnresolvedValue(STRING_TYPE))
    assertEquals(STRING, propertyModel.valueType)
    assertEquals(REGULAR, propertyModel.propertyType)

    var deps = propertyModel.dependencies
    for (i in 1..7) {
      assertSize(1, deps)
      val value = deps[0]
      expected = expected.drop(1)
      assertEquals(expected, value.getValue(STRING_TYPE))
      assertEquals("${9 - i}\${prop${8 - i}}", value.getUnresolvedValue(STRING_TYPE))
      assertEquals(STRING, propertyModel.valueType)
      assertEquals(REGULAR, propertyModel.propertyType)
      deps = deps[0].dependencies
    }

    assertSize(1, deps)
    val value = deps[0]
    assertEquals("1", value.getValue(STRING_TYPE))
    assertEquals("1", value.getUnresolvedValue(STRING_TYPE))
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
      assertEquals("${'$'}{prop1} world!", propertyModel2.getUnresolvedValue(STRING_TYPE))
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
      assertEquals("${'$'}{prop1} world!", propertyModel2.getUnresolvedValue(STRING_TYPE))
      // Check dependency is correct.
      verifyPropertyModel(propertyModel4.dependencies[0], STRING_TYPE, newValue, STRING, REGULAR, 0)
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
      assertEquals("${'$'}{prop2}", propertyModel.getUnresolvedValue(STRING_TYPE))
      applyChangesAndReparse(buildModel)
      val newRefModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(newRefModel, STRING_TYPE, "ref", STRING, type, 1)
      assertEquals("${'$'}{prop2}", newRefModel.getUnresolvedValue(STRING_TYPE))
    }
  }

  private fun <T> verifyPropertyModel(model: GradlePropertyModel, type: TypeReference<T>, value: T,
                                      valueType: ValueType, propertyType: PropertyType, dependencies: Int) {
    assertEquals(value, model.getValue(type))
    assertEquals(valueType, model.valueType)
    assertEquals(propertyType, model.propertyType)
    assertEquals(dependencies, model.dependencies.size)
  }

  private fun checkContainsValue(models: Collection<GradlePropertyModel>, model: GradlePropertyModel) {
    val result = models.any { areModelsEqual(it, model) }
    assertTrue("checkContainsValue", result)
  }
}