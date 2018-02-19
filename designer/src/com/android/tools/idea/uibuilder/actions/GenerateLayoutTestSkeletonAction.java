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
package com.android.tools.idea.uibuilder.actions;

import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.common.editor.NlEditor;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.common.util.NlTreeDumper;
import com.android.tools.idea.rendering.parsers.AttributeSnapshot;
import com.android.tools.idea.templates.TemplateUtils;
import com.android.tools.idea.uibuilder.editor.NlPreviewManager;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import static com.android.SdkConstants.*;

public class GenerateLayoutTestSkeletonAction extends AnAction {
  private static final Pattern XML_PROLOG = Pattern.compile("^<\\?xml version.*$");

  public GenerateLayoutTestSkeletonAction() {
    super("Generate LayoutTest Skeleton");
  }

  @Override
  public void update(AnActionEvent event) {
    event.getPresentation().setEnabled(getModel(event.getProject()) != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    NlModel model = getModel(event.getProject());
    if (model == null) {
      return;
    }
    int option = Messages.showDialog(
      model.getProject(),
      "Generate LayoutTest skeleton with the current layout components.",
      "Generate LayoutTest Skeleton",
      new String[]{"Copy to Clipboard", "Cancel"},
      0,
      StudioIcons.Shell.Filetree.ANDROID_TEST_ROOT);

    if (option == 0) {
      CopyPasteManager.getInstance().setContents(new StringSelection(generateModelFixture(model)));
    }
  }

  @Nullable
  private static NlModel getModel(@Nullable Project project) {
    if (project == null) {
      return null;
    }
    DesignSurface surface = getSurface(project);
    if (surface == null) {
      return null;
    }
    SceneView screenView = surface.getCurrentSceneView();
    if (screenView == null) {
      return null;
    }
    return screenView.getModel();
  }

  @Nullable
  private static DesignSurface getSurface(@NotNull Project project) {
    FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
    FileEditor[] editors = fileEditorManager.getSelectedEditors();
    for (FileEditor fileEditor : editors) {
      if (fileEditor instanceof NlEditor) {
        return ((NlEditor)fileEditor).getComponent().getSurface();
      }
    }

    Editor editor = fileEditorManager.getSelectedTextEditor();
    if (editor == null) {
      return null;
    }

    NlPreviewManager previewManager = NlPreviewManager.getInstance(project);
    if (previewManager.isWindowVisible()) {
      return previewManager.getPreviewForm().getSurface();
    }

    PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, project);
    if (file == null) {
      return null;
    }

    for (FileEditor fileEditor : fileEditorManager.getEditors(file.getVirtualFile())) {
      if (fileEditor instanceof NlEditor) {
        return ((NlEditor)fileEditor).getComponent().getSurface();
      }
    }

    return null;
  }

  @NotNull
  private static String generateModelFixture(@NotNull NlModel model) {
    List<NlComponent> components = model.getComponents();
    StringBuilder builder = new StringBuilder();
    builder
      .append("import com.android.tools.idea.uibuilder.LayoutTestCase;\n")
      .append("import com.android.tools.idea.uibuilder.fixtures.ModelBuilder;\n")
      .append("import com.android.tools.idea.uibuilder.model.NlModel;\n")
      .append("import com.android.tools.idea.uibuilder.util.NlTreeDumper;\n")
      .append("import org.jetbrains.annotations.NotNull;\n")
      .append("\n")
      .append("import static com.android.SdkConstants.*;\n")
      .append("\n")
      .append("public class NewTest extends LayoutTestCase {\n")
      .append("\n")
      .append("  // TODO: Rename this test method\n")
      .append("  public void testRenameThis() {\n")
      .append("    NlModel model = createModel();\n")
      .append("  }\n")
      .append("\n")
      .append("  @NotNull\n")
      .append("  private NlModel createModel() {\n")
      .append("    ModelBuilder builder = model(\"").append(model.getFile().getName()).append("\",\n");

    for (NlComponent component : components) {
      appendComponent(component, "\"    ModelBuilder builder = model(".length(), builder);
    }

    builder.append(");\n");

    builder
      .append("    NlModel model = builder.build();\n")
      .append("    format(model.getFile());\n")
      .append("    assertEquals(").append(components.size()).append(", model.getComponents().size());\n");

    appendTreeComparison(components, builder);
    appendXmlComparison(model, builder);
    builder
      .append("\n")
      .append("    return model;\n")
      .append("  }\n")
      .append("}\n");

    return builder.toString();
  }

  private static void appendComponent(@NotNull NlComponent component, int indent, @NotNull StringBuilder builder) {
    builder
      .append(StringUtil.repeat(" ", indent))
      .append("component(").append(getTagSymbol(component.getTagName())).append(")\n")
      .append(StringUtil.repeat(" ", indent + 2))
      .append(makeBounds(component)).append("\n");

    for (AttributeSnapshot attribute : component.getAttributes()) {
      appendAttribute(attribute, indent + 4, builder);
    }

    if (component.getChildCount() > 0) {
      builder.append(StringUtil.repeat(" ", indent + 2)).append(".children(\n");
      for (NlComponent child : component.getChildren()) {
        appendComponent(child, indent + 4, builder);
        // Replace last \n with a comma separator:
        builder.replace(builder.length() - 1, builder.length(), ",\n\n");
      }
      // Replace last comma separator with end paren
      builder.replace(builder.length() - 3, builder.length(), ")\n");
    }
  }

  private static void appendAttribute(@NotNull AttributeSnapshot attribute, int indent, @NotNull StringBuilder builder) {
    if (implicitAttribute(attribute)) {
      return;
    }
    assert attribute.value != null;
    if (ANDROID_NS_NAME.equals(attribute.prefix)) {
      switch (attribute.name) {
        case ATTR_ID:
          appendAttribute(indent, ".id", attribute.value, builder);
          return;
        case ATTR_TEXT:
          appendAttribute(indent, ".text", attribute.value, builder);
          return;
        case ATTR_LAYOUT_HEIGHT:
          appendHeightOrWidth(indent, attribute.value, "height", builder);
          return;
        case ATTR_LAYOUT_WIDTH:
          appendHeightOrWidth(indent, attribute.value, "width", builder);
          return;
      }
    }
    appendAttribute(indent, ".withAttribute", attribute.prefix, attribute.name, attribute.value, builder);
  }

  private static void appendHeightOrWidth(int indent, @NotNull String value, @NotNull String orientation, @NotNull StringBuilder builder) {
    switch (value) {
      case VALUE_WRAP_CONTENT:
        appendAttribute(indent, ".wrapContent" + StringUtil.capitalize(orientation), builder);
        break;
      case VALUE_MATCH_PARENT:
        appendAttribute(indent, ".matchParent" + StringUtil.capitalize(orientation), builder);
        break;
      default:
        appendAttribute(indent, "." + orientation, value, builder);
        break;
    }
  }

  private static void appendAttribute(int indent, @NotNull String with, @NotNull StringBuilder builder) {
    builder.append(StringUtil.repeat(" ", indent)).append(with).append("()\n");
  }

  private static void appendAttribute(int indent, @NotNull String with, @Nullable String value, @NotNull StringBuilder builder) {
    appendAttribute(indent, with, null, null, value, builder);
  }

  private static void appendAttribute(int indent, @NotNull String with, @Nullable String prefix, @Nullable String name, @Nullable String value, @NotNull StringBuilder builder) {
    if (value == null) {
      return;
    }
    builder.append(StringUtil.repeat(" ", indent)).append(with).append("(\"");
    if (prefix != null) {
      builder.append(prefix).append(":");
    }
    if (name != null) {
      builder.append(name).append("\", \"");
    }
    builder.append(value).append("\")\n");
  }

  private static boolean implicitAttribute(@NotNull AttributeSnapshot attribute) {
    if (XMLNS.equals(attribute.prefix)) {
      return true;
    }
    if (attribute.value == null) {
      return true;
    }
    return false;
  }

  @NotNull
  private static String makeBounds(@NotNull NlComponent component) {
    Rectangle bounds = getBounds(component);
    return String.format(".withBounds(%d, %d, %d, %d)", bounds.x, bounds.y, bounds.width, bounds.height);
  }

  @NotNull
  private static Rectangle getBounds(@NotNull NlComponent component) {
    NlComponent parent = component.getParent();
    Rectangle parentBounds = parent != null ? getBounds(parent) : new Rectangle(0, 0, 1000, 1000);
    ViewInfo viewInfo = NlComponentHelperKt.getViewInfo(component);
    if (viewInfo == null) {
      return new Rectangle(parentBounds.x,
                           parentBounds.y,
                           100,
                           100);
    }
    return new Rectangle(viewInfo.getLeft() + parentBounds.x,
                         viewInfo.getTop() + parentBounds.y,
                         viewInfo.getRight() - viewInfo.getLeft(),
                         viewInfo.getBottom() - viewInfo.getTop());
  }

  @NotNull
  private static String getTagSymbol(@NotNull String tagName) {
    tagName = tagName.substring(1 + tagName.lastIndexOf('.'));
    return TemplateUtils.camelCaseToUnderlines(tagName).toUpperCase(Locale.ROOT);
  }

  private static void appendTreeComparison(@NotNull List<NlComponent> components, @NotNull StringBuilder builder) {
    appendMultilineAssert(NlTreeDumper.dumpTree(components), "NlTreeDumper.dumpTree(model.getComponents())", builder, ImmutableList.of(), false);
  }

  private static void appendXmlComparison(@NotNull NlModel model, @NotNull StringBuilder builder) {
    appendMultilineAssert(model.getFile().getText(), "model.getFile().getText()", builder, ImmutableList.of(XML_PROLOG), true);
  }

  private static void appendMultilineAssert(@NotNull String expectedMultilineResult,
                                            @NotNull String subject,
                                            @NotNull StringBuilder builder,
                                            @NotNull List<Pattern> lineFilters,
                                            boolean addNewlineToLastLine) {
    String assertEquals = "    assertEquals(";
    builder.append(assertEquals);

    String lastLine = "";
    int indent = 0;
    for (String line : Splitter.on("\n").split(expectedMultilineResult)) {
      if (matches(line, lineFilters)) {
        continue;
      }
      builder.append(StringUtil.repeat(" ", indent));
      builder.append("\"").append(line.replace("\"", "\\\"")).append("\\n\" +\n");
      indent = assertEquals.length();
      lastLine = line;
    }
    if (indent > 0) {
      builder.setLength(builder.length() - 6);
      if (lastLine.isEmpty()) {
        builder.setLength(builder.length() - 1 - indent - 6);
      }
      if (addNewlineToLastLine) {
        builder.append("\\n");
      }
      builder.append("\",\n");
    }
    else {
      builder.append("\"\",\n");
    }
    builder.append(StringUtil.repeat(" ", assertEquals.length()));
    builder.append(subject).append(");\n");
  }

  private static boolean matches(@NotNull String line, @NotNull List<Pattern> lineFilters) {
    for (Pattern pattern : lineFilters) {
      if (pattern.matcher(line).matches()) {
        return true;
      }
    }
    return false;
  }
}

