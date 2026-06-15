import json
import logging
import paho.mqtt.client as mqtt
import config

logger = logging.getLogger(__name__)


class MqttManager:
    def __init__(self, on_control_cmd=None):
        self.client = mqtt.Client(client_id="rainwell-gateway", clean_session=True)
        self.on_control_cmd = on_control_cmd
        self.client.on_connect = self._on_connect
        self.client.on_message = self._on_message

    def _on_connect(self, client, userdata, flags, rc):
        if rc == 0:
            logger.info("MQTT 连接成功")
            client.subscribe(config.TOPIC_CONTROL_CMD)
            logger.info("已订阅控制指令主题: %s", config.TOPIC_CONTROL_CMD)
        else:
            logger.error("MQTT 连接失败, rc=%d", rc)

    def _on_message(self, client, userdata, msg):
        try:
            payload = json.loads(msg.payload.decode("utf-8"))
            logger.info("收到控制指令: topic=%s, payload=%s", msg.topic, payload)
            if self.on_control_cmd:
                self.on_control_cmd(payload)
        except Exception as e:
            logger.error("处理控制指令异常: %s", e)

    def connect(self):
        try:
            self.client.connect(
                host=config.MQTT_BROKER,
                port=config.MQTT_PORT,
                keepalive=config.MQTT_KEEPALIVE,
            )
            self.client.loop_start()
        except Exception as e:
            logger.error("MQTT 连接异常: %s", e)

    def publish_water_level(self, levels):
        payload = {
            "timestamp": __import__("time").time(),
            "wells": levels,
        }
        self.client.publish(
            config.TOPIC_WATER_LEVEL,
            json.dumps(payload),
            qos=1,
        )
        logger.debug("发送水位数据: %s", payload)

    def publish_ack(self, ack_data):
        self.client.publish(
            config.TOPIC_CONTROL_ACK,
            json.dumps(ack_data),
            qos=1,
        )
        logger.info("发送控制应答: %s", ack_data)

    def disconnect(self):
        self.client.loop_stop()
        self.client.disconnect()
        logger.info("MQTT 已断开")
