/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.ResourceRepository;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.android.AndroidFacetProjectDescriptor;
import org.jetbrains.annotations.NotNull;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

public class ResourceClassRegistryTest extends LightJavaCodeInsightFixtureTestCase {
  ResourceClassRegistry myRegistry;
  private ResourceIdManager myIdManager;

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return AndroidFacetProjectDescriptor.INSTANCE;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myRegistry = spy(ResourceClassRegistry.get(getProject()));
    myIdManager = ResourceIdManager.get(myFixture.getModule());
  }

  @Override
  public void tearDown() throws Exception {
    try {
      myIdManager.resetDynamicIds();
      myRegistry.clearCache();
    }
    finally {
      super.tearDown();
    }
  }

  public void testAddLibrary() {
    String pkg1 = "com.google.example1";
    String pkg2 = "com.google.example2";
    ResourceRepository repository = mock(ResourceRepository.class);
    myRegistry.addLibrary(repository, myIdManager, pkg1, ResourceNamespace.fromPackageName(pkg1));
    assertSameElements(myRegistry.getGeneratorMap().keySet(), repository);
    assertSameElements(myRegistry.getPackages(), pkg1);
    myRegistry.addLibrary(repository, myIdManager, pkg2, ResourceNamespace.fromPackageName(pkg2));
    assertSameElements(myRegistry.getGeneratorMap().keySet(), repository);
    assertSameElements(myRegistry.getPackages(), pkg1, pkg2);
  }
}
