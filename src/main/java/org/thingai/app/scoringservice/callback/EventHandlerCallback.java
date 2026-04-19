package org.thingai.app.scoringservice.callback;

import org.thingai.app.scoringservice.entity.Event;

public interface EventHandlerCallback {
    void onSetEvent();

    void isCurrentEventSet(Event currentEvent);

    void isNotCurrentEventSet();
}