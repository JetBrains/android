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

import com.intellij.openapi.diagnostic.thisLogger

/** An action that has changed the code in some way, for some reason. */
sealed interface CodeEditingAction {
  fun getCodeEditedEvents(addedText: String, removedText: String): List<CodeEdited>

  /** A common base class for other [CodeEditingAction]s. */
  sealed class SimpleCodeEditingAction(private val source: Source) : CodeEditingAction {
    final override fun getCodeEditedEvents(addedText: String, removedText: String) =
      listOf(CodeEdited(addedText.length, removedText.length, source))
  }

  /**
   * We do not know what caused the code to change.
   *
   * Note that this can happen for many reasons. We may be incapable of knowing what caused the
   * change, or we may be capable of knowing but did not believe that tracking the cause was
   * worthwhile (effort, complexity of the stored data, etc).
   *
   * Here is a non-exhaustive list of "known unknowns", i.e. actions that we know will trigger
   * [Unknown] as the source of changes:
   * * Undo/Redo actions
   * * Cut actions (including Emacs-style "kill" actions which are implemented separately)
   * * Hitting backspace/delete when text is selected
   * * Changes to the document that happen while a refactor is being set up, but not actually
   *   executed - e.g. when renaming a variable using the in-editor version, the changes to the
   *   current document will show up as [Unknown], but once the refactor actually starts (to make
   *   changes to other files), those will correctly report as [Refactoring].
   *
   * **As more cases are discovered, they should be added to this list. As cases are eliminated by
   * categorizing them correctly, they should be removed from this list.**
   */
  data object Unknown : SimpleCodeEditingAction(Source.UNKNOWN)

  /**
   * The user has typed characters into the editor using the keyboard, or hit backspace without
   * selecting any text.
   */
  data object Typing : SimpleCodeEditingAction(Source.TYPING)

  /** The user has pasted content into the editor. */
  data object UserPaste : SimpleCodeEditingAction(Source.USER_PASTE)

  /**
   * The user used the IDE to refactor code. This only covers the actual execution of the
   * refactoring, and not any code editing that happens as part of configuring the refactoring
   * action.
   */
  data object Refactoring : SimpleCodeEditingAction(Source.REFACTORING)

  /** The user accepted a deterministic, IDE-generated code completion. */
  data object CodeCompletion : SimpleCodeEditingAction(Source.CODE_COMPLETION)

  /**
   * Represents an automatically inserted closure, such as when the user types '(' and the paired
   * ')' character is inserted after the cursor.
   */
  data class PairedEnclosureInserted(val text: String) : CodeEditingAction {
    override fun getCodeEditedEvents(addedText: String, removedText: String): List<CodeEdited> {
      if (addedText != text) {
        thisLogger().error("""Expected "$text", got "$addedText".""")
      }
      return listOf(CodeEdited(addedText.length, removedText.length, Source.TYPING))
    }
  }

  /** The user has typed a newline into the editor. */
  data object NewLine : CodeEditingAction {
    override fun getCodeEditedEvents(addedText: String, removedText: String): List<CodeEdited> =
      if (addedText.contains('\n')) {
        // When the user hits enter, we get a newline and all the following indentation. Only count
        // the newline as typing.
        // Additionally, if there's extra indentation it'll come in as a second bit of added text
        // without a new line; don't count that as typing either.
        listOf(
          CodeEdited(1, removedText.length, Source.TYPING),
          CodeEdited(addedText.length - 1, 0, Source.IDE_ACTION),
        )
      } else {
        listOf(CodeEdited(addedText.length, removedText.length, Source.IDE_ACTION))
      }
  }

  /** The user has accepted code completion powered by AI. */
  data object AiCodeCompletion : SimpleCodeEditingAction(Source.AI_CODE_COMPLETION)

  /** The user has accepted AI-generated code. */
  data object AiCodeGeneration : SimpleCodeEditingAction(Source.AI_CODE_GENERATION)

  /** The user has accepted a suggestion from a chat with an AI agent. */
  data object PasteFromAiChat : SimpleCodeEditingAction(Source.PASTE_FROM_AI_CHAT)
}
