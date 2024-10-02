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
package com.android.tools.idea.editing.metrics

import com.google.common.base.CaseFormat
import com.google.common.base.Enums
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

const val ADDED = "foobar"
const val REMOVED = "baz"

@RunWith(JUnit4::class)
class CodeEditingActionTest {
  @Test
  fun simpleCodeEditingActions() {
    fun CodeEditingAction.SimpleCodeEditingAction.toSource(): Source {
      val sourceName = CaseFormat.UPPER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, javaClass.simpleName)
      val source = Enums.getIfPresent(Source::class.java, sourceName)
      assertWithMessage("No Source found with name $sourceName").that(source).isPresent()
      return source.get()
    }

    for (simpleAction in CodeEditingAction.SimpleCodeEditingAction::class.sealedSubclasses) {
      val instance =
        simpleAction.objectInstance
          ?: throw AssertionError("${simpleAction.simpleName} must be a singleton object")
      assertThat(instance.getCodeEditedEvents(ADDED, REMOVED))
        .containsExactly(CodeEdited(ADDED.length, REMOVED.length, instance.toSource()))
    }
  }

  @Test
  fun pairedEnclosureInserted() {
    assertThat(CodeEditingAction.PairedEnclosureInserted(ADDED).getCodeEditedEvents(ADDED, REMOVED))
      .containsExactly(CodeEdited(ADDED.length, REMOVED.length, Source.TYPING))
  }

  @Test
  fun newLine() {
    assertThat(CodeEditingAction.NewLine.getCodeEditedEvents(ADDED, REMOVED))
      .containsExactly(CodeEdited(ADDED.length, REMOVED.length, Source.IDE_ACTION))
    assertThat(CodeEditingAction.NewLine.getCodeEditedEvents("\n$ADDED", REMOVED))
      .containsExactly(
        CodeEdited(1, REMOVED.length, Source.TYPING),
        CodeEdited(ADDED.length, 0, Source.IDE_ACTION),
      )
  }
}
