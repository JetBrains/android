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
package com.android.tools.profilers.memory;

import static com.android.tools.profilers.memory.adapters.MemoryObject.INVALID_VALUE;

import com.android.tools.adtui.model.formatter.NumberFormatter;
import com.android.tools.profilers.ProfilerLayout;
import com.android.tools.profilers.memory.adapters.MemoryObject;
import com.android.tools.profilers.memory.adapters.ValueObject;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import java.util.Comparator;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;
import java.util.function.ToLongFunction;
import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.SortOrder;
import javax.swing.SwingConstants;
import org.jetbrains.annotations.NotNull;

class SimpleColumnRenderer<T extends MemoryObject> extends ColoredTreeCellRenderer {
  @NotNull private final Function<MemoryObjectTreeNode<T>, String> myTextGetter;
  @NotNull private final Function<MemoryObjectTreeNode<T>, Icon> myIconGetter;
  @NotNull private final Function<MemoryObjectTreeNode<T>, SimpleTextAttributes> myTextAttributesGetter;
  private final int myAlignment;

  public SimpleColumnRenderer(@NotNull Function<MemoryObjectTreeNode<T>, String> textGetter,
                              @NotNull Function<MemoryObjectTreeNode<T>, Icon> iconGetter,
                              int alignment) {
    this(textGetter, iconGetter, value -> SimpleTextAttributes.REGULAR_ATTRIBUTES, alignment);
  }

  public SimpleColumnRenderer(@NotNull Function<MemoryObjectTreeNode<T>, String> textGetter,
                              @NotNull Function<MemoryObjectTreeNode<T>, Icon> iconGetter,
                              @NotNull Function<MemoryObjectTreeNode<T>, SimpleTextAttributes> textAttributesGetter,
                              int alignment) {
    myTextGetter = textGetter;
    myIconGetter = iconGetter;
    myTextAttributesGetter = textAttributesGetter;
    myAlignment = alignment;
  }

  @SuppressWarnings("unchecked")
  @Override
  public void customizeCellRenderer(@NotNull JTree tree,
                                    Object value,
                                    boolean selected,
                                    boolean expanded,
                                    boolean leaf,
                                    int row,
                                    boolean hasFocus) {
    if (value instanceof MemoryObjectTreeNode) {
      /*
        Note - the same text is added as a fragment tag so it can be exposed for us to validate
        the values we are setting to the fragments.
        See {@link com.intellij.ui.SimpleColoredComponent#getFragmentTag(int)} for details.
       */
      String text = myTextGetter.apply((MemoryObjectTreeNode<T>)value);
      append(text, myTextAttributesGetter.apply((MemoryObjectTreeNode<T>)value), text);
      setTextAlign(myAlignment);

      Icon icon = myIconGetter.apply((MemoryObjectTreeNode<T>)value);
      if (icon != null) {
        setIcon(icon);
      }
      else {
        // Only include cell insets if we don't have an icon. Otherwise, the padding appears between the text and icon instead of all
        // the way on the left of the cell.
        if (myAlignment == SwingConstants.LEFT) {
          setIpad(ProfilerLayout.TABLE_COLUMN_CELL_INSETS);
        }
        else {
          setIpad(ProfilerLayout.TABLE_COLUMN_RIGHT_ALIGNED_CELL_INSETS);
        }
      }
    }
  }

  /**
   * Make simple right-aligned column that displays a node's integer-property
   * if the node's data belongs to the subclass and its property satisfies the predicate.
   * The column displays "" otherwise.
   */
  public static <T extends MemoryObject> AttributeColumn<MemoryObject>
      makeIntColumn(String name,
                    Class<T> subclass,
                    ToLongFunction<T> prop,
                    LongPredicate pred,
                    LongFunction<String> formatter,
                    SortOrder order) {
    Function<MemoryObjectTreeNode<MemoryObject>, String> textGetter =
      makeConditionalTextGetter(subclass, prop, pred, formatter);
    return new AttributeColumn<>(name,
                                 () -> new SimpleColumnRenderer<>(textGetter, v -> null, SwingConstants.RIGHT),
                                 SwingConstants.RIGHT, DEFAULT_COLUMN_WIDTH,
                                 order, compareOn(subclass, prop));
  }

  public static AttributeColumn<MemoryObject> makeSizeColumn(String name, ToLongFunction<ValueObject> prop) {
    return makeIntColumn(name,
                         ValueObject.class, prop, s -> s != INVALID_VALUE,
                         NumberFormatter::formatInteger,
                         SortOrder.DESCENDING);
  }

  public static final int DEFAULT_COLUMN_WIDTH = 80;

  /**
   * Make a function that returns a text representation for a node's integer-property,
   * defaulting to the empty string if the node doesn't belong to the right subclass or
   * the property fails the predicate
   */
  public static <T extends MemoryObject> Function<MemoryObjectTreeNode<MemoryObject>, String>
      makeConditionalTextGetter(Class<T> subclass,
                                ToLongFunction<T> prop,
                                LongPredicate pred,
                                LongFunction<String> formatter) {
    return makeConditionalGetter(subclass, prop, pred, formatter, "");
  }

  /**
   * Make a function that computes a value based on a node's integer-property
   * if the node belongs to a sub-class and the property satisfies the predicate.
   * The function resorts to the fallback value otherwise.
   */
  public static <T extends MemoryObject, A> Function<MemoryObjectTreeNode<MemoryObject>, A>
      makeConditionalGetter(Class<T> subclass,
                            ToLongFunction<T> prop,
                            LongPredicate pred,
                            LongFunction<A> result,
                            A fallback) {
    return onSubclass(subclass,
                      t -> {
                        long v = prop.applyAsLong(t);
                        return pred.test(v) ? result.apply(v) : fallback;
                      },
                      s -> fallback);
  }

  /**
   * Apply the function to the value if it belongs to the given subclass,
   * or resort to the fallback function in the general case
   */
  public static <S extends MemoryObject, T extends S, A> Function<MemoryObjectTreeNode<S>, A>
      onSubclass(Class<T> c, Function<T, A> f, Function<S, A> fallback) {
    return o -> {
      S s = o.getAdapter();
      return c.isInstance(s) ? f.apply(c.cast(s)) : fallback.apply(s);
    };
  }

  public static <T extends MemoryObject> Comparator<MemoryObjectTreeNode<MemoryObject>>
      compareOn(Class<T> subclass, ToLongFunction<T> prop) {
    return Comparator.comparingLong(o -> prop.applyAsLong(subclass.cast(o.getAdapter())));
  }
}
