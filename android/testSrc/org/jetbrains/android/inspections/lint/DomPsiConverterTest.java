/*
 * Copyright (C) 2013 The Android Open Source Project
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
package org.jetbrains.android.inspections.lint;

import com.android.ide.common.xml.XmlPrettyPrinter;
import com.android.utils.XmlUtils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.AndroidTestCase;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.util.concurrent.atomic.AtomicReference;

import static com.android.SdkConstants.ANDROID_URI;

public class DomPsiConverterTest extends AndroidTestCase {
  public void test() {
    VirtualFile file = myFixture.copyFileToProject("AndroidManifest.xml", "AndroidManifest.xml");
    assertNotNull(file);
    assertTrue(file.exists());
    Project project = getProject();
    assertNotNull(project);
    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    assertTrue(psiFile instanceof XmlFile);
    XmlFile xmlFile = (XmlFile)psiFile;
    Document domDocument = DomPsiConverter.convert(xmlFile);
    assertNotNull(domDocument);

    // Pretty print; requires DOM iteration
    String formatted = XmlPrettyPrinter.prettyPrint(domDocument, true);

    // Compare to plain DOM implementation pretty printed
    @SuppressWarnings("ConstantConditions")
    String expected = XmlPrettyPrinter.prettyPrint(XmlUtils.parseDocumentSilently(xmlFile.getText(), true), true);

    assertEquals(expected, formatted);

    // Check some additional operations
    NodeList elementsByTagName = domDocument.getElementsByTagName("application");
    assertEquals(1, elementsByTagName.getLength());
    assertEquals("@drawable/icon", elementsByTagName.item(0).getAttributes().getNamedItemNS(ANDROID_URI, "icon").getNodeValue());
  }

  public void testAsyncAccess() throws InterruptedException {
    VirtualFile file = myFixture.copyFileToProject("AndroidManifest.xml", "AndroidManifest.xml");
    assertNotNull(file);
    assertTrue(file.exists());
    Project project = getProject();
    assertNotNull(project);
    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    assertTrue(psiFile instanceof XmlFile);
    XmlFile xmlFile = (XmlFile)psiFile;
    final Document domDocument = DomPsiConverter.convert(xmlFile);
    assertNotNull(domDocument);

    // Perform iteration on a different thread without read access
    final AtomicReference<String> formattedHolder = new AtomicReference<>();
    Thread thread = new Thread("dom psi") {
      @Override
      public void run() {
        try {
          assertFalse(ApplicationManager.getApplication().isReadAccessAllowed());
          String formatted = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
            @Override
            public String compute() {
              return XmlPrettyPrinter.prettyPrint(domDocument, true);
            }
          });
          formattedHolder.set(formatted);
        } catch (Exception e) {
          e.printStackTrace();
          fail(e.toString());
        }
      }
    };
    thread.start();
    thread.join();

    // Compare to plain DOM implementation pretty printed
    @SuppressWarnings("ConstantConditions")
    String expected = XmlPrettyPrinter.prettyPrint(XmlUtils.parseDocumentSilently(xmlFile.getText(), true), true);

    String formatted = formattedHolder.get();
    assertEquals(expected, formatted);

    // Check some additional operations
    NodeList elementsByTagName = domDocument.getElementsByTagName("application");
    assertEquals(1, elementsByTagName.getLength());
    assertEquals("@drawable/icon", elementsByTagName.item(0).getAttributes().getNamedItemNS(ANDROID_URI, "icon").getNodeValue());
  }
}
