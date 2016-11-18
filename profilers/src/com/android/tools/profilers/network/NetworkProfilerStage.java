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
package com.android.tools.profilers.network;

import com.android.tools.profilers.AspectModel;
import com.android.tools.profilers.Stage;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profiler.proto.NetworkProfiler.HttpDetailsResponse.Request;
import com.android.tools.profiler.proto.NetworkProfiler.HttpDetailsResponse.Response;
import com.android.tools.profiler.proto.NetworkProfiler.HttpDetailsResponse.Body;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NetworkProfilerStage extends Stage {

  // Whether the connection data screen is active.
  private boolean myConnectionDataEnabled;

  // If null, means no connection to show in the details pane.
  @Nullable
  private HttpData myConnection;

  public AspectModel<NetworkProfilerAspect> aspect = new AspectModel<>();

  public NetworkProfilerStage(StudioProfilers profiler) {
    super(profiler);
  }

  @Override
  public void enter() {

  }

  @Override
  public void exit() {

  }

  public NetworkRequestsModel getRequestsModel() {
    //return new FakeNetworkRequestsModel();
    return new RpcNetworkRequestsModel(getStudioProfilers().getClient().getNetworkClient(), getStudioProfilers().getProcessId());
  }

  public void setEnableConnectionData(boolean enable) {
    myConnectionDataEnabled = enable;
    aspect.changed(NetworkProfilerAspect.REQUEST_DETAILS);
  }

  /**
   * Sets the active connection, or clears the previously selected active connection if given data is null.
   */
  public void setConnection(@Nullable HttpData connection) {
    myConnection = connection;
    aspect.changed(NetworkProfilerAspect.REQUEST_DETAILS);
  }

  /**
   * Gets the details of the current connection, or null if none.
   */
  public ConnectionDetails getConnectionDetails() {
    if (myConnection == null) {
      return null;
    }
    // TODO: Fetch the data via RPC
    return new ConnectionDetails(
        Request.getDefaultInstance(),
        Response.newBuilder().setCode("404").build(),
        Body.getDefaultInstance());
  }

  public boolean isConnectionDataEnabled() {
    return myConnectionDataEnabled;
  }

  /**
   * Returns the active connection, or {@code null} if no request is currently selected.
   */
  @Nullable
  public HttpData getConnection() {
    return myConnection;
  }

  private static class ConnectionDetails {
    public final Request request;
    public final Response response;
    public final Body body;

    private ConnectionDetails(Request request, Response response, Body body) {
      this.request = request;
      this.response = response;
      this.body = body;
    }
  }
}
