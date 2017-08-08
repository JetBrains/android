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
public class SimplePerfTraceParser implements TraceParser {

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
   * Maps a thread id to its last callchain collected in samples.
   */
  private final Map<Integer, List<SimpleperfReport.Sample.CallChainEntry>> myLastCallChain;

  /**
   * Stores the {@link CaptureNode} on the top of the last call stack corresponding to a thread.
   * Storing a {@link CaptureNode} for each thread is important, for instance,
   * to avoid parsing the same call chain multiple times.
   */
  private final Map<Integer, CaptureNode> myLastCallStackTopNode;

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

  public SimplePerfTraceParser() {
    myFiles = new HashMap<>();
    mySamples = new ArrayList<>();
    myCaptureTrees = new HashMap<>();
    myLastCallChain = new HashMap<>();
    myLastCallStackTopNode = new HashMap<>();
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
    return c1.getVaddrInFile() == c2.getVaddrInFile() && c1.getFileId() == c2.getFileId() && c1.getSymbolId() == c2.getSymbolId();
  }

  private static Logger getLog() {
    return Logger.getInstance(SimplePerfTraceParser.class);
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
  private static CaptureNode createCaptureNode(String name, long timestamp) {
    CaptureNode node = new CaptureNode();
    node.setMethodModel(new MethodModel(name));
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
    long startTimestamp = mySamples.get(0).getTime();

    // Process each sample
    for (SimpleperfReport.Sample sample : mySamples) {
      parseCallChain(sample.getCallchainList(), sample.getThreadId(), sample.getTime());
    }

    // Update the end timestamp of the last active call chain of each thread
    long endTimestamp = mySamples.get(mySamples.size() - 1).getTime();
    myRange = new Range(TimeUnit.NANOSECONDS.toMicros(startTimestamp), TimeUnit.NANOSECONDS.toMicros(endTimestamp));
    for (CaptureNode lastNode : myLastCallStackTopNode.values()) {
      CaptureNode node = lastNode;
      while (node != null && node.getEnd() == 0) {
        setNodeEndTime(node, endTimestamp);
        node = node.getParent();
      }
    }
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
   * Given a {@link SimpleperfReport.Sample.CallChainEntry}, a thread id and a sample timestamp, update the {@link CaptureNode}
   * corresponding to that thread with the information obtained from the call chain.
   */
  private void parseCallChain(List<SimpleperfReport.Sample.CallChainEntry> callChain, int threadId, long timestamp) {
    if (!myLastCallStackTopNode.containsKey(threadId)) {
      // if there is no entry for threadId in the map, create one to represent the thread itself.
      CaptureNode main = createCaptureNode(myThreads.getOrDefault(threadId, "main"), timestamp);
      main.setDepth(0);
      if (!myThreads.containsKey(threadId)) {
        throw new IllegalStateException("Malformed trace file: thread with id " + threadId + " not found.");
      }
      myCaptureTrees.put(new CpuThreadInfo(threadId, myThreads.get(threadId)), main);
      myLastCallStackTopNode.put(threadId, main);
      // Initiate the previous call chain as an empty list
      myLastCallChain.put(threadId, new LinkedList<>());
    }

    List<SimpleperfReport.Sample.CallChainEntry> previousCallChain = myLastCallChain.get(threadId);
    // First, identify where the call chains diverge, so we update the endTime of the nodes that are not in the call chain anymore.
    // If the last call chain is empty, there is no divergent index and no end values need to be updated.
    // TODO: We probably can just reverse the callchain in the beginning of the method with no performance impact.
    // Revisit that later to check that and make the change to simplify the code. Make sure to benchmark to verify the efficiency.
    int previousCallChainIndex = previousCallChain.size() - 1;
    int newCallChainIndex = callChain.size() - 1;
    CaptureNode divergentNodeParent = null;
    if (!previousCallChain.isEmpty()) {
      while (previousCallChainIndex >= 0 && newCallChainIndex >= 0 &&
             equals(previousCallChain.get(previousCallChainIndex), callChain.get(newCallChainIndex))) {
        previousCallChainIndex--;
        newCallChainIndex--;
      }
      divergentNodeParent = findDivergenceAndUpdateEndTime(previousCallChainIndex, threadId, timestamp);
    }

    // Now, add the nodes of the new call chain to the tree
    if (newCallChainIndex >= 0) {
      divergentNodeParent = divergentNodeParent == null ? myLastCallStackTopNode.get(threadId) : divergentNodeParent;
      addNewNodes(callChain, divergentNodeParent, newCallChainIndex, timestamp, threadId);
    }

    // Finally, update previous call chain and previous last node
    myLastCallChain.put(threadId, callChain);
  }

  /**
   * Update the end timestamp of the last call chain node corresponding to a thread.
   * Then, go backwards and do the same to the ancestors of the node until the newly read call chain
   * matches with the previous one. When a divergence is found, return the parent of the divergent node.
   */
  private CaptureNode findDivergenceAndUpdateEndTime(int divergenceCount, int tid, long endTimestamp) {
    CaptureNode node = myLastCallStackTopNode.get(tid);
    for (int i = 0; i < divergenceCount; i++) {
      assert node != null;
      setNodeEndTime(node, endTimestamp);
      node = node.getParent();
    }

    // Node should be the parent of the first divergent node
    return node;
  }

  /**
   * Given a list of call chain entries and a start index, convert them to {@link CaptureNode}
   * and add them to the call tree corresponding to the thread id passed as argument, as descendants
   * of a given node.
   */
  private void addNewNodes(List<SimpleperfReport.Sample.CallChainEntry> callChain,
                           CaptureNode node, int startIndex, long startTimestamp, int tid) {
    assert node != null;
    for (int i = startIndex; i >= 0; i--) {
      CaptureNode child = createCaptureNode(parseMethodName(callChain.get(i)), startTimestamp);
      node.addChild(child);
      child.setDepth(node.getDepth() + 1);
      node = child;
    }
    // Update the pointer to the last call chain node
    myLastCallStackTopNode.put(tid, node);
  }

  private String parseMethodName(SimpleperfReport.Sample.CallChainEntry callChainEntry) {
    int symbolId = callChainEntry.getSymbolId();
    SimpleperfReport.File symbolFile = myFiles.get(callChainEntry.getFileId());
    if (symbolFile == null) {
      throw new IllegalStateException("Symbol file with id \"" + callChainEntry.getFileId() + "\" not found.");
    }
    String methodName;
    if (symbolId == INVALID_SYMBOL_ID) {
      // if symbol_id is -1, we report the method as fileName+vAddress (e.g. program.so+0x3039)
      String hexAddress = "0x" + Long.toHexString(callChainEntry.getVaddrInFile());
      methodName = fileNameFromPath(symbolFile.getPath()) + "+" + hexAddress;
    }
    else {
      // otherwise, read the method name from the symbol table
      methodName = symbolFile.getSymbol(symbolId);
    }
    return methodName;
  }
}
