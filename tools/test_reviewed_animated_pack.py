"""Keep the checked-in first real-motion pack valid for the phone importer."""
import hashlib
import json
from pathlib import Path
import unittest

from publish_factory_pack import validate_pack, validate_references


class ReviewedAnimatedPackTest(unittest.TestCase):
    def test_moving_animal_moods(self):
        root = Path(__file__).resolve().parents[1] / "assets/reviewed/moving-animal-moods"
        data = (root / "pack.zip").read_bytes()
        self.assertEqual(hashlib.sha256(data).hexdigest(),
                         "c22c5c63db1bc772c79aba05a68b57e343f2c21a952fa4f86cea4f33240351fc")
        manifest = validate_pack(data, public=True)
        self.assertEqual(len(manifest["packs"]), 1)
        pack = manifest["packs"][0]
        self.assertEqual((pack["kind"], pack["category"], len(pack["stickers"])),
                         ("ANIMATED", "REACTIONS", 3))
        self.assertEqual([item["label"] for item in pack["stickers"]],
                         ["Meri bhi sun lo!", "Busy hoon, baad mein.", "Khaana kidhar hai?"])
        references = validate_references((root / "references.json").read_bytes(), count=3)
        self.assertTrue(all(ref["media_credit"]["license"] == "CC0" for ref in references))
        self.assertEqual(len({ref["media_credit"]["source_sha256"] for ref in references}), 3)


if __name__ == "__main__":
    unittest.main()
