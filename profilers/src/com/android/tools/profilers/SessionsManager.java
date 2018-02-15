/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers;

import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.model.AspectModel;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Profiler.*;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A wrapper class for keeping track of the list of sessions that the profilers have seen, along with their associated artifacts (e.g.
 * memory heap dump, CPU capture)
 */
public class SessionsManager extends AspectModel<SessionAspect> {

  @NotNull private final StudioProfilers myProfilers;

  /**
   * A map of Session's Id -> Sessions.
   */
  @NotNull private Map<Long, Common.Session> mySessions;

  /**
   * The currently selected session.
   */
  @NotNull private Common.Session mySession;

  public SessionsManager(@NotNull StudioProfilers profilers) {
    myProfilers = profilers;
    mySession = Common.Session.getDefaultInstance();
    mySessions = new HashMap<>();
  }

  @NotNull
  public Map<Long, Common.Session> getSessions() {
    return mySessions;
  }

  @NotNull
  public Common.Session getSession() {
    return mySession;
  }

  public boolean isSessionAlive() {
    return mySession.getEndTimestamp() == Long.MAX_VALUE;
  }

  /**
   * Perform an update to retrieve all session instances.
   */
  public void update() {
    GetSessionsResponse sessionsResponse = myProfilers.getClient().getProfilerClient().getSessions(GetSessionsRequest.getDefaultInstance());
    Map<Long, Common.Session> sessions =
      sessionsResponse.getSessionsList().stream().collect(Collectors.toMap(Common.Session::getSessionId, Function.identity()));
    if (!sessions.equals(mySessions)) {
      mySessions = sessions;
      changed(SessionAspect.SESSIONS);
    }
  }

  /**
   * Request to begin a new session using the input device and process.
   */
  public void beginSession(@Nullable Common.Device device, @Nullable Common.Process process) {
    // We currently don't support more than one ongoing session at a time.
    assert !isSessionAlive();

    if (device == null || process == null) {
      mySession = Common.Session.getDefaultInstance();
      changed(SessionAspect.SELECTED_SESSION);
      return;
    }

    // TODO this part is currently only for backward compatibility
    // Once we switched to the new device+process dropdown (b/67509466), we should not see offline device and process anymore.
    if ((device != null && device.getState() != Common.Device.State.ONLINE) ||
        (process != null && process.getState() != Common.Process.State.ALIVE)) {
      return;
    }

    BeginSessionRequest.Builder requestBuilder = BeginSessionRequest.newBuilder()
      .setDeviceId(device.getDeviceId())
      .setPid(process.getPid())
      .setSessionName(buildSessionName(device, process))
      .setRequestTimeEpochMs(System.currentTimeMillis());
    // Attach agent for advanced profiling if JVMTI is enabled
    if (device.getFeatureLevel() >= AndroidVersion.VersionCodes.O &&
        myProfilers.getIdeServices().getFeatureConfig().isJvmtiAgentEnabled()) {
      // If an agent has been previously attached, Perfd will only re-notify the existing agent of the updated grpc target instead
      // of re-attaching an agent. See ProfilerService::AttachAgent on the Perfd side for more details.
      requestBuilder.setJvmtiConfig(BeginSessionRequest.JvmtiConfig.newBuilder()
                                      .setAttachAgent(true)
                                      .setAgentLibFileName(String.format("libperfa_%s.so", process.getAbiCpuArch()))
                                      .build());
    }

    BeginSessionResponse response = myProfilers.getClient().getProfilerClient().beginSession(requestBuilder.build());
    mySession = response.getSession();
    mySessions.put(mySession.getSessionId(), mySession);
    changed(SessionAspect.SELECTED_SESSION);
    changed(SessionAspect.SESSIONS);
  }

  /**
   * Request to end the currently active session if there is one.
   */
  public void endCurrentSession() {
    if (!isSessionAlive()) {
      return;
    }

    EndSessionResponse response = myProfilers.getClient().getProfilerClient().endSession(EndSessionRequest.newBuilder()
                                                                                           .setDeviceId(mySession.getDeviceId())
                                                                                           .setSessionId(mySession.getSessionId())
                                                                                           .build());
    mySession = response.getSession();
    mySessions.put(mySession.getSessionId(), mySession);
    changed(SessionAspect.SELECTED_SESSION);
    changed(SessionAspect.SESSIONS);
  }

  @NotNull
  private String buildSessionName(@NotNull Common.Device device, @NotNull Common.Process process) {
    StringBuilder deviceNameBuilder = new StringBuilder();
    String manufacturer = device.getManufacturer();
    String model = device.getModel();
    String serial = device.getSerial();
    String suffix = String.format("-%s", serial);
    if (model.endsWith(suffix)) {
      model = model.substring(0, model.length() - suffix.length());
    }
    if (!StringUtil.isEmpty(manufacturer)) {
      deviceNameBuilder.append(manufacturer);
      deviceNameBuilder.append(" ");
    }
    deviceNameBuilder.append(model);

    return String.format("%s (%s)", process.getName(), deviceNameBuilder.toString());
  }
}
