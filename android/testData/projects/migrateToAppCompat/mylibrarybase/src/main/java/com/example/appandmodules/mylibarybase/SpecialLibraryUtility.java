package com.example.appandmodules.mylibarybase;

import android.view.ActionProvider;
import android.view.MenuItem;

public class SpecialLibraryUtility {

    public static ActionProvider theActionProvider(MenuItem item) {
        return item.getActionProvider();
    }
}
