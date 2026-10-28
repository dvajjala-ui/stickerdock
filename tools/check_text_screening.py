"""Real local OCR acceptance. No network or AI calls; fails if OCR is unavailable."""
from pathlib import Path
import tempfile
from PIL import Image, ImageDraw, ImageFont
from source_text_screening import source_has_text
from sticker_fonts import default_font


def main():
    with tempfile.TemporaryDirectory() as temporary:
        root=Path(temporary)
        font=ImageFont.truetype(str(default_font()),68)
        for mode,outlined in (('RGB',False),('RGBA',True)):
            image=Image.new(mode,(1000,700),'#223a52')
            draw=ImageDraw.Draw(image)
            draw.ellipse((200,80,800,520),fill='#e4a256')
            draw.text((500,590),'NOT TODAY',font=font,anchor='mm',
                fill='white',stroke_fill='black',stroke_width=4 if outlined else 0)
            path=root/(mode+'.png');image.save(path)
            assert source_has_text(path), 'Missed embedded caption in '+mode
        clean=Image.new('RGB',(1000,700),'#223a52')
        ImageDraw.Draw(clean).ellipse((200,80,800,520),fill='#e4a256')
        path=root/'no-text.png';clean.save(path)
        assert not source_has_text(path), 'Rejected clean source'
    print('Real Tesseract screening: opaque RGB/RGBA captions, outlined text and clean image passed')


if __name__=='__main__':
    main()
