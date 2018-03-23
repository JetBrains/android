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

import com.android.tools.idea.gradle.dsl.api.GradleFileModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.*
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.*
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType.REGULAR
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType.VARIABLE
import com.android.tools.idea.gradle.dsl.api.util.GradleDslModel
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel
import com.android.tools.idea.gradle.dsl.model.GradleFileModelImpl
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

  private fun GradleFileModel.dslElement() : GradlePropertiesDslElement {
    assert(this is GradleFileModelImpl)
    val field = GradleFileModelImpl::class.java.getDeclaredField("myGradleDslFile")
    field.isAccessible = true
    return field.get(this) as GradlePropertiesDslElement
  }

  private class TestBlockElement(parent : GradleDslElement, name : String) : GradleDslBlockElement(parent, GradleNameElement.create(name))

  private fun newLiteral(parent : GradleDslElement, name : String, value : Any) : GradleDslLiteral {
    val newElement = GradleDslLiteral(parent, GradleNameElement.create(name))
    newElement.setValue(value)
    newElement.setUseAssignment(true)
    newElement.elementType = REGULAR
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

    run {
      val firstProperty = buildModel.ext().findProperty("prop1")
      firstProperty.setValue(1)
      val secondProperty = buildModel.ext().findProperty("prop2")
      secondProperty.setValue("2")
      val thirdProperty = buildModel.ext().findProperty("prop3")
      thirdProperty.convertToEmptyList().addListValue().setValue(3)
      val forthProperty = buildModel.ext().findProperty("prop4")
      forthProperty.convertToEmptyMap().getMapValue("key").setValue("4")
    }

    fun verify() {
      val firstProperty = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(firstProperty, INTEGER_TYPE, 1, INTEGER, REGULAR, 0, "prop1")
      val secondProperty = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(secondProperty, STRING_TYPE, "2", STRING, REGULAR, 0, "prop2")
      val thirdProperty = buildModel.ext().findProperty("prop3")
      verifyListProperty(thirdProperty, listOf(3), REGULAR, 0, "prop3")
      val forthProperty = buildModel.ext().findProperty("prop4")
      verifyMapProperty(forthProperty, mapOf("key" to "4"), REGULAR, 0, "prop4")
    }

    verify()

    applyChangesAndReparse(buildModel)

    val expected = """
                   ext {
                     prop1 = 1
                     prop2 = '2'
                     prop3 = [3]
                     prop4 = [key : '4']
                   }""".trimIndent()
    verifyFileContents(myBuildFile, expected)
    verify()
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

    fun verify() {
      val firstModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(firstModel, INTEGER_TYPE, 1, INTEGER, REGULAR, 0, "prop1")
      val secondModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(secondModel, INTEGER_TYPE, 2, INTEGER, REGULAR, 0, "prop2")
      val thirdModel = buildModel.ext().findProperty("prop3")
      verifyPropertyModel(thirdModel, INTEGER_TYPE, 3, INTEGER, REGULAR, 0, "prop3")
    }

    verify()

    applyChangesAndReparse(buildModel)

    val expected = """
                   ext {
                     prop1 = 1
                     prop2 = 2
                     prop3 = 3
                   }""".trimIndent()
    verifyFileContents(myBuildFile, expected)
    verify()
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

    fun verify() {
      val firstModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(firstModel, STRING_TYPE, "1", STRING, REGULAR, 0, "prop1")
      val secondModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(secondModel, INTEGER_TYPE, 2, INTEGER, REGULAR, 0, "prop2")
      val thirdModel = buildModel.ext().findProperty("prop3")
      verifyPropertyModel(thirdModel, INTEGER_TYPE, 3, INTEGER, REGULAR, 0, "prop3")
      val forthModel = buildModel.ext().findProperty("prop4")
      verifyPropertyModel(forthModel, INTEGER_TYPE, 4, INTEGER, REGULAR, 0, "prop4")
    }

    verify()

    applyChangesAndReparse(buildModel)

    val expected = """
                   ext {
                     prop1 = '1'
                     prop2 = 2
                     prop3 = 3
                     prop4 = 4
                   }""".trimIndent()
    verifyFileContents(myBuildFile, expected)
    verify()
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

  @Test
  fun testMoveBlockStatement() {
    val text = """
               android {
                 buildTypes {
                   debug {
                     def var = sneaky
                   }
                 }
               }
               ext {
                 prop1 = "hello"
                 prop2 = true
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val extElement = buildModel.ext().dslElement()
    val fileElement = buildModel.dslElement()

    fileElement.moveElementTo(0, extElement)

    applyChangesAndReparse(buildModel)

    val expected = """
                   ext {
                     prop1 = "hello"
                     prop2 = true
                   }
                   android {
                     buildTypes {
                       debug {
                         def var = sneaky
                       }
                     }
                   }""".trimIndent()
    verifyFileContents(myBuildFile, expected)

    // Check that the build model still works.
    val ext = buildModel.ext()
    val extProperties = ext.declaredProperties
    assertSize(2, extProperties)
    verifyPropertyModel(extProperties[0], STRING_TYPE, "hello", STRING, REGULAR, 0, "prop1")
    verifyPropertyModel(extProperties[1], BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 0, "prop2")
    val debug = buildModel.android()!!.buildTypes()[0]!!
    val debugProperties = debug.declaredProperties
    assertSize(1, debugProperties)
    verifyPropertyModel(debugProperties[0], STRING_TYPE, "sneaky", REFERENCE, VARIABLE, 0, "var")

    // Delete one and edit one to make sure the PsiElements are still valid for these operations.
    extProperties[0].delete()
    extProperties[1].setValue(43)
    extProperties[1].rename("prop")

    val newExtProperties = ext.declaredProperties
    assertSize(1, newExtProperties)
    verifyPropertyModel(newExtProperties[0], INTEGER_TYPE, 43, INTEGER, REGULAR, 0, "prop")

    applyChangesAndReparse(buildModel)

    // Check the resulting file again.
    val finalExpected = """
                        ext {
                          prop = 43
                        }
                        android {
                          buildTypes {
                            debug {
                              def var = sneaky
                            }
                          }
                        }""".trimIndent()
    verifyFileContents(myBuildFile, finalExpected)
  }

  @Test
  fun testMoveBasicStatement() {
    val text = """
               apply plugin: 'com.android.application'

               ext {
                 prop1 = "3"
                 def var1 = 2
                 prop2 = [1, true, "1"]
                 def var3 = [key: 6, key1: "7"]
                 def var2 = var1
               }
               android {
                 compileSdkVersion 19
                 buildToolsVersion "19.1.0"

                 signingConfigs {
                   myConfig {
                     storeFile file("debug.keystore")
                     storePassword "android"
                     keyAlias "androiddebugkey"
                     keyPassword "android"
                    }
                 }
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val extElement = buildModel.ext().dslElement()
    val fileElement = buildModel.dslElement()
    val extElementMap = extElement.elements

    // Reorder the elements inside the ext block.
    extElement.moveElementTo(0, extElementMap["prop2"]!!)
    extElement.moveElementTo(2, extElementMap["prop1"]!!)
    extElement.moveElementTo(extElementMap.size, extElementMap["var3"]!!)

    // Move the Ext block to the bottom of the file.
    fileElement.moveElementTo(Int.MAX_VALUE, extElement)

    fun verify() {
      val firstModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(firstModel, STRING_TYPE, "3", STRING, REGULAR, 0, "prop1")
      val firstVarModel = buildModel.ext().findProperty("var1")
      verifyPropertyModel(firstVarModel, INTEGER_TYPE, 2, INTEGER, VARIABLE, 0, "var1")
      val secondModel = buildModel.ext().findProperty("prop2")
      verifyListProperty(secondModel, listOf(1, true, "1"), REGULAR, 0, "prop2")
      val secondVarModel = buildModel.ext().findProperty("var2")
      verifyPropertyModel(secondVarModel, STRING_TYPE, "var1", REFERENCE, VARIABLE, 1, "var2")
      val thirdVarModel = buildModel.ext().findProperty("var3")
      verifyMapProperty(thirdVarModel, mapOf("key" to 6, "key1" to "7"), VARIABLE, 0, "var3")
    }

    verify()

    applyChangesAndReparse(buildModel)

    val expected = """
                   apply plugin: 'com.android.application'

                   android {
                     compileSdkVersion 19
                     buildToolsVersion "19.1.0"

                     signingConfigs {
                       myConfig {
                         storeFile file("debug.keystore")
                         storePassword "android"
                         keyAlias "androiddebugkey"
                         keyPassword "android"
                       }
                     }
                   }
                   ext {
                     prop2 = [1, true, "1"]
                     def var1 = 2
                     prop1 = "3"
                     def var2 = var1
                     def var3 = [key: 6, key1: "7"]
                   }""".trimIndent()
    verifyFileContents(myBuildFile, expected)
    verify()
  }

  @Test
  fun testCreateThenMoveBlock() {
    val text = """
               android {
                 compileSdkVersion 19
                 buildToolsVersion "19.1.0"
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    // Create a property, this will create an ext element.
    val propertyModel = buildModel.ext().findProperty("prop1")
    propertyModel.setValue(true)

    // Then move it to the top of the file.
    val extElement = buildModel.ext().dslElement()
    val fileElement = buildModel.dslElement()
    fileElement.moveElementTo(0, extElement)

    applyChangesAndReparse(buildModel)

    val expected = """
                   ext {
                     prop1 = true
                   }
                   android {
                     compileSdkVersion 19
                     buildToolsVersion "19.1.0"
                   }
                   """.trimIndent()
    verifyFileContents(myBuildFile, expected)
  }

  @Test
  fun testCreateThenMove() {
    val text = """
               ext {
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val extElement = buildModel.ext().dslElement()

    // Create three properties.
    val firstModel = buildModel.ext().findProperty("prop1")
    firstModel.convertToEmptyMap()
    firstModel.getMapValue("key").setValue(1)
    firstModel.getMapValue("key1").setValue("two")
    val secondModel = buildModel.ext().findProperty("prop2")
    secondModel.setValue(72)

    // Move them around
    val elementMap = extElement.elements
    extElement.moveElementTo(0, elementMap["prop2"]!!)
    // Note: Even though this is effectively a no-op, a move of the property still occurs.
    extElement.moveElementTo(1, elementMap["prop1"]!!)

    applyChangesAndReparse(buildModel)

    val expected = """
                   ext {
                     prop2 = 72
                     prop1 = [key1: 'two', key: 1]
                   }""".trimIndent()
    verifyFileContents(myBuildFile, expected)
  }

  @Test
  fun testEditBeforeMove() {
    val text = """
               ext {
                 prop1 = "hello"
                 prop2 = "swap around me :)"
                 prop3 = [1, 2, 3]
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    val extElement = buildModel.ext().dslElement()

    // Edit the elements
    val firstModel = buildModel.ext().findProperty("prop1")
    firstModel.convertToEmptyList().addListValue().setValue("one")
    firstModel.addListValue().setValue("two")
    firstModel.addListValue().setValue("three")
    val secondModel = buildModel.ext().findProperty("prop3")
    secondModel.setValue(true)

    // We need to get the map after since editing the elements will cause it to change.
    val extElementMap = extElement.elements

    // Swap prop1 and prop3
    extElement.moveElementTo(3, extElementMap["prop1"]!!)
    extElement.moveElementTo(0, extElementMap["prop3"]!!)

    applyChangesAndReparse(buildModel)

    val expected = """
                   ext {
                     prop3 = true
                     prop2 = "swap around me :)"
                     prop1 = ['one', 'two', 'three']
                   }""".trimIndent()
    verifyFileContents(myBuildFile, expected)
  }

  @Test
  fun testMoveBeforeEdit() {
    val text = """
               ext {
                 prop1 = "hello"
                 prop2 = "swap around me :)"
                 prop3 = [1, 2, 3]
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    val extElement = buildModel.ext().dslElement()
    val extElementMap = extElement.elements

    // Swap prop1 and prop3
    extElement.moveElementTo(3, extElementMap["prop1"]!!)
    extElement.moveElementTo(0, extElementMap["prop3"]!!)

    // Edit the elements
    val firstModel = buildModel.ext().findProperty("prop1")
    firstModel.convertToEmptyList().addListValue().setValue("one")
    firstModel.addListValue().setValue("two")
    firstModel.addListValue().setValue("three")
    val secondModel = buildModel.ext().findProperty("prop3")
    secondModel.setValue(true)

    applyChangesAndReparse(buildModel)

    val expected = """
                   ext {
                     prop3 = true
                     prop2 = "swap around me :)"
                     prop1 = ['one', 'two', 'three']
                   }""".trimIndent()
    verifyFileContents(myBuildFile, expected)
  }

  @Test
  fun testMoveCommentsInBlock() {
    val text = """
               apply plugin: 'com.android.application'
                /*
                  This is a top level comment*/

               android {
                 /*
                   This is a really cool comment!!
                   And look, it is on multiple lines!
                 */
                 compileSdkVersion 19
                 buildToolsVersion "19.1.0"
               }
               ext {
                 /* This is another
                 block comment
                 on lots of lines*/
                 prop1 = 1
                 /**
                 // This is another comment
                 **/
                 prop2 = prop1
                 /* and a last one
                 this one start on a line, who would do this???*/prop3 = "hello"
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    val fileElement = buildModel.dslElement()
    val extElement = buildModel.ext().dslElement()

    // Move the ext block to the top
    fileElement.moveElementTo(1, extElement)

    applyChangesAndReparse(buildModel)
    val expected = """
                   apply plugin: 'com.android.application'
                   ext {
                     /* This is another
                        block comment
                        on lots of lines*/
                     prop1 = 1
                     /**
                      // This is another comment
                     **/
                     prop2 = prop1
                     /* and a last one
                     this one start on a line, who would do this???*/prop3 = "hello"
                   }
                   /*
                     This is a top level comment */

                   android {
                     /*
                       This is a really cool comment!!
                       And look, it is on multiple lines!
                     */
                     compileSdkVersion 19
                     buildToolsVersion "19.1.0"
                   }
                   """.trimIndent()
    verifyFileContents(myBuildFile, expected)
  }

  // TODO: Comments don't get moved with the line they are on.
  @Test
  fun /*test*/MoveBasicWithComments() {
    val text = """
               ext {
                 prop1 = value1 // This is a comment
                 prop2 = 'hello' /* this is also a comment */
                 prop3 = "Boo" // This is another comment
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val extElement = buildModel.ext().dslElement()

    val extElementMap = extElement.elements

    // Move all of the elements around
    extElement.moveElementTo(0, extElementMap["prop3"]!!)
    extElement.moveElementTo(2, extElementMap["prop1"]!!)
    extElement.moveElementTo(0, extElementMap["prop2"]!!)

    fun verify() {
      val firstModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(firstModel, STRING_TYPE, "value1", REFERENCE, REGULAR, 0, "prop1")
      val secondModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(secondModel, STRING_TYPE, "hello", STRING, REGULAR, 0, "prop2")
      val thirdModel = buildModel.ext().findProperty("prop3")
      verifyPropertyModel(thirdModel, STRING_TYPE, "Boo", STRING, REGULAR, 0, "prop3")
    }

    verify()

    applyChangesAndReparse(buildModel)
    val expected = """
                   ext {
                     prop2 = 'hello' /* this is also a comment */
                     prop3 = "Boo" // This is another comment
                     prop1 = value1 // This is a comment
                   }""".trimIndent()
    verifyFileContents(myBuildFile, expected)
    verify()
  }

  @Test
  fun testMoveBlockWithUnparsedContent() {
    val text = """
               ext {
                 prop1 = 3 + 5
                 prop2 = var.invokeMethod()
                 prop2 = hello("goodbye")
                 def var = System.out.println("Boo")
                 prop4 = true
               }
               android {
                   compileSdkVersion 19
                   buildToolsVersion "19.1.0"
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val extElement = buildModel.ext().dslElement()
    val fileElement = buildModel.dslElement()

    val extElementMap = extElement.elements

    // Move the block containing the unknown elements.
    fileElement.moveElementTo(1, extElement)

    // Move the normal one to the middle of the unknown properties.
    extElement.moveElementTo(2, extElementMap["prop4"]!!)
    // Move some unknown properties around.
    extElement.moveElementTo(0, extElementMap["prop2"]!!)
    extElement.moveElementTo(4, extElementMap["var"]!!)

    applyChangesAndReparse(buildModel)
    val expected = """
                   android {
                     compileSdkVersion 19
                     buildToolsVersion "19.1.0"
                   }
                   ext {
                     prop2 = hello("goodbye")
                     prop1 = 3 + 5
                     prop2 = var.invokeMethod()
                     prop4 = true
                     def var = System.out.println("Boo")
                   }""".trimIndent()
    verifyFileContents(myBuildFile, expected)
  }

  @Test
  fun testWrongOrderNoDependency() {
    val text = """
               ext {
                 prop1 = prop2
                 prop2 = "hello"
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    run {
      val firstModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(firstModel, STRING_TYPE, "prop2", REFERENCE, REGULAR, 0, "prop1")
      val secondModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(secondModel, STRING_TYPE, "hello", STRING, REGULAR, 0, "prop2")
    }
  }

  @Test
  fun testDirectReferenceExtCorrectOrder() {
    val text = """
               ext {
                 prop1 = 10
                 prop1 = 20
               }

               android {
                 defaultConfig {
                   minSdkVersion prop1
                 }
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val propertyModel = buildModel.android()!!.defaultConfig().minSdkVersion()
    verifyPropertyModel(propertyModel, INTEGER_TYPE, 20, INTEGER, REGULAR, 1, "minSdkVersion")
  }

  @Test
  fun testAboveExt() {
    val text = """
               android {
                 defaultConfig {
                   minSdkVersion minSdk
                   maxSdkVersion maxSdk
                 }
               }

               ext {
                 minSdk 14
                 maxSdk 18
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    val minSdkModel = buildModel.android()!!.defaultConfig().minSdkVersion()
    val maxSdkModel = buildModel.android()!!.defaultConfig().maxSdkVersion()

    verifyPropertyModel(minSdkModel, STRING_TYPE, "minSdk", REFERENCE, REGULAR, 0, "minSdkVersion")
    verifyPropertyModel(maxSdkModel, STRING_TYPE, "maxSdk", REFERENCE, REGULAR, 0, "maxSdkVersion")
  }

  @Test
  fun testAboveExtQualifiedReference() {
    val text = """
               android {
                 defaultConfig {
                   minSdkVersion ext.minSdk
                   maxSdkVersion ext.maxSdk
                 }
               }

               ext {
                 minSdk 14
                 maxSdk 18
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    val minSdkModel = buildModel.android()!!.defaultConfig().minSdkVersion()
    val maxSdkModel = buildModel.android()!!.defaultConfig().maxSdkVersion()

    verifyPropertyModel(minSdkModel, STRING_TYPE, "ext.minSdk", REFERENCE, REGULAR, 0, "minSdkVersion")
    verifyPropertyModel(maxSdkModel, STRING_TYPE, "ext.maxSdk", REFERENCE, REGULAR, 0, "maxSdkVersion")
  }

  @Test
  fun testResolveToLastProperty() {
    val text = """
               ext {
                 def var1 = "hello"
                 def var1 = "goodbye"
                 def var2 = "on"
                 def var2 = "off"

                 greeting = var1
                 state = var2
               }

               android {
                 signingConfigs {
                   myConfig {
                     storeFile file(greeting)
                     storePassword state
                   }
                 }
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val configModel = buildModel.android()!!.signingConfigs()[0]!!
    val fileModel = configModel.storeFile()
    val passwordModel = configModel.storePassword()

    verifyPropertyModel(fileModel, STRING_TYPE, "goodbye", STRING, REGULAR, 1, "storeFile")
    verifyPropertyModel(passwordModel.resolve(), STRING_TYPE, "off", STRING, REGULAR, 1, "storePassword")
  }
}