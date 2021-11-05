/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.virtualtab;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.repository.targets.SystemImage;
import com.android.tools.idea.devicemanager.CountDownLatchAssert;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class VirtualDisplayListTest {
  @Rule
  public final AndroidProjectRule myRule = AndroidProjectRule.inMemory();
  private VirtualDeviceModel myVirtualDeviceModel;
  private VirtualDisplayList myVirtualDisplayList;

  @Before
  public void setUpFixture() throws Exception {
    IdeaTestFixtureFactory.getFixtureFactory().createBareFixture().setUp();
    myRule.mockProjectService(GradleBuildInvoker.class);
  }

  @Test
  public void emptyAvds() {
    myVirtualDeviceModel = new VirtualDeviceModel(Collections::emptyList);
    myVirtualDisplayList = new VirtualDisplayList(myRule.getProject(),
                                                  myVirtualDeviceModel,
                                                  null,
                                                  Mockito.mock(VirtualDeviceTableCellRenderer.class));

    myVirtualDisplayList.refreshAvds();

    assertTrue(myVirtualDisplayList.getTableItems().isEmpty());
  }

  @Test
  public void refreshAvds() throws InterruptedException {
    List<AvdInfo> avds = new ArrayList<>();
    CountDownLatch latch = new CountDownLatch(1);
    myVirtualDeviceModel = new VirtualDeviceModel(() -> avds);
    myVirtualDisplayList = new VirtualDisplayList(myRule.getProject(),
                                                  myVirtualDeviceModel,
                                                  latch,
                                                  Mockito.mock(VirtualDeviceTableCellRenderer.class));
    AvdInfo avd = new AvdInfo("Pixel 3",
                              Paths.get("ini/file"),
                              Paths.get("data/folder/path"),
                              Mockito.mock(SystemImage.class),
                              null);

    assertTrue(myVirtualDisplayList.getTableItems().isEmpty());

    // The AVDs should still be empty after more are available because it hasn't yet refreshed
    avds.add(avd);
    assertTrue(myVirtualDisplayList.getTableItems().isEmpty());

    // Refresh the AVDs
    myVirtualDisplayList.refreshAvds();

    CountDownLatchAssert.await(latch);

    // There is one AVD
    assertEquals(1, myVirtualDisplayList.getTableItems().size());
    assertEquals("Pixel 3", myVirtualDisplayList.getTableItems().get(0).getName());
  }
}
