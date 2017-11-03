package android.arch.lifecycle;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public class <warning descr="Class 'LifecycleUsage' is never used">LifecycleUsage</warning> {
    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    void addLocationListener() {
    }
}

@SuppressWarnings("unused")
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface OnLifecycleEvent {
    Lifecycle.Event[] value();
}

class Lifecycle {
    enum Event {
        ON_RESUME
    }
}