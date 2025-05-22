/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.screenshottest.ui.diffviewer

import com.intellij.diff.contents.DiffContent
import com.intellij.diff.requests.ContentDiffRequest
import com.intellij.openapi.project.Project

/**
 * Custom diff request for ThreesideImageDiffViewer
 */
class ImageDiffRequest(val project: Project, private val diffContentList: MutableList<DiffContent>) : ContentDiffRequest() {
  override fun getTitle(): String = "Screenshot Test Diff"

  override fun getContents(): MutableList<DiffContent> = diffContentList

  override fun getContentTitles(): MutableList<String> = diffContentList.map { (it as ImageDiffContent).title }.toMutableList()
}