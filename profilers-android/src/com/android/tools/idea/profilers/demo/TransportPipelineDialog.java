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
package com.android.tools.idea.profilers.demo;

import static com.android.tools.profiler.proto.Transport.Command.CommandType.ECHO;

import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.model.FpsTimer;
import com.android.tools.adtui.model.stdui.CommonAction;
import com.android.tools.adtui.model.updater.Updatable;
import com.android.tools.adtui.model.updater.Updater;
import com.android.tools.adtui.stdui.menu.CommonDropDownButton;
import com.android.tools.idea.profilers.ProfilerService;
import com.android.tools.pipeline.example.proto.Echo;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profilers.ProfilerClient;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import icons.StudioIcons;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// TODO move this to android-transport once the TransportService is complete.
public class TransportPipelineDialog extends DialogWrapper implements Updatable {
  static final String TITLE = "Transport Pipeline";

  private JPanel myRootPanel;

  private static final Map<Transport.Command.CommandType, Transport.Command.Builder> SUPPORTED_COMMANDS = ImmutableMap.of(
    ECHO, Transport.Command.newBuilder().setType(ECHO).setEchoData(Echo.EchoData.newBuilder().setData("Hello World"))
  );


  @NotNull private final CommonAction myProcessSelectionAction;
  @NotNull private final CommonDropDownButton myProcessSelectionDropDown;
  @NotNull private final JLabel myProcessAgentStatus;
  @NotNull private final ComboBox<Transport.Command.CommandType> myCommandComboBox;
  @NotNull private final JButton mySendCommandButton;
  @NotNull private final ComboBox<Common.Event.Kind> myEventFilter;
  @NotNull private final JBTextArea myEventLog;

  @NotNull private final Updater myUpdater;
  // TODO replace with TransportClient once ready.
  @Nullable private final ProfilerClient myClient;
  @Nullable private Common.Stream mySelectedStream = Common.Stream.getDefaultInstance();
  @Nullable private Common.Process mySelectedProcess = Common.Process.getDefaultInstance();
  private Map<Common.Stream, List<Common.Process>> myProcessesMap;
  private boolean myAgentConnected;
  private long myLastEventRequestTimestampNs = Long.MIN_VALUE;

  public TransportPipelineDialog(@Nullable Project project) {
    super(project);
    setTitle(TITLE);
    setModal(false);

    myClient = ProfilerService.getInstance(project).getProfilerClient();
    myUpdater = new Updater(new FpsTimer(1));
    myUpdater.register(this);

    myProcessSelectionAction = new CommonAction("Select Process", StudioIcons.Common.ADD);
    myProcessSelectionDropDown = new CommonDropDownButton(myProcessSelectionAction);
    myProcessSelectionDropDown.setToolTipText("Select a process to connect to.");
    myProcessAgentStatus = new JLabel("");

    myCommandComboBox = new ComboBox<>();
    for (Transport.Command.CommandType type : SUPPORTED_COMMANDS.keySet()) {
      myCommandComboBox.addItem(type);
    }
    mySendCommandButton = new JButton("Send Command");
    mySendCommandButton.addActionListener(e -> {
      Transport.Command.CommandType selectedCommand = (Transport.Command.CommandType)myCommandComboBox.getSelectedItem();
      if (SUPPORTED_COMMANDS.containsKey(selectedCommand)) {
        Transport.Command command = SUPPORTED_COMMANDS.get(selectedCommand)
          .setStreamId(mySelectedStream.getStreamId())
          .setPid(mySelectedProcess.getPid())
          .build();
        myClient.getTransportClient().execute(Transport.ExecuteRequest.newBuilder().setCommand(command).build());
      }
    });

    myEventFilter = new ComboBox<>();
    for (Common.Event.Kind kind : Common.Event.Kind.values()) {
      if (kind != Common.Event.Kind.UNRECOGNIZED) {
        myEventFilter.addItem(kind);
      }
    }
    myEventLog = new JBTextArea();
    myEventFilter.addActionListener(e -> {
      myLastEventRequestTimestampNs = Long.MIN_VALUE;
      myEventLog.setText("");
    });

    init();
    toggleControls(myAgentConnected);
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

  @Override
  public void update(long elapsedNs) {
    if (myClient == null) {
      return;
    }

    // Query for current devices and processes
    Map<Common.Stream, List<Common.Process>> processesMap = new HashMap<>();
    {
      List<Common.Stream> streams = new LinkedList<>();
      // Get all streams of all types.
      Transport.GetEventGroupsRequest request = Transport.GetEventGroupsRequest.newBuilder()
        .setStreamId(-1)  // DataStoreService.DATASTORE_RESERVED_STREAM_ID
        .setKind(Common.Event.Kind.STREAM)
        .build();
      Transport.GetEventGroupsResponse response = myClient.getTransportClient().getEventGroups(request);
      for (Transport.EventGroup group : response.getGroupsList()) {
        boolean isStreamDead = group.getEvents(group.getEventsCount() - 1).getIsEnded();
        if (isStreamDead) {
          // Ignore dead streams.
          continue;
        }
        Common.Event connectedEvent = getLastMatchingEvent(group, e -> (e.hasStream() && e.getStream().hasStreamConnected()));
        if (connectedEvent == null) {
          // Ignore stream event groups that do not have the connected event.
          continue;
        }
        Common.Stream stream = connectedEvent.getStream().getStreamConnected().getStream();
        // We only want streams of type device to get process information.
        if (stream.getType() == Common.Stream.Type.DEVICE) {
          streams.add(stream);
        }
      }

      for (Common.Stream stream : streams) {
        Transport.GetEventGroupsRequest processRequest = Transport.GetEventGroupsRequest.newBuilder()
          .setStreamId(stream.getStreamId())
          .setKind(Common.Event.Kind.PROCESS)
          .build();
        Transport.GetEventGroupsResponse processResponse = myClient.getTransportClient().getEventGroups(processRequest);
        List<Common.Process> processList = new ArrayList<>();
        // A group is a collection of events that happened to a single process.
        for (Transport.EventGroup groupProcess : processResponse.getGroupsList()) {
          boolean isProcessDead = groupProcess.getEvents(groupProcess.getEventsCount() - 1).getIsEnded();
          if (isProcessDead) {
            // Ignore dead processes.
            continue;
          }
          Common.Event aliveEvent = getLastMatchingEvent(groupProcess, e -> (e.hasProcess() && e.getProcess().hasProcessStarted()));
          if (aliveEvent == null) {
            // Ignore process event groups that do not have the started event.
            continue;
          }
          Common.Process process = aliveEvent.getProcess().getProcessStarted().getProcess();
          processList.add(process);
        }
        processesMap.put(stream, processList);
      }
    }

    // Populate the process selection dropdown.
    if (!processesMap.equals(myProcessesMap)) {
      myProcessesMap = processesMap;
      refreshProcessDropdown(myProcessesMap);
    }

    // If a process is selected, enabled the UI once the agent is detected.
    if (!mySelectedProcess.equals(Common.Process.getDefaultInstance())) {
      if (myAgentConnected) {
        Common.Event.Kind eventKind = (Common.Event.Kind)myEventFilter.getSelectedItem();
        if (eventKind != null && !Common.Event.Kind.NONE.equals(eventKind)) {
          Transport.GetEventGroupsRequest eventRequest = Transport.GetEventGroupsRequest.newBuilder()
            .setKind(eventKind)
            .setFromTimestamp(myLastEventRequestTimestampNs)
            .setToTimestamp(Long.MAX_VALUE)
            .build();
          Transport.GetEventGroupsResponse eventResponse = myClient.getTransportClient().getEventGroups(eventRequest);
          if (!eventResponse.equals(Transport.GetEventGroupsResponse.getDefaultInstance())) {
            List<Common.Event> events = new ArrayList<>();
            eventResponse.getGroupsList().forEach(group -> events.addAll(group.getEventsList()));
            Collections.sort(events, Comparator.comparingLong(Common.Event::getTimestamp));
            if (!events.isEmpty()) {
              events.forEach(evt -> {
                if (evt.getTimestamp() >= myLastEventRequestTimestampNs) {
                  myEventLog.append(evt.toString());
                }
              });
              myLastEventRequestTimestampNs = Math.max(myLastEventRequestTimestampNs, events.get(events.size() - 1).getTimestamp() + 1);
            }
          }
        }
      }
      else {
        // Get agent data for requested session.
        Transport.GetEventGroupsRequest agentRequest = Transport.GetEventGroupsRequest.newBuilder()
          .setKind(Common.Event.Kind.AGENT)
          .setStreamId(mySelectedStream.getStreamId())
          .setPid(mySelectedProcess.getPid())
          .build();
        Transport.GetEventGroupsResponse response = myClient.getTransportClient().getEventGroups(agentRequest);
        for (Transport.EventGroup group : response.getGroupsList()) {
          if (group.getEvents(group.getEventsCount() - 1).getAgentData().getStatus().equals(Common.AgentData.Status.ATTACHED)) {
            myAgentConnected = true;
            toggleControls(myAgentConnected);
            break;
          }
        }
      }
    }
  }

  private void refreshProcessDropdown(Map<Common.Stream, List<Common.Process>> processesMap) {
    myProcessSelectionAction.clear();

    // Rebuild the action tree.
    if (processesMap.isEmpty()) {
      CommonAction noDeviceAction = new CommonAction("No devices detected", null);
      noDeviceAction.setEnabled(false);
      myProcessSelectionAction.addChildrenActions(noDeviceAction);
    }
    else {
      for (Common.Stream stream : processesMap.keySet()) {
        CommonAction deviceAction = new CommonAction(buildDeviceName(stream.getDevice()), null);
        List<Common.Process> processes = processesMap.get(stream);
        if (processes.isEmpty()) {
          CommonAction noProcessAction = new CommonAction("No debuggable processes detected", null);
          noProcessAction.setEnabled(false);
          deviceAction.addChildrenActions(noProcessAction);
        }
        else {
          List<CommonAction> processActions = new ArrayList<>();
          for (Common.Process process : processes) {
            CommonAction processAction = new CommonAction(String.format("%s (%d)", process.getName(), process.getPid()), null);
            processAction.setAction(() -> {
              mySelectedStream = stream;
              mySelectedProcess = process;

              // The device daemon takes care of the case if and when the agent is previously attached already.
              Transport.Command attachCommand = Transport.Command.newBuilder()
                .setStreamId(mySelectedStream.getStreamId())
                .setPid(mySelectedProcess.getPid())
                .setType(Transport.Command.CommandType.ATTACH_AGENT)
                .setAttachAgent(
                  Transport.AttachAgent.newBuilder().setAgentLibFileName(String.format("libperfa_%s.so", process.getAbiCpuArch())))
                .build();
              myClient.getTransportClient().execute(Transport.ExecuteRequest.newBuilder().setCommand(attachCommand).build());
              myAgentConnected = false;
              toggleControls(myAgentConnected);
            });
            processActions.add(processAction);
          }

          deviceAction.addChildrenActions(processActions);
        }
        myProcessSelectionAction.addChildrenActions(deviceAction);
      }
    }
  }

  /**
   * Helper method to return the last even in an EventGroup that matches the input condition.
   */
  @Nullable
  private static Common.Event getLastMatchingEvent(@NotNull Transport.EventGroup group, @NotNull Predicate<Common.Event> predicate) {
    Common.Event matched = null;
    for (Common.Event event : group.getEventsList()) {
      if (predicate.test(event)) {
        matched = event;
      }
    }

    return matched;
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
