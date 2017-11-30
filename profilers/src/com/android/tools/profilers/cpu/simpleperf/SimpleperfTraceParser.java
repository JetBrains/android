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

import com.android.annotations.VisibleForTesting;
import com.android.tools.adtui.model.Range;
import com.android.tools.profiler.proto.SimpleperfReport;
import com.android.tools.profilers.cpu.CaptureNode;
import com.android.tools.profilers.cpu.CpuThreadInfo;
import com.android.tools.profilers.cpu.MethodModel;
import com.android.tools.profilers.cpu.TraceParser;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Parses a trace file obtained using simpleperf to a map threadId -> {@link CaptureNode}.
 */
public class SimpleperfTraceParser implements TraceParser {

  /**
   * When the name of a function (symbol) is not found in the symbol table, the symbol_id field is set to -1.
   */
  private static final int INVALID_SYMBOL_ID = -1;

  /**
   * Maps a file id to its correspondent {@link SimpleperfReport.File}.
   */
  private final Map<Integer, SimpleperfReport.File> myFiles;

  /**
   * Maps a thread id to its correspondent name.
   */
  private final Map<Integer, String> myThreads;

  /**
   * List of samples containing method trace data.
   */
  @VisibleForTesting
  final List<SimpleperfReport.Sample> mySamples;

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
   * Capture range in absolute time, measured in microseconds.
   */
  private Range myRange;

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
  public void parse(File trace) throws IOException {
    parseTraceFile(trace);
    parseSampleData();
  }

  @Override
  public Map<CpuThreadInfo, CaptureNode> getCaptureTrees() {
    return myCaptureTrees;
  }

  @Override
  public Range getRange() {
    return myRange;
  }

  public long getLostSampleCount() {
    return myLostSampleCount;
  }

  public long getSampleCount() {
    return mySampleCount;
  }

  @NotNull
  private static CaptureNode createCaptureNode(MethodModel model, long timestamp) {
    CaptureNode node = new CaptureNode();
    node.setMethodModel(model);
    setNodeStartTime(node, timestamp);
    node.setDepth(0);
    return node;
  }

  /**
   * Parses the trace file, which should have the following format:
   * LittleEndian32(record_size_0)
   * SimpleperfReport.Record (having record_size_0 bytes)
   * LittleEndian32(record_size_1)
   * message Record(record_1) (having record_size_1 bytes)
   * ...
   * LittleEndian32(record_size_N)
   * message Record(record_N) (having record_size_N bytes)
   * LittleEndian32(0)
   *
   * Parsed data is stored in {@link #myFiles} and {@link #mySamples}.
   */
  @VisibleForTesting
  void parseTraceFile(File trace) throws IOException {
    ByteBuffer buffer = byteBufferFromFile(trace, ByteOrder.LITTLE_ENDIAN);
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
          myThreads.put(thread.getThreadId(), thread.getThreadName());
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
  }

  /**
   * Parses the data from {@link #mySamples} into a map of tid -> {@link CaptureNode}.
   */
  private void parseSampleData() {
    if (mySamples.isEmpty()) {
      return;
    }
    // Set the capture range
    long startTimestamp = mySamples.get(0).getTime();
    long endTimestamp = mySamples.get(mySamples.size() - 1).getTime();
    myRange = new Range(TimeUnit.NANOSECONDS.toMicros(startTimestamp), TimeUnit.NANOSECONDS.toMicros(endTimestamp));

    // Split the samples per thread.
    Map<Integer, List<SimpleperfReport.Sample>> threadSamples = splitSamplesPerThread();

    // Process the sampels for each thread
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

  // TODO: support thread time
  private static void setNodeEndTime(CaptureNode node, long endTimeNs) {
    node.setEndGlobal(TimeUnit.NANOSECONDS.toMicros(endTimeNs));
    node.setEndThread(TimeUnit.NANOSECONDS.toMicros(endTimeNs));
  }

  // TODO: support thread time
  private static void setNodeStartTime(CaptureNode node, long startTimeNs) {
    node.setStartGlobal(TimeUnit.NANOSECONDS.toMicros(startTimeNs));
    node.setStartThread(TimeUnit.NANOSECONDS.toMicros(startTimeNs));
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
    CaptureNode root = createCaptureNode(new MethodModel.Builder(myThreads.get(threadId)).build(), firstTimestamp);
    root.setDepth(0);
    myCaptureTrees.put(new CpuThreadInfo(threadId, myThreads.get(threadId)), root);

    // Parse the first call chain so we have a value for lastCallchain
    List<SimpleperfReport.Sample.CallChainEntry> previousCallChain = Lists.reverse(threadSamples.get(0).getCallchainList());
    // Node used to traverse the tree. In the first traversal we pass an empty list as previous call chain and root as last visited node.
    CaptureNode lastVisitedNode = parseCallChain(previousCallChain, Collections.emptyList(), threadSamples.get(0).getTime(), root);

    // Now parse all the rest of the samples collected for this thread
    for (int i = 1; i < threadSamples.size(); i++) {
      SimpleperfReport.Sample sample = threadSamples.get(i);
      // Reverse the call chain order because simpleperf returns the call chains ordered from leaf to root,
      // so reversing it makes the traversal easier.
      List<SimpleperfReport.Sample.CallChainEntry> callChain = Lists.reverse(sample.getCallchainList());
      // TODO: when --trace-offcpu is supported, we will need to call updateAncestorsEndTime if sample has a "schedule" out event.
      lastVisitedNode = parseCallChain(callChain, previousCallChain, sample.getTime(), lastVisitedNode);
      previousCallChain = callChain;
    }

    // Finally, update the end timestamp of the nodes in the last sample of the thread, which should be the last sample's timestamp.
    // TODO: when --trace-offcpu is supported, we need to check if the last sample has a "schedule" out event before updating the end time.
    long lastTimestamp = mySamples.get(mySamples.size() - 1).getTime();
    updateAncestorsEndTime(lastTimestamp, lastVisitedNode);
    // update the root timestamp
    setNodeEndTime(root, lastTimestamp);
  }

  /**
   * Updates the end timestamp of a node and all its ancestors except the root.
   */
  private static void updateAncestorsEndTime(long endTimestamp, CaptureNode lastVisited) {
    CaptureNode node = lastVisited;
    while (node.getParent() != null && node.getEnd() == 0) {
      setNodeEndTime(node, endTimestamp);
      node = node.getParent();
      assert node != null;
    }
  }

  /**
   * Given a {@link SimpleperfReport.Sample.CallChainEntry} and the previous one, add the new method calls as nodes to the tree and set
   * their start time to the given timestamp. Also, check which methods are not on the call chain anymore and update their end time.
   * Receives a {@link CaptureNode} as a starting point to traverse the tree when adding new nodes or visiting existing ones. Returns the
   * last visited node.
   */
  private CaptureNode parseCallChain(List<SimpleperfReport.Sample.CallChainEntry> callChain,
                                     List<SimpleperfReport.Sample.CallChainEntry> previousCallChain,
                                     long sampleTimestamp, CaptureNode lastVisitedNode) {
    // Node used to traverse the tree when adding new nodes or going up to find the divergent node ancestor.
    CaptureNode traversalNode = lastVisitedNode;

    // Find the node whre the current call chain diverge from the previous one
    int divergenceIndex = 0;
    while (divergenceIndex < callChain.size() && divergenceIndex < previousCallChain.size() &&
           equals(previousCallChain.get(divergenceIndex), callChain.get(divergenceIndex))) {
      divergenceIndex ++;
    }

    // If there is a divergence, we update the end time of the traversal node and go up in the tree until we find the divergent node parent.
    if (divergenceIndex < previousCallChain.size()) {
      int divergenceCount = previousCallChain.size() - divergenceIndex;
      traversalNode = findDivergenceAndUpdateEndTime(divergenceCount, sampleTimestamp, traversalNode);
    }

    // We add the new nodes (if any) present in the new call chain as descendants of the parent of the first divergent node.
    if (divergenceIndex < callChain.size()) {
      traversalNode = addNewNodes(callChain, traversalNode, divergenceIndex, sampleTimestamp);
    }

    // Finally, return the traversal node.
    return traversalNode;
  }

  /**
   * Updates the end timestamp of a given node and go up in the tree N times, where N is the divergence count passed as an argument.
   * Returns the parent of the last visited node, meaning nodes that we have changed the end time.
   */
  private static CaptureNode findDivergenceAndUpdateEndTime(int divergenceCount, long endTimestamp, CaptureNode node) {
    for (int i = 0; i < divergenceCount; i++) {
      assert node != null;
      setNodeEndTime(node, endTimestamp);
      node = node.getParent();
    }

    return node;
  }

  /**
   * Given a list of call chain entries and a start index, convert them to {@link CaptureNode} and add them as descendants of a given node.
   * Returns the last visited (added) node.
   */
  private CaptureNode addNewNodes(List<SimpleperfReport.Sample.CallChainEntry> callChain,
                                  CaptureNode node, int startIndex, long startTimestamp) {
    assert node != null;
    for (int i = startIndex; i < callChain.size(); i++) {
      CaptureNode child = createCaptureNode(methodModelFromCallchainEntry(callChain.get(i)), startTimestamp);
      node.addChild(child);
      child.setDepth(node.getDepth() + 1);
      node = child;
    }
    // Return the last added node, as it's the visited one
    return node;
  }

  private MethodModel methodModelFromCallchainEntry(SimpleperfReport.Sample.CallChainEntry callChainEntry) {
    int symbolId = callChainEntry.getSymbolId();
    SimpleperfReport.File symbolFile = myFiles.get(callChainEntry.getFileId());
    if (symbolFile == null) {
      throw new IllegalStateException("Symbol file with id \"" + callChainEntry.getFileId() + "\" not found.");
    }
    if (symbolId == INVALID_SYMBOL_ID) {
      // if symbol_id is -1, we report the method as fileName+vAddress (e.g. program.so+0x3039)
      String hexAddress = "0x" + Long.toHexString(callChainEntry.getVaddrInFile());
      String methodName = fileNameFromPath(symbolFile.getPath()) + "+" + hexAddress;
      return new MethodModel.Builder(methodName).build();
    }
    // Otherwise, read the method from the symbol table and parse it into a MethodModel
    return MethodNameParser.parseMethodName(symbolFile.getSymbol(symbolId));
  }
}
