import unittest

from apple_simulator_destination import select_iphone


class AppleSimulatorDestinationTest(unittest.TestCase):
    def test_uses_available_iphone_not_ipad_or_unavailable_runtime(self):
        document = {"devices": {"com.apple.CoreSimulator.SimRuntime.iOS-18-5": [
            {"name": "iPhone old", "udid": "unavailable", "isAvailable": False},
            {"name": "iPad Pro", "udid": "ipad", "isAvailable": True},
            {"name": "iPhone 16", "udid": "iphone", "isAvailable": True},
        ]}}
        self.assertEqual(select_iphone(document), "iphone")

    def test_fails_explicitly_when_only_tvos_or_unavailable_devices_exist(self):
        for document in ({}, {"devices": {"tvOS-18": [{"name": "iPhone bogus", "udid": "no", "isAvailable": True}]}}):
            with self.assertRaises(RuntimeError):
                select_iphone(document)


if __name__ == "__main__":
    unittest.main()
