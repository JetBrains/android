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

package com.android.tools.adtui.visual.flamegraph;

import com.android.tools.adtui.hchart.HNode;

import java.util.HashMap;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.TimeUnit;

public class Sampler implements Runnable {

    volatile boolean running;

    HashMap<String, HNode<MethodUsage>> furnace;

    private final static long SAMPLING_RESOLUTION = TimeUnit.MILLISECONDS.toMillis(1);

    public static final int MAX_VALUE = 10000;

    public Sampler() {
        this.running = false;
    }


    public void startSampling() {
        this.running = true;
        this.furnace = new HashMap();
        Thread t = new Thread(this);
        t.start();
    }

    public void stopSampling() {
        this.running = false;
        generateStartsAndEnds();
    }

    @Override
    public void run() {
        while (running) {
            try {
                Thread.sleep(SAMPLING_RESOLUTION);
                if (running) {
                    this.sample();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void sample() {
        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        for (Thread sampledThread : threadSet) {
            StackTraceElement[] elements = sampledThread.getStackTrace();

            // Some threads (e.g: SignalDispatcher) have no elements.
            if (elements.length == 0) {
                continue;
            }

            // Retrieve the ongoing flame associate with this thread.
            HNode<MethodUsage> flame = furnace.get(sampledThread.getName());
            if (flame == null) {
                flame = new HNode();
                MethodUsage m = new MethodUsage();
                flame.setData(m);
                furnace.put(sampledThread.getName(), flame);
            }

            // Make sure this stacktrace has nodes. Create them if needed.
            ensureNodesExist(elements, flame);

            // Increase invocation counter for all element in the tracktrace.
            int depth = elements.length - 1;
            while (depth >= 0) {
                flame = findChildWithMethod(elements[depth], flame);
                flame.getData().incInvocationCounter();
                depth--;
            }
        }
    }

    private boolean isSameMethod(HNode<MethodUsage> currentNode, StackTraceElement element) {
        return currentNode.getData().getName().equals(element.getMethodName()) &&
                currentNode.getData().getNameSpace().equals(element.getClassName());
    }

    private void ensureNodesExist(StackTraceElement[] trace, HNode<MethodUsage> root) {
        HNode<MethodUsage> previous;
        HNode<MethodUsage> current = root;

        int depth = trace.length - 1;
        while (depth >= 0) {
            StackTraceElement currentMethod = trace[depth];
            previous = current;
            current = findChildWithMethod(currentMethod, previous);
            if (current == null) {
                // Create a new node.
                current = new HNode<>();
                current.setDepth(trace.length - depth);
                MethodUsage m = new MethodUsage();
                m.setInvocationCount(0);
                m.setNamespace(currentMethod.getClassName());
                m.setName(currentMethod.getMethodName());
                current.setData(m);
                // Add new node to parent
                previous.addHNode(current);
            }
            depth--;
        }
    }

    private HNode<MethodUsage> findChildWithMethod(StackTraceElement stackTraceElement,
            HNode<MethodUsage> node) {
        for (HNode<MethodUsage> n : node.getChildren()) {
            if (isSameMethod(n, stackTraceElement)) {
                return n;
            }
        }
        return null;
    }

    private void generateStartsAndEnds() {
        for (String threadName : furnace.keySet()) {
            HNode<MethodUsage> root = furnace.get(threadName);
            // HChart nodes are integers. We represent percentage with two decimals * 100.
            root.setStart(0);
            root.setEnd(MAX_VALUE);
            root.getData().setInvocationCount(sumInvocationCountForTree(root));
            // Recursion
            generateChildsStartsAndEnds(root);
        }
    }

    private void generateChildsStartsAndEnds(HNode<MethodUsage> node) {
        // Get total invocation count for this depth level.
        int childInvocationTotal = 0;
        for (HNode<MethodUsage> n : node.getChildren()) {
            childInvocationTotal += n.getData().getInvocationCount();
        }

        long childWidth = node.getEnd() - node.getStart();

        // For each child, generate: start, end and the absolute percentage.
        long base = node.getStart();
        for (HNode<MethodUsage> child : node.getChildren()) {
            child.setStart(base);
            child.setEnd(base +
                    (long) (childWidth * (child.getData().getInvocationCount()
                            / (float) childInvocationTotal)));
            child.getData().setPercentage((child.getEnd() - child.getStart()) / (float)(MAX_VALUE));
            base = child.getEnd();
        }

        // Recurse for each child
        for (HNode<MethodUsage> child : node.getChildren()) {
            generateChildsStartsAndEnds(child);
        }
    }

    private int sumInvocationCountForTree(HNode<MethodUsage> root) {
        int invocationCount = 0;
        Stack<HNode<MethodUsage>> stack = new Stack<>();
        stack.addAll(root.getChildren());
        while (!stack.isEmpty()) {
            HNode<MethodUsage> n = stack.pop();
            invocationCount += n.getData().getInvocationCount();
            stack.addAll(n.getChildren());
        }
        return invocationCount;
    }

    public HashMap<String, HNode<MethodUsage>> getData() {
        return furnace;
    }
}
