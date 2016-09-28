/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.editors.hprof;

import com.android.tools.idea.ddms.EdtExecutor;
import com.android.tools.idea.editors.hprof.views.HprofAnalysisContentsDelegate;
import com.android.tools.idea.profiling.view.CaptureEditor;
import com.android.tools.idea.profiling.view.CapturePanel;
import com.android.tools.perflib.analyzer.AnalysisReport;
import com.android.tools.perflib.analyzer.AnalyzerTask;
import com.android.tools.perflib.analyzer.CaptureGroup;
import com.android.tools.perflib.captures.MemoryMappedFileBuffer;
import com.android.tools.perflib.heap.Snapshot;
import com.android.tools.perflib.heap.analysis.ComputationProgress;
import com.android.tools.perflib.heap.memoryanalyzer.DuplicatedStringsAnalyzerTask;
import com.android.tools.perflib.heap.memoryanalyzer.LeakedActivityAnalyzerTask;
import com.android.tools.perflib.heap.memoryanalyzer.MemoryAnalyzer;
import com.google.common.base.Throwables;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.designer.DesignerEditorPanelFacade;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.status.InlineProgressIndicator;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.Set;
import java.util.concurrent.Executors;

public class HprofEditor extends CaptureEditor {
  @NotNull private static final Logger LOG = Logger.getInstance(HprofEditor.class);
  @Nullable private HprofView myView;
  private Snapshot mySnapshot;
  private boolean myIsValid = true;

  public HprofEditor(@NotNull final Project project, @NotNull final VirtualFile file) {
    AnalyzerTask[] tasks = new AnalyzerTask[]{new LeakedActivityAnalyzerTask(), new DuplicatedStringsAnalyzerTask()};
    myPanel = new CapturePanel(project, this, tasks, true);

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        final File hprofFile = VfsUtilCore.virtualToIoFile(file);
        final InlineProgressIndicator indicator = myPanel.getProgressIndicator();
        assert indicator != null;
        Timer timer = null;

        try {
          updateIndicator(indicator, 0.01, "Parsing hprof file...");
          mySnapshot = Snapshot.createSnapshot(new MemoryMappedFileBuffer(hprofFile));

          // Refresh the timer at 30fps (33ms/frame).
          timer = new Timer(1000 / 30, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
              Snapshot.DominatorComputationStage stage = mySnapshot.getDominatorComputationStage();
              ComputationProgress progress = mySnapshot.getComputationProgress();
              updateIndicator(indicator, Snapshot.DominatorComputationStage.toAbsoluteProgressPercentage(stage, progress),
                              progress.getMessage());
            }
          });
          timer.start();
          mySnapshot.computeDominators();
        }
        catch (Throwable throwable) {
          LOG.info(throwable);
          //noinspection ThrowableResultOfMethodCallIgnored
          final String errorMessage = "Unexpected error while processing hprof file: " + Throwables.getRootCause(throwable).getMessage();
          indicator.cancel();
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              Messages.showErrorDialog(project, errorMessage, getName());
            }
          });
        }
        finally {
          if (timer != null) {
            timer.stop();
          }
          if (mySnapshot != null) {
            myView = new HprofView(project, HprofEditor.this, mySnapshot);
            HprofAnalysisContentsDelegate delegate = new HprofAnalysisContentsDelegate(HprofEditor.this);
            myPanel.setEditorPanel(myView.getComponent(), delegate);

            Disposer.register(HprofEditor.this, myView);
            Disposer.register(HprofEditor.this, delegate);
          }
        }
      }
    });
  }

  @Nullable
  public HprofView getView() {
    return myView;
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  @NotNull
  @Override
  public String getName() {
    return "HprofView";
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
  }

  @Override
  public boolean isModified() {
    return false;
  }

  public void setInvalid() {
    myIsValid = false;
  }

  @Override
  public boolean isValid() {
    // TODO: handle deletion of the underlying file?
    return myIsValid;
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

  @Nullable
  @Override
  public StructureViewBuilder getStructureViewBuilder() {
    return null;
  }

  @Override
  public void dispose() {
    mySnapshot.dispose();
    mySnapshot = null;
    myPanel = null;
    myIsValid = false;
  }

  @NotNull
  @Override
  public DesignerEditorPanelFacade getFacade() {
    return myPanel;
  }

  @NotNull
  @Override
  public AnalysisReport performAnalysis(@NotNull Set<? extends AnalyzerTask> tasks, @NotNull Set<AnalysisReport.Listener> listeners) {
    assert mySnapshot != null;
    CaptureGroup captureGroup = new CaptureGroup();
    captureGroup.addCapture(mySnapshot);

    MemoryAnalyzer memoryAnalyzer = new MemoryAnalyzer();
    assert memoryAnalyzer.accept(captureGroup);

    // TODO change this back to PooledThreadExecutor.INSTANCE once multi-reader problem has been solved in Snapshot
    return memoryAnalyzer.analyze(captureGroup, listeners, tasks, EdtExecutor.INSTANCE, Executors.newSingleThreadExecutor());
  }

  private static void updateIndicator(@NotNull final InlineProgressIndicator indicator, final double fraction, @NotNull final String text) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        indicator.setFraction(fraction);
        indicator.setText(text);
      }
    });
  }
}
