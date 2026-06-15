import logging
import time
import json

from serial_reader import SerialReader
from mqtt_client import MqttManager
from valve_controller import ValveController
import config

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)


def main():
    serial_reader = SerialReader()
    valve_ctrl = ValveController()

    def on_control_cmd(payload):
        logger.info("处理控制指令: %s", payload)
        results = valve_ctrl.handle_control_command(payload)
        mqtt_mgr.publish_ack(
            {"command_id": payload.get("command_id"), "results": results}
        )

    mqtt_mgr = MqttManager(on_control_cmd=on_control_cmd)
    mqtt_mgr.connect()

    logger.info("===== 雨水集蓄工控网关启动 =====")
    logger.info("井数: %d, 采集间隔: %ds", config.WELL_COUNT, config.POLL_INTERVAL_SEC)

    try:
        while True:
            levels = serial_reader.read_water_levels()
            if levels:
                mqtt_mgr.publish_water_level(levels)
                logger.info("水位采集: %s", levels)
            else:
                logger.warning("本次水位采集失败，跳过上报")
            time.sleep(config.POLL_INTERVAL_SEC)
    except KeyboardInterrupt:
        logger.info("接收到停止信号，正在关闭...")
    finally:
        serial_reader.close()
        mqtt_mgr.disconnect()
        logger.info("===== 网关已关闭 =====")


if __name__ == "__main__":
    main()
