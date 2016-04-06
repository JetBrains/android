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
package com.android.tools.idea.gradle.structure.daemon;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.daemon.analysis.PsAndroidModuleAnalyzer;
import com.android.tools.idea.gradle.structure.daemon.analysis.PsModelAnalyzer;
import com.android.tools.idea.gradle.structure.model.PsIssueCollection;
import com.android.tools.idea.gradle.structure.model.PsModel;
import com.google.common.collect.Maps;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.RunResult;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Alarm.ThreadToUse;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.EventListener;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.util.ui.update.MergingUpdateQueue.ANY_COMPONENT;

public class PsDaemonAnalyzer implements Disposable {
  private static final Logger LOG = Logger.getInstance(PsDaemonAnalyzer.class);

  @NotNull private final PsContext myContext;

  @NotNull private final MergingUpdateQueue myAnalyzerQueue;
  @NotNull private final MergingUpdateQueue myResultsUpdaterQueue;
  @NotNull private final AtomicBoolean myStopped;
  @NotNull private final PsIssueCollection myIssues;

  @NotNull private final Map<Class<?>, PsModelAnalyzer<?>> myModelAnalyzers = Maps.newHashMap();

  @NotNull private final EventDispatcher<IssuesUpdatedListener> myIssuesUpdatedEventDispatcher =
    EventDispatcher.create(IssuesUpdatedListener.class);

  public PsDaemonAnalyzer(@NotNull PsContext context) {
    myContext = context;
    Disposer.register(context, this);

    myAnalyzerQueue = createQueue("Project Structure Daemon Analyzer", null);
    myResultsUpdaterQueue = createQueue("Project Structure Analysis Results Updater", ANY_COMPONENT);
    myStopped = new AtomicBoolean(false);
    myIssues = new PsIssueCollection(myContext);

    createModelAnalyzers();
  }

  @NotNull
  private MergingUpdateQueue createQueue(@NotNull String name, @Nullable JComponent modalityStateComponent) {
    return new MergingUpdateQueue(name, 300, false, modalityStateComponent, this, null, ThreadToUse.POOLED_THREAD);
  }

  private void createModelAnalyzers() {
    add(new PsAndroidModuleAnalyzer(myContext));
  }

  private void add(@NotNull PsModelAnalyzer<? extends PsModel> analyzer) {
    myModelAnalyzers.put(analyzer.getSupportedModelType(), analyzer);
  }

  public void add(@NotNull IssuesUpdatedListener listener, @NotNull Disposable parentDisposable) {
    myIssuesUpdatedEventDispatcher.addListener(listener, parentDisposable);
  }

  public void queueUpdate(@NotNull PsModel model) {
    myAnalyzerQueue.queue(new AnalyzeModelUpdate(model));
  }

  private void doCheck(@NotNull PsModel model) {
    PsModelAnalyzer<?> analyzer = myModelAnalyzers.get(model.getClass());
    if (analyzer == null) {
      LOG.info("Failed to find analyzer for model of type " + model.getClass().getName());
      return;
    }
    RunResult<ActionCallback> result = new ReadAction<ActionCallback>() {
      @Override
      protected void run(@NotNull Result<ActionCallback> result) throws Throwable {
        if (isStopped()) {
          return;
        }
        analyzer.analyze(model, myIssues);
        result.setResult(ActionCallback.DONE);
      }
    }.execute();
    result.getResultObject().doWhenDone(() -> myResultsUpdaterQueue.queue(new PsIssuesComputedUpdate(model)));
  }

  public void reset() {
    reset(myAnalyzerQueue, myResultsUpdaterQueue);
    myAnalyzerQueue.queue(new Update("reset") {
      @Override
      public void run() {
        myStopped.set(false);
      }
    });
  }

  private static void reset(@NotNull MergingUpdateQueue... queues) {
    for (MergingUpdateQueue queue : queues) {
      queue.activate();
    }
  }

  public void stop() {
    myStopped.set(true);
    stop(myAnalyzerQueue, myResultsUpdaterQueue);
    clearCaches();
  }

  private static void stop(@NotNull MergingUpdateQueue... queues) {
    for (MergingUpdateQueue queue : queues) {
      queue.cancelAllUpdates();
      queue.deactivate();
    }
  }

  private void clearCaches() {

  }

  private boolean isStopped() {
    return myStopped.get();
  }

  @NotNull
  public PsIssueCollection getIssues() {
    return myIssues;
  }

  @Override
  public void dispose() {
    stop();
  }

  private class AnalyzeModelUpdate extends Update {
    @NotNull private final PsModel myModel;

    AnalyzeModelUpdate(@NotNull PsModel model) {
      super(model);
      myModel = model;
    }

    @Override
    public void run() {
      try {
        doCheck(myModel);
      }
      catch (Throwable e) {
        LOG.error("Failed to analyze " + myModel, e);
      }
    }
  }

  private class PsIssuesComputedUpdate extends Update {
    @NotNull private final PsModel myModel;

    public PsIssuesComputedUpdate(@NotNull PsModel model) {
      super(model);
      myModel = model;
    }

    @Override
    public void run() {
      if (isStopped()) {
        return;
      }
      myIssuesUpdatedEventDispatcher.getMulticaster().issuesUpdated(myModel);
    }
  }

  public interface IssuesUpdatedListener extends EventListener {
    void issuesUpdated(@NotNull PsModel model);
  }
}
