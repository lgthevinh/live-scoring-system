package org.thingai.app.scoringservice.repository;

import org.thingai.app.scoringservice.entity.event.Event;
import org.thingai.base.dao.Dao;

public class EventRepository {
    private static Dao dao;

    public static void initialize(Dao daoInstance) {
        dao = daoInstance;
    }

    public static Event insertEvent(Event event) throws Exception {
        dao.insert(event);
        return event;
    }

    public static Event updateEvent(Event event) throws Exception {
        dao.insertOrUpdate(event);
        return event;
    }

    public static void deleteEvent(String eventUuid) throws Exception {
        dao.deleteByColumn(Event.class, "uuid", eventUuid);
    }

    public static Event[] listEvents() throws Exception {
        return dao.readAll(Event.class);
    }

    public static Event getEventById(String eventUuid) throws Exception {
        Event[] events = dao.query(Event.class, new String[]{"uuid"}, new String[]{eventUuid});
        if (events != null && events.length > 0) {
            return events[0];
        }
        return null;
    }

    public static Event getEventByEventCode(String eventCode) throws Exception {
        Event[] events = dao.query(Event.class, new String[]{"eventCode"}, new String[]{eventCode});
        if (events != null && events.length > 0) {
            return events[0];
        }
        return null;
    }
}