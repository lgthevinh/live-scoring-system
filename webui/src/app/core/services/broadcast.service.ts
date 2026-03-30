import { Injectable, OnDestroy } from '@angular/core';
import {Client, IMessage, StompSubscription} from '@stomp/stompjs';
import { Subject, Observable, BehaviorSubject } from 'rxjs'; // Import BehaviorSubject
import { BroadcastMessage } from '../models/broadcast.model';
import {environment} from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class BroadcastService implements OnDestroy {
  private client: Client;
  private messagesSubject = new Subject<BroadcastMessage>();
  public messages$: Observable<BroadcastMessage> = this.messagesSubject.asObservable();
  // Use BehaviorSubject to track connection state and allow late subscribers
  private connectionState = new BehaviorSubject<boolean>(false);
  private subscriptions: Map<string, StompSubscription> = new Map();

  constructor() {
    const brokerUrl: string = this.resolveBrokerUrl();
    this.client = new Client({
      brokerURL: brokerUrl,
      debug: (str) => { console.debug(new Date(), str); },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
    });

    console.log("websocket url:", brokerUrl);

    this.client.onConnect = (frame) => {
      console.log('STOMP: Connected');
      this.connectionState.next(true); // Signal successful connection
    };

    this.client.onStompError = (frame) => {
      console.error('STOMP: Broker reported error: ' + frame.headers['message']);
      console.error('STOMP: Additional details: ' + frame.body);
      this.connectionState.next(false);
    };

    this.client.onWebSocketClose = () => {
      console.log("STOMP: WebSocket connection closed.");
      this.connectionState.next(false);
    };

    this.client.onWebSocketError = (error) => {
      console.error("STOMP: WebSocket error:", error);
      this.connectionState.next(false);
      // Attempt reconnection
      if (!this.client.active) {
        this.client.activate();
      }
    }

    this.client.activate();
  }

  private resolveBrokerUrl(): string {
    const apiBaseUrl = environment.apiBaseUrl?.trim();
    if (apiBaseUrl) {
      const normalized = apiBaseUrl.replace(/\/$/, '');
      return normalized.replace(/^http/, 'ws') + '/ws';
    }

    const protocol = window.location.protocol === 'https:' ? 'wss://' : 'ws://';
    return protocol + window.location.host + '/ws';
  }

  // Generic internal subscription logic
  private subscribeToTopicInternal(topic: string) {
    this.client.subscribe(topic, (message: IMessage) => {
      try {
        const parsedMessage: BroadcastMessage = JSON.parse(message.body);
        this.messagesSubject.next(parsedMessage);
      } catch (e) {
        console.error(`Failed to parse incoming WebSocket message on ${topic}`, message.body, e);
      }
    });
    console.log(`STOMP: Subscribed to ${topic}`);
  }


  // Public method for components to subscribe dynamically
  subscribeToTopic(topic: string): Observable<BroadcastMessage> {
    const topicSubject = new Subject<BroadcastMessage>();
    // Only subscribe if connected, handle late subscription
    this.connectionState.subscribe(isConnected => {
      if (isConnected && this.client.connected) {
        const subscription = this.client.subscribe(topic, (message: IMessage) => {
          try {
            topicSubject.next(JSON.parse(message.body));
          } catch (e) { topicSubject.error(e); }
        });
        this.subscriptions.set(topic, subscription);
        console.log(`Subscribed to ${topic}`);
        // When the topicSubject is unsubscribed from, clean up the STOMP subscription
        return () => {
          if (subscription) {
            subscription.unsubscribe();
            console.log(`Unsubscribed from ${topic}`);
          }
        }
      }
      return undefined;
    });
    return topicSubject.asObservable();
  }

  subscribeToTopics(topics: string[]): Observable<BroadcastMessage> {
    topics.forEach(topic => this.subscribeToTopicInternal(topic));
    return this.messages$;
  }

  unsubscribeFromTopic(topic: string) {
    const subscription = this.subscriptions.get(topic);
    if (subscription) {
      subscription.unsubscribe();
      this.subscriptions.delete(topic);
      console.log(`STOMP: Unsubscribed from ${topic}`);
    } else {
      console.warn(`STOMP: No active subscription found for topic ${topic}`);
    }
  }

  unsubscribeAll() {
    this.subscriptions.forEach(sub => sub.unsubscribe());
    this.subscriptions.clear();
    console.log("STOMP: Unsubscribed from all topics.");
  }

  publishMessage(topic: string, message: BroadcastMessage) {
    if (this.client && this.client.connected) {
      this.client.publish({
        destination: topic,
        body: JSON.stringify(message)
      });
      console.log(`STOMP: Published message to ${topic}`, message);
    } else {
      console.error("STOMP: Cannot publish message, client not connected.");
    }
  }

  ngOnDestroy() {
    if (this.client.active) {
      this.client.deactivate().then(() => {
        console.log("STOMP: Client deactivated on destroy.");
      });
    }
    this.connectionState.complete();
    this.messagesSubject.complete();
  }
}

