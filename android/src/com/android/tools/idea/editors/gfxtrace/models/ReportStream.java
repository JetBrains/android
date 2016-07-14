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
package com.android.tools.idea.editors.gfxtrace.models;

import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.android.tools.idea.editors.gfxtrace.UiErrorCallback;
import com.android.tools.idea.editors.gfxtrace.service.ErrDataUnavailable;
import com.android.tools.idea.editors.gfxtrace.service.Report;
import com.android.tools.idea.editors.gfxtrace.service.path.CapturePath;
import com.android.tools.idea.editors.gfxtrace.service.path.PathListener;
import com.android.tools.idea.editors.gfxtrace.service.path.PathStore;
import com.android.tools.idea.editors.gfxtrace.service.path.ReportPath;
import com.android.tools.rpclib.rpccore.Rpc;
import com.android.tools.rpclib.rpccore.RpcException;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

public class ReportStream implements PathListener {
  private static final Logger LOG = Logger.getInstance(ReportStream.class);

  private final GfxTraceEditor myEditor;
  private final PathStore<ReportPath> myReportPath = new PathStore<>();
  private Report myReport;

  private final Listeners myListeners = new Listeners();

  public ReportStream(GfxTraceEditor editor) {
    myEditor = editor;
  }

  @Override
  public void notifyPath(PathEvent event) {
    if (myReportPath.updateIfNotNull(CapturePath.report(event.findCapturePath()))) {
      myListeners.onReportLoadingStart(this);
      ListenableFuture<Object> future = myEditor.getClient().get(myReportPath.getPath());
      Rpc.listen(future, LOG, new UiErrorCallback<Object, Report, String>() {
        @Override
        protected ResultOrError<Report, String> onRpcThread(Rpc.Result<Object> result) throws RpcException, ExecutionException {
          try {
            return success((Report)result.get());
          }
          catch (ErrDataUnavailable e) {
            return error(e.getMessage());
          }
        }

        @Override
        protected void onUiThreadSuccess(Report result) {
          update(result);
        }

        @Override
        protected void onUiThreadError(String error) {
          update(error);
        }
      });
    }
  }

  public Report getReport() {
    return myReport;
  }

  @Nullable("path has not been updated yet")
  public ReportPath getPath() {
    return myReportPath.getPath();
  }

  public boolean isLoaded() {
    return myReport != null;
  }

  public void addListener(Listener listener) {
    myListeners.add(listener);
  }

  public void removeListener(Listener listener) {
    myListeners.remove(listener);
  }

  private void update(Report report) {
    myReport = report;
    myListeners.onReportLoadingSuccess(this);
  }

  private void update(String errorMessage) {
    myReport = null;
    myListeners.onReportLoadingFailure(this, errorMessage);
  }

  public interface Listener {
    void onReportLoadingStart(ReportStream reportStream);

    void onReportLoadingFailure(ReportStream reportStream, String errorMessage);

    void onReportLoadingSuccess(ReportStream reportStream);
  }

  private static class Listeners extends ArrayList<Listener> implements Listener {
    @Override
    public void onReportLoadingStart(ReportStream reportStream) {
      for (Listener listener : toArray(new Listener[size()])) {
        listener.onReportLoadingStart(reportStream);
      }
    }

    @Override
    public void onReportLoadingFailure(ReportStream reportStream, String errorMessage) {
      for (Listener listener : toArray(new Listener[size()])) {
        listener.onReportLoadingFailure(reportStream, errorMessage);
      }
    }

    @Override
    public void onReportLoadingSuccess(ReportStream reportStream) {
      for (Listener listener : toArray(new Listener[size()])) {
        listener.onReportLoadingSuccess(reportStream);
      }
    }
  }
}
