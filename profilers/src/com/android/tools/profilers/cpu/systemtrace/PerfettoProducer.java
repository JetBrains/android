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
package com.android.tools.profilers.cpu.systemtrace;

import com.android.tools.idea.protobuf.CodedInputStream;
import com.android.tools.idea.protobuf.DescriptorProtos;
import com.android.tools.idea.protobuf.ExtensionRegistryLite;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.Predicate;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import perfetto.protos.PerfettoTrace;
import trebuchet.io.DataSlice;

/**
 * This class converts perfetto traces to {@link DataSlice} objects. The {@link DataSlice} objects are then used by the
 * {@link trebuchet.task.ImportTask} to create a {@link trebuchet.model.Model}.
 * This model is used by the profilers UI to render systrace data.
 */
public class PerfettoProducer implements TrebuchetBufferProducer {
  // Required line for trebuchet to parse as ftrace.
  private static final String FTRACE_HEADER = "# tracer: nop";
  // Supported events are events that we know how to convert from perfetto format to systrace format.
  // The current set of supported events are the only events that we need as they are the only events that trebuchet supports.
  private static final Predicate<PerfettoTrace.FtraceEvent> IS_SUPPORTED_EVENT = event ->
    event.hasSchedSwitch() ||
    event.hasSchedWakeup() ||
    event.hasSchedWaking() ||
    event.hasPrint();

  // Maps thread id to thread group id. A tgid is the thread id at the root of the tree. This is also known as the PID in user space.
  private final ArrayDeque<String> myGeneratedTrebuchetLines = new ArrayDeque<>();
  private final PerfettoPacketDBSorter mySorter = new PerfettoPacketDBSorter();
  private final HashMap<Integer, String> myMappedState = new HashMap<>();

  private static double nanosToSeconds(double nanos) {
    return nanos / TimeUnit.SECONDS.toNanos(1);

  }

  private static double nanosToMillis(double nanos) {
    return nanos / TimeUnit.MILLISECONDS.toNanos(1);
  }

  /**
   * @param file handle to a file that represents a perfetto trace. A single trace packet will be read from this file to verify
   *             the file can be opened and parsed to a {@link PerfettoTrace.TracePacket}
   * @return true if one {@link PerfettoTrace.TracePacket} was able to be read from the file.
   */
  public static boolean verifyFileHasPerfettoTraceHeader(@NotNull File file) {
    try {
      CodedInputStream inputStream = CodedInputStream.newInstance(new FileInputStream(file));
      ExtensionRegistryLite packetRegistry = ExtensionRegistryLite.newInstance();
      PerfettoTrace.registerAllExtensions(packetRegistry);
      PerfettoTrace.TracePacket packet = readOnePacket(inputStream, packetRegistry);
      // If we can load 1 packet then we assume this is a perfetto file.
      return packet != null;
    }
    catch (IOException ex) {
      getLogger().error(ex);
      return false;
    }
  }

  /**
   * Helper function for reading one packet from the {@link PerfettoTrace.Trace} proto.
   * We read one proto this way because reading the full {@link PerfettoTrace.Trace} requires us to store each
   * {@link PerfettoTrace.TracePacket} in memory. For a 60 second file this can be 10K+ packets consuming greater than 700mb of memory.
   * <p>
   * Note: This accepts a CodedInputStream instead of input stream because CodedInputStream reads more than it needs and is buffered.
   * Note: This accepts the packet registry so we can reuse this API in the static function to check perfetto trace headers.
   *
   * @return Null is returned for end of stream, otherwise a trace packet is returned.
   */
  private static PerfettoTrace.TracePacket readOnePacket(CodedInputStream stream, ExtensionRegistryLite packetRegistry) {
    try {
      // Coded Input Streams by default only let you read in 64KB of data from one proto message. Because our root level proto message is
      // greater than this we need to reset the size counter each time we read a new packet.
      stream.resetSizeCounter();
      int tag = stream.readTag();
      // If tag == 0, we have reached end of stream, or some other error.
      if (tag == 0) {
        return null;
      }
      // Since we know the layout of the Trace proto, we know it has one field that is a repeated field. So we expect to tag to match this
      // value. If it does not we throw an error since we can't processes unknown tags.
      if (tag != DescriptorProtos.FieldDescriptorProto.Type.TYPE_GROUP_VALUE) {
        getLogger().error(String.format("Encountered unknown tag (%d) when attempting to parse perfetto capture.", tag));
        return null;
      }
      return stream.readMessage(PerfettoTrace.TracePacket.parser(), packetRegistry);
    }
    catch (IOException ex) {
      getLogger().error(ex);
      return null;
    }
  }

  private static Logger getLogger() {
    return Logger.getInstance(PerfettoProducer.class);
  }

  public PerfettoProducer() {
    myMappedState.put(1, "S");
    myMappedState.put(2, "D");
    myMappedState.put(4, "T");
    myMappedState.put(8, "t");
    myMappedState.put(16, "Z");
    myMappedState.put(32, "X");
    myMappedState.put(64, "x");
    myMappedState.put(128, "K");
    myMappedState.put(256, "W");
    myMappedState.put(512, "P");
    myMappedState.put(1024, "N");
  }

  @Override
  public boolean parseFile(File file) {
    try {
      convertToTraceLines(file);
      return true;
    }
    catch (IOException ex) {
      getLogger().error(ex);
      return false;
    }
  }

  private void convertToTraceLines(File file) throws IOException {
    Map<Integer, Integer> myTidToTgid = new HashMap<>();
    Map<Integer, String> myTidToName = new HashMap<>();

    // Add a special case name for thread id 0.
    // Thread id 0 is used for events that are generated by the system not associated with any process.
    // In systrace and perfetto they use <idle> as the name for events generated with this thread id.
    myTidToName.put(0, "<idle>");
    // The clock sync packet is set to the first packet encountered with a clock snapshot.
    PerfettoTrace.TracePacket clockSyncPacket = null;

    // Do a first pass on the file in order to collect all thread names, and thread group names mapped to id.
    // This allows us to properly build the list of threads / events required by trebuchet for it to
    // map threads to processes.
    ExtensionRegistryLite packetRegistry = ExtensionRegistryLite.newInstance();
    PerfettoTrace.registerAllExtensions(packetRegistry);
    CodedInputStream inputStream = CodedInputStream.newInstance(new FileInputStream(file));
    PerfettoTrace.TracePacket packet;
    while ((packet = readOnePacket(inputStream, packetRegistry)) != null) {
      if (packet.hasFtraceEvents()) {
        PerfettoTrace.FtraceEventBundle bundle = packet.getFtraceEvents();
        for (PerfettoTrace.FtraceEvent event : bundle.getEventList()) {
          if (!event.hasSchedSwitch()) {
            continue;
          }
          PerfettoTrace.SchedSwitchFtraceEvent schedSwitch = event.getSchedSwitch();
          myTidToName.putIfAbsent(schedSwitch.getPrevPid(), schedSwitch.getPrevComm());
          myTidToName.putIfAbsent(schedSwitch.getNextPid(), schedSwitch.getNextComm());
        }
      }
      else if (packet.hasProcessTree()) {
        PerfettoTrace.ProcessTree processTree = packet.getProcessTree();
        for (PerfettoTrace.ProcessTree.Process process : processTree.getProcessesList()) {
          // Main threads will have the same pid as tgid.
          myTidToTgid.putIfAbsent(process.getPid(), process.getPid());
        }
        for (PerfettoTrace.ProcessTree.Thread thread : processTree.getThreadsList()) {
          myTidToTgid.putIfAbsent(thread.getTid(), thread.getTgid());
          if (thread.hasName()) {
            myTidToName.putIfAbsent(thread.getTid(), thread.getName());
          }
        }
      }
      else if (packet.hasClockSnapshot() && clockSyncPacket == null) {
        // We only want the first clock sync packet.
        clockSyncPacket = packet;
      }
    }

    LineFormatter formatter = new LineFormatter(myTidToTgid, myTidToName);

    // Do a second pass on the file now that we have all thread names do a second pass on the file to generate the lines for trebuchet.
    inputStream = CodedInputStream.newInstance(new FileInputStream(file));
    while ((packet = readOnePacket(inputStream, packetRegistry)) != null) {
      if (packet.hasFtraceEvents()) {
        PerfettoTrace.FtraceEventBundle bundle = packet.getFtraceEvents();
        for (PerfettoTrace.FtraceEvent event : bundle.getEventList()) {
          if (IS_SUPPORTED_EVENT.apply(event)) {
            mySorter.addLine(event.getTimestamp(), formatter.formatLine(event, bundle.getCpu()));
          }
        }
      }
    }

    // Build systrace lines for each packet.
    // Note: lines need to be sorted by time else assumptions in trebuchet break.
    myGeneratedTrebuchetLines.add("# Initial Data Required by Importer");
    myGeneratedTrebuchetLines.add(FTRACE_HEADER);

    // Each perfetto trace has many clock sync packets. We need the mono and real time clocks from the first packet to align timestamps with
    // ftrace to timestamps from studio.
    assert clockSyncPacket != null;
    addClockSyncLines(clockSyncPacket, formatter);
    mySorter.resetForIterator();
  }

  private void addClockSyncLines(@NotNull PerfettoTrace.TracePacket clockSyncPacket, LineFormatter formatter) {
    PerfettoTrace.ClockSnapshot snapshot = clockSyncPacket.getClockSnapshot();
    PerfettoTrace.ClockSnapshot.Clock monotonicClock = null;
    PerfettoTrace.ClockSnapshot.Clock realtimeClock = null;
    PerfettoTrace.ClockSnapshot.Clock boottimeClock = null;
    for (PerfettoTrace.ClockSnapshot.Clock clock : snapshot.getClocksList()) {
      PerfettoTrace.ClockSnapshot.Clock.BuiltinClocks clockType =
        PerfettoTrace.ClockSnapshot.Clock.BuiltinClocks.forNumber(clock.getClockId());

      if (clockType == null) {
        continue;
      }

      switch (clockType) {
        case MONOTONIC:
          monotonicClock = clock;
          break;
        case REALTIME:
          realtimeClock = clock;
          break;
        case BOOTTIME:
          boottimeClock = clock;
          break;
        default:
          // do nothing for other clocks
      }
    }
    assert monotonicClock != null && realtimeClock != null && boottimeClock != null;
    //<...>-29454 (-----) [002] ...1 1214209.724359: tracing_mark_write: trace_event_clock_sync: parent_ts=539454.250000
    myGeneratedTrebuchetLines.add(formatter.formatEventPrefix(boottimeClock.getTimestamp(), 0, Short.MAX_VALUE) +
                                  String.format("tracing_mark_write: trace_event_clock_sync: parent_ts=%.6f",
                                               nanosToSeconds(monotonicClock.getTimestamp())));
    //<...>-29454 (-----) [002] ...1 1214209.724366: tracing_mark_write: trace_event_clock_sync: realtime_ts=1520548500187
    myGeneratedTrebuchetLines.add(formatter.formatEventPrefix(boottimeClock.getTimestamp(), 0, Short.MAX_VALUE) +
                                  "tracing_mark_write: trace_event_clock_sync: realtime_ts=" +
                                  nanosToMillis(realtimeClock.getTimestamp()));
  }

  @Nullable
  @Override
  public DataSlice next() {
    // A line comes from either our required lines, or our line sorter.
    String line;
    if (!myGeneratedTrebuchetLines.isEmpty()) {
      line = myGeneratedTrebuchetLines.poll();
    }
    else {
      if (!mySorter.hasNext()) {
        // Null signals end of file.
        return null;
      }
      line = mySorter.next();
    }
    assert line != null;
    // Trebuchet has a bug where all lines need to be truncated to 1023 characters including the newline.
    byte[] data = String.format("%s\n", line.substring(0, Math.min(1022, line.length()))).getBytes(Charsets.UTF_8);
    return new DataSlice(data);
  }

  @Override
  public void close() {
    myGeneratedTrebuchetLines.clear();
    mySorter.close();
  }

  /**
   * Helper function to format specific event protos to the systrace equivalent format.
   * Example: tracing_mark_write: B|123|drawFrame
   */
  private String formatEvent(PerfettoTrace.FtraceEvent event) {
    if (event.hasSchedSwitch()) {
      PerfettoTrace.SchedSwitchFtraceEvent sched = event.getSchedSwitch();
      return String.format("sched_switch: prev_comm=%s prev_pid=%d prev_prio=%d prev_state=%s ==> next_comm=%s next_pid=%d next_prio=%d",
                           sched.getPrevComm(), sched.getPrevPid(), sched.getPrevPrio(), mapStateToString(sched.getPrevState()),
                           sched.getNextComm(), sched.getNextPid(), sched.getNextPrio());
    }
    else if (event.hasSchedCpuHotplug()) {
      PerfettoTrace.SchedCpuHotplugFtraceEvent sched = event.getSchedCpuHotplug();
      return String.format("sched_cpu_hotplug: cpu %d %s error=%d", sched.getAffectedCpu(), sched.getStatus() == 0 ? "offline" : "online",
                           sched.getError());
    }
    else if (event.hasSchedBlockedReason()) {
      PerfettoTrace.SchedBlockedReasonFtraceEvent sched = event.getSchedBlockedReason();
      return String.format("sched_blocked_reason: pid=%d iowait=%d caller=%s", sched.getPid(), sched.getIoWait(), sched.getCaller());
    }
    else if (event.hasSchedWaking()) {
      PerfettoTrace.SchedWakingFtraceEvent sched = event.getSchedWaking();
      return String
        .format("sched_waking: comm=%s pid=%d prio=%d success=%d target_cpu=%03d", sched.getComm(), sched.getPid(), sched.getPrio(),
                sched.getSuccess(), sched.getTargetCpu());
    }
    else if (event.hasSchedWakeup()) {
      PerfettoTrace.SchedWakeupFtraceEvent sched = event.getSchedWakeup();
      return String
        .format("sched_wakeup: comm=%s pid=%d prio=%d success=%d target_cpu=%03d", sched.getComm(), sched.getPid(), sched.getPrio(),
                sched.getSuccess(), sched.getTargetCpu());
    }
    else if (event.hasPrint()) {
      return String.format("tracing_mark_write: %s", event.getPrint().getBuf().replace("\n", ""));
    }
    else {
      getLogger().assertTrue(IS_SUPPORTED_EVENT.apply(event), "Attempted to format a non-supported event.");
    }
    return "";
  }

  @VisibleForTesting
  public String mapStateToString(long state) {
    // For mapping information see https://github.com/torvalds/linux/blob/v4.4/include/trace/events/sched.h
    int TASK_STATE_HIGH_BIT_MASK = 0x800;
    int TASK_STATE_LOW_BITS_MASK = 0x7FF;
    StringBuilder mappedState = new StringBuilder();
    // If bits 0-11 are 0 return R.
    if ((state & TASK_STATE_LOW_BITS_MASK) == 0) {
      mappedState.append("R");
    } else {
      for (int i = 0; i < 11; i++) {
        int flag = (int)(state & (1 << i));
        if (flag != 0) {
          mappedState.append(myMappedState.get(flag));
        }
      }
    }
    if ((state & TASK_STATE_HIGH_BIT_MASK) != 0) {
        mappedState.append("+");
    }
    return mappedState.toString();
  }

  private class LineFormatter {
    private final Map<Integer, Integer> myTidToTgid;
    private final Map<Integer, String> myTidToName;

    private LineFormatter(Map<Integer, Integer> tidToTgid, Map<Integer, String> tidToName) {
      myTidToTgid = tidToTgid;
      myTidToName = tidToName;
    }

    /**
     * Function passed to the PacketSorter to convert an FtraceEvent to a line.
     */
    private String formatLine(PerfettoTrace.FtraceEvent event, int cpu) {
      return formatEventPrefix(event.getTimestamp(), cpu, event.getPid()) + formatEvent(event);
    }

    /**
     * Helper function that builds the prefix for systrace lines. The prefix is in the format of
     * [thread name]-[tid]     ([tgid]) [[cpu]] d..3 [time in seconds].
     * Note d..3 is hard coded as it is expected to be part of the line, however it is not used.
     */
    private String formatEventPrefix(long timestampNs, int cpu, int pid) {
      String name = myTidToName.getOrDefault(pid, "<...>");
      // Convert Ns to seconds as seconds is the expected atrace format.
      String timeSeconds = String.format("%.6f", nanosToSeconds(timestampNs));
      String tgid = "-----";
      if (myTidToTgid.containsKey(pid)) {
        tgid = String.format("%5d", myTidToTgid.get(pid));
      }
      return String.format("%s-%d     (%s) [%3d] d..3 %s: ", name, pid, tgid, cpu, timeSeconds);
    }
  }
}
