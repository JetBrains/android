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
package com.android.tools.idea.rendering;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.ResourceValueMap;
import com.android.resources.ResourceType;
import com.android.tools.adtui.imagediff.ImageDiffUtil;
import com.android.tools.idea.io.TestFileUtils;
import com.android.utils.XmlUtils;
import org.jetbrains.android.AndroidTestCase;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.SdkConstants.PREFIX_THEME_REF;
import static com.google.common.truth.Truth.assertThat;

public class GutterIconFactoryTest extends AndroidTestCase {

  private Path mySampleXmlPath;

  private static final int XML_MAX_WIDTH = 50, XML_MAX_HEIGHT = 60;
  private static final String XML_CONTENTS_FORMAT = "<vector android:height=\"%2$ddp\""
                                                    + " android:width=\"%1$ddp\""
                                                    + " xmlns:android=\"http://schemas.android.com/apk/res/android\"> "
                                                    + "</vector>";

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    mySampleXmlPath = FileSystems.getDefault().getPath(myModule.getProject().getBasePath(),
                                                       "app", "src", "main", "res", "drawable", "GutterIconTest_sample.xml");
  }

  public void testCreateIcon_XmlScalingMeetsSizeConstraints() throws Exception {
    for (int i = -1; i <= 1; i++) {
      for (int j = -1; j <= 1; j++) {
        int width = XML_MAX_WIDTH + i;
        int height = XML_MAX_HEIGHT + j;

        TestFileUtils.writeFileAndRefreshVfs(mySampleXmlPath, String.format(XML_CONTENTS_FORMAT, width, height));

        Icon icon = GutterIconFactory.createIcon(mySampleXmlPath.toString(), null, XML_MAX_WIDTH, XML_MAX_HEIGHT);

        assertThat(icon).isNotNull();
        assertThat(icon.getIconWidth()).isAtMost(XML_MAX_WIDTH);
        assertThat(icon.getIconHeight()).isAtMost(XML_MAX_HEIGHT);
      }
    }
  }

  public void testCreateIcon_BitmapBigEnough() throws Exception {
    String path = Paths.get(getTestDataPath(), "render/imageutils/actual.png").toString();
    BufferedImage input = ImageIO.read(new File(path));
    // Sanity check.
    assertThat(input.getHeight()).isGreaterThan(GutterIconCache.MAX_HEIGHT);
    assertThat(input.getWidth()).isGreaterThan(GutterIconCache.MAX_WIDTH);

    Icon icon = GutterIconFactory.createIcon(path, null, GutterIconCache.MAX_WIDTH, GutterIconCache.MAX_HEIGHT);
    assertThat(icon).isNotNull();
    assertThat(icon.getIconWidth()).isAtMost(GutterIconCache.MAX_WIDTH);
    assertThat(icon.getIconHeight()).isAtMost(GutterIconCache.MAX_HEIGHT);
  }

  public void testCreateIcon_BitmapSmallAlready() throws Exception {
    String path = Paths.get(getTestDataPath(), "annotator/ic_tick_thumbnail.png").toString();
    BufferedImage input = ImageIO.read(new File(path));
    // Sanity check.
    assertThat(input.getHeight()).isAtMost(GutterIconCache.MAX_HEIGHT);
    assertThat(input.getWidth()).isAtMost(GutterIconCache.MAX_WIDTH);

    Icon icon = GutterIconFactory.createIcon(path, null, GutterIconCache.MAX_WIDTH, GutterIconCache.MAX_HEIGHT);
    assertThat(icon).isNotNull();
    BufferedImage output = TestRenderingUtils.getImageFromIcon(icon);

    // Input and output should be identical.
    ImageDiffUtil.assertImageSimilar(getName(), input, output, 0);
  }

  public void testIsReference() {
    final String themeReference = PREFIX_THEME_REF + "android:attr/textColorSecondary";
    final String resourceReference = PREFIX_RESOURCE_REF + "android:color/opaque_red";
    final String notAReference = "#00aa00";

    assertThat(GutterIconFactory.isReference(themeReference)).isTrue();
    assertThat(GutterIconFactory.isReference(resourceReference)).isTrue();
    assertThat(GutterIconFactory.isReference(notAReference)).isFalse();
  }

  public void testReplaceResourceReferences() {
    final String red = "#ff0000";
    final String green = "#00ff00";
    final String[] colors = {red, "@color/directRef", "@color/indirectRef", "@color/indirectRefCycle"};

    final Node root = createVectorRoot(colors);
    final ResourceResolver resolver = createResourceResolver();

    addColor(resolver, "directRef", green);
    addColor(resolver, "indirectRef", "@color/directRef");
    addColor(resolver, "indirectRefCycle", "@color/indirectRefCycle");

    // Sanity check
    for (int i = 0; i < colors.length; i++) {
      assertThat(getColorFromRoot(root, i)).isEqualTo(colors[i]);
    }

    GutterIconFactory.replaceResourceReferences(root, resolver);

    // Hardcoded color should not have been affected
    assertThat(getColorFromRoot(root, 0)).isEqualTo(red);
    // Both direct and indirect references should have been replaced with true value
    assertThat(getColorFromRoot(root, 1)).isEqualTo(green);
    assertThat(getColorFromRoot(root, 2)).isEqualTo(green);
    // Cyclic references should not be replaced
    assertThat(getColorFromRoot(root, 3)).isEqualTo(colors[3]);
  }

  // Helper methods for testing replaceResourceReferences

  private static String getColorFromRoot(Node root, int i) {
    return root.getChildNodes().item(i).getAttributes().getNamedItem("android:fillColor").getNodeValue();
  }

  private static Node createVectorRoot(String...colors) {
    StringBuilder xml = new StringBuilder("<vector" +
                       " android:height=\"50px\"  android:width=\"50px\"" +
                       " android:viewportWidth=\"50.0\" android:viewportHeight=\"50.0\"" +
                       " xmlns:android=\"http://schemas.android.com/apk/res/android\">");

    for (String color: colors) {
      xml.append("<path android:fillColor=\"");
      xml.append(color);
      xml.append("\" android:pathData=\"M0,0 L50,0 L50,50 z\"/>");
    }

    xml.append("</vector>");
    return XmlUtils.parseDocumentSilently(xml.toString(), true).getDocumentElement();
  }

  private static ResourceResolver createResourceResolver() {
    return ResourceResolver.create(Collections.singletonMap(ResourceNamespace.RES_AUTO,
                                                            Collections.singletonMap(ResourceType.COLOR,
                                                                                     ResourceValueMap.create())),
                                   null);
  }

  private static void addColor(ResourceResolver resolver, String colorName, String colorValue) {
    ResourceValueMap resourceValueMap = resolver.getProjectResources().get(ResourceType.COLOR);

    resourceValueMap.put(colorName, new ResourceValue(ResourceNamespace.TODO, ResourceType.COLOR, colorName, colorValue));
  }
}
