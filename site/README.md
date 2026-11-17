# ark.esdy.cc

Static page for Estudely Ark, served by Cloudflare Workers static assets.

```sh
cp release/Estudely-Ark-v1.0-2026-11.torrent site/public/   # git-ignored; the page links to it
cd site && CLOUDFLARE_API_TOKEN=... npx wrangler@4 deploy
```
