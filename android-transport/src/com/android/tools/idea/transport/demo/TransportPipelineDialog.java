/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.transport.demo;

import static com.android.tools.profiler.proto.Commands.Command.CommandType.ECHO;

import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.model.stdui.CommonAction;
import com.android.tools.adtui.stdui.menu.CommonDropDownButton;
import com.android.tools.idea.transport.TransportClient;
import com.android.tools.idea.transport.TransportFileManager;
import com.android.tools.idea.transport.TransportService;
import com.android.tools.idea.transport.poller.TransportEventListener;
import com.android.tools.idea.transport.poller.TransportEventPoller;
import com.android.tools.pipeline.example.proto.Echo;
import com.android.tools.profiler.proto.Commands;
import com.android.tools.profiler.proto.Commands.Command;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Transport;
import com.google.common.collect.ImmutableMap;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TransportPipelineDialog extends DialogWrapper {
  static final String TITLE = "Transport Pipeline";
  private static final String NO_DEVICES_DETECTED = "No devices detected";
  private static final String NO_DEBUGGABLE_PROCESSES = "No debuggable processes detected";

  private JPanel myRootPanel;

  private static final Map<Command.CommandType, Command.Builder> SUPPORTED_COMMANDS = ImmutableMap.of(
    ECHO, Command.newBuilder().setType(ECHO).setEchoData(Echo.EchoData.newBuilder().setData("Hello World"))
  );

  @NotNull private final CommonAction myProcessSelectionAction;
  @NotNull private final CommonDropDownButton myProcessSelectionDropDown;
  @NotNull private final JLabel myProcessAgentStatus;
  @NotNull private final ComboBox<Command.CommandType> myCommandComboBox;
  @NotNull private final JButton mySendCommandButton;
  @NotNull private final ComboBox<Common.Event.Kind> myEventFilter;
  @NotNull private final JBTextArea myEventLog;

  @Nullable private final TransportClient myClient;
  @Nullable private Common.Stream mySelectedStream = Common.Stream.getDefaultInstance();
  @Nullable private Common.Process mySelectedProcess = Common.Process.getDefaultInstance();
  @NotNull private final Map<Long, List<Common.Process>> myProcessesMap;
  @NotNull private final Map<Long, Common.Stream> myStreamIdMap;
  @NotNull private final Map<Long, Common.Process> myProcessIdMap;

  @NotNull private final TransportEventPoller myTransportEventPoller;
  private TransportEventListener mySelectedEventListener;
  private TransportEventListener myAgentStatusListener;

  private long getSelectedStreamId() {
    return mySelectedStream.getStreamId();
  }

  private int getSelectedProcessId() {
    return mySelectedProcess.getPid();
  }

  public TransportPipelineDialog(@Nullable Project project) {
    super(project);
    setTitle(TITLE);
    setModal(false);

    // The following line initializes the transport service as a side-effect (or is a no-op if already initialized).
    // If we don't do this, calls to blocking gRPC stubs later will hang forever.
    TransportService.getInstance();

    myClient = new TransportClient(TransportService.getChannelName());

    myProcessSelectionAction = new CommonAction("Select Process", AllIcons.General.Add);
    myProcessSelectionDropDown = new CommonDropDownButton(myProcessSelectionAction);
    myProcessSelectionDropDown.setToolTipText("Select a process to connect to.");
    myProcessAgentStatus = new JLabel("");

    myCommandComboBox = new ComboBox<>();
    for (Command.CommandType type : SUPPORTED_COMMANDS.keySet()) {
      myCommandComboBox.addItem(type);
    }
    mySendCommandButton = new JButton("Send Command");
    mySendCommandButton.addActionListener(e -> {
      Command.CommandType selectedCommand = (Command.CommandType)myCommandComboBox.getSelectedItem();
      if (SUPPORTED_COMMANDS.containsKey(selectedCommand)) {
        Command command = SUPPORTED_COMMANDS.get(selectedCommand)
          .setStreamId(mySelectedStream.getStreamId())
          .setPid(mySelectedProcess.getPid())
          .build();
        // TODO(b/150503095)
        Transport.ExecuteResponse response =
            myClient.getTransportStub().execute(Transport.ExecuteRequest.newBuilder().setCommand(command).build());
      }
    });

    myProcessesMap = new HashMap<>();
    myStreamIdMap = new HashMap<>();
    myProcessIdMap = new HashMap<>();

    myTransportEventPoller = TransportEventPoller.createStartedPoller(myClient.getTransportStub(), TimeUnit.MILLISECONDS.toNanos(250));

    // Register the event listeners with myTransportEventPoller
    initializeEventListeners();

    myEventFilter = new ComboBox<>();
    for (Common.Event.Kind kind : Common.Event.Kind.values()) {
      if (kind != Common.Event.Kind.UNRECOGNIZED) {
        myEventFilter.addItem(kind);
      }
    }
    myEventLog = new JBTextArea();
    myEventFilter.addActionListener(e -> {
      myEventLog.setText("");
      // First unregister the old listener
      if (mySelectedEventListener != null) {
        myTransportEventPoller.unregisterListener(mySelectedEventListener);
      }

      // Create listener for selected status
      Common.Event.Kind currentEventKind = (Common.Event.Kind)myEventFilter.getSelectedItem();
      mySelectedEventListener = new TransportEventListener(
        currentEventKind,
        ApplicationManager.getApplication()::invokeLater,
        event -> {
          // Add events to log
          myEventLog.append(event.toString());
          return false;
        });
      myTransportEventPoller.registerListener(mySelectedEventListener);
    });

    init();
    toggleControls(false);
    rebuildDevicesDropdown();
  }

  @Override
  protected void dispose() {
    super.dispose();
    TransportEventPoller.stopPoller(myTransportEventPoller);
  }

  // Triggered byt init() call.
  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new TabularLayout("Fit,Fit,*"));
    panel.add(myProcessSelectionDropDown, new TabularLayout.Constraint(0, 0));
    panel.add(myProcessAgentStatus, new TabularLayout.Constraint(1, 0, 3));

    panel.add(new JLabel("Commands"), new TabularLayout.Constraint(2, 0));
    panel.add(myCommandComboBox, new TabularLayout.Constraint(2, 1));
    panel.add(mySendCommandButton, new TabularLayout.Constraint(2, 2));

    panel.add(new JLabel("Events"), new TabularLayout.Constraint(3, 0));
    panel.add(myEventFilter, new TabularLayout.Constraint(3, 1));

    JScrollPane logScrollPane = new JBScrollPane(myEventLog);
    logScrollPane.setMinimumSize(new Dimension(JBUI.scale(300), JBUI.scale(600)));
    panel.add(logScrollPane, new TabularLayout.Constraint(4, 1, 2));
    myRootPanel.add(panel, BorderLayout.CENTER);

    return myRootPanel;
  }

  /**
   * Called from constructor to register event listeners with TransportEventPoller
   */
  private void initializeEventListeners() {
    // Create listener for STREAM connected
    TransportEventListener streamConnectedListener = new TransportEventListener(
      Common.Event.Kind.STREAM,
      ApplicationManager.getApplication()::invokeLater,
      event -> event.getStream().hasStreamConnected(),
      event -> {
        Common.Stream stream = event.getStream().getStreamConnected().getStream();
        myStreamIdMap.put(stream.getStreamId(), stream);
        myProcessesMap.put(stream.getStreamId(), new ArrayList<>());
        rebuildDevicesDropdown();
        return false;
      });
    myTransportEventPoller.registerListener(streamConnectedListener);


    // Create listener for STREAM disconnected
    TransportEventListener streamDisconnectedListener = new TransportEventListener(
      Common.Event.Kind.STREAM,
      ApplicationManager.getApplication()::invokeLater,
      event -> !event.getStream().hasStreamConnected(),
      event -> {
        // Group ID here is Stream ID
        myStreamIdMap.remove(event.getGroupId());
        myProcessesMap.remove(event.getGroupId());
        rebuildDevicesDropdown();
        return false;
      });
    myTransportEventPoller.registerListener(streamDisconnectedListener);


    // Create listener for PROCESS started
    TransportEventListener processStartedListener = new TransportEventListener(
      Common.Event.Kind.PROCESS, ApplicationManager.getApplication()::invokeLater,
      event -> event.getProcess().hasProcessStarted(),
      event -> {
        // Group ID here is the process ID
        Common.Process process = event.getProcess().getProcessStarted().getProcess();
        // TransportPipelineDialog aims to demo the full capability of the pipeline, of which the
        // JVMTI agent is an important functionality. As JMVTI agent is supported by debuggable
        // processes only, we show them only and ignore profileable ones here.
        if (process.getExposureLevel() == Common.Process.ExposureLevel.DEBUGGABLE) {
          myProcessesMap.get(process.getDeviceId()).add(process);
          myProcessIdMap.put(event.getGroupId(), process);
          rebuildDevicesDropdown();
        }
        return false;
      });
    myTransportEventPoller.registerListener(processStartedListener);


    // Create listener for PROCESS stopped
    TransportEventListener processEndedListener = new TransportEventListener(
      Common.Event.Kind.PROCESS, ApplicationManager.getApplication()::invokeLater,
      event -> !event.getProcess().hasProcessStarted(),
      event -> {
        // Group ID here is the process ID
        Common.Process process = myProcessIdMap.remove(event.getGroupId());
        if (myProcessesMap.get(process.getDeviceId()) != null) {
          myProcessesMap.get(process.getDeviceId()).remove(process);
        }
        rebuildDevicesDropdown();
        return false;
      });
    myTransportEventPoller.registerListener(processEndedListener);
  }

  private void toggleControls(boolean enabled) {
    myCommandComboBox.setEnabled(enabled);
    mySendCommandButton.setEnabled(enabled);
    myEventFilter.setEnabled(enabled);
    myEventLog.setEnabled(enabled);
    myEventLog.setText("");

    myProcessAgentStatus.setVisible(!mySelectedProcess.equals(Common.Process.getDefaultInstance()));
    if (myProcessAgentStatus.isVisible()) {
      myProcessAgentStatus
        .setText(String.format(enabled ? "Agent connected to %s" : "Awaiting agent for %s", mySelectedProcess.getName()));
    }
  }

  private void rebuildDevicesDropdown() {
    myProcessSelectionAction.clear();
    Map<Long, List<Common.Process>> processesMap = myProcessesMap;

    // Rebuild the action tree.
    if (processesMap.isEmpty()) {
      CommonAction noDeviceAction = new CommonAction(NO_DEVICES_DETECTED, null);
      noDeviceAction.setEnabled(false);
      myProcessSelectionAction.addChildrenActions(noDeviceAction);
    }
    else {
      for (long streamId : processesMap.keySet()) {
        Common.Stream stream = myStreamIdMap.get(streamId);
        CommonAction deviceAction = new CommonAction(buildDeviceName(stream.getDevice()), null);
        List<Common.Process> processes = processesMap.get(streamId);

        rebuildProcessesDropdown(deviceAction, stream, processes);
        myProcessSelectionAction.addChildrenActions(deviceAction);
      }
    }
  }

  private void rebuildProcessesDropdown(CommonAction deviceAction, Common.Stream stream, List<Common.Process> processes) {
    deviceAction.clear();
    if (processes.isEmpty()) {
      CommonAction noProcessAction = new CommonAction(NO_DEBUGGABLE_PROCESSES, null);
      noProcessAction.setEnabled(false);
      deviceAction.addChildrenActions(noProcessAction);
    }
    else {
      List<CommonAction> processActions = new ArrayList<>();
      for (Common.Process process : processes) {
        CommonAction processAction = new CommonAction(String.format(Locale.US, "%s (%d)", process.getName(), process.getPid()), null);
        processAction.setAction(() -> {
          mySelectedStream = stream;
          mySelectedProcess = process;

          // Re-register the AGENT listener every time a process is selected, to start over for the timeframe
          registerAgentListener();

          // The device daemon takes care of the case if and when the agent is previously attached already.
          Command attachCommand = Command.newBuilder()
            .setStreamId(mySelectedStream.getStreamId())
            .setPid(mySelectedProcess.getPid())
            .setType(Command.CommandType.ATTACH_AGENT)
            .setAttachAgent(
              Commands.AttachAgent.newBuilder()
                .setAgentLibFileName(String.format("libjvmtiagent_%s.so", process.getAbiCpuArch()))
                .setAgentConfigPath(TransportFileManager.getAgentConfigFile())
                .setPackageName(process.getPackageName()))
            .build();
          // TODO(b/150503095)
          Transport.ExecuteResponse response =
              myClient.getTransportStub().execute(Transport.ExecuteRequest.newBuilder().setCommand(attachCommand).build());
          toggleControls(false);
        });
        processActions.add(processAction);
      }

      deviceAction.addChildrenActions(processActions);
    }
  }

  private void registerAgentListener() {
    if (myAgentStatusListener != null) {
      myTransportEventPoller.unregisterListener(myAgentStatusListener);
    }

    // Create listener for agent status
    myAgentStatusListener = new TransportEventListener(
      Common.Event.Kind.AGENT, ApplicationManager.getApplication()::invokeLater,
      event -> event.getAgentData().getStatus().equals(Common.AgentData.Status.ATTACHED),
      this::getSelectedStreamId,
      this::getSelectedProcessId,
      event -> {
        // If a process is selected, enable the UI once the agent is detected.
        toggleControls(true);
        return false;
      });
    myTransportEventPoller.registerListener(myAgentStatusListener);
  }

  @NotNull
  private static String buildDeviceName(@NotNull Common.Device device) {
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

    return deviceNameBuilder.toString();
  }
}
