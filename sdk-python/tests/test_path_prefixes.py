from tollara_service_sdk.path_prefixes import (
    ECS_GATEWAY_PATH_PREFIX,
    DEFAULT_GATEWAY_PATH_PREFIX,
    is_hosted_tollara_api_origin,
    resolve_gateway_path_prefix,
)


def test_hosted_tollara_api_origin():
    assert is_hosted_tollara_api_origin("https://api.tollara.ai")
    assert not is_hosted_tollara_api_origin("http://host.docker.internal:8083")


def test_resolve_gateway_path_prefix_prod():
    assert resolve_gateway_path_prefix("https://api.tollara.ai") == ECS_GATEWAY_PATH_PREFIX
    assert resolve_gateway_path_prefix("http://host.docker.internal:8083") == DEFAULT_GATEWAY_PATH_PREFIX
