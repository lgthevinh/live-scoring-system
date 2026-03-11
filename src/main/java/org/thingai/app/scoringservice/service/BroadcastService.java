package org.thingai.app.scoringservice.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.thingai.app.scoringservice.dto.BroadcastMessageDto;
import org.thingai.base.log.ILog;

@Service
public class BroadcastService {
    private static final String TAG = "BroadcastService";

    private static SimpMessagingTemplate messagingTemplate;

    @Autowired
    public BroadcastService(SimpMessagingTemplate messagingTemplate) {
        BroadcastService.messagingTemplate = messagingTemplate;
    }

    /**
     * Broadcast a message to a specific topic.
     *
     * @param topic   The topic to broadcast to (e.g., "/topic/scores").
     * @param message The message payload.
     */
    public static void broadcast(String topic, Object message, String messageType) {
        ILog.d(TAG, topic, messageType);
        BroadcastMessageDto broadcastMessage = new BroadcastMessageDto(messageType, message);
        messagingTemplate.convertAndSend(topic, broadcastMessage);
    }
}