// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.intellij.openapi.application.ex.PathManagerEx;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class LayoutlibProviderRegistrationTest {
  private static final String LAYOUTLIB_XML = "/META-INF/layoutlib.xml";

  @Test
  public void androidPluginRegistersLayoutlibProviderWithoutDesignTools() throws Exception {
    assertEquals(List.of(LAYOUTLIB_XML), layoutlibIncludes("android/android-plugin/descriptor/resources/META-INF/plugin.xml"));
    assertEquals(List.of(), layoutlibIncludes("android/design-plugin/descriptor/resources/META-INF/plugin.xml"));

    String layoutlibXml = Files.readString(communityFile("android/layoutlib/resources" + LAYOUTLIB_XML).toPath());
    assertTrue(layoutlibXml.contains("<layoutLibraryProvider implementation=\"com.android.layoutlib.LayoutlibProvider\"/>"));
  }

  private static List<String> layoutlibIncludes(String descriptorPath) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    NodeList includes = factory.newDocumentBuilder().parse(communityFile(descriptorPath))
      .getElementsByTagNameNS("http://www.w3.org/2001/XInclude", "include");
    List<String> result = new ArrayList<>();
    for (int i = 0; i < includes.getLength(); i++) {
      String href = ((Element)includes.item(i)).getAttribute("href");
      if (href.equals(LAYOUTLIB_XML)) {
        result.add(href);
      }
    }
    return result;
  }

  private static File communityFile(String path) {
    return PathManagerEx.findFileUnderCommunityHome(path);
  }
}
