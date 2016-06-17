package com.android.tools.idea.monitor.ui.cpu.model;

import com.android.tools.profiler.proto.Cpu;
import gnu.trove.TLongArrayList;

import java.util.ArrayList;
import java.util.List;

public final class ThreadStatesDataModel {

  private final String myName;

  // Process ID (a.k.a: tid)
  private final int myPid;

  private final List<Cpu.ThreadActivity.State> myThreadStates = new ArrayList<>();

  /**
   * Timestamps of the thread state changes.
   */
  private final TLongArrayList myTimestamps = new TLongArrayList();

  public ThreadStatesDataModel(String name, int pid) {
    myName = name;
    myPid = pid;
  }

  public void addState(Cpu.ThreadActivity.State newState, long timestamp) {
    myThreadStates.add(newState);
    myTimestamps.add(timestamp);
  }

  public TLongArrayList getTimestamps() {
    return myTimestamps;
  }

  public List<Cpu.ThreadActivity.State> getThreadStates() {
    return myThreadStates;
  }

  public String getName() {
    return myName;
  }

  public int getPid() {
    return myPid;
  }
}