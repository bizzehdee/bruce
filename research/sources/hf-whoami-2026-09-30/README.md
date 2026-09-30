# Hugging Face token check (for TASK-079)

Retrieved 2026-09-30 with curl:

- `hf_api.py`: https://raw.githubusercontent.com/huggingface/huggingface_hub/main/src/huggingface_hub/hf_api.py
  — `whoami` calls `GET {endpoint}/api/whoami-v2` with `Authorization: Bearer <token>`; 401 means
  an invalid token.
- `_login.py`: https://raw.githubusercontent.com/huggingface/huggingface_hub/main/src/huggingface_hub/_login.py
  — the response's `name` is the username; `auth.accessToken.role` is the token's role;
  organisation tokens start with `api_org` and are refused for login.
- `security-tokens.md`: https://raw.githubusercontent.com/huggingface/hub-docs/main/docs/hub/security-tokens.md
  — roles `read`, `write` and `fine-grained`; a write token can push to the user's repos.

Checked: `curl https://huggingface.co/api/whoami-v2` without a token returns 401.

Decision (2026-09-30, secure default): Bruce only downloads, so it refuses `write` tokens and asks
for a read or fine-grained one.
