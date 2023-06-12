package com.example.myface;

import android.view.SurfaceHolder;

import com.google.common.util.concurrent.ListenableFuture;

import androidx.annotation.NonNull;
import androidx.wear.watchface.ComplicationSlotsManager;
import androidx.wear.watchface.ListenableWatchFaceService;
import androidx.wear.watchface.WatchFace;
import androidx.wear.watchface.WatchState;
import androidx.wear.watchface.style.CurrentUserStyleRepository;

public class MyWatchFace extends ListenableWatchFaceService {
    @NonNull
    @Override
    protected ListenableFuture<WatchFace> createWatchFaceFuture(@NonNull SurfaceHolder surfaceHolder, @NonNull WatchState watchState, @NonNull ComplicationSlotsManager complicationSlotsManager, @NonNull CurrentUserStyleRepository currentUserStyleRepository) {
        return null;
    }
}
