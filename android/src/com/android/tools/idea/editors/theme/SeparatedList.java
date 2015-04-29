/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.theme;

import java.util.List;

/**
 * Class used to provide a view for several lists as if they're been concatenated with separator,
 * where separator is inserted only between non-empty portions of resulting view (i.e., there
 * wouldn't be two separators in a row and no separators at the beginning and end).
 */
public class SeparatedList {
  private final Object mySeparator;
  private final Group[] myLists;

  public SeparatedList(Object separator, Group... lists) {
    mySeparator = separator;
    myLists = lists;
  }

  public int size() {
    int result = 0;
    for (Group list : myLists) {
      if (list.size() == 0) {
        continue;
      }

      result += (result == 0 ? 0 : 1) + list.size();
    }

    return result;
  }

  public Object get(int index) {
    int offset = 0;

    for (Group list : myLists) {
      if (list.size() == 0) {
        continue;
      }

      if (index == offset && offset > 0) {
        return mySeparator;
      }

      if (offset > 0) {
        offset += 1;
      }

      if (index < offset + list.size()) {
        return list.get(index - offset);
      }

      offset += list.size();
    }

    throw new IndexOutOfBoundsException();
  }

  public static Group group(Object... objects) {
    return new Group(objects);
  }

  public static class Group {
    private final Object[] myContents;

    Group(final Object[] contents) {
      myContents = contents;
    }

    int size() {
      int result = 0;

      for (Object object : myContents) {
        if (object instanceof List) {
          result += ((List)object).size();
        } else {
          result += 1;
        }
      }

      return result;
    }

    Object get(int index) {
      int offset = 0;

      for (Object object : myContents) {
        if (object instanceof List) {
          List list = (List)object;
          if (index < offset + list.size()) {
            return list.get(index - offset);
          }

          offset += list.size();
        } else {
          if (index == offset) {
            return object;
          }

          offset += 1;
        }
      }

      throw new IndexOutOfBoundsException();
    }
  }
}
