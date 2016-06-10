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
package com.android.tools.idea.editors.gfxtrace;

import com.android.annotations.concurrency.Immutable;
import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.editor.AndroidJavaDebugger;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.*;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.ui.breakpoints.FilteredRequestor;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.Time;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.concurrency.FutureResult;
import com.intellij.util.concurrency.Futures;
import com.sun.jdi.Value;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.request.MethodEntryRequest;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class GapiiLibraryLoader {

  @NotNull private static final Logger LOG = Logger.getInstance(GapiiLibraryLoader.class);

  private static final int MAX_CONNECT_ATTEMPTS = 20;
  private static final int GAPII_INIT_PORT = 9287; // TODO(valbulescu): Find free port instead of hoping for the best.
  private static final int TIME_BETWEEN_ATTEMPTS_MS = 250;
  private static final long LIB_INSTALLATION_TIMEOUT_MS = 20000;

  private IDevice myDevice;
  private Project myProject;

  public GapiiLibraryLoader(@NotNull Project project, @NotNull IDevice device) {
    myProject = project;
    myDevice = device;
  }

  private static List<Throwable> waitForFutures(long timeout, TimeUnit unit, Future... futures) {
    long startTime = System.nanoTime();
    List<Throwable> errors = Lists.newArrayList();
    for (Future future : futures) {
      try {
        future.get(startTime + unit.toNanos(timeout) - System.nanoTime(), TimeUnit.NANOSECONDS);
      } catch (Throwable t) {
        errors.add(t);
      }
    }
    return errors;
  }

  public void connectToProcessAndInstallLibraries(@NotNull String pkg, @NotNull final File... libs) throws Exception {
    long startTime = System.nanoTime();

    LOG.debug("Attaching to " + pkg);
    AndroidJavaDebugger debugger = new AndroidJavaDebugger();
    Client client = myDevice.getClient(pkg);

    Future<?> clientAttachFuture = EdtExecutorService.getInstance().submit(() -> debugger.attachToClient(myProject, client));
    clientAttachFuture.get();

    DebuggerSession debuggerSession = debugger.getDebuggerSession(client);
    DebugProcessImpl debugProcess = debuggerSession.getProcess();

    FutureResult<Void> evalCompletion = new FutureResult<>();
    FutureTask<Void> senderTask = new FutureTask<>(() -> createForwardAndSendFiles(libs));

    FilteredRequestor appOnCreateFilter = new ClassFilterRequestor("android.app.Application") {
      @Override
      public boolean processLocatableEvent(SuspendContextCommandImpl action, LocatableEvent event) throws EventProcessingException {
        if (!("onCreate".equals(event.location().method().name()))) {
          return false;
        }
        event.request().disable();
        LOG.debug("Application.onCreate() called, running library installation.");

        try {
          ApplicationManager.getApplication().executeOnPooledThread(senderTask);

          evaluate(CodeFragmentKind.CODE_BLOCK, JdwpSnippets.getLoaderSnippet(libs), action.getSuspendContext());
          evalCompletion.set(null);
        } catch (Throwable t) {
          evalCompletion.setException(t);
        }
        return false; // Pretend we haven't hit a breakpoint.
      }
    };

    debugProcess.getManagerThread().invokeAndWait(new DebuggerCommandImpl() {
      @Override
      protected void action() throws Exception {
        LOG.debug("Waiting for Application.onCreate() to be called.");
        MethodEntryRequest req = debugProcess.getRequestsManager().createMethodEntryRequest(appOnCreateFilter);
        req.enable();
      }
    });

    List<Throwable> errors = waitForFutures(LIB_INSTALLATION_TIMEOUT_MS, TimeUnit.MILLISECONDS, senderTask, evalCompletion);

    if (!evalCompletion.isDone()) {
      // evaluation could hang while e.g. waiting for a connection from the library sender.
      LOG.warn("Attempting to terminate evaluation.");
      debugProcess.getManagerThread().terminateAndInvoke(debugProcess.createStopCommand(true), 1000);
    }
    if (!senderTask.isDone()) {
      senderTask.cancel(true);
    }

    EdtExecutorService.getInstance().submit(() -> {
      debugProcess.getProcessHandler().detachProcess();
      ExecutionManager.getInstance(myProject).getContentManager().removeRunContent(
        DefaultDebugExecutor.getDebugExecutorInstance(),
        debuggerSession.getXDebugSession().getRunContentDescriptor()
      );
    });

    if (!errors.isEmpty()) {
      errors.stream().forEach(LOG::error);
      throw new RuntimeException(
        errors.stream().map(String::valueOf).collect(Collectors.joining(", ", "Library installation failed: ", "")), errors.get(0));
    }

    long durationMs = TimeUnit.MILLISECONDS.convert(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
    LOG.debug("Library installation took " + durationMs / 1e3 + "s");
  }

  private Value evaluate(CodeFragmentKind kind, String snippet, SuspendContextImpl suspendContext) throws EvaluateException {
    return ApplicationManager.getApplication().<Value, EvaluateException>runReadAction(
        () -> {
          EvaluationContext evaluationContext = new EvaluationContextImpl(
            suspendContext,
            suspendContext.getFrameProxy(),
            suspendContext.getFrameProxy().getStackFrame().thisObject()
          );

          ExpressionEvaluator evaluator = EvaluatorBuilderImpl.build(
            new TextWithImportsImpl(kind, snippet),
            null,
            null,
            myProject);

          return evaluator.evaluate(evaluationContext);
        }
    );
  }

  private Void createForwardAndSendFiles(File[] files) throws Exception {
    byte[] expectedHeader = JdwpSnippets.GAPII_HEADER.getBytes(Charsets.UTF_8);

    try {
      myDevice.createForward(GAPII_INIT_PORT, JdwpSnippets.GAPII_ABSTRACT_SOCKET, IDevice.DeviceUnixSocketNamespace.ABSTRACT);

      boolean headerReceived = false;
      for (int attempt = 1; ; attempt++) {
        Thread.sleep(TIME_BETWEEN_ATTEMPTS_MS);

        try (
          Socket clientSocket = new Socket("localhost", GAPII_INIT_PORT);
          OutputStream outStream = clientSocket.getOutputStream();
          InputStream inStream = clientSocket.getInputStream();
        ) {
          expect(expectedHeader, inStream);
          headerReceived = true;

          for (File file : files) {
            LOG.debug("Sending file " + file);
            Files.copy(file, outStream);
          }

          LOG.debug("Finished sending files.");
          return null;
        } catch (IOException ex) {
          if (headerReceived || attempt >= MAX_CONNECT_ATTEMPTS) {
            throw ex;
          }
        }
      }
    } finally {
      myDevice.removeForward(GAPII_INIT_PORT, JdwpSnippets.GAPII_ABSTRACT_SOCKET, IDevice.DeviceUnixSocketNamespace.ABSTRACT);
    }
  }

  private void expect(byte[] expected, InputStream inStream) throws IOException {
    byte[] actual = new byte[expected.length];
    if (ByteStreams.read(inStream, actual, 0, expected.length) < expected.length) {
      throw new IOException("Premature end of stream.");
    }
    if (!Arrays.equals(expected, actual)) {
      throw new IllegalStateException("Unexpected response.");
    }
  }
}
