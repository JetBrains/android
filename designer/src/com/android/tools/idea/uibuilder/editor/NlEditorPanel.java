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

import com.android.tools.adtui.workbench.*;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationHolder;
import com.android.tools.idea.uibuilder.mockup.Mockup;
import com.android.tools.idea.uibuilder.mockup.editor.MockupToolDefinition;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.palette.NlPaletteDefinition;
import com.android.tools.idea.uibuilder.property.NlPropertyPanelDefinition;
import com.android.tools.idea.uibuilder.structure.NlComponentTreeDefinition;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Assembles a designer editor from various components
 */
public class NlEditorPanel extends WorkBench<DesignSurface> {
  private final XmlFile myFile;
  private final DesignSurface mySurface;

  public NlEditorPanel(@NotNull NlEditor editor, @NotNull Project project, @NotNull AndroidFacet facet, @NotNull VirtualFile file) {
    super(project, "NELE_EDITOR", editor);
    setOpaque(true);

    myFile = (XmlFile)AndroidPsiUtils.getPsiFileSafely(project, file);
    assert myFile != null : file;

    mySurface = new DesignSurface(project, false);
    Disposer.register(editor, mySurface);
    NlModel model = NlModel.create(mySurface, editor, facet, myFile);
    mySurface.setModel(model);

    JPanel contentPanel = new JPanel(new BorderLayout());
    JComponent toolbarComponent = mySurface.getActionManager().createToolbar(model);
    contentPanel.add(toolbarComponent, BorderLayout.NORTH);
    contentPanel.add(mySurface);

    List<ToolWindowDefinition<DesignSurface>> tools = new ArrayList<>(4);
    tools.add(new NlPaletteDefinition(project, Side.LEFT, Split.TOP, AutoHide.DOCKED));
    tools.add(new NlComponentTreeDefinition(Side.LEFT, Split.BOTTOM, AutoHide.DOCKED));
    tools.add(new NlPropertyPanelDefinition(project, Side.RIGHT, Split.TOP, AutoHide.DOCKED));
    if (Mockup.ENABLE_FEATURE) {
      tools.add(new MockupToolDefinition(Side.RIGHT, Split.TOP, AutoHide.AUTO_HIDE));
    }

    init(contentPanel, mySurface, tools);
  }

  @Nullable
  public JComponent getPreferredFocusedComponent() {
    return mySurface.getPreferredFocusedComponent();
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

  @NotNull
  public DesignSurface getSurface() {
    return mySurface;
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
  }
}
