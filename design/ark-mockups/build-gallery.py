from pathlib import Path
import base64,json

ROOT=Path(__file__).resolve().parent
PROJECT=ROOT.parent.parent

def build():
    concepts=[]
    for name in ['concepts-01-04.json','concepts-05-07.json','concepts-08-10.json']:
        concepts.extend(json.loads((ROOT/name).read_text()))
    concepts.sort(key=lambda c:c['id'])
    assert [c['id'] for c in concepts]==[f'{i:02}' for i in range(1,11)]
    for c in concepts:
        assert len(c['palette'])>=4
        assert f'm{c["id"]}' in c['html']
        assert '<script' not in c['html'].lower()
    fonts=''
    for name,filename,weight in [('Ark Serif','source-serif-4-latin-opsz-normal.woff2','200 900'),('Ark Mono','ibm-plex-mono-latin-400-normal.woff2','400')]:
        encoded=base64.b64encode((PROJECT/'site/public/fonts'/filename).read_bytes()).decode()
        fonts+=f"@font-face{{font-family:'{name}';font-style:normal;font-weight:{weight};font-display:swap;src:url(data:font/woff2;base64,{encoded}) format('woff2');}}\n"
    css='\n'.join(c['css'] for c in concepts)
    page=TEMPLATE.replace('__FONTS__',fonts).replace('__CONCEPT_CSS__',css).replace('__CONCEPT_DATA__',json.dumps(concepts,ensure_ascii=False).replace('</','<\\/'))
    (ROOT/'index.html').write_text(page)
    lines=['# Estudely Ark: ten Android design directions','',
           'Created using Claude’s frontend-design skill. These are visual concepts, not changes to the Android app. Download, reading and storage states are illustrative. No account, ads or AI features are proposed.','',
           '## Shared brief','',
           'Make the existing library readable and inviting. Put reading first, separate downloaded subjects from available subjects, make module size and download state easy to scan, and give storage and Wi-Fi controls a clear home. Keep all current functional capabilities.','',
           'The existing screen puts long descriptions, checkboxes, logs, storage, downloads, and sharing together. These concepts explore different hierarchies and compositions rather than ten recolors.','',
           '## Directions','']
    for c in concepts:
        lines.extend([f"### {c['id']} — {c['name']}",'',c['tagline'],'',c['rationale'],'',f"Palette: {', '.join(c['palette'])}. Typography: {c['font']}.",'',c.get('designNote',''),''])
    lines.extend(['## Review','',
                  'Open index.html for the interactive gallery. Select any of ten directions, use the previous/next controls, compare all concepts, or shortlist favorites. The gallery is self-contained and works without internet. The underlying concept JSON and this build script make each direction editable.',''])
    (ROOT/'DESIGN-NOTES.md').write_text('\n'.join(lines))
    # Individual screens and a full contact sheet share the gallery's embedded fonts.
    (ROOT/'screens').mkdir(exist_ok=True)
    for c in concepts:
        name=c['id']+'-'+c['name'].lower().replace(' ','-')+'.html'
        screen='<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>'+c['name']+' — Estudely Ark</title><style>'+fonts+'html,body{margin:0;padding:0}.mock{width:360px;height:760px;overflow:hidden}'+c['css']+'</style></head><body>'+c['html']+'</body></html>'
        (ROOT/'screens'/name).write_text(screen)
    contact="<style>.gallery{max-width:none}.toolbar,.choices,.scene,.pager,.caption,.shortlist{display:none!important}.compare-grid{display:grid!important;grid-template-columns:repeat(5,minmax(0,1fr));gap:22px 10px}.compare-card .device-slot{--scale:.78}.compare-caption{max-width:282px}</style><script>document.getElementById('compare').click();</script>"
    (ROOT/'contact-sheet.html').write_text(page.replace('</body>',contact+'</body>'))
    print('Built 10-concept gallery:',ROOT/'index.html')

TEMPLATE=r'''<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Estudely Ark — Android design concepts</title>
<style>
__FONTS__
*{box-sizing:border-box}html,body{margin:0;padding:0}body{font-family:var(--font-sans,'Avenir Next',Arial,sans-serif);color:var(--foreground,#243045)}button{font:inherit;cursor:pointer}button:focus-visible{outline:3px solid var(--ring,#4c75ce);outline-offset:3px}button:disabled{cursor:default;opacity:.5}
.gallery{max-width:1000px;margin:0 auto}.toolbar{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:5px 0 16px}.toolbar span{font-size:13px;color:var(--muted-foreground,#68758a)}.control{border:1px solid var(--border,#d8dee7);border-radius:9px;background:var(--secondary,#f3f5f9);color:var(--foreground,#243045);min-height:39px;padding:8px 12px;font-size:12px;font-weight:600}.control[aria-pressed=true]{background:var(--primary,#254da2);color:var(--primary-foreground,#fff);border-color:transparent}.choices{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:7px;padding-bottom:23px}.choice{display:flex;align-items:center;gap:7px;text-align:left;min-height:47px;padding:8px;border:1px solid var(--border,#d8dee7);border-radius:9px;background:var(--card,#fff);color:var(--foreground,#243045);font-size:11px;font-weight:600}.choice[aria-pressed=true]{border-color:var(--primary,#254da2);box-shadow:0 0 0 1px var(--primary,#254da2)}.choice .num{width:25px;height:25px;border-radius:50%;display:grid;place-items:center;flex:none;font-size:9px;font-weight:700}.choice-name{line-height:1.25}.scene{display:grid;grid-template-columns:360px minmax(0,1fr);gap:30px;align-items:start;justify-content:center}.device-slot{--scale:1;width:calc(360px * var(--scale));height:calc(760px * var(--scale));position:relative;justify-self:center}.device{width:360px;height:760px;border-radius:27px;overflow:hidden;transform:scale(var(--scale));transform-origin:top left;outline:1px solid rgba(120,132,153,.3);box-shadow:0 12px 30px rgba(12,25,45,.12)}.mock{width:360px;height:760px;overflow:hidden;position:relative}.mock svg{flex-shrink:0}.mock button{font:inherit}.direction{padding-top:32px}.direction .count{font-size:12px;color:var(--muted-foreground,#68758a);margin:0 0 11px}.direction h2{font-size:29px;font-weight:650;letter-spacing:-1px;line-height:1.12;margin:0 0 14px}.direction .tagline{font-size:15px;line-height:1.5;margin:0 0 20px}.direction .rationale{font-size:13px;line-height:1.7;color:var(--muted-foreground,#68758a);margin:0 0 24px}.swatches{display:flex;gap:7px;margin-bottom:16px}.swatch{width:26px;height:26px;border-radius:50%;box-shadow:inset 0 0 0 1px rgba(100,110,125,.18)}.type-note{font-size:11px;line-height:1.6;color:var(--muted-foreground,#68758a);margin:0 0 25px}.direction .control{width:100%;margin-bottom:8px}.pager{display:flex;justify-content:center;align-items:center;gap:12px;padding:22px 0 12px}.pager .control{min-width:85px}.pager span{font-size:12px;color:var(--muted-foreground,#68758a)}.caption{font-size:11px;color:var(--muted-foreground,#68758a);text-align:center;line-height:1.6;margin:0 auto 8px;max-width:540px}.compare-grid{display:none;grid-template-columns:repeat(2,minmax(0,1fr));gap:28px 12px}.compare-card{display:flex;flex-direction:column;align-items:center;min-width:0}.compare-card>button{border:0;color:var(--foreground,#243045);background:transparent;text-align:left;align-self:stretch;padding:0 5px 12px;font-size:13px;font-weight:650;min-height:40px}.compare-card .device-slot{--scale:.84}.compare-card .device{pointer-events:none}.compare-caption{font-size:11px;color:var(--muted-foreground,#68758a);line-height:1.5;padding:13px 6px 0;max-width:300px;margin:0}.gallery.comparing .compare-grid{display:grid}.gallery.comparing .scene,.gallery.comparing .pager{display:none}.gallery.comparing .caption{margin-top:20px}.shortlist{font-size:12px;color:var(--muted-foreground,#68758a);text-align:center;min-height:20px;margin:10px 0}
@media(max-width:660px){.scene{grid-template-columns:minmax(0,1fr);gap:8px}.direction{padding:12px 2px 0}.direction h2{font-size:23px;margin-bottom:8px}.direction .rationale{margin-bottom:13px}.direction .tagline{margin-bottom:9px}.direction .type-note{margin-bottom:12px}.direction .swatches{float:right}.direction .control{width:auto;min-width:140px}.choices{grid-template-columns:repeat(5,minmax(0,1fr));gap:5px}.choice{flex-direction:column;justify-content:center;text-align:center;gap:5px;font-size:9px;padding:7px 3px}.choice .num{width:22px;height:22px}.compare-card .device-slot{--scale:.45}.compare-grid{gap:20px 5px}.compare-card>button{font-size:11px;padding:0 2px 8px}.compare-caption{font-size:10px}.toolbar{padding-bottom:12px}.toolbar span{font-size:12px}}
@media(max-width:390px){.scene>.device-slot{--scale:.9}.compare-card .device-slot{--scale:.42}.toolbar .control{font-size:11px;padding:8px}.choice-name{max-width:57px}.direction h2{font-size:24px}}
@media(prefers-reduced-motion:reduce){*{scroll-behavior:auto!important;transition:none!important;animation:none!important}}
__CONCEPT_CSS__
</style></head><body><main class="gallery" id="gallery">
<div class="toolbar"><span>10 directions for Estudely Ark</span><button class="control" id="compare" aria-pressed="false">Compare all 10</button></div>
<nav class="choices" id="choices" aria-label="Choose an app mockup"></nav>
<div class="scene" id="scene"><div class="device-slot"><div class="device" id="device"></div></div><aside class="direction" id="direction" aria-live="polite"></aside></div>
<div class="compare-grid" id="compare-grid"></div>
<div class="pager"><button class="control" id="prev" aria-label="Previous mockup">Previous</button><span id="position"></span><button class="control" id="next" aria-label="Next mockup">Next</button></div>
<p class="caption">Visual concepts for the Android app. Library contents and download states are examples. Select a direction to inspect it, or compare all ten.</p><p class="shortlist" id="shortlist" aria-live="polite"></p>
</main><script>
const concepts=__CONCEPT_DATA__;
let selected=0;let comparing=false;const favorites=new Set();
const el=function(id){return document.getElementById(id)};
function esc(s){return String(s).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]})}
function refresh(){
 document.querySelectorAll('.choice').forEach(function(b,i){b.setAttribute('aria-pressed',String(i===selected));});
 const c=concepts[selected];el('device').innerHTML=c.html;el('position').textContent=c.id+' / 10';
 el('direction').innerHTML='<p class="count">Direction '+c.id+' of 10</p><h2>'+esc(c.name)+'</h2><p class="tagline">'+esc(c.tagline)+'</p><p class="rationale">'+esc(c.rationale)+'</p><div class="swatches" aria-label="Color palette">'+c.palette.map(function(p){return '<span class="swatch" style="background:'+p+'" title="'+p+'"></span>'}).join('')+'</div><p class="type-note">'+esc(c.font)+'</p><button class="control" id="favorite" aria-pressed="'+favorites.has(c.id)+'">'+(favorites.has(c.id)?'Shortlisted':'Shortlist this direction')+'</button>';
 el('favorite').onclick=function(){if(favorites.has(c.id)){favorites.delete(c.id)}else{favorites.add(c.id)}refresh()};
 el('shortlist').textContent=favorites.size?'Your shortlist: '+concepts.filter(function(x){return favorites.has(x.id)}).map(function(x){return x.id+' '+x.name}).join(', '):'';
}
function select(i){selected=(i+10)%10;if(comparing){comparing=false;el('gallery').classList.remove('comparing');el('compare').setAttribute('aria-pressed','false');el('compare').textContent='Compare all 10'}refresh()}
el('choices').innerHTML=concepts.map(function(c,i){return '<button class="choice" aria-pressed="false" data-index="'+i+'"><span class="num" style="background:var(--secondary,#eef1f5);color:var(--foreground,#243045);border-bottom:3px solid '+c.palette[2]+'">'+c.id+'</span><span class="choice-name">'+esc(c.name)+'</span></button>'}).join('');
el('choices').addEventListener('click',function(e){const b=e.target.closest('[data-index]');if(b)select(Number(b.dataset.index))});
el('prev').onclick=function(){select(selected-1)};el('next').onclick=function(){select(selected+1)};
el('compare').onclick=function(){comparing=!comparing;el('gallery').classList.toggle('comparing',comparing);this.setAttribute('aria-pressed',String(comparing));this.textContent=comparing?'Back to selected concept':'Compare all 10';if(comparing&&!el('compare-grid').children.length){el('compare-grid').innerHTML=concepts.map(function(c,i){return '<section class="compare-card"><button data-index="'+i+'">'+c.id+' '+esc(c.name)+' · inspect</button><div class="device-slot"><div class="device">'+c.html+'</div></div><p class="compare-caption">'+esc(c.tagline)+'</p></section>'}).join('')}};
el('compare-grid').addEventListener('click',function(e){const b=e.target.closest('[data-index]');if(b)select(Number(b.dataset.index))});
document.addEventListener('keydown',function(e){if(e.target.matches('input,textarea,select'))return;if(e.key==='ArrowRight'){e.preventDefault();select(selected+1)}if(e.key==='ArrowLeft'){e.preventDefault();select(selected-1)}});
refresh();
</script></body></html>'''

if __name__=='__main__':build()
