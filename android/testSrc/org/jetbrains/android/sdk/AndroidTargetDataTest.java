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
