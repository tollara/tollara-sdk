import json
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any, Dict, Optional
from urllib.parse import parse_qs, urlparse

from .tollara_headers import TollaraHeaders
from .completion_status import CompletionStatus
from .hmac_utils import calculate_hmac_with_timestamp
from .usage_breakdown import UsageBreakdown, parse_usage_breakdown
from .path_prefixes import resolve_usage_path_prefix

DEFAULT_USAGE_PATH_PREFIX = "/api/usage"


def _usage_report_iso_and_epoch_sec(timestamp: Optional[float]) -> tuple[str, str]:
    """Body uses ISO-8601 instant; HMAC header uses Unix epoch seconds (spec §3)."""
    if timestamp is None:
        sec = int(time.time())
    elif timestamp > 1e11:
        sec = int(timestamp // 1000)
    else:
        sec = int(timestamp)
    iso = datetime.fromtimestamp(sec, tz=timezone.utc).isoformat().replace("+00:00", "Z")
    return iso, str(sec)


def _usage_report_url(base_url: str, usage_path_prefix: Optional[str]) -> str:
    base = base_url.rstrip("/")
    p = resolve_usage_path_prefix(base_url, usage_path_prefix).strip()
    if not p.startswith("/"):
        p = "/" + p
    p = p.rstrip("/")
    return f"{base}{p}/report"


@dataclass
class UsageReportResponse:
    report_schema_version: int
    status: Optional[str]
    warning: Optional[str]
    user_id: Optional[str]
    service_id: Optional[str]
    billing_model_type: Optional[str]
    measurement_type: Optional[str]
    unit_label: Optional[str]
    breakdown: Optional[UsageBreakdown]


@dataclass
class UsageCallbackResult:
    success: bool
    http_status: int
    http_status_text: str
    request_url: str
    response_body: Optional[str] = None
    network_error: Optional[str] = None


def _parse_url_params(url: Optional[str]) -> tuple[str, Optional[str]]:
    if url is None:
        return "", None
    parsed = urlparse(url)
    base = f"{parsed.scheme}://{parsed.netloc}{parsed.path}" if parsed.scheme else url.split("?")[0]
    if not parsed.query:
        return base, None
    qs = parse_qs(parsed.query)
    timestamp = (qs.get("timestamp") or [None])[0]
    return base, timestamp


def _post_signed_usage_callback(
    url_with_query: str,
    body_str: str,
    service_secret: str,
    *,
    session: Optional["requests.Session"] = None,
) -> UsageCallbackResult:
    base_url, timestamp = _parse_url_params(url_with_query)
    if not timestamp:
        status_text = (
            "Missing timestamp query parameter in URL"
            if url_with_query
            else "Missing or invalid callback/progress URL"
        )
        return UsageCallbackResult(
            success=False,
            http_status=0,
            http_status_text=status_text,
            request_url=base_url,
        )

    try:
        import requests
    except ImportError as exc:
        raise ImportError("usage callbacks require 'requests'. pip install requests") from exc

    signature = calculate_hmac_with_timestamp(body_str, timestamp, service_secret)
    sess = session or requests.Session()
    try:
        resp = sess.post(
            base_url,
            data=body_str,
            headers={
                "Content-Type": "application/json",
                TollaraHeaders.SIGNATURE: signature,
                TollaraHeaders.TIMESTAMP: timestamp,
            },
        )
        response_body = resp.text or None
        return UsageCallbackResult(
            success=resp.ok,
            http_status=resp.status_code,
            http_status_text=resp.reason or ("OK" if resp.ok else f"HTTP {resp.status_code}"),
            request_url=base_url,
            response_body=response_body,
        )
    except requests.RequestException as exc:
        return UsageCallbackResult(
            success=False,
            http_status=0,
            http_status_text="Network error",
            request_url=base_url,
            network_error=str(exc),
        )


def report_progress(
    progress_url: str,
    request_id: str,
    stage: str,
    percentage_complete: int,
    service_secret: str,
    error_message: Optional[str] = None,
    *,
    session: Optional["requests.Session"] = None,
) -> UsageCallbackResult:
    """POST progress to usage service. Requires 'requests'."""
    body: Dict[str, Any] = {
        "stage": stage,
        "percentageComplete": percentage_complete,
        "timestamp": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
    }
    if error_message is not None:
        body["errorMessage"] = error_message
    body_str = json.dumps(body, separators=(",", ":"))
    return _post_signed_usage_callback(
        progress_url, body_str, service_secret, session=session
    )


def report_completion(
    callback_url: str,
    request_id: str,
    status: CompletionStatus,
    service_secret: str,
    *,
    result: Optional[str] = None,
    result_url: Optional[str] = None,
    content_type: Optional[str] = None,
    units: float = 0.0,
    session: Optional["requests.Session"] = None,
) -> UsageCallbackResult:
    """POST completion to usage service. Requires 'requests'."""
    body: Dict[str, Any] = {
        "status": status.api_value,
        "timestamp": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "units": units,
    }
    if result is not None:
        body["result"] = result
    if result_url is not None:
        body["resultUrl"] = result_url
    if content_type is not None:
        body["contentType"] = content_type
    body_str = json.dumps(body, separators=(",", ":"))
    return _post_signed_usage_callback(
        callback_url, body_str, service_secret, session=session
    )


def report_usage(
    base_url: str,
    user_id: str,
    service_id: str,
    units_used: float,
    service_secret: str,
    *,
    usage_path_prefix: Optional[str] = None,
    session: Optional["requests.Session"] = None,
) -> UsageReportResponse:
    """Report usage with current time as timestamp."""
    return report_usage_at(
        base_url,
        user_id,
        service_id,
        units_used,
        service_secret,
        timestamp=None,
        usage_path_prefix=usage_path_prefix,
        session=session,
    )


def report_usage_at(
    base_url: str,
    user_id: str,
    service_id: str,
    units_used: float,
    service_secret: str,
    timestamp: Optional[float] = None,
    *,
    usage_path_prefix: Optional[str] = None,
    session: Optional["requests.Session"] = None,
) -> UsageReportResponse:
    try:
        import requests
    except ImportError:
        raise ImportError("report_usage requires 'requests'. pip install requests")
    iso, ts_sec = _usage_report_iso_and_epoch_sec(timestamp)
    body = {"userId": user_id, "serviceId": service_id, "unitsUsed": units_used, "timestamp": iso}
    body_str = json.dumps(body, separators=(",", ":"))
    signature = calculate_hmac_with_timestamp(body_str, ts_sec, service_secret)
    url = _usage_report_url(base_url, usage_path_prefix)
    sess = session or requests.Session()
    resp = sess.post(
        url,
        data=body_str,
        headers={
            "Content-Type": "application/json",
            TollaraHeaders.SIGNATURE: signature,
            TollaraHeaders.TIMESTAMP: ts_sec,
        },
    )
    resp.raise_for_status()
    data = resp.json()
    breakdown_raw = data.get("breakdown")
    breakdown = parse_usage_breakdown(breakdown_raw) if isinstance(breakdown_raw, dict) else None
    return UsageReportResponse(
        report_schema_version=int(data.get("reportSchemaVersion", 0)),
        status=data.get("status"),
        warning=data.get("warning"),
        user_id=data.get("userId"),
        service_id=data.get("serviceId"),
        billing_model_type=data.get("billingModelType"),
        measurement_type=data.get("measurementType"),
        unit_label=data.get("unitLabel"),
        breakdown=breakdown,
    )
