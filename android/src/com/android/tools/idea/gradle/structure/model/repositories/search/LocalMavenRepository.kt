/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model.repositories.search

import com.android.ide.common.repository.GradleVersion
import com.google.common.base.Strings.nullToEmpty
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.JDOMUtil.loadDocument
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.FileVisitResult.CONTINUE
import java.nio.file.FileVisitResult.SKIP_SUBTREE
import java.nio.file.Files.walkFileTree
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

data class LocalMavenRepository(val rootLocation: File, override val name: String) : ArtifactRepository() {
  private val rootLocationPath: Path = rootLocation.toPath()
  override val isRemote: Boolean = false

  override fun doSearch(request: SearchRequest): SearchResult {
    val foundArtifacts = mutableListOf<FoundArtifact>()

    try {
      walkFileTree(rootLocationPath, object : SimpleFileVisitor<Path>() {
        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
          val parent = dir.toFile()
          val mavenMetadataFile = File(parent, "maven-metadata.xml")

          if (!mavenMetadataFile.isFile) return CONTINUE

          val match = isMatch(mavenMetadataFile, request.query.groupId?.toWildcardMatchingPredicate() ?: { true },
                              request.query.artifactName?.toWildcardMatchingPredicate() ?: { true })
          if (match != null) {
            val versions = parent.listFiles()?.filter { it.isDirectory}?.mapNotNull { GradleVersion.tryParse(it.name) } ?: listOf()
            foundArtifacts.add(FoundArtifact(name, match.groupId, match.artifactName, versions))
          }
          return SKIP_SUBTREE
        }
      })
    }
    catch (e: Throwable) {
      val msg = "Failed to search local repository $rootLocationPath"
      Logger.getInstance(LocalMavenRepository::class.java).warn(msg, e)
    }

    return SearchResult(foundArtifacts.sortedWith(compareBy<FoundArtifact> { it.groupId }.thenBy { it.name }))
  }

  private fun isMatch(
    mavenMetadataFile: File,
    groupIdPredicate: ((String) -> Boolean),
    artifactNamePredicate: (String) -> Boolean
  ): Match? {

    try {
      val document = loadDocument(mavenMetadataFile)
      val rootElement = document.rootElement
      if (rootElement != null) {
        val groupIdElement = rootElement.getChild("groupId")
        val currentGroupId = groupIdElement?.value.orEmpty()
        if (!groupIdPredicate(currentGroupId)) return null

        val artifactIdElement = rootElement.getChild("artifactId") ?: return null
        val currentArtifactName = artifactIdElement.value
        if (artifactNamePredicate(currentArtifactName)) {
          return Match(currentArtifactName, nullToEmpty(currentGroupId))
        }
      }
    }
    catch (e: Throwable) {
      val msg = String.format("Failed to parse '%1\$s'", mavenMetadataFile.path)
      Logger.getInstance(LocalMavenRepository::class.java).warn(msg, e)
    }

    return null
  }

  private data class Match internal constructor(internal val artifactName: String, internal val groupId: String)
}

private fun String.toWildcardMatchingPredicate() : (String) -> Boolean =
  if (isBlank()) {
    { true }
  }
  else {
    Regex(replace("*", ".*")).let { { probe: String -> it.matches(probe) } }
  }
