package com.android.navigation;

import java.util.ArrayList;

class Stack<T> extends ArrayList<T> {
    public void push(T a) {
        add(a);
    }

    public T getLast() {
        return get(size() - 1);
    }

    public T getLast(int i) {
        return get(size() - 1 - i);
    }

    public T pop() {
        return remove(size() - 1);
    }
}
