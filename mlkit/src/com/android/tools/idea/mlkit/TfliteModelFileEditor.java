/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.mlkit;

import com.android.tools.mlkit.MetadataExtractor;
import com.android.tools.mlkit.MlkitNames;
import com.android.tools.mlkit.ModelInfo;
import com.android.tools.mlkit.ModelParsingException;
import com.android.tools.mlkit.SubGraphInfo;
import com.android.tools.mlkit.TensorInfo;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import java.awt.Component;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Editor for the TFLite mode file.
 */
// TODO(b/148866418): complete this based on the UX spec.
public class TfliteModelFileEditor extends UserDataHolderBase implements FileEditor {
  private static final String NAME = "TFLite Model File";
  private static final String HTML_TABLE_STYLE = "<style>\n" +
                                                 "table {\n" +
                                                 "  font-family: arial, sans-serif;\n" +
                                                 "  border-collapse: collapse;\n" +
                                                 "  width: 60%;\n" +
                                                 "}\n" +
                                                 "td, th {\n" +
                                                 "  border: 0;\n" +
                                                 "  text-align: left;\n" +
                                                 "  padding: 8px;\n" +
                                                 "}\n" +
                                                 "</style>";

  private final Module myModule;
  private final VirtualFile myFile;
  private final JBScrollPane myRootPane;

  public TfliteModelFileEditor(@NotNull Project project, @NotNull VirtualFile file) {
    myFile = file;
    myModule = ModuleUtilCore.findModuleForFile(file, project);

    JPanel contentPanel = new JPanel();
    contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
    contentPanel.setBackground(UIUtil.getTextFieldBackground());
    contentPanel.setBorder(JBUI.Borders.empty(20));

    try {
      ModelInfo modelInfo = ModelInfo.buildFrom(new MetadataExtractor(ByteBuffer.wrap(file.contentsToByteArray())));
      addModelSection(contentPanel, modelInfo);
      addTensorsSection(contentPanel, modelInfo);

      PsiClass modelClass = MlkitModuleService.getInstance(myModule)
        .getOrCreateLightModelClass(new MlModelMetadata(file.getUrl(), MlkitUtils.computeModelClassName(file.getUrl())));
      addSampleCodeSection(contentPanel, modelClass);
    }
    catch (IOException e) {
      Logger.getInstance(TfliteModelFileEditor.class).error(e);
    }
    catch (ModelParsingException e) {
      Logger.getInstance(TfliteModelFileEditor.class).warn(e);
      // TODO(deanzhou): show warning message in panel
    }

    myRootPane = new JBScrollPane(contentPanel);
  }

  private static JTextPane createPaneFromHtml(@NotNull String html) {
    JTextPane modelPane = new JTextPane();
    modelPane.setAlignmentX(Component.LEFT_ALIGNMENT);
    setHtml(modelPane, html);

    return modelPane;
  }

  @NotNull
  private static String createHtmlRow(@NotNull String[] row, boolean useHeaderCells) {
    StringBuilder htmlRow = new StringBuilder();
    htmlRow.append("<tr>\n");
    for (String value : row) {
      htmlRow.append(useHeaderCells ? "<th>" + value + "</th>\n" : "<td>" + value + "</td>\n");
    }
    htmlRow.append("</tr>\n");

    return htmlRow.toString();
  }

  private static void addModelSection(@NotNull JPanel contentPanel, @NotNull ModelInfo modelInfo) {
    // TODO(b/148866418): make table collapsible.
    List<String[]> table = new ArrayList<>();
    table.add(new String[]{"Name", modelInfo.getModelName()});
    table.add(new String[]{"Description", modelInfo.getModelDescription()});
    table.add(new String[]{"Version", modelInfo.getModelVersion()});
    table.add(new String[]{"Author", modelInfo.getModelAuthor()});
    table.add(new String[]{"License", modelInfo.getModelLicense()});

    contentPanel.add(createPaneFromTable(table, "Model", false));
  }

  private static void addSampleCodeSection(@NotNull JPanel contentPanel, @NotNull PsiClass modelClass) {
    // TODO(b/148866418): make table collapsible.
    String sampleCodeHtml = "<h2>Sample Code</h2>\n" +
                            "<code>" + buildSampleCode(modelClass) + "</code>";

    contentPanel.add(createPaneFromHtml(sampleCodeHtml));
  }

  private static void addTensorsSection(@NotNull JPanel contentPanel, @NotNull ModelInfo modelInfo) {
    // TODO(b/148866418): make table collapsible.
    List<String[]> table = new ArrayList<>();
    table.add(new String[]{"Name", "Type", "Description", "Shape", "Mean / Std", "Min / Max"});
    table.add(new String[]{"inputs"});
    for (TensorInfo tensorInfo : modelInfo.getInputs()) {
      table.add(getTensorsRow(tensorInfo));
    }
    table.add(new String[]{"outputs"});
    for (TensorInfo tensorInfo : modelInfo.getOutputs()) {
      table.add(getTensorsRow(tensorInfo));
    }

    contentPanel.add(createPaneFromTable(table, "Tensors", true));
  }

  @NotNull
  private static JTextPane createPaneFromTable(@NotNull List<String[]> table, @NotNull String title, boolean useHeaderCells) {
    StringBuilder htmlBuilder = new StringBuilder("<h2>" + title + "</h2>");
    if (!table.isEmpty()) {
      htmlBuilder.append("<table>\n").append(createHtmlRow(table.get(0), useHeaderCells));
      for (String[] row : table.subList(1, table.size())) {
        htmlBuilder.append(createHtmlRow(row, false));
      }
      htmlBuilder.append("</table>\n");
    }

    return createPaneFromHtml(htmlBuilder.toString());
  }

  @NotNull
  private static String[] getTensorsRow(@NotNull TensorInfo tensorInfo) {
    MetadataExtractor.NormalizationParams params = tensorInfo.getNormalizationParams();
    String meanStdRow = params != null ? Arrays.toString(params.getMean()) + "/" + Arrays.toString(params.getStd()) : "";
    String minMaxRow = params != null ? Arrays.toString(params.getMin()) + "/" + Arrays.toString(params.getMax()) : "";

    return new String[]{tensorInfo.getName(), tensorInfo.getContentType().toString(), tensorInfo.getDescription(),
      Arrays.toString(tensorInfo.getShape()), meanStdRow, minMaxRow};
  }

  private static void setHtml(@NotNull JEditorPane pane, @NotNull String bodyContent) {
    String html = "<html><head>" + HTML_TABLE_STYLE + "</head><body>" + bodyContent + "</body></html>";
    pane.setContentType("text/html");
    pane.setEditable(false);
    pane.setText(html);
    pane.setBackground(UIUtil.getTextFieldBackground());
  }

  @NotNull
  private static String buildSampleCode(@NotNull PsiClass modelClass) {
    StringBuilder stringBuilder = new StringBuilder();
    String modelClassName = modelClass.getName();
    stringBuilder.append(String.format("%s model = %s.newInstance(context);<br>", modelClassName, modelClassName));

    PsiMethod processMethod = modelClass.findMethodsByName("process", false)[0];
    if (processMethod != null && processMethod.getReturnType() != null) {
      stringBuilder
        .append(String.format("<br>%s.%s outputs = model.%s(", modelClassName, processMethod.getReturnType().getPresentableText(),
                              processMethod.getName()));
      for (PsiParameter parameter : processMethod.getParameterList().getParameters()) {
        stringBuilder.append(parameter.getType().getPresentableText() + " " + parameter.getName() + ",");
      }
      stringBuilder.deleteCharAt(stringBuilder.length() - 1);

      stringBuilder.append(");<br><br>");
    }

    int index = 1;
    PsiClass outputsClass = getInnerClass(modelClass, MlkitNames.OUTPUTS);
    if (outputsClass != null) {
      for (PsiMethod psiMethod : outputsClass.getMethods()) {
        stringBuilder.append(
          String.format("%s %s = outputs.%s();<br>", psiMethod.getReturnType().getPresentableText(), "data" + index, psiMethod.getName()));
        index++;
      }
    }

    return stringBuilder.toString();
  }

  @Nullable
  private static PsiClass getInnerClass(@NotNull PsiClass modelClass, @NotNull String innerClassName) {
    for (PsiClass innerClass : modelClass.getInnerClasses()) {
      if (innerClassName.equals(innerClass.getName())) {
        return innerClass;
      }
    }
    return null;
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myRootPane;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  @NotNull
  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public boolean isValid() {
    return myFile.isValid();
  }

  @Override
  public void selectNotify() {
  }

  @Override
  public void deselectNotify() {
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Nullable
  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return null;
  }

  @Nullable
  @Override
  public FileEditorLocation getCurrentLocation() {
    return null;
  }

  @Override
  public void dispose() {
  }
}
