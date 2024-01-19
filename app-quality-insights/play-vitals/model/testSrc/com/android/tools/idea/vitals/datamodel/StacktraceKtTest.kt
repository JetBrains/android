/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.vitals.datamodel

import com.android.tools.idea.insights.Blames
import com.android.tools.idea.insights.Caption
import com.android.tools.idea.insights.ExceptionStack
import com.android.tools.idea.insights.Frame
import com.android.tools.idea.insights.Stacktrace
import com.google.common.truth.Truth.assertThat
import org.junit.Test

private val SINGLE_TRACE_GROUP =
  """
      Exception java.lang.OutOfMemoryError:
        at java.util.Arrays.copyOf (Arrays.java:3766)
        at java.lang.AbstractStringBuilder.ensureCapacityInternal (AbstractStringBuilder.java:125)
        at java.lang.AbstractStringBuilder.append (AbstractStringBuilder.java:449)
        at java.lang.StringBuilder.append (StringBuilder.java:137)
        at asj.toString (:com.google.android.gms.dynamite_dynamitemodulesc@230914044@23.09.14 (190400-0):4)
        at java.lang.String.valueOf (String.java:3657)
        at aks.d (:com.google.android.gms.dynamite_dynamitemodulesc@230914044@23.09.14 (190400-0):6)
        at ajk.run (:com.google.android.gms.dynamite_dynamitemodulesc@230914044@23.09.14 (190400-0):1)
        at java.util.concurrent.ThreadPoolExecutor.runWorker (ThreadPoolExecutor.java:1137)
        at java.lang.Thread.run (Thread.java:1012)
    """
    .trimIndent()

private val MULTIPLE_TRACE_GROUPS_NO_AT_IDENTIFIER =
  """
    Exception java.lang.SecurityException:
      android.os.Parcel.createException (Parcel.java:2071)
      android.os.Parcel.readException (Parcel.java:2039)
    Caused by android.os.RemoteException: Remote stack trace:
      com.android.server.wm.ActivityStackSupervisor.checkStartAnyActivityPermission (ActivityStackSupervisor.java:1121)
  """
    .trimIndent()

private val MULTIPLE_TRACE_GROUPS =
  """
    Exception java.lang.SecurityException:
      at android.os.Parcel.createException (Parcel.java:2071)
      at android.os.Parcel.readException (Parcel.java:2039)
    Caused by android.os.RemoteException: Remote stack trace:
      at com.android.server.wm.ActivityStackSupervisor.checkStartAnyActivityPermission (ActivityStackSupervisor.java:1121)
  """
    .trimIndent()

private val NATIVE_CRASH =
  """
  backtrace:
    #00  pc 0x00000000005b7664  /data/app/~~SNE_UVeZUo4zwNCuxWwuHQ==/com.redhotlabs.bingo-dVfGb9wVnpJONeVLGBR-Ug==/lib/arm64/libunity.so (bool UnityDefaultAllocator\u003cLowLevelAllocator\u003e::AllocationPage\u003c(RequestType)0\u003e(void const*) const)
    #01  pc 0x00000000005b7204  /data/app/~~SNE_UVeZUo4zwNCuxWwuHQ==/com.redhotlabs.bingo-dVfGb9wVnpJONeVLGBR-Ug==/lib/arm64/libunity.so (UnityDefaultAllocator\u003cLowLevelAllocator\u003e::RegisterAllocation(void const*))
"""
    .trimIndent()

private val THREAD_DUMP =
  """
"main" tid=1 Native
  #00  pc 0x000000000009a5a8  /apex/com.android.runtime/lib/bionic/libc.so (__epoll_pwait+20)
  #01  pc 0x000000000006be19  /apex/com.android.runtime/lib/bionic/libc.so (epoll_wait+16)
  at android.os.MessageQueue.nativePollOnce (Native method)
  at android.os.MessageQueue.next (MessageQueue.java:335)

"FinalizerDaemon" tid=12 Runnable
  at android.os.Looper.getMainLooper (Looper.java:141)
  at java.lang.DaemonsFinalizerDaemon.doFinalize (Daemons.java:291)
  """
    .trimIndent()

class StacktraceKtTest {
  @Test
  fun `extract native crash from string blob`() {
    val extracted = NATIVE_CRASH.extractException()
    assertThat(extracted.exceptions.size).isEqualTo(1)
    assertThat(extracted.exceptions)
      .containsExactly(
        ExceptionStack(
          stacktrace =
            Stacktrace(
              Caption(title = "backtrace", subtitle = ""),
              blames = Blames.UNKNOWN_BLAMED,
              frames =
                listOf(
                  Frame(
                    line = 0,
                    file = "",
                    rawSymbol =
                      "#00  pc 0x00000000005b7664  /data/app/~~SNE_UVeZUo4zwNCuxWwuHQ==/com.redhotlabs.bingo-dVfGb9wVnpJONeVLGBR-Ug==/lib/arm64/libunity.so (bool UnityDefaultAllocator\\u003cLowLevelAllocator\\u003e::AllocationPage\\u003c(RequestType)0\\u003e(void const*) const)",
                    symbol =
                      "#00  pc 0x00000000005b7664  /data/app/~~SNE_UVeZUo4zwNCuxWwuHQ==/com.redhotlabs.bingo-dVfGb9wVnpJONeVLGBR-Ug==/lib/arm64/libunity.so (bool UnityDefaultAllocator\\u003cLowLevelAllocator\\u003e::AllocationPage\\u003c(RequestType)0\\u003e(void const*) const)",
                    offset = 0,
                    address = 0,
                    library = "",
                    blame = Blames.UNKNOWN_BLAMED,
                  ),
                  Frame(
                    line = 0,
                    file = "",
                    rawSymbol =
                      "#01  pc 0x00000000005b7204  /data/app/~~SNE_UVeZUo4zwNCuxWwuHQ==/com.redhotlabs.bingo-dVfGb9wVnpJONeVLGBR-Ug==/lib/arm64/libunity.so (UnityDefaultAllocator\\u003cLowLevelAllocator\\u003e::RegisterAllocation(void const*))",
                    symbol =
                      "#01  pc 0x00000000005b7204  /data/app/~~SNE_UVeZUo4zwNCuxWwuHQ==/com.redhotlabs.bingo-dVfGb9wVnpJONeVLGBR-Ug==/lib/arm64/libunity.so (UnityDefaultAllocator\\u003cLowLevelAllocator\\u003e::RegisterAllocation(void const*))",
                    offset = 0,
                    address = 0,
                    library = "",
                    blame = Blames.UNKNOWN_BLAMED,
                  ),
                ),
            ),
          type = "backtrace",
          exceptionMessage = "",
          rawExceptionMessage = "backtrace:",
        )
      )
  }

  @Test
  fun `multiple trace groups without AT identifier from string blob`() {
    val extracted = MULTIPLE_TRACE_GROUPS_NO_AT_IDENTIFIER.extractException()
    assertThat(extracted.exceptions.size).isEqualTo(2)
    assertThat(extracted.exceptions)
      .containsExactly(
        ExceptionStack(
          stacktrace =
            Stacktrace(
              caption = Caption(title = "Exception java.lang.SecurityException", subtitle = ""),
              blames = Blames.UNKNOWN_BLAMED,
              frames =
                listOf(
                  Frame(
                    line = 2071,
                    file = "Parcel.java",
                    rawSymbol = "android.os.Parcel.createException (Parcel.java:2071)",
                    symbol = "android.os.Parcel.createException",
                    offset = 0,
                    address = 0,
                    library = "",
                    blame = Blames.UNKNOWN_BLAMED,
                  ),
                  Frame(
                    line = 2039,
                    file = "Parcel.java",
                    rawSymbol = "android.os.Parcel.readException (Parcel.java:2039)",
                    symbol = "android.os.Parcel.readException",
                    offset = 0,
                    address = 0,
                    library = "",
                    blame = Blames.UNKNOWN_BLAMED,
                  ),
                ),
            ),
          type = "Exception java.lang.SecurityException",
          exceptionMessage = "",
          rawExceptionMessage = "Exception java.lang.SecurityException:",
        ),
        ExceptionStack(
          stacktrace =
            Stacktrace(
              caption =
                Caption(
                  title = "Caused by android.os.RemoteException",
                  subtitle = "Remote stack trace",
                ),
              blames = Blames.UNKNOWN_BLAMED,
              frames =
                listOf(
                  Frame(
                    line = 1121,
                    file = "ActivityStackSupervisor.java",
                    rawSymbol =
                      "com.android.server.wm.ActivityStackSupervisor.checkStartAnyActivityPermission (ActivityStackSupervisor.java:1121)",
                    symbol =
                      "com.android.server.wm.ActivityStackSupervisor.checkStartAnyActivityPermission",
                    offset = 0,
                    address = 0,
                    library = "",
                    blame = Blames.UNKNOWN_BLAMED,
                  )
                ),
            ),
          type = "Caused by android.os.RemoteException",
          exceptionMessage = "Remote stack trace",
          rawExceptionMessage = "Caused by android.os.RemoteException: Remote stack trace:",
        ),
      )
  }

  @Test
  fun `multiple trace groups from string blob`() {
    val extracted = MULTIPLE_TRACE_GROUPS.extractException()
    assertThat(extracted.exceptions.size).isEqualTo(2)
    assertThat(extracted.exceptions)
      .containsExactly(
        ExceptionStack(
          stacktrace =
            Stacktrace(
              caption = Caption(title = "Exception java.lang.SecurityException", subtitle = ""),
              blames = Blames.UNKNOWN_BLAMED,
              frames =
                listOf(
                  Frame(
                    line = 2071,
                    file = "Parcel.java",
                    rawSymbol = "at android.os.Parcel.createException (Parcel.java:2071)",
                    symbol = "android.os.Parcel.createException",
                    offset = 0,
                    address = 0,
                    library = "",
                    blame = Blames.UNKNOWN_BLAMED,
                  ),
                  Frame(
                    line = 2039,
                    file = "Parcel.java",
                    rawSymbol = "at android.os.Parcel.readException (Parcel.java:2039)",
                    symbol = "android.os.Parcel.readException",
                    offset = 0,
                    address = 0,
                    library = "",
                    blame = Blames.UNKNOWN_BLAMED,
                  ),
                ),
            ),
          type = "Exception java.lang.SecurityException",
          exceptionMessage = "",
          rawExceptionMessage = "Exception java.lang.SecurityException:",
        ),
        ExceptionStack(
          stacktrace =
            Stacktrace(
              caption =
                Caption(
                  title = "Caused by android.os.RemoteException",
                  subtitle = "Remote stack trace",
                ),
              blames = Blames.UNKNOWN_BLAMED,
              frames =
                listOf(
                  Frame(
                    line = 1121,
                    file = "ActivityStackSupervisor.java",
                    rawSymbol =
                      "at com.android.server.wm.ActivityStackSupervisor.checkStartAnyActivityPermission (ActivityStackSupervisor.java:1121)",
                    symbol =
                      "com.android.server.wm.ActivityStackSupervisor.checkStartAnyActivityPermission",
                    offset = 0,
                    address = 0,
                    library = "",
                    blame = Blames.UNKNOWN_BLAMED,
                  )
                ),
            ),
          type = "Caused by android.os.RemoteException",
          exceptionMessage = "Remote stack trace",
          rawExceptionMessage = "Caused by android.os.RemoteException: Remote stack trace:",
        ),
      )
  }

  @Test
  fun `single trace group from string blob`() {
    val extracted = SINGLE_TRACE_GROUP.extractException()
    assertThat(extracted.exceptions.size).isEqualTo(1)
    assertThat(extracted.exceptions.first())
      .isEqualTo(
        ExceptionStack(
          stacktrace =
            Stacktrace(
              caption = Caption(title = "Exception java.lang.OutOfMemoryError", subtitle = ""),
              blames = Blames.UNKNOWN_BLAMED,
              frames =
                listOf(
                  Frame(
                    line = 3766,
                    file = "Arrays.java",
                    rawSymbol = "at java.util.Arrays.copyOf (Arrays.java:3766)",
                    symbol = "java.util.Arrays.copyOf",
                    offset = 0,
                    address = 0,
                    library = "",
                    blame = Blames.UNKNOWN_BLAMED,
                  ),
                  Frame(
                    line = 125,
                    file = "AbstractStringBuilder.java",
                    rawSymbol =
                      "at java.lang.AbstractStringBuilder.ensureCapacityInternal (AbstractStringBuilder.java:125)",
                    symbol = "java.lang.AbstractStringBuilder.ensureCapacityInternal",
                    offset = 0,
                    address = 0,
                    library = "",
                    blame = Blames.UNKNOWN_BLAMED,
                  ),
                  Frame(
                    line = 449,
                    file = "AbstractStringBuilder.java",
                    rawSymbol =
                      "at java.lang.AbstractStringBuilder.append (AbstractStringBuilder.java:449)",
                    symbol = "java.lang.AbstractStringBuilder.append",
                    offset = 0,
                    address = 0,
                    library = "",
                    blame = Blames.UNKNOWN_BLAMED,
                  ),
                  Frame(
                    line = 137,
                    file = "StringBuilder.java",
                    rawSymbol = "at java.lang.StringBuilder.append (StringBuilder.java:137)",
                    symbol = "java.lang.StringBuilder.append",
                    offset = 0,
                    address = 0,
                    library = "",
                    blame = Blames.UNKNOWN_BLAMED,
                  ),
                  Frame(
                    line = 0,
                    file = "",
                    rawSymbol =
                      "at asj.toString (:com.google.android.gms.dynamite_dynamitemodulesc@230914044@23.09.14 (190400-0):4)",
                    symbol =
                      "at asj.toString (:com.google.android.gms.dynamite_dynamitemodulesc@230914044@23.09.14 (190400-0):4)",
                    offset = 0,
                    address = 0,
                    library = "",
                    blame = Blames.UNKNOWN_BLAMED,
                  ),
                  Frame(
                    line = 3657,
                    file = "String.java",
                    rawSymbol = "at java.lang.String.valueOf (String.java:3657)",
                    symbol = "java.lang.String.valueOf",
                    offset = 0,
                    address = 0,
                    library = "",
                    blame = Blames.UNKNOWN_BLAMED,
                  ),
                  Frame(
                    line = 0,
                    file = "",
                    rawSymbol =
                      "at aks.d (:com.google.android.gms.dynamite_dynamitemodulesc@230914044@23.09.14 (190400-0):6)",
                    symbol =
                      "at aks.d (:com.google.android.gms.dynamite_dynamitemodulesc@230914044@23.09.14 (190400-0):6)",
                    offset = 0,
                    address = 0,
                    library = "",
                    blame = Blames.UNKNOWN_BLAMED,
                  ),
                  Frame(
                    line = 0,
                    file = "",
                    rawSymbol =
                      "at ajk.run (:com.google.android.gms.dynamite_dynamitemodulesc@230914044@23.09.14 (190400-0):1)",
                    symbol =
                      "at ajk.run (:com.google.android.gms.dynamite_dynamitemodulesc@230914044@23.09.14 (190400-0):1)",
                    offset = 0,
                    address = 0,
                    library = "",
                    blame = Blames.UNKNOWN_BLAMED,
                  ),
                  Frame(
                    line = 1137,
                    file = "ThreadPoolExecutor.java",
                    rawSymbol =
                      "at java.util.concurrent.ThreadPoolExecutor.runWorker (ThreadPoolExecutor.java:1137)",
                    symbol = "java.util.concurrent.ThreadPoolExecutor.runWorker",
                    offset = 0,
                    address = 0,
                    library = "",
                    blame = Blames.UNKNOWN_BLAMED,
                  ),
                  Frame(
                    line = 1012,
                    file = "Thread.java",
                    rawSymbol = "at java.lang.Thread.run (Thread.java:1012)",
                    symbol = "java.lang.Thread.run",
                    offset = 0,
                    address = 0,
                    library = "",
                    blame = Blames.UNKNOWN_BLAMED,
                  ),
                ),
            ),
          type = "Exception java.lang.OutOfMemoryError",
          exceptionMessage = "",
          rawExceptionMessage = "Exception java.lang.OutOfMemoryError:",
        )
      )
  }

  @Test
  fun `thread dump from string blob`() {
    val extracted = THREAD_DUMP.extractThreadDump()
    assertThat(extracted.exceptions)
      .containsExactly(
        ExceptionStack(
          stacktrace =
            Stacktrace(
              caption = Caption(title = "\"main\" tid=1 Native", subtitle = ""),
              blames = Blames.BLAMED,
              frames =
                listOf(
                  Frame(
                    line = 0,
                    file = "",
                    rawSymbol =
                      "#00  pc 0x000000000009a5a8  /apex/com.android.runtime/lib/bionic/libc.so (__epoll_pwait+20)",
                    symbol =
                      "#00  pc 0x000000000009a5a8  /apex/com.android.runtime/lib/bionic/libc.so (__epoll_pwait+20)",
                    offset = 0,
                    address = 0,
                    library = "",
                    blame = Blames.UNKNOWN_BLAMED,
                  ),
                  Frame(
                    line = 0,
                    file = "",
                    rawSymbol =
                      "#01  pc 0x000000000006be19  /apex/com.android.runtime/lib/bionic/libc.so (epoll_wait+16)",
                    symbol =
                      "#01  pc 0x000000000006be19  /apex/com.android.runtime/lib/bionic/libc.so (epoll_wait+16)",
                    offset = 0,
                    address = 0,
                    library = "",
                    blame = Blames.UNKNOWN_BLAMED,
                  ),
                  Frame(
                    line = 0,
                    file = "",
                    rawSymbol = "at android.os.MessageQueue.nativePollOnce (Native method)",
                    symbol = "at android.os.MessageQueue.nativePollOnce (Native method)",
                    offset = 0,
                    address = 0,
                    library = "",
                    blame = Blames.UNKNOWN_BLAMED,
                  ),
                  Frame(
                    line = 335,
                    file = "MessageQueue.java",
                    rawSymbol = "at android.os.MessageQueue.next (MessageQueue.java:335)",
                    symbol = "android.os.MessageQueue.next",
                    offset = 0,
                    address = 0,
                    library = "",
                    blame = Blames.UNKNOWN_BLAMED,
                  ),
                ),
            ),
          type = "\"main\" tid=1 Native",
          exceptionMessage = "",
          rawExceptionMessage = "\"main\" tid=1 Native",
        ),
        ExceptionStack(
          stacktrace =
            Stacktrace(
              caption = Caption(title = "\"FinalizerDaemon\" tid=12 Runnable", subtitle = ""),
              blames = Blames.NOT_BLAMED,
              frames =
                listOf(
                  Frame(
                    line = 141,
                    file = "Looper.java",
                    rawSymbol = "at android.os.Looper.getMainLooper (Looper.java:141)",
                    symbol = "android.os.Looper.getMainLooper",
                    offset = 0,
                    address = 0,
                    library = "",
                    blame = Blames.UNKNOWN_BLAMED,
                  ),
                  Frame(
                    line = 291,
                    file = "Daemons.java",
                    rawSymbol = "at java.lang.DaemonsFinalizerDaemon.doFinalize (Daemons.java:291)",
                    symbol = "java.lang.DaemonsFinalizerDaemon.doFinalize",
                    offset = 0,
                    address = 0,
                    library = "",
                    blame = Blames.UNKNOWN_BLAMED,
                  ),
                ),
            ),
          type = "\"FinalizerDaemon\" tid=12 Runnable",
          exceptionMessage = "",
          rawExceptionMessage = "\"FinalizerDaemon\" tid=12 Runnable",
        ),
      )
      .inOrder()
  }
}
