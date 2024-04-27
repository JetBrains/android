/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.variant.conflict

import com.android.tools.idea.testing.JavaModuleModelBuilder.Companion.rootModuleBuilder
import com.android.tools.idea.testing.findAppModule
import com.android.tools.idea.testing.findModule
import com.android.tools.idea.testing.setupTestProjectFromAndroidModel
import com.google.common.collect.Iterables
import com.google.common.truth.Truth.assertThat
import java.io.File

/**
 * Tests for [ConflictSet].
 */
class ConflictSetTest : ConflictsTestCase() {

  fun testFindSelectionConflictsWithoutConflict() {
    setupTestProjectFromAndroidModel(project, File(myFixture.tempDirPath), rootModuleBuilder, appModuleBuilder(), libModuleBuilder())

    val conflicts = ConflictSet.findConflicts(project).selectionConflicts
    assertThat(conflicts).isEmpty()
  }

  fun testFindSelectionConflictsWithoutEmptyVariantDependency() {
    setupTestProjectFromAndroidModel(project, File(myFixture.tempDirPath), rootModuleBuilder, appModuleBuilder(dependOnVariant = ""), libModuleBuilder())

    val conflicts = ConflictSet.findConflicts(project).selectionConflicts
    assertThat(conflicts).isEmpty()
  }

  fun testFindSelectionConflictsWithoutNullVariantDependency() {
    setupTestProjectFromAndroidModel(project, File(myFixture.tempDirPath), rootModuleBuilder, appModuleBuilder(dependOnVariant = null), libModuleBuilder())

    val conflicts = ConflictSet.findConflicts(project).selectionConflicts
    assertThat(conflicts).isEmpty()
  }

  fun testFindSelectionConflictsWithConflict() {
    setupTestProjectFromAndroidModel(project, File(myFixture.tempDirPath), rootModuleBuilder, appModuleBuilder(dependOnVariant = "release"), libModuleBuilder())

    val conflicts = ConflictSet.findConflicts(project).selectionConflicts
    assertThat(conflicts).hasSize(1)

    val conflict = conflicts[0]

    assertThat(conflict.source).isSameAs(project.findModule("lib"))
    assertThat(conflict.selectedVariant).isEqualTo("debug")
    val affectedModules = conflict.affectedModules
    assertThat(affectedModules).hasSize(1)
    val affectedModule = Iterables.getOnlyElement(affectedModules)
    assertThat(affectedModule.target).isSameAs(project.findAppModule())
    assertThat(affectedModule.expectedVariant).isEqualTo("release")
  }
}
