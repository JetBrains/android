/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.idea.testing.IntellijRule;
import com.google.idea.testing.UsernameRule;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link FeatureRolloutExperiment}. */
@RunWith(JUnit4.class)
public class FeatureRolloutExperimentTest {

  @Rule public IntellijRule intellij = new IntellijRule();
  @Rule public UsernameRule usernameRule = new UsernameRule();
  private MockExperimentService experimentService;
  private FeatureRolloutExperiment rolloutExperiment;

  @Before
  public void setUp() {
    InternalDevFlag.markUserAsInternalDev(false);

    experimentService = new MockExperimentService();
    intellij.registerApplicationService(ExperimentService.class, experimentService);

    rolloutExperiment = new FeatureRolloutExperiment("feature.name.for.canary.rollout");
  }

  private void setFeatureRolloutPercentage(int percentage) {
    experimentService.setFeatureRolloutExperiment(rolloutExperiment, percentage);
  }

  @Test
  public void userHashesInCorrectRange() {
    List<String> userNames =
        Stream.generate(FeatureRolloutExperimentTest::generateUsername)
            .limit(10000)
            .collect(Collectors.toList());

    for (String userName : userNames) {
      int percentage = rolloutExperiment.getUserHash(userName);
      assertWithMessage(userName).that(percentage).isLessThan(100);
      assertWithMessage(userName).that(percentage).isAtLeast(0);
    }
  }

  @Test
  public void userHashIsSameForEqualUsernameAndExperiment() {
    rolloutExperiment = new FeatureRolloutExperiment("rollout.experiment.one");
    FeatureRolloutExperiment equalExperiment =
        new FeatureRolloutExperiment("rollout.experiment.one");

    List<String> userNames =
        Stream.generate(FeatureRolloutExperimentTest::generateUsername)
            .limit(10000)
            .collect(Collectors.toList());

    for (String userName : userNames) {
      int percentage = rolloutExperiment.getUserHash(userName);
      assertWithMessage(userName)
          .that(percentage)
          .isEqualTo(rolloutExperiment.getUserHash(userName));
      assertWithMessage(userName).that(percentage).isEqualTo(equalExperiment.getUserHash(userName));
    }
  }

  @Test
  public void userHashForSameUserDependsOnExperimentKey() {
    rolloutExperiment = new FeatureRolloutExperiment("rollout.experiment.one");
    FeatureRolloutExperiment differentExperiment =
        new FeatureRolloutExperiment("rollout.experiment.two");

    List<String> userNames =
        Stream.generate(FeatureRolloutExperimentTest::generateUsername)
            .limit(10000)
            .collect(Collectors.toList());

    long differenceCount =
        userNames.stream()
            .filter(
                userName ->
                    rolloutExperiment.getUserHash(userName)
                        != differentExperiment.getUserHash(userName))
            .count();
    assertThat(differenceCount).isAtLeast(9800L);
  }

  /**
   * Test that for some set of 'random' userNames, approximately the expected proportion of users
   * have the feature enabled.
   */
  @Test
  public void rolloutPercentageApproximatelyCorrect() {
    List<String> userNames =
        Stream.generate(FeatureRolloutExperimentTest::generateUsername)
            .limit(10000)
            .collect(Collectors.toList());

    setFeatureRolloutPercentage(0);
    assertThat(userNames.stream().noneMatch(this::isEnabled)).isTrue();

    setFeatureRolloutPercentage(100);
    assertThat(userNames.stream().allMatch(this::isEnabled)).isTrue();

    setFeatureRolloutPercentage(2);
    long matchCount = userNames.stream().filter(this::isEnabled).count();
    assertThat(matchCount).isAtLeast(150L);
    assertThat(matchCount).isAtMost(250L);

    setFeatureRolloutPercentage(20);
    matchCount = userNames.stream().filter(this::isEnabled).count();
    assertThat(matchCount).isAtLeast(1900L);
    assertThat(matchCount).isAtMost(2100L);

    setFeatureRolloutPercentage(50);
    matchCount = userNames.stream().filter(this::isEnabled).count();
    assertThat(matchCount).isAtLeast(4900L);
    assertThat(matchCount).isAtMost(5100L);
  }

  @Test
  public void alwaysDisabled_ifInvalidRolloutPercentage() {
    List<String> userNames =
        Stream.generate(FeatureRolloutExperimentTest::generateUsername)
            .limit(10000)
            .collect(Collectors.toList());

    setFeatureRolloutPercentage(-1);
    assertThat(userNames.stream().noneMatch(this::isEnabled)).isTrue();

    setFeatureRolloutPercentage(101);
    assertThat(userNames.stream().noneMatch(this::isEnabled)).isTrue();

    setFeatureRolloutPercentage(200);
    assertThat(userNames.stream().noneMatch(this::isEnabled)).isTrue();
  }

  @Test
  public void alwaysEnabledForDevs_ifRolloutGreaterZero() {
    List<String> userNames =
        Stream.generate(FeatureRolloutExperimentTest::generateUsername)
            .limit(10000)
            .collect(Collectors.toList());
    InternalDevFlag.markUserAsInternalDev(true);

    setFeatureRolloutPercentage(1);
    assertThat(userNames.stream().allMatch(this::isEnabled)).isTrue();

    setFeatureRolloutPercentage(20);
    assertThat(userNames.stream().allMatch(this::isEnabled)).isTrue();

    setFeatureRolloutPercentage(100);
    assertThat(userNames.stream().allMatch(this::isEnabled)).isTrue();
  }

  @Test
  public void notAlwaysEnabled_whenDevMarkerDisabled() {
    List<String> userNames =
        Stream.generate(FeatureRolloutExperimentTest::generateUsername)
            .limit(10000)
            .collect(Collectors.toList());
    InternalDevFlag.markUserAsInternalDev(true);
    experimentService.setExperiment(InternalDevFlag.disableInternalDevMarker, true);

    setFeatureRolloutPercentage(0);
    assertThat(userNames.stream().noneMatch(this::isEnabled)).isTrue();

    setFeatureRolloutPercentage(20);
    long matchCount = userNames.stream().filter(this::isEnabled).count();
    assertThat(matchCount).isAtLeast(1900L);
    assertThat(matchCount).isAtMost(2100L);

    setFeatureRolloutPercentage(100);
    assertThat(userNames.stream().allMatch(this::isEnabled)).isTrue();
  }

  @Test
  public void disabledForDevs_ifRolloutZeroOrLess() {
    List<String> userNames =
        Stream.generate(FeatureRolloutExperimentTest::generateUsername)
            .limit(10000)
            .collect(Collectors.toList());
    InternalDevFlag.markUserAsInternalDev(true);

    setFeatureRolloutPercentage(0);
    assertThat(userNames.stream().noneMatch(this::isEnabled)).isTrue();

    setFeatureRolloutPercentage(-1);
    assertThat(userNames.stream().noneMatch(this::isEnabled)).isTrue();
  }

  @Test
  public void alwaysDisabled_whenExplicitlyDisabled() {
    List<String> userNames =
        Stream.generate(FeatureRolloutExperimentTest::generateUsername)
            .limit(10000)
            .collect(Collectors.toList());
    experimentService.setExperimentRaw(rolloutExperiment.getKey(), "disabled");

    InternalDevFlag.markUserAsInternalDev(false);
    assertThat(userNames.stream().noneMatch(this::isEnabled)).isTrue();

    InternalDevFlag.markUserAsInternalDev(true);
    assertThat(userNames.stream().noneMatch(this::isEnabled)).isTrue();
  }

  private boolean isEnabled(String userName) {
    usernameRule.setUsername(userName);
    return rolloutExperiment.isEnabled();
  }

  private static final Random random = new Random(12345);

  /** Generates a string of lower-case letters, between 1 and 10 characters in length. */
  private static String generateUsername() {
    int length = Math.round(random.nextFloat() * 9 + 1);
    char diff = 'z' - 'a';
    StringBuilder builder = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      builder.append((char) ('a' + random.nextFloat() * diff));
    }
    return builder.toString();
  }
}
