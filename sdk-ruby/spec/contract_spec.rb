# frozen_string_literal: true

require "spec_helper"

RSpec.describe "contract models" do
  it "parses validate v3 result" do
    result = TollaraSdk::ServiceKeyValidationResult.from_hash(
      "userId" => "user-1",
      "serviceId" => "svc-1",
      "serviceProductId" => "prod-1",
      "roles" => ["USER"],
      "subscriptionStatus" => "ACTIVE",
      "validationSchemaVersion" => 3
    )
    expect(result.service_product_id).to eq("prod-1")
    expect(result.validation_schema_version).to eq(3)
    expect(result.grants_access).to be true
  end

  it "parses estimate v3 with breakdown" do
    est = TollaraSdk::UsageEstimateResult.from_hash(
      {
        "sufficientCredits" => true,
        "wouldExceedCap" => false,
        "wouldAllow" => true,
        "breakdown" => { "remainingSpendingCap" => 20, "unitsRemaining" => 199 },
        "estimateSchemaVersion" => 3,
        "timestamp" => 1_700_000_000
      },
      200
    )
    expect(est.estimate_schema_version).to eq(3)
    expect(est.breakdown.remaining_spending_cap).to eq(20.0)
  end

  it "parses report v2 with breakdown" do
    rep = TollaraSdk::UsageReportResponse.from_hash(
      "reportSchemaVersion" => 2,
      "status" => "ok",
      "userId" => "user-1",
      "serviceId" => "svc-1",
      "breakdown" => { "unitsRemaining" => 99, "isOverLimit" => false }
    )
    expect(rep.report_schema_version).to eq(2)
    expect(rep.breakdown.units_remaining).to eq(99.0)
  end
end
