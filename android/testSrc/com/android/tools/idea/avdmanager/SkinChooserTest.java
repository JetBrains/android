/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.avdmanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Executor;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SkinChooserTest {
  @Ignore("b/176905390")
  @Test
  public void skinChooser() {
    // Arrange
    ListenableFuture<Collection<Path>> future = Futures.immediateFailedFuture(new RuntimeException());
    Executor executor = MoreExecutors.directExecutor();

    // Act
    SkinChooser chooser = new SkinChooser(null, () -> future, executor, executor);

    // Assert
    assertFalse(chooser.isEnabled());

    assertEquals(Collections.singletonList(SkinChooser.FAILED_TO_LOAD_SKINS), chooser.getItems());
    assertEquals(SkinChooser.FAILED_TO_LOAD_SKINS, chooser.getComboBox().getSelectedItem());
  }
}
