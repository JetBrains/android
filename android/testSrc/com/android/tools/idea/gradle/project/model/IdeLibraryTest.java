/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.model;

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.Nullable;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.Library;
import com.android.builder.model.MavenCoordinates;
import com.android.tools.idea.gradle.model.IdeLibrary;
import com.android.tools.idea.gradle.model.impl.BuildFolderPaths;
import com.android.tools.idea.gradle.model.stubs.AndroidLibraryStub;
import com.android.tools.idea.gradle.model.stubs.LibraryStub;
import com.android.tools.idea.gradle.model.stubs.MavenCoordinatesStub;
import com.android.tools.idea.gradle.project.sync.ModelCacheV1ImplKt;
import com.google.common.truth.Truth;
import java.io.File;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link IdeLibrary}. */
public class IdeLibraryTest {

  @Test
    public void computeMavenAddress() {
      Library library = new LibraryStub() {
        @Override
        public MavenCoordinates getResolvedCoordinates() {
          return new MavenCoordinatesStub("com.android.tools", "test", "2.1", "aar");
        }
      };
      Truth.assertThat(computeCoordinates(library.getResolvedCoordinates())).isEqualTo("com.android.tools:test:2.1@aar");
    }

    @Test
    public void computeMavenAddressWithNestedModuleLibrary() {
      Library library = new LibraryStub() {
        @Override
        public MavenCoordinates getResolvedCoordinates() {
          return new MavenCoordinatesStub("myGroup", ":androidLib:subModule", "undefined", "aar");
        }
      };
      Truth.assertThat(computeCoordinates(library.getResolvedCoordinates())).isEqualTo("myGroup:androidLib.subModule:undefined@aar");
    }

    @Test
    public void checkIsLocalAarModule() {
      AndroidLibrary localAarLibrary = new AndroidLibraryStub() {
        @Override
        public String getProject() {
          return ":aarModule";
        }

        @Override
        public File getBundle() {
          return new File("/ProjectRoot/aarModule/aarModule.aar");
        }
      };
      AndroidLibrary moduleLibrary = new AndroidLibraryStub() {
        @Override
        public String getProject() {
          return ":androidLib";
        }

        @Override
        public File getBundle() {
          return new File("/ProjectRoot/androidLib/build/androidLib.aar");
        }
      };

      AndroidLibrary externalLibrary = new AndroidLibraryStub() {
        @Override
        @Nullable
        public String getProject() {
        return null;
        }
      };

      BuildFolderPaths buildFoldersPath = new BuildFolderPaths();
      buildFoldersPath.setRootBuildId("project");
      buildFoldersPath.addBuildFolderMapping(
        "project", ":aarModule", new File("/ProjectRoot/aarModule/build/")
      );
      buildFoldersPath.addBuildFolderMapping(
        "project", ":androidLib", new File("/ProjectRoot/androidLib/build/")
      );
      Assert.assertTrue(ModelCacheV1ImplKt.isLocalAarModule(buildFoldersPath, localAarLibrary));
      Assert.assertFalse(ModelCacheV1ImplKt.isLocalAarModule(buildFoldersPath, moduleLibrary));
      Assert.assertFalse(ModelCacheV1ImplKt.isLocalAarModule(buildFoldersPath, externalLibrary));
    }

  @Test
  public void checkIsLocalAarModuleWithCompositeBuild() {
    // simulate project structure:
    // project(root)     - aarModule
    // project(root)     - androidLib
    //      project1     - aarModule
    //      project1     - androidLib
    AndroidLibrary localAarLibraryInRootProject = new AndroidLibraryStub() {
      @Override
      public String getProject() {
        return ":aarModule";
      }

      @Override
      public File getBundle() {
        return new File("/Project/aarModule/aarModule.aar");
      }

      @Override
      @Nullable
      public String getBuildId() {
      return "Project";
      }
    };

    AndroidLibrary localAarLibraryInProject1 = new AndroidLibraryStub() {
      @Override
      public String getProject() {
        return ":aarModule";
      }

      @Override
      public File getBundle() {
        return new File("/Project1/aarModule/aarModule.aar");
      }

      @Override
      @Nullable
      public String getBuildId() {
      return "Project1";
      }
    };

    AndroidLibrary moduleLibraryInRootProject = new AndroidLibraryStub() {
      @Override
      public String getProject() {
        return ":androidLib";
      }

      @Override
      public File getBundle() {
        return new File("/Project/androidLib/build/androidLib.aar");
      }

      @Override
      @Nullable
      public String getBuildId() {
      return "Project";
      }
    };

    AndroidLibrary moduleLibraryInProject1 = new AndroidLibraryStub() {
      @Override
      public String getProject() {
        return ":androidLib";
      }

      @Override
      public File getBundle() {
        return new File("/Project1/androidLib/build/androidLib.aar");
      }

      @Override
      @Nullable
      public String getBuildId() {
      return "Project1";
      }
    };

    AndroidLibrary externalLibrary = new AndroidLibraryStub() {
      @Override
      public String getProject() {
      return null;
      }
    };

    BuildFolderPaths buildFolderPaths = new BuildFolderPaths();
    buildFolderPaths.setRootBuildId("Project");
    buildFolderPaths.addBuildFolderMapping(
      "Project", ":aarModule", new File("/Project/aarModule/build/")
    );
    buildFolderPaths.addBuildFolderMapping(
      "Project", ":androidLib", new File("/Project/androidLib/build/")
    );
    buildFolderPaths.addBuildFolderMapping(
      "Project1", ":aarModule", new File("/Project1/aarModule/build/")
    );
    buildFolderPaths.addBuildFolderMapping(
      "Project1", ":androidLib", new File("/Project1/androidLib/build/")
    );
    Assert.assertTrue(ModelCacheV1ImplKt.isLocalAarModule(buildFolderPaths, localAarLibraryInRootProject));
    Assert.assertTrue(ModelCacheV1ImplKt.isLocalAarModule(buildFolderPaths, localAarLibraryInProject1));
    Assert.assertFalse(ModelCacheV1ImplKt.isLocalAarModule(buildFolderPaths, moduleLibraryInRootProject));
    Assert.assertFalse(ModelCacheV1ImplKt.isLocalAarModule(buildFolderPaths, moduleLibraryInProject1));
    Assert.assertFalse(ModelCacheV1ImplKt.isLocalAarModule(buildFolderPaths, externalLibrary));
  }

  private String computeCoordinates(MavenCoordinates coordinate) {
      String artifactId = coordinate.getArtifactId();
      if (artifactId.startsWith(":")) {
        artifactId = artifactId.substring(1);
      }
      artifactId = artifactId.replace(':', '.');

      String address = coordinate.getGroupId() + ":" + artifactId + ":" + coordinate.getVersion();
      String classifier = coordinate.getClassifier();
      if (classifier != null) {
        address = address + ":" + classifier;
      }
      String packaging = coordinate.getPackaging();
      address = address + "@" + packaging;
      return address;
    }
}
