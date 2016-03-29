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

import com.android.tools.idea.editors.gfxtrace.service.atom.Atom;
import com.android.tools.idea.editors.gfxtrace.service.atom.DynamicAtom;
import com.android.tools.idea.editors.gfxtrace.service.path.Path;
import com.android.tools.rpclib.binary.BinaryObject;
import com.android.tools.rpclib.schema.Dynamic;
import com.android.tools.rpclib.schema.Field;
import sun.reflect.FieldInfo;

import java.util.ArrayList;
import java.util.Map;

/**
 * SnippetObject provides a way to wrap an object in the gfx data
 * model and associate it with the snippet path and snippets.
 *
 * Created by anton on 2/16/16.
 */
public class SnippetObject {
  private final Object myObject;  // the underlying object
  private final Pathway myPath;   // the pathway for this object.
  private final KindredSnippets[] mySnippets;  // the snippets at the root.

  /**
   * Construct a snippet object from its sub-components.
   * Note the snippets for an individual object are only computed when requested.
   *
   * @param obj the underlying object.
   * @param path the pathway for this object.
   * @param snippets the snippets at the root.
   */
  public SnippetObject(Object obj, Pathway path, KindredSnippets[] snippets) {
    myObject = obj;
    myPath = path;
    mySnippets = snippets;
  }

  public String toString() {
    return myObject == null ? "null" : myObject.toString();
  }

  public Pathway getPath() {
    return myPath;
  }

  public Object getObject() {
    return myObject;
  }

  /**
   * Verify if this object has no interesting snippets.
   * @return true if this object has no interesting snippets.
   */
  public boolean isEmpty() {
    if (mySnippets.length == 0) {
      return true;
    }
    for (KindredSnippets snip : mySnippets) {
      Pathway path = snip.getPath();
      if (myPath.isPrefix(path)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Compute the snippets for this object.
   * @return an array containing the snippets for this object.
   */
  public KindredSnippets[] getSnippets() {
    ArrayList<KindredSnippets> snippets = null;  // avoid allocation if not required.
    for (KindredSnippets snip : mySnippets) {
      Pathway path = snip.getPath();
      if (path.equals(myPath)) {
        snippets = KindredSnippets.append(snippets, snip);
      }
    }
    return KindredSnippets.toArray(snippets);
  }

  /**
   * Determine if this is the root object (aka the global state object).
   * @return true if this is the root object.
   */
  public boolean isRoot() {
    return myPath == null && mySnippets != null;
  }

  /**
   * Determine if this is a symbol object. Symbol objects are used so that
   * entity field names and map keys can share the same tree structure.
   * @return true if this is a symbol object.
   */
  public boolean isSymbol() {
    return myPath == null && mySnippets == null && myObject != null && myObject.getClass() == String.class;
  }

  /**
   * Determine if this is a null pointer.
   * @return true if this is a null pointer.
   */
  public boolean isNull() {
    return myObject == null;
  }

  /**
   * Determine if this object is an atom.
   * @return true if this is an atom.
   */
  public boolean isAtom() {
    return myObject instanceof Atom;
  }

  /**
   * Determine if this object is a primitive value
   * @return true if this is a primitive value.
   */
  public boolean isPrimitive() {
    return !isBinaryObject() && !isCollection() && !isSymbol() && !isNull();
  }

  /**
   * Determine if this object is a binary object.
   * @return true if this is a binary object.
   */
  public boolean isBinaryObject() {
    return myObject instanceof BinaryObject;
  }

  /**
   * Determine if this is a collection.
   * @return true if this a collection.
   */
  public boolean isCollection() {
    return myObject instanceof Map || myObject instanceof Object[] || myObject instanceof byte[];
  }

  private static Object longify(Object value) {
    // Behavior inherited from StateController.
    // Turn integers into longs, so they equal longs from paths.
    return (value instanceof Integer) ? ((Integer)value).longValue() : value;
  }

  /**
   * Build a symbol object. Symbol objects are used so that entity
   * field names and map keys can share the same tree structure.
   * Note the symbol themselves do not have snippets.
   * @return true if this is a symbol object.
   */
  public static SnippetObject symbol(Object symbol) {
    return new SnippetObject(longify(symbol), null, null);
  }

  /**
   * Build a key from a map entry (this object is the map).
   * @param e the map entry containing the key.
   * @return a new snippet object for the key.
   */
  public SnippetObject key(Map.Entry<Object, Object> e) {
    return new SnippetObject(longify(e.getKey()), myPath.key(), mySnippets);
  }

  /**
   * Build an element from a map entry (this object is the map).
   * @param e the map entry containing the value.
   * @return a new snippet object for the element.
   */
  public SnippetObject elem(Map.Entry<Object, Object> e) {
    return new SnippetObject(e.getValue(), myPath.elem(), mySnippets);
  }

  /**
   * Build an element from an array entry (this object is the array).
   * @param object the array entry.
   * @return a new snippet object for the element.
   */
  public SnippetObject elem(Object object) {
    return new SnippetObject(object, myPath.elem(), mySnippets);
  }

  /**
   * Build a field of a dynamic entity (this object is the entity).
   * @param obj the entity as Dynamic
   * @param fieldIndex the index of the field of the entity.
   * @return a new snippet object for the field value.
   */
  public SnippetObject field(Dynamic obj, int fieldIndex) {
    final Field info = obj.getFieldInfo(fieldIndex);
    final String name = info.getDeclared();
    // In the UI globals are treated like fields of a magic state entity.
    Pathway path = myPath == null ? Pathway.global(name) : myPath.field(name);
    return new SnippetObject(obj.getFieldValue(fieldIndex), path, mySnippets);
  }

  /**
   * lower case the first character of a string.
   * @param str the string to transform.
   * @return a new string with a lower case first character.
   */
  private static String lowerCaseFirstCharacter(String str) {
    if (str.length() == 0) {
      return str;
    }
    return Character.toLowerCase(str.charAt(0)) + str.substring(1);
  }

  /**
   * Build a new atom parameter object (this object is the atom).
   * @param atom the atom as DynamicAtom
   * @param paramIndex the index of the parameter in the atom.
   * @return a new snippet object for the parameter value.
   */
  public static SnippetObject param(DynamicAtom atom, int paramIndex) {
    final KindredSnippets[] snippets = KindredSnippets.fromMetadata(atom.unwrap().klass().entity().getMetadata());
    final Field info = atom.getFieldInfo(paramIndex);
    // The parameter name in the schema had the first letter capitalised (presumably to make it public in Go).
    final String name = lowerCaseFirstCharacter(info.getDeclared());
    return new SnippetObject(atom.getFieldValue(paramIndex), Pathway.param(atom.getName(), name), snippets);
  }

  /**
   * Build a root object for the global state.
   * @param obj the Dynamic object containing the global state.
   * @param snippets the snippets for the state object.
   * @return a new snippet object for root object.
   */
  public static SnippetObject root(Dynamic obj, KindredSnippets[] snippets) {
    return new SnippetObject(obj, null, snippets);
  }

  // Note only the underlying object is considered in equals().
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null) {
      return false;
    }
    if (o == null || getClass() != o.getClass()) return false;

    SnippetObject that = (SnippetObject)o;

    if (myObject != null ? !myObject.equals(that.myObject) : that.myObject != null) return false;

    return true;
  }

  //Note only the underlying object is considered in hashCode()
  @Override
  public int hashCode() {
    return myObject != null ? myObject.hashCode() : 0;
  }
}
