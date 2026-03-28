from enum import Enum


class CompletionStatus(str, Enum):
    """Completion status for usage async completion (sdk-api-spec §3.3)."""

    COMPLETED = "COMPLETED"
    FAILED = "FAILED"

    @property
    def api_value(self) -> str:
        return self.value
