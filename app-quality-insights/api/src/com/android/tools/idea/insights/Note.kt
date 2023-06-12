/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.insights

import java.time.Instant

/**
 * Note identifier for an issue.
 *
 * @param issueId The belonging issue identifier.
 * @param noteId The simple note id for an issue.
 * @param sessionId The identifier for a locally drafted note.
 */
data class NoteId(val issueId: IssueId, val noteId: String, val sessionId: String? = null)

/**
 * Developer notes for an issue.
 *
 * @param id The note identifier.
 * @param timestamp Time when this note was created.
 * @param author The email of the author of the note.
 * @param body The message body of the note.
 */
data class Note(
  val id: NoteId,
  val timestamp: Instant,
  val author: String,
  val body: String,
  val state: NoteState
)

/** State of the given note. */
enum class NoteState(val displayText: String) {
  CREATED(""),
  CREATING(" (sending...)"),
  DELETING(" (deleting...)");

  fun isPending() = this != CREATED
}
