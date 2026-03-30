import base64
import hmac
import hashlib
from typing import Optional


def calculate_hmac(data: str, key: str) -> str:
    """HMAC-SHA256, UTF-8, Base64 (per docs/hmac-spec.md)."""
    digest = hmac.new(key.encode("utf-8"), data.encode("utf-8"), hashlib.sha256).digest()
    return base64.b64encode(digest).decode("ascii")


def calculate_hmac_with_timestamp(body_string: str, timestamp: str, key: str) -> str:
    """Outbound: canonical = bodyString + timestamp."""
    return calculate_hmac(body_string + timestamp, key)


def constant_time_equals(a: Optional[str], b: Optional[str]) -> bool:
    """Constant-time comparison to avoid timing attacks."""
    if a is None or b is None:
        return a is b
    if len(a) != len(b):
        return False
    return hmac.compare_digest(a.encode("utf-8"), b.encode("utf-8"))


def validate_hmac_signature(signature: str, payload_string: str, key: str) -> bool:
    if not signature or not key:
        return False
    try:
        expected = calculate_hmac(payload_string, key)
        return constant_time_equals(expected, signature)
    except Exception:
        return False
