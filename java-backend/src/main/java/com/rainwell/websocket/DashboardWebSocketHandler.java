package com.rainwell.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class DashboardWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(DashboardWebSocketHandler.class);

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper;

    public DashboardWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("WebSocket 连接建立: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("WebSocket 连接关闭: {}", session.getId());
    }

    public void broadcastWaterLevels(Map<Integer, Integer> levels) {
        try {
            Map<String, Object> msg = Map.of(
                    "type", "water_level",
                    "data", levels
            );
            String json = objectMapper.writeValueAsString(msg);
            broadcast(json);
        } catch (Exception e) {
            log.error("广播水位数据异常: {}", e.getMessage());
        }
    }

    public void broadcastPumpStatus(Map<Integer, Boolean> pumpStates) {
        try {
            Map<String, Object> msg = Map.of(
                    "type", "pump_status",
                    "data", pumpStates
            );
            String json = objectMapper.writeValueAsString(msg);
            broadcast(json);
        } catch (Exception e) {
            log.error("广播水泵状态异常: {}", e.getMessage());
        }
    }

    public void broadcastValveStatus(Map<String, Boolean> valveStates) {
        try {
            Map<String, Object> msg = Map.of(
                    "type", "valve_status",
                    "data", valveStates
            );
            String json = objectMapper.writeValueAsString(msg);
            broadcast(json);
        } catch (Exception e) {
            log.error("广播阀门状态异常: {}", e.getMessage());
        }
    }

    private void broadcast(String json) {
        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(message);
                } catch (IOException e) {
                    log.error("发送 WebSocket 消息失败: {}", e.getMessage());
                }
            }
        }
    }
}
