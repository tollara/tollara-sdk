import json
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any, Dict, Optional
from urllib.parse import parse_qs, urlparse

from .agentvend_headers import AgentVendHeaders
from .completion_status import CompletionStatus
from .hmac_utils import calculate_hmac_with_timestamp

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
    p = (usage_path_prefix or DEFAULT_USAGE_PATH_PREFIX).strip()
    if not p.startswith("/"):
        p = "/" + p
    p = p.rstrip("/")
    return f"{base}{p}/report"


@dataclass
class UsageReportResponse:
    status: Optional[str]
    warning: Optional[str]
    is_over_limit: bool
    remaining_requests_per_period: int
    remaining_time_units_per_period: Optional[float]
    remaining_spending_cap: Optional[float]
    overage_rate: Optional[float]


def _parse_url_params(url: str) -> tuple[str, Optional[str]]:
    parsed = urlparse(url)
    base = f"{parsed.scheme}://{parsed.netloc}{parsed.path}" if parsed.scheme else url.split("?")[0]
    if not parsed.query:
        return base, None
    qs = parse_qs(parsed.query)
    timestamp = (qs.get("timestamp") or [None])[0]
    return base, timestamp


def report_progress(
    progress_url: str,
    request_id: str,
    stage: str,
    percentage_complete: int,
    service_secret: str,
    error_message: Optional[str] = None,
    *,
    session: Optional["requests.Session"] = None,
) -> bool:
    """POST progress to usage service. Requires 'requests'."""
    try:
        import requests
    except ImportError:
        raise ImportError("report_progress requires 'requests'. pip install requests")
    base_url, timestamp = _parse_url_params(progress_url)
    if not timestamp:
        return False
    body = {"stage": stage, "percentageComplete": percentage_complete, "timestamp": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")}
    if error_message is not None:
        body["errorMessage"] = error_message
    body_str = json.dumps(body, separators=(",", ":"))
    signature = calculate_hmac_with_timestamp(body_str, timestamp, service_secret)
    sess = session or __import__("requests").Session()
    resp = sess.post(
        base_url,
        data=body_str,
        headers={
            "Content-Type": "application/json",
            AgentVendHeaders.SIGNATURE: signature,
            AgentVendHeaders.TIMESTAMP: timestamp,
        },
    )
    return resp.ok


def report_completion(
    callback_url: str,
    request_id: str,
    status: CompletionStatus,
    service_secret: str,
    *,
    units: float = 0.0,
    session: Optional["requests.Session"] = None,
) -> bool:
    """POST completion with status and units only."""
    return report_completion_full(
        callback_url,
        request_id,
        status,
        service_secret,
        units=units,
        session=session,
    )


def report_completion_with_result(
    callback_url: str,
    request_id: str,
    status: CompletionStatus,
    service_secret: str,
    result: str,
    *,
    units: float = 0.0,
    session: Optional["requests.Session"] = None,
) -> bool:
    """POST completion with inline result text."""
    return report_completion_full(
        callback_url,
        request_id,
        status,
        service_secret,
        result=result,
        units=units,
        session=session,
    )


def report_completion_full(
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
) -> bool:
    try:
        import requests
    except ImportError:
        raise ImportError("report_completion_full requires 'requests'. pip install requests")
    base_url, timestamp = _parse_url_params(callback_url)
    if not timestamp:
        return False
    body = {
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
    signature = calculate_hmac_with_timestamp(body_str, timestamp, service_secret)
    sess = session or requests.Session()
    resp = sess.post(
        base_url,
        data=body_str,
        headers={
            "Content-Type": "application/json",
            AgentVendHeaders.SIGNATURE: signature,
            AgentVendHeaders.TIMESTAMP: timestamp,
        },
    )
    return resp.ok


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
            AgentVendHeaders.SIGNATURE: signature,
            AgentVendHeaders.TIMESTAMP: ts_sec,
        },
    )
    resp.raise_for_status()
    data = resp.json()
    return UsageReportResponse(
        status=data.get("status"),
        warning=data.get("warning"),
        is_over_limit=bool(data.get("isOverLimit")),
        remaining_requests_per_period=int(data.get("remainingRequestsPerPeriod", 0)),
        remaining_time_units_per_period=_opt_float(data.get("remainingTimeUnitsPerPeriod")),
        remaining_spending_cap=_opt_float(data.get("remainingSpendingCap")),
        overage_rate=_opt_float(data.get("overageRate")),
    )


def _opt_float(v: Any) -> Optional[float]:
    if v is None:
        return None
    if isinstance(v, (int, float)):
        return float(v)
    return None
