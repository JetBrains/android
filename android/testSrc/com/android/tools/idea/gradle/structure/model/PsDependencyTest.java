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
package com.android.tools.idea.gradle.structure.model;

import com.android.tools.idea.gradle.dsl.api.dependencies.DependencyModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class PsDependencyTest {
  private PsDependency myPsDependency;

  @Before
  public void setUp() throws Exception {
    myPsDependency = new TestDependency(mock(PsModule.class), null);
  }

  @Test
  public void constructor_withDependency() throws Exception {
    DependencyModel testModelA = new TestDependencyModel();
    PsDependency dependency = new TestDependency(mock(PsModule.class), testModelA);

    assertThat(dependency.getParsedModels()).containsExactly(testModelA);
  }

  @Test
  public void getJoinedConfigurationNames_empty() throws Exception {
    assertEquals("", myPsDependency.getJoinedConfigurationNames());
  }

  @Test
  public void getJoinedConfigurationNames_single() throws Exception {
    myPsDependency.addParsedModel(new TestDependencyModel("ConfigA"));
    myPsDependency.addParsedModel(new TestDependencyModel("ConfigA"));

    assertEquals("ConfigA", myPsDependency.getJoinedConfigurationNames());
  }

  @Test
  public void getJoinedConfigurationNames_multiple() throws Exception {
    myPsDependency.addParsedModel(new TestDependencyModel("ConfigA"));
    myPsDependency.addParsedModel(new TestDependencyModel("ConfigB"));
    myPsDependency.addParsedModel(new TestDependencyModel("ConfigC"));

    assertEquals("ConfigA, ConfigB, ConfigC", myPsDependency.getJoinedConfigurationNames());
  }

  @Test
  public void getConfigurationNames_empty() throws Exception {
    assertThat(myPsDependency.getConfigurationNames()).isEmpty();
  }

  @Test
  public void getConfigurationNames_unique() throws Exception {
    myPsDependency.addParsedModel(new TestDependencyModel("ConfigA"));
    myPsDependency.addParsedModel(new TestDependencyModel("ConfigB"));
    myPsDependency.addParsedModel(new TestDependencyModel("ConfigC"));

    assertThat(myPsDependency.getConfigurationNames()).containsExactly("ConfigA", "ConfigB", "ConfigC");
  }

  @Test
  public void getConfigurationNames_duplicates() throws Exception {
    myPsDependency.addParsedModel(new TestDependencyModel("ConfigA"));
    myPsDependency.addParsedModel(new TestDependencyModel("ConfigB"));
    myPsDependency.addParsedModel(new TestDependencyModel("ConfigB"));

    assertThat(myPsDependency.getConfigurationNames()).containsExactly("ConfigA", "ConfigB");
  }

  @Test
  public void isDeclared() throws Exception {
    assertFalse(myPsDependency.isDeclared());

    myPsDependency.addParsedModel(new TestDependencyModel());

    assertTrue(myPsDependency.isDeclared());
  }

  @Test
  public void addParsedModel() throws Exception {
    assertThat(myPsDependency.getParsedModels()).isEmpty();

    DependencyModel testModel = new TestDependencyModel();
    myPsDependency.addParsedModel(testModel);

    assertThat(myPsDependency.getParsedModels()).containsExactly(testModel);
  }

  @Test
  public void getParsedModels() throws Exception {
    DependencyModel testModelA = new TestDependencyModel();
    DependencyModel testModelB = new TestDependencyModel();
    DependencyModel testModelC = new TestDependencyModel();
    myPsDependency.addParsedModel(testModelA);
    myPsDependency.addParsedModel(testModelB);
    myPsDependency.addParsedModel(testModelC);

    assertThat(myPsDependency.getParsedModels()).containsExactly(testModelA, testModelB, testModelC);
  }

  private static class TestDependency extends PsDependency {

    protected TestDependency(@NotNull PsModule parent, @Nullable DependencyModel parsedModel) {
      super(parent, parsedModel);
    }

    @NotNull
    @Override
    public String toText(@NotNull TextType type) {
      return "";
    }

    @NotNull
    @Override
    public String getName() {
      return "";
    }

    @Nullable
    @Override
    public Object getResolvedModel() {
      return null;
    }
  }

  private static class TestDependencyModel implements DependencyModel {

    @NotNull
    private final String myName;

    public TestDependencyModel() {
      myName = "Default Name";
    }

    public TestDependencyModel(@NotNull String name) {
      myName = name;
    }

    @NotNull
    @Override
    public String configurationName() {
      return myName;
    }
  }
}