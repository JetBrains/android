/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.uipreview;


import com.android.SdkConstants;
import com.android.ide.common.resources.configuration.DeviceConfigHelper;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.android.tools.idea.configurations.*;
import com.android.tools.idea.rendering.RenderResult;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CheckboxAction;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.components.JBScrollPane;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidLayoutPreviewToolWindowForm implements Disposable, ConfigurationListener, RenderContext {
  private JPanel myContentPanel;
  private AndroidLayoutPreviewPanel myPreviewPanel;
  private JBScrollPane myScrollPane;
  private JPanel myComboPanel;
  private PsiFile myFile;
  private Configuration myConfiguration;
  private final AndroidLayoutPreviewToolWindowManager myToolWindowManager;
  private final ActionToolbar myActionToolBar;
  private final AndroidLayoutPreviewToolWindowSettings mySettings;

  public AndroidLayoutPreviewToolWindowForm(final Project project, AndroidLayoutPreviewToolWindowManager toolWindowManager) {
    Disposer.register(this, myPreviewPanel);

    myToolWindowManager = toolWindowManager;
    mySettings = AndroidLayoutPreviewToolWindowSettings.getInstance(project);

    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(new ZoomToFitAction());
    actionGroup.add(new ZoomActualAction());
    actionGroup.addSeparator();
    actionGroup.add(new ZoomInAction());
    actionGroup.add(new ZoomOutAction());
    actionGroup.addSeparator();
    actionGroup.add(new RefreshAction());
    myActionToolBar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, actionGroup, true);
    myActionToolBar.setReservePlaceAutoPopupIcon(false);

    final DefaultActionGroup optionsGroup = new DefaultActionGroup();
    final ActionToolbar optionsToolBar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, optionsGroup, true);
    optionsToolBar.setReservePlaceAutoPopupIcon(false);
    optionsToolBar.setSecondaryActionsTooltip("Options");
    optionsGroup.addAction(new CheckboxAction("Hide for non-layout files") {
      @Override
      public boolean isSelected(AnActionEvent e) {
        return mySettings.getGlobalState().isHideForNonLayoutFiles();
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        mySettings.getGlobalState().setHideForNonLayoutFiles(state);
      }
    }).setAsSecondary(true);

    final JComponent toolbar = myActionToolBar.getComponent();
    final JPanel toolBarWrapper = new JPanel(new BorderLayout());
    toolBarWrapper.add(toolbar, BorderLayout.CENTER);
    Dimension preferredToolbarSize = toolbar.getPreferredSize();
    Dimension minimumToolbarSize = toolbar.getMinimumSize();
    toolBarWrapper.setPreferredSize(new Dimension(preferredToolbarSize.width, minimumToolbarSize.height));
    toolBarWrapper.setMinimumSize(new Dimension(10, minimumToolbarSize.height));

    final JPanel fullToolbarComponent = new JPanel(new BorderLayout());
    fullToolbarComponent.add(toolBarWrapper, BorderLayout.CENTER);
    fullToolbarComponent.add(optionsToolBar.getComponent(), BorderLayout.EAST);

    ConfigurationToolBar configToolBar = new ConfigurationToolBar(this);

    final GridBagConstraints gb = new GridBagConstraints();
    gb.fill = GridBagConstraints.HORIZONTAL;
    gb.anchor = GridBagConstraints.CENTER;
    gb.insets = new Insets(0, 2, 2, 2);
    gb.weightx = 1;
    gb.gridx = 0;
    gb.gridy = 0;
    gb.gridwidth = 1;
    myComboPanel.add(configToolBar, gb);
    gb.fill = GridBagConstraints.NONE;
    gb.anchor = GridBagConstraints.EAST;
    gb.gridx = 0;
    gb.gridy++;
    myComboPanel.add(fullToolbarComponent, gb);

    myContentPanel.addComponentListener(new ComponentListener() {
      @Override
      public void componentResized(ComponentEvent e) {
        myPreviewPanel.updateImageSize();
      }

      @Override
      public void componentMoved(ComponentEvent e) {
      }

      @Override
      public void componentShown(ComponentEvent e) {
      }

      @Override
      public void componentHidden(ComponentEvent e) {
      }
    });

    myScrollPane.getHorizontalScrollBar().setUnitIncrement(5);
    myScrollPane.getVerticalScrollBar().setUnitIncrement(5);
  }

  public JPanel getContentPanel() {
    return myContentPanel;
  }

  public PsiFile getFile() {
    return myFile;
  }

  public void setFile(@Nullable PsiFile file) {
    final boolean fileChanged = !Comparing.equal(myFile, file);
    myFile = file;

    if (fileChanged) {
      if (myConfiguration != null) {
        myConfiguration.removeListener(this);
        myConfiguration = null;
      }

      if (file != null) {
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile != null) {
          final AndroidFacet facet = AndroidFacet.getInstance(file);
          if (facet != null) {
            ConfigurationManager manager = facet.getConfigurationManager();
            myConfiguration = manager.get(virtualFile);
            myConfiguration.addListener(this);
          }
        }
      }
    }
  }
  
  private void saveState() {
    if (myConfiguration != null) {
      myConfiguration.save();
    }
  }

  @Override
  public void dispose() {
  }

  public void setRenderResult(@NotNull final RenderResult renderResult, @Nullable final TextEditor editor) {
    myPreviewPanel.setRenderResult(renderResult, editor);
  }

  @NotNull
  public AndroidLayoutPreviewPanel getPreviewPanel() {
    return myPreviewPanel;
  }

  public void updatePreviewPanel() {
    myPreviewPanel.update();
  }

  public void updateDevicesAndTargets(@Nullable AndroidPlatform platform) {
    // TODO: When is this called? How do I update my configuration?
  }

  // ---- Implements RenderContext ----

  @Override
  @Nullable
  public Configuration getConfiguration() {
    return myConfiguration;
  }

  @Override
  public void requestRender() {
    if (myFile != null) {
      myToolWindowManager.render();
    }
  }

  @Override
  @NotNull
  public UsageType getType() {
    return UsageType.XML_PREVIEW;
  }

  @Nullable
  @Override
  public XmlFile getXmlFile() {
    return (XmlFile)myFile;
  }

  @Nullable
  @Override
  public VirtualFile getVirtualFile() {
    return myFile != null ? myFile.getVirtualFile() : null;
  }

  @NotNull
  @Override
  public Module getModule() {
    if (myFile != null) {
      final AndroidFacet facet = AndroidFacet.getInstance(myFile);
      if (facet != null) {
        return facet.getModule();
      }
    }

    return null;
  }

  // ---- Implements ConfigurationListener ----

  @Override
  public boolean changed(int flags) {
    saveState();
    myToolWindowManager.render();

    return true;
  }

  private class ZoomInAction extends AnAction {
    ZoomInAction() {
      super(AndroidBundle.message("android.layout.preview.zoom.in.action.text"), null, AndroidIcons.ZoomIn);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myPreviewPanel.zoomIn();
      myActionToolBar.updateActionsImmediately();
    }
  }

  private class ZoomOutAction extends AnAction {
    ZoomOutAction() {
      super(AndroidBundle.message("android.layout.preview.zoom.out.action.text"), null, AndroidIcons.ZoomOut);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myPreviewPanel.zoomOut();
      myActionToolBar.updateActionsImmediately();
    }
  }

  private class ZoomActualAction extends AnAction {
    ZoomActualAction() {
      super(AndroidBundle.message("android.layout.preview.zoom.actual.action.text"), null, AndroidIcons.ZoomActual);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myPreviewPanel.zoomActual();
      myActionToolBar.updateActionsImmediately();
    }
  }

  private class ZoomToFitAction extends ToggleAction {
    ZoomToFitAction() {
      super(AndroidBundle.message("android.layout.preview.zoom.to.fit.action.text"), null, AndroidIcons.ZoomFit);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myPreviewPanel.isZoomToFit();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myPreviewPanel.setZoomToFit(state);
      myActionToolBar.updateActionsImmediately();
    }
  }

  private class RefreshAction extends AnAction {
    RefreshAction() {
      super(AndroidBundle.message("android.layout.preview.refresh.action.text"), null, AllIcons.Actions.Refresh);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myToolWindowManager.render();
    }
  }
}
