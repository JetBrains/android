/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.editor;

import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationHolder;
import com.android.tools.idea.uibuilder.api.DragType;
import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.designer.DesignerEditorPanelFacade;
import com.intellij.designer.LightFillLayout;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.CutProvider;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.PasteProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Assembles a designer editor from various components
 */
public class NlEditorPanel extends JPanel implements DesignerEditorPanelFacade, DataProvider {
  private final XmlFile myFile;
  private final DesignSurface mySurface;
  private final ThreeComponentsSplitter myContentSplitter;

  public NlEditorPanel(@NotNull NlEditor editor, @NotNull AndroidFacet facet, @NotNull VirtualFile file) {
    super(new BorderLayout());
    setOpaque(true);

    final Project project = facet.getModule().getProject();
    myFile = (XmlFile)AndroidPsiUtils.getPsiFileSafely(project, file);
    assert myFile != null : file;

    mySurface = new DesignSurface(project);
    NlModel model = NlModel.create(mySurface, editor, facet, myFile);
    mySurface.setModel(model);

    myContentSplitter = new ThreeComponentsSplitter();

    // The {@link LightFillLayout} provides the UI for the minimized forms of the {@link LightToolWindow}
    // used for the palette and the structure/properties panes.
    JPanel contentPanel = new JPanel(new LightFillLayout());
    final NlActionsToolbar actionsToolbar = new NlActionsToolbar(mySurface);
    actionsToolbar.setModel(model);
    contentPanel.add(actionsToolbar.getToolbarComponent());
    contentPanel.add(mySurface);
    List<NlComponent> components = model.getComponents();

    myContentSplitter.setDividerWidth(0);
    myContentSplitter.setDividerMouseZoneSize(Registry.intValue("ide.splitter.mouseZone"));
    myContentSplitter.setInnerComponent(contentPanel);
    add(myContentSplitter, BorderLayout.CENTER);
  }

  @Nullable
  public JComponent getPreferredFocusedComponent() {
    return mySurface.getPreferredFocusedComponent();
  }

  public void dispose() {
    NlPaletteManager.get(mySurface.getProject()).dispose(this);
    NlPropertiesWindowManager.get(mySurface.getProject()).dispose(this);
    Disposer.dispose(myContentSplitter);
  }

  public void activate() {
    mySurface.activate();
  }

  public void deactivate() {
    mySurface.deactivate();
  }

  @NotNull
  public XmlFile getFile() {
    return myFile;
  }

  public DesignSurface getSurface() {
    return mySurface;
  }

  @Override
  public Object getData(@NonNls String dataId) {
    if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId) ||
        PlatformDataKeys.CUT_PROVIDER.is(dataId) ||
        PlatformDataKeys.COPY_PROVIDER.is(dataId) ||
        PlatformDataKeys.PASTE_PROVIDER.is(dataId)) {
      return new ActionHandler(this);
    }
    return null;
  }

  @Override
  public ThreeComponentsSplitter getContentSplitter() {
    return myContentSplitter;
  }

  private static class ActionHandler implements DeleteProvider, CutProvider, CopyProvider, PasteProvider {
    private final NlEditorPanel myEditor;

    public ActionHandler(NlEditorPanel panel) {
      myEditor = panel;
    }

    @Override
    public void performCopy(@NotNull DataContext dataContext) {
      ScreenView screenView = myEditor.getSurface().getCurrentScreenView();
      if (screenView == null) {
        return;
      }
      CopyPasteManager.getInstance().setContents(screenView.getModel().getSelectionAsTransferable());
    }

    @Override
    public boolean isCopyEnabled(@NotNull DataContext dataContext) {
      return hasNonEmptySelection();
    }

    @Override
    public boolean isCopyVisible(@NotNull DataContext dataContext) {
      return true;
    }

    @Override
    public void performCut(@NotNull DataContext dataContext) {
      performCopy(dataContext);
      deleteElement(dataContext);
    }

    @Override
    public boolean isCutEnabled(@NotNull DataContext dataContext) {
      return hasNonEmptySelection();
    }

    @Override
    public boolean isCutVisible(@NotNull DataContext dataContext) {
      return true;
    }

    @Override
    public void deleteElement(@NotNull DataContext dataContext) {
      DesignSurface surface = myEditor.getSurface();
      ScreenView screenView = surface.getCurrentScreenView();
      if (screenView == null) {
        return;
      }
      SelectionModel selectionModel = screenView.getSelectionModel();
      NlModel model = screenView.getModel();
      model.delete(selectionModel.getSelection());
    }

    @Override
    public boolean canDeleteElement(@NotNull DataContext dataContext) {
      return hasNonEmptySelection();
    }

    @Override
    public void performPaste(@NotNull DataContext dataContext) {
      pasteOperation(false /* check and perform the actual paste */);
    }

    @Override
    public boolean isPastePossible(@NotNull DataContext dataContext) {
      return true;
    }

    @Override
    public boolean isPasteEnabled(@NotNull DataContext dataContext) {
      return pasteOperation(true /* check only */);
    }

    private boolean hasNonEmptySelection() {
      ScreenView screenView = myEditor.getSurface().getCurrentScreenView();
      return screenView != null && !screenView.getSelectionModel().isEmpty();
    }

    private boolean pasteOperation(boolean checkOnly) {
      ScreenView screenView = myEditor.getSurface().getCurrentScreenView();
      if (screenView == null) {
        return false;
      }
      List<NlComponent> selection = screenView.getSelectionModel().getSelection();
      if (selection.size() != 1) {
        return false;
      }
      NlComponent receiver = selection.get(0);
      NlComponent before;
      NlModel model = screenView.getModel();
      ViewHandlerManager handlerManager = ViewHandlerManager.get(model.getProject());
      ViewHandler handler = handlerManager.getHandler(receiver);
      if (handler instanceof ViewGroupHandler) {
        before = receiver.getChild(0);
      }
      else {
        before = receiver.getNextSibling();
        receiver = receiver.getParent();
        if (receiver == null) {
          return false;
        }
      }

      DnDTransferItem item = getClipboardData();
      if (item == null) {
        return false;
      }
      InsertType insertType = model.determineInsertType(DragType.PASTE, item, checkOnly);
      List<NlComponent> pasted = model.createComponents(screenView, item, insertType);
      if (!model.canAddComponents(pasted, receiver, before)) {
        return false;
      }
      if (checkOnly) {
        return true;
      }
      model.addComponents(pasted, receiver, before, insertType);
      return true;
    }

    @Nullable
    private static DnDTransferItem getClipboardData() {
      try {
        Object data = CopyPasteManager.getInstance().getContents(ItemTransferable.DESIGNER_FLAVOR);
        if (!(data instanceof DnDTransferItem)) {
          return null;
        }
        return (DnDTransferItem)data;
      }
      catch (Exception e) {
        return null;
      }
    }
  }

  /**
   * <b>Temporary</b> bridge to older Configuration actions. When we can ditch the old layout preview
   * and old layout editors, we no longer needs this level of indirection to let the configuration actions
   * talk to multiple different editor implementations, and the render actions can directly address DesignSurface.
   */
  public static class NlConfigurationHolder implements ConfigurationHolder {
    @NotNull private final DesignSurface mySurface;

    public NlConfigurationHolder(@NotNull DesignSurface surface) {
      mySurface = surface;
    }

    @Nullable
    @Override
    public Configuration getConfiguration() {
      return mySurface.getConfiguration();
    }

    @Override
    public void setConfiguration(@NotNull Configuration configuration) {
      mySurface.setConfiguration(configuration);
    }
  }
}
