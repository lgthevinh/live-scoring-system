package org.thingai.app.scoringservice.repository;

import org.thingai.app.scoringservice.entity.Event;
import org.thingai.base.dao.Dao;

public class DaoEvent {
    private final Dao dao;

    public DaoEvent(Dao dao) {
        this.dao = dao;
    }

    public Event insertEvent(Event event) throws Exception {
        dao.insert(event);
        return event;
    }

    public Event updateEvent(Event event) throws Exception {
        dao.insertOrUpdate(event);
        return event;
    }

    public void deleteEvent(String eventUuid) throws Exception {
        dao.deleteByColumn(Event.class, "uuid", eventUuid);
    }

    public Event[] listEvents() throws Exception {
        return dao.readAll(Event.class);
    }

    public Event getEventById(String eventUuid) throws Exception {
        Event[] events = dao.query(Event.class, new String[]{"uuid"}, new String[]{eventUuid});
        if (events != null && events.length > 0) {
            return events[0];
        }
        return null;
    }

    public Event getEventByEventCode(String eventCode) throws Exception {
        Event[] events = dao.query(Event.class, new String[]{"eventCode"}, new String[]{eventCode});
        if (events != null && events.length > 0) {
            return events[0];
        }
        return null;
    }
}
