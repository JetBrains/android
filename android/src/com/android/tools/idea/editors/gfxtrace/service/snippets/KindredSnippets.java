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
package com.android.tools.idea.editors.gfxtrace.service.snippets;

import com.android.tools.rpclib.binary.BinaryClass;
import com.android.tools.rpclib.binary.BinaryObject;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * Created by anton on 2/10/16.
 */
public abstract class KindredSnippets implements BinaryObject {
  public static KindredSnippets wrap(BinaryObject obj) {
    return (KindredSnippets)obj;
  }

  public BinaryObject unwrap() {
    return this;
  }

  /**
   * the pathway from the top-level to these snippets.
   * @return the pathway from top-level to these snippets.
   */
  public abstract Pathway getPath();

  /**
   * find the snippets in the metadata.
   * @param metadata arbitrary metadata.
   * @return metadata of type KindredSnippets.
   */
  public static KindredSnippets[] fromMetadata(BinaryObject[] metadata) {
    ArrayList<KindredSnippets> snippets = new ArrayList<KindredSnippets>();
    for (BinaryObject obj : metadata) {
      if (obj instanceof KindredSnippets) {
        snippets.add((KindredSnippets)obj);
      }
    }
    return snippets.toArray(new KindredSnippets[snippets.size()]);
  }
}

