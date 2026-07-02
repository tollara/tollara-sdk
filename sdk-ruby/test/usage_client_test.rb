# frozen_string_literal: true

require "minitest/autorun"
require_relative "../lib/tollara_sdk"

class UsageClientTest < Minitest::Test
  def client
    @client ||= TollaraSdk::TollaraClient.new(api_url: "http://usage.test", service_secret: "secret")
  end

  def test_report_progress_handles_missing_url_without_throwing
    result = client.send_progress_update("", "req-1", "processing", 25)
    assert_instance_of TollaraSdk::UsageCallbackResult, result
    refute result.success
    assert_equal 0, result.http_status
    assert_equal "Missing or invalid callback/progress URL", result.http_status_text
  end

  def test_report_progress_returns_failure_when_url_missing_timestamp
    result = client.send_progress_update("http://usage.test/api/usage/progress/req-1", "req-1", "stage", 0)
    refute result.success
    assert_equal 0, result.http_status
    assert_equal "Missing timestamp query parameter in URL", result.http_status_text
  end

  def test_report_completion_returns_failure_when_url_missing_timestamp
    result = client.send_completion("http://usage.test/api/usage/complete/req-1", "req-1", "FAILED", 0)
    refute result.success
    assert_equal 0, result.http_status
  end
end
