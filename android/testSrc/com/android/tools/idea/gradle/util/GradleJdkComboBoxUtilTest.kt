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
package com.android.tools.idea.gradle.util

import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.projectRoots.impl.MockSdk
import com.intellij.openapi.projectRoots.impl.UnknownSdkType
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ui.configuration.SdkListItem
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.containers.MultiMap
import org.jetbrains.android.sdk.AndroidSdkType
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.util.collectionUtils.filterIsInstanceMapNotNull
import org.junit.Test
import kotlin.test.assertContentEquals

class GradleJdkComboBoxUtilTest : LightPlatformTestCase() {

  private val javaSdk by lazy { createSdk(JavaSdk.getInstance()) }
  private val androidSdk by lazy { createSdk(AndroidSdkType.getInstance()) }
  private val kotlinSdk by lazy { createSdk(KotlinSdkType.INSTANCE) }
  private val unknownSdk by lazy { createSdk(UnknownSdkType.getInstance("unknown")) }

  @Test
  fun testCreateJdkComboBoxItemsWithEmptyModel() {
    val sdksModel = ProjectSdksModel()
    createJdkComboBoxItems(sdksModel).run {
      assertEmpty(this)
    }
  }

  @Test
  fun testCreateJdkComboBoxItemsItemsWithJavaSdks() {
    val sdksModel = ProjectSdksModel()
      .addSdks(javaSdk, javaSdk, javaSdk)
    createJdkComboBoxItems(sdksModel).run {
      assertContentEquals(sdksModel.sdks, this)
    }
  }

  @Test
  fun testCreateJdkComboBoxItemsWithMultipleSdkTypes() {
    val sdksModel = ProjectSdksModel()
      .addSdks(androidSdk, androidSdk, javaSdk, kotlinSdk, unknownSdk)
    createJdkComboBoxItems(sdksModel).run {
      assertEquals(1, size)
      assertEquals(javaSdk.name, first().name)
    }
  }

  private fun createSdk(type: SdkType): Sdk {
    val rootsMap = MultiMap.create<OrderRootType, VirtualFile>()
    return MockSdk(type.name, "path", "version", rootsMap, type)
  }

  private fun createJdkComboBoxItems(sdksModel: ProjectSdksModel): Array<Sdk> {
    val modelBuilder = GradleJdkComboBoxUtil.createBoxModel(project, sdksModel).modelBuilder
    modelBuilder.reloadSdks()
    return modelBuilder.buildModel()
      .items
      .filterIsInstanceMapNotNull<SdkListItem.SdkItem, Sdk> { it.sdk }
      .toTypedArray()
  }

  private fun ProjectSdksModel.addSdks(vararg sdks: Sdk): ProjectSdksModel {
    sdks.forEach { addSdk(it) }
    return this
  }
}
