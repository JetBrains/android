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
package com.android.tools.idea.gradle.structure.model.repositories.search;

import com.android.ide.common.repository.GradleVersion;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Objects;

import static com.google.common.base.Strings.nullToEmpty;
import static com.intellij.openapi.util.JDOMUtil.loadDocument;
import static com.intellij.openapi.util.io.FileUtil.notNullize;
import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.FileVisitResult.SKIP_SUBTREE;
import static java.nio.file.Files.walkFileTree;

public class LocalMavenRepository extends ArtifactRepository {
  @NotNull private final Path myRootLocation;
  @NotNull private final String myName;

  public LocalMavenRepository(@NotNull File rootLocation, @NotNull String name) {
    myRootLocation =  rootLocation.toPath();
    myName = name;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  public boolean isRemote() {
    return false;
  }

  @Override
  @NotNull
  protected SearchResult doSearch(@NotNull SearchRequest request) {
    List<FoundArtifact> foundArtifacts = Lists.newArrayList();

    try {
      walkFileTree(myRootLocation, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
          File parent = dir.toFile();
          File mavenMetadataFile = new File(parent, "maven-metadata.xml");

          if (mavenMetadataFile.isFile()) {
            Match match = isMatch(mavenMetadataFile, request);
            if (match != null) {
              List<GradleVersion> versions = Lists.newArrayList();
              for (File child : notNullize(parent.listFiles())) {
                if (child.isDirectory()) {
                  String version = child.getName();
                  versions.add(GradleVersion.parse(version));
                }
              }

              FoundArtifact artifact = new FoundArtifact(myName, match.groupId, match.artifactName, versions);
              foundArtifacts.add(artifact);
            }
            return SKIP_SUBTREE;
          }
          return CONTINUE;
        }
      });
    }
    catch (Throwable e) {
      String msg = String.format("Failed to search local repository '%1$s'", myRootLocation);
      Logger.getInstance(LocalMavenRepository.class).warn(msg, e);
    }

    return new SearchResult(myName, foundArtifacts, foundArtifacts.size());
  }

  @Nullable
  private static Match isMatch(@NotNull File mavenMetadataFile, @NotNull SearchRequest request) {
    String groupId = request.getGroupId();
    String artifactName = request.getArtifactName();

    try {
      Document document = loadDocument(mavenMetadataFile);
      Element rootElement = document.getRootElement();
      if (rootElement != null) {
        Element groupIdElement = rootElement.getChild("groupId");
        if (groupId != null && groupIdElement == null) {
          return null;
        }
        groupId = nullToEmpty(groupId);
        String currentGroupId = nullToEmpty(groupIdElement.getValue());
        if (!currentGroupId.contains(groupId)) {
          return null;
        }

        Element artifactIdElement = rootElement.getChild("artifactId");
        if (artifactIdElement == null) {
          return null;
        }
        String currentArtifactName = artifactIdElement.getValue();
        if (currentArtifactName.contains(artifactName)) {
          return new Match(currentArtifactName, nullToEmpty(currentGroupId));
        }
      }
    }
    catch (Throwable e) {
      String msg = String.format("Failed to parse '%1$s'", mavenMetadataFile.getPath());
      Logger.getInstance(LocalMavenRepository.class).warn(msg, e);
    }
    return null;
  }

  private static class Match {
    @NotNull final String artifactName;
    @NotNull final String groupId;

    Match(@NotNull String artifactName, @NotNull String groupId) {
      this.artifactName = artifactName;
      this.groupId = groupId;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    LocalMavenRepository that = (LocalMavenRepository)o;
    return Objects.equals(myRootLocation, that.myRootLocation) &&
           Objects.equals(myName, that.myName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myRootLocation, myName);
  }
}
