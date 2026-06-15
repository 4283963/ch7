import serial
import logging
import time
import config

logger = logging.getLogger(__name__)


class SerialReader:
    def __init__(self):
        self.ser = None

    def connect(self):
        try:
            self.ser = serial.Serial(
                port=config.SERIAL_PORT,
                baudrate=config.SERIAL_BAUDRATE,
                timeout=config.SERIAL_TIMEOUT,
            )
            logger.info("串口连接成功: %s", config.SERIAL_PORT)
        except serial.SerialException as e:
            logger.error("串口连接失败: %s", e)
            self.ser = None

    def read_water_levels(self):
        if not self.ser or not self.ser.is_open:
            self.connect()
            if not self.ser:
                return None

        try:
            levels = {}
            for well_id in config.WELL_IDS:
                cmd = f"RD:{well_id:02d}\r\n"
                self.ser.write(cmd.encode("utf-8"))
                time.sleep(0.1)
                resp = self.ser.readline().decode("utf-8").strip()
                if resp.startswith("OK"):
                    parts = resp.split(":")
                    if len(parts) >= 3:
                        level_cm = int(parts[2])
                        levels[well_id] = level_cm
                else:
                    logger.warning("井 %d 读取异常: %s", well_id, resp)
                    levels[well_id] = -1

            return levels if levels else None
        except Exception as e:
            logger.error("读取水位异常: %s", e)
            self.ser = None
            return None

    def close(self):
        if self.ser and self.ser.is_open:
            self.ser.close()
            logger.info("串口已关闭")
