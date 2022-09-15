/*
 * Copyright (C) 2015 The Android Open Source Project
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
package org.jetbrains.android.sdk;

import static com.google.common.io.Files.asCharSink;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.sdklib.IAndroidTarget;
import com.android.testutils.TestUtils;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.util.xml.NanoXmlUtil;
import gnu.trove.TIntObjectHashMap;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import net.n3.nanoxml.IXMLBuilder;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;

public class AndroidTargetDataTest extends AndroidTestCase {

  private static void parseAndClose(InputStream stream, IXMLBuilder builder) throws IOException {
    try (stream) {
      NanoXmlUtil.parse(stream, builder);
    }
  }

  /**
   * Tests that we correctly can read the platform public.xml
   */
  public void testPlatformResourceIdMap() throws Exception {
    final AndroidTargetData.MyPublicResourceIdMapBuilder builder = new AndroidTargetData.MyPublicResourceIdMapBuilder();

    parseAndClose(Files.newInputStream(TestUtils.resolvePlatformPath("data/res/values/public-final.xml")), builder);

    TIntObjectHashMap<String> map = builder.getIdMap();
    assertEquals("@android:transition/move", map.get(0x010f0001));
    assertEquals("@android:id/widget_frame", map.get(0x01020018));
    assertEquals("@android:attr/colorSecondary", map.get(0x01010530));
    assertNull(map.get(0));
  }

  public void testPublicGroupParsing() throws IOException {
    final String publicXmlContent = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                                    "<!-- This file defines the base public resources exported by the\n" +
                                    "     platform, which must always exist. -->" +
                                    "" +
                                    "<resources>" +
                                    "  <eat-comment />\n" +
                                    "\n" +
                                    "  <public type=\"attr\" name=\"theme\" id=\"0x01010000\" />\n" +
                                    "  <public type=\"attr\" name=\"label\" id=\"0x01010001\" />\n" +
                                    "  <public type=\"attr\" name=\"manageSpaceActivity\" id=\"0x01010004\" />\n" +
                                    "\n" +
                                    "  <public-group type=\"attr\" first-id=\"0x01010531\">" +
                                    "        <public name=\"fontStyle\" />\n" +
                                    "        <public name=\"font\" />\n" +
                                    "        <public name=\"fontWeight\" />\n" +
                                    "        <public name=\"tooltipText\" />\n" +
                                    "        <public name=\"autoSizeText\" />\n" +
                                    "  </public-group>\n" +
                                    "  <public type=\"drawable\" name=\"btn_minus\" id=\"0x01080007\" />\n" +
                                    "  <public type=\"drawable\" name=\"btn_plus\" id=\"0x01080008\" />" +
                                    "  <public type=\"attr\" name=\"titleMargin\" id=\"0x010104f8\" />\n" +
                                    "  <public type=\"attr\" name=\"titleMarginStart\" id=\"0x010104f9\" />\n" +
                                    "  <public-group type=\"id\" first-id=\"0x01020041\">\n" +
                                    "     <public name=\"textAssist\" />\n" +
                                    "  </public-group>" +
                                    "</resources>";
    final AndroidTargetData.MyPublicResourceIdMapBuilder builder = new AndroidTargetData.MyPublicResourceIdMapBuilder();

    parseAndClose(new ByteArrayInputStream(publicXmlContent.getBytes(UTF_8)), builder);
    TIntObjectHashMap<String> map = builder.getIdMap();

    // Check that we handle correctly elements before and after a public-group
    assertEquals("@android:attr/theme", map.get(0x01010000));
    assertEquals("@android:drawable/btn_minus", map.get(0x01080007));
    assertEquals("@android:attr/titleMargin", map.get(0x010104f8));

    // Check the public group elements
    assertEquals("@android:attr/fontStyle", map.get(0x01010531));
    assertEquals("@android:attr/autoSizeText", map.get(0x01010531 + 4));
    assertEquals("@android:id/textAssist", map.get(0x01020041));
  }

  public void testRemovedAttrs() throws Exception {
    File tempDir = new File(myFixture.getTempDirPath());
    File resDir = new File(tempDir, "sdk/res");
    File valuesDir = new File(resDir, "values");
    assertTrue(valuesDir.mkdirs());

    File attrsXml = new File(valuesDir, "attrs.xml");
    asCharSink(attrsXml, UTF_8).write(
      // language=xml
      "<resources>" +
          "<attr name='realAttr' format='string' />" +
          "<attr name='__removed1' format='string' />" +
      "</resources>");

    File manifestAttrsXml = new File(valuesDir, "manifest-attrs.xml");
    asCharSink(manifestAttrsXml, UTF_8).write(
      // language=xml
      "<resources>" +
      "</resources>");


    File publicXml = new File(valuesDir, "public.xml");
    asCharSink(publicXml, UTF_8).write(
      // language=xml
      "<resources>" +
      "<public type='attr' name='realAttr' />" +
      "<public type='attr' name='__removed1' />" +
      "</resources>");

    LocalFileSystem.getInstance().refresh(false);

    IAndroidTarget target = mock(IAndroidTarget.class);
    when(target.getPath(eq(IAndroidTarget.ATTRIBUTES))).thenReturn(attrsXml.toPath());
    when(target.getPath(eq(IAndroidTarget.MANIFEST_ATTRIBUTES))).thenReturn(manifestAttrsXml.toPath());
    when(target.getPath(eq(IAndroidTarget.RESOURCES))).thenReturn(resDir.toPath());

    AndroidTargetData targetData = new AndroidTargetData(mock(AndroidSdkData.class), target);
    AttributeDefinitions publicAttrs = targetData.getPublicAttrDefs(getProject());

    assertThat(publicAttrs.getAttrs()).containsExactly(ResourceReference.attr(ResourceNamespace.ANDROID, "realAttr"));
  }
}
