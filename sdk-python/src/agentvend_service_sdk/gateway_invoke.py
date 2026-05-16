"""Gateway service invoke (sync/async). See ``docs-sdk/MAIN-SDK-API-SPEC.md`` §1.1–1.2."""

from __future__ import annotations

import json
from dataclasses import dataclass
from typing import TYPE_CHECKING, Any, Dict, Optional

from .gateway_client import DEFAULT_GATEWAY_PATH_PREFIX, _build_url

if TYPE_CHECKING:
    import requests


@dataclass
class GatewayInvokeAsyncEnvelope:
    request_id: str
    callback_url: str
    progress_url: str


@dataclass
class GatewayInvokeResult:
    status_code: int
    body: str
    async_envelope: Optional[GatewayInvokeAsyncEnvelope] = None


def invoke_service(
    gateway_base_url: str,
    method: str,
    service_id: str,
    endpoint_id: str,
    service_key: str,
    *,
    body: Optional[str] = None,
    async_: bool = False,
    gateway_path_prefix: str = DEFAULT_GATEWAY_PATH_PREFIX,
    session: Optional["requests.Session"] = None,
) -> Optional[GatewayInvokeResult]:
    """Invoke gateway ``…/service/{serviceId}/endpoint/{endpointId}/invoke`` (or ``…/invoke/async``). Requires ``requests``."""
    try:
        import requests
    except ImportError:
        raise ImportError("invoke_service requires 'requests'. pip install requests")
    suffix = f"/service/{service_id}/endpoint/{endpoint_id}/invoke"
    if async_:
        suffix += "/async"
    url = _build_url(gateway_base_url, gateway_path_prefix, suffix)
    m = (method or "GET").strip().upper()
    headers: Dict[str, str] = {"Authorization": f"Bearer {service_key}"}
    payload = body or None
    if payload and m in ("POST", "PUT"):
        headers["Content-Type"] = "application/json"
    sess = session or requests.Session()
    try:
        resp = sess.request(m, url, data=payload, headers=headers, timeout=60)
    except OSError:
        return None
    text = resp.text or ""
    env: Optional[GatewayInvokeAsyncEnvelope] = None
    if resp.status_code == 202 and text.strip():
        try:
            j = json.loads(text)
            if isinstance(j, dict) and j.get("requestId"):
                env = GatewayInvokeAsyncEnvelope(
                    request_id=str(j.get("requestId", "")),
                    callback_url=str(j.get("callbackUrl", "") or ""),
                    progress_url=str(j.get("progressUrl", "") or ""),
                )
        except (json.JSONDecodeError, TypeError, ValueError):
            pass
    return GatewayInvokeResult(status_code=resp.status_code, body=text, async_envelope=env)
