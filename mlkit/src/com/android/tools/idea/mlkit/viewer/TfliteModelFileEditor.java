/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.mlkit.viewer;

import com.android.tools.idea.mlkit.LoggingUtils;
import com.android.tools.idea.mlkit.MlModuleService;
import com.android.tools.idea.mlkit.lightpsi.ClassNames;
import com.android.tools.idea.mlkit.lightpsi.LightModelClass;
import com.android.tools.idea.mlkit.lightpsi.LightModelOutputsClass;
import com.android.tools.mlkit.MlConstants;
import com.android.tools.mlkit.MlNames;
import com.android.tools.mlkit.ModelInfo;
import com.android.tools.mlkit.TensorGroupInfo;
import com.android.tools.mlkit.TensorInfo;
import com.android.tools.mlkit.TfliteModelException;
import com.android.utils.StringHelper;
import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.primitives.Floats;
import com.google.wireless.android.sdk.stats.MlModelBindingEvent.EventType;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.icons.AllIcons;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.table.JBTable;
import com.intellij.util.LineSeparator;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.URLUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.JBUI.Borders;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FocusTraversalPolicy;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
import javax.swing.table.TableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.KotlinFileType;

/**
 * Editor for the TFLite mode file.
 */
public class TfliteModelFileEditor extends UserDataHolderBase implements FileEditor {
  private static final String NAME = "TFLite Model File";
  private static final ImmutableList<String> TENSOR_TABLE_HEADER =
    ImmutableList.of("Name", "Type", "Description", "Shape", "Min / Max");
  // Do not use this separator in sample code block as it would cause document creation failure on Windows, see b/156460170.
  private static final String LINE_SEPARATOR = LineSeparator.getSystemLineSeparator().getSeparatorString();
  private static final String INDENT = "    ";
  private static final Color CODE_PANE_BORDER_COLOR = new JBColor(0xC9C9C9, 0x2C2F30);
  private static final Color CODE_EDITOR_BG_COLOR = JBColor.namedColor("MlModelBinding.Viewer.CodeEditor.background", 0xF1F3F4, 0x3D3F41);

  private final Project myProject;
  private final VirtualFile myFile;
  @Nullable private final Module myModule;
  private final UiStyleTracker myUiStyleTracker;
  private final JBScrollPane myRootPane;

  @Nullable private JBTabbedPane myTabbedCodePaneForFocus;
  @Nullable private LightModelClass myLightModelClass;

  public TfliteModelFileEditor(@NotNull Project project, @NotNull VirtualFile file) {
    myProject = project;
    myFile = file;
    myModule = ModuleUtilCore.findModuleForFile(file, project);
    myUiStyleTracker = new UiStyleTracker();
    myLightModelClass = getLatestLightModelClass();
    myRootPane = new JBScrollPane(createContentPanel());
    myRootPane.setFocusCycleRoot(true);
    myRootPane.setFocusTraversalPolicy(new EditorFocusTraversalPolicy());

    if (myLightModelClass != null) {
      LoggingUtils.logEvent(EventType.MODEL_VIEWER_OPEN, myLightModelClass.getModelInfo());
    }
    else {
      LoggingUtils.logEvent(EventType.MODEL_VIEWER_OPEN, file);
    }
  }

  @NotNull
  private JComponent createContentPanel() {
    if (myFile.getLength() > MlConstants.MAX_SUPPORTED_MODEL_FILE_SIZE_IN_BYTES) {
      return createWarningMessagePanel("This file is over the maximum supported size 200 MB.");
    }

    try {
      JPanel contentPanel = createPanelWithYAxisBoxLayout(Borders.empty(20));
      ModelInfo modelInfo;
      if (myLightModelClass != null) {
        modelInfo = myLightModelClass.getModelInfo();
      }
      else {
        // Falls back to build model info from model file.
        modelInfo = ModelInfo.buildFrom(ByteBuffer.wrap(Files.readAllBytes(VfsUtilCore.virtualToIoFile(myFile).toPath())));
      }

      if (!modelInfo.isMinParserVersionSatisfied()) {
        contentPanel.add(createMetadataVersionTooHighSection());
      }
      else if (modelInfo.isMetadataExisted()) {
        contentPanel.add(createModelSection(modelInfo));
        contentPanel.add(createTensorsSection(modelInfo));
      }
      else {
        contentPanel.add(createNoMetadataSection());
      }

      if (myLightModelClass != null) {
        contentPanel.add(createSampleCodeSection(myLightModelClass, modelInfo));
      }

      return contentPanel;
    }
    catch (TfliteModelException e) {
      return createWarningMessagePanel(e.getMessage());
    }
    catch (IOException e) {
      Logger.getInstance(TfliteModelFileEditor.class).error(e);
      return createWarningMessagePanel("Error reading model file.");
    }
  }

  @Nullable
  private LightModelClass getLatestLightModelClass() {
    if (myModule != null) {
      return MlModuleService.getInstance(myModule).getLightModelClassList().stream()
        .filter(lightModelClass -> lightModelClass.getModelFile().getUrl().equals(myFile.getUrl()))
        .findFirst()
        .orElse(null);
    }
    return null;
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
  private static JComponent createMetadataVersionTooHighSection() {
    JPanel sectionPanel = createPanelWithYAxisBoxLayout(Borders.empty());
    sectionPanel.add(createSectionHeader("Model"));

    JBLabel infoLabel = new JBLabel(
      "Model is not fully supported in current " +
      ApplicationNamesInfo.getInstance().getFullProductName() +
      " or Android Gradle Plugin. " +
      "Please update to the latest version to get the best experience.");
    infoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
    infoLabel.setBorder(Borders.empty(10, 20, 10, 0));
    sectionPanel.add(infoLabel);

    return sectionPanel;
  }

  @NotNull
  private static JComponent createModelSection(@NotNull ModelInfo modelInfo) {
    JPanel sectionPanel = createPanelWithYAxisBoxLayout(Borders.empty());
    sectionPanel.add(createSectionHeader("Model"));

    JPanel modelTablePanel = createPanelWithFlowLayout(Borders.emptyLeft(20));
    addTable(modelTablePanel, getModelTableData(modelInfo), Collections.emptyList());
    sectionPanel.add(modelTablePanel);
    sectionPanel.setMaximumSize(sectionPanel.getPreferredSize());

    return sectionPanel;
  }

  @NotNull
  private static JComponent createTensorsSection(@NotNull ModelInfo modelInfo) {
    JPanel sectionContentPanel = createPanelWithYAxisBoxLayout(Borders.emptyLeft(20));

    JBLabel inputsLabel = new JBLabel("Inputs");
    inputsLabel.setBorder(Borders.empty(6, 0));
    sectionContentPanel.add(inputsLabel);

    JBTable inputTensorTable = addTable(sectionContentPanel, getTensorTableData(modelInfo.getInputs()), TENSOR_TABLE_HEADER);
    inputTensorTable.setBorder(BorderFactory.createLineBorder(JBColor.LIGHT_GRAY));

    JBLabel outputsLabel = new JBLabel("Outputs");
    outputsLabel.setBorder(Borders.empty(10, 0, 6, 0));
    sectionContentPanel.add(outputsLabel);

    JBTable outputTensorTable = addTable(sectionContentPanel, getTensorTableData(modelInfo.getOutputs()), TENSOR_TABLE_HEADER);
    outputTensorTable.setBorder(BorderFactory.createLineBorder(JBColor.LIGHT_GRAY));

    // Align column width between tensor tables.
    for (int c = 0; c < TENSOR_TABLE_HEADER.size(); c++) {
      TableColumn inputTensorTableColumn = inputTensorTable.getColumnModel().getColumn(c);
      TableColumn outputTensorTableColumn = outputTensorTable.getColumnModel().getColumn(c);
      int newColumnWidth = Math.max(inputTensorTableColumn.getPreferredWidth(), outputTensorTableColumn.getPreferredWidth());
      inputTensorTableColumn.setPreferredWidth(newColumnWidth);
      outputTensorTableColumn.setPreferredWidth(newColumnWidth);
    }

    JPanel sectionContentPanelContainer = createPanelWithFlowLayout(Borders.empty());
    sectionContentPanelContainer.add(sectionContentPanel);
    sectionContentPanelContainer.setMaximumSize(sectionContentPanelContainer.getPreferredSize());

    JPanel sectionPanel = createPanelWithYAxisBoxLayout(Borders.empty());
    sectionPanel.add(createSectionHeader("Tensors"));
    sectionPanel.add(sectionContentPanelContainer);

    return sectionPanel;
  }

  @NotNull
  private JComponent createSampleCodeSection(@NotNull PsiClass modelClass, @NotNull ModelInfo modelInfo) {
    JPanel sectionPanel = createPanelWithYAxisBoxLayout(Borders.empty());

    JComponent header = createSectionHeader("Sample Code");
    sectionPanel.add(header);

    JPanel codePaneContainer = createPanelWithFlowLayout(Borders.empty(8, 20, 0, 0));
    sectionPanel.add(codePaneContainer);

    JBTabbedPane tabbedCodePane = new JBTabbedPane();
    tabbedCodePane.setBackground(UIUtil.getTextFieldBackground());
    tabbedCodePane.setBorder(BorderFactory.createLineBorder(CODE_PANE_BORDER_COLOR));
    tabbedCodePane.setTabComponentInsets(JBUI.insets(0));
    String sampleKotlinCode = buildSampleCodeInKotlin(modelClass, modelInfo);
    tabbedCodePane.add("Kotlin", createCodeEditor(myProject, KotlinFileType.INSTANCE, sampleKotlinCode));
    String sampleJavaCode = buildSampleCodeInJava(modelClass, modelInfo);
    tabbedCodePane.add("Java", createCodeEditor(myProject, JavaFileType.INSTANCE, sampleJavaCode));
    codePaneContainer.add(tabbedCodePane);
    myTabbedCodePaneForFocus = tabbedCodePane;

    return sectionPanel;
  }

  @NotNull
  private static EditorTextField createCodeEditor(@NotNull Project project, @NotNull FileType fileType, @NotNull String codeBody) {
    EditorTextField codeEditor = new EditorTextField(codeBody, project, fileType);
    codeEditor.setAlignmentX(Component.LEFT_ALIGNMENT);
    codeEditor.setBackground(CODE_EDITOR_BG_COLOR);
    codeEditor.setBorder(Borders.customLine(CODE_EDITOR_BG_COLOR, 12));
    codeEditor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, StartupUiUtil.getLabelFont().getSize()));
    codeEditor.setOneLineMode(false);
    codeEditor.getDocument().setReadOnly(true);
    return codeEditor;
  }

  @NotNull
  private static JComponent createWarningMessagePanel(@NotNull String message) {
    JLabel messageLabel = new JLabel(message);
    messageLabel.setBackground(UIUtil.getTextFieldBackground());
    messageLabel.setHorizontalAlignment(SwingConstants.CENTER);
    messageLabel.setVerticalAlignment(SwingConstants.CENTER);
    return messageLabel;
  }

  /**
   * Returns the table just added.
   */
  @NotNull
  private static JBTable addTable(@NotNull JPanel tableContainer,
                                  @NotNull List<List<String>> rowDataList,
                                  @NotNull List<String> headerData) {
    MetadataTableModel tableModel = new MetadataTableModel(rowDataList, headerData);
    JBTable table = new JBTable(tableModel);
    table.setAlignmentX(Component.LEFT_ALIGNMENT);
    table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    table.setBackground(UIUtil.getTextFieldBackground());
    table.setDefaultEditor(String.class, new MetadataCellComponentProvider());
    table.setDefaultRenderer(String.class, new MetadataCellComponentProvider());
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
    if (!headerData.isEmpty()) {
      JTableHeader tableHeader = table.getTableHeader();
      tableHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
      tableHeader.setBorder(BorderFactory.createMatteBorder(1, 1, 0, 1, JBColor.LIGHT_GRAY));
      tableHeader.setDefaultRenderer(new TableHeaderCellRenderer());
      tableContainer.add(tableHeader);
    }

    // Sets up column width and row height to fit into content.
    TableCellRenderer headerCellRenderer = table.getTableHeader().getDefaultRenderer();
    int[] rowHeights = new int[table.getRowCount()];
    for (int c = 0; c < table.getColumnCount(); c++) {
      TableColumn column = table.getColumnModel().getColumn(c);
      int cellWidth =
        headerCellRenderer.getTableCellRendererComponent(table, column.getHeaderValue(), false, false, 0, c).getPreferredSize().width;
      for (int r = 0; r < table.getRowCount(); r++) {
        TableCellRenderer cellRenderer = table.getCellRenderer(r, c);
        Dimension preferredSize = table.prepareRenderer(cellRenderer, r, c).getPreferredSize();
        cellWidth = Math.max(cellWidth, preferredSize.width);
        rowHeights[r] = Math.max(rowHeights[r], preferredSize.height);
      }
      column.setPreferredWidth(cellWidth + JBUIScale.scale(4));
    }
    for (int r = 0; r < table.getRowCount(); r++) {
      table.setRowHeight(r, rowHeights[r]);
    }

    tableContainer.add(table);
    return table;
  }

  @NotNull
  private static List<List<String>> getModelTableData(@NotNull ModelInfo modelInfo) {
    List<List<String>> tableData = new ArrayList<>();
    tableData.add(Lists.newArrayList("Name", modelInfo.getModelName()));
    tableData.add(Lists.newArrayList("Description", breakIntoMultipleLines(modelInfo.getModelDescription(), 80)));
    tableData.add(Lists.newArrayList("Version", modelInfo.getModelVersion()));
    tableData.add(Lists.newArrayList("Author", modelInfo.getModelAuthor()));
    tableData.add(Lists.newArrayList("License", breakIntoMultipleLines(modelInfo.getModelLicense(), 80)));
    return tableData;
  }

  @NotNull
  private static List<List<String>> getTensorTableData(List<TensorInfo> tensorInfoList) {
    List<List<String>> tableData = new ArrayList<>();
    for (TensorInfo tensorInfo : tensorInfoList) {
      TensorInfo.NormalizationParams params = tensorInfo.getNormalizationParams();
      String minMaxColumn = isValidMinMaxColumn(params) ? convertFloatArrayPairToString(params.getMin(), params.getMax()) : "[] / []";
      tableData.add(
        Lists.newArrayList(
          tensorInfo.getName(),
          getTypeStringForDisplay(tensorInfo),
          breakIntoMultipleLines(tensorInfo.getDescription(), 60),
          Arrays.toString(tensorInfo.getShape()),
          minMaxColumn
        ));
    }
    return tableData;
  }

  @NotNull
  private static String getTypeStringForDisplay(@NotNull TensorInfo tensorInfo) {
    StringBuilder stringBuilder = new StringBuilder();
    if (tensorInfo.getContentType() == TensorInfo.ContentType.IMAGE) {
      // Display Image only if it is RGB image.
      stringBuilder.append(tensorInfo.isRGBImage()
                           ? formatUpperString(CaseFormat.UPPER_CAMEL, TensorInfo.ContentType.IMAGE.toString())
                           : formatUpperString(CaseFormat.UPPER_CAMEL, TensorInfo.ContentType.FEATURE.toString()));
    }
    else {
      stringBuilder.append(formatUpperString(CaseFormat.UPPER_CAMEL, tensorInfo.getContentType().toString()));
    }

    stringBuilder.append(
      String.format(
        "%s<%s>", LINE_SEPARATOR, formatUpperString(CaseFormat.LOWER_CAMEL, tensorInfo.getDataType().toString())));

    return stringBuilder.toString();
  }

  @NotNull
  private static String formatUpperString(@NotNull CaseFormat caseFormat, @NotNull String content) {
    return CaseFormat.UPPER_UNDERSCORE.to(caseFormat, content);
  }

  private static boolean isValidMinMaxColumn(@NotNull TensorInfo.NormalizationParams params) {
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
    StringBuilder codeBuilder = new StringBuilder("try {\n");
    String modelClassName = modelClass.getName();
    codeBuilder.append(INDENT).append(String.format("%s model = %s.newInstance(context);\n\n", modelClassName, modelClassName));

    PsiMethod processMethod = findUndeprecatedProcessMethod(modelClass);
    if (processMethod.getReturnType() != null) {
      codeBuilder.append(buildTensorInputSampleCodeInJava(processMethod, modelInfo));

      codeBuilder.append(INDENT).append("// Runs model inference and gets result.\n");
      String parameterNames = Arrays.stream(processMethod.getParameterList().getParameters())
        .map(PsiParameter::getName)
        .collect(Collectors.joining(", "));
      codeBuilder
        .append(INDENT)
        .append(String.format(
          "%s.%s outputs = model.%s(%s);\n",
          modelClassName,
          processMethod.getReturnType().getPresentableText(),
          processMethod.getName(),
          parameterNames
        ));
    }

    PsiClass outputsClass = getInnerOutputsClass(modelClass);
    boolean hasGroupInfo = !modelInfo.getOutputTensorGroups().isEmpty();
    if (outputsClass != null) {
      if (!hasGroupInfo) {
        Iterator<String> outputTensorNameIterator = modelInfo.getOutputs().stream().map(TensorInfo::getIdentifierName).iterator();
        List<PsiMethod> normalMethods =
          ContainerUtil
            .filter(Arrays.asList(outputsClass.getMethods()),
                    method -> isOriginInfoMatched(method, LightModelOutputsClass.TAG_NORMAL_METHOD));
        for (PsiMethod psiMethod : normalMethods) {
          if (psiMethod.isDeprecated()) {
            continue;
          }

          String tensorName = outputTensorNameIterator.next();
          codeBuilder
            .append(INDENT)
            .append(
              String.format(
                "%s %s = outputs.%s();\n",
                Objects.requireNonNull(psiMethod.getReturnType()).getPresentableText(),
                tensorName,
                psiMethod.getName()));
          switch (psiMethod.getReturnType().getCanonicalText()) {
            case ClassNames.TENSOR_LABEL:
              codeBuilder
                .append(INDENT)
                .append(String.format("Map<String, Float> %sMap = %s.getMapWithFloatValue();\n", tensorName, tensorName));
              break;
            case ClassNames.TENSOR_IMAGE:
              codeBuilder.append(INDENT).append(String.format("Bitmap %sBitmap = %s.getBitmap();\n", tensorName, tensorName));
              break;
          }
        }
      }
      else {
        Iterator<String> groupNameIterator = modelInfo.getOutputTensorGroups().stream().map(TensorGroupInfo::getIdentifierName).iterator();
        List<PsiMethod> groupMethods = ContainerUtil
          .filter(Arrays.asList(outputsClass.getMethods()), method -> isOriginInfoMatched(method, LightModelOutputsClass.TAG_GROUP_METHOD));

        for (PsiMethod psiMethod : groupMethods) {
          String groupName = groupNameIterator.next();
          PsiClass groupClass = getInnerGroupClass(modelClass, psiMethod.getReturnType());
          codeBuilder
            .append(INDENT)
            .append(
              String.format(
                "%s %s = outputs.%s().get(0);\n",
                groupClass.getName(),
                groupName,
                psiMethod.getName()));

          codeBuilder
            .append("\n" + INDENT)
            .append(String.format("// Gets result from %s.\n", groupClass.getName()));

          Iterator<String> tensorNameIterator =
            getTensorGroupInfoByIdentifierName(modelInfo, StringHelper.usLocaleDecapitalize(groupClass.getName()))
              .getTensorNames().iterator();
          for (PsiMethod groupMethod : groupClass.getMethods()) {
            codeBuilder
              .append(INDENT)
              .append(
                String.format(
                  "%s %s = %s.%s();\n",
                  Objects.requireNonNull(groupMethod.getReturnType()).getPresentableText(),
                  tensorNameIterator.next(),
                  groupName,
                  groupMethod.getName()));
          }
        }
      }
      codeBuilder.append("\n");
    }

    codeBuilder
      .append(INDENT).append("// Releases model resources if no longer used.\n")
      .append(INDENT).append("model.close();\n");

    codeBuilder
      .append("} catch (IOException e) {\n")
      .append(INDENT)
      .append("// TODO Handle the exception\n")
      .append("}\n");

    return codeBuilder.toString();
  }

  private static boolean isOriginInfoMatched(@NotNull PsiMethod psiMethod, @NotNull String originInfo) {
    return psiMethod instanceof LightMethodBuilder && ((LightMethodBuilder)psiMethod).getOriginInfo().equals(originInfo);
  }

  @NotNull
  private static TensorGroupInfo getTensorGroupInfoByIdentifierName(@NotNull ModelInfo modelInfo, @NotNull String identifierName) {
    Optional<TensorGroupInfo> optional =
      modelInfo.getOutputTensorGroups().stream().filter(tensorGroupInfo -> tensorGroupInfo.getIdentifierName().equals(identifierName))
        .findFirst();

    if (!optional.isPresent()) {
      Logger.getInstance(TfliteModelFileEditor.class)
        .error(String.format("Model %s doesn't have tensor group with name: %s", modelInfo.getModelName(), identifierName));
    }

    return optional.get();
  }

  @NotNull
  private static String buildSampleCodeInKotlin(@NotNull PsiClass modelClass, @NotNull ModelInfo modelInfo) {
    StringBuilder codeBuilder = new StringBuilder(String.format("val model = %s.newInstance(context)\n\n", modelClass.getName()));

    PsiMethod processMethod = findUndeprecatedProcessMethod(modelClass);
    if (processMethod.getReturnType() != null) {
      codeBuilder.append(buildTensorInputSampleCodeInKotlin(processMethod, modelInfo));

      String parameterNames = Arrays.stream(processMethod.getParameterList().getParameters())
        .map(PsiParameter::getName)
        .collect(Collectors.joining(", "));
      codeBuilder
        .append("// Runs model inference and gets result.\n")
        .append(String.format("val outputs = model.%s(%s)\n", processMethod.getName(), parameterNames));
    }

    PsiClass outputsClass = getInnerOutputsClass(modelClass);
    boolean hasGroupInfo = !modelInfo.getOutputTensorGroups().isEmpty();
    if (outputsClass != null) {
      if (!hasGroupInfo) {
        Iterator<String> outputTensorNameIterator = modelInfo.getOutputs().stream().map(TensorInfo::getIdentifierName).iterator();
        List<PsiMethod> normalMethods =
          ContainerUtil
            .filter(Arrays.asList(outputsClass.getMethods()),
                    method -> isOriginInfoMatched(method, LightModelOutputsClass.TAG_NORMAL_METHOD));
        for (PsiMethod psiMethod : normalMethods) {
          if (psiMethod.isDeprecated()) {
            continue;
          }
          String tensorName = outputTensorNameIterator.next();
          codeBuilder.append(String.format("val %s = outputs.%s\n", tensorName, convertToKotlinPropertyName(psiMethod.getName())));
          switch (Objects.requireNonNull(psiMethod.getReturnType()).getCanonicalText()) {
            case ClassNames.TENSOR_LABEL:
              codeBuilder.append(String.format("val %sMap = %s.mapWithFloatValue\n", tensorName, tensorName));
              break;
            case ClassNames.TENSOR_IMAGE:
              codeBuilder.append(String.format("val %sBitmap = %s.bitmap\n", tensorName, tensorName));
              break;
          }
        }
      }
      else {
        Iterator<String> groupNameIterator = modelInfo.getOutputTensorGroups().stream().map(TensorGroupInfo::getIdentifierName).iterator();
        List<PsiMethod> groupMethods = ContainerUtil
          .filter(Arrays.asList(outputsClass.getMethods()), method -> isOriginInfoMatched(method, LightModelOutputsClass.TAG_GROUP_METHOD));
        for (PsiMethod psiMethod : groupMethods) {
          String groupName = groupNameIterator.next();
          PsiClass groupClass = getInnerGroupClass(modelClass, psiMethod.getReturnType());
          codeBuilder.append(String.format("val %s = outputs.%s.get(0)\n", groupName, convertToKotlinPropertyName(psiMethod.getName())));

          Iterator<String> tensorNameIterator =
            getTensorGroupInfoByIdentifierName(modelInfo, StringHelper.usLocaleDecapitalize(groupClass.getName()))
              .getTensorNames().iterator();
          codeBuilder
            .append("\n")
            .append(String.format(String.format("// Gets result from %s.\n", groupClass.getName())));
          for (PsiMethod groupMethod : groupClass.getMethods()) {
            codeBuilder
              .append(
                String.format(
                  "val %s = %s.%s;\n",
                  tensorNameIterator.next(),
                  groupName,
                  convertToKotlinPropertyName(groupMethod.getName())));
          }
        }
      }
      codeBuilder.append("\n");
    }

    codeBuilder
      .append("// Releases model resources if no longer used.\n")
      .append("model.close()\n");

    return codeBuilder.toString();
  }

  @NotNull
  private static PsiMethod findUndeprecatedProcessMethod(@NotNull PsiClass psiClass) {
    PsiMethod[] methods = psiClass.findMethodsByName("process", false);
    return ContainerUtil.filter(Arrays.asList(methods), method -> !method.isDeprecated()).get(0);
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
  private static String buildTensorInputSampleCodeInJava(@NotNull PsiMethod processMethod, @NotNull ModelInfo modelInfo) {
    StringBuilder codeBuilder = new StringBuilder(INDENT + "// Creates inputs for reference.\n");
    Iterator<TensorInfo> tensorInfoIterator = modelInfo.getInputs().iterator();
    for (PsiParameter parameter : processMethod.getParameterList().getParameters()) {
      TensorInfo tensorInfo = tensorInfoIterator.next();
      switch (parameter.getType().getCanonicalText()) {
        case ClassNames.TENSOR_IMAGE:
          codeBuilder.append(INDENT).append(String.format("TensorImage %s = TensorImage.fromBitmap(bitmap);\n", parameter.getName()));
          break;
        case ClassNames.TENSOR_BUFFER:
          codeBuilder
            .append(INDENT)
            .append(
              String.format(
                "TensorBuffer %s = TensorBuffer.createFixedSize(%s, %s);\n",
                parameter.getName(),
                buildIntArrayInJava(tensorInfo.getShape()),
                buildDataType(tensorInfo.getDataType())));
          codeBuilder.append(INDENT).append(String.format("%s.loadBuffer(byteBuffer);\n", parameter.getName()));
          break;
      }
    }
    codeBuilder.append("\n");

    return codeBuilder.toString();
  }

  @NotNull
  private static String buildTensorInputSampleCodeInKotlin(@NotNull PsiMethod processMethod, @NotNull ModelInfo modelInfo) {
    StringBuilder codeBuilder = new StringBuilder("// Creates inputs for reference.\n");
    Iterator<TensorInfo> tensorInfoIterator = modelInfo.getInputs().iterator();
    for (PsiParameter parameter : processMethod.getParameterList().getParameters()) {
      TensorInfo tensorInfo = tensorInfoIterator.next();
      switch (parameter.getType().getCanonicalText()) {
        case ClassNames.TENSOR_IMAGE:
          codeBuilder.append(String.format("val %s = TensorImage.fromBitmap(bitmap)\n", parameter.getName()));
          break;
        case ClassNames.TENSOR_BUFFER:
          codeBuilder
            .append(
              String.format(
                "val %s = TensorBuffer.createFixedSize(%s, %s)\n",
                parameter.getName(),
                buildIntArrayInKotlin(tensorInfo.getShape()),
                buildDataType(tensorInfo.getDataType())));
          codeBuilder.append(String.format("%s.loadBuffer(byteBuffer)\n", parameter.getName()));
          break;
      }
    }
    codeBuilder.append("\n");

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
  private static PsiClass getInnerOutputsClass(@NotNull PsiClass modelClass) {
    return getInnerClass(modelClass, MlNames.OUTPUTS);
  }

  /**
   * Gets inner group class from {@param returnType} of getter method.
   * <p>
   * For each inner group class, it has one specified getter method in output class, so it's safe to extract it from there.
   */
  @Nullable
  private static PsiClass getInnerGroupClass(@NotNull PsiClass modelClass, @Nullable PsiType returnType) {
    if (returnType instanceof PsiClassType) {
      PsiType[] psiTypes = ((PsiClassType)returnType).getParameters();
      if (psiTypes.length == 1) {
        return getInnerClass(modelClass, psiTypes[0].getPresentableText());
      }
    }

    return null;
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
    LightModelClass lightModelClass = getLatestLightModelClass();
    if (myUiStyleTracker.isUiStyleChanged() || !Objects.equals(myLightModelClass, lightModelClass)) {
      myLightModelClass = lightModelClass;
      myRootPane.setViewportView(createContentPanel());
    }
    return myRootPane;
  }

  @NotNull
  @Override
  public VirtualFile getFile() {
    return myFile;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTabbedCodePaneForFocus;
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
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Nullable
  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return FileEditor.super.getBackgroundHighlighter();
  }

  @Override
  public void dispose() {
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
  private static JPanel createPanelWithFlowLayout(@NotNull Border border) {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    panel.setAlignmentX(Component.LEFT_ALIGNMENT);
    panel.setBackground(UIUtil.getTextFieldBackground());
    panel.setBorder(border);
    return panel;
  }

  @NotNull
  private static String breakIntoMultipleLines(@NotNull String text, int maxLineLength) {
    String[] words = text.split(" ");
    StringBuilder result = new StringBuilder();
    StringBuilder tmp = new StringBuilder();
    for (String word : words) {
      if (word.isEmpty()) {
        continue;
      }

      if (tmp.length() + word.length() > maxLineLength) {
        result.append(tmp.toString().trim()).append(LINE_SEPARATOR);
        tmp.setLength(0);
      }
      tmp.append(word).append(" ");
    }

    return result.append(tmp).toString().trim();
  }

  @NotNull
  private static String convertFloatArrayPairToString(@NotNull float[] array1, @NotNull float[] array2) {
    DecimalFormat decimalFormat = new DecimalFormat("#.##");
    String arrayString1 =
      IntStream.range(0, array1.length).mapToObj(i -> decimalFormat.format(array1[i])).collect(Collectors.joining(", ", "[", "]"));
    String arrayString2 =
      IntStream.range(0, array2.length).mapToObj(i -> decimalFormat.format(array2[i])).collect(Collectors.joining(", ", "[", "]"));
    String separator = " /" + (array1.length >= 3 || array2.length >= 3 ? LINE_SEPARATOR : " ");
    return arrayString1 + separator + arrayString2;
  }

  private static boolean isCellContentTypeHtml(TableModel tableModel, int rowIndex, int columnIndex) {
    return ((String)tableModel.getValueAt(rowIndex, columnIndex)).startsWith("<html>");
  }

  private static class MetadataTableModel extends AbstractTableModel {
    private final List<List<String>> myRowDataList;
    private final List<String> myHeaderData;

    private MetadataTableModel(@NotNull List<List<String>> rowDataList, @NotNull List<String> headerData) {
      myRowDataList = ContainerUtil.map(rowDataList, row -> ContainerUtil.map(
        row, cellValue -> URLUtil.URL_PATTERN.matcher(cellValue).find() ? HtmlUtils.plainTextToHtml(cellValue) : cellValue));
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
      return isCellContentTypeHtml(this, rowIndex, columnIndex);
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
      myTextPane.setContentType(isCellContentTypeHtml(table.getModel(), row, column) ? "text/html" : "text/plain");
      myTextPane.setText((String)table.getValueAt(row, column));
      if (((MetadataTableModel)table.getModel()).hasHeader()) {
        myTextPane.setBorder(Borders.empty(8, 8, 8, 0));
      }
      else {
        myTextPane.setBorder(Borders.empty(8, 0, 8, 40));
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

  /**
   * {@link FocusTraversalPolicy} for {@link TfliteModelFileEditor} to traverse focus in viewer.
   */
  private class EditorFocusTraversalPolicy extends FocusTraversalPolicy {

    @Override
    @Nullable
    public Component getComponentAfter(@NotNull Container aContainer, @NotNull Component aComponent) {
      if (aComponent == myTabbedCodePaneForFocus) {
        return myTabbedCodePaneForFocus.getSelectedComponent();
      }
      else {
        return myTabbedCodePaneForFocus;
      }
    }

    @Override
    @Nullable
    public Component getComponentBefore(@NotNull Container aContainer, @NotNull Component aComponent) {
      if (aComponent == myTabbedCodePaneForFocus) {
        return myTabbedCodePaneForFocus.getSelectedComponent();
      }
      else {
        return myTabbedCodePaneForFocus;
      }
    }

    @Override
    @Nullable
    public Component getFirstComponent(@NotNull Container aContainer) {
      return myTabbedCodePaneForFocus;
    }

    @Override
    @Nullable
    public Component getLastComponent(@NotNull Container aContainer) {
      if (myTabbedCodePaneForFocus != null) {
        return myTabbedCodePaneForFocus.getSelectedComponent();
      }
      return null;
    }

    @Override
    @Nullable
    public Component getDefaultComponent(@NotNull Container aContainer) {
      return myTabbedCodePaneForFocus;
    }
  }
}
