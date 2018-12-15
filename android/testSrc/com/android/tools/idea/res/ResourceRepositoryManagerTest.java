/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.res;

import static com.android.tools.idea.res.ResourcesTestsUtil.addBinaryAarDependency;
import static com.google.common.truth.Truth.assertThat;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.google.common.collect.Iterables;
import org.jetbrains.android.AndroidTestCase;

/**
 * Tests for {@link ResourceRepositoryManager}.
 */
public class ResourceRepositoryManagerTest extends AndroidTestCase {
  /**
   * Checks that adding an AAR dependency is reflected in the result returned by {@link ResourceRepositoryManager#getLibraryResources()}.
   */
  public void testLibraryResources() {
    enableNamespacing("p1.p2");

    ResourceRepositoryManager repositoryManager = ResourceRepositoryManager.getInstance(myFacet);
    assertThat(repositoryManager.getLibraryResources()).isEmpty();
    ResourceNamespace libraryNamespace = ResourceNamespace.fromPackageName("com.example.mylibrary");
    addBinaryAarDependency(myModule);
    assertThat(repositoryManager.getLibraryResources()).hasSize(1);
    assertThat(Iterables.getOnlyElement(repositoryManager.getLibraryResources()).getNamespace()).isEqualTo(libraryNamespace);
  }
}
