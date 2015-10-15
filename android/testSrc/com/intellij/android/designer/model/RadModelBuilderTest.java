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
package com.intellij.android.designer.model;

import com.intellij.android.designer.designSurface.AndroidDesignerEditorPanel;
import com.intellij.android.designer.designSurface.LayoutEditorTestBase;

/**
 * <p>
 * TODO:
 * <ul>
 *   <li>Test show included in</li>
 *   <li>Test merge cookies</li>
 *   <li>Test properties</li>
 *   <li>Test that the various keyed fields are available (module providers, tree decorators, etc)</li>
 *   <li>Test consistency (that parent and children pointers etc) all agree after a sync, that properties are always
 *       loaded and the layout params are consistent with the parent</li>
 *   <li>Test that when we switch between merge and non-merge roots, we properly update the model</li>
 *   <li>Test included rendering</li>
 *   <li>Test layout interactions</li>
 *   <li>Test resizing</li>
 *   <li>Test configuration changes</li>
 *   <li>Test model conversion coordinates</li>
 *   <li>Test zooming and painting!</li>
 * </ul>
 */
public class RadModelBuilderTest extends LayoutEditorTestBase {
  // Test that the model is updated correctly after edits
  public void testAddComponent() throws Exception {
    AndroidDesignerEditorPanel editor = createLayoutEditor(getTestFile("simple2.xml"));
    RadViewComponent rootComponent = editor.getRootViewComponent();
    assertNotNull(rootComponent);
    assertEquals("Device Screen\n" +
                 "    LinearLayout (vertical)\n" +
                 "        Button - \"My Button\"\n" +
                 "        TextView - \"My TextView\"",
                 printTree(rootComponent, false));
    /* Bounds calculations vary across platforms; not included in test for now
    assertEquals("RadViewComponent{tag=<LinearLayout>, bounds=[0,0:768x1280}\n" +
                 "    RadViewContainer{tag=<LinearLayout>, bounds=[0,98:768x1086}\n" +
                 "        RadViewComponent{tag=<Button>, bounds=[0,98:768x200}\n" +
                 "        RadViewComponent{tag=<TextView>, bounds=[0,298:400x220}",
                 printTree(rootComponent, true));
   */

    final RadViewComponent parent = (RadViewComponent)rootComponent.getChildren().get(0);

    RadViewComponent editText = addComponent(editor, parent, null, "EditText");
    setProperty(editor, editText, "text",  "New Text Value");
    // Set size to predictable size to prevent test instability due to platform variations in font size and text layout
    setProperty(editor, editText, "layout:width",  "400px");
    setProperty(editor, editText, "layout:height",  "200px");

    editor.requestImmediateRender();

    assertEquals("Device Screen\n" +
                 "    LinearLayout (vertical)\n" +
                 "        Button - \"My Button\"\n" +
                 "        TextView - \"My TextView\"\n" +
                 "        editText - \"New Text Value\"",
                 printTree(rootComponent, false)
    );
    /* Bounds calculations vary across platforms; not included in test for now
    assertEquals("RadViewComponent{tag=<LinearLayout>, bounds=[0,0:768x1280}\n" +
                 "    RadViewContainer{tag=<LinearLayout>, bounds=[0,98:768x1086}\n" +
                 "        RadViewComponent{tag=<Button>, bounds=[0,98:768x200}\n" +
                 "        RadViewComponent{tag=<TextView>, bounds=[0,298:400x220}\n" +
                 "        RadViewComponent{tag=<EditText>, id=@+id/editText, bounds=[0,518:400x200}",
                 printTree(rootComponent, true));
    */

    // Also make sure the various state getters work correctly:
    assertSame(editor, RadModelBuilder.getDesigner(editText));
    assertSame(editor.getModule(), RadModelBuilder.getModule(editText));
    assertSame(editor.getProject(), RadModelBuilder.getProject(editText));
    assertSame(editor.getTreeDecorator(), RadModelBuilder.getTreeDecorator(editText));
    assertSame(editor.getXmlFile(), RadModelBuilder.getXmlFile(editText));
  }
}