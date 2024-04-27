/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.editors

import com.android.test.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * List find usages for methods, classes, and fields in both Java and Kotlin.
 *
 * This test does not validate a specific piece of code, but rather functionality that comes from the IJ platform and should be correctly
 * configured in Android Studio.
 */
@RunWith(JUnit4::class)
@RunsInEdt
class FindUsagesJavaKotlinTest {
  @get:Rule
  val androidProjectRule = AndroidProjectRule.onDisk().onEdt()

  private val myFixture by lazy {
    androidProjectRule.fixture.apply {
      testDataPath = resolveWorkspacePath("tools/adt/idea/android/testData").toString()
    } as JavaCodeInsightTestFixture
  }

  @Before
  fun setUp() {
    myFixture.copyDirectoryToProject("findUsagesJavaKotlin", "src/com/codegeneration")
  }

  @Test
  fun findUsagesPrivateMethodJava() {
    myFixture.openFileInEditor(myFixture.findClass("com.codegeneration.MainActivity").containingFile.virtualFile)
    myFixture.moveCaret("void addNew|Car()")

    val usageRepresentation = myFixture.getUsageViewTreeTextRepresentation(myFixture.elementAtCaret)

    // There should only be one usage
    assertThat(usageRepresentation).contains("<root> (1)")

    // The usage should be in MainActivity.onCreate
    assertThat(usageRepresentation).contains(
      """
      |    com.codegeneration (1)
      |     MainActivity (1)
      |      onCreate(Bundle) (1)
      |       14addNewCar();
      """.trimMargin())

    // Inventory.kt also contains a method names `addNewCar`. It should not be included in results.
    assertThat(usageRepresentation).doesNotContain("Inventory")
  }

  @Test
  fun findUsagesPublicFunctionKotlin() {
    myFixture.openFileInEditor(myFixture.findClass("com.codegeneration.Inventory").containingFile.virtualFile)
    myFixture.moveCaret("fun addNew|Car()")

    val usageRepresentation = myFixture.getUsageViewTreeTextRepresentation(myFixture.elementAtCaret)

    // There should be two usages
    assertThat(usageRepresentation).contains("<root> (2)")

    // One usage should be in Inventory's init
    assertThat(usageRepresentation).contains(
      """
      |    com.codegeneration (1)
      |     Inventory.kt (1)
      |      Inventory (1)
      |       6addNewCar() // function call
      """.trimMargin())

    // One usage should be in MainActivity.onCreate
    assertThat(usageRepresentation).contains(
      """
      |    com.codegeneration (1)
      |     MainActivity (1)
      |      onCreate(Bundle) (1)
      |       15new Inventory().addNewCar();
      """.trimMargin())
  }

  @Test
  fun findUsagesJavaClass() {
    myFixture.openFileInEditor(myFixture.findClass("com.codegeneration.Car").containingFile.virtualFile)
    myFixture.moveCaret("public class C|ar")

    val usageRepresentation = myFixture.getUsageViewTreeTextRepresentation(myFixture.elementAtCaret)

    // There should be five usages of this class: two in MainActivity.java, two in Car.java, and one usage in Inventory.kt
    assertThat(usageRepresentation).contains("<root> (5)")

    assertThat(usageRepresentation).contains(
      """
      |  Class static member access (1)
      |   app (1)
      |    com.codegeneration (1)
      |     MainActivity (1)
      |      addNewCar() (1)
      |       19Car car = Car.getInstance();
      """.trimMargin()
    )

    assertThat(usageRepresentation).contains(
      """
      |  Local variable declaration (1)
      |   app (1)
      |    com.codegeneration (1)
      |     MainActivity (1)
      |      addNewCar() (1)
      |       19Car car = Car.getInstance();
      """.trimMargin()
    )

    assertThat(usageRepresentation).contains(
      """
      |  Method return type (1)
      |   app (1)
      |    com.codegeneration (1)
      |     Car (1)
      |      getInstance() (1)
      |       38public static Car getInstance(){
      """.trimMargin()
    )

    assertThat(usageRepresentation).contains(
      """
      |  New instance creation (1)
      |   app (1)
      |    com.codegeneration (1)
      |     Car (1)
      |      getInstance() (1)
      |       39return new Car();
      """.trimMargin()
    )

    assertThat(usageRepresentation).contains(
      """
      |  Nested class/object (1)
      |   app (1)
      |    com.codegeneration (1)
      |     Inventory.kt (1)
      |      Inventory (1)
      |       addNewCar (1)
      |        10val car = Car.getInstance()
      """.trimMargin()
    )
  }

  @Test
  fun findUsagesJavaField() {
    myFixture.openFileInEditor(myFixture.findClass("com.codegeneration.Car").containingFile.virtualFile)
    myFixture.moveCaret("private String ma|ke;")

    val usageRepresentation = myFixture.getUsageViewTreeTextRepresentation(myFixture.elementAtCaret)

    // Two usages within Car.java expected.
    assertThat(usageRepresentation).contains("<root> (2)")

    assertThat(usageRepresentation).contains(
      """
      |  Value read (1)
      |   app (1)
      |    com.codegeneration (1)
      |     Car (1)
      |      getMake() (1)
      |       10return make;
      """.trimMargin()
    )

    assertThat(usageRepresentation).contains(
      """
      |  Value write (1)
      |   app (1)
      |    com.codegeneration (1)
      |     Car (1)
      |      setMake(String) (1)
      |       22this.make = (make.equals(""))? "BMW" : make;
      """.trimMargin()
    )
  }
}
