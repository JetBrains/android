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

import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.InstantRun;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.model.ide.android.level2.IdeDependenciesFactory;
import com.android.tools.idea.gradle.project.model.ide.android.stubs.AndroidArtifactStub;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import java.io.Serializable;
import java.util.Collection;
import java.util.Objects;

import static com.android.tools.idea.gradle.project.model.ide.android.IdeModelTestUtils.*;
import static com.android.tools.idea.gradle.project.model.ide.android.Serialization.deserialize;
import static com.android.tools.idea.gradle.project.model.ide.android.Serialization.serialize;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link IdeAndroidArtifactImpl}.
 */
public class IdeAndroidArtifactImplTest {
  private ModelCache myModelCache;
  private GradleVersion myGradleVersion;
  private IdeDependenciesFactory myDependenciesFactory;

  @Before
  public void setUp() throws Exception {
    myModelCache = new ModelCache();
    myGradleVersion = GradleVersion.parse("3.2");
    myDependenciesFactory = new IdeDependenciesFactory();
  }

  @Test
  public void serializable() {
    assertThat(IdeAndroidArtifactImpl.class).isAssignableTo(Serializable.class);
  }

  @Test
  public void serialization() throws Exception {
    IdeAndroidArtifact artifact =
      new IdeAndroidArtifactImpl(new AndroidArtifactStub(), myModelCache, myDependenciesFactory, myGradleVersion);
    byte[] bytes = serialize(artifact);
    Object o = deserialize(bytes);
    assertEquals(artifact, o);
  }

  @Test
  public void model1_dot_5() {
    AndroidArtifactStub original = new AndroidArtifactStub() {
      @Override
      @NotNull
      public InstantRun getInstantRun() {
        throw new UnsupportedMethodException("Unsupported method: AndroidArtifact.getInstantRun()");
      }

      @Override
      public int hashCode() {
        return Objects.hash(getName(), getCompileTaskName(), getAssembleTaskName(), getClassesFolder(), getJavaResourcesFolder(),
                            getDependencies(), getCompileDependencies(), getDependencyGraphs(), getIdeSetupTaskNames(),
                            getGeneratedSourceFolders(), getVariantSourceProvider(), getMultiFlavorSourceProvider(), getOutputs(),
                            getApplicationId(), getSourceGenTaskName(), getGeneratedResourceFolders(), getBuildConfigFields(),
                            getResValues(), getSigningConfigName(), getAbiFilters(), getNativeLibraries(), isSigned());
      }
    };
    IdeAndroidArtifact artifact = new IdeAndroidArtifactImpl(original, myModelCache, myDependenciesFactory, myGradleVersion);
    expectUnsupportedMethodException(artifact::getInstantRun);
  }

  @Test
  public void constructor() throws Throwable {
    AndroidArtifact original = new AndroidArtifactStub();
    IdeAndroidArtifactImpl copy = new IdeAndroidArtifactImpl(original, myModelCache, myDependenciesFactory, myGradleVersion);
    assertEqualsOrSimilar(original, copy);
    verifyUsageOfImmutableCollections(copy);
  }

  // See http://b/64305584
  @Test
  public void withNpeInGetOutputs() throws Throwable {
    AndroidArtifact original = new AndroidArtifactStub() {
      @Override
      @NotNull
      public Collection<AndroidArtifactOutput> getOutputs() {
        throw new NullPointerException();
      }
      @Override

      public int hashCode() {
        return Objects.hash(getName(), getCompileTaskName(), getAssembleTaskName(), getClassesFolder(), getJavaResourcesFolder(),
                            getDependencies(), getCompileDependencies(), getDependencyGraphs(), getIdeSetupTaskNames(),
                            getGeneratedSourceFolders(), getVariantSourceProvider(), getMultiFlavorSourceProvider(), getApplicationId(),
                            getSourceGenTaskName(), getGeneratedResourceFolders(), getBuildConfigFields(), getResValues(), getInstantRun(),
                            getSigningConfigName(), getAbiFilters(), getNativeLibraries(), isSigned(), getAdditionalRuntimeApks(),
                            getTestOptions());
      }
    };
    IdeAndroidArtifactImpl copy = new IdeAndroidArtifactImpl(original, myModelCache, myDependenciesFactory, myGradleVersion);
    assertThat(copy.getOutputs()).isEmpty();
  }

  @Test
  public void equalsAndHashCode() {
    createEqualsVerifier(IdeAndroidArtifactImpl.class).withRedefinedSuperclass().verify();
  }
}
