/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.navigator.nodes

import com.android.tools.idea.flags.StudioFlags
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.guessProjectDir
import org.jetbrains.android.AndroidTestCase
import org.mockito.Mockito.mock

class AndroidViewProjectNodeTest : AndroidTestCase() {
  override fun setUp() {
    super.setUp()
    StudioFlags.IMPORT_PROJECT_ENABLED.override(true)
  }

  override fun tearDown() {
    StudioFlags.IMPORT_PROJECT_ENABLED.clearOverride()
    super.tearDown()
  }

  fun testGetChildrenWithMigrationDir() {
    val projectBaseDir = project.guessProjectDir()!!
    runWriteAction {
      val migrationDir = projectBaseDir.createChildDirectory(this, ".migration")
      migrationDir.createChildData(this, "test.txt")
    }

    val settings = mock(ViewSettings::class.java)
    val projectNode = AndroidViewProjectNode(project, settings)
    val children = projectNode.children

    val migrationNode = children.filterIsInstance<MigrationArtifactsGroupNode>().singleOrNull()
    assertNotNull(migrationNode)
    migrationNode!!.update()
    assertEquals("Migration Artifacts", migrationNode.presentation.presentableText)

    val migrationChildren = migrationNode.children
    // Should contain contents of .migration directly
    assertTrue(migrationChildren.any { it is PsiFileNode && it.virtualFile?.name == "test.txt" })
  }

  fun testGetChildrenFlattened() {
    val projectBaseDir = project.baseDir!!
    runWriteAction {
      val migrationDir = projectBaseDir.createChildDirectory(this, ".migration")
      val importDir = migrationDir.createChildDirectory(this, "import")
      importDir.createChildData(this, "spec.md")
    }

    val settings = mock(ViewSettings::class.java)
    val migrationDir = projectBaseDir.findChild(".migration")!!
    val migrationNode = MigrationArtifactsGroupNode(project, settings, migrationDir)

    val migrationChildren = migrationNode.children
    // .migration has ONLY 'import' directory, so it should be flattened.
    // It should show children of 'import', which is 'spec.md'.
    assertEquals(1, migrationChildren.size)
    assertTrue(migrationChildren.any { it is PsiFileNode && it.virtualFile?.name == "spec.md" })
  }

  fun testGetChildrenWithoutMigrationDir() {
    val settings = mock(ViewSettings::class.java)
    val projectNode = AndroidViewProjectNode(project, settings)
    val children = projectNode.children

    assertFalse(children.any { it is MigrationArtifactsGroupNode })
  }
}
