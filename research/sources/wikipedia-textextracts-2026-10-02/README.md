# Wikipedia article text through TextExtracts (retrieved 2026-10-02)

- `extension-textextracts.wikitext`: https://www.mediawiki.org/w/index.php?title=Extension:TextExtracts&action=raw
- `api-help-query-extracts.html`: https://en.wikipedia.org/w/api.php?action=help&modules=query%2Bextracts
- `sample-october-2.json`: https://en.wikipedia.org/w/api.php?action=query&prop=extracts&explaintext=1&exsectionformat=wiki&redirects=1&format=json&formatversion=2&titles=October_2

Extract:

- `prop=extracts` "Returns plain-text or limited HTML extracts of the given pages". `explaintext`
  gives plain text; `exsectionformat=wiki` (the default) marks sections as `== like this ==`.
- `exlimit`: "Multiple extracts can only be returned if exintro is set to true", so one title gets
  the whole article.
- Caveats: inline images are stripped; in plain-text mode "citations may not be stripped".
- `redirects=1` follows redirects (checked: `Caesar` gives `Julius Caesar` and a `redirects` list).
  A missing title gives `"missing": true` and no `extract`.
- October 2 as an extract is 20,813 characters starting with the lead sentence and the Events list;
  the same page as HTML text starts with about 2,000 characters of calendar tables.
