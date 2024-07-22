/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.preview

/**
 * Contains lists of classes to preload when rendering previews in different scenarios, like for
 * example when using interactive preview.
 */
object PreviewPreloadClasses {
  @JvmStatic
  /** List of classes to preload when using interactive preview */
  // TODO(b/354679689): move tool specific dependencies to their corresponding modules and only
  //   keep common ones here.
  val INTERACTIVE_CLASSES_TO_PRELOAD: List<String> =
    listOf(
      // As of Compose alpha12
      // First touch event
      "android.view.MotionEvent",
      "androidx.compose.ui.input.pointer.PointerId",
      "androidx.compose.ui.input.pointer.MotionEventAdapterKt",
      "android.view.MotionEvent\$PointerCoords",
      "androidx.compose.ui.input.pointer.PointerType",
      "androidx.compose.ui.input.pointer.PointerInputEventData",
      "androidx.compose.ui.input.pointer.PointerInputEvent",
      "androidx.compose.ui.input.pointer.PointerInputChangeEventProducer\$PointerInputData",
      "androidx.compose.ui.input.pointer.PointerInputChange",
      "androidx.compose.ui.input.pointer.ConsumedData",
      "androidx.compose.ui.input.pointer.InternalPointerEvent",
      "androidx.compose.ui.input.pointer.PointerEventKt",
      "androidx.compose.ui.input.pointer.Node",
      "androidx.compose.ui.input.pointer.HitPathTracker\$CustomEventDispatcherImpl",
      "androidx.compose.ui.input.pointer.CustomEventDispatcher",
      "androidx.compose.ui.input.pointer.NodeParent\$removeDetachedPointerInputFilters$1",
      "androidx.compose.ui.input.pointer.NodeParent\$removeDetachedPointerInputFilters$2",
      "androidx.compose.ui.input.pointer.NodeParent\$removeDetachedPointerInputFilters$3",
      "androidx.compose.ui.input.pointer.PointerEventPass",
      "androidx.compose.ui.input.pointer.PointerEvent",
      "androidx.compose.ui.gesture.GestureUtilsKt",
      "androidx.compose.ui.input.pointer.PointerInputEventProcessorKt",
      "androidx.compose.ui.input.pointer.ProcessResult",
      // First callback execution after touch event
      "androidx.compose.runtime.snapshots.SnapshotStateObserver\$applyObserver$1$2",
      "androidx.compose.ui.platform.AndroidComposeViewKt\$sam\$java_lang_Runnable$0",
      "androidx.compose.runtime.Invalidation",
      "androidx.compose.runtime.InvalidationResult",
      "androidx.compose.runtime.Recomposer\$runRecomposeAndApplyChanges$2$4",
      "androidx.compose.runtime.PausableMonotonicFrameClock\$withFrameNanos$1",
      "androidx.compose.ui.platform.AndroidUiFrameClock\$withFrameNanos$2\$callback$1",
      "androidx.compose.ui.platform.AndroidUiFrameClock\$withFrameNanos$2$1",
      "kotlinx.coroutines.InvokeOnCancel",
      "androidx.compose.runtime.ComposerImpl\$updateValue$2",
      "androidx.compose.runtime.ComposerImpl\$realizeOperationLocation$2",
      "androidx.compose.runtime.ComposerImpl\$realizeDowns$1",
      "androidx.compose.runtime.ComposerImpl\$realizeUps$1",
      "androidx.compose.ui.platform.JvmActualsKt",
      "kotlinx.coroutines.JobCancellationException",
      "kotlinx.coroutines.CopyableThrowable",
      "kotlinx.coroutines.DebugStringsKt",
      // Animation
      "androidx.compose.material.ElevationDefaults",
      "androidx.compose.animation.core.AnimationKt",
      "androidx.compose.animation.core.TargetBasedAnimation",
      "androidx.compose.animation.core.Animation",
      "androidx.compose.animation.core.VectorizedTweenSpec",
      "androidx.compose.animation.core.VectorizedDurationBasedAnimationSpec",
      "androidx.compose.animation.core.VectorizedFiniteAnimationSpec",
      "androidx.compose.animation.core.VectorizedAnimationSpec",
      "androidx.compose.animation.core.VectorizedFloatAnimationSpec",
      "androidx.compose.animation.core.FloatTweenSpec",
      "androidx.compose.animation.core.FloatAnimationSpec",
      "androidx.compose.animation.core.VectorizedFloatAnimationSpec$1",
      "androidx.compose.animation.core.Animations",
      "androidx.compose.animation.core.VectorizedDurationBasedAnimationSpec\$DefaultImpls",
      "androidx.compose.animation.core.VectorizedFiniteAnimationSpec\$DefaultImpls",
      "androidx.compose.animation.core.VectorizedAnimationSpec\$DefaultImpls",
      "androidx.compose.animation.core.Animatable\$runAnimation$2",
      "kotlin.jvm.internal.Ref\$BooleanRef",
      "androidx.compose.animation.core.Animatable\$runAnimation$2$1",
      "androidx.compose.animation.core.SuspendAnimationKt",
      "androidx.compose.animation.core.SuspendAnimationKt\$animate$4",
      "androidx.compose.animation.core.Animation\$DefaultImpls",
      "androidx.compose.animation.core.SuspendAnimationKt\$animate\$startTimeNanosSpecified$1",
      "androidx.compose.runtime.MonotonicFrameClockKt",
      "androidx.compose.runtime.BroadcastFrameClock\$FrameAwaiter",
      "androidx.compose.runtime.BroadcastFrameClock\$withFrameNanos$2$1",
      "androidx.compose.material.ripple.RippleAnimation",
      "androidx.compose.material.ripple.RippleIndicationInstance\$addRipple\$ripple$1",
      "androidx.compose.animation.core.AnimationVector2D",
      "androidx.compose.material.ripple.RippleAnimation$1",
      "kotlinx.collections.immutable.internal.ListImplementation",
      "androidx.compose.ui.graphics.ClipOp",
      "androidx.compose.ui.graphics.AndroidCanvas\$WhenMappings",
      "androidx.compose.ui.graphics.PointMode",
      "android.graphics.Region\$Op",
      "androidx.compose.ui.input.pointer.NodeParent\$removePointerId$2",
      "androidx.compose.animation.core.AnimationScope",
      "androidx.compose.animation.core.SuspendAnimationKt\$animate$6",
      "androidx.compose.animation.core.SuspendAnimationKt\$animate$7",
      "androidx.compose.material.ripple.RippleAnimation\$fadeIn$2",
      "androidx.compose.material.ripple.RippleAnimation\$fadeIn$2$1",
      "androidx.compose.material.ripple.RippleAnimation\$fadeIn$2$2",
      "androidx.compose.material.ripple.RippleAnimation\$fadeIn$2$3",
      "kotlinx.coroutines.JobSupport\$ChildCompletion",
      "androidx.compose.animation.core.AnimationSpecKt",
      "kotlinx.coroutines.internal.StackTraceRecoveryKt",
      "kotlin.coroutines.jvm.internal.BaseContinuationImpl",
      "kotlinx.coroutines.internal.StackTraceRecoveryKt",
      "kotlinx.coroutines.internal.ExceptionsConstuctorKt",
      "kotlin.jvm.JvmClassMappingKt",
      "kotlin.jvm.internal.ClassReference",
      "kotlin.jvm.internal.ClassReference\$Companion",
      "kotlin.jvm.functions.Function12",
      "kotlin.jvm.functions.Function22",
      "java.util.concurrent.locks.ReentrantReadWriteLock",
      "java.util.ArrayDeque",
      "kotlin.coroutines.jvm.internal.DebugMetadataKt",
      "kotlin.coroutines.jvm.internal.DebugMetadata",
      "java.lang.annotation.Annotation",
      "kotlin.annotation.Target",
      "java.lang.annotation.Retention",
      "java.lang.annotation.RetentionPolicy",
      "java.lang.annotation.Target",
      "kotlin.Metadata",
      "java.lang.reflect.Proxy",
      "java.lang.reflect.UndeclaredThrowableException",
      "java.lang.NoSuchMethodError",
      "java.lang.NoClassDefFoundError",
      "java.lang.reflect.InvocationHandler",
      "kotlin.annotation.Retention",
      "kotlin.coroutines.jvm.internal.ModuleNameRetriever",
      "kotlin.coroutines.jvm.internal.ModuleNameRetriever\$Cache",
      "java.lang.ClassLoader",
      "java.lang.Module",
      "java.lang.module.ModuleDescriptor",
      "kotlinx.coroutines.TimeoutCancellationException",
      "java.util.IdentityHashMap",
      "androidx.compose.animation.core.AnimationEndReason",
      "androidx.compose.animation.core.AnimationResult",
    )
}
