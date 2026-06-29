# frozen_string_literal: true

require "spec_helper"

RSpec.describe TollaraSdk::TollaraClient do
  let(:client) do
    described_class.new(
      api_url: "http://core.test",
      service_secret: "test-agent-secret",
      service_id: "550e8400-e29b-41d4-a716-446655440000",
    )
  end

  def stub_post_json(response)
    allow(client).to receive(:post_json).and_return(response)
  end

  def build_response(status_code, body:, signature: nil, timestamp: nil)
    response = instance_double(
      Net::HTTPResponse,
      code: status_code.to_s,
      body: body,
    )
    allow(response).to receive(:[]).with(TollaraSdk::HEADERS[:signature]).and_return(signature)
    allow(response).to receive(:[]).with(TollaraSdk::HEADERS[:timestamp]).and_return(timestamp)
    success = status_code >= 200 && status_code < 300
    allow(response).to receive(:is_a?).with(Net::HTTPSuccess).and_return(success)
    response
  end

  it "returns success outcome with valid HMAC" do
    body = {
      valid: true,
      serviceKeyId: "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
      userId: "user-123",
      serviceId: "550e8400-e29b-41d4-a716-446655440000",
      serviceProductId: "7c9e6679-7425-40de-944b-e07fc1f90ae7",
      roles: ["user"],
      subscriptionStatus: "ACTIVE",
      billingModelType: "SUBSCRIPTION",
      measurementType: "UNITS",
      unit: "request",
    }
    body_str = JSON.generate(body)
    timestamp = "1700000000"
    signature = TollaraSdk.calculate_hmac(body_str + timestamp, "test-agent-secret")
    stub_post_json(build_response(200, body: body_str, signature: signature, timestamp: timestamp))

    outcome = client.validate_service_key_with_outcome("valid-key")
    expect(outcome[:ok]).to be true
    expect(outcome[:result].user_id).to eq("user-123")
  end

  it "returns MISSING_KEY for blank key" do
    outcome = client.validate_service_key_with_outcome("  ")
    expect(outcome).to eq({ ok: false, code: "MISSING_KEY" })
  end

  it "returns HTTP_ERROR on 401" do
    stub_post_json(build_response(401, body: "unauthorized"))
    outcome = client.validate_service_key_with_outcome("bad-key")
    expect(outcome[:ok]).to be false
    expect(outcome[:code]).to eq("HTTP_ERROR")
    expect(outcome[:httpStatus]).to eq(401)
  end

  it "returns HMAC_MISMATCH on bad signature" do
    body_str = JSON.generate(valid: true, userId: "u1")
    stub_post_json(build_response(200, body: body_str, signature: "bad-signature", timestamp: "1700000000"))
    outcome = client.validate_service_key_with_outcome("k")
    expect(outcome[:ok]).to be false
    expect(outcome[:code]).to eq("HMAC_MISMATCH")
    expect(outcome[:httpStatus]).to eq(200)
  end

  it "returns INVALID_KEY with message" do
    body_str = JSON.generate(valid: false, error: "Key expired")
    timestamp = "1700000000"
    signature = TollaraSdk.calculate_hmac(body_str + timestamp, "test-agent-secret")
    stub_post_json(build_response(200, body: body_str, signature: signature, timestamp: timestamp))
    outcome = client.validate_service_key_with_outcome("expired")
    expect(outcome[:ok]).to be false
    expect(outcome[:code]).to eq("INVALID_KEY")
    expect(outcome[:message]).to eq("Key expired")
  end

  it "returns NETWORK on connection failure" do
    allow(client).to receive(:post_json).and_raise(StandardError, "connection refused")
    outcome = client.validate_service_key_with_outcome("k")
    expect(outcome[:ok]).to be false
    expect(outcome[:code]).to eq("NETWORK")
  end
end
