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

import com.android.builder.model.level2.Library;
import com.android.tools.idea.gradle.project.model.ide.android.ModelCache;
import com.android.tools.idea.gradle.project.model.ide.android.stubs.level2.AndroidLibraryStub;
import org.junit.Before;
import org.junit.Test;

import java.io.Serializable;

import static com.android.tools.idea.gradle.project.model.ide.android.IdeModelTestUtils.createEqualsVerifier;
import static com.android.tools.idea.gradle.project.model.ide.android.IdeModelTestUtils.verifyUsageOfImmutableCollections;
import static com.android.tools.idea.gradle.project.model.ide.android.Serialization.deserialize;
import static com.android.tools.idea.gradle.project.model.ide.android.Serialization.serialize;
import static com.android.utils.FileUtils.join;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link IdeAndroidLibrary}.
 */
public class IdeAndroidLibraryTest {
  private IdeLibraryFactory myLibraryFactory;
  private ModelCache myModelCache;

  @Before
  public void setUp() {
    myLibraryFactory = new IdeLibraryFactory();
    myModelCache = new ModelCache();
  }

  @Test
  public void serializable() {
    assertThat(IdeAndroidLibrary.class).isAssignableTo(Serializable.class);
  }

  @Test
  public void serialization() throws Exception {
    Library androidLibrary = myLibraryFactory.create(new AndroidLibraryStub(), myModelCache);
    byte[] bytes = serialize(androidLibrary);
    Object o = deserialize(bytes);
    assertEquals(androidLibrary, o);
  }

  @Test
  public void constructor() throws Throwable {
    Library original = new AndroidLibraryStub();
    Library copy = myLibraryFactory.create(original, myModelCache);
    assertThat(copy.getAidlFolder()).isEqualTo(join(original.getFolder(), original.getAidlFolder()).getPath());
    assertThat(copy.getRenderscriptFolder()).isEqualTo(join(original.getFolder(), original.getRenderscriptFolder()).getPath());
    assertThat(copy.getResFolder()).isEqualTo(join(original.getFolder(), original.getResFolder()).getPath());
    assertThat(copy.getJarFile()).isEqualTo(join(original.getFolder(), original.getJarFile()).getPath());
    assertThat(copy.getAssetsFolder()).isEqualTo(join(original.getFolder(), original.getAssetsFolder()).getPath());
    assertThat(copy.getManifest()).isEqualTo(join(original.getFolder(), original.getManifest()).getPath());
    verifyUsageOfImmutableCollections(copy);
  }

  @Test
  public void equalsAndHashCode() {
    createEqualsVerifier(IdeAndroidLibrary.class).verify();
  }
}
