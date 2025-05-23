/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.common.experiments;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.idea.common.experiments.FeatureRolloutExperiment.UsernameProvider;
import com.google.idea.testing.IntellijRule;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.NlsSafe;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineScopeKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link ExperimentServiceImpl}.
 */
@RunWith(JUnit4.class)
public class ExperimentServiceImplTest {

  enum TestEnum {AA, DefaultBB, CC}

  private static final BoolExperiment BOOL_EXPERIMENT = new BoolExperiment("test.property", false);
  private static final StringExperiment STRING_EXPERIMENT = new StringExperiment("test.property");
  private static final IntExperiment INT_EXPERIMENT = new IntExperiment("test.property", 0);
  private static final EnumExperiment<TestEnum>ENUM_EXPERIMENT = new EnumExperiment<>("test.enum", TestEnum.DefaultBB);
  private static final CoroutineScope DUMMY_SCOPE = CoroutineScopeKt.CoroutineScope(
      EmptyCoroutineContext.INSTANCE);

  @Rule
  public IntellijRule intellij = new IntellijRule();

  @Before
  public void setUp() {
    intellij.registerApplicationService(ApplicationInfo.class, new TestApplicationInfo());
  }

  @Test
  public void testEmptyLoadersList() {
    ExperimentService experimentService = new ExperimentServiceImpl(DUMMY_SCOPE,
        new ExperimentLoader[]{});
    assertThat(experimentService.getExperiment(BOOL_EXPERIMENT, false)).isFalse();
  }

  @Test
  public void testBooleanPropertyTrue() {
    ExperimentService experimentService =
        new ExperimentServiceImpl(DUMMY_SCOPE,
            new MapExperimentLoader("id", BOOL_EXPERIMENT.getKey(), "1"));
    assertThat(experimentService.getExperiment(BOOL_EXPERIMENT, false)).isTrue();
  }

  @Test
  public void testBooleanPropertyFalse() {
    ExperimentService experimentService =
        new ExperimentServiceImpl(DUMMY_SCOPE,
            new MapExperimentLoader("id", BOOL_EXPERIMENT.getKey(), "0"));
    assertThat(experimentService.getExperiment(BOOL_EXPERIMENT, true)).isFalse();
  }

  @Test
  public void testBooleanPropertyReturnsDefaultWhenMissing() {
    ExperimentService experimentService = new ExperimentServiceImpl(DUMMY_SCOPE,
        new MapExperimentLoader("id"));
    assertThat(experimentService.getExperiment(BOOL_EXPERIMENT, true)).isTrue();
  }

  @Test
  public void testStringProperty() {
    ExperimentService experimentService =
        new ExperimentServiceImpl(DUMMY_SCOPE,
            new MapExperimentLoader("id", STRING_EXPERIMENT.getKey(), "hi"));
    assertThat(experimentService.getExperimentString(STRING_EXPERIMENT, null)).isEqualTo("hi");
    assertThat(experimentService.getAllQueriedExperiments())
        .containsExactly(STRING_EXPERIMENT.getKey(), STRING_EXPERIMENT);
  }

  @Test
  public void testEnumProperty() {
    ExperimentService experimentService =
        new ExperimentServiceImpl(DUMMY_SCOPE,
            new MapExperimentLoader("id", ENUM_EXPERIMENT.getKey(), "cc"));
    intellij.registerApplicationComponent(ExperimentService.class, experimentService);
    assertThat(ENUM_EXPERIMENT.getValue()).isEqualTo(TestEnum.CC);
    assertThat(experimentService.getAllQueriedExperiments())
        .containsExactly(ENUM_EXPERIMENT.getKey(), ENUM_EXPERIMENT);
  }

  @Test
  public void testEnumProperty_caseInsensitivity() {
    ExperimentService experimentService =
        new ExperimentServiceImpl(DUMMY_SCOPE,
            new MapExperimentLoader("id", ENUM_EXPERIMENT.getKey(), "Aa"));
    intellij.registerApplicationComponent(ExperimentService.class, experimentService);
    assertThat(ENUM_EXPERIMENT.getValue()).isEqualTo(TestEnum.AA);
    assertThat(experimentService.getAllQueriedExperiments())
        .containsExactly(ENUM_EXPERIMENT.getKey(), ENUM_EXPERIMENT);
  }

  @Test
  public void testEnumProperty_default() {
    ExperimentService experimentService =
        new ExperimentServiceImpl(DUMMY_SCOPE,
            new MapExperimentLoader("id"));
    intellij.registerApplicationComponent(ExperimentService.class, experimentService);
    assertThat(ENUM_EXPERIMENT.getValue()).isEqualTo(TestEnum.DefaultBB);
    assertThat(experimentService.getAllQueriedExperiments())
        .containsExactly(ENUM_EXPERIMENT.getKey(), ENUM_EXPERIMENT);
  }

  @Test
  public void testBuildProperty() {
    ExperimentService experimentService =
        new ExperimentServiceImpl(DUMMY_SCOPE,
            new MapExperimentLoader("id", "12345." + STRING_EXPERIMENT.getKey(), "hi"));
    assertThat(experimentService.getExperimentString(STRING_EXPERIMENT, null)).isEqualTo("hi");
    assertThat(experimentService.getAllQueriedExperiments())
        .containsExactly(STRING_EXPERIMENT.getKey(), STRING_EXPERIMENT);
  }

  @Test
  public void testVeresionProperty() {
    ExperimentService experimentService =
        new ExperimentServiceImpl(DUMMY_SCOPE,
            new MapExperimentLoader("id", "1.2.3." + STRING_EXPERIMENT.getKey(), "hi"));
    assertThat(experimentService.getExperimentString(STRING_EXPERIMENT, null)).isEqualTo("hi");
    assertThat(experimentService.getAllQueriedExperiments())
        .containsExactly(STRING_EXPERIMENT.getKey(), STRING_EXPERIMENT);
  }

  @Test
  public void testStringPropertyReturnsDefaultWhenMissing() {
    ExperimentService experimentService = new ExperimentServiceImpl(DUMMY_SCOPE,
        new MapExperimentLoader("id"));
    assertThat(experimentService.getExperimentString(STRING_EXPERIMENT, "bye")).isEqualTo("bye");
  }

  @Test
  public void testFirstLoaderOverridesSecond() {
    ExperimentService experimentService =
        new ExperimentServiceImpl(DUMMY_SCOPE,
            new MapExperimentLoader("id", BOOL_EXPERIMENT.getKey(), "1"),
            new MapExperimentLoader("id", BOOL_EXPERIMENT.getKey(), "0"));
    assertThat(experimentService.getExperiment(BOOL_EXPERIMENT, false)).isTrue();
  }

  @Test
  public void testOnlyInSecondLoader() {
    ExperimentService experimentService =
        new ExperimentServiceImpl(DUMMY_SCOPE,
            new MapExperimentLoader("id"),
            new MapExperimentLoader("id", BOOL_EXPERIMENT.getKey(), "1"));
    assertThat(experimentService.getExperiment(BOOL_EXPERIMENT, false)).isTrue();
  }

  @Test
  public void testIntProperty() {
    ExperimentService experimentService =
        new ExperimentServiceImpl(DUMMY_SCOPE,
            new MapExperimentLoader("id", INT_EXPERIMENT.getKey(), "10"));
    assertThat(experimentService.getExperimentInt(INT_EXPERIMENT, 0)).isEqualTo(10);
  }

  @Test
  public void testIntPropertyDefaultValue() {
    ExperimentService experimentService = new ExperimentServiceImpl(DUMMY_SCOPE,
        new MapExperimentLoader("id"));
    assertThat(experimentService.getExperimentInt(INT_EXPERIMENT, 100)).isEqualTo(100);
  }

  @Test
  public void testIntPropertyThatDoesntParseReturnsDefaultValue() {
    ExperimentService experimentService =
        new ExperimentServiceImpl(DUMMY_SCOPE,
            new MapExperimentLoader("id", INT_EXPERIMENT.getKey(), "hello"));
    assertThat(experimentService.getExperimentInt(INT_EXPERIMENT, 111)).isEqualTo(111);
  }

  @Test
  public void testDataIsReloadedAgainWhenLeavingAScope() {
    MapExperimentLoader experimentLoader = new MapExperimentLoader("id");
    ExperimentService experimentService = new ExperimentServiceImpl(DUMMY_SCOPE, experimentLoader);
    experimentService.startExperimentScope();
    assertThat(experimentService.getExperimentString(STRING_EXPERIMENT, "default"))
        .isEqualTo("default");
    experimentLoader.map.put(STRING_EXPERIMENT.getKey(), "hello");
    experimentService.endExperimentScope();
    assertThat(experimentService.getExperimentString(STRING_EXPERIMENT, "default"))
        .isEqualTo("hello");
  }

  @Test
  public void testEnterTwoScopesButOnlyLeaveOne() {
    MapExperimentLoader experimentLoader = new MapExperimentLoader("id");
    ExperimentService experimentService = new ExperimentServiceImpl(DUMMY_SCOPE, experimentLoader);
    experimentService.startExperimentScope();
    assertThat(experimentService.getExperimentString(STRING_EXPERIMENT, "default"))
        .isEqualTo("default");
    experimentService.startExperimentScope();
    experimentService.endExperimentScope();
    experimentLoader.map.put(STRING_EXPERIMENT.getKey(), "hello");
    assertThat(experimentService.getExperimentString(STRING_EXPERIMENT, "default"))
        .isEqualTo("default");
  }

  @Test
  public void testEnterAndLeaveTwoScopes() {
    MapExperimentLoader experimentLoader = new MapExperimentLoader("id");
    ExperimentService experimentService = new ExperimentServiceImpl(DUMMY_SCOPE, experimentLoader);
    experimentService.startExperimentScope();
    assertThat(experimentService.getExperimentString(STRING_EXPERIMENT, "default"))
        .isEqualTo("default");
    experimentService.startExperimentScope();
    experimentLoader.map.put(STRING_EXPERIMENT.getKey(), "hello");
    experimentService.endExperimentScope();
    experimentService.endExperimentScope();
    assertThat(experimentService.getExperimentString(STRING_EXPERIMENT, "default"))
        .isEqualTo("hello");
  }

  @Test
  public void testLeaveAndEnterRefreshes() {
    MapExperimentLoader experimentLoader = new MapExperimentLoader("id");
    experimentLoader.map.put(STRING_EXPERIMENT.getKey(), "one");
    ExperimentService experimentService = new ExperimentServiceImpl(DUMMY_SCOPE, experimentLoader);
    experimentService.startExperimentScope();
    assertThat(experimentService.getExperimentString(STRING_EXPERIMENT, "default"))
        .isEqualTo("one");
    experimentLoader.map.put(STRING_EXPERIMENT.getKey(), "two");
    experimentService.endExperimentScope();
    experimentService.startExperimentScope();
    assertThat(experimentService.getExperimentString(STRING_EXPERIMENT, "default"))
        .isEqualTo("two");
  }

  @Test
  public void testGetOverrides() {
    ExperimentServiceImpl service =
        new ExperimentServiceImpl(DUMMY_SCOPE,
            new MapExperimentLoader("id1", "exp1", "value1", "exp3", "value3"),
            new MapExperimentLoader("id2", "exp1", "value2", "exp4", "value4"));

    assertThat(service.getOverrides("exp1"))
        .containsExactly(
            ExperimentValue.create("id1", "exp1", "value1"),
            ExperimentValue.create("id2", "exp1", "value2"));
    assertThat(service.getOverrides("exp3"))
        .containsExactly(ExperimentValue.create("id1", "exp3", "value3"));
    assertThat(service.getOverrides("exp4"))
        .containsExactly(ExperimentValue.create("id2", "exp4", "value4"));
  }

  @Test
  public void testGetOverrideLog() {
    ExperimentServiceImpl service =
        new ExperimentServiceImpl(DUMMY_SCOPE,
            new MapExperimentLoader(
                "id1", "bool", "1", "string", "over", "int", "10", "rollout", "10", "null", "0"),
            new MapExperimentLoader(
                "id2", "bool", "0", "string", "val", "int", "11", "rollout", "5"),
            new MapExperimentLoader("id3", "bool", "0", "string", "base", "int", "12"));

    BoolExperiment boolEx = new BoolExperiment("bool", true);
    StringExperiment stringEx = new StringExperiment("string");
    IntExperiment intEx = new IntExperiment("int", 42);
    IntExperiment nullEx = new IntExperiment("null", 0);
    FeatureRolloutExperiment rolloutEx =
        new FeatureRolloutExperiment(
            new UsernameProvider() {
              @Override
              public String getUsername() {
                return "username";
              }
            },
            "rollout");

    assertThat(service.getOverridesLog(boolEx))
        .isEqualTo("bool: true [id1], false [id2], false [id3], true [default]");
    assertThat(service.getOverridesLog(stringEx))
        .isEqualTo("string: over [id1], val [id2], base [id3]");
    assertThat(service.getOverridesLog(intEx))
        .isEqualTo("int: 10 [id1], 11 [id2], 12 [id3], 42 [default]");
    assertThat(service.getOverridesLog(rolloutEx))
        .isEqualTo("rollout: 7<10? enabled [id1], 7<5? disabled [id2]");
    assertThat(service.getOverridesLog(nullEx)).isEqualTo("");

    boolean unused = service.getExperiment(boolEx, true);
    unused = service.getExperiment(rolloutEx, true);
    unused = service.getExperiment(nullEx, true);

    assertThat(service.getOverridesLog())
        .isEqualTo(
            "bool: true [id1], false [id2], false [id3], true [default]\n"
                + "rollout: 7<10? enabled [id1], 7<5? disabled [id2]\n");
  }

  private static class MapExperimentLoader implements ExperimentLoader {

    private final String id;
    private final Map<String, String> map;

    private MapExperimentLoader(String id, String... keysAndValues) {
      this.id = id;
      checkState(keysAndValues.length % 2 == 0);
      map = new HashMap<>();
      for (int i = 0; i < keysAndValues.length; i += 2) {
        map.put(keysAndValues[i], keysAndValues[i + 1]);
      }
    }

    @Override
    public ImmutableMap<String, String> getExperiments() {
      return ImmutableMap.copyOf(map);
    }

    @Override
    public void initialize() {
    }

    @Override
    public String getId() {
      return id;
    }
  }

  private static class TestApplicationInfo extends ApplicationInfo {
    @Override
    public Calendar getBuildDate() {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull ZonedDateTime getBuildTime() {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull BuildNumber getBuild() {
      return new BuildNumber("AI", 2024, 12345);
    }

    @Override
    public @NotNull String getApiVersion() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getMajorVersion() {
      return "1";
    }

    @Override
    public String getMinorVersion() {
      return "2";
    }

    @Override
    public String getMicroVersion() {
      return "3";
    }

    @Override
    public String getPatchVersion() {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NlsSafe String getVersionName() {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NlsSafe String getCompanyName() {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NlsSafe String getShortCompanyName() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getCompanyURL() {
      throw new UnsupportedOperationException();
    }

    @Override
    public @Nullable String getProductUrl() {
      throw new UnsupportedOperationException();
    }

    @Override
    public @Nullable String getJetBrainsTvUrl() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasHelp() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasContextHelp() {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NlsSafe @NotNull String getFullVersion() {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NlsSafe @NotNull String getStrictVersion() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getFullApplicationName() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEssentialPlugin(@NotNull String pluginId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEssentialPlugin(@NotNull PluginId pluginId) {
      throw new UnsupportedOperationException();
    }
  }
}
