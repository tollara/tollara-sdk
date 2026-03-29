# frozen_string_literal: true

require "base64"
require "openssl"

module AgentVendSdk
  HEADERS = {
    signature: "X-AgentVend-Signature",
    timestamp: "X-AgentVend-Timestamp",
    user_id: "X-AgentVend-User-ID",
    plan: "X-AgentVend-Plan",
    roles: "X-AgentVend-Roles",
    quota_remaining: "X-AgentVend-Quota-Remaining",
    subscription_active: "X-AgentVend-Subscription-Active",
    billing_model: "X-AgentVend-Billing-Model",
    measurement_type: "X-AgentVend-Measurement-Type",
    unit_label: "X-AgentVend-Unit-Label"
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

  def self.build_gateway_user_context_string(user_id, plan, roles, quota_remaining, subscription_active, billing, measurement, unit)
    r = roles || []
    sub = subscription_active ? "true" : "false"
    (user_id || "").to_s + (plan || "").to_s + r.join(",") + (quota_remaining || "").to_s + sub + (billing || "").to_s + (measurement || "").to_s + (unit || "").to_s
  end

  def self.verify_signature(agent_secret, signature, timestamp, payload, user_id, plan, roles, quota_remaining, subscription_active, billing = nil, measurement = nil, unit = nil)
    return false if signature.to_s.empty? || timestamp.to_s.empty? || agent_secret.to_s.empty?
    r = roles || []
    q = format_quota_for_signing(quota_remaining)
    user_context_string = build_gateway_user_context_string(user_id, plan, r, q, subscription_active, billing, measurement, unit)
    data_to_sign = (payload || "").to_s + timestamp.to_s + user_context_string
    expected = calculate_hmac(data_to_sign, agent_secret)
    constant_time_equals(expected, signature)
  end

  # +inbound+ is a Hash with keys :signature, :timestamp, :payload, :user_id, :plan, :roles, optional quota, :subscription_active, billing keys
  def self.verify_inbound_hmac(agent_secret, inbound)
    sub = inbound[:subscription_active] == true || inbound[:subscription_active].to_s == "true" || inbound[:subscription_active].to_s == "1"
    verify_signature(
      agent_secret,
      inbound[:signature],
      inbound[:timestamp],
      inbound[:payload].to_s,
      inbound[:user_id],
      inbound[:plan],
      inbound[:roles] || [],
      inbound[:quota_remaining],
      sub,
      inbound[:billing_model_type],
      inbound[:measurement_type],
      inbound[:unit_label]
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
    sub_raw = header_get_ci(headers, HEADERS[:subscription_active])
    sub_active = sub_raw == "true" || sub_raw == "1"
    bm = header_get_ci(headers, HEADERS[:billing_model])
    mt = header_get_ci(headers, HEADERS[:measurement_type])
    ul = header_get_ci(headers, HEADERS[:unit_label])
    verify_inbound_hmac(
      agent_secret,
      signature: sig,
      timestamp: ts,
      payload: payload,
      user_id: header_get_ci(headers, HEADERS[:user_id]),
      plan: header_get_ci(headers, HEADERS[:plan]),
      roles: roles,
      quota_remaining: qraw,
      subscription_active: sub_active,
      billing_model_type: bm,
      measurement_type: mt,
      unit_label: ul
    )
  end

  # Verify HMAC; returns user context hash if valid, nil if invalid (do not trust headers).
  def self.verify_signature_from_headers_and_user_context(agent_secret, headers, payload)
    return nil unless verify_signature_from_headers(agent_secret, headers, payload)
    user_context_from_headers(headers)
  end

  def self.user_context_from_headers(headers)
    roles_csv = header_get_ci(headers, HEADERS[:roles]).to_s
    roles = roles_csv.split(",").map(&:strip).reject(&:empty?)
    qraw = header_get_ci(headers, HEADERS[:quota_remaining])
    quota = qraw.nil? || qraw.empty? ? nil : qraw.to_f
    sub = header_get_ci(headers, HEADERS[:subscription_active])
    sub_active = sub == "true" || sub == "1"
    bm = header_get_ci(headers, HEADERS[:billing_model]).to_s
    mt = header_get_ci(headers, HEADERS[:measurement_type]).to_s
    ul = header_get_ci(headers, HEADERS[:unit_label]).to_s
    {
      user_id: header_get_ci(headers, HEADERS[:user_id]),
      plan: header_get_ci(headers, HEADERS[:plan]),
      roles: roles,
      quota_remaining: quota,
      subscription_active: sub_active,
      billing_model_type: bm.empty? ? nil : bm,
      measurement_type: mt.empty? ? nil : mt,
      unit_label: ul.empty? ? nil : ul
    }
  end
end
