# frozen_string_literal: true

require "base64"
require "openssl"

module MarketplaceAgentSdk
  def self.calculate_hmac(data, key)
    digest = OpenSSL::Digest.new("SHA256")
    Base64.strict_encode64(OpenSSL::HMAC.digest(digest, key, data))
  end

  def self.calculate_hmac_with_timestamp(body_string, timestamp, key)
    calculate_hmac(body_string + timestamp.to_s, key)
  end

  def self.constant_time_equals(a, b)
    return false if a.nil? || b.nil?
    return false if a.bytesize != b.bytesize
    OpenSSL.fixed_length_secure_compare(a, b)
  end

  def self.verify_signature(agent_secret, signature, timestamp, payload, user_id, plan, roles, quota_remaining)
    return false if signature.to_s.empty? || timestamp.to_s.empty? || agent_secret.to_s.empty?
    user_context_string = (user_id || "") + (plan || "") + (roles || []).join(",") + (quota_remaining || "").to_s
    data_to_sign = (payload || "").to_s + timestamp.to_s + user_context_string
    expected = calculate_hmac(data_to_sign, agent_secret)
    constant_time_equals(expected, signature)
  end
end
