package sdk

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"strconv"
	"strings"
	"time"
)

const (
	DefaultCorePathPrefix    = "/api/v1"
	DefaultGatewayPathPrefix = "/api"
	DefaultUsagePathPrefix   = "/api/usage"
)

type TollaraClient struct {
	HTTPClient        *http.Client
	APIURL            string
	CoreBaseURL       string
	GatewayBaseURL    string
	UsageBaseURL      string
	CorePathPrefix    string
	GatewayPathPrefix string
	UsagePathPrefix   string
	ServiceID         string
	ServiceSecret     string
}

type TollaraClientOptions struct {
	HTTPClient        *http.Client
	APIURL            string
	CoreBaseURL       string
	GatewayBaseURL    string
	UsageBaseURL      string
	CorePathPrefix    string
	GatewayPathPrefix string
	UsagePathPrefix   string
	ServiceID         string
	ServiceSecret     string
}

type ServiceKeyValidationResult struct {
	UserID             string   `json:"userId"`
	ServiceID          string   `json:"serviceId"`
	ServiceKeyID       string   `json:"serviceKeyId"`
	Plan               string   `json:"plan"`
	Roles              []string `json:"roles"`
	QuotaRemaining     *float64 `json:"quotaRemaining"`
	SubscriptionActive bool     `json:"subscriptionActive"`
	BillingModelType   string   `json:"billingModelType"`
	MeasurementType    string   `json:"measurementType"`
	UnitLabel          string   `json:"unitLabel"`
}

type UsageEstimateResult struct {
	SufficientCredits    bool                   `json:"sufficientCredits"`
	WouldExceedCap       bool                   `json:"wouldExceedCap"`
	WouldAllow           bool                   `json:"wouldAllow"`
	EstimatedCost        *float64               `json:"estimatedCost"`
	RemainingCredits     *float64               `json:"remainingCredits"`
	RemainingSpendingCap *float64               `json:"remainingSpendingCap"`
	BillingModelType     string                 `json:"billingModelType"`
	MeasurementType      string                 `json:"measurementType"`
	UnitLabel            string                 `json:"unitLabel"`
	Breakdown            map[string]interface{} `json:"breakdown"`
	EstimateSchemaVersion int                   `json:"estimateSchemaVersion"`
	Timestamp            int64                  `json:"timestamp"`
	HTTPStatus           int                    `json:"-"`
}

type UsageReportResponse struct {
	Status                    string   `json:"status"`
	Warning                   string   `json:"warning"`
	IsOverLimit               bool     `json:"isOverLimit"`
	RemainingRequestsPerPeriod int64   `json:"remainingRequestsPerPeriod"`
	RemainingTimeUnitsPerPeriod *float64 `json:"remainingTimeUnitsPerPeriod"`
	RemainingSpendingCap      *float64 `json:"remainingSpendingCap"`
	OverageRate               *float64 `json:"overageRate"`
}

type GatewayInvokeAsyncEnvelope struct {
	Status      string `json:"status"`
	RequestID   string `json:"requestId"`
	CallbackURL string `json:"callbackUrl"`
	ProgressURL string `json:"progressUrl"`
}

type GatewayInvokeResult struct {
	StatusCode int
	Body       string
	Async      *GatewayInvokeAsyncEnvelope
}

func NewTollaraClient(opts TollaraClientOptions) (*TollaraClient, error) {
	apiURL := firstNonBlank(opts.APIURL, os.Getenv(EnvAPIURL), DefaultAPIURL)
	serviceID := firstNonBlank(opts.ServiceID, os.Getenv(EnvServiceID))
	serviceSecret := firstNonBlank(opts.ServiceSecret, os.Getenv(EnvServiceSecret))
	if strings.TrimSpace(serviceSecret) == "" {
		return nil, fmt.Errorf("service secret is required: set ServiceSecret or %s", EnvServiceSecret)
	}
	httpClient := opts.HTTPClient
	if httpClient == nil {
		httpClient = &http.Client{Timeout: 60 * time.Second}
	}
	corePrefix := firstNonBlank(opts.CorePathPrefix, DefaultCorePathPrefix)
	gatewayPrefix := firstNonBlank(opts.GatewayPathPrefix, DefaultGatewayPathPrefix)
	usagePrefix := firstNonBlank(opts.UsagePathPrefix, DefaultUsagePathPrefix)
	return &TollaraClient{
		HTTPClient:        httpClient,
		APIURL:            trimTrailingSlash(apiURL),
		CoreBaseURL:       trimTrailingSlash(firstNonBlank(opts.CoreBaseURL, apiURL)),
		GatewayBaseURL:    trimTrailingSlash(firstNonBlank(opts.GatewayBaseURL, apiURL)),
		UsageBaseURL:      trimTrailingSlash(firstNonBlank(opts.UsageBaseURL, apiURL)),
		CorePathPrefix:    normalizePrefix(corePrefix),
		GatewayPathPrefix: normalizePrefix(gatewayPrefix),
		UsagePathPrefix:   normalizePrefix(usagePrefix),
		ServiceID:         strings.TrimSpace(serviceID),
		ServiceSecret:     strings.TrimSpace(serviceSecret),
	}, nil
}

func (c *TollaraClient) ValidateServiceKey(serviceKey string) (*ServiceKeyValidationResult, error) {
	body := map[string]interface{}{"serviceKey": serviceKey, "serviceSecret": c.ServiceSecret}
	if c.ServiceID != "" {
		body["serviceId"] = c.ServiceID
	}
	url := c.CoreBaseURL + c.CorePathPrefix + "/service-keys/validate"
	respBody, headers, status, err := c.doJSON("POST", url, body, map[string]string{"Content-Type": "application/json"})
	if err != nil || status < 200 || status >= 300 {
		return nil, err
	}
	sig := headerGetCI(headers, HeaderSignature)
	ts := headerGetCI(headers, HeaderTimestamp)
	if sig == "" || ts == "" || !ValidateHmacSignature(sig, respBody+ts, c.ServiceSecret) {
		return nil, nil
	}
	var raw struct {
		Valid bool `json:"valid"`
		ServiceKeyValidationResult
	}
	if json.Unmarshal([]byte(respBody), &raw) != nil || !raw.Valid {
		return nil, nil
	}
	return &raw.ServiceKeyValidationResult, nil
}

func (c *TollaraClient) EstimateUsage(serviceKey string, estimatedUnits float64) (*UsageEstimateResult, error) {
	body := map[string]interface{}{"serviceKey": serviceKey, "serviceSecret": c.ServiceSecret, "estimatedUnits": estimatedUnits}
	if c.ServiceID != "" {
		body["serviceId"] = c.ServiceID
	}
	url := c.CoreBaseURL + c.CorePathPrefix + "/service-keys/estimate-usage"
	respBody, headers, status, err := c.doJSON("POST", url, body, map[string]string{"Content-Type": "application/json"})
	if err != nil || (status != 200 && status != 403 && status != 429) || strings.TrimSpace(respBody) == "" {
		return nil, err
	}
	sig := headerGetCI(headers, HeaderSignature)
	ts := headerGetCI(headers, HeaderTimestamp)
	if sig == "" || ts == "" || !ValidateHmacSignature(sig, respBody+ts, c.ServiceSecret) {
		return nil, nil
	}
	var out UsageEstimateResult
	if json.Unmarshal([]byte(respBody), &out) != nil {
		return nil, nil
	}
	out.HTTPStatus = status
	return &out, nil
}

func (c *TollaraClient) EstimateUsageWithJWT(bearerToken, userID, serviceID string, estimatedUnits float64) (*UsageEstimateResult, error) {
	url := c.CoreBaseURL + c.CorePathPrefix + "/billing/usage/estimate"
	body := map[string]interface{}{"userId": userID, "serviceId": serviceID, "estimatedUnits": estimatedUnits}
	respBody, _, status, err := c.doJSON("POST", url, body, map[string]string{
		"Content-Type":  "application/json",
		"Authorization": "Bearer " + strings.TrimSpace(bearerToken),
	})
	if err != nil || (status != 200 && status != 403 && status != 429) || strings.TrimSpace(respBody) == "" {
		return nil, err
	}
	var out UsageEstimateResult
	if json.Unmarshal([]byte(respBody), &out) != nil {
		return nil, nil
	}
	out.HTTPStatus = status
	return &out, nil
}

func (c *TollaraClient) InvokeService(method, serviceID, endpointID, serviceKey, body string, async bool) (*GatewayInvokeResult, error) {
	path := fmt.Sprintf("%s/service/%s/endpoint/%s/invoke", c.GatewayPathPrefix, serviceID, endpointID)
	if async {
		path += "/async"
	}
	url := c.GatewayBaseURL + path
	headers := map[string]string{"Authorization": "Bearer " + serviceKey}
	if (method == "POST" || method == "PUT") && strings.TrimSpace(body) != "" {
		headers["Content-Type"] = "application/json"
	}
	respBody, _, status, err := c.doRaw(strings.ToUpper(method), url, body, headers)
	if err != nil {
		return nil, err
	}
	out := &GatewayInvokeResult{StatusCode: status, Body: respBody}
	if status == 202 && strings.TrimSpace(respBody) != "" {
		var env GatewayInvokeAsyncEnvelope
		if json.Unmarshal([]byte(respBody), &env) == nil && env.RequestID != "" {
			out.Async = &env
		}
	}
	return out, nil
}

func (c *TollaraClient) ReportUsage(userID, serviceID string, unitsUsed float64) (*UsageReportResponse, error) {
	return c.ReportUsageAt(userID, serviceID, unitsUsed, nil)
}

func (c *TollaraClient) ReportUsageAt(userID, serviceID string, unitsUsed float64, ts *time.Time) (*UsageReportResponse, error) {
	t := time.Now().UTC()
	if ts != nil {
		t = ts.UTC()
	}
	headerTS := strconv.FormatInt(t.Unix(), 10)
	body := map[string]interface{}{
		"userId": userID, "serviceId": serviceID, "unitsUsed": unitsUsed, "timestamp": t.Format(time.RFC3339Nano),
	}
	bodyBytes, _ := json.Marshal(body)
	sig := CalculateHmacWithTimestamp(string(bodyBytes), headerTS, c.ServiceSecret)
	url := c.UsageBaseURL + c.UsagePathPrefix + "/report"
	respBody, _, status, err := c.doRaw("POST", url, string(bodyBytes), map[string]string{
		"Content-Type":      "application/json",
		HeaderSignature:     sig,
		HeaderTimestamp:     headerTS,
	})
	if err != nil || status < 200 || status >= 300 {
		return nil, err
	}
	var out UsageReportResponse
	if json.Unmarshal([]byte(respBody), &out) != nil {
		return nil, nil
	}
	return &out, nil
}

func (c *TollaraClient) SendProgressUpdate(progressURL, requestID, stage string, percentageComplete int, errorMessage *string) (bool, error) {
	base, ts := splitURLTimestamp(progressURL)
	if ts == "" {
		return false, nil
	}
	body := map[string]interface{}{
		"stage": stage, "percentageComplete": percentageComplete, "timestamp": time.Now().UTC().Format(time.RFC3339Nano),
	}
	if errorMessage != nil {
		body["errorMessage"] = *errorMessage
	}
	bodyBytes, _ := json.Marshal(body)
	sig := CalculateHmacWithTimestamp(string(bodyBytes), ts, c.ServiceSecret)
	_, _, status, err := c.doRaw("POST", base, string(bodyBytes), map[string]string{
		"Content-Type":  "application/json",
		HeaderSignature: sig,
		HeaderTimestamp: ts,
	})
	return err == nil && status >= 200 && status < 300, err
}

func (c *TollaraClient) SendCompletion(callbackURL, requestID, status string, units float64, result, resultURL, contentType *string) (bool, error) {
	base, ts := splitURLTimestamp(callbackURL)
	if ts == "" {
		return false, nil
	}
	body := map[string]interface{}{
		"status": strings.ToUpper(status), "timestamp": time.Now().UTC().Format(time.RFC3339Nano), "units": units,
	}
	if result != nil { body["result"] = *result }
	if resultURL != nil { body["resultUrl"] = *resultURL }
	if contentType != nil { body["contentType"] = *contentType }
	bodyBytes, _ := json.Marshal(body)
	sig := CalculateHmacWithTimestamp(string(bodyBytes), ts, c.ServiceSecret)
	_, _, code, err := c.doRaw("POST", base, string(bodyBytes), map[string]string{
		"Content-Type":  "application/json",
		HeaderSignature: sig,
		HeaderTimestamp: ts,
	})
	return err == nil && code >= 200 && code < 300, err
}

func (c *TollaraClient) GetRequestStatus(requestID, serviceKey string) (bool, int, string, error) {
	url := fmt.Sprintf("%s%s/requests/%s/status", c.GatewayBaseURL, c.GatewayPathPrefix, requestID)
	body, _, status, err := c.doRaw("GET", url, "", map[string]string{"Authorization": "Bearer " + serviceKey})
	return err == nil && status >= 200 && status < 300, status, body, err
}

func (c *TollaraClient) GetRequestResult(requestID, serviceKey string) (bool, int, string, error) {
	url := fmt.Sprintf("%s%s/requests/%s/result", c.GatewayBaseURL, c.GatewayPathPrefix, requestID)
	body, _, status, err := c.doRaw("GET", url, "", map[string]string{"Authorization": "Bearer " + serviceKey})
	return err == nil && status >= 200 && status < 300, status, body, err
}

func (c *TollaraClient) doJSON(method, url string, body interface{}, headers map[string]string) (string, http.Header, int, error) {
	b, _ := json.Marshal(body)
	return c.doRaw(method, url, string(b), headers)
}

func (c *TollaraClient) doRaw(method, url, body string, headers map[string]string) (string, http.Header, int, error) {
	var rdr io.Reader
	if body != "" {
		rdr = bytes.NewBufferString(body)
	}
	req, err := http.NewRequest(method, url, rdr)
	if err != nil {
		return "", nil, 0, err
	}
	for k, v := range headers {
		req.Header.Set(k, v)
	}
	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return "", nil, 0, err
	}
	defer resp.Body.Close()
	raw, _ := io.ReadAll(resp.Body)
	return string(raw), resp.Header, resp.StatusCode, nil
}

func trimTrailingSlash(s string) string {
	return strings.TrimRight(strings.TrimSpace(s), "/")
}
func normalizePrefix(s string) string {
	p := strings.TrimSpace(s)
	if !strings.HasPrefix(p, "/") {
		p = "/" + p
	}
	return strings.TrimRight(p, "/")
}
func firstNonBlank(values ...string) string {
	for _, v := range values {
		if strings.TrimSpace(v) != "" {
			return strings.TrimSpace(v)
		}
	}
	return ""
}
func splitURLTimestamp(raw string) (string, string) {
	parts := strings.SplitN(raw, "?", 2)
	if len(parts) != 2 {
		return raw, ""
	}
	base := parts[0]
	for _, pair := range strings.Split(parts[1], "&") {
		kv := strings.SplitN(pair, "=", 2)
		if len(kv) == 2 && kv[0] == "timestamp" && kv[1] != "" {
			return base, kv[1]
		}
	}
	return base, ""
}
func headerGetCI(h http.Header, name string) string {
	for k, vals := range h {
		if strings.EqualFold(k, name) && len(vals) > 0 {
			return vals[0]
		}
	}
	return ""
}
