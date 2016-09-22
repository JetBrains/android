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
import com.android.tools.idea.editors.gfxtrace.service.ReportItem;
import com.android.tools.idea.editors.gfxtrace.service.log.LogProtos;
import com.android.tools.idea.editors.gfxtrace.service.path.*;
import com.android.tools.rpclib.multiplex.Channel;
import com.android.tools.rpclib.rpccore.Rpc;
import com.android.tools.rpclib.rpccore.RpcException;
import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Range;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

public class ReportStream implements PathListener {
  private static final Logger LOG = Logger.getInstance(ReportStream.class);

  @NotNull private final GfxTraceEditor myEditor;
  @NotNull private final PathStore<ReportPath> myReportPath = new PathStore<>();
  @NotNull private final PathStore<DevicePath> myDevicePath = new PathStore<>();
  @NotNull private final PathStore<ReportItemPath> myReportItemPath = new PathStore<>();
  @Nullable("no report has been fetched yet") private Report myReport;

  @NotNull private final Listeners myListeners = new Listeners();

  public ReportStream(@NotNull GfxTraceEditor editor) {
    myEditor = editor;
  }

  @Override
  public void notifyPath(PathEvent event) {
    // TODO: What if we want to deselect a device?
    myDevicePath.updateIfNotNull(event.findDevicePath());

    if (!myEditor.getFeatures().hasReport()) {
      return;
    }
    if (myReportPath.updateIfNotNull(CapturePath.report(event.findCapturePath(), myDevicePath.getPath()))) {
      myListeners.onReportLoadingStart(this);
      ListenableFuture<Object> future = myEditor.getClient().get(myReportPath.getPath());
      Rpc.listen(future, new UiErrorCallback<Object, Report, String>(myEditor, LOG) {
        @Override
        protected ResultOrError<Report, String> onRpcThread(Rpc.Result<Object> result)
          throws RpcException, ExecutionException, Channel.NotConnectedException {
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

    if (myReportItemPath.updateIfNotNull(event.findReportItemPath()) && myReport != null) {
      myListeners.onReportItemSelected(myReport.getItems()[(int)myReportItemPath.getPath().getIndex()]);
    }
  }

  /**
   * Computes the most critical severity level among multiple reports
   */
  public LogProtos.Severity maxSeverity(Range<Integer> reportItemIndices) {
    LogProtos.Severity maxSeverity = LogProtos.Severity.Debug;
    if (myReport != null) {
      for (int index : ContiguousSet.create(reportItemIndices, DiscreteDomain.integers())) {
        ReportItem item = myReport.getItems()[index];
        LogProtos.Severity itemSeverity = item.getSeverity();
        if (maxSeverity.compareTo(itemSeverity) > 0) {
          maxSeverity = itemSeverity;
        }
        if (maxSeverity == LogProtos.Severity.Emergency) {
          break;
        }
      }
    }
    return maxSeverity;
  }

  public ReportItemPath getReportItemPath(long atomId) {
    return myReport == null ? null : buildReportItemPath(myReport.getForAtom(atomId));
  }

  public ReportItemPath getReportItemPath(long atomGroupStartId, long atomGroupLastId) {
    return myReport == null ? null : buildReportItemPath(myReport.getForAtoms(atomGroupStartId, atomGroupLastId));
  }

  /**
   * As for now builds single path for the first item for given atom / group
   */
  private ReportItemPath buildReportItemPath(@Nullable Range<Integer> reportItemIndices) {
    if (reportItemIndices != null && !reportItemIndices.isEmpty()) {
      ReportItemPath path = new ReportItemPath();
      path.setIndex(reportItemIndices.lowerEndpoint());
      path.setReport(myReportPath.getPath());
      return path;
    }
    return null;
  }

  public boolean hasReportItems(long atomId) {
    if (myReport == null) {
      return false;
    }
    Range<Integer> reportItemIndices = myReport.getForAtom(atomId);
    return reportItemIndices != null && !reportItemIndices.isEmpty();
  }

  @Nullable
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

    void onReportItemSelected(ReportItem reportItem);
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

    @Override
    public void onReportItemSelected(ReportItem reportItem) {
      for (Listener listener : toArray(new Listener[size()])) {
        listener.onReportItemSelected(reportItem);
      }
    }
  }
}
