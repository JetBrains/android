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
package com.android.tools.idea.gradle.project.sync;

import static com.intellij.openapi.util.text.StringUtil.equalsIgnoreCase;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import java.util.List;
import org.jetbrains.plugins.gradle.internal.daemon.DaemonState;
import org.jetbrains.plugins.gradle.internal.daemon.GradleDaemonServices;

public abstract class GradleSyncIntegrationTestCase extends AndroidGradleTestCase {
  private boolean myUseSingleVariantSync;
  private boolean myDefaultUseSingleVariantSync;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myUseSingleVariantSync = StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.get();
    StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.override(useSingleVariantSyncInfrastructure());
    myDefaultUseSingleVariantSync = GradleExperimentalSettings.getInstance().USE_SINGLE_VARIANT_SYNC;
    GradleExperimentalSettings.getInstance().USE_SINGLE_VARIANT_SYNC = useSingleVariantSyncInfrastructure();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.override(myUseSingleVariantSync);
      GradleExperimentalSettings.getInstance().USE_SINGLE_VARIANT_SYNC = myDefaultUseSingleVariantSync;
    }
    finally {
      super.tearDown();
    }
  }

  static boolean areGradleDaemonsRunning() {
    List<DaemonState> daemonStatus = GradleDaemonServices.getDaemonsStatus();
    for (DaemonState status : daemonStatus) {
      if (!equalsIgnoreCase(status.getStatus(), "stopped")) {
        return true;
      }
    }
    return false;
  }

  protected boolean useSingleVariantSyncInfrastructure() {
    return true;
  }
}
