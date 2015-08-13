package com.example.android.multiproject.person;

import java.util.ArrayList;
import java.util.Iterator;

public class People implements Iterable<Person> {
    public Iterator<Person> iterator() {
        ArrayList<Person> list = new ArrayList<Person>();
        list.add(new Person("Fred"));
        list.add(new Person("Barney"));
        return list.iterator();
    }
}
