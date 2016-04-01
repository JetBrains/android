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
package com.android.tools.idea.uibuilder.palette;

import com.android.SdkConstants;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.uibuilder.model.ResourceType;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.psi.xml.XmlFile;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestCase;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import java.util.Set;

public class IconPreviewFactoryTest extends AndroidTestCase {

  @Language("XML")
  private static final String XML =
    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
    "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
    "    android:layout_width=\"match_parent\"\n" +
    "    android:layout_height=\"match_parent\">\n" +
    "    <!-- My comment -->\n" +
    "    <TextView " +
    "        android:layout_width=\"match_parent\"\n" +
    "        android:layout_height=\"match_parent\"\n" +
    "        android:text=\"@string/hello\" />\n" +
    "</FrameLayout>";

  @Override
  protected boolean requireRecentSdk() {
    return true;
  }

  // This test is here to make sure we generate a preview image for each component that is supposed to have a preview.
  // If LayoutLib has trouble with a component (or a combination of components) we may start missing previews of several components.
  // Also make sure we have unique IDs for all preview components.
  public void testLoad() throws Exception {
    List<String> requestedIds = Lists.newArrayList();
    List<String> generatedIds = Lists.newArrayList();
    IconPreviewFactory factory = IconPreviewFactory.get();
    factory.load(getConfiguration(), loadPalette(), true, requestedIds, generatedIds);

    Set<String> ids = Sets.newLinkedHashSet();
    for (String id : requestedIds) {
      if (!ids.add(id)) {
        fail("This id contains multiple definitions: " + id);
      }
    }

    for (String id : generatedIds) {
      if (!ids.remove(id)) {
        fail("Image generated but not requested: " + id);
      }
    }

    // TODO: Add the design support library to the test project (could not find out how to do so).
    assertTrue(ids.remove(SdkConstants.TOOLBAR_V7));

    assertEmpty("Failed to generate preview for: " + Joiner.on(", ").join(ids), ids);
  }

  private Configuration getConfiguration() {
    XmlFile layout1 = (XmlFile)myFixture.addFileToProject("res/layout/my_layout1.xml", XML);
    return myFacet.getConfigurationManager().getConfiguration(layout1.getVirtualFile());
  }

  private Palette loadPalette() throws Exception {
    NlPaletteModel model = NlPaletteModel.get(getProject());
    Reader reader = new InputStreamReader(NlPaletteModel.class.getResourceAsStream(ResourceType.LAYOUT.getPaletteFileName()));
    try {
      model.loadPalette(reader, ResourceType.LAYOUT);
    }
    finally {
      reader.close();
    }
    return model.getPalette(ResourceType.LAYOUT);
  }
}
