/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.cpu.simpleperf;

import com.android.tools.perflib.vmtrace.ClockType;
import com.android.tools.profilers.cpu.BaseCpuCapture;
import com.google.common.annotations.VisibleForTesting;
import com.android.tools.adtui.model.Range;
import com.android.tools.profiler.proto.SimpleperfReport;
import com.android.tools.profilers.cpu.CaptureNode;
import com.android.tools.profilers.cpu.CpuCapture;
import com.android.tools.profilers.cpu.CpuThreadInfo;
import com.android.tools.profilers.cpu.TraceParser;
import com.android.tools.profilers.cpu.nodemodel.CaptureNodeModel;
import com.android.tools.profilers.cpu.nodemodel.NoSymbolModel;
import com.android.tools.profilers.cpu.nodemodel.SingleNameModel;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import com.android.tools.profilers.cpu.config.ProfilingConfiguration.TraceType;

/**
 * Parses a trace file obtained using simpleperf to a map threadId -> {@link CaptureNode}.
 */
public class SimpleperfTraceParser implements TraceParser {

  /**
   * Magic string that should appear in the very beginning of the simpleperf trace.
   */
  private static final String MAGIC = "SIMPLEPERF";

  /**
   * When the name of a function (symbol) is not found in the symbol table, the symbol_id field is set to -1.
   */
  private static final int INVALID_SYMBOL_ID = -1;

  /**
   * Directory containing files (.art, .odex, .so, .apk) related to app's. Each app's files are located in a subdirectory whose name starts
   * with the app ID. For instance, "com.google.sample.tunnel" app's directory could be something like
   * "/data/app/com.google.sample.tunnel-qpKipbnc0pE6uQs6gxAmbQ=="
   */
  private static final String DATA_APP_DIR = "/data/app";

  /**
   * The name of the event that should be used in simpleperf record command to support thread time.
   * <p>
   * Older versions of Android Studio used "cpu-cycles" which may have a better sampling cadence because it's
   * hardware based. However, CPU cycles are harder to correlate to wall clock time. Therefore, we support thread
   * time only if "cpu-clock" is used.
   */
  private static final String CPU_CLOCK_EVENT = "cpu-clock";

  /**
   * The message to surface to the user when dual clock isn't supported.
   */
  private static final String DUAL_CLOCK_DISABLED_MESSAGE =
    "This imported trace supports Wall Clock Time only.<p>" +
    "To view Thread Time, take a new recording using the latest version of Android Studio.";

  /**
   * Version of the trace file to be parsed. Should be obtained from the file itself.
   */
  private int myTraceVersion;

  /**
   * Maps a file id to its correspondent {@link SimpleperfReport.File}.
   */
  private final Map<Integer, SimpleperfReport.File> myFiles;

  /**
   * Maps a thread id to its corresponding {@link SimpleperfReport.Thread} object.
   */
  private final Map<Integer, SimpleperfReport.Thread> myThreads;

  /**
   * List of samples containing method trace data.
   */
  @VisibleForTesting final List<SimpleperfReport.Sample> mySamples;

  /**
   * Maps a {@link CpuThreadInfo} to its correspondent method call tree.
   */
  private final Map<CpuThreadInfo, CaptureNode> myCaptureTrees;

  /**
   * Number of samples read from trace file.
   */
  private long mySampleCount;

  /**
   * Number of samples lost when recording the trace.
   */
  private long myLostSampleCount;

  /**
   * The ID (i.e., index) of the {@link CPU_CLOCK_EVENT} (i.e., "cpu-clock") event in {@link myEventTypes}.
   */
  private int myCpuClockEventTypeId = -1;

  /**
   * Capture range in absolute time, measured in microseconds.
   * <p>
   * If empty (min > max), it means the capture doesn't contain any sampling data.
   */
  @NotNull
  private final Range myCaptureRange = new Range();

  /**
   * List of event types (e.g. cpu-cycles, sched:sched_switch) present in the trace.
   */
  private List<String> myEventTypes;

  private String myAppPackageName;

  /**
   * Prefix (up to the app name) of the /data/app subfolder corresponding to the app being profiled. For example:
   * "/data/app/com.google.sample.tunnel".
   */
  private String myAppDataFolderPrefix;

  private Set<String> myTags = new TreeSet<>(TAG_COMPARATOR);

  public SimpleperfTraceParser() {
    myFiles = new HashMap<>();
    mySamples = new ArrayList<>();
    myCaptureTrees = new HashMap<>();
    myThreads = new HashMap<>();
  }

  /**
   * Given Unix-like path string (e.g. /system/my-path/file.so), returns the file name (e.g. file.so).
   */
  private static String fileNameFromPath(String path) {
    String[] splitPath = path.split("/");
    return splitPath[splitPath.length - 1];
  }

  private static boolean equals(SimpleperfReport.Sample.CallChainEntry c1, SimpleperfReport.Sample.CallChainEntry c2) {
    boolean isSameFileAndSymbolId = c1.getFileId() == c2.getFileId() && c1.getSymbolId() == c2.getSymbolId();
    if (!isSameFileAndSymbolId) {
      // Call chain entries need to be obtained from the same file and have the same symbol id in order to be equal.
      return false;
    }
    if (c1.getSymbolId() == -1) {
      // Symbol is invalid, fallback to vaddress
      return c1.getVaddrInFile() == c2.getVaddrInFile();
    }
    // Both file and symbol id match, and symbol is valid
    return true;
  }

  private static Logger getLog() {
    return Logger.getInstance(SimpleperfTraceParser.class);
  }

  private static ByteBuffer byteBufferFromFile(File f, ByteOrder byteOrder) throws IOException {
    try (FileInputStream dataFile = new FileInputStream(f)) {
      MappedByteBuffer buffer = dataFile.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, f.length());
      buffer.order(byteOrder);
      return buffer;
    }
  }

  @Override
  public CpuCapture parse(@NotNull File trace, long traceId) throws IOException {
    parseTraceFile(trace);
    parseSampleData();
    return new BaseCpuCapture(traceId, TraceType.SIMPLEPERF,
                              isThreadTimeSupported(), isThreadTimeSupported() ? null : DUAL_CLOCK_DISABLED_MESSAGE,
                              myCaptureRange, getCaptureTrees(), myTags);
  }

  public Map<CpuThreadInfo, CaptureNode> getCaptureTrees() {
    return myCaptureTrees;
  }

  public long getLostSampleCount() {
    return myLostSampleCount;
  }

  public long getSampleCount() {
    return mySampleCount;
  }

  /**
   * @return whether this trace supports thread time. This is equivalent to supporting dual clock because simpleperf
   *         traces always support wall clock time (ClockType.GLOBAL).
   */
  private boolean isThreadTimeSupported() {
    return myCpuClockEventTypeId >= 0;
  }

  @NotNull
  private static CaptureNode createCaptureNode(CaptureNodeModel model, long startGlobalNs, long startThreadNs) {
    CaptureNode node = new CaptureNode(model, ClockType.GLOBAL);
    setNodeStartTime(node, startGlobalNs, startThreadNs);
    node.setDepth(0);
    return node;
  }

  /**
   * Parses the trace file, which should have the following format:
   * char magic[10] = "SIMPLEPERF";
   * LittleEndian16(version) = 1;
   * LittleEndian32(record_size_0)
   * SimpleperfReport.Record (having record_size_0 bytes)
   * LittleEndian32(record_size_1)
   * message Record(record_1) (having record_size_1 bytes)
   * ...
   * LittleEndian32(record_size_N)
   * message Record(record_N) (having record_size_N bytes)
   * LittleEndian32(0)
   * <p>
   * Parsed data is stored in {@link #myFiles} and {@link #mySamples}.
   */
  @VisibleForTesting
  void parseTraceFile(File trace) throws IOException {
    ByteBuffer buffer = byteBufferFromFile(trace, ByteOrder.LITTLE_ENDIAN);
    verifyMagicNumber(buffer);
    parseVersionNumber(buffer);

    // Read the first record size
    int recordSize = buffer.getInt();

    // 0 is used to indicate the end of the trace
    while (recordSize != 0) {
      // The next recordSize bytes should represent the record
      byte[] recordBytes = new byte[recordSize];
      buffer.get(recordBytes);
      SimpleperfReport.Record record = SimpleperfReport.Record.parseFrom(recordBytes);

      switch (record.getRecordDataCase()) {
        case FILE:
          SimpleperfReport.File file = record.getFile();
          myFiles.put(file.getId(), file);
          break;
        case LOST:
          // Only one occurrence of LOST type is expected.
          SimpleperfReport.LostSituation situation = record.getLost();
          mySampleCount = situation.getSampleCount();
          myLostSampleCount = situation.getLostCount();
          break;
        case SAMPLE:
          SimpleperfReport.Sample sample = record.getSample();
          mySamples.add(sample);
          break;
        case THREAD:
          SimpleperfReport.Thread thread = record.getThread();
          myThreads.put(thread.getThreadId(), thread);
          break;
        case META_INFO:
          SimpleperfReport.MetaInfo info = record.getMetaInfo();
          myEventTypes = info.getEventTypeList();
          myAppPackageName = info.getAppPackageName();
          myAppDataFolderPrefix = String.format("%s/%s", DATA_APP_DIR, myAppPackageName);
          break;
        default:
          getLog().warn("Unexpected record data type " + record.getRecordDataCase());
      }

      // read the next record size
      recordSize = buffer.getInt();
    }

    if (mySamples.size() != mySampleCount) {
      // TODO: create a trace file to test this exception is thrown when it should.
      throw new IllegalStateException("Samples count doesn't match the number of samples read.");
    }

    myCpuClockEventTypeId = myEventTypes.indexOf(CPU_CLOCK_EVENT);
  }

  /**
   * Parses the next 16-bit number of the given {@link ByteBuffer} as the trace version.
   */
  private void parseVersionNumber(ByteBuffer buffer) {
    myTraceVersion = buffer.getShort();
  }

  /**
   * Verifies the first 10 characters of the given {@link ByteBuffer} are {@code SIMPLEPERF}.
   * Throws an {@link IllegalStateException} otherwise.
   */
  private static void verifyMagicNumber(ByteBuffer buffer) {
    byte[] magic = new byte[MAGIC.length()];
    buffer.get(magic);
    if (!(new String(magic)).equals(MAGIC)) {
      throw new IllegalStateException("Simpleperf trace could not be parsed due to magic number mismatch.");
    }
  }

  /**
   * Parses the data from {@link #mySamples} into a map of tid -> {@link CaptureNode}.
   */
  private void parseSampleData() {
    if (mySamples.isEmpty()) {
      myCaptureRange.clear();
      return;
    }
    // Set the capture range
    long startTimestamp = mySamples.get(0).getTime();
    long endTimestamp = mySamples.get(mySamples.size() - 1).getTime();
    myCaptureRange.set(TimeUnit.NANOSECONDS.toMicros(startTimestamp), TimeUnit.NANOSECONDS.toMicros(endTimestamp));

    // Split the samples per thread.
    Map<Integer, List<SimpleperfReport.Sample>> threadSamples = splitSamplesPerThread();

    // Process the samples for each thread
    for (Map.Entry<Integer, List<SimpleperfReport.Sample>> threadSamplesEntry : threadSamples.entrySet()) {
      parseThreadSamples(threadSamplesEntry.getKey(), threadSamplesEntry.getValue());
    }
  }

  /**
   * Group the samples collected by thread.
   */
  private Map<Integer, List<SimpleperfReport.Sample>> splitSamplesPerThread() {
    Map<Integer, List<SimpleperfReport.Sample>> threadSamples = new HashMap<>();
    for (SimpleperfReport.Sample sample : mySamples) {
      int threadId = sample.getThreadId();
      if (!threadSamples.containsKey(threadId)) {
        threadSamples.put(threadId, new ArrayList<>());
      }
      threadSamples.get(threadId).add(sample);
    }
    return threadSamples;
  }

  private static void setNodeStartTime(CaptureNode node, long startGlobalNs, long startThreadNs) {
    node.setStartGlobal(TimeUnit.NANOSECONDS.toMicros(startGlobalNs));
    node.setStartThread(TimeUnit.NANOSECONDS.toMicros(startThreadNs));
  }

  private static void setNodeEndTime(CaptureNode node, long endGlobalNs, long endThreadNs) {
    node.setEndGlobal(TimeUnit.NANOSECONDS.toMicros(endGlobalNs));
    node.setEndThread(TimeUnit.NANOSECONDS.toMicros(endThreadNs));
  }

  /**
   * Parses the list of samples of a thread into a {@link CaptureNode} tree.
   */
  private void parseThreadSamples(int threadId, List<SimpleperfReport.Sample> threadSamples) {
    if (threadSamples.isEmpty()) {
      getLog().warn(String.format("Warning: No samples read for thread %s (%d)", myThreads.get(threadId), threadId));
      return;
    }

    if (!myThreads.containsKey(threadId)) {
      throw new IllegalStateException("Malformed trace file: thread with id " + threadId + " not found.");
    }

    // Add a root node to represent the thread itself.
    long firstTimestamp = threadSamples.get(0).getTime();
    // Align the start of each thread's thread time to the start of wall-clock start time, to comply with the logic
    // that synchronizes the two clocks in CpuAnalysisChartModel, similar to adjustNodesTimeAndDepth() in
    // ArtTraceHandler.
    long threadTimeNs = firstTimestamp;
    SimpleperfReport.Thread thread = myThreads.get(threadId);
    CaptureNode root = createCaptureNode(new SingleNameModel(thread.getThreadName()), firstTimestamp, threadTimeNs);
    root.setDepth(0);
    myCaptureTrees.put(new CpuThreadInfo(threadId, thread.getThreadName(), threadId == thread.getProcessId()), root);

    // Parse the first call chain so we have a value for lastCallchain
    List<SimpleperfReport.Sample.CallChainEntry> previousCallChain = Lists.reverse(threadSamples.get(0).getCallchainList());
    // Node used to traverse the tree. In the first traversal we pass an empty list as previous call chain and root as last visited node.
    CaptureNode lastVisitedNode = parseCallChain(previousCallChain, Collections.emptyList(), firstTimestamp,
                                                 threadTimeNs, root);

    // Now parse all the rest of the samples collected for this thread
    for (int i = 1; i < threadSamples.size(); i++) {
      SimpleperfReport.Sample sample = threadSamples.get(i);
      // Reverse the call chain order because simpleperf returns the call chains ordered from leaf to root,
      // so reversing it makes the traversal easier.
      List<SimpleperfReport.Sample.CallChainEntry> callChain = Lists.reverse(sample.getCallchainList());
      // A sample may be triggered by the when the thread is scheduled off the CPU, if --trace-offcpu is used
      // while collecting the trace.
      if (isThreadTimeSupported() && sample.getEventTypeId() == myCpuClockEventTypeId) {
        threadTimeNs += sample.getEventCount();
      }
      // TODO: when --trace-offcpu is supported, we will need to call updateAncestorsEndTime if sample has a "schedule" out event.
      lastVisitedNode = parseCallChain(callChain, previousCallChain, sample.getTime(), threadTimeNs, lastVisitedNode);
      previousCallChain = callChain;
    }

    // Finally, update the end timestamp of the nodes in the last sample of the thread, which should be the last sample's timestamp.
    // TODO: when --trace-offcpu is supported, we need to check if the last sample has a "schedule" out event before updating the end time.
    long lastTimestamp = mySamples.get(mySamples.size() - 1).getTime();
    updateAncestorsEndTime(lastTimestamp, threadTimeNs, lastVisitedNode);
    // update the root timestamp
    setNodeEndTime(root, lastTimestamp, threadTimeNs);
  }

  /**
   * Updates the end timestamp of a node and all its ancestors except the root.
   */
  private static void updateAncestorsEndTime(long globalTimeNs, long threadTimeNs, CaptureNode lastVisited) {
    CaptureNode node = lastVisited;
    while (node.getParent() != null && node.getEnd() == 0) {
      setNodeEndTime(node, globalTimeNs, threadTimeNs);
      node = node.getParent();
      assert node != null;
    }
  }

  /**
   * Given a {@link SimpleperfReport.Sample.CallChainEntry} and the previous one, add the new method calls as nodes to
   * the tree and set their start time to the given timestamps (GLOBAL and THREAD). Also, check which methods are not
   * on the call chain anymore and update their end time. Receives a {@link CaptureNode} as a starting point to
   * traverse the tree when adding new nodes or visiting existing ones. Returns the last visited node.
   */
  private CaptureNode parseCallChain(List<SimpleperfReport.Sample.CallChainEntry> callChain,
                                     List<SimpleperfReport.Sample.CallChainEntry> previousCallChain,
                                     long globalTimeNs, long threadTimeNs, CaptureNode lastVisitedNode) {
    // Node used to traverse the tree when adding new nodes or going up to find the divergent node ancestor.
    CaptureNode traversalNode = lastVisitedNode;

    // Find the node where the current call chain diverge from the previous one
    int divergenceIndex = 0;
    while (divergenceIndex < callChain.size() && divergenceIndex < previousCallChain.size() &&
           equals(previousCallChain.get(divergenceIndex), callChain.get(divergenceIndex))) {
      divergenceIndex++;
    }

    // If there is a divergence, we update the end time of the traversal node and go up in the tree until we find the divergent node parent.
    if (divergenceIndex < previousCallChain.size()) {
      int divergenceCount = previousCallChain.size() - divergenceIndex;
      traversalNode = findDivergenceAndUpdateEndTime(divergenceCount, globalTimeNs, threadTimeNs, traversalNode);
    }

    // We add the new nodes (if any) present in the new call chain as descendants of the parent of the first divergent node.
    if (divergenceIndex < callChain.size()) {
      traversalNode = addNewNodes(callChain, traversalNode, divergenceIndex, globalTimeNs, threadTimeNs);
    }

    // Finally, return the traversal node.
    return traversalNode;
  }

  /**
   * Updates the end timestamp of a given node and go up in the tree N times, where N is the divergence count passed as an argument.
   * Returns the parent of the last visited node, meaning nodes that we have changed the end time.
   */
  private static CaptureNode findDivergenceAndUpdateEndTime(int divergenceCount, long endGlobalNs, long endThreadNs,
                                                            CaptureNode node) {
    for (int i = 0; i < divergenceCount; i++) {
      assert node != null;
      setNodeEndTime(node, endGlobalNs, endThreadNs);
      node = node.getParent();
    }

    return node;
  }

  /**
   * Given a list of call chain entries and a start index, convert them to {@link CaptureNode} and add them as descendants of a given node.
   * Returns the last visited (added) node.
   */
  private CaptureNode addNewNodes(List<SimpleperfReport.Sample.CallChainEntry> callChain,
                                  CaptureNode node, int startIndex, long startGlobalNs, long startThreadNs) {
    assert node != null;
    for (int i = startIndex; i < callChain.size(); i++) {
      // Get the parent function vAddress. That corresponds to the line of the parent function where the current function is called.
      long parentVAddress = i > 0 ? callChain.get(i - 1).getVaddrInFile() : -1;
      CaptureNode child = createCaptureNode(methodModelFromCallchainEntry(callChain.get(i), parentVAddress),
                                            startGlobalNs, startThreadNs);
      node.addChild(child);
      child.setDepth(node.getDepth() + 1);
      node = child;
    }
    // Return the last added node, as it's the visited one
    return node;
  }

  private CaptureNodeModel methodModelFromCallchainEntry(SimpleperfReport.Sample.CallChainEntry callChainEntry, long parentVAddress) {
    int symbolId = callChainEntry.getSymbolId();
    SimpleperfReport.File symbolFile = myFiles.get(callChainEntry.getFileId());
    if (symbolFile == null) {
      throw new IllegalStateException("Symbol file with id \"" + callChainEntry.getFileId() + "\" not found.");
    }
    if (symbolId == INVALID_SYMBOL_ID) {
      // if symbol_id is -1, we report the method as fileName+vAddress (e.g. program.so+0x3039)
      String hexAddress = "0x" + Long.toHexString(callChainEntry.getVaddrInFile());
      String methodName = fileNameFromPath(symbolFile.getPath()) + "+" + hexAddress;
      return nodeWithTagAdded(new NoSymbolModel(symbolFile.getPath(), methodName));
    }
    // Otherwise, read the method from the symbol table and parse it into a CaptureNodeModel. User's code symbols come from
    // files located inside the app's directory, therefore we check if the symbol path has the same prefix of such directory.
    boolean isUserWritten = symbolFile.getPath().startsWith(myAppDataFolderPrefix);
    return nodeWithTagAdded(NodeNameParser.parseNodeName(symbolFile.getSymbol(symbolId),
                                                         isUserWritten, symbolFile.getPath(), parentVAddress));
  }

  private CaptureNodeModel nodeWithTagAdded(CaptureNodeModel node) {
    if (node.getTag() != null) {
      myTags.add(node.getTag());
    }
    return node;
  }

  // Order the tags coarsely depending on whether they're full paths or wild cards
  private static TagClass tagClass(String tag) {
    return tag.contains("*") ? TagClass.PREFIXED_PATH :
           tag.contains("[") ? TagClass.DESCRIPTION :
           TagClass.EXACT_PATH;
  }

  private enum TagClass {
    EXACT_PATH, DESCRIPTION, PREFIXED_PATH
  }

  @VisibleForTesting
  static Comparator<String> TAG_COMPARATOR =
    Comparator.comparing(SimpleperfTraceParser::tagClass).thenComparing(String::compareTo);
}
