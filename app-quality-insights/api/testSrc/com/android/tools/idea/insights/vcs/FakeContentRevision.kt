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
package com.android.tools.idea.insights.vcs

import com.intellij.dvcs.DvcsUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.history.ShortVcsRevisionNumber
import com.intellij.openapi.vcs.history.VcsRevisionNumber

data class FakeContentRevision(
  private val localFilePath: FilePath,
  private val revision: String,
  private val contentProvider: () -> String,
) : ContentRevision {
  override fun getContent(): String {
    return contentProvider.invoke()
  }

  override fun getFile(): FilePath {
    return localFilePath
  }

  override fun getRevisionNumber(): VcsRevisionNumber {
    return FakeVcsRevisionNumber(revision)
  }
}

data class FakeVcsRevisionNumber(private val revision: String) :
  VcsRevisionNumber, ShortVcsRevisionNumber {
  override fun compareTo(other: VcsRevisionNumber?): Int {
    return 0
  }

  override fun asString(): String {
    return revision
  }

  override fun toShortString(): String {
    return DvcsUtil.getShortHash(revision)
  }
}
