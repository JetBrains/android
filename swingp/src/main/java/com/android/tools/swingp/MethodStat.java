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
package com.android.tools.swingp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

/**
 * A stat counter that surrounds and captures timing and call information of a sequence of calls within a stack frame.
 */
public abstract class MethodStat {
  @NotNull private final List<MethodStat> myChildStats = new ArrayList<>(1);
  /**
   * {@link #myOwner} is the {@link Object} of the method in which this {@link MethodStat} was instantiated.
   * This will be useful in determining what kind of {@link javax.swing.JComponent} is getting rendered.
   */
  @NotNull protected final SoftReference<?> myOwner;
  private long myStartTime = System.nanoTime();
  private long myEndTime;

  public MethodStat(@NotNull Object owner) {
    myOwner = new SoftReference<>(owner);
    // TODO: instrument caller with try-catch as well.
    RenderStatsManager.push(this);
  }

  @NotNull
  public final JsonElement getDescription() {
    JsonObject description = new JsonObject();
    description.addProperty("__type", getClass().getSimpleName());
    description.addProperty("__startTime", getStartTime());
    addAttributeDescriptions(description);
    return description;
  }

  /**
   * Subclasses of this class should override this method with more relevant attribute/child attributes related to the implementing stats.
   */
  protected void addAttributeDescriptions(@NotNull JsonObject description) {
    Object owner = myOwner.get();
    description.addProperty("__owner", owner == null ? "<gc>" : owner.getClass().getSimpleName());
    description.addProperty("__endTime", getEndTime());

    JsonArray callees = new JsonArray();
    myChildStats.stream().map(methodStat -> methodStat.getDescription()).forEach(jsonElement -> callees.add(jsonElement));
    description.add("__callee", callees);
  }

  /**
   * This method HAS to be called at the end of the function that created this object.
   */
  public void endMethod() {
    myEndTime = System.nanoTime();
    RenderStatsManager.pop(this);
  }

  /**
   * Adds a {@MethodStat} as a descendant in the call tree.
   */
  void addChildStat(@NotNull MethodStat codeStat) {
    myChildStats.add(codeStat);
  }

  /**
   * @return the time at which this object was constructed.
   */
  protected long getStartTime() {
    return myStartTime;
  }

  /**
   * @return the time at which {@link #endMethod()} was invoked.
   */
  protected long getEndTime() {
    return myEndTime;
  }
}
