# frozen_string_literal: true

require "base64"
require "openssl"

module AgentvendAgentSdk
  HEADERS = {
    signature: "X-AgentVend-Signature",
    timestamp: "X-AgentVend-Timestamp",
    user_id: "X-AgentVend-User-ID",
    plan: "X-AgentVend-Plan",
    roles: "X-AgentVend-Roles",
    quota_remaining: "X-AgentVend-Quota-Remaining",
    subscription_active: "X-AgentVend-Subscription-Active"
  }.freeze

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

  def self.format_quota_for_signing(q)
    return "" if q.nil?
    s = q.to_s.strip
    return "" if s.empty?
    f = s.to_f
    return f.to_i.to_s if f == f.to_i
    s
  end

  def self.verify_signature(agent_secret, signature, timestamp, payload, user_id, plan, roles, quota_remaining)
    return false if signature.to_s.empty? || timestamp.to_s.empty? || agent_secret.to_s.empty?
    r = roles || []
    user_context_string = (user_id || "").to_s + (plan || "").to_s + r.join(",") + (quota_remaining || "").to_s
    data_to_sign = (payload || "").to_s + timestamp.to_s + user_context_string
    expected = calculate_hmac(data_to_sign, agent_secret)
    constant_time_equals(expected, signature)
  end

  # +inbound+ is a Hash with keys :signature, :timestamp, :payload, :user_id, :plan, :roles (Array), :quota_remaining (optional)
  def self.verify_inbound_hmac(agent_secret, inbound)
    q = format_quota_for_signing(inbound[:quota_remaining])
    verify_signature(
      agent_secret,
      inbound[:signature],
      inbound[:timestamp],
      inbound[:payload].to_s,
      inbound[:user_id],
      inbound[:plan],
      inbound[:roles] || [],
      q
    )
  end

  def self.header_get_ci(headers, canonical_name)
    target = canonical_name.downcase
    headers.each do |k, v|
      return v.to_s if k.to_s.downcase == target
    end
    nil
  end

  def self.verify_signature_from_headers(agent_secret, headers, payload)
    sig = header_get_ci(headers, HEADERS[:signature])
    ts = header_get_ci(headers, HEADERS[:timestamp])
    return false if sig.nil? || sig.empty? || ts.nil? || ts.empty?
    roles_csv = header_get_ci(headers, HEADERS[:roles]).to_s
    roles = roles_csv.split(",").map(&:strip).reject(&:empty?)
    qraw = header_get_ci(headers, HEADERS[:quota_remaining])
    verify_inbound_hmac(
      agent_secret,
      signature: sig,
      timestamp: ts,
      payload: payload,
      user_id: header_get_ci(headers, HEADERS[:user_id]),
      plan: header_get_ci(headers, HEADERS[:plan]),
      roles: roles,
      quota_remaining: qraw
    )
  end

  def self.user_context_from_headers(headers)
    roles_csv = header_get_ci(headers, HEADERS[:roles]).to_s
    roles = roles_csv.split(",").map(&:strip).reject(&:empty?)
    qraw = header_get_ci(headers, HEADERS[:quota_remaining])
    quota = qraw.nil? || qraw.empty? ? nil : qraw.to_f
    sub = header_get_ci(headers, HEADERS[:subscription_active])
    sub_active = sub == "true" || sub == "1"
    {
      user_id: header_get_ci(headers, HEADERS[:user_id]),
      plan: header_get_ci(headers, HEADERS[:plan]),
      roles: roles,
      quota_remaining: quota,
      subscription_active: sub_active
    }
  end
end
