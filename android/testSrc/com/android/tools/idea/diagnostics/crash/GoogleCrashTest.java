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
package com.android.tools.idea.diagnostics.crash;

import com.google.common.truth.Truth;
import com.intellij.openapi.project.IndexNotReadyException;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.*;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class GoogleCrashTest {
  public static final String STACK_TRACE =
    "\tat com.intellij.util.indexing.FileBasedIndexImpl.handleDumbMode(FileBasedIndexImpl.java:853)\n" +
    "\tat com.intellij.util.indexing.FileBasedIndexImpl.ensureUpToDate(FileBasedIndexImpl.java:802)\n" +
    "\tat com.intellij.psi.stubs.StubIndexImpl.processElements(StubIndexImpl.java:238)\n" +
    "\tat com.intellij.psi.stubs.StubIndex.process(StubIndex.java:76)\n" +
    "\tat com.intellij.psi.stubs.StubIndex.getElements(StubIndex.java:144)\n";

  private static final String SAMPLE_EXCEPTION =
    "com.intellij.openapi.project.IndexNotReadyException: Please change caller according to com.intellij.openapi.project.IndexNotReadyException documentation\n" +
    STACK_TRACE;

  @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
  private static final Throwable ourIndexNotReadyException = createExceptionFromDesc(SAMPLE_EXCEPTION, IndexNotReadyException.create());

  // Most of the tests do a future.get(), but since they are uploaded to a local server, they should complete relatively quickly.
  // This rule enforces a shorter timeout for the tests. If you are debugging, you probably want to comment this out.
  @Rule
  public TestRule myTimeout = new Timeout(15, TimeUnit.SECONDS);

  private GoogleCrash myReporter;
  private LocalTestServer myTestServer;

  @Before
  public void setup() throws Exception {
    int port = getFreePort();
    assertTrue("Could not obtain free port", port > 0);
    myTestServer = new LocalTestServer(port);
    myTestServer.start();

    double infiniteQps = 1000; // a high enough number for the tests here
    myReporter = new GoogleCrash("http://localhost:" + port + "/submit", UploadRateLimiter.create(infiniteQps));
  }

  @After
  public void tearDown() {
    myTestServer.stop();
  }

  @Test(expected = ExecutionException.class)
  public void checkServerErrorCaptured() throws Exception {
    myTestServer.setResponseSupplier(httpRequest -> new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST));
    CrashReport report = CrashReport.Builder.createForException(ourIndexNotReadyException)
      .setProduct("AndroidStudioTestProduct")
      .setVersion("1.2.3.4")
      .build();
    myReporter.submit(report).get();
    fail("The above get call should have failed");
  }

  @Test
  public void checkRateLimiting() {
    UploadRateLimiter mockLimiter = mock(UploadRateLimiter.class); // defaults to a rate limiter that denies all requests
    myReporter = new GoogleCrash("http://404", mockLimiter); // the actual address doesn't matter since we should've stopped long before..
    CrashReport report = CrashReport.Builder.createForException(ourIndexNotReadyException)
      .setProduct("AndroidStudioTestProduct")
      .setVersion("1.2.3.4")
      .build();
    try {
      myReporter.submit(report).getNow("123");
      fail("Should not be able to submit a report when the rate of crash upload exceeds the limit");
    }
    catch (CompletionException e) {
      Truth.assertThat(e.getCause().getMessage()).isEqualTo("Exceeded Quota of crashes that can be reported");
    }
  }

  private static int getFreePort() {
    try (ServerSocket s = new ServerSocket(0)) {
      return s.getLocalPort();
    }
    catch (IOException e) {
      return -1;
    }
  }

  // Performs a real upload to staging
  public static void main(String[] args) {
    GoogleCrash crash = new GoogleCrash();

    CompletableFuture<String> response = crash.submit(CrashReport.Builder.createForException(ourIndexNotReadyException).build());
    try {
      String reportId = response.get(20, TimeUnit.SECONDS);
      System.out.println("View report at http://go/crash-staging/" + reportId);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  // Copied from RenderErrorPanelTest
  @SuppressWarnings("ThrowableInstanceNeverThrown")
  private static Throwable createExceptionFromDesc(String desc, @Nullable Throwable throwable) {
    // First line: description and type
    Iterator<String> iterator = Arrays.asList(desc.split("\n")).iterator(); // Splitter.on('\n').split(desc).iterator();
    assertTrue(iterator.hasNext());
    final String first = iterator.next();
    assertTrue(iterator.hasNext());
    String message = null;
    String exceptionClass;
    int index = first.indexOf(':');
    if (index != -1) {
      exceptionClass = first.substring(0, index).trim();
      message = first.substring(index + 1).trim();
    } else {
      exceptionClass = first.trim();
    }

    if (throwable == null) {
      try {
        @SuppressWarnings("unchecked")
        Class<Throwable> clz = (Class<Throwable>)Class.forName(exceptionClass);
        if (message == null) {
          throwable = clz.newInstance();
        } else {
          Constructor<Throwable> constructor = clz.getConstructor(String.class);
          throwable = constructor.newInstance(message);
        }
      } catch (Throwable t) {
        if (message == null) {
          throwable = new Throwable() {
            @Override
            public String getMessage() {
              return first;
            }

            @Override
            public String toString() {
              return first;
            }
          };
        } else {
          throwable = new Throwable(message);
        }
      }
    }

    List<StackTraceElement> frames = new ArrayList<StackTraceElement>();
    Pattern outerPattern = Pattern.compile("\tat (.*)\\.([^.]*)\\((.*)\\)");
    Pattern innerPattern = Pattern.compile("(.*):(\\d*)");
    while (iterator.hasNext()) {
      String line = iterator.next();
      if (line.isEmpty()) {
        break;
      }
      Matcher outerMatcher = outerPattern.matcher(line);
      if (!outerMatcher.matches()) {
        fail("Line " + line + " does not match expected stactrace pattern");
      } else {
        String clz = outerMatcher.group(1);
        String method = outerMatcher.group(2);
        String inner = outerMatcher.group(3);
        if (inner.equals("Native Method")) {
          frames.add(new StackTraceElement(clz, method, null, -2));
        } else if (inner.equals("Unknown Source")) {
          frames.add(new StackTraceElement(clz, method, null, -1));
        } else {
          Matcher innerMatcher = innerPattern.matcher(inner);
          if (!innerMatcher.matches()) {
            fail("Trace parameter list " + inner + " does not match expected pattern");
          } else {
            String file = innerMatcher.group(1);
            int lineNum = Integer.parseInt(innerMatcher.group(2));
            frames.add(new StackTraceElement(clz, method, file, lineNum));
          }
        }
      }
    }

    throwable.setStackTrace(frames.toArray(new StackTraceElement[frames.size()]));

    // Dump stack back to string to make sure we have the same exception
    assertEquals(desc, getStackTrace(throwable));

    return throwable;
  }

  @NotNull
  private static String getStackTrace(@NotNull Throwable t) {
    final StringWriter stringWriter = new StringWriter();
    try (PrintWriter writer = new PrintWriter(stringWriter)) {
      t.printStackTrace(writer);
    }

    return stringWriter.toString().replace("\r", "");
  }
}
