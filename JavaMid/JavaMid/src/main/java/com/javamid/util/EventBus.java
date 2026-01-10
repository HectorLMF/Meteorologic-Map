package com.javamid.util;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * Lightweight event bus using PropertyChangeSupport.
 */
public class EventBus {
    private final PropertyChangeSupport support = new PropertyChangeSupport(this);

    public void subscribe(String eventName, PropertyChangeListener listener) {
        support.addPropertyChangeListener(eventName, listener);
    }

    public void unsubscribe(String eventName, PropertyChangeListener listener) {
        support.removePropertyChangeListener(eventName, listener);
    }

    public void publish(String eventName, Object payload) {
        support.firePropertyChange(eventName, null, payload);
    }
}
