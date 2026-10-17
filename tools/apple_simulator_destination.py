"""Select an available iPhone runtime for the manual Mac verification job."""
import json
import subprocess


def select_iphone(document):
    for runtime, devices in sorted(document.get("devices", {}).items(), reverse=True):
        if "iOS" not in runtime:
            continue
        for device in devices:
            if device.get("isAvailable") and device.get("name", "").startswith("iPhone"):
                return device["udid"]
    raise RuntimeError("Install an iPhone simulator runtime in Xcode before running Apple verification")


if __name__ == "__main__":
    result = subprocess.run(["xcrun", "simctl", "list", "devices", "available", "--json"], check=True, capture_output=True, text=True)
    print("STICKERDOCK_SIMULATOR_ID=" + select_iphone(json.loads(result.stdout)))
