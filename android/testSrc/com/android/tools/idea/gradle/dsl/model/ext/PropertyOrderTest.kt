/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.ext

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.iStr
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType
import com.android.tools.idea.gradle.dsl.api.util.GradleDslModel
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import com.android.tools.idea.gradle.dsl.parser.elements.*
import org.junit.Test

class PropertyOrderTest : GradleFileModelTestCase() {
  private fun GradleDslModel.dslElement() : GradlePropertiesDslElement {
    assert(this is GradleDslBlockModel)
    val field = GradleDslBlockModel::class.java.getDeclaredField("myDslElement")
    field.isAccessible = true
    return field.get(this) as GradlePropertiesDslElement
  }

  private class TestBlockElement(parent : GradleDslElement, name : String) : GradleDslBlockElement(parent, GradleNameElement.create(name))

  private fun newLiteral(parent : GradleDslElement, name : String, value : Any) : GradleDslLiteral {
    val newElement = GradleDslLiteral(parent, GradleNameElement.create(name))
    newElement.setValue(value)
    newElement.setUseAssignment(true)
    newElement.elementType = PropertyType.REGULAR
    return newElement
  }

  private fun newBlock(parent : GradleDslElement, name : String) : GradlePropertiesDslElement {
    return TestBlockElement(parent, name)
  }

  private fun newElementList(parent : GradleDslElement, name : String) : GradleDslElementList {
    return GradleDslElementList(parent, GradleNameElement.fake(name))
  }

  @Test
  fun testAddPropertiesToEmpty() {
    val text = ""
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    val firstProperty = buildModel.ext().findProperty("prop1")
    firstProperty.setValue(1)
    val secondProperty = buildModel.ext().findProperty("prop2")
    secondProperty.setValue("2")
    val thirdProperty = buildModel.ext().findProperty("prop3")
    thirdProperty.convertToEmptyList().addListValue().setValue(3)
    val forthProperty = buildModel.ext().findProperty("prop4")
    forthProperty.convertToEmptyMap().getMapValue("key").setValue("4")

    applyChangesAndReparse(buildModel)

    val expected = """
                   ext {
                     prop1 = 1
                     prop2 = '2'
                     prop3 = [3]
                     prop4 = [key : '4']
                   }""".trimIndent()
    verifyFileContents(myBuildFile, expected)
  }

  @Test
  fun testCreatePropertyMiddle() {
    val text = """
               ext {
                 prop1 = 1
                 prop3 = 3
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    val dslElement = buildModel.ext().dslElement()
    dslElement.addNewElementAt(1, newLiteral(dslElement, "prop2", 2))

    applyChangesAndReparse(buildModel)

    val expected = """
                   ext {
                     prop1 = 1
                     prop2 = 2
                     prop3 = 3
                   }""".trimIndent()
    verifyFileContents(myBuildFile, expected)
  }

  @Test
  fun testCreatePropertyStartAndEnd() {
    val text = """
               ext {
                 prop2 = 2
                 prop3 = 3
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    val dslElement = buildModel.ext().dslElement()
    dslElement.addNewElementAt(0, newLiteral(dslElement, "prop1", "1"))
    dslElement.addNewElementAt(4, newLiteral(dslElement, "prop4", 4))

    applyChangesAndReparse(buildModel)

    val expected = """
                   ext {
                     prop1 = '1'
                     prop2 = 2
                     prop3 = 3
                     prop4 = 4
                   }""".trimIndent()
    verifyFileContents(myBuildFile, expected)
  }

  @Test
  fun testCreateBlockElement() {
    val text = """
               ext {
                 def var1 = 'hello'
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val dslElement = buildModel.ext().dslElement()
    val topBlock = newBlock(dslElement, "topBlock")
    // Adding at an index that doesn't exist should just place the element at the end.
    topBlock.addNewElementAt(400000, newLiteral(topBlock, "prop2", true))
    topBlock.addNewElementAt(1, newLiteral(topBlock, "prop3", false))
    topBlock.addNewElementAt(0, newLiteral(topBlock, "prop1", 42))

    val bottomBlock = newBlock(dslElement, "bottomBlock")
    // Using a negative index will add the element to the start of the list.
    bottomBlock.addNewElementAt(-1, newLiteral(bottomBlock, "prop4", "hello"))
    bottomBlock.addNewElementAt(1, newLiteral(bottomBlock, "prop6", false))
    bottomBlock.addNewElementAt(1, newLiteral(bottomBlock, "prop5", true))

    val middleBlock = newBlock(dslElement, "middleBlock")
    middleBlock.addNewElementAt(0, newLiteral(middleBlock, "greeting", "goodbye :)"))

    dslElement.addNewElementAt(0, topBlock)
    dslElement.addNewElementAt(2, bottomBlock)
    dslElement.addNewElementAt(1, middleBlock)

    applyChangesAndReparse(buildModel)

    val expected = """
                   ext {
                     topBlock {
                       prop1 = 42
                       prop2 = true
                       prop3 = false
                     }
                     middleBlock {
                       greeting = 'goodbye :)'
                     }
                     def var1 = 'hello'
                     bottomBlock {
                       prop4 = 'hello'
                       prop5 = true
                       prop6 = false
                     }
                   }""".trimIndent()
    verifyFileContents(myBuildFile, expected)
  }

  @Test
  fun testCreateElementList() {
    val text = ""
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val dslElement =  buildModel.ext().dslElement()

    val elementList = newElementList(dslElement, "middleList")
    elementList.addNewElement(newLiteral(elementList, "element1", true))
    elementList.addNewElement(newLiteral(elementList, "element2", true))
    elementList.addNewElement(newLiteral(elementList, "element3", false))

    dslElement.addNewElementAt(0, elementList)

    applyChangesAndReparse(buildModel)

    val expected = """
                   ext {
                     element1 = true
                     element2 = true
                     element3 = false
                   }""".trimIndent()
    verifyFileContents(myBuildFile, expected)
  }

  @Test
  fun testAddElementList() {
    val text = """
               ext {
                 prop1 = 'first'
                 prop2 = 'last'
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val dslElement = buildModel.ext().dslElement()

    val topList = newElementList(dslElement, "topList")
    val block = newBlock(topList, "test")
    block.addNewElementAt(0, newLiteral(block, "prop0", 72))
    topList.addNewElement(block)

    val middleList = newElementList(dslElement, "middleList")
    middleList.addNewElement(newLiteral(middleList, "prop1dot3", 1))
    val middleBlock = newBlock(middleList, "test")
    middleBlock.addNewElementAt(0, newLiteral(middleBlock, "prop1dot6", iStr("hello")))
    middleList.addNewElement(middleBlock)
    middleList.addNewElement(newLiteral(middleList, "prop1dot9", 4))

    val bottomList = newElementList(dslElement, "bottomList")
    bottomList.addNewElement(newLiteral(bottomList, "prop3", 4))
    bottomList.addNewElement(newLiteral(bottomList, "prop4", 6))

    dslElement.addNewElementAt(1, middleList)
    dslElement.addNewElementAt(0, topList)
    dslElement.addNewElementAt(4, bottomList)

    applyChangesAndReparse(buildModel)

    val expected = """
                   ext {
                     test {
                       prop0 = 72
                     }
                     prop1 = 'first'
                     prop1dot3 = 1
                     test {
                       prop1dot6 = "hello"
                     }
                     prop1dot9 = 4
                     prop2 = 'last'
                     prop3 = 4
                     prop4 = 6
                   }
                   """.trimIndent()
    verifyFileContents(myBuildFile, expected)
  }

  @Test
  fun testAddToExistingElementList() {
    val text = """
               android {
                 buildTypes {
                   debug {
                     buildConfigField "String", "SOME_NAME", "some_value"
                     def var = sneaky
                     buildConfigField "String", "OTHER_NAME", "other_value"
                   }
                 }
               }
               """.trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val dslElement = buildModel.android()!!.buildTypes()[0].dslElement()

    // Adding an element to index greater than 2 should put it after 'def var = sneaky', however adding to index 1 will place the element
    // after the last buildConfigField. This is an unfortunate consequence of having GradleDslElementLists.
    //
    // Also adding any element to the list given by buildConfig field will cause it to be added after.
    dslElement.addNewElementAt(0, newLiteral(dslElement, "first", true)) // Should be placed at the top
    // Should be placed after the last build config field
    dslElement.addNewElementAt(2, newLiteral(dslElement, "last", false))
    dslElement.addNewElementAt(2, newLiteral(dslElement, "afterLastBuildConfig", 72))

    // Add an element to the internal list, this should be placed at the bottom.
    val buildConfigField = dslElement.getElement("buildConfigField")!! as GradleDslElementList
    buildConfigField.addNewElement(newLiteral(buildConfigField, "buildConfigField", "hello"))

    applyChangesAndReparse(buildModel)

    val expected = """
                   android {
                     buildTypes {
                       debug {
                         first = true
                         buildConfigField "String", "SOME_NAME", "some_value"
                         def var = sneaky
                         buildConfigField "String", "OTHER_NAME", "other_value"
                         buildConfigField = 'hello'
                         afterLastBuildConfig = 72
                         last = false
                       }
                     }
                   }""".trimIndent()
    verifyFileContents(myBuildFile, expected)
  }
}