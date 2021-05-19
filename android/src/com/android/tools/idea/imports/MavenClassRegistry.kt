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
package com.android.tools.idea.imports

/** Lookup from key class names to well known maven.google.com artifacts */
object MavenClassRegistry {
  /**
   * Given a simple class name, return the likely groupid:artifactid for the maven.google.com
   * artifact containing that class.
   *
   * This implementation only returns results for a few important classes, not an exhaustive search
   * from a full index.
   *
   * NOTE: This library should be returning the pre-AndroidX library names when applicable; the caller is
   * responsible for mapping this to the corresponding AndroidX library artifact if the project is
   * using it. (The reason for this is practical: we have an artifact map to map forwards, not backwards.)
   */
  fun findArtifact(className: String): String? {
    return findArtifactData(className)?.artifact
  }

  fun findImport(className: String): String? {
    val data = findArtifactData(className) ?: return null
    return "${data.import}.$className"
  }

  private data class Library(val artifact: String, val import: String)

  private fun findArtifactData(className: String): Library? {
    val index = className.lastIndexOf('.')
    val symbol = if (index == -1) {
      className
    } else {
      className.substring(index + 1)
    }

    return when (symbol) {
      "Nullable",
      "NonNull" ->
        // In AndroidX this is androidx.annotation
        Library(artifact = "com.android.support:support-annotations", import = "android.support.annotation")

      "RecyclerView",
      "LinearLayoutManager" ->
        // In AndroidX this is androidx.recyclerview.widget
        Library(artifact = "com.android.support:recyclerview-v7", import = "android.support.v7.widget")
      "DiffUtil" ->
        // In AndroidX this is androidx.recyclerview.widget
        Library(artifact = "com.android.support:recyclerview-v7", import = "android.support.v7.util")

      "NavHost",
      "NavController" ->
        // Not available prior to AndroidX
        Library(artifact = "android.arch.navigation:navigation-runtime", import = "androidx.navigation")

      "NavHostFragment" ->
        // Not available prior to AndroidX
        Library(artifact = "android.arch.navigation:navigation-fragment", import = "androidx.navigation.fragment")

      "TextClassifier" ->
        // Not available prior to AndroidX
        Library(artifact = "com.android.support:textclassifier", import = "androidx.textclassifier")

      "PrintHelper" ->
        // In AndroidX this is androidx.print
        Library(artifact = "com.android.support:print", import = "android.support.v4.print")

      "CoordinatorLayout" ->
        // In AndroidX this is androidx.coordinatorlayout.widget
        Library(artifact = "com.android.support:coordinatorlayout", import = "android.support.design.widget")

      "MediaPlayer" ->
        // Not available prior to AndroidX
        Library(artifact = "com.android.support:media2", import = "androidx.media2.player")

      "ExoPlayer",
      "ExoPlayerFactory" ->
        Library(artifact = "com.android.support:media2-exoplayer", import = "com.google.android.exoplayer2")

      "Loader",
      "CursorLoader",
      "AsyncTaskLoader" ->
        // In AndroidX this is androidx.loader.content
        Library(artifact = "com.android.support:loader", import = "android.support.v4.content")

      "DrawerLayout" ->
        // In AndroidX this is androidx.drawerlayout.widget
        Library(artifact = "com.android.support:drawerlayout", import = "android.support.v4.widget")

      "SupportSQLiteQuery",
      "SupportSQLiteQueryBuilder",
      "SupportSQLiteOpenHelper",
      "SupportSQLiteDatabase" ->
        // In AndroidX this is androidx.sqlite.db
        Library(artifact = "android.arch.persistence:db", import = "android.arch.persistence.db")

      "Palette" ->
        // In AndroidX this is androidx.palette.graphics
        Library(artifact = "com.android.support:palette-v7", import = "android.support.v7.graphics")

      "WebViewCompat" ->
        // Not available prior to AndroidX
        Library(artifact = "com.android.support:webkit", import = "androidx.webkit")

      "Transition" ->
        // In AndroidX this is androidx.transition
        Library(artifact = "com.android.support:transition", import = "android.support.transition")

      "VersionedParcel",
      "VersionedParcelable" ->
        // Not available prior to AndroidX
        Library(artifact = "com.android.support:versionedparcelable", import = "androidx.versionedparcelable")

      "ViewPager" ->
        // In AndroidX this is androidx.viewpager.widget
        Library(artifact = "com.android.support:viewpager", import = "android.support.v4.view")

      "ViewPager2" ->
        // Not available prior to AndroidX
        Library(artifact = "androidx.viewpager2:viewpager2", import = "androidx.viewpager2.widget")

      "SwipeRefreshLayout" ->
        // In AndroidX this is androidx.swiperefreshlayout.widget
        Library(artifact = "com.android.support:swiperefreshlayout", import = "android.support.v4.widget")

      "MediaRouter" ->
        // In AndroidX this is "androidx.mediarouter.media
        Library(artifact = "com.android.support:mediarouter-v7", import = "android.support.v7.media")

      "MediaRouteButton",
      "MediaRouteActionProvider" ->
        // In AndroidX this is androidx.mediarouter.app
        Library(artifact = "com.android.support:mediarouter-v7", import = "android.support.v7.app")

      "FingerprintDialogFragment",
      "BiometricPrompt",
      "BiometricFragment" ->
        // Not available prior to AndroidX
        Library(artifact = "androidx.biometric:biometric", import = "androidx.biometric")

      "HeifWriter",
      "HeifEncoder" ->
        // Not available prior to AndroidX
        Library(artifact = "com.android.support:heifwriter", import = "androidx.heifwriter")

      "ExifInterface" ->
        // In AndroidX this is androidx.exifinterface.media
        Library(artifact = "com.android.support:exifinterface", import = "android.support.media")

      "PagedList",
      "PagedListBuilder",
      "PagedListAdapter" ->
        // In AndroidX this is androidx.paging
        Library(artifact = "android.arch.paging:runtime", import = "android.arch.paging")

      "VectorDrawableCompat" ->
        // In AndroidX this is androidx.vectordrawable.graphics.drawable
        Library(artifact = "com.android.support:support-vector-drawable", import = "android.support.graphics.drawable")

      "PathInterpolatorCompat" ->
        // In AndroidX this is androidx.core.view.animation
        Library(artifact = "com.android.support:support-vector-drawable", import = "android.support.v4.view.animation")

      "AnimatedVectorDrawableCompat" ->
        // In AndroidX this is androidx.vectordrawable.graphics.drawable
        Library(artifact = "com.android.support:animated-vector-drawable", import = "android.support.graphics.drawable")

      "ItemKeyProvider",
      "ItemDetailsLookup",
      "MutableSelection",
      "Selection",
      "SelectionTracker",
      "StorageStrategy" ->
        // Not available prior to AndroidX
        Library(artifact = "com.android.support:recyclerview-selection", import = "androidx.recyclerview.selection")

      "LiveData",
      "MutableLiveData",
      "Observer",
      "ComputableLiveData" ->
        // In AndroidX this is androidx.lifecycle
        Library(artifact = "android.arch.lifecycle:livedata", import = "android.arch.lifecycle")

      "ViewModelProviders" ->
        // In AndroidX this is androidx.lifecycle
        Library(artifact = "android.arch.lifecycle:extensions", import = "android.arch.lifecycle")

      "ViewModel",
      "ViewModelProvider" ->
        // In AndroidX this is androidx.lifecycle
        Library(artifact = "android.arch.lifecycle:viewmodel", import = "android.arch.lifecycle")

      "Lifecycle" ->
        // In AndroidX this is androidx.lifecycle
        Library(artifact = "android.arch.lifecycle:common", import = "android.arch.lifecycle")

      "ProcessLifecycleOwner" ->
        // In AndroidX this is androidx.lifecycle
        Library(artifact = "androidx.lifecycle:lifecycle-process", import = "android.arch.lifecycle")

      "ConstraintLayout" ->
        // In AndroidX this is androidx.constraintlayout.widget
        Library(artifact = "com.android.support.constraint:constraint-layout", import = "android.support.constraint")

      "MotionLayout" ->
        // Not available prior to AndroidX
        Library(artifact = "com.android.support.constraint:constraint-layout", import = "androidx.constraintlayout.motion.widget.MotionLayout")

      "LocalBroadcastManager" ->
        // In AndroidX this is androidx.localbroadcastmanager.content
        Library(artifact = "com.android.support:localbroadcastmanager", import = "android.support.v4.content")

      "ContentRecommendation" ->
        // In AndroidX this is androidx.recommendation.app
        Library(artifact = "com.android.support:recommendation", import = "android.support.app.recommendation")

      "Preference",
      "ListPreference",
      "PreferenceGroup",
      "PreferenceGroupAdapter",
      "PreferenceScreen",
      "PreferenceViewHolder" ->
        // In AndroidX this is androidx.preference
        Library(artifact = "com.android.support:preference-v7", import = "android.support.v14.preference")

      "AppCompatActivity" ->
        // In AndroidX this is androidx.appcompat.app
        Library(artifact = "com.android.support:appcompat-v7", import = "android.support.v7.app")

      "Toolbar" ->
        // In AndroidX this is androidx.appcompat.widget
        Library(artifact = "com.android.support:appcompat-v7", import = "android.support.v7.widget")

      "Data",
      "ListenableWorker",
      "OneTimeWorkRequest",
      "PeriodicWorkRequest",
      "Worker",
      "WorkManager",
      "WorkRequest" ->
        // Not available prior to AndroidX
        Library(artifact = "android.arch.work:work-runtime", import = "androidx.work")

      "RxWorker" ->
        // Not available prior to AndroidX
        Library(artifact = "android.arch.work:work-rxjava2", import = "androidx.work")

      "CoroutineWorker" ->
        // Not available prior to AndroidX
        Library(artifact = "android.arch.work.work-runtime-ktx", import = "androidx.work")

      "CardView" ->
        // In AndroidX this is androidx.cardview.widget
        Library(artifact = "com.android.support:cardview-v7", import = "android.support.v7.widget")

      "ContentPager" ->
        // In AndroidX this is androidx.contentpager.content
        Library(artifact = "com.android.support:support-content", import = "android.support.content")

      "CursorAdapter",
      "SimpleCursorAdapter",
      "CursorFilter" ->
        // In AndroidX this is androidx.cursoradapter.widget
        Library(artifact = "com.android.support:cursoradapter", import = "android.support.v4.widget")

      "DocumentFile",
      "RawDocumentFile",
      "SingleDocumentFile",
      "TreeDocumentFile" ->
        // In AndroidX this is androidx.documentfile.provider
        Library(artifact = "com.android.support:documentfile", import = "android.support.v4.provider")

      "EmojiCompat",
      "FontRequestEmojiCompatConfig" ->
        // In AndroidX this is androidx.emoji.text
        Library(artifact = "com.android.support:support-emoji", import = "android.support.text.emoji")

      "EmojiButton",
      "EmojiTextView" ->
        // In AndroidX this is androidx.emoji.widget
        Library(artifact = "com.android.support:support-emoji", import = "android.support.text.emoji.widget")

      "DialogFragment",
      "Fragment",
      "ListFragment",
      "FragmentManager",
      "FragmentActivity",
      "FragmentTransaction" ->
        // In AndroidX this is androidx.fragment.app
        Library(artifact = "androidx.fragment:fragment", import = "android.support.v4.app")

      "FragmentContainerView" ->
        // Not available prior to AndroidX
        Library(artifact = "androidx.fragment:fragment", import = "androidx.fragment.app")

      "GridLayout" ->
        // In AndroidX this is androidx.gridlayout.widget
        Library(artifact = "com.android.support:gridlayout-v7", import = "android.support.v7.widget")

      "FastOutLinearInInterpolator",
      "FastOutSlowInInterpolator",
      "LinearOutSlowInInterpolator",
      "LookupTableInterpolator" ->
        // In AndroidX this is androidx.interpolator.view.animation
        Library(artifact = "com.android.support:interpolator", import = "android.support.v4.view.animation")

      "Query",
      "Entity",
      "Dao",
      "Insert",
      "Delete",
      "Database",
      "Room",
      "RoomDatabase" ->
        // In AndroidX this is androidx.room
        Library(artifact = "android.arch.persistence.room:runtime", import = "android.arch.persistence.room")

      "SliceAction",
      "ListBuilder",
      "GridRowBuilder" ->
        // Not available prior to AndroidX
        Library(artifact = "com.android.support:slices-builders", import = "androidx.slice.builders")

      "SliceManager" ->
        // Not available prior to AndroidX
        Library(artifact = "com.android.support:slices-core", import = "androidx.slice")

      "SliceView" ->
        // Not available prior to AndroidX
        Library(artifact = "com.android.support:slices-view", import = "androidx.slice.widget")

      "SliceUtils" ->
        // Not available prior to AndroidX
        Library(artifact = "com.android.support:slices-view", import = "androidx.slice")

      "SlidingPaneLayout" ->
        // In AndroidX this is androidx.slidingpanelayout.widget
        Library(artifact = "com.android.support:slidingpanelayout", import = "android.support.v4.widget")

      "CustomTabsIntent" ->
        // In AndroidX this is androidx.browser.customtabs
        Library(artifact = "com.android.support:customtabs", import = "android.support.customtabs")

      // com.android.support:support-compat: Most compat classes are here
      "MenuCompat",
      "MenuItemCompat",
      "GravityCompat" ->
        // In AndroidX this is androidx.core.view
        Library(artifact = "com.android.support:support-compat", import = "android.support.v4.view")

      "NotificationCompat",
      "NotificationManagerCompat",
      "ServiceCompat",
      "JobIntentService" ->
        // In AndroidX this is androidx.core.app
        Library(artifact = "com.android.support:support-compat", import = "android.support.v4.app")

      "FileProvider",
      "ContextCompat" ->
        // In AndroidX this is androidx.core.content
        Library(artifact = "com.android.support:support-compat", import = "android.support.v4.content")

      "DrawableCompat" ->
        // In AndroidX this is androidx.core.graphics.drawable
        Library(artifact = "com.android.support:support-compat", import = "android.support.v4.graphics.drawable")

      "BitmapCompat" ->
        // In AndroidX this is androidx.core.graphics
        Library(artifact = "com.android.support:support-compat", import = "android.support.v4.graphics")

      "AdvertisingIdClient",
      "AdvertisingIdInfo" ->
        // Not available prior to AndroidX
        Library(artifact = "androidx.ads:ads-identifier", import = "androidx.ads.identifier")

      "AsyncLayoutInflater" ->
        // In AndroidX this is androidx.asynclayoutinflater.view
        Library(artifact = "androidx.asynclayoutinflater:asynclayoutinflater", import = "android.support.v4.view")

      "HintConstants" ->
        // Not available prior to AndroidX
        Library(artifact = "androidx.autofill:autofill", import = "androidx.autofill")

      "BenchmarkRule" ->
        // Not available prior to AndroidX
        Library(artifact = "androidx.benchmark:benchmark-junit4", import = "androidx.benchmark.junit4")

      "CameraX",
      "ImageAnalysis",
      "ImageAnalysisConfig",
      "Preview",
      "PreviewConfig",
      "ImageCapture",
      "ImageCaptureConfig" ->
        // Not available prior to AndroidX
        Library(artifact = "androidx.camera:camera-camera2", import = "androidx.camera.core")

      "AbsSavedState",
      "ExploreByTouchHelper",
      "FocusStrategy",
      "ViewDragHelper" ->
        // In AndroidX this is androidx.customview.widget
        Library(artifact = "androidx.customview:customview", import = "android.support.v4.view")

      "KeyedAppState",
      "KeyedAppStatesReporter",
      "KeyedAppStatesService",
      "ReceivedKeyedAppState" ->
        // Not available prior to AndroidX
        Library(artifact = "androidx.enterprise:enterprise-feedback", import = "androidx.enterprise.feedback")

      "RemoteCallback",
      "CallbackReceiver",
      "BroadcastReceiverWithCallbacks",
      "AppWidgetProviderWithCallbacks",
      "ContentProviderWithCallbacks",
      "ExternalInput" ->
        // Not available prior to AndroidX
        Library(artifact = "androidx.remotecallback:remotecallback", import = "androidx.remotecallback")

      "SavedStateRegistryOwner",
      "SavedStateRegistry",
      "SavedStateProvider",
      "SavedStateRegistryController" ->
        // Not available prior to AndroidX
        Library(artifact = "androidx.savedstate:savedstate", import = "androidx.savedstate")

      "EncryptedFile",
      "EncryptedSharedPreferences",
      "MasterKeys" ->
        // Not available prior to AndroidX
        Library(artifact = "androidx.security:security-crypto", import = "androidx.security.crypto")

      "CallbackToFutureAdapter" ->
        Library(artifact = "androidx.concurrent:concurrent-listenablefuture-callback", import = "androidx.concurrent.futures")

      "Composable" ->
        Library(artifact = "androidx.compose:compose-runtime", import = "androidx.compose")

      // Material Design library
      "Snackbar",
      "FloatingActionButton",
      "BottomAppBar",
      "TabLayout",
      "AppBarLayout",
      "BottomNavigationView",
      "CheckableImageButton" ->
        // In AndroidX this has a number of different package names based on each widget
        Library(artifact = "com.android.support:design", import = "android.support.design.widget")

      // Firebase Libraries

      "AppInviteInvitation" ->
        Library(artifact = "com.google.firebase:firebase-invites", import = "com.google.android.gms.appinvite")
      "AdView",
      "AdRequest" ->
        Library(artifact = "com.google.firebase:firebase-ads", import = "com.google.android.gms.ads")
      "FirebaseAnalytics" ->
        Library(artifact = "com.google.firebase:firebase-core", import = "com.google.firebase.analytics")
      "FirebaseMessagingService" ->
        Library(artifact = "com.google.firebase:firebase-messaging", import = "com.google.firebase.messaging")
      "FirebaseAuth" ->
        Library(artifact = "com.google.firebase:firebase-auth", import = "com.google.firebase.auth")
      "FirebaseDatabase" ->
        Library(artifact = "com.google.firebase:firebase-database", import = "com.google.firebase.database")
      "FirebaseStorage",
      "StorageReference" ->
        Library(artifact = "com.google.firebase:firebase-storage", import = "com.google.firebase.storage")
      "FirebaseRemoteConfig" ->
        Library(artifact = "com.google.firebase:firebase-config", import = "com.google.firebase.remoteconfig")
      "FirebaseCrash" ->
        Library(artifact = "com.google.firebase:firebase-crash", import = "com.google.firebase.crash")
      "FirebaseDynamicLinks" ->
        Library(artifact = "com.google.firebase:firebase-invites", import = "com.google.firebase.dynamiclinks")
      "FirebaseFirestore" ->
        Library(artifact = "com.google.firebase:firebase-firestore", import = "com.google.firebase.firestore")

      // ML

      "FirebaseVisionFace",
      "FirebaseVisionFaceDetectorOptions" ->
        Library(artifact = "com.google.firebase:firebase-ml-vision", import = "com.google.firebase.ml.vision.face")
      "FirebaseVisionLabelDetector" ->
        Library(artifact = "com.google.firebase:firebase-ml-vision", import = "com.google.firebase.ml.vision.label")
      "FirebaseVisionImage" ->
        Library(artifact = "com.google.firebase:firebase-ml-vision", import = "com.google.firebase.ml.vision.common")
      "FirebaseVisionText" ->
        Library(artifact = "com.google.firebase:firebase-ml-vision", import = "com.google.firebase.ml.vision.text")
      "FirebaseVisionBarcode" ->
        Library(artifact = "com.google.firebase:firebase-ml-vision", import = "com.google.firebase.ml.vision.barcode")
      "FirebaseVision" ->
        Library(artifact = "com.google.firebase:firebase-ml-vision", import = "com.google.firebase.ml.vision")
      "FirebaseVisionCloudDetectorOptions" ->
        Library(artifact = "com.google.firebase:firebase-ml-vision", import = "com.google.firebase.ml.vision.cloud")

      // Play Services Libraries

      "SignInButton" ->
        Library(artifact = "com.google.android.gms:play-services-auth", import = "com.google.android.gms.common")

      "GoogleSignIn",
      "GoogleSignInAccount",
      "GoogleSignInOptions" ->
        Library(artifact = "com.google.android.gms:play-services-auth", import = "com.google.android.gms.auth.api.signin")

      "Awareness" ->
        Library(artifact = "com.google.android.gms:play-services-awareness", import = "com.google.android.gms.awareness")

      "CastOptions",
      "CastContext" ->
        Library(artifact = "com.google.android.gms:play-services-cast", import = "com.google.android.gms.cast.framework")

      "Fitness",
      "FitnessOptions" ->
        Library(artifact = "com.google.android.gms:play-services-fitness", import = "com.google.android.gms.fitness")

      "LocationRequest",
      "LocationServices" ->
        Library(artifact = "com.google.android.gms:play-services-location", import = "com.google.android.gms.location")

      // https://developers.google.com.android.reference.com.google.android.gms.maps.OnMapReadyCallback
      "GoogleMap",
      "CameraUpdateFactory",
      "OnMapReadyCallback",
      "SupportMapFragment" ->
        Library(artifact = "com.google.android.gms:play-services-maps", import = "com.google.android.gms.maps")

      "LatLng",
      "MarkerOptions" ->
        Library(artifact = "com.google.android.gms:play-services-maps", import = "com.google.android.gms.maps.model")

      "Nearby" ->
        Library(artifact = "com.google.android.gms:play-services-nearby", import = "com.google.android.gms.nearby")

      "Games" ->
        Library(artifact = "com.google.android.gms:play-services-games", import = "com.google.android.gms.games")

      "PaymentsClient",
      "Wallet" ->
        Library(artifact = "com.google.android.gms:play-services-wallet", import = "com.google.android.gms.wallet")

      "BillingClient" ->
        Library(artifact = "com.android.billingclient:billing", import = "com.android.billingclient.api")

      "AppUpdateInfo",
      "AppUpdateManager",
      "AppUpdateManagerFactory" ->
        Library(artifact = "com.google.android.play:core", import = "com.google.android.play.core.appupdate")

      else -> null
    }
  }

  // For the given runtime artifact, if it also requires an annotation processor, provide it
  fun findAnnotationProcessor(artifact: String): String? {
    return when (artifact) {
      "androidx.room:room-runtime",
      "android.arch.persistence.room:runtime" -> "android.arch.persistence.room:compiler"
      "androidx.remotecallback:remotecallback" -> "androidx.remotecallback:remotecallback-processor"
      else -> null
    }
  }

  fun findKtxLibrary(artifact: String): String? {
    return when (artifact) {
      "android.arch.work:work-runtime" -> "android.arch.work:work-runtime-ktx"
      "android.arch.navigation:navigation-runtime" -> "android.arch.navigation:navigation-runtime-ktx"
      "android.arch.navigation:navigation-fragment" -> "android.arch.navigation:navigation-fragment-ktx"
      "android.arch.navigation:navigation-common" -> "android.arch.navigation:navigation-common-ktx"
      "android.arch.navigation:navigation-ui" -> "android.arch.navigation:;navigation-ui-ktx"
      "androidx.fragment:fragment" -> "androidx.fragment:fragment-ktx"
      "androidx.collection:collection" -> "androidx.collection:collection-ktx"
      "androidx.sqlite:sqlite" -> "androidx.sqlite:sqlite-ktx"
      "androidx.palette:palette" -> "androidx.palette:palette-ktx"
      "androidx.dynamicanimation:dynamicanimation" -> "androidx.dynamicanimation:dynamicanimation-ktx"
      "androidx.activity:activity" -> "androidx.activity:activity-ktx"
      "androidx.paging:paging-runtime" -> "androidx.paging:paging-runtime-ktx"
      "androidx.paging:paging-common" -> "androidx.paging:paging-common-ktx"
      "androidx.paging:paging-rxjava2" -> "androidx.paging:paging-rxjava2-ktx"
      "androidx.core:core" -> "androidx.core:core-ktx"
      "androidx.preference:preference" -> "androidx.preference:preference-ktx"
      "androidx.lifecycle:lifecycle-viewmodel" -> "androidx.lifecycle:lifecycle-viewmodel-ktx"
      "androidx.lifecycle:lifecycle-reactivestreams" -> "androidx.lifecycle:lifecycle-reactivestreams-ktx"
      "androidx.lifecycle:lifecycle-livedata" -> "androidx.lifecycle:lifecycle-livedata-ktx"
      "androidx.lifecycle:lifecycle-livedata-core" -> "androidx.lifecycle:lifecycle-livedata-core-ktx"
      "androidx.slice:slice-builders" -> "androidx.slice:slice-builders-ktx"
      "com.google.android.play:core" -> "com.google.android.play:core-ktx"
      "com.google.firebase:firebase-common" -> "com.google.firebase:firebase-common-ktx"
      "com.google.firebase:firebase-config" -> "com.google.firebase:firebase-config-ktx"
      "com.google.firebase:firebase-database" -> "com.google.firebase:firebase-database-ktx"
      "com.google.firebase:firebase-firestore" -> "com.google.firebase:firebase-firestore-ktx"
      "com.google.firebase:firebase-functions" -> "com.google.firebase:firebase-functions-ktx"
      "com.google.firebase:firebase-storage" -> "com.google.firebase:firebase-storage-ktx"
      else -> null
    }
  }
}
