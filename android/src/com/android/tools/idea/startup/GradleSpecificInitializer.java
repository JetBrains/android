/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.startup;

import com.android.tools.idea.startup.profile.GradleIdeConfigurationProfile;
import com.android.tools.idea.startup.profile.IdeConfigurationProfileService;
import org.jetbrains.annotations.NonNls;

/** Performs Gradle-specific IDE initialization */
public class GradleSpecificInitializer implements Runnable {
  /**
   * We set the timeout for Gradle daemons to -1, this way IDEA will not set it to 1 minute and it will use the default instead (3 hours.)
   * We need to keep Gradle daemons around as much as possible because creating new daemons is resource-consuming and slows down the IDE.
   */
  public static final int GRADLE_DAEMON_TIMEOUT_MS = -1;
  static {
    System.setProperty("external.system.remote.process.idle.ttl.ms", String.valueOf(GRADLE_DAEMON_TIMEOUT_MS));
  }

  @NonNls public static final String ENABLE_EXPERIMENTAL_ACTIONS = "enable.experimental.actions";

  @Override
  public void run() {
    IdeConfigurationProfileService.getInstance().setConfigurationProfileId(GradleIdeConfigurationProfile.ID);
  }
}
