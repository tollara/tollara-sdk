# frozen_string_literal: true

require "spec_helper"

RSpec.describe TollaraSdk::PathPrefixes do
  describe ".hosted_tollara_api_origin?" do
    it "detects hosted prod and PPE origins" do
      expect(described_class.hosted_tollara_api_origin?("https://api.tollara.ai")).to be true
      expect(described_class.hosted_tollara_api_origin?("https://acme.api.tollara.ai")).to be true
      expect(described_class.hosted_tollara_api_origin?("http://host.docker.internal:8083")).to be false
    end
  end

  describe ".resolve_gateway_path_prefix" do
    it "uses ECS prefix for hosted prod" do
      expect(described_class.resolve_gateway_path_prefix("https://api.tollara.ai"))
        .to eq(TollaraSdk::ECS_GATEWAY_PATH_PREFIX)
      expect(described_class.resolve_gateway_path_prefix("http://host.docker.internal:8083"))
        .to eq(TollaraSdk::DEFAULT_GATEWAY_PATH_PREFIX)
    end

    it "honours explicit override" do
      expect(described_class.resolve_gateway_path_prefix("https://api.tollara.ai", "/api")).to eq("/api")
    end
  end

  describe ".resolve_core_path_prefix" do
    it "uses ECS prefix for hosted prod" do
      expect(described_class.resolve_core_path_prefix("https://api.tollara.ai"))
        .to eq(TollaraSdk::ECS_CORE_PATH_PREFIX)
    end
  end

  describe ".resolve_usage_path_prefix" do
    it "uses ECS prefix for hosted prod" do
      expect(described_class.resolve_usage_path_prefix("https://api.tollara.ai"))
        .to eq(TollaraSdk::ECS_USAGE_PATH_PREFIX)
    end
  end
end
