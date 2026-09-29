# Brave Search API (for TASK-070)

Retrieved 2026-09-29 with curl:

- `brave-api-home.html`: https://brave.com/search/api/ — plans: Search $5 per 1,000 requests,
  $5 of free credit each month, 50 queries per second; auth header `X-Subscription-Token`;
  sign-up at https://api-dashboard.search.brave.com/register.
- `documentation_services_web-search.html`: https://api-dashboard.search.brave.com/documentation/services/web-search
  — `GET https://api.search.brave.com/res/v1/web/search?q=...`, results for human consumption; it
  points agents and chatbots to LLM Context.
- `documentation_services_llm-context.html`: https://api-dashboard.search.brave.com/documentation/services/llm-context
  — `GET/POST https://api.search.brave.com/res/v1/llm/context`, `q` 1–600 characters,
  `maximum_number_of_tokens` 1024–32768 (default 8192), `maximum_number_of_urls` 1–50 (default 20).
  Response: `grounding.generic[]` of `{url, title, snippets[]}`, plus `sources[url]` metadata.
- `terms-of-service.html`: https://api-dashboard.search.brave.com/documentation/resources/terms-of-service
  (served under the dashboard path) — the customer must not "store, cache, or create a database of
  Search Results … other than transient storage required for operation", nor redistribute them or
  use them to train or evaluate AI models. Attribution ("POWERED BY BRAVE") is optional.

Decisions (owner, 2026-09-29): Bruce uses LLM Context; search results are saved with the chat like
other skill results, although the terms above restrict storage (the user holds their own key and
agreement with Brave).
