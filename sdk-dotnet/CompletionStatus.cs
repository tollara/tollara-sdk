namespace AgentVend;

/// <summary>Completion status for usage async completion (sdk-api-spec §3.3).</summary>
public enum CompletionStatus
{
    Completed,
    Failed,
}

public static class CompletionStatusExtensions
{
    public static string ToApiString(this CompletionStatus status) =>
        status == CompletionStatus.Completed ? "COMPLETED" : "FAILED";
}
