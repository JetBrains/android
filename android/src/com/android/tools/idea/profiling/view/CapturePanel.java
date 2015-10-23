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
package com.android.tools.idea.profiling.view;

import com.android.tools.perflib.analyzer.AnalysisReport;
import com.android.tools.perflib.analyzer.AnalyzerTask;
import com.intellij.designer.DesignerEditorPanelFacade;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.impl.status.InlineProgressIndicator;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Set;

public class CapturePanel extends JPanel implements DesignerEditorPanelFacade {
  @NotNull private Project myProject;
  @NotNull private CaptureEditor myEditor;
  @NotNull private AnalyzerTask[] myTasks;
  @NotNull private ThreeComponentsSplitter myThreeComponentsSplitter;
  @Nullable private InlineProgressIndicator myProgressIndicator;
  @Nullable private AnalysisContentsDelegate myResultsDelegate;
  @Nullable private Runnable myFinishedLoadingCallback;

  public CapturePanel(@NotNull Project project, @NotNull CaptureEditor editor, @NotNull AnalyzerTask[] tasks, boolean startAsLoading) {
    myProject = project;
    myEditor = editor;
    myTasks = tasks;

    myThreeComponentsSplitter = new ThreeComponentsSplitter(false);
    myThreeComponentsSplitter.setHonorComponentsMinimumSize(true);
    Disposer.register(editor, myThreeComponentsSplitter);

    if (startAsLoading) {
      TaskInfo taskInfo = new TaskInfo() {
        @NotNull
        @Override
        public String getTitle() {
          return "";
        }

        @Override
        public String getCancelText() {
          return null;
        }

        @Override
        public String getCancelTooltipText() {
          return null;
        }

        @Override
        public boolean isCancellable() {
          return false;
        }

        @Override
        public String getProcessId() {
          return null;
        }
      };

      myProgressIndicator = new InlineProgressIndicator(true, taskInfo) {
        @Override
        protected void queueRunningUpdate(Runnable update) {
          ApplicationManager.getApplication().invokeLater(update);
        }
      };

      setLayout(new GridBagLayout());
      add(myProgressIndicator.getComponent());
    }
  }

  /**
   * Stops loading indicator and sets the main component of the panel. Call this after all required data have been initialized and the main
   * panel is ready to be shown. Call this only once.
   *
   * @param editorPanel              the main editor panel to show
   * @param delegate                 the delegate that is responsible for the contents of the analysis light tool window
   */
  public void setEditorPanel(@NotNull final JComponent editorPanel,
                             @NotNull final AnalysisContentsDelegate delegate) {
    assert !isDoneLoading();
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        removeAll();
        myProgressIndicator = null;
        myResultsDelegate = delegate;

        setLayout(new BorderLayout());
        myThreeComponentsSplitter.setInnerComponent(editorPanel);
        AnalysisResultsManager.getInstance(myProject).bind(CapturePanel.this);
        add(myThreeComponentsSplitter, BorderLayout.CENTER);

        if (myFinishedLoadingCallback != null) {
          myFinishedLoadingCallback.run();
          myFinishedLoadingCallback = null;
        }
      }
    });
  }

  @Override
  public ThreeComponentsSplitter getContentSplitter() {
    return myThreeComponentsSplitter;
  }

  @Nullable
  public InlineProgressIndicator getProgressIndicator() {
    return myProgressIndicator;
  }

  public boolean isDoneLoading() {
    return myProgressIndicator == null;
  }

  @NotNull
  public AnalyzerTask[] getAnalyzerTasks() {
    return myTasks;
  }

  @NotNull
  public AnalysisReport performAnalysis(Set<? extends AnalyzerTask> tasks, @NotNull Set<AnalysisReport.Listener> listeners) {
    return myEditor.performAnalysis(tasks, listeners);
  }

  @NotNull
  public AnalysisContentsDelegate getContentsDelegate() {
    assert myResultsDelegate != null;
    return myResultsDelegate;
  }

  @NotNull
  public CaptureEditor getEditor() {
    return myEditor;
  }

  public void runWhenFinishedLoading(@NotNull Runnable callback) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myFinishedLoadingCallback = callback;
    if (isDoneLoading()) {
      myFinishedLoadingCallback.run();
      myFinishedLoadingCallback = null;
    }
  }
}
