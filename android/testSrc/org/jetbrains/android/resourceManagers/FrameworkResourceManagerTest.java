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
package org.jetbrains.android.resourceManagers;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.intellij.openapi.module.Module;
import org.jetbrains.android.AndroidTestCase;

public class FrameworkResourceManagerTest extends AndroidTestCase {

  public void testDisposedModule() {
    FrameworkResourceManager manager = new FrameworkResourceManager(myFacet.getModule(), true);
    // Check that the FrameworkResourceManagerTest.getLeafResourceRepositories method returns a single resource repository.
    assertThat(manager.getLeafResourceRepositories()).hasSize(1);

    Module disposedModule = mock(Module.class);
    when(disposedModule.isDisposed()).thenReturn(true);
    when(disposedModule.getProject()).thenReturn(getProject());
    manager = new FrameworkResourceManager(disposedModule, true);
    // Check that the FrameworkResourceManagerTest.getLeafResourceRepositories method gracefully handles a disposed module.
    assertThat(manager.getLeafResourceRepositories()).isEmpty();
  }
}
