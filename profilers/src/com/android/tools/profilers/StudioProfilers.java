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
package com.android.tools.profilers;

import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.model.AspectModel;
import com.android.tools.adtui.model.FpsTimer;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.StopwatchTimer;
import com.android.tools.adtui.model.StreamingTimeline;
import com.android.tools.adtui.model.axis.AxisComponentModel;
import com.android.tools.adtui.model.axis.ResizingAxisComponentModel;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.adtui.model.updater.Updatable;
import com.android.tools.adtui.model.updater.Updater;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.flags.enums.PowerProfilerDisplayMode;
import com.android.tools.idea.io.grpc.StatusRuntimeException;
import com.android.tools.idea.transport.manager.StreamQueryUtils;
import com.android.tools.idea.transport.poller.TransportEventPoller;
import com.android.tools.profiler.proto.Commands;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Common.AgentData;
import com.android.tools.profiler.proto.Common.Event;
import com.android.tools.profiler.proto.Common.Stream;
import com.android.tools.profiler.proto.Trace;
import com.android.tools.profiler.proto.Transport.EventGroup;
import com.android.tools.profiler.proto.Transport.GetEventGroupsRequest;
import com.android.tools.profiler.proto.Transport.GetEventGroupsResponse;
import com.android.tools.profiler.proto.Transport.TimeRequest;
import com.android.tools.profiler.proto.Transport.TimeResponse;
import com.android.tools.profilers.cpu.CpuCaptureMetadata;
import com.android.tools.profilers.cpu.CpuProfiler;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.android.tools.profilers.customevent.CustomEventProfiler;
import com.android.tools.profilers.customevent.CustomEventProfilerStage;
import com.android.tools.profilers.event.EventProfiler;
import com.android.tools.profilers.memory.MainMemoryProfilerStage;
import com.android.tools.profilers.memory.MemoryProfiler;
import com.android.tools.profilers.sessions.SessionAspect;
import com.android.tools.profilers.sessions.SessionItem;
import com.android.tools.profilers.sessions.SessionsManager;
import com.android.tools.profilers.taskbased.home.TaskHomeTabModel;
import com.android.tools.profilers.taskbased.home.selections.deviceprocesses.ProcessListModel.ProfilerDeviceSelection;
import com.android.tools.profilers.taskbased.home.selections.deviceprocesses.ProcessListModel.ToolbarDeviceSelection;
import com.android.tools.profilers.tasks.ProfilerTaskLauncher;
import com.android.tools.profilers.tasks.ProfilerTaskType;
import com.android.tools.profilers.tasks.TaskTypeMappingUtils;
import com.android.tools.profilers.tasks.args.TaskArgs;
import com.android.tools.profilers.tasks.taskhandlers.ProfilerTaskHandler;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.hash.Hashing;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import kotlin.jvm.functions.Function0;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The suite of profilers inside Android Studio. This object is responsible for maintaining the information
 * global across all the profilers, device management, process management, current state of the tool etc.
 */
public class StudioProfilers extends AspectModel<ProfilerAspect> implements Updatable {

  private static Logger getLogger() { return Logger.getInstance(StudioProfilers.class); }

  /**
   * The collection of data used to select a new process when we don't have a process to profile.
   */
  private static final class Preference {
    public static final Preference NONE = new Preference(null, null, null);

    @Nullable
    public final String deviceName;

    @Nullable
    public final String processName;

    @NotNull
    public final Predicate<Common.Process> processFilter;

    public Preference(@Nullable String deviceName,
                      @Nullable String processName,
                      @Nullable Predicate<Common.Process> processFilter) {
      this.deviceName = deviceName;
      this.processName = processName;
      this.processFilter = processFilter == null ? (process -> true) : processFilter;
    }
  }

  // Device directory where the transport daemon lives.
  public static final String DAEMON_DEVICE_DIR_PATH = "/data/local/tmp/perfd";

  @VisibleForTesting static final int AGENT_STATUS_MAX_RETRY_COUNT = 10;

  /**
   * The number of updates per second our simulated object models receive.
   */
  public static final int PROFILERS_UPDATE_RATE = 60;
  public static final long TRANSPORT_POLLER_INTERVAL_NS = TimeUnit.MILLISECONDS.toNanos(500);

  @NotNull private final ProfilerClient myClient;

  private final StreamingTimeline myTimeline;

  private final List<StudioProfiler> myProfilers;

  @NotNull
  private final IdeProfilerServices myIdeServices;

  /**
   * Callback to create and open the profiler's task tab for a specified task type, making the task tab creation functionality of the tool
   * window accessible.
   */
  @NotNull
  private final BiConsumer<ProfilerTaskType, TaskArgs> myCreateTaskTab;

  /**
   * Callback to open the profiler's task tab, making the task tab opening functionality of the tool window accessible.
   */
  @NotNull
  private final Runnable myOpenTaskTab;

  @NotNull
  private final Function0<List<ToolbarDeviceSelection>> myToolbarDeviceSelectionsFetcher;

  /**
   * Callback to fetch the selected run configuration's package name. In the Task-Based UX, this is invoked at each tick in the update
   * method and used to set the preferred process name.
   *
   * Note: This callback will be set to null by test-only constructors to prevent test environments from invoking it and overwriting the
   * preferred process set by the test. A non-null value will be set on tool window initialization in production.
   */
  @Nullable
  private final Function0<String> myPreferredProcessNameFetcher;

  @Nullable
  private ToolbarDeviceSelection myLastToolbarDeviceSelection = null;

  /**
   * Processes from devices come from the latest update, and are filtered to include only ALIVE ones and {@code myProcess}.
   */
  private Map<Common.Device, List<Common.Process>> myProcesses;

  /**
   * A map of device to stream ids. This is needed to map devices to their transport-database streams.
   */
  private Map<Common.Device, Long> myDeviceToStreamIds;

  private Map<Long, Common.Stream> myStreamIdToStreams;

  /**
   * Data model for the Task-Based UX's home tab.
   */
  @NotNull
  private final TaskHomeTabModel myTaskHomeTabModel;

  @NotNull
  private final Map<ProfilerTaskType, ProfilerTaskHandler> myTaskHandlers;

  @NotNull private final SessionsManager mySessionsManager;

  @Nullable
  private Common.Process myProcess;

  @NotNull
  private AgentData myAgentData;

  @NotNull
  private Preference myPreference = Preference.NONE;

  private Common.Device myDevice;

  /**
   * The session that is currently selected.
   */
  @NotNull private Common.Session mySelectedSession;

  /**
   * The session that is currently under profiling.
   */
  @NotNull private Common.Session myProfilingSession;

  @NotNull
  private Stage myStage;

  private Updater myUpdater;

  private AxisComponentModel myViewAxis;

  private long myRefreshDevicesAndPreferredProcessName;

  private long myEventPollingInternvalNs;

  private final Map<Common.SessionMetaData.SessionType, Runnable> mySessionChangeListener;

  /**
   * Whether the profiler should auto-select a process to profile.
   */
  private boolean myAutoProfilingEnabled;

  /**
   * The number of update count the profilers have waited for an agent status to become ATTACHED for a particular session id.
   * If the agent status remains UNSPECIFIED after {@link StudioProfilers#AGENT_STATUS_MAX_RETRY_COUNT}, the profilers deem the process to
   * be without agent.
   */
  public final Map<Long, Integer> mySessionIdToAgentStatusRetryMap = new HashMap<>();

  private TransportEventPoller myTransportPoller;

  @VisibleForTesting
  public StudioProfilers(@NotNull ProfilerClient client, @NotNull IdeProfilerServices ideServices) {
    this(client, ideServices, new FpsTimer(PROFILERS_UPDATE_RATE));
  }

  @VisibleForTesting
  public StudioProfilers(@NotNull ProfilerClient client, @NotNull IdeProfilerServices ideServices, @NotNull StopwatchTimer timer) {
    this(client, ideServices, timer, new HashMap<>(), (i, j) -> {}, () -> {}, ArrayList::new, null);
  }

  /**
   * Under the Task-Based UX, this constructor serves as the primary constructor to create the StudioProfilers instance for the Profiler
   * tool window. What differentiates it from other StudioProfilers constructors is the addition of the project and taskHandlers parameters.
   * The project is utilized to interface to the tool window code, allowing us to create and open tabs from StudioProfilers. The
   * taskHandlers is just a map of task types to their respective handlers. These task handlers and their functionality can now be utilized
   * in profiler-level code, not just toolwindow code where they are created.
   */
  public StudioProfilers(@NotNull ProfilerClient client,
                         @NotNull IdeProfilerServices ideServices,
                         @NotNull HashMap<ProfilerTaskType, ProfilerTaskHandler> taskHandlers,
                         @NotNull BiConsumer<ProfilerTaskType, TaskArgs> createTaskTab,
                         @NotNull Runnable openTaskTab,
                         @NotNull Function0<List<ToolbarDeviceSelection>> toolbarDeviceSelectionsFetcher,
                         @Nullable Function0<String> preferredProcessNameFetcher) {
    this(client, ideServices, new FpsTimer(PROFILERS_UPDATE_RATE), taskHandlers, createTaskTab, openTaskTab,
         toolbarDeviceSelectionsFetcher, preferredProcessNameFetcher);
  }

  private StudioProfilers(@NotNull ProfilerClient client,
                          @NotNull IdeProfilerServices ideServices,
                          @NotNull StopwatchTimer timer,
                          @NotNull HashMap<ProfilerTaskType, ProfilerTaskHandler> taskHandlers,
                          @NotNull BiConsumer<ProfilerTaskType, TaskArgs> createTaskTab,
                          @NotNull Runnable openTaskTab,
                          @NotNull Function0<List<ToolbarDeviceSelection>> toolbarDeviceSelectionsFetcher,
                          @Nullable Function0<String> preferredProcessNameFetcher) {
    myClient = client;
    myIdeServices = ideServices;
    myStage = createDefaultStage();
    mySessionsManager = new SessionsManager(this);
    mySessionChangeListener = new HashMap<>();
    myDeviceToStreamIds = new HashMap<>();
    myStreamIdToStreams = new HashMap<>();
    myTaskHomeTabModel = new TaskHomeTabModel(this);
    myTaskHandlers = taskHandlers;
    myCreateTaskTab = createTaskTab;
    myOpenTaskTab = openTaskTab;
    myToolbarDeviceSelectionsFetcher = toolbarDeviceSelectionsFetcher;
    myPreferredProcessNameFetcher = preferredProcessNameFetcher;
    myStage.enter();

    myUpdater = new Updater(timer);

    // Order in which events are added to profilersBuilder will be order they appear in monitor stage
    ImmutableList.Builder<StudioProfiler> profilersBuilder = new ImmutableList.Builder<>();
    profilersBuilder.add(new EventProfiler(this));

    // Show the custom event monitor in the monitor stage view when enabled right under the activity bar
    if (myIdeServices.getFeatureConfig().isCustomEventVisualizationEnabled()) {
      profilersBuilder.add(new CustomEventProfiler(this));
    }
    profilersBuilder.add(new CpuProfiler(this));
    profilersBuilder.add(new MemoryProfiler(this));
    myProfilers = profilersBuilder.build();

    myTimeline = new StreamingTimeline(myUpdater);

    myProcesses = Maps.newHashMap();
    myDevice = null;
    myProcess = null;

    // TODO: StudioProfilers initalizes with a default session, which a lot of tests now relies on to avoid a NPE.
    // We should clean all the tests up to either have StudioProfilers create a proper session first or handle the null cases better.
    mySelectedSession = myProfilingSession = Common.Session.getDefaultInstance();
    myAgentData = AgentData.getDefaultInstance();

    myTimeline.getSelectionRange().addDependency(this).onChange(Range.Aspect.RANGE, () -> {
      if (!myTimeline.getSelectionRange().isEmpty()) {
        myTimeline.setStreaming(false);
      }
    });

    registerSessionChangeListener(Common.SessionMetaData.SessionType.FULL, () -> {
      if (!ideServices.getFeatureConfig().isTaskBasedUxEnabled()) {
        setStage(new StudioMonitorStage(this));
      }
      if (SessionsManager.isSessionAlive(mySelectedSession)) {
        // The session is live - move the timeline to the current time.
        TimeResponse timeResponse = myClient.getTransportClient().getCurrentTime(
          TimeRequest.newBuilder().setStreamId(mySelectedSession.getStreamId()).build());

        myTimeline.reset(mySelectedSession.getStartTimestamp(), timeResponse.getTimestampNs());
        boolean isTaskBasedUXEnabled = getIdeServices().getFeatureConfig().isTaskBasedUxEnabled();

        if (isTaskBasedUXEnabled) {
          if (startupProfilingStarted()) {
            getTaskHomeTabModel().resetSelectionStateOnTaskEnter();
          }
        }
        else {
          if (startupCpuProfilingStarted()) {
            setStage(new CpuProfilerStage(this, CpuCaptureMetadata.CpuProfilerEntryPoint.STARTUP_PROFILING));
          }
          else if (startupMemoryProfilingStarted()) {
            setStage(new MainMemoryProfilerStage(this));
          }
        }
      }
      else {
        // The session is finished, reset the timeline to include the entire data range.
        myTimeline.reset(mySelectedSession.getStartTimestamp(), mySelectedSession.getEndTimestamp());
        // Disable data range update and stream/snap features.
        myTimeline.setIsPaused(true);
        myTimeline.setStreaming(false);
        myTimeline.getViewRange().set(mySessionsManager.getSessionPreferredViewRange(mySelectedSession));
      }
    });

    mySessionsManager.addDependency(this)
      .onChange(SessionAspect.SELECTED_SESSION, this::selectedSessionChanged)
      .onChange(SessionAspect.PROFILING_SESSION, this::profilingSessionChanged);

    myViewAxis = new ResizingAxisComponentModel.Builder(myTimeline.getViewRange(), TimeAxisFormatter.DEFAULT)
      .setGlobalRange(myTimeline.getDataRange()).build();

    // Manage our own poll interval with the poller instead of using the ScheduledExecutorService helper provided in TransportEventPoller.
    // The rest of the Studio code runs on its own updater and assumes all UI-related code (e.g. Aspect) be handled via the updating
    // thread. Using the ScheduleExecutorService would violate that assumption and cause concurrency issues.
    myTransportPoller = new TransportEventPoller(myClient.getTransportClient(), Comparator.comparing(Common.Event::getTimestamp));

    myUpdater.register(this);
  }

  public boolean isStopped() {
    return !myUpdater.isRunning();
  }

  public void stop() {
    if (isStopped()) {
      // Profiler is already stopped. Nothing to do. Ideally, this method shouldn't be called when the profiler is already stopped.
      // However, some exceptions might be thrown when listeners are notified about ProfilerAspect.STAGE aspect change and react
      // accordingly. In this case, we could end up with an inconsistent model and allowing to try to call stop and notify the listeners
      // again can only make it worse. Therefore, we return early to avoid making the model problem bigger.
      return;
    }
    // The following line can't throw an exception, will stop the updater's timer and guarantees future calls to isStopped() return true.
    myUpdater.stop();
    // The following lines trigger aspect changes and, therefore, can make many models to update. That might cause an exception to be thrown
    // and make some models inconsistent. In this case, we want future calls to this method to return early, as we can only make the
    // inconsistency worse if we call these lines again.
    setProcess(null, null);
    changed(ProfilerAspect.STAGE);
    // Shutdown the gRPC channel after changing the aspect because some operations triggered by the aspect depends on the channel.
    myClient.shutdownChannel();
  }

  @NotNull
  public TransportEventPoller getTransportPoller() {
    return myTransportPoller;
  }

  public Map<Common.Device, List<Common.Process>> getDeviceProcessMap() {
    return myProcesses;
  }

  public List<Common.Device> getDevices() {
    return Lists.newArrayList(myProcesses.keySet());
  }

  @NotNull
  public TaskHomeTabModel getTaskHomeTabModel() {
    return myTaskHomeTabModel;
  }

  /**
   * Tells the profiler to select and profile the device+process combo of the same name next time it
   * is detected.
   *
   * @param deviceName    The target device that the app will launch in.
   * @param processName   The process to profile.
   * @param processFilter Additional filter used for choosing the most desirable preferred process.
   *                      e.g. Process of a particular pid, or process that starts after a certain
   *                      time.
   */
  public void setPreferredProcess(@Nullable String deviceName,
                                  @Nullable String processName,
                                  @Nullable Predicate<Common.Process> processFilter) {

    boolean processNameChanged = !Objects.equals(myPreference.processName, processName);

    myPreference = new Preference(
      deviceName,
      processName,
      processFilter
    );

    // Checks whether we can switch immediately if the device is already there.
    if (!getIdeServices().getFeatureConfig().isTaskBasedUxEnabled()) {
      myIdeServices.getFeatureTracker().trackAutoProfilingRequested();
      setAutoProfilingEnabled(true);
    }

    changed(ProfilerAspect.PREFERRED_PROCESS);

    if (processNameChanged) {
      changed(ProfilerAspect.PREFERRED_PROCESS_NAME);
    }
  }

  public void setPreferredProcessName(@Nullable String processName) {
    if (Objects.equals(myPreference.processName, processName)) {
      return;
    }

    myPreference = new Preference(
      myPreference.deviceName,
      processName,
      myPreference.processFilter
    );

    changed(ProfilerAspect.PREFERRED_PROCESS_NAME);
  }

  @Nullable
  public String getPreferredProcessName() {
    return myPreference.processName;
  }

  /**
   * Enable/disable auto device+process selection, which looks for the preferred device + process combination and starts profiling. If no
   * preference has been set (via {@link #setProcess(Common.Device, Common.Process)}, then we profiling any online device+process combo.
   */
  public void setAutoProfilingEnabled(boolean enabled) {
    myAutoProfilingEnabled = enabled;

    if (enabled && getIdeServices().getFeatureConfig().isTaskBasedUxEnabled()) {
      getLogger().warn("Auto profiling should not be enabled or used when the Task-Based UX is enabled.");
    }
    // Do nothing. Let update() take care of which process should be selected.
    // If setProcess() is called now, it may be confused if the process start/end events don't arrive immediately.
    // For example, if the user ends a session (which will set myProcess null), then click the Run button.
    // setAutoProfilingEnabled(true) will be called when Run button is clicked. If the previous process's
    // DEAD event is delayed, setProcess() may start a new session on it (which is already dead) because
    // the event stream shows it is still alive, and it's "new" in the perspective of StudioProfilers.
  }

  public boolean getAutoProfilingEnabled() {
    return myAutoProfilingEnabled;
  }

  private List<Common.Device> getUpToDateDevices() {
    return getUpToDateDevices(myClient, myDeviceToStreamIds, myStreamIdToStreams);
  }

  /**
   * Returns a up-to-date list of devices including disconnected ones.
   * This method works under either new or old data pipeline.
   *
   * @param client                   The ProfilerClient that can call into ProfilerService.
   * @param deviceToStreamIds        An updatable cache that maps a device to its stream ID.
   * @param streamIdToStreams        An updatable cache that maps a stream ID to the stream.
   */
  @NotNull
  public static List<Common.Device> getUpToDateDevices(@NotNull ProfilerClient client,
                                                       @Nullable Map<Common.Device, Long> deviceToStreamIds,
                                                       @Nullable Map<Long, Common.Stream> streamIdToStreams) {
    List<Common.Stream> streams = StreamQueryUtils.queryForDevices(client.getTransportClient());
    return streams.stream().map((Stream stream) -> {
      if (deviceToStreamIds != null) {
        deviceToStreamIds.putIfAbsent(stream.getDevice(), stream.getStreamId());
      }
      if (streamIdToStreams != null) {
        streamIdToStreams.putIfAbsent(stream.getStreamId(), stream);
      }
      return stream.getDevice();
    }).collect(Collectors.toList());
  }

  /**
   * Reads main toolbar's device dropdown selections and updates the respective device state model with such selection data.
   */
  private void readMainToolbarDeviceSelection() {
    if (IdeInfo.isGameTool()) {
      return;
    }
    List<ToolbarDeviceSelection> toolbarDeviceSelections = myToolbarDeviceSelectionsFetcher.invoke();
    int toolbarSelectedDevicesCount = toolbarDeviceSelections.size();

    // In the case where only one device is selected, and it is a new selection, the selected device state is updated.
    if (toolbarSelectedDevicesCount == 1) {
      ToolbarDeviceSelection toolbarDeviceSelection = toolbarDeviceSelections.get(0);

      ProfilerDeviceSelection currentlySelectedDevice = myTaskHomeTabModel.getSelectedDevice();

      String toolbarSelectionName = toolbarDeviceSelection.getName();
      String toolbarSelectionSerial = toolbarDeviceSelection.getSerial();
      // The following cases cover all cases of detecting a new, unique selection made. This is because (1) Studio does not allow
      // duplicate AVD names, and (2) if there is a device created with the same name a physical device, the serial number is used to
      // differentiate. To avoid the same device being selected repeatedly, the last toolbar device selection is stored and compared
      // against the current toolbar selection.
      if (currentlySelectedDevice == null ||
          // Use toolbar selection if its name differs from the current selection
          (!currentlySelectedDevice.getName().equals(toolbarSelectionName) ||
           // OR if names match but serial numbers differ
           !currentlySelectedDevice.getDevice().getSerial().equals(toolbarSelectionSerial) ||
          // OR if the toolbar selection isn't the same as the last stored one to avoid repeated selection of the same device.
          !Objects.equals(myLastToolbarDeviceSelection, toolbarDeviceSelection))) {
        myTaskHomeTabModel.getProcessListModel().onDeviceSelection(toolbarDeviceSelection);
        myLastToolbarDeviceSelection = toolbarDeviceSelection;
      }
    }
    else {
      // The call to onDeviceSelection made if there is only one device selected will set ProcessListModel.selectedDeviceCount to a value
      // of 1. Because no ProcessListModel device selection occurs when zero or multiple devices are selected, the number of selected
      // devices are set here. This data allows the user to be notified when there is zero or multiple devices selected.
      myTaskHomeTabModel.getProcessListModel().setSelectedDevicesCount(toolbarSelectedDevicesCount);
      myTaskHomeTabModel.getProcessListModel().resetDeviceSelection();
    }
  }

  private void startProfileableDiscoveryIfApplicable(Collection<Common.Device> previousDevices,
                                                     Collection<Common.Device> currentDevices) {
    Set<Common.Device> newDevices = Sets.difference(filterOnlineDevices(currentDevices),
                                                    filterOnlineDevices(previousDevices));
    for (Common.Device device : newDevices) {
      int level = device.getFeatureLevel();
      if (level == AndroidVersion.VersionCodes.Q || level == AndroidVersion.VersionCodes.R) {
        myClient.executeAsync(
          Commands.Command.newBuilder()
            .setStreamId(myDeviceToStreamIds.get(device))
            .setType(Commands.Command.CommandType.DISCOVER_PROFILEABLE)
            .build(),
          getIdeServices().getPoolExecutor());
      }
    }
  }

  @NotNull
  public Common.Stream getStream(long streamId) {
    return myStreamIdToStreams.getOrDefault(streamId, Common.Stream.getDefaultInstance());
  }

  @Override
  public void update(long elapsedNs) {
    myEventPollingInternvalNs += elapsedNs;
    if (myEventPollingInternvalNs >= TRANSPORT_POLLER_INTERVAL_NS) {
      myTransportPoller.poll();
      myEventPollingInternvalNs = 0;
    }

    myRefreshDevicesAndPreferredProcessName += elapsedNs;
    if (myRefreshDevicesAndPreferredProcessName < TimeUnit.SECONDS.toNanos(1)) {
      return;
    }
    myRefreshDevicesAndPreferredProcessName = 0;

    if (getIdeServices().getFeatureConfig().isTaskBasedUxEnabled() && myPreferredProcessNameFetcher != null) {
      String preferredProcessName = myPreferredProcessNameFetcher.invoke();
      setPreferredProcessName(preferredProcessName);
    }

    try {
      readMainToolbarDeviceSelection();

      Map<Common.Device, List<Common.Process>> newProcesses = new HashMap<>();
      List<Common.Device> devices = getUpToDateDevices();
      startProfileableDiscoveryIfApplicable(myProcesses.keySet(), devices);

      for (Common.Device device : devices) {
        List<Common.Process> processList = StreamQueryUtils.queryForProcesses(
          myClient.getTransportClient(),
          myDeviceToStreamIds.get(device),
          (Boolean isProcessAlive, Common.Process process) -> {
            int lastProcessId = myProcess == null ? 0 : myProcess.getPid();
            return (isProcessAlive || process.getPid() == lastProcessId) &&
                   (process.getExposureLevel().equals(Common.Process.ExposureLevel.DEBUGGABLE) ||
                    process.getExposureLevel().equals(Common.Process.ExposureLevel.PROFILEABLE));
          }
        );
        newProcesses.put(device, processList);
      }

      if (!newProcesses.equals(myProcesses)) {
        myProcesses = newProcesses;
        // The following call to setProcess will start a session on profiler start and on process selection change, but Task-Based UX does
        // not auto start a session on profiler start or on process change. Thus, we disable the call to setProcess if the Task-Based UX
        // is enabled.
        boolean isTaskBasedUXEnabled = getIdeServices().getFeatureConfig().isTaskBasedUxEnabled();
        if (!isTaskBasedUXEnabled) {
          setProcess(findPreferredDevice(), null);
        }

        if (isTaskBasedUXEnabled) {
          TaskHomeTabModel.SelectionStateOnTaskEnter selectionStateOnTaskEnter = getTaskHomeTabModel().getSelectionStateOnTaskEnter();
          if (selectionStateOnTaskEnter != null) {
            boolean isProfilingFromProcessStart =
            selectionStateOnTaskEnter.getProfilingProcessStartingPoint() == TaskHomeTabModel.ProfilingProcessStartingPoint.PROCESS_START;
            ProfilerTaskType selectedTaskType = selectionStateOnTaskEnter.getSelectedStartupTaskType();
            // The check for a non-null preferred device makes sure the preferred device is alive and detected. It is imperative for startup
            // scenarios, although this condition may be true in non-startup scenarios too. It's worth noting that repeated calls to
            // setProcess with the same parameters are harmless, as setProcess prevents starting a session/task with the same device and
            // process combination when facilitating a startup task.
            if (findPreferredDevice() != null && isProfilingFromProcessStart && selectedTaskType != ProfilerTaskType.UNSPECIFIED) {
              setProcess(findPreferredDevice(), null, TaskTypeMappingUtils.convertTaskType(selectedTaskType), true);
            }
          }
        }

        // These need to be fired every time the process list changes so that the device/process dropdown always reflects the latest.
        changed(ProfilerAspect.PROCESSES);
      }

      mySessionsManager.update();

      // A heartbeat event may not have been sent by perfa when we first profile an app, here we keep pinging the status and
      // fire the corresponding change and tracking events.
      if (SessionsManager.isSessionAlive(mySelectedSession)) {
        AgentData agentData = getAgentData(mySelectedSession);
        // Consider the agent to be unattachable if it remains unspecified for long enough.
        int agentStatusRetryCount = mySessionIdToAgentStatusRetryMap.getOrDefault(mySelectedSession.getSessionId(), 0) + 1;
        if (agentData.getStatus() == AgentData.Status.UNSPECIFIED && agentStatusRetryCount >= AGENT_STATUS_MAX_RETRY_COUNT) {
          agentData = AgentData.newBuilder().setStatus(AgentData.Status.UNATTACHABLE).build();
        }
        mySessionIdToAgentStatusRetryMap.put(mySelectedSession.getSessionId(), agentStatusRetryCount);

        if (!myAgentData.equals(agentData)) {
          if (myAgentData.getStatus() != AgentData.Status.ATTACHED &&
              agentData.getStatus() == AgentData.Status.ATTACHED) {
            getIdeServices().getFeatureTracker().trackAdvancedProfilingStarted();
          }
          myAgentData = agentData;
          changed(ProfilerAspect.AGENT);
        }
      }
    }
    catch (StatusRuntimeException e) {
      // TODO: Clean up this exception, this has the potential to capture some subtle bugs
      // As an example the MemoryProfilerStateTest:testAgentStatusUpdatesObjectSeries depends on this exception being thrown
      // the exception gets thrown due to startMonitor being called on a service the test didn't setup, this seems like an
      // unintentional side effect of the state of the test that sets this class up properly, if we handle the exception elsewhere
      // the test will fail as a different service will run and an UNIMPLEMENTED exception will be thrown.
      System.err.println("Cannot find profiler service, retrying...");
    }
  }

  private static Set<Common.Device> filterOnlineDevices(Collection<Common.Device> devices) {
    return devices.stream().filter(device -> device.getState().equals(Common.Device.State.ONLINE)).collect(Collectors.toSet());
  }

  /**
   * Finds and returns the preferred device if there is an online device with a matching name.
   * Otherwise, we attempt to maintain the currently selected device.
   */
  @Nullable
  private Common.Device findPreferredDevice() {
    Set<Common.Device> devices = myProcesses.keySet();
    Set<Common.Device> onlineDevices = filterOnlineDevices(devices);

    // We have a preferred device, try not to select anything else.
    if ((getIdeServices().getFeatureConfig().isTaskBasedUxEnabled() || myAutoProfilingEnabled) && myPreference.deviceName != null) {
      for (Common.Device device : onlineDevices) {
        if (myPreference.deviceName.equals(buildDeviceName(device))) {
          return device;
        }
      }
    }

    // Next, prefer the device currently used.
    if (myDevice != null) {
      for (Common.Device device : devices) {
        if (myDevice.getDeviceId() == device.getDeviceId()) {
          return device;
        }
      }
    }

    return null;
  }

  public void setDefaultStage() {
    setStage(createDefaultStage());
  }

  public void setMonitoringStage() {
    setStage(new StudioMonitorStage(this));
  }

  public void setProcess(@Nullable Common.Device device, @Nullable Common.Process process) {
    setProcess(device, process, Common.ProfilerTaskType.UNSPECIFIED_TASK, false);
  }

  /**
   * Chooses a device+process combination, and starts profiling it if not already (and stops profiling the previous one).
   *
   * @param device    the device that will be selected. If it is null, no device and process will be selected for profiling.
   * @param process   the process that will be selected. Note that the process is expected to be spawned from the specified device.
   *                  If it is null, a process will be determined automatically by heuristics.
   * @param taskType  the type of task to pass into beginSession so that the created session has a respective task type.
   */
  public void setProcess(@Nullable Common.Device device,
                         @Nullable Common.Process process,
                         @NotNull Common.ProfilerTaskType taskType,
                         boolean isStartupTask) {
    if (device != null) {
      // Device can be not null in the following scenarios:
      // 1. User explicitly sets a device from the dropdown.
      // 2. The update loop has found the preferred device, in which case it will stay selected until the user selects something else.
      // All of these cases mean that we can unset the preferred device.
      myPreference = new Preference(
        null,
        myPreference.processName,
        myPreference.processFilter
      );

      // If the device is unsupported (e.g. pre-Lolipop), switch to the null stage with the unsupported reason.
      if (!device.getUnsupportedReason().isEmpty()) {
        setStage(new NullMonitorStage(this, device.getUnsupportedReason()));
      }
    }

    if (!Objects.equals(device, myDevice)) {
      // The device has changed and we need to reset the process.
      // First, end the current session on the previous device.
      mySessionsManager.endCurrentSession();
      myDevice = device;
      myIdeServices.getFeatureTracker().trackChangeDevice(myDevice);
    }

    List<Common.Process> processes = myProcesses.get(myDevice);
    if (process == null || processes == null || !processes.contains(process)) {
      process = getPreferredProcess(processes);
    }
    else {
      // The user wants to select a different process explicitly.
      // If the user intentionally selects something else, the profiler should not switch
      // back to the preferred process in any cases.
      setAutoProfilingEnabled(false);
    }

    // If the process changes OR the process was set in via a non-startup task initiation in the Task-Based UX, we end the current session
    // and begin a new one. In regard to startup tasks, a change in process to end the current session and begin a new one.
    if (!Objects.equals(process, myProcess) || (!isStartupTask && getIdeServices().getFeatureConfig().isTaskBasedUxEnabled())) {
      // First make sure to end the previous session.
      mySessionsManager.endCurrentSession();

      myProcess = process;
      changed(ProfilerAspect.PROCESSES);
      myIdeServices.getFeatureTracker().trackChangeProcess(myProcess);

      // Only start a new session if the process is valid.
      if (myProcess != null && myProcess.getState() == Common.Process.State.ALIVE) {
        mySessionsManager.beginSession(myDeviceToStreamIds.get(myDevice), myDevice, myProcess, taskType, isStartupTask);
      }
    }
  }

  /**
   * Register the listener to set proper stage when a new session is selected.
   *
   * @param sessionType type of the new session.
   * @param listener    listener to register.
   */
  public void registerSessionChangeListener(Common.SessionMetaData.SessionType sessionType, Runnable listener) {
    mySessionChangeListener.put(sessionType, listener);
  }

  private void selectedSessionChanged() {
    Common.Session newSession = mySessionsManager.getSelectedSession();

    // The current selected session has not changed but it has gone from live to finished, simply pause the timeline.
    if (mySelectedSession.getSessionId() == newSession.getSessionId() &&
        SessionsManager.isSessionAlive(mySelectedSession) && !SessionsManager.isSessionAlive(newSession)) {
      mySelectedSession = newSession;
      myTimeline.setIsPaused(true);
      return;
    }

    mySelectedSession = newSession;
    myAgentData = getAgentData(mySelectedSession);
    if (Common.Session.getDefaultInstance().equals(newSession)) {
      // No selected session - go to the null stage.
      myTimeline.setIsPaused(true);
      setDefaultStage();
      return;
    }

    // Set the stage base on session type
    Common.SessionMetaData.SessionType sessionType = mySessionsManager.getSelectedSessionMetaData().getType();
    assert mySessionChangeListener.containsKey(sessionType);

    // Disable the CPU_CAPTURE and MEMORY_CAPTURE session type change listener if the Task-Based UX is enabled. This prevents the automatic
    // entering of the CpuCaptureStage or MainMemoryProfilerStage respectively. Prevention of entering these stages also prevents the
    // parsing + insertion of a fake CPU_TRACE or MEMORY_TRACE event. This is done as, with the Task-Based UX enabled, the insertion of the
    // CPU_TRACE or MEMORY_TRACE event will be done at import-time rather than when their respective stage is set.
    if (!getIdeServices().getFeatureConfig().isTaskBasedUxEnabled() ||
        !sessionType.equals(Common.SessionMetaData.SessionType.CPU_CAPTURE) &&
        !sessionType.equals(Common.SessionMetaData.SessionType.MEMORY_CAPTURE)) {
      mySessionChangeListener.get(sessionType).run();
    }

    // Profilers can query data depending on whether the agent is set. Even though we set the status above, delay until after the
    // session is properly assigned before firing this aspect change.
    changed(ProfilerAspect.AGENT);

    // At the top of this function there is an early return for a session that is ending. So, if the session made it to this point, it a
    // new/alive session or a terminated session that has selected before. These two cases correspond respectively to the two major cases
    // in which a task should be launched: launching a task to collect new data and launching a task using data collected prior.
    // Additionally, it is worth noting that the placement of launching the task after running the session change listener and firing the
    // ProfilerAspect.AGENT aspect is done purposefully; these two synchronous operations serve as setup for the tasks to record new data
    // or load past recording (non-imported) data successfully.
    if (getIdeServices().getFeatureConfig().isTaskBasedUxEnabled()) {
      ProfilerTaskType selectedTaskType = mySessionsManager.getCurrentTaskType();
      Map<Long, SessionItem> sessionIdToSessionItems = mySessionsManager.getSessionIdToSessionItems();
      boolean isStartupTask = mySessionsManager.isCurrentTaskStartup();
      ProfilerTaskLauncher.launchProfilerTask(selectedTaskType, isStartupTask, getTaskHandlers(), getSession(), sessionIdToSessionItems,
                                              myCreateTaskTab, myIdeServices);
    }
  }

  private void profilingSessionChanged() {
    Common.Session newSession = mySessionsManager.getProfilingSession();
    // Stops the previous profiling session if it is active
    if (!Common.Session.getDefaultInstance().equals(myProfilingSession)) {
      assert SessionsManager.isSessionAlive(myProfilingSession);
      // TODO: If there are multiple instances of StudioProfiers class, we should call stopProfiling() only once.
      myProfilers.forEach(profiler -> profiler.stopProfiling(myProfilingSession));
    }

    myProfilingSession = newSession;

    if (!Common.Session.getDefaultInstance().equals(myProfilingSession)
        // When multiple instances of Studio are open, there may be multiple instances of StudioProfiers class. Each of them
        // registers to PROFILING_SESSION aspect and this method will be called on each of the instance. We should not call
        // startProfiling() if the device isn't the one where the new session is from. Ideally, we should call startProfiling()
        // only once.
        && myDevice != null && myDevice.getDeviceId() == myProfilingSession.getStreamId()) {
      assert SessionsManager.isSessionAlive(myProfilingSession);
      myProfilers.forEach(profiler -> profiler.startProfiling(myProfilingSession));
      myIdeServices.getFeatureTracker().trackProfilingStarted();
      if (getAgentData(myProfilingSession).getStatus() == AgentData.Status.ATTACHED) {
        getIdeServices().getFeatureTracker().trackAdvancedProfilingStarted();
      }
    }
  }

  private boolean startupProfilingStarted() {
    return startupCpuProfilingStarted() || startupMemoryProfilingStarted();
  }

  private boolean startupMemoryProfilingStarted() {
    List<Common.Event> events =
      MemoryProfiler.getNativeHeapEventsForSessionSortedByTimestamp(myClient, mySelectedSession, new Range(Long.MIN_VALUE, Long.MAX_VALUE));
    if (events.isEmpty()) {
      return false;
    }
    Trace.TraceStartStatus lastStartStatus = events.get(events.size() - 1).getTraceStatus().getTraceStartStatus();
    // If we are ongoing, and we started before the process then we have a startup session.
    return lastStartStatus.getStatus() == Trace.TraceStartStatus.Status.SUCCESS &&
           lastStartStatus.getStartTimeNs() <= mySelectedSession.getStartTimestamp();
  }

  /**
   * Checks whether startup CPU Profiling started for the selected session by making RPC call to perfd.
   */
  private boolean startupCpuProfilingStarted() {
    List<Trace.TraceInfo> traceInfoList = CpuProfiler.getTraceInfoFromSession(myClient, mySelectedSession);
    if (!traceInfoList.isEmpty()) {
      Trace.TraceInfo lastTraceInfo = traceInfoList.get(traceInfoList.size() - 1);
      return lastTraceInfo.getConfiguration().getInitiationType() == Trace.TraceInitiationType.INITIATED_BY_STARTUP;
    }

    return false;
  }

  /**
   * Chooses a process among all potential candidates starting from the project's app process, and then the one previously used. If no
   * candidate is available and no preferred process has been configured, select the first available process.
   */
  @Nullable
  private Common.Process getPreferredProcess(List<Common.Process> processes) {
    if (processes == null || processes.isEmpty()) {
      return null;
    }

    // Prefer the project's app if available.
    if ((getIdeServices().getFeatureConfig().isTaskBasedUxEnabled() || myAutoProfilingEnabled) && myPreference.processName != null) {
      for (Common.Process process : processes) {
        if (process.getName().equals(myPreference.processName) &&
            process.getState() == Common.Process.State.ALIVE &&
            myPreference.processFilter.test(process)) {
          myIdeServices.getFeatureTracker().trackAutoProfilingSucceeded();
          myAutoProfilingEnabled = false;
          return process;
        }
      }
    }

    // Next, prefer the one previously used, either selected by user or automatically (even if the process has switched states)
    if (myProcess != null) {
      for (Common.Process process : processes) {
        if (isSameProcess(myProcess, process) &&
            // The profilers only keep the same process under the following scenarios:
            // 1. The process's states have not changed
            // 2. The process went from alive to dead. (e.g. the process is killed, the device is disconnected)
            // If a identical process goes from dead to alive, it is most likely due to a device being reconnected, or an emulator snapshot
            // being booted with a previously running process. We don't want to select and profiler that process in those cases.
            (myProcess.getState() == process.getState() ||
             (myProcess.getState() == Common.Process.State.ALIVE && process.getState() == Common.Process.State.DEAD))) {
          return process;
        }
      }
    }

    return null;
  }

  @NotNull
  private AgentData getAgentData(@NotNull Common.Session session) {
    AgentData agentData = AgentData.getDefaultInstance();
    if (Common.Session.getDefaultInstance().equals(session)) {
      return agentData;
    }
    // Get agent data for requested session.
    GetEventGroupsRequest request = GetEventGroupsRequest.newBuilder()
      .setKind(Event.Kind.AGENT)
      .setStreamId(session.getStreamId())
      .setPid(session.getPid())
      .build();
    GetEventGroupsResponse response = myClient.getTransportClient().getEventGroups(request);
    for (EventGroup group : response.getGroupsList()) {
      agentData = group.getEvents(group.getEventsCount() - 1).getAgentData();
    }
    return agentData;
  }

  /**
   * @return true if the processes are the same, false otherwise. The reason Objects.equals is not used here is because the states could
   * have changed between process1 and process2, but they should be considered the same as long as we have matching pids and names, so we
   * don't reset the stage.
   */
  private static boolean isSameProcess(@Nullable Common.Process process1, @Nullable Common.Process process2) {
    return process1 != null &&
           process2 != null &&
           process1.getPid() == process2.getPid() && process1.getName().equals(process2.getName()) &&
           // pid and name are not enough, because emulator snapshot could try to restore previous pid of the app.
           process1.getStartTimestampNs() == process2.getStartTimestampNs();
  }

  public List<Common.Process> getProcesses() {
    List<Common.Process> processes = myProcesses.get(myDevice);
    return processes == null ? ImmutableList.of() : processes;
  }

  @NotNull
  public Stage getStage() {
    return myStage;
  }

  @NotNull
  public ProfilerClient getClient() {
    return myClient;
  }

  @NotNull
  public SessionsManager getSessionsManager() {
    return mySessionsManager;
  }

  /**
   * @return the active session, otherwise {@link Common.Session#getDefaultInstance()} if no session is currently being profiled.
   */
  @NotNull
  public Common.Session getSession() {
    return mySelectedSession;
  }

  /**
   * @return map of task types to their respective task handlers.
   */
  public Map<ProfilerTaskType, ProfilerTaskHandler> getTaskHandlers() {
    return myTaskHandlers;
  }

  /**
   * Return the selected app's package name if present, otherwise returns empty string.
   * <p>
   * <p>TODO (78597376): Clean up the method to make it reusable.</p>
   */
  @NotNull
  public String getSelectedAppName() {
    String name = "";
    if (!getSession().equals(Common.Session.getDefaultInstance())) {
      name = mySessionsManager.getSelectedSessionMetaData().getSessionName();
    }
    else if (myProcess != null) {
      name = myProcess.getName();
    }
    // The selected profiling name could be android.com.test (Google Pixel), remove the phone name.
    String[] nameSplit = name.split(" \\(", 2);
    return nameSplit.length > 0 ? nameSplit[0] : "";
  }

  public void setStage(@NotNull Stage stage) {
    myStage.exit();
    getTimeline().getSelectionRange().clear();
    myStage = stage;
    myStage.getStudioProfilers().getUpdater().reset();
    myStage.enter();
    this.changed(ProfilerAspect.STAGE);
  }

  @NotNull
  public StreamingTimeline getTimeline() {
    return myTimeline;
  }

  @Nullable
  public Common.Device getDevice() {
    return myDevice;
  }

  @Nullable
  public Common.Process getProcess() {
    return myProcess;
  }

  /**
   * Returns the support level for the live process of given PID. Assumes the process is running.
   * If the assumption cannot be guaranteed, use `getProcessForStreamIdPidTimestamp`, which may be slower.
   */
  @NotNull
  public SupportLevel getLiveProcessSupportLevel(int pid) {
    return myProcesses.values().stream().flatMap(Collection::stream)
      .filter(p -> p.getPid() == pid).findFirst()
      .map(p -> SupportLevel.of(p.getExposureLevel()))
      .orElse(SupportLevel.NONE);
  }

  /**
   * Returns the process protobuf object from the given stream, of the given PID, at the given timestamp.
   */
  @NotNull
  private Common.Process getProcessForStreamIdPidTimestamp(long streamId, int pid, long timestamp) {
    GetEventGroupsRequest request = GetEventGroupsRequest.newBuilder()
      .setKind(Event.Kind.PROCESS)
      .setStreamId(streamId)
      .setPid(pid)
      .setToTimestamp(timestamp)
      .build();
    GetEventGroupsResponse response = myClient.getTransportClient().getEventGroups(request);
    if (response.getGroupsCount() == 1) {
      Common.Event processEvent = StreamQueryUtils.getHighestExposureEventForLastProcess(response.getGroups(0));
      if (processEvent != null) {
        return processEvent.getProcess().getProcessStarted().getProcess();
      }
    }
    if (pid != 0) {
      // When PID is 0, e.g., during initialization of profilers, it's expected that no process is found.
      getLogger().warn("Cannot find the unique process for the given criteria.");
    }
    return Common.Process.getDefaultInstance();
  }

  @NotNull
  public SupportLevel getSupportLevelForSession(Common.Session session) {
    return SupportLevel.of(
      getProcessForStreamIdPidTimestamp(session.getStreamId(), session.getPid(), session.getStartTimestamp()).getExposureLevel());
  }

  @NotNull
  public SupportLevel getSelectedSessionSupportLevel() {
    return getSupportLevelForSession(mySessionsManager.getSelectedSession());
  }

  public boolean isAgentAttached() {
    return myAgentData.getStatus() == AgentData.Status.ATTACHED;
  }

  @NotNull
  public AgentData getAgentData() {
    return myAgentData;
  }

  public List<StudioProfiler> getProfilers() {
    return myProfilers;
  }

  @NotNull
  public IdeProfilerServices getIdeServices() {
    return myIdeServices;
  }

  public Updater getUpdater() {
    return myUpdater;
  }

  public AxisComponentModel getViewAxis() {
    return myViewAxis;
  }

  /**
   * Return the list of stages that target a specific profiler, which a user might want to jump
   * between. This should exclude things like the top-level profiler stage, null stage, etc.
   */
  public List<Class<? extends Stage>> getDirectStages() {
    ImmutableList.Builder<Class<? extends Stage>> listBuilder = ImmutableList.builder();
    listBuilder.add(CpuProfilerStage.class);
    listBuilder.add(MainMemoryProfilerStage.class);
    boolean isPowerProfilerDisabled =
      getIdeServices().getFeatureConfig().getSystemTracePowerProfilerDisplayMode() == PowerProfilerDisplayMode.HIDE;

    // Show the custom event stage in the dropdown list of profiling options when enabled
    if (getIdeServices().getFeatureConfig().isCustomEventVisualizationEnabled()) {
      listBuilder.add(CustomEventProfilerStage.class);
    }
    return listBuilder.build();
  }

  @NotNull
  public Class<? extends Stage> getStageClass() {
    return myStage.getClass();
  }

  @VisibleForTesting
  public void addTaskHandler(ProfilerTaskType taskType, ProfilerTaskHandler taskHandler) {
    myTaskHandlers.putIfAbsent(taskType, taskHandler);
  }

  // TODO: Unify with how monitors expand.
  public void setNewStage(Class<? extends Stage> clazz) {
    try {
      Constructor<? extends Stage> constructor = clazz.getConstructor(StudioProfilers.class);
      Stage stage = constructor.newInstance(this);
      setStage(stage);
    }
    catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
      // will not happen
    }
  }

  private StreamingStage createDefaultStage() {
    return new NullMonitorStage(this);
  }

  @NotNull
  public static String buildSessionName(@NotNull Common.Device device, @NotNull Common.Process process) {
    return String.format("%s (%s)", process.getName(), buildDeviceName(device));
  }

  /**
   * Opens the Profiler task tab (if it already has been created).
   */
  public void openTaskTab() {
    myOpenTaskTab.run();
  }

  /**
   * Mirrors AndroidProfilerToolWindow#getDeviceDisplayName but works with a {@link Common.Device}.
   */
  @NotNull
  public static String buildDeviceName(@NotNull Common.Device device) {
    StringBuilder deviceNameBuilder = new StringBuilder();
    String manufacturer = device.getManufacturer();
    String model = device.getModel();
    String serial = device.getSerial();
    String suffix = String.format("-%s", serial);
    if (model.endsWith(suffix)) {
      model = model.substring(0, model.length() - suffix.length());
    }
    if (!StringUtil.isEmpty(manufacturer) && !model.toUpperCase(Locale.US).startsWith(manufacturer.toUpperCase(Locale.US))) {
      deviceNameBuilder.append(manufacturer);
      deviceNameBuilder.append(" ");
    }
    deviceNameBuilder.append(model);

    return deviceNameBuilder.toString();
  }

  /**
   * Return the start and end timestamps for the artificial session created for the given imported file.
   * <p>
   * For each imported file, an artificial session is created. The start timestamp will be used as the
   * session's ID. Therefore, this function returns a nearly unique hash as the start timestamp for each file.
   * <p>
   * The range constructed by the two timestamps (after casting to microseconds) should still include the start
   * timestamp in nanoseconds because our code base shares much of live session's logic to handle imported
   * files. The two timestamps will construct a Range object. As the Range class uses microseconds, the
   * range may become a point when nanoseconds are casted into microseconds if it's too short, and the
   * nanosecond-timestamp may fall out of it. Therefore, this method makes the range one microsecond long
   * to avoid a point-range after casting.
   * <p>
   * This method avoid negative timestamps which may be counter-intuitive.
   */
  public static Pair<Long, Long> computeImportedFileStartEndTimestampsNs(File file) {
    long hash = normalizeHash(Hashing.sha256().hashString(file.getAbsolutePath(), StandardCharsets.UTF_8).asLong());
    // Avoid Long.MAX_VALUE which as the end timestamp means ongoing in transport pipeline.
    if (hash == Long.MAX_VALUE || hash == Long.MIN_VALUE || hash == Long.MIN_VALUE + 1) {
      hash /= 2;
    }
    // Avoid negative values.
    if (hash < 0) {
      hash = -hash;
    }

    // An IEEE 754 64 bit floating point number (which has 52 bits, plus 1 implied) can exactly represent integers with an absolute value
    // of less than or equal to 2^53. The largest hash is 2 ^ 63 - 1, in nanoseconds, which is 2 ^ 60 in microseconds. So we need a range
    // that's larger than 2 ^ (60 - 53) to guarantee the range's start and end are different in microseconds of double type. 1 second is
    // 10 ^ 6 microsecond that can satisfy this condition.
    // For example, 9060106487899244232L and 9060106487899245232L in nanoseconds (different by 1000) will be converted to the same
    // microseconds in double type number 9.060106487899244E15.
    long rangeNs = TimeUnit.SECONDS.toNanos(1);

    // Make sure (hash + rangeNs) as the end timestamp doesn't overflow.
    if (hash >= Long.MAX_VALUE - rangeNs) {
      hash -= rangeNs;
    }
    return new Pair<>(hash, hash + rangeNs);
  }

  /***
   * This method will eliminate errors in the system due to possible loss of precision during conversion.
   *
   * Profilers code converts the range's start and end timestamps between nanoseconds as long and microseconds as double. The conversion
   * may lose precision. For example, 9097726376199135381L will be converted to 9.097726376199136E15, which will then be converted to
   * 9097726376199136000L. As the start timestamp is used as both the range's start and the import event timestamp, the loss of precision
   * may cause the event fall out of range (the event's timestamp isn't being converted). (see details in b/311035879).
   * @param originalHash
   * @return long
   */
  private static long normalizeHash(long originalHash) {
    double hashMs = TimeUnit.NANOSECONDS.toMicros(originalHash);
    return TimeUnit.MICROSECONDS.toNanos((long)hashMs);
  }
}
