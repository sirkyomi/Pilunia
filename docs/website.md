# Pilunia auf GitHub Pages

Die Website liegt vollständig in `website/`. Sie benötigt weder npm noch einen Build.
HTML und CSS setzen die aktualisierte Seite „Landing Page · Issue 2“ aus
`Pilunia.leinwand` um, einschließlich der separaten Lizenzseite.

## Lokal ansehen

Vom Repository-Root:

```sh
python -m http.server 8080 --directory website
```

Danach <http://localhost:8080> öffnen. Die Website funktioniert auch unter einem
Projektpfad wie `/Pilunia/`, da alle internen URLs relativ sind.

Vor dem Veröffentlichen die lokalen Links, SVG-Verweise und Assets prüfen:

```sh
node scripts/check-website.mjs
```

## Veröffentlichen

1. Die Website und `.github/workflows/pages.yml` nach `main` übernehmen und pushen.
2. Im Repository unter **Settings → Pages → Build and deployment → Source**
   **GitHub Actions** auswählen.
3. Unter **Custom domain** `pilunia.regbr.de` eintragen. Beim DNS-Anbieter den
   CNAME für `pilunia` auf `sirkyomi.github.io` setzen. Wenn die Domain bereits
   konfiguriert ist, die vorhandenen Einstellungen beibehalten.
4. Nach erfolgreicher Domain-Prüfung **Enforce HTTPS** aktivieren.
5. Der Workflow „Publish Pilunia website“ veröffentlicht `website/` bei Änderungen
   auf `main`. Nach erstmaliger Einrichtung lässt er sich unter **Actions →
   Publish Pilunia website → Run workflow** auch manuell starten.

GitHub Pages beachtet bei einem Actions-Workflow die Domain in den
Repository-Einstellungen; `website/CNAME` dokumentiert dieselbe Domain und
unterstützt eine spätere Veröffentlichung aus einem Branch.

Ohne Custom Domain ist die Website unter <https://sirkyomi.github.io/Pilunia/>
erreichbar. Mit der konfigurierten Domain: <https://pilunia.regbr.de>.
Die Download-Buttons führen wie im Design zu
<https://github.com/sirkyomi/Pilunia/releases/latest>.

Offizielle Anleitung: <https://docs.github.com/en/pages/getting-started-with-github-pages/using-custom-workflows-with-github-pages>.

## Assets und Pflege

- Schriften werden lokal aus `website/assets/fonts` geladen; keine externen
  Font-Dienste, Cookies oder Analyse-Skripte.
  Atkinson verwendet dieselbe angepasste Schriftdatei wie die App auf `main`:
  Die Null hat in allen Schriftstärken keinen Schrägstrich.
- Figuren und Screenshots stammen aus `docs/char` und `docs/screenshots` und sind
  für die Website in WebP umgewandelt.
- `website/assets/icons.svg` enthält die verwendeten Lucide-Symbole.
- Die mitgelieferten vollständigen Schrift- und Symbollizenzen stehen unter
  `website/assets/licenses`. Die Website verlinkt sie auf `lizenzen.html`.
- Quellenhinweise: `website/assets/CREDITS.txt`.
- `website/styles.css` enthält die Pilunia-Tokens und die responsiven Layouts.
- Die Lizenzseite und der Copyright-Link entsprechen dem zuletzt geänderten
  Design. Impressum und Website-Datenschutzerklärung sind im aktuellen Design
  nicht enthalten.

Zum Austausch eines App-Screenshots das entsprechende Bild in
`website/assets/images` ersetzen; zugehörige Bildmaße und Alt-Texte bei einer
Formatänderung in den HTML-Dateien mit anpassen.
