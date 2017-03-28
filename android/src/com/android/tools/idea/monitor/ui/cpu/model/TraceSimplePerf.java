package com.android.tools.idea.monitor.ui.cpu.model;

import com.android.tools.adtui.model.HNode;
import com.android.tools.adtui.chart.hchart.Method;
import com.android.tools.adtui.chart.hchart.Separators;
import com.android.tools.profiler.proto.SimpleperfReport;
import com.android.utils.SparseArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class TraceSimplePerf extends AppTrace {

  // Protobuffer trace as it is generated from "simpleperf report"
  private final File myTraceFile;

  SparseArray<HNode<Method>> forest;

  private static final String KERNEL_SYMBOL = "[kernel.kallsyms]";

  public TraceSimplePerf(File traceFile) {
    myTraceFile = traceFile;
    forest = new SparseArray<>();
  }

  @Override
  public Source getSource() {
    return Source.SIMPLEPERF;
  }

  @Override
  public String getSeparator() {
    return Separators.NATIVE_CODE;
  }

  @Override
  public void parse() throws IOException {
    SparseArray<Long> maxSampleTimes = new SparseArray<>();
    FileInputStream in = new FileInputStream(myTraceFile);
    while (true) {
      int size = readNextIntFromStream(in);
      if (size == 0) {
        break;
      }
      byte[] buf = new byte[size];
      int read = in.read(buf);
      if (read != size) {
        throw new IOException("End of stream reached unexpectingly");
      }
      SimpleperfReport.Record record = SimpleperfReport.Record.parseFrom(buf);
      addRecordToModel(record, maxSampleTimes);
    }
    markCallEnds(maxSampleTimes);
    in.close();
  }

  private int readNextIntFromStream(FileInputStream in) throws IOException {
    byte[] rawBuffer = new byte[4];
    int read = in.read(rawBuffer);
    if (read != 4) {
      throw new IOException("Stream is unaligned (unable to read 4 bytes).");
    }
    ByteBuffer buffer = ByteBuffer.wrap(rawBuffer);
    buffer.order(ByteOrder.LITTLE_ENDIAN);
    return buffer.getInt();
  }

  private void addRecordToModel(SimpleperfReport.Record record, SparseArray<Long> maxSampleTime) {
    SimpleperfReport.Sample sample = record.getSample();
    if (sample == null) {
      return;
    }

    if (sample.getCallchainCount() == 0) {
      return;
    }

    int threadId = sample.getThreadId();
    long sampleTime = sample.getTime();
    maxSampleTime.put(threadId, sampleTime);
    // Retrieve the ongoing tree associate with this thread.
    HNode<Method> tree = forest.get(sample.getThreadId());
    if (tree == null) {
      tree = new HNode();
      tree.setStart(sampleTime);
      Method rootMethod = new Method();
      rootMethod.setName("root()");
      rootMethod.setNamespace("root.package.foo.bar");
      tree.setData(rootMethod);
      forest.put(sample.getThreadId(), tree);
    }

    // Compare last captured stack with current stack. Stop as soon as they diverge.
    int depth = sample.getCallchainCount() - 1;
    HNode<Method> previousNode = tree;
    HNode<Method> currentNode = previousNode.getLastChild();
    while (currentNode != null && depth >= 0 && isSameMethod(currentNode.getData(),
                                                             sample.getCallchain(depth))) {
      depth--;
      previousNode = currentNode;
      currentNode = currentNode.getLastChild();
    }

    // We found the point where the stacks diverge. We need to:
    // 1. Mark previous calls are ended via timestamps.
    // 2. Insert all new calls which are currently ongoing.

    //1. Mark all previous calls as ended.
    markCallEnds(currentNode, sampleTime);

    //2. Those are new calls on the stack: Add them to the tree.
    while (depth >= 0) {
      SimpleperfReport.Sample.CallChainEntry invocation = sample.getCallchain(depth);

      // Discard all kernel stack frames
      if (KERNEL_SYMBOL.equals(invocation.getFile())) {
        break;
      }

      // New node data is a Method.
      Method m = new Method();
      parseInvocation(invocation, m);
      m.setFilename(invocation.getFile());
      HNode<Method> newNode = new HNode<>();
      newNode.setStart(sampleTime);
      newNode.setData(m);
      newNode.setDepth(sample.getCallchainCount() - depth - 1);
      previousNode.addHNode(newNode);

      previousNode = newNode;
      depth--;
    }
  }

  void markCallEnds(HNode node, long endTime) {
    while (node != null) {
      node.setEnd(endTime);
      node = node.getLastChild();
    }
  }

  private boolean isSameMethod(Method method, SimpleperfReport.Sample.CallChainEntry invocation) {
    Method parseInvocation = new Method();
    parseInvocation(invocation, parseInvocation);
    return method.getName().equals(parseInvocation.getName()) &&
           method.getNameSpace().equals(parseInvocation.getNameSpace());
  }

  // Symbol name can be many things:
  // If the unwinder failed, it will be "unknown"
  // From a C symbol it is just the name of the function "memset"
  // From a C++ symbol it is namespace::functionname(parameters)
  private void parseInvocation(SimpleperfReport.Sample.CallChainEntry invocation, Method m) {
    m.setNamespace("");
    String symbol = invocation.getSymbol();
    if (symbol.startsWith("*")) { // simpleperf markes unknown symbol with a leading '*'.
      String file = invocation.getFile();
      if (file.contains("/")) {
        m.setName(file.substring(file.lastIndexOf("/") + 1));
      }
      else {
        m.setName(invocation.getFile());
      }
      m.setName("[" + m.getName() + "]");
    }
    else {
      if (symbol.contains("(")) {
        symbol = symbol.substring(0, symbol.indexOf("("));
        if (symbol.contains("::")) {
          m.setNamespace(symbol.substring(0, symbol.lastIndexOf("::")));
          m.setName(symbol.substring(symbol.lastIndexOf("::") + 2));
        }
        else {
          m.setName(symbol);
        }
      }
      else {
        m.setName(invocation.getSymbol());
      }
    }

  }

  private void markCallEnds(SparseArray<Long> maxSampleTimes) {
    for (int i = 0; i < forest.size(); i++) {
      int threadId = forest.keyAt(i);
      HNode node = forest.get(threadId);
      long sampleTime = maxSampleTimes.get(threadId);
      markCallEnds(node, sampleTime);
    }
  }

  @Override
  public SparseArray<HNode<Method>> getThreadsGraph() {
    return forest;
  }
}
