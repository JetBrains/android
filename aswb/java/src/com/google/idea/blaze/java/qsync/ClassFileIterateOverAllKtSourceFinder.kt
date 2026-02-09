/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.java.qsync

import com.google.idea.blaze.qsync.deps.JavaArtifactInfo
import java.nio.file.Path
import org.jetbrains.kotlin.analysis.decompiler.psi.file.KtClsFile

/**
 * This finder will iterate over all source files of the JavaArtifactInfo of its ktClsFile since we cannot identify the source file's name
 * from ktClsFile directly e.g. @file:JvmName("CustomClassName"). But it may lead to performance issue since there could be a lot. Only use
 * this finder when necessary and as last option.
 */
class ClassFileIterateOverAllKtSourceFinder(private val ktClsFile: KtClsFile) : SourceFileInWorkspaceFinderBase(ktClsFile) {
  override fun filterSourcePaths(artifactInfo: JavaArtifactInfo): Sequence<Path> {
    val project = querySyncManager.getLoadedProject().orElse(null) ?: return emptySequence()
    val pathResolver = project.projectPathResolver
    return artifactInfo.sources().asSequence().map { pathResolver.resolve(it) }
  }

  override fun getSourceFileNamesFromClasses(): Set<String> {
    return emptySet()
  }
}
