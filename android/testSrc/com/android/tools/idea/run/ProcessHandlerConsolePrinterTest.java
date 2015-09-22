/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.intellij.execution.process.ProcessHandler;
import junit.framework.TestCase;
import org.mockito.InOrder;
import org.mockito.Mockito;

import static com.intellij.execution.process.ProcessOutputTypes.STDERR;
import static com.intellij.execution.process.ProcessOutputTypes.STDOUT;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link ProcessHandlerConsolePrinter}.
 */
public class ProcessHandlerConsolePrinterTest extends TestCase {

  public void testStdout() {
    ProcessHandler handler = mock(ProcessHandler.class);
    ProcessHandlerConsolePrinter printer = new ProcessHandlerConsolePrinter(handler);
    printer.stdout("text");
    Mockito.verify(handler).notifyTextAvailable("text\n", STDOUT);
  }

  public void testStderr() {
    ProcessHandler handler = mock(ProcessHandler.class);
    ProcessHandlerConsolePrinter printer = new ProcessHandlerConsolePrinter(handler);
    printer.stderr("text");
    Mockito.verify(handler).notifyTextAvailable("text\n", STDERR);
  }

  public void testSetProcessHandler() {
    ProcessHandlerConsolePrinter printer = new ProcessHandlerConsolePrinter(null);
    printer.stdout("stdout1");
    printer.stderr("stderr1");

    ProcessHandler handler = mock(ProcessHandler.class);
    printer.setProcessHandler(handler);
    // The stored messages are sent to the newly-set process handler.
    InOrder inOrder = Mockito.inOrder(handler);
    inOrder.verify(handler).notifyTextAvailable("stdout1\n", STDOUT);
    inOrder.verify(handler).notifyTextAvailable("stderr1\n", STDERR);

    printer.stdout("stdout2");
    // New messages are sent to the process handler.
    verify(handler).notifyTextAvailable("stdout2\n", STDOUT);
  }

  public void testSetProcessHandlerRepeatedly() {
    ProcessHandlerConsolePrinter printer = new ProcessHandlerConsolePrinter(null);
    printer.stdout("stdout1");
    printer.stderr("stderr1");

    printer.setProcessHandler(mock(ProcessHandler.class));
    printer.stdout("stdout2");

    ProcessHandler handler = mock(ProcessHandler.class);
    printer.setProcessHandler(handler);
    printer.stdout("stdout3");
    // New messages are sent to the last-set process handler.
    verify(handler).notifyTextAvailable("stdout3\n", STDOUT);
    // None of the earlier messages are sent to the last-set process handler.
    verifyNoMoreInteractions(handler);
  }

  public void testSetProcessHandlerWhenConstructedWithOne() {
    ProcessHandlerConsolePrinter printer = new ProcessHandlerConsolePrinter(mock(ProcessHandler.class));
    printer.stdout("stdout1");

    ProcessHandler handler = mock(ProcessHandler.class);
    printer.setProcessHandler(handler);
    printer.stdout("stdout2");

    // New messages are sent to the last-set process handler.
    verify(handler).notifyTextAvailable("stdout2\n", STDOUT);
    // Previous messages were not sent to the last-set process handler.
    verifyNoMoreInteractions(handler);
  }
}
