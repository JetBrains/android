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
import com.android.utils.StringHelper;
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
import com.intellij.openapi.fileTypes.FileType;
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
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.JBUI.Borders;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.swing.AbstractCellEditor;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.KotlinFileType;

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
  private final UiStyleTracker myUiStyleTracker;
  private final JBScrollPane myRootPane;

  @VisibleForTesting
  boolean myIsSampleCodeSectionVisible;

  public TfliteModelFileEditor(@NotNull Project project, @NotNull VirtualFile file) {
    myProject = project;
    myFile = file;
    myModule = ModuleUtilCore.findModuleForFile(file, project);
    myUiStyleTracker = new UiStyleTracker();
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
      }
      else {
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
    header.setBorder(Borders.empty(24, 0, 16, 0));
    sectionPanel.add(header);

    JPanel codePaneContainer = createPanelWithYAxisBoxLayout(Borders.emptyLeft(20));
    sectionPanel.add(codePaneContainer);

    JBTabbedPane tabbedCodePane = new JBTabbedPane();
    tabbedCodePane.setBackground(UIUtil.getTextFieldBackground());
    tabbedCodePane.setBorder(BorderFactory.createLineBorder(new JBColor(ColorUtil.fromHex("#C9C9C9"), ColorUtil.fromHex("#2C2F30"))));
    tabbedCodePane.setTabComponentInsets(JBUI.insets(0));
    String sampleKotlinCode = buildSampleCodeInKotlin(modelClass, modelInfo);
    tabbedCodePane.add("Kotlin", createCodeEditor(myProject, KotlinFileType.INSTANCE, sampleKotlinCode));
    String sampleJavaCode = buildSampleCodeInJava(modelClass, modelInfo);
    tabbedCodePane.add("Java", createCodeEditor(myProject, JavaFileType.INSTANCE, sampleJavaCode));
    codePaneContainer.add(tabbedCodePane);

    return sectionPanel;
  }

  @NotNull
  private static EditorTextField createCodeEditor(@NotNull Project project, @NotNull FileType fileType, @NotNull String codeBody) {
    Color bgColor = new JBColor(ColorUtil.fromHex("#F1F3F4"), ColorUtil.fromHex("#3D3F41"));
    EditorTextField codeEditor = new EditorTextField(codeBody, project, fileType);
    codeEditor.setAlignmentX(Component.LEFT_ALIGNMENT);
    codeEditor.setBackground(bgColor);
    codeEditor.setBorder(Borders.customLine(bgColor, 12));
    codeEditor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, StartupUiUtil.getLabelFont().getSize()));
    codeEditor.setOneLineMode(false);
    codeEditor.getDocument().setReadOnly(true);
    return codeEditor;
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
    JBTable table = new JBTable(tableModel);
    table.setAlignmentX(Component.LEFT_ALIGNMENT);
    table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    table.setBackground(UIUtil.getTextFieldBackground());
    table.setDefaultEditor(String.class, new MetadataCellComponentProvider());
    table.setDefaultRenderer(String.class, new MetadataCellComponentProvider());
    table.setFocusable(false);
    table.setRowSelectionAllowed(false);
    table.setShowGrid(false);
    table.setShowColumns(true);
    table.getTableHeader().setReorderingAllowed(false);
    table.getTableHeader().setResizingAllowed(false);
    table.addMouseMotionListener(new MouseAdapter() {
      @Override
      public void mouseMoved(@NotNull MouseEvent event) {
        int row = table.rowAtPoint(event.getPoint());
        int column = table.columnAtPoint(event.getPoint());
        if (row != -1 && column != -1 && table.isCellEditable(row, column)) {
          // Hack for skipping one extra click to turn the table cell into editable mode so links can be clickable immediately.
          table.editCellAt(row, column);
        }
        else {
          table.removeEditor();
        }
      }
    });

    // Sets up appropriate column width.
    TableCellRenderer headerCellRenderer = table.getTableHeader().getDefaultRenderer();
    for (int c = 0; c < table.getColumnCount(); c++) {
      TableColumn column = table.getColumnModel().getColumn(c);
      int cellWidth =
        headerCellRenderer.getTableCellRendererComponent(table, column.getHeaderValue(), false, false, 0, c).getPreferredSize().width;
      for (int r = 0; r < table.getRowCount(); r++) {
        TableCellRenderer cellRenderer = table.getCellRenderer(r, c);
        Component component = table.prepareRenderer(cellRenderer, r, c);
        cellWidth = Math.max(cellWidth, component.getPreferredSize().width);
      }
      column.setPreferredWidth(cellWidth + 10);
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
  private static String buildSampleCodeInJava(@NotNull PsiClass modelClass, @NotNull ModelInfo modelInfo) {
    StringBuilder codeBuilder = new StringBuilder();
    String modelClassName = modelClass.getName();
    codeBuilder.append("try {\n");
    codeBuilder.append(String.format("  %s model = %s.newInstance(context);\n\n", modelClassName, modelClassName));

    PsiMethod processMethod = modelClass.findMethodsByName("process", false)[0];
    if (processMethod.getReturnType() != null) {
      codeBuilder.append(buildTensorInputSampleCode(processMethod, modelInfo));
      String parameterNames = Arrays.stream(processMethod.getParameterList().getParameters())
        .map(PsiParameter::getName)
        .collect(Collectors.joining(", "));
      codeBuilder.append(String.format(
        "  %s.%s outputs = model.%s(%s);\n\n",
        modelClassName,
        processMethod.getReturnType().getPresentableText(),
        processMethod.getName(),
        parameterNames
      ));
    }

    PsiClass outputsClass = getInnerClass(modelClass, MlkitNames.OUTPUTS);
    if (outputsClass != null) {
      Iterator<String> outputTensorNameIterator = modelInfo.getOutputs().stream().map(TensorInfo::getName).iterator();
      for (PsiMethod psiMethod : outputsClass.getMethods()) {
        String tensorName = outputTensorNameIterator.next();
        codeBuilder.append(
          String.format(
            "  %s %s = outputs.%s();\n",
            Objects.requireNonNull(psiMethod.getReturnType()).getPresentableText(),
            tensorName,
            psiMethod.getName()));
        switch (psiMethod.getReturnType().getCanonicalText()) {
          case ClassNames.TENSOR_LABEL:
            codeBuilder.append(String.format("  Map<String, Float> %sMap = %s.getMapWithFloatValue();\n", tensorName, tensorName));
            break;
          case ClassNames.TENSOR_IMAGE:
            codeBuilder.append(String.format("  Bitmap %sBitmap = %s.getBitmap();\n", tensorName, tensorName));
            break;
        }
      }
    }

    codeBuilder.append("} catch (IOException e) {\n  // TODO Handle the exception\n}");

    return codeBuilder.toString();
  }

  @NotNull
  private static String buildSampleCodeInKotlin(@NotNull PsiClass modelClass, @NotNull ModelInfo modelInfo) {
    StringBuilder codeBuilder = new StringBuilder();
    codeBuilder.append(String.format("val model = %s.newInstance(context)\n\n", modelClass.getName()));

    PsiMethod processMethod = modelClass.findMethodsByName("process", false)[0];
    if (processMethod.getReturnType() != null) {
      codeBuilder.append(buildTensorInputSampleCodeInKotlin(processMethod, modelInfo));
      String parameterNames = Arrays.stream(processMethod.getParameterList().getParameters())
        .map(PsiParameter::getName)
        .collect(Collectors.joining(", "));
      codeBuilder.append(String.format("val outputs = model.%s(%s)\n\n", processMethod.getName(), parameterNames));
    }

    PsiClass outputsClass = getInnerClass(modelClass, MlkitNames.OUTPUTS);
    if (outputsClass != null) {
      Iterator<String> outputTensorNameIterator = modelInfo.getOutputs().stream().map(TensorInfo::getName).iterator();
      for (PsiMethod psiMethod : outputsClass.getMethods()) {
        String tensorName = outputTensorNameIterator.next();
        codeBuilder.append(String.format("val %s = outputs.%s\n", tensorName, convertToKotlinPropertyName(psiMethod.getName())));
        switch (psiMethod.getReturnType().getCanonicalText()) {
          case ClassNames.TENSOR_LABEL:
            codeBuilder.append(String.format("val %sMap = %s.mapWithFloatValue\n", tensorName, tensorName));
            break;
          case ClassNames.TENSOR_IMAGE:
            codeBuilder.append(String.format("val %sBitmap = %s.bitmap\n", tensorName, tensorName));
            break;
        }
      }
    }

    return codeBuilder.toString();
  }

  /**
   * Converts Java getter method name to Kotlin property name, e.g. getFoo -> foo.
   */
  @NotNull
  private static String convertToKotlinPropertyName(String getterMethodName) {
    // TODO: Is there a better way?
    return StringHelper.usLocaleDecapitalize(getterMethodName.substring(3));
  }

  @NotNull
  private static String buildTensorInputSampleCode(@NotNull PsiMethod processMethod, @NotNull ModelInfo modelInfo) {
    StringBuilder codeBuilder = new StringBuilder();
    int index = 0;
    for (PsiParameter parameter : processMethod.getParameterList().getParameters()) {
      TensorInfo tensorInfo = modelInfo.getInputs().get(index++);
      switch (parameter.getType().getCanonicalText()) {
        case ClassNames.TENSOR_IMAGE:
          codeBuilder
            .append(String.format("  TensorImage %s = new TensorImage();\n", parameter.getName()))
            .append(String.format("  %s.load(bitmap);\n", parameter.getName()));
          break;
        case ClassNames.TENSOR_BUFFER:
          codeBuilder
            .append(
              String.format(
                "  TensorBuffer %s = TensorBuffer.createFixedSize(%s, %s);\n",
                parameter.getName(),
                buildIntArrayInJava(tensorInfo.getShape()),
                buildDataType(tensorInfo.getDataType())))
            .append(String.format("  %s.loadBuffer(byteBuffer);\n", parameter.getName()));
          break;
      }
    }

    return codeBuilder.toString();
  }

  @NotNull
  private static String buildTensorInputSampleCodeInKotlin(@NotNull PsiMethod processMethod, @NotNull ModelInfo modelInfo) {
    StringBuilder codeBuilder = new StringBuilder();
    Iterator<TensorInfo> tensorInfoIterator = modelInfo.getInputs().iterator();
    for (PsiParameter parameter : processMethod.getParameterList().getParameters()) {
      TensorInfo tensorInfo = tensorInfoIterator.next();
      switch (parameter.getType().getCanonicalText()) {
        case ClassNames.TENSOR_IMAGE:
          codeBuilder
            .append(String.format("val %s = TensorImage()\n", parameter.getName()))
            .append(String.format("%s.load(bitmap)\n", parameter.getName()));
          break;
        case ClassNames.TENSOR_BUFFER:
          codeBuilder
            .append(
              String.format(
                "val %s = TensorBuffer.createFixedSize(%s, %s)\n",
                parameter.getName(),
                buildIntArrayInKotlin(tensorInfo.getShape()),
                buildDataType(tensorInfo.getDataType())))
            .append(String.format("%s.loadBuffer(byteBuffer)\n", parameter.getName()));
          break;
      }
    }

    return codeBuilder.toString();
  }

  /**
   * Returns the Java declaration of the array, e.g. new int[]{1, 2, 3}.
   */
  @NotNull
  private static String buildIntArrayInJava(@NotNull int[] array) {
    return Arrays.stream(array)
      .mapToObj(Integer::toString)
      .collect(Collectors.joining(", ", "new int[]{", "}"));
  }

  /**
   * Returns the Kotlin declaration of the array, e.g. intArrayOf(1, 2, 3).
   */
  @NotNull
  private static String buildIntArrayInKotlin(@NotNull int[] array) {
    return Arrays.stream(array)
      .mapToObj(Integer::toString)
      .collect(Collectors.joining(", ", "intArrayOf(", ")"));
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
    if (myUiStyleTracker.isUiStyleChanged() || myIsSampleCodeSectionVisible != shouldDisplaySampleCodeSection()) {
      myIsSampleCodeSectionVisible = shouldDisplaySampleCodeSection();
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
      tmp.append(word).append(" ");
      if (tmp.length() > MAX_LINE_LENGTH) {
        result.append(tmp).append("\n");
        tmp.setLength(0);
      }
    }
    result.append(tmp);
    return result.toString().trim();
  }

  private static class MetadataTableModel extends AbstractTableModel {
    private final List<List<String>> myRowDataList;
    private final List<String> myHeaderData;

    private MetadataTableModel(@NotNull List<List<String>> rowDataList, @NotNull List<String> headerData) {
      myRowDataList = ContainerUtil.map(rowDataList, row -> ContainerUtil.map(row, cellValue -> breakIntoMultipleLines(cellValue)));
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
    public String getValueAt(int rowIndex, int columnIndex) {
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

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      // HACK We're relying on cell editor components (as opposed to cell renderer components) in order to receive events so we can linkify
      // urls and make them clickable. We're not using those editors to actually edit the table model values.
      return getValueAt(rowIndex, columnIndex).startsWith("<html>");
    }

    private boolean hasHeader() {
      return !myHeaderData.isEmpty();
    }
  }

  // HACK This is a TableCellEditor so the hyperlink listener works. It doesn't actually edit any table model cell values.
  private static class MetadataCellComponentProvider extends AbstractCellEditor implements TableCellRenderer, TableCellEditor {
    @NotNull
    private final JTextPane myTextPane;

    private MetadataCellComponentProvider() {
      myTextPane = new JTextPane();
      myTextPane.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);
      myTextPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
      myTextPane.setBackground(UIUtil.getTextFieldBackground());
      myTextPane.setEditable(false);
      myTextPane.setHighlighter(null);
    }

    @NotNull
    @Override
    public Component getTableCellRendererComponent(@NotNull JTable table,
                                                   @NotNull Object value,
                                                   boolean isSelected,
                                                   boolean hasFocus,
                                                   int row,
                                                   int column) {
      configureTextPane(table, row, column);
      return myTextPane;
    }

    @NotNull
    @Override
    public Component getTableCellEditorComponent(@NotNull JTable table, @NotNull Object value, boolean isSelected, int row, int column) {
      configureTextPane(table, row, column);
      return myTextPane;
    }

    @Nullable
    @Override
    public Object getCellEditorValue() {
      return null;
    }

    private void configureTextPane(@NotNull JTable table, int row, int column) {
      // TODO(b/153093288): add html wrapping function and set content type "text/html" properly.
      myTextPane.setContentType("text/plain");
      myTextPane.setText((String)table.getValueAt(row, column));
      if (((MetadataTableModel)table.getModel()).hasHeader()) {
        myTextPane.setBorder(Borders.empty(8, 8, 8, 0));
      }
      else {
        myTextPane.setBorder(Borders.empty(4, 0));
      }
    }
  }

  private static class TableHeaderCellRenderer extends DefaultTableCellRenderer {
    @NotNull
    @Override
    public Component getTableCellRendererComponent(@NotNull JTable table,
                                                   @NotNull Object value,
                                                   boolean isSelected,
                                                   boolean hasFocus,
                                                   int row,
                                                   int column) {
      Component delegate = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      if (!(delegate instanceof JLabel)) return delegate;

      JLabel label = (JLabel)delegate;
      label.setHorizontalAlignment(SwingConstants.LEFT);
      label.setBorder(Borders.empty(4, 8));
      return label;
    }
  }

  private static class UiStyleTracker {
    private Font myLabelFont;
    private boolean myUnderDarcula;

    private UiStyleTracker() {
      myLabelFont = StartupUiUtil.getLabelFont();
      myUnderDarcula = StartupUiUtil.isUnderDarcula();
    }

    private boolean isUiStyleChanged() {
      if (myLabelFont.equals(StartupUiUtil.getLabelFont()) && myUnderDarcula == StartupUiUtil.isUnderDarcula()) {
        return false;
      }

      myLabelFont = StartupUiUtil.getLabelFont();
      myUnderDarcula = StartupUiUtil.isUnderDarcula();
      return true;
    }
  }
}
