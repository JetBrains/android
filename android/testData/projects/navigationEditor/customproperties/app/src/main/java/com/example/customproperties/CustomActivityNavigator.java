package com.example.customproperties;

import android.content.Context;
import android.support.annotation.NonNull;

import androidx.navigation.*;

@Navigator.Name("mycustomactivity")
public class CustomActivityNavigator extends ActivityNavigator {
    public CustomActivityNavigator(@NonNull Context context) {
        super(context);
    }
}