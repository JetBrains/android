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

import com.google.common.base.Objects;
import com.intellij.android.designer.AndroidDesignerEditor;
import com.intellij.android.designer.componentTree.AndroidTreeDecorator;
import com.intellij.android.designer.model.*;
import com.intellij.designer.componentTree.AttributeWrapper;
import com.intellij.designer.designSurface.EditOperation;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.designSurface.tools.ComponentCreationFactory;
import com.intellij.designer.designSurface.tools.CreationTool;
import com.intellij.designer.model.MetaModel;
import com.intellij.designer.model.Property;
import com.intellij.designer.palette.DefaultPaletteItem;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.SimpleColoredRenderer;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.util.Collections;

public abstract class LayoutEditorTestBase extends AndroidTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  @Override
  public void tearDown() throws Exception {
    try {
      // Prevent LeakHunter from flagging this as a memory leak (RadViewLayout.INSTANCE points to the last seen component).
      RadViewLayout.INSTANCE.setContainer(null);
    } finally {
      super.tearDown();
    }
  }

  @Override
  protected boolean requireRecentSdk() {
    return true;
  }

  protected File getTestDir() {
    return new File(FileUtil.toSystemDependentName(getTestDataPath()), "designer");
  }

  @NotNull
  protected VirtualFile getTestFile(String filename) {
    File sourceFile = new File(getTestDir(), filename);
    return myFixture.copyFileToProject(sourceFile.getPath(), "res/layout/" + filename);
  }

  protected AndroidDesignerEditorPanel createLayoutEditor(VirtualFile xmlFile) {
    Project project = getProject();
    AndroidDesignerEditor editor = new AndroidDesignerEditor(project, xmlFile);
    AndroidDesignerEditorPanel panel = (AndroidDesignerEditorPanel)editor.getDesignerPanel();
    panel.requestImmediateRender();
    Disposer.register(project, editor);
    return panel;
  }

  public static String printTree(RadViewComponent root, boolean internal) {
    StringBuilder sb = new StringBuilder(200);
    if (internal) {
      describe(sb, root, 0);
    } else {
      decorate(sb, root, 0);
    }
    return sb.toString().trim();
  }

  private static void decorate(StringBuilder sb, RadViewComponent component, int depth) {
    for (int i = 0; i < depth; i++) {
      sb.append("    ");
    }

    SimpleColoredRenderer renderer = new SimpleColoredRenderer();
    AndroidTreeDecorator decorator = new AndroidTreeDecorator(RadModelBuilder.getProject(component));
    decorator.decorate(component, renderer, AttributeWrapper.DEFAULT, true);
    sb.append(renderer);
    sb.append('\n');
    for (RadViewComponent child : RadViewComponent.getViewComponents(component.getChildren())) {
      decorate(sb, child, depth + 1);
    }
  }

  private static void describe(StringBuilder sb, RadViewComponent component, int depth) {
    for (int i = 0; i < depth; i++) {
      sb.append("    ");
    }
    sb.append(describe(component));
    sb.append('\n');
    for (RadViewComponent child : RadViewComponent.getViewComponents(component.getChildren())) {
      describe(sb, child, depth + 1);
    }
  }

  private static String describe(RadViewComponent root) {
    return Objects.toStringHelper(root).omitNullValues()
      .add("tag", describe(root.getTag()))
      .add("id", root.getId())
      .add("bounds", describe(root.getBounds()))
      .toString();
  }

  private static String describe(@Nullable XmlTag tag) {
    if (tag == null) {
      return "";
    } else {
      return '<' + tag.getName() + '>';
    }
  }

  private static String describe(Rectangle rectangle) {
    // More brief description than toString default: java.awt.Rectangle[x=0,y=100,width=768,height=1084]
    return "[" + rectangle.x + "," + rectangle.y + ":" + rectangle.width + "x" + rectangle.height;
  }

  @NotNull
  protected Property getProperty(@NotNull RadViewComponent component, @NotNull String name) {
    for (Property property : component.getProperties()) {
      if (name.equals(property.getName())) {
        return property;
      }
    }
    fail("Did not find property as expected");
    throw new RuntimeException("not reached");
  }

  protected RadViewComponent addComponent(@NotNull AndroidDesignerEditorPanel editor, @NotNull final RadViewComponent parent,
                              @Nullable final RadViewComponent before, @NotNull String tagName) throws Exception {
    XmlFile psiFile = editor.getXmlFile();
    ViewsMetaManager metaManager = ViewsMetaManager.getInstance(getProject());
    MetaModel metaModel = metaManager.getModelByTag(tagName);
    assertNotNull(metaModel);

    final boolean ADD_AS_DROP_OPERATION = true;
    //noinspection ConstantConditions
    if (ADD_AS_DROP_OPERATION) {
      DefaultPaletteItem paletteItem = new DefaultPaletteItem(tagName, "", "", "", "", "");
      paletteItem.setMetaModel(metaModel);
      ComponentCreationFactory factory = editor.createCreationFactory(paletteItem);
      final OperationContext context = new OperationContext();
      context.setType(OperationContext.CREATE);
      CreationTool tool = new CreationTool(true, factory);
      context.setComponents(Collections.singletonList(tool.getFactory().create()));
      EditOperation operation = new AbstractEditOperation(parent, context) {
        @Override
        public void showFeedback() {
        }

        @Override
        public void eraseFeedback() {
        }

        @Override
        public void execute() throws Exception {
          AbstractEditOperation.execute(myContext, (RadViewComponent)myContainer, myComponents, before);
        }
      };
      operation.setComponents(context.getComponents());
      editor.getToolProvider().execute(Collections.singletonList(operation), context.getMessage());

      final RadViewComponent newComponent = (RadViewComponent)context.getComponents().get(0);
      assertNotNull(newComponent);
      return newComponent;
    } else {
      // Add as XML tag edit
      XmlTag tag = XmlElementFactory.getInstance(getProject()).createTagFromText(metaModel.getCreation());
      assertNotNull(tag);
      final RadViewComponent newComponent = RadComponentOperations.createComponent(tag, metaModel);
      assertNotNull(newComponent.getTag());
      assertEquals(tagName, newComponent.getTag().getName());

      WriteCommandAction<Void> action = new WriteCommandAction<Void>(getProject(), "Add Tag " + tagName, psiFile) {
        @Override
        protected void run(@NotNull Result<Void> result) throws Throwable {
          RadComponentOperations.addComponent(parent, newComponent, before);
        }
      };
      action.execute();
      return newComponent;
    }
  }

  protected void setProperty(@NotNull AndroidDesignerEditorPanel editor, @NotNull final RadViewComponent component,
                             @NotNull final String name, @Nullable final Object value) {
    XmlFile psiFile = editor.getXmlFile();
    WriteCommandAction<Void> action = new WriteCommandAction<Void>(getProject(), "Set Attribute " + name + " to " + value, psiFile) {
      @Override
      protected void run(@NotNull Result<Void> result) throws Throwable {
        //noinspection unchecked
        getProperty(component, name).setValue(component, value);
      }
    };
    action.execute();
  }
}
