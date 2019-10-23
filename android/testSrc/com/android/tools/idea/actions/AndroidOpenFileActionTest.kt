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
package com.android.tools.idea.actions

import com.android.tools.adtui.validation.Validator
import com.android.tools.idea.ui.validation.validators.PathValidator
import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.StubVirtualFile
import com.intellij.testFramework.IdeaTestCase
import junit.framework.TestCase
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.io.File
import kotlin.properties.Delegates

/**
 * Tests for [AndroidOpenFileAction]
 */
class AndroidOpenFileActionTest : IdeaTestCase() {

  fun testSelectableFiles() {
    val descriptor = mock(FileChooserDescriptor::class.java)
    `when`(descriptor.isFileSelectable(any<VirtualFile>())).thenReturn(true)

    val file = MockVirtualFile("test.txt")
    val issue = AndroidOpenFileAction.validateFiles(listOf(file), descriptor)
    TestCase.assertEquals(Validator.Result.OK, issue.result)
  }

  fun testNotSelectableFiles() {
    val descriptor = mock(FileChooserDescriptor::class.java)
    `when`(descriptor.isFileSelectable(any<VirtualFile>())).thenReturn(false)

    val file = MockVirtualFile("test.txt")
    val issue = AndroidOpenFileAction.validateFiles(listOf(file), descriptor)
    TestCase.assertEquals("Cannot open file " + file.presentableUrl, issue.result.message)
  }
}