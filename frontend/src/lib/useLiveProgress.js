import { useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

/**
 * Subscribes to live processing progress over STOMP.
 * Uses SockJS with notification-service websocket.
 * Auto reconnect enabled.
 */
export function useLiveProgress(userId) {
  const [connected, setConnected] = useState(false);
  const [events, setEvents] = useState([]);
  const clientRef = useRef(null);

  useEffect(() => {
    if (userId == null) return undefined;

    const client = new Client({
      webSocketFactory: () =>
        new SockJS('http://localhost:8083/ws'),

      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,

      onConnect: () => {
        console.log('WebSocket connected');
        setConnected(true);

        // User-specific progress
        client.subscribe(`/topic/progress/${userId}`, (msg) => {
          const ev = JSON.parse(msg.body);

          setEvents((prev) => [
            ev,
            ...prev,
          ].slice(0, 200));
        });

        // Global progress stream
        client.subscribe('/topic/progress', (msg) => {
          const ev = JSON.parse(msg.body);

          setEvents((prev) => [
            { ...ev, _global: true },
            ...prev,
          ].slice(0, 200));
        });
      },

      onDisconnect: () => {
        console.log('WebSocket disconnected');
        setConnected(false);
      },

      onWebSocketClose: () => {
        console.log('WebSocket closed');
        setConnected(false);
      },

      onStompError: (frame) => {
        console.error('STOMP Error:', frame);
        setConnected(false);
      },
    });

    client.activate();
    clientRef.current = client;

    return () => {
      if (clientRef.current) {
        clientRef.current.deactivate();
      }
    };
  }, [userId]);

  return {
    connected,
    events,
  };
}