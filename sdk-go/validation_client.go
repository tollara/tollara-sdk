package sdk

import (
	"encoding/json"
	"net/http"
	"strings"
)

// ValidationFailureCode is a canonical validate outcome code (docs-sdk §2.1.1).
type ValidationFailureCode string

const (
	ValidationFailureMissingKey            ValidationFailureCode = "MISSING_KEY"
	ValidationFailureNetwork               ValidationFailureCode = "NETWORK"
	ValidationFailureHTTPError             ValidationFailureCode = "HTTP_ERROR"
	ValidationFailureMissingSignature      ValidationFailureCode = "MISSING_SIGNATURE_HEADERS"
	ValidationFailureHMACMismatch           ValidationFailureCode = "HMAC_MISMATCH"
	ValidationFailureInvalidKey             ValidationFailureCode = "INVALID_KEY"
	ValidationFailureParseError             ValidationFailureCode = "PARSE_ERROR"
)

// ServiceKeyValidationFailure is a structured validate failure (§2.1.1).
type ServiceKeyValidationFailure struct {
	Code       ValidationFailureCode `json:"code"`
	Message    string                `json:"message,omitempty"`
	HTTPStatus int                   `json:"httpStatus,omitempty"`
}

// ServiceKeyValidationOutcome is the result of validateServiceKeyWithOutcome.
type ServiceKeyValidationOutcome struct {
	OK      bool                         `json:"ok"`
	Result  *ServiceKeyValidationResult  `json:"result,omitempty"`
	Failure *ServiceKeyValidationFailure `json:"failure,omitempty"`
}

type validateResponseBody struct {
	Valid bool   `json:"valid"`
	Error string `json:"error"`
	ServiceKeyValidationResult
}

func validationFailureOutcome(code ValidationFailureCode, message string, httpStatus int) ServiceKeyValidationOutcome {
	f := &ServiceKeyValidationFailure{Code: code, HTTPStatus: httpStatus}
	if message != "" {
		f.Message = message
	}
	return ServiceKeyValidationOutcome{OK: false, Failure: f}
}

type unsignedValidateBody struct {
	Valid *bool  `json:"valid"`
	Error string `json:"error"`
}

func invalidKeyFromUnsignedErrorBody(responseText string, httpStatus int) *ServiceKeyValidationFailure {
	if httpStatus != 401 && httpStatus != 403 {
		return nil
	}
	var data unsignedValidateBody
	if json.Unmarshal([]byte(responseText), &data) != nil || data.Valid == nil || *data.Valid {
		return nil
	}
	msg := strings.TrimSpace(data.Error)
	return &ServiceKeyValidationFailure{
		Code:       ValidationFailureInvalidKey,
		Message:    msg,
		HTTPStatus: httpStatus,
	}
}

func outcomeFromValidateResponse(
	responseText string,
	headers http.Header,
	httpStatus int,
	serviceSecret string,
	fallbackServiceID string,
) ServiceKeyValidationOutcome {
	if httpStatus < 200 || httpStatus >= 300 {
		if failure := invalidKeyFromUnsignedErrorBody(responseText, httpStatus); failure != nil {
			return ServiceKeyValidationOutcome{OK: false, Failure: failure}
		}
		return validationFailureOutcome(ValidationFailureHTTPError, "", httpStatus)
	}

	sig := headerGetCI(headers, HeaderSignature)
	ts := headerGetCI(headers, HeaderTimestamp)
	if sig == "" || ts == "" {
		return validationFailureOutcome(ValidationFailureMissingSignature, "", httpStatus)
	}
	if !ValidateHmacSignature(sig, responseText+ts, serviceSecret) {
		return validationFailureOutcome(ValidationFailureHMACMismatch, "", httpStatus)
	}

	var data validateResponseBody
	if json.Unmarshal([]byte(responseText), &data) != nil {
		return validationFailureOutcome(ValidationFailureParseError, "", httpStatus)
	}
	if !data.Valid {
		return validationFailureOutcome(ValidationFailureInvalidKey, strings.TrimSpace(data.Error), httpStatus)
	}

	result := data.ServiceKeyValidationResult
	if result.ServiceID == "" && fallbackServiceID != "" {
		result.ServiceID = fallbackServiceID
	}
	return ServiceKeyValidationOutcome{OK: true, Result: &result}
}

// ValidateServiceKeyWithOutcome validates a service key and returns a structured outcome (§2.1.1).
func (c *TollaraClient) ValidateServiceKeyWithOutcome(serviceKey string) ServiceKeyValidationOutcome {
	if strings.TrimSpace(serviceKey) == "" {
		return validationFailureOutcome(ValidationFailureMissingKey, "", 0)
	}

	body := map[string]interface{}{"serviceKey": serviceKey, "serviceSecret": c.ServiceSecret}
	if c.ServiceID != "" {
		body["serviceId"] = c.ServiceID
	}
	url := c.CoreBaseURL + c.CorePathPrefix + "/service-keys/validate"
	respBody, headers, status, err := c.doJSON("POST", url, body, map[string]string{"Content-Type": "application/json"})
	if err != nil {
		return validationFailureOutcome(ValidationFailureNetwork, "", 0)
	}
	return outcomeFromValidateResponse(respBody, headers, status, c.ServiceSecret, c.ServiceID)
}
