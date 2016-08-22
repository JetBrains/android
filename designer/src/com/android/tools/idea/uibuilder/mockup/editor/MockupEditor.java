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
package com.android.tools.idea.uibuilder.mockup.editor;

import com.android.sdklib.devices.Device;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.uibuilder.mockup.Mockup;
import com.android.tools.idea.uibuilder.mockup.MockupFileHelper;
import com.android.tools.idea.uibuilder.mockup.editor.tools.CropTool;
import com.android.tools.idea.uibuilder.mockup.editor.tools.ExtractWidgetTool;
import com.android.tools.idea.uibuilder.model.ModelListener;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.DesignSurfaceListener;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.tools.pixelprobe.PixelProbe;
import com.android.tools.pixelprobe.Image;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.TextAccessor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * Panel displaying the mockup and allowing actions like
 * cropping and widget extraction
 */
public class MockupEditor extends JPanel {

  public static final Logger LOG = Logger.getInstance(MockupEditor.class);
  private static final String TITLE = "Mockup Editor";
  private static final Dimension MINIMUM_SIZE = new Dimension(100, 100);
  private final myModelListener myModelListener;

  @Nullable private NlModel myModel;
  @Nullable private Mockup myMockup;

  private final List<MockupEditorListener> myEditorListeners = new ArrayList<>();
  private final Set<Tool> myActiveTools = new HashSet<>();
  private final ExtractWidgetTool myExtractWidgetTool;
  private final Mockup.MockupModelListener myMockupListener;

  // UI
  private final MockupViewPanel myMockupViewPanel;
  private final MyTopBar myTopBar;

  public MockupEditor(@NotNull DesignSurface surface, @Nullable NlModel model) {
    super(new BorderLayout());
    myModelListener = new myModelListener(this);
    setModel(model);
    surface.addListener(new MyDesignSurfaceListener(this));
    myMockupListener = this::repopulateFields;
    myMockupViewPanel = new MockupViewPanel(this);
    myExtractWidgetTool = new ExtractWidgetTool(surface, this);

    add(myMockupViewPanel, BorderLayout.CENTER);
    myTopBar = new MyTopBar(this);
    add(myTopBar, BorderLayout.NORTH);
    myExtractWidgetTool.enable(this);

    setMinimumSize(MINIMUM_SIZE);
    init();
  }


  private void init() {
    if (myModel != null) {
      List<NlComponent> selection = myModel.getSelectionModel().getSelection();
      if (selection.isEmpty()) {
        selection = myModel.getComponents();
      }
      selectionUpdated(myModel, selection);
    }
  }

  /**
   * Update the currently displayed mockup with the new selection
   *
   * @param model     The model where the selection has been made
   * @param selection The selected component
   */
  private void selectionUpdated(@Nullable NlModel model, @NotNull List<NlComponent> selection) {
    Mockup mockup = myMockup;
    if (model != null &&
        (selection.isEmpty() || !Mockup.hasMockupAttribute(selection.get(0)))) {
      // If the first element of the selection does not have a mockup attribute
      selection = model.getComponents();
    }

    if (!selection.isEmpty()) {
      NlComponent component = selection.get(0);
      mockup = Mockup.create(component, true);
      if (mockup == null) {
        return;
      }
    }
    if (mockup != myMockup) {
      showMockupInEditor(mockup);
    }
  }

  /**
   * Reset the editor as it was just opened, but with a new mockup
   *
   * @param mockup the new mockup to display in the editor
   */
  private void showMockupInEditor(@Nullable Mockup mockup) {
    resetTools();
    if (mockup != myMockup) {
      setMockup(mockup);
    }
    repopulateFields(mockup);
    notifyListeners(mockup);
  }

  private void setMockup(@Nullable Mockup mockup) {
    if (myMockup != null) {
      myMockup.removeMockupListener(myMockupListener);
    }
    myMockup = mockup;
    if (myMockup != null) {
      myMockup.addMockupListener(myMockupListener);
    }
  }

  private void notifyListeners(Mockup mockup) {
    myEditorListeners.forEach(listener -> listener.editorUpdated(mockup));
  }

  /**
   * Update the values of the file chooser and the viewTypeTextField to
   * match the data of the mockup
   *
   * @param mockup
   */
  private void repopulateFields(@Nullable Mockup mockup) {
    if (mockup == null) {
      return;
    }
    VirtualFile virtualFile = mockup.getVirtualFile();
    UIUtil.invokeLaterIfNeeded(() -> {
      String fileName = virtualFile != null ? virtualFile.getPath() : null;
      myTopBar.setFileName(fileName);
    });
  }

  public void addListener(@NotNull MockupEditorListener listener) {
    if (!myEditorListeners.contains(listener)) {
      myEditorListeners.add(listener);
    }
  }

  public void removeListener(@NotNull MockupEditorListener listener) {
    myEditorListeners.remove(listener);
  }

  @NotNull
  public MockupViewPanel getMockupViewPanel() {
    return myMockupViewPanel;
  }

  /**
   * Disable every currently active tool and
   * enable only the default one
   */
  private void resetTools() {
    for (Tool activeTool : myActiveTools) {
      activeTool.disable(this);
    }
    myActiveTools.clear();
    myExtractWidgetTool.enable(this);
  }

  /**
   * Disable tool and enable default tool
   *
   * @param tool the tool to disable
   */
  public void disableTool(@NotNull Tool tool) {
    tool.disable(this);
    myActiveTools.remove(tool);
    if (myActiveTools.isEmpty()) {
      myExtractWidgetTool.enable(this);
    }
  }

  /**
   * Disable default tool and enable tool
   *
   * @param tool the tool to enable
   */
  public void enableTool(@NotNull Tool tool) {
    myExtractWidgetTool.disable(this);
    tool.enable(this);
    myActiveTools.add(tool);
  }

  @Nullable
  public Mockup getMockup() {
    return myMockup;
  }

  private void setModel(@Nullable NlModel model) {
    if (model == myModel) {
      return;
    }
    if (myModel != null) {
      myModel.removeListener(myModelListener);
    }
    myModel = model;
    if (myModel != null) {
      myModel.addListener(myModelListener);
    }
    List<NlComponent> selection = myModel != null
                                  ? myModel.getSelectionModel().getSelection()
                                  : Collections.emptyList();

    selectionUpdated(myModel, selection);
  }

  /**
   * Tool used in the mockup editor
   */
  public interface Tool {

    /**
     * The implementing class should set mockupViewPanel to the
     * needed state for itself
     *
     * @param mockupEditor The {@link MockupEditor} on which the {@link Tool} behave
     */
    void enable(@NotNull MockupEditor mockupEditor);

    /**
     * The implementing class should reset mockupViewPanel to the state it was before {@link #enable(MockupEditor)}.
     * Can use {@link MockupViewPanel#resetState()}.
     * needed state for itself
     *
     * @param mockupEditor The {@link MockupEditor} on which the {@link Tool} behave
     */
    void disable(@NotNull MockupEditor mockupEditor);
  }

  /**
   * Listener to update the editor when the selection or model has changed
   */
  private static class MyDesignSurfaceListener implements DesignSurfaceListener {
    MockupEditor myEditor;

    public MyDesignSurfaceListener(@NotNull MockupEditor editor) {
      myEditor = editor;
    }

    @Override
    public void componentSelectionChanged(@NotNull DesignSurface surface, @NotNull List<NlComponent> newSelection) {
      myEditor.selectionUpdated(myEditor.myModel, newSelection);
    }

    @Override
    public void screenChanged(@NotNull DesignSurface surface, @Nullable ScreenView screenView) {
    }

    @Override
    public void modelChanged(@NotNull DesignSurface surface, @Nullable NlModel model) {
      myEditor.setModel(model);
    }


    @Override
    public boolean activatePreferredEditor(@NotNull DesignSurface surface, @NotNull NlComponent component) {
      return false;
    }
  }

  /**
   * Bar on top showing the title and actions
   */
  private static class MyTopBar extends JPanel {
    private final MockupEditor myMockupEditor;
    private TextFieldWithBrowseButton myFileChooser;

    MyTopBar(@NotNull MockupEditor mockupEditor) {
      super(new BorderLayout());
      myMockupEditor = mockupEditor;
      add(createTitleBar(), BorderLayout.NORTH);
      add(createActionBar(), BorderLayout.SOUTH);
    }

    @NotNull
    private JPanel createActionBar() {
      JPanel actionBar = new JPanel(new BorderLayout());
      JPanel cropTool = new CropTool(myMockupEditor);
      actionBar.add(cropTool, BorderLayout.EAST);
      actionBar.setBorder(new CompoundBorder(
        IdeBorderFactory.createBorder(SideBorder.BOTTOM),
        IdeBorderFactory.createEmptyBorder(0, 10, 0, 5)));
      return actionBar;
    }

    @NotNull
    private JPanel createTitleBar() {
      JPanel titleBar = new JPanel(new BorderLayout());
      myFileChooser = new TextFieldWithBrowseButton();
      myFileChooser.setEditable(false);
      myFileChooser.addActionListener(new FileChooserActionListener(myMockupEditor));

      titleBar.add(myFileChooser, BorderLayout.EAST);
      titleBar.add(new JBLabel(TITLE), BorderLayout.WEST);
      titleBar.setBorder(new CompoundBorder(
        IdeBorderFactory.createBorder(SideBorder.BOTTOM),
        IdeBorderFactory.createEmptyBorder(1, 5, 1, 10)));
      return titleBar;
    }

    private void setFileName(String fileName) {
      myFileChooser.setText(fileName);
    }
  }

  /**
   * Listener for the file chooser
   */
  private static class FileChooserActionListener implements ActionListener {

    private MockupEditor myMockupEditor;

    public FileChooserActionListener(@NotNull MockupEditor mockupEditor) {
      myMockupEditor = mockupEditor;
    }

    @Override
    public void actionPerformed(ActionEvent e) {

      Mockup mockup = myMockupEditor.getMockup();
      if (mockup == null) {
        return;
      }
      FileChooserDescriptor descriptor = MockupFileHelper.getFileChooserDescriptor();
      VirtualFile selectedFile = mockup.getVirtualFile();

      FileChooser.chooseFile(
        descriptor, null, myMockupEditor, selectedFile,
        (virtualFile) -> {
          if (mockup.getComponent().isRoot()) {
            openDeviceChoiceDialog(virtualFile, mockup);
          }
          else {
            saveMockupFile(virtualFile, mockup);
            TextAccessor textAccessor = e.getSource() instanceof TextAccessor ? ((TextAccessor)e.getSource()) : null;
            if (textAccessor != null) {
              textAccessor.setText(virtualFile.getPath());
            }
          }
        });
    }

    /**
     * Open a dialog asking to choose a device whose dimensions match those of the image
     *
     * @param virtualFile
     * @param mockup
     */
    private static void openDeviceChoiceDialog(VirtualFile virtualFile, @NotNull Mockup mockup) {
      if (virtualFile.exists() && !virtualFile.isDirectory()) {
        try {
          Image probe = PixelProbe.probe(virtualFile.getInputStream());
          BufferedImage image = probe.getMergedImage();
          if (image == null) {
            return;
          }
          NlModel model = mockup.getComponent().getModel();
          Configuration configuration = model.getConfiguration();
          Device device = configuration.getDevice();
          if (device == null) {
            return;
          }

          ApplicationManager.getApplication().invokeLater(() -> {
            DeviceSelectionPopup deviceSelectionPopup =
              new DeviceSelectionPopup(model.getProject(), configuration, image);
            if (deviceSelectionPopup.showAndGet()) {
              saveMockupFile(virtualFile, mockup);
            }
          });
        }
        catch (IOException e1) {
          LOG.warn("Unable to open this file\n" + e1.getMessage());
        }
      }
    }

    private static void saveMockupFile(VirtualFile virtualFile, @NotNull Mockup mockup) {
      MockupFileHelper.writeFileNameToXML(virtualFile, mockup.getComponent());
    }
  }

  /**
   * Notify when the currently displayed mockup has been changed
   */
  public interface MockupEditorListener {
    void editorUpdated(Mockup mockup);
  }

  private static class myModelListener implements ModelListener {
    private final MockupEditor myMockupEditor;

    public myModelListener(MockupEditor mockupEditor) {
      myMockupEditor = mockupEditor;
    }

    @Override
    public void modelChanged(@NotNull NlModel model) {
      myMockupEditor.selectionUpdated(model, model.getSelectionModel().getSelection());
    }

    @Override
    public void modelRendered(@NotNull NlModel model) {
      myMockupEditor.selectionUpdated(model, model.getSelectionModel().getSelection());
    }
  }
}
