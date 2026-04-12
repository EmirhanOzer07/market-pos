package com.market.pos.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);

    public void logLoginAttempt(String username, boolean success, String ip) {
        String status = success ? "SUCCESS" : "FAILED";
        log.info("[AUDIT] LOGIN {} | User: {} | IP: {}", status, username, ip);
    }

    public void logUserCreation(String username, String createdBy) {
        log.info("[AUDIT] USER_CREATED | User: {} | CreatedBy: {}", username, createdBy);
    }

    public void logUserDeletion(Long userId, String deletedBy) {
        log.info("[AUDIT] USER_DELETED | UserId: {} | DeletedBy: {}", userId, deletedBy);
    }

    public void logSalesTransaction(Long saleId, String cashier, java.math.BigDecimal amount) {
        log.info("[AUDIT] SALE_COMPLETED | SaleId: {} | Cashier: {} | Amount: {} TL",
                saleId, cashier, amount);
    }

    public void logUnauthorizedAccess(String user, String action, String reason) {
        log.warn("[AUDIT] UNAUTHORIZED_ACCESS | User: {} | Action: {} | Reason: {}",
                user, action, reason);
    }

    
    public void logFailedAdminAttempt(String ip, String reason) {
        log.warn("[AUDIT] ADMIN_ATTEMPT_FAILED | IP: {} | Reason: {}", ip, reason);
    }

    
    public void logSuccessfulAdminAction(String ip, String action) {
        log.info("[AUDIT] ADMIN_ACTION_SUCCESS | IP: {} | Action: {}", ip, action);
    }
}