"""Independently parse generated cloud-init/TOML and syntax-check POSIX first-boot scripts.

Run Gradle testDebugUnitTest first. Requires PyYAML==6.0.2 and a POSIX shell.
The shell is invoked with -n; no provisioning script is ever executed.
"""
from pathlib import Path
import shutil
import subprocess
import tomllib
import yaml

ROOT = Path(__file__).resolve().parents[1]


def main():
    samples = ROOT / "app/build/customization-samples"
    for mode in ("cloudinit", "cloudinit-rpi"):
        user = yaml.safe_load((samples / mode / "user-data").read_text())
        network = yaml.safe_load((samples / mode / "network-config").read_text())
        assert user["hostname"] == "test-pi"
        assert user["user"]["name"] == "testuser"
        assert user["user"]["passwd"].startswith("$6$")
        assert user["ssh_pwauth"] is True
        connect = next(command for command in user["runcmd"] if "CONNECT_DIR=" in command[-1])
        assert connect[:4] == ["sh", "-eu", "-c", connect[-1]]
        assert "rpuak_" + "A" * 24 in connect[-1]
        assert 'chmod 600 "$CONNECT_DIR/auth.key"' in connect[-1]
        script_path = samples / mode / "connect.sh"
        script_path.write_text(connect[-1], encoding="utf-8")
        shell = shutil.which("sh") or "C:/Program Files/Git/bin/sh.exe"
        subprocess.run([shell, "-n", str(script_path)], check=True)
        assert network["network"]["ethernets"]["eth0"]["dhcp4"] is True
        assert network["network"]["wifis"]["wlan0"]["access-points"]['Test: " Wi-Fi']["password"] == "test'\"$`123456"
        print(f"{mode}: YAML structure, credentials encoding and wired DHCP passed")
    preseed = tomllib.loads((samples / "rpi-preseed/rpi-preseed.toml").read_text())
    assert preseed["system"]["hostname"] == "test-pi"
    assert preseed["user"]["password_encrypted"] is True
    assert preseed["user"]["password"].startswith("$6$")
    assert preseed["wlan"]["ssid"] == 'Test: " Wi-Fi'
    assert preseed["wlan"]["password"] == "test'\"$`123456"
    assert preseed["interfaces"] == {"i2c": True, "spi": True, "onewire": True}
    assert preseed["connect"] == {"enabled": True, "mode": "token", "token": "rpuak_" + "A" * 24}
    print("rpi-preseed: TOML syntax, sections and credentials encoding passed")
    shell = shutil.which("sh") or "C:/Program Files/Git/bin/sh.exe"
    subprocess.run([shell, "-n", str(samples / "systemd/firstrun.sh")], check=True)
    print("systemd: POSIX shell syntax passed (script was not executed)")


if __name__ == "__main__":
    main()
