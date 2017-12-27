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

import com.android.builder.model.NativeArtifact;
import com.android.tools.idea.gradle.project.model.ide.android.stubs.NativeArtifactStub;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Objects;

import static com.android.tools.idea.gradle.project.model.ide.android.IdeModelTestUtils.*;
import static com.android.tools.idea.gradle.project.model.ide.android.Serialization.deserialize;
import static com.android.tools.idea.gradle.project.model.ide.android.Serialization.serialize;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link IdeNativeArtifact}.
 */
public class IdeNativeArtifactTest {
  private ModelCache myModelCache;

  @Before
  public void setUp() throws Exception {
    myModelCache = new ModelCache();
  }

  @Test
  public void serializable() {
    assertThat(IdeNativeArtifact.class).isAssignableTo(Serializable.class);
  }

  @Test
  public void serialization() throws Exception {
    IdeNativeArtifact nativeArtifact = new IdeNativeArtifact(new NativeArtifactStub(), myModelCache);
    byte[] bytes = serialize(nativeArtifact);
    Object o = deserialize(bytes);
    assertEquals(nativeArtifact, o);
  }

  @Test(expected = UnusedModelMethodException.class)
  public void getRuntimeFilesWithPlugin2dot2() {
    NativeArtifactStub original = new NativeArtifactStub() {
      @Override
      @NotNull
      public Collection<File> getRuntimeFiles() {
        throw new UnsupportedMethodException("getRuntimeFiles()");
      }

      @Override
      public int hashCode() {
        return Objects.hash(getName(), getToolChain(), getGroupName(), getAssembleTaskName(), getSourceFolders(), getSourceFiles(),
                            getExportedHeaders(), getAbi(), getTargetName(), getOutputFile());
      }
    };
    IdeNativeArtifact artifact = new IdeNativeArtifact(original, myModelCache);
    artifact.getRuntimeFiles();
  }

  @Test(expected = UnusedModelMethodException.class)
  public void getAssembleTaskNameWithExperimentalPlugin0dot7() {
    NativeArtifactStub original = new NativeArtifactStub() {
      @Override
      @NotNull
      public String getAssembleTaskName() {
        throw new UnsupportedMethodException("getAssembleTaskName");
      }

      @Override
      public int hashCode() {
        return Objects.hash(getName(), getToolChain(), getGroupName(), getSourceFolders(), getSourceFiles(), getExportedHeaders(),
                            getAbi(), getTargetName(), getOutputFile(), getRuntimeFiles());
      }
    };
    IdeNativeArtifact artifact = new IdeNativeArtifact(original, myModelCache);
    artifact.getAssembleTaskName();
  }

  @Test
  public void getAbiWithExperimentalPlugin0dot7() {
    NativeArtifactStub original = new NativeArtifactStub() {
      @Override
      @NotNull
      public String getAbi() {
        throw new UnsupportedMethodException("getAbi");
      }

      @Override
      public int hashCode() {
        return Objects.hash(getName(), getToolChain(), getGroupName(), getAssembleTaskName(), getSourceFolders(), getSourceFiles(),
                            getExportedHeaders(), getTargetName(), getOutputFile(), getRuntimeFiles());
      }
    };
    IdeNativeArtifact artifact = new IdeNativeArtifact(original, myModelCache);
    try {
      artifact.getAbi();
      Assert.fail("Expecting UnsupportedMethodException");
    }
    catch (UnsupportedMethodException expected) {
      // ignored
    }
  }

  @Test
  public void getTargetNameWithExperimentalPlugin0dot7() {
    NativeArtifactStub original = new NativeArtifactStub() {
      @Override
      @NotNull
      public String getTargetName() {
        throw new UnsupportedMethodException("getTargetName");
      }


      @Override
      public int hashCode() {
        return Objects.hash(getName(), getToolChain(), getGroupName(), getAssembleTaskName(), getSourceFolders(), getSourceFiles(),
                            getExportedHeaders(), getAbi(), getOutputFile(), getRuntimeFiles());
      }
    };
    IdeNativeArtifact artifact = new IdeNativeArtifact(original, myModelCache);
    try {
      artifact.getTargetName();
      Assert.fail("Expecting UnsupportedMethodException");
    }
    catch (UnsupportedMethodException expected) {
      // ignored
    }
  }

  @Test
  public void constructor() throws Throwable {
    NativeArtifact original = new NativeArtifactStub();
    IdeNativeArtifact copy = new IdeNativeArtifact(original, myModelCache);
    assertEqualsOrSimilar(original, copy);
    verifyUsageOfImmutableCollections(copy);
  }

  @Test
  public void equalsAndHashCode() {
    createEqualsVerifier(IdeNativeArtifact.class).verify();
  }
}