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
package com.android.tools.idea.gradle.project.model.ide.android;

import com.android.builder.model.JavaLibrary;
import com.android.tools.idea.gradle.project.model.ide.android.stubs.JavaLibraryStub;
import com.android.tools.idea.gradle.project.model.ide.android.stubs.Level2AndroidLibraryStub;
import com.android.tools.idea.gradle.project.model.ide.android.stubs.Level2JavaLibraryStub;
import com.android.tools.idea.gradle.project.model.ide.android.stubs.Level2ModuleLibraryStub;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link IdeLevel2LibraryFactory}.
 */
public class IdeLevel2LibraryFactoryTest {
  private ModelCache myModelCache;

  @Before
  public void setUp() throws Exception {
    myModelCache = new ModelCache();
  }

  @Test
  public void createFromL2Library() {
    assertThat(IdeLevel2LibraryFactory.create(new Level2AndroidLibraryStub(), myModelCache)).isInstanceOf(IdeLevel2AndroidLibrary.class);
    assertThat(IdeLevel2LibraryFactory.create(new Level2JavaLibraryStub(), myModelCache)).isInstanceOf(IdeLevel2JavaLibrary.class);
    assertThat(IdeLevel2LibraryFactory.create(new Level2ModuleLibraryStub(), myModelCache)).isInstanceOf(IdeLevel2ModuleLibrary.class);
  }

  @Test
  public void createFromJavaLibrary() {
    // Verify JavaLibrary of module dependency returns instance of IdeLevel2ModuleLibrary.
    assertThat(IdeLevel2LibraryFactory.create(new JavaLibraryStub(), myModelCache)).isInstanceOf(IdeLevel2ModuleLibrary.class);

    // Verify JavaLibrary of jar dependency returns instance of IdeLevel2JavaLibrary.
    JavaLibrary javaLibrary = new JavaLibraryStub() {
      @Override
      @Nullable
      public String getProject() {
        return null;
      }
    };
    assertThat(IdeLevel2LibraryFactory.create(javaLibrary, myModelCache)).isInstanceOf(IdeLevel2JavaLibrary.class);
  }

  @Test
  public void createFromString() {
    assertThat(IdeLevel2LibraryFactory.create("lib", myModelCache)).isInstanceOf(IdeLevel2ModuleLibrary.class);
  }
}
