/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run.cloud;

import com.android.tools.idea.run.ValidationError;
import com.google.common.collect.Lists;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Utility methods for cloud target choosers.
 */
public final class CloudTargetChooserUtil {
  private CloudTargetChooserUtil() {} // Not instantiable.

  @NotNull
  public static List<ValidationError> validate(@NotNull AndroidFacet facet,
                                               @NotNull CloudConfiguration.Kind kind,
                                               @NotNull String cloudProjectId,
                                               int cloudConfigurationId) {
    List<ValidationError> errors = Lists.newArrayList();
    if (cloudProjectId.isEmpty()) {
      errors.add(ValidationError.fatal("Cloud project not specified."));
    }
    CloudConfigurationProvider provider = CloudConfigurationProvider.getCloudConfigurationProvider();
    if (provider == null) {
      errors.add(ValidationError.fatal("No cloud configuration provider found."));
      // Can't continue.
      return errors;
    }

    CloudConfiguration selectedConfig = null;
    for (CloudConfiguration config : provider.getCloudConfigurations(facet, kind)) {
      if (config.getId() == cloudConfigurationId) {
        selectedConfig = config;
      }
    }

    if (selectedConfig == null) {
      errors.add(ValidationError.fatal("Cloud configuration not specified"));
      // Can't continue.
      return errors;
    }

    if (selectedConfig.getDeviceConfigurationCount() < 1) {
      errors.add(ValidationError.fatal("Selected cloud configuration is empty."));
    }

    return errors;
  }
}
