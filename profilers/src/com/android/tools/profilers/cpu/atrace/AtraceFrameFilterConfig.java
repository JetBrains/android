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
package com.android.tools.profilers.cpu.atrace;

/**
 * This class defines a set of properties that are used to filter frames.
 */
public class AtraceFrameFilterConfig {
  /**
   * Events that occur on the main thread to define when a frame starts. These events changed in M, and System Tracing in profilers
   * is only supporting O+ devices however to keep this class in sync with the systrace sibling keeping both here for reference.
   */
  public static final String APP_MAIN_THREAD_FRAME_ID_MPLUS = "Choreographer#doFrame";
  public static final String APP_RENDER_THREAD_FRAME_ID_MPLUS = "(DrawFrame|doFrame|queueBuffer)";

  private final String myIdentifierRegEx;
  private final int myThreadId;
  private final long myLongFrameTimingUs;

  /**
   * @param identifierRegEx   regular expression used to filter events by name.
   * @param threadId          id of the thread to enumerate events.
   * @param longFrameTimingUs any frame greater than this duration will be considered
   *                          {@link AtraceFrame.PerfClass#BAD} otherwise
   *                          {@link AtraceFrame.PerfClass#GOOD}.
   */
  public AtraceFrameFilterConfig(String identifierRegEx, int threadId, long longFrameTimingUs) {

    myIdentifierRegEx = identifierRegEx;
    myThreadId = threadId;
    myLongFrameTimingUs = longFrameTimingUs;
  }


  public String getIdentifierRegEx() {
    return myIdentifierRegEx;
  }

  public int getThreadId() {
    return myThreadId;
  }

  public long getLongFrameTimingUs() {
    return myLongFrameTimingUs;
  }
}
