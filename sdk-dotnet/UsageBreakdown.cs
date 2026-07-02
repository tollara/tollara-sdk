using System.Text.Json.Serialization;

namespace Tollara;

/// <summary>Shared usage breakdown for estimate and report responses (see docs-sdk/MAIN-SDK-API-SPEC.md).</summary>
public record UsageBreakdown
{
    public decimal? UnitsUsed { get; init; }
    public decimal? BaseUnitsUsed { get; init; }
    public decimal? OverageUnits { get; init; }
    public decimal? ChargeableOverageUnits { get; init; }
    public decimal? SurplusOverageUnits { get; init; }
    public decimal? OverageCost { get; init; }
    public decimal? TotalOverageCost { get; init; }
    public decimal? UnitsRemaining { get; init; }

    /// <summary>PREPAID: credit balance after this chunk; null for other billing models.</summary>
    public decimal? RemainingCredits { get; init; }

    public decimal? RemainingSpendingCap { get; init; }
    public decimal? TotalUnitsUsedThisCycle { get; init; }

    [JsonPropertyName("isOverLimit")]
    public bool? OverLimit { get; init; }

    [JsonPropertyName("isOverage")]
    public bool? Overage { get; init; }

    [JsonPropertyName("isOverageAllowed")]
    public bool? OverageAllowed { get; init; }
}
