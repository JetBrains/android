/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
import com.android.tools.idea.gradle.dsl.api.values.GradleNullableValue;
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.IdeaTestCase;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.gradle.tooling.model.GradleModuleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.Mockito;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link PsArtifactDependencySpec}.
 */
public class PsArtifactDependencySpecTest extends IdeaTestCase {
  private IdeComponents myIdeComponents;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myIdeComponents = new IdeComponents(myProject);
    myIdeComponents.mockService(PsUISettings.class);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      myIdeComponents.restore();
    }
    finally {
      //noinspection ThrowFromFinallyBlock
      super.tearDown();
    }
  }

  public void testCreate_empty() {
    PsArtifactDependencySpec spec = PsArtifactDependencySpec.create("");
    assertNull(spec);
  }

  public void testCreate_incomplete() {
    PsArtifactDependencySpec spec = PsArtifactDependencySpec.create(":");
    assertNull(spec);
  }

  public void testCreate_nameOnly() {
    PsArtifactDependencySpec spec = PsArtifactDependencySpec.create("name");
    assertNotNull(spec);
    assertEquals(null, spec.getGroup());
    assertEquals("name", spec.getName());
    assertEquals(null, spec.getVersion());
  }

  public void testCreate_nameAndVersion() {
    PsArtifactDependencySpec spec = PsArtifactDependencySpec.create("name:1.0");
    assertNotNull(spec);
    assertEquals(null, spec.getGroup());
    assertEquals("name", spec.getName());
    assertEquals("1.0", spec.getVersion());
  }

  public void testCreate_nameAndPlus() {
    PsArtifactDependencySpec spec = PsArtifactDependencySpec.create("name:+");
    assertNotNull(spec);
    assertEquals(null, spec.getGroup());
    assertEquals("name", spec.getName());
    assertEquals("+", spec.getVersion());
  }

  public void testCreate_groupAndName() {
    PsArtifactDependencySpec spec = PsArtifactDependencySpec.create("group:name");
    assertNotNull(spec);
    assertEquals("group", spec.getGroup());
    assertEquals("name", spec.getName());
    assertEquals(null, spec.getVersion());
  }

  public void testCreate_groupNameAndVersion() {
    PsArtifactDependencySpec spec = PsArtifactDependencySpec.create("group:name:1.0");
    assertNotNull(spec);
    assertEquals("group", spec.getGroup());
    assertEquals("name", spec.getName());
    assertEquals("1.0", spec.getVersion());
  }

  public void testCreate_groupNameAndDynamicVersion() {
    PsArtifactDependencySpec spec = PsArtifactDependencySpec.create("group:name:1.+");
    assertNotNull(spec);
    assertEquals("group", spec.getGroup());
    assertEquals("name", spec.getName());
    assertEquals("1.+", spec.getVersion());
  }

  public void testCreate_groupNameVersionAndClassifier() {
    PsArtifactDependencySpec spec = PsArtifactDependencySpec.create("group:name:1.0:jdk");
    assertNotNull(spec);
    assertEquals("group", spec.getGroup());
    assertEquals("name", spec.getName());
    assertEquals("1.0", spec.getVersion());
  }

  public void testCreate_groupNameVersionClassifierAndPackage() {
    PsArtifactDependencySpec spec = PsArtifactDependencySpec.create("group:name:1.0:jdk15@jar");
    assertNotNull(spec);
    assertEquals("group", spec.getGroup());
    assertEquals("name", spec.getName());
    assertEquals("1.0", spec.getVersion());
  }

  public void testCreate_groupNameVersionAndPackage() {
    PsArtifactDependencySpec spec = PsArtifactDependencySpec.create("group:name:1.0@jar");
    assertNotNull(spec);
    assertEquals("group", spec.getGroup());
    assertEquals("name", spec.getName());
    assertEquals("1.0", spec.getVersion());
  }

  public void testCreate_groupNameAndPackage() {
    PsArtifactDependencySpec spec = PsArtifactDependencySpec.create("group:name@jar");
    assertNotNull(spec);
    assertEquals("group", spec.getGroup());
    assertEquals("name", spec.getName());
    assertEquals(null, spec.getVersion());
  }

  public void testCreate_nameAndPackage() {
    PsArtifactDependencySpec spec = PsArtifactDependencySpec.create("name@jar");
    assertNotNull(spec);
    assertEquals(null, spec.getGroup());
    assertEquals("name", spec.getName());
    assertEquals(null, spec.getVersion());
  }

  public void testCreate_complexSpecification() {
    PsArtifactDependencySpec spec = PsArtifactDependencySpec.create("a.b.c..323.d:name2:1.0-res.+:jdk15@jar");
    assertNotNull(spec);
    assertEquals("a.b.c..323.d", spec.getGroup());
    assertEquals("name2", spec.getName());
    assertEquals("1.0-res.+", spec.getVersion());
  }

  public void testCreate_artifactDependencyModel() {
    ArtifactDependencyModel model = Mockito.mock(ArtifactDependencyModel.class);
    when(model.group()).thenReturn(new TestGradleValue("group"));
    when(model.name()).thenReturn(new TestGradleValue("name"));
    when(model.version()).thenReturn(new TestGradleValue("version"));
    PsArtifactDependencySpec spec = PsArtifactDependencySpec.create(model);
    assertNotNull(spec);
    assertEquals("group", spec.getGroup());
    assertEquals("name", spec.getName());
    assertEquals("version", spec.getVersion());
  }

  public void testCreate_mavenCoordinates() {
    GradleCoordinate coordinates = GradleCoordinate.parseCoordinateString("group:name:version@aar");
    PsArtifactDependencySpec spec = PsArtifactDependencySpec.create(coordinates);
    assertNotNull(spec);
    assertEquals("group", spec.getGroup());
    assertEquals("name", spec.getName());
    assertEquals("version", spec.getVersion());
  }

  public void testCreate_gradleModuleVersion() {
    GradleModuleVersion version = mock(GradleModuleVersion.class);
    when(version.getGroup()).thenReturn("group");
    when(version.getName()).thenReturn("name");
    when(version.getVersion()).thenReturn("version");
    PsArtifactDependencySpec spec = PsArtifactDependencySpec.create(version);
    assertNotNull(spec);
    assertEquals("group", spec.getGroup());
    assertEquals("name", spec.getName());
    assertEquals("version", spec.getVersion());
  }

  public void testGetDisplayText_fullySpecifiedWithGroup() {
    PsArtifactDependencySpec spec = PsArtifactDependencySpec.create("group:name:1.0");
    assertNotNull(spec);

    PsUISettings.getInstance().DECLARED_DEPENDENCIES_SHOW_GROUP_ID = true;
    assertEquals("group:name:1.0", spec.getDisplayText());
  }

  public void testGetDisplayText_fullySpecifiedWithoutGroup() {
    PsArtifactDependencySpec spec = PsArtifactDependencySpec.create("group:name:1.0");
    assertNotNull(spec);

    PsUISettings.getInstance().DECLARED_DEPENDENCIES_SHOW_GROUP_ID = false;
    assertEquals("name:1.0", spec.getDisplayText());
  }

  public void testGetDisplayText_noVersionWithGroup() {
    PsArtifactDependencySpec spec = PsArtifactDependencySpec.create("group:name");
    assertNotNull(spec);

    PsUISettings.getInstance().DECLARED_DEPENDENCIES_SHOW_GROUP_ID = true;
    assertEquals("group:name", spec.getDisplayText());
  }

  public void testGetDisplayText_noVersionWithoutGroup() {
    PsArtifactDependencySpec spec = PsArtifactDependencySpec.create("group:name");
    assertNotNull(spec);

    PsUISettings.getInstance().DECLARED_DEPENDENCIES_SHOW_GROUP_ID = false;
    assertEquals("name", spec.getDisplayText());
  }

  public void testCompactNotation() {
    PsArtifactDependencySpec spec = PsArtifactDependencySpec.create("group:name:1.0");
    assertNotNull(spec);

    assertEquals("group:name:1.0", spec.compactNotation());
  }

  public void testEqualsAndHashCode() {
    EqualsVerifier.forClass(PsArtifactDependencySpec.class).verify();
  }

  private static class TestGradleValue implements GradleNullableValue<String>, GradleNotNullValue<String> {

    @NotNull
    private final String myValue;

    public TestGradleValue(@NotNull String value) {
      myValue = value;
    }

    @NotNull
    @Override
    public String value() {
      return myValue;
    }

    @Nullable
    @Override
    public PsiElement getPsiElement() {
      return null;
    }

    @NotNull
    @Override
    public VirtualFile getFile() {
      return null;
    }

    @NotNull
    @Override
    public String getPropertyName() {
      return null;
    }

    @Nullable
    @Override
    public String getDslText() {
      return null;
    }

    @NotNull
    @Override
    public Map<String, GradleNotNullValue<Object>> getResolvedVariables() {
      return null;
    }
  }
}