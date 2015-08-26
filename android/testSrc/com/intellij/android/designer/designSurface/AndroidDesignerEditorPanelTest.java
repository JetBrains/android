/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.intellij.android.designer.designSurface;

import com.android.tools.idea.rendering.IncludeReference;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.openapi.vfs.VirtualFile;

public class AndroidDesignerEditorPanelTest extends LayoutEditorTestBase {
  public void testSimple() {
    AndroidDesignerEditorPanel editor = createLayoutEditor(getTestFile("simple.xml"));
    RadViewComponent rootComponent = editor.getRootViewComponent();
    assertNotNull(rootComponent);
    assertEquals("Device Screen\n" +
                 "    LinearLayout",
                 printTree(rootComponent, false));
    //assertEquals("RadViewComponent{tag=<LinearLayout>, bounds=[0,0:768x1280}\n" +
    //             "    RadViewContainer{tag=<LinearLayout>, bounds=[0,100:768x1084}",
    //             printTree(rootComponent, true));
  }

  public void testSimple2() {
    AndroidDesignerEditorPanel editor = createLayoutEditor(getTestFile("simple2.xml"));
    RadViewComponent rootComponent = editor.getRootViewComponent();
    assertNotNull(rootComponent);
    assertEquals("Device Screen\n" +
                 "    LinearLayout (vertical)\n" +
                 "        Button - \"My Button\"\n" +
                 "        TextView - \"My TextView\"",
                 printTree(rootComponent, false));
    //assertEquals("RadViewComponent{tag=<LinearLayout>, bounds=[0,0:768x1280}\n" +
    //             "    RadViewContainer{tag=<LinearLayout>, bounds=[0,100:768x1084}\n" +
    //             "        RadViewComponent{tag=<Button>, bounds=[0,100:768x200}\n" +
    //             "        RadViewComponent{tag=<TextView>, bounds=[0,300:400x220}",
    //             printTree(rootComponent, true));
  }

  public void testShowInIncluded() {
    // create files first such that the editors can resolve references
    VirtualFile includer = getTestFile("includer.xml");
    VirtualFile included = getTestFile("included.xml");

    AndroidDesignerEditorPanel editor = createLayoutEditor(included);
    IncludeReference includedWithin = editor.getLastRenderResult().getIncludedWithin();
    assertNotNull(includedWithin);
    assertNotSame(IncludeReference.NONE, includedWithin);
    assertEquals("@layout/includer", includedWithin.getFromResourceUrl());

    RadViewComponent rootComponent = editor.getRootViewComponent();
    assertNotNull(rootComponent);
    assertEquals("Shown in @layout/includer\n" +
                 "    LinearLayout (vertical)\n" +
                 "        textView4 - \"Included Layout\"\n" +
                 "        textView5 - \"This text is from the ...ayout\"",
                 printTree(rootComponent, false));
    //assertEquals("RadViewComponent{tag=<LinearLayout>, bounds=[0,0:768x1280}\n" +
    //             "    RadViewContainer{tag=<LinearLayout>, bounds=[0,260:768x160}\n" +
    //             "        RadViewComponent{tag=<TextView>, id=@+id/textView4, bounds=[0,260:500x80}\n" +
    //             "        RadViewComponent{tag=<TextView>, id=@+id/textView5, bounds=[0,340:500x80}",
    //             printTree(rootComponent, true));

    AndroidDesignerEditorPanel includingEditor = createLayoutEditor(includer);
    rootComponent = includingEditor.getRootViewComponent();
    assertNotNull(rootComponent);
    assertEquals("Device Screen\n" +
                 "    LinearLayout (vertical)\n" +
                 "        textView - \"Outer Layout\"\n" +
                 "        textView2 - \"This text is from the ...ayout\"\n" +
                 "        include - @layout/included\n" +
                 "        textView3 - \"This text is at the en...ayout\"",
                 printTree(rootComponent, false));
    //assertEquals("RadViewComponent{tag=<LinearLayout>, bounds=[0,0:768x1280}\n" +
    //             "    RadViewContainer{tag=<LinearLayout>, bounds=[0,100:768x1084}\n" +
    //             "        RadViewComponent{tag=<TextView>, id=@+id/textView, bounds=[134,100:500x80}\n" +
    //             "        RadViewComponent{tag=<TextView>, id=@+id/textView2, bounds=[0,180:500x80}\n" +
    //             "        RadIncludeLayout{tag=<include>, bounds=[0,260:768x160}\n" +
    //             "        RadViewComponent{tag=<TextView>, id=@+id/textView3, bounds=[0,420:500x80}",
    //             printTree(rootComponent, true));
  }
}