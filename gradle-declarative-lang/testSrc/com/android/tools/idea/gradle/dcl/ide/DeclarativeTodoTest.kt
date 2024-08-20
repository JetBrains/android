/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.dcl.ide

import com.intellij.editor.TodoItemsTestCase

class DeclarativeTodoTest : TodoItemsTestCase() {
  override fun getFileExtension(): String = "build.dcl"
  override fun supportsCStyleMultiLineComments(): Boolean = false
  override fun supportsCStyleSingleLineComments(): Boolean = false

  fun `test single line todo`() = testTodos("""
        // [TODO first line]
        // second line
    """)

  fun `test single line todo2`() = testTodos("""
        // [TODO first line]
        //  [second line]
    """)

  fun `test block comment todo`() = testTodos("""
       /* [TODO first line]
          next line
       */
    """)

  fun `test block comment todo2`() = testTodos("""
       /* [TODO first line]
           [next line]
       */
    """)
}