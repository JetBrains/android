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

import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * An experiment controlling gradual rollout of a feature. The experiment value must be an integer
 * between 0 and 100, indicating the percentage of users for whom the feature should be active.
 *
 * <p>If no experiment value is found, it will default to disabled. It will always be enabled for
 * internal plugin developers, unless the experiment value is set to the string "disabled".
 */
public class FeatureRolloutExperiment extends Experiment {

  private final UsernameProvider usernameProvider;

  @VisibleForTesting
  public FeatureRolloutExperiment(UsernameProvider provider, String key) {
    super(key);
    usernameProvider = provider;
  }

  public FeatureRolloutExperiment(String key) {
    this(new UsernameProvider() {}, key);
  }

  /** Returns true if the feature should be enabled for this user. */
  public boolean isEnabled() {
    if (Objects.equals(
        ExperimentService.getInstance().getExperimentString(this, /* defaultValue= */ null),
        "disabled")) {
      return false;
    }
    return getUserPercentage() < getRolloutPercentage();
  }

  private int getUserPercentage() {
    return getUserHash(usernameProvider.getUsername());
  }

  @Override
  public String getLogValue() {
    return String.valueOf(isEnabled());
  }

  /**
   * Returns an integer between 0 and 100, inclusive, indicating the percentage of users for whom
   * this feature should be enabled.
   *
   * <p>If the experiment value is outside the range [0, 100], 0 is returned.
   */
  private int getRolloutPercentage() {
    int percentage = ExperimentService.getInstance().getExperimentInt(this, /* defaultValue= */ 0);
    return percentage < 0 || percentage > 100 ? 0 : percentage;
  }

  /**
   * Returns an integer between 0 and 99, inclusive, based on {@link String#hashCode()} of the
   * feature key and username. If the rollout percentage is greater than this value, the feature
   * will be enabled for this user.
   *
   * @param userName the username of the current IDE user.
   *     <ol>
   *       Special cases (in order of priority, highest priority first):
   *       <li>If the current user is determined to be an internal developer via {@link
   *           InternalDevFlag#isInternalDev()}, return 0. This means that the feature will be
   *           enabled as soon as the rollout is greater than 0%.
   *       <li>If {@code userName} is null, return 99. This means that the feature will be inactive,
   *           unless it's set to 100% rollout. </>
   *     </ol>
   */
  @VisibleForTesting
  int getUserHash(@Nullable String userName) {
    if (InternalDevFlag.isInternalDev()) {
      return 0;
    }
    if (userName == null) {
      return 99;
    }
    int hash = (userName + getKey()).hashCode();
    return Math.abs(hash) % 100;
  }

  @Override
  public String getRawDefault() {
    return null;
  }

  @Override
  public String renderValue(String value) {
    try {
      int rollout = Integer.parseInt(value);
      int user = getUserPercentage();
      boolean enabled = user < rollout;
      return String.format("%d<%d? %s", user, rollout, enabled ? "enabled" : "disabled");
    } catch (NumberFormatException e) {
      return value;
    }
  }

  @Override
  public boolean isOverridden(List<ExperimentValue> values) {
    return super.isOverridden(values)
        || (values.size() == 1
            && !values.get(0).value().equals("100")
            && !values.get(0).value().equals("0"));
  }

  /** A class to provide user names, used to inject testing dependencies */
  @VisibleForTesting
  public interface UsernameProvider {
    default String getUsername() {
      return ExperimentUsernameProvider.getUsername();
    }
  }
}
