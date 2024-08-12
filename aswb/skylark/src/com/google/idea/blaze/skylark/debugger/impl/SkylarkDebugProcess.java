/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.skylark.debugger.impl;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.starlarkdebugging.StarlarkDebuggingProtos;
import com.google.devtools.build.lib.starlarkdebugging.StarlarkDebuggingProtos.ContinueExecutionRequest;
import com.google.devtools.build.lib.starlarkdebugging.StarlarkDebuggingProtos.DebugEvent;
import com.google.devtools.build.lib.starlarkdebugging.StarlarkDebuggingProtos.DebugEvent.PayloadCase;
import com.google.devtools.build.lib.starlarkdebugging.StarlarkDebuggingProtos.DebugRequest;
import com.google.devtools.build.lib.starlarkdebugging.StarlarkDebuggingProtos.EvaluateRequest;
import com.google.devtools.build.lib.starlarkdebugging.StarlarkDebuggingProtos.Location;
import com.google.devtools.build.lib.starlarkdebugging.StarlarkDebuggingProtos.PauseReason;
import com.google.devtools.build.lib.starlarkdebugging.StarlarkDebuggingProtos.PauseThreadRequest;
import com.google.devtools.build.lib.starlarkdebugging.StarlarkDebuggingProtos.PausedThread;
import com.google.devtools.build.lib.starlarkdebugging.StarlarkDebuggingProtos.SetBreakpointsRequest;
import com.google.devtools.build.lib.starlarkdebugging.StarlarkDebuggingProtos.StartDebuggingRequest;
import com.google.devtools.build.lib.starlarkdebugging.StarlarkDebuggingProtos.Stepping;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator.XEvaluationCallback;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XSuspendContext;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Core class controlling skylark debugging behavior. Interfaces with an {@link XDebugSession},
 * responsible for the UI and some other state.
 */
public class SkylarkDebugProcess extends XDebugProcess {

  private static final Logger logger = Logger.getInstance(SkylarkDebugProcess.class);

  private final Project project;
  private final ExecutionResult executionResult;
  private final DebugClientTransport transport;

  // TODO(brendandouglas): Here for backwards-compatibility, remove in v2018.10+
  private volatile boolean debuggingStarted = false;

  // SkylarkLineBreakpointType extends from XLineBreakpointTypeBase which uses raw
  // XBreakpointProperties. The raw use of XBreakpointProperties needs to propagate to all affected
  // classes. Check XLineBreakpointTypeBase again after #api212.
  @SuppressWarnings("rawtypes")
  // state shared with debug server
  private final ConcurrentMap<Location, XLineBreakpoint<XBreakpointProperties>> lineBreakpoints =
      new ConcurrentHashMap<>();

  private final ConcurrentMap<Long, PausedThreadState> pausedThreads = new ConcurrentHashMap<>();
  // the currently-stepping thread gets priority in the UI -- we always grab focus when it's paused
  private volatile long currentlySteppingThreadId = 0;

  public SkylarkDebugProcess(XDebugSession session, ExecutionResult executionResult, int port) {
    super(session);
    this.project = session.getProject();
    this.executionResult = executionResult;
    this.transport = new DebugClientTransport(this, port);

    session.setPauseActionSupported(true);
  }

  Collection<PausedThreadState> getPausedThreads() {
    return ImmutableList.copyOf(pausedThreads.values());
  }

  @Override
  public XBreakpointHandler<?>[] getBreakpointHandlers() {
    return new XBreakpointHandler<?>[] {new SkylarkLineBreakpointHandler(this)};
  }

  @Override
  public XDebuggerEditorsProvider getEditorsProvider() {
    return new SkylarkDebuggerEditorsProvider();
  }

  @Override
  protected ProcessHandler doGetProcessHandler() {
    return executionResult.getProcessHandler();
  }

  @Override
  public ExecutionConsole createConsole() {
    return executionResult.getExecutionConsole();
  }

  @Override
  public void sessionInitialized() {
    waitForConnection();
    ProcessHandler processHandler = getSession().getRunContentDescriptor().getProcessHandler();
    processHandler.addProcessListener(
        new ProcessAdapter() {
          @Override
          public void processWillTerminate(ProcessEvent event, boolean willBeDestroyed) {
            if (transport.isConnected()) {
              // unset breakpoints and resume all threads prior to stopping debugger, otherwise the
              // interrupt signal won't be properly handled
              clearBreakpoints();
              startStepping(null, Stepping.NONE);
            }
          }
        });
  }

  /** Returns false if the blaze process is terminating or already terminated. */
  boolean isProcessAlive() {
    ProcessHandler handler = getProcessHandler();
    return !handler.isProcessTerminated() && !handler.isProcessTerminating();
  }

  /** Returns true if the transport socket still appears to be connected. */
  private boolean isConnected() {
    return transport.isConnected();
  }

  @Override
  public void stop() {
    transport.close();
  }

  private void waitForConnection() {
    ProgressManager.getInstance()
        .run(
            new Task.Backgroundable(project, "Connecting To Debugger", /* canBeCanceled= */ false) {
              @Override
              public void run(ProgressIndicator indicator) {
                indicator.setText("Waiting for connection...");
                boolean success = transport.waitForConnection();
                if (!success) {
                  reportError("Failed to connect to the debugger");
                  transport.close();
                  getSession().stop();
                  return;
                }
                init();
              }
            });
  }

  private void init() {
    getSession().rebuildViews();
    registerBreakpoints();
    boolean started =
        assertResponseType(
            transport.sendRequest(
                DebugRequest.newBuilder()
                    .setStartDebugging(StartDebuggingRequest.newBuilder().build())),
            PayloadCase.START_DEBUGGING);
    if (started) {
      debuggingStarted = true;
    } else {
      // abort the debugging session entirely if we couldn't successfully initialize
      transport.close();
      getSession().stop();
    }
  }

  private void registerBreakpoints() {
    SetBreakpointsRequest.Builder request = SetBreakpointsRequest.newBuilder();
    lineBreakpoints.forEach((l, b) -> request.addBreakpoint(getBreakpointProto(l, b)));
    assertResponseType(
        transport.sendRequest(DebugRequest.newBuilder().setSetBreakpoints(request)),
        PayloadCase.SET_BREAKPOINTS);
  }

  private void clearBreakpoints() {
    lineBreakpoints.clear();
    registerBreakpoints();
  }

  // Check XLineBreakpointTypeBase for raw use of XBreakpointProperties after #api212.
  @SuppressWarnings("rawtypes")
  private static StarlarkDebuggingProtos.Breakpoint getBreakpointProto(
      Location location, XLineBreakpoint<XBreakpointProperties> breakpoint) {
    StarlarkDebuggingProtos.Breakpoint.Builder builder =
        StarlarkDebuggingProtos.Breakpoint.newBuilder().setLocation(location);
    String condition = getConditionExpression(breakpoint);
    if (condition != null) {
      builder.setExpression(condition);
    }
    return builder.build();
  }

  @Nullable
  private static String getConditionExpression(XBreakpoint<?> breakpoint) {
    return breakpoint.getConditionExpression() == null
        ? null
        : breakpoint.getConditionExpression().getExpression();
  }

  private void reportError(StarlarkDebuggingProtos.Error error) {
    reportError(error.getMessage());
  }

  private void reportError(String message) {
    if (isConnected() && isProcessAlive()) {
      getSession().reportError(message);
    }
  }

  /**
   * If the response doesn't match the expected type, or is an error event, log an error.
   *
   * @return false if no response was received, or the response type doesn't match that expected.
   */
  private boolean assertResponseType(@Nullable DebugEvent response, PayloadCase expectedType) {
    if (response == null) {
      reportError(String.format("No '%s' response received from the debugger", expectedType));
      return false;
    }
    if (expectedType.equals(response.getPayloadCase())) {
      return true;
    }
    if (response.hasError()) {
      reportError(response.getError());
    } else {
      String message =
          String.format(
              "Expected response type '%s', but got '%s'", expectedType, response.getPayloadCase());
      reportError(message);
    }
    return false;
  }

  // Check XLineBreakpointTypeBase for raw use of XBreakpointProperties after #api212.
  @SuppressWarnings("rawtypes")
  private Location convertLocation(XLineBreakpoint<XBreakpointProperties> breakpoint) {
    // TODO(brendandouglas): handle local changes?
    return Location.newBuilder()
        .setLineNumber(breakpoint.getLine() + 1)
        .setPath(breakpoint.getPresentableFilePath())
        .build();
  }

  @Override
  public void startStepOver(@Nullable XSuspendContext context) {
    startStepping(context, Stepping.OVER);
  }

  @Override
  public void startStepInto(@Nullable XSuspendContext context) {
    startStepping(context, Stepping.INTO);
  }

  @Override
  public void startStepOut(@Nullable XSuspendContext context) {
    startStepping(context, Stepping.OUT);
  }

  @Override
  public void resume(@Nullable XSuspendContext context) {
    // unpausing only a single thread isn't well supported by the debugging API, so pass through a
    // null suspect context, indicating all threads should be unpaused
    startStepping(null, Stepping.NONE);
  }

  private void startStepping(@Nullable XSuspendContext context, Stepping stepping) {
    if (!isConnected()) {
      return;
    }
    ApplicationManager.getApplication()
        .executeOnPooledThread(() -> doStartStepping(context, stepping));
  }

  /** Blocks waiting for a response from the debugger, so must be called on a worker thread. */
  private void doStartStepping(@Nullable XSuspendContext context, Stepping stepping) {
    long threadId = getThreadId(context);
    if (threadId == 0 && stepping != Stepping.NONE) {
      // TODO(brendandouglas): cache suspended threads here, and apply stepping behavior to all?
      return;
    }
    transport.sendRequest(
        DebugRequest.newBuilder()
            .setContinueExecution(
                ContinueExecutionRequest.newBuilder()
                    .setThreadId(threadId)
                    .setStepping(stepping)
                    .build()));

    currentlySteppingThreadId = threadId;

    // this is necessary because we yet aren't reliably informed of thread death. If the thread
    // finishes without triggering the stepping condition, we need to remind the debugger that there
    // could still be suspended threads remaining.
    scheduleWakeupIfNecessary(2000);
  }

  private void scheduleWakeupIfNecessary(int delayMillis) {
    if (pausedThreads.size() < 2) {
      // if no other threads are paused, the UI state will already be up-to-date
      return;
    }
    @SuppressWarnings({"unused", "nullness"})
    Future<?> possiblyIgnoredError =
        AppExecutorUtil.getAppScheduledExecutorService()
            .schedule(this::wakeUpUiIfNecessary, delayMillis, TimeUnit.MILLISECONDS);
  }

  @Override
  public void startPausing() {
    if (!isConnected()) {
      return;
    }
    // pause all threads
    transport.sendRequest(
        DebugRequest.newBuilder().setPauseThread(PauseThreadRequest.newBuilder()));
  }

  private long getThreadId(@Nullable XSuspendContext context) {
    if (context instanceof SkylarkSuspendContext) {
      return ((SkylarkSuspendContext) context).getActiveExecutionStack().getThreadId();
    }
    return 0;
  }

  // Check XLineBreakpointTypeBase for raw use of XBreakpointProperties after #api212.
  @SuppressWarnings("rawtypes")
  void addBreakpoint(XLineBreakpoint<XBreakpointProperties> breakpoint) {
    lineBreakpoints.put(convertLocation(breakpoint), breakpoint);
    if (isConnected()) {
      registerBreakpoints();
    }
  }

  // Check XLineBreakpointTypeBase for raw use of XBreakpointProperties after #api212.
  @SuppressWarnings("rawtypes")
  void removeBreakpoint(XLineBreakpoint<XBreakpointProperties> breakpoint) {
    boolean changed = lineBreakpoints.remove(convertLocation(breakpoint)) != null;
    if (changed && isConnected()) {
      registerBreakpoints();
    }
  }

  /**
   * Sends an {@link EvaluateRequest} to the debug server for the current frame, passing the
   * response on to the {@link XEvaluationCallback}.
   */
  void evaluate(String expression, XEvaluationCallback callback) {
    try {
      evaluate(currentFrame(), expression, callback);
    } catch (SkylarkDebuggerException e) {
      callback.errorOccurred(e.getMessage());
    }
  }

  private void evaluate(SkylarkStackFrame frame, String expression, XEvaluationCallback callback) {
    EvaluateRequest request =
        EvaluateRequest.newBuilder().setThreadId(frame.threadId).setStatement(expression).build();
    doEvaluate(request, frame, callback);
  }

  private SkylarkStackFrame currentFrame() throws SkylarkDebuggerException {
    if (!isConnected()) {
      getSession().stop();
      throw new SkylarkDebuggerException("Disconnected");
    }
    SkylarkStackFrame frame = (SkylarkStackFrame) getSession().getCurrentStackFrame();
    if (frame == null) {
      throw new RuntimeException("Process is running");
    }
    return frame;
  }

  private void doEvaluate(
      EvaluateRequest request, SkylarkStackFrame frame, XEvaluationCallback callback) {
    DebugEvent response = transport.sendRequest(DebugRequest.newBuilder().setEvaluate(request));
    if (response == null) {
      callback.errorOccurred("No response from the Skylark debugger");
      return;
    }
    if (response.hasError()) {
      callback.errorOccurred(response.getError().getMessage());
      return;
    }
    checkState(response.getPayloadCase() == PayloadCase.EVALUATE);
    callback.evaluated(SkylarkDebugValue.fromProto(frame, response.getEvaluate().getResult()));
  }

  @Nullable
  List<StarlarkDebuggingProtos.Value> getChildren(
      long threadId, StarlarkDebuggingProtos.Value value) {
    PausedThreadState threadState = pausedThreads.get(threadId);
    if (threadState == null) {
      return null;
    }
    return threadState.childCache.getChildren(transport, value);
  }

  void listFrames(long threadId, XExecutionStack.XStackFrameContainer container) {
    DebugEvent response =
        transport.sendRequest(
            DebugRequest.newBuilder()
                .setListFrames(
                    StarlarkDebuggingProtos.ListFramesRequest.newBuilder().setThreadId(threadId)));
    if (response == null) {
      container.errorOccurred("No frames data received from the Skylark debugger");
      return;
    }
    if (response.hasError()) {
      container.errorOccurred(response.getError().getMessage());
      return;
    }
    checkState(response.getPayloadCase() == PayloadCase.LIST_FRAMES);
    List<StarlarkDebuggingProtos.Frame> frames = response.getListFrames().getFrameList();
    container.addStackFrames(
        frames.stream().map(f -> convert(threadId, f)).collect(Collectors.toList()), true);
  }

  private SkylarkStackFrame convert(long threadId, StarlarkDebuggingProtos.Frame frame) {
    return new SkylarkStackFrame(this, threadId, frame);
  }

  void handleEvent(StarlarkDebuggingProtos.DebugEvent event) {
    switch (event.getPayloadCase()) {
      case ERROR:
        reportError(event.getError());
        return;
      case THREAD_PAUSED:
        handleThreadPausedEvent(event.getThreadPaused().getThread());
        return;
      case THREAD_CONTINUED:
        pausedThreads.remove(event.getThreadContinued().getThreadId());
        return;
      case LIST_FRAMES:
      case EVALUATE:
      case SET_BREAKPOINTS:
      case CONTINUE_EXECUTION:
      case START_DEBUGGING:
      case PAUSE_THREAD:
      case GET_CHILDREN:
        logger.error("Can't handle a response event without the associated request");
        return;
      case PAYLOAD_NOT_SET:
        break; // intentional fall through to error reporting
    }
    reportError(
        "Unrecognized or unset skylark debugger response type. Try upgrading to a newer "
            + "version of the plugin.");
  }

  /**
   * The debugger UI doesn't handle multiple concurrently paused threads well. If the 'primary'
   * thread dies, or never resumes, we need to manually remind it that there may be other suspended
   * threads remaining.
   *
   * <p>See RemoteDebugger#processThreadEvent for an upstream example.
   */
  private void wakeUpUiIfNecessary() {
    if (getSession().isSuspended()) {
      // we already have an active thread
      return;
    }
    PausedThreadState state = Iterables.getFirst(getPausedThreads(), null);
    if (state != null) {
      notifyThreadPaused(state, true);
    }
  }

  // Check XLineBreakpointTypeBase for raw use of XBreakpointProperties after #api212.
  @SuppressWarnings("rawtypes")
  private void handleThreadPausedEvent(PausedThread thread) {
    // ignore threads paused during initialization
    if (!debuggingStarted && thread.getPauseReason() == PauseReason.ALL_THREADS_PAUSED) {
      // Temporary backwards-compatibility code. TODO(brendandouglas): remove in v2018.10+
      return;
    }
    if (thread.getPauseReason() == PauseReason.INITIALIZING) {
      return;
    }

    if (thread.getPauseReason() != PauseReason.CONDITIONAL_BREAKPOINT_ERROR) {
      notifyThreadPaused(thread);
      return;
    }
    XLineBreakpoint<XBreakpointProperties> breakpoint =
        lineBreakpoints.get(thread.getLocation().toBuilder().setColumnNumber(0).build());
    if (breakpoint == null) {
      notifyThreadPaused(thread);
    } else {
      handleConditionalBreakpointError(breakpoint, thread);
    }
  }

  private void notifyThreadPaused(PausedThread thread) {
    notifyThreadPaused(new PausedThreadState(thread), /* alwaysNotify= */ false);
  }

  // Check XLineBreakpointTypeBase for raw use of XBreakpointProperties after #api212.
  @SuppressWarnings("rawtypes")
  private void notifyThreadPaused(PausedThreadState threadState, boolean alwaysNotify) {
    pausedThreads.put(threadState.thread.getId(), threadState);
    XLineBreakpoint<XBreakpointProperties> breakpoint =
        lineBreakpoints.get(
            threadState.thread.getLocation().toBuilder().setColumnNumber(0).build());

    boolean isSuspended = getSession().isSuspended();
    if (!alwaysNotify && isSuspended && breakpoint != null) {
      // don't notify for subsequent breakpoint hits when we're already suspended. Otherwise we can
      // get 100s of threads stopping at a single breakpoint, kicking off a listFrames for each
      return;
    }

    SkylarkSuspendContext suspendContext = new SkylarkSuspendContext(this, threadState);
    if (breakpoint != null) {
      getSession().breakpointReached(breakpoint, null, suspendContext);
    } else if (alwaysNotify
        || threadState.thread.getId() == currentlySteppingThreadId
        || !isSuspended
        || individualThreadPausedByUser(threadState.thread.getPauseReason())) {
      getSession().positionReached(suspendContext);
    }
  }

  private boolean individualThreadPausedByUser(StarlarkDebuggingProtos.PauseReason reason) {
    switch (reason) {
      case STEPPING:
      case PAUSE_THREAD_REQUEST:
      case HIT_BREAKPOINT:
      case CONDITIONAL_BREAKPOINT_ERROR:
        return true;
      case INITIALIZING:
      case ALL_THREADS_PAUSED:
      case UNSET:
        return false;
      case UNRECOGNIZED:
    }
    reportError("Unrecognized pause reason. Try upgrading to a newer version of the plugin.");
    // default to returning true, so we don't leave the debugger in an unusable state
    return true;
  }

  // Check XLineBreakpointTypeBase for raw use of XBreakpointProperties after #api212.
  @SuppressWarnings("rawtypes")
  private void handleConditionalBreakpointError(
      XLineBreakpoint<XBreakpointProperties> breakpoint, PausedThread thread) {
    // TODO(brendandouglas): also navigate to the problematic breakpoint
    String error = thread.getConditionalBreakpointError().getMessage();
    String title = "Breakpoint Condition Error";
    String message =
        String.format(
            "Breakpoint: %s\nError: %s\nWould you like to stop at the breakpoint?",
            breakpoint.getType().getDisplayText(breakpoint), error);
    Ref<Boolean> stop = new Ref<>(true);
    ApplicationManager.getApplication()
        .invokeAndWait(
            () ->
                stop.set(
                    Messages.showYesNoDialog(project, message, title, Messages.getQuestionIcon())
                        == Messages.YES));
    if (stop.get()) {
      notifyThreadPaused(thread);
      return;
    }
    // else resume the thread
    transport.sendRequest(
        DebugRequest.newBuilder()
            .setContinueExecution(
                ContinueExecutionRequest.newBuilder()
                    .setThreadId(thread.getId())
                    .setStepping(Stepping.NONE)
                    .build()));
  }
}
