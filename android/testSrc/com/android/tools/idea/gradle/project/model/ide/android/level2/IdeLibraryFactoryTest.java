/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.model.ide.android.level2;

import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.Library;
import com.android.builder.model.MavenCoordinates;
import com.android.tools.idea.gradle.project.model.ide.android.ModelCache;
import com.android.tools.idea.gradle.project.model.ide.android.stubs.LibraryStub;
import com.android.tools.idea.gradle.project.model.ide.android.stubs.MavenCoordinatesStub;
import com.android.tools.idea.gradle.project.model.ide.android.stubs.level2.AndroidLibraryStub;
import com.android.tools.idea.gradle.project.model.ide.android.stubs.level2.JavaLibraryStub;
import com.android.tools.idea.gradle.project.model.ide.android.stubs.level2.ModuleLibraryStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static com.android.tools.idea.gradle.project.model.ide.android.level2.IdeLibraries.computeAddress;
import static com.android.tools.idea.gradle.project.model.ide.android.level2.IdeLibraries.isLocalAarModule;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link IdeLibraryFactory}.
 */
public class IdeLibraryFactoryTest {
  private IdeLibraryFactory myLibraryFactory;
  private ModelCache myModelCache;

  @Before
  public void setUp() throws Exception {
    myLibraryFactory = new IdeLibraryFactory();
    myModelCache = new ModelCache();
  }

  @Test
  public void createFromL2Library() {
    com.android.builder.model.level2.Library androidLibrary = myLibraryFactory.create(new AndroidLibraryStub(), myModelCache);
    assertThat(androidLibrary).isInstanceOf(IdeAndroidLibrary.class);

    com.android.builder.model.level2.Library javaLibrary = myLibraryFactory.create(new JavaLibraryStub(), myModelCache);
    assertThat(javaLibrary).isInstanceOf(IdeJavaLibrary.class);


    ModuleLibraryStub moduleLibrary = new ModuleLibraryStub();
    assertThat(myLibraryFactory.create(moduleLibrary, myModelCache)).isInstanceOf(IdeModuleLibrary.class);
  }

  @Test
  public void createFromJavaLibrary() {
    // Verify JavaLibrary of module dependency returns instance of IdeModuleLibrary.
    com.android.builder.model.level2.Library moduleLibrary =
      myLibraryFactory.create(new com.android.tools.idea.gradle.project.model.ide.android.stubs.JavaLibraryStub(), myModelCache);
    assertThat(moduleLibrary).isInstanceOf(IdeModuleLibrary.class);

    // Verify JavaLibrary of jar dependency returns instance of IdeJavaLibrary.
    JavaLibrary javaLibrary = new com.android.tools.idea.gradle.project.model.ide.android.stubs.JavaLibraryStub() {
      @Override
      @Nullable
      public String getProject() {
        return null;
      }
    };
    assertThat(myLibraryFactory.create(javaLibrary, myModelCache)).isInstanceOf(IdeJavaLibrary.class);
  }

  @Test
  public void createFromString() {
    assertThat(IdeLibraryFactory.create("lib", myModelCache)).isInstanceOf(IdeModuleLibrary.class);
  }

  @Test
  public void computeMavenAddress() {
    Library library = new LibraryStub() {
      @Override
      @NotNull
      public MavenCoordinates getResolvedCoordinates() {
        return new MavenCoordinatesStub("com.android.tools", "test", "2.1", "aar");
      }
    };
    assertThat(computeAddress(library)).isEqualTo("com.android.tools:test:2.1@aar");
  }

  @Test
  public void computeMavenAddressWithModuleLibrary() {
    Library library = new LibraryStub() {
      @Override
      @NotNull
      public MavenCoordinates getResolvedCoordinates() {
        return new MavenCoordinatesStub("myGroup", ":androidLib", "undefined", "aar");
      }
    };
    assertThat(computeAddress(library)).isEqualTo("myGroup:androidLib:undefined@aar");
  }

  @Test
  public void checkIsLocalAarModule() {
    AndroidLibrary localAarLibrary = new com.android.tools.idea.gradle.project.model.ide.android.stubs.AndroidLibraryStub() {
      @Override
      @NotNull
      public String getProject() {
        return ":aarModule";
      }

      @Override
      @NotNull
      public File getBundle() {
        return new File("/ProjectRoot/aarModule/aarModule.aar");
      }
    };
    AndroidLibrary moduleLibrary = new com.android.tools.idea.gradle.project.model.ide.android.stubs.AndroidLibraryStub() {
      @Override
      @NotNull
      public String getProject() {
        return ":androidLib";
      }

      @Override
      @NotNull
      public File getBundle() {
        return new File("/ProjectRoot/androidLib/build/androidLib.aar");
      }
    };
    AndroidLibrary externalLibrary = new com.android.tools.idea.gradle.project.model.ide.android.stubs.AndroidLibraryStub() {
      @Override
      @Nullable
      public String getProject() {
        return null;
      }
    };
    BuildFolderPaths buildFolderPaths = new BuildFolderPaths();
    buildFolderPaths.addBuildFolderMapping(":aarModule", "/ProjectRoot/aarModule/build/");
    buildFolderPaths.addBuildFolderMapping(":androidLib", "/ProjectRoot/androidLib/build/");

    assertTrue(isLocalAarModule(localAarLibrary, buildFolderPaths));
    assertFalse(isLocalAarModule(moduleLibrary, buildFolderPaths));
    assertFalse(isLocalAarModule(externalLibrary, buildFolderPaths));
  }
}
