/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.heap;

import static com.android.tools.idea.diagnostics.heap.HeapTraverseUtil.isArrayOfPrimitives;
import static com.android.tools.idea.diagnostics.heap.HeapTraverseUtil.isInitialized;

import com.android.tools.idea.flags.StudioFlags;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HeapTraverseChildProcessor {

  private static final Set<String> REFERENCE_CLASS_FIELDS_TO_IGNORE = Set.of("referent", "discovered", "next");

  @NotNull
  private final Object myDisposerTree;
  private final boolean myShouldUseDisposerTreeReferences;

  public HeapTraverseChildProcessor() {
    myDisposerTree = Disposer.getTree();
    myShouldUseDisposerTreeReferences = StudioFlags.USE_DISPOSER_TREE_REFERENCES.get();
  }

  /**
   * Iterates over objects referred from the passed object and calls the passed consumer for them.
   *
   * @param obj        processing object
   * @param consumer   processor for references: gets a referred object and the type of the reference as {@link HeapTraverseNode.RefWeight}
   * @param fieldCache cache that stores fields declared for the given class.
   */
  void processChildObjects(@Nullable final Object obj,
                           @NotNull final BiConsumer<Object, HeapTraverseNode.RefWeight> consumer,
                           @NotNull final FieldCache fieldCache) {
    if (obj == null) {
      return;
    }
    if (obj == myDisposerTree) {
      /*
        We don't traverse the subtree of disposer tree to prevent the situation when DisposerTree (or the component that owns it) will be
        resolved as an owner of the registered Disposables. DisposerTree doesn't actually "own" them, just manages their lifecycles.
       */
      return;
    }
    Class<?> nodeClass = obj.getClass();
    for (Field field : fieldCache.getInstanceFields(nodeClass)) {
      // do not follow weak/soft refs
      if (obj instanceof Reference && REFERENCE_CLASS_FIELDS_TO_IGNORE.contains(field.getName())) {
        continue;
      }

      Object value;
      try {
        value = field.get(obj);
      }
      catch (IllegalArgumentException | IllegalAccessException e) {
        throw new RuntimeException(e);
      }
      consumer.accept(value, HeapTraverseNode.RefWeight.INSTANCE_FIELD);
    }

    // JVMTI_HEAP_REFERENCE_ARRAY_ELEMENT
    if (nodeClass.isArray() && !isArrayOfPrimitives(nodeClass)) {
      for (Object value : (Object[])obj) {
        consumer.accept(value, HeapTraverseNode.RefWeight.ARRAY_ELEMENT);
      }
    }
    // We need to check that class is initialized and only in this case traverse child elements of the class. Class object may be reachable
    // by traversal but not yet initialized if it's stored somewhere in form of class object instance but non of the following events
    // occurred with the corresponding class:
    // 1) an instance of the class is created,
    // 2) a static method of the class is invoked,
    // 3) a static field of the class is assigned,
    // 4) a non-constant static field is used;
    if (obj instanceof Class && isInitialized((Class<?>)obj)) {
      // JVMTI_HEAP_REFERENCE_STATIC_FIELD
      for (Field field : fieldCache.getStaticFields((Class<?>)obj)) {
        try {
          Object value = field.get(null);
          consumer.accept(value, HeapTraverseNode.RefWeight.STATIC_FIELD);
        }
        catch (IllegalAccessException ignored) {
        }
      }
    }

    // Check is the object implements Disposable and is registered in a Disposer tree. In that case, we consider that the disposable
    // object refers to the children from Disposer tree.
    if (myShouldUseDisposerTreeReferences && obj instanceof Disposable) {
      Object objToNodeMap = getFieldValue(myDisposerTree, "myObject2NodeMap");
      if (!(objToNodeMap instanceof Map)) {
        return;
      }
      Object disposableTreeNode = ((Map<?, ?>)objToNodeMap).get(obj);
      if (disposableTreeNode == null) {
        return;
      }
      Object disposableTreeNodeChildren = getFieldValue(disposableTreeNode, "myChildren");
      if (!(disposableTreeNodeChildren instanceof List)) {
        return;
      }
      for (Object child : (List<?>)disposableTreeNodeChildren) {
        Object currDisposable = getFieldValue(child, "myObject");
        if (!(currDisposable instanceof Disposable)) {
          continue;
        }
        consumer.accept(currDisposable, HeapTraverseNode.RefWeight.DISPOSER_TREE_REFERENCE);
      }
    }
  }

  @Nullable
  private static Object getFieldValue(@NotNull Object object, @NotNull String fieldName) {
    try {
      Field field = object.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      return field.get(object);
    }
    catch (ReflectiveOperationException e) {
      throw new Error(e); // Should not happen unless there is a bug in this class.
    }
  }
}
