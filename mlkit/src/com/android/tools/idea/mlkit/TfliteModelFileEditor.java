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
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
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
  private static final String MODEL_TABLE_STYLE = "#model {\n" +
                                                  "  border-collapse: collapse;\n" +
                                                  "  margin-left: 12px;\n" +
                                                  "  width: 60%;\n" +
                                                  "}\n" +
                                                  "#model td, #model th {\n" +
                                                  "  border: 0;\n" +
                                                  "  text-align: left;\n" +
                                                  "  padding: 8px;\n" +
                                                  "}\n";
  private static final String TENSORS_TABLE_STYLE = "#tensors {\n" +
                                                    "  border-collapse: collapse;\n" +
                                                    "  border: 1px solid #dddddd;\n" +
                                                    "  margin-left: 20px;\n" +
                                                    "  width: 60%;\n" +
                                                    "}\n" +
                                                    "#tensors td, #tensors th {\n" +
                                                    "  text-align: left;\n" +
                                                    "  padding: 6px;\n" +
                                                    "}\n";

  private final Module myModule;
  private final VirtualFile myFile;
  private final JBScrollPane myRootPane;
  private final JEditorPane myEditorPane;
  private boolean myUnderDarcula;
  private boolean shouldDisplaySampleCodeSection;

  public TfliteModelFileEditor(@NotNull Project project, @NotNull VirtualFile file) {
    myFile = file;
    myModule = ModuleUtilCore.findModuleForFile(file, project);
    myUnderDarcula = StartupUiUtil.isUnderDarcula();
    shouldDisplaySampleCodeSection = shouldDisplaySampleCodeSection(file);

    JPanel contentPanel = new JPanel();
    contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
    contentPanel.setBackground(UIUtil.getTextFieldBackground());
    contentPanel.setBorder(JBUI.Borders.empty(20));

    myEditorPane = createPaneFromHtml(createHtmlBody());
    contentPanel.add(myEditorPane);

    myRootPane = new JBScrollPane(contentPanel);
  }

  @NotNull
  private String createHtmlBody() {
    StringBuilder htmlBodyBuilder = new StringBuilder();
    try {
      ModelInfo modelInfo = ModelInfo.buildFrom(new MetadataExtractor(ByteBuffer.wrap(myFile.contentsToByteArray())));
      htmlBodyBuilder.append(getModelSectionBody(modelInfo));
      htmlBodyBuilder.append(getTensorsSectionBody(modelInfo));
      if (shouldDisplaySampleCodeSection) {
        PsiClass modelClass = MlkitModuleService.getInstance(myModule)
          .getOrCreateLightModelClass(
            new MlModelMetadata(myFile.getUrl(), MlkitNames.computeModelClassName((VfsUtilCore.virtualToIoFile(myFile)))));
        htmlBodyBuilder.append(getSampleCodeSectionBody(modelClass, modelInfo));
      }
    }
    catch (IOException e) {
      Logger.getInstance(TfliteModelFileEditor.class).error(e);
    }
    catch (ModelParsingException e) {
      Logger.getInstance(TfliteModelFileEditor.class).warn(e);
      // TODO(deanzhou): show warning message in panel
    }

    return htmlBodyBuilder.toString();
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
      htmlRow.append(useHeaderCells ? "<th valign=\"top\">" + value + "</th>\n" : "<td valign=\"top\">" + value + "</td>\n");
    }
    htmlRow.append("</tr>\n");

    return htmlRow.toString();
  }

  private static String getModelSectionBody(@NotNull ModelInfo modelInfo) {
    List<String[]> table = new ArrayList<>();
    table.add(new String[]{"Name", modelInfo.getModelName()});
    table.add(new String[]{"Description", modelInfo.getModelDescription()});
    table.add(new String[]{"Version", modelInfo.getModelVersion()});
    table.add(new String[]{"Author", modelInfo.getModelAuthor()});
    table.add(new String[]{"License", modelInfo.getModelLicense()});

    StringBuilder bodyBuilder = new StringBuilder("<h2>Model</h2>");
    bodyBuilder.append("<table id=\"model\">\n");
    for (String[] row : table) {
      bodyBuilder.append(createHtmlRow(row, false));
    }
    bodyBuilder.append("</table>\n");

    return bodyBuilder.toString();
  }

  private static String getSampleCodeSectionBody(@NotNull PsiClass modelClass, @NotNull ModelInfo modelInfo) {
    return "<h2>Sample Code</h2>\n" +
           "<div id=\"sample_code\"><pre>" + buildSampleCode(modelClass, modelInfo) + "</pre></div>";
  }

  private static String getTensorsSectionBody(@NotNull ModelInfo modelInfo) {
    StringBuilder bodyBuilder = new StringBuilder("<h2>Tensors</h2>");
    List<String[]> inputsTable = new ArrayList<>();
    inputsTable.add(new String[]{"Name", "Type", "Description", "Shape", "Mean / Std", "Min / Max"});
    for (TensorInfo tensorInfo : modelInfo.getInputs()) {
      inputsTable.add(getTensorsRow(tensorInfo));
    }
    bodyBuilder.append(getTensorTableBody(inputsTable, "Inputs"));

    List<String[]> outputsTable = new ArrayList<>();
    outputsTable.add(new String[]{"Name", "Type", "Description", "Shape", "Mean / Std", "Min / Max"});
    for (TensorInfo tensorInfo : modelInfo.getOutputs()) {
      outputsTable.add(getTensorsRow(tensorInfo));
    }
    bodyBuilder.append(getTensorTableBody(outputsTable, "Outputs"));

    return bodyBuilder.toString();
  }

  @NotNull
  private static String getTensorTableBody(@NotNull List<String[]> table, @NotNull String title) {
    StringBuilder bodyBuilder = new StringBuilder("<p style=\"margin-left:20px;margin-bottom:10px;\">" + title + "</p>");
    bodyBuilder.append("<table id=\"tensors\">\n").append(createHtmlRow(table.get(0), false));
    for (String[] row : table.subList(1, table.size())) {
      bodyBuilder.append(createHtmlRow(row, false));
    }
    bodyBuilder.append("</table>\n");

    return bodyBuilder.toString();
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
    String html =
      "<html><head><style>" +
      MODEL_TABLE_STYLE +
      TENSORS_TABLE_STYLE +
      buildSampleCodeStyle() +
      "</style></head><body>" +
      bodyContent +
      "</body></html>";
    pane.setContentType("text/html");
    pane.setEditable(false);
    pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
    pane.setText(html);
    pane.setBackground(UIUtil.getTextFieldBackground());
  }

  @NotNull
  private static String buildSampleCodeStyle() {
    return "#sample_code {\n" +
           "  font-family: 'Source Sans Pro', sans-serif; \n" +
           "  background-color: " + (StartupUiUtil.isUnderDarcula() ? "#2A3141" : "#F1F3F4") + ";\n" +
           "  color: " + (StartupUiUtil.isUnderDarcula() ? "#EDEFF1" : "#3A474E") + ";\n" +
           "  margin-left: 20px;\n" +
           "  display: block;\n" +
           "  width: 60%;\n" +
           "  padding: 5px;\n" +
           "  padding-left: 10px;\n" +
           "  margin-top: 10px;\n" +
           "}";
  }

  @NotNull
  private static String buildSampleCode(@NotNull PsiClass modelClass, @NotNull ModelInfo modelInfo) {
    StringBuilder stringBuilder = new StringBuilder();
    String modelClassName = modelClass.getName();
    stringBuilder.append(String.format("%s model = %s.newInstance(context);\n\n", modelClassName, modelClassName));

    PsiMethod processMethod = modelClass.findMethodsByName("process", false)[0];
    if (processMethod != null && processMethod.getReturnType() != null) {
      stringBuilder
        .append(String.format("%s.%s outputs = model.%s(", modelClassName, processMethod.getReturnType().getPresentableText(),
                              processMethod.getName()));
      for (PsiParameter parameter : processMethod.getParameterList().getParameters()) {
        stringBuilder.append(parameter.getType().getPresentableText() + " " + parameter.getName() + ",");
      }
      stringBuilder.deleteCharAt(stringBuilder.length() - 1);

      stringBuilder.append(");\n\n");
    }

    int index = 0;
    PsiClass outputsClass = getInnerClass(modelClass, MlkitNames.OUTPUTS);
    if (outputsClass != null) {
      for (PsiMethod psiMethod : outputsClass.getMethods()) {
        stringBuilder.append(
          String.format("%s %s = outputs.%s();\n", psiMethod.getReturnType().getPresentableText(),
                        modelInfo.getOutputs().get(index++).getName(), psiMethod.getName()));
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
    if (myUnderDarcula != StartupUiUtil.isUnderDarcula() || shouldDisplaySampleCodeSection != shouldDisplaySampleCodeSection(myFile)) {
      myUnderDarcula = StartupUiUtil.isUnderDarcula();
      shouldDisplaySampleCodeSection = shouldDisplaySampleCodeSection(myFile);
      // Refresh UI
      setHtml(myEditorPane, createHtmlBody());
    }
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

  private static boolean shouldDisplaySampleCodeSection(@NotNull VirtualFile modelFile) {
    // TODO(b/150960988): take build feature state into account as well once ag/10508121 landed.
    return MlkitUtils.isModelFileInMlModelsFolder(modelFile);
  }
}
