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
package com.android.tools.idea.uibuilder.structure;

import com.android.tools.idea.common.model.ItemTransferable;
import com.android.tools.idea.common.model.NlComponent;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreePath;
import java.awt.datatransfer.Transferable;

/**
 * Indicates that the implementing class can handle a {@link DelegatedTreeEvent}.
 *
 * This is used to delegate events from {@link NlComponentTree} to a {@link com.android.tools.idea.uibuilder.api.ViewGroupHandler}
 * for non NlComponent operations.
 */
public interface DelegatedTreeEventHandler {

  /**
   * The implementing class will be called using this method if the {@link com.android.tools.idea.uibuilder.api.ViewHandler}
   * corresponding to parentComponent implements this class.
   *
   * @param event           The event object containing the data about the event
   * @param parentComponent The receiver of the event used to get the handler.
   * @return true if the event has been handled false otherwise.
   * @see com.android.tools.idea.uibuilder.model.NlComponentHelperKt#getLayoutHandler(NlComponent)
   */
  boolean handleTreeEvent(@NotNull DelegatedTreeEvent event, @NotNull NlComponent parentComponent);

  /**
   * Since the tree event does not happen on a {@link NlComponent},
   * the implementing class should provide a {@link Transferable} for the given
   * path in order to enable a drag and drop operation on them.
   *<p>
   * A basic implementation can define its own {@link java.awt.datatransfer.DataFlavor} and
   * use it in the implementation of the {@link Transferable} interface.
   * <p>
   * If the {@link DelegatedTreeEventHandler} can also handle {@link NlComponent} it can use an
   * {@link ItemTransferable} and/or
   * the {@link ItemTransferable#DESIGNER_FLAVOR}
   */
  Transferable getTransferable(TreePath[] paths);
}
