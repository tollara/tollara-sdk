# frozen_string_literal: true

require "spec_helper"

RSpec.describe TollaraSdk do
  describe ".build_gateway_user_context_string_v3" do
    it "all fields present golden string" do
      ctx = described_class.build_gateway_user_context_string_v3(
        "sub-ext-id", "prod-uuid-1", %w[roleA roleB],
        "ACTIVE", "SUBSCRIPTION", "PER_REQUEST", "request"
      )
      expect(ctx).to eq("3sub-ext-idprod-uuid-1roleA,roleBACTIVESUBSCRIPTIONPER_REQUESTrequest")
    end

    it "empty roles golden string" do
      ctx = described_class.build_gateway_user_context_string_v3("user-1", "prod-1", [], "TRIAL", nil, nil, nil)
      expect(ctx).to eq("3user-1prod-1TRIAL")
    end

    it "billing fields absent golden string" do
      ctx = described_class.build_gateway_user_context_string_v3("owner-id", "", nil, "ACTIVE", nil, nil, nil)
      expect(ctx).to eq("3owner-idACTIVE")
    end

    it "non access status golden string" do
      ctx = described_class.build_gateway_user_context_string_v3(
        "user-x", "prod-x", ["r1"], "EXPIRED", "PREPAID", "PER_REQUEST", "request"
      )
      expect(ctx).to eq("3user-xprod-xr1EXPIREDPREPAIDPER_REQUESTrequest")
    end
  end

  describe ".grants_access" do
    it "returns true for eligible statuses" do
      expect(described_class.grants_access("ACTIVE")).to be true
      expect(described_class.grants_access("trial")).to be true
      expect(described_class.grants_access("CANCELLING_PENDING")).to be true
    end

    it "returns false for ineligible statuses" do
      expect(described_class.grants_access("EXPIRED")).to be false
      expect(described_class.grants_access(nil)).to be false
    end
  end

  describe ".verify_signature_from_headers" do
    it "accepts v3 signing version" do
      secret = "my-agent-secret"
      payload = ""
      ts = "1700000000"
      user_ctx = described_class.build_gateway_user_context_string_v3(
        "user1", "prod-1", %w[role1 role2], "ACTIVE", "SUBSCRIPTION", "PER_REQUEST", "request"
      )
      sig = described_class.calculate_hmac(payload + ts + user_ctx, secret)
      headers = {
        "X-Tollara-Signature" => sig,
        "X-Tollara-Timestamp" => ts,
        "X-Tollara-Signing-Version" => "3",
        "X-Tollara-User-ID" => "user1",
        "X-Tollara-Service-Product-ID" => "prod-1",
        "X-Tollara-Roles" => "role1,role2",
        "X-Tollara-Subscription-Status" => "ACTIVE",
        "X-Tollara-Billing-Model" => "SUBSCRIPTION",
        "X-Tollara-Measurement-Type" => "PER_REQUEST",
        "X-Tollara-Unit-Label" => "request"
      }
      expect(described_class.verify_signature_from_headers(secret, headers, payload)).to be true
      ctx = described_class.verify_signature_from_headers_and_user_context(secret, headers, payload)
      expect(ctx[:service_product_id]).to eq("prod-1")
      expect(ctx[:subscription_status]).to eq("ACTIVE")
    end
  end
end
