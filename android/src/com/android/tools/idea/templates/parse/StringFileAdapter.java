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
package com.android.tools.idea.templates.parse;

import com.android.utils.XmlUtils;
import com.intellij.openapi.util.io.FileUtil;

import javax.xml.bind.annotation.adapters.XmlAdapter;
import java.io.File;

/**
 * JAXB adapter that converts string values to Files.
 */
public final class StringFileAdapter extends XmlAdapter<String, File> {
  @Override
  public File unmarshal(String s) throws Exception {
    String unescapedString = XmlUtils.fromXmlAttributeValue(s);
    return new File(FileUtil.toSystemDependentName(unescapedString));
  }

  @Override
  public String marshal(File file) throws Exception {
    throw new UnsupportedOperationException("File -> String marshalling should not be needed");
  }
}
