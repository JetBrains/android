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
package com.android.tools.idea.run.deployment.liveedit.k2

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.components.KtCompiledFile
import org.jetbrains.kotlin.backend.common.output.OutputFile
import java.io.File

/**
 * A class used to convert the compile result of K2 codegen API to [OutputFile].
 */
@OptIn(KaExperimentalApi::class)
@ApiStatus.Internal
class OutputFileForKtCompiledFile(private val compiledFile: KtCompiledFile): OutputFile {
  override val relativePath: String
    get() = compiledFile.path

  override val sourceFiles: List<File>
    get() = compiledFile.sourceFiles

  override fun asByteArray(): ByteArray = compiledFile.content

  override fun asText(): String = String(compiledFile.content)
}