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
package com.android.tools.idea.apk.viewer.arsc;

import com.android.tools.idea.apk.viewer.BinaryXmlParser;
import com.google.common.collect.ImmutableList;
import com.google.devrel.gmscore.tools.apk.arsc.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.table.AbstractTableModel;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ResourceTypeTableModel extends AbstractTableModel {
  private final StringPoolChunk myStringPool;
  private final PackageChunk myPackageChunk;
  private final TypeSpecChunk myTypeSpec;
  private final List<TypeChunk> myTypes;

  public ResourceTypeTableModel(@NotNull StringPoolChunk stringPool, @NotNull PackageChunk packageChunk, @NotNull TypeSpecChunk typeSpec) {
    myStringPool = stringPool;
    myPackageChunk = packageChunk;
    myTypeSpec = typeSpec;
    myTypes = ImmutableList.copyOf(packageChunk.getTypeChunks(typeSpec.getId()));
  }

  @Override
  public int getRowCount() {
    return myTypeSpec.getResourceCount();
  }

  @Override
  public int getColumnCount() {
    return myTypes.size() + 2;
  }

  @Override
  public Object getValueAt(int row, int col) {
    if (col == 0) { // resource id
      BinaryResourceIdentifier id = BinaryResourceIdentifier.create(myPackageChunk.getId(), myTypeSpec.getId(), row);
      return id.toString();
    }
    else if (col == 1) { // resource name
      String key = "unknown";
      for (TypeChunk type : myTypes) {
        TypeChunk.Entry entry = type.getEntries().get(row);
        if (entry != null) {
          key = entry.key();
          break;
        }
      }
      return key;
    }
    else {
      TypeChunk typeChunk = myTypes.get(col - 2);
      if (typeChunk.getEntries().containsKey(row)) {
        TypeChunk.Entry entry = typeChunk.getEntries().get(row);
        BinaryResourceValue value = entry.value();
        if (value != null) {
          return formatValue(value);
        }
        Map<Integer, BinaryResourceValue> values = entry.values();
        if (values != null) {
          return values.values().stream().map(this::formatValue).collect(Collectors.joining(", "));
        }
        return "?";
      }
      else {
        return "";
      }
    }
  }

  @NotNull
  private String formatValue(@NotNull BinaryResourceValue value) {
    if (value.type() == BinaryResourceValue.Type.STRING) {
      return myStringPool.getString(value.data());
    }
    return BinaryXmlParser.formatValue(value, myStringPool);
  }

  @Override
  public String getColumnName(int column) {
    if (column == 0) {
      return "ID";
    }
    else if (column == 1) {
      return "Name";
    }
    else {
      TypeChunk typeChunk = myTypes.get(column - 2);
      return typeChunk.getConfiguration().toString();
    }
  }
}
