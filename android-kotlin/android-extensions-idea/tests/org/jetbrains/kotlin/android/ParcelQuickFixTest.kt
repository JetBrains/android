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
package org.jetbrains.kotlin.android

import com.android.testutils.TestUtils.getSdk
import org.jetbrains.kotlin.android.quickfix.AbstractAndroidQuickFixMultiFileTest

abstract class ParcelQuickFixTest : AbstractAndroidQuickFixMultiFileTest() {

  companion object {
    private const val TEST_DIR = "android-extensions-idea/testData/android/parcel/quickfix"
  }

  override fun setUp() {
    super.setUp()

    val androidSdk = getSdk()
    val androidJarDir = androidSdk.resolve("platforms").toFile().listFiles().first { it.name.startsWith("android-") }
    ConfigLibraryUtil.addLibrary(myFixture.module, "androidJar", androidJarDir.resolve("android.jar"))

    val kotlinPaths = ConfigLibraryUtil.kotlinPaths
    ConfigLibraryUtil.addLibrary(myFixture.module, "parcelizeRuntime", kotlinPaths.basePath.resolve("parcelize-runtime.jar"))
  }

  override fun tearDown() {
    ConfigLibraryUtil.removeLibrary(myFixture.module, "androidJar")
    ConfigLibraryUtil.removeLibrary(myFixture.module, "parcelizeRuntime")

    super.tearDown()
  }

  class AddPrimaryConstructor : ParcelQuickFixTest() {
    fun testConstructorWithDelegate() = doTest("$TEST_DIR/addPrimaryConstructor/constructorWithDelegate")
    fun testNoQuickFix() = doTest("$TEST_DIR/addPrimaryConstructor/noQuickFix")
    fun testSimple() = doTest("$TEST_DIR/addPrimaryConstructor/simple")
  }

  class CantBeInnerClass : ParcelQuickFixTest() {
    fun testSimple() = doTest("$TEST_DIR/cantBeInnerClass/simple")
  }

  class ClassShouldBeAnnotated : ParcelQuickFixTest() {
    fun testSimple() = doTest("$TEST_DIR/classShouldBeAnnotated/simple")
  }

  class DeleteIncompatible : ParcelQuickFixTest() {
    fun testCreatorField() = doTest("$TEST_DIR/deleteIncompatible/creatorField")
    fun testWriteToParcel() = doTest("$TEST_DIR/deleteIncompatible/writeToParcel")
  }

  class Migrations : ParcelQuickFixTest() {
    fun testBasic() = doTest("$TEST_DIR/migrations/basic")
    fun testComplexCase1() = doTest("$TEST_DIR/migrations/complexCase1")
    fun testCustomDescribeContents() = doTest("$TEST_DIR/migrations/customDescribeContents")
    fun testFromCreatorObject() = doTest("$TEST_DIR/migrations/fromCreatorObject")
    fun testInnerClassFactory() = doTest("$TEST_DIR/migrations/innerClassFactory")
    fun testJvmField() = doTest("$TEST_DIR/migrations/jvmField")
    fun testNoWriteToParcel() = doTest("$TEST_DIR/migrations/noWriteToParcel")
    fun testWithoutDescribeContents() = doTest("$TEST_DIR/migrations/withoutDescribeContents")
  }

  class NoParcelableSupertype : ParcelQuickFixTest() {
    fun testAlreadyHasSupertype() = doTest("$TEST_DIR/noParcelableSupertype/alreadyHasSupertype")
    fun testSimple() = doTest("$TEST_DIR/noParcelableSupertype/simple")
  }

  class PropertyWontBeSerialized : ParcelQuickFixTest() {
    fun testSimple() = doTest("$TEST_DIR/propertyWontBeSerialized/simple")
  }

  class RemoveDuplicatingTypeParcelerAnnotation : ParcelQuickFixTest() {
    fun testSimple() = doTest("$TEST_DIR/removeDuplicatingTypeParcelerAnnotation/simple")
  }
}
