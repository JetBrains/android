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
package com.android.tools.idea.gradle.repositories.search

import com.android.ide.common.gradle.Version
import com.google.wireless.android.sdk.stats.PSDEvent.PSDRepositoryUsage.PSDRepository.PROJECT_STRUCTURE_DIALOG_REPOSITORY_LOCAL
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.Url
import com.intellij.util.Urls
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files.walkFileTree
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

data class LocalMavenRepository(val rootLocation: File, override val name: String) :
  ArtifactRepository(PROJECT_STRUCTURE_DIALOG_REPOSITORY_LOCAL) {
  private val rootLocationPath: Path = rootLocation.toPath()
  override val isRemote: Boolean = false

  override fun doSearch(request: SearchRequest): SearchResult {
    val foundArtifacts = mutableListOf<FoundArtifact>()
    val groupIdPredicate = request.query.groupId?.toWildcardMatchingPredicate() ?: { true }
    val artifactNamePredicate = request.query.artifactName?.toWildcardMatchingPredicate() ?: { true }

    try {
      walkFileTree(rootLocationPath, object : SimpleFileVisitor<Path>() {
        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
          val visitedDirFile: File = dir.toFile()
          val repositoryRelativeDirectory = visitedDirFile.relativeTo(rootLocation)
          if (repositoryRelativeDirectory.parentFile == null) return FileVisitResult.CONTINUE
          val groupIdProbe = repositoryRelativeDirectory.parentFile.path.replace(File.separatorChar, '.')
          val artifactNameProbe = visitedDirFile.name

          if (groupIdPredicate(groupIdProbe) && artifactNamePredicate(artifactNameProbe)) {
            val versions =
              visitedDirFile
                .listFiles()
                ?.mapNotNull {
                  val versionProbe = it.name
                  val expectedPomFileName = "$artifactNameProbe-$versionProbe.pom"
                  if (it.isDirectory && it.resolve(expectedPomFileName).isFile) Version.parse(versionProbe) else null
                }
                .orEmpty()
            if (versions.isNotEmpty()) {
              foundArtifacts.add(FoundArtifact(name, groupIdProbe, artifactNameProbe, versions))
              return FileVisitResult.SKIP_SUBTREE
            }
          }
          return FileVisitResult.CONTINUE
        }
      })
    }
    catch (e: Throwable) {
      val msg = "Failed to search local repository $rootLocationPath"
      Logger.getInstance(LocalMavenRepository::class.java).warn(msg, e)
    }

    return SearchResult(foundArtifacts.sortedWith(compareBy<FoundArtifact> { it.groupId }.thenBy { it.name }))
  }

  private data class Match internal constructor(internal val artifactName: String, internal val groupId: String)

  companion object {
    fun maybeCreateLocalMavenRepository(mavenRepositoryUrl: String, mavenRepositoryName: String): LocalMavenRepository? {
      val parsedRepositoryUrl = parseToLocalFile(mavenRepositoryUrl, false) ?: parseToLocalFile(mavenRepositoryUrl, true) ?: return null
      val repositoryPath = parsedRepositoryUrl.path
      val repositoryRootFile = File(repositoryPath)
      if (repositoryRootFile.isAbsolute) {
        return LocalMavenRepository(repositoryRootFile, mavenRepositoryName)
      }
      return null
    }
  }
}

private fun String.toWildcardMatchingPredicate(): (String) -> Boolean =
  if (isBlank()) {
    { true }
  }
  else {
    Regex(replace("*", ".*")).let { { probe: String -> it.matches(probe) } }
  }

private fun parseToLocalFile(url: String, asLocalIfNoScheme: Boolean): Url? {
  val parsedRepositoryUrl = Urls.parse(url, asLocalIfNoScheme) ?: return null
  return if (parsedRepositoryUrl.isInLocalFileSystem) parsedRepositoryUrl else null
}
