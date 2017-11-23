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
package com.android.tools.idea.gradle.structure.model.android;

import com.android.ide.common.gradle.model.stubs.ProductFlavorStub;
import com.android.tools.idea.gradle.dsl.api.android.ProductFlavorModel;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PsProductFlavorTest {
  @Test
  public void constructor_getNameFromResolvedModelOnly() throws Exception {
    PsProductFlavor flavor = new PsProductFlavor(mock(PsAndroidModule.class), new ProductFlavorStub(), null);
    assertEquals("name", flavor.getName());
  }

  @Test
  public void constructor_getNameFromResolvedModel() throws Exception {
    PsProductFlavor flavor = new PsProductFlavor(mock(PsAndroidModule.class), new ProductFlavorStub(), getParsedModelStub());
    assertEquals("name", flavor.getName());
  }

  @Test
  public void constructor_getNameFromParsedModel() throws Exception {
    PsProductFlavor flavor = new PsProductFlavor(mock(PsAndroidModule.class), null, getParsedModelStub());
    assertEquals("resolvedName", flavor.getName());
  }

  @NotNull
  private static ProductFlavorModel getParsedModelStub() {
    ProductFlavorModel model = mock(ProductFlavorModel.class);
    when(model.name()).thenReturn("resolvedName");
    return model;
  }

  @Test
  public void setName() throws Exception {
    PsProductFlavor flavor = new PsProductFlavor(mock(PsAndroidModule.class), null, null);
    assertEquals("", flavor.getName());
    flavor.setName("newName");
    assertEquals("newName", flavor.getName());
  }

  @Test
  public void isDeclared_declared() throws Exception {
    PsProductFlavor flavor = new PsProductFlavor(mock(PsAndroidModule.class), null, getParsedModelStub());
    assertTrue(flavor.isDeclared());
  }

  @Test
  public void isDeclared_notDeclared() throws Exception {
    PsProductFlavor flavor = new PsProductFlavor(mock(PsAndroidModule.class), null, null);
    assertFalse(flavor.isDeclared());
  }
}