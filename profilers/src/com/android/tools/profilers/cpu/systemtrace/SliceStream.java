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
package com.android.tools.profilers.cpu.systemtrace;

import com.android.tools.adtui.model.Range;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

/**
 * This class assist with doing a depth first search enumeration of SliceGroups.
 */
public final class SliceStream {

  public enum EnumerationResult {
    /**
     * Continue enumerating depth first.
     */
    CONTINUE,
    /**
     * Stop enumerating.
     */
    TERMINATE,
    /**
     * Skip enumerating children of this slice.
     */
    SKIP_CHILDREN,
  }
  @NotNull
  private final List<TraceEventModel> mySlices;
  private Pattern myPattern = Pattern.compile(".*");
  private Range myRange = new Range(Double.MIN_VALUE, Double.MAX_VALUE);

  /**
   * Constructs a default stream of slices.
   * @param slices list of slices to perform stream events on.
   */
  public SliceStream(@NotNull List<TraceEventModel> slices) {
    mySlices = slices;
  }

  /**
   * @param pattern to match on slices default is .*
   */
  public SliceStream matchPattern(@NotNull Pattern pattern) {
    myPattern = pattern;
    return this;
  }

  /**
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
  public void enumerate(@NotNull Function<TraceEventModel, EnumerationResult> action) {
    forEachMatchingSlice(mySlices, myPattern, myRange, action);
  }

  /**
   * Returns the first element in the slice list matching any filters set. If no element matches any filters null is returned.
   */
  public TraceEventModel findFirst() {
    TraceEventModel[] events = new TraceEventModel[1];
    forEachMatchingSlice(mySlices, myPattern, myRange, (sliceGroup) -> {
      events[0] = sliceGroup;
      return EnumerationResult.TERMINATE;
    });
    return events[0];
  }

  /**
   * A helper function for enumerating slices by a filter. The function passed in receives each slice with a name matching the filter,
   * and is expected to return an {@link EnumerationResult} for the enumeration to continue, skip children, stop the enumeration.
   *`
   * @param eventGroups the group of slices to perform a depth first search on.
   * @param pattern     the regex filter restrict the action callback to.
   * @param range       the range to restrict the search to.
   * @param action      the action to perform on each slice matching the name criteria.
   */
  private static EnumerationResult forEachMatchingSlice(@NotNull List<TraceEventModel> eventGroups,
                                                        @NotNull Pattern pattern,
                                                        @NotNull Range range,
                                                        Function<TraceEventModel, EnumerationResult> action) {
    for (TraceEventModel event : eventGroups) {
      if (event.getStartTimestampUs() <= range.getMax() && event.getEndTimestampUs() >= range.getMin()) {
        boolean skipChildren = false;
        if (pattern.matcher(event.getName()).matches()) {
          EnumerationResult continueResult = action.apply(event);
          if (continueResult == EnumerationResult.TERMINATE) {
            return continueResult;
          }
          skipChildren = continueResult == EnumerationResult.SKIP_CHILDREN;
        }
        if (!skipChildren) {
          EnumerationResult result = forEachMatchingSlice(event.getChildrenEvents(), pattern, range, action);
          if (result == EnumerationResult.TERMINATE) {
            return result;
          }
        }
      }
    }
    return EnumerationResult.CONTINUE;
  }
}
