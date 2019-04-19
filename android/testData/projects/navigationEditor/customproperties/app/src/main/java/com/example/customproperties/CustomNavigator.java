package com.example.customproperties;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import androidx.navigation.*;

@Navigator.Name("mycustomdestination")
public class CustomNavigator extends Navigator<CustomNavigator.Destination> {
    @Override
    public CustomNavigator.Destination createDestination() {
        return new Destination(this);
    }

    @Override
    @Nullable
    public NavDestination navigate(@NonNull CustomNavigator.Destination destination,
                                   @Nullable Bundle args,
                                   @Nullable NavOptions navOptions,
                                   @Nullable Extras navigatorExtras) {
        return new Destination(this);
    }

    @Override
    public boolean popBackStack() {
        return false;
    }

    static class Destination extends NavDestination {
        Destination(CustomNavigator navigator) {
            super(navigator);
        }
    }
}