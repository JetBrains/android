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
import static java.util.Collections.singletonList;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.Dependencies.ProjectIdentifier;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.Library;
import com.android.builder.model.MavenCoordinates;
import com.android.tools.idea.gradle.model.IdeArtifactLibrary;
import com.android.tools.idea.gradle.model.IdeJavaLibrary;
import com.android.tools.idea.gradle.model.IdeLibrary;
import com.android.tools.idea.gradle.model.IdeModuleLibrary;
import com.android.tools.idea.gradle.model.impl.BuildFolderPaths;
import com.android.tools.idea.gradle.model.impl.IdeJavaLibraryCore;
import com.android.tools.idea.gradle.model.impl.IdeJavaLibraryImpl;
import com.android.tools.idea.gradle.model.impl.IdeModuleLibraryCore;
import com.android.tools.idea.gradle.model.impl.IdeModuleLibraryImpl;
import com.android.tools.idea.gradle.model.stubs.AndroidLibraryStub;
import com.android.tools.idea.gradle.model.stubs.JavaLibraryStub;
import com.android.tools.idea.gradle.model.stubs.LibraryStub;
import com.android.tools.idea.gradle.model.stubs.MavenCoordinatesStub;
import com.android.tools.idea.gradle.project.sync.ModelCacheKt;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import java.io.File;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link IdeLibrary}. */
public class IdeLibraryTest {

    @Test
    public void createModuleAndJavaLibraries() {
        JavaLibrary javaLibraryA = new JavaLibraryStub() {
                    @Override
                    @Nullable
                    public String getProject() {
                        return null;
                    }

                    @Override
                    @NonNull
                    public MavenCoordinates getResolvedCoordinates() {
                        return new MavenCoordinatesStub("com", "java", "A", "jar");
                    }
                };
        JavaLibrary javaLibraryB = new JavaLibraryStub() {
                    @Override
                    @Nullable
                    public String getProject() {
                        return null;
                    }

                    @Override
                    @NonNull
                    public List<? extends JavaLibrary> getDependencies() {
                        return singletonList(javaLibraryA);
                    }

                    @Override
                    @NonNull
                    public MavenCoordinates getResolvedCoordinates() {
                        return new MavenCoordinatesStub("com", "java", "B", "jar");
                    }
                };

        ProjectIdentifier identifier1 =
                new ProjectIdentifier() {
                    @NonNull
                    @Override
                    public String getBuildId() {
                        return "/root/project1";
                    }

                    @NonNull
                    @Override
                    public String getProjectPath() {
                        return ":";
                    }
                };

        ProjectIdentifier identifier2 =
                new ProjectIdentifier() {
                    @NonNull
                    @Override
                    public String getBuildId() {
                        return "/root/project2";
                    }

                    @NonNull
                    @Override
                    public String getProjectPath() {
                        return ":";
                    }
                };

        IdeJavaLibraryCore coreA = new IdeJavaLibraryCore(computeCoordinates(javaLibraryA.getResolvedCoordinates()), javaLibraryA.getJarFile());
        IdeJavaLibrary ideJavaLibraryA = new IdeJavaLibraryImpl(coreA, false);

        IdeJavaLibraryCore coreB = new IdeJavaLibraryCore(computeCoordinates(javaLibraryB.getResolvedCoordinates()), javaLibraryB.getJarFile());
        IdeJavaLibrary ideJavaLibraryB = new IdeJavaLibraryImpl(coreB, false);

        IdeModuleLibraryCore core1 = new IdeModuleLibraryCore(
          identifier1.getProjectPath(),
          identifier1.getBuildId()
        );
        IdeModuleLibrary ideLibrary1 = new IdeModuleLibraryImpl(core1, false);

        IdeModuleLibraryCore core2 = new IdeModuleLibraryCore(
          identifier2.getProjectPath(),
          identifier2.getBuildId()
        );
        IdeModuleLibrary ideLibrary2 = new IdeModuleLibraryImpl(core2, false);

        assertThat(
                        ImmutableList.of(ideJavaLibraryA, ideJavaLibraryB).stream()
                                .map(IdeArtifactLibrary::getArtifactAddress)
                                .collect(Collectors.toList()))
                .containsExactly("com:java:A@jar", "com:java:B@jar");

        assertThat(
          ImmutableList.of(ideLibrary1, ideLibrary2).stream()
                                .map(library -> library.getBuildId() + "@@" + library.getProjectPath())
                                .collect(Collectors.toList()))
                .containsExactly("/root/project1@@:", "/root/project2@@:");
    }

    @Test
    public void createLibrariesKeepInsertionOrder() {
        JavaLibrary javaLibraryA = createJavaLibrary("A");
        JavaLibrary javaLibraryB = createJavaLibrary("B");
        JavaLibrary javaLibraryC = createJavaLibrary("C");
        JavaLibrary javaLibraryD = createJavaLibrary("D");

        IdeJavaLibraryCore coreA = new IdeJavaLibraryCore(computeCoordinates(javaLibraryA.getResolvedCoordinates()), javaLibraryA.getJarFile());
        IdeJavaLibrary ideJavaLibraryA = new IdeJavaLibraryImpl(coreA, false);

        IdeJavaLibraryCore coreB = new IdeJavaLibraryCore(computeCoordinates(javaLibraryB.getResolvedCoordinates()), javaLibraryB.getJarFile());
        IdeJavaLibrary ideJavaLibraryB = new IdeJavaLibraryImpl(coreB, false);

        IdeJavaLibraryCore coreC = new IdeJavaLibraryCore(computeCoordinates(javaLibraryC.getResolvedCoordinates()), javaLibraryC.getJarFile());
        IdeJavaLibrary ideJavaLibraryC = new IdeJavaLibraryImpl(coreC, false);

        IdeJavaLibraryCore coreD = new IdeJavaLibraryCore(computeCoordinates(javaLibraryD.getResolvedCoordinates()), javaLibraryD.getJarFile());
        IdeJavaLibrary ideJavaLibraryD = new IdeJavaLibraryImpl(coreD, false);

        assertThat(
          ImmutableList.of(ideJavaLibraryD, ideJavaLibraryB, ideJavaLibraryC, ideJavaLibraryA).stream()
                                .map(IdeArtifactLibrary::getArtifactAddress)
                                .collect(Collectors.toList()))
                .containsExactly(
                        "com:java:D@jar", "com:java:B@jar", "com:java:C@jar", "com:java:A@jar")
                .inOrder();
    }

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
      Assert.assertTrue(ModelCacheKt.isLocalAarModule(buildFoldersPath, localAarLibrary));
      Assert.assertFalse(ModelCacheKt.isLocalAarModule(buildFoldersPath, moduleLibrary));
      Assert.assertFalse(ModelCacheKt.isLocalAarModule(buildFoldersPath, externalLibrary));
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
    Assert.assertTrue(ModelCacheKt.isLocalAarModule(buildFolderPaths, localAarLibraryInRootProject));
    Assert.assertTrue(ModelCacheKt.isLocalAarModule(buildFolderPaths, localAarLibraryInProject1));
    Assert.assertFalse(ModelCacheKt.isLocalAarModule(buildFolderPaths, moduleLibraryInRootProject));
    Assert.assertFalse(ModelCacheKt.isLocalAarModule(buildFolderPaths, moduleLibraryInProject1));
    Assert.assertFalse(ModelCacheKt.isLocalAarModule(buildFolderPaths, externalLibrary));
  }

    @NonNull
    private static JavaLibrary createJavaLibrary(@NonNull String version) {
        return new com.android.tools.idea.gradle.model.stubs.JavaLibraryStub() {
            @Override
            @Nullable
            public String getProject() {
                return null;
            }

            @Override
            @NonNull
            public MavenCoordinates getResolvedCoordinates() {
                return new MavenCoordinatesStub("com", "java", version, "jar");
            }
        };
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
