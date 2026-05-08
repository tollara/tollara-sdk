<?php

declare(strict_types=1);

namespace AgentVend\AgentSdk;

final class AgentVendClient
{
    public const DEFAULT_API_URL = 'https://api.agentvend.api';
    public const DEFAULT_CORE_PATH_PREFIX = '/api/v1';
    public const DEFAULT_GATEWAY_PATH_PREFIX = '/api';
    public const DEFAULT_USAGE_PATH_PREFIX = '/api/usage';

    private string $apiUrl;
    private string $coreApiUrl;
    private string $gatewayApiUrl;
    private string $usageApiUrl;
    private string $corePathPrefix;
    private string $gatewayPathPrefix;
    private string $usagePathPrefix;
    private string $serviceId;
    private string $serviceSecret;

    public function __construct(
        ?string $apiUrl = null,
        ?string $serviceId = null,
        ?string $serviceSecret = null,
        ?string $coreApiUrl = null,
        ?string $gatewayApiUrl = null,
        ?string $usageApiUrl = null,
        string $corePathPrefix = self::DEFAULT_CORE_PATH_PREFIX,
        string $gatewayPathPrefix = self::DEFAULT_GATEWAY_PATH_PREFIX,
        string $usagePathPrefix = self::DEFAULT_USAGE_PATH_PREFIX,
    ) {
        $this->apiUrl = rtrim($apiUrl ?: (getenv('AGENTVEND_API_URL') ?: self::DEFAULT_API_URL), '/');
        $this->coreApiUrl = rtrim($coreApiUrl ?: $this->apiUrl, '/');
        $this->gatewayApiUrl = rtrim($gatewayApiUrl ?: $this->apiUrl, '/');
        $this->usageApiUrl = rtrim($usageApiUrl ?: $this->apiUrl, '/');
        $this->corePathPrefix = $this->normalizePrefix($corePathPrefix);
        $this->gatewayPathPrefix = $this->normalizePrefix($gatewayPathPrefix);
        $this->usagePathPrefix = $this->normalizePrefix($usagePathPrefix);
        $this->serviceId = trim((string) ($serviceId ?: getenv('AGENTVEND_SERVICE_ID') ?: getenv('AGENTVEND_AGENT_ID') ?: ''));
        $this->serviceSecret = trim((string) ($serviceSecret ?: getenv('AGENTVEND_SERVICE_SECRET') ?: getenv('AGENTVEND_AGENT_SECRET') ?: ''));
        if ($this->serviceSecret === '') {
            throw new \InvalidArgumentException('serviceSecret is required');
        }
    }

    /** @return array<string,mixed>|null */
    public function validateServiceKey(string $serviceKey): ?array
    {
        $body = ['serviceKey' => $serviceKey, 'serviceSecret' => $this->serviceSecret];
        if ($this->serviceId !== '') {
            $body['serviceId'] = $this->serviceId;
        }
        $res = $this->requestJson('POST', $this->coreApiUrl . $this->corePathPrefix . '/service-keys/validate', $body);
        if ($res['status'] < 200 || $res['status'] >= 300) {
            return null;
        }
        $sig = $this->headerGetCi($res['headers'], AgentVendHeaders::SIGNATURE);
        $ts = $this->headerGetCi($res['headers'], AgentVendHeaders::TIMESTAMP);
        if ($sig === '' || $ts === '' || !Hmac::validateHmacSignature($sig, $res['body'] . $ts, $this->serviceSecret)) {
            return null;
        }
        $json = json_decode($res['body'], true);
        if (!is_array($json) || empty($json['valid'])) {
            return null;
        }
        return $json;
    }

    /** @return array<string,mixed>|null */
    public function estimateUsage(string $serviceKey, float $estimatedUnits): ?array
    {
        $body = ['serviceKey' => $serviceKey, 'serviceSecret' => $this->serviceSecret, 'estimatedUnits' => $estimatedUnits];
        if ($this->serviceId !== '') {
            $body['serviceId'] = $this->serviceId;
        }
        $res = $this->requestJson('POST', $this->coreApiUrl . $this->corePathPrefix . '/service-keys/estimate-usage', $body);
        if (!in_array($res['status'], [200, 403, 429], true) || trim($res['body']) === '') {
            return null;
        }
        $sig = $this->headerGetCi($res['headers'], AgentVendHeaders::SIGNATURE);
        $ts = $this->headerGetCi($res['headers'], AgentVendHeaders::TIMESTAMP);
        if ($sig === '' || $ts === '' || !Hmac::validateHmacSignature($sig, $res['body'] . $ts, $this->serviceSecret)) {
            return null;
        }
        $json = json_decode($res['body'], true);
        if (!is_array($json)) {
            return null;
        }
        $json['httpStatus'] = $res['status'];
        return $json;
    }

    /** @return array<string,mixed>|null */
    public function estimateUsageWithJwt(string $bearerToken, string $userId, string $serviceId, float $estimatedUnits): ?array
    {
        $res = $this->requestJson('POST', $this->coreApiUrl . $this->corePathPrefix . '/billing/usage/estimate', [
            'userId' => $userId,
            'serviceId' => $serviceId,
            'estimatedUnits' => $estimatedUnits,
        ], [
            'Authorization: Bearer ' . trim($bearerToken),
        ]);
        if (!in_array($res['status'], [200, 403, 429], true) || trim($res['body']) === '') {
            return null;
        }
        $json = json_decode($res['body'], true);
        if (!is_array($json)) {
            return null;
        }
        $json['httpStatus'] = $res['status'];
        return $json;
    }

    /** @return array{statusCode:int,body:string,asyncEnvelope:?array<string,mixed>}|null */
    public function invokeService(string $method, string $serviceId, string $endpointId, string $serviceKey, ?string $body = null, bool $async = false): ?array
    {
        $path = sprintf('%s/service/%s/endpoint/%s/invoke%s', $this->gatewayPathPrefix, $serviceId, $endpointId, $async ? '/async' : '');
        $headers = ['Authorization: Bearer ' . $serviceKey];
        if ($body !== null && $body !== '' && in_array(strtoupper($method), ['POST', 'PUT'], true)) {
            $headers[] = 'Content-Type: application/json';
        }
        $res = $this->requestRaw(strtoupper($method), $this->gatewayApiUrl . $path, $body, $headers);
        if ($res === null) {
            return null;
        }
        $out = ['statusCode' => $res['status'], 'body' => $res['body'], 'asyncEnvelope' => null];
        if ($res['status'] === 202) {
            $json = json_decode($res['body'], true);
            if (is_array($json) && isset($json['requestId'])) {
                $out['asyncEnvelope'] = $json;
            }
        }
        return $out;
    }

    /** @return array<string,mixed>|null */
    public function reportUsage(string $userId, string $serviceId, float $unitsUsed): ?array
    {
        return $this->reportUsageAt($userId, $serviceId, $unitsUsed, null);
    }

    /** @return array<string,mixed>|null */
    public function reportUsageAt(string $userId, string $serviceId, float $unitsUsed, ?\DateTimeInterface $timestamp): ?array
    {
        $t = $timestamp ?: new \DateTimeImmutable('now', new \DateTimeZone('UTC'));
        $headerTs = (string) $t->getTimestamp();
        $bodyArr = ['userId' => $userId, 'serviceId' => $serviceId, 'unitsUsed' => $unitsUsed, 'timestamp' => $t->format(DATE_ATOM)];
        $body = json_encode($bodyArr, JSON_UNESCAPED_SLASHES);
        if (!is_string($body)) {
            return null;
        }
        $sig = Hmac::calculateHmacWithTimestamp($body, $headerTs, $this->serviceSecret);
        $res = $this->requestRaw('POST', $this->usageApiUrl . $this->usagePathPrefix . '/report', $body, [
            'Content-Type: application/json',
            AgentVendHeaders::SIGNATURE . ': ' . $sig,
            AgentVendHeaders::TIMESTAMP . ': ' . $headerTs,
        ]);
        if ($res === null || $res['status'] < 200 || $res['status'] >= 300) {
            return null;
        }
        $json = json_decode($res['body'], true);
        return is_array($json) ? $json : null;
    }

    public function sendProgressUpdate(string $progressUrl, string $requestId, string $stage, int $percentageComplete, ?string $errorMessage = null): bool
    {
        [$baseUrl, $ts] = $this->splitTimestampUrl($progressUrl);
        if ($ts === null) {
            return false;
        }
        $bodyArr = ['stage' => $stage, 'percentageComplete' => $percentageComplete, 'timestamp' => gmdate('c')];
        if ($errorMessage !== null) {
            $bodyArr['errorMessage'] = $errorMessage;
        }
        $body = json_encode($bodyArr, JSON_UNESCAPED_SLASHES);
        if (!is_string($body)) {
            return false;
        }
        $sig = Hmac::calculateHmacWithTimestamp($body, $ts, $this->serviceSecret);
        $res = $this->requestRaw('POST', $baseUrl, $body, [
            'Content-Type: application/json',
            AgentVendHeaders::SIGNATURE . ': ' . $sig,
            AgentVendHeaders::TIMESTAMP . ': ' . $ts,
        ]);
        return $res !== null && $res['status'] >= 200 && $res['status'] < 300;
    }

    public function sendCompletion(
        string $callbackUrl,
        string $requestId,
        string $status,
        float $units = 0.0,
        ?string $result = null,
        ?string $resultUrl = null,
        ?string $contentType = null
    ): bool {
        [$baseUrl, $ts] = $this->splitTimestampUrl($callbackUrl);
        if ($ts === null) {
            return false;
        }
        $bodyArr = ['status' => strtoupper($status), 'timestamp' => gmdate('c'), 'units' => $units];
        if ($result !== null) {
            $bodyArr['result'] = $result;
        }
        if ($resultUrl !== null) {
            $bodyArr['resultUrl'] = $resultUrl;
        }
        if ($contentType !== null) {
            $bodyArr['contentType'] = $contentType;
        }
        $body = json_encode($bodyArr, JSON_UNESCAPED_SLASHES);
        if (!is_string($body)) {
            return false;
        }
        $sig = Hmac::calculateHmacWithTimestamp($body, $ts, $this->serviceSecret);
        $res = $this->requestRaw('POST', $baseUrl, $body, [
            'Content-Type: application/json',
            AgentVendHeaders::SIGNATURE . ': ' . $sig,
            AgentVendHeaders::TIMESTAMP . ': ' . $ts,
        ]);
        return $res !== null && $res['status'] >= 200 && $res['status'] < 300;
    }

    /** @return array{ok:bool,status:int,body:string} */
    public function getRequestStatus(string $requestId, string $serviceKey): array
    {
        $res = $this->requestRaw('GET', sprintf('%s%s/requests/%s/status', $this->gatewayApiUrl, $this->gatewayPathPrefix, $requestId), null, [
            'Authorization: Bearer ' . $serviceKey,
        ]);
        if ($res === null) {
            return ['ok' => false, 'status' => 0, 'body' => ''];
        }
        return ['ok' => $res['status'] >= 200 && $res['status'] < 300, 'status' => $res['status'], 'body' => $res['body']];
    }

    /** @return array{ok:bool,status:int,body:string} */
    public function getRequestResult(string $requestId, string $serviceKey): array
    {
        $res = $this->requestRaw('GET', sprintf('%s%s/requests/%s/result', $this->gatewayApiUrl, $this->gatewayPathPrefix, $requestId), null, [
            'Authorization: Bearer ' . $serviceKey,
        ]);
        if ($res === null) {
            return ['ok' => false, 'status' => 0, 'body' => ''];
        }
        return ['ok' => $res['status'] >= 200 && $res['status'] < 300, 'status' => $res['status'], 'body' => $res['body']];
    }

    private function normalizePrefix(string $prefix): string
    {
        $p = trim($prefix);
        if ($p === '') {
            return '';
        }
        if ($p[0] !== '/') {
            $p = '/' . $p;
        }
        return rtrim($p, '/');
    }

    /** @return array{string,?string} */
    private function splitTimestampUrl(string $url): array
    {
        $parts = parse_url($url);
        if ($parts === false) {
            return [$url, null];
        }
        $scheme = $parts['scheme'] ?? '';
        $host = $parts['host'] ?? '';
        $port = isset($parts['port']) ? ':' . $parts['port'] : '';
        $path = $parts['path'] ?? '';
        $base = $scheme !== '' ? sprintf('%s://%s%s%s', $scheme, $host, $port, $path) : $url;
        $ts = null;
        if (isset($parts['query'])) {
            parse_str($parts['query'], $qs);
            if (isset($qs['timestamp']) && is_string($qs['timestamp']) && $qs['timestamp'] !== '') {
                $ts = $qs['timestamp'];
            }
        }
        return [$base, $ts];
    }

    /** @param array<string,mixed> $headers */
    private function headerGetCi(array $headers, string $name): string
    {
        foreach ($headers as $k => $v) {
            if (strcasecmp((string) $k, $name) === 0) {
                return is_array($v) ? (string) ($v[0] ?? '') : (string) $v;
            }
        }
        return '';
    }

    /** @param array<string,mixed> $payload
     *  @param list<string> $extraHeaders
     *  @return array{status:int,body:string,headers:array<string,mixed>}
     */
    private function requestJson(string $method, string $url, array $payload, array $extraHeaders = []): array
    {
        $headers = ['Content-Type: application/json'];
        foreach ($extraHeaders as $k => $v) {
            if (is_int($k)) {
                $headers[] = (string) $v;
            } else {
                $headers[] = $k . ': ' . (string) $v;
            }
        }
        $body = json_encode($payload, JSON_UNESCAPED_SLASHES);
        if (!is_string($body)) {
            $body = '{}';
        }
        return $this->requestRaw($method, $url, $body, $headers) ?? ['status' => 0, 'body' => '', 'headers' => []];
    }

    /** @param list<string> $headers
     *  @return array{status:int,body:string,headers:array<string,mixed>}|null
     */
    private function requestRaw(string $method, string $url, ?string $body, array $headers): ?array
    {
        $ch = curl_init($url);
        if ($ch === false) {
            return null;
        }
        curl_setopt_array($ch, [
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_CUSTOMREQUEST => strtoupper($method),
            CURLOPT_TIMEOUT => 60,
            CURLOPT_HTTPHEADER => $headers,
            CURLOPT_HEADER => true,
        ]);
        if ($body !== null && $body !== '') {
            curl_setopt($ch, CURLOPT_POSTFIELDS, $body);
        }
        $raw = curl_exec($ch);
        if ($raw === false) {
            curl_close($ch);
            return null;
        }
        $status = (int) curl_getinfo($ch, CURLINFO_RESPONSE_CODE);
        $headerSize = (int) curl_getinfo($ch, CURLINFO_HEADER_SIZE);
        curl_close($ch);
        $headerRaw = substr($raw, 0, $headerSize);
        $bodyRaw = substr($raw, $headerSize);
        $parsedHeaders = [];
        foreach (explode("\r\n", (string) $headerRaw) as $line) {
            if (str_contains($line, ':')) {
                [$k, $v] = explode(':', $line, 2);
                $parsedHeaders[trim($k)] = trim($v);
            }
        }
        return ['status' => $status, 'body' => (string) $bodyRaw, 'headers' => $parsedHeaders];
    }
}

