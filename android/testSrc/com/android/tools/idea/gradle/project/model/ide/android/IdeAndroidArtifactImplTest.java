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
import com.android.builder.model.InstantRun;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.model.ide.android.stubs.AndroidArtifactStub;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import java.io.Serializable;
import java.util.Objects;

import static com.android.tools.idea.gradle.project.model.ide.android.CopyVerification.assertEqualsOrSimilar;
import static com.android.tools.idea.gradle.project.model.ide.android.IdeModelTestUtils.expectUnsupportedMethodException;
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

  @Before
  public void setUp() throws Exception {
    myModelCache = new ModelCache();
    myGradleVersion = GradleVersion.parse("3.2");
  }

  @Test
  public void serializable() {
    assertThat(IdeAndroidArtifactImpl.class).isAssignableTo(Serializable.class);
  }

  @Test
  public void serialization() throws Exception {
    IdeAndroidArtifact artifact = new IdeAndroidArtifactImpl(new AndroidArtifactStub(), myModelCache, myGradleVersion);
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
    IdeAndroidArtifact artifact = new IdeAndroidArtifactImpl(original, myModelCache, myGradleVersion);
    expectUnsupportedMethodException(artifact::getInstantRun);

  }

  @Test
  public void constructor() throws Throwable {
    AndroidArtifact original = new AndroidArtifactStub();
    assertEqualsOrSimilar(original, new IdeAndroidArtifactImpl(original, myModelCache, myGradleVersion));
  }

  @Test
  public void equalsAndHashCode() {
    EqualsVerifier.forClass(IdeAndroidArtifactImpl.class).withRedefinedSuperclass()
      .withCachedHashCode("myHashCode", "calculateHashCode", null)
      .suppress(Warning.NO_EXAMPLE_FOR_CACHED_HASHCODE)
      .suppress(Warning.ALL_FIELDS_SHOULD_BE_USED)
      .verify();
  }
}
