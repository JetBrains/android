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
package com.android.tools.idea.debug;

import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.CLASS_RESOURCES;

/**
 * {@link DynamicResourceIdResolver} can resolve a resource id to its name by invoking Resources.getResourceName()
 * on all the Resources objects inside the target VM.
 */
public class DynamicResourceIdResolver implements ResourceIdResolver {
  private static final Logger LOG = Logger.getInstance(DynamicResourceIdResolver.class);

  private final EvaluationContext myContext;
  private final ResourceIdResolver myDelegate;

  public DynamicResourceIdResolver(@NotNull EvaluationContext context, @NotNull ResourceIdResolver delegate) {
    myContext = context;
    myDelegate = delegate;
  }

  @Nullable
  @Override
  public String getAndroidResourceName(int resId) {
    String id = myDelegate.getAndroidResourceName(resId);
    if (id != null) {
      return id;
    }

    DebugProcess debugProcess = myContext.getDebugProcess();
    VirtualMachineProxyImpl vmProxy = (VirtualMachineProxyImpl)debugProcess.getVirtualMachineProxy();
    List<ReferenceType> classes = vmProxy.classesByName(CLASS_RESOURCES);
    if (classes.isEmpty()) {
      LOG.warn(CLASS_RESOURCES + " class not loaded?");
      return null;
    }

    if (classes.size() != 1) {
      LOG.warn("Expected a single Resource class loaded, but found " + classes.size());
    }

    ReferenceType resourcesClassType = classes.get(0);
    Method getResourceNameMethod = DebuggerUtils.findMethod(resourcesClassType, "getResourceName", "(I)Ljava/lang/String;");
    if (getResourceNameMethod == null) {
      LOG.warn("Unable to locate getResourceName(int id) in class " + resourcesClassType.name());
      return null;
    }

    List<ObjectReference> instances = resourcesClassType.instances(10);
    if (instances.isEmpty()) {
      LOG.warn("No instances of Resource class found");
      return null;
    }

    List args = Collections.singletonList(DebuggerUtilsEx.createValue(vmProxy, "int", resId));
    for (ObjectReference ref : instances) {
      try {
        Value value = debugProcess.invokeMethod(myContext, ref, getResourceNameMethod, args);
        if (value instanceof StringReference) {
          StringReference nameRef = (StringReference)value;
          return nameRef.value();
        }
      }
      catch (EvaluateException e) {
        LOG.warn("Unexpected error while invoking Resources.getResourceName()", e);
        // continue and try this on other object references
      }
    }

    return null;
  }
}
