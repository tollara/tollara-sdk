// Placeholder when http feature is enabled
#[allow(dead_code)]
pub struct AgentKeyValidationResult {
    pub user_id: Option<String>,
    pub agent_id: Option<String>,
    pub plan: Option<String>,
    pub roles: Vec<String>,
    pub quota_remaining: Option<f64>,
    pub subscription_active: bool,
}
