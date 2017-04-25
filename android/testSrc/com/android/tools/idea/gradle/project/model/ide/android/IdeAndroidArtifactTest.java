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
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.model.ide.android.stubs.AndroidArtifactStub;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Before;
import org.junit.Test;

import java.io.Serializable;

import static com.android.tools.idea.gradle.project.model.ide.android.CopyVerification.assertEqualsOrSimilar;
import static com.android.tools.idea.gradle.project.model.ide.android.Serialization.deserialize;
import static com.android.tools.idea.gradle.project.model.ide.android.Serialization.serialize;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.*;

/**
 * Tests for {@link IdeAndroidArtifact}.
 */
public class IdeAndroidArtifactTest {
  private ModelCache myModelCache;
  private GradleVersion myGradleVersion;

  @Before
  public void setUp() throws Exception {
    myModelCache = new ModelCache();
    myGradleVersion = GradleVersion.parse("3.2");
  }

  @Test
  public void serializable() {
    assertThat(IdeAndroidArtifact.class).isAssignableTo(Serializable.class);
  }

  @Test
  public void serialization() throws Exception {
    IdeAndroidArtifact artifact = new IdeAndroidArtifact(new AndroidArtifactStub(), myModelCache, myGradleVersion);
    byte[] bytes = serialize(artifact);
    Object o = deserialize(bytes);
    assertEquals(artifact, o);
  }

  @Test
  public void constructor() throws Throwable {
    AndroidArtifact original = new AndroidArtifactStub();
    assertEqualsOrSimilar(original, new IdeAndroidArtifact(original, myModelCache, myGradleVersion));
  }

  @Test
  public void equalsAndHashCode() {
    EqualsVerifier.forClass(IdeAndroidArtifact.class).withRedefinedSuperclass().verify();
  }
}