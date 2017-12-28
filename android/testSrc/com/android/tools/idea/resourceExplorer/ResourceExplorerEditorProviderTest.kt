/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer

import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LeakHunter
import org.gradle.api.Project
import org.junit.Assert

class ResourceExplorerEditorProviderTest : ResourceExplorerTestCase() {

  fun testAccept() {
    ResourceExplorerTestCase.runWithResourceExplorerFlagDisabled {
      Assert.assertFalse(ResourceExplorerEditorProvider().accept(project, ResourceExplorerFile.getResourceEditorFile(project, myFacet)))
    }
    Assert.assertTrue(ResourceExplorerEditorProvider().accept(project, ResourceExplorerFile.getResourceEditorFile(project, myFacet)))
  }

  fun testCreateEditor() {
    val file = ResourceExplorerFile.getResourceEditorFile(project, myFacet)
    val editor = ResourceExplorerEditorProvider().createEditor(project, file)
    Assert.assertTrue(editor is ResourceExplorerEditor)
    val providers = FileEditorProviderManager.getInstance().getProviders(project, file)
    Assert.assertEquals(1, providers.size)
    Assert.assertTrue(providers[0] is ResourceExplorerEditorProvider)
    Disposer.dispose(editor)
  }
}