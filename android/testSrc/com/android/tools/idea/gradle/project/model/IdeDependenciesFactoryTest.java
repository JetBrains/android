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
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.Dependencies.ProjectIdentifier;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.MavenCoordinates;
import com.android.tools.idea.gradle.model.IdeDependencies;
import com.android.tools.idea.gradle.model.IdeLibrary;
import com.android.tools.idea.gradle.model.stubs.BaseArtifactStub;
import com.android.tools.idea.gradle.model.stubs.DependenciesStub;
import com.android.tools.idea.gradle.model.stubs.MavenCoordinatesStub;
import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link com.android.tools.idea.gradle.model.impl.IdeDependenciesFactory}. */
public class IdeDependenciesFactoryTest {
    private ModelCacheTesting myModelCache;

    @Before
    public void setUp() throws Exception {
        myModelCache = ModelCache.createForTesting();
    }

    @Test
    public void createFromDependencies() {
        JavaLibrary javaLibraryA =
                new com.android.tools.idea.gradle.model.stubs.JavaLibraryStub() {
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
        JavaLibrary javaLibraryB =
                new com.android.tools.idea.gradle.model.stubs.JavaLibraryStub() {
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

        DependenciesStub dependenciesStub =
                new DependenciesStub(
                        Collections.emptyList(),
                        singletonList(javaLibraryB),
                        Collections.emptyList(),
                        Lists.newArrayList(identifier1, identifier2),
                        Collections.emptyList());

        BaseArtifactStub baseArtifactStub =
                new BaseArtifactStub(AndroidProject.ARTIFACT_MAIN) {
                    @Override
                    @NonNull
                    public Dependencies getDependencies() {
                        return dependenciesStub;
                    }
                };

        IdeDependencies level2Dependencies = myModelCache.dependenciesFrom(baseArtifactStub);

        assertThat(level2Dependencies.getAndroidLibraries()).hasSize(0);

        assertThat(level2Dependencies.getJavaLibraries()).hasSize(2);
        assertThat(
                        level2Dependencies.getJavaLibraries().stream()
                                .map(IdeLibrary::getArtifactAddress)
                                .collect(Collectors.toList()))
                .containsExactly("com:java:A@jar", "com:java:B@jar");

        assertThat(level2Dependencies.getModuleDependencies()).hasSize(2);
        assertThat(
                        level2Dependencies.getModuleDependencies().stream()
                                .map(IdeLibrary::getArtifactAddress)
                                .collect(Collectors.toList()))
                .containsExactly("/root/project1@@:", "/root/project2@@:");
    }

    @Test
    public void createFromDependenciesKeepInsertionOrder() {
        JavaLibrary javaLibraryA = createJavaLibrary("A");
        JavaLibrary javaLibraryB = createJavaLibrary("B");
        JavaLibrary javaLibraryC = createJavaLibrary("C");
        JavaLibrary javaLibraryD = createJavaLibrary("D");

        DependenciesStub dependenciesStub =
                new DependenciesStub(
                        Collections.emptyList(),
                        Arrays.asList(javaLibraryD, javaLibraryB, javaLibraryC, javaLibraryA),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList());

        BaseArtifactStub baseArtifactStub =
                new BaseArtifactStub(AndroidProject.ARTIFACT_MAIN) {
                    @Override
                    @NonNull
                    public Dependencies getDependencies() {
                        return dependenciesStub;
                    }
                };

        IdeDependencies level2Dependencies = myModelCache.dependenciesFrom(baseArtifactStub);

        assertThat(
                        level2Dependencies.getJavaLibraries().stream()
                                .map(IdeLibrary::getArtifactAddress)
                                .collect(Collectors.toList()))
                .containsExactly(
                        "com:java:D@jar", "com:java:B@jar", "com:java:C@jar", "com:java:A@jar")
                .inOrder();
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
}
