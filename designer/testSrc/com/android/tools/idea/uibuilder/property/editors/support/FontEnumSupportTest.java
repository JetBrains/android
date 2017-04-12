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
package com.android.tools.idea.uibuilder.property.editors.support;

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.ResourceValueMap;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.adtui.workbench.PropertiesComponentMock;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.intellij.ide.util.PropertiesComponent;
import org.jetbrains.android.AndroidTestCase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;

import java.util.HashMap;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class FontEnumSupportTest extends AndroidTestCase {
  @Mock
  private NlProperty myProperty;
  @Mock
  private ResourceResolver myResolver;
  @Mock
  private NlModel myModel;

  private FontEnumSupport mySupport;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    ResourceValueMap fonts = ResourceValueMap.create();
    fonts.put("Lobster", new ResourceValue(ResourceUrl.create(null, ResourceType.FONT, "Lobster"), "/very/long/filename/do/not/use"));
    fonts.put("DancingScript", new ResourceValue(ResourceUrl.create(null, ResourceType.FONT, "DancingScript"), "/very/long/filename/do/not/use"));
    Map<ResourceType, ResourceValueMap> projectResources = new HashMap<>();
    projectResources.put(ResourceType.FONT, fonts);
    initMocks(this);
    when(myProperty.getResolver()).thenReturn(myResolver);
    when(myProperty.getModel()).thenReturn(myModel);
    when(myProperty.resolveValue(anyString())).thenAnswer(invocation -> invocation.getArguments()[0]);
    when(myProperty.resolveValue(eq("@font/Lobster"))).thenReturn("Lobster");
    when(myProperty.resolveValue(eq("@font/DancingScript"))).thenReturn("DancingScript");
    when(myResolver.getProjectResources()).thenReturn(projectResources);
    when(myModel.getFacet()).thenReturn(myFacet);
    PropertiesComponent propertiesComponent = new PropertiesComponentMock();
    registerApplicationComponent(PropertiesComponent.class, propertiesComponent);
    mySupport = new FontEnumSupport(myProperty);
  }

  public void testFindPossibleValues() {
    assertThat(mySupport.getAllValues()).containsExactly(
      new ValueWithDisplayString("sans-serif", "sans-serif"),
      new ValueWithDisplayString("sans-serif-condensed", "sans-serif-condensed"),
      new ValueWithDisplayString("serif", "serif"),
      new ValueWithDisplayString("monospace", "monospace"),
      new ValueWithDisplayString("serif-monospace", "serif-monospace"),
      new ValueWithDisplayString("casual", "casual"),
      new ValueWithDisplayString("cursive", "cursive"),
      new ValueWithDisplayString("sans-serif-smallcaps", "sans-serif-smallcaps"),
      new ValueWithDisplayString("Lobster", "@font/Lobster"),
      new ValueWithDisplayString("DancingScript", "@font/DancingScript")).inOrder();
  }

  public void testCreateDefaultValue() {
    assertThat(mySupport.createValue(""))
      .isEqualTo(ValueWithDisplayString.UNSET);
  }

  public void testCreateValueWithPrefix() {
    assertThat(mySupport.createValue("@font/Lobster"))
      .isEqualTo(new ValueWithDisplayString("Lobster", "@font/Lobster"));
  }

  public void testCreateValueWithoutPrefix() {
    assertThat(mySupport.createValue("serif"))
      .isEqualTo(new ValueWithDisplayString("serif", "serif"));
  }
}
