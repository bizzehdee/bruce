# Free web search providers (owner, 2026-10-02: Brave is paid)

Retrieved 2026-10-02 with curl (pages) and WebFetch (reading). Question: which search can Bruce
offer that is free to the user, without a payment card?

| Provider | Free terms (as published) | Key | Card | Index | File |
|---|---|---|---|---|---|
| Brave Search API | "$5 per 1,000 requests", "Includes $5 in free credits every month"; FAQ: "For free plans, the card is only used to confirm your identity and will not be charged" | yes | **required** | general web | `brave-api-home.html` (https://brave.com/search/api/) |
| LangSearch | "Every Web Search feature. Free." "$0 USD / month"; daily token allowance (resets 00:00 UTC), 5 requests a second for new accounts; no card mentioned | yes (free sign-up) | no | general web | `langsearch-home.html` (https://langsearch.com/) |
| Tavily | "1,000 API credits / month", "No credit card required"; credit cost per search not stated on the page | yes (free sign-up) | no | general web | `tavily-pricing.html` (https://www.tavily.com/pricing) |
| Marginalia Search | key `public` without sign-up, "often hits a rate limit"; free non-commercial key by email; results CC-BY-NC-SA 4.0; `api2.marginalia-search.com`, header `API-Key`; JSON `results[]` of `url`, `title`, `description` | `public` or emailed | no | small independent web only | `marginalia-api.html` (https://about.marginalia-search.com/article/api/) |
| SearXNG (the user's own server) | free software; `GET /search?q=…&format=json`; JSON is off by default and enabled per instance in `search.formats`, so most public instances refuse it (403) | none | no | aggregates other engines | `searxng-search-api.html`, `searxng-settings-search.html` (https://docs.searxng.org/dev/search_api.html, https://docs.searxng.org/admin/settings/settings_search.html) |
| Wikipedia (MediaWiki REST) | no key, no sign-up; `GET https://<lang>.wikipedia.org/w/rest.php/v1/search/page?q=…&limit=…` returns `pages[]` of `key`, `title`, `excerpt` (with `<span class="searchmatch">` markup), `description`; a User-Agent identifying the client is expected | none | no | Wikipedia only | `wikipedia-rest-search.html` (https://api.wikimedia.org/wiki/Core_REST_API/Reference/Search/Search_content), `wikipedia-search-sample.json` (live call) |

DuckDuckGo publishes no web search API (only Instant Answers); scraping its result pages is not
an option.

Extract for Bruce: Wikipedia needs nothing from the user and covers factual questions; LangSearch
and Tavily are the free general-web APIs that need only a sign-up; the user's own SearXNG server
fits Bruce's "your own server" pattern (as remote model servers do) with no key. Commercial-use
and result-storage terms of LangSearch and Tavily were not on these pages and must be read before
either is built.

## Scraping a search engine's result pages (owner asked, 2026-10-02)

- DuckDuckGo: `robots.txt` (`duckduckgo-robots.txt`) has `User-agent: *` `Disallow: /lite` and
  `Disallow: /html`, the two result pages that can be read without JavaScript. Its Acceptable Use
  Policy (`duckduckgo-acceptable-use.html`, https://duckduckgo.com/acceptable-use) forbids to
  "Frame, inline link, or similarly display any portion of the services within another service".
- Google: `robots.txt` (`google-robots.txt`) disallows `/search`; its terms (`google-terms.html`,
  https://policies.google.com/terms) forbid "using automated means to access content from any of our
  services in violation of the machine-readable instructions on our web pages (for example,
  robots.txt files…)".

Extract: reading search engines' result pages from the app goes against their published rules,
breaks whenever the page layout changes, and is blocked with challenges; it is not an option for an
app published on Google Play.

## LangSearch, read before building (TASK-113, 2026-10-02)

- API (`langsearch-web-search-api.html`, https://docs.langsearch.com/api/web-search-api):
  `POST https://api.langsearch.com/v1/web-search`, header `Authorization: Bearer <key>`, JSON body
  `query` (non-empty), `count` (1–50, default 10; above 50 rejected), `freshness`, `includeDomains`,
  `excludeDomains`, `contents.text` (true or `{maxCharacters}`; full page text, 5,000 characters by
  default, returned as `text` instead of `snippet`). Response: `code`, `log_id`, `msg`,
  `data.webPages.value[]` of `id`, `name`, `url`, `displayUrl`, `snippet` or `text`,
  `datePublished`; `usage.input_tokens`, `output_tokens`. "All supported Web Search features are
  included in the Free Plan, subject to your account allowance." "Fewer results, including zero, are valid."
- Terms (`langsearch-terms-of-service.html`, https://langsearch.com/terms-of-service, effective
  2024-12-19): non-exclusive revocable licence; prohibited: "Scraping, mining, or harvesting data
  from LangSearch Services", disruption, harming its reputation; "LangSearch provides the Web Search
  API on a free-access basis" with no guarantee; users 18+ or with consent; US law. Nothing on
  storing results or commercial use.
- Privacy (`langsearch-privacy-policy.html`, https://langsearch.com/privacy-policy): collects usage
  data "including search queries, API requests, and usage patterns" and technical data (IP address,
  device information), used "to provide and improve" the service and to analyse usage.

Extract: Bruce may use LangSearch with the user's own key; results kept with the chat are not
restricted; the privacy policy must say LangSearch logs the search text and IP address.
