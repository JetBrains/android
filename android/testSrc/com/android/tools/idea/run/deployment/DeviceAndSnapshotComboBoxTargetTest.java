/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.run.deployment;

import static org.junit.Assert.assertEquals;

import com.android.tools.idea.run.DeviceCount;
import com.android.tools.idea.run.DeviceFutures;
import com.android.tools.idea.run.deployment.DeviceAndSnapshotComboBoxTargetProvider.State;
import com.android.tools.idea.run.editor.DeployTarget;
import com.android.tools.idea.run.editor.DeployTargetState;
import com.android.tools.idea.testing.AndroidProjectRule;
import java.util.Collections;
import org.jetbrains.android.facet.AndroidFacet;
import org.junit.Rule;
import org.junit.Test;

public final class DeviceAndSnapshotComboBoxTargetTest {
  @Rule
  public final AndroidProjectRule myRule = AndroidProjectRule.inMemory();

  @Test
  public void getDevices() {
    DeployTarget<DeployTargetState> target = new DeviceAndSnapshotComboBoxTarget();
    DeployTargetState state = new State();
    AndroidFacet facet = AndroidFacet.getInstance(myRule.getModule());

    Object devices = target.getDevices(state, facet, DeviceCount.MULTIPLE, false, 2118987277);

    assertEquals(new DeviceFutures(Collections.emptyList()), devices);
  }
}
