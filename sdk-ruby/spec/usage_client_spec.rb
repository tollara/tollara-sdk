# frozen_string_literal: true

require "spec_helper"

RSpec.describe TollaraSdk::TollaraClient do
  let(:client) { described_class.new(api_url: "http://usage.test", service_secret: "secret") }

  it "report progress handles missing url without throwing" do
    result = client.send_progress_update("", "req-1", "processing", 25)
    expect(result).to be_a(TollaraSdk::UsageCallbackResult)
    expect(result.success).to be false
    expect(result.http_status).to eq(0)
    expect(result.http_status_text).to eq("Missing or invalid callback/progress URL")
  end

  it "report progress returns failure when url missing timestamp" do
    result = client.send_progress_update("http://usage.test/api/usage/progress/req-1", "req-1", "stage", 0)
    expect(result.success).to be false
    expect(result.http_status).to eq(0)
    expect(result.http_status_text).to eq("Missing timestamp query parameter in URL")
  end

  it "report completion returns failure when url missing timestamp" do
    result = client.send_completion("http://usage.test/api/usage/complete/req-1", "req-1", "FAILED", 0)
    expect(result.success).to be false
    expect(result.http_status).to eq(0)
  end
end
