"""Resolve ECS vs Docker path prefixes from API origin (parity with sdk-js)."""

from __future__ import annotations

from typing import Optional
from urllib.parse import urlparse

DEFAULT_API_URL = "https://api.tollara.ai"
DEFAULT_CORE_PATH_PREFIX = "/api/v1"
DEFAULT_GATEWAY_PATH_PREFIX = "/api"
DEFAULT_USAGE_PATH_PREFIX = "/api/usage"

ECS_CORE_PATH_PREFIX = "/core/api/v1"
ECS_GATEWAY_PATH_PREFIX = "/gateway/api/v1"
ECS_USAGE_PATH_PREFIX = "/usage/api/v1"


def _resolve_origin(base_url: Optional[str]) -> str:
    if base_url is not None and base_url.strip():
        return base_url.strip().rstrip("/")
    return DEFAULT_API_URL


def is_hosted_tollara_api_origin(origin: str) -> bool:
    try:
        host = (urlparse(origin).hostname or "").lower()
    except ValueError:
        return False
    if host == "api.tollara.ai" or host.endswith(".api.tollara.ai"):
        return True
    return host == "api.ppe.tollara.ai" or host.endswith(".api.ppe.tollara.ai")


def resolve_gateway_path_prefix(
    base_url: Optional[str] = None,
    override: Optional[str] = None,
) -> str:
    if override is not None and override.strip():
        return override.strip()
    origin = _resolve_origin(base_url)
    return ECS_GATEWAY_PATH_PREFIX if is_hosted_tollara_api_origin(origin) else DEFAULT_GATEWAY_PATH_PREFIX


def resolve_core_path_prefix(
    base_url: Optional[str] = None,
    override: Optional[str] = None,
) -> str:
    if override is not None and override.strip():
        return override.strip()
    origin = _resolve_origin(base_url)
    return ECS_CORE_PATH_PREFIX if is_hosted_tollara_api_origin(origin) else DEFAULT_CORE_PATH_PREFIX


def resolve_usage_path_prefix(
    base_url: Optional[str] = None,
    override: Optional[str] = None,
) -> str:
    if override is not None and override.strip():
        return override.strip()
    origin = _resolve_origin(base_url)
    return ECS_USAGE_PATH_PREFIX if is_hosted_tollara_api_origin(origin) else DEFAULT_USAGE_PATH_PREFIX
