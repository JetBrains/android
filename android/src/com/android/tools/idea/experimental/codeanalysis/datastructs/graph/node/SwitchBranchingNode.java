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
package com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node;


import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.SwitchCaseGraph;
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public interface SwitchBranchingNode extends GraphNode {

  /**
   * switch (value) {
   *   case label1:
   *    break;
   *   case label2:
   *    break;
   *   default:
   *    break;
   * }
   * @return the value.
   */
  @NotNull
  public Value getCheckedValue();

  /**
   * switch (value) {
   *   case label1:
   *    break;
   *   case label2:
   *    break;
   *   default:
   *    break;
   * }
   * @return The nodes after the default. Return null if default does not exist.
   */
  @Nullable
  public GraphNode getDefaultTarget();

  /**
   * Set the target node of default.
   * @param target The target node.
   */
  public void setDefaultTarget(@NotNull GraphNode target);

  /**
   * Get all the labels after the case statements.
   * @return A new array of values of labels.
   */
  @NotNull
  public Value[] getKeys();

  /**
   * Get the target node of a certain label.
   * Return null if not found.
   * @param key The value of the label.
   * @return The target node
   */
  @Nullable
  public GraphNode getTargetViaKey(Value key);

  /**
   * Set the target node of a given label.
   * @param key The value of the label.
   * @param target The target.
   */
  public void setTargetViaKey(@NotNull Value key, @NotNull GraphNode target);

  /**
   * Set the BlockGraph body for this switch case statement.
   * @param graph The graph body.
   */
  public void setSwitchCaseGraph(@NotNull SwitchCaseGraph graph);

  /**
   * Get the BlockGraph of the code body of this switch case statement.
   * @return The graph body
   */
  @NotNull
  public SwitchCaseGraph getSwitchCaseGraph();

}
