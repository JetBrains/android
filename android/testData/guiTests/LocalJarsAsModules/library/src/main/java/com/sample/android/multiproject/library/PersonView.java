package com.example.android.multiproject.library;

import android.content.Context;
import android.widget.TextView;

class PersonView extends TextView {
    public PersonView(Context context, String name) {
        super(context);
        setTextSize(20);
        setText(name);
    }
}
