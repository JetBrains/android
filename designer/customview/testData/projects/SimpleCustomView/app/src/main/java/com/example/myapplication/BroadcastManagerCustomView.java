package com.example.myapplication;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class BroadcastManagerCustomView extends View {
    public BroadcastManagerCustomView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        LocalBroadcastManager.getInstance(context);
    }
}
