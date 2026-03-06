package org.thingai.app.scoringservice.handler;

import org.thingai.app.scoringservice.callback.EventHandlerCallback;
import org.thingai.app.scoringservice.callback.RequestCallback;
import org.thingai.app.scoringservice.define.ErrorCode;
import org.thingai.app.scoringservice.entity.config.DbMapEntity;
import org.thingai.app.scoringservice.entity.event.Event;
import org.thingai.app.scoringservice.repository.LocalRepository;
import org.thingai.base.dao.exceptions.DaoException;
import org.thingai.base.log.ILog;

import java.io.File;

public class EventHandler {
    private static final String TAG = "EventHandler";

    private final EventHandlerCallback eventCallback;

    private Event currentEvent;

    public EventHandler(EventHandlerCallback eventCallback) {
        this.eventCallback = eventCallback;

        try {
            if (isCurrentEventSet()) {
                LocalRepository.initializeEvent(this.currentEvent.getEventCode());
                eventCallback.isCurrentEventSet(this.currentEvent);
            } else {
                eventCallback.isNotCurrentEventSet();
            }
        } catch (Exception e) {
            ILog.e(TAG, "Error initializing EventHandler: " + e.getMessage());
            eventCallback.isNotCurrentEventSet();
        }
    }

    public void createEvent(Event event, RequestCallback<Event> callback) {
        try {
            LocalRepository.eventDao().insertEvent(event);
            this.currentEvent = event;
            callback.onSuccess(event, "Event created successfully");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.DAO_CREATE_FAILED, "Failed to create event: " + e.getMessage());
        }
    }

    public void listEvents(RequestCallback<Event[]> callback) {
        try {
            Event[] events = LocalRepository.eventDao().listEvents();
            callback.onSuccess(events, "Events retrieved successfully.");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.DAO_RETRIEVE_FAILED, "Error retrieving events: " + e.getMessage());
        }
    }

    public void getEventByCode(String eventCode, RequestCallback<Event> callback) {
        try {
            Event event = LocalRepository.eventDao().getEventByEventCode(eventCode);
            if (event == null) {
                callback.onFailure(ErrorCode.DAO_NOT_FOUND, "Event with code " + eventCode + " not found.");
                return;
            }
            callback.onSuccess(event, "Event retrieved successfully.");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.DAO_RETRIEVE_FAILED, "Error retrieving event: " + e.getMessage());
        }
    }

    public void deleteEventByCode(String eventCode, boolean cleanDelete, RequestCallback<Void> callback) {
        try {
            if (currentEvent != null && currentEvent.getEventCode().equals(eventCode)) {
                callback.onFailure(ErrorCode.DAO_DELETE_FAILED, "Cannot delete the current active event.");
                return;
            }

            Event event = LocalRepository.eventDao().getEventByEventCode(eventCode);
            if (event == null) {
                callback.onFailure(ErrorCode.DAO_NOT_FOUND, "Event with code " + eventCode + " not found.");
                return;
            }

            LocalRepository.eventDao().deleteEvent(event.getUuid());

            if (cleanDelete) {
                File dbFile = new File(eventCode + ".db");
                if (dbFile.exists()) {
                    if (!dbFile.delete()) {
                        callback.onFailure(ErrorCode.DAO_DELETE_FAILED, "Failed to delete event database file.");
                        return;
                    }
                }
                File eventFilesDir = new File("files/" + eventCode);
                if (eventFilesDir.exists() && eventFilesDir.isDirectory()) {
                    File[] files = eventFilesDir.listFiles();
                    if (files == null) {
                        return;
                    }
                    for (File file : files) {
                        if (!file.delete()) {
                            callback.onFailure(ErrorCode.DAO_DELETE_FAILED, "Failed to delete event file: " + file.getName());
                            return;
                        }
                    }
                }
            }

            callback.onSuccess(null, "Event deleted successfully.");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.DAO_DELETE_FAILED, "Error deleting event: " + e.getMessage());
        }
    }

    public void updateEvent(Event event, RequestCallback<Boolean> callback) {
        try {
            if (event.getEventCode() == null || event.getEventCode().isEmpty()) {
                callback.onFailure(ErrorCode.DAO_UPDATE_FAILED, "Event code is required for update.");
                return;
            }

            if (event.getUuid().equals(this.currentEvent.getUuid())) {
                currentEvent = event;
            }

            LocalRepository.eventDao().updateEvent(event);
            callback.onSuccess(true, "Event updated successfully.");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.DAO_UPDATE_FAILED, "Error updating event: " + e.getMessage());
        }
    }

    public void setSystemEvent(String eventCode, RequestCallback<Event> callback) {
        try {
            ILog.d(TAG, eventCode);
            Event event = LocalRepository.eventDao().getEventByEventCode(eventCode);
            if (event == null) {
                callback.onFailure(ErrorCode.DAO_NOT_FOUND, "Event with code " + eventCode + " not found.");
                return;
            }
            this.currentEvent = event;

            DbMapEntity mapEntity = new DbMapEntity();
            mapEntity.setKey("current_event");
            mapEntity.setValue(eventCode);
            LocalRepository.systemDatabase().insertOrUpdate(mapEntity);

            LocalRepository.initializeEvent(eventCode);
            eventCallback.onSetEvent();

            callback.onSuccess(this.currentEvent, "Current event set successfully.");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.DAO_RETRIEVE_FAILED, "Error setting current event: " + e.getMessage());
        }
    }

    public void clearCurrentEvent(RequestCallback<Void> callback) {
        try {
            if (this.currentEvent == null) {
                callback.onSuccess(null, "No current event to clear.");
                return;
            }
            
            this.currentEvent = null;

            DbMapEntity[] mapEntities = LocalRepository.systemDatabase().query(DbMapEntity.class, "key", "current_event");
            if (mapEntities.length > 0) {
                LocalRepository.systemDatabase().delete(mapEntities[0]);
            }
            
            eventCallback.isNotCurrentEventSet();
            
            callback.onSuccess(null, "Current event cleared successfully.");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.DAO_UPDATE_FAILED, "Error clearing current event: " + e.getMessage());
        }
    }

    public boolean isCurrentEventSet() throws DaoException {
        DbMapEntity[] mapEntities = LocalRepository.systemDatabase().query(DbMapEntity.class, "key", "current_event");
        if (mapEntities.length > 0) {
            String eventCode = mapEntities[0].getValue();
            try {
                Event event = LocalRepository.eventDao().getEventByEventCode(eventCode);
                if (event != null) {
                    this.currentEvent = event;
                    return true;
                }
            } catch (Exception e) {
                return false;
            }
        }

        return this.currentEvent != null;
    }

    public Event getCurrentEvent() {
        return currentEvent;
    }
}