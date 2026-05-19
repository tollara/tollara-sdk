namespace Tollara;

/// <summary>
/// Completion status for usage async completion (sdk-api-spec §3.3).
/// The usage API expects uppercase <c>COMPLETED</c> / <c>FAILED</c> in JSON; use <see cref="CompletionStatusExtensions.ToApiString"/>
/// when building request bodies (as <see cref="UsageClient"/> does). Default <see cref="System.Text.Json"/> enum serialization
/// would use numeric or CLR names—do not rely on it for API payloads.
/// </summary>
public enum CompletionStatus
{
    Completed,
    Failed,
}

public static class CompletionStatusExtensions
{
    /// <summary>API JSON value for the usage completion <c>status</c> field (uppercase, per sdk-api-spec).</summary>
    public static string ToApiString(this CompletionStatus status) =>
        status == CompletionStatus.Completed ? "COMPLETED" : "FAILED";
}
