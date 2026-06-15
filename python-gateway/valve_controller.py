import serial
import logging
import config

logger = logging.getLogger(__name__)


class ValveController:
    def __init__(self):
        self.ser = None
        self.valve_states = {1: False, 2: False, 3: False}
        self.pump_states = {1: False, 2: False, 3: False}

    def _ensure_connection(self):
        if not self.ser or not self.ser.is_open:
            try:
                self.ser = serial.Serial(
                    port=config.SERIAL_PORT,
                    baudrate=config.SERIAL_BAUDRATE,
                    timeout=config.SERIAL_TIMEOUT,
                )
            except serial.SerialException as e:
                logger.error("阀门控制器串口连接失败: %s", e)
                self.ser = None
                return False
        return True

    def set_valve(self, well_a, well_b, open_valve):
        if not self._ensure_connection():
            return False

        valve_id = f"{min(well_a, well_b)}{max(well_a, well_b)}"
        action = "OPEN" if open_valve else "CLOSE"
        cmd = f"VL:{valve_id}:{action}\r\n"
        try:
            self.ser.write(cmd.encode("utf-8"))
            resp = self.ser.readline().decode("utf-8").strip()
            if resp.startswith("OK"):
                key = (min(well_a, well_b), max(well_a, well_b))
                self.valve_states[f"{key[0]}-{key[1]}"] = open_valve
                logger.info(
                    "阀门 %d-%d %s 成功", well_a, well_b, action
                )
                return True
            else:
                logger.warning("阀门 %d-%d %s 失败: %s", well_a, well_b, action, resp)
                return False
        except Exception as e:
            logger.error("阀门控制异常: %s", e)
            self.ser = None
            return False

    def set_pump(self, well_id, running):
        if not self._ensure_connection():
            return False

        action = "ON" if running else "OFF"
        cmd = f"PM:{well_id:02d}:{action}\r\n"
        try:
            self.ser.write(cmd.encode("utf-8"))
            resp = self.ser.readline().decode("utf-8").strip()
            if resp.startswith("OK"):
                self.pump_states[well_id] = running
                logger.info("井 %d 抽水泵 %s 成功", well_id, action)
                return True
            else:
                logger.warning("井 %d 抽水泵 %s 失败: %s", well_id, action, resp)
                return False
        except Exception as e:
            logger.error("水泵控制异常: %s", e)
            self.ser = None
            return False

    def handle_control_command(self, payload):
        actions = payload.get("actions", [])
        results = []
        for act in actions:
            act_type = act.get("type")
            if act_type == "valve":
                ok = self.set_valve(act["well_a"], act["well_b"], act["open"])
                results.append(
                    {"type": "valve", "well_a": act["well_a"], "well_b": act["well_b"], "success": ok}
                )
            elif act_type == "pump":
                ok = self.set_pump(act["well_id"], act["running"])
                results.append(
                    {"type": "pump", "well_id": act["well_id"], "success": ok}
                )
        return results
