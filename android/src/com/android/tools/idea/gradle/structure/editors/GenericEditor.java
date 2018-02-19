/*
 * Copyright 2004-2005 Alexey Efimov
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
package com.android.tools.idea.gradle.structure.editors;

import com.android.tools.idea.structure.EditorPanel;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModuleConfigurationEditor;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.concurrent.Callable;

/**
 * A class which wraps an {@link EditorPanel} while providing an implementation for
 * {@link ModuleConfigurationEditor}. The wrapped panel shows up in the right-hand side of the
 * Project Structure dialog and provides a way to edit some subset of a module's properties.
 */
public class GenericEditor<E extends EditorPanel> implements ModuleConfigurationEditor {
  private static final Logger LOG = Logger.getInstance(GenericEditor.class);

  private final String myName;
  private E myPanel;
  private final Callable<E> myPanelFactory;

  public GenericEditor(String name, Callable<E> panelFactory) {
    myName = name;
    myPanelFactory = panelFactory;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    try {
      myPanel = myPanelFactory.call();
    } catch (Exception e) {
      LOG.error("Error while creating dialog", e);
    }
    return myPanel;
  }

  @Override
  public boolean isModified() {
    return myPanel != null && myPanel.isModified();
  }

  @Override
  @Nullable
  public String getHelpTopic() {
    return null;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return myName;
  }

  @Override
  public void apply() throws ConfigurationException {
    CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
      @Override
      public void run() {
        try {
          WriteAction.run(myPanel::apply);
        }
        catch (Exception e) {
          LOG.error("Error while applying changes", e);
        }
      }
    });
  }

  @Override
  public void reset() {
  }

  @Override
  public void disposeUIResources() {
    myPanel = null;
  }

}
