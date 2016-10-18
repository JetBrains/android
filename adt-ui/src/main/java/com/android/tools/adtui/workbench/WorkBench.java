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
package com.android.tools.adtui.workbench;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

/**
 * Provides a work area with 1 or more {@link ToolWindowDefinition}s.
 * Each {@link ToolContent} represents a tool that can manipulate the data in the {@link WorkBench}.
 * There can be up to 4 visible {@link ToolWindowDefinition}s at any given time:<br/>
 * <pre>
 *     +-+-----+---------------+-----+-+
 *     | |  A  |               |  C  |F|
 *     | +-----+   WorkBench   +-----+ |
 *     |E|  B  |               |  D  | |
 *     +-+-----+---------------+-----+-+
 * </pre>
 * In the diagram the {@link WorkBench} has 4 visible {@link ToolWindowDefinition}s: A & B on the left side and
 * C & D on the right side. The {@link ToolWindowDefinition} on the bottom are referred to as split windows.<br/><br/>
 *
 * When a {@link ToolWindowDefinition} is not visible a button with its name is shown in narrow side panel. The
 * buttons will restore the tool in a visible state. In the diagram E & F represent such buttons.
 *
 * @param <T> Specifies the type of data controlled by this {@link WorkBench}.
 */
public class WorkBench<T> extends JPanel implements Disposable {
  private final Project myProject;
  private final String myName;
  private final FileEditor myFileEditor;
  private JComponent myContent;
  private List<ToolWindowDefinition<T>> myDefinitions;
  private T myContext;

  /**
   * Creates a work space with associated tool windows, which can be attached.
   *
   * @param project the project associated with this work space.
   * @param name a name used to identify this type of {@link WorkBench}. Also used for associating properties.
   * @param fileEditor the file editor this work space is associated with.
   */
  public WorkBench(@NotNull Project project, @NotNull String name, @NotNull FileEditor fileEditor) {
    myProject = project;
    myName = name;
    myFileEditor = fileEditor;
  }

  /**
   * Initializes a {@link WorkBench} with content, context and tool windows.
   *
   * @param content the content of the main area of the {@link WorkBench}
   * @param context an instance identifying the data the {@link WorkBench} is manipulating
   * @param definitions a list of tool windows associated with this {@link WorkBench}
   */
  public void init(@NotNull JComponent content,
                   @NotNull T context,
                   @NotNull List<ToolWindowDefinition<T>> definitions) {
    myContent = content;
    myDefinitions = definitions;
    myContext = context;
  }

  @Override
  public void dispose() {
  }
}
