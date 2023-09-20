/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.avdmanager.skincombobox;

import static org.junit.Assert.assertEquals;

import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.avdmanager.skincombobox.SkinComboBoxModel.Merge;
import com.android.tools.idea.concurrency.CountDownLatchAssert;
import com.android.tools.idea.concurrency.CountDownLatchFutureCallback;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SkinComboBoxModelTest {
  @Test
  public void load() throws Exception {
    // Arrange
    var path = Path.of(System.getProperty("user.home"), "Android", "Sdk", "platforms", "android-32", "skins", "HVGA");
    var skin = new PlatformSkin(path, new AndroidVersion(32));
    var latch = new CountDownLatch(1);

    var model = new SkinComboBoxModel(() -> List.of(skin), m -> new CountDownLatchFutureCallback<>(new Merge(m), latch));

    // Act
    model.load();

    // Assert
    CountDownLatchAssert.await(latch);
    assertEquals(List.of(NoSkin.INSTANCE, skin), model.getSkins());
  }
}
