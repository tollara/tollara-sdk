"""Unified HTTP client: env-based config and URL layout aligned with Java `AgentVendClient`."""

from __future__ import annotations

import os
from typing import TYPE_CHECKING, Optional

from .completion_status import CompletionStatus
from .gateway_client import GatewayPollResult, get_request_result, get_request_status
from .usage_client import (
    DEFAULT_USAGE_PATH_PREFIX,
    report_completion,
    report_completion_full,
    report_progress,
    report_usage,
    report_usage_at,
    UsageReportResponse,
)
from .validation_client import AgentKeyValidationResult, validate_agent_key

if TYPE_CHECKING:
    import requests

ENV_API_URL = "AGENTVEND_API_URL"
ENV_AGENT_ID = "AGENTVEND_AGENT_ID"
ENV_AGENT_SECRET = "AGENTVEND_AGENT_SECRET"

DEFAULT_CORE_PATH_PREFIX = "/api/v1"
DEFAULT_GATEWAY_PATH_PREFIX = "/api"


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


class AgentVendClient:
    """
    Single entry point for Core validate, Usage report/progress/complete, and Gateway polling.
    Explicit constructor arguments override environment variables (same order as Java `Builder`).
    """

    ENV_API_URL = ENV_API_URL
    ENV_AGENT_ID = ENV_AGENT_ID
    ENV_AGENT_SECRET = ENV_AGENT_SECRET
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
        agent_id: Optional[str] = None,
        agent_secret: Optional[str] = None,
        session: Optional["requests.Session"] = None,
    ) -> None:
        resolved = _first_non_blank(api_url, os.environ.get(ENV_API_URL))
        resolved = _trim_trailing_slashes(resolved)
        if not resolved:
            raise ValueError(
                f"AgentVend API URL is required: pass api_url=... or set environment variable {ENV_API_URL}"
            )

        core_base = _trim_trailing_slashes(_first_non_blank(core_api_url, resolved))
        gw_base = _trim_trailing_slashes(_first_non_blank(gateway_api_url, resolved))
        usage_base = _trim_trailing_slashes(_first_non_blank(usage_api_url, resolved))

        cp = core_path_prefix if core_path_prefix is not None else DEFAULT_CORE_PATH_PREFIX
        gp = gateway_path_prefix if gateway_path_prefix is not None else DEFAULT_GATEWAY_PATH_PREFIX
        up = usage_path_prefix if usage_path_prefix is not None else DEFAULT_USAGE_PATH_PREFIX

        sec = _first_non_blank(agent_secret, os.environ.get(ENV_AGENT_SECRET))
        if not sec:
            raise ValueError(
                f"Agent secret is required: pass agent_secret=... or set environment variable {ENV_AGENT_SECRET}"
            )

        aid = _first_non_blank(agent_id, os.environ.get(ENV_AGENT_ID))
        aid_opt: Optional[str] = aid if aid else None

        self._gateway_base_url = gw_base
        self._gateway_path_prefix = gp
        self._core_root = _join_url(core_base, cp)
        self._usage_base = usage_base
        self._usage_path_prefix = up
        self._agent_id = aid_opt
        self._agent_secret = sec
        self._session = session

    @classmethod
    def from_env(cls, *, session: Optional["requests.Session"] = None) -> AgentVendClient:
        """Build using only `AGENTVEND_*` environment variables."""
        return cls(session=session)

    def validate_agent_key(self, agent_key: str) -> Optional[AgentKeyValidationResult]:
        return validate_agent_key(
            self._core_root,
            agent_key,
            self._agent_secret,
            self._agent_id,
            session=self._session,
        )

    def report_usage(
        self,
        user_id: str,
        agent_id: str,
        units_used: float,
        *,
        session: Optional["requests.Session"] = None,
    ) -> UsageReportResponse:
        return report_usage(
            self._usage_base,
            user_id,
            agent_id,
            units_used,
            self._agent_secret,
            usage_path_prefix=self._usage_path_prefix,
            session=session or self._session,
        )

    def report_usage_at(
        self,
        user_id: str,
        agent_id: str,
        units_used: float,
        timestamp: Optional[float],
        *,
        session: Optional["requests.Session"] = None,
    ) -> UsageReportResponse:
        return report_usage_at(
            self._usage_base,
            user_id,
            agent_id,
            units_used,
            self._agent_secret,
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
            self._agent_secret,
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
                self._agent_secret,
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
            self._agent_secret,
            units=units,
            session=sess,
        )

    def get_request_status(
        self,
        request_id: str,
        agent_key: str,
        *,
        session: Optional["requests.Session"] = None,
    ) -> GatewayPollResult:
        return get_request_status(
            self._gateway_base_url,
            self._gateway_path_prefix,
            request_id,
            agent_key,
            session=session or self._session,
        )

    def get_request_result(
        self,
        request_id: str,
        agent_key: str,
        *,
        session: Optional["requests.Session"] = None,
    ) -> GatewayPollResult:
        return get_request_result(
            self._gateway_base_url,
            self._gateway_path_prefix,
            request_id,
            agent_key,
            session=session or self._session,
        )
