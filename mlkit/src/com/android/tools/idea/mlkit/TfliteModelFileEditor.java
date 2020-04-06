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

import com.android.tools.idea.mlkit.lightpsi.ClassNames;
import com.android.tools.mlkit.MetadataExtractor;
import com.android.tools.mlkit.MlkitNames;
import com.android.tools.mlkit.ModelInfo;
import com.android.tools.mlkit.ModelParsingException;
import com.android.tools.mlkit.TensorInfo;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.Floats;
import com.google.wireless.android.sdk.stats.MlModelBindingEvent.EventType;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.io.FileTooBigException;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.PlatformIcons;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import java.awt.Component;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Editor for the TFLite mode file.
 */
public class TfliteModelFileEditor extends UserDataHolderBase implements FileEditor {
  private static final String NAME = "TFLite Model File";
  private static final String MODEL_TABLE_STYLE = "#model {\n" +
                                                  "  border-collapse: collapse;\n" +
                                                  "  margin-left: 12px;\n" +
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
                                                    "}\n" +
                                                    "#tensors td, #tensors th {\n" +
                                                    "  text-align: left;\n" +
                                                    "  padding: 6px;\n" +
                                                    "}\n";
  private static final int MAX_LINE_LENGTH = 100;

  private final Module myModule;
  private final VirtualFile myFile;
  private final JBScrollPane myRootPane;
  private final JEditorPane myHtmlEditorPane;
  private boolean myUnderDarcula;
  @VisibleForTesting
  boolean myIsSampleCodeSectionVisible;

  public TfliteModelFileEditor(@NotNull Project project, @NotNull VirtualFile file) {
    myFile = file;
    myModule = ModuleUtilCore.findModuleForFile(file, project);
    myUnderDarcula = StartupUiUtil.isUnderDarcula();
    myIsSampleCodeSectionVisible = shouldDisplaySampleCodeSection();

    JPanel contentPanel = new JPanel();
    contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
    contentPanel.setBackground(UIUtil.getTextFieldBackground());
    contentPanel.setBorder(JBUI.Borders.empty(20));

    myHtmlEditorPane = new JEditorPane();
    contentPanel.add(myHtmlEditorPane);
    setUpPopupMenu(myHtmlEditorPane);
    updateHtmlContent();

    myRootPane = new JBScrollPane(contentPanel);

    MessageBusConnection connection = myModule.getMessageBus().connect(myModule);
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        for (VFileEvent event : events) {
          if (myFile.equals(event.getFile())) {
            updateHtmlContent();
            return;
          }
        }
      }
    });

    LoggingUtils.logEvent(EventType.MODEL_VIEWER_OPEN, file);
  }

  private void setUpPopupMenu(@NotNull JEditorPane editorPane) {
    JBPopupMenu popupMenu = new JBPopupMenu();
    JBMenuItem menuItem = new JBMenuItem("Copy", PlatformIcons.COPY_ICON);
    menuItem.addActionListener(event -> myHtmlEditorPane.copy());
    popupMenu.add(menuItem);

    editorPane.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(@NotNull Component component, int x, int y) {
        popupMenu.show(component, x, y);
      }
    });
  }

  private void updateHtmlContent() {
    String html =
      "<html><head><style>\n" +
      MODEL_TABLE_STYLE +
      TENSORS_TABLE_STYLE +
      buildSampleCodeStyle() +
      "</style></head><body>\n" +
      createHtmlBody() +
      "</body></html>";
    myHtmlEditorPane.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);
    myHtmlEditorPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
    myHtmlEditorPane.setAlignmentX(Component.LEFT_ALIGNMENT);
    myHtmlEditorPane.setBackground(UIUtil.getTextFieldBackground());
    myHtmlEditorPane.setContentType("text/html");
    myHtmlEditorPane.setEditable(false);
    myHtmlEditorPane.setText(html);
  }

  @NotNull
  @VisibleForTesting
  String createHtmlBody() {
    StringBuilder htmlBodyBuilder = new StringBuilder();
    try {
      ModelInfo modelInfo = ModelInfo.buildFrom(new MetadataExtractor(ByteBuffer.wrap(myFile.contentsToByteArray())));
      htmlBodyBuilder.append(getModelSectionBody(modelInfo));
      htmlBodyBuilder.append(getTensorsSectionBody(modelInfo));
      if (myIsSampleCodeSectionVisible) {
        PsiClass modelClass = MlkitModuleService.getInstance(myModule)
          .getOrCreateLightModelClass(
            new MlModelMetadata(myFile.getUrl(), MlkitNames.computeModelClassName((VfsUtilCore.virtualToIoFile(myFile)))));
        htmlBodyBuilder.append(getSampleCodeSectionBody(modelClass, modelInfo));
      }
    }
    catch (FileTooBigException e) {
      htmlBodyBuilder.append(
        "Model file is larger than 20MB, please check <a href=\"https://developer.android.com/studio/write/mlmodelbinding\">our " +
        "documentation</a> for a workaround.");
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
    String modelDescription = modelInfo.getModelDescription();
    table.add(new String[]{"Description", modelDescription != null ? breakLineIfTooLong(modelDescription) : "null"});
    table.add(new String[]{"Version", modelInfo.getModelVersion()});
    table.add(new String[]{"Author", modelInfo.getModelAuthor()});
    String modelLicense = modelInfo.getModelLicense();
    table.add(new String[]{"License", modelLicense != null ? linkifyUrls(modelLicense) : "null"});

    StringBuilder bodyBuilder = new StringBuilder("<h2>Model</h2>");
    bodyBuilder.append("<table id=\"model\">\n");
    for (String[] row : table) {
      bodyBuilder.append(createHtmlRow(row, false));
    }
    bodyBuilder.append("</table>\n");

    return bodyBuilder.toString();
  }

  private static String getTensorsSectionBody(@NotNull ModelInfo modelInfo) {
    StringBuilder bodyBuilder = new StringBuilder("<h2>Tensors</h2>\n");
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
    StringBuilder bodyBuilder = new StringBuilder("<p style=\"margin-left:20px;margin-bottom:10px;\">" + title + "</p>\n");
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
    String meanStdColumn = params != null ? Arrays.toString(params.getMean()) + " / " + Arrays.toString(params.getStd()) : "";
    String minMaxColumn = isValidMinMaxColumn(params) ? Arrays.toString(params.getMin()) + " / " + Arrays.toString(params.getMax()) : "";

    String description = tensorInfo.getDescription();
    return new String[]{tensorInfo.getName(), tensorInfo.getContentType().toString(),
      description != null ? breakLineIfTooLong(description) : "null",
      Arrays.toString(tensorInfo.getShape()), meanStdColumn, minMaxColumn};
  }

  private static boolean isValidMinMaxColumn(@Nullable MetadataExtractor.NormalizationParams params) {
    if (params == null || params.getMin() == null || params.getMax() == null) {
      return false;
    }

    for (float min : params.getMin()) {
      if (Floats.compare(min, Float.MIN_VALUE) != 0) {
        return true;
      }
    }

    for (float max : params.getMax()) {
      if (Floats.compare(max, Float.MAX_VALUE) != 0) {
        return true;
      }
    }

    return false;
  }

  private static String getSampleCodeSectionBody(@NotNull PsiClass modelClass, @NotNull ModelInfo modelInfo) {
    return "<h2 style=\"padding-top:8px;\">Sample Code</h2>\n" +
           "<table id=\"sample_code\"><tr><td><pre>\n" + buildSampleCode(modelClass, modelInfo) + "</pre></td></tr></table>\n";
  }

  @NotNull
  private static String buildSampleCode(@NotNull PsiClass modelClass, @NotNull ModelInfo modelInfo) {
    StringBuilder stringBuilder = new StringBuilder();
    String modelClassName = modelClass.getName();
    stringBuilder.append("try {\n");
    stringBuilder.append(String.format("  %s model = %s.newInstance(context);\n\n", modelClassName, modelClassName));

    PsiMethod processMethod = modelClass.findMethodsByName("process", false)[0];
    if (processMethod != null && processMethod.getReturnType() != null) {
      stringBuilder.append(buildTensorInputSampleCode(processMethod, modelInfo));
      stringBuilder
        .append(String.format("  %s.%s outputs = model.%s(", modelClassName, processMethod.getReturnType().getPresentableText(),
                              processMethod.getName()));
      for (PsiParameter parameter : processMethod.getParameterList().getParameters()) {
        stringBuilder.append(parameter.getName()).append(", ");
      }
      stringBuilder.delete(stringBuilder.length() - 2, stringBuilder.length());

      stringBuilder.append(");\n\n");
    }

    int index = 0;
    PsiClass outputsClass = getInnerClass(modelClass, MlkitNames.OUTPUTS);
    if (outputsClass != null) {
      for (PsiMethod psiMethod : outputsClass.getMethods()) {
        String tensorName = modelInfo.getOutputs().get(index++).getName();
        stringBuilder.append(
          String.format("  %s %s = outputs.%s();\n", psiMethod.getReturnType().getPresentableText(), tensorName, psiMethod.getName()));
        if (ClassNames.TENSOR_LABEL.equals(psiMethod.getReturnType().getCanonicalText())) {
          stringBuilder.append(String.format("  Map&lt;String, Float&gt; %sMap = %s.getMapWithFloatValue();\n", tensorName, tensorName));
        }
        else if (ClassNames.TENSOR_IMAGE.equals(psiMethod.getReturnType().getCanonicalText())) {
          stringBuilder.append(String.format("  Bitmap %sBitmap = %s.getBitmap();\n", tensorName, tensorName));
        }
      }
    }

    stringBuilder.append("} catch (IOException e) {\n  // Handles exception here.\n}");

    return stringBuilder.toString();
  }

  @NotNull
  private static String buildTensorInputSampleCode(@NotNull PsiMethod processMethod, @NotNull ModelInfo modelInfo) {
    StringBuilder stringBuilder = new StringBuilder();
    int index = 0;
    for (PsiParameter parameter : processMethod.getParameterList().getParameters()) {
      TensorInfo tensorInfo = modelInfo.getInputs().get(index++);
      if (ClassNames.TENSOR_IMAGE.equals(parameter.getType().getCanonicalText())) {
        stringBuilder.append(String.format("  TensorImage %s = new TensorImage();\n", parameter.getName()))
          .append(String.format("  %s.load(bitmap);\n", parameter.getName()));
      }
      else if (ClassNames.TENSOR_BUFFER.equals(parameter.getType().getCanonicalText())) {
        stringBuilder
          .append(String.format("  TensorBuffer %s = TensorBuffer.createFixedSize(%s, %s);\n", parameter.getName(),
                                           buildIntArray(tensorInfo.getShape()), buildDataType(tensorInfo.getDataType())))
          .append(String.format("  %s.loadBuffer(byteBuffer);\n", parameter.getName()));
      }
    }

    return stringBuilder.toString();
  }

  /**
   * Returns string representation of int array (e.g. new int[] {1,2,3}.)
   */
  @NotNull
  private static String buildIntArray(@NotNull int[] array) {
    StringBuilder stringBuilder = new StringBuilder("new int[]{");
    for (int value : array) {
      stringBuilder.append(value).append(",");
    }
    stringBuilder.deleteCharAt(stringBuilder.length() - 1).append("}");

    return stringBuilder.toString();
  }

  @NotNull
  private static String buildDataType(@NotNull TensorInfo.DataType dataType) {
    return "DataType." + dataType.toString();
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
  private static String buildSampleCodeStyle() {
    return "#sample_code {\n" +
           "  font-family: 'Source Sans Pro', sans-serif; \n" +
           "  background-color: " + (StartupUiUtil.isUnderDarcula() ? "#2B2B2B" : "#F1F3F4") + ";\n" +
           "  color: " + (StartupUiUtil.isUnderDarcula() ? "#DDDDDD" : "#3A474E") + ";\n" +
           "  margin-left: 20px;\n" +
           "  padding: 5px;\n" +
           "  margin-top: 10px;\n" +
           "}\n";
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    if (myUnderDarcula != StartupUiUtil.isUnderDarcula() || myIsSampleCodeSectionVisible != shouldDisplaySampleCodeSection()) {
      myUnderDarcula = StartupUiUtil.isUnderDarcula();
      myIsSampleCodeSectionVisible = shouldDisplaySampleCodeSection();
      // Refresh UI
      updateHtmlContent();
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

  private boolean shouldDisplaySampleCodeSection() {
    return MlkitUtils.isMlModelBindingBuildFeatureEnabled(myModule) && MlkitUtils.isModelFileInMlModelsFolder(myModule, myFile);
  }

  @NotNull
  private static String breakLineIfTooLong(@NotNull String text) {
    String[] words = text.split(" ");
    StringBuilder result = new StringBuilder();
    StringBuilder tmp = new StringBuilder();
    for (String word : words) {
      tmp.append(word);
      if (tmp.length() > MAX_LINE_LENGTH) {
        tmp.append("<br>");
        result.append(tmp);
        tmp = new StringBuilder();
      }
      else {
        tmp.append(" ");
      }
    }
    result.append(tmp);
    return result.toString().trim();
  }

  @NotNull
  private static String linkifyUrls(@NotNull String text) {
    StringBuilder result = new StringBuilder();
    for (String word : text.split(" ")) {
      if (!word.isEmpty()) {
        try {
          int wordLen = word.length();
          boolean hasSentenceSeparator = word.charAt(wordLen - 1) == ',' || word.charAt(wordLen - 1) == '.';
          URL url = new URL(hasSentenceSeparator ? word.substring(0, word.length() - 1) : word);
          result.append(String.format("<a href=\"%s\">%s</a>%s ", url, url, hasSentenceSeparator ? word.charAt(wordLen - 1) : ""));
        }
        catch (MalformedURLException e) {
          result.append(word).append(" ");
        }
      }
    }
    return result.toString().trim();
  }
}
