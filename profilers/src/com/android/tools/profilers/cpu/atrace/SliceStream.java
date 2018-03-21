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
package com.android.tools.profilers.cpu.atrace;

import com.android.tools.adtui.model.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import trebuchet.model.base.SliceGroup;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * This class assist with doing a depth first search enumeration of SliceGroups.
 */
public final class SliceStream {
  @NotNull
  private final List<SliceGroup> mySlices;
  private Pattern myPattern = Pattern.compile(".*");
  private Range myRange = new Range(Double.MIN_VALUE, Double.MAX_VALUE);

  /**
   * Constructs a default stream of slices.
   * @param slices list of slices to perform stream events on.
   */
  public SliceStream(@NotNull List<SliceGroup> slices) {
    mySlices = slices;
  }

  /**
   * @param pattern to match on slices default is .*
   */
  public SliceStream matchPattern(@NotNull Pattern pattern) {
    myPattern = pattern;
    return this;
  }

  /*
   * @param name to match on slices default is *
   */
  public SliceStream matchName(@NotNull String name) {
    return matchPattern(Pattern.compile("^" + Pattern.quote(name) + ""));
  }

  /**
   * @param range used to filter slices. The range is inclusive of min and max values.
   *              Default is Double.MIN to Double.MAX
   */
  public SliceStream overlapsRange(Range range) {
    myRange = range;
    return this;
  }

  /**
   * Enumerates each element in the slice list. For each element the action function is called. Enumeration is stopped when the tree
   * is fully exhausted or false is returned from the action.
   * @param action callback action to perform on each element.
   */
  public void enumerate(@NotNull Function<SliceGroup, Boolean> action) {
    forEachMatchingSlice(mySlices, myPattern, myRange, action);
  }

  /**
   * Returns the first element in the slice list matching any filters set. If no element matches any filters null is returned.
   */
  public SliceGroup findFirst() {
    SliceGroup[] slices = new SliceGroup[1];
    forEachMatchingSlice(mySlices, myPattern, myRange, (sliceGroup) -> {
      slices[0] = sliceGroup;
      return false;
    });
    return slices[0];
  }

  /**
   * A helper function for enumerating slices by a filter. The function passed in receives each slice with a name matching the filter,
   * and is expected to return true for the enumeration to continue or false to stop the enumeration.
   *
   * @param sliceGroups the group of slices to perform a depth first search on.
   * @param pattern     the regex filter restrict the action callback to.
   * @param range       the range to restrict the search to.
   * @param action      the action to perform on each slice matching the name criteria.
   */
  private static boolean forEachMatchingSlice(@NotNull List<SliceGroup> sliceGroups,
                                             @NotNull Pattern pattern,
                                             @NotNull Range range,
                                             Function<SliceGroup, Boolean> action) {
    for (SliceGroup slice : sliceGroups) {
      if (slice.getStartTime() <= range.getMax() && slice.getEndTime() >= range.getMin()) {
        if (pattern.matcher(slice.getName()).matches()) {
          if (!action.apply(slice)) {
            return false;
          }
        }
        boolean shouldContinue = forEachMatchingSlice(slice.getChildren(), pattern, range, action);
        if (!shouldContinue) {
          return shouldContinue;
        }
      }
    }
    return true;
  }
}
