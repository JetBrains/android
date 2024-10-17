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
import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import junit.framework.TestCase
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

/**
 * Tests for [AndroidOpenFileAction]
 */
class AndroidOpenFileActionTest : TestCase() {

  fun testSelectableFiles() {
    val descriptor = mock(FileChooserDescriptor::class.java)
    whenever(descriptor.isFileSelectable(any())).thenReturn(true)

    val file = MockVirtualFile("test.txt")
    val issue = AndroidOpenFileAction.validateFiles(listOf(file), descriptor)
    assertEquals(Validator.Result.OK, issue.result)
  }

  fun testNotSelectableFiles() {
    val descriptor = mock(FileChooserDescriptor::class.java)
    whenever(descriptor.isFileSelectable(any())).thenReturn(false)

    val file = MockVirtualFile("test.txt")
    val issue = AndroidOpenFileAction.validateFiles(listOf(file), descriptor)
    assertEquals("Cannot open file " + file.presentableUrl, issue.result.message)
  }
}