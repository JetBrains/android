/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle;

import static com.android.tools.idea.gradle.LibraryFilePaths.getLibraryId;
import static com.google.common.truth.Truth.assertThat;

import com.android.ide.gradle.model.ArtifactIdentifier;
import com.android.ide.gradle.model.artifacts.AdditionalClassifierArtifacts;
import com.android.ide.gradle.model.artifacts.AdditionalClassifierArtifactsModel;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tests for {@link LibraryFilePaths}.
 */
public class LibraryFilePathsTest extends AndroidGradleTestCase {
  public void testGetLibraryId() {
    assertThat(getLibraryId("Gradle: junit:junit:4.12@jar")).isEqualTo("junit:junit:4.12");
    assertThat(getLibraryId("androidx.fragment:fragment:1.0.0@aar")).isEqualTo("androidx.fragment:fragment:1.0.0");
  }

  public void testPopulateAndRetrieve() {
    LibraryFilePaths libraryFilePaths = LibraryFilePaths.getInstance(getProject());
    libraryFilePaths.populate(createArtifacts());
    assertThat(libraryFilePaths.retrieveCachedLibs()).containsExactly("junit:junit:4.12", "androidx.fragment:fragment:1.0.0");
  }

  public void testFindJarPath() {
    LibraryFilePaths libraryFilePaths = LibraryFilePaths.getInstance(getProject());
    libraryFilePaths.populate(createArtifacts());
    assertThat(libraryFilePaths.getCachedPathsForArtifact("Gradle: junit:junit:4.12@jar").javaDoc.getPath())
      .isEqualTo(new File("/cache/junit-javadoc.jar").getPath());
    assertThat(libraryFilePaths.getCachedPathsForArtifact("Gradle: junit:junit:4.12@jar").sources)
      .containsExactly(new File("/cache/junit-sources.jar"));
    assertThat(libraryFilePaths.findPomPathForLibrary("Gradle: junit:junit:4.12@jar", new File("dummy")).getPath())
      .isEqualTo(new File("/cache/junit.pom").getPath());
    assertThat(libraryFilePaths.getCachedPathsForArtifact("Gradle: androidx.fragment:fragment:1.0.0@aar").javaDoc.getPath())
      .isEqualTo(new File("/cache/fragment-javadoc.jar").getPath());
    assertThat(libraryFilePaths.getCachedPathsForArtifact("Gradle: androidx.fragment:fragment:1.0.0@aar").sources)
      .containsExactly(new File("/cache/fragment-sources.jar"));
    assertThat(libraryFilePaths.findPomPathForLibrary("Gradle: androidx.fragment:fragment:1.0.0@aar", new File("dummy")).getPath())
      .isEqualTo(new File("/cache/fragment.pom").getPath());
  }

  @NotNull
  private static AdditionalClassifierArtifactsModel createArtifacts() {
    return new AdditionalClassifierArtifactsModel() {
      @NotNull
      @Override
      public Collection<AdditionalClassifierArtifacts> getArtifacts() {
        return Arrays
          .asList(createArtifact("junit", "junit", "4.12", "/cache/junit-javadoc.jar", "/cache/junit-sources.jar", "/cache/junit.pom"),
                  createArtifact("androidx.fragment", "fragment", "1.0.0", "/cache/fragment-javadoc.jar",
                                 "/cache/fragment-sources.jar", "/cache/fragment.pom"));
      }

      @Nullable
      @Override
      public String getErrorMessage() {
        return null;
      }
    };
  }

  @NotNull
  private static AdditionalClassifierArtifacts createArtifact(@NotNull String group,
                                                              @NotNull String artifactId,
                                                              @NotNull String version,
                                                              @NotNull String javadoc,
                                                              @NotNull String sources,
                                                              @NotNull String pom) {
    return new AdditionalClassifierArtifacts() {
      @NotNull
      @Override
      public ArtifactIdentifier getId() {
        return new ArtifactIdentifier() {
          @NotNull
          @Override
          public String getGroupId() {
            return group;
          }

          @NotNull
          @Override
          public String getArtifactId() {
            return artifactId;
          }

          @NotNull
          @Override
          public String getVersion() {
            return version;
          }
        };
      }

      @NotNull
      @Override
      public List<File> getSources() {
        return Collections.singletonList(new File(sources));
      }

      @Nullable
      @Override
      public File getJavadoc() {
        return new File(javadoc);
      }

      @Nullable
      @Override
      public File getMavenPom() {
        return new File(pom);
      }
    };
  }
}
