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

import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.NullOutputReceiver;
import com.android.tools.idea.editors.gfxtrace.gapi.GapiPaths;
import com.android.tools.idea.run.editor.AndroidJavaDebugger;
import com.google.common.collect.Lists;
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
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.concurrency.FutureResult;
import com.sun.tools.jdi.StringReferenceImpl;
import com.sun.jdi.Value;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.request.MethodEntryRequest;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.*;

public class GapiiLibraryLoader {

  @NotNull private static final Logger LOG = Logger.getInstance(GapiiLibraryLoader.class);

  private static final String DEVICE_TMP_DIR = "/data/local/tmp";
  private static final long LIB_INSTALLATION_TIMEOUT_MS = 60000;

  private IDevice myDevice;
  private Project myProject;

  public GapiiLibraryLoader(@NotNull Project project, @NotNull IDevice device) {
    myProject = project;
    myDevice = device;
  }

  public void connectToProcessAndInstallLibraries(@NotNull String pkg) throws Exception {
    long startTime = System.nanoTime();

    LOG.debug("Attaching to " + pkg);
    AndroidJavaDebugger debugger = new AndroidJavaDebugger();
    Client client = myDevice.getClient(pkg);
    if (client == null) {
      throw new RuntimeException("Failed to attach to process.");
    }

    Future<?> clientAttachFuture = EdtExecutorService.getInstance().submit(() -> debugger.attachToClient(myProject, client));
    clientAttachFuture.get();

    DebuggerSession debuggerSession = debugger.getDebuggerSession(client);
    DebugProcessImpl debugProcess = debuggerSession.getProcess();

    FutureResult<Void> evalCompletion = new FutureResult<>();

    FilteredRequestor appOnCreateFilter = new ClassFilterRequestor("android.app.Application") {
      @Override
      public boolean processLocatableEvent(SuspendContextCommandImpl action, LocatableEvent event) throws EventProcessingException {
        if (!("onCreate".equals(event.location().method().name()))) {
          return false;
        }
        event.request().disable();
        LOG.debug("Application.onCreate() called, running library installation.");

        // Discover the running APK's ABI so we can pick the correct .so.
        String abi = myDevice.getAbis().get(0);
        try {
          // android.os.Build.CPU_ABI is marked as deprecated, but appears to be the only
          // way to know whether the application is running as 32 bit or 64 bit on a device
          // that supports both. Android uses VMRuntime.getRuntime().is64Bit() to pick
          // between android.os.Build.SUPPORTED_32_BIT_ABIS and
          // android.os.Build.SUPPORTED_64_BIT_ABIS, but this appears inaccessible to the
          // debugger.
          // TODO: Replace with something that isn't deprecated.
          Value res = evaluate(CodeFragmentKind.EXPRESSION,
                               "android.os.Build.CPU_ABI",
                               action.getSuspendContext());
          abi = ((StringReferenceImpl)res).value();
        }
        catch (Throwable t) {
          LOG.warn("Couldn't determine ABI from android.os.Build.CPU_ABI. Defaulting to device primary ABI.", t);
        }

        // Build a list of libraries that need installing.
        List<File> libraries = Lists.newArrayList();

        // libinterceptor.so was introduced at GAPID 3.0. We install it if it is found.
        try {
          libraries.add(GapiPaths.findInterceptorLibrary(abi));
        } catch (IOException ex) {
          LOG.info("Skipping libinterceptor.so: " + ex.getMessage());
        }

        // libgapii.so must be found.
        try {
          libraries.add(GapiPaths.findTraceLibrary(abi));
        } catch (IOException ex) {
          Exception e = new RuntimeException("Couldn't find libgapii.so for target ABI '" + abi + "'", ex);
          LOG.error(e);
          evalCompletion.setException(e);
          return false;
        }

        // Push each of the libraries, copy them to the package files directory, load them.
        try {
          for (File lib : libraries) {
            String remoteFilePath = new File(DEVICE_TMP_DIR, lib.getName()).getAbsolutePath();
            try {
              myDevice.pushFile(lib.getAbsolutePath(), remoteFilePath);
              evaluate(CodeFragmentKind.CODE_BLOCK, getLoaderSnippet(lib), action.getSuspendContext());
            } finally {
              myDevice.executeShellCommand("rm " + remoteFilePath, NullOutputReceiver.getReceiver());
            }
          }

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

    try {
      evalCompletion.get(LIB_INSTALLATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    } catch (TimeoutException ex) {
      debugProcess.getManagerThread().terminateAndInvoke(debugProcess.createStopCommand(true), 1000);
      throw ex;
    } finally {
      EdtExecutorService.getInstance().submit(() -> {
        debugProcess.getProcessHandler().detachProcess();
        ExecutionManager.getInstance(myProject).getContentManager().removeRunContent(
          DefaultDebugExecutor.getDebugExecutorInstance(),
          debuggerSession.getXDebugSession().getRunContentDescriptor()
        );
      });
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

  private static String getLoaderSnippet(File lib) {
    return
      "  String TAG = \"gapii-init\";\n" +
      "  String libName = \"" + lib.getName() + "\";\n" +
      "  java.io.File inFile = new java.io.File(\"" + DEVICE_TMP_DIR + "\", libName);\n" +
      "  java.io.File outFile = new java.io.File(((android.app.Application) this).getFilesDir(), libName);\n" +
      "  long libSize = inFile.length();\n" +
      "\n" +
      "  android.util.Log.d(TAG, String.format(\"Copying %d bytes to %s.\", libSize, outFile));\n" +
      "  java.nio.channels.FileChannel inChannel = new java.io.FileInputStream(inFile).getChannel();\n" +
      "  java.nio.channels.FileChannel outChannel = new java.io.FileOutputStream(outFile).getChannel();\n" +
      "  long remaining = libSize;\n" +
      "  while (remaining > 0) {\n" +
      "    remaining -= outChannel.transferFrom(inChannel, libSize - remaining, remaining); \n" +
      "  }\n" +
      "\n" +
      "  outChannel.close();\n" +
      "  inChannel.close();\n" +
      "\n" +
      "  android.util.Log.d(TAG, \"Library copied, loading.\");\n" +
      // Work around for loading libraries in the N previews. See http://b/29441142.
      "  if (android.os.Build.VERSION.SDK_INT == 24) {\n" + // 24 is N
      "    Runtime.getRuntime().doLoad(outFile.toString(), null);\n" +
      "  } else {\n" +
      "    System.load(outFile.toString());\n" +
      "  }\n";
  }
}
