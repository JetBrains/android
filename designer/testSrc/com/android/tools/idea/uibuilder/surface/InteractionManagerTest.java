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
package com.android.tools.idea.uibuilder.surface;

import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.model.SelectionModel;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.xml.XmlFile;
import org.intellij.lang.annotations.Language;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;

import static com.android.tools.idea.uibuilder.LayoutTestUtilities.*;

public class InteractionManagerTest extends LayoutTestCase {
  @Override
  protected boolean requireRecentSdk() {
    return true;
  }

  public void ignore_testDragAndDrop() throws Exception {
    // Drops a fragment (xmlFragment below) into the design surface (via drag & drop events) and verifies that
    // the resulting document ends up modified as expected.

    @Language("XML")
    String source = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                    "    android:layout_width=\"0dp\"\n" +
                    "    android:layout_height=\"0dp\"\n" +
                    "    android:orientation=\"vertical\">\n" +
                    "\n" +
                    "</LinearLayout>\n";
    XmlFile xmlFile = (XmlFile)myFixture.addFileToProject("res/layout/layout.xml", source);

    DesignSurface surface = createSurface();
    NlModel model = createModel(surface, myFacet, xmlFile);

    ScreenView screenView = createScreen(surface, model, new SelectionModel());
    DesignSurface designSurface = screenView.getSurface();
    InteractionManager manager = createManager(designSurface);

    @Language("XML")
    String xmlFragment = "" +
                         "<TextView xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                         "     android:layout_width=\"wrap_content\"\n" +
                         "     android:layout_height=\"wrap_content\"\n" +
                         "     android:text=\"Hello World\"\n" +
                         "/>";
    Transferable transferable = createTransferable(DataFlavor.stringFlavor, xmlFragment);
    dragDrop(manager, 0, 0, 100, 100, transferable);
    Disposer.dispose(model);

    @Language("XML")
    String expected = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                      "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                      "    android:layout_width=\"0dp\"\n" +
                      "    android:layout_height=\"0dp\"\n" +
                      "    android:orientation=\"vertical\">\n" +
                      "\n" +
                      "    <TextView\n" +
                      "            android:layout_width=\"match_parent\"\n" +
                      "            android:layout_height=\"wrap_content\"\n" +
                      "            android:text=\"Hello World\"\n" +
                      "            android:id=\"@+id/textView\"/>\n" +
                      "</LinearLayout>\n";
    assertEquals(expected, xmlFile.getText());
  }
}
