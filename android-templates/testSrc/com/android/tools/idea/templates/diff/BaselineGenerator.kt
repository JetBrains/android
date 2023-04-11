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
package com.android.tools.idea.templates.diff

import com.android.tools.idea.wizard.template.Template
import com.android.utils.FileUtils
import java.nio.file.Path

/**
 * Generates files from a template and performs checks on them to ensure they're valid and can be checked in as golden files, then copies
 * the validated files to the testData directory.
 */
class BaselineGenerator(template: Template) : ProjectRenderer(template) {
  override fun handleDirectories(goldenDir: Path, projectDir: Path) {
    FileUtils.deleteRecursivelyIfExists(goldenDir.toFile())
    FileUtils.copyDirectory(projectDir.toFile(), goldenDir.toFile())
    FILES_TO_IGNORE.forEach { FileUtils.deleteRecursivelyIfExists(goldenDir.resolve(it).toFile()) }
  }

  // TODO: build
  // TODO: lint
  // TODO: other checks
}

