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
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.primitives.Floats;
import com.google.wireless.android.sdk.stats.MlModelBindingEvent.EventType;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.icons.AllIcons;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
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
import com.intellij.ui.ColorUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI.Borders;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Editor for the TFLite mode file.
 */
public class TfliteModelFileEditor extends UserDataHolderBase implements FileEditor {
  private static final String NAME = "TFLite Model File";
  private static final ImmutableList<String> TENSOR_TABLE_HEADER =
    ImmutableList.of("Name", "Type", "Description", "Shape", "Mean / Std", "Min / Max");
  private static final int MAX_LINE_LENGTH = 100;

  private final Project myProject;
  private final VirtualFile myFile;
  @Nullable private final Module myModule;
  private final JBScrollPane myRootPane;

  private boolean myUnderDarcula;
  @VisibleForTesting
  boolean myIsSampleCodeSectionVisible;

  public TfliteModelFileEditor(@NotNull Project project, @NotNull VirtualFile file) {
    myProject = project;
    myFile = file;
    myModule = ModuleUtilCore.findModuleForFile(file, project);
    myUnderDarcula = StartupUiUtil.isUnderDarcula();
    myIsSampleCodeSectionVisible = shouldDisplaySampleCodeSection();

    myRootPane = new JBScrollPane(createContentPanel());

    project.getMessageBus().connect(project).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        for (VFileEvent event : events) {
          if (myFile.equals(event.getFile())) {
            myRootPane.setViewportView(createContentPanel());
            return;
          }
        }
      }
    });

    LoggingUtils.logEvent(EventType.MODEL_VIEWER_OPEN, file);
  }

  @NotNull
  private JComponent createContentPanel() {
    JPanel contentPanel = createPanelWithYAxisBoxLayout(Borders.empty(20));
    try {
      ModelInfo modelInfo = ModelInfo.buildFrom(new MetadataExtractor(ByteBuffer.wrap(myFile.contentsToByteArray())));
      if (modelInfo.isMetadataExisted()) {
        contentPanel.add(createModelSection(modelInfo));
        contentPanel.add(createTensorsSection(modelInfo));
      } else {
        contentPanel.add(createNoMetadataSection());
      }
      if (myModule != null && myIsSampleCodeSectionVisible) {
        PsiClass modelClass = MlkitModuleService.getInstance(myModule)
          .getOrCreateLightModelClass(
            new MlModelMetadata(myFile.getUrl(), MlkitNames.computeModelClassName((VfsUtilCore.virtualToIoFile(myFile)))));
        if (modelClass != null) {
          contentPanel.add(createSampleCodeSection(modelClass, modelInfo));
        }
      }
    }
    catch (FileTooBigException e) {
      contentPanel.add(createFileTooBigPane());
    }
    catch (IOException e) {
      Logger.getInstance(TfliteModelFileEditor.class).error(e);
    }
    catch (ModelParsingException e) {
      Logger.getInstance(TfliteModelFileEditor.class).warn(e);
      // TODO(deanzhou): show warning message in panel
    }

    return contentPanel;
  }

  @NotNull
  private static JComponent createSectionHeader(@NotNull String title) {
    JBLabel titleLabel = new JBLabel(title);
    titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
    titleLabel.setBackground(UIUtil.getTextFieldBackground());
    titleLabel.setBorder(Borders.empty(10, 0));
    Font font = titleLabel.getFont();
    titleLabel.setFont(font.deriveFont(font.getStyle() | Font.BOLD).deriveFont(font.getSize() * 1.2f));
    return titleLabel;
  }

  @NotNull
  private static JComponent createNoMetadataSection() {
    JPanel sectionPanel = createPanelWithYAxisBoxLayout(Borders.empty());
    sectionPanel.add(createSectionHeader("Model"));

    JPanel sectionContentPanel = createPanelWithYAxisBoxLayout(Borders.empty(50, 100, 50, 0));
    sectionPanel.add(sectionContentPanel);

    JBLabel infoLabel = new JBLabel("No metadata found in this model");
    infoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
    infoLabel.setBorder(Borders.emptyBottom(4));
    sectionContentPanel.add(infoLabel);

    HyperlinkLabel addMetadataLinkLabel = new HyperlinkLabel("Add metadata to your model");
    addMetadataLinkLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
    addMetadataLinkLabel.setHyperlinkTarget("https://www.tensorflow.org/lite/convert/metadata");
    addMetadataLinkLabel.setIcon(AllIcons.General.ContextHelp);
    addMetadataLinkLabel.setMaximumSize(addMetadataLinkLabel.getPreferredSize());
    sectionContentPanel.add(addMetadataLinkLabel);

    return sectionPanel;
  }

  @NotNull
  private static JComponent createModelSection(@NotNull ModelInfo modelInfo) {
    JPanel sectionPanel = createPanelWithYAxisBoxLayout(Borders.empty());
    sectionPanel.add(createSectionHeader("Model"));

    JBTable modelTable = createTable(getModelTableData(modelInfo), Collections.emptyList());
    JPanel modelTablePanel = createPanelWithYAxisBoxLayout(Borders.emptyLeft(20));
    modelTablePanel.add(modelTable);
    sectionPanel.add(modelTablePanel);

    return sectionPanel;
  }

  @NotNull
  private static JComponent createTensorsSection(@NotNull ModelInfo modelInfo) {
    JPanel sectionPanel = createPanelWithYAxisBoxLayout(Borders.empty());
    sectionPanel.add(createSectionHeader("Tensors"));

    JPanel sectionContentPanel = createPanelWithYAxisBoxLayout(Borders.emptyLeft(20));

    JBLabel inputsLabel = new JBLabel("Inputs");
    inputsLabel.setBorder(Borders.empty(6, 0));
    sectionContentPanel.add(inputsLabel);

    JBTable inputTensorTable = createTable(getTensorTableData(modelInfo.getInputs()), TENSOR_TABLE_HEADER);
    addTableHeader(sectionContentPanel, inputTensorTable);
    inputTensorTable.setBorder(BorderFactory.createLineBorder(JBColor.LIGHT_GRAY));
    sectionContentPanel.add(inputTensorTable);

    JBLabel outputsLabel = new JBLabel("Outputs");
    outputsLabel.setBorder(Borders.empty(10, 0, 6, 0));
    sectionContentPanel.add(outputsLabel);

    JBTable outputTensorTable = createTable(getTensorTableData(modelInfo.getOutputs()), TENSOR_TABLE_HEADER);
    addTableHeader(sectionContentPanel, outputTensorTable);
    outputTensorTable.setBorder(BorderFactory.createLineBorder(JBColor.LIGHT_GRAY));
    sectionContentPanel.add(outputTensorTable);

    sectionPanel.add(sectionContentPanel);

    return sectionPanel;
  }

  private static void addTableHeader(@NotNull JComponent container, @NotNull JBTable table) {
    JTableHeader tableHeader = table.getTableHeader();
    tableHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
    tableHeader.setBorder(BorderFactory.createMatteBorder(1, 1, 0, 1, JBColor.LIGHT_GRAY));
    tableHeader.setDefaultRenderer(new TableHeaderCellRenderer());
    container.add(tableHeader);
  }

  @NotNull
  private JComponent createSampleCodeSection(@NotNull PsiClass modelClass, @NotNull ModelInfo modelInfo) {
    JPanel sectionPanel = createPanelWithYAxisBoxLayout(Borders.empty());

    JComponent header = createSectionHeader("Sample Code");
    header.setBorder(Borders.empty(24, 0, 14, 0));
    sectionPanel.add(header);

    JPanel codeEditorContainer = createPanelWithYAxisBoxLayout(Borders.emptyLeft(20));
    sectionPanel.add(codeEditorContainer);

    // TODO(b/153084173): implement Kotlin/Java code snippet switcher.
    Color bgColor = new JBColor(ColorUtil.fromHex("#F1F3F4"), ColorUtil.fromHex("#2B2B2B"));
    EditorTextField codeEditor = new EditorTextField(buildSampleCode(modelClass, modelInfo), myProject, JavaFileType.INSTANCE);
    codeEditor.setAlignmentX(Component.LEFT_ALIGNMENT);
    codeEditor.setBackground(bgColor);
    codeEditor.setBorder(Borders.customLine(bgColor, 12));
    codeEditor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, StartupUiUtil.getLabelFont().getSize()));
    codeEditor.setOneLineMode(false);
    codeEditor.getDocument().setReadOnly(true);
    codeEditorContainer.add(codeEditor);

    return sectionPanel;
  }

  @NotNull
  private static JEditorPane createFileTooBigPane() {
    JEditorPane editorPane = new JEditorPane();
    editorPane.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);
    editorPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
    editorPane.setAlignmentX(Component.LEFT_ALIGNMENT);
    editorPane.setBackground(UIUtil.getTextFieldBackground());
    editorPane.setContentType("text/html");
    editorPane.setEditable(false);
    editorPane.setText(
      "<html>Model file is larger than 20 MB, please check " +
      "<a href=\"https://developer.android.com/studio/write/mlmodelbinding\">our documentation</a> " +
      "for a workaround.</html>");
    return editorPane;
  }

  @NotNull
  private static JBTable createTable(@NotNull List<List<String>> rowDataList, @NotNull List<String> headerData) {
    MetadataTableModel tableModel = new MetadataTableModel(rowDataList, headerData);
    JBTable table = new JBTable(new MetadataTableModel(rowDataList, headerData));
    table.setAlignmentX(Component.LEFT_ALIGNMENT);
    table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    table.setBackground(UIUtil.getTextFieldBackground());
    table.setDefaultRenderer(String.class, new MetadataCellRenderer());
    table.setFocusable(false);
    table.setRowSelectionAllowed(false);
    table.setShowGrid(false);
    table.setShowColumns(true);
    table.getTableHeader().setReorderingAllowed(false);
    table.getTableHeader().setResizingAllowed(false);

    // Sets up a appropriate width for each column.
    TableCellRenderer headerCellRenderer = table.getTableHeader().getDefaultRenderer();
    for (int c = 0; c < table.getColumnCount(); c++) {
      TableColumn column = table.getColumnModel().getColumn(c);
      int headerCellWidth =
        headerCellRenderer.getTableCellRendererComponent(table, column.getHeaderValue(), false, false, 0, c).getPreferredSize().width;
      int cellWidth = table.getDefaultRenderer(tableModel.getColumnClass(c))
        .getTableCellRendererComponent(table, tableModel.getLongestCellValue(c), false, false, 0, c).getPreferredSize().width;
      column.setPreferredWidth(Math.max(headerCellWidth, cellWidth) + 10);
    }

    return table;
  }

  @NotNull
  private static List<List<String>> getModelTableData(@NotNull ModelInfo modelInfo) {
    List<List<String>> tableData = new ArrayList<>();
    tableData.add(Lists.newArrayList("Name", Strings.nullToEmpty(modelInfo.getModelName())));
    tableData.add(Lists.newArrayList("Description", Strings.nullToEmpty(modelInfo.getModelDescription())));
    tableData.add(Lists.newArrayList("Version", Strings.nullToEmpty(modelInfo.getModelVersion())));
    tableData.add(Lists.newArrayList("Author", Strings.nullToEmpty(modelInfo.getModelAuthor())));
    // TODO(b/153093288): Linkify urls in the license text correctly.
    tableData.add(Lists.newArrayList("License", Strings.nullToEmpty(modelInfo.getModelLicense())));
    return tableData;
  }

  @NotNull
  private static List<List<String>> getTensorTableData(List<TensorInfo> tensorInfoList) {
    List<List<String>> tableData = new ArrayList<>();
    for (TensorInfo tensorInfo : tensorInfoList) {
      MetadataExtractor.NormalizationParams params = tensorInfo.getNormalizationParams();
      String meanStdColumn = params != null ? Arrays.toString(params.getMean()) + " / " + Arrays.toString(params.getStd()) : "";
      String minMaxColumn = isValidMinMaxColumn(params) ? Arrays.toString(params.getMin()) + " / " + Arrays.toString(params.getMax()) : "";
      tableData.add(
        Lists.newArrayList(
          Strings.nullToEmpty(tensorInfo.getName()),
          tensorInfo.getContentType().toString(),
          Strings.nullToEmpty(tensorInfo.getDescription()),
          Arrays.toString(tensorInfo.getShape()),
          meanStdColumn,
          minMaxColumn
        ));
    }
    return tableData;
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
          stringBuilder.append(String.format("  Map<String, Float> %sMap = %s.getMapWithFloatValue();\n", tensorName, tensorName));
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
  @Override
  public JComponent getComponent() {
    if (myUnderDarcula != StartupUiUtil.isUnderDarcula() || myIsSampleCodeSectionVisible != shouldDisplaySampleCodeSection()) {
      myUnderDarcula = StartupUiUtil.isUnderDarcula();
      myIsSampleCodeSectionVisible = shouldDisplaySampleCodeSection();
      // Refresh UI
      myRootPane.setViewportView(createContentPanel());
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
    return myModule != null &&
           MlkitUtils.isMlModelBindingBuildFeatureEnabled(myModule) &&
           MlkitUtils.isModelFileInMlModelsFolder(myModule, myFile);
  }

  @NotNull
  private static JPanel createPanelWithYAxisBoxLayout(@NotNull Border border) {
    JPanel sectionPanel = new JPanel();
    sectionPanel.setLayout(new BoxLayout(sectionPanel, BoxLayout.Y_AXIS));
    sectionPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
    sectionPanel.setBackground(UIUtil.getTextFieldBackground());
    sectionPanel.setBorder(border);
    return sectionPanel;
  }

  @NotNull
  private static String breakIntoMultipleLines(@NotNull String text) {
    String[] words = text.split(" ");
    StringBuilder result = new StringBuilder();
    StringBuilder tmp = new StringBuilder();
    for (String word : words) {
      tmp.append(word);
      if (tmp.length() > MAX_LINE_LENGTH) {
        tmp.append("\n");
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

  private static class MetadataTableModel extends AbstractTableModel {
    private final List<List<String>> myRowDataList;
    private final List<String> myHeaderData;

    private MetadataTableModel(@NotNull List<List<String>> rowDataList, @NotNull List<String> headerData) {
      myRowDataList = rowDataList;
      myHeaderData = headerData;
    }

    @Override
    public int getRowCount() {
      return myRowDataList.size();
    }

    @Override
    public int getColumnCount() {
      return myRowDataList.get(0).size();
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      return myRowDataList.get(rowIndex).get(columnIndex);
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return String.class;
    }

    @Override
    public String getColumnName(int column) {
      return column < myHeaderData.size() ? myHeaderData.get(column) : super.getColumnName(column);
    }

    @NotNull
    private String getLongestCellValue(int columnIndex) {
      Optional<String> optionalValue = myRowDataList.stream()
        .map(row -> row.get(columnIndex))
        .max(Comparator.comparing(String::length));
      return optionalValue.orElse("");
    }

    private boolean hasHeader() {
      return !myHeaderData.isEmpty();
    }
  }

  private static class MetadataCellRenderer implements TableCellRenderer {
    private final JTextArea myTextArea;

    private MetadataCellRenderer() {
      myTextArea = new JTextArea();
      myTextArea.setEditable(false);
      myTextArea.setOpaque(false);
      myTextArea.setFont(StartupUiUtil.getLabelFont());
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      String cellValue = (String)value;
      if (cellValue.length() > MAX_LINE_LENGTH) {
        cellValue = breakIntoMultipleLines(cellValue);
      }
      myTextArea.setText(cellValue);

      if (((MetadataTableModel)table.getModel()).hasHeader()) {
        myTextArea.setBorder(Borders.empty(8, 8, 8, 0));
      }
      else {
        myTextArea.setBorder(Borders.empty(4, 0));
      }

      return myTextArea;
    }
  }

  private static class TableHeaderCellRenderer extends DefaultTableCellRenderer {

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      Component delegate = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      if (!(delegate instanceof JLabel)) return delegate;

      JLabel label = (JLabel)delegate;
      label.setHorizontalAlignment(SwingConstants.LEFT);
      label.setBorder(Borders.empty(4, 8));
      return label;
    }
  }
}
