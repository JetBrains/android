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
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.structure.NlComponentTree;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.tools.pixelprobe.*;
import com.android.tools.pixelprobe.Image;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.ui.FrameWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.WeakHashMap;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.dnd.DropTarget;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class MockupEditor implements Disposable {
  public static final Logger LOG = Logger.getInstance(MockupEditor.class);
  public static final String TITLE = "Mockup Editor";

  private static final double RELATIVE_SIZE_TO_SOURCE = 0.90;
  private static WeakHashMap<ScreenView, MockupEditor> ourOpenedEditors = new WeakHashMap<>();
  private Mockup myMockup;
  private final ScreenView myScreenView;

  private FrameWrapper myFrameWrapper;
  private JTextField myViewTypeTextField;
  private TextFieldWithBrowseButton myFileChooser;
  private JPanel myContentPane;
  private JButton myCloseButton;
  private JPanel myCropTool;
  private NlComponentTree myComponentTree;
  private MockupViewPanel myMockupViewPanel;
  private List<MockupEditorListener> myEditorListeners;
  private Set<Tool> myActiveTools;

  private ExtractWidgetTool myExtractWidgetTool;
  private final Mockup.MockupModelListener myMockupListener;

  public MockupEditor(ScreenView screenView, Mockup mockup) {
    myMockup = mockup;
    myScreenView = screenView;
    myMockupListener = this::repopulateFields;
    repopulateFields(myMockup);
    myCloseButton.addActionListener(e -> {
      ourOpenedEditors.remove(screenView);
      Disposer.dispose(myFrameWrapper);
    });
    myFileChooser.addActionListener(new FileChooserActionListener());
  }

  private void createUIComponents() {
    myMockup.addMockupListener(myMockupListener);
    myEditorListeners = new ArrayList<>();
    myActiveTools = new HashSet<>();
    myMockupViewPanel = new MockupViewPanel(myMockup, this);
    myCropTool = new CropTool(myMockup, this);
    myExtractWidgetTool = new ExtractWidgetTool(myMockup, myScreenView, myMockupViewPanel, this);
    myExtractWidgetTool.enable(myMockupViewPanel);
    myComponentTree = new NlComponentTree(myScreenView.getSurface());
    myComponentTree.addMouseListener(new ComponentTreeMouseListener());
    myComponentTree.setToggleClickCount(3);
    myComponentTree.setDropTarget(new DropTarget(myComponentTree, new MockupDropListener(myComponentTree)));
    myComponentTree.setExpandableItemsEnabled(true);
  }

  /**
   * Reset the editor as it was just opened, but with a new mockup
   *
   * @param mockup the new mockup to display in the editor
   */
  private void updateMockup(Mockup mockup) {
    resetTools();
    if (mockup != myMockup) {
      setMockup(mockup);
    }
    repopulateFields(mockup);
    notifyListeners(mockup);
  }

  /**
   * Brings this editor in front of others windows
   */
  private void toFront() {
    if (myFrameWrapper != null) {
      myFrameWrapper.getFrame().toFront();
    }
  }

  private void setMockup(Mockup mockup) {
    if (myMockup != null) {
      myMockup.removeMockupListener(myMockupListener);
    }
    myMockup = mockup;
    myMockup.addMockupListener(myMockupListener);
  }

  private void notifyListeners(Mockup mockup) {
    for (int i = 0; i < myEditorListeners.size(); i++) {
      myEditorListeners.get(i).editorUpdated(mockup);
    }
  }

  /**
   * Update the values of the file chooser and the viewTypeTextField to
   * match the data of the mockup
   *
   * @param mockup
   */
  private void repopulateFields(Mockup mockup) {
    final VirtualFile virtualFile = mockup.getVirtualFile();
    UIUtil.invokeLaterIfNeeded(() -> {
      myFileChooser.setText(virtualFile != null ? virtualFile.getPath() : null);
      myViewTypeTextField.setText(mockup.getComponent().getTagName());
    });
  }

  public void addListener(@NotNull MockupEditorListener listener) {
    if (!myEditorListeners.contains(listener)) {
      myEditorListeners.add(listener);
    }
  }

  public void removeListener(MockupEditorListener listener) {
    myEditorListeners.remove(listener);
  }

  @Nullable
  public MockupViewPanel getMockupViewPanel() {
    return myMockupViewPanel;
  }

  /**
   * Disable every currently active tool and
   * enable only the default one
   */
  private void resetTools() {
    for (Tool activeTool : myActiveTools) {
      activeTool.disable(myMockupViewPanel);
    }
    myActiveTools.clear();
    myExtractWidgetTool.enable(myMockupViewPanel);
  }

  /**
   * Disable tool and enable default tool
   *
   * @param tool the tool to disable
   */
  public void disableTool(Tool tool) {
    tool.disable(myMockupViewPanel);
    myActiveTools.remove(tool);
    if (myActiveTools.isEmpty()) {
      myExtractWidgetTool.enable(myMockupViewPanel);
    }
  }

  /**
   * Disable default tool and enable tool
   *
   * @param tool the tool to enable
   */
  public void enableTool(Tool tool) {
    myExtractWidgetTool.disable(myMockupViewPanel);
    tool.enable(myMockupViewPanel);
    myActiveTools.add(tool);
  }

  protected JPanel getContentPane() {
    return myContentPane;
  }

  @Override
  public void dispose() {
    ourOpenedEditors.remove(myScreenView);
  }

  public void setFrameWrapper(FrameWrapper frameWrapper) {
    myFrameWrapper = frameWrapper;
  }

  /**
   * Tool used in the mockup editor
   */
  public interface Tool {

    /**
     * The implementing class should set mockupViewPanel to the
     * needed state for itself
     *
     * @param mockupViewPanel The {@link MockupViewPanel} on which the {@link Tool} behave
     */
    void enable(MockupViewPanel mockupViewPanel);

    /**
     * The implementing class should reset mockupViewPanel to the state it was before {@link #enable(MockupViewPanel)}.
     * Can use {@link MockupViewPanel#resetState()}.
     * needed state for itself
     *
     * @param mockupViewPanel The {@link MockupViewPanel} on which the {@link Tool} behave
     */
    void disable(MockupViewPanel mockupViewPanel);
  }

  private class FileChooserActionListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
      if (myMockup == null) {
        return;
      }
      final FileChooserDescriptor descriptor = MockupFileHelper.getFileChooserDescriptor();
      VirtualFile selectedFile = myMockup.getVirtualFile();

      FileChooser.chooseFile(
        descriptor, null, myContentPane, selectedFile,
        (virtualFile) -> {
          if (myMockup.getComponent().isRoot()) {
            openDeviceChoiceDialog(virtualFile);
          }
          else {
            saveMockupFile(virtualFile);
          }
        });
    }

    /**
     * Open a dialog asking to choose a device whose dimensions match those of the image
     * @param virtualFile
     */
    private void openDeviceChoiceDialog(VirtualFile virtualFile) {
      if (virtualFile.exists() && !virtualFile.isDirectory()) {
        try {
          final Image probe = PixelProbe.probe(virtualFile.getInputStream());
          final BufferedImage image = probe.getMergedImage();
          if (image == null) {
            return;
          }
          final NlModel model = myMockup.getComponent().getModel();
          final Configuration configuration = model.getConfiguration();
          final Device device = configuration.getDevice();
          if (device == null) {
            return;
          }

          ApplicationManager.getApplication().invokeLater(() -> {
            final DeviceSelectionPopup deviceSelectionPopup =
              new DeviceSelectionPopup(model.getProject(), configuration, image);
            if (deviceSelectionPopup.showAndGet()) {
              saveMockupFile(virtualFile);
            }
          });
        }
        catch (IOException e1) {
          LOG.warn("Unable to open this file\n" + e1.getMessage());
        }
      }
    }

    private void saveMockupFile(VirtualFile virtualFile) {
      MockupFileHelper.writeFileNameToXML(virtualFile, myMockup.getComponent());
      myFileChooser.setText(virtualFile.getName());
    }
  }

  /**
   * Notify when the currently displayed mockup has been changed
   */
  public interface MockupEditorListener {

    void editorUpdated(Mockup mockup);
  }

  private class ComponentTreeMouseListener extends MouseAdapter {

    @Override
    public void mouseClicked(MouseEvent e) {
      if (e.getClickCount() == 1) {
        TreePath path = myComponentTree.getPathForLocation(e.getX(), e.getY());
        Object component;
        if (path != null) {
          component = path.getLastPathComponent();
        }
        else {
          component = myComponentTree.getLastSelectedPathComponent();
        }
        if (component instanceof NlComponent) {
          create(myScreenView, (NlComponent)component);
        }
      }
    }
  }

  /**
   * Create a popup showing the tools to edit the mockup of the selected component
   */
  public static void create(ScreenView screenView, @Nullable NlComponent component) {

    // Do not show the popup if nothing is selected
    if (component == null) {
      return;
    }
    final Mockup mockup = Mockup.create(component, true);
    if (mockup == null) {
      return;
    }

    final DesignSurface designSurface = screenView.getSurface();

    // If an editor with the same screenView is already open,
    // update it with the new mockup
    if (ourOpenedEditors.containsKey(screenView)) {
      final MockupEditor editor = ourOpenedEditors.get(screenView);
      editor.updateMockup(mockup);
      editor.toFront();
      return;
    }

    final MockupEditor editor = new MockupEditor(screenView, mockup);

    // Find the parent window to display the editor in the middle
    Component rootPane = SwingUtilities.getRoot(designSurface);
    final Dimension minSize = new Dimension((int)Math.round(rootPane.getWidth() * RELATIVE_SIZE_TO_SOURCE),
                                            (int)Math.round(rootPane.getHeight() * RELATIVE_SIZE_TO_SOURCE));

    FrameWrapper frame = new FrameWrapper(designSurface.getProject());
    frame.setTitle(String.format("%s - %s - %s",
                                 screenView.getModel().getProject().getName(),
                                 TITLE,
                                 screenView.getModel().getFile().getName()));
    frame.setComponent(editor.myContentPane);
    frame.setSize(minSize);
    frame.addDisposable(editor);
    Point point = new Point(
      (int)Math.round(rootPane.getX() + (rootPane.getWidth()) / 2 - minSize.getWidth() / 2),
      (int)Math.round(rootPane.getY() + (rootPane.getHeight()) / 2 - minSize.getHeight() / 2));

    frame.setLocation(point);
    frame.getFrame().setSize(minSize);
    frame.show();

    editor.setFrameWrapper(frame);
    // Keep track of the opened Editor to open only one by ScreenView
    ourOpenedEditors.put(screenView, editor);
  }
}
