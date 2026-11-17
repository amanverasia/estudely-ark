from pathlib import Path
import base64
root=Path(__file__).resolve().parent
font=base64.b64encode((root.parent.parent/'site/public/fonts/source-serif-4-latin-opsz-normal.woff2').read_bytes()).decode()
s=(root/'source.html').read_text().replace('__SERIF_FONT__',font)
(root/'index.html').write_text(s)
print(root/'index.html')
