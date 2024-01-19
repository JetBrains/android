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

import com.intellij.dvcs.repo.Repository
import com.intellij.dvcs.repo.RepositoryImpl
import com.intellij.dvcs.repo.VcsRepositoryCreator
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vfs.VirtualFile

class FakeVcsRepositoryCreator(private val fakeVcs: AbstractVcs) : VcsRepositoryCreator {
  override fun createRepositoryIfValid(
    project: Project,
    root: VirtualFile,
    parentDisposable: Disposable,
  ): Repository {
    return object : RepositoryImpl(project, root, parentDisposable) {
      override fun getState(): Repository.State {
        TODO("Not yet implemented")
      }

      override fun getCurrentBranchName(): String? {
        TODO("Not yet implemented")
      }

      override fun getVcs(): AbstractVcs {
        return fakeVcs
      }

      override fun getCurrentRevision(): String? {
        TODO("Not yet implemented")
      }

      override fun update() {
        TODO("Not yet implemented")
      }

      override fun toLogString(): String {
        TODO("Not yet implemented")
      }
    }
  }

  override fun getVcsKey(): VcsKey {
    return fakeVcs.keyInstanceMethod
  }
}
