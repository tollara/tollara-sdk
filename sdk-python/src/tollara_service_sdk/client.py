"""Unified HTTP client: env-based config and URL layout aligned with Java `TollaraClient`."""

from __future__ import annotations

import os
from typing import TYPE_CHECKING, Optional

from .completion_status import CompletionStatus
from .billing_client import estimate_usage_with_jwt as core_estimate_usage_with_jwt
from .gateway_client import (
    DEFAULT_GATEWAY_PATH_PREFIX,
    GatewayPollResult,
    get_request_result,
    get_request_status,
)
from .gateway_invoke import GatewayInvokeResult, invoke_service as gateway_invoke_service
from .usage_client import (
    DEFAULT_USAGE_PATH_PREFIX,
    report_completion,
    report_completion_full,
    report_progress,
    report_usage,
    report_usage_at,
    UsageReportResponse,
)
from .validation_client import (
    DEFAULT_CORE_PATH_PREFIX,
    ServiceKeyValidationResult,
    UsageEstimateResult,
    estimate_usage,
    validate_service_key,
)

if TYPE_CHECKING:
    import requests

ENV_API_URL = "TOLLARA_API_URL"
ENV_SERVICE_ID = "TOLLARA_SERVICE_ID"
ENV_SERVICE_SECRET = "TOLLARA_SERVICE_SECRET"

# Production API origin; override with `api_url=...` or `TOLLARA_API_URL` for tests/staging.
DEFAULT_API_URL = "https://api.tollara.ai"


def _trim_trailing_slashes(s: str) -> str:
    t = s.strip()
    while t.endswith("/"):
        t = t[:-1]
    return t


def _join_url(base: str, path: Optional[str]) -> str:
    b = _trim_trailing_slashes(base)
    if not path:
        return b
    p = path if path.startswith("/") else f"/{path}"
    return b + p


def _first_non_blank(a: Optional[str], b: Optional[str]) -> str:
    if a is not None and a.strip():
        return a.strip()
    if b is not None and b.strip():
        return b.strip()
    return ""


def _resolve_api_url(api_url: Optional[str]) -> str:
    if api_url is not None and api_url.strip():
        return _trim_trailing_slashes(api_url.strip())
    env = os.environ.get(ENV_API_URL)
    if env is not None and env.strip():
        return _trim_trailing_slashes(env.strip())
    return DEFAULT_API_URL


class TollaraClient:
    """
    Single entry point for Core validate, Usage report/progress/complete, and Gateway polling.
    Explicit constructor arguments override environment variables (same order as Java `Builder`).
    The API origin defaults to :data:`DEFAULT_API_URL` when neither `api_url` nor ``TOLLARA_API_URL`` is set.
    """

    ENV_API_URL = ENV_API_URL
    ENV_SERVICE_ID = ENV_SERVICE_ID
    ENV_SERVICE_SECRET = ENV_SERVICE_SECRET
    DEFAULT_API_URL = DEFAULT_API_URL
    DEFAULT_CORE_PATH_PREFIX = DEFAULT_CORE_PATH_PREFIX
    DEFAULT_GATEWAY_PATH_PREFIX = DEFAULT_GATEWAY_PATH_PREFIX
    DEFAULT_USAGE_PATH_PREFIX = DEFAULT_USAGE_PATH_PREFIX

    def __init__(
        self,
        *,
        api_url: Optional[str] = None,
        core_api_url: Optional[str] = None,
        gateway_api_url: Optional[str] = None,
        usage_api_url: Optional[str] = None,
        core_path_prefix: Optional[str] = None,
        gateway_path_prefix: Optional[str] = None,
        usage_path_prefix: Optional[str] = None,
        service_id: Optional[str] = None,
        service_secret: Optional[str] = None,
        session: Optional["requests.Session"] = None,
    ) -> None:
        resolved = _resolve_api_url(api_url)

        core_base = _trim_trailing_slashes(_first_non_blank(core_api_url, resolved))
        gw_base = _trim_trailing_slashes(_first_non_blank(gateway_api_url, resolved))
        usage_base = _trim_trailing_slashes(_first_non_blank(usage_api_url, resolved))

        cp = core_path_prefix if core_path_prefix is not None else DEFAULT_CORE_PATH_PREFIX
        gp = gateway_path_prefix if gateway_path_prefix is not None else DEFAULT_GATEWAY_PATH_PREFIX
        up = usage_path_prefix if usage_path_prefix is not None else DEFAULT_USAGE_PATH_PREFIX

        sec = _first_non_blank(service_secret, os.environ.get(ENV_SERVICE_SECRET))
        if not sec:
            raise ValueError(
                f"Service secret is required: pass service_secret=... or set environment variable {ENV_SERVICE_SECRET}"
            )

        aid = _first_non_blank(service_id, os.environ.get(ENV_SERVICE_ID))
        aid_opt: Optional[str] = aid if aid else None

        self._gateway_base_url = gw_base
        self._gateway_path_prefix = gp
        self._core_base = core_base
        self._core_path_prefix = cp
        self._usage_base = usage_base
        self._usage_path_prefix = up
        self._service_id = aid_opt
        self._service_secret = sec
        self._session = session

    @classmethod
    def from_env(cls, *, session: Optional["requests.Session"] = None) -> TollaraClient:
        """Build from environment (optional `TOLLARA_API_URL` / service id / service secret). Uses :data:`DEFAULT_API_URL` when unset."""
        return cls(session=session)

    def validate_service_key(self, service_key: str) -> Optional[ServiceKeyValidationResult]:
        return validate_service_key(
            self._core_base,
            service_key,
            self._service_secret,
            self._service_id,
            core_path_prefix=self._core_path_prefix,
            session=self._session,
        )

    def estimate_usage(
        self, service_key: str, estimated_units: float, *, session: Optional["requests.Session"] = None
    ) -> Optional[UsageEstimateResult]:
        return estimate_usage(
            self._core_base,
            service_key,
            self._service_secret,
            estimated_units,
            self._service_id,
            core_path_prefix=self._core_path_prefix,
            session=session or self._session,
        )

    def estimate_usage_with_jwt(
        self,
        bearer_token: str,
        user_id: str,
        service_id: str,
        estimated_units: float,
        *,
        session: Optional["requests.Session"] = None,
    ) -> Optional[UsageEstimateResult]:
        """Core JWT usage estimate (§2.2). Response is not HMAC-signed."""
        return core_estimate_usage_with_jwt(
            self._core_base,
            bearer_token,
            user_id,
            service_id,
            estimated_units,
            core_path_prefix=self._core_path_prefix,
            session=session or self._session,
        )

    def invoke_service(
        self,
        method: str,
        service_id: str,
        endpoint_id: str,
        service_key: str,
        *,
        body: Optional[str] = None,
        async_: bool = False,
        session: Optional["requests.Session"] = None,
    ) -> Optional[GatewayInvokeResult]:
        """Gateway service invoke (§1.1–1.2)."""
        return gateway_invoke_service(
            self._gateway_base_url,
            method,
            service_id,
            endpoint_id,
            service_key,
            body=body,
            async_=async_,
            gateway_path_prefix=self._gateway_path_prefix,
            session=session or self._session,
        )

    def report_usage(
        self,
        user_id: str,
        service_id: str,
        units_used: float,
        *,
        session: Optional["requests.Session"] = None,
    ) -> UsageReportResponse:
        return report_usage(
            self._usage_base,
            user_id,
            service_id,
            units_used,
            self._service_secret,
            usage_path_prefix=self._usage_path_prefix,
            session=session or self._session,
        )

    def report_usage_at(
        self,
        user_id: str,
        service_id: str,
        units_used: float,
        timestamp: Optional[float],
        *,
        session: Optional["requests.Session"] = None,
    ) -> UsageReportResponse:
        return report_usage_at(
            self._usage_base,
            user_id,
            service_id,
            units_used,
            self._service_secret,
            timestamp=timestamp,
            usage_path_prefix=self._usage_path_prefix,
            session=session or self._session,
        )

    def send_progress_update(
        self,
        progress_url: str,
        request_id: str,
        stage: str,
        percentage_complete: int,
        error_message: Optional[str] = None,
        *,
        session: Optional["requests.Session"] = None,
    ) -> bool:
        return report_progress(
            progress_url,
            request_id,
            stage,
            percentage_complete,
            self._service_secret,
            error_message,
            session=session or self._session,
        )

    def send_completion(
        self,
        callback_url: str,
        request_id: str,
        status: CompletionStatus,
        units: float,
        *,
        result: Optional[str] = None,
        result_url: Optional[str] = None,
        content_type: Optional[str] = None,
        session: Optional["requests.Session"] = None,
    ) -> bool:
        sess = session or self._session
        if result is not None or result_url is not None or content_type is not None:
            return report_completion_full(
                callback_url,
                request_id,
                status,
                self._service_secret,
                result=result,
                result_url=result_url,
                content_type=content_type,
                units=units,
                session=sess,
            )
        return report_completion(
            callback_url,
            request_id,
            status,
            self._service_secret,
            units=units,
            session=sess,
        )

    def get_request_status(
        self,
        request_id: str,
        service_key: str,
        *,
        session: Optional["requests.Session"] = None,
    ) -> GatewayPollResult:
        return get_request_status(
            self._gateway_base_url,
            request_id,
            service_key,
            gateway_path_prefix=self._gateway_path_prefix,
            session=session or self._session,
        )

    def get_request_result(
        self,
        request_id: str,
        service_key: str,
        *,
        session: Optional["requests.Session"] = None,
    ) -> GatewayPollResult:
        return get_request_result(
            self._gateway_base_url,
            request_id,
            service_key,
            gateway_path_prefix=self._gateway_path_prefix,
            session=session or self._session,
        )

