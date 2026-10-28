import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile
from unittest.mock import patch
from PIL import Image
import source_text_screening as screening

HEADER='level\tconf\ttext\n'


class SourceTextScreeningTest(unittest.TestCase):
    def test_production_audit_only_downloads_snapshot_and_does_not_save_private_text(self):
        from audit_factory_source_text import audit
        image=io.BytesIO();Image.new('RGB',(80,80),'blue').save(image,'PNG')
        archive=io.BytesIO()
        with zipfile.ZipFile(archive,'w') as target:
            target.writestr('reference-images/source.png',image.getvalue())
        class ReadOnlyClient:
            def download(self,bucket,path,missing_ok=False):
                return archive.getvalue()
        with patch.object(screening,'source_has_text',return_value=True):
            result=audit(ReadOnlyClient())
        self.assertEqual((result['screened'],result['withheld'],result['storage_writes'],result['generation_calls']),
                         (1,1,0,0))
        self.assertEqual(result['withheld_sha256'],[screening.hashlib.sha256(image.getvalue()).hexdigest()])
        self.assertNotIn('source.png',json.dumps(result))

    def test_confident_words_withhold_source_but_noise_does_not(self):
        self.assertTrue(screening.has_confident_text(HEADER+'5\t92\tNOT\n5\t87\tTODAY\n'))
        self.assertTrue(screening.has_confident_text(HEADER+'5\t90\tWHATEVER\n'))
        self.assertFalse(screening.has_confident_text(HEADER+'5\t20\tNOT\n5\t30\tTODAY\n'))
        self.assertFalse(screening.has_confident_text(HEADER+'5\t98\tI\n5\t90\t12\n'))
        self.assertFalse(screening.has_confident_text(HEADER))
        with self.assertRaisesRegex(ValueError,'invalid output'):
            screening.has_confident_text('unexpected tool output')

    def test_opaque_alpha_sources_are_screened_and_pixels_stay_local(self):
        with tempfile.TemporaryDirectory() as temporary:
            path=Path(temporary)/'source.png'
            Image.new('RGBA',(500,400),'navy').save(path)
            with patch.object(screening.shutil,'which',return_value='tesseract'), \
                    patch.object(screening.subprocess,'run',return_value=subprocess.CompletedProcess([],0,
                        (HEADER+'5\t95\tNOT\n5\t95\tTODAY\n').encode())) as run:
                self.assertTrue(screening.source_has_text(path))
            call=run.call_args
            self.assertEqual(call.args[0],['tesseract','stdin','stdout','-l','eng','--psm','11','tsv'])
            self.assertEqual(call.kwargs['timeout'],15)
            self.assertNotIn('shell',call.kwargs)
            self.assertLessEqual(max(Image.open(io.BytesIO(call.kwargs['input'])).size),1200)

    def test_missing_engine_and_timeout_never_clear_a_source(self):
        with patch.object(screening.shutil,'which',return_value=None):
            with self.assertRaisesRegex(ValueError,'require local Tesseract'):
                screening.source_has_text(Path('not-read.png'))
        with tempfile.TemporaryDirectory() as temporary:
            path=Path(temporary)/'source.png';Image.new('RGB',(100,100),'blue').save(path)
            with patch.object(screening.shutil,'which',return_value='tesseract'), \
                    patch.object(screening.subprocess,'run',side_effect=subprocess.TimeoutExpired('tesseract',15)):
                with self.assertRaisesRegex(ValueError,'not cleared'):
                    screening.source_has_text(path)

    def test_digest_cache_survives_rename_and_invalidates_when_pixels_change(self):
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary);path=root/'source.png';cache=root/'screening.json'
            Image.new('RGB',(100,100),'blue').save(path)
            with patch.object(screening,'source_has_text',return_value=True) as ocr:
                self.assertEqual(screening.screen_sources([path],cache),[path])
                renamed=root/'renamed.png';path.rename(renamed)
                self.assertEqual(screening.screen_sources([renamed],cache),[renamed])
                ocr.assert_called_once()
            Image.new('RGB',(100,100),'red').save(renamed)
            with patch.object(screening,'source_has_text',return_value=False) as ocr:
                self.assertEqual(screening.screen_sources([renamed],cache),[])
                ocr.assert_called_once()
            self.assertTrue(all(type(value) is bool for value in json.loads(cache.read_text()).values()))
            self.assertNotIn('recognized',cache.read_text())
