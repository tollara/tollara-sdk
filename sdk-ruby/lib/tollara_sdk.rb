# frozen_string_literal: true

require "base64"
require "openssl"
require "json"
require "net/http"
require "uri"
require "time"

module TollaraSdk
  DEFAULT_API_URL = "https://api.tollara.ai"
  DEFAULT_CORE_PATH_PREFIX = "/api/v1"
  DEFAULT_GATEWAY_PATH_PREFIX = "/api"
  DEFAULT_USAGE_PATH_PREFIX = "/api/usage"

  HEADERS = {
    signature: "X-Tollara-Signature",
    timestamp: "X-Tollara-Timestamp",
    user_id: "X-Tollara-User-ID",
    plan: "X-Tollara-Plan",
    roles: "X-Tollara-Roles",
    quota_remaining: "X-Tollara-Quota-Remaining",
    subscription_active: "X-Tollara-Subscription-Active",
    billing_model: "X-Tollara-Billing-Model",
    measurement_type: "X-Tollara-Measurement-Type",
    unit_label: "X-Tollara-Unit-Label"
    signing_version: "X-Tollara-Signing-Version"
  }.freeze

  class TollaraClient
    attr_reader :api_url, :service_id, :service_secret

    def initialize(
      api_url: nil, core_api_url: nil, gateway_api_url: nil, usage_api_url: nil,
      core_path_prefix: DEFAULT_CORE_PATH_PREFIX, gateway_path_prefix: DEFAULT_GATEWAY_PATH_PREFIX,
      usage_path_prefix: DEFAULT_USAGE_PATH_PREFIX, service_id: nil, service_secret: nil
    )
      @api_url = (api_url || ENV["TOLLARA_API_URL"] || DEFAULT_API_URL).to_s.sub(%r{/\z}, "")
      @core_api_url = (core_api_url || @api_url).to_s.sub(%r{/\z}, "")
      @gateway_api_url = (gateway_api_url || @api_url).to_s.sub(%r{/\z}, "")
      @usage_api_url = (usage_api_url || @api_url).to_s.sub(%r{/\z}, "")
      @core_path_prefix = normalize_prefix(core_path_prefix)
      @gateway_path_prefix = normalize_prefix(gateway_path_prefix)
      @usage_path_prefix = normalize_prefix(usage_path_prefix)
      @service_id = (service_id || ENV["TOLLARA_SERVICE_ID"]).to_s
      @service_secret = (service_secret || ENV["TOLLARA_SERVICE_SECRET"]).to_s
      raise ArgumentError, "service_secret is required" if @service_secret.strip.empty?
    end

    def validate_service_key(service_key)
      body = { serviceKey: service_key, serviceSecret: @service_secret }
      body[:serviceId] = @service_id unless @service_id.to_s.strip.empty?
      url = "#{@core_api_url}#{@core_path_prefix}/service-keys/validate"
      res = post_json(url, body)
      return nil unless res.is_a?(Net::HTTPSuccess)
      sig = res[HEADERS[:signature]]
      ts = res[HEADERS[:timestamp]]
      return nil if sig.to_s.empty? || ts.to_s.empty?
      raw = res.body.to_s
      return nil unless TollaraSdk.validate_hmac_signature(sig, raw + ts, @service_secret)
      json = JSON.parse(raw) rescue nil
      return nil unless json.is_a?(Hash) && json["valid"]
      json
    end

    def estimate_usage(service_key, estimated_units)
      body = { serviceKey: service_key, serviceSecret: @service_secret, estimatedUnits: estimated_units }
      body[:serviceId] = @service_id unless @service_id.to_s.strip.empty?
      url = "#{@core_api_url}#{@core_path_prefix}/service-keys/estimate-usage"
      res = post_json(url, body)
      return nil unless [200, 403, 429].include?(res.code.to_i)
      raw = res.body.to_s
      return nil if raw.strip.empty?
      sig = res[HEADERS[:signature]]
      ts = res[HEADERS[:timestamp]]
      return nil if sig.to_s.empty? || ts.to_s.empty?
      return nil unless TollaraSdk.validate_hmac_signature(sig, raw + ts, @service_secret)
      json = JSON.parse(raw) rescue nil
      return nil unless json.is_a?(Hash)
      json["httpStatus"] = res.code.to_i
      json
    end

    def estimate_usage_with_jwt(bearer_token, user_id, service_id, estimated_units)
      body = { userId: user_id, serviceId: service_id, estimatedUnits: estimated_units }
      url = "#{@core_api_url}#{@core_path_prefix}/billing/usage/estimate"
      res = post_json(url, body, "Authorization" => "Bearer #{bearer_token}")
      return nil unless [200, 403, 429].include?(res.code.to_i)
      raw = res.body.to_s
      return nil if raw.strip.empty?
      json = JSON.parse(raw) rescue nil
      return nil unless json.is_a?(Hash)
      json["httpStatus"] = res.code.to_i
      json
    end

    def invoke_service(method, service_id, endpoint_id, service_key, body: nil, async: false)
      suffix = "/service/#{service_id}/endpoint/#{endpoint_id}/invoke"
      suffix += "/async" if async
      url = "#{@gateway_api_url}#{@gateway_path_prefix}#{suffix}"
      headers = { "Authorization" => "Bearer #{service_key}" }
      headers["Content-Type"] = "application/json" if %w[POST PUT].include?(method.to_s.upcase) && !body.to_s.empty?
      res = request(method.to_s.upcase, url, body, headers)
      out = { "statusCode" => res.code.to_i, "body" => res.body.to_s }
      if res.code.to_i == 202
        parsed = JSON.parse(res.body.to_s) rescue nil
        out["asyncEnvelope"] = parsed if parsed.is_a?(Hash) && parsed["requestId"]
      end
      out
    end

    def report_usage(user_id, service_id, units_used)
      report_usage_at(user_id, service_id, units_used, nil)
    end

    def report_usage_at(user_id, service_id, units_used, timestamp = nil)
      t = timestamp ? Time.at(timestamp.to_f).utc : Time.now.utc
      header_ts = t.to_i.to_s
      body_hash = { userId: user_id, serviceId: service_id, unitsUsed: units_used, timestamp: t.iso8601(3) }
      body = JSON.generate(body_hash)
      sig = TollaraSdk.calculate_hmac_with_timestamp(body, header_ts, @service_secret)
      url = "#{@usage_api_url}#{@usage_path_prefix}/report"
      res = request("POST", url, body, {
        "Content-Type" => "application/json",
        HEADERS[:signature] => sig,
        HEADERS[:timestamp] => header_ts
      })
      raise "Usage report failed: #{res.code}" unless res.is_a?(Net::HTTPSuccess)
      JSON.parse(res.body.to_s)
    end

    def send_progress_update(progress_url, request_id, stage, percentage_complete, error_message = nil)
      base, ts = split_timestamp_url(progress_url)
      return false if ts.to_s.empty?
      body_hash = { stage: stage, percentageComplete: percentage_complete, timestamp: Time.now.utc.iso8601(3) }
      body_hash[:errorMessage] = error_message unless error_message.nil?
      body = JSON.generate(body_hash)
      sig = TollaraSdk.calculate_hmac_with_timestamp(body, ts, @service_secret)
      res = request("POST", base, body, {
        "Content-Type" => "application/json",
        HEADERS[:signature] => sig,
        HEADERS[:timestamp] => ts
      })
      res.is_a?(Net::HTTPSuccess)
    end

    def send_completion(callback_url, request_id, status, units, result: nil, result_url: nil, content_type: nil)
      base, ts = split_timestamp_url(callback_url)
      return false if ts.to_s.empty?
      body_hash = { status: status.to_s.upcase, timestamp: Time.now.utc.iso8601(3), units: units }
      body_hash[:result] = result unless result.nil?
      body_hash[:resultUrl] = result_url unless result_url.nil?
      body_hash[:contentType] = content_type unless content_type.nil?
      body = JSON.generate(body_hash)
      sig = TollaraSdk.calculate_hmac_with_timestamp(body, ts, @service_secret)
      res = request("POST", base, body, {
        "Content-Type" => "application/json",
        HEADERS[:signature] => sig,
        HEADERS[:timestamp] => ts
      })
      res.is_a?(Net::HTTPSuccess)
    end

    def get_request_status(request_id, service_key)
      url = "#{@gateway_api_url}#{@gateway_path_prefix}/requests/#{request_id}/status"
      res = request("GET", url, nil, { "Authorization" => "Bearer #{service_key}" })
      { ok: res.is_a?(Net::HTTPSuccess), status: res.code.to_i, body: res.body.to_s }
    end

    def get_request_result(request_id, service_key)
      url = "#{@gateway_api_url}#{@gateway_path_prefix}/requests/#{request_id}/result"
      res = request("GET", url, nil, { "Authorization" => "Bearer #{service_key}" })
      { ok: res.is_a?(Net::HTTPSuccess), status: res.code.to_i, body: res.body.to_s }
    end

    private

    def normalize_prefix(prefix)
      p = prefix.to_s.strip
      p = "/#{p}" unless p.start_with?("/")
      p.sub(%r{/\z}, "")
    end

    def split_timestamp_url(url)
      uri = URI(url)
      ts = nil
      if uri.query
        URI.decode_www_form(uri.query).each do |k, v|
          ts = v if k == "timestamp"
        end
      end
      uri.query = nil
      [uri.to_s, ts]
    end

    def post_json(url, payload, extra_headers = {})
      request("POST", url, JSON.generate(payload), { "Content-Type" => "application/json" }.merge(extra_headers))
    end

    def request(method, url, body, headers = {})
      uri = URI(url)
      req = case method
            when "GET" then Net::HTTP::Get.new(uri)
            when "POST" then Net::HTTP::Post.new(uri)
            when "PUT" then Net::HTTP::Put.new(uri)
            when "DELETE" then Net::HTTP::Delete.new(uri)
            else Net::HTTP::Get.new(uri)
            end
      headers.each { |k, v| req[k] = v }
      req.body = body if body
      Net::HTTP.start(uri.host, uri.port, use_ssl: uri.scheme == "https") do |http|
        http.read_timeout = 60
        http.open_timeout = 60
        http.request(req)
      end
    end
  end

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

  def self.build_gateway_user_context_string_v2(user_id, plan, roles, subscription_active, billing, measurement, unit)
    r = roles || []
    sub = subscription_active ? "true" : "false"
    "2" + (user_id || "").to_s + (plan || "").to_s + r.join(",") + sub + (billing || "").to_s + (measurement || "").to_s + (unit || "").to_s
  end

  def self.verify_signature(agent_secret, signature, timestamp, payload, user_id, plan, roles, quota_remaining, subscription_active, billing = nil, measurement = nil, unit = nil, signing_version = nil)
    return false if signature.to_s.empty? || timestamp.to_s.empty? || agent_secret.to_s.empty?
    r = roles || []
    q = format_quota_for_signing(quota_remaining)
    user_context_string =
      if signing_version.to_s.strip == "2"
        build_gateway_user_context_string_v2(user_id, plan, r, subscription_active, billing, measurement, unit)
      else
        build_gateway_user_context_string(user_id, plan, r, q, subscription_active, billing, measurement, unit)
      end
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
      unit_label: ul,
      signing_version: header_get_ci(headers, HEADERS[:signing_version])
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
