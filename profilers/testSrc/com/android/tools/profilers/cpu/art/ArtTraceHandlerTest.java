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
package com.android.tools.profilers.cpu.art;

import com.android.tools.perflib.vmtrace.VmTraceParser;
import com.android.tools.profilers.cpu.CaptureNode;
import com.android.tools.profilers.cpu.CpuProfilerTestUtils;
import com.android.tools.profilers.cpu.CpuThreadInfo;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import static org.junit.Assert.*;

public class ArtTraceHandlerTest {
  @Test
  public void testTreeStructure() throws IOException {
    CaptureNode node = parseCaptureNode();
    assertEquals("AsyncTask #1", node.getData().getId());
    expectedChildrenIds(node, "android/os/Debug.startMethodTracing(Ljava/lang/String;)V",
                        "com/test/android/traceview/Basic.foo()V",
                        "android/os/Debug.stopMethodTracing()V");

    expectedChildrenIds(node.getChildren().get(0), "android/os/Debug.startMethodTracing(Ljava/lang/String;II)V");
    expectedChildrenIds(node.getChildren().get(1), "com/test/android/traceview/Basic.bar()I");
    expectedChildrenIds(node.getChildren().get(2), "dalvik/system/VMDebug.stopMethodTracing()V");
    expectedChildrenIds(node.getChildren().get(0).getChildren().get(0),
                        "dalvik/system/VMDebug.startMethodTracing(Ljava/lang/String;II)V");
  }

  @Test
  public void testDepth() throws IOException {
    CaptureNode node = parseCaptureNode();

    Queue<CaptureNode> queue = new LinkedList<>();
    queue.add(node);
    while (!queue.isEmpty()) {
      CaptureNode curNode = queue.poll();
      if (curNode.getParent() == null) {
        assertEquals(0, curNode.getDepth());
      } else {
        assertEquals(curNode.getParent().getDepth() + 1, curNode.getDepth());
      }

      queue.addAll(curNode.getChildren());
    }
  }

  @Test
  public void testGlobalAndThreadTime() throws IOException {
    Map<String, String> expected = new HashMap<>();
    expected.put("AsyncTask #1",
                 "global: 1374703971214985-1374703971215049, thread: 1374703971214985-1374703971215049");
    expected.put("android/os/Debug.startMethodTracing(Ljava/lang/String;)V",
                 "global: 1374703971214986-1374703971214994, thread: 1374703971214985-1374703971214994");
    expected.put("com/test/android/traceview/Basic.foo()V",
                 "global: 1374703971215001-1374703971215008, thread: 1374703971215001-1374703971215008");
    expected.put("android/os/Debug.stopMethodTracing()V",
                 "global: 1374703971215030-1374703971215048, thread: 1374703971215030-1374703971215048");
    expected.put("android/os/Debug.startMethodTracing(Ljava/lang/String;II)V",
                 "global: 1374703971214987-1374703971214992, thread: 1374703971214986-1374703971214992");
    expected.put("com/test/android/traceview/Basic.bar()I",
                 "global: 1374703971215004-1374703971215006, thread: 1374703971215004-1374703971215006");
    expected.put("dalvik/system/VMDebug.stopMethodTracing()V",
                 "global: 1374703971215046-1374703971215047, thread: 1374703971215046-1374703971215047");
    expected.put("dalvik/system/VMDebug.startMethodTracing(Ljava/lang/String;II)V",
                 "global: 1374703971214988-1374703971214989, thread: 1374703971214987-1374703971214988");

    CaptureNode node = parseCaptureNode();
    Queue<CaptureNode> queue = new LinkedList<>();
    queue.add(node);
    while (!queue.isEmpty()) {
      CaptureNode curNode = queue.poll();
      String result = String.format("global: %d-%d, thread: %d-%d",
                                    curNode.getStartGlobal(), curNode.getEndGlobal(),
                                    curNode.getStartThread(), curNode.getEndThread());
      assertTrue(expected.containsKey(curNode.getCaptureNodeModel().getId()));
      assertEquals(expected.get(curNode.getCaptureNodeModel().getId()), result);
      queue.addAll(curNode.getChildren());
    }
  }

  private static CaptureNode parseCaptureNode() throws IOException {
    ArtTraceHandler handler = new ArtTraceHandler();
    VmTraceParser parser = new VmTraceParser(CpuProfilerTestUtils.getTraceFile("basic.trace"), handler);
    parser.parse();

    Map<CpuThreadInfo, CaptureNode> trees = handler.getThreadsGraph();
    assertEquals(1, trees.size());
    CpuThreadInfo thread = trees.keySet().iterator().next();
    assertEquals("AsyncTask #1", thread.getName());
    assertEquals(11, thread.getId());

    return trees.get(thread);
  }

  private static void expectedChildrenIds(CaptureNode node, String... ids) {
    assertEquals(ids.length, node.getChildren().size());
    for (int i = 0; i < ids.length; ++i) {
      assertEquals(ids[i], node.getChildren().get(i).getData().getId());
    }
  }
}